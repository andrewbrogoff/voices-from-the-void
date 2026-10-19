package com.andrewbrogoff.vftv.core.arcane;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.vis.VisQuote;
import com.andrewbrogoff.vftv.core.aspect.AspectLang;
import com.andrewbrogoff.vftv.core.text.model.NumberFormats;
import com.andrewbrogoff.vftv.core.vis.VisContent;
import com.andrewbrogoff.vftv.core.vis.VisService;
import com.andrewbrogoff.vftv.core.vis.WandParts;
import com.andrewbrogoff.vftv.core.vis.WandState;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import org.jspecify.annotations.Nullable;

/**
 * The authoritative preview of one arcane recipe (arcane spec, "The authoritative preview"): the
 * only cost source for the station, the book's recipe pages, the fact fields and JEI. It embeds the
 * complete {@link VisQuote} unchanged. Internal in v1.
 *
 * @param recipe the recipe id
 * @param output the created result
 * @param inputs the slot displays: shaped, 9 row-major over the 3x3 with the pattern at the top
 *     left; shapeless, the ingredients in data order
 * @param vis the base cost, ascending aspect id
 * @param availability whether, and why not, a wand prices it
 * @param quote the quote, present iff {@code PRICED}
 * @param wand where the pricing wand is, absent iff {@code NO_WAND}
 */
public record ArcanePreview(
        Identifier recipe,
        ItemStack output,
        List<SlotDisplay> inputs,
        SortedMap<ResourceKey<Aspect>, Integer> vis,
        Availability availability,
        @Nullable VisQuote quote,
        @Nullable Wand wand) {
    /** The fact fields, for the {@code recipe} source. */
    public static final Set<String> FIELDS =
            Set.of("output", "inputs", "vis", "vis_effective", "flux");

    /** Whether a wand prices the preview. */
    public enum Availability {
        /** A non-inert wand priced it. */
        PRICED,
        /** Every wand found is inert. */
        INERT,
        /** No wand. */
        NO_WAND
    }

    /** Where the pricing wand is. */
    public enum Where {
        /** The Worktable's wand slot. */
        STATION,
        /** The selected hotbar slot. */
        MAIN_HAND,
        /** The offhand. */
        OFFHAND,
        /** An inventory slot. */
        INVENTORY
    }

    /**
     * The pricing wand.
     *
     * @param where where it is
     * @param slot its inventory slot (0-35) for {@code INVENTORY} and {@code MAIN_HAND}, else 0
     * @param stack the live stack
     */
    public record Wand(Where where, int slot, ItemStack stack) {}

    /** The station's wand: the wand slot's stack, or null when it holds no wand. */
    public static @Nullable Wand station(ItemStack stack) {
        return stack.is(VisContent.wand()) ? new Wand(Where.STATION, 0, stack) : null;
    }

    /**
     * The reference wand outside the station: the first non-inert {@code vftv:wand} among the
     * selected hotbar slot, the offhand, then inventory slots 0-35 in order; if every wand found is
     * inert, the first one found; null for none. One reference wand prices both the book and JEI.
     */
    public static @Nullable Wand reference(Player player) {
        WandParts parts = WandParts.of(player.level().registryAccess());
        Inventory inventory = player.getInventory();
        List<Wand> candidates = new ArrayList<>();
        int selected = inventory.getSelectedSlot();
        candidates.add(new Wand(Where.MAIN_HAND, selected, inventory.getSelectedItem()));
        candidates.add(new Wand(Where.OFFHAND, 0, player.getOffhandItem()));
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            candidates.add(new Wand(Where.INVENTORY, i, inventory.getItem(i)));
        }
        Wand inert = null;
        for (Wand c : candidates) {
            if (!c.stack().is(VisContent.wand())) {
                continue;
            }
            if (!WandState.of(c.stack(), parts).inert()) {
                return c;
            }
            if (inert == null) {
                inert = c;
            }
        }
        return inert;
    }

    /** The preview of a recipe priced with a wand, or with none. */
    public static ArcanePreview of(
            Player player, RecipeHolder<ArcaneRecipe> holder, @Nullable Wand wand) {
        ArcaneRecipe recipe = holder.value();
        Availability availability;
        VisQuote quote = null;
        if (wand == null) {
            availability = Availability.NO_WAND;
        } else if (WandState.of(wand.stack(), WandParts.of(player.level().registryAccess()))
                .inert()) {
            availability = Availability.INERT;
        } else {
            availability = Availability.PRICED;
            quote =
                    VisService.quote(
                            player, wand.stack(), recipe.vis(), Worktables.context(holder));
        }
        return new ArcanePreview(
                holder.id().identifier(),
                recipe.createResult().result().orElse(ItemStack.EMPTY),
                inputs(recipe),
                new TreeMap<>(recipe.vis()),
                availability,
                quote,
                wand);
    }

    private static List<SlotDisplay> inputs(ArcaneRecipe recipe) {
        Optional<ShapedRecipePattern> shaped = recipe.pattern();
        if (shaped.isEmpty()) {
            return recipe.ingredients().stream().map(Ingredient::display).toList();
        }
        ShapedRecipePattern pattern = shaped.get();
        List<SlotDisplay> cells = new ArrayList<>(9);
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                cells.add(
                        r < pattern.height() && c < pattern.width()
                                ? Ingredient.optionalIngredientToDisplay(
                                        pattern.ingredients().get(c + r * pattern.width()))
                                : SlotDisplay.Empty.INSTANCE);
            }
        }
        return List.copyOf(cells);
    }

    /** The absent text for an unpriced preview, or null when priced. */
    public @Nullable Component absent() {
        return switch (availability) {
            case PRICED -> null;
            case INERT -> Component.translatable(ArcaneLang.INERT_WAND);
            case NO_WAND -> Component.translatable(ArcaneLang.NO_WAND);
        };
    }

    /**
     * {@code arcane.vftv.priced_with} naming the wand and where it is, the absent text without a
     * priced wand, or null at the station.
     */
    public @Nullable Component pricedWith() {
        Wand w = wand;
        if (availability != Availability.PRICED || w == null) {
            return absent();
        }
        return w.where() == Where.STATION
                ? null
                : Component.translatable(
                        ArcaneLang.PRICED_WITH,
                        w.stack().getHoverName(),
                        Component.translatable(
                                ArcaneLang.WHERE + w.where().name().toLowerCase(Locale.ROOT)));
    }

    /**
     * A fact field's value (arcane spec, fact fields), or null for an unknown field.
     *
     * @param recipe the recipe this preview prices, whose ingredients the {@code inputs} field
     *     names
     */
    public @Nullable Component fact(ArcaneRecipe recipe, String field) {
        return switch (field) {
            case "output" ->
                    output.getCount() > 1
                            ? Component.literal(NumberFormats.integer(output.getCount()) + " × ")
                                    .append(output.getHoverName())
                            : output.getHoverName();
            case "inputs" -> inputs(recipe.pattern(), recipe.ingredients());
            case "vis" -> costs(vis);
            case "vis_effective" -> {
                VisQuote q = quote;
                yield q == null ? absent() : costs(q.effective());
            }
            case "flux" -> {
                VisQuote q = quote;
                yield q == null ? absent() : Component.literal(NumberFormats.integer(q.spill()));
            }
            default -> null;
        };
    }

    /** "<em>n</em> × <em>name</em>" per distinct ingredient in first-appearance order. */
    // items() is the one loader-free path to an ingredient's first listed alternative; a fact is
    // rendered once per tooltip, never per tick.
    @SuppressWarnings("deprecation")
    private static Component inputs(Optional<ShapedRecipePattern> pattern, List<Ingredient> list) {
        List<Ingredient> cells = new ArrayList<>();
        pattern.ifPresentOrElse(
                p -> p.ingredients().forEach(o -> o.ifPresent(cells::add)),
                () -> cells.addAll(list));
        Map<Ingredient, Integer> counts = new LinkedHashMap<>();
        cells.forEach(i -> counts.merge(i, 1, Integer::sum));
        MutableComponent out = Component.empty();
        boolean first = true;
        for (Map.Entry<Ingredient, Integer> e : counts.entrySet()) {
            Optional<Holder<Item>> item = e.getKey().items().findFirst();
            if (item.isEmpty()) {
                continue;
            }
            if (!first) {
                out.append(", ");
            }
            first = false;
            out.append(NumberFormats.integer(e.getValue()) + " × ")
                    .append(new ItemStack(item.get()).getHoverName());
        }
        return out;
    }

    /** "<em>Aspect</em> <em>n</em>", ascending aspect id, joined by ", ". */
    private static Component costs(Map<ResourceKey<Aspect>, Integer> cost) {
        MutableComponent out = Component.empty();
        boolean first = true;
        for (Map.Entry<ResourceKey<Aspect>, Integer> e : new TreeMap<>(cost).entrySet()) {
            if (!first) {
                out.append(", ");
            }
            first = false;
            out.append(AspectLang.name(e.getKey()))
                    .append(" " + NumberFormats.integer(e.getValue()));
        }
        return out;
    }
}
