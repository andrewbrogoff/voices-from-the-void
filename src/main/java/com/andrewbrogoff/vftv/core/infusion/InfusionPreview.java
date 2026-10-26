package com.andrewbrogoff.vftv.core.infusion;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.infusion.InfusionCost;
import com.andrewbrogoff.vftv.api.infusion.InfusionPlan;
import com.andrewbrogoff.vftv.api.vis.VisKind;
import com.andrewbrogoff.vftv.core.aspect.AspectLang;
import com.andrewbrogoff.vftv.core.aspect.KnowledgeService;
import com.andrewbrogoff.vftv.core.aspect.model.Knowledge;
import com.andrewbrogoff.vftv.core.infusion.InfusionPayloads.Kind;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The authoritative preview of one infusion recipe (infusion spec, "The authoritative preview"):
 * the only cost source for recipe pages, fact fields and JEI. With a context (the player's current
 * readout of the targeted bowl, for this recipe) it shows that readout's figures; without one, no
 * stability, chance, clean figure, flux, flag or availability. It decides nothing.
 *
 * @param recipe the recipe id
 * @param kind the recipe's kind
 * @param output the output: with a context the readout's; without, the created result (for
 *     enchanting, empty: the fact says "raised to n")
 * @param centre the centre's slot display
 * @param components the components' slot displays
 * @param essentia the costs, ascending, each marked discovered for the reader: effective with a
 *     context, per level for enchanting without one
 * @param perLevel whether {@code essentia} is per level (enchanting without a context)
 * @param instability {@code R} with a context; the recipe's own without
 * @param context the context, or null
 * @param availability first match of the context's state
 */
public record InfusionPreview(
        Identifier recipe,
        VisKind kind,
        ItemStack output,
        SlotDisplay centre,
        List<SlotDisplay> components,
        List<Cost> essentia,
        boolean perLevel,
        int instability,
        @Nullable Context context,
        Availability availability) {
    /** The fact fields, for the {@code recipe} source. */
    public static final Set<String> FIELDS =
            Set.of(
                    "output",
                    "inputs",
                    "essentia",
                    "instability",
                    "stability",
                    "chance",
                    "clean_floor",
                    "clean_full_supply",
                    "clean",
                    "upset_flux",
                    "abandon_flux",
                    "flux",
                    "stabilisers_capped",
                    "clamped",
                    "status");

    /** Whether, and how, the context stands. */
    public enum Availability {
        /** No readout of this recipe at the targeted bowl. */
        NO_ALTAR,
        /** A run of this recipe is ringing. */
        RINGING,
        /** A cost aspect is held short now; a start is still allowed. */
        SHORT_NOW,
        /** Ready. */
        READY
    }

    /**
     * One cost entry.
     *
     * @param aspect the aspect
     * @param amount the exact amount
     * @param discovered whether the reader has discovered it
     */
    public record Cost(ResourceKey<Aspect> aspect, int amount, boolean discovered) {}

    /**
     * The preview's context: the identity of the readout it reads.
     *
     * @param kind {@code ARMED} or {@code RUN}
     * @param dimension the bowl's dimension
     * @param pos the bowl's position
     * @param revision the readout's revision
     * @param readout the readout
     */
    public record Context(
            Kind kind,
            ResourceKey<Level> dimension,
            BlockPos pos,
            int revision,
            InfusionReadout readout) {}

    /**
     * The preview for a reader against a context: null, or a readout that is this recipe's ({@code
     * ARMED} and {@code READY}, or a {@code RUN} of it). Any other context counts as none.
     */
    public static InfusionPreview of(
            Player player, RecipeHolder<InfusionRecipe> holder, @Nullable Context context) {
        InfusionRecipe recipe = holder.value();
        Context ctx =
                context != null && holder.id().equals(context.readout().recipe()) ? context : null;
        Knowledge known = KnowledgeService.get(player);
        List<Cost> costs = new ArrayList<>();
        InfusionPlan plan = ctx == null ? null : ctx.readout().ready();
        InfusionReadout.Run run = ctx == null ? null : ctx.readout().run();
        boolean shortNow = false;
        List<InfusionCost> cells =
                plan != null ? plan.costs() : run != null ? run.costs() : List.of();
        if (!cells.isEmpty()) {
            for (InfusionCost c : cells) {
                costs.add(new Cost(c.aspect(), c.cost(), heard(known, c.aspect())));
                shortNow |= plan != null && c.heldNow() < c.cost();
            }
        } else {
            recipe.essentia().forEach((k, v) -> costs.add(new Cost(k, v, heard(known, k))));
        }
        ItemStack output =
                plan != null
                        ? plan.output()
                        : run != null
                                ? run.output()
                                : recipe.result() instanceof InfusionRecipe.Result.Item item
                                        ? item.item().create()
                                        : ItemStack.EMPTY;
        List<SlotDisplay> parts = new ArrayList<>();
        recipe.components().forEach(c -> parts.add(c.display()));
        Availability availability =
                ctx == null
                        ? Availability.NO_ALTAR
                        : run != null
                                ? Availability.RINGING
                                : shortNow ? Availability.SHORT_NOW : Availability.READY;
        return new InfusionPreview(
                holder.id().identifier(),
                recipe.kind(),
                output,
                recipe.centre().display(),
                List.copyOf(parts),
                List.copyOf(costs),
                ctx == null && recipe.result() instanceof InfusionRecipe.Result.Enchant,
                plan != null ? plan.instability() : recipe.instability(),
                ctx,
                availability);
    }

    private static boolean heard(@Nullable Knowledge known, ResourceKey<Aspect> aspect) {
        return known != null && known.known().contains(aspect.identifier().toString());
    }

    /** A per-mille value as "n.d%". */
    public static String perMille(int value) {
        return value / 10 + "." + value % 10 + "%";
    }

    /**
     * A fact field's value (infusion spec, fact fields), or null for an unknown field. An absent
     * value renders the absent text for the availability, never a number.
     *
     * @param recipe the recipe, for the enchanting fact
     * @param centreItems the centre's alternatives, first listed first
     * @param componentItems each component's alternatives, first listed first
     */
    public @Nullable Component fact(
            InfusionRecipe recipe,
            String field,
            List<Holder<Item>> centreItems,
            List<List<Holder<Item>>> componentItems) {
        InfusionPlan plan = context == null ? null : context.readout().ready();
        InfusionReadout.Run run = context == null ? null : context.readout().run();
        return switch (field) {
            case "output" ->
                    output.isEmpty() && recipe.result() instanceof InfusionRecipe.Result.Enchant e
                            ? Component.translatable(
                                    InfusionLang.FACT_ENCHANT,
                                    e.enchantment().value().description(),
                                    Integer.toString(e.cap()))
                            : output.getHoverName();
            case "inputs" -> {
                MutableComponent text = Component.empty();
                List<List<Holder<Item>>> all = new ArrayList<>();
                all.add(centreItems);
                all.addAll(componentItems);
                boolean first = true;
                for (List<Holder<Item>> alternatives : all) {
                    if (alternatives.isEmpty()) {
                        continue;
                    }
                    if (!first) {
                        text.append(", ");
                    }
                    first = false;
                    text.append("1 × ")
                            .append(new ItemStack(alternatives.getFirst()).getHoverName());
                }
                yield text;
            }
            case "essentia" -> {
                MutableComponent text = Component.empty();
                for (int i = 0; i < essentia.size(); i++) {
                    Cost c = essentia.get(i);
                    if (i > 0) {
                        text.append(", ");
                    }
                    text.append(
                                    c.discovered()
                                            ? AspectLang.name(c.aspect())
                                            : Component.translatable(InfusionLang.UNHEARD))
                            .append(" " + c.amount());
                }
                if (perLevel) {
                    text.append(Component.translatable(InfusionLang.FACT_PER_LEVEL));
                }
                yield text;
            }
            case "instability" -> Component.literal(Integer.toString(instability));
            case "stability" ->
                    plan != null
                            ? Component.literal(plan.stability() + "%")
                            : run != null
                                    ? Component.literal(run.stability() + "%")
                                    : absent(field);
            case "chance" ->
                    plan != null
                            ? Component.literal(perMille(plan.upsetChance()))
                            : run != null
                                    ? Component.literal(perMille(run.chance()))
                                    : absent(field);
            case "clean_floor", "clean" ->
                    plan != null ? Component.literal(perMille(plan.cleanFloor())) : absent(field);
            case "clean_full_supply" ->
                    plan != null
                            ? Component.literal(perMille(plan.cleanFullSupply()))
                            : absent(field);
            case "upset_flux", "flux" ->
                    plan != null
                            ? Component.literal(Long.toString(plan.upsetFluxBound()))
                            : run != null
                                    ? Component.literal(Long.toString(run.upsetFlux()))
                                    : absent(field);
            case "abandon_flux" ->
                    run != null
                            ? Component.literal(Integer.toString(run.abandonFlux()))
                            : absent(field);
            case "stabilisers_capped" ->
                    plan != null ? yesNo(plan.stabilisersCapped()) : absent(field);
            case "clamped" -> plan != null ? yesNo(plan.clamped()) : absent(field);
            case "status" ->
                    switch (availability) {
                        case NO_ALTAR ->
                                Component.translatable(InfusionLang.PREVIEW_STATUS + "no_altar");
                        case SHORT_NOW ->
                                Component.translatable(InfusionLang.PREVIEW_STATUS + "short_now");
                        case READY -> Component.translatable(InfusionLang.PREVIEW_STATUS + "ready");
                        case RINGING -> {
                            InfusionReadout r = java.util.Objects.requireNonNull(context).readout();
                            yield Component.translatable(r.key(), r.args().toArray());
                        }
                    };
            default -> null;
        };
    }

    private Component absent(String field) {
        if (context == null) {
            return Component.translatable(InfusionLang.PREVIEW_STATUS + "no_altar");
        }
        return field.startsWith("clean")
                ? Component.translatable(InfusionLang.PREVIEW_STATUS + "ringing_only")
                : Component.translatable(InfusionLang.PREVIEW_STATUS + "not_ringing");
    }

    private static Component yesNo(boolean value) {
        return Component.translatable(value ? InfusionLang.YES : InfusionLang.NO);
    }

    /** The context line, {@code infusion.vftv.checked_at}, or null without a context. */
    public @Nullable Component checkedAt() {
        Context c = context;
        return c == null
                ? null
                : Component.translatable(
                        InfusionLang.CHECKED_AT, AltarReading.xyz(c.pos()).toArray());
    }
}
