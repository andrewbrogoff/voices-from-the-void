package com.andrewbrogoff.vftv.core.infusion;

import com.andrewbrogoff.vftv.api.infusion.InfusionLimits;
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
 * One side's immutable set of {@code vftv:infusion} recipes (infusion spec, "Recipe sync and
 * reload"), following the crucible snapshot: sorted by id, indexed by simple centre item, plus one
 * id-ordered list of non-simple centres, and a generation that arms compare. Replaced whole.
 */
public final class InfusionRecipeSnapshot {
    /** No recipes. */
    public static final InfusionRecipeSnapshot EMPTY = new InfusionRecipeSnapshot(List.of(), 0);

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Comparator<RecipeHolder<?>> BY_ID =
            Comparator.comparing(h -> h.id().identifier().toString());

    private final List<RecipeHolder<InfusionRecipe>> recipes;
    private final long generation;
    private final Map<Identifier, RecipeHolder<InfusionRecipe>> byId = new HashMap<>();
    private final Map<Item, List<RecipeHolder<InfusionRecipe>>> simple = new HashMap<>();
    private final List<RecipeHolder<InfusionRecipe>> complex = new ArrayList<>();

    private InfusionRecipeSnapshot(List<RecipeHolder<InfusionRecipe>> sorted, long generation) {
        recipes = List.copyOf(sorted);
        this.generation = generation;
        for (RecipeHolder<InfusionRecipe> holder : recipes) {
            byId.put(holder.id().identifier(), holder);
            if (!holder.value().centre().isCustom()) {
                holder.value().centre().getValues().stream()
                        .map(Holder::value)
                        .distinct()
                        .forEach(
                                i -> simple.computeIfAbsent(i, k -> new ArrayList<>()).add(holder));
            } else {
                complex.add(holder);
            }
        }
    }

    /**
     * Builds a snapshot with this generation. A recipe whose item result isn't one item is left out
     * with a logged error. More than {@value InfusionLimits#MAX_INFUSION_RECIPES} is an error.
     */
    public static DataResult<InfusionRecipeSnapshot> of(
            Collection<RecipeHolder<InfusionRecipe>> holders, long generation) {
        List<RecipeHolder<InfusionRecipe>> valid = new ArrayList<>(holders.size());
        for (RecipeHolder<InfusionRecipe> holder : holders) {
            holder.value()
                    .checkResult()
                    .ifSuccess(r -> valid.add(holder))
                    .ifError(
                            e ->
                                    LOGGER.error(
                                            "Skipping infusion recipe {}: {}",
                                            holder.id().identifier(),
                                            e.message()));
        }
        if (valid.size() > InfusionLimits.MAX_INFUSION_RECIPES) {
            int count = valid.size();
            return DataResult.error(
                    () ->
                            count
                                    + " infusion recipes exceed the cap of "
                                    + InfusionLimits.MAX_INFUSION_RECIPES);
        }
        valid.sort(BY_ID);
        return DataResult.success(new InfusionRecipeSnapshot(valid, generation));
    }

    /** +1 per replacement; arms compare it. */
    public long generation() {
        return generation;
    }

    /** Every recipe, in ascending id. */
    public List<RecipeHolder<InfusionRecipe>> recipes() {
        return recipes;
    }

    /** The recipe with this id, or null. */
    public @Nullable RecipeHolder<InfusionRecipe> get(Identifier id) {
        return byId.get(id);
    }

    /**
     * The candidates (infusion spec, "Selection"): recipes whose centre accepts a count-one copy of
     * {@code centre} and whose research is learned, in ascending id.
     */
    public List<RecipeHolder<InfusionRecipe>> candidates(
            ItemStack centre, Predicate<ResourceKey<Research>> learned) {
        List<RecipeHolder<InfusionRecipe>> listed =
                simple.getOrDefault(centre.getItem(), List.of());
        if (listed.isEmpty() && complex.isEmpty()) {
            return List.of();
        }
        List<RecipeHolder<InfusionRecipe>> out = new ArrayList<>();
        int a = 0;
        int b = 0;
        while (a < listed.size() || b < complex.size()) {
            RecipeHolder<InfusionRecipe> next =
                    b >= complex.size()
                                    || (a < listed.size()
                                            && BY_ID.compare(listed.get(a), complex.get(b)) < 0)
                            ? listed.get(a++)
                            : complex.get(b++);
            if (learned.test(next.value().research()) && next.value().matchesCentre(centre)) {
                out.add(next);
            }
        }
        return out;
    }
}
