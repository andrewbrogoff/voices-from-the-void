package com.andrewbrogoff.vftv.neoforge.config.client;

import com.andrewbrogoff.vftv.core.hush.client.HushClientConfig;
import com.andrewbrogoff.vftv.core.hush.client.HushHud;
import com.andrewbrogoff.vftv.neoforge.common.ConfigKeys;
import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The client keys 5-6 ({@code hush.muffle}, {@code hush.hud}) of {@code vftv-client.toml}
 * (configuration, "Client keys"), and the HushClientConfig they fill. {@link VftvClientConfig}
 * calls {@link #define} once.
 */
final class HushClientKeys {
    private HushClientKeys() {}

    /** Defines the keys on {@code builder} and returns the records that read them. */
    static List<ClientConfigPublisher.ClientRecord<?>> define(ModConfigSpec.Builder builder) {
        builder.translation("options.vftv.hush").push("hush");
        ModConfigSpec.ConfigValue<Boolean> muffle =
                ConfigKeys.bool(
                        builder,
                        "muffle",
                        HushClientConfig.MUFFLE_COMMENT,
                        "options.vftv.hush.muffle",
                        HushClientConfig.DEFAULT.muffle());
        ModConfigSpec.ConfigValue<Boolean> hud =
                ConfigKeys.bool(
                        builder,
                        "hud",
                        HushClientConfig.HUD_COMMENT,
                        "options.vftv.hush.hud",
                        HushClientConfig.DEFAULT.hud());
        builder.pop();
        return List.of(
                new ClientConfigPublisher.ClientRecord<>(
                        () -> new HushClientConfig(muffle.get(), hud.get()),
                        HushHud::installConfig));
    }
}
