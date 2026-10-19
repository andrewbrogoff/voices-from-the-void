package com.andrewbrogoff.vftv.core.arcane;

import com.andrewbrogoff.vftv.api.research.Research;
import com.andrewbrogoff.vftv.api.research.ResearchKeys;
import com.andrewbrogoff.vftv.core.common.Registrar;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * The arcane feature's content holders (arcane spec, "The Worktable"): the Worktable block and its
 * item, the {@code vftv:worktable} menu type and the two sound events.
 */
public final class ArcaneContent {
    /** {@code vftv:worktable}, the block. */
    public static final ResourceKey<Block> WORKTABLE_BLOCK_KEY =
            ResourceKey.create(Registries.BLOCK, id("worktable"));

    /** {@code vftv:worktable}, the item. */
    public static final ResourceKey<Item> WORKTABLE_ITEM_KEY =
            ResourceKey.create(Registries.ITEM, id("worktable"));

    /** {@code vftv:arcane_crafting}: gates turning a table and the Bell. */
    public static final ResourceKey<Research> ARCANE_CRAFTING =
            ResourceKey.create(ResearchKeys.REGISTRY, id("arcane_crafting"));

    /** {@code vftv:worktable.convert}: a successful turn. */
    public static final Identifier CONVERT = id("worktable.convert");

    /** {@code vftv:worktable.craft}: once per craft payload with a committed iteration. */
    public static final Identifier CRAFT = id("worktable.craft");

    /** Both sound events, in the spec's order. */
    public static final List<Identifier> SOUNDS = List.of(CONVERT, CRAFT);

    private static Supplier<Block> worktable = ArcaneContent::missing;
    private static Supplier<Item> worktableItem = ArcaneContent::missing;
    private static Supplier<MenuType<WorktableMenu>> menu = ArcaneContent::missing;
    private static Supplier<SoundEvent> convert = ArcaneContent::missing;
    private static Supplier<SoundEvent> craft = ArcaneContent::missing;

    private ArcaneContent() {}

    /** The Worktable block. */
    public static Block worktable() {
        return worktable.get();
    }

    /** The Worktable's item. */
    public static Item worktableItem() {
        return worktableItem.get();
    }

    /** The {@code vftv:worktable} menu type. */
    public static MenuType<WorktableMenu> menu() {
        return menu.get();
    }

    /** {@code worktable.convert}. */
    public static SoundEvent convertSound() {
        return convert.get();
    }

    /** {@code worktable.craft}. */
    public static SoundEvent craftSound() {
        return craft.get();
    }

    static void register(Registrar registrar) {
        worktable =
                registrar.register(
                        Registries.BLOCK,
                        "worktable",
                        () ->
                                new WorktableBlock(
                                        BlockBehaviour.Properties.ofFullCopy(Blocks.CRAFTING_TABLE)
                                                .setId(WORKTABLE_BLOCK_KEY)));
        worktableItem =
                registrar.register(
                        Registries.ITEM,
                        "worktable",
                        () ->
                                new BlockItem(
                                        worktable(),
                                        new Item.Properties()
                                                .setId(WORKTABLE_ITEM_KEY)
                                                .useBlockDescriptionPrefix()));
        Supplier<MenuType<?>> type =
                registrar.register(
                        Registries.MENU,
                        "worktable",
                        () -> new MenuType<>(WorktableMenu::new, FeatureFlags.VANILLA_SET));
        // Registered from a MenuType<WorktableMenu> just above.
        @SuppressWarnings("unchecked")
        Supplier<MenuType<WorktableMenu>> typed = () -> (MenuType<WorktableMenu>) type.get();
        menu = typed;
        convert = sound(registrar, CONVERT);
        craft = sound(registrar, CRAFT);
    }

    private static Supplier<SoundEvent> sound(Registrar registrar, Identifier id) {
        return registrar.register(
                Registries.SOUND_EVENT,
                id.getPath(),
                () -> SoundEvent.createVariableRangeEvent(id));
    }

    @SuppressWarnings("TypeParameterUnusedInFormals") // it only throws, so no cast is hidden
    private static <T> T missing() {
        throw new IllegalStateException("ArcaneFeature.init has not run");
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("vftv", path);
    }
}
