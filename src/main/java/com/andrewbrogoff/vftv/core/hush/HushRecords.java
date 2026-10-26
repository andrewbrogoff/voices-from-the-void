package com.andrewbrogoff.vftv.core.hush;

import com.andrewbrogoff.vftv.api.hush.HushLimits;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;

/**
 * The Hush's persisted records and their bounded codecs (Hush spec, "Sync and state"): the aura,
 * {@code vftv_flux_pending}, {@code vftv_hushed_nodes} and the Pealing Bell. Decoding checks format
 * bounds only; wrap each in {@code RawRecordCodec} to keep unreadable records raw.
 */
public final class HushRecords {
    /** Every record's format. */
    public static final int FORMAT = 1;

    /** The longest type id the journal keeps. */
    public static final int MAX_ID = 256;

    /** The Pealing Bell's largest cursor: one less than the cube of side 33. */
    public static final int MAX_CURSOR = 33 * 33 * 33 - 1;

    /** The Pealing Bell's largest Sonus. */
    public static final int MAX_SONUS = 1_000;

    private HushRecords() {}

    /**
     * The aura's fields.
     *
     * @param flux pooled flux, 0 to {@link HushLimits#FLUX_FORMAT_MAX}
     * @param seep the latest retained deposit's {@code asLong()}, inside the chunk
     * @param spiller the last known player behind a retained deposit
     * @param steps attempts made, a wrapping word
     * @param taken blocks converted, a wrapping word
     */
    public record AuraData(
            int format,
            int flux,
            Optional<Long> seep,
            Optional<UUID> spiller,
            long steps,
            long taken) {}

    /** The aura's bounded codec. */
    public static final Codec<AuraData> AURA =
            RecordCodecBuilder.<AuraData>create(
                            i ->
                                    i.group(
                                                    Codec.INT
                                                            .fieldOf("format")
                                                            .forGetter(AuraData::format),
                                                    Codec.INT
                                                            .fieldOf("flux")
                                                            .forGetter(AuraData::flux),
                                                    Codec.LONG
                                                            .optionalFieldOf("seep")
                                                            .forGetter(AuraData::seep),
                                                    UUIDUtil.CODEC
                                                            .optionalFieldOf("spiller")
                                                            .forGetter(AuraData::spiller),
                                                    Codec.LONG
                                                            .fieldOf("steps")
                                                            .forGetter(AuraData::steps),
                                                    Codec.LONG
                                                            .fieldOf("taken")
                                                            .forGetter(AuraData::taken))
                                            .apply(i, AuraData::new))
                    .validate(
                            a ->
                                    a.format() != FORMAT
                                            ? DataResult.error(() -> "unknown format " + a.format())
                                            : a.flux() < 0 || a.flux() > HushLimits.FLUX_FORMAT_MAX
                                                    ? DataResult.error(
                                                            () ->
                                                                    "flux "
                                                                            + a.flux()
                                                                            + " out of bounds")
                                                    : DataResult.success(a));

    /**
     * One pending deposit.
     *
     * @param chunk the chunk's {@code pack()}
     * @param flux 1 to {@link HushLimits#FLUX_FORMAT_MAX}
     * @param seep the latest deposit's {@code asLong()}
     * @param spiller the last known player
     */
    public record PendingEntry(long chunk, int flux, long seep, Optional<UUID> spiller) {}

    /**
     * {@code vftv_flux_pending}.
     *
     * @param overflow the level's overflow total, saturating
     * @param entries at most {@link HushLimits#MAX_PENDING}, distinct chunks
     */
    public record Pending(int format, long overflow, List<PendingEntry> entries) {
        /** The empty store. */
        public static final Pending EMPTY = new Pending(FORMAT, 0, List.of());
    }

    private static final Codec<PendingEntry> PENDING_ENTRY =
            RecordCodecBuilder.<PendingEntry>create(
                            i ->
                                    i.group(
                                                    Codec.LONG
                                                            .fieldOf("chunk")
                                                            .forGetter(PendingEntry::chunk),
                                                    Codec.INT
                                                            .fieldOf("flux")
                                                            .forGetter(PendingEntry::flux),
                                                    Codec.LONG
                                                            .fieldOf("seep")
                                                            .forGetter(PendingEntry::seep),
                                                    UUIDUtil.CODEC
                                                            .optionalFieldOf("spiller")
                                                            .forGetter(PendingEntry::spiller))
                                            .apply(i, PendingEntry::new))
                    .validate(
                            e ->
                                    e.flux() < 1 || e.flux() > HushLimits.FLUX_FORMAT_MAX
                                            ? DataResult.error(
                                                    () ->
                                                            "pending flux "
                                                                    + e.flux()
                                                                    + " out of bounds")
                                            : DataResult.success(e));

    /** {@code vftv_flux_pending}'s bounded codec. */
    public static final Codec<Pending> PENDING =
            RecordCodecBuilder.<Pending>create(
                            i ->
                                    i.group(
                                                    Codec.INT
                                                            .fieldOf("format")
                                                            .forGetter(Pending::format),
                                                    Codec.LONG
                                                            .fieldOf("overflow")
                                                            .forGetter(Pending::overflow),
                                                    PENDING_ENTRY
                                                            .listOf(0, HushLimits.MAX_PENDING)
                                                            .fieldOf("entries")
                                                            .forGetter(Pending::entries))
                                            .apply(i, Pending::new))
                    .validate(HushRecords::checkPending);

    private static DataResult<Pending> checkPending(Pending p) {
        if (p.format() != FORMAT) {
            return DataResult.error(() -> "unknown format " + p.format());
        }
        if (p.overflow() < 0) {
            return DataResult.error(() -> "negative overflow");
        }
        Set<Long> chunks = new HashSet<>();
        for (PendingEntry e : p.entries()) {
            if (!chunks.add(e.chunk())) {
                return DataResult.error(() -> "duplicate pending chunk " + e.chunk());
            }
        }
        return DataResult.success(p);
    }

    /**
     * One hushed node's original.
     *
     * @param type its type before it was hushed
     * @param clarity its clarity's serialized name
     */
    public record Original(Identifier type, String clarity) {}

    /**
     * {@code vftv_hushed_nodes}: the originals by node id.
     *
     * @param nodes at most {@link HushLimits#MAX_HUSHED_NODES}
     */
    public record Journal(int format, Map<Long, Original> nodes) {
        /** The empty journal. */
        public static final Journal EMPTY = new Journal(FORMAT, Map.of());
    }

    private static final Codec<Identifier> TYPE =
            Identifier.CODEC.validate(
                    id ->
                            id.toString().length() <= MAX_ID
                                    ? DataResult.success(id)
                                    : DataResult.error(() -> "type id over " + MAX_ID));

    private static final Codec<String> CLARITY =
            Codec.STRING.validate(
                    c ->
                            c.equals("faint") || c.equals("steady") || c.equals("clear")
                                    ? DataResult.success(c)
                                    : DataResult.error(() -> "unknown clarity " + c));

    private static final Codec<Original> ORIGINAL =
            RecordCodecBuilder.create(
                    i ->
                            i.group(
                                            TYPE.fieldOf("type").forGetter(Original::type),
                                            CLARITY.fieldOf("clarity").forGetter(Original::clarity))
                                    .apply(i, Original::new));

    private static final Codec<Long> DECIMAL =
            Codec.STRING.comapFlatMap(
                    s -> {
                        try {
                            return DataResult.success(Long.parseLong(s));
                        } catch (NumberFormatException e) {
                            return DataResult.error(() -> "not a decimal id: " + s);
                        }
                    },
                    String::valueOf);

    /** {@code vftv_hushed_nodes}' bounded codec. */
    public static final Codec<Journal> JOURNAL =
            RecordCodecBuilder.<Journal>create(
                            i ->
                                    i.group(
                                                    Codec.INT
                                                            .fieldOf("format")
                                                            .forGetter(Journal::format),
                                                    Codec.unboundedMap(DECIMAL, ORIGINAL)
                                                            .<Map<Long, Original>>xmap(
                                                                    TreeMap::new, m -> m)
                                                            .fieldOf("nodes")
                                                            .forGetter(Journal::nodes))
                                            .apply(i, Journal::new))
                    .validate(
                            j ->
                                    j.format() != FORMAT
                                            ? DataResult.error(() -> "unknown format " + j.format())
                                            : j.nodes().size() > HushLimits.MAX_HUSHED_NODES
                                                    ? DataResult.error(
                                                            () -> "too many hushed nodes")
                                                    : DataResult.success(j));

    /**
     * The Pealing Bell's record.
     *
     * @param sonus 0 to {@link #MAX_SONUS}
     * @param cursor 0 to {@link #MAX_CURSOR}
     */
    public record Bell(int format, int sonus, int cursor) {
        /** A new bell: empty, cursor 0. */
        public static final Bell EMPTY = new Bell(FORMAT, 0, 0);
    }

    /** The bell's bounded codec. */
    public static final Codec<Bell> BELL =
            RecordCodecBuilder.<Bell>create(
                            i ->
                                    i.group(
                                                    Codec.INT
                                                            .fieldOf("format")
                                                            .forGetter(Bell::format),
                                                    Codec.INT
                                                            .fieldOf("sonus")
                                                            .forGetter(Bell::sonus),
                                                    Codec.INT
                                                            .fieldOf("cursor")
                                                            .forGetter(Bell::cursor))
                                            .apply(i, Bell::new))
                    .validate(
                            b ->
                                    b.format() != FORMAT
                                            ? DataResult.error(() -> "unknown format " + b.format())
                                            : b.sonus() < 0 || b.sonus() > MAX_SONUS
                                                    ? DataResult.error(() -> "sonus out of bounds")
                                                    : b.cursor() < 0 || b.cursor() > MAX_CURSOR
                                                            ? DataResult.error(
                                                                    () -> "cursor out of bounds")
                                                            : DataResult.success(b));
}
