package com.andrewbrogoff.vftv.core.crucible;

import com.andrewbrogoff.vftv.api.crucible.CrucibleLimits;
import com.andrewbrogoff.vftv.api.research.Research;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * One side's immutable set of {@code vftv:crucible} recipes (crucible spec, "Recipe sync and
 * reload"): sorted by id, with an index from item to the simple-catalyst recipes listing it and one
 * id-ordered list of non-simple catalysts. Replaced whole.
 */
public final class CrucibleRecipeSnapshot {
    /** No recipes. */
    public static final CrucibleRecipeSnapshot EMPTY = new CrucibleRecipeSnapshot(List.of());

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Comparator<RecipeHolder<?>> BY_ID =
            Comparator.comparing(h -> h.id().identifier().toString());

    private final List<RecipeHolder<CrucibleRecipe>> recipes;
    private final Map<Identifier, RecipeHolder<CrucibleRecipe>> byId = new HashMap<>();
    private final Map<Item, List<RecipeHolder<CrucibleRecipe>>> simple = new HashMap<>();
    private final List<RecipeHolder<CrucibleRecipe>> complex = new ArrayList<>();

    private CrucibleRecipeSnapshot(List<RecipeHolder<CrucibleRecipe>> sorted) {
        recipes = List.copyOf(sorted);
        for (RecipeHolder<CrucibleRecipe> holder : recipes) {
            byId.put(holder.id().identifier(), holder);
            // A custom ingredient (simple or not) is tested from the non-simple list: listing its
            // items would need its NeoForge type, which core never sees.
            if (!holder.value().catalyst().isCustom()) {
                holder.value().catalyst().getValues().stream()
                        .map(Holder::value)
                        .distinct()
                        .forEach(
                                item ->
                                        simple.computeIfAbsent(item, i -> new ArrayList<>())
                                                .add(holder));
            } else {
                complex.add(holder);
            }
        }
    }

    /**
     * Builds a snapshot. A recipe whose result creates an empty or over-full stack is left out with
     * a logged error. More than {@value CrucibleLimits#MAX_CRUCIBLE_RECIPES} is an error.
     */
    public static DataResult<CrucibleRecipeSnapshot> of(
            Collection<RecipeHolder<CrucibleRecipe>> holders) {
        List<RecipeHolder<CrucibleRecipe>> valid = new ArrayList<>(holders.size());
        for (RecipeHolder<CrucibleRecipe> holder : holders) {
            holder.value()
                    .createResult()
                    .ifSuccess(stack -> valid.add(holder))
                    .ifError(
                            e ->
                                    LOGGER.error(
                                            "Skipping crucible recipe {}: {}",
                                            holder.id().identifier(),
                                            e.message()));
        }
        if (valid.size() > CrucibleLimits.MAX_CRUCIBLE_RECIPES) {
            int count = valid.size();
            return DataResult.error(
                    () ->
                            count
                                    + " crucible recipes exceed the cap of "
                                    + CrucibleLimits.MAX_CRUCIBLE_RECIPES);
        }
        valid.sort(BY_ID);
        return DataResult.success(new CrucibleRecipeSnapshot(valid));
    }

    /** Every recipe, in ascending id. */
    public List<RecipeHolder<CrucibleRecipe>> recipes() {
        return recipes;
    }

    /** The recipe with this id, or null. */
    public @Nullable RecipeHolder<CrucibleRecipe> get(Identifier id) {
        return byId.get(id);
    }

    /**
     * The candidates (crucible spec, "Selection"): recipes whose catalyst matches a count-one copy
     * of {@code stack} and whose research is learned, in ascending id. The stack's simple list and
     * the non-simple list are merged in id order.
     */
    public List<RecipeHolder<CrucibleRecipe>> candidates(
            ItemStack stack, Predicate<ResourceKey<Research>> learned) {
        List<RecipeHolder<CrucibleRecipe>> listed = simple.getOrDefault(stack.getItem(), List.of());
        if (listed.isEmpty() && complex.isEmpty()) {
            return List.of();
        }
        ItemStack one = stack.copyWithCount(1);
        List<RecipeHolder<CrucibleRecipe>> out = new ArrayList<>();
        int a = 0;
        int b = 0;
        while (a < listed.size() || b < complex.size()) {
            RecipeHolder<CrucibleRecipe> next;
            if (b >= complex.size()
                    || (a < listed.size() && BY_ID.compare(listed.get(a), complex.get(b)) < 0)) {
                next = listed.get(a++);
            } else {
                next = complex.get(b++);
            }
            CrucibleRecipe recipe = next.value();
            if (learned.test(recipe.research()) && recipe.catalyst().test(one)) {
                out.add(next);
            }
        }
        return out;
    }
}
