package com.andrewbrogoff.vftv.core.vis;

import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.api.vis.VisKeys;
import com.andrewbrogoff.vftv.core.aspect.Strikes;
import com.andrewbrogoff.vftv.core.common.Platform;
import com.andrewbrogoff.vftv.core.research.FactSource;
import com.andrewbrogoff.vftv.core.vis.model.VisRegistryRules;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * The vis feature's entry into {@code core}: {@code Vftv.init} calls {@link #init} once, during mod
 * construction. It registers the content, the three synced datapack registries, the rules payload,
 * the fork's node source and the book's {@code vis} fact source.
 */
public final class VisFeature {
    private VisFeature() {}

    /** Registers the feature's content, registries, data slots and payloads. */
    public static void init(Platform platform) {
        platform.registrar()
                .dataPackRegistry(
                        VisKeys.WAND_ROD_REGISTRY,
                        WandRodEntry.ELEMENT_CODEC,
                        WandRodEntry.ELEMENT_CODEC);
        platform.registrar()
                .dataPackRegistry(
                        VisKeys.WAND_CAP_REGISTRY,
                        WandCapEntry.ELEMENT_CODEC,
                        WandCapEntry.ELEMENT_CODEC);
        platform.registrar()
                .dataPackRegistry(
                        VisKeys.NODE_TYPE_REGISTRY,
                        NodeTypeEntry.ELEMENT_CODEC,
                        NodeTypeEntry.ELEMENT_CODEC);
        VisContent.register(platform.registrar());
        VisRulesService.init(platform.network());
        Strikes.registerNodeSource(NodeService.SOURCE);
        FactSource.Registry.register("vis", VisFacts.SOURCE);
    }

    /**
     * The world-open load validation of the part and node-type registries (wands spec, "Parts" and
     * "Node types"): every problem, each naming its entry; empty when the world may open.
     */
    public static List<String> validate(HolderLookup.Provider access) {
        WandParts parts = WandParts.of(access);
        Map<String, VisRegistryRules.Part> rods = new HashMap<>();
        Map<String, VisRegistryRules.Part> caps = new HashMap<>();
        parts.rods()
                .forEach(
                        (k, e) ->
                                rods.put(
                                        k.identifier().toString(),
                                        new VisRegistryRules.Part(
                                                e.item().toString(), e.capacity())));
        parts.caps()
                .forEach(
                        (k, e) ->
                                caps.put(
                                        k.identifier().toString(),
                                        new VisRegistryRules.Part(
                                                e.item().toString(), e.costMultiplier())));
        List<String> problems =
                new java.util.ArrayList<>(
                        VisRegistryRules.parts(
                                rods,
                                caps,
                                id ->
                                        BuiltInRegistries.ITEM.containsKey(
                                                net.minecraft.resources.Identifier.parse(id))));
        Map<String, VisRegistryRules.Type> types = new HashMap<>();
        parts.types().forEach((k, e) -> types.put(k.identifier().toString(), e.rules()));
        Set<String> aspects = new HashSet<>();
        access.lookupOrThrow(AspectKeys.REGISTRY)
                .listElements()
                .forEach(h -> aspects.add(h.key().identifier().toString()));
        problems.addAll(VisRegistryRules.types(types, aspects, parts.visAspectIds()));
        return problems;
    }
}
