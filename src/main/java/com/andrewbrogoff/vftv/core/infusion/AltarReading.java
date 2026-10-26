package com.andrewbrogoff.vftv.core.infusion;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.essentia.EssentiaKeys;
import com.andrewbrogoff.vftv.api.essentia.SpillCause;
import com.andrewbrogoff.vftv.api.flux.FluxRules;
import com.andrewbrogoff.vftv.api.infusion.InfusionCost;
import com.andrewbrogoff.vftv.api.infusion.InfusionKeys;
import com.andrewbrogoff.vftv.api.infusion.InfusionLimits;
import com.andrewbrogoff.vftv.api.infusion.InfusionPlan;
import com.andrewbrogoff.vftv.core.flux.FluxRulesService;
import com.andrewbrogoff.vftv.core.flux.model.FluxMath;
import com.andrewbrogoff.vftv.core.infusion.model.InfusionMath;
import com.andrewbrogoff.vftv.core.infusion.model.InfusionTuning;
import com.andrewbrogoff.vftv.core.infusion.model.RunRules;
import com.andrewbrogoff.vftv.core.research.ResearchService;
import com.andrewbrogoff.vftv.core.research.model.ResearchRecord;
import com.andrewbrogoff.vftv.core.warp.WarpDials;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.TransmuteRecipe;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jspecify.annotations.Nullable;

/**
 * Reading an altar (infusion spec, "Reading an altar"): the server's snapshot of the box, the
 * resolution of every Minecraft predicate, and the assessment the model's formulas give. The
 * snapshot is callback-free, so rebuilding it is the commit's live fingerprint; selection runs the
 * foreign callbacks (custom ingredients, {@code supportsEnchantment}).
 */
final class AltarReading {
    private AltarReading() {}

    /** A pedestal's claim, as an interaction finds it; a dead claim is cleared and reads NONE. */
    enum ClaimState {
        NONE,
        LIVE,
        UNKNOWN,
        UNRESOLVED
    }

    /**
     * One pedestal at the bowl's level.
     *
     * @param pos its position
     * @param be its block entity (null if missing)
     * @param item a copy of its item
     * @param state its claim's state
     * @param claim its claim, as read
     */
    record Cell(
            BlockPos pos,
            @Nullable PedestalBlockEntity be,
            ItemStack item,
            ClaimState state,
            PedestalBlockEntity.@Nullable Claim claim) {}

    /** Everything a plan depends on; the arm's revision bumps whenever these differ. */
    static final class Inputs {
        final BowlBlockEntity bowl;
        final long bowlId;
        final int runs;
        final ItemStack centre;
        final boolean loaded;
        final long[] pedestals = new long[InfusionMath.WORDS];
        final long[] items = new long[InfusionMath.WORDS];
        final long[] stabilisers = new long[InfusionMath.WORDS];
        final long[] endpoints = new long[InfusionMath.WORDS];
        final List<Cell> cells = new ArrayList<>();
        final long recipes;
        final long rules;
        final long research;
        final int warp;
        final long flux;

        Inputs(ServerLevel level, BowlBlockEntity bowl, ServerPlayer player, int warp) {
            this.bowl = bowl;
            this.bowlId = bowl.id;
            this.runs = bowl.runs;
            this.centre = bowl.item.copy();
            this.loaded = boxLoaded(level, bowl.getBlockPos());
            BlockPos at = bowl.getBlockPos();
            if (loaded) {
                for (int i = 0; i < InfusionMath.CELLS; i++) {
                    int dx = InfusionMath.dx(i);
                    int dy = InfusionMath.dy(i);
                    int dz = InfusionMath.dz(i);
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    BlockPos p = at.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(p);
                    if (isStabiliser(state)) {
                        InfusionMath.set(stabilisers, i);
                    }
                    if (state.is(EssentiaKeys.ESSENTIA_ENDPOINTS)) {
                        InfusionMath.set(endpoints, i);
                    }
                    if (dy == 0 && state.is(InfusionContent.pedestal())) {
                        InfusionMath.set(pedestals, i);
                        BlockEntity be = level.getBlockEntity(p);
                        PedestalBlockEntity pedestal =
                                be instanceof PedestalBlockEntity pe ? pe : null;
                        ItemStack item = pedestal == null ? ItemStack.EMPTY : pedestal.item.copy();
                        if (!item.isEmpty()) {
                            InfusionMath.set(items, i);
                        }
                        ClaimState claim =
                                pedestal == null
                                        ? ClaimState.NONE
                                        : InfusionAltars.claimState(level, pedestal);
                        cells.add(
                                new Cell(
                                        p,
                                        pedestal,
                                        item,
                                        claim,
                                        pedestal == null ? null : pedestal.claim));
                    }
                }
                cells.sort(Comparator.comparingLong(c -> c.pos().asLong()));
            }
            this.recipes = InfusionRecipes.server().generation();
            this.rules = InfusionAltars.rules().generation();
            ResearchRecord record = ResearchService.record(player);
            this.research = record == null ? -1 : record.revision();
            this.warp = warp;
            FluxRules f = FluxRulesService.current();
            this.flux = f.generation();
        }

        /** Whether every input is the same: instances, copies and generations. */
        @SuppressWarnings("ReferenceEquality") // block-entity identity is an input
        boolean same(@Nullable Inputs o) {
            if (o == null
                    || o.bowl != bowl
                    || o.bowlId != bowlId
                    || o.runs != runs
                    || o.loaded != loaded
                    || !ItemStack.matches(o.centre, centre)
                    || !Arrays.equals(o.pedestals, pedestals)
                    || !Arrays.equals(o.items, items)
                    || !Arrays.equals(o.stabilisers, stabilisers)
                    || !Arrays.equals(o.endpoints, endpoints)
                    || o.recipes != recipes
                    || o.rules != rules
                    || o.research != research
                    || o.warp != warp
                    || o.flux != flux
                    || o.cells.size() != cells.size()) {
                return false;
            }
            for (int i = 0; i < cells.size(); i++) {
                Cell a = cells.get(i);
                Cell b = o.cells.get(i);
                if (a.be() != b.be()
                        || !a.pos().equals(b.pos())
                        || !ItemStack.matches(a.item(), b.item())
                        || a.state() != b.state()
                        || !java.util.Objects.equals(a.claim(), b.claim())
                        || (a.be() != null && a.be().isRemoved())) {
                    return false;
                }
            }
            return true;
        }

        /** The pedestal cells holding an item, in pedestal order. */
        List<Cell> loaded() {
            List<Cell> out = new ArrayList<>();
            for (Cell c : cells) {
                if (!c.item().isEmpty()) {
                    out.add(c);
                }
            }
            return out;
        }
    }

    /**
     * What a selection chose.
     *
     * @param holder the recipe
     * @param output the frozen output (transmuted or enchanted as the recipe says)
     * @param parts the matched pedestals in absorb order, each with its promised copy
     * @param aspects the cost aspects, ascending
     * @param costs the effective costs, parallel to {@code aspects}
     * @param r the recipe's instability with any enchanting term
     */
    record Selected(
            RecipeHolder<InfusionRecipe> holder,
            ItemStack output,
            List<Cell> parts,
            List<ResourceKey<Aspect>> aspects,
            List<Integer> costs,
            int r) {}

    /**
     * An assessment: its inputs, the readout, and for {@code READY} the selection with the frozen
     * stability and rules.
     */
    record Assessment(
            Inputs inputs,
            InfusionReadout readout,
            @Nullable Selected selected,
            InfusionRun.@Nullable Stability stability,
            @Nullable RunRules rules) {}

    /** Whether every box chunk is loaded and block-ticking; nothing is loaded here. */
    static boolean boxLoaded(ServerLevel level, BlockPos bowl) {
        int r = InfusionLimits.BOX_RADIUS;
        for (int cx = (bowl.getX() - r) >> 4; cx <= (bowl.getX() + r) >> 4; cx++) {
            for (int cz = (bowl.getZ() - r) >> 4; cz <= (bowl.getZ() + r) >> 4; cz++) {
                if (level.getChunkSource().getChunkNow(cx, cz) == null
                        || !level.shouldTickBlocksAt(ChunkPos.pack(cx, cz))) {
                    return false;
                }
            }
        }
        return true;
    }

    /** A stabiliser: in the tag, with no {@code lit} property or lit. */
    static boolean isStabiliser(BlockState state) {
        return state.is(InfusionKeys.INFUSION_STABILISERS)
                && (!state.hasProperty(BlockStateProperties.LIT)
                        || state.getValue(BlockStateProperties.LIT));
    }

    /**
     * Assesses an idle bowl for a striker (infusion spec, "Statuses", "Selection", "Stability").
     *
     * @param held "held now" per cost aspect to reuse (an arm's refresh), or null to compute it
     */
    static Assessment assess(
            ServerLevel level,
            BowlBlockEntity bowl,
            ServerPlayer player,
            @Nullable Map<ResourceKey<Aspect>, Integer> held) {
        BlockPos pos = bowl.getBlockPos();
        // Wp needs the recipe's kind; the input is the ordinary penalty, the kind's at selection.
        int ordinaryWarp =
                WarpDials.infusionPenalty(player, com.andrewbrogoff.vftv.api.vis.VisKind.ORDINARY);
        Inputs inputs = new Inputs(level, bowl, player, ordinaryWarp);
        if (bowl.unreadable()) {
            return simple(inputs, level, pos, InfusionStatus.UNREADABLE, List.of());
        }
        if (!inputs.loaded) {
            return simple(inputs, level, pos, InfusionStatus.UNLOADED, List.of());
        }
        if (bowl.item.isEmpty()) {
            return simple(inputs, level, pos, InfusionStatus.EMPTY, List.of());
        }
        if (inputs.cells.size() > InfusionLimits.MAX_PEDESTALS) {
            return simple(
                    inputs,
                    level,
                    pos,
                    InfusionStatus.TOO_MANY,
                    List.of(Component.literal(Integer.toString(inputs.cells.size()))));
        }
        for (Cell c : inputs.cells) {
            if (c.be() == null || c.be().unreadable()) {
                return simple(inputs, level, pos, InfusionStatus.PEDESTAL_UNREADABLE, xyz(c.pos()));
            }
        }
        for (Cell c : inputs.cells) {
            if (c.state() != ClaimState.NONE) {
                return simple(inputs, level, pos, InfusionStatus.PROMISED, xyz(c.pos()));
            }
        }
        boolean busy = bowl.busy();
        for (Cell c : inputs.cells) {
            busy |= c.be() != null && c.be().busy();
        }
        if (busy) {
            return simple(inputs, level, pos, InfusionStatus.BUSY, List.of());
        }
        return select(level, bowl, player, inputs, held);
    }

    private static Assessment select(
            ServerLevel level,
            BowlBlockEntity bowl,
            ServerPlayer player,
            Inputs inputs,
            @Nullable Map<ResourceKey<Aspect>, Integer> held) {
        BlockPos pos = bowl.getBlockPos();
        ItemStack centre = inputs.centre.copyWithCount(1);
        List<Cell> items = inputs.loaded();
        InfusionTuning tuning = InfusionAltars.tuning();
        InfusionStatus.EnchantRefusal refusal = null;
        List<Component> refusalArgs = List.of();
        for (RecipeHolder<InfusionRecipe> holder :
                InfusionRecipes.server()
                        .candidates(centre, key -> ResearchService.isLearned(player, key))) {
            InfusionRecipe recipe = holder.value();
            if (recipe.components().size() != items.size()) {
                continue;
            }
            boolean[][] matches = new boolean[items.size()][items.size()];
            for (int c = 0; c < items.size(); c++) {
                for (int i = 0; i < items.size(); i++) {
                    matches[c][i] = recipe.components().get(c).test(items.get(i).item());
                }
            }
            int[] matching = InfusionMath.match(matches, items.size());
            if (matching == null) {
                continue;
            }
            ItemStack output;
            int r = recipe.instability();
            int[] costs = new int[recipe.essentia().size()];
            List<ResourceKey<Aspect>> aspects = new ArrayList<>(recipe.essentia().keySet());
            int k = 0;
            for (int cost : recipe.essentia().values()) {
                costs[k++] = cost;
            }
            switch (recipe.result()) {
                case InfusionRecipe.Result.Item item -> {
                    output =
                            item.transmute()
                                    ? TransmuteRecipe.createWithOriginalComponents(
                                            item.item(), centre)
                                    : item.item().create();
                    if (output.isEmpty()
                            || output.getCount() != 1
                            || output.getCount() > output.getMaxStackSize()) {
                        continue; // a transmuted output that isn't one item doesn't match
                    }
                }
                case InfusionRecipe.Result.Enchant e -> {
                    EnchantCheck check = enchant(centre, e);
                    if (check.refusal() != null) {
                        if (refusal == null) {
                            refusal = check.refusal();
                            refusalArgs = check.args();
                        }
                        continue;
                    }
                    output = centre.copy();
                    int cap = e.cap();
                    EnchantmentHelper.updateEnchantments(output, m -> m.set(e.enchantment(), cap));
                    int levels = InfusionMath.levels(check.from(), cap);
                    for (int i = 0; i < costs.length; i++) {
                        costs[i] *= levels;
                    }
                    r =
                            InfusionMath.enchantInstability(
                                    recipe.instability(),
                                    tuning.instabilityPerLevel(),
                                    check.onItem(),
                                    cap,
                                    check.from());
                }
            }
            List<Cell> parts = new ArrayList<>();
            for (int c = 0; c < matching.length; c++) {
                parts.add(items.get(matching[c]));
            }
            parts.sort(Comparator.comparingLong(c -> c.pos().asLong()));
            Selected selected =
                    new Selected(
                            holder,
                            output,
                            parts,
                            aspects,
                            java.util.Arrays.stream(costs).boxed().toList(),
                            Math.min(100, r));
            return ready(level, bowl, player, inputs, selected, held);
        }
        if (refusal != null) {
            return new Assessment(
                    inputs,
                    new InfusionReadout(
                            InfusionStatus.ENCHANT_REFUSED,
                            refusal,
                            refusalArgs,
                            level.dimension(),
                            pos,
                            null,
                            null,
                            false),
                    null,
                    null,
                    null);
        }
        return simple(
                inputs,
                level,
                pos,
                InfusionStatus.NO_RECIPE,
                List.of(Component.literal(Integer.toString(items.size()))));
    }

    /** An enchanting candidate's conditions, in order; the first failure is the reason. */
    private record EnchantCheck(
            InfusionStatus.@Nullable EnchantRefusal refusal,
            List<Component> args,
            int from,
            int onItem) {}

    private static EnchantCheck enchant(ItemStack centre, InfusionRecipe.Result.Enchant e) {
        Holder<Enchantment> holder = e.enchantment();
        if (centre.is(InfusionKeys.INFUSION_UNENCHANTABLE)
                || !EnchantmentHelper.canStoreEnchantments(centre)) {
            return new EnchantCheck(
                    InfusionStatus.EnchantRefusal.UNSTORABLE, List.of(centre.getHoverName()), 0, 0);
        }
        if (!centre.supportsEnchantment(holder)) {
            return new EnchantCheck(
                    InfusionStatus.EnchantRefusal.UNSUPPORTED,
                    List.of(holder.value().description(), centre.getHoverName()),
                    0,
                    0);
        }
        ItemEnchantments on = EnchantmentHelper.getEnchantmentsForCrafting(centre);
        int from = on.getLevel(holder);
        int cap = e.cap();
        if (from >= cap) {
            return new EnchantCheck(
                    InfusionStatus.EnchantRefusal.AT_MAX,
                    List.of(holder.value().description(), Component.literal(Integer.toString(cap))),
                    from,
                    0);
        }
        int onItem = 0;
        Holder<Enchantment> conflict = null;
        List<Holder<Enchantment>> others = new ArrayList<>(on.keySet());
        others.sort(
                Comparator.comparing(
                        h -> h.unwrapKey().map(k -> k.identifier().toString()).orElse("")));
        for (Holder<Enchantment> other : others) {
            onItem += on.getLevel(other);
            if (!other.equals(holder)
                    && conflict == null
                    && !Enchantment.areCompatible(holder, other)) {
                conflict = other;
            }
        }
        if (conflict != null) {
            return new EnchantCheck(
                    InfusionStatus.EnchantRefusal.CONFLICT,
                    List.of(holder.value().description(), conflict.value().description()),
                    from,
                    onItem);
        }
        return new EnchantCheck(null, List.of(), from, onItem);
    }

    private static Assessment ready(
            ServerLevel level,
            BowlBlockEntity bowl,
            ServerPlayer player,
            Inputs inputs,
            Selected s,
            @Nullable Map<ResourceKey<Aspect>, Integer> held) {
        RunRules rules = InfusionAltars.tuning().runRules();
        int wp = WarpDials.infusionPenalty(player, s.holder().value().kind());
        int l = InfusionMath.lonePedestals(inputs.pedestals);
        int i = InfusionMath.loneItems(inputs.pedestals, inputs.items);
        int p = InfusionMath.matchedPairs(inputs.stabilisers);
        int b = InfusionMath.bonus(p, rules.maxPairs(), rules.perPair());
        long raw =
                InfusionMath.rawStability(
                        s.r(), l, i, wp, b, rules.lonePedestal(), rules.loneItem());
        int stability = InfusionMath.stability(raw);
        int chance = InfusionMath.chance(stability, rules.maxChance());
        int components = s.parts().size();
        int[] cost = s.costs().stream().mapToInt(Integer::intValue).toArray();
        int nominal = InfusionMath.nominal(cost, rules.rate(), components);
        int worst = InfusionMath.worst(cost, components);
        int ceiling = InfusionMath.ceiling(worst, rules.max(), rules.spill());
        long fluxBound =
                (long) rules.max()
                        * FluxMath.essentiaFluxBound(
                                rules.spill(), SpillCause.UPSET, FluxRulesService.current());
        long[] now =
                held == null
                        ? InfusionAltars.hooks().heldNow(level, bowl.getBlockPos(), s.aspects())
                        : null;
        List<InfusionCost> costs = new ArrayList<>();
        for (int a = 0; a < s.aspects().size(); a++) {
            int h =
                    now != null
                            ? (int) Math.min(InfusionLimits.HELD_NOW_MAX, Math.max(0, now[a]))
                            : java.util.Objects.requireNonNull(held)
                                    .getOrDefault(s.aspects().get(a), 0);
            costs.add(new InfusionCost(s.aspects().get(a), s.costs().get(a), h));
        }
        InfusionPlan plan =
                new InfusionPlan(
                        s.holder().id(),
                        s.holder().value().kind(),
                        s.output(),
                        s.r(),
                        wp,
                        l,
                        i,
                        p,
                        b,
                        rules.maxPairs(),
                        stability,
                        chance,
                        nominal,
                        worst,
                        ceiling,
                        InfusionMath.clean(chance, worst),
                        InfusionMath.clean(chance, nominal),
                        rules.max(),
                        rules.spill(),
                        fluxBound,
                        costs,
                        p > rules.maxPairs(),
                        raw != stability);
        InfusionReadout readout =
                new InfusionReadout(
                        InfusionStatus.READY,
                        null,
                        List.of(s.output().getHoverName()),
                        level.dimension(),
                        bowl.getBlockPos(),
                        plan,
                        null,
                        false);
        return new Assessment(
                inputs,
                readout,
                s,
                new InfusionRun.Stability(s.r(), l, i, p, b, wp, stability, chance),
                rules);
    }

    private static Assessment simple(
            Inputs inputs,
            ServerLevel level,
            BlockPos pos,
            InfusionStatus status,
            List<Component> args) {
        return new Assessment(
                inputs,
                new InfusionReadout(status, null, args, level.dimension(), pos, null, null, false),
                null,
                null,
                null);
    }

    /** A position as the three plain-integer arguments a key takes. */
    static List<Component> xyz(BlockPos p) {
        return List.of(
                Component.literal(Integer.toString(p.getX())),
                Component.literal(Integer.toString(p.getY())),
                Component.literal(Integer.toString(p.getZ())));
    }
}
