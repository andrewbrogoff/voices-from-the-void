package com.andrewbrogoff.vftv.core.infusion;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.essentia.SpillCause;
import com.andrewbrogoff.vftv.api.infusion.InfusionCost;
import com.andrewbrogoff.vftv.api.infusion.InfusionLimits;
import com.andrewbrogoff.vftv.api.infusion.InfusionPayload;
import com.andrewbrogoff.vftv.api.infusion.InfusionPlan;
import com.andrewbrogoff.vftv.api.vis.VisKind;
import com.andrewbrogoff.vftv.core.common.Network;
import com.andrewbrogoff.vftv.core.flux.FluxRulesService;
import com.andrewbrogoff.vftv.core.flux.model.FluxMath;
import com.andrewbrogoff.vftv.core.infusion.AltarReading.Assessment;
import com.andrewbrogoff.vftv.core.infusion.AltarReading.Cell;
import com.andrewbrogoff.vftv.core.infusion.AltarReading.ClaimState;
import com.andrewbrogoff.vftv.core.infusion.AltarReading.Inputs;
import com.andrewbrogoff.vftv.core.infusion.InfusionPayloads.Kind;
import com.andrewbrogoff.vftv.core.infusion.model.InfusionMath;
import com.andrewbrogoff.vftv.core.infusion.model.InfusionTuning;
import com.andrewbrogoff.vftv.core.text.StatusLines;
import com.andrewbrogoff.vftv.core.text.model.SharedMessages;
import com.andrewbrogoff.vftv.core.vis.VisContent;
import com.andrewbrogoff.vftv.core.warp.WarpDials;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The altar's server logic (infusion spec): hand use, strikes and arms, the second strike's checked
 * commit, the shared assessment allowance and its pending queue, the arm and step queues, claims,
 * removal and the coalesced sync. The run itself is {@link InfusionRunner}'s. Server thread only;
 * the client half of hand use only predicts.
 */
public final class InfusionAltars {
    /** The tuning file under {@code data/vftv/}. */
    public static final String TUNING_PATH = "tuning/infusion.json";

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_SEEN_PER_TICK = 4;

    private static volatile InfusionRules rules = InfusionRules.DEFAULTS;
    private static volatile @Nullable InfusionRules clientRules;
    private static volatile InfusionHooks hooks = InfusionHooks.NONE;
    private static @Nullable Network network;
    private static final Map<ServerLevel, LevelState> LEVELS = new HashMap<>();
    private static final Map<UUID, Arm> ARMS = new HashMap<>();

    private InfusionAltars() {}

    // --- State ----------------------------------------------------------------------------

    /** A step-queue entry. */
    record Due(long due, long pos, long id, int seq) {}

    /** An arm-queue entry. */
    record ArmDue(long due, UUID player) {}

    /** A queued strike or commit attempt. */
    record Pending(
            long tick,
            UUID player,
            BlockPos pos,
            long bowlId,
            boolean commit,
            InteractionHand hand,
            int revision) {}

    private static final Comparator<Due> DUE_ORDER =
            Comparator.comparingLong(Due::due).thenComparingLong(Due::pos);
    private static final Comparator<ArmDue> ARM_ORDER =
            Comparator.comparingLong(ArmDue::due).thenComparing(ArmDue::player);
    private static final Comparator<Pending> PENDING_ORDER =
            Comparator.comparingLong(Pending::tick).thenComparing(Pending::player);

    /** One level's queues, allowance and dirty set. */
    static final class LevelState {
        final TreeSet<Due> steps = new TreeSet<>(DUE_ORDER);
        final TreeSet<ArmDue> arms = new TreeSet<>(ARM_ORDER);
        final TreeSet<Pending> pending = new TreeSet<>(PENDING_ORDER);
        final Map<UUID, Pending> pendingBy = new HashMap<>();
        final Set<AltarBlockEntity> dirty = new LinkedHashSet<>();
        long allowanceTick = Long.MIN_VALUE;
        int assessments;
        int stepsRun; // for the bounds test: steps in the last tick
        int popsRun;

        boolean spend(long now) {
            if (allowanceTick != now) {
                allowanceTick = now;
                assessments = 0;
            }
            if (assessments >= InfusionLimits.MAX_ASSESSMENTS_PER_TICK) {
                return false;
            }
            assessments++;
            return true;
        }
    }

    /** A player's readout surface: an {@code ARMED} arm or a {@code RUN} subscription. */
    static final class Arm {
        Kind kind;
        final ResourceKey<Level> dimension;
        final BlockPos pos;
        long bowlId;
        int revision;
        int seen;
        boolean hasSeen;
        @Nullable Inputs inputs;
        InfusionReadout readout;
        long lastStrike;
        long due;
        long seenTick = Long.MIN_VALUE;
        int seenCount;

        Arm(
                Kind kind,
                ResourceKey<Level> dimension,
                BlockPos pos,
                long bowlId,
                InfusionReadout readout) {
            this.kind = kind;
            this.dimension = dimension;
            this.pos = pos.immutable();
            this.bowlId = bowlId;
            this.readout = readout;
        }
    }

    private static LevelState state(ServerLevel level) {
        return LEVELS.computeIfAbsent(level, l -> new LevelState());
    }

    /** Installs the platform's hooks; called once from the platform's wiring. */
    public static void install(InfusionHooks platform) {
        hooks = platform;
    }

    /** The platform's hooks. */
    public static InfusionHooks hooks() {
        return hooks;
    }

    /** Takes the network seam; called once from the feature's init. */
    static void init(Network seam) {
        network = seam;
    }

    /** The rules in force on the server. */
    public static InfusionRules rules() {
        return rules;
    }

    /** The tuning in force on the server. */
    public static InfusionTuning tuning() {
        return rules.tuning();
    }

    /** The client's copy of the rules, or null before the first arrives. */
    public static @Nullable InfusionRules clientRules() {
        return clientRules;
    }

    /** Sets the client's copy (null at disconnect). */
    public static void setClientRules(@Nullable InfusionRules received) {
        clientRules = received;
    }

    /** Reads a tuning file's text, or why it is invalid. */
    public static DataResult<InfusionTuning> parse(String json) {
        try {
            return DataResult.success(InfusionTuning.parse(json));
        } catch (IllegalArgumentException e) {
            return DataResult.error(e::getMessage);
        }
    }

    /**
     * Applies a tuning read: an invalid one refuses the world at open and keeps the previous record
     * on {@code /reload}. A new record applies to future starts only.
     *
     * @throws IllegalStateException for an invalid read while the world opens
     */
    public static synchronized void applyTuning(
            DataResult<InfusionTuning> read, boolean reloading) {
        read.ifSuccess(t -> rules = new InfusionRules(InfusionRules.FORMAT, t))
                .ifError(
                        error -> {
                            String message =
                                    "Invalid data/vftv/" + TUNING_PATH + ": " + error.message();
                            if (!reloading) {
                                throw new IllegalStateException(message);
                            }
                            LOGGER.error("{}; keeping the previous infusion tuning", message);
                        });
    }

    /** Sends the rules in force to one player, as on join and after {@code /reload}. */
    public static void sendRules(ServerPlayer player) {
        if (network != null) {
            network.toPlayer(player, new InfusionPayloads.Rules(rules));
        }
    }

    /** Drops every level's state and every arm when the server stops. */
    public static void clear() {
        LEVELS.clear();
        ARMS.clear();
        rules = InfusionRules.DEFAULTS;
    }

    /** Drops a level's queues when it unloads. */
    public static void onLevelUnload(ServerLevel level) {
        LEVELS.remove(level);
    }

    /** Ends a player's arm at logout; nothing is sent to a closed connection. */
    public static void onLogout(ServerPlayer player) {
        ARMS.remove(player.getUUID());
        LEVELS.values()
                .forEach(
                        s -> {
                            Pending p = s.pendingBy.remove(player.getUUID());
                            if (p != null) {
                                s.pending.remove(p);
                            }
                        });
    }

    // --- Hand use -------------------------------------------------------------------------

    /** {@code useItemOn} on either block (infusion spec, "Hand use"): the hand used decides. */
    static InteractionResult useItemOn(
            Level level, BlockPos pos, Player player, InteractionHand hand, ItemStack stack) {
        if (!(level.getBlockEntity(pos) instanceof AltarBlockEntity be)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        ServerPlayer sp = player instanceof ServerPlayer p ? p : null;
        boolean server = level instanceof ServerLevel;
        if (be.unreadable()) {
            say(sp, Component.translatable(SharedMessages.UNREADABLE));
            return InteractionResult.CONSUME;
        }
        if (be.busy()) {
            say(sp, Component.translatable(SharedMessages.BUSY));
            return InteractionResult.CONSUME;
        }
        if (be instanceof BowlBlockEntity bowl) {
            if (stack.is(VisContent.wand())) {
                if (server && sp != null) {
                    strike((ServerLevel) level, bowl, sp, hand);
                }
                return InteractionResult.SUCCESS;
            }
            if (bowl.ringing()) {
                say(sp, Component.translatable(InfusionLang.MESSAGE + "ringing"));
                return InteractionResult.CONSUME;
            }
            if (bowl.item.isEmpty()) {
                if (server) {
                    place(level, bowl, player, stack);
                }
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        PedestalBlockEntity pedestal = (PedestalBlockEntity) be;
        if (!pedestal.item.isEmpty()) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!(level instanceof ServerLevel s)) {
            return pedestal.claimed() ? InteractionResult.CONSUME : InteractionResult.SUCCESS;
        }
        if (mayRestore(s, pedestal, stack)) {
            place(level, pedestal, player, stack);
            return InteractionResult.SUCCESS;
        }
        say(sp, promised(pedestal));
        return InteractionResult.CONSUME;
    }

    /** {@code useWithoutItem}: acts only when the main hand is empty. */
    static InteractionResult useWithoutItem(Level level, BlockPos pos, Player player) {
        if (!player.getMainHandItem().isEmpty()
                || !(level.getBlockEntity(pos) instanceof AltarBlockEntity be)) {
            return InteractionResult.PASS;
        }
        ServerPlayer sp = player instanceof ServerPlayer p ? p : null;
        if (be.unreadable()) {
            say(sp, Component.translatable(SharedMessages.UNREADABLE));
            return InteractionResult.CONSUME;
        }
        if (be.busy()) {
            say(sp, Component.translatable(SharedMessages.BUSY));
            return InteractionResult.CONSUME;
        }
        if (be instanceof BowlBlockEntity bowl) {
            if (bowl.ringing()) {
                if (!(level instanceof ServerLevel s) || sp == null) {
                    return InteractionResult.SUCCESS;
                }
                return InfusionRunner.abandonUse(s, bowl, sp)
                        ? InteractionResult.SUCCESS
                        : InteractionResult.CONSUME;
            }
            if (bowl.item.isEmpty()) {
                return InteractionResult.PASS;
            }
            if (level instanceof ServerLevel) {
                take(level, bowl, player);
            }
            return InteractionResult.SUCCESS;
        }
        PedestalBlockEntity pedestal = (PedestalBlockEntity) be;
        boolean claimed =
                level instanceof ServerLevel s ? claimHolds(s, pedestal) : pedestal.claimed();
        if (!claimed && !pedestal.item.isEmpty()) {
            if (level instanceof ServerLevel) {
                take(level, pedestal, player);
            }
            return InteractionResult.SUCCESS;
        }
        if (claimed) {
            say(sp, promised(pedestal));
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    private static void place(Level level, AltarBlockEntity be, Player player, ItemStack stack) {
        be.item = stack.copyWithCount(1);
        if (!player.hasInfiniteMaterials()) {
            stack.shrink(1);
        }
        be.markDirty();
        level.playSound(
                null,
                be.getBlockPos(),
                SoundEvents.ITEM_FRAME_ADD_ITEM,
                SoundSource.BLOCKS,
                1.0f,
                1.0f);
    }

    private static void take(Level level, AltarBlockEntity be, Player player) {
        ItemStack taken = be.item;
        be.item = ItemStack.EMPTY;
        be.markDirty();
        player.getInventory().placeItemBackInInventory(taken);
        level.playSound(
                null,
                be.getBlockPos(),
                SoundEvents.ITEM_FRAME_REMOVE_ITEM,
                SoundSource.BLOCKS,
                1.0f,
                1.0f);
    }

    private static Component promised(PedestalBlockEntity pedestal) {
        PedestalBlockEntity.Claim claim = pedestal.claim;
        BlockPos bowl = claim == null ? pedestal.getBlockPos() : BlockPos.of(claim.bowl());
        return Component.translatable(
                InfusionLang.MESSAGE + "promised", AltarReading.xyz(bowl).toArray());
    }

    // --- Claims ---------------------------------------------------------------------------

    /**
     * A pedestal's claim as found now (infusion spec, "Claims"), without loading anything: unknown
     * while the bowl's chunk is unloaded, unresolved for an unreadable bowl, live for its bowl's
     * pending component at this offset; anything else is dead, cleared, and reads {@code NONE}.
     */
    static ClaimState claimState(ServerLevel level, PedestalBlockEntity pedestal) {
        PedestalBlockEntity.Claim claim = pedestal.claim;
        if (claim == null) {
            return ClaimState.NONE;
        }
        BlockPos at = BlockPos.of(claim.bowl());
        if (level.getChunkSource().getChunkNow(at.getX() >> 4, at.getZ() >> 4) == null) {
            return ClaimState.UNKNOWN;
        }
        BlockEntity be = level.getBlockEntity(at);
        if (be instanceof BowlBlockEntity bowl) {
            if (bowl.unreadable()) {
                return ClaimState.UNRESOLVED;
            }
            InfusionRun run = bowl.run;
            BlockPos p = pedestal.getBlockPos();
            if (bowl.id == claim.id()
                    && run != null
                    && run.seq == claim.seq()
                    && p.getY() == at.getY()
                    && run.pendingAt(p.getX() - at.getX(), p.getZ() - at.getZ()) >= 0) {
                return ClaimState.LIVE;
            }
        }
        pedestal.claim = null;
        pedestal.markDirty();
        return ClaimState.NONE;
    }

    /** Whether a claim locks taking: live, unknown or unresolved. */
    static boolean claimHolds(ServerLevel level, PedestalBlockEntity pedestal) {
        return claimState(level, pedestal) != ClaimState.NONE;
    }

    /**
     * Whether a pedestal may take {@code stack} now: unclaimed takes anything; a live claim takes
     * only its pending component's promised stack (restoration); an unknown or unresolved one,
     * nothing.
     */
    static boolean mayRestore(ServerLevel level, PedestalBlockEntity pedestal, ItemStack stack) {
        return switch (claimState(level, pedestal)) {
            case NONE -> true;
            case LIVE -> {
                PedestalBlockEntity.Claim claim = java.util.Objects.requireNonNull(pedestal.claim);
                BlockPos at = BlockPos.of(claim.bowl());
                BowlBlockEntity bowl =
                        (BowlBlockEntity)
                                java.util.Objects.requireNonNull(level.getBlockEntity(at));
                InfusionRun run = java.util.Objects.requireNonNull(bowl.run);
                BlockPos p = pedestal.getBlockPos();
                int i = run.pendingAt(p.getX() - at.getX(), p.getZ() - at.getZ());
                yield i >= 0
                        && ItemStack.isSameItemSameComponents(stack, run.parts.get(i).promised());
            }
            default -> false;
        };
    }

    // --- Strikes, arms and commits ----------------------------------------------------------

    /** A permitted wand use on a bowl (infusion spec, "The second strike"). */
    static void strike(
            ServerLevel level, BowlBlockEntity bowl, ServerPlayer player, InteractionHand hand) {
        LevelState s = state(level);
        long now = level.getGameTime();
        if (bowl.ringing()) {
            subscribe(level, bowl, player, true);
            play(level, bowl.getBlockPos(), InfusionContent.STRIKE, 1.0f);
            return;
        }
        Arm arm = ARMS.get(player.getUUID());
        boolean commit =
                arm != null
                        && arm.kind == Kind.ARMED
                        && arm.dimension.equals(level.dimension())
                        && arm.pos.equals(bowl.getBlockPos())
                        && arm.bowlId == bowl.id
                        && arm.hasSeen
                        && arm.seen == arm.revision;
        Pending action =
                new Pending(
                        now,
                        player.getUUID(),
                        bowl.getBlockPos().immutable(),
                        bowl.id,
                        commit,
                        hand,
                        commit ? java.util.Objects.requireNonNull(arm).revision : 0);
        if (s.spend(now)) {
            service(level, action, player, bowl);
        } else {
            Pending old = s.pendingBy.put(player.getUUID(), action);
            if (old != null) {
                s.pending.remove(old);
            }
            s.pending.add(action);
        }
    }

    /** Services a strike or commit attempt, inside its use or from the pending queue. */
    private static void service(
            ServerLevel level, Pending action, ServerPlayer player, BowlBlockEntity bowl) {
        if (action.commit()) {
            commit(level, action, player, bowl);
        } else {
            assessStrike(level, bowl, player);
        }
    }

    private static void assessStrike(ServerLevel level, BowlBlockEntity bowl, ServerPlayer player) {
        Assessment a = AltarReading.assess(level, bowl, player, null);
        Arm arm = ARMS.get(player.getUUID());
        long now = level.getGameTime();
        if (arm != null
                && arm.kind == Kind.ARMED
                && arm.dimension.equals(level.dimension())
                && arm.pos.equals(bowl.getBlockPos())
                && arm.bowlId == bowl.id) {
            if (!a.inputs().same(arm.inputs)) {
                arm.revision++;
            }
        } else {
            end(player, arm);
            arm = new Arm(Kind.ARMED, level.dimension(), bowl.getBlockPos(), bowl.id, a.readout());
            ARMS.put(player.getUUID(), arm);
        }
        arm.inputs = a.inputs();
        arm.readout = a.readout();
        arm.lastStrike = now;
        scheduleArm(level, player, arm, now);
        send(player, arm, true);
        InfusionPlan plan = a.readout().ready();
        play(
                level,
                bowl.getBlockPos(),
                InfusionContent.STRIKE,
                plan == null ? 1.0f : 0.94f + 0.12f * plan.stability() / 100f);
    }

    /** The checked commit (infusion spec, "The commit"): checks 1-9, then the write. */
    private static void commit(
            ServerLevel level, Pending action, ServerPlayer player, BowlBlockEntity bowl) {
        Arm arm = ARMS.get(player.getUUID());
        // 1 and 2
        if (!player.isAlive()
                || player.isSpectator()
                || arm == null
                || arm.kind != Kind.ARMED
                || !arm.dimension.equals(level.dimension())
                || !arm.pos.equals(bowl.getBlockPos())
                || arm.bowlId != bowl.id
                || !player.getItemInHand(action.hand()).is(VisContent.wand())) {
            say(player, Component.translatable(InfusionLang.MESSAGE + "strike_first"));
            return;
        }
        // 3: dropped, no message
        BlockPos pos = bowl.getBlockPos();
        if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null
                || !player.isWithinBlockInteractionRange(pos, 1.0)
                || !level.mayInteract(player, pos)) {
            return;
        }
        // 4
        if (arm.revision != action.revision() || !arm.hasSeen || arm.seen != arm.revision) {
            stale(level, bowl, player, arm, false);
            return;
        }
        // 5: the recheck runs every foreign callback
        Assessment a = AltarReading.assess(level, bowl, player, null);
        if (!a.inputs().same(arm.inputs)) {
            stale(player, arm, true, a);
            return;
        }
        // 6
        if (a.readout().status() != InfusionStatus.READY || a.selected() == null) {
            arm.readout = a.readout();
            send(player, arm, true);
            say(player, Component.translatable(a.readout().key(), a.readout().args().toArray()));
            return;
        }
        AltarReading.Selected sel = a.selected();
        // 7
        boolean busy = bowl.busy();
        for (Cell c : sel.parts()) {
            busy |= c.be() == null || c.be().busy();
        }
        if (!hooks.idle() || busy) {
            say(player, Component.translatable(SharedMessages.BUSY));
            return;
        }
        // 8
        for (Cell c : sel.parts()) {
            if (c.be() == null || claimState(level, c.be()) != ClaimState.NONE) {
                arm.readout = a.readout();
                send(player, arm, true);
                say(player, promised(java.util.Objects.requireNonNull(c.be())));
                return;
            }
        }
        // 9: the live fingerprint, callback-free
        Inputs live = new Inputs(level, bowl, player, a.inputs().warp);
        if (!live.same(a.inputs())) {
            stale(player, arm, true, a);
            return;
        }
        // The commit: no foreign code from here to the notifications.
        if (bowl.runs == InfusionLimits.MAX_RUNS) {
            bowl.id = mintId(level);
            bowl.runs = 0;
        }
        int seq = bowl.runs + 1;
        bowl.runs = seq;
        long seed = InfusionMath.seed(level.getRandom().nextLong());
        List<InfusionRun.Part> parts = new ArrayList<>();
        for (Cell c : sel.parts()) {
            parts.add(
                    new InfusionRun.Part(
                            c.pos().getX() - pos.getX(),
                            c.pos().getZ() - pos.getZ(),
                            c.item().copyWithCount(1)));
        }
        InfusionPlan plan = java.util.Objects.requireNonNull(a.readout().ready());
        bowl.run =
                new InfusionRun(
                        seq,
                        seed,
                        player.getUUID(),
                        sel.holder().id().identifier(),
                        sel.holder().value().research(),
                        sel.holder().value().kind(),
                        sel.output(),
                        parts,
                        new boolean[parts.size()],
                        sel.aspects(),
                        sel.costs().stream().mapToInt(Integer::intValue).toArray(),
                        new int[sel.costs().size()],
                        java.util.Objects.requireNonNull(a.stability()),
                        java.util.Objects.requireNonNull(a.rules()),
                        FluxRulesService.current(),
                        0,
                        0,
                        null);
        bowl.stall = InfusionReadout.Stall.NONE;
        for (Cell c : sel.parts()) {
            PedestalBlockEntity p = java.util.Objects.requireNonNull(c.be());
            p.claim = new PedestalBlockEntity.Claim(pos.asLong(), bowl.id, seq);
            p.markDirty();
        }
        bowl.markDirty();
        schedule(level, bowl);
        arm.kind = Kind.RUN;
        arm.bowlId = bowl.id;
        arm.inputs = null;
        arm.hasSeen = false;
        arm.readout = runReadout(level, bowl);
        arm.lastStrike = level.getGameTime();
        notify(
                pos,
                () ->
                        post(
                                new InfusionPayload.Started(
                                        level,
                                        pos,
                                        bowl.id,
                                        seq,
                                        player.getUUID(),
                                        plan.recipe(),
                                        plan),
                                null));
        notify(pos, () -> play(level, pos, InfusionContent.BEGIN, 1.0f));
        notify(pos, () -> send(player, arm, true));
    }

    private static void stale(
            ServerLevel level, BowlBlockEntity bowl, ServerPlayer player, Arm arm, boolean bump) {
        stale(player, arm, bump, AltarReading.assess(level, bowl, player, heldOf(arm.readout)));
    }

    private static void stale(ServerPlayer player, Arm arm, boolean bump, Assessment fresh) {
        if (bump || !fresh.inputs().same(arm.inputs)) {
            arm.revision++;
        }
        arm.inputs = fresh.inputs();
        arm.readout = fresh.readout().asChanged();
        say(player, Component.translatable(SharedMessages.COST_CHANGED));
        send(player, arm, true);
    }

    /** "Held now" of a readout's cost cells, kept across refreshes. */
    private static @Nullable Map<ResourceKey<Aspect>, Integer> heldOf(
            @Nullable InfusionReadout readout) {
        Map<ResourceKey<Aspect>, Integer> held = new HashMap<>();
        if (readout != null && readout.ready() != null) {
            for (InfusionCost c : readout.ready().costs()) {
                held.put(c.aspect(), c.heldNow());
            }
        }
        return held;
    }

    /** A strike on a ringing bowl: a run subscription. */
    static void subscribe(
            ServerLevel level, BowlBlockEntity bowl, ServerPlayer player, boolean solicited) {
        Arm arm = ARMS.get(player.getUUID());
        if (arm == null
                || !arm.pos.equals(bowl.getBlockPos())
                || !arm.dimension.equals(level.dimension())) {
            end(player, arm);
            arm =
                    new Arm(
                            Kind.RUN,
                            level.dimension(),
                            bowl.getBlockPos(),
                            bowl.id,
                            runReadout(level, bowl));
            ARMS.put(player.getUUID(), arm);
        }
        arm.kind = Kind.RUN;
        arm.bowlId = bowl.id;
        arm.inputs = null;
        arm.hasSeen = false;
        arm.readout = runReadout(level, bowl);
        arm.lastStrike = level.getGameTime();
        scheduleArm(level, player, arm, level.getGameTime());
        send(player, arm, solicited);
    }

    /** Re-sends a subscription's readout after a change it made (an abandon's use). */
    static void refreshSubscription(
            ServerLevel level, BowlBlockEntity bowl, ServerPlayer player, boolean solicited) {
        Arm arm = ARMS.get(player.getUUID());
        if (arm != null && arm.kind == Kind.RUN && arm.pos.equals(bowl.getBlockPos())) {
            arm.readout = runReadout(level, bowl);
            send(player, arm, solicited);
        } else {
            subscribe(level, bowl, player, solicited);
        }
    }

    private static void scheduleArm(ServerLevel level, ServerPlayer player, Arm arm, long now) {
        LevelState s = state(level);
        s.arms.remove(new ArmDue(arm.due, player.getUUID()));
        arm.due = now + tuning().armRefresh();
        s.arms.add(new ArmDue(arm.due, player.getUUID()));
    }

    /** Ends an arm, sending {@code CLEARED} to its player. */
    private static void end(ServerPlayer player, @Nullable Arm arm) {
        if (arm == null) {
            return;
        }
        ARMS.remove(player.getUUID(), arm);
        if (network != null && !player.hasDisconnected()) {
            network.toPlayer(
                    player,
                    new InfusionPayloads.Readout(
                            arm.pos, arm.revision, Kind.CLEARED, false, Optional.empty()));
        }
    }

    /** Ends every subscription to a bowl's run (its completion, abandonment or removal). */
    static void endSubscriptions(ServerLevel level, BlockPos pos) {
        MinecraftServer server = level.getServer();
        for (Map.Entry<UUID, Arm> e : new ArrayList<>(ARMS.entrySet())) {
            Arm arm = e.getValue();
            if (arm.kind == Kind.RUN
                    && arm.pos.equals(pos)
                    && arm.dimension.equals(level.dimension())) {
                ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
                if (p != null) {
                    end(p, arm);
                } else {
                    ARMS.remove(e.getKey(), arm);
                }
            }
        }
    }

    private static void send(ServerPlayer player, Arm arm, boolean solicited) {
        if (network != null) {
            network.toPlayer(
                    player,
                    new InfusionPayloads.Readout(
                            arm.pos, arm.revision, arm.kind, solicited, Optional.of(arm.readout)));
        }
    }

    /** The seen ack (infusion spec, "Arms and revisions"): at most 4 per player per tick. */
    public static void onSeen(InfusionPayloads.Seen seen, ServerPlayer player) {
        Arm arm = ARMS.get(player.getUUID());
        long now = player.level().getGameTime();
        if (arm == null) {
            return;
        }
        if (arm.seenTick != now) {
            arm.seenTick = now;
            arm.seenCount = 0;
        }
        if (++arm.seenCount > MAX_SEEN_PER_TICK) {
            return; // a rate cap: dropped with no reply
        }
        if (arm.kind == Kind.ARMED
                && arm.dimension.equals(player.level().dimension())
                && arm.pos.equals(seen.pos())
                && arm.revision == seen.revision()) {
            arm.seen = seen.revision();
            arm.hasSeen = true;
        }
    }

    /** The plain readout of a bowl for a player, as a strike would assess it; no arm is made. */
    public static InfusionReadout readout(ServerLevel level, BlockPos pos, ServerPlayer player) {
        if (!(level.getBlockEntity(pos) instanceof BowlBlockEntity bowl)) {
            throw new IllegalArgumentException("no Singing Bowl at " + pos);
        }
        return bowl.ringing()
                ? runReadout(level, bowl)
                : AltarReading.assess(level, bowl, player, null).readout();
    }

    /** The player's current arm's revision and seen state, for tests; null without an arm. */
    public static int @Nullable [] armState(ServerPlayer player) {
        Arm arm = ARMS.get(player.getUUID());
        return arm == null
                ? null
                : new int[] {
                    arm.kind == Kind.RUN ? 1 : 0,
                    arm.revision,
                    arm.hasSeen ? arm.seen : Integer.MIN_VALUE
                };
    }

    // --- Run readouts and views -------------------------------------------------------------

    /** A ringing bowl's run readout: frozen figures, live counters, the abandon price now. */
    static InfusionReadout runReadout(ServerLevel level, BowlBlockEntity bowl) {
        InfusionRun run = java.util.Objects.requireNonNull(bowl.run);
        List<InfusionCost> costs = new ArrayList<>();
        List<InfusionCost> cells = new ArrayList<>();
        int[] drawn = new int[run.costs.length];
        for (int i = 0; i < run.costs.length; i++) {
            costs.add(new InfusionCost(run.aspects.get(i), run.costs[i], 0));
            cells.add(new InfusionCost(run.aspects.get(i), run.costs[i] - run.drawn[i], 0));
            drawn[i] = run.drawn[i];
        }
        InfusionReadout.Stall stall =
                AltarReading.boxLoaded(level, bowl.getBlockPos())
                        ? bowl.stall
                        : InfusionReadout.Stall.PAUSED;
        long flux =
                (long) run.rules.max()
                        * FluxMath.essentiaFluxBound(
                                run.rules.spill(), SpillCause.UPSET, run.fluxRules);
        InfusionReadout.Run r =
                new InfusionReadout.Run(
                        ResourceKey.create(
                                net.minecraft.core.registries.Registries.RECIPE, run.recipe),
                        run.output,
                        run.stability.s(),
                        run.stability.chance(),
                        run.drawnTotal(),
                        run.owedTotal(),
                        costs,
                        cells,
                        run.absorbed(),
                        run.parts.size(),
                        run.upsets,
                        run.rules.max(),
                        stall,
                        stall == InfusionReadout.Stall.COMPONENT ? bowl.missing : null,
                        flux,
                        FluxMath.essentiaFlux(
                                drawn, SpillCause.ABANDONED, FluxRulesService.current()),
                        run.abandon != null && run.abandon.until() > level.getGameTime());
        return new InfusionReadout(
                InfusionStatus.RINGING,
                null,
                List.of(run.output.getHoverName()),
                level.dimension(),
                bowl.getBlockPos(),
                null,
                r,
                false);
    }

    /** A bowl's synced view, computed from its run and its pedestals now. */
    static BowlView view(BowlBlockEntity bowl) {
        InfusionRun run = bowl.run;
        if (run == null || !(bowl.getLevel() instanceof ServerLevel level)) {
            return BowlView.NONE;
        }
        List<Integer> pending = new ArrayList<>();
        BlockPos at = bowl.getBlockPos();
        for (int i = 0; i < run.parts.size(); i++) {
            if (!run.done[i]) {
                InfusionRun.Part p = run.parts.get(i);
                BlockPos pp = at.offset(p.dx(), 0, p.dz());
                boolean present =
                        level.isLoaded(pp)
                                && level.getBlockEntity(pp) instanceof PedestalBlockEntity pe
                                && ItemStack.isSameItemSameComponents(pe.item, p.promised());
                pending.add(BowlView.pack(p.dx(), p.dz(), present));
            }
        }
        Optional<net.minecraft.resources.Identifier> drawing = Optional.empty();
        for (int i = 0; i < run.costs.length; i++) {
            if (run.drawn[i] < run.costs[i]) {
                drawing = Optional.of(run.aspects.get(i).identifier());
                break;
            }
        }
        return new BowlView(
                pending,
                drawing,
                run.drawnTotal(),
                run.owedTotal(),
                run.absorbed(),
                run.parts.size());
    }

    // --- Loading, scheduling and the tick ----------------------------------------------------

    static void onBowlLoad(ServerLevel level, BowlBlockEntity bowl) {
        if (bowl.unreadable()) {
            hooks.invalidate(level, bowl.getBlockPos());
            return;
        }
        if (bowl.id == 0) {
            bowl.id = mintId(level);
            bowl.setChanged();
        }
        if (bowl.run != null) {
            schedule(level, bowl);
        }
    }

    static void onBowlUnload(ServerLevel level, BowlBlockEntity bowl) {
        InfusionRun run = bowl.run;
        if (run != null) {
            LevelState s = state(level);
            s.steps.removeIf(d -> d.pos() == bowl.getBlockPos().asLong() && d.seq() == run.seq);
        }
    }

    private static long mintId(ServerLevel level) {
        long id;
        do {
            id = level.getRandom().nextLong();
        } while (id == 0);
        return id;
    }

    /** Enters a ringing bowl in its level's step queue at its next phase tick. */
    static void schedule(ServerLevel level, BowlBlockEntity bowl) {
        InfusionRun run = java.util.Objects.requireNonNull(bowl.run);
        long pos = bowl.getBlockPos().asLong();
        LevelState s = state(level);
        s.steps.removeIf(d -> d.pos() == pos);
        s.steps.add(
                new Due(
                        InfusionMath.nextDue(level.getGameTime(), pos, run.rules.interval()),
                        pos,
                        bowl.id,
                        run.seq));
    }

    /** Leaves the step queue (the run's end). */
    static void unschedule(ServerLevel level, BlockPos pos) {
        long p = pos.asLong();
        state(level).steps.removeIf(d -> d.pos() == p);
    }

    /**
     * The end of a level's tick: pending assessments, then arm refreshes, both on the allowance;
     * then the step queue; then the coalesced flush. Never loads a chunk.
     */
    public static void levelTick(ServerLevel level) {
        LevelState s = LEVELS.get(level);
        if (s == null) {
            return;
        }
        long now = level.getGameTime();
        servicePending(level, s, now);
        refreshArms(level, s, now);
        runSteps(level, s, now);
        flush(level, s);
    }

    private static void servicePending(ServerLevel level, LevelState s, long now) {
        while (!s.pending.isEmpty()) {
            Pending p = s.pending.first();
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(p.player());
            if (now - p.tick() >= InfusionLimits.ASSESS_TTL) {
                s.pending.pollFirst();
                s.pendingBy.remove(p.player(), p);
                say(player, Component.translatable(InfusionLang.MESSAGE + "too_busy"));
                continue;
            }
            if (!s.spend(now)) {
                return;
            }
            s.pending.pollFirst();
            s.pendingBy.remove(p.player(), p);
            if (player == null
                    || !player.isAlive()
                    || player.isSpectator()
                    || !player.level().equals(level)
                    || !player.isWithinBlockInteractionRange(p.pos(), 1.0)
                    || !level.mayInteract(player, p.pos())
                    || !(level.getBlockEntity(p.pos()) instanceof BowlBlockEntity bowl)
                    || bowl.id != p.bowlId()) {
                continue; // revalidation failed: dropped
            }
            if (bowl.ringing()) {
                subscribe(level, bowl, player, true);
            } else {
                service(level, p, player, bowl);
            }
        }
    }

    private static void refreshArms(ServerLevel level, LevelState s, long now) {
        List<ArmDue> kept = new ArrayList<>();
        int pops = 0;
        while (!s.arms.isEmpty()
                && pops < InfusionLimits.MAX_QUEUE_POPS_PER_TICK
                && s.arms.first().due() <= now) {
            ArmDue d = s.arms.pollFirst();
            pops++;
            Arm arm = ARMS.get(d.player());
            if (arm == null || arm.due != d.due()) {
                continue; // stale
            }
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(d.player());
            if (player == null) {
                ARMS.remove(d.player(), arm);
                continue;
            }
            BlockEntity be = level.getBlockEntity(arm.pos);
            if (!player.isAlive()
                    || !player.level().equals(level)
                    || !arm.dimension.equals(level.dimension())
                    || !player.isWithinBlockInteractionRange(arm.pos, 1.0)
                    || now - arm.lastStrike > tuning().armTtl()
                    || !(be instanceof BowlBlockEntity bowl)
                    || bowl.id != arm.bowlId) {
                end(player, arm);
                continue;
            }
            if (arm.kind == Kind.RUN) {
                if (bowl.run == null) {
                    end(player, arm);
                    continue;
                }
                InfusionReadout next = runReadout(level, bowl);
                if (!sameRun(next, arm.readout)) {
                    arm.readout = next;
                    send(player, arm, false);
                }
            } else if (bowl.ringing()) {
                subscribe(level, bowl, player, false);
                continue;
            } else {
                if (!s.spend(now)) {
                    kept.add(d);
                    continue;
                }
                Inputs fresh =
                        new Inputs(
                                level,
                                bowl,
                                player,
                                WarpDials.infusionPenalty(player, VisKind.ORDINARY));
                if (!fresh.same(arm.inputs)) {
                    Assessment a = AltarReading.assess(level, bowl, player, heldOf(arm.readout));
                    arm.revision++;
                    arm.inputs = a.inputs();
                    arm.readout = a.readout();
                    send(player, arm, false);
                }
            }
            arm.due = now + tuning().armRefresh();
            s.arms.add(new ArmDue(arm.due, d.player()));
        }
        s.arms.addAll(kept);
    }

    /** Whether two run readouts show the same thing (the output is frozen, so it is skipped). */
    private static boolean sameRun(InfusionReadout a, InfusionReadout b) {
        InfusionReadout.Run x = a.run();
        InfusionReadout.Run y = b.run();
        return x != null
                && y != null
                && x.drawn() == y.drawn()
                && x.owed() == y.owed()
                && x.cells().equals(y.cells())
                && x.absorbed() == y.absorbed()
                && x.upsets() == y.upsets()
                && x.stall() == y.stall()
                && java.util.Objects.equals(x.missing(), y.missing())
                && x.abandonFlux() == y.abandonFlux()
                && x.abandonArmed() == y.abandonArmed();
    }

    private static void runSteps(ServerLevel level, LevelState s, long now) {
        List<Due> again = new ArrayList<>();
        int pops = 0;
        int steps = 0;
        while (!s.steps.isEmpty()
                && pops < InfusionLimits.MAX_QUEUE_POPS_PER_TICK
                && steps < InfusionLimits.MAX_STEPS_PER_TICK
                && s.steps.first().due() <= now) {
            Due d = s.steps.pollFirst();
            pops++;
            BlockPos pos = BlockPos.of(d.pos());
            if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null
                    || !(level.getBlockEntity(pos) instanceof BowlBlockEntity bowl)
                    || bowl.isRemoved()
                    || bowl.id != d.id()
                    || bowl.run == null
                    || bowl.run.seq != d.seq()) {
                continue; // stale: the bowl is gone or replaced, or the run ended
            }
            steps++;
            long next;
            try (var _ = com.andrewbrogoff.vftv.core.common.TickBudget.enter("infusion")) {
                next = InfusionRunner.step(level, bowl, now);
            }
            if (next == Long.MIN_VALUE) {
                continue; // the run ended
            }
            InfusionRun run = bowl.run;
            if (run != null && !bowl.isRemoved()) {
                again.add(new Due(next < 0 ? d.due() : next, d.pos(), bowl.id, run.seq));
            }
        }
        s.stepsRun = steps;
        s.popsRun = pops;
        s.steps.addAll(again);
    }

    /** The step queue's size and the last tick's steps and pops, for the bounds tests. */
    public static int[] queueStats(ServerLevel level) {
        LevelState s = state(level);
        return new int[] {s.steps.size(), s.stepsRun, s.popsRun, s.pending.size(), s.assessments};
    }

    // --- Sync -----------------------------------------------------------------------------

    static void markDirty(AltarBlockEntity be) {
        be.setChanged();
        if (be.getLevel() instanceof ServerLevel level) {
            state(level).dirty.add(be);
        }
    }

    private static void flush(ServerLevel level, LevelState s) {
        if (s.dirty.isEmpty()) {
            return;
        }
        List<AltarBlockEntity> held = new ArrayList<>();
        for (AltarBlockEntity be : s.dirty) {
            if (be instanceof BowlBlockEntity bowl && bowl.operation) {
                held.add(be); // no sync is published while an operation is open
            } else if (!be.isRemoved() && level.getBlockEntity(be.getBlockPos()) == be) {
                BlockState state = be.getBlockState();
                level.sendBlockUpdated(be.getBlockPos(), state, state, Block.UPDATE_CLIENTS);
            }
        }
        s.dirty.clear();
        s.dirty.addAll(held);
    }

    // --- Removal ----------------------------------------------------------------------------

    /**
     * {@code preRemoveSideEffects} (infusion spec, "Removal"): deferred while an operation or a
     * transaction is open; otherwise an unreadable bowl's record is lost with one WARN, and a run
     * is spilled. Returns whether the removal was deferred (no drop now).
     */
    static boolean removing(ServerLevel level, AltarBlockEntity be) {
        if ((be instanceof BowlBlockEntity bowl && bowl.operation) || be.busy()) {
            be.pendingRemoval = true;
            return true;
        }
        if (be instanceof BowlBlockEntity bowl) {
            if (bowl.unreadable()) {
                LOGGER.warn(
                        "Singing Bowl at {} removed with an unreadable record, which is lost: {}",
                        bowl.getBlockPos(),
                        bowl.raw());
            } else if (bowl.run != null) {
                InfusionRunner.removeRun(level, bowl);
            }
        }
        return false;
    }

    /** An operation or a root closed: run a deferred removal exactly once, then drop. */
    static void settle(AltarBlockEntity be) {
        if (!be.pendingRemoval
                || be.busy()
                || (be instanceof BowlBlockEntity bowl && bowl.operation)
                || !(be.getLevel() instanceof ServerLevel level)) {
            return;
        }
        be.pendingRemoval = false;
        try {
            if (be instanceof BowlBlockEntity bowl && bowl.run != null && !bowl.unreadable()) {
                InfusionRunner.removeRun(level, bowl);
            }
        } finally {
            net.minecraft.world.Containers.dropContents(level, be.getBlockPos(), be);
            be.item = ItemStack.EMPTY;
        }
    }

    // --- Helpers ----------------------------------------------------------------------------

    static void post(
            InfusionPayload payload, com.andrewbrogoff.vftv.api.flux.@Nullable FluxRules flux) {
        try {
            hooks.post(payload, flux);
        } catch (RuntimeException e) {
            LOGGER.error("An InfusionEvent listener threw; the state stands", e);
        }
    }

    /** Runs one notification, logging a throw so the remaining ones still run. */
    static void notify(BlockPos pos, Runnable notification) {
        try {
            notification.run();
        } catch (RuntimeException e) {
            LOGGER.error("An infusion notification at {} threw; the state stands", pos, e);
        }
    }

    static void play(
            ServerLevel level,
            BlockPos pos,
            net.minecraft.resources.Identifier sound,
            float pitch) {
        SoundEvent event = InfusionContent.sound(sound);
        level.playSound(null, pos, event, SoundSource.BLOCKS, 1.0f, pitch);
    }

    static void say(@Nullable ServerPlayer player, Component line) {
        if (player != null) {
            StatusLines.send(player, line);
        }
    }
}
