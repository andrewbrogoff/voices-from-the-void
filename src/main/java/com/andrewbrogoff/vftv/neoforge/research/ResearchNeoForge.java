package com.andrewbrogoff.vftv.neoforge.research;

import com.andrewbrogoff.vftv.api.neoforge.research.ResearchCapabilities;
import com.andrewbrogoff.vftv.core.research.ResearchService;
import com.andrewbrogoff.vftv.neoforge.common.NeoForgeRegistrar;
import net.minecraft.resources.Identifier;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.EntityTypes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.resource.VanillaServerListeners;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * The research feature's NeoForge wiring, called once from the {@code @Mod} constructor: game-bus
 * and mod-bus listeners, capabilities and data-map types. Each listener calls {@code core} in one
 * line.
 */
public final class ResearchNeoForge {
    private static final Identifier VALIDATION =
            Identifier.fromNamespaceAndPath("vftv", "research_validation");

    private ResearchNeoForge() {}

    /** Subscribes the feature's listeners and registers its NeoForge-only types. */
    public static void register(IEventBus modBus, IEventBus gameBus, NeoForgeRegistrar registrar) {
        // The view for players (API spec, "ResearchView and BaffleView"): live for a server player.
        modBus.addListener(
                RegisterCapabilitiesEvent.class,
                event ->
                        event.registerEntity(
                                ResearchCapabilities.VIEW,
                                EntityTypes.PLAYER,
                                (player, context) ->
                                        player instanceof ServerPlayer p
                                                ? ResearchService.view(p)
                                                : null));
        // Load validation when a world opens, after the advancements load; the registries are
        // fixed while it is open, so a /reload runs nothing.
        gameBus.addListener(
                AddServerReloadListenersEvent.class,
                event -> {
                    if (ServerLifecycleHooks.getCurrentServer() == null) {
                        event.addListener(VALIDATION, validation(event.getServerResources()));
                        event.addDependency(VanillaServerListeners.ADVANCEMENTS, VALIDATION);
                    }
                });
    }

    private static ResourceManagerReloadListener validation(ReloadableServerResources resources) {
        return manager ->
                ResearchService.validateAtOpen(
                        resources.getRegistryLookup(),
                        id -> resources.getAdvancements().get(id) != null);
    }
}
