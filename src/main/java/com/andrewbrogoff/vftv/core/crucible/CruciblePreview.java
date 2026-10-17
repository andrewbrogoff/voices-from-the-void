package com.andrewbrogoff.vftv.core.crucible;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.core.aspect.KnowledgeService;
import com.andrewbrogoff.vftv.core.aspect.model.Knowledge;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import org.jspecify.annotations.Nullable;

/**
 * The authoritative preview of one crucible recipe (crucible spec, "The authoritative preview"):
 * the only cost source for recipe pages, fact fields and JEI. It decides nothing; the step decides
 * against the live pool.
 *
 * @param recipe the recipe id
 * @param output the created result
 * @param catalyst the catalyst's slot display
 * @param aspects the cost in ascending aspect id, each marked discovered for the reader
 * @param spill always 0: crafting spills nothing
 * @param availability first match of the context's state
 * @param shortfall cost minus pool per short aspect, empty unless {@code SHORT}
 * @param crucible the context's position, null iff {@code NO_CRUCIBLE}
 */
public record CruciblePreview(
        Identifier recipe,
        ItemStack output,
        SlotDisplay catalyst,
        List<Cost> aspects,
        int spill,
        Availability availability,
        SortedMap<String, Integer> shortfall,
        @Nullable BlockPos crucible) {
    /** The fact fields, for the {@code recipe} source. */
    public static final Set<String> FIELDS =
            Set.of("output", "inputs", "aspects", "flux", "status");

    /** Whether, and why not, the context can pay. */
    public enum Availability {
        /** No crucible is targeted. */
        NO_CRUCIBLE,
        /** The targeted crucible's record is unreadable. */
        UNREADABLE,
        /** It holds no water. */
        DRY,
        /** It is not heated. */
        COLD,
        /** It holds too little. */
        SHORT,
        /** It holds enough. */
        READY
    }

    /**
     * One cost entry.
     *
     * @param aspect the aspect
     * @param amount the exact cost
     * @param discovered whether the reader has discovered it
     */
    public record Cost(ResourceKey<Aspect> aspect, int amount, boolean discovered) {}

    /**
     * The preview for a reader against a context (the crucible they target, or null).
     *
     * @param context the targeted crucible's block entity, or null for none or an unloaded chunk
     */
    public static CruciblePreview of(
            Player player,
            RecipeHolder<CrucibleRecipe> holder,
            @Nullable CrucibleBlockEntity context) {
        CrucibleRecipe recipe = holder.value();
        Knowledge known = KnowledgeService.get(player);
        List<Cost> costs = new ArrayList<>();
        recipe.aspects()
                .forEach(
                        (k, v) ->
                                costs.add(
                                        new Cost(
                                                k,
                                                v,
                                                known != null
                                                        && known.known()
                                                                .contains(
                                                                        k.identifier()
                                                                                .toString()))));
        Availability availability;
        SortedMap<String, Integer> shortfall = new TreeMap<>();
        if (context == null) {
            availability = Availability.NO_CRUCIBLE;
        } else if (context.unreadable()) {
            availability = Availability.UNREADABLE;
        } else if (!context.getBlockState().getValue(CrucibleBlock.WATER)) {
            availability = Availability.DRY;
        } else if (!context.getBlockState().getValue(CrucibleBlock.BOILING)) {
            availability = Availability.COLD;
        } else {
            shortfall = context.pool().shortfall(recipe.cost());
            availability = shortfall.isEmpty() ? Availability.READY : Availability.SHORT;
        }
        return new CruciblePreview(
                holder.id().identifier(),
                recipe.result().create(),
                recipe.catalyst().display(),
                List.copyOf(costs),
                0,
                availability,
                shortfall,
                context == null ? null : context.getBlockPos());
    }

    /**
     * A fact field's value (crucible spec, fact fields), or null for an unknown field.
     *
     * @param reader whose discovered aspects mask the names
     * @param catalystItems the catalyst's alternatives, first listed first
     */
    public @Nullable Component fact(Player reader, String field, List<Holder<Item>> catalystItems) {
        return switch (field) {
            case "output" ->
                    output.getCount() > 1
                            ? Component.literal(output.getCount() + " × ")
                                    .append(output.getHoverName())
                            : output.getHoverName();
            case "inputs" ->
                    catalystItems.isEmpty()
                            ? Component.empty()
                            : Component.literal("1 × ")
                                    .append(new ItemStack(catalystItems.getFirst()).getHoverName());
            case "aspects" -> {
                SortedMap<String, Integer> all = new TreeMap<>();
                aspects.forEach(c -> all.put(c.aspect().identifier().toString(), c.amount()));
                yield CrucibleService.aspectList(reader, all);
            }
            case "flux" -> Component.literal("0");
            case "status" ->
                    switch (availability) {
                        case READY -> Component.translatable(CrucibleLang.STATUS + "ready");
                        case SHORT ->
                                Component.translatable(
                                        CrucibleLang.STATUS + "short",
                                        CrucibleService.aspectList(reader, shortfall));
                        case NO_CRUCIBLE ->
                                Component.translatable(CrucibleLang.STATUS + "no_crucible");
                        case UNREADABLE ->
                                Component.translatable(CrucibleLang.STATUS + "unreadable");
                        case DRY -> Component.translatable(CrucibleLang.STATUS + "dry");
                        case COLD -> Component.translatable(CrucibleLang.STATUS + "cold");
                    };
            default -> null;
        };
    }

    /** The context line, {@code crucible.vftv.checked_at}, or null without a context. */
    public @Nullable Component checkedAt() {
        BlockPos at = crucible;
        return at == null
                ? null
                : Component.translatable(
                        CrucibleLang.CHECKED_AT,
                        String.valueOf(at.getX()),
                        String.valueOf(at.getY()),
                        String.valueOf(at.getZ()));
    }
}
