package com.andrewbrogoff.vftv.core.crucible;

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

/**
 * The crucible feature's content holders: the crucible block, its item and block entity, the
 * Resonant Brass ingot, the seven sound events, and the {@code vftv:crucible} recipe type,
 * serializer and display type.
 */
public final class CrucibleContent {
    /** {@code vftv:crucible}, the block and its item. */
    public static final ResourceKey<Block> CRUCIBLE_BLOCK_KEY =
            ResourceKey.create(Registries.BLOCK, id("crucible"));

    /** {@code vftv:crucible}, the item. */
    public static final ResourceKey<Item> CRUCIBLE_ITEM_KEY =
            ResourceKey.create(Registries.ITEM, id("crucible"));

    /** {@code vftv:resonant_brass_ingot}, frozen by the crucible spec. */
    public static final ResourceKey<Item> INGOT_KEY =
            ResourceKey.create(Registries.ITEM, id("resonant_brass_ingot"));

    /** {@code vftv:crucible.fill}. */
    public static final Identifier FILL = id("crucible.fill");

    /** {@code vftv:crucible.empty}. */
    public static final Identifier EMPTY = id("crucible.empty");

    /** {@code vftv:crucible.spill}. */
    public static final Identifier SPILL = id("crucible.spill");

    /** {@code vftv:crucible.dissolve}. */
    public static final Identifier DISSOLVE = id("crucible.dissolve");

    /** {@code vftv:crucible.craft}. */
    public static final Identifier CRAFT = id("crucible.craft");

    /** {@code vftv:crucible.refuse}. */
    public static final Identifier REFUSE = id("crucible.refuse");

    /** {@code vftv:crucible.boil}. */
    public static final Identifier BOIL = id("crucible.boil");

    /** Every sound event, in the spec's order. */
    public static final List<Identifier> SOUNDS =
            List.of(FILL, EMPTY, SPILL, DISSOLVE, CRAFT, REFUSE, BOIL);

    private static Supplier<Block> crucible = CrucibleContent::missing;
    private static Supplier<Item> crucibleItem = CrucibleContent::missing;
    private static Supplier<Item> ingot = CrucibleContent::missing;
    private static Supplier<BlockEntityType<CrucibleBlockEntity>> blockEntity =
            CrucibleContent::missing;
    private static Supplier<SoundEvent> fill = CrucibleContent::missing;
    private static Supplier<SoundEvent> empty = CrucibleContent::missing;
    private static Supplier<SoundEvent> spill = CrucibleContent::missing;
    private static Supplier<SoundEvent> dissolve = CrucibleContent::missing;
    private static Supplier<SoundEvent> craft = CrucibleContent::missing;
    private static Supplier<SoundEvent> refuse = CrucibleContent::missing;
    private static Supplier<SoundEvent> boil = CrucibleContent::missing;

    private CrucibleContent() {}

    /** The crucible block. */
    public static Block crucible() {
        return crucible.get();
    }

    /** The crucible's item. */
    public static Item crucibleItem() {
        return crucibleItem.get();
    }

    /** Resonant Brass. */
    public static Item ingot() {
        return ingot.get();
    }

    /** The crucible's block entity type. */
    public static BlockEntityType<CrucibleBlockEntity> blockEntity() {
        return blockEntity.get();
    }

    /** {@code crucible.fill}. */
    public static SoundEvent fillSound() {
        return fill.get();
    }

    /** {@code crucible.empty}. */
    public static SoundEvent emptySound() {
        return empty.get();
    }

    /** {@code crucible.spill}. */
    public static SoundEvent spillSound() {
        return spill.get();
    }

    /** {@code crucible.dissolve}. */
    public static SoundEvent dissolveSound() {
        return dissolve.get();
    }

    /** {@code crucible.craft}. */
    public static SoundEvent craftSound() {
        return craft.get();
    }

    /** {@code crucible.refuse}. */
    public static SoundEvent refuseSound() {
        return refuse.get();
    }

    /** {@code crucible.boil}. */
    public static SoundEvent boilSound() {
        return boil.get();
    }

    static void register(Registrar registrar) {
        crucible =
                registrar.register(
                        Registries.BLOCK,
                        "crucible",
                        () ->
                                new CrucibleBlock(
                                        BlockBehaviour.Properties.ofFullCopy(Blocks.CAULDRON)
                                                .setId(CRUCIBLE_BLOCK_KEY)));
        crucibleItem =
                registrar.register(
                        Registries.ITEM,
                        "crucible",
                        () ->
                                new BlockItem(
                                        crucible(),
                                        new Item.Properties()
                                                .setId(CRUCIBLE_ITEM_KEY)
                                                .useBlockDescriptionPrefix()));
        ingot =
                registrar.register(
                        Registries.ITEM,
                        "resonant_brass_ingot",
                        () -> new Item(new Item.Properties().setId(INGOT_KEY)));
        Supplier<BlockEntityType<?>> type =
                registrar.register(
                        Registries.BLOCK_ENTITY_TYPE,
                        "crucible",
                        () -> new BlockEntityType<>(CrucibleBlockEntity::new, crucible()));
        // Registered from a BlockEntityType<CrucibleBlockEntity> just above.
        @SuppressWarnings("unchecked")
        Supplier<BlockEntityType<CrucibleBlockEntity>> typed =
                () -> (BlockEntityType<CrucibleBlockEntity>) type.get();
        blockEntity = typed;
        fill = sound(registrar, FILL);
        empty = sound(registrar, EMPTY);
        spill = sound(registrar, SPILL);
        dissolve = sound(registrar, DISSOLVE);
        craft = sound(registrar, CRAFT);
        refuse = sound(registrar, REFUSE);
        boil = sound(registrar, BOIL);
        String name = CrucibleRecipe.ID.getPath();
        registrar.<RecipeType<?>>register(Registries.RECIPE_TYPE, name, () -> CrucibleRecipe.TYPE);
        registrar.<RecipeSerializer<?>>register(
                Registries.RECIPE_SERIALIZER, name, () -> CrucibleRecipe.SERIALIZER);
        registrar.<RecipeDisplay.Type<?>>register(
                Registries.RECIPE_DISPLAY, name, () -> CrucibleDisplay.TYPE);
    }

    private static Supplier<SoundEvent> sound(Registrar registrar, Identifier id) {
        return registrar.register(
                Registries.SOUND_EVENT,
                id.getPath(),
                () -> SoundEvent.createVariableRangeEvent(id));
    }

    @SuppressWarnings("TypeParameterUnusedInFormals") // it only throws, so no cast is hidden
    private static <T> T missing() {
        throw new IllegalStateException("CrucibleFeature.init has not run");
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("vftv", path);
    }
}
