package com.andrewbrogoff.vftv.core.hush;

import com.andrewbrogoff.vftv.api.essentia.SpillCause;
import com.andrewbrogoff.vftv.api.flux.FluxClearSource;
import com.andrewbrogoff.vftv.api.flux.FluxRules;
import com.andrewbrogoff.vftv.api.flux.FluxSource;
import com.andrewbrogoff.vftv.api.hush.HushLimits;
import com.andrewbrogoff.vftv.core.flux.model.FluxMath;
import com.andrewbrogoff.vftv.core.hush.model.BallTables;
import com.andrewbrogoff.vftv.core.hush.model.HushMath;
import com.andrewbrogoff.vftv.core.text.StatusLines;
import com.andrewbrogoff.vftv.core.text.model.NumberFormats;
import com.andrewbrogoff.vftv.core.text.model.SharedMessages;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;

/**
 * The counterplay (Hush spec, "The Clearing Bell", "The Pealing Bell"): a ring, and the Pealing
 * Bells' index, queue and peals, with the bell's removal spill. Server thread only.
 */
public final class HushBells {
    /** {@code message.vftv.hush.nothing}. */
    public static final String NOTHING = "message.vftv.hush.nothing";

    /** {@code status.vftv.pealing_bell}: Sonus, capacity. */
    public static final String STATUS = "status.vftv.pealing_bell";

    /** {@code status.vftv.pealing_bell.unreadable}. */
    public static final String STATUS_UNREADABLE = "status.vftv.pealing_bell.unreadable";

    /** The motes a ring that did something shows. */
    public static final int RING_MOTES = 8;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<UUID, Long> RUNG = new HashMap<>();
    private static final Map<UUID, Long> BUSY_SAID = new HashMap<>();

    private HushBells() {}

    /** Drops the per-player tick memory, when the server stops. */
    public static void clear() {
        RUNG.clear();
        BUSY_SAID.clear();
    }

    // --- the Clearing Bell -------------------------------------------------------------------

    /**
     * One ring attempt: at most one per player per tick (counted before any scan), silent on
     * cooldown, else clear the chunk underfoot, restore around the player and pay one point.
     */
    public static void ring(ServerPlayer player, InteractionHand hand) {
        ServerLevel level = player.level();
        long g = level.getGameTime();
        Long last = RUNG.put(player.getUUID(), g);
        ItemStack stack = player.getItemInHand(hand);
        if ((last != null && last == g) || player.getCooldowns().isOnCooldown(stack)) {
            return;
        }
        try (var self = HushService.GUARD.tryEnter(player.getUUID());
                var guard = self == null ? null : HushService.GUARD.tryEnter(level.dimension())) {
            if (self == null || guard == null) {
                Long said = BUSY_SAID.put(player.getUUID(), g);
                if (said == null || said != g) {
                    StatusLines.send(player, Component.translatable(SharedMessages.BUSY));
                }
                return;
            }
            FluxRules r = HushService.rules();
            BlockPos origin = player.blockPosition();
            LevelChunk under = FluxService.chunkNow(level, ChunkPos.pack(origin));
            int cleared =
                    under == null
                            ? 0
                            : FluxService.clear(
                                    level, under, r.bellFlux(), FluxClearSource.BELL, player);
            int restored = 0;
            boolean node = false;
            BlockPos first = null;
            int radius = r.bellRadius();
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int i = 0; i < BallTables.size(radius); i++) {
                if (restored >= r.bellRestore() && (node || cleared > 0)) {
                    break;
                }
                if (!stillRinging(player, level, hand, stack)) {
                    break;
                }
                pos.set(
                        origin.getX() + BallTables.dx(radius, i),
                        origin.getY() + BallTables.dy(radius, i),
                        origin.getZ() + BallTables.dz(radius, i));
                if (HushService.ticking(level, pos) == null) {
                    continue;
                }
                HushService.Restorer who =
                        new HushService.Restorer(
                                player, null, () -> stillRinging(player, level, hand, stack));
                if (restored < r.bellRestore() && HushService.restoreBlock(level, pos, who)) {
                    restored++;
                    first = first == null ? pos.immutable() : first;
                } else if (!node
                        && cleared == 0
                        && HushService.journaled(level, pos)
                        && HushService.restoreNode(level, pos, who)) {
                    node = true;
                    first = first == null ? pos.immutable() : first;
                }
            }
            if (cleared == 0 && restored == 0 && !node) {
                StatusLines.send(player, Component.translatable(NOTHING));
                return;
            }
            stack.hurtAndBreak(1, player, hand);
            level.playSound(
                    null,
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    HushContent.sound(HushContent.CLEARING_RING),
                    SoundSource.PLAYERS,
                    1.0F,
                    1.0F);
            if (first != null) {
                level.playSound(
                        null,
                        first,
                        HushContent.sound(HushContent.RESTORE),
                        SoundSource.BLOCKS,
                        0.6F,
                        1.0F);
            }
            level.sendParticles(
                    HushContent.mote(),
                    player.getX(),
                    player.getY() + 1.2,
                    player.getZ(),
                    RING_MOTES,
                    0.6,
                    0.4,
                    0.6,
                    0.01);
            player.getCooldowns().addCooldown(stack, r.bellCooldown());
        }
    }

    /** The ring's revalidation: online, alive, in the level, holding the same bell stack. */
    @SuppressWarnings("ReferenceEquality") // the same stack object is the rule
    private static boolean stillRinging(
            ServerPlayer player, ServerLevel level, InteractionHand hand, ItemStack stack) {
        return !player.hasDisconnected()
                && player.isAlive()
                && player.level() == level
                && player.getItemInHand(hand) == stack;
    }

    // --- the Pealing Bell --------------------------------------------------------------------

    /** Sneak-use with an empty hand: the bell's Sonus, built on the server. */
    static void status(ServerPlayer player, PealingBellBlockEntity be) {
        StatusLines.send(
                player,
                be.unreadable()
                        ? Component.translatable(STATUS_UNREADABLE)
                        : Component.translatable(
                                STATUS,
                                NumberFormats.integer(be.sonus()),
                                NumberFormats.integer(HushService.rules().pealCapacity())));
    }

    private static long due(long g, long pos, FluxRules r) {
        return HushMath.nextDue(g, r.pealInterval(), HushMath.bellPhase(pos, r.pealInterval()));
    }

    /** A bell's block entity loaded: indexed and queued at its next phase tick. */
    static void index(ServerLevel level, PealingBellBlockEntity be) {
        HushLevel state = FluxService.state(level);
        long key = be.getBlockPos().asLong();
        state.bellIndex.put(key, be);
        state.bells.offer(key, due(level.getGameTime(), key, HushService.rules()));
    }

    /** A bell's block entity removed or unloaded. */
    static void unindex(ServerLevel level, PealingBellBlockEntity be) {
        HushLevel state = FluxService.state(level);
        long key = be.getBlockPos().asLong();
        if (state.bellIndex.get(key) == be) {
            state.bellIndex.remove(key);
            state.bells.remove(key);
        }
    }

    /** A tuning reload: every bell's due recomputed from the new phase. */
    static void rebuild(ServerLevel level, HushLevel state, FluxRules r) {
        long g = level.getGameTime();
        for (long key : state.bells.keys()) {
            state.bells.put(key, due(g, key, r));
        }
    }

    /**
     * The bells' service, oldest due first: every pop counts against {@code
     * MAX_PEAL_POPS_PER_TICK}, skips included, and at most {@code MAX_PEALS_PER_TICK} peal.
     */
    static void serve(ServerLevel level) {
        HushLevel state = FluxService.state(level);
        if (state.bells.size() == 0) {
            return;
        }
        FluxRules r = HushService.rules();
        long g = level.getGameTime();
        int pops = 0;
        int peals = 0;
        while (pops < HushLimits.MAX_PEAL_POPS_PER_TICK) {
            var e = state.bells.first();
            if (e == null || e.due() > g) {
                return;
            }
            long key = e.key();
            PealingBellBlockEntity be = state.bellIndex.get(key);
            if (be == null || be.isRemoved()) {
                state.bells.remove(key);
                pops++;
                continue;
            }
            boolean ready =
                    HushService.attended(level, ChunkPos.pack(be.getBlockPos()), r, true)
                            && !be.unreadable()
                            && !be.busy()
                            && be.sonus >= 1;
            if (ready && peals >= HushLimits.MAX_PEALS_PER_TICK) {
                return; // stays due, served first next tick
            }
            pops++;
            if (ready) {
                peals++;
                peal(level, be, r);
            }
            if (state.bellIndex.get(key) == be) {
                state.bells.put(key, due(g, key, r));
            }
        }
    }

    /**
     * One peal: reserve one Sonus, clear the 3 × 3 chunks, scan from the cursor, then commit the
     * reservation at the first work or refund it when nothing happened.
     */
    public static void peal(ServerLevel level, PealingBellBlockEntity be, FluxRules r) {
        try (var bell = HushService.GUARD.tryEnter(be);
                var guard = bell == null ? null : HushService.GUARD.tryEnter(level.dimension())) {
            if (bell == null || guard == null || be.unreadable() || be.busy() || be.sonus < 1) {
                return;
            }
            BlockPos at = be.getBlockPos();
            int sonusBefore = be.sonus;
            int cursorBefore = be.cursor;
            be.pealing = true;
            be.sonus -= 1; // reserved
            boolean committed = false;
            int cleared = 0;
            int restored = 0;
            boolean node = false;
            BlockPos first = null;
            try {
                ChunkPos centre = ChunkPos.containing(at);
                List<Long> chunks = new ArrayList<>();
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        chunks.add(ChunkPos.pack(centre.x() + dx, centre.z() + dz));
                    }
                }
                chunks.sort(null);
                for (long pack : chunks) {
                    LevelChunk chunk = FluxService.chunkNow(level, pack);
                    if (chunk != null) {
                        cleared +=
                                FluxService.clear(
                                        level, chunk, r.pealFlux(), FluxClearSource.PEAL, null);
                    }
                }
                committed = cleared > 0;
                int radius = r.pealRadius();
                int side = 2 * radius + 1;
                int cube = side * side * side;
                int cursor = be.cursor % cube;
                HushService.Restorer who = new HushService.Restorer(null, at, null);
                BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
                for (int examined = 0; examined < r.pealScan(); examined++) {
                    if (restored >= r.pealRestore() && (node || cleared > 0)) {
                        break;
                    }
                    int i = cursor;
                    cursor = (cursor + 1) % cube;
                    pos.set(
                            at.getX() + i % side - radius,
                            at.getY() + i / (side * side) - radius,
                            at.getZ() + (i / side) % side - radius);
                    if (HushService.ticking(level, pos) == null) {
                        continue; // examined, never loaded
                    }
                    if (restored < r.pealRestore() && HushService.restoreBlock(level, pos, who)) {
                        restored++;
                        committed = true;
                        first = first == null ? pos.immutable() : first;
                    } else if (!node
                            && cleared == 0
                            && HushService.journaled(level, pos)
                            && HushService.restoreNode(level, pos, who)) {
                        node = true;
                        committed = true;
                        first = first == null ? pos.immutable() : first;
                    }
                    if (be.isRemoved()) {
                        break;
                    }
                }
                be.cursor = cursor;
            } finally {
                be.pealing = false;
                if (!committed) {
                    be.sonus += 1; // refunded: nothing happened
                }
                if (be.sonus != sonusBefore || be.cursor != cursorBefore) {
                    if (!be.isRemoved()) {
                        be.changed();
                    }
                }
            }
            if (committed) {
                level.playSound(
                        null,
                        at,
                        HushContent.sound(HushContent.PEALING_RING),
                        SoundSource.BLOCKS,
                        2.0F,
                        1.0F);
                if (first != null) {
                    level.playSound(
                            null,
                            first,
                            HushContent.sound(HushContent.RESTORE),
                            SoundSource.BLOCKS,
                            0.6F,
                            1.0F);
                }
            }
        }
        settle(level, be);
    }

    // --- removal -----------------------------------------------------------------------------

    /**
     * The removal hook: spills the final Sonus once, or records one pending removal while a peal
     * owns the bell or a transaction is open on it.
     */
    static void onRemoved(ServerLevel level, PealingBellBlockEntity be) {
        be.removalPending = true;
        settle(level, be);
    }

    /** Runs a pending removal once both scopes have settled. */
    static void settle(ServerLevel level, PealingBellBlockEntity be) {
        if (!be.removalPending || be.pealing || be.busy()) {
            return;
        }
        be.removalPending = false;
        if (be.unreadable()) {
            LOGGER.warn(
                    "Pealing Bell at {} removed; its unreadable record is lost: {}",
                    be.getBlockPos(),
                    be.raw());
            return;
        }
        int sonus = be.sonus;
        be.sonus = 0;
        int flux =
                FluxMath.essentiaFlux(new int[] {sonus}, SpillCause.REMOVED, HushService.rules());
        FluxService.deposit(
                level, be.getBlockPos(), flux, new FluxSource.Essentia(SpillCause.REMOVED), null);
    }
}
