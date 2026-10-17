package com.andrewbrogoff.vftv.core.aspect;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.aspect.AspectDiscoveredPayload;
import com.andrewbrogoff.vftv.api.aspect.ScanKey;
import com.andrewbrogoff.vftv.api.aspect.ScanOutcome;
import com.andrewbrogoff.vftv.api.aspect.ScanTargetKind;
import com.andrewbrogoff.vftv.api.aspect.ThingScannedPayload;
import com.andrewbrogoff.vftv.core.aspect.model.Knowledge;
import com.andrewbrogoff.vftv.core.common.DataSlot;
import com.andrewbrogoff.vftv.core.common.DataSlots;
import com.andrewbrogoff.vftv.core.common.OperationGuard;
import com.andrewbrogoff.vftv.core.common.RawRecordCodec.Retained;
import com.andrewbrogoff.vftv.core.text.StatusLines;
import com.andrewbrogoff.vftv.core.text.model.SharedMessages;
import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The knowledge service (aspects spec, "Knowledge", "Aspect points", "Events"): every write to a
 * player's {@code vftv:knowledge} goes through here, under the knowledge guard, and posts its
 * events after the write while the guard is held. Server thread only.
 */
public final class KnowledgeService {
    private static final OperationGuard GUARD = new OperationGuard("knowledge");
    private static final Map<UUID, Long> BUSY_SENT = new HashMap<>();
    private static final Set<UUID> WARNED = new HashSet<>();
    private static final Logger LOGGER = LogUtils.getLogger();
    private static @Nullable DataSlot<Player, Retained<Knowledge>> slot;

    private KnowledgeService() {}

    /** Creates the attachment; called once from the feature's init. */
    static void init(DataSlots slots) {
        slot =
                slots.playerWithDeltas(
                        "knowledge",
                        KnowledgeCodecs.RETAINED,
                        () -> new Retained.Read<>(Knowledge.EMPTY),
                        KnowledgeCodecs.SYNC);
    }

    /** A player's knowledge, or null while their record is unreadable. Either side. */
    public static @Nullable Knowledge get(Player player) {
        return slot().get(player) instanceof Retained.Read<Knowledge> r ? r.value() : null;
    }

    /** The raw slot value, unreadable records included; for persistence tests. */
    public static Retained<Knowledge> raw(Player player) {
        return slot().get(player);
    }

    /** Replaces a player's knowledge whole, as an operator grant or a GameTest does. */
    public static void set(Player player, Knowledge knowledge) {
        slot().set(player, new Retained.Read<>(knowledge.baseline()));
    }

    /**
     * What a strike did, for the readout.
     *
     * @param strike the ledger's result
     * @param gained the points gained per aspect, scan payout and discovery bonuses together
     * @param after the knowledge after the strike
     */
    public record Struck(
            Knowledge.Strike strike, SortedMap<String, Integer> gained, Knowledge after) {}

    /**
     * Records a strike on a thing with values (non-empty; a node may hold more than a data-map
     * value's 5) and posts its events.
     *
     * @return what it did, or null when it was refused (unreadable knowledge, or the guard held)
     */
    public static @Nullable Struck strike(
            ServerPlayer player,
            ScanKey key,
            ScanTargetKind kind,
            Map<ResourceKey<Aspect>, Integer> values) {
        try (OperationGuard.Scope scope = GUARD.tryEnter(player.getUUID())) {
            if (scope == null) {
                busy(player);
                return null;
            }
            Knowledge before = readable(player);
            if (before == null) {
                StatusLines.send(player, Component.translatable(SharedMessages.UNREADABLE));
                return null;
            }
            AspectSet set = AspectSet.of(player.registryAccess());
            Map<String, Integer> amounts = new TreeMap<>();
            values.forEach((aspect, n) -> amounts.put(AspectSet.text(aspect), n));
            Knowledge.Strike strike =
                    before.strike(
                            key.toString(),
                            amounts,
                            set.rules(ScanningService.tuning().discoveryBonus()));
            if (strike.outcome() != ScanOutcome.REPEAT) { // a repeat changes nothing to sync
                slot().set(player, new Retained.Read<>(strike.next()));
            }
            SortedMap<String, Integer> gained = new TreeMap<>(strike.points());
            for (Knowledge.Discovery d : strike.discovered()) {
                gained.merge(d.aspect(), d.bonus(), Integer::sum);
                AspectEvents.discovered(
                        new AspectDiscoveredPayload(player, AspectSet.key(d.aspect()), d.bonus()));
            }
            SortedMap<ResourceKey<Aspect>, Integer> points = new TreeMap<>();
            strike.points().forEach((a, n) -> points.put(AspectSet.key(a), n));
            AspectEvents.scanned(
                    new ThingScannedPayload(
                            player, key, kind, strike.outcome(), strike.complete(), points));
            return new Struck(strike, gained, strike.next());
        }
    }

    /**
     * Discovers an aspect outright, paying its bonus, as a research reward does; posts {@code
     * AspectDiscoveredEvent}.
     *
     * @return whether it was newly discovered
     */
    public static boolean discover(ServerPlayer player, ResourceKey<Aspect> aspect) {
        try (OperationGuard.Scope scope = GUARD.tryEnter(player.getUUID())) {
            if (scope == null) {
                busy(player);
                return false;
            }
            Knowledge before = readable(player);
            AspectSet set = AspectSet.of(player.registryAccess());
            if (before == null || !set.contains(aspect)) {
                return false;
            }
            String id = AspectSet.text(aspect);
            int bonus = set.rules(ScanningService.tuning().discoveryBonus()).bonus(id);
            Knowledge next = before.discover(id, bonus);
            if (next == null) {
                return false;
            }
            slot().set(player, new Retained.Read<>(next));
            AspectEvents.discovered(
                    new AspectDiscoveredPayload(player, aspect, Math.max(0, bonus)));
            return true;
        }
    }

    /**
     * Credits points of an aspect the player already knows, as a Voice insight does. It never
     * discovers and posts no event, so it can never name an unheard aspect.
     *
     * @return whether anything was credited
     */
    public static boolean credit(ServerPlayer player, ResourceKey<Aspect> aspect, int points) {
        try (OperationGuard.Scope scope = GUARD.tryEnter(player.getUUID())) {
            if (scope == null) {
                busy(player);
                return false;
            }
            Knowledge before = readable(player);
            Knowledge next = before == null ? null : before.credit(AspectSet.text(aspect), points);
            if (next == null) {
                return false;
            }
            slot().set(player, new Retained.Read<>(next));
            return true;
        }
    }

    /**
     * A player's knowledge for a write, or null while unreadable: one WARN per player per server
     * session names the reason (conventions, "What an unreadable record does").
     */
    private static @Nullable Knowledge readable(ServerPlayer player) {
        Retained<Knowledge> raw = slot().get(player);
        if (raw instanceof Retained.Unreadable<Knowledge> u && WARNED.add(player.getUUID())) {
            LOGGER.warn(
                    "The vftv:knowledge of {} is unreadable ({}); it is kept as it is",
                    player.getGameProfile().name(),
                    u.reason());
        }
        return raw instanceof Retained.Read<Knowledge> r ? r.value() : null;
    }

    /** {@code message.vftv.busy}, at most once per player per tick. */
    private static void busy(ServerPlayer player) {
        long tick = player.level().getGameTime();
        Long last = BUSY_SENT.put(player.getUUID(), tick);
        if (last == null || last != tick) {
            StatusLines.send(player, Component.translatable(SharedMessages.BUSY));
        }
    }

    private static DataSlot<Player, Retained<Knowledge>> slot() {
        return Objects.requireNonNull(slot, "AspectFeature.init has not run");
    }
}
