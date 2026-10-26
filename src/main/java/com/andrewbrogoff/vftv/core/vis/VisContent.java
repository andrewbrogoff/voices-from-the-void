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
import com.andrewbrogoff.vftv.core.vis.model.RelayNetwork;
import net.minecraft.world.item.BlockItem;
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

    /** {@code vftv:bell_jar}. */
    public static final ResourceKey<Item> BELL_JAR_KEY =
            ResourceKey.create(Registries.ITEM, id("bell_jar"));

    /** {@code vftv:node_tap}. */
    public static final ResourceKey<Block> NODE_TAP_KEY =
            ResourceKey.create(Registries.BLOCK, id("node_tap"));

    /** {@code vftv:vis_relay}. */
    public static final ResourceKey<Block> VIS_RELAY_KEY =
            ResourceKey.create(Registries.BLOCK, id("vis_relay"));

    /** {@code vftv:wand_cradle}. */
    public static final ResourceKey<Block> WAND_CRADLE_KEY =
            ResourceKey.create(Registries.BLOCK, id("wand_cradle"));

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
                    id("node.hum"),
                    id("node.absorb"),
                    id("node.clarity_rise"),
                    id("relay.link"),
                    id("cradle.charge"),
                    id("bell_jar.capture"),
                    id("bell_jar.release"));

    /** {@code node.absorb}'s index in {@link #SOUNDS}. */
    public static final int ABSORB = 5;

    /** {@code node.clarity_rise}'s index. */
    public static final int CLARITY_RISE = 6;

    /** {@code relay.link}'s index. */
    public static final int RELAY_LINK = 7;

    /** {@code cradle.charge}'s index. */
    public static final int CRADLE_CHARGE = 8;

    /** {@code bell_jar.capture}'s index. */
    public static final int JAR_CAPTURE = 9;

    /** {@code bell_jar.release}'s index. */
    public static final int JAR_RELEASE = 10;

    /** {@code vftv:vis}'s codec: the pools, kept raw when unreadable. */
    public static final Codec<Retained<VisPools>> VIS_CODEC = RawRecordCodec.wrap(VisPools.BOUNDED);

    /** {@code vftv:captured_node}'s codec: the jar's node, kept raw when unreadable. */
    public static final Codec<Retained<CapturedNode>> CAPTURED_CODEC =
            RawRecordCodec.wrap(CapturedNode.BOUNDED);

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
    private static @Nullable Supplier<Item> bellJar;
    private static @Nullable Supplier<Block> nodeTap;
    private static @Nullable Supplier<Block> visRelay;
    private static @Nullable Supplier<Block> wandCradle;
    private static @Nullable Supplier<BlockEntityType<?>> nodeTapEntity;
    private static @Nullable Supplier<BlockEntityType<?>> visRelayEntity;
    private static @Nullable Supplier<BlockEntityType<?>> cradleEntity;
    private static @Nullable Supplier<DataComponentType<Retained<CapturedNode>>> captured;
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

    /** The Bell Jar. */
    public static Item bellJar() {
        return get(bellJar);
    }

    /** The Node Tap block. */
    public static Block nodeTap() {
        return get(nodeTap);
    }

    /** The Vis Relay block. */
    public static Block visRelay() {
        return get(visRelay);
    }

    /** The Wand Cradle block. */
    public static Block wandCradle() {
        return get(wandCradle);
    }

    /** The Node Tap's block entity type. */
    public static BlockEntityType<RelayBlockEntity> nodeTapEntity() {
        @SuppressWarnings("unchecked") // registered as a BlockEntityType<RelayBlockEntity>
        BlockEntityType<RelayBlockEntity> type =
                (BlockEntityType<RelayBlockEntity>) get(nodeTapEntity);
        return type;
    }

    /** The Vis Relay's block entity type. */
    public static BlockEntityType<?> visRelayEntity() {
        return get(visRelayEntity);
    }

    /** The data-less member type of a tap or relay state. */
    static BlockEntityType<?> relayEntity(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(nodeTap()) ? nodeTapEntity() : visRelayEntity();
    }

    /** The Wand Cradle's block entity type. */
    public static BlockEntityType<WandCradleBlockEntity> cradleEntity() {
        @SuppressWarnings("unchecked") // registered as a BlockEntityType<WandCradleBlockEntity>
        BlockEntityType<WandCradleBlockEntity> type =
                (BlockEntityType<WandCradleBlockEntity>) get(cradleEntity);
        return type;
    }

    /** {@code vftv:captured_node}. */
    public static DataComponentType<Retained<CapturedNode>> captured() {
        return get(captured);
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
        bellJar =
                r.register(
                        Registries.ITEM,
                        "bell_jar",
                        () -> new BellJarItem(new Item.Properties().setId(BELL_JAR_KEY).stacksTo(1)));
        nodeTap =
                r.register(
                        Registries.BLOCK,
                        "node_tap",
                        () -> new NodeTapBlock(RelayBlock.properties(NODE_TAP_KEY)));
        visRelay =
                r.register(
                        Registries.BLOCK,
                        "vis_relay",
                        () ->
                                new RelayBlock(
                                        RelayBlock.properties(VIS_RELAY_KEY),
                                        RelayNetwork.Kind.RELAY,
                                        Block.box(4, 0, 4, 12, 15, 12)));
        wandCradle =
                r.register(
                        Registries.BLOCK,
                        "wand_cradle",
                        () -> new WandCradleBlock(RelayBlock.properties(WAND_CRADLE_KEY)));
        blockItem(r, NODE_TAP_KEY, VisContent::nodeTap);
        blockItem(r, VIS_RELAY_KEY, VisContent::visRelay);
        blockItem(r, WAND_CRADLE_KEY, VisContent::wandCradle);
        nodeTapEntity =
                r.register(
                        Registries.BLOCK_ENTITY_TYPE,
                        "node_tap",
                        () -> new BlockEntityType<RelayBlockEntity>(RelayBlockEntity::new, nodeTap()));
        visRelayEntity =
                r.register(
                        Registries.BLOCK_ENTITY_TYPE,
                        "vis_relay",
                        () ->
                                new BlockEntityType<RelayBlockEntity>(
                                        RelayBlockEntity::new, visRelay()));
        cradleEntity =
                r.register(
                        Registries.BLOCK_ENTITY_TYPE,
                        "wand_cradle",
                        () ->
                                new BlockEntityType<WandCradleBlockEntity>(
                                        WandCradleBlockEntity::new, wandCradle()));
        captured =
                component(
                        r,
                        VisKeys.CAPTURED_NODE_COMPONENT.getPath(),
                        () ->
                                DataComponentType.<Retained<CapturedNode>>builder()
                                        .persistent(CAPTURED_CODEC)
                                        .networkSynchronized(ByteBufCodecs.fromCodec(CAPTURED_CODEC))
                                        .build());
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

    private static void blockItem(Registrar r, ResourceKey<Block> key, Supplier<Block> block) {
        r.register(
                Registries.ITEM,
                key.identifier().getPath(),
                () ->
                        new BlockItem(
                                block.get(),
                                new Item.Properties()
                                        .setId(ResourceKey.create(Registries.ITEM, key.identifier()))
                                        .useBlockDescriptionPrefix()));
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
