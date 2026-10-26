package com.andrewbrogoff.vftv.core.worldgen.client;

import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * The Answerers' model (worldgen spec, "Models"): the frozen parts, pivots, cuboids and UV origins,
 * converted from the spec's y-up feet-origin space into the model's y-down space ({@code y' = 24 -
 * y}). The Cantor's bell hangs from the right arm and shows only on the Cantor. Poses: vanilla's
 * arm bob and limb swing, the right arm's attack swing, and the chant. No allocation per frame.
 */
public final class AnswererModel extends EntityModel<AnswererRenderState> {
    private static final float DEG = Mth.DEG_TO_RAD;

    private final ModelPart head;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;
    private final ModelPart bell;

    /** The model over its baked root. */
    public AnswererModel(ModelPart root) {
        super(root);
        head = root.getChild("head");
        rightArm = root.getChild("right_arm");
        leftArm = root.getChild("left_arm");
        rightLeg = root.getChild("right_leg");
        leftLeg = root.getChild("left_leg");
        bell = rightArm.getChild("bell");
    }

    /** The one layer both types share, on a 64 × 64 texture. */
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition head =
                root.addOrReplaceChild(
                        "head",
                        CubeListBuilder.create().texOffs(0, 0).addBox(-4, -8, -4, 8, 8, 8),
                        PartPose.offset(0, 0, 0));
        head.addOrReplaceChild(
                "mouthpiece",
                CubeListBuilder.create().texOffs(32, 0).addBox(-1, -4, -8, 2, 2, 4),
                PartPose.ZERO);
        root.addOrReplaceChild(
                "body",
                CubeListBuilder.create().texOffs(16, 16).addBox(-4, 0, -2, 8, 12, 4),
                PartPose.offset(0, 0, 0));
        root.addOrReplaceChild(
                "robe",
                CubeListBuilder.create().texOffs(0, 32).addBox(-5, 0, -3, 10, 10, 6),
                PartPose.offset(0, 12, 0));
        PartDefinition rightArm =
                root.addOrReplaceChild(
                        "right_arm",
                        CubeListBuilder.create().texOffs(40, 16).addBox(-3, -2, -2, 4, 12, 4),
                        PartPose.offset(-5, 2, 0));
        rightArm.addOrReplaceChild(
                "bell",
                CubeListBuilder.create().texOffs(44, 0).addBox(-2, 1, -2, 3, 3, 3),
                PartPose.offset(-1, 10, 0));
        root.addOrReplaceChild(
                "left_arm",
                CubeListBuilder.create().texOffs(40, 16).mirror().addBox(-1, -2, -2, 4, 12, 4),
                PartPose.offset(5, 2, 0));
        root.addOrReplaceChild(
                "right_leg",
                CubeListBuilder.create().texOffs(0, 16).addBox(-2, 0, -2, 4, 12, 4),
                PartPose.offset(-2, 12, 0));
        root.addOrReplaceChild(
                "left_leg",
                CubeListBuilder.create().texOffs(0, 16).mirror().addBox(-2, 0, -2, 4, 12, 4),
                PartPose.offset(2, 12, 0));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(AnswererRenderState state) {
        super.setupAnim(state);
        head.yRot = state.yRot * DEG;
        head.xRot = state.xRot * DEG;
        float pos = state.walkAnimationPos;
        float speed = state.walkAnimationSpeed;
        rightArm.xRot = Mth.cos(pos * 0.6662F + Mth.PI) * speed;
        leftArm.xRot = Mth.cos(pos * 0.6662F) * speed;
        rightLeg.xRot = Mth.cos(pos * 0.6662F) * 1.4F * speed * 0.5F;
        leftLeg.xRot = Mth.cos(pos * 0.6662F + Mth.PI) * 1.4F * speed * 0.5F;
        AnimationUtils.bobArms(rightArm, leftArm, state.ageInTicks);
        if (state.attackAnim > 0) {
            rightArm.xRot = -2.0F * Mth.sin(state.attackAnim * Mth.PI);
        }
        bell.visible = state.cantor;
        if (state.cantor && state.chanting) {
            rightArm.xRot = -60 * DEG;
            leftArm.xRot = -60 * DEG;
            rightArm.zRot = 10 * DEG * Mth.sin(state.ageInTicks * Mth.TWO_PI / 4);
            head.xRot = -15 * DEG;
        }
    }
}
