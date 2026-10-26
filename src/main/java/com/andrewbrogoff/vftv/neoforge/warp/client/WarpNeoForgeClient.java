package com.andrewbrogoff.vftv.neoforge.warp.client;

import com.andrewbrogoff.vftv.api.neoforge.warp.WarpDataMaps;
import com.andrewbrogoff.vftv.api.warp.WarpRules;
import com.andrewbrogoff.vftv.core.warp.StillingDraught;
import com.andrewbrogoff.vftv.core.warp.WarpContent;
import com.andrewbrogoff.vftv.core.warp.WarpRecord;
import com.andrewbrogoff.vftv.core.warp.WarpRulesService;
import com.andrewbrogoff.vftv.core.warp.WarpService;
import com.andrewbrogoff.vftv.core.warp.client.ApparitionRenderer;
import com.andrewbrogoff.vftv.core.warp.client.ColourDrainPass;
import com.andrewbrogoff.vftv.core.warp.client.VoiceHud;
import com.andrewbrogoff.vftv.core.warp.client.VoicesClient;
import com.andrewbrogoff.vftv.core.warp.model.WarpTuning;
import com.andrewbrogoff.vftv.neoforge.text.client.TextNeoForgeClient;
import com.google.gson.JsonObject;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.InterModComms;
import net.neoforged.fml.event.lifecycle.InterModEnqueueEvent;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * The warp feature's client registration, called once from the client wiring on a physical client
 * only: renderers, GUI layers, screens, particles and key mappings, one line each.
 */
public final class WarpNeoForgeClient {
    /** The Voice text's layer, above the status line in the caption band. */
    public static final Identifier VOICE_TEXT =
            Identifier.fromNamespaceAndPath("vftv", "voice_text");

    private WarpNeoForgeClient() {}

    /** Registers the feature's client-side types on the mod bus. */
    public static void register(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(
                ClientPlayerNetworkEvent.LoggingOut.class,
                event -> {
                    WarpRulesService.clearClient();
                    VoicesClient.clear();
                });
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> VoicesClient.tick());
        NeoForge.EVENT_BUS.addListener(
                RenderLevelStageEvent.AfterLevel.class,
                event ->
                        ColourDrainPass.render(
                                VoicesClient.drain(
                                                Minecraft.getInstance()
                                                        .getDeltaTracker()
                                                        .getGameTimeDeltaPartialTick(false))
                                        * Minecraft.getInstance()
                                                .options
                                                .screenEffectScale()
                                                .get()
                                                .floatValue()));
        NeoForge.EVENT_BUS.addListener(
                InputEvent.InteractionKeyMappingTriggered.class,
                event -> {
                    if ((event.isAttack() || event.isUseItem()) && VoicesClient.onInput()) {
                        event.setCanceled(true);
                        event.setSwingHand(false);
                    }
                });
        modBus.addListener(
                RegisterGuiLayersEvent.class,
                event ->
                        event.registerAbove(
                                TextNeoForgeClient.STATUS, VOICE_TEXT, VoiceHud::layer));
        modBus.addListener(
                EntityRenderersEvent.RegisterLayerDefinitions.class,
                event ->
                        event.registerLayerDefinition(
                                ApparitionRenderer.LAYER, ApparitionRenderer.Model::createLayer));
        modBus.addListener(
                EntityRenderersEvent.RegisterRenderers.class,
                event ->
                        event.registerEntityRenderer(
                                WarpContent.apparition(), ApparitionRenderer::new));
        modBus.addListener(
                AddClientReloadListenersEvent.class,
                event ->
                        event.addListener(
                                Identifier.fromNamespaceAndPath("vftv", "voices"),
                                (ResourceManagerReloadListener) VoicesClient::onResources));
        NeoForge.EVENT_BUS.addListener(
                ItemTooltipEvent.class,
                event -> {
                    Integer gear =
                            event.getItemStack().typeHolder().getData(WarpDataMaps.GEAR_WARP);
                    if (gear != null) {
                        event.getToolTip()
                                .add(Component.translatable(WarpService.GEAR_TOOLTIP, gear));
                    }
                    if (event.getItemStack().is(WarpContent.stillingDraught())) {
                        WarpRules rules = WarpRulesService.client();
                        WarpRecord r =
                                event.getEntity() == null
                                        ? null
                                        : WarpService.get(event.getEntity()).readable();
                        event.getToolTip()
                                .add(
                                        Component.translatable(
                                                StillingDraught.TOOLTIP,
                                                rules == null ? "?" : rules.stillAmount(),
                                                r == null ? "?" : r.lasting()));
                    }
                });
        modBus.addListener(
                InterModEnqueueEvent.class,
                event ->
                        InterModComms.sendTo(
                                "mc_agent_harness",
                                "dump_client",
                                () ->
                                        Map.entry(
                                                "vftv:warp_client",
                                                (Function<String, String>)
                                                        WarpNeoForgeClient::dump)));
        modBus.addListener(
                InterModEnqueueEvent.class,
                event ->
                        InterModComms.sendTo(
                                "mc_agent_harness",
                                "dump_client",
                                () ->
                                        Map.entry(
                                                "vftv:voices_client",
                                                (Function<String, String>)
                                                        args -> VoicesClient.dump())));
    }

    /** The client's synced record and rules, as the harness reads them. */
    private static String dump(String args) {
        LocalPlayer player = Minecraft.getInstance().player;
        JsonObject data =
                player == null
                        ? new JsonObject()
                        : WarpRecord.dump(
                                player.getPlainTextName(), WarpService.get(player).readable());
        WarpRules rules = WarpRulesService.client();
        data.addProperty("rulesReceived", rules != null);
        if (rules != null) {
            data.add("rules", WarpTuning.toJson(rules, true));
        }
        return data.toString();
    }
}
