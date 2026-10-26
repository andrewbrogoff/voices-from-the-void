package com.andrewbrogoff.vftv.neoforge.config.client;

import com.andrewbrogoff.vftv.core.warp.WarpLang;
import com.andrewbrogoff.vftv.core.warp.client.VoicesClient;
import com.andrewbrogoff.vftv.core.warp.client.WarpClientConfig;
import com.andrewbrogoff.vftv.neoforge.common.ConfigKeys;
import java.util.ArrayList;
import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The client keys 7-10 ({@code voices.*}) of {@code vftv-client.toml} (configuration, "Client
 * keys"), and the WarpClientConfig they fill. {@link VftvClientConfig} calls {@link #define} once.
 */
final class WarpClientKeys {
    private WarpClientKeys() {}

    /** Defines the keys on {@code builder} and returns the records that read them. */
    static List<ClientConfigPublisher.ClientRecord<?>> define(ModConfigSpec.Builder builder) {
        builder.translation("options.vftv.voices").push("voices");
        List<ModConfigSpec.ConfigValue<Boolean>> keys = new ArrayList<>();
        WarpLang.VOICE_OPTIONS.forEach(
                (key, comment) ->
                        keys.add(
                                ConfigKeys.bool(
                                        builder,
                                        key,
                                        comment,
                                        "options.vftv.voices." + key,
                                        true)));
        builder.pop();
        return List.of(
                new ClientConfigPublisher.ClientRecord<>(
                        () ->
                                new WarpClientConfig(
                                        keys.get(0).get(),
                                        keys.get(1).get(),
                                        keys.get(2).get(),
                                        keys.get(3).get()),
                        VoicesClient::installConfig));
    }
}
