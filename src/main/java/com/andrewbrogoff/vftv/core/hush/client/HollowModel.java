package com.andrewbrogoff.vftv.core.hush.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * The Hollow's model (Hush spec, "Rendering and sound"): the frozen parts, pivots, cuboids and UV
 * origins, converted from the spec's y-up feet-origin space into the model's y-down space ({@code
 * y' = 24 - y}). Diagonal leg pairs swing ±25° with the walk; the head follows the look, clamped to
 * ±45° yaw and ±30° pitch, and droops 10° with no target. No allocation per frame.
 */
public final class HollowModel extends EntityModel<HollowRenderState> {
    private static final float DEG = Mth.DEG_TO_RAD;

    private final ModelPart head;
    private final ModelPart frontLeft;
    private final ModelPart frontRight;
    private final ModelPart backLeft;
    private final ModelPart backRight;

    /** The model over its baked root. */
    public HollowModel(ModelPart root) {
        super(root);
        head = root.getChild("head");
        frontLeft = root.getChild("leg_front_left");
        frontRight = root.getChild("leg_front_right");
        backLeft = root.getChild("leg_back_left");
        backRight = root.getChild("leg_back_right");
    }

    /** The layer, on a 64 × 32 texture. */
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild(
                "body",
                CubeListBuilder.create().texOffs(0, 0).addBox(-3, -5, -7, 6, 5, 14),
                PartPose.offset(0, 17, 0));
        root.addOrReplaceChild(
                "head",
                CubeListBuilder.create().texOffs(40, 0).addBox(-2, -4, -4, 4, 5, 4),
                PartPose.offset(0, 14, -7));
        leg(root, "leg_front_left", 2, -5, false);
        leg(root, "leg_front_right", -2, -5, true);
        leg(root, "leg_back_left", 2, 5, false);
        leg(root, "leg_back_right", -2, 5, true);
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static void leg(PartDefinition root, String name, float x, float z, boolean mirror) {
        CubeListBuilder cube = CubeListBuilder.create().texOffs(0, 19);
        if (mirror) {
            cube.mirror();
        }
        root.addOrReplaceChild(name, cube.addBox(-1, 0, -1, 2, 7, 2), PartPose.offset(x, 17, z));
    }

    @Override
    public void setupAnim(HollowRenderState state) {
        super.setupAnim(state);
        head.yRot = Mth.clamp(state.yRot, -45F, 45F) * DEG;
        head.xRot = (Mth.clamp(state.xRot, -30F, 30F) + (state.hunting ? 0F : 10F)) * DEG;
        float swing =
                Mth.cos(state.walkAnimationPos * 0.6662F) * 25F * DEG * state.walkAnimationSpeed;
        frontLeft.xRot = swing;
        backRight.xRot = swing;
        frontRight.xRot = -swing;
        backLeft.xRot = -swing;
    }
}
