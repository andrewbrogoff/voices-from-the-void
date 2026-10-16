package com.andrewbrogoff.vftv.api.warp;

import com.andrewbrogoff.vftv.api.Stable;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * The warp feature's ids: the {@code vftv:voice_event} and {@code vftv:bargain} registries, the
 * {@code vftv:gear_warp} data-map id and the {@code vftv:warp} attachment id. Stable: no id here is
 * removed or renamed within a major. See the warp spec.
 */
@Stable
public final class WarpKeys {
    private WarpKeys() {}

    /** The datapack registry {@code vftv:voice_event} of the Voice events. */
    public static final ResourceKey<Registry<VoiceEvent>> VOICE_EVENT_REGISTRY =
            ResourceKey.createRegistryKey(id("voice_event"));

    /** The server-only datapack registry {@code vftv:bargain} of the Bargains. */
    public static final ResourceKey<Registry<Bargain>> BARGAIN_REGISTRY =
            ResourceKey.createRegistryKey(id("bargain"));

    /**
     * The id of the {@code vftv:gear_warp} item data map: gear warp per carried stack, 1–10. Its
     * type is {@code WarpDataMaps.GEAR_WARP}.
     */
    public static final Identifier GEAR_WARP = id("gear_warp");

    /**
     * The id of the {@code vftv:warp} player attachment (diagnostics only; direct use is
     * unsupported).
     */
    public static final Identifier WARP_ATTACHMENT = id("warp");

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("vftv", path);
    }
}
