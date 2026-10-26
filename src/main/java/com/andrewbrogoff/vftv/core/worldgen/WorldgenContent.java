package com.andrewbrogoff.vftv.core.worldgen;

import com.andrewbrogoff.vftv.core.common.Registrar;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * The worldgen feature's content holders: the Reliquary with its item and block entity, the two
 * Answerer types, the six sound events and the {@code vftv:roll_node} structure processor type.
 */
public final class WorldgenContent {
    /** {@code vftv:reliquary}. */
    public static final ResourceKey<Block> RELIQUARY_KEY =
            ResourceKey.create(Registries.BLOCK, id("reliquary"));

    /** {@code vftv:reliquary}, the item. */
    public static final ResourceKey<Item> RELIQUARY_ITEM_KEY =
            ResourceKey.create(Registries.ITEM, id("reliquary"));

    /** {@code vftv:answerer}. */
    public static final ResourceKey<EntityType<?>> ANSWERER_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, id("answerer"));

    /** {@code vftv:answerer_cantor}. */
    public static final ResourceKey<EntityType<?>> CANTOR_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, id("answerer_cantor"));

    /** {@code vftv:roll_node}, the structure processor type. */
    public static final Identifier ROLL_NODE = id("roll_node");

    /** {@code vftv:answerer.ambient}. */
    public static final Identifier ANSWERER_AMBIENT = id("answerer.ambient");

    /** {@code vftv:answerer.hurt}. */
    public static final Identifier ANSWERER_HURT = id("answerer.hurt");

    /** {@code vftv:answerer.death}. */
    public static final Identifier ANSWERER_DEATH = id("answerer.death");

    /** {@code vftv:cantor.chant}. */
    public static final Identifier CANTOR_CHANT = id("cantor.chant");

    /** {@code vftv:reliquary.give}. */
    public static final Identifier RELIQUARY_GIVE = id("reliquary.give");

    /** {@code vftv:reliquary.empty}. */
    public static final Identifier RELIQUARY_EMPTY = id("reliquary.empty");

    /** Every sound event, in the spec's order. */
    public static final List<Identifier> SOUNDS =
            List.of(
                    ANSWERER_AMBIENT,
                    ANSWERER_HURT,
                    ANSWERER_DEATH,
                    CANTOR_CHANT,
                    RELIQUARY_GIVE,
                    RELIQUARY_EMPTY);

    private static Supplier<Block> reliquary = WorldgenContent::missing;
    private static Supplier<Item> reliquaryItem = WorldgenContent::missing;
    private static Supplier<BlockEntityType<ReliquaryBlockEntity>> reliquaryType =
            WorldgenContent::missing;
    private static Supplier<EntityType<Answerer>> answerer = WorldgenContent::missing;
    private static Supplier<EntityType<Answerer>> cantor = WorldgenContent::missing;
    private static List<Supplier<SoundEvent>> sounds = List.of();

    private WorldgenContent() {}

    /** The Reliquary. */
    public static Block reliquary() {
        return reliquary.get();
    }

    /** The Reliquary's item (creative only). */
    public static Item reliquaryItem() {
        return reliquaryItem.get();
    }

    /** The Reliquary's block entity type. */
    public static BlockEntityType<ReliquaryBlockEntity> reliquaryType() {
        return reliquaryType.get();
    }

    /** The Answerer. */
    public static EntityType<Answerer> answerer() {
        return answerer.get();
    }

    /** The Cantor. */
    public static EntityType<Answerer> cantor() {
        return cantor.get();
    }

    /** The sound event registered as {@code id}, one of {@link #SOUNDS}. */
    public static SoundEvent sound(Identifier id) {
        return sounds.get(SOUNDS.indexOf(id)).get();
    }

    static void register(Registrar registrar) {
        reliquary =
                registrar.register(
                        Registries.BLOCK,
                        "reliquary",
                        () ->
                                new ReliquaryBlock(
                                        BlockBehaviour.Properties.of()
                                                .mapColor(MapColor.COLOR_PURPLE)
                                                .strength(-1.0F, 3_600_000.0F)
                                                .noLootTable()
                                                .sound(SoundType.TUFF_BRICKS)
                                                .noOcclusion()
                                                .pushReaction(PushReaction.BLOCK)
                                                .setId(RELIQUARY_KEY)));
        reliquaryItem =
                registrar.register(
                        Registries.ITEM,
                        "reliquary",
                        () ->
                                new BlockItem(
                                        reliquary(),
                                        new Item.Properties()
                                                .setId(RELIQUARY_ITEM_KEY)
                                                .useBlockDescriptionPrefix()));
        Supplier<BlockEntityType<?>> be =
                registrar.register(
                        Registries.BLOCK_ENTITY_TYPE,
                        "reliquary",
                        () -> new BlockEntityType<>(ReliquaryBlockEntity::new, reliquary()));
        // Registered from the typed BlockEntityType just above.
        @SuppressWarnings("unchecked")
        Supplier<BlockEntityType<ReliquaryBlockEntity>> typed =
                () -> (BlockEntityType<ReliquaryBlockEntity>) be.get();
        reliquaryType = typed;
        answerer = entity(registrar, ANSWERER_KEY, false);
        cantor = entity(registrar, CANTOR_KEY, true);
        sounds =
                SOUNDS.stream()
                        .map(
                                s ->
                                        registrar.register(
                                                Registries.SOUND_EVENT,
                                                s.getPath(),
                                                () -> SoundEvent.createVariableRangeEvent(s)))
                        .toList();
        registrar.register(
                Registries.STRUCTURE_PROCESSOR, ROLL_NODE.getPath(), () -> RollNodeProcessor.CODEC);
    }

    /** Registered without {@code notInPeaceful()}: allowed in peaceful, as the spec says. */
    private static Supplier<EntityType<Answerer>> entity(
            Registrar registrar, ResourceKey<EntityType<?>> key, boolean cantor) {
        Supplier<EntityType<?>> type =
                registrar.register(
                        Registries.ENTITY_TYPE,
                        key.identifier().getPath(),
                        () ->
                                EntityType.Builder.<Answerer>of(
                                                cantor ? Cantor::new : Answerer::new,
                                                MobCategory.MONSTER)
                                        .sized(0.6F, 1.95F)
                                        .eyeHeight(1.62F)
                                        .clientTrackingRange(8)
                                        .build(key));
        // Registered from the typed EntityType just above.
        @SuppressWarnings("unchecked")
        Supplier<EntityType<Answerer>> typed = () -> (EntityType<Answerer>) type.get();
        return typed;
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("vftv", path);
    }

    @SuppressWarnings("TypeParameterUnusedInFormals") // it only throws, so no cast is hidden
    private static <T> T missing() {
        throw new IllegalStateException("WorldgenFeature.init has not run");
    }
}
