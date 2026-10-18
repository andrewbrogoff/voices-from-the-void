package com.andrewbrogoff.vftv.core.vis;

import com.andrewbrogoff.vftv.api.vis.VisKeys;
import com.andrewbrogoff.vftv.api.vis.WandCap;
import com.andrewbrogoff.vftv.api.vis.WandRod;
import com.andrewbrogoff.vftv.core.common.RawRecordCodec;
import com.andrewbrogoff.vftv.core.common.RawRecordCodec.Retained;
import com.andrewbrogoff.vftv.core.common.Registrar;
import com.mojang.serialization.Codec;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.levelgen.feature.Feature;
import org.jspecify.annotations.Nullable;

/**
 * The vis feature's content holders (wands spec): the wand and the Stage 2 part items, the node
 * block and its block entity, the wand components, the assembly recipe's serializer, the natural
 * spread feature, the glint particle and the sound events. Resolved after registration.
 */
public final class VisContent {
    /** {@code vftv:wand}. */
    public static final ResourceKey<Item> WAND_KEY =
            ResourceKey.create(Registries.ITEM, id("wand"));

    /** {@code vftv:chime_rod}, the Stage 2 rod's ingredient. */
    public static final ResourceKey<Item> CHIME_ROD_KEY =
            ResourceKey.create(Registries.ITEM, id("chime_rod"));

    /** {@code vftv:resonant_brass_cap}, the Stage 2 cap's ingredient. */
    public static final ResourceKey<Item> RESONANT_BRASS_CAP_KEY =
            ResourceKey.create(Registries.ITEM, id("resonant_brass_cap"));

    /** {@code vftv:node}. */
    public static final ResourceKey<Block> NODE_KEY =
            ResourceKey.create(Registries.BLOCK, id("node"));

    /** {@code vftv:wand_assembly}: the special recipe's id and its serializer's. */
    public static final Identifier WAND_ASSEMBLY = id("wand_assembly");

    /** {@code vftv:node}: the natural-spread feature and its configured feature. */
    public static final Identifier NODE_FEATURE = id("node");

    /** {@code vftv:node_glint}. */
    public static final Identifier NODE_GLINT = id("node_glint");

    /** The tap's start, stop, full and drone sounds, and the node hum, in the spec's order. */
    public static final List<Identifier> SOUNDS =
            List.of(
                    id("wand.tap_start"),
                    id("wand.tap_stop"),
                    id("wand.full"),
                    id("wand.tap_drone"),
                    id("node.hum"));

    /** {@code vftv:vis}'s codec: the pools, kept raw when unreadable. */
    public static final Codec<Retained<VisPools>> VIS_CODEC = RawRecordCodec.wrap(VisPools.BOUNDED);

    /** {@code vftv:wand_focus}'s codec. */
    public static final Codec<Retained<WandFocus>> FOCUS_CODEC =
            RawRecordCodec.wrap(WandFocus.BOUNDED);

    private static @Nullable Supplier<Item> wand;
    private static @Nullable Supplier<Item> chimeRod;
    private static @Nullable Supplier<Item> resonantBrassCap;
    private static @Nullable Supplier<Block> node;
    private static @Nullable Supplier<BlockEntityType<?>> nodeEntity;
    private static @Nullable Supplier<DataComponentType<ResourceKey<WandRod>>> rod;
    private static @Nullable Supplier<DataComponentType<ResourceKey<WandCap>>> cap;
    private static @Nullable Supplier<DataComponentType<Retained<VisPools>>> vis;
    private static @Nullable Supplier<DataComponentType<Retained<WandFocus>>> focus;
    private static @Nullable Supplier<ParticleType<?>> glint;
    private static List<Supplier<SoundEvent>> sounds = List.of();

    private VisContent() {}

    /** The wand. */
    public static Item wand() {
        return get(wand);
    }

    /** The chime rod item. */
    public static Item chimeRod() {
        return get(chimeRod);
    }

    /** The Resonant Brass cap item. */
    public static Item resonantBrassCap() {
        return get(resonantBrassCap);
    }

    /** The node block. */
    public static Block node() {
        return get(node);
    }

    /** The node block entity type. */
    public static BlockEntityType<NodeBlockEntity> nodeEntity() {
        @SuppressWarnings("unchecked") // registered as a BlockEntityType<NodeBlockEntity>
        BlockEntityType<NodeBlockEntity> type = (BlockEntityType<NodeBlockEntity>) get(nodeEntity);
        return type;
    }

    /** {@code vftv:wand_rod}. */
    public static DataComponentType<ResourceKey<WandRod>> rod() {
        return get(rod);
    }

    /** {@code vftv:wand_cap}. */
    public static DataComponentType<ResourceKey<WandCap>> cap() {
        return get(cap);
    }

    /** {@code vftv:vis}. */
    public static DataComponentType<Retained<VisPools>> vis() {
        return get(vis);
    }

    /** {@code vftv:wand_focus}. */
    public static DataComponentType<Retained<WandFocus>> focus() {
        return get(focus);
    }

    /** {@code vftv:node_glint}. */
    public static SimpleParticleType glint() {
        return (SimpleParticleType) get(glint);
    }

    /** A sound by its index in {@link #SOUNDS}. */
    public static SoundEvent sound(int index) {
        return sounds.get(index).get();
    }

    static void register(Registrar r) {
        wand =
                r.register(
                        Registries.ITEM,
                        "wand",
                        () -> new WandItem(new Item.Properties().setId(WAND_KEY).stacksTo(1)));
        chimeRod =
                r.register(
                        Registries.ITEM,
                        "chime_rod",
                        () -> new Item(new Item.Properties().setId(CHIME_ROD_KEY)));
        resonantBrassCap =
                r.register(
                        Registries.ITEM,
                        "resonant_brass_cap",
                        () -> new Item(new Item.Properties().setId(RESONANT_BRASS_CAP_KEY)));
        node =
                r.register(
                        Registries.BLOCK, "node", () -> new NodeBlock(NodeBlock.nodeProperties()));
        nodeEntity =
                r.register(
                        Registries.BLOCK_ENTITY_TYPE,
                        "node",
                        () -> new BlockEntityType<NodeBlockEntity>(NodeBlockEntity::new, node()));
        rod =
                component(
                        r,
                        VisKeys.WAND_ROD_COMPONENT.getPath(),
                        () ->
                                DataComponentType.<ResourceKey<WandRod>>builder()
                                        .persistent(ResourceKey.codec(VisKeys.WAND_ROD_REGISTRY))
                                        .networkSynchronized(
                                                ResourceKey.streamCodec(VisKeys.WAND_ROD_REGISTRY))
                                        .build());
        cap =
                component(
                        r,
                        VisKeys.WAND_CAP_COMPONENT.getPath(),
                        () ->
                                DataComponentType.<ResourceKey<WandCap>>builder()
                                        .persistent(ResourceKey.codec(VisKeys.WAND_CAP_REGISTRY))
                                        .networkSynchronized(
                                                ResourceKey.streamCodec(VisKeys.WAND_CAP_REGISTRY))
                                        .build());
        vis =
                component(
                        r,
                        VisKeys.VIS_COMPONENT.getPath(),
                        () ->
                                DataComponentType.<Retained<VisPools>>builder()
                                        .persistent(VIS_CODEC)
                                        .networkSynchronized(ByteBufCodecs.fromCodec(VIS_CODEC))
                                        .ignoreSwapAnimation()
                                        .build());
        focus =
                component(
                        r,
                        VisKeys.WAND_FOCUS_COMPONENT.getPath(),
                        () ->
                                DataComponentType.<Retained<WandFocus>>builder()
                                        .persistent(FOCUS_CODEC)
                                        .networkSynchronized(
                                                ByteBufCodecs.fromCodecWithRegistries(FOCUS_CODEC))
                                        .build());
        r.<RecipeSerializer<?>>register(
                Registries.RECIPE_SERIALIZER,
                WAND_ASSEMBLY.getPath(),
                () -> WandAssemblyRecipe.SERIALIZER);
        r.<Feature<?>>register(
                Registries.FEATURE,
                NODE_FEATURE.getPath(),
                () -> new NodeFeature(NodeFeature.Config.CODEC));
        glint =
                r.<ParticleType<?>>register(
                        Registries.PARTICLE_TYPE,
                        NODE_GLINT.getPath(),
                        () -> new SimpleParticleType(false));
        sounds =
                SOUNDS.stream()
                        .map(
                                s ->
                                        r.register(
                                                Registries.SOUND_EVENT,
                                                s.getPath(),
                                                () -> SoundEvent.createVariableRangeEvent(s)))
                        .toList();
    }

    private static <T> Supplier<DataComponentType<T>> component(
            Registrar r, String name, Supplier<DataComponentType<T>> factory) {
        Supplier<DataComponentType<?>> registered =
                r.register(Registries.DATA_COMPONENT_TYPE, name, factory);
        return () -> cast(registered.get());
    }

    // Each component is registered from a factory of exactly this type.
    @SuppressWarnings("unchecked")
    private static <T> DataComponentType<T> cast(DataComponentType<?> type) {
        return (DataComponentType<T>) type;
    }

    private static <T> T get(@Nullable Supplier<T> holder) {
        if (holder == null) {
            throw new IllegalStateException("VisFeature.init has not run");
        }
        return holder.get();
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("vftv", path);
    }
}
