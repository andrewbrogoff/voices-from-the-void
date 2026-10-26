package com.andrewbrogoff.vftv.core.worldgen.client;

import com.andrewbrogoff.vftv.core.worldgen.Answerer;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

/** The Answerers' renderer: one model, a texture per type, shadow radius 0.5, no glow. */
public final class AnswererRenderer
        extends MobRenderer<Answerer, AnswererRenderState, AnswererModel> {
    /** The model layer both types share. */
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(Identifier.fromNamespaceAndPath("vftv", "answerer"), "main");

    private static final Identifier ANSWERER =
            Identifier.fromNamespaceAndPath("vftv", "textures/entity/answerer.png");
    private static final Identifier CANTOR =
            Identifier.fromNamespaceAndPath("vftv", "textures/entity/answerer_cantor.png");

    /** A renderer over the baked layer. */
    public AnswererRenderer(EntityRendererProvider.Context context) {
        super(context, new AnswererModel(context.bakeLayer(LAYER)), 0.5F);
    }

    @Override
    public AnswererRenderState createRenderState() {
        return new AnswererRenderState();
    }

    @Override
    public void extractRenderState(Answerer entity, AnswererRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.cantor = entity.isCantor();
        state.chanting = entity.chanting();
        state.attackAnim = entity.getAttackAnim(partialTicks);
    }

    @Override
    public Identifier getTextureLocation(AnswererRenderState state) {
        return state.cantor ? CANTOR : ANSWERER;
    }
}
