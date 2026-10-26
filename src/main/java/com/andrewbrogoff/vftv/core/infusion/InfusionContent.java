package com.andrewbrogoff.vftv.core.infusion;

import com.andrewbrogoff.vftv.core.common.Registrar;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;

/**
 * The infusion feature's content holders: the Singing Bowl and the Pedestal with their items and
 * block entities, the Echo rod and cap items, the eight sound events, and the {@code vftv:infusion}
 * recipe type, serializer and display type.
 */
public final class InfusionContent {
    /** {@code vftv:singing_bowl}. */
    public static final ResourceKey<Block> BOWL_KEY = block("singing_bowl");

    /** {@code vftv:pedestal}. */
    public static final ResourceKey<Block> PEDESTAL_KEY = block("pedestal");

    /** {@code vftv:singing_bowl}, the item. */
    public static final ResourceKey<Item> BOWL_ITEM_KEY = item("singing_bowl");

    /** {@code vftv:pedestal}, the item. */
    public static final ResourceKey<Item> PEDESTAL_ITEM_KEY = item("pedestal");

    /** {@code vftv:echo_rod}, frozen by the infusion spec. */
    public static final ResourceKey<Item> ECHO_ROD_KEY = item("echo_rod");

    /** {@code vftv:echo_cap}, frozen by the infusion spec. */
    public static final ResourceKey<Item> ECHO_CAP_KEY = item("echo_cap");

    /** {@code vftv:infusion.strike}. */
    public static final Identifier STRIKE = id("infusion.strike");

    /** {@code vftv:infusion.begin}. */
    public static final Identifier BEGIN = id("infusion.begin");

    /** {@code vftv:infusion.draw}. */
    public static final Identifier DRAW = id("infusion.draw");

    /** {@code vftv:infusion.absorb}. */
    public static final Identifier ABSORB = id("infusion.absorb");

    /** {@code vftv:infusion.upset}. */
    public static final Identifier UPSET = id("infusion.upset");

    /** {@code vftv:infusion.finish}. */
    public static final Identifier FINISH = id("infusion.finish");

    /** {@code vftv:infusion.abandon}. */
    public static final Identifier ABANDON = id("infusion.abandon");

    /** {@code vftv:infusion.ring}. */
    public static final Identifier RING = id("infusion.ring");

    /** Every sound event, in the spec's order. */
    public static final List<Identifier> SOUNDS =
            List.of(STRIKE, BEGIN, DRAW, ABSORB, UPSET, FINISH, ABANDON, RING);

    private static Supplier<Block> bowl = InfusionContent::missing;
    private static Supplier<Block> pedestal = InfusionContent::missing;
    private static Supplier<Item> bowlItem = InfusionContent::missing;
    private static Supplier<Item> pedestalItem = InfusionContent::missing;
    private static Supplier<Item> echoRod = InfusionContent::missing;
    private static Supplier<Item> echoCap = InfusionContent::missing;
    private static Supplier<BlockEntityType<BowlBlockEntity>> bowlType = InfusionContent::missing;
    private static Supplier<BlockEntityType<PedestalBlockEntity>> pedestalType =
            InfusionContent::missing;
    private static List<Supplier<SoundEvent>> sounds = List.of();

    private InfusionContent() {}

    /** The Singing Bowl. */
    public static Block bowl() {
        return bowl.get();
    }

    /** The Pedestal. */
    public static Block pedestal() {
        return pedestal.get();
    }

    /** The Singing Bowl's item. */
    public static Item bowlItem() {
        return bowlItem.get();
    }

    /** The Pedestal's item. */
    public static Item pedestalItem() {
        return pedestalItem.get();
    }

    /** The Echo rod. */
    public static Item echoRod() {
        return echoRod.get();
    }

    /** The Echo cap. */
    public static Item echoCap() {
        return echoCap.get();
    }

    /** The bowl's block entity type. */
    public static BlockEntityType<BowlBlockEntity> bowlType() {
        return bowlType.get();
    }

    /** The pedestal's block entity type. */
    public static BlockEntityType<PedestalBlockEntity> pedestalType() {
        return pedestalType.get();
    }

    /** The sound event registered as {@code id}, one of {@link #SOUNDS}. */
    public static SoundEvent sound(Identifier id) {
        return sounds.get(SOUNDS.indexOf(id)).get();
    }

    static void register(Registrar registrar) {
        bowl =
                registrar.register(
                        Registries.BLOCK,
                        "singing_bowl",
                        () ->
                                new AltarBlock(
                                        BlockBehaviour.Properties.ofFullCopy(Blocks.LECTERN)
                                                .noOcclusion()
                                                .pushReaction(PushReaction.BLOCK)
                                                .setId(BOWL_KEY),
                                        true));
        pedestal =
                registrar.register(
                        Registries.BLOCK,
                        "pedestal",
                        () ->
                                new AltarBlock(
                                        BlockBehaviour.Properties.ofFullCopy(
                                                        Blocks.POLISHED_DEEPSLATE)
                                                .noOcclusion()
                                                .pushReaction(PushReaction.BLOCK)
                                                .setId(PEDESTAL_KEY),
                                        false));
        bowlItem = blockItem(registrar, "singing_bowl", BOWL_ITEM_KEY, InfusionContent::bowl);
        pedestalItem =
                blockItem(registrar, "pedestal", PEDESTAL_ITEM_KEY, InfusionContent::pedestal);
        echoRod =
                registrar.register(
                        Registries.ITEM,
                        "echo_rod",
                        () -> new Item(new Item.Properties().setId(ECHO_ROD_KEY)));
        echoCap =
                registrar.register(
                        Registries.ITEM,
                        "echo_cap",
                        () -> new Item(new Item.Properties().setId(ECHO_CAP_KEY)));
        Supplier<BlockEntityType<?>> bowlBe =
                registrar.register(
                        Registries.BLOCK_ENTITY_TYPE,
                        "singing_bowl",
                        () -> new BlockEntityType<>(BowlBlockEntity::new, bowl()));
        Supplier<BlockEntityType<?>> pedestalBe =
                registrar.register(
                        Registries.BLOCK_ENTITY_TYPE,
                        "pedestal",
                        () -> new BlockEntityType<>(PedestalBlockEntity::new, pedestal()));
        // Registered from the typed BlockEntityTypes just above.
        @SuppressWarnings("unchecked")
        Supplier<BlockEntityType<BowlBlockEntity>> b =
                () -> (BlockEntityType<BowlBlockEntity>) bowlBe.get();
        @SuppressWarnings("unchecked")
        Supplier<BlockEntityType<PedestalBlockEntity>> p =
                () -> (BlockEntityType<PedestalBlockEntity>) pedestalBe.get();
        bowlType = b;
        pedestalType = p;
        sounds =
                SOUNDS.stream()
                        .map(
                                s ->
                                        registrar.register(
                                                Registries.SOUND_EVENT,
                                                s.getPath(),
                                                () -> SoundEvent.createVariableRangeEvent(s)))
                        .toList();
        String name = InfusionRecipe.ID.getPath();
        registrar.<RecipeType<?>>register(Registries.RECIPE_TYPE, name, () -> InfusionRecipe.TYPE);
        registrar.<RecipeSerializer<?>>register(
                Registries.RECIPE_SERIALIZER, name, () -> InfusionRecipe.SERIALIZER);
        registrar.<RecipeDisplay.Type<?>>register(
                Registries.RECIPE_DISPLAY, name, () -> InfusionDisplay.TYPE);
    }

    private static Supplier<Item> blockItem(
            Registrar registrar, String name, ResourceKey<Item> key, Supplier<Block> block) {
        return registrar.register(
                Registries.ITEM,
                name,
                () ->
                        new BlockItem(
                                block.get(),
                                new Item.Properties().setId(key).useBlockDescriptionPrefix()));
    }

    @SuppressWarnings("TypeParameterUnusedInFormals") // it only throws, so no cast is hidden
    private static <T> T missing() {
        throw new IllegalStateException("InfusionFeature.init has not run");
    }

    private static ResourceKey<Block> block(String path) {
        return ResourceKey.create(Registries.BLOCK, id(path));
    }

    private static ResourceKey<Item> item(String path) {
        return ResourceKey.create(Registries.ITEM, id(path));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("vftv", path);
    }
}
