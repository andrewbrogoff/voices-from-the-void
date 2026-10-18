package com.andrewbrogoff.vftv.core.vis.client;

import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.api.vis.VisLimits;
import com.andrewbrogoff.vftv.core.aspect.AspectIcons;
import com.andrewbrogoff.vftv.core.vis.NodeBlockEntity;
import com.andrewbrogoff.vftv.core.vis.NodeService;
import com.andrewbrogoff.vftv.core.vis.NodeState;
import com.andrewbrogoff.vftv.core.vis.VisRulesService;
import com.andrewbrogoff.vftv.core.vis.model.NodeClientNumbers;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The node's renderer (wands spec, "Rendering and sound"): camera-facing, cutout, full-bright
 * quads, at most seven per node: the core from the eight-frame strip {@code vftv:block/node_core},
 * sized by the summed current and the clarity, and one mote per aspect drawing that aspect's own
 * icon. An inert or unreadable node draws its core only.
 */
public final class NodeRenderer
        implements BlockEntityRenderer<NodeBlockEntity, NodeRenderer.State> {
    /** The core strip. */
    public static final Identifier CORE =
            Identifier.fromNamespaceAndPath("vftv", "block/node_core");

    private static final int FULL_BRIGHT = 15728880;
    private static final int VIEW_DISTANCE = 64;

    /** One node's render state, preallocated for six motes. */
    public static final class State extends BlockEntityRenderState {
        float coreSize;
        int frame;
        int motes;
        final @Nullable TextureAtlasSprite[] moteSprites =
                new TextureAtlasSprite[VisLimits.MAX_NODE_ASPECTS];
        float time;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(
            NodeBlockEntity node,
            State state,
            float partialTicks,
            Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(node, state, breakProgress);
        NodeClientNumbers n = NodeAmbience.numbers();
        long now = node.getLevel() == null ? 0 : node.getLevel().getGameTime();
        // Reduced by the orbit before the float conversion, so late game times keep sub-tick
        // motion.
        state.time = Math.floorMod(now, (long) n.orbitTicks()) + partialTicks;
        state.motes = 0;
        NodeState s = node.state();
        if (s == null) {
            state.coreSize = (float) n.coreSize(0, 0);
            state.frame = n.frame(now, 0);
            return;
        }
        int clarity = s.clarity().index();
        NodeState settled =
                node.getLevel() == null
                        ? s
                        : s.settle(now, VisRulesService.tuning(node.getLevel()));
        int sum = 0;
        for (NodeState.NodeAspect a : settled.aspects()) {
            sum += a.current();
        }
        state.coreSize = (float) n.coreSize(sum, clarity);
        state.frame = n.frame(now, clarity);
        if (node.getLevel() == null || NodeService.type(node.getLevel(), s.type()) == null) {
            return;
        }
        var registry = node.getLevel().registryAccess().lookup(AspectKeys.REGISTRY).orElse(null);
        var gui = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.GUI);
        for (NodeState.NodeAspect a : settled.aspects()) {
            if (registry == null || state.motes >= VisLimits.MAX_NODE_ASPECTS) {
                break;
            }
            var holder =
                    registry.get(
                            net.minecraft.resources.ResourceKey.create(
                                    AspectKeys.REGISTRY, a.aspect()));
            if (holder.isPresent()) {
                state.moteSprites[state.motes++] = gui.getSprite(AspectIcons.of(holder.get()));
            }
        }
    }

    @Override
    public void submit(
            State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        NodeClientNumbers n = NodeAmbience.numbers();
        TextureAtlasSprite core =
                Minecraft.getInstance()
                        .getAtlasManager()
                        .getAtlasOrThrow(AtlasIds.BLOCKS)
                        .getSprite(CORE);
        float v0 = core.getV0() + (core.getV1() - core.getV0()) * state.frame / 8f;
        float v1 = core.getV0() + (core.getV1() - core.getV0()) * (state.frame + 1) / 8f;
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        quad(pose, collector, camera, core, core.getU0(), core.getU1(), v0, v1, state.coreSize);
        for (int i = 0; i < state.motes; i++) {
            TextureAtlasSprite sprite = state.moteSprites[i];
            if (sprite == null) {
                continue;
            }
            double theta = 2 * Math.PI * (state.time / n.orbitTicks() + (double) i / state.motes);
            pose.pushPose();
            pose.translate(
                    n.orbitRadius() * Math.cos(theta),
                    n.bob() * Math.sin(2 * theta),
                    n.orbitRadius() * Math.sin(theta));
            quad(
                    pose,
                    collector,
                    camera,
                    sprite,
                    sprite.getU0(),
                    sprite.getU1(),
                    sprite.getV0(),
                    sprite.getV1(),
                    (float) n.moteSize());
            pose.popPose();
        }
        pose.popPose();
    }

    private static void quad(
            PoseStack pose,
            SubmitNodeCollector collector,
            CameraRenderState camera,
            TextureAtlasSprite sprite,
            float u0,
            float u1,
            float v0,
            float v1,
            float size) {
        pose.pushPose();
        pose.mulPose(camera.orientation);
        float h = size / 2;
        collector.submitCustomGeometry(
                pose,
                RenderTypes.entityCutout(sprite.atlasLocation()),
                (p, buffer) -> {
                    vertex(p, buffer, -h, -h, u0, v1);
                    vertex(p, buffer, h, -h, u1, v1);
                    vertex(p, buffer, h, h, u1, v0);
                    vertex(p, buffer, -h, h, u0, v0);
                });
        pose.popPose();
    }

    private static void vertex(
            PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float u, float v) {
        buffer.addVertex(pose, x, y, 0)
                .setColor(-1)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT)
                .setNormal(pose, 0, 0, 1);
    }

    @Override
    public int getViewDistance() {
        return VIEW_DISTANCE;
    }

    @Override
    public AABB getRenderBoundingBox(NodeBlockEntity node) {
        return new AABB(node.getBlockPos()).inflate(1);
    }
}
