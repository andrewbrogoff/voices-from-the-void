package com.andrewbrogoff.vftv.core.hush;

import com.andrewbrogoff.vftv.core.common.TickBudget;
import com.andrewbrogoff.vftv.core.warp.WarpService;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * {@code vftv:hollow} (Hush spec, "Hollows"): the one creature the Hush raises. It rises, never
 * spawns, is persistent and homes on its seep; it hunts only players inside its home who aren't
 * Silenced, and returns home when it strays. Silent except when hurt, dying and rising.
 */
public class Hollow extends Monster {
    /** Health, fixed at registration. */
    public static final double HEALTH = 30;

    /** Attack damage. */
    public static final double ATTACK = 5;

    /** Movement speed. */
    public static final double SPEED = 0.26;

    /** Follow range. */
    public static final double FOLLOW = 16;

    /** Experience on death. */
    public static final int XP = 5;

    /** The most Hush Ash it drops without Looting. */
    public static final int DROPS_MAX = 2;

    /** The rise's scale-in length, in ticks. */
    public static final int RISE_TICKS = 20;

    private static final EntityDataAccessor<Integer> RISING =
            SynchedEntityData.defineId(Hollow.class, EntityDataSerializers.INT);

    /** A Hollow. */
    public Hollow(EntityType<? extends Hollow> type, Level level) {
        super(type, level);
        this.xpReward = XP;
    }

    /** The Hollow's stats. */
    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HEALTH)
                .add(Attributes.ATTACK_DAMAGE, ATTACK)
                .add(Attributes.MOVEMENT_SPEED, SPEED)
                .add(Attributes.FOLLOW_RANGE, FOLLOW)
                .add(Attributes.ARMOR, 0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(RISING, 0);
    }

    /** Starts the rise's scale-in. */
    void startRise() {
        entityData.set(RISING, RISE_TICKS);
    }

    /** The rise's remaining ticks, 0 once risen. */
    public int rising() {
        return entityData.get(RISING);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new ReturnHomeGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false));
        goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0F));
        targetSelector.addGoal(1, new TargetGoal(this));
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        if (target == null || target instanceof Player) {
            super.setTarget(target); // Hollows hunt players only
        }
    }

    /** The whole server-side entity tick is timed as hush (architecture, "Tick timing"). */
    @Override
    public void tick() {
        if (!(level() instanceof ServerLevel)) {
            super.tick();
            return;
        }
        try (var _ = TickBudget.enter("hush")) {
            int rising = rising();
            if (rising > 0) {
                entityData.set(RISING, rising - 1);
            }
            super.tick();
        }
    }

    /** Retention, every tick before the goals run: an invalid target is dropped at once. */
    @Override
    public void aiStep() {
        if (level() instanceof ServerLevel
                && getTarget() instanceof Player p
                && !validTarget(this, p)) {
            setTarget(null);
        }
        super.aiStep();
    }

    /**
     * The one targeting rule: not peaceful, a living player in this level who isn't creative, a
     * spectator or Silenced, within follow range and standing inside the Hollow's home.
     */
    public static boolean validTarget(Hollow mob, Player player) {
        return mob.level().getDifficulty() != Difficulty.PEACEFUL
                && player.isAlive()
                && player.level().dimension().equals(mob.level().dimension())
                && !player.isCreative()
                && !player.isSpectator()
                && !WarpService.isSilenced(player)
                && mob.isWithinHome(player.blockPosition())
                && mob.distanceToSqr(player) <= FOLLOW * FOLLOW;
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return HushContent.sound(HushContent.HOLLOW_HURT);
    }

    @Override
    protected SoundEvent getDeathSound() {
        return HushContent.sound(HushContent.HOLLOW_DEATH);
    }

    @Override
    protected float getSoundVolume() {
        return 0.6F;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {}

    /** Return home while outside it, dropping any target. */
    static final class ReturnHomeGoal extends Goal {
        private final Hollow mob;

        ReturnHomeGoal(Hollow mob) {
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
            mob.getNavigation().moveTo(home.x, home.y, home.z, 1.0);
        }
    }

    /** Acquisition: the nearest player for whom {@link #validTarget} holds. */
    static final class TargetGoal extends Goal {
        private final Hollow mob;

        TargetGoal(Hollow mob) {
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
