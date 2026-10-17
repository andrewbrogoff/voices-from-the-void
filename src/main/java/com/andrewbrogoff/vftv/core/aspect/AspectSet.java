package com.andrewbrogoff.vftv.core.aspect;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.api.aspect.HiddenPrimal;
import com.andrewbrogoff.vftv.core.aspect.model.AspectGraph;
import com.andrewbrogoff.vftv.core.aspect.model.Knowledge;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

/**
 * One loaded {@code vftv:aspect} registry as the model sees it: the graph by id text, the tiers in
 * the taught view (discovery order) and each aspect's own bonus. The registry is fixed while a
 * world is open, so the last one built is kept and reused.
 */
public final class AspectSet {
    private static @Nullable AspectSet last;

    private final HolderLookup.RegistryLookup<Aspect> registry;
    private final Map<String, AspectGraph.Node> nodes = new HashMap<>();
    private final Map<String, Integer> bonuses = new HashMap<>();
    private final Map<String, Integer> taughtTiers;

    private AspectSet(HolderLookup.RegistryLookup<Aspect> registry) {
        this.registry = registry;
        registry.listElements()
                .forEach(
                        holder -> {
                            Aspect aspect = holder.value();
                            String id = text(holder.key());
                            HiddenPrimal hidden = aspect.hiddenPrimal();
                            nodes.put(
                                    id,
                                    new AspectGraph.Node(
                                            aspect.parents().stream().map(AspectSet::text).toList(),
                                            hidden == null
                                                    ? List.of()
                                                    : hidden.taughtParents().stream()
                                                            .map(AspectSet::text)
                                                            .toList()));
                            aspect.discoveryBonus().ifPresent(b -> bonuses.put(id, b));
                        });
        List<AspectGraph.Problem> problems = AspectGraph.validate(nodes, List.of());
        taughtTiers =
                problems.stream().anyMatch(AspectGraph.Problem::fatal)
                        ? Map.of()
                        : AspectGraph.tiers(nodes, true);
    }

    /** The set for a registry access's {@code vftv:aspect}. */
    public static AspectSet of(RegistryAccess access) {
        return of(access.lookupOrThrow(AspectKeys.REGISTRY));
    }

    /** The set for a registry, rebuilt only when the registry instance changes. */
    public static synchronized AspectSet of(HolderLookup.RegistryLookup<Aspect> registry) {
        AspectSet set = last;
        if (set == null || !set.registry.equals(registry)) {
            set = new AspectSet(registry);
            last = set;
        }
        return set;
    }

    /** Every rule checked over this registry, with each built-in key required. */
    public List<AspectGraph.Problem> validate() {
        return AspectGraph.validate(
                nodes, BuiltinAspects.ALL.keySet().stream().map(AspectSet::text).toList());
    }

    /** The knowledge rules, with {@code defaultBonus} where an aspect names none. */
    public Knowledge.Rules rules(int defaultBonus) {
        return new Knowledge.Rules() {
            @Override
            public List<String> prerequisites(String aspect) {
                AspectGraph.Node node = nodes.get(aspect);
                // An id the registry lacks (a snapshot outliving a pack) waits on itself: never
                // discovered, never paid, until the pack returns.
                return node == null ? List.of(aspect) : node.prerequisites();
            }

            @Override
            public int order(String aspect) {
                return taughtTiers.getOrDefault(aspect, 0);
            }

            @Override
            public int bonus(String aspect) {
                return bonuses.getOrDefault(aspect, defaultBonus);
            }
        };
    }

    /** An aspect's prerequisites, as keys; empty for a primal or an unknown id. */
    public List<ResourceKey<Aspect>> prerequisites(ResourceKey<Aspect> aspect) {
        AspectGraph.Node node = nodes.get(text(aspect));
        return node == null
                ? List.of()
                : node.prerequisites().stream().map(AspectSet::key).toList();
    }

    /** Whether the registry holds an aspect. */
    public boolean contains(ResourceKey<Aspect> aspect) {
        return nodes.containsKey(text(aspect));
    }

    /** An aspect key's id text, as the model and the ledger hold it. */
    public static String text(ResourceKey<Aspect> key) {
        return key.identifier().toString();
    }

    /** The aspect key of an id text. */
    public static ResourceKey<Aspect> key(String text) {
        return ResourceKey.create(AspectKeys.REGISTRY, Identifier.parse(text));
    }
}
