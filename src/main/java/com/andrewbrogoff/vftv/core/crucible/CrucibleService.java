package com.andrewbrogoff.vftv.core.crucible;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.api.aspect.AspectValues;
import com.andrewbrogoff.vftv.api.crucible.CrucibleKeys;
import com.andrewbrogoff.vftv.api.crucible.CrucibleLimits;
import com.andrewbrogoff.vftv.api.crucible.CruciblePayload;
import com.andrewbrogoff.vftv.api.essentia.SpillCause;
import com.andrewbrogoff.vftv.core.aspect.AspectLang;
import com.andrewbrogoff.vftv.core.aspect.AspectLookup;
import com.andrewbrogoff.vftv.core.aspect.KnowledgeService;
import com.andrewbrogoff.vftv.core.aspect.model.Knowledge;
import com.andrewbrogoff.vftv.core.crucible.model.CrucibleMath;
import com.andrewbrogoff.vftv.core.crucible.model.CrucibleTuning;
import com.andrewbrogoff.vftv.core.crucible.model.Decision;
import com.andrewbrogoff.vftv.core.research.ResearchService;
import com.andrewbrogoff.vftv.core.text.StatusLines;
import com.andrewbrogoff.vftv.core.text.model.SharedMessages;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongRBTreeSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The crucible's server logic (crucible spec): heat and the refresh queue, the bucket paths,
 * admission, the step (dissolving, refusing and the provisional craft), spilling, and the coalesced
 * sync. Server thread only.
 */
public final class CrucibleService {
    /** The tuning file under {@code data/vftv/}. */
    public static final String TUNING_PATH = "tuning/crucible.json";

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double SPAWN_Y = 1.0625;
    private static final int SPILL_PARTICLES = 8;
    private static final AABB INTERIOR = CrucibleBlock.INTERIOR.bounds();

    private static volatile CrucibleTuning tuning = CrucibleTuning.DEFAULTS;
    private static volatile CrucibleHooks hooks = CrucibleHooks.NONE;
    private static final Map<ServerLevel, LevelState> LEVELS = new HashMap<>();

    private CrucibleService() {}

    /** The per-level loaded set, refresh queue and dirty set. */
    private static final class LevelState {
        final Long2ObjectOpenHashMap<CrucibleBlockEntity> loaded = new Long2ObjectOpenHashMap<>();
        final LongRBTreeSet queue = new LongRBTreeSet();
        final Set<CrucibleBlockEntity> dirty = new LinkedHashSet<>();
    }

    private static LevelState state(ServerLevel level) {
        return LEVELS.computeIfAbsent(level, l -> new LevelState());
    }

    /** Installs the platform's hooks; called once from the platform's wiring. */
    public static void install(CrucibleHooks platform) {
        hooks = platform;
    }

    /** The platform's hooks. */
    public static CrucibleHooks hooks() {
        return hooks;
    }

    /** The tuning in force. */
    public static CrucibleTuning tuning() {
        return tuning;
    }

    /** Reads a tuning file's text, or why it is invalid. */
    public static DataResult<CrucibleTuning> parse(String json) {
        try {
            return DataResult.success(CrucibleTuning.parse(json));
        } catch (IllegalArgumentException e) {
            return DataResult.error(e::getMessage);
        }
    }

    /**
     * Applies a tuning read: an invalid one refuses the world at open and keeps the previous record
     * on {@code /reload}. A new record queues every loaded crucible, so trackers get the capacity.
     *
     * @throws IllegalStateException for an invalid read while the world opens
     */
    public static synchronized void applyTuning(
            DataResult<CrucibleTuning> read, boolean reloading) {
        read.ifSuccess(
                        t -> {
                            tuning = t;
                            if (reloading) {
                                LEVELS.keySet().forEach(CrucibleService::queueAll);
                                logUnpayable();
                            }
                        })
                .ifError(
                        error -> {
                            String message =
                                    "Invalid data/vftv/" + TUNING_PATH + ": " + error.message();
                            if (!reloading) {
                                throw new IllegalStateException(message);
                            }
                            LOGGER.error("{}; keeping the previous crucible tuning", message);
                        });
    }

    /** Logs every recipe whose cost can never fit the capacity in force. */
    static void logUnpayable() {
        int capacity = tuning.capacity();
        for (RecipeHolder<CrucibleRecipe> holder : CrucibleRecipes.server().recipes()) {
            int total = holder.value().total();
            if (total > capacity) {
                LOGGER.warn(
                        "Crucible recipe {} costs {} in all, over the pool capacity {}: it can"
                                + " never be paid",
                        holder.id().identifier(),
                        total,
                        capacity);
            }
        }
    }

    /** Drops every level's state when the server stops. */
    public static void clear() {
        LEVELS.clear();
        tuning = CrucibleTuning.DEFAULTS;
    }

    // --- Heat and the refresh queue -------------------------------------------------------

    static void onLoad(ServerLevel level, CrucibleBlockEntity be) {
        LevelState s = state(level);
        long pos = be.getBlockPos().asLong();
        s.loaded.put(pos, be);
        s.queue.add(pos);
        be.queued = true;
    }

    static void onUnload(ServerLevel level, CrucibleBlockEntity be) {
        LevelState s = state(level);
        s.loaded.remove(be.getBlockPos().asLong(), be);
    }

    /** Queues every loaded crucible of a level (a tag or tuning reload). */
    public static void queueAll(ServerLevel level) {
        LevelState s = state(level);
        s.loaded.forEach(
                (pos, be) -> {
                    s.queue.add(pos);
                    be.queued = true;
                });
    }

    /** Queues every loaded crucible of every level. */
    public static void queueAll() {
        new ArrayList<>(LEVELS.keySet()).forEach(CrucibleService::queueAll);
    }

    /**
     * The end of a level's tick: up to {@value CrucibleLimits#MAX_REFRESHES_PER_TICK} refreshes in
     * ascending position, then the coalesced flush. Never loads a chunk.
     */
    public static void levelTick(ServerLevel level) {
        LevelState s = LEVELS.get(level);
        if (s == null) {
            return;
        }
        List<Long> waiting = new ArrayList<>();
        for (int i = 0; i < CrucibleLimits.MAX_REFRESHES_PER_TICK && !s.queue.isEmpty(); i++) {
            long pos = s.queue.firstLong();
            s.queue.remove(pos);
            BlockPos at = BlockPos.of(pos);
            LevelChunk chunk = level.getChunkSource().getChunkNow(at.getX() >> 4, at.getZ() >> 4);
            CrucibleBlockEntity loaded = s.loaded.get(pos);
            if (chunk != null && chunk.getBlockEntity(at) instanceof CrucibleBlockEntity be) {
                refresh(level, be);
            } else if (chunk == null && loaded != null && !loaded.isRemoved()) {
                // Loaded but its chunk isn't full yet: it stays queued, so it waits, not stalls.
                waiting.add(pos);
            }
        }
        s.queue.addAll(waiting);
        flush(level, s);
    }

    private static void refresh(ServerLevel level, CrucibleBlockEntity be) {
        be.queued = false;
        BlockPos pos = be.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof CrucibleBlock)) {
            return;
        }
        BlockState next = CrucibleBlock.withHeat(state, level.getBlockState(pos.below()));
        if (!next.equals(state)) {
            level.setBlock(pos, next, Block.UPDATE_CLIENTS);
        }
        markDirty(be);
    }

    // --- Sync ----------------------------------------------------------------------------

    static void markDirty(CrucibleBlockEntity be) {
        be.setChanged();
        if (be.getLevel() instanceof ServerLevel level) {
            state(level).dirty.add(be);
        }
    }

    private static void flush(ServerLevel level, LevelState s) {
        if (s.dirty.isEmpty()) {
            return;
        }
        List<CrucibleBlockEntity> held = new ArrayList<>();
        for (CrucibleBlockEntity be : s.dirty) {
            if (be.operation) {
                held.add(be); // no sync is published while an operation is open
            } else if (!be.isRemoved() && level.getBlockEntity(be.getBlockPos()) == be) {
                BlockState state = be.getBlockState();
                level.sendBlockUpdated(be.getBlockPos(), state, state, Block.UPDATE_CLIENTS);
            }
        }
        s.dirty.clear();
        s.dirty.addAll(held);
    }

    // --- Buckets -------------------------------------------------------------------------

    static InteractionResult useBucket(
            Level level,
            BlockPos pos,
            BlockState state,
            Player player,
            InteractionHand hand,
            ItemStack held,
            boolean waterBucket) {
        boolean water = state.getValue(CrucibleBlock.WATER);
        if (!(level instanceof ServerLevel server)
                || !(level.getBlockEntity(pos) instanceof CrucibleBlockEntity be)) {
            return waterBucket != water ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
        }
        ServerPlayer sp = player instanceof ServerPlayer p ? p : null;
        if (be.unreadable()) {
            feedback(sp, Component.translatable(SharedMessages.UNREADABLE));
            return InteractionResult.CONSUME;
        }
        if (be.busy() || be.operation) {
            feedback(sp, Component.translatable(SharedMessages.BUSY));
            return InteractionResult.CONSUME;
        }
        if (waterBucket) {
            if (water) {
                return InteractionResult.CONSUME;
            }
            BlockState filled =
                    CrucibleBlock.withHeat(
                            state.setValue(CrucibleBlock.WATER, true),
                            level.getBlockState(pos.below()));
            level.setBlock(pos, filled, Block.UPDATE_ALL);
            player.setItemInHand(
                    hand, ItemUtils.createFilledResult(held, player, new ItemStack(Items.BUCKET)));
            play(server, pos, CrucibleContent.fillSound());
            level.gameEvent(player, GameEvent.FLUID_PLACE, pos);
            markDirty(be);
            return InteractionResult.SUCCESS;
        }
        if (!water) {
            return InteractionResult.CONSUME;
        }
        return empty(server, pos, state, be, player, hand, held);
    }

    /** Emptying by bucket (crucible spec, "Emptying by bucket"). */
    private static InteractionResult empty(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            CrucibleBlockEntity be,
            Player player,
            InteractionHand hand,
            ItemStack held) {
        be.operation = true;
        SortedMap<String, Integer> spilled = be.pool().snapshot();
        boolean changed;
        try {
            be.pool().clear();
            changed =
                    level.setBlock(
                            pos,
                            state.setValue(CrucibleBlock.WATER, false)
                                    .setValue(CrucibleBlock.BOILING, false),
                            Block.UPDATE_ALL);
            if (!changed) {
                be.pool().restore(spilled);
            }
        } finally {
            be.operation = false;
        }
        if (!changed) {
            return InteractionResult.FAIL;
        }
        player.setItemInHand(
                hand,
                ItemUtils.createFilledResult(held, player, new ItemStack(Items.WATER_BUCKET)));
        level.gameEvent(player, GameEvent.FLUID_PICKUP, pos);
        if (!be.isRemoved()) {
            markDirty(be);
        }
        play(level, pos, CrucibleContent.emptySound());
        if (!spilled.isEmpty()) {
            spill(
                    level,
                    pos,
                    player instanceof ServerPlayer p ? p : null,
                    spilled,
                    SpillCause.EMPTIED);
        }
        return InteractionResult.SUCCESS;
    }

    // --- Removal and transactions --------------------------------------------------------

    static void onRemoved(ServerLevel level, CrucibleBlockEntity be) {
        if (be.operation) {
            return; // the operation owns the pool
        }
        if (be.busy()) {
            be.pendingRemoval = true;
            return;
        }
        if (be.unreadable()) {
            LOGGER.warn(
                    "Crucible at {} removed with an unreadable record, which is lost: {}",
                    be.getBlockPos(),
                    be.raw());
            return;
        }
        spillRemoved(level, be);
    }

    /** The open transaction's root closed: run a pending removal on the pool it left. */
    static void transactionClosed(CrucibleBlockEntity be) {
        if (be.pendingRemoval && be.getLevel() instanceof ServerLevel level) {
            be.pendingRemoval = false;
            spillRemoved(level, be);
        }
    }

    private static void spillRemoved(ServerLevel level, CrucibleBlockEntity be) {
        if (be.pool().isEmpty()) {
            return;
        }
        SortedMap<String, Integer> spilled = be.pool().snapshot();
        be.pool().clear();
        spill(level, be.getBlockPos(), null, spilled, SpillCause.REMOVED);
    }

    private static void spill(
            ServerLevel level,
            BlockPos pos,
            @Nullable ServerPlayer player,
            SortedMap<String, Integer> spilled,
            SpillCause cause) {
        play(level, pos, CrucibleContent.spillSound());
        level.sendParticles(
                ParticleTypes.SMOKE,
                pos.getX() + 0.5,
                pos.getY() + 1.05,
                pos.getZ() + 0.5,
                SPILL_PARTICLES,
                0.3,
                0.05,
                0.3,
                0.01);
        post(new CruciblePayload.Spilled(level, pos, player, keys(spilled), cause));
    }

    // --- Admission and the step ----------------------------------------------------------

    /**
     * A player tossed an item: a sneak-drop carries the dissolve mark (captured now, never later).
     */
    public static void onToss(Player player, ItemEntity item) {
        if (!player.level().isClientSide() && player.isShiftKeyDown()) {
            hooks.markDissolve(item);
        }
    }

    /** An entity touches the interior (server only): offer it on a phase tick. */
    static void contact(ServerLevel level, BlockPos pos, BlockState state, Entity entity) {
        if (!(entity instanceof ItemEntity item)
                || !item.isAlive()
                || !state.getValue(CrucibleBlock.BOILING)
                || !(level.getBlockEntity(pos) instanceof CrucibleBlockEntity be)
                || be.unreadable()
                || be.queued
                || be.operation
                || be.busy()
                || be.stepping) {
            return;
        }
        long time = level.getGameTime();
        if (Long.remainderUnsigned(time, tuning.stepInterval()) != be.phase(tuning.stepInterval())
                || hooks.refused(item)) {
            return;
        }
        be.candidates.offer(time, item.getId());
    }

    /** The boiling ticker: a step on this crucible's phase ticks. */
    static void tick(Level level, BlockPos pos, BlockState state, CrucibleBlockEntity be) {
        int interval = tuning.stepInterval();
        if (!(level instanceof ServerLevel server)
                || Long.remainderUnsigned(level.getGameTime(), interval) != be.phase(interval)) {
            return;
        }
        try (var _ = com.andrewbrogoff.vftv.core.common.TickBudget.enter("crucible")) {
            step(server, pos, be);
        }
    }

    /** What one step did: its sound and each thrower's first refusal. */
    private static final class StepResult {
        boolean crafted;
        boolean dissolved;
        boolean refused;
        final Map<ServerPlayer, Component> feedback = new LinkedHashMap<>();
    }

    /** One step (crucible spec, "Admission" and "Processing an entity"). */
    static void step(ServerLevel level, BlockPos pos, CrucibleBlockEntity be) {
        if (be.unreadable() || be.queued || be.operation || be.busy() || be.stepping) {
            return;
        }
        int[] ids = be.candidates.drain(level.getGameTime());
        if (ids.length == 0) {
            return;
        }
        StepResult result = new StepResult();
        be.stepping = true;
        try {
            for (int id : ids) {
                if (level.getEntity(id) instanceof ItemEntity item && live(level, pos, be, item)) {
                    process(level, pos, be, item, result);
                }
            }
        } finally {
            be.stepping = false;
        }
        SoundEvent sound =
                result.crafted
                        ? CrucibleContent.craftSound()
                        : result.dissolved
                                ? CrucibleContent.dissolveSound()
                                : result.refused ? CrucibleContent.refuseSound() : null;
        if (sound != null) {
            play(level, pos, sound);
        }
        result.feedback.forEach(StatusLines::send);
    }

    private static boolean live(
            ServerLevel level, BlockPos pos, CrucibleBlockEntity be, ItemEntity item) {
        return item.isAlive()
                && !hooks.refused(item)
                && item.getBoundingBox().intersects(INTERIOR.move(pos))
                && level.getBlockEntity(pos) == be
                && !be.isRemoved()
                && !be.unreadable()
                && level.getBlockState(pos).getValue(CrucibleBlock.BOILING);
    }

    private static void process(
            ServerLevel level,
            BlockPos pos,
            CrucibleBlockEntity be,
            ItemEntity item,
            StepResult result) {
        ItemStack stack = item.getItem();
        ServerPlayer thrower = thrower(item);
        if (isProtected(stack)) {
            refuse(level, pos, item, thrower, CrucibleRefusal.PROTECTED, null, result);
            return;
        }
        if (hooks.dissolve(item)) {
            dissolve(level, pos, be, item, thrower, result);
            return;
        }
        List<RecipeHolder<CrucibleRecipe>> holders =
                thrower == null ? List.of() : CrucibleRecipes.candidates(thrower, stack);
        List<Decision.Candidate> candidates = new ArrayList<>(holders.size());
        holders.forEach(
                h ->
                        candidates.add(
                                new Decision.Candidate(
                                        h.id().identifier().toString(), h.value().cost())));
        Decision decision = Decision.decide(false, false, candidates, be.pool());
        switch (decision.kind()) {
            case CRAFT -> {
                RecipeHolder<CrucibleRecipe> holder =
                        holders.get(candidates.indexOf(decision.recipe()));
                craft(
                        level,
                        pos,
                        be,
                        item,
                        java.util.Objects.requireNonNull(thrower),
                        holder,
                        result);
            }
            case SHORT ->
                    refuse(
                            level,
                            pos,
                            item,
                            thrower,
                            CrucibleRefusal.SHORT,
                            aspectList(thrower, decision.shortfall()),
                            result);
            default -> dissolve(level, pos, be, item, thrower, result);
        }
    }

    /**
     * Whether a stack is protected: in {@code #vftv:crucible_refuses}, or carrying contents or
     * writing (crucible spec, "Protection").
     */
    public static boolean isProtected(ItemStack stack) {
        if (stack.is(CrucibleKeys.CRUCIBLE_REFUSES)) {
            return true;
        }
        ItemContainerContents container = stack.get(DataComponents.CONTAINER);
        if (container != null && container.nonEmptyItemCopyStream().findAny().isPresent()) {
            return true;
        }
        BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null && !bundle.isEmpty()) {
            return true;
        }
        WritableBookContent writable = stack.get(DataComponents.WRITABLE_BOOK_CONTENT);
        if (writable != null && !writable.pages().isEmpty()) {
            return true;
        }
        return stack.has(DataComponents.WRITTEN_BOOK_CONTENT);
    }

    /**
     * The item entity's thrower, server-wide, online and alive; null for none. Vanilla 26.2
     * resolves the owner through {@code getEntityInAnyDimension}, so this covers other levels.
     */
    static @Nullable ServerPlayer thrower(ItemEntity item) {
        if (!(item.getOwner() instanceof ServerPlayer player)
                || !player.isAlive()
                || player.hasDisconnected()) {
            return null;
        }
        return java.util.Objects.equals(
                        player,
                        player.level().getServer().getPlayerList().getPlayer(player.getUUID()))
                ? player
                : null;
    }

    private static void dissolve(
            ServerLevel level,
            BlockPos pos,
            CrucibleBlockEntity be,
            ItemEntity item,
            @Nullable ServerPlayer thrower,
            StepResult result) {
        ItemStack stack = item.getItem();
        AspectValues values = AspectLookup.item(stack.getItem());
        if (values == null || values.aspects().isEmpty()) {
            refuse(level, pos, item, thrower, CrucibleRefusal.NOTHING, null, result);
            return;
        }
        Map<String, Integer> v = new TreeMap<>();
        values.aspects().forEach((k, a) -> v.put(k.identifier().toString(), a));
        int k = be.pool().fit(v, stack.getCount(), be.capacity());
        if (k == 0) {
            refuse(level, pos, item, thrower, CrucibleRefusal.FULL, null, result);
            return;
        }
        be.pool().credit(v, k);
        ItemStack dissolved = stack.copyWithCount(k);
        ItemStack left = stack.copyWithCount(stack.getCount() - k);
        if (left.isEmpty()) {
            item.discard();
        } else {
            item.setItem(left);
        }
        markDirty(be);
        result.dissolved = true;
        Map<String, Integer> added = new TreeMap<>();
        v.forEach((id, a) -> added.put(id, a * k));
        post(new CruciblePayload.Dissolved(level, pos, thrower, dissolved, keys(added)));
        if (!left.isEmpty()) {
            refuse(level, pos, item, thrower, CrucibleRefusal.FULL, null, result);
        }
    }

    private static void refuse(
            ServerLevel level,
            BlockPos pos,
            ItemEntity item,
            @Nullable ServerPlayer thrower,
            CrucibleRefusal refusal,
            @Nullable Component argument,
            StepResult result) {
        hooks.markRefused(item);
        item.setDefaultPickUpDelay();
        double[] v = velocity(level, pos, thrower);
        item.setDeltaMovement(v[0], v[1], v[2]);
        result.refused = true;
        if (thrower != null) {
            result.feedback.putIfAbsent(
                    thrower,
                    argument == null
                            ? Component.translatable(refusal.key())
                            : Component.translatable(refusal.key(), argument));
        }
    }

    private static double[] velocity(ServerLevel level, BlockPos pos, @Nullable ServerPlayer to) {
        return CrucibleMath.ejection(
                pos.getX(),
                pos.getZ(),
                to == null || !to.level().equals(level)
                        ? null
                        : new double[] {to.getX(), to.getZ()});
    }

    /** The provisional craft (crucible spec, "Committing a craft"). */
    private static void craft(
            ServerLevel level,
            BlockPos pos,
            CrucibleBlockEntity be,
            ItemEntity catalyst,
            ServerPlayer thrower,
            RecipeHolder<CrucibleRecipe> holder,
            StepResult result) {
        BlockState state = level.getBlockState(pos);
        int revision = be.pool().revision();
        ItemStack copy = catalyst.getItem().copy();
        CrucibleRecipeSnapshot snapshot = CrucibleRecipes.server();
        CrucibleRecipe recipe = holder.value();
        Identifier id = holder.id().identifier();
        ItemStack made = recipe.result().create();
        ItemEntity out =
                new ItemEntity(
                        level,
                        pos.getX() + 0.5,
                        pos.getY() + SPAWN_Y,
                        pos.getZ() + 0.5,
                        made.copy());
        hooks.markRefused(out);
        out.setDefaultPickUpDelay();
        double[] v = velocity(level, pos, thrower);
        out.setDeltaMovement(v[0], v[1], v[2]);
        boolean added;
        try {
            added = level.addFreshEntity(out);
        } catch (RuntimeException e) {
            LOGGER.error("Crucible recipe {}: spawning the result threw", id, e);
            added = false;
        }
        if (!added) {
            if (out.isAlive()) {
                out.discard();
            }
            LOGGER.info("Crucible recipe {}: the result's spawn was cancelled", id);
            refuse(level, pos, catalyst, thrower, CrucibleRefusal.ERROR, null, result);
            return;
        }
        String failed;
        if (level.getBlockEntity(pos) != be || be.isRemoved() || be.unreadable()) {
            failed = "the crucible changed";
        } else if (be.operation || be.busy()) {
            failed = "the crucible is busy";
        } else if (!level.getBlockState(pos).equals(state)
                || !state.getValue(CrucibleBlock.BOILING)) {
            failed = "the crucible's state changed";
        } else if (be.pool().revision() != revision) {
            failed = "the pool changed";
        } else if (!catalyst.isAlive()
                || hooks.refused(catalyst)
                || !ItemStack.matches(catalyst.getItem(), copy)
                || !recipe.matches(catalyst.getItem())) {
            failed = "the catalyst changed";
        } else if (CrucibleRecipes.server() != snapshot) {
            failed = "the recipes changed";
        } else if (!java.util.Objects.equals(thrower, thrower(catalyst))
                || !ResearchService.isLearned(thrower, recipe.research())) {
            failed = "the thrower changed";
        } else if (!out.isAlive() || !ItemStack.matches(out.getItem(), made)) {
            failed = "the result changed";
        } else {
            failed = null;
        }
        if (failed != null) {
            if (out.isAlive()) {
                out.discard();
            }
            LOGGER.info("Crucible recipe {} not crafted: {}", id, failed);
            refuse(level, pos, catalyst, thrower, CrucibleRefusal.ERROR, null, result);
            return;
        }
        be.pool().debit(recipe.cost());
        ItemStack left = catalyst.getItem().copyWithCount(catalyst.getItem().getCount() - 1);
        if (left.isEmpty()) {
            catalyst.discard();
        } else {
            catalyst.setItem(left);
        }
        markDirty(be);
        result.crafted = true;
        ItemStack one = copy.copyWithCount(1);
        notify(
                id,
                () ->
                        post(
                                new CruciblePayload.Crafted(
                                        level,
                                        pos,
                                        thrower,
                                        holder.id(),
                                        one,
                                        made,
                                        keys(recipe.cost()))));
        notify(id, () -> out.getItem().onCraftedBy(thrower, made.getCount()));
        notify(id, () -> hooks.crafted(thrower, made.copy(), new SimpleContainer(one.copy())));
        notify(
                id,
                () ->
                        CriteriaTriggers.RECIPE_CRAFTED.trigger(
                                thrower, holder.id(), List.of(one.copy())));
    }

    private static void notify(Identifier recipe, Runnable notification) {
        try {
            notification.run();
        } catch (RuntimeException e) {
            LOGGER.error("A notification of crucible recipe {} threw; the craft stands", recipe, e);
        }
    }

    // --- Helpers -------------------------------------------------------------------------

    /** "Aspect n, …" in ascending id; an undiscovered aspect as "something unheard n". */
    static Component aspectList(@Nullable Player reader, SortedMap<String, Integer> amounts) {
        Knowledge known = reader == null ? null : KnowledgeService.get(reader);
        MutableComponent list = Component.empty();
        boolean first = true;
        for (Map.Entry<String, Integer> e : amounts.entrySet()) {
            if (!first) {
                list.append(", ");
            }
            first = false;
            ResourceKey<Aspect> key =
                    ResourceKey.create(AspectKeys.REGISTRY, Identifier.parse(e.getKey()));
            boolean heard = known != null && known.known().contains(e.getKey());
            list.append(heard ? AspectLang.name(key) : Component.translatable(CrucibleLang.UNHEARD))
                    .append(" " + e.getValue());
        }
        return list;
    }

    private static void post(CruciblePayload payload) {
        try {
            hooks.post(payload);
        } catch (RuntimeException e) {
            LOGGER.error("A CrucibleEvent listener threw; the state stands", e);
        }
    }

    private static java.util.SequencedMap<ResourceKey<Aspect>, Integer> keys(
            Map<String, Integer> amounts) {
        TreeMap<ResourceKey<Aspect>, Integer> out = new TreeMap<>();
        amounts.forEach(
                (id, a) ->
                        out.put(ResourceKey.create(AspectKeys.REGISTRY, Identifier.parse(id)), a));
        return out;
    }

    private static void play(ServerLevel level, BlockPos pos, SoundEvent sound) {
        level.playSound(null, pos, sound, SoundSource.BLOCKS, 1.0f, 1.0f);
    }

    private static void feedback(@Nullable ServerPlayer player, Component line) {
        if (player != null) {
            StatusLines.send(player, line);
        }
    }
}
