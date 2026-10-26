package com.andrewbrogoff.vftv.core.warp;

import com.andrewbrogoff.vftv.api.warp.CommitResult;
import com.andrewbrogoff.vftv.api.warp.WarpChangedPayload;
import com.andrewbrogoff.vftv.api.warp.WarpCost;
import com.andrewbrogoff.vftv.api.warp.WarpKeys;
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
import com.andrewbrogoff.vftv.core.text.model.SharedMessages;
import com.andrewbrogoff.vftv.core.warp.model.WarpMath;
import com.andrewbrogoff.vftv.core.warp.model.WarpPhase;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
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
    static final OperationGuard GUARD = new OperationGuard("warp");
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
        if (id != null
                && r.milestonesPaid().size() >= WarpLimits.MAX_MILESTONES
                && !r.milestonesPaid().contains(id)
                && WarpMath.isMilestone(cost.permanent(), source.kind(), id.toString(), rules)) {
            return null; // the paid set is full: a new milestone can't be recorded
        }
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
            transientOf(player).listenDue = false;
            try {
                publish(player, r.state(), next.state(), source(WarpSourceKind.SILENCING));
            } finally {
                VoiceChecks.send(player, new VoicePayloads.VoiceClear());
            }
            return new CommitResult(
                    CommitResult.Status.OK, current, OptionalLong.of(next.revision()));
        }
    }

    // ---- Bargains and the Listen rite --------------------------------------------------------

    /** What an offer action or the Listen rite answered; each refusal has its message key. */
    public enum Answer {
        /** Done. */
        OK(""),
        /** The guard is held. */
        BUSY(SharedMessages.BUSY),
        /** The record is unreadable. */
        UNREADABLE(SharedMessages.UNREADABLE),
        /** Silenced, or the Voices are switched off. */
        CLOSED("message.vftv.warp.listen_closed"),
        /** Not in a Void-touched place. */
        PLACE("message.vftv.warp.listen_place"),
        /** The rite is cooling; the seconds left are {@link #listenCooldownSeconds}. */
        COOLDOWN("message.vftv.warp.listen_cooldown"),
        /** No live offer with that sequence number. */
        GONE("message.vftv.warp.offer_gone"),
        /** The price would be clipped. */
        FULL("message.vftv.warp.offer_full"),
        /** The echoed preview differs from the current one. */
        STALE(SharedMessages.COST_CHANGED),
        /** The power could not be given; nothing was charged. */
        CANNOT("message.vftv.warp.offer_cannot");

        private final String key;

        Answer(String key) {
            this.key = key;
        }

        /** The refusal's message key; empty for {@link #OK}. */
        public String key() {
            return key;
        }
    }

    /** The Listen rite's cost, {@code listen.cost}. */
    public static WarpCost listenCost(WarpRules rules) {
        return new WarpCost(rules.listenCostTemporary(), rules.listenCostLasting(), 0);
    }

    /** The preview of the Listen rite's cost, or null for an unreadable record. */
    public static @Nullable WarpPreview ritePreview(Player player) {
        WarpRecord r = get(player).readable();
        WarpRules rules = WarpRulesService.current();
        return r == null
                ? null
                : preview(r, listenCost(rules), source(WarpSourceKind.LISTEN), rules);
    }

    /**
     * The Listen rite's current refusal from rows 2–6 ("The Listen rite"), as the book's quote
     * carries it, or {@link Answer#OK} when it would be accepted. The place is read now.
     */
    public static Answer riteRefusal(ServerPlayer player) {
        WarpRecord r = get(player).readable();
        return r == null ? Answer.UNREADABLE : riteRefusal(player, r, WarpRulesService.current());
    }

    /** Rows 3–6 of the rite over a readable record: closed, place, cooling, clipped, else OK. */
    private static Answer riteRefusal(ServerPlayer player, WarpRecord r, WarpRules rules) {
        if (r.silenced() || !rules.voicesEnabled()) {
            return Answer.CLOSED;
        }
        if (!inVoidTouchedPlace(player.level(), player.blockPosition())) {
            return Answer.PLACE;
        }
        if (!riteReady(r, gameTime(player), rules)) {
            return Answer.COOLDOWN;
        }
        WarpPreview p = preview(r, listenCost(rules), source(WarpSourceKind.LISTEN), rules);
        return p == null || p.clipped() ? Answer.FULL : Answer.OK;
    }

    /** Whether the rite's cooldown has passed, at overworld time {@code g}. */
    public static boolean riteReady(WarpRecord r, long g, WarpRules rules) {
        long last = r.lastListen();
        return last < 0 || g < last || g - last >= rules.listenCooldown();
    }

    /** The whole seconds left on the rite's cooldown, 0 when ready or unreadable. */
    public static long listenCooldownSeconds(ServerPlayer player) {
        WarpRecord r = get(player).readable();
        WarpRules rules = WarpRulesService.current();
        long g = gameTime(player);
        if (r == null || riteReady(r, g, rules)) {
            return 0;
        }
        return Math.ceilDiv(rules.listenCooldown() - (g - r.lastListen()), 20);
    }

    /**
     * The Listen rite (warp spec, "The Listen rite"), one guarded compound operation after the
     * research layer's checks: {@code BUSY}, {@code UNREADABLE}, {@code CLOSED}, {@code PLACE},
     * {@code COOLDOWN}, {@code FULL}, {@code STALE}, or {@code OK}: the cost written as a commit
     * (the counter rising with it), {@code last_listen} set and the forced check made due, with
     * {@code WarpChangedEvent(LISTEN)} posted while the guard is held.
     */
    public static Answer listen(ServerPlayer player, WarpPreview expected) {
        try (OperationGuard.Scope scope = GUARD.tryEnter(player.getUUID())) {
            if (scope == null) {
                return Answer.BUSY;
            }
            WarpRecord r = record(player);
            if (r == null) {
                return Answer.UNREADABLE;
            }
            WarpRules rules = WarpRulesService.current();
            long g = gameTime(player);
            Answer refusal = riteRefusal(player, r, rules);
            if (refusal != Answer.OK) {
                return refusal;
            }
            WarpSource source = source(WarpSourceKind.LISTEN);
            WarpPreview actual =
                    Objects.requireNonNull(preview(r, listenCost(rules), source, rules));
            if (!actual.equals(expected)) {
                return Answer.STALE;
            }
            WarpRecord next =
                    new WarpRecord(
                            WarpRecord.FORMAT,
                            r.temporary() + actual.dT(),
                            r.lasting() + actual.dL(),
                            r.permanent(),
                            r.gear(),
                            WarpMath.counterAfterGain(r.counter(), WarpMath.gains(actual), rules),
                            r.checks(),
                            r.milestonesPaid(),
                            g,
                            r.silenced(),
                            r.revision() + 1);
            transientOf(player).listenDue = true;
            write(player, r, next, source);
            return Answer.OK;
        }
    }

    /**
     * The standing offer, or null; an expired one counts as absent and is deleted here (warp spec,
     * "The offer": expiry is lazy).
     */
    public static WarpAttachment.@Nullable Offer offer(ServerPlayer player) {
        WarpAttachment value = get(player);
        WarpAttachment.Offer o = value.offer();
        if (o != null && gameTime(player) >= o.expires()) {
            try (OperationGuard.Scope scope = GUARD.tryEnter(player.getUUID())) {
                if (scope != null) {
                    slot().set(player, new WarpAttachment(get(player).record(), null));
                }
            }
            return null;
        }
        return o;
    }

    /** The preview of the standing offer's cost, or null with no offer or an unreadable record. */
    public static @Nullable WarpPreview offerPreview(ServerPlayer player) {
        WarpAttachment.Offer o = offer(player);
        WarpRecord r = get(player).readable();
        if (o == null || r == null) {
            return null;
        }
        WarpRules rules = WarpRulesService.current();
        return preview(r, offerCost(o), new WarpSource(WarpSourceKind.BARGAIN, o.bargain()), rules);
    }

    /**
     * Accepts the offer {@code seq} (warp spec, "Accepting"), one guarded compound operation:
     * {@code BUSY}; {@code GONE} (an expired or invalid offer deleted, a newer one kept); {@code
     * FULL}; {@code STALE}; {@code CANNOT} when the one {@code addEffect} call says no; or {@code
     * OK}: the warp written as a commit, the offer deleted, {@code WarpChangedEvent(BARGAIN)}
     * posted and {@code bargain.accept} played to the owner.
     */
    public static Answer acceptOffer(ServerPlayer player, long seq, WarpPreview echoed) {
        try (OperationGuard.Scope scope = GUARD.tryEnter(player.getUUID())) {
            if (scope == null) {
                return Answer.BUSY;
            }
            WarpAttachment value = get(player);
            WarpAttachment.Offer o = value.offer();
            if (o == null) {
                return Answer.GONE;
            }
            boolean expired = gameTime(player) >= o.expires();
            boolean invalid =
                    player.level()
                            .registryAccess()
                            .lookupOrThrow(WarpKeys.BARGAIN_REGISTRY)
                            .get(ResourceKey.create(WarpKeys.BARGAIN_REGISTRY, o.bargain()))
                            .isEmpty();
            if (expired || (o.seq() == seq && invalid)) {
                slot().set(player, new WarpAttachment(value.record(), null));
                return Answer.GONE;
            }
            if (o.seq() != seq) {
                return Answer.GONE;
            }
            WarpRecord r = record(player);
            if (r == null) {
                return Answer.UNREADABLE;
            }
            WarpRules rules = WarpRulesService.current();
            WarpSource source = new WarpSource(WarpSourceKind.BARGAIN, o.bargain());
            WarpPreview actual = preview(r, offerCost(o), source, rules);
            if (actual == null || actual.clipped()) {
                return Answer.FULL;
            }
            if (!actual.equals(echoed)) {
                return Answer.STALE;
            }
            if (!VoiceChecks.addEffect(player, o.effect(), o.duration(), o.amplifier())) {
                return Answer.CANNOT;
            }
            WarpRecord next =
                    next(
                            r,
                            r.temporary() + actual.dT(),
                            r.lasting() + actual.dL(),
                            r.permanent(),
                            r.gear(),
                            WarpMath.counterAfterGain(r.counter(), WarpMath.gains(actual), rules),
                            r.milestonesPaid(),
                            r.silenced());
            write(player, r, new WarpAttachment(new Retained.Read<>(next), null), source);
            VoiceChecks.playToOwner(player, WarpContent.sound(WarpContent.BARGAIN_ACCEPT));
            return Answer.OK;
        }
    }

    /**
     * Refuses the offer {@code seq}: it costs nothing, ever. {@code BUSY}; {@code GONE} (an expired
     * offer deleted, a newer one kept); or {@code OK}, the offer deleted.
     */
    public static Answer refuseOffer(ServerPlayer player, long seq) {
        try (OperationGuard.Scope scope = GUARD.tryEnter(player.getUUID())) {
            if (scope == null) {
                return Answer.BUSY;
            }
            WarpAttachment value = get(player);
            WarpAttachment.Offer o = value.offer();
            if (o == null) {
                return Answer.GONE;
            }
            boolean expired = gameTime(player) >= o.expires();
            if (!expired && o.seq() != seq) {
                return Answer.GONE;
            }
            slot().set(player, new WarpAttachment(value.record(), null));
            return expired ? Answer.GONE : Answer.OK;
        }
    }

    /**
     * Replaces the standing offer under the guard, for GameTests that need an offer in a given
     * state (an expired one, say) without waiting out its lifetime.
     */
    public static void putOffer(ServerPlayer player, WarpAttachment.@Nullable Offer offer) {
        try (OperationGuard.Scope scope = GUARD.tryEnter(player.getUUID())) {
            if (scope != null) {
                slot().set(player, new WarpAttachment(get(player).record(), offer));
            }
        }
    }

    /** Whether the Listen rite's forced check is due; for GameTests and the harness dump. */
    public static boolean listenDue(ServerPlayer player) {
        return transientOf(player).listenDue;
    }

    static WarpCost offerCost(WarpAttachment.Offer o) {
        return new WarpCost(o.costTemporary(), o.costLasting(), 0);
    }

    // ---- Screens, arrival and death ----------------------------------------------------------

    /** {@code VoiceQuiet(open)}: stores the client's screen state; the last value wins. */
    public static void quiet(ServerPlayer player, boolean open) {
        transientOf(player).quiet = open;
    }

    /** A death: the offer and the rite's due check are deleted; warp itself is untouched. */
    public static void onDeath(ServerPlayer player) {
        transientOf(player).listenDue = false;
        WarpAttachment value = get(player);
        if (value.offer() != null) {
            try (OperationGuard.Scope scope = GUARD.tryEnter(player.getUUID())) {
                if (scope != null) {
                    slot().set(player, new WarpAttachment(value.record(), null));
                }
            }
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
        int check = rules.checkInterval();
        if (t.checkInterval != check) {
            t.checkInterval = check;
            t.checkPhase = WarpPhase.of(player.getUUID(), check);
        }
        if (g % check == t.checkPhase) {
            VoiceChecks.check(player, false);
        }
        if (t.listenDue && VoiceChecks.suppressed(player) == null) {
            t.listenDue = false;
            VoiceChecks.check(player, true);
        }
    }

    /**
     * A login, respawn or dimension change: the gear is sampled in the same tick, the grace period
     * begins and a due rite check lapses (its price stays paid).
     */
    public static void onArrive(ServerPlayer player) {
        Transient t = transientOf(player);
        t.graceUntil = gameTime(player) + WarpRulesService.current().grace();
        t.listenDue = false;
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
    static WarpRecord next(
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
    static void write(ServerPlayer player, WarpRecord before, WarpRecord next, WarpSource source) {
        write(
                player,
                before,
                new WarpAttachment(new Retained.Read<>(next), get(player).offer()),
                source);
    }

    /** Writes a whole value (record and offer), then posts the event if warp moved. */
    static void write(
            ServerPlayer player, WarpRecord before, WarpAttachment value, WarpSource source) {
        WarpRecord next = Objects.requireNonNull(value.readable());
        slot().set(player, value);
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

    static WarpSource source(WarpSourceKind kind) {
        return new WarpSource(kind, null);
    }

    static CommitResult result(CommitResult.Status status, @Nullable WarpPreview preview) {
        return new CommitResult(status, preview, OptionalLong.empty());
    }

    static long gameTime(ServerPlayer player) {
        return player.level().getServer().overworld().getGameTime();
    }

    static Transient transientOf(ServerPlayer player) {
        return TRANSIENT.computeIfAbsent(player.getUUID(), Transient::new);
    }

    static DataSlot<Player, WarpAttachment> slot() {
        DataSlot<Player, WarpAttachment> s = slot;
        if (s == null) {
            throw new IllegalStateException("WarpFeature.init has not run");
        }
        return s;
    }

    /** A player's transient memory: never persisted, dropped at logout. */
    static final class Transient {
        private final int gearPhase;
        private int decayInterval = -1;
        private int decayPhase;
        private int checkInterval = -1;
        private int checkPhase;
        private long voidWorkUntil = Long.MIN_VALUE;
        private boolean warned;

        /** The client's screen state ({@code VoiceQuiet}): suppression row 8. */
        boolean quiet;

        /** No check before this overworld tick: suppression row 6. */
        long graceUntil = Long.MIN_VALUE;

        /** A Listen rite has paid and its forced check is due. */
        boolean listenDue;

        /** The last offer's per-login sequence number. */
        long offerSeq;

        private Transient(UUID player) {
            gearPhase = WarpPhase.of(player, GEAR_INTERVAL);
        }
    }
}
