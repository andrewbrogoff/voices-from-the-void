package com.andrewbrogoff.vftv.core.vis;

import com.andrewbrogoff.vftv.api.vis.VisLimits;
import com.andrewbrogoff.vftv.core.vis.model.RelayNetwork;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.core.BlockPos;

/**
 * A dimension's relay membership, the {@code vftv_relays} SavedData (wands spec, "Topology"):
 * {@code {"format": 1, "members": [{"pos", "kind"}]}}, positions and kinds only. Components, the
 * spatial hash and the lookups are rebuilt in memory when the level loads.
 *
 * @param members every member, ascending position key
 */
public record RelayLedger(List<Member> members) {
    /** The only format this build reads and writes. */
    public static final int FORMAT = 1;

    /** No members. */
    public static final RelayLedger EMPTY = new RelayLedger(List.of());

    /** A member kind by its lower-case name. */
    static final Codec<RelayNetwork.Kind> KIND =
            Codec.STRING.comapFlatMap(
                    s -> {
                        for (RelayNetwork.Kind k : RelayNetwork.Kind.values()) {
                            if (k.name().toLowerCase(Locale.ROOT).equals(s)) {
                                return DataResult.success(k);
                            }
                        }
                        return DataResult.error(() -> "unknown member kind " + s);
                    },
                    k -> k.name().toLowerCase(Locale.ROOT));

    /**
     * One member.
     *
     * @param pos its position
     * @param kind its kind
     */
    public record Member(BlockPos pos, RelayNetwork.Kind kind) {
        static final Codec<Member> CODEC =
                VisCodecs.strict(
                        RecordCodecBuilder.create(
                                i ->
                                        i.group(
                                                        BlockPos.CODEC
                                                                .fieldOf("pos")
                                                                .forGetter(Member::pos),
                                                        KIND.fieldOf("kind").forGetter(Member::kind))
                                                .apply(i, Member::new)),
                        Set.of("pos", "kind"));
    }

    /** The bounded codec: format 1, at most 2²⁰ members, no unknown key. */
    public static final Codec<RelayLedger> BOUNDED =
            VisCodecs.strict(
                    RecordCodecBuilder.<RelayLedger>create(
                            i ->
                                    i.group(
                                                    Codec.INT
                                                            .fieldOf("format")
                                                            .forGetter(l -> FORMAT),
                                                    Member.CODEC
                                                            .sizeLimitedListOf(
                                                                    VisLimits
                                                                            .MAX_RELAY_RECORD_MEMBERS)
                                                            .fieldOf("members")
                                                            .forGetter(RelayLedger::members))
                                            .apply(i, RelayLedger::decoded)),
                    Set.of("format", "members"));

    /** Copies the members. */
    public RelayLedger {
        members = List.copyOf(members);
    }

    private static RelayLedger decoded(int format, List<Member> members) {
        if (format != FORMAT) {
            throw new IllegalArgumentException("unknown format " + format);
        }
        return new RelayLedger(members);
    }
}
