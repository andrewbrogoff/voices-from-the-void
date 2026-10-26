package com.andrewbrogoff.vftv.core.infusion;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.essentia.SpillCause;
import com.andrewbrogoff.vftv.api.flux.FluxRules;
import com.andrewbrogoff.vftv.api.infusion.AbandonCause;
import com.andrewbrogoff.vftv.api.infusion.InfusionPayload;
import com.andrewbrogoff.vftv.api.infusion.UpsetEffect;
import com.andrewbrogoff.vftv.core.flux.FluxRulesService;
import com.andrewbrogoff.vftv.core.flux.model.FluxMath;
import com.andrewbrogoff.vftv.core.infusion.AltarReading.ClaimState;
import com.andrewbrogoff.vftv.core.infusion.model.InfusionMath;
import com.andrewbrogoff.vftv.core.research.ResearchService;
import com.andrewbrogoff.vftv.core.text.model.SharedMessages;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.SequencedMap;
import java.util.TreeMap;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Recipe;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The run (infusion spec, "The run"): a step's draw, absorb or finish and its roll, upsets with
 * their provisional jolt, abandoning, and a removal's spill. Every step runs with the bowl's
 * operation open, closed in a {@code finally}; notifications follow the writes.
 */
final class InfusionRunner {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double JOLT_Y = 1.0625;

    private InfusionRunner() {}

    /**
     * One step of a ringing bowl at {@code now}: the next due tick, {@code -1} to keep its due (a
     * transaction is open), or {@link Long#MIN_VALUE} when the run ended.
     */
    static long step(ServerLevel level, BowlBlockEntity bowl, long now) {
        InfusionRun run = java.util.Objects.requireNonNull(bowl.run);
        BlockPos pos = bowl.getBlockPos();
        long next = InfusionMath.nextDue(now, pos.asLong(), run.rules.interval());
        if (bowl.unreadable() || bowl.operation) {
            return next; // a listener's re-entry: rescheduled
        }
        InfusionRun.Abandon abandon = run.abandon;
        if (abandon != null) {
            if (abandon.until() > now) {
                return next; // paused while armed
            }
            run.abandon = null; // the deadline passed: disarmed, the run resumes
            bowl.markDirty();
        }
        if (!AltarReading.boxLoaded(level, pos)) {
            bowl.stall = InfusionReadout.Stall.PAUSED;
            return next;
        }
        boolean owed = run.owedTotal() > 0;
        if (owed && !InfusionAltars.hooks().idle()) {
            return -1; // deferred to the next tick, its due kept
        }
        Runnable after = null;
        Identifier sound = null;
        BlockPos soundAt = pos;
        boolean changed = false;
        bowl.operation = true;
        try {
            boolean rolled;
            if (owed) {
                rolled = draw(level, bowl, run);
                sound = rolled ? InfusionContent.DRAW : null;
            } else if (run.firstPending() >= 0) {
                BlockPos at = absorb(level, bowl, run);
                rolled = at != null;
                sound = rolled ? InfusionContent.ABSORB : null;
                soundAt = at == null ? pos : at;
            } else {
                after = finish(level, bowl, run);
                rolled = false;
            }
            changed = rolled || after != null;
            if (rolled) {
                roll(level, bowl, run);
            }
        } catch (RuntimeException e) {
            LOGGER.error(
                    "An infusion step at {} threw; the run continues from what committed", pos, e);
        } finally {
            bowl.operation = false;
            InfusionAltars.settle(bowl);
            if (changed) {
                bowl.markDirty();
            }
            if (sound != null) {
                Identifier s = sound;
                BlockPos at = soundAt;
                InfusionAltars.notify(pos, () -> InfusionAltars.play(level, at, s, 1.0f));
            }
        }
        if (after != null) {
            after.run();
            return Long.MIN_VALUE;
        }
        return bowl.run == run ? next : Long.MIN_VALUE;
    }

    // --- Drawing --------------------------------------------------------------------------

    /** A draw step: whether the ledger shows a credit (a rolled draw). */
    private static boolean draw(ServerLevel level, BowlBlockEntity bowl, InfusionRun run) {
        int[] need = new int[run.costs.length];
        for (int i = 0; i < need.length; i++) {
            need[i] = Math.min(run.rules.rate(), run.costs[i] - run.drawn[i]);
        }
        int before = run.drawnTotal();
        InfusionHooks.Ledger ledger =
                new InfusionHooks.Ledger() {
                    @Override
                    public int[] snapshot() {
                        return run.drawn.clone();
                    }

                    @Override
                    public void restore(int[] snapshot) {
                        System.arraycopy(snapshot, 0, run.drawn, 0, snapshot.length);
                    }

                    @Override
                    public void credit(int index, int amount) {
                        run.drawn[index] += amount;
                    }
                };
        try {
            InfusionAltars.hooks().draw(level, bowl.getBlockPos(), run.aspects, need, ledger);
        } catch (RuntimeException e) {
            // The ledger is the record of truth: before the commit it shows no credit; after
            // the commit (a root-close callback threw) the credit stands and is never retried.
            LOGGER.error("An infusion draw at {} threw", bowl.getBlockPos(), e);
        }
        boolean drew = run.drawnTotal() > before;
        bowl.stall = drew ? InfusionReadout.Stall.NONE : InfusionReadout.Stall.ESSENTIA;
        return drew;
    }

    // --- Absorbing ------------------------------------------------------------------------

    /** An absorb step: the pedestal's position if it absorbed, or null for a stall. */
    private static @Nullable BlockPos absorb(
            ServerLevel level, BowlBlockEntity bowl, InfusionRun run) {
        int i = run.firstPending();
        InfusionRun.Part part = run.parts.get(i);
        BlockPos at = bowl.getBlockPos().offset(part.dx(), 0, part.dz());
        if (!(level.getBlockEntity(at) instanceof PedestalBlockEntity pedestal)
                || pedestal.unreadable()
                || pedestal.busy()
                || !ItemStack.isSameItemSameComponents(pedestal.item, part.promised())) {
            return stall(bowl, at);
        }
        ClaimState state = InfusionAltars.claimState(level, pedestal);
        if (state == ClaimState.NONE) {
            pedestal.claim =
                    new PedestalBlockEntity.Claim(bowl.getBlockPos().asLong(), bowl.id, run.seq);
        } else if (!ours(bowl, run, pedestal, state)) {
            return stall(bowl, at); // another bowl's, or unknown or unresolved: untouched
        }
        pedestal.item = ItemStack.EMPTY; // a direct write: no callbacks run
        pedestal.claim = null;
        run.done[i] = true;
        pedestal.markDirty();
        bowl.stall = InfusionReadout.Stall.NONE;
        level.sendParticles(
                new ItemParticleOption(
                        ParticleTypes.ITEM, ItemStackTemplate.fromNonEmptyStack(part.promised())),
                at.getX() + 0.5,
                at.getY() + 1.1,
                at.getZ() + 0.5,
                8,
                0.1,
                0.2,
                0.1,
                0.05);
        return at;
    }

    private static @Nullable BlockPos stall(BowlBlockEntity bowl, BlockPos at) {
        bowl.stall = InfusionReadout.Stall.COMPONENT;
        bowl.missing = at.immutable();
        return null;
    }

    /** Whether a pedestal holds this run's own live claim. */
    private static boolean ours(
            BowlBlockEntity bowl, InfusionRun run, PedestalBlockEntity pedestal, ClaimState state) {
        PedestalBlockEntity.Claim claim = pedestal.claim;
        return state == ClaimState.LIVE
                && claim != null
                && claim.bowl() == bowl.getBlockPos().asLong()
                && claim.id() == bowl.id
                && claim.seq() == run.seq;
    }

    // --- Upsets ---------------------------------------------------------------------------

    /** The roll after a rolled step; rolls stop at the upset cap. */
    private static void roll(ServerLevel level, BowlBlockEntity bowl, InfusionRun run) {
        if (run.upsets >= run.rules.max()) {
            return;
        }
        int k = run.rolls;
        run.rolls = k + 1;
        if (!InfusionMath.upsets(run.seed, k, run.stability.chance())) {
            return;
        }
        run.upsets++;
        boolean jolt =
                InfusionMath.jolts(run.seed, k, run.rules.joltWeight(), run.rules.spillWeight());
        UpsetEffect effect;
        if (jolt) {
            effect = jolt(level, bowl, run, k);
            if (effect == null) {
                effect = spill(level, bowl, run);
            }
        } else {
            effect = spill(level, bowl, run);
            if (effect == null) {
                effect = jolt(level, bowl, run, k);
            }
        }
        UpsetEffect posted = effect == null ? new UpsetEffect.None() : effect;
        BlockPos pos = bowl.getBlockPos();
        InfusionAltars.post(new InfusionPayload.Upset(level, pos, run.seq, posted), null);
        InfusionAltars.play(level, pos, InfusionContent.UPSET, 1.0f);
        level.sendParticles(
                ParticleTypes.SMOKE,
                pos.getX() + 0.5,
                pos.getY() + 1.0,
                pos.getZ() + 0.5,
                8,
                0.2,
                0.1,
                0.2,
                0.01);
    }

    /**
     * A jolt: null when no component is eligible (the caller falls back), {@code None} when the
     * provisional spawn fails its revalidation.
     */
    private static @Nullable UpsetEffect jolt(
            ServerLevel level, BowlBlockEntity bowl, InfusionRun run, int k) {
        List<Integer> eligible = new ArrayList<>();
        for (int i = 0; i < run.parts.size(); i++) {
            if (!run.done[i] && present(level, bowl, run, i) != null) {
                eligible.add(i);
            }
        }
        if (eligible.isEmpty()) {
            return null;
        }
        int i = eligible.get(InfusionMath.target(run.seed, k, eligible.size()));
        PedestalBlockEntity pedestal =
                java.util.Objects.requireNonNull(present(level, bowl, run, i));
        BlockPos at = pedestal.getBlockPos();
        ItemStack copy = run.parts.get(i).promised().copy();
        ItemEntity entity =
                new ItemEntity(
                        level, at.getX() + 0.5, at.getY() + JOLT_Y, at.getZ() + 0.5, copy.copy());
        entity.setDeltaMovement(0, 0.25, 0);
        entity.setDefaultPickUpDelay();
        boolean added;
        try {
            added = level.addFreshEntity(entity);
        } catch (RuntimeException e) {
            LOGGER.error("An infusion jolt's spawn at {} threw", at, e);
            added = false;
        }
        if (added
                && entity.isAlive()
                && ItemStack.matches(entity.getItem(), copy)
                && present(level, bowl, run, i) == pedestal
                && bowl.run == run) {
            pedestal.item = ItemStack.EMPTY;
            pedestal.markDirty();
            return new UpsetEffect.Jolt(at, copy);
        }
        if (entity.isAlive()) {
            entity.discard();
        }
        return new UpsetEffect.None();
    }

    /**
     * Component {@code i}'s pedestal if it is eligible to jolt: readable, idle, ours, holding it.
     */
    private static @Nullable PedestalBlockEntity present(
            ServerLevel level, BowlBlockEntity bowl, InfusionRun run, int i) {
        InfusionRun.Part part = run.parts.get(i);
        BlockPos at = bowl.getBlockPos().offset(part.dx(), 0, part.dz());
        if (level.getBlockEntity(at) instanceof PedestalBlockEntity p
                && !p.isRemoved()
                && !p.unreadable()
                && !p.busy()
                && ItemStack.isSameItemSameComponents(p.item, part.promised())
                && ours(bowl, run, p, InfusionAltars.claimState(level, p))) {
            return p;
        }
        return null;
    }

    /** A spill: null when nothing is drawn (the caller falls back). */
    private static @Nullable UpsetEffect spill(
            ServerLevel level, BowlBlockEntity bowl, InfusionRun run) {
        int x = -1;
        for (int i = 0; i < run.drawn.length; i++) {
            if (run.drawn[i] > 0 && (x < 0 || run.drawn[i] > run.drawn[x])) {
                x = i; // the largest, ties to the lowest id
            }
        }
        if (x < 0) {
            return null;
        }
        int amount = Math.min(run.drawn[x], run.rules.spill());
        run.drawn[x] -= amount; // owed again first
        ResourceKey<Aspect> aspect = run.aspects.get(x);
        SequencedMap<ResourceKey<Aspect>, Integer> spilled =
                new TreeMap<>(java.util.Comparator.comparing(k -> k.identifier().toString()));
        spilled.put(aspect, amount);
        InfusionAltars.post(
                new InfusionPayload.Spilled(level, bowl.getBlockPos(), spilled, SpillCause.UPSET),
                run.fluxRules);
        return new UpsetEffect.Spill(aspect, amount);
    }

    // --- Finishing ------------------------------------------------------------------------

    /** The finish's writes, with the operation open; returns what follows its close. */
    private static Runnable finish(ServerLevel level, BowlBlockEntity bowl, InfusionRun run) {
        BlockPos pos = bowl.getBlockPos();
        ItemStack centre = bowl.item.copyWithCount(1);
        bowl.item = run.output.copy();
        bowl.run = null;
        InfusionAltars.unschedule(level, pos);
        ItemStack output = run.output.copy();
        ResourceKey<Recipe<?>> recipe = ResourceKey.create(Registries.RECIPE, run.recipe);
        int seq = run.seq;
        return () -> {
            InfusionAltars.notify(
                    pos, () -> InfusionAltars.play(level, pos, InfusionContent.FINISH, 1.0f));
            level.sendParticles(
                    ParticleTypes.ENCHANT,
                    pos.getX() + 0.5,
                    pos.getY() + 1.0,
                    pos.getZ() + 0.5,
                    12,
                    0.4,
                    0.4,
                    0.4,
                    0.5);
            InfusionAltars.endSubscriptions(level, pos);
            InfusionAltars.notify(
                    pos,
                    () ->
                            InfusionAltars.post(
                                    new InfusionPayload.Completed(
                                            level, pos, seq, run.starter, recipe, output),
                                    null));
            ServerPlayer starter = level.getServer().getPlayerList().getPlayer(run.starter);
            if (starter != null && starter.isAlive() && starter.level().equals(level)) {
                InfusionAltars.notify(pos, () -> output.copy().onCraftedBy(starter, 1));
                InfusionAltars.notify(
                        pos,
                        () ->
                                InfusionAltars.hooks()
                                        .crafted(
                                                starter,
                                                output.copy(),
                                                new SimpleContainer(centre.copy())));
                InfusionAltars.notify(
                        pos,
                        () ->
                                CriteriaTriggers.RECIPE_CRAFTED.trigger(
                                        starter, recipe, List.of(centre.copy())));
            }
        };
    }

    // --- Abandoning and removal ------------------------------------------------------------

    /**
     * An empty-handed use on a ringing bowl (infusion spec, "Abandoning"): arm, confirm, or refuse.
     * Returns whether it acted (not refused).
     */
    static boolean abandonUse(ServerLevel level, BowlBlockEntity bowl, ServerPlayer player) {
        InfusionRun run = java.util.Objects.requireNonNull(bowl.run);
        if (bowl.operation) {
            InfusionAltars.say(player, Component.translatable(SharedMessages.BUSY));
            return false;
        }
        ServerPlayer starter = level.getServer().getPlayerList().getPlayer(run.starter);
        if (!ResearchService.isLearned(player, run.research)
                || (starter != null
                        && starter.level().equals(level)
                        && !starter.getUUID().equals(player.getUUID()))) {
            InfusionAltars.say(player, Component.translatable(InfusionLang.MESSAGE + "not_yours"));
            return false;
        }
        long now = level.getGameTime();
        InfusionRun.Abandon armed = run.abandon;
        boolean live = armed != null && armed.until() > now;
        if (armed != null && live && !armed.player().equals(player.getUUID())) {
            InfusionAltars.say(
                    player, Component.translatable(InfusionLang.MESSAGE + "abandon_pending"));
            return false;
        }
        FluxRules rules = FluxRulesService.current();
        if (armed != null && live && armed.fluxGeneration() == rules.generation()) {
            abandon(level, bowl, run, player, rules);
            return true;
        }
        run.abandon =
                new InfusionRun.Abandon(
                        player.getUUID(),
                        now + InfusionAltars.tuning().abandonWindow(),
                        rules.generation());
        bowl.markDirty();
        InfusionAltars.say(
                player,
                Component.translatable(
                        InfusionLang.MESSAGE + "abandon_confirm",
                        Integer.toString(
                                FluxMath.essentiaFlux(
                                        run.drawn.clone(), SpillCause.ABANDONED, rules))));
        InfusionAltars.refreshSubscription(level, bowl, player, true);
        return true;
    }

    private static void abandon(
            ServerLevel level,
            BowlBlockEntity bowl,
            InfusionRun run,
            ServerPlayer player,
            FluxRules rules) {
        BlockPos pos = bowl.getBlockPos();
        SequencedMap<ResourceKey<Aspect>, Integer> drawn;
        bowl.operation = true;
        try {
            drawn = drawnMap(run);
            bowl.run = null; // claims die with it; the centre stays
            InfusionAltars.unschedule(level, pos);
        } finally {
            bowl.operation = false;
            InfusionAltars.settle(bowl);
        }
        bowl.markDirty();
        InfusionAltars.endSubscriptions(level, pos);
        InfusionAltars.notify(
                pos,
                () ->
                        InfusionAltars.post(
                                new InfusionPayload.Spilled(
                                        level, pos, drawn, SpillCause.ABANDONED),
                                rules));
        InfusionAltars.notify(
                pos,
                () ->
                        InfusionAltars.post(
                                new InfusionPayload.Abandoned(
                                        level, pos, run.seq, AbandonCause.PLAYER, player, drawn),
                                null));
        InfusionAltars.notify(
                pos, () -> InfusionAltars.play(level, pos, InfusionContent.ABANDON, 1.0f));
    }

    /** A removal with a run: spill, then the caller's default drop. */
    static void removeRun(ServerLevel level, BowlBlockEntity bowl) {
        InfusionRun run = java.util.Objects.requireNonNull(bowl.run);
        BlockPos pos = bowl.getBlockPos();
        SequencedMap<ResourceKey<Aspect>, Integer> drawn = drawnMap(run);
        bowl.run = null;
        InfusionAltars.unschedule(level, pos);
        InfusionAltars.endSubscriptions(level, pos);
        FluxRules rules = FluxRulesService.current();
        InfusionAltars.notify(
                pos,
                () ->
                        InfusionAltars.post(
                                new InfusionPayload.Spilled(level, pos, drawn, SpillCause.REMOVED),
                                rules));
        InfusionAltars.notify(
                pos,
                () ->
                        InfusionAltars.post(
                                new InfusionPayload.Abandoned(
                                        level, pos, run.seq, AbandonCause.REMOVED, null, drawn),
                                null));
        InfusionAltars.notify(
                pos, () -> InfusionAltars.play(level, pos, InfusionContent.ABANDON, 1.0f));
    }

    private static SequencedMap<ResourceKey<Aspect>, Integer> drawnMap(InfusionRun run) {
        SequencedMap<ResourceKey<Aspect>, Integer> out =
                new TreeMap<>(java.util.Comparator.comparing(k -> k.identifier().toString()));
        for (int i = 0; i < run.drawn.length; i++) {
            if (run.drawn[i] > 0) {
                out.put(run.aspects.get(i), run.drawn[i]);
            }
        }
        return out;
    }
}
