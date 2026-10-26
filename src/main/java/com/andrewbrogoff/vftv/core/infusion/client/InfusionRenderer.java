package com.andrewbrogoff.vftv.core.infusion.client;

import com.andrewbrogoff.vftv.core.infusion.AltarBlockEntity;
import com.andrewbrogoff.vftv.core.infusion.BowlBlockEntity;
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
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The held item (infusion spec, "Rendering and sound"): on the bowl at (0.5, 0.75, 0.5), on a
 * pedestal at (0.5, 1.0, 0.5), {@code FIXED} at scale 0.5, turning 2° per tick about Y; a ringing
 * bowl's item also rises 0.125 and bobs ±1/32 over 80 ticks. Client game time and partial tick
 * only; the render state is reused, so nothing is allocated per frame.
 *
 * @param <T> the bowl's or the pedestal's block entity
 */
public final class InfusionRenderer<T extends AltarBlockEntity>
        implements BlockEntityRenderer<T, InfusionRenderer.State> {
    private final ItemModelResolver resolver;

    /** The extracted state: the item's render state and its motion. */
    public static final class State extends BlockEntityRenderState {
        final ItemStackRenderState item = new ItemStackRenderState();
        float y;
        float angle;
    }

    /** A renderer for one of the two block entity types. */
    public InfusionRenderer(BlockEntityRendererProvider.Context context) {
        this.resolver = context.itemModelResolver();
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
        BlockEntityRenderState.extractBase(be, state, breakProgress);
        resolver.updateForTopItem(
                state.item,
                be.getTheItem(),
                ItemDisplayContext.FIXED,
                be.getLevel(),
                null,
                (int) be.getBlockPos().asLong());
        float time =
                be.getLevel() == null ? 0 : (be.getLevel().getGameTime() % 7200L) + partialTicks;
        state.angle = (time * 2f) % 360f;
        if (be instanceof BowlBlockEntity bowl) {
            state.y =
                    bowl.ringing()
                            ? 0.75f + 0.125f + Mth.sin(time * Mth.TWO_PI / 80f) / 32f
                            : 0.75f;
        } else {
            state.y = 1.0f;
        }
    }

    @Override
    public void submit(
            State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.item.isEmpty()) {
            return;
        }
        pose.pushPose();
        pose.translate(0.5f, state.y, 0.5f);
        pose.mulPose(Axis.YP.rotationDegrees(state.angle));
        pose.scale(0.5f, 0.5f, 0.5f);
        state.item.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }
}
