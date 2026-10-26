package com.andrewbrogoff.vftv.core.warp.client;

import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * The colour drain's screen pass (warp spec, "Perception effects"; architecture, "Rendering"): the
 * post chain {@code vftv:voice_drain}, a luminance-preserving desaturation (Rec. 709 weights), run
 * after the level with an unpooled allocator. A post pass's uniforms are fixed when it loads, so
 * the per-frame intensity reaches the shader as a 1 × 1 texture input, rewritten each frame.
 */
public final class ColourDrainPass {
    private static final Identifier CHAIN = Identifier.fromNamespaceAndPath("vftv", "voice_drain");

    /** The texture input the chain samples; {@code textures/effect/} as post chains resolve it. */
    private static final Identifier INTENSITY =
            Identifier.fromNamespaceAndPath("vftv", "textures/effect/voice_drain.png");

    private static @Nullable DynamicTexture intensity;
    private static int written = -1;

    private ColourDrainPass() {}

    /** Draws the drain at {@code amount} (0–1) over the level just rendered; 0 draws nothing. */
    @SuppressWarnings("deprecation") // PostChain.process is the architecture spec's named path
    public static void render(float amount) {
        int level = Math.round(Math.clamp(amount, 0F, 1F) * 255);
        if (level == 0) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        DynamicTexture texture = intensity;
        if (texture == null) {
            texture = new DynamicTexture(() -> "vftv voice drain", 1, 1, true);
            minecraft.getTextureManager().register(INTENSITY, texture);
            intensity = texture;
            written = -1;
        }
        if (level != written) {
            texture.getPixels().setPixel(0, 0, 0xFF000000 | level << 16 | level << 8 | level);
            texture.upload();
            written = level;
        }
        PostChain chain =
                minecraft.getShaderManager().getPostChain(CHAIN, LevelTargetBundle.MAIN_TARGETS);
        if (chain != null) {
            chain.process(
                    minecraft.gameRenderer.mainRenderTarget(), GraphicsResourceAllocator.UNPOOLED);
        }
    }
}
