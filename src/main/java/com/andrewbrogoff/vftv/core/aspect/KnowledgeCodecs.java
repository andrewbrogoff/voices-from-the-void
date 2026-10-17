package com.andrewbrogoff.vftv.core.aspect;

import com.andrewbrogoff.vftv.core.aspect.model.Knowledge;
import com.andrewbrogoff.vftv.core.common.DataSlots;
import com.andrewbrogoff.vftv.core.common.RawRecordCodec;
import com.andrewbrogoff.vftv.core.common.RawRecordCodec.Retained;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import org.jspecify.annotations.Nullable;

/**
 * The {@code vftv:knowledge} attachment's codecs (aspects spec, "Sync and state"): the persisted
 * record with its {@code format}, kept raw when unreadable, and the owner sync's baseline and delta
 * stream codecs. Decoding checks format bounds only, never the current registry.
 */
public final class KnowledgeCodecs {
    /** The persisted format. */
    public static final int FORMAT = 1;

    /** The most aspects one ledger snapshot holds (a node's maximum, with room to widen). */
    public static final int MAX_SNAPSHOT = 16;

    /** The most known aspects a sync carries. */
    public static final int MAX_KNOWN = 4096;

    /** The most ledger entries a baseline carries. */
    public static final int MAX_SCANS = 1 << 20;

    private static final Pattern SCAN_KEY =
            Pattern.compile(
                    "(?:(?:item|block|entity):[a-z0-9_.-]+:[a-z0-9_./-]+)|(?:node:-?[0-9]{1,19})");

    private static final Codec<String> ID =
            Codec.STRING.comapFlatMap(
                    s ->
                            Identifier.tryParse(s) != null && s.indexOf(':') > 0
                                    ? DataResult.success(s)
                                    : DataResult.error(() -> "bad id " + s),
                    s -> s);

    private static final Codec<String> KEY =
            Codec.STRING.validate(
                    s ->
                            SCAN_KEY.matcher(s).matches()
                                    ? DataResult.success(s)
                                    : DataResult.error(() -> "bad scan key " + s));

    private static final Codec<Map<String, Integer>> AMOUNTS =
            Codec.unboundedMap(ID, ExtraCodecs.POSITIVE_INT);

    private static final Codec<Knowledge.Scan> SCAN =
            RecordCodecBuilder.create(
                    i ->
                            i.group(
                                            AMOUNTS.validate(
                                                            m ->
                                                                    m.size() <= MAX_SNAPSHOT
                                                                            ? DataResult.success(m)
                                                                            : DataResult.error(
                                                                                    () ->
                                                                                            "snapshot too large"))
                                                    .optionalFieldOf("snapshot", Map.of())
                                                    .forGetter(s -> new TreeMap<>(s.snapshot())),
                                            ID.listOf()
                                                    .optionalFieldOf("paid", List.of())
                                                    .forGetter(
                                                            s ->
                                                                    List.copyOf(
                                                                            new TreeSet<>(
                                                                                    s.paid()))))
                                    .apply(
                                            i,
                                            (snapshot, paid) ->
                                                    new Knowledge.Scan(
                                                            snapshot.isEmpty(),
                                                            snapshot,
                                                            unique(paid))));

    /** The bounded record codec: format 1, every id parsing and every amount positive. */
    public static final Codec<Knowledge> BOUNDED =
            RecordCodecBuilder.<Knowledge>create(
                    i ->
                            i.group(
                                            Codec.INT.fieldOf("format").forGetter(k -> FORMAT),
                                            ID.listOf()
                                                    .fieldOf("known")
                                                    .forGetter(
                                                            k ->
                                                                    List.copyOf(
                                                                            new TreeSet<>(
                                                                                    k.known()))),
                                            Codec.unboundedMap(KEY, SCAN)
                                                    .fieldOf("scans")
                                                    .forGetter(k -> new TreeMap<>(k.scans())),
                                            AMOUNTS.fieldOf("pool")
                                                    .forGetter(k -> new TreeMap<>(k.pool())))
                                    .apply(
                                            i,
                                            (format, known, scans, pool) -> {
                                                if (format != FORMAT) {
                                                    throw new IllegalArgumentException(
                                                            "unknown format " + format);
                                                }
                                                return new Knowledge(
                                                        unique(known), scans, pool, null);
                                            }));

    /** The attachment codec: an unreadable record is kept raw. */
    public static final Codec<Retained<Knowledge>> RETAINED = RawRecordCodec.wrap(BOUNDED);

    private static final StreamCodec<ByteBuf, String> ID_STREAM = ByteBufCodecs.stringUtf8(256);

    private static final StreamCodec<ByteBuf, Map<String, Integer>> AMOUNTS_STREAM =
            amounts(MAX_KNOWN);

    private static final StreamCodec<ByteBuf, Set<String>> IDS_STREAM = ids(MAX_KNOWN);

    private static final StreamCodec<ByteBuf, Knowledge.Scan> SCAN_STREAM =
            StreamCodec.composite(
                    amounts(MAX_SNAPSHOT),
                    Knowledge.Scan::snapshot,
                    ids(MAX_SNAPSHOT),
                    Knowledge.Scan::paid,
                    (snapshot, paid) -> new Knowledge.Scan(snapshot.isEmpty(), snapshot, paid));

    private static StreamCodec<ByteBuf, Map<String, Integer>> amounts(int max) {
        return ByteBufCodecs.map(n -> new TreeMap<>(), ID_STREAM, ByteBufCodecs.VAR_INT, max);
    }

    private static StreamCodec<ByteBuf, Set<String>> ids(int max) {
        return ByteBufCodecs.collection(n -> new TreeSet<>(), ID_STREAM, max);
    }

    private static StreamCodec<ByteBuf, Map<String, Knowledge.Scan>> scans(int max) {
        return ByteBufCodecs.map(
                n -> new TreeMap<>(), ByteBufCodecs.stringUtf8(512), SCAN_STREAM, max);
    }

    /** A full baseline: on join, and whenever the record is replaced whole. */
    public static final StreamCodec<ByteBuf, Knowledge> BASELINE =
            StreamCodec.composite(
                    IDS_STREAM,
                    Knowledge::known,
                    scans(MAX_SCANS),
                    Knowledge::scans,
                    AMOUNTS_STREAM,
                    Knowledge::pool,
                    (known, scans, pool) -> new Knowledge(known, scans, pool, null));

    /** One change: the aspects newly known, the entries written and the pool amounts changed. */
    public static final StreamCodec<ByteBuf, Knowledge.Delta> DELTA =
            StreamCodec.composite(
                    IDS_STREAM,
                    Knowledge.Delta::known,
                    scans(64),
                    Knowledge.Delta::scans,
                    AMOUNTS_STREAM,
                    Knowledge.Delta::pool,
                    Knowledge.Delta::new);

    /**
     * The owner sync: a baseline flag, then the record (or the unreadable flag alone, never the raw
     * tag) or the delta applied to the client's copy.
     */
    public static final DataSlots.DeltaSync<Retained<Knowledge>> SYNC =
            new DataSlots.DeltaSync<>() {
                @Override
                public void write(
                        RegistryFriendlyByteBuf buf, Retained<Knowledge> value, boolean baseline) {
                    Knowledge.Delta delta =
                            value instanceof Retained.Read<Knowledge> r ? r.value().delta() : null;
                    if (!baseline && delta != null) {
                        buf.writeBoolean(false);
                        DELTA.encode(buf, delta);
                        return;
                    }
                    buf.writeBoolean(true);
                    if (value instanceof Retained.Read<Knowledge> r) {
                        buf.writeBoolean(true);
                        BASELINE.encode(buf, r.value().baseline());
                    } else {
                        buf.writeBoolean(false);
                    }
                }

                @Override
                public Retained<Knowledge> read(
                        RegistryFriendlyByteBuf buf, @Nullable Retained<Knowledge> previous) {
                    if (!buf.readBoolean()) {
                        Knowledge.Delta delta = DELTA.decode(buf);
                        Knowledge base =
                                previous instanceof Retained.Read<Knowledge> r
                                        ? r.value()
                                        : Knowledge.EMPTY;
                        return new Retained.Read<>(base.apply(delta));
                    }
                    return buf.readBoolean()
                            ? new Retained.Read<>(BASELINE.decode(buf))
                            : new Retained.Unreadable<>(new CompoundTag(), "unreadable");
                }
            };

    private KnowledgeCodecs() {}

    private static Set<String> unique(List<String> ids) {
        Set<String> set = new HashSet<>(ids);
        if (set.size() != ids.size()) {
            throw new IllegalArgumentException("duplicate id");
        }
        return set;
    }
}
