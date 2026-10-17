package com.andrewbrogoff.vftv.neoforge.warp.client;

import com.andrewbrogoff.vftv.api.neoforge.warp.WarpDataMaps;
import com.andrewbrogoff.vftv.api.warp.WarpRules;
import com.andrewbrogoff.vftv.core.warp.WarpRecord;
import com.andrewbrogoff.vftv.core.warp.WarpRulesService;
import com.andrewbrogoff.vftv.core.warp.WarpService;
import com.andrewbrogoff.vftv.core.warp.model.WarpTuning;
import com.google.gson.JsonObject;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.InterModComms;
import net.neoforged.fml.event.lifecycle.InterModEnqueueEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * The warp feature's client registration, called once from the client wiring on a physical client
 * only: renderers, GUI layers, screens, particles and key mappings, one line each.
 */
public final class WarpNeoForgeClient {
    private WarpNeoForgeClient() {}

    /** Registers the feature's client-side types on the mod bus. */
    public static void register(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(
                ClientPlayerNetworkEvent.LoggingOut.class, event -> WarpRulesService.clearClient());
        NeoForge.EVENT_BUS.addListener(
                ItemTooltipEvent.class,
                event -> {
                    Integer gear =
                            event.getItemStack().typeHolder().getData(WarpDataMaps.GEAR_WARP);
                    if (gear != null) {
                        event.getToolTip()
                                .add(Component.translatable(WarpService.GEAR_TOOLTIP, gear));
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
