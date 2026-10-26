package com.andrewbrogoff.vftv.core.arcane;

import com.andrewbrogoff.vftv.api.arcane.ArcaneLimits;
import com.andrewbrogoff.vftv.api.vis.VisContext;
import com.andrewbrogoff.vftv.api.vis.VisQuote;
import com.andrewbrogoff.vftv.core.arcane.model.WorktableRules;
import com.andrewbrogoff.vftv.core.aspect.AspectLang;
import com.andrewbrogoff.vftv.core.common.TickBudget;
import com.andrewbrogoff.vftv.core.text.model.NumberFormats;
import com.andrewbrogoff.vftv.core.vis.VisContent;
import com.andrewbrogoff.vftv.core.vis.VisLang;
import com.andrewbrogoff.vftv.core.vis.VisService;
import com.andrewbrogoff.vftv.core.vis.VisService.SlotChange;
import com.andrewbrogoff.vftv.core.vis.WandParts;
import com.andrewbrogoff.vftv.core.vis.WandState;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.WeakHashMap;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Crafting at the Worktable (arcane spec, "Crafting"): the one evaluation the client predicts its
 * status line from and the server checks with, and the server handler of {@link WorktableCraft},
 * the only path that commits a craft.
 */
public final class Worktables {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile ArcaneHooks hooks = ArcaneHooks.NONE;

    /** Check 1: each player's last craft-payload tick (server thread only; weak, so no cleanup). */
    private static final Map<ServerPlayer, Long> LAST_CRAFT = new WeakHashMap<>();

    private Worktables() {}

    /** Installs the platform's hooks, once at mod construction. */
    public static void install(ArcaneHooks platform) {
        hooks = platform;
    }

    /** The platform's hooks. */
    static ArcaneHooks hooks() {
        return hooks;
    }

    /**
     * One evaluation of the menu's state, in the status precedence.
     *
     * @param status the first status that applies; {@code NO_RECIPE} for an empty grid
     * @param empty whether the grid is empty (the client then shows no status line)
     * @param recipe the selected recipe, or null before selection succeeds
     * @param result the selected recipe's created result, or empty
     * @param quote the quote for the station's wand, or null before it is priced
     * @param grid the planned grid changes, or null before planning succeeds
     * @param output the planned output change, or null unless ready
     */
    public record Evaluation(
            WorktableStatus status,
            boolean empty,
            @Nullable RecipeHolder<ArcaneRecipe> recipe,
            ItemStack result,
            @Nullable VisQuote quote,
            @Nullable List<SlotChange> grid,
            @Nullable SlotChange output) {
        /** Whether the craft can be activated. */
        public boolean ready() {
            return status == WorktableStatus.READY;
        }

        /**
         * The status line for a status (this one, or a shown refusal), with its arguments from this
         * evaluation; null for an empty grid behind a wand that is present and ready.
         */
        public @Nullable Component line(WorktableStatus shown) {
            if (shown == status
                    && empty
                    && status != WorktableStatus.NO_WAND
                    && status != WorktableStatus.INERT_WAND) {
                return null;
            }
            VisQuote q = quote;
            return switch (shown) {
                case READY ->
                        q != null && WorktableRules.taxed(q.warp(), q.spill())
                                ? Component.translatable(
                                        ArcaneLang.READY_TAXED,
                                        result.getHoverName(),
                                        NumberFormats.factor(q.warp()),
                                        NumberFormats.integer(q.spill()))
                                : Component.translatable(shown.key(), result.getHoverName());
                case SHORT ->
                        Component.translatable(
                                VisLang.SPEND_SHORT,
                                ComponentUtils.formatList(
                                        q == null ? List.of() : q.shortAspects(),
                                        AspectLang::name));
                default -> Component.translatable(shown.key());
            };
        }

        /**
         * The batch total's {@code n}: the batch cap, the crafts every pool can pay at the
         * effective cost, the smallest occupied grid stack and the output's room; 0 unless priced.
         */
        public int batchSize(WorktableMenu menu) {
            VisQuote q = quote;
            if (q == null || result.isEmpty()) {
                return 0;
            }
            Map<String, Integer> pools = new TreeMap<>();
            Map<String, Integer> costs = new TreeMap<>();
            ItemStack wand = menu.wandSlot().getItem(0);
            WandParts parts = WandParts.of(menu.player().level().registryAccess());
            if (WandState.of(wand, parts) instanceof WandState.Ready ready) {
                q.effective()
                        .forEach(
                                (a, n) -> {
                                    pools.put(a.identifier().toString(), ready.pool(a));
                                    costs.put(a.identifier().toString(), n);
                                });
            }
            int smallest = Integer.MAX_VALUE;
            for (int i = 0; i < menu.grid().getContainerSize(); i++) {
                ItemStack stack = menu.grid().getItem(i);
                if (!stack.isEmpty()) {
                    smallest = Math.min(smallest, stack.getCount());
                }
            }
            ItemStack held = menu.output().getItem(0);
            int room =
                    WorktableRules.outputRoom(
                            held.isEmpty() || ItemStack.isSameItemSameComponents(held, result),
                            held.getCount(),
                            result.getMaxStackSize(),
                            result.getCount());
            return WorktableRules.batchSize(
                    ArcaneLimits.MAX_BATCH,
                    WorktableRules.wandCrafts(pools, costs),
                    smallest,
                    room);
        }
    }

    /** The quote context of a recipe: its id, kind and research. */
    public static VisContext context(RecipeHolder<ArcaneRecipe> holder) {
        return new VisContext(
                holder.id().identifier(), holder.value().kind(), holder.value().research());
    }

    /** The menu's state for its player, as the client predicts it. */
    public static Evaluation evaluate(WorktableMenu menu) {
        return evaluate(menu.player(), menu, null, null);
    }

    /**
     * Checks 4-10 in the status line's precedence.
     *
     * @param expected the echoed recipe id (check 7), or null
     * @param pinned a batch's pinned recipe, matched instead of selecting, or null
     */
    private static Evaluation evaluate(
            Player player,
            WorktableMenu menu,
            @Nullable Identifier expected,
            @Nullable RecipeHolder<ArcaneRecipe> pinned) {
        ItemStack wand = menu.wandSlot().getItem(0);
        boolean empty = menu.grid().isEmpty();
        // Selection has no side effects, so it runs first: a disabled preview still shows what it
        // would make. The statuses below keep the spec's precedence.
        RecipeHolder<ArcaneRecipe> holder;
        if (pinned == null) {
            holder = ArcaneRecipes.select(player, menu.input()).orElse(null);
        } else {
            holder = !empty && pinned.value().matches(menu.input(), player.level()) ? pinned : null;
        }
        ItemStack result =
                holder == null
                        ? ItemStack.EMPTY
                        : holder.value().createResult().result().orElse(ItemStack.EMPTY);
        if (!wand.is(VisContent.wand())) {
            return refused(WorktableStatus.NO_WAND, empty, holder, result, null, null);
        }
        if (WandState.of(wand, WandParts.of(player.level().registryAccess())).inert()) {
            return refused(WorktableStatus.INERT_WAND, empty, holder, result, null, null);
        }
        if (holder == null) {
            return refused(WorktableStatus.NO_RECIPE, empty, null, ItemStack.EMPTY, null, null);
        }
        if (expected != null && !holder.id().identifier().equals(expected)) {
            return refused(WorktableStatus.STALE, empty, holder, result, null, null);
        }
        List<SlotChange> grid = WorktablePlan.grid(menu.grid());
        if (grid == null) {
            return refused(WorktableStatus.LEFTOVER, empty, holder, result, null, null);
        }
        VisQuote quote = VisService.quote(player, wand, holder.value().vis(), context(holder));
        if (quote.exceedsCapacity()) {
            return refused(WorktableStatus.EXCEEDS_CAPACITY, empty, holder, result, quote, grid);
        }
        if (!quote.affordable()) {
            return refused(WorktableStatus.SHORT, empty, holder, result, quote, grid);
        }
        SlotChange output = WorktablePlan.output(menu.output(), result);
        if (output == null) {
            return refused(WorktableStatus.OUTPUT_FULL, empty, holder, result, quote, grid);
        }
        return new Evaluation(WorktableStatus.READY, empty, holder, result, quote, grid, output);
    }

    private static Evaluation refused(
            WorktableStatus status,
            boolean empty,
            @Nullable RecipeHolder<ArcaneRecipe> recipe,
            ItemStack result,
            @Nullable VisQuote quote,
            @Nullable List<SlotChange> grid) {
        return new Evaluation(status, empty, recipe, result, quote, grid, null);
    }

    // --- the craft payload --------------------------------------------------------------------

    /** The server handler of {@link WorktableCraft}, on the server thread. */
    public static void onCraft(WorktableCraft payload, ServerPlayer player) {
        try (var _ = TickBudget.enter("arcane")) {
            craft(payload, player);
        }
    }

    private static void craft(WorktableCraft payload, ServerPlayer player) {
        long tick = player.level().getServer().getTickCount();
        Long last = LAST_CRAFT.put(player, tick);
        if ((last != null && last == tick)
                || player.isSpectator()
                || !(player.containerMenu instanceof WorktableMenu menu)
                || menu.containerId != payload.containerId()
                || !menu.stillValid(player)) {
            return;
        }
        menu.clearRefusal();
        menu.changeCheck();
        if (menu.revision() != payload.revision()) {
            menu.refuse(WorktableStatus.STALE);
            return;
        }
        Evaluation first = evaluate(player, menu, payload.recipeId(), null);
        RecipeHolder<ArcaneRecipe> pinned = first.recipe();
        if (!first.ready() || pinned == null) {
            menu.refuse(first.status());
            return;
        }
        VisQuote expected = payload.echo().quote();
        int iterations = payload.batch() ? ArcaneLimits.MAX_BATCH : 1;
        boolean committed = false;
        Evaluation now = first;
        for (int i = 0; i < iterations; i++) {
            if (i > 0) {
                if (player.isSpectator()
                        || player.containerMenu != menu
                        || !menu.stillValid(player)) {
                    break;
                }
                menu.changeCheck();
                if (menu.revision() != menu.ownRevision()) {
                    break;
                }
                now = evaluate(player, menu, null, pinned);
                if (!now.ready()) {
                    break;
                }
            }
            Iteration it = commit(player, menu, pinned, now, expected);
            if (it.refusal() != null) {
                if (i == 0) {
                    menu.refuse(it.refusal());
                }
                break;
            }
            committed = true;
            if (it.stop()) {
                break;
            }
        }
        BlockPos pos = menu.pos();
        if (committed && pos != null) {
            player.level()
                    .playSound(null, pos, ArcaneContent.craftSound(), SoundSource.BLOCKS, 1f, 1f);
        }
        menu.broadcastChanges();
    }

    /**
     * One iteration's outcome.
     *
     * @param refusal the refusal of a spend that committed nothing, or null after a commit
     * @param stop whether the batch stops after this committed iteration
     */
    private record Iteration(@Nullable WorktableStatus refusal, boolean stop) {}

    private static Iteration commit(
            ServerPlayer player,
            WorktableMenu menu,
            RecipeHolder<ArcaneRecipe> holder,
            Evaluation evaluation,
            VisQuote expected) {
        List<SlotChange> grid = evaluation.grid();
        SlotChange output = evaluation.output();
        if (grid == null || output == null) {
            throw new IllegalStateException("a ready evaluation carries its plan");
        }
        List<SlotChange> effect = new ArrayList<>(grid);
        effect.add(output);
        SimpleContainer before = new SimpleContainer(menu.grid().getContainerSize());
        for (int i = 0; i < before.getContainerSize(); i++) {
            before.setItem(i, menu.grid().getItem(i).copy());
        }
        List<ItemStack> consumed = new ArrayList<>(grid.size());
        grid.forEach(c -> consumed.add(c.expectedBefore().copyWithCount(1)));
        VisService.SpendResult spent;
        menu.suppress();
        try {
            spent =
                    VisService.spend(
                            player,
                            menu.wandSlot().getItem(0),
                            holder.value().vis(),
                            context(holder),
                            expected,
                            effect,
                            menu.boundary());
        } catch (RuntimeException e) {
            // Rolled back and logged by the spend.
            return new Iteration(WorktableStatus.ERROR, true);
        } finally {
            menu.endSuppression(false); // a refused spend never reaches its boundary
        }
        VisService.Outcome outcome = spent.outcome();
        if (!outcome.committed()) {
            return new Iteration(refusal(spent), true);
        }
        boolean stop = outcome == VisService.Outcome.COMMITTED_NOTIFY_FAILED;
        if (stop) {
            LOGGER.warn(
                    "A VisSpentEvent listener failed after arcane recipe {} committed; it stands",
                    holder.id().identifier());
        }
        ItemStack result = evaluation.result();
        stop |= !notify(holder, () -> result.copy().onCraftedBy(player, result.getCount()));
        stop |= !notify(holder, () -> hooks.crafted(player, result.copy(), before));
        stop |=
                !notify(
                        holder,
                        () ->
                                CriteriaTriggers.RECIPE_CRAFTED.trigger(
                                        player, holder.id(), consumed));
        return new Iteration(null, stop);
    }

    /** Runs one post-commit notification; a throw is logged with the recipe id, never a refusal. */
    private static boolean notify(RecipeHolder<ArcaneRecipe> holder, Runnable notification) {
        try {
            notification.run();
            return true;
        } catch (RuntimeException e) {
            LOGGER.error(
                    "A notification after arcane recipe {} committed threw; the craft stands",
                    holder.id().identifier(),
                    e);
            return false;
        }
    }

    /** The spend's refusal as a status (arcane spec, "SpendResult mapping"). */
    static WorktableStatus refusal(VisService.SpendResult result) {
        return switch (result.outcome()) {
            case INERT -> WorktableStatus.INERT_WAND;
            case UNLEARNED -> WorktableStatus.NO_RECIPE;
            case STALE -> WorktableStatus.STALE;
            case SHORT -> {
                VisQuote quote = result.quote();
                yield quote != null && quote.exceedsCapacity()
                        ? WorktableStatus.EXCEEDS_CAPACITY
                        : WorktableStatus.SHORT;
            }
            case SPENT, COMMITTED_NOTIFY_FAILED ->
                    throw new IllegalArgumentException("a committed spend is not a refusal");
        };
    }
}
