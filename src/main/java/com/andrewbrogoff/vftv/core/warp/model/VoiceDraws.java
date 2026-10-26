package com.andrewbrogoff.vftv.core.warp.model;

import com.andrewbrogoff.vftv.core.common.model.Hashes;
import com.andrewbrogoff.vftv.core.common.model.Salts;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.SortedMap;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * The seeded draws of one Voice check (warp spec, "Selection and draws"): one {@code
 * java.util.Random(fold([VOIC, uuidMsb, uuidLsb, checks]))}, drawn in a fixed order: the roll, the
 * pick over the eligible events' weights, the cue seed, then the type's own parameter. Pure, so the
 * vectors replay without a world.
 */
public final class VoiceDraws {
    private VoiceDraws() {}

    /**
     * One eligible event, in ascending id order.
     *
     * @param id the event id
     * @param weight its weight, ≥ 1
     * @param paramBound the bound of its parameter draw: the known aspects for an insight, the
     *     eligible bargains' total weight for a bargain; 0 draws nothing
     */
    public record Candidate(String id, int weight, int paramBound) {}

    /** How a check ends before its server part runs. */
    public enum Result {
        /** The roll was at or above the chance. */
        QUIET,
        /** Nothing was eligible. */
        NONE,
        /** An event was picked; its server part decides between fired and failed. */
        PICKED
    }

    /**
     * The draws of one check.
     *
     * @param roll the roll, 0–999, drawn even for a forced check
     * @param result quiet, none or picked
     * @param pick the pick, 0 to {@code of − 1}; −1 unless picked
     * @param of the eligible events' total weight; 0 unless picked
     * @param index the picked candidate's index; −1 unless picked
     * @param cue the cue seed; 0 unless picked
     * @param param the parameter draw; −1 when none was drawn
     */
    public record Draw(int roll, Result result, int pick, int of, int index, long cue, int param) {}

    /** The check's {@code Random} seed: {@code fold([VOIC, msb, lsb, checks])}. */
    public static long seed(UUID player, long checks) {
        return Hashes.fold(
                Salts.word(Salts.VOIC),
                player.getMostSignificantBits(),
                player.getLeastSignificantBits(),
                checks);
    }

    /**
     * Draws one check.
     *
     * @param pm the chance in per mille
     * @param forced the Listen rite's check: the roll is drawn and ignored
     * @param eligible the eligible events in ascending id order
     */
    public static Draw draw(
            UUID player, long checks, int pm, boolean forced, List<Candidate> eligible) {
        Random random = new Random(seed(player, checks));
        int roll = random.nextInt(1000);
        if (!forced && roll >= pm) {
            return new Draw(roll, Result.QUIET, -1, 0, -1, 0, -1);
        }
        int of = 0;
        for (Candidate c : eligible) {
            of += c.weight();
        }
        if (of == 0) {
            return new Draw(roll, Result.NONE, -1, 0, -1, 0, -1);
        }
        int pick = random.nextInt(of);
        int index = 0;
        for (int running = eligible.get(0).weight(); pick >= running; ) {
            running += eligible.get(++index).weight();
        }
        long cue = random.nextLong();
        int bound = eligible.get(index).paramBound();
        int param = bound > 0 ? random.nextInt(bound) : -1;
        return new Draw(roll, Result.PICKED, pick, of, index, cue, param);
    }

    /**
     * The eligible events in ascending id order ("Selection and draws", step 2): not disabled, the
     * band within the event's, in context if it asks, a bargain only with no live offer and an
     * eligible bargain, an insight only with a known aspect.
     *
     * @param events the {@code vftv:voice_event} entries by id
     * @param disabled the ids of events whose type is switched off
     * @param context whether the player is in context, read only if an event asks
     * @param offer whether a live offer stands
     * @param known how many aspects the player knows
     * @param bargainWeight the eligible bargains' total weight, 0 with none
     */
    public static List<Candidate> eligible(
            SortedMap<String, VoiceContent.Event> events,
            Set<String> disabled,
            int band,
            BooleanSupplier context,
            boolean offer,
            int known,
            int bargainWeight) {
        List<Candidate> out = new ArrayList<>();
        Boolean inContext = null;
        for (Map.Entry<String, VoiceContent.Event> entry : events.entrySet()) {
            VoiceContent.Event e = entry.getValue();
            if (disabled.contains(entry.getKey()) || band < e.minBand() || band > e.maxBand()) {
                continue;
            }
            if (e.context()) {
                if (inContext == null) {
                    inContext = context.getAsBoolean();
                }
                if (!inContext) {
                    continue;
                }
            }
            int bound =
                    switch (e.type()) {
                        case VoiceContent.BARGAIN -> offer ? 0 : bargainWeight;
                        case VoiceContent.INSIGHT -> known;
                        default -> -1;
                    };
            if (bound == 0) {
                continue;
            }
            out.add(new Candidate(entry.getKey(), e.weight(), Math.max(0, bound)));
        }
        return out;
    }

    /** The index a draw {@code r} picks over cumulative {@code weights}. */
    public static int weighted(int r, int[] weights) {
        int running = 0;
        for (int i = 0; i < weights.length; i++) {
            running += weights[i];
            if (r < running) {
                return i;
            }
        }
        throw new IllegalArgumentException("draw " + r + " beyond the weights");
    }
}
