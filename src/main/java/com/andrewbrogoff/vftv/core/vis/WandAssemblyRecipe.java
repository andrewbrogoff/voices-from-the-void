package com.andrewbrogoff.vftv.core.vis;

import com.andrewbrogoff.vftv.api.vis.WandCap;
import com.andrewbrogoff.vftv.api.vis.WandPartKeys;
import com.andrewbrogoff.vftv.api.vis.WandRod;
import com.andrewbrogoff.vftv.core.common.RawRecordCodec.Retained;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * {@code vftv:wand_assembly} (wands spec, "Assembly"): a cap, a rod and the same cap, in one column
 * of three consecutive rows and nothing else, make an empty wand of those parts. Ungated and free;
 * nothing about it depends on a player, so a click, a shift-click and the Crafter agree.
 *
 * @param category the recipe-book category, {@code equipment} by default
 */
public record WandAssemblyRecipe(CraftingBookCategory category)
        implements net.minecraft.world.item.crafting.CraftingRecipe {
    /** The recipe's codec: {@code {"category"}} and nothing else. */
    public static final MapCodec<WandAssemblyRecipe> MAP_CODEC =
            RecordCodecBuilder.mapCodec(
                    i ->
                            i.group(
                                            CraftingBookCategory.CODEC
                                                    .optionalFieldOf(
                                                            "category",
                                                            CraftingBookCategory.EQUIPMENT)
                                                    .forGetter(WandAssemblyRecipe::category))
                                    .apply(i, WandAssemblyRecipe::new));

    /** The recipe's stream codec. */
    public static final StreamCodec<RegistryFriendlyByteBuf, WandAssemblyRecipe> STREAM_CODEC =
            StreamCodec.composite(
                    CraftingBookCategory.STREAM_CODEC,
                    WandAssemblyRecipe::category,
                    WandAssemblyRecipe::new);

    /** The serializer. */
    public static final RecipeSerializer<WandAssemblyRecipe> SERIALIZER =
            new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    /**
     * The parts the grid holds, or null when it doesn't match.
     *
     * @param rod the rod's key
     * @param cap the cap's key
     */
    record Parts(ResourceKey<WandRod> rod, ResourceKey<WandCap> cap) {}

    static @Nullable Parts parts(CraftingInput input, WandParts parts) {
        if (input.width() != 1 || input.height() != 3 || input.ingredientCount() != 3) {
            return null;
        }
        ResourceKey<WandCap> top = parts.capByItem().get(input.getItem(0, 0).getItem());
        ResourceKey<WandRod> rod = parts.rodByItem().get(input.getItem(0, 1).getItem());
        ResourceKey<WandCap> bottom = parts.capByItem().get(input.getItem(0, 2).getItem());
        return top != null && rod != null && top.equals(bottom) ? new Parts(rod, top) : null;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return parts(input, WandParts.of(level.registryAccess())) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        Parts p = parts(input, WandParts.last());
        return p == null ? ItemStack.EMPTY : wand(p.rod(), p.cap());
    }

    /** An empty wand of these parts. */
    public static ItemStack wand(ResourceKey<WandRod> rod, ResourceKey<WandCap> cap) {
        ItemStack wand = new ItemStack(VisContent.wand());
        wand.set(VisContent.rod(), rod);
        wand.set(VisContent.cap(), cap);
        wand.set(VisContent.vis(), new Retained.Read<>(VisPools.EMPTY));
        return wand;
    }

    @Override
    public List<RecipeDisplay> display() {
        WandParts parts = WandParts.last();
        SlotDisplay caps =
                composite(
                        parts.caps().values().stream()
                                .flatMap(c -> BuiltInRegistries.ITEM.getOptional(c.item()).stream())
                                .toList());
        SlotDisplay rods =
                composite(
                        parts.rods().values().stream()
                                .flatMap(r -> BuiltInRegistries.ITEM.getOptional(r.item()).stream())
                                .toList());
        DataComponentPatch representative =
                DataComponentPatch.builder()
                        .set(VisContent.rod(), WandPartKeys.WOODEN_ROD)
                        .set(VisContent.cap(), WandPartKeys.COPPER_CAP)
                        .set(VisContent.vis(), new Retained.Read<>(VisPools.EMPTY))
                        .build();
        return List.of(
                new ShapedCraftingRecipeDisplay(
                        1,
                        3,
                        List.of(caps, rods, caps),
                        new SlotDisplay.ItemStackSlotDisplay(
                                new ItemStackTemplate(VisContent.wand(), representative)),
                        new SlotDisplay.ItemSlotDisplay(
                                BuiltInRegistries.ITEM.wrapAsHolder(Items.CRAFTING_TABLE))));
    }

    private static SlotDisplay composite(List<Item> items) {
        return new SlotDisplay.Composite(
                items.stream()
                        .map(
                                i ->
                                        (SlotDisplay)
                                                new SlotDisplay.ItemSlotDisplay(
                                                        BuiltInRegistries.ITEM.wrapAsHolder(i)))
                        .toList());
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public boolean showNotification() {
        return false;
    }

    @Override
    public String group() {
        return "";
    }

    @Override
    public net.minecraft.world.item.crafting.PlacementInfo placementInfo() {
        return net.minecraft.world.item.crafting.PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public RecipeSerializer<WandAssemblyRecipe> getSerializer() {
        return SERIALIZER;
    }
}
