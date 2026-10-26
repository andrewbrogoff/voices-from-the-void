package com.andrewbrogoff.vftv.core.worldgen;

import com.andrewbrogoff.vftv.api.worldgen.WorldgenKeys;
import com.andrewbrogoff.vftv.api.worldgen.WorldgenLimits;
import com.andrewbrogoff.vftv.core.common.TickBudget;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * {@code vftv:answerer} (worldgen spec, "The Answerers"): a living would-be listener, found only in
 * a ruin's templates and never despawning. It targets players only, through one predicate, {@link
 * #validTarget}, checked at acquisition, every tick while held, and on retaliation and alert.
 */
public class Answerer extends Monster {
    /** The follow range, and the alert's radius: 20 blocks. */
    public static final double FOLLOW_RANGE = 20.0;

    /** The ambient sound's interval: 240 ticks, fixed. */
    public static final int AMBIENT_INTERVAL = 240;

    /** An Answerer. */
    public Answerer(EntityType<? extends Answerer> type, Level level) {
        super(type, level);
        this.xpReward = 8;
    }

    /** The Answerer's stats (worldgen spec, "The creatures"). */
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 24.0)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.ARMOR, 2.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, FOLLOW_RANGE);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new ReturnHomeGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false));
        goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8.0F));
        targetSelector.addGoal(1, new TargetPlayerGoal(this));
    }

    @Override
    @SuppressWarnings("deprecation") // override-only: vanilla and NeoForge call it, never us
    public @Nullable SpawnGroupData finalizeSpawn(
            ServerLevelAccessor level,
            DifficultyInstance difficulty,
            EntitySpawnReason reason,
            @Nullable SpawnGroupData data) {
        setHomeTo(blockPosition(), WorldgenService.tuning().homeRadius());
        return super.finalizeSpawn(level, difficulty, reason, data);
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        if (target == null || target instanceof Player) {
            super.setTarget(target); // Answerers only ever target players
        }
    }

    @Override
    protected boolean canRide(Entity vehicle) {
        return false;
    }

    /** The whole server-side entity tick is timed as worldgen (architecture, "Tick timing"). */
    @Override
    public void tick() {
        if (!(level() instanceof ServerLevel)) {
            super.tick();
            return;
        }
        try (var _ = TickBudget.enter("worldgen")) {
            super.tick();
        }
    }

    /** Retention, before the goals run each tick: an invalid target is dropped at once. */
    @Override
    public void aiStep() {
        if (level() instanceof ServerLevel
                && getTarget() instanceof Player p
                && !validTarget(this, p)) {
            setTarget(null);
        }
        super.aiStep();
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        boolean hurt = super.hurtServer(level, source, damage);
        if (hurt && source.getEntity() instanceof ServerPlayer player && isAlive()) {
            try (var _ = TickBudget.enter("worldgen")) {
                retaliate(level, player);
            }
        }
        return hurt;
    }

    /**
     * Retaliation and alert: target the player iff valid, then alert the nearest untargeted
     * Answerers within the follow range, each targeting the player iff valid for it.
     */
    void retaliate(ServerLevel level, ServerPlayer player) {
        if (!validTarget(this, player)) {
            return;
        }
        setTarget(player);
        for (Mob m : nearest(level, this, FOLLOW_RANGE, WorldgenLimits.MAX_ALERT, true)) {
            if (validTarget(m, player)) {
                m.setTarget(player);
            }
        }
    }

    /**
     * The {@code #vftv:answerers} mobs within {@code radius} of {@code centre}, nearest first with
     * ties by entity id, at most {@code max}; only untargeted ones when {@code untargeted}.
     */
    static List<Mob> nearest(
            ServerLevel level, Mob centre, double radius, int max, boolean untargeted) {
        AABB box = centre.getBoundingBox().inflate(radius);
        return level
                .getEntitiesOfClass(
                        Mob.class,
                        box,
                        m ->
                                m.isAlive()
                                        && m.typeHolder().is(WorldgenKeys.ANSWERERS)
                                        && (!untargeted || m.getTarget() == null))
                .stream()
                .sorted(
                        Comparator.<Mob>comparingDouble(m -> m.distanceToSqr(centre))
                                .thenComparingInt(Entity::getId))
                .limit(max)
                .toList();
    }

    /**
     * The one targeting predicate (worldgen spec, "Behaviour"): not peaceful, and the player is
     * attackable, inside that mob's home and within that mob's follow range.
     */
    public static boolean validTarget(Mob mob, Player player) {
        return mob.level().getDifficulty() != Difficulty.PEACEFUL
                && player.isAlive()
                && player.level().dimension().equals(mob.level().dimension())
                && !player.isCreative()
                && !player.isSpectator()
                && mob.isWithinHome(player.blockPosition())
                && mob.distanceToSqr(player) <= followRange(mob) * followRange(mob);
    }

    private static double followRange(Mob mob) {
        return mob.getAttributeValue(Attributes.FOLLOW_RANGE);
    }

    @Override
    public int getAmbientSoundInterval() {
        return AMBIENT_INTERVAL;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return WorldgenContent.sound(WorldgenContent.ANSWERER_AMBIENT);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return WorldgenContent.sound(WorldgenContent.ANSWERER_HURT);
    }

    @Override
    protected SoundEvent getDeathSound() {
        return WorldgenContent.sound(WorldgenContent.ANSWERER_DEATH);
    }

    /** Whether this is a Cantor. */
    public boolean isCantor() {
        return false;
    }

    /** Whether the Cantor is showing its chant; always false for an Answerer. */
    public boolean chanting() {
        return false;
    }

    /** Return home while outside it, dropping any target; far homes are walked to in legs. */
    static final class ReturnHomeGoal extends Goal {
        private static final double LEG = 12.0;
        private final Answerer mob;

        ReturnHomeGoal(Answerer mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return mob.hasHome() && !mob.isWithinHome();
        }

        @Override
        public void start() {
            mob.setTarget(null);
            walk();
        }

        @Override
        public void tick() {
            if (mob.getTarget() != null) {
                mob.setTarget(null);
            }
            if (mob.getNavigation().isDone()) {
                walk();
            }
        }

        private void walk() {
            Vec3 home = Vec3.atBottomCenterOf(mob.getHomePosition());
            Vec3 to = home.subtract(mob.position());
            Vec3 at = to.length() > LEG ? mob.position().add(to.normalize().scale(LEG)) : home;
            mob.getNavigation().moveTo(at.x, at.y, at.z, 1.0);
        }
    }

    /** Acquisition: the nearest player for whom {@link #validTarget} holds. */
    static final class TargetPlayerGoal extends Goal {
        private final Answerer mob;

        TargetPlayerGoal(Answerer mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Goal.Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (mob.getTarget() != null) {
                return false;
            }
            Player best = null;
            double bestDistance = Double.MAX_VALUE;
            for (Player p : mob.level().players()) {
                double d = mob.distanceToSqr(p);
                if (d < bestDistance && validTarget(mob, p)) {
                    best = p;
                    bestDistance = d;
                }
            }
            if (best != null) {
                mob.setTarget(best);
            }
            return false; // the target is set; retention is checked every tick
        }
    }
}
