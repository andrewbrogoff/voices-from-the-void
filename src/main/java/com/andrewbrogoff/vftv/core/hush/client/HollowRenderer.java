package com.andrewbrogoff.vftv.core.hush.client;

import com.andrewbrogoff.vftv.core.hush.Hollow;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

/** The Hollow's renderer: one model, shadow radius 0.4, the rise's scale-in about the feet. */
public final class HollowRenderer extends MobRenderer<Hollow, HollowRenderState, HollowModel> {
    /** The model layer. */
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(Identifier.fromNamespaceAndPath("vftv", "hollow"), "main");

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath("vftv", "textures/entity/hollow.png");

    /** A renderer over the baked layer. */
    public HollowRenderer(EntityRendererProvider.Context context) {
        super(context, new HollowModel(context.bakeLayer(LAYER)), 0.4F);
    }

    @Override
    public HollowRenderState createRenderState() {
        return new HollowRenderState();
    }

    @Override
    public void extractRenderState(Hollow entity, HollowRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.hunting = entity.isAggressive();
        float left = Math.max(0F, entity.rising() - partialTicks);
        state.rise = 1.0F - 0.5F * Math.min(1F, left / Hollow.RISE_TICKS);
    }

    @Override
    protected void scale(HollowRenderState state, PoseStack poseStack) {
        poseStack.scale(state.rise, state.rise, state.rise);
    }

    @Override
    public Identifier getTextureLocation(HollowRenderState state) {
        return TEXTURE;
    }
}
