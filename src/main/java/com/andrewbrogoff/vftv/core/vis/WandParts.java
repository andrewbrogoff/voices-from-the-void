package com.andrewbrogoff.vftv.core.vis;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.api.vis.NodeType;
import com.andrewbrogoff.vftv.api.vis.VisKeys;
import com.andrewbrogoff.vftv.api.vis.WandCap;
import com.andrewbrogoff.vftv.api.vis.WandRod;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

/**
 * One loaded set of the vis registries as the wand reads them: the parts by key and by item, the
 * node types, and the vis aspects (exposed primals, ascending id). The registries are fixed while a
 * world is open, so the last set built is kept: crafting's {@code assemble}, the recipe display and
 * the item bar get no registry access and read {@link #last()}.
 *
 * @param rods the rods by key, ascending
 * @param caps the caps by key, ascending
 * @param rodByItem each rod's key by its item
 * @param capByItem each cap's key by its item
 * @param types the node types by key
 * @param visAspects the vis aspects, ascending id
 */
public record WandParts(
        Map<ResourceKey<WandRod>, WandRodEntry> rods,
        Map<ResourceKey<WandCap>, WandCapEntry> caps,
        Map<Item, ResourceKey<WandRod>> rodByItem,
        Map<Item, ResourceKey<WandCap>> capByItem,
        Map<ResourceKey<NodeType>, NodeTypeEntry> types,
        List<ResourceKey<Aspect>> visAspects) {
    /** No registries loaded. */
    public static final WandParts EMPTY =
            new WandParts(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), List.of());

    private static volatile WandParts last = EMPTY;
    private static final Map<HolderLookup.Provider, WandParts> SETS = new java.util.WeakHashMap<>();

    /** Copies the collections. */
    public WandParts {
        rods = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(rods));
        caps = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(caps));
        rodByItem = Map.copyOf(rodByItem);
        capByItem = Map.copyOf(capByItem);
        types = Map.copyOf(types);
        visAspects = List.copyOf(visAspects);
    }

    /** The last set built, or {@link #EMPTY}. */
    public static WandParts last() {
        return last;
    }

    /** The set of a registry access, rebuilt only when the access instance changes. */
    public static synchronized WandParts of(HolderLookup.Provider access) {
        // One set per access: an integrated client and its server hold different instances.
        WandParts parts = SETS.get(access);
        if (parts == null) {
            parts = build(access);
            SETS.put(access, parts);
        }
        last = parts;
        return parts;
    }

    /** Forgets the cached sets, at disconnect or server stop. */
    public static synchronized void clear() {
        last = EMPTY;
        SETS.clear();
    }

    private static WandParts build(HolderLookup.Provider access) {
        Map<ResourceKey<WandRod>, WandRodEntry> rods = new TreeMap<>();
        Map<ResourceKey<WandCap>, WandCapEntry> caps = new TreeMap<>();
        Map<Item, ResourceKey<WandRod>> rodByItem = new LinkedHashMap<>();
        Map<Item, ResourceKey<WandCap>> capByItem = new LinkedHashMap<>();
        Map<ResourceKey<NodeType>, NodeTypeEntry> types = new TreeMap<>();
        access.lookup(VisKeys.WAND_ROD_REGISTRY)
                .ifPresent(
                        r ->
                                r.listElements()
                                        .forEach(
                                                h -> {
                                                    if (h.value() instanceof WandRodEntry e) {
                                                        rods.put(h.key(), e);
                                                    }
                                                }));
        access.lookup(VisKeys.WAND_CAP_REGISTRY)
                .ifPresent(
                        r ->
                                r.listElements()
                                        .forEach(
                                                h -> {
                                                    if (h.value() instanceof WandCapEntry e) {
                                                        caps.put(h.key(), e);
                                                    }
                                                }));
        access.lookup(VisKeys.NODE_TYPE_REGISTRY)
                .ifPresent(
                        r ->
                                r.listElements()
                                        .forEach(
                                                h -> {
                                                    if (h.value() instanceof NodeTypeEntry e) {
                                                        types.put(h.key(), e);
                                                    }
                                                }));
        rods.forEach(
                (k, e) ->
                        BuiltInRegistries.ITEM
                                .getOptional(e.item())
                                .ifPresent(i -> rodByItem.putIfAbsent(i, k)));
        caps.forEach(
                (k, e) ->
                        BuiltInRegistries.ITEM
                                .getOptional(e.item())
                                .ifPresent(i -> capByItem.putIfAbsent(i, k)));
        List<ResourceKey<Aspect>> vis = new ArrayList<>();
        access.lookup(AspectKeys.REGISTRY)
                .ifPresent(
                        r ->
                                r.listElements()
                                        .forEach(
                                                h -> {
                                                    if (h.value().parents().isEmpty()
                                                            && h.value().hiddenPrimal() == null) {
                                                        vis.add(h.key());
                                                    }
                                                }));
        vis.sort(null);
        return new WandParts(rods, caps, rodByItem, capByItem, types, vis);
    }

    /** The vis aspects as id texts. */
    public Set<String> visAspectIds() {
        return visAspects.stream()
                .map(a -> a.identifier().toString())
                .collect(Collectors.toCollection(java.util.TreeSet::new));
    }

    /** Whether an aspect id is a vis aspect. */
    public boolean isVis(String aspect) {
        return visAspects.stream().anyMatch(a -> a.identifier().toString().equals(aspect));
    }
}
