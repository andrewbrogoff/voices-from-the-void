package com.andrewbrogoff.vftv.core.infusion;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.api.flux.FluxRules;
import com.andrewbrogoff.vftv.api.infusion.InfusionLimits;
import com.andrewbrogoff.vftv.api.research.Research;
import com.andrewbrogoff.vftv.api.research.ResearchKeys;
import com.andrewbrogoff.vftv.api.vis.VisKind;
import com.andrewbrogoff.vftv.core.flux.FluxRulesCodecs;
import com.andrewbrogoff.vftv.core.infusion.model.InfusionMath;
import com.andrewbrogoff.vftv.core.infusion.model.RunRules;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * A run's whole plan and its counters (infusion spec, "The plan"), written complete by the commit.
 * A run never reads its recipe, the tags or the tuning again; it is validated only against its own
 * stored rules. Mutable counters change only on the server thread inside a run operation.
 */
public final class InfusionRun {
    /** One component in absorb order: its pedestal's offset and the promised count-one stack. */
    public record Part(int dx, int dz, ItemStack promised) {
        /** Copies the stack. */
        public Part {
            promised = promised.copy();
        }
    }

    /**
     * The frozen stability.
     *
     * @param r the recipe's instability, with the enchanting term
     * @param lonePedestals {@code L}
     * @param loneItems {@code I}
     * @param matched {@code P}
     * @param bonus {@code B}
     * @param warp {@code Wp}
     * @param s {@code S}
     * @param chance {@code p}, per mille
     */
    public record Stability(
            int r,
            int lonePedestals,
            int loneItems,
            int matched,
            int bonus,
            int warp,
            int s,
            int chance) {}

    /**
     * An armed abandon.
     *
     * @param player who armed it
     * @param until the deadline, in game time
     * @param fluxGeneration the flux rules' generation when it was armed
     */
    public record Abandon(UUID player, long until, long fluxGeneration) {}

    final int seq;
    final long seed;
    final UUID starter;
    final Identifier recipe;
    final ResourceKey<Research> research;
    final VisKind kind;
    final ItemStack output;
    final List<Part> parts;
    final boolean[] done;
    final List<ResourceKey<Aspect>> aspects;
    final int[] costs;
    final int[] drawn;
    final Stability stability;
    final RunRules rules;
    final @Nullable FluxRules fluxRules;
    int rolls;
    int upsets;
    @Nullable Abandon abandon;

    InfusionRun(
            int seq,
            long seed,
            UUID starter,
            Identifier recipe,
            ResourceKey<Research> research,
            VisKind kind,
            ItemStack output,
            List<Part> parts,
            boolean[] done,
            List<ResourceKey<Aspect>> aspects,
            int[] costs,
            int[] drawn,
            Stability stability,
            RunRules rules,
            @Nullable FluxRules fluxRules,
            int rolls,
            int upsets,
            @Nullable Abandon abandon) {
        this.seq = seq;
        this.seed = seed;
        this.starter = starter;
        this.recipe = recipe;
        this.research = research;
        this.kind = kind;
        this.output = output.copy();
        this.parts = List.copyOf(parts);
        this.done = done.clone();
        this.aspects = List.copyOf(aspects);
        this.costs = costs.clone();
        this.drawn = drawn.clone();
        this.stability = stability;
        this.rules = rules;
        this.fluxRules = fluxRules;
        this.rolls = rolls;
        this.upsets = upsets;
        this.abandon = abandon;
    }

    /** {@code seq}, the run's number on its bowl. */
    public int seq() {
        return seq;
    }

    /** The recipe id. */
    public Identifier recipe() {
        return recipe;
    }

    /** The components in absorb order. */
    public List<Part> parts() {
        return parts;
    }

    /** The cost aspects, in ascending id. */
    public List<ResourceKey<Aspect>> aspects() {
        return aspects;
    }

    /** The effective cost of aspect {@code i}. */
    public int cost(int i) {
        return costs[i];
    }

    /** What has been drawn of aspect {@code i}. */
    public int drawn(int i) {
        return drawn[i];
    }

    /** The frozen stability. */
    public Stability stability() {
        return stability;
    }

    /** The frozen rules. */
    public RunRules rules() {
        return rules;
    }

    /** The frozen flux rules, or null. */
    public @Nullable FluxRules fluxRules() {
        return fluxRules;
    }

    /** The upsets so far. */
    public int upsets() {
        return upsets;
    }

    /** Units owed in all. */
    public int owedTotal() {
        int n = 0;
        for (int i = 0; i < costs.length; i++) {
            n += costs[i] - drawn[i];
        }
        return n;
    }

    /** Units drawn in all. */
    public int drawnTotal() {
        int n = 0;
        for (int d : drawn) {
            n += d;
        }
        return n;
    }

    /** Components absorbed. */
    public int absorbed() {
        int n = 0;
        for (boolean d : done) {
            n += d ? 1 : 0;
        }
        return n;
    }

    /** The first pending component's index, or -1. */
    public int firstPending() {
        for (int i = 0; i < done.length; i++) {
            if (!done[i]) {
                return i;
            }
        }
        return -1;
    }

    /** The pending component at offset (dx, dz), or -1. */
    public int pendingAt(int dx, int dz) {
        for (int i = 0; i < parts.size(); i++) {
            if (!done[i] && parts.get(i).dx() == dx && parts.get(i).dz() == dz) {
                return i;
            }
        }
        return -1;
    }

    /** The run's own ceiling: its worst plus {@code max × spill}. */
    public int ceiling() {
        return InfusionMath.ceiling(
                InfusionMath.worst(costs, parts.size()), rules.max(), rules.spill());
    }

    // --- Codec ----------------------------------------------------------------------------

    private static final Codec<Identifier> ID =
            Identifier.CODEC.validate(
                    id ->
                            id.toString().length() <= InfusionLimits.MAX_ID_CHARS
                                    ? DataResult.success(id)
                                    : DataResult.error(() -> "id longer than 256 characters"));

    private static final Codec<ItemStack> ONE =
            ItemStack.CODEC.validate(
                    s ->
                            s.getCount() == 1
                                    ? DataResult.success(s)
                                    : DataResult.error(
                                            () -> "a stack of " + s.getCount() + ", not 1"));

    private record PartState(List<Integer> at, ItemStack item, boolean done) {}

    private static final Codec<PartState> PART =
            RecordCodecBuilder.create(
                    i ->
                            i.group(
                                            Codec.intRange(
                                                            -InfusionMath.RADIUS,
                                                            InfusionMath.RADIUS)
                                                    .listOf(2, 2)
                                                    .fieldOf("at")
                                                    .forGetter(PartState::at),
                                            ONE.fieldOf("item").forGetter(PartState::item),
                                            Codec.BOOL.fieldOf("done").forGetter(PartState::done))
                                    .apply(i, PartState::new));

    private static final Codec<Map<Identifier, Integer>> AMOUNTS =
            Codec.unboundedMap(
                    ID, Codec.intRange(InfusionLimits.MIN_COST, InfusionLimits.MAX_COST));

    private static final Codec<Stability> STABILITY =
            RecordCodecBuilder.create(
                    i ->
                            i.group(
                                            Codec.intRange(0, 100)
                                                    .fieldOf("r")
                                                    .forGetter(Stability::r),
                                            Codec.intRange(0, InfusionLimits.MAX_PEDESTALS)
                                                    .fieldOf("lone_pedestals")
                                                    .forGetter(Stability::lonePedestals),
                                            Codec.intRange(0, InfusionLimits.MAX_PEDESTALS)
                                                    .fieldOf("lone_items")
                                                    .forGetter(Stability::loneItems),
                                            Codec.intRange(0, InfusionMath.CELLS)
                                                    .fieldOf("matched")
                                                    .forGetter(Stability::matched),
                                            Codec.intRange(0, 6400)
                                                    .fieldOf("bonus")
                                                    .forGetter(Stability::bonus),
                                            Codec.intRange(0, InfusionLimits.MAX_WARP_PENALTY)
                                                    .fieldOf("warp")
                                                    .forGetter(Stability::warp),
                                            Codec.intRange(0, 100)
                                                    .fieldOf("s")
                                                    .forGetter(Stability::s),
                                            Codec.intRange(0, 1000)
                                                    .fieldOf("chance")
                                                    .forGetter(Stability::chance))
                                    .apply(i, Stability::new));

    private static final Codec<RunRules> RULES =
            Codec.unboundedMap(Codec.STRING, Codec.INT)
                    .comapFlatMap(InfusionRun::rulesFrom, InfusionRun::rulesTo);

    private static final List<String> RULE_KEYS =
            List.of(
                    "interval",
                    "rate",
                    "lone_pedestal",
                    "lone_item",
                    "per_pair",
                    "max_pairs",
                    "max_chance",
                    "max",
                    "spill",
                    "jolt_weight",
                    "spill_weight");

    private static final Codec<Abandon> ABANDON =
            RecordCodecBuilder.create(
                    i ->
                            i.group(
                                            UUIDUtil.CODEC
                                                    .fieldOf("player")
                                                    .forGetter(Abandon::player),
                                            Codec.LONG.fieldOf("until").forGetter(Abandon::until),
                                            Codec.LONG
                                                    .fieldOf("flux_generation")
                                                    .forGetter(Abandon::fluxGeneration))
                                    .apply(i, Abandon::new));

    private static final Codec<VisKind> KIND =
            Codec.STRING.comapFlatMap(
                    s ->
                            switch (s) {
                                case "ordinary" -> DataResult.success(VisKind.ORDINARY);
                                case "void" -> DataResult.success(VisKind.VOID);
                                default -> DataResult.error(() -> "unknown kind " + s);
                            },
                    k -> k.name().toLowerCase(Locale.ROOT));

    /**
     * The {@code <run>} record: every format bound and cross-field invariant, checked only against
     * the run's own stored rules. Decoding with registry access also requires every aspect and the
     * research entry to exist: a missing one makes the record unreadable (the infusion site
     * difference), so its claims stay unresolved until the pack returns.
     */
    public static final Codec<InfusionRun> CODEC =
            new Codec<InfusionRun>() {
                @Override
                public <T> DataResult<com.mojang.datafixers.util.Pair<InfusionRun, T>> decode(
                        DynamicOps<T> ops, T input) {
                    return RAW.decode(ops, input)
                            .flatMap(p -> checkRegistries(ops, p.getFirst()).map(r -> p));
                }

                @Override
                public <T> DataResult<T> encode(InfusionRun input, DynamicOps<T> ops, T prefix) {
                    return RAW.encode(input, ops, prefix);
                }
            };

    private record Raw(
            int seq,
            long seed,
            UUID starter,
            Identifier recipe,
            Identifier research,
            VisKind kind,
            ItemStack output,
            List<PartState> components,
            Map<Identifier, Integer> costs,
            Map<Identifier, Integer> drawn,
            Stability stability,
            RunRules rules,
            Optional<FluxRules> fluxRules,
            int rolls,
            int upsets,
            Optional<Abandon> abandon) {
        static Raw of(InfusionRun r) {
            List<PartState> parts = new ArrayList<>();
            for (int i = 0; i < r.parts.size(); i++) {
                Part p = r.parts.get(i);
                parts.add(new PartState(List.of(p.dx(), p.dz()), p.promised(), r.done[i]));
            }
            return new Raw(
                    r.seq,
                    r.seed,
                    r.starter,
                    r.recipe,
                    r.research.identifier(),
                    r.kind,
                    r.output,
                    parts,
                    r.amounts(r.costs),
                    r.amounts(r.drawn),
                    r.stability,
                    r.rules,
                    Optional.ofNullable(r.fluxRules),
                    r.rolls,
                    r.upsets,
                    Optional.ofNullable(r.abandon));
        }

        DataResult<InfusionRun> toRun() {
            if (!costs.keySet().containsAll(drawn.keySet())) {
                return DataResult.error(() -> "drawn names an aspect costs doesn't");
            }
            List<ResourceKey<Aspect>> aspects =
                    costs.keySet().stream()
                            .sorted(java.util.Comparator.comparing(Identifier::toString))
                            .map(id -> ResourceKey.create(AspectKeys.REGISTRY, id))
                            .toList();
            int[] c = new int[aspects.size()];
            int[] d = new int[aspects.size()];
            for (int i = 0; i < aspects.size(); i++) {
                Identifier id = aspects.get(i).identifier();
                c[i] = java.util.Objects.requireNonNull(costs.get(id));
                d[i] = drawn.getOrDefault(id, 0);
            }
            List<Part> parts = new ArrayList<>();
            boolean[] done = new boolean[components.size()];
            for (int i = 0; i < components.size(); i++) {
                PartState p = components.get(i);
                parts.add(new Part(p.at().get(0), p.at().get(1), p.item()));
                done[i] = p.done();
            }
            InfusionRun run =
                    new InfusionRun(
                            seq,
                            seed,
                            starter,
                            recipe,
                            ResourceKey.create(ResearchKeys.REGISTRY, research),
                            kind,
                            output,
                            parts,
                            done,
                            aspects,
                            c,
                            d,
                            stability,
                            rules,
                            fluxRules.orElse(null),
                            rolls,
                            upsets,
                            abandon.orElse(null));
            String problem = problem(run);
            return problem == null ? DataResult.success(run) : DataResult.error(() -> problem);
        }
    }

    private static final Codec<InfusionRun> RAW =
            RecordCodecBuilder.<Raw>create(
                            i ->
                                    i.group(
                                                    Codec.intRange(1, InfusionLimits.MAX_RUNS)
                                                            .fieldOf("seq")
                                                            .forGetter(Raw::seq),
                                                    Codec.LONG.fieldOf("seed").forGetter(Raw::seed),
                                                    UUIDUtil.CODEC
                                                            .fieldOf("starter")
                                                            .forGetter(Raw::starter),
                                                    ID.fieldOf("recipe").forGetter(Raw::recipe),
                                                    ID.fieldOf("research").forGetter(Raw::research),
                                                    KIND.fieldOf("kind").forGetter(Raw::kind),
                                                    ONE.fieldOf("output").forGetter(Raw::output),
                                                    PART.listOf(1, InfusionLimits.MAX_COMPONENTS)
                                                            .fieldOf("components")
                                                            .forGetter(Raw::components),
                                                    AMOUNTS.fieldOf("costs").forGetter(Raw::costs),
                                                    AMOUNTS.fieldOf("drawn").forGetter(Raw::drawn),
                                                    STABILITY
                                                            .fieldOf("stability")
                                                            .forGetter(Raw::stability),
                                                    RULES.fieldOf("rules").forGetter(Raw::rules),
                                                    FluxRulesCodecs.CODEC
                                                            .optionalFieldOf("flux_rules")
                                                            .forGetter(Raw::fluxRules),
                                                    Codec.intRange(0, InfusionLimits.MAX_ROLLS)
                                                            .fieldOf("rolls")
                                                            .forGetter(Raw::rolls),
                                                    Codec.intRange(0, 64)
                                                            .fieldOf("upsets")
                                                            .forGetter(Raw::upsets),
                                                    ABANDON.optionalFieldOf("abandon")
                                                            .forGetter(Raw::abandon))
                                            .apply(i, Raw::new))
                    .flatXmap(Raw::toRun, r -> DataResult.success(Raw.of(r)));

    private Map<Identifier, Integer> amounts(int[] values) {
        Map<Identifier, Integer> out = new TreeMap<>();
        for (int i = 0; i < values.length; i++) {
            if (values[i] > 0) {
                out.put(aspects.get(i).identifier(), values[i]);
            }
        }
        return out;
    }

    /** Every cross-field invariant of "Sync and state", against the run's own rules only. */
    static @Nullable String problem(InfusionRun r) {
        if (r.costs.length < 1 || r.costs.length > InfusionLimits.MAX_COST_ENTRIES) {
            return "1-8 costs, not " + r.costs.length;
        }
        for (int i = 0; i < r.costs.length; i++) {
            if (r.drawn[i] > r.costs[i]) {
                return "drawn over cost for " + r.aspects.get(i).identifier();
            }
        }
        Set<Integer> offsets = new HashSet<>();
        for (Part p : r.parts) {
            if (Math.abs(p.dx()) > InfusionMath.RADIUS
                    || Math.abs(p.dz()) > InfusionMath.RADIUS
                    || (p.dx() == 0 && p.dz() == 0)
                    || !offsets.add(p.dx() * 31 + p.dz())) {
                return "a component offset outside the box, on the bowl, or repeated";
            }
        }
        Stability s = r.stability;
        RunRules k = r.rules;
        if (s.bonus() != InfusionMath.bonus(s.matched(), k.maxPairs(), k.perPair())) {
            return "bonus disagrees with matched";
        }
        int stability =
                InfusionMath.stability(
                        InfusionMath.rawStability(
                                s.r(),
                                s.lonePedestals(),
                                s.loneItems(),
                                s.warp(),
                                s.bonus(),
                                k.lonePedestal(),
                                k.loneItem()));
        if (s.s() != stability) {
            return "s disagrees with its inputs";
        }
        if (s.chance() != InfusionMath.chance(s.s(), k.maxChance())) {
            return "chance disagrees with s";
        }
        if (r.upsets > k.max()) {
            return "upsets over max";
        }
        if (r.rolls > r.ceiling()) {
            return "rolls over the run's ceiling";
        }
        return null;
    }

    private static <T> DataResult<InfusionRun> checkRegistries(DynamicOps<T> ops, InfusionRun run) {
        if (!(ops instanceof RegistryOps<T> registryOps)) {
            return DataResult.success(run); // no registries to check against (a unit test)
        }
        var aspects = registryOps.getter(AspectKeys.REGISTRY);
        for (ResourceKey<Aspect> key : run.aspects) {
            if (aspects.flatMap(g -> g.get(key)).isEmpty()) {
                return DataResult.error(() -> "unknown aspect " + key.identifier());
            }
        }
        var research = registryOps.getter(ResearchKeys.REGISTRY);
        if (research.isPresent() && research.get().get(run.research).isEmpty()) {
            return DataResult.error(() -> "unknown research entry " + run.research.identifier());
        }
        return DataResult.success(run);
    }

    private static DataResult<RunRules> rulesFrom(Map<String, Integer> map) {
        if (!map.keySet().equals(new HashSet<>(RULE_KEYS))) {
            return DataResult.error(() -> "run rules fields " + map.keySet());
        }
        try {
            int[] v = RULE_KEYS.stream().mapToInt(map::get).toArray();
            return DataResult.success(
                    new RunRules(
                            v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8], v[9], v[10]));
        } catch (IllegalArgumentException e) {
            return DataResult.error(() -> "run rules " + e.getMessage());
        }
    }

    private static Map<String, Integer> rulesTo(RunRules r) {
        int[] v = {
            r.interval(),
            r.rate(),
            r.lonePedestal(),
            r.loneItem(),
            r.perPair(),
            r.maxPairs(),
            r.maxChance(),
            r.max(),
            r.spill(),
            r.joltWeight(),
            r.spillWeight()
        };
        Map<String, Integer> out = new java.util.LinkedHashMap<>();
        for (int i = 0; i < v.length; i++) {
            out.put(RULE_KEYS.get(i), v[i]);
        }
        return out;
    }
}
