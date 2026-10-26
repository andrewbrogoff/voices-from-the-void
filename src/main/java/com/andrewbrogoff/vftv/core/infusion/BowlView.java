package com.andrewbrogoff.vftv.core.infusion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.Identifier;

/**
 * A ringing bowl's synced view (infusion spec, "Sync and state"): the pending components' offsets
 * with a present flag, the aspect being drawn, units drawn and owed, and components done and in
 * all. Display only.
 *
 * @param pending each pending component, {@link #pack packed}
 * @param drawing the first aspect still owed, if any
 * @param drawn units drawn in all
 * @param owed units owed in all
 * @param done components absorbed
 * @param total components in all
 */
public record BowlView(
        List<Integer> pending,
        Optional<Identifier> drawing,
        int drawn,
        int owed,
        int done,
        int total) {
    /** No run. */
    public static final BowlView NONE = new BowlView(List.of(), Optional.empty(), 0, 0, 0, 0);

    /** The update-tag form; at most 16 pending entries. */
    public static final Codec<BowlView> CODEC =
            RecordCodecBuilder.create(
                    i ->
                            i.group(
                                            Codec.INT
                                                    .listOf(0, 16)
                                                    .fieldOf("pending")
                                                    .forGetter(BowlView::pending),
                                            Identifier.CODEC
                                                    .optionalFieldOf("drawing")
                                                    .forGetter(BowlView::drawing),
                                            Codec.INT.fieldOf("drawn").forGetter(BowlView::drawn),
                                            Codec.INT.fieldOf("owed").forGetter(BowlView::owed),
                                            Codec.INT.fieldOf("done").forGetter(BowlView::done),
                                            Codec.INT.fieldOf("total").forGetter(BowlView::total))
                                    .apply(i, BowlView::new));

    /** Copies the list. */
    public BowlView {
        pending = List.copyOf(pending);
    }

    /** A pending entry: offset (dx, dz) and whether its pedestal holds the promised stack. */
    public static int pack(int dx, int dz, boolean present) {
        return (dx + 8) | (dz + 8) << 4 | (present ? 1 << 8 : 0);
    }

    /** An entry's dx. */
    public static int dx(int packed) {
        return (packed & 15) - 8;
    }

    /** An entry's dz. */
    public static int dz(int packed) {
        return (packed >> 4 & 15) - 8;
    }

    /** Whether an entry's pedestal holds the promised stack. */
    public static boolean present(int packed) {
        return (packed & 1 << 8) != 0;
    }
}
