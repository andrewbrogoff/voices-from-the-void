package com.andrewbrogoff.vftv.core.essentia.client;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.core.essentia.EssentiaTubeBlock;
import com.andrewbrogoff.vftv.core.essentia.EssentiaTubeBlock.Face;
import com.andrewbrogoff.vftv.core.essentia.LabelledBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The tube's and valve's renderer (essentia spec, "The tube band" and "Flow motes"): a labelled
 * tube's band and glyphs, and any mote passing through this cell. Draws nothing otherwise.
 */
public final class TubeRenderer<T extends BlockEntity>
        implements BlockEntityRenderer<T, TubeRenderer.State> {
    private static final Identifier BAND =
            Identifier.fromNamespaceAndPath("vftv", "block/essentia_tube_band");
    private static final Identifier RING =
            Identifier.fromNamespaceAndPath("vftv", "block/essentia_tube_band_ring");

    /** What one tube draws this frame. */
    public static final class State extends BlockEntityRenderState {
        @Nullable Identifier glyph;
        final Face[] faces = new Face[6];
        final List<FlowMotes.Drawn> motes = new ArrayList<>();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(
            T be,
            State state,
            float partialTicks,
            Vec3 camera,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(
                be, state, partialTicks, camera, breakProgress);
        state.glyph = null;
        if (be instanceof LabelledBlockEntity tube
                && be.getBlockState().getBlock() instanceof EssentiaTubeBlock) {
            ResourceKey<Aspect> label = tube.label();
            if (!tube.readable()) {
                state.glyph = ClientEssentia.MISSING_SPRITE;
            } else if (label != null) {
                state.glyph = ClientEssentia.glyph(label);
            }
            for (Direction d : Direction.values()) {
                state.faces[d.get3DDataValue()] =
                        be.getBlockState().getValue(EssentiaTubeBlock.face(d));
            }
        }
        state.motes.clear();
        FlowMotes.inCell(be.getBlockPos(), partialTicks, state.motes);
    }

    @Override
    public void submit(
            State state, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
        int light = state.lightCoords;
        Identifier glyph = state.glyph;
        if (glyph != null) {
            // The state is this frame's and is drawn within it: no copy needed.
            Face[] faces = state.faces;
            out.submitCustomGeometry(
                    pose,
                    EssentiaQuads.BLOCK,
                    (p, buffer) -> {
                        for (Direction d : Direction.values()) {
                            boolean none = faces[d.get3DDataValue()] == Face.NONE;
                            EssentiaQuads.face(
                                    p,
                                    buffer,
                                    EssentiaQuads.block(none ? BAND : RING),
                                    d,
                                    4,
                                    4,
                                    4,
                                    12,
                                    12,
                                    12,
                                    -1,
                                    light);
                        }
                    });
            out.submitCustomGeometry(
                    pose,
                    EssentiaQuads.GUI,
                    (p, buffer) -> {
                        var sprite = EssentiaQuads.gui(glyph);
                        for (Direction d : Direction.values()) {
                            Face f = faces[d.get3DDataValue()];
                            if (f == Face.NONE) {
                                glyphOn(p, buffer, sprite, d, 3.99f, light);
                            } else if (f == Face.BLOCKED) {
                                glyphOn(p, buffer, sprite, d, -0.01f, light);
                            }
                        }
                    });
        }
        for (FlowMotes.Drawn mote : state.motes) {
            pose.pushPose();
            pose.translate(mote.x(), mote.y(), mote.z());
            pose.mulPose(camera.orientation);
            float half = (float) (ClientEssentia.moteSize() * 8);
            Identifier sprite = mote.sprite();
            out.submitCustomGeometry(
                    pose,
                    EssentiaQuads.GUI,
                    (p, buffer) ->
                            EssentiaQuads.face(
                                    p,
                                    buffer,
                                    EssentiaQuads.gui(sprite),
                                    Direction.NORTH,
                                    -half,
                                    -half,
                                    0,
                                    half,
                                    half,
                                    0,
                                    -1,
                                    0xF000F0));
            pose.popPose();
        }
    }

    /**
     * A 6×6 px glyph on face {@code d}, {@code inset} px in from the block boundary (negative: just
     * outside it).
     */
    private static void glyphOn(
            PoseStack.Pose p,
            com.mojang.blaze3d.vertex.VertexConsumer buffer,
            net.minecraft.client.renderer.texture.TextureAtlasSprite sprite,
            Direction d,
            float inset,
            int light) {
        float lo = inset;
        float hi = 16 - inset;
        switch (d) {
            case DOWN -> EssentiaQuads.face(p, buffer, sprite, d, 5, lo, 5, 11, lo, 11, -1, light);
            case UP -> EssentiaQuads.face(p, buffer, sprite, d, 5, hi, 5, 11, hi, 11, -1, light);
            case NORTH -> EssentiaQuads.face(p, buffer, sprite, d, 5, 5, lo, 11, 11, lo, -1, light);
            case SOUTH -> EssentiaQuads.face(p, buffer, sprite, d, 5, 5, hi, 11, 11, hi, -1, light);
            case WEST -> EssentiaQuads.face(p, buffer, sprite, d, lo, 5, 5, lo, 11, 11, -1, light);
            case EAST -> EssentiaQuads.face(p, buffer, sprite, d, hi, 5, 5, hi, 11, 11, -1, light);
        }
    }
}
