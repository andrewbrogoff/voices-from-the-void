package com.andrewbrogoff.vftv.neoforge.research;

import com.andrewbrogoff.vftv.api.neoforge.aspect.AspectDiscoveredEvent;
import com.andrewbrogoff.vftv.api.neoforge.aspect.ThingScannedEvent;
import com.andrewbrogoff.vftv.api.neoforge.research.ResearchCapabilities;
import com.andrewbrogoff.vftv.api.neoforge.research.ResearchLearnedEvent;
import com.andrewbrogoff.vftv.api.neoforge.research.StageReachedEvent;
import com.andrewbrogoff.vftv.api.neoforge.warp.WarpChangedEvent;
import com.andrewbrogoff.vftv.api.warp.WarpState;
import com.andrewbrogoff.vftv.core.aspect.AspectSet;
import com.andrewbrogoff.vftv.core.common.TickBudget;
import com.andrewbrogoff.vftv.core.research.ResearchEvents;
import com.andrewbrogoff.vftv.core.research.ResearchService;
import com.andrewbrogoff.vftv.core.research.Resolver;
import com.andrewbrogoff.vftv.core.research.model.Evidence;
import com.andrewbrogoff.vftv.neoforge.common.NeoForgeRegistrar;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.EntityTypes;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.resource.VanillaServerListeners;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

/**
 * The research feature's NeoForge wiring, called once from the {@code @Mod} constructor: game-bus
 * and mod-bus listeners, capabilities and data-map types. Each listener calls {@code core} in one
 * line.
 */
public final class ResearchNeoForge {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Identifier VALIDATION =
            Identifier.fromNamespaceAndPath("vftv", "research_validation");

    /** The warp feature's tuning listener, whose record the milestone rules read at open. */
    private static final Identifier WARP_TUNING =
            Identifier.fromNamespaceAndPath("vftv", "warp_tuning");

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
        ResearchEvents.install(
                p -> post(new ResearchLearnedEvent(p)), p -> post(new StageReachedEvent(p)));
        // Load validation when a world opens, after the advancements and the warp tuning load;
        // the registries are fixed while it is open, so a /reload runs nothing.
        gameBus.addListener(
                AddServerReloadListenersEvent.class,
                event -> {
                    if (ServerLifecycleHooks.getCurrentServer() == null) {
                        event.addListener(VALIDATION, validation(event.getServerResources()));
                        event.addDependency(VanillaServerListeners.ADVANCEMENTS, VALIDATION);
                        event.addDependency(WARP_TUNING, VALIDATION);
                    }
                });
        // The triggers (research spec, "Triggers"): each sibling event is evidence.
        gameBus.addListener(
                AdvancementEvent.AdvancementEarnEvent.class,
                event -> {
                    if (event.getEntity() instanceof ServerPlayer player) {
                        evidence(
                                player,
                                new Evidence.Advancement(event.getAdvancement().id().toString()));
                    }
                });
        gameBus.addListener(
                ThingScannedEvent.class,
                event -> {
                    String key = event.payload().key().toString();
                    evidence(
                            event.payload().player(),
                            new Evidence.Scanned(key, tag -> Resolver.inTag(key, tag)));
                });
        gameBus.addListener(
                AspectDiscoveredEvent.class,
                event ->
                        evidence(
                                event.payload().player(),
                                new Evidence.Discovered(AspectSet.text(event.payload().aspect()))));
        gameBus.addListener(
                WarpChangedEvent.class,
                event -> {
                    WarpState after = event.payload().after();
                    evidence(
                            event.payload().player(),
                            new Evidence.Warp(after.total(), after.lasting() + after.permanent()));
                });
        gameBus.addListener(
                PlayerEvent.PlayerLoggedInEvent.class,
                event -> {
                    if (event.getEntity() instanceof ServerPlayer player) {
                        try (var _ = TickBudget.enter("research")) {
                            Resolver.login(player);
                        }
                    }
                });
        gameBus.addListener(
                PlayerEvent.PlayerLoggedOutEvent.class,
                event -> {
                    if (event.getEntity() instanceof ServerPlayer player) {
                        Resolver.logout(player);
                    }
                });
        gameBus.addListener(
                ServerTickEvent.Post.class,
                event -> {
                    try (var _ = TickBudget.enter("research")) {
                        Resolver.drainDeferred();
                    }
                });
        gameBus.addListener(
                ServerStoppedEvent.class,
                event -> {
                    Resolver.onServerStopped();
                    ResearchService.onServerStopped();
                });
    }

    private static void evidence(ServerPlayer player, Evidence evidence) {
        try (var _ = TickBudget.enter("research")) {
            Resolver.evidence(player, evidence);
        }
    }

    /** Posts an event; a throwing listener is logged and the committed state stands. */
    private static void post(Event event) {
        try {
            NeoForge.EVENT_BUS.post(event);
        } catch (RuntimeException e) {
            LOGGER.error("A listener of {} threw; the state stands", event.getClass().getName(), e);
        }
    }

    private static ResourceManagerReloadListener validation(ReloadableServerResources resources) {
        return manager ->
                ResearchService.validateAtOpen(
                        resources.getRegistryLookup(),
                        id -> resources.getAdvancements().get(id) != null);
    }
}
