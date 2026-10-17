package com.andrewbrogoff.vftv.core.vis.model;

import com.andrewbrogoff.vftv.api.vis.VisLimits;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * The world-open load validation of the part and node-type registries (wands spec, "Parts" and
 * "Node types"): every rule that needs more than one entry, or another registry. Each problem names
 * its entry; any problem refuses the world.
 */
public final class VisRegistryRules {
    /** The required built-in rods. */
    public static final List<String> REQUIRED_RODS = List.of("vftv:wooden", "vftv:chime");

    /** The required built-in caps. */
    public static final List<String> REQUIRED_CAPS = List.of("vftv:copper", "vftv:resonant_brass");

    /** The required built-in node type. */
    public static final String WELLSPRING = "vftv:wellspring";

    /** The six built-in primals the Wellspring holds. */
    public static final List<String> BUILT_IN_PRIMALS =
            List.of(
                    "vftv:aer",
                    "vftv:aqua",
                    "vftv:ignis",
                    "vftv:ordo",
                    "vftv:perditio",
                    "vftv:terra");

    private VisRegistryRules() {}

    /** One part: its ingredient item and its value (capacity or cost multiplier). */
    public record Part(String item, double value) {}

    /** One node type's aspect: its id and rolled range. */
    public record TypeAspect(String aspect, int min, int max) {}

    /**
     * One node type, as the registry holds it.
     *
     * @param aspects its aspects
     * @param mainAspect its main aspect
     * @param clarity its clarity weights: faint, steady, clear
     * @param natural whether it spreads naturally
     * @param biomes its biome tags (empty when not natural)
     * @param weight its spread weight, or null
     * @param anchored whether it cannot be captured
     */
    public record Type(
            List<TypeAspect> aspects,
            String mainAspect,
            List<Integer> clarity,
            boolean natural,
            List<String> biomes,
            @Nullable Integer weight,
            boolean anchored) {}

    /** The problems of a rod value (capacity 1 to 1000). */
    public static @Nullable String rodValue(int capacity) {
        return capacity < VisLimits.MIN_ROD_CAPACITY || capacity > VisLimits.MAX_ROD_CAPACITY
                ? "capacity " + capacity + " outside 1..1000"
                : null;
    }

    /** The problems of a cap value (0.25 to 2.00, at most 2 decimal places). */
    public static @Nullable String capValue(double multiplier) {
        if (!(multiplier >= VisLimits.MIN_COST_MULTIPLIER
                && multiplier <= VisLimits.MAX_COST_MULTIPLIER)) {
            return "cost_multiplier " + multiplier + " outside 0.25..2.00";
        }
        if (BigDecimal.valueOf(multiplier).stripTrailingZeros().scale() > 2) {
            return "cost_multiplier " + multiplier + " has more than 2 decimal places";
        }
        return null;
    }

    /**
     * The part registries' problems: an unknown item or air; one item used by two parts across both
     * registries; a value out of range; a required key missing.
     */
    public static List<String> parts(
            Map<String, Part> rods, Map<String, Part> caps, Predicate<String> itemExists) {
        List<String> problems = new ArrayList<>();
        Map<String, String> itemOwner = new HashMap<>();
        check(rods, "wand_rod", true, itemExists, itemOwner, problems);
        check(caps, "wand_cap", false, itemExists, itemOwner, problems);
        REQUIRED_RODS.stream()
                .filter(k -> !rods.containsKey(k))
                .forEach(k -> problems.add("vftv:wand_rod " + k + ": required, missing"));
        REQUIRED_CAPS.stream()
                .filter(k -> !caps.containsKey(k))
                .forEach(k -> problems.add("vftv:wand_cap " + k + ": required, missing"));
        return problems;
    }

    private static void check(
            Map<String, Part> parts,
            String registry,
            boolean rod,
            Predicate<String> itemExists,
            Map<String, String> itemOwner,
            List<String> problems) {
        new java.util.TreeMap<>(parts)
                .forEach(
                        (id, part) -> {
                            String where = "vftv:" + registry + " " + id + ": ";
                            if (part.item().equals("minecraft:air")
                                    || !itemExists.test(part.item())) {
                                problems.add(where + "unknown item or air " + part.item());
                            }
                            String other = itemOwner.putIfAbsent(part.item(), where.strip());
                            if (other != null) {
                                problems.add(where + "item " + part.item() + " is also " + other);
                            }
                            String value =
                                    rod ? rodValue((int) part.value()) : capValue(part.value());
                            if (value != null) {
                                problems.add(where + value);
                            }
                        });
    }

    /**
     * The node-type registry's problems.
     *
     * @param types every type by id
     * @param aspects every aspect id in the loaded registry
     * @param visAspects the vis aspects (exposed primals)
     */
    public static List<String> types(
            Map<String, Type> types, Set<String> aspects, Set<String> visAspects) {
        List<String> problems = new ArrayList<>();
        if (visAspects.size() > VisLimits.MAX_VIS_ASPECTS) {
            problems.add(
                    "vftv:aspect: "
                            + visAspects.size()
                            + " exposed primals, more than "
                            + VisLimits.MAX_VIS_ASPECTS);
        }
        new java.util.TreeMap<>(types)
                .forEach(
                        (id, type) -> {
                            String where = "vftv:node_type " + id + ": ";
                            type(type, aspects).forEach(p -> problems.add(where + p));
                            if (id.equals(WELLSPRING)) {
                                wellspring(type, visAspects).forEach(p -> problems.add(where + p));
                            }
                        });
        if (!types.containsKey(WELLSPRING)) {
            problems.add("vftv:node_type " + WELLSPRING + ": required, missing");
        }
        return problems;
    }

    /** One type's own rules. */
    public static List<String> type(Type type, Set<String> aspects) {
        List<String> problems = new ArrayList<>();
        if (type.aspects().isEmpty() || type.aspects().size() > VisLimits.MAX_NODE_ASPECTS) {
            problems.add(type.aspects().size() + " aspects, outside 1..6");
        }
        Set<String> seen = new HashSet<>();
        for (TypeAspect a : type.aspects()) {
            if (!aspects.contains(a.aspect())) {
                problems.add("unknown aspect " + a.aspect());
            }
            if (!seen.add(a.aspect())) {
                problems.add("aspect " + a.aspect() + " twice");
            }
            if (!(1 <= a.min() && a.min() <= a.max() && a.max() <= VisLimits.MAX_AMOUNT)) {
                problems.add("range of " + a.aspect() + " outside 1 <= min <= max <= 1000");
            }
        }
        if (!seen.contains(type.mainAspect())) {
            problems.add("main_aspect " + type.mainAspect() + " is not among its aspects");
        }
        int sum = 0;
        for (int w : type.clarity()) {
            if (w < 0 || w > 1000) {
                problems.add("clarity weight " + w + " outside 0..1000");
            }
            sum += w;
        }
        if (type.clarity().size() != 3 || sum <= 0) {
            problems.add("clarity weights must be three and sum above 0");
        }
        if (type.natural()) {
            if (type.biomes().isEmpty() || type.biomes().size() > 8 || type.weight() == null) {
                problems.add("a natural type needs 1..8 biomes and a weight");
            }
        } else if (!type.biomes().isEmpty() || type.weight() != null) {
            problems.add("biomes or weight without natural");
        }
        Integer weight = type.weight();
        if (weight != null && (weight < 1 || weight > 1000)) {
            problems.add("weight " + weight + " outside 1..1000");
        }
        return problems;
    }

    /**
     * The Wellspring's rules: exactly the six built-in primals, each at [32, 32] and still a vis
     * aspect; anchored, not natural, with weight only on clear.
     */
    public static List<String> wellspring(Type type, Set<String> visAspects) {
        List<String> problems = new ArrayList<>();
        Set<String> held = new HashSet<>();
        for (TypeAspect a : type.aspects()) {
            held.add(a.aspect());
            if (a.min() != 32 || a.max() != 32) {
                problems.add("Wellspring aspect " + a.aspect() + " must have range [32, 32]");
            }
        }
        if (!held.equals(new HashSet<>(BUILT_IN_PRIMALS)) || type.aspects().size() != 6) {
            problems.add("the Wellspring must hold exactly the six built-in primals");
        }
        BUILT_IN_PRIMALS.stream()
                .filter(p -> !visAspects.contains(p))
                .forEach(p -> problems.add(p + " is no longer a vis aspect"));
        if (!type.anchored() || type.natural()) {
            problems.add("the Wellspring must be anchored and not natural");
        }
        if (type.clarity().size() != 3
                || type.clarity().get(0) != 0
                || type.clarity().get(1) != 0
                || type.clarity().get(2) <= 0) {
            problems.add("the Wellspring's clarity weight must be on clear only");
        }
        return problems;
    }
}
