package com.andrewbrogoff.vftv.core.warp;

import com.andrewbrogoff.vftv.api.warp.CommitResult;
import com.andrewbrogoff.vftv.api.warp.WarpChangedPayload;
import com.andrewbrogoff.vftv.api.warp.WarpCost;
import com.andrewbrogoff.vftv.api.warp.WarpLimits;
import com.andrewbrogoff.vftv.api.warp.WarpPreview;
import com.andrewbrogoff.vftv.api.warp.WarpRules;
import com.andrewbrogoff.vftv.api.warp.WarpSource;
import com.andrewbrogoff.vftv.api.warp.WarpSourceKind;
import com.andrewbrogoff.vftv.api.warp.WarpState;
import com.andrewbrogoff.vftv.core.common.DataSlot;
import com.andrewbrogoff.vftv.core.common.DataSlots;
import com.andrewbrogoff.vftv.core.common.OperationGuard;
import com.andrewbrogoff.vftv.core.common.RawRecordCodec.Retained;
import com.andrewbrogoff.vftv.core.warp.model.WarpMath;
import com.andrewbrogoff.vftv.core.warp.model.WarpPhase;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * A player's warp (warp spec): the {@code vftv:warp} record, its preview, commit and apply, the
 * schedule's decay and gear sample, Void work, the place queries and Silencing. Server thread only,
 * except the pure reads, which also serve the client's synced copy.
 *
 * <p>Every change is made under the player's warp guard and posts one {@code WarpChangedEvent} once
 * its writes are complete, while the guard is held; a warp change from inside a listener is refused
 * {@code BUSY}. Internal in v1: no addon commits or applies warp.
 */
public final class WarpService {
    /** The Joining milestone: Joined iff it is in {@code milestones_paid}. */
    public static final Identifier JOINING = Identifier.fromNamespaceAndPath("vftv", "joining");

    /** The tooltip line of an item with a {@code vftv:gear_warp} value, with that value. */
    public static final String GEAR_TOOLTIP = "tooltip.vftv.gear_warp";

    /** Ticks between gear samples (fixed in code). */
    public static final int GEAR_INTERVAL = 20;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final OperationGuard GUARD = new OperationGuard("warp");
    private static final List<VoidPlaceQuery> QUERIES = new CopyOnWriteArrayList<>();
    private static final Map<UUID, Transient> TRANSIENT = new HashMap<>();

    private static @Nullable DataSlot<Player, WarpAttachment> slot;
    private static Consumer<WarpChangedPayload> publisher = payload -> {};
    private static ToIntFunction<ItemStack> gearValue = stack -> 0;
    private static WornStacks worn = (player, budget, sink) -> 0;

    private WarpService() {}

    /**
     * Carried stacks beyond the vanilla inventory (Curios, when loaded): gives each non-empty stack
     * to {@code sink} in the collector's order until {@code budget} stacks or the sink says stop,
     * and returns how many it gave (compatibility, "Wiring the collector").
     */
    @FunctionalInterface
    public interface WornStacks {
        /** Collects at most {@code budget} stacks. */
        int collect(Player player, int budget, Predicate<ItemStack> sink);
    }

    /** Creates the attachment; called once from the feature's init. */
    static void init(DataSlots slots) {
        slot =
                slots.player(
                        "warp",
                        WarpAttachment.CODEC,
                        () -> WarpAttachment.EMPTY,
                        WarpAttachment.STREAM_CODEC);
    }

    /**
     * Takes the platform's parts: the {@code WarpChangedEvent} publisher, the {@code
     * vftv:gear_warp} lookup and the worn-slot collector. Called once from the NeoForge wiring.
     */
    public static void installPlatform(
            Consumer<WarpChangedPayload> events, ToIntFunction<ItemStack> gear, WornStacks extra) {
        publisher = events;
        gearValue = gear;
        worn = extra;
    }

    // ---- Reads -------------------------------------------------------------------------------

    /** The player's attachment value: on a client, the synced copy. */
    public static WarpAttachment get(Player player) {
        return slot().get(player);
    }

    /**
     * The player's readable record, or null when it is unreadable, which logs one WARN per player
     * per login on the server.
     */
    public static @Nullable WarpRecord record(Player player) {
        WarpAttachment value = get(player);
        WarpRecord readable = value.readable();
        if (readable == null && player instanceof ServerPlayer server) {
            Transient t = transientOf(server);
            if (!t.warned) {
                t.warned = true;
                String reason =
                        value.record() instanceof Retained.Unreadable<WarpRecord> u
                                ? u.reason()
                                : "?";
                LOGGER.warn(
                        "The warp record of {} is unreadable ({}); it is kept as it is",
                        player.getPlainTextName(),
                        reason);
            }
        }
        return readable;
    }

    /** Whether the player is Silenced: a pure read of the flag, false for an unreadable record. */
    public static boolean isSilenced(Player player) {
        WarpRecord r = get(player).readable();
        return r != null && r.silenced();
    }

    // ---- The commit --------------------------------------------------------------------------

    /**
     * The preview of {@code cost} from {@code source} (warp spec, "Preview"): pure over the record
     * and the rules. Status {@code OK} with the actual deltas, {@code UNREADABLE} or {@code
     * INVALID}; never a revision.
     */
    public static CommitResult preview(Player player, WarpCost cost, WarpSource source) {
        WarpRecord r = get(player).readable();
        if (r == null) {
            return result(CommitResult.Status.UNREADABLE, null);
        }
        WarpPreview actual = preview(r, cost, source, WarpRulesService.current());
        return actual == null
                ? result(CommitResult.Status.INVALID, null)
                : result(CommitResult.Status.OK, actual);
    }

    /**
     * A change the player saw previewed (warp spec, "Commit and apply"): {@code BUSY}, {@code
     * UNREADABLE}, {@code INVALID}, {@code STALE} with the current preview when it differs from
     * {@code expected}, or {@code OK} with the actual deltas and the new revision.
     */
    public static CommitResult commit(
            ServerPlayer player, WarpCost cost, WarpSource source, WarpPreview expected) {
        return change(player, cost, source, expected);
    }

    /**
     * A server-originated change with no echoed preview (decay, stilling, commands): as {@link
     * #commit} without the stale check.
     */
    public static CommitResult apply(ServerPlayer player, WarpCost cost, WarpSource source) {
        return change(player, cost, source, null);
    }

    private static CommitResult change(
            ServerPlayer player, WarpCost cost, WarpSource source, @Nullable WarpPreview expected) {
        try (OperationGuard.Scope scope = GUARD.tryEnter(player.getUUID())) {
            if (scope == null) {
                return result(CommitResult.Status.BUSY, null);
            }
            WarpRecord r = record(player);
            if (r == null) {
                return result(CommitResult.Status.UNREADABLE, null);
            }
            WarpRules rules = WarpRulesService.current();
            WarpPreview actual = preview(r, cost, source, rules);
            if (actual == null) {
                return result(CommitResult.Status.INVALID, null);
            }
            if (expected != null && !actual.equals(expected)) {
                return result(CommitResult.Status.STALE, actual);
            }
            List<Identifier> paid = r.milestonesPaid();
            Identifier id = source.id();
            if (id != null
                    && !paid.contains(id)
                    && WarpMath.isMilestone(
                            cost.permanent(), source.kind(), id.toString(), rules)) {
                paid = new ArrayList<>(paid);
                paid.add(id);
            }
            WarpRecord next =
                    next(
                            r,
                            r.temporary() + actual.dT(),
                            r.lasting() + actual.dL(),
                            r.permanent() + actual.dP(),
                            r.gear(),
                            WarpMath.counterAfterGain(r.counter(), WarpMath.gains(actual), rules),
                            paid,
                            r.silenced());
            write(player, r, next, source);
            return new CommitResult(
                    CommitResult.Status.OK, actual, OptionalLong.of(next.revision()));
        }
    }

    private static @Nullable WarpPreview preview(
            WarpRecord r, WarpCost cost, WarpSource source, WarpRules rules) {
        Identifier id = source.id();
        return WarpMath.preview(
                r.temporary(),
                r.lasting(),
                r.permanent(),
                r.paid(),
                cost.temporary(),
                cost.lasting(),
                cost.permanent(),
                source.kind(),
                id == null ? null : id.toString(),
                rules);
    }

    // ---- Silencing ---------------------------------------------------------------------------

    /**
     * Silencing (warp spec, "Joining and Silencing"), checked like a commit against the current
     * {@code (−T, −L, 0)} preview: {@code BUSY}, {@code UNREADABLE}, {@code STALE} (also when
     * already Silenced) or {@code OK}. {@code T}, {@code L} and the counter go to 0, the flag is
     * set and the offer deleted, all before the one {@code WarpChangedEvent(SILENCING)}.
     */
    public static CommitResult silence(ServerPlayer player, WarpPreview expected) {
        try (OperationGuard.Scope scope = GUARD.tryEnter(player.getUUID())) {
            if (scope == null) {
                return result(CommitResult.Status.BUSY, null);
            }
            WarpRecord r = record(player);
            if (r == null) {
                return result(CommitResult.Status.UNREADABLE, null);
            }
            WarpPreview current = new WarpPreview(-r.temporary(), -r.lasting(), 0, false);
            if (r.silenced() || !current.equals(expected)) {
                return result(CommitResult.Status.STALE, current);
            }
            WarpRecord next = next(r, 0, 0, r.permanent(), r.gear(), 0, r.milestonesPaid(), true);
            slot().set(player, new WarpAttachment(new Retained.Read<>(next), null));
            publish(player, r.state(), next.state(), source(WarpSourceKind.SILENCING));
            return new CommitResult(
                    CommitResult.Status.OK, current, OptionalLong.of(next.revision()));
        }
    }

    // ---- Commands ----------------------------------------------------------------------------

    /**
     * {@code /vftv warp clear}: {@code T}, {@code L}, {@code P} and the counter to 0, as one
     * command apply; {@code milestones_paid} is kept.
     */
    public static CommitResult clear(ServerPlayer player) {
        try (OperationGuard.Scope scope = GUARD.tryEnter(player.getUUID())) {
            if (scope == null) {
                return result(CommitResult.Status.BUSY, null);
            }
            WarpRecord r = record(player);
            if (r == null) {
                return result(CommitResult.Status.UNREADABLE, null);
            }
            WarpPreview actual =
                    new WarpPreview(-r.temporary(), -r.lasting(), -r.permanent(), false);
            WarpRecord next = next(r, 0, 0, 0, r.gear(), 0, r.milestonesPaid(), r.silenced());
            write(player, r, next, source(WarpSourceKind.COMMAND));
            return new CommitResult(
                    CommitResult.Status.OK, actual, OptionalLong.of(next.revision()));
        }
    }

    /** {@code /vftv warp set <player> counter <n>}: the counter, clamped to its format bound. */
    public static CommitResult setCounter(ServerPlayer player, int counter) {
        try (OperationGuard.Scope scope = GUARD.tryEnter(player.getUUID())) {
            if (scope == null) {
                return result(CommitResult.Status.BUSY, null);
            }
            WarpRecord r = record(player);
            if (r == null) {
                return result(CommitResult.Status.UNREADABLE, null);
            }
            int value = Math.max(0, Math.min(WarpLimits.COUNTER_FORMAT_MAX, counter));
            WarpRecord next =
                    next(
                            r,
                            r.temporary(),
                            r.lasting(),
                            r.permanent(),
                            r.gear(),
                            value,
                            r.milestonesPaid(),
                            r.silenced());
            write(player, r, next, source(WarpSourceKind.COMMAND));
            return new CommitResult(
                    CommitResult.Status.OK,
                    new WarpPreview(0, 0, 0, false),
                    OptionalLong.of(next.revision()));
        }
    }

    // ---- Context -----------------------------------------------------------------------------

    /**
     * Registers a Void-touched place query, at init, by the feature that owns the place (warp spec,
     * "Context").
     */
    public static void registerPlaceQuery(VoidPlaceQuery query) {
        QUERIES.add(query);
    }

    /** Context clause 3 alone, as a pure read: whether any registered query answers true. */
    public static boolean inVoidTouchedPlace(ServerLevel level, BlockPos pos) {
        for (VoidPlaceQuery query : QUERIES) {
            if (query.test(level, pos)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the player is in context (warp spec, "Context"): recent Void work, a Void-touched
     * dimension, or a Void-touched place.
     */
    public static boolean inContext(ServerPlayer player) {
        ServerLevel level = player.level();
        return gameTime(player) < transientOf(player).voidWorkUntil
                || WarpRulesService.current()
                        .contextDimensions()
                        .contains(level.dimension().identifier().toString())
                || inVoidTouchedPlace(level, player.blockPosition());
    }

    /**
     * A {@code VOID} vis spend by the player (warp spec, "Context" and "The counter and the
     * chance"): context for {@code context.window} ticks, and {@code counter.per_void_work} on the
     * counter, never warp. A held guard skips the increment; an unreadable or Silenced record takes
     * none. The record syncs only if the counter changed.
     */
    public static void noteVoidWork(ServerPlayer player) {
        WarpRules rules = WarpRulesService.current();
        transientOf(player).voidWorkUntil = gameTime(player) + rules.contextWindow();
        try (OperationGuard.Scope scope = GUARD.tryEnter(player.getUUID())) {
            if (scope == null) {
                return;
            }
            WarpRecord r = record(player);
            if (r == null || r.silenced()) {
                return;
            }
            int counter = WarpMath.counterAfterVoidWork(r.counter(), rules);
            if (counter != r.counter()) {
                WarpRecord next =
                        next(
                                r,
                                r.temporary(),
                                r.lasting(),
                                r.permanent(),
                                r.gear(),
                                counter,
                                r.milestonesPaid(),
                                r.silenced());
                // The counter alone: no warp moves, so no event.
                write(player, r, next, source(WarpSourceKind.COMMAND));
            }
        }
    }

    // ---- The schedule ------------------------------------------------------------------------

    /**
     * One server tick for one online player (warp spec, "Schedule"): decay, then the gear sample,
     * each on its phase tick. The Voice check is the Voice events'. Allocates nothing unless a step
     * is due.
     */
    public static void onPlayerTick(ServerPlayer player) {
        long g = gameTime(player);
        WarpRules rules = WarpRulesService.current();
        Transient t = transientOf(player);
        int decay = rules.temporaryDecayInterval();
        if (t.decayInterval != decay) {
            t.decayInterval = decay;
            t.decayPhase = WarpPhase.of(player.getUUID(), decay);
        }
        if (g % decay == t.decayPhase) {
            WarpRecord r = get(player).readable();
            if (r != null && r.temporary() > 0) {
                apply(player, new WarpCost(-1, 0, 0), source(WarpSourceKind.DECAY));
            }
        }
        if (g % GEAR_INTERVAL == t.gearPhase) {
            sampleGear(player);
        }
    }

    /** A login, respawn or dimension change: the gear is sampled in the same tick. */
    public static void onArrive(ServerPlayer player) {
        sampleGear(player);
    }

    /** A logout: the player's transient state goes. */
    public static void onLogout(ServerPlayer player) {
        TRANSIENT.remove(player.getUUID());
    }

    /**
     * Samples the player's gear warp (warp spec, "Gear warp"): the {@code vftv:gear_warp} values of
     * at most {@link WarpLimits#MAX_GEAR_SLOTS} carried non-empty stacks, the vanilla inventory
     * first (main, armour, off hand), then the worn-slot collector's, capped at {@code gear.max}; 0
     * with {@code warp.gear} off. A changed sum is written under the guard and posts {@code
     * WarpChangedEvent(GEAR)}; a held guard skips the sample.
     */
    public static void sampleGear(ServerPlayer player) {
        WarpRules rules = WarpRulesService.current();
        int sum = rules.gear() ? Math.min(rules.gearMax(), carried(player)) : 0;
        try (OperationGuard.Scope scope = GUARD.tryEnter(player.getUUID())) {
            if (scope == null) {
                return;
            }
            WarpRecord r = record(player);
            if (r == null || r.gear() == sum) {
                return;
            }
            WarpRecord next =
                    next(
                            r,
                            r.temporary(),
                            r.lasting(),
                            r.permanent(),
                            sum,
                            r.counter(),
                            r.milestonesPaid(),
                            r.silenced());
            write(player, r, next, source(WarpSourceKind.GEAR));
        }
    }

    /** The uncapped sum of the carried stacks' gear values, within the one slot allowance. */
    static int carried(Player player) {
        Inventory inventory = player.getInventory();
        int budget = WarpLimits.MAX_GEAR_SLOTS;
        int sum = 0;
        for (int i = 0; i <= Inventory.SLOT_OFFHAND && budget > 0; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                budget--;
                sum += gearValue.applyAsInt(stack);
            }
        }
        int[] total = {sum};
        if (budget > 0) {
            worn.collect(
                    player,
                    budget,
                    stack -> {
                        total[0] += gearValue.applyAsInt(stack);
                        return true;
                    });
        }
        return total[0];
    }

    // ---- Internals ---------------------------------------------------------------------------

    /** The next record: the given values, every other field kept, the revision bumped. */
    private static WarpRecord next(
            WarpRecord r,
            int temporary,
            int lasting,
            int permanent,
            int gear,
            int counter,
            List<Identifier> paid,
            boolean silenced) {
        return new WarpRecord(
                WarpRecord.FORMAT,
                temporary,
                lasting,
                permanent,
                gear,
                counter,
                r.checks(),
                paid,
                r.lastListen(),
                silenced,
                r.revision() + 1);
    }

    /** Writes {@code next} (which syncs it to the owner), then posts the event if warp moved. */
    private static void write(
            ServerPlayer player, WarpRecord before, WarpRecord next, WarpSource source) {
        slot().set(player, get(player).with(next));
        if (!before.state().equals(next.state())) {
            publish(player, before.state(), next.state(), source);
        }
    }

    /** Posts {@code WarpChangedEvent}; a throwing listener is logged once and the change stands. */
    private static void publish(
            ServerPlayer player, WarpState before, WarpState after, WarpSource source) {
        WarpState delta =
                new WarpState(
                        after.temporary() - before.temporary(),
                        after.lasting() - before.lasting(),
                        after.permanent() - before.permanent(),
                        after.gear() - before.gear(),
                        after.total() - before.total());
        try {
            publisher.accept(new WarpChangedPayload(player, before, after, delta, source));
        } catch (RuntimeException e) {
            LOGGER.warn("A WarpChangedEvent listener threw; the change stands", e);
        }
    }

    private static WarpSource source(WarpSourceKind kind) {
        return new WarpSource(kind, null);
    }

    private static CommitResult result(CommitResult.Status status, @Nullable WarpPreview preview) {
        return new CommitResult(status, preview, OptionalLong.empty());
    }

    private static long gameTime(ServerPlayer player) {
        return player.level().getServer().overworld().getGameTime();
    }

    private static Transient transientOf(ServerPlayer player) {
        return TRANSIENT.computeIfAbsent(player.getUUID(), Transient::new);
    }

    private static DataSlot<Player, WarpAttachment> slot() {
        DataSlot<Player, WarpAttachment> s = slot;
        if (s == null) {
            throw new IllegalStateException("WarpFeature.init has not run");
        }
        return s;
    }

    /** A player's transient memory: never persisted, dropped at logout. */
    private static final class Transient {
        private final int gearPhase;
        private int decayInterval = -1;
        private int decayPhase;
        private long voidWorkUntil = Long.MIN_VALUE;
        private boolean warned;

        private Transient(UUID player) {
            gearPhase = WarpPhase.of(player, GEAR_INTERVAL);
        }
    }
}
