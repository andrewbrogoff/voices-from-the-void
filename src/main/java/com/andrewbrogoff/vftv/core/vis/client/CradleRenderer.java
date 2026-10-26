package com.andrewbrogoff.vftv.core.vis.client;

import com.andrewbrogoff.vftv.core.vis.WandCradleBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Wand Cradle's renderer: the cradled wand laid across the two rests, as vanilla draws an item
 * on a campfire. One preallocated item state per cradle.
 */
public final class CradleRenderer
        implements BlockEntityRenderer<WandCradleBlockEntity, CradleRenderer.State> {
    private final ItemModelResolver items;

    /** One cradle's render state. */
    public static final class State extends BlockEntityRenderState {
        final ItemStackRenderState wand = new ItemStackRenderState();
    }

    /** A renderer resolving item models through the context's resolver. */
    public CradleRenderer(BlockEntityRendererProvider.Context context) {
        this.items = context.itemModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(
            WandCradleBlockEntity cradle,
            State state,
            float partialTicks,
            Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(cradle, state, breakProgress);
        items.updateForTopItem(
                state.wand,
                cradle.wand(),
                ItemDisplayContext.FIXED,
                cradle.getLevel(),
                null,
                (int) cradle.getBlockPos().asLong());
    }

    @Override
    public void submit(
            State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.wand.isEmpty()) {
            return;
        }
        pose.pushPose();
        pose.translate(0.5f, 0.6f, 0.5f);
        pose.mulPose(Axis.XP.rotationDegrees(90.0f));
        pose.mulPose(Axis.ZP.rotationDegrees(90.0f)); // the upright sprite lies along x
        pose.scale(0.75f, 0.75f, 0.75f);
        state.wand.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }
}
