package com.andrewbrogoff.vftv.core.essentia.client;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.core.essentia.EssentiaJarBlock;
import com.andrewbrogoff.vftv.core.essentia.JarBlockEntity;
import com.andrewbrogoff.vftv.core.essentia.model.EssentiaMath;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The jar's renderer (essentia spec, "Rendering and sound, The jar"): the liquid, tinted by its
 * aspect at the capacity in force, and on the {@code facing} face the label paper and the glyph.
 * Identity never rests on the tint.
 */
public final class JarRenderer implements BlockEntityRenderer<JarBlockEntity, JarRenderer.State> {
    private static final Identifier LIQUID =
            Identifier.fromNamespaceAndPath("vftv", "block/essentia_jar_liquid");
    private static final Identifier PAPER =
            Identifier.fromNamespaceAndPath("vftv", "block/essentia_label");

    /** What one jar draws this frame. */
    public static final class State extends BlockEntityRenderState {
        int height;
        int color;
        @Nullable Identifier glyph;
        boolean paper;
        Direction facing = Direction.NORTH;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(
            JarBlockEntity jar,
            State state,
            float partialTicks,
            Vec3 camera,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(
                jar, state, partialTicks, camera, breakProgress);
        state.facing = jar.getBlockState().getValue(EssentiaJarBlock.FACING);
        state.height = 0;
        state.glyph = null;
        state.paper = false;
        if (!jar.readable()) {
            return;
        }
        var contents = jar.contents();
        ResourceKey<Aspect> held = contents.aspect();
        ResourceKey<Aspect> label = jar.label();
        if (held != null) {
            state.height = EssentiaMath.liquidHeight(contents.amount(), ClientEssentia.capacity());
            state.color = ClientEssentia.color(held);
        }
        ResourceKey<Aspect> shown = label != null ? label : held;
        state.paper = label != null;
        state.glyph = shown == null ? null : ClientEssentia.glyph(shown);
    }

    @Override
    public void submit(
            State state, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
        int light = state.lightCoords;
        if (state.height > 0) {
            int top = 1 + state.height;
            int color = state.color;
            out.submitCustomGeometry(
                    pose,
                    EssentiaQuads.BLOCK,
                    (p, buffer) -> {
                        var sprite = EssentiaQuads.block(LIQUID);
                        for (Direction d : Direction.values()) {
                            EssentiaQuads.face(
                                    p, buffer, sprite, d, 4, 1, 4, 12, top, 12, color, light);
                        }
                    });
        }
        Identifier glyph = state.glyph;
        if (glyph == null) {
            return;
        }
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-state.facing.toYRot() + 180));
        pose.translate(-0.5, 0, -0.5);
        if (state.paper) {
            out.submitCustomGeometry(
                    pose,
                    EssentiaQuads.BLOCK,
                    (p, buffer) ->
                            EssentiaQuads.face(
                                    p,
                                    buffer,
                                    EssentiaQuads.block(PAPER),
                                    Direction.NORTH,
                                    4,
                                    1,
                                    2.995f,
                                    12,
                                    9,
                                    3,
                                    -1,
                                    light));
        }
        out.submitCustomGeometry(
                pose,
                EssentiaQuads.GUI,
                (p, buffer) ->
                        EssentiaQuads.face(
                                p,
                                buffer,
                                EssentiaQuads.gui(glyph),
                                Direction.NORTH,
                                5,
                                2,
                                2.99f,
                                11,
                                8,
                                3,
                                -1,
                                light));
        pose.popPose();
    }
}
