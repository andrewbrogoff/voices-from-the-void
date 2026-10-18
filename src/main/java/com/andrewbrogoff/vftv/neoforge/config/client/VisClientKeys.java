package com.andrewbrogoff.vftv.neoforge.config.client;

import com.andrewbrogoff.vftv.core.vis.VisLang;
import com.andrewbrogoff.vftv.core.vis.client.VisClientConfig;
import com.andrewbrogoff.vftv.core.vis.client.VisHud;
import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The client keys 1-2 ({@code vis_hud.x}, {@code vis_hud.y}) of {@code vftv-client.toml}
 * (configuration, "Client keys"), and the VisClientConfig they fill. {@link VftvClientConfig} calls
 * {@link #define} once.
 */
final class VisClientKeys {
    private VisClientKeys() {}

    /** Defines the keys on {@code builder} and returns the records that read them. */
    static List<ClientConfigPublisher.ClientRecord<?>> define(ModConfigSpec.Builder builder) {
        builder.translation("options.vftv.vis_hud").push("vis_hud");
        ModConfigSpec.IntValue x =
                builder.comment(VisLang.HUD_X_COMMENT)
                        .translation("options.vftv.vis_hud.x")
                        .defineInRange("x", VisClientConfig.DEFAULT.x(), 0, 4096);
        ModConfigSpec.IntValue y =
                builder.comment(VisLang.HUD_Y_COMMENT)
                        .translation("options.vftv.vis_hud.y")
                        .defineInRange("y", VisClientConfig.DEFAULT.y(), 0, 4096);
        builder.pop();
        return List.of(
                new ClientConfigPublisher.ClientRecord<>(
                        () -> new VisClientConfig(x.get(), y.get()), VisHud::installConfig));
    }
}
