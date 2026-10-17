package com.andrewbrogoff.vftv.core.essentia.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;

/**
 * The essentia renderers' one quad: a face of a pixel-unit box, in vanilla's vertex order, with the
 * whole sprite mapped onto it. Block-atlas sprites draw translucent; GUI-atlas glyphs cut out.
 */
final class EssentiaQuads {
    /** The block atlas, translucent: the liquid and the label paper. */
    static final RenderType BLOCK = Sheets.translucentBlockItemSheet();

    /** The GUI atlas, cut out: the glyphs and motes. */
    static final RenderType GUI = RenderTypes.entityCutout(Sheets.GUI_SHEET);

    private EssentiaQuads() {}

    /** A block-atlas sprite. */
    static TextureAtlasSprite block(Identifier id) {
        return Minecraft.getInstance()
                .getAtlasManager()
                .getAtlasOrThrow(AtlasIds.BLOCKS)
                .getSprite(id);
    }

    /** A GUI-atlas sprite. */
    static TextureAtlasSprite gui(Identifier id) {
        return Minecraft.getInstance()
                .getAtlasManager()
                .getAtlasOrThrow(AtlasIds.GUI)
                .getSprite(id);
    }

    /** Face {@code d} of the box {@code [x0,y0,z0]–[x1,y1,z1]} in pixels. */
    static void face(
            PoseStack.Pose pose,
            VertexConsumer out,
            TextureAtlasSprite sprite,
            Direction d,
            float x0,
            float y0,
            float z0,
            float x1,
            float y1,
            float z1,
            int color,
            int light) {
        float a = x0 / 16;
        float b = y0 / 16;
        float c = z0 / 16;
        float e = x1 / 16;
        float f = y1 / 16;
        float g = z1 / 16;
        float[][] v =
                switch (d) {
                    case DOWN -> new float[][] {{a, b, g}, {a, b, c}, {e, b, c}, {e, b, g}};
                    case UP -> new float[][] {{a, f, c}, {a, f, g}, {e, f, g}, {e, f, c}};
                    case NORTH -> new float[][] {{e, f, c}, {e, b, c}, {a, b, c}, {a, f, c}};
                    case SOUTH -> new float[][] {{a, f, g}, {a, b, g}, {e, b, g}, {e, f, g}};
                    case WEST -> new float[][] {{a, f, c}, {a, b, c}, {a, b, g}, {a, f, g}};
                    case EAST -> new float[][] {{e, f, g}, {e, b, g}, {e, b, c}, {e, f, c}};
                };
        float[] us = {sprite.getU0(), sprite.getU0(), sprite.getU1(), sprite.getU1()};
        float[] vs = {sprite.getV0(), sprite.getV1(), sprite.getV1(), sprite.getV0()};
        for (int i = 0; i < 4; i++) {
            out.addVertex(pose, v[i][0], v[i][1], v[i][2])
                    .setColor(color)
                    .setUv(us[i], vs[i])
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(pose, d.getStepX(), d.getStepY(), d.getStepZ());
        }
    }
}
