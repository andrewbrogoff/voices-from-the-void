package com.andrewbrogoff.vftv.core.vis;

import com.andrewbrogoff.vftv.api.vis.VisLimits;
import com.andrewbrogoff.vftv.core.vis.model.Reconcile;
import com.andrewbrogoff.vftv.core.vis.model.Regen;
import com.andrewbrogoff.vftv.core.vis.model.VisTuning;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;

/**
 * A node's state (wands spec, "Node state"), held by the node block entity: {@code {"format": 1,
 * …}}. The codec checks format bounds only; tuning is reconciled after decoding, at settlement.
 *
 * @param id the stable identity, or 0 while unset
 * @param origin how it was born
 * @param type its type id, kept as written even when the type is missing
 * @param clarity its clarity
 * @param aspects its aspects, ascending id, 1 to 6
 * @param regen the regeneration clock and the parameters in force since it
 * @param cultivation progress toward the next cultivation step
 */
public record NodeState(
        long id,
        Origin origin,
        Identifier type,
        Clarity clarity,
        List<NodeAspect> aspects,
        RegenClock regen,
        Cultivation cultivation) {
    /** The only format this build reads and writes. */
    public static final int FORMAT = 1;

    /** How a node was born. */
    public enum Origin implements StringRepresentable {
        /** Natural spread. */
        NATURAL,
        /** A structure template. */
        STRUCTURE,
        /** The starter. */
        STARTER;

        static final Codec<Origin> CODEC = StringRepresentable.fromEnum(Origin::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        /** The model's origin. */
        public Reconcile.Origin model() {
            return Reconcile.Origin.valueOf(name());
        }
    }

    /** A node's clarity: how brightly it rings. */
    public enum Clarity implements StringRepresentable {
        /** Faint. */
        FAINT,
        /** Steady. */
        STEADY,
        /** Clear. */
        CLEAR;

        static final Codec<Clarity> CODEC = StringRepresentable.fromEnum(Clarity::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        /** Its index in the order the weights and tuning name them: faint 0, steady 1, clear 2. */
        public int index() {
            return switch (this) {
                case FAINT -> 0;
                case STEADY -> 1;
                case CLEAR -> 2;
            };
        }

        /** The clarity at an index: faint 0, steady 1, clear 2. */
        public static Clarity byIndex(int index) {
            return switch (index) {
                case 0 -> FAINT;
                case 1 -> STEADY;
                default -> CLEAR;
            };
        }

        /** Its lang key, {@code node.vftv.clarity.<name>}. */
        public String langKey() {
            return "node.vftv.clarity." + getSerializedName();
        }
    }

    /**
     * One aspect of a node.
     *
     * @param aspect the aspect id, kept as written
     * @param max its capacity, 0 to 1000
     * @param current its amount, 0 to max
     */
    public record NodeAspect(Identifier aspect, int max, int current) {
        static final Codec<NodeAspect> CODEC =
                RecordCodecBuilder.<NodeAspect>create(
                                i ->
                                        i.group(
                                                        Identifier.CODEC
                                                                .fieldOf("aspect")
                                                                .forGetter(NodeAspect::aspect),
                                                        ExtraCodecs.intRange(
                                                                        0, VisLimits.MAX_AMOUNT)
                                                                .fieldOf("max")
                                                                .forGetter(NodeAspect::max),
                                                        ExtraCodecs.intRange(
                                                                        0, VisLimits.MAX_AMOUNT)
                                                                .fieldOf("current")
                                                                .forGetter(NodeAspect::current))
                                                .apply(i, NodeAspect::new))
                        .validate(
                                a ->
                                        a.current() <= a.max()
                                                ? DataResult.success(a)
                                                : DataResult.error(() -> "current above max"));
    }

    /**
     * The regeneration clock.
     *
     * @param tick the game time it was last settled at
     * @param interval the interval in force since {@code tick}, 1 to 24,000
     * @param divisor the divisor in force since {@code tick}, 1 to 1,000
     */
    public record RegenClock(long tick, int interval, int divisor) {
        static final Codec<RegenClock> CODEC =
                RecordCodecBuilder.create(
                        i ->
                                i.group(
                                                Codec.LONG
                                                        .fieldOf("tick")
                                                        .forGetter(RegenClock::tick),
                                                ExtraCodecs.intRange(1, 24000)
                                                        .fieldOf("interval")
                                                        .forGetter(RegenClock::interval),
                                                ExtraCodecs.intRange(1, 1000)
                                                        .fieldOf("divisor")
                                                        .forGetter(RegenClock::divisor))
                                        .apply(i, RegenClock::new));
    }

    /**
     * Cultivation progress.
     *
     * @param counters per aspect, 0 to 63
     * @param sonus 0 to 4,095
     */
    public record Cultivation(SortedMap<Identifier, Integer> counters, int sonus) {
        /** Nothing yet. */
        public static final Cultivation NONE = new Cultivation(new TreeMap<>(), 0);

        static final Codec<Cultivation> CODEC =
                RecordCodecBuilder.create(
                        i ->
                                i.group(
                                                Codec.unboundedMap(
                                                                Identifier.CODEC,
                                                                ExtraCodecs.intRange(
                                                                        0,
                                                                        VisLimits
                                                                                .MAX_CULTIVATION_COUNTER))
                                                        .xmap(
                                                                m ->
                                                                        (SortedMap<
                                                                                        Identifier,
                                                                                        Integer>)
                                                                                new TreeMap<>(m),
                                                                m -> m)
                                                        .fieldOf("counters")
                                                        .forGetter(Cultivation::counters),
                                                ExtraCodecs.intRange(
                                                                0, VisLimits.MAX_CULTIVATION_SONUS)
                                                        .fieldOf("sonus")
                                                        .forGetter(Cultivation::sonus))
                                        .apply(i, Cultivation::new));

        /** Copies the counters. */
        public Cultivation {
            counters = java.util.Collections.unmodifiableSortedMap(new TreeMap<>(counters));
        }
    }

    /** The bounded codec: format 1, every field required, aspects ascending and unique. */
    public static final Codec<NodeState> BOUNDED =
            VisCodecs.strict(
                    RecordCodecBuilder.<NodeState>create(
                                    i ->
                                            i.group(
                                                            Codec.INT
                                                                    .fieldOf("format")
                                                                    .forGetter(s -> FORMAT),
                                                            Codec.LONG
                                                                    .fieldOf("id")
                                                                    .forGetter(NodeState::id),
                                                            Origin.CODEC
                                                                    .fieldOf("origin")
                                                                    .forGetter(NodeState::origin),
                                                            Identifier.CODEC
                                                                    .fieldOf("type")
                                                                    .forGetter(NodeState::type),
                                                            Clarity.CODEC
                                                                    .fieldOf("clarity")
                                                                    .forGetter(NodeState::clarity),
                                                            NodeAspect.CODEC
                                                                    .listOf(
                                                                            1,
                                                                            VisLimits
                                                                                    .MAX_NODE_ASPECTS)
                                                                    .fieldOf("aspects")
                                                                    .forGetter(NodeState::aspects),
                                                            RegenClock.CODEC
                                                                    .fieldOf("regen")
                                                                    .forGetter(NodeState::regen),
                                                            Cultivation.CODEC
                                                                    .fieldOf("cultivation")
                                                                    .forGetter(
                                                                            NodeState::cultivation))
                                                    .apply(
                                                            i,
                                                            (format, id, o, t, c, a, r, cu) ->
                                                                    format == FORMAT
                                                                            ? new NodeState(
                                                                                    id, o, t, c, a,
                                                                                    r, cu)
                                                                            : unknownFormat(
                                                                                    format)))
                            .validate(NodeState::check),
                    Set.of(
                            "format",
                            "id",
                            "origin",
                            "type",
                            "clarity",
                            "aspects",
                            "regen",
                            "cultivation"));

    /** Copies the aspects. */
    public NodeState {
        aspects = List.copyOf(aspects);
    }

    private static NodeState unknownFormat(int format) {
        throw new IllegalArgumentException("unknown format " + format);
    }

    private static DataResult<NodeState> check(NodeState s) {
        if (s.id < 0) {
            return DataResult.error(() -> "negative id");
        }
        for (int a = 1; a < s.aspects.size(); a++) {
            if (s.aspects
                            .get(a - 1)
                            .aspect()
                            .toString()
                            .compareTo(s.aspects.get(a).aspect().toString())
                    >= 0) {
                return DataResult.error(() -> "aspects not in ascending id order");
            }
        }
        return DataResult.success(s);
    }

    /**
     * A node at birth: every current at its max, counters zero, the clock stamped at {@code now}
     * with the tuning in force.
     */
    public static NodeState born(
            long id,
            Origin origin,
            Identifier type,
            Clarity clarity,
            SortedMap<String, Integer> maxes,
            long now,
            VisTuning tuning) {
        List<NodeAspect> aspects = new ArrayList<>();
        maxes.forEach((a, max) -> aspects.add(new NodeAspect(Identifier.parse(a), max, max)));
        return new NodeState(
                id,
                origin,
                type,
                clarity,
                aspects,
                new RegenClock(now, tuning.regenInterval(clarity.index()), tuning.regenDivisor()),
                Cultivation.NONE);
    }

    /** This state with another id. */
    public NodeState withId(long next) {
        return new NodeState(next, origin, type, clarity, aspects, regen, cultivation);
    }

    /** This state with its clock restamped at {@code now} under the current tuning. */
    public NodeState stamped(long now, VisTuning tuning) {
        return new NodeState(
                id,
                origin,
                type,
                clarity,
                aspects,
                new RegenClock(now, tuning.regenInterval(clarity.index()), tuning.regenDivisor()),
                cultivation);
    }

    /** This state with new aspects. */
    public NodeState withAspects(List<NodeAspect> next) {
        return new NodeState(id, origin, type, clarity, next, regen, cultivation);
    }

    /**
     * Settles regeneration at {@code now}, then rebases: if the stored interval or divisor differs
     * from the tuning for its clarity, the tuning's values are adopted and the clock moves to
     * {@code now}.
     */
    public NodeState settle(long now, VisTuning tuning) {
        int n = aspects.size();
        int[] max = new int[n];
        int[] current = new int[n];
        for (int a = 0; a < n; a++) {
            max[a] = aspects.get(a).max();
            current[a] = aspects.get(a).current();
        }
        long tick =
                Regen.settle(max, current, regen.tick(), now, regen.interval(), regen.divisor());
        List<NodeAspect> next = new ArrayList<>(n);
        for (int a = 0; a < n; a++) {
            next.add(new NodeAspect(aspects.get(a).aspect(), max[a], current[a]));
        }
        int interval = tuning.regenInterval(clarity.index());
        int divisor = tuning.regenDivisor();
        RegenClock clock =
                interval != regen.interval() || divisor != regen.divisor()
                        ? new RegenClock(now, interval, divisor)
                        : new RegenClock(tick, regen.interval(), regen.divisor());
        return new NodeState(id, origin, type, clarity, next, clock, cultivation);
    }

    /** The aspects by id text. */
    public Map<String, NodeAspect> byAspect() {
        Map<String, NodeAspect> out = new TreeMap<>();
        aspects.forEach(a -> out.put(a.aspect().toString(), a));
        return out;
    }
}
