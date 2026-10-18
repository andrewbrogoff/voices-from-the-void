package com.andrewbrogoff.vftv.core.crucible.client;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.core.crucible.CrucibleBlock;
import com.andrewbrogoff.vftv.core.crucible.CrucibleBlockEntity;
import com.andrewbrogoff.vftv.core.crucible.model.CrucibleMath;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The crucible's liquid (crucible spec, "The liquid"): one horizontal quad at y = 15/16 over the
 * interior, from {@code vftv:block/crucible_liquid}, tinted with the dominant aspect's colour
 * (Aqua's for none), with the block's light; nothing when dry.
 */
public final class CrucibleRenderer
        implements BlockEntityRenderer<CrucibleBlockEntity, CrucibleRenderer.State> {
    /** The liquid sprite. */
    public static final Identifier LIQUID =
            Identifier.fromNamespaceAndPath("vftv", "block/crucible_liquid");

    private static final float Y = 15f / 16f;
    private static final float LO = 2f / 16f;
    private static final float HI = 14f / 16f;
    private static final String AQUA = AspectKeys.AQUA.identifier().toString();

    private static HolderLookup.@Nullable RegistryLookup<Aspect> cachedRegistry;
    private static List<String> registryIds = List.of();

    /** The extracted state: whether a quad shows, and its colour. */
    public static final class State extends BlockEntityRenderState {
        boolean wet;
        int color;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(
            CrucibleBlockEntity be,
            State state,
            float partialTicks,
            Vec3 camera,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(be, state, breakProgress);
        state.wet = be.getBlockState().getValue(CrucibleBlock.WATER);
        state.color = state.wet ? be.tint(CrucibleRenderer::color) : 0;
    }

    @Override
    public void submit(
            State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.wet) {
            return;
        }
        TextureAtlasSprite sprite =
                Minecraft.getInstance()
                        .getAtlasManager()
                        .getAtlasOrThrow(AtlasIds.BLOCKS)
                        .getSprite(LIQUID);
        int color = state.color;
        int light = state.lightCoords;
        collector.submitCustomGeometry(
                pose,
                RenderTypes.translucentMovingBlock(),
                (p, buffer) -> {
                    vertex(
                            p,
                            buffer,
                            LO,
                            LO,
                            sprite.getU(0.125f),
                            sprite.getV(0.125f),
                            color,
                            light);
                    vertex(
                            p,
                            buffer,
                            LO,
                            HI,
                            sprite.getU(0.125f),
                            sprite.getV(0.875f),
                            color,
                            light);
                    vertex(
                            p,
                            buffer,
                            HI,
                            HI,
                            sprite.getU(0.875f),
                            sprite.getV(0.875f),
                            color,
                            light);
                    vertex(
                            p,
                            buffer,
                            HI,
                            LO,
                            sprite.getU(0.875f),
                            sprite.getV(0.125f),
                            color,
                            light);
                });
    }

    private static void vertex(
            PoseStack.Pose pose,
            VertexConsumer buffer,
            float x,
            float z,
            float u,
            float v,
            int color,
            int light) {
        buffer.addVertex(pose, x, Y, z)
                .setColor(color)
                .setUv(u, v)
                .setLight(light)
                .setNormal(pose, 0, 1, 0);
    }

    /** The dominant registry aspect's colour, opaque; Aqua's for none or an unreadable record. */
    private static int color(CrucibleBlockEntity be) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return 0xFFFFFFFF;
        }
        HolderLookup.RegistryLookup<Aspect> registry =
                level.registryAccess().lookupOrThrow(AspectKeys.REGISTRY);
        if (!registry.equals(cachedRegistry)) {
            cachedRegistry = registry;
            registryIds =
                    registry.listElements().map(h -> h.key().identifier().toString()).toList();
        }
        Map<String, Integer> pool = be.unreadable() ? Map.of() : be.pool().snapshot();
        String id = CrucibleMath.dominant(registryIds, pool, AQUA);
        Optional<Holder.Reference<Aspect>> aspect =
                registry.get(ResourceKey.create(AspectKeys.REGISTRY, Identifier.parse(id)));
        return 0xFF000000 | aspect.map(a -> a.value().color()).orElse(0xFFFFFF);
    }
}
