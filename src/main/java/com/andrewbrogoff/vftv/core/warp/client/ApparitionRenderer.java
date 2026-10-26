package com.andrewbrogoff.vftv.core.warp.client;

import com.andrewbrogoff.vftv.core.warp.Apparition;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * The apparition (warp spec, "Phantoms"): a robed, faceless Choir figure, 8 × 30 × 6 px on a 64 ×
 * 64 sheet, standing still and facing the Listener. Its editable model is {@code
 * tools/art/warp/apparition.bbmodel}; this layer is that model's cuboids.
 */
public final class ApparitionRenderer extends EntityRenderer<Apparition, ApparitionRenderer.State> {
    /** The model layer. */
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(Identifier.fromNamespaceAndPath("vftv", "apparition"), "main");

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath("vftv", "textures/entity/apparition.png");

    private final Model model;

    /** A renderer over the baked layer. */
    public ApparitionRenderer(EntityRendererProvider.Context context) {
        super(context);
        model = new Model(context.bakeLayer(LAYER));
    }

    @Override
    public void submit(
            State state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - state.yRot));
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -1.501F, 0.0F);
        collector.submitModel(
                model,
                state,
                poseStack,
                TEXTURE,
                state.lightCoords,
                OverlayTexture.NO_OVERLAY,
                state.outlineColor,
                null);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(Apparition entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.yRot = entity.getYRot();
    }

    /** The apparition's render state: its facing. */
    public static final class State extends EntityRenderState {
        /** The yaw it faces, in degrees. */
        public float yRot;
    }

    /** The model: a hood over a long robe, no face. */
    public static final class Model extends EntityModel<State> {
        /** The model over its baked root. */
        public Model(ModelPart root) {
            super(root);
        }

        /** The layer: hood 6 × 7 × 6 over robe 8 × 23 × 6, on 64 × 64. */
        public static LayerDefinition createLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            root.addOrReplaceChild(
                    "hood",
                    CubeListBuilder.create().texOffs(0, 0).addBox(-3, -7, -3, 6, 7, 6),
                    PartPose.offset(0, 1, 0));
            root.addOrReplaceChild(
                    "robe",
                    CubeListBuilder.create().texOffs(0, 16).addBox(-4, 0, -3, 8, 23, 6),
                    PartPose.offset(0, 1, 0));
            return LayerDefinition.create(mesh, 64, 64);
        }
    }
}
