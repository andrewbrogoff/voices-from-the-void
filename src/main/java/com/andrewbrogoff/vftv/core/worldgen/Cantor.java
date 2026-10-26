package com.andrewbrogoff.vftv.core.worldgen;

import com.andrewbrogoff.vftv.api.worldgen.WorldgenLimits;
import com.andrewbrogoff.vftv.core.worldgen.model.WorldgenTuning;
import java.util.EnumSet;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * {@code vftv:answerer_cantor} (worldgen spec, "The chant"): an Answerer that, while it has a valid
 * target, chants every {@code cantor.interval} ticks, giving Resistance to the nearest Answerers,
 * itself included. Its schedule is transient: created or loaded, it waits a full interval.
 */
public final class Cantor extends Answerer {
    private static final EntityDataAccessor<Boolean> CHANTING =
            SynchedEntityData.defineId(Cantor.class, EntityDataSerializers.BOOLEAN);
    private static final long UNSET = Long.MIN_VALUE;

    private long nextChant = UNSET;
    private long showUntil;

    /** A Cantor. */
    public Cantor(EntityType<? extends Answerer> type, Level level) {
        super(type, level);
        this.xpReward = 10;
    }

    /** The Cantor's stats (worldgen spec, "The creatures"). */
    public static AttributeSupplier.Builder createCantorAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.ATTACK_DAMAGE, 2.0)
                .add(Attributes.ARMOR, 0.0)
                .add(Attributes.MOVEMENT_SPEED, 0.23)
                .add(Attributes.FOLLOW_RANGE, FOLLOW_RANGE);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CHANTING, false);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(3, new ChantGoal(this));
    }

    @Override
    public boolean isCantor() {
        return true;
    }

    @Override
    public boolean chanting() {
        return entityData.get(CHANTING);
    }

    /** The game time of the next chant, or {@link Long#MIN_VALUE} before the first tick. */
    public long nextChant() {
        return nextChant;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        long now = level.getGameTime();
        if (nextChant == UNSET) {
            nextChant = now + WorldgenService.tuning().chantInterval();
        }
        if (chanting() && now >= showUntil) {
            entityData.set(CHANTING, false);
        }
        super.customServerAiStep(level);
    }

    /** One chant, if due: Resistance to the nearest Answerers, the sound, the next schedule. */
    boolean chantIfDue(ServerLevel level) {
        long now = level.getGameTime();
        if (nextChant == UNSET || now < nextChant || !(getTarget() instanceof Player p)) {
            return false;
        }
        if (!validTarget(this, p)) {
            return false;
        }
        WorldgenTuning t = WorldgenService.tuning();
        for (Mob m : nearest(level, this, t.chantRadius(), t.chantMaxTargets(), false)) {
            m.addEffect(
                    new MobEffectInstance(
                            MobEffects.RESISTANCE, t.chantDuration(), t.chantAmplifier()));
        }
        level.playSound(
                null,
                getX(),
                getY(),
                getZ(),
                WorldgenContent.sound(WorldgenContent.CANTOR_CHANT),
                SoundSource.HOSTILE,
                1.5F,
                1.0F);
        nextChant = now + WorldgenService.tuning().chantInterval();
        showUntil = now + WorldgenLimits.CHANT_SHOW_TICKS;
        entityData.set(CHANTING, true);
        return true;
    }

    /**
     * The chant: no flags, so it runs concurrently with melee and movement; it runs while the
     * Cantor has a target and checks the schedule every tick.
     */
    static final class ChantGoal extends Goal {
        private final Cantor mob;

        ChantGoal(Cantor mob) {
            this.mob = mob;
            setFlags(EnumSet.noneOf(Goal.Flag.class));
        }

        @Override
        public boolean canUse() {
            return mob.getTarget() instanceof Player;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (mob.level() instanceof ServerLevel level) {
                mob.chantIfDue(level);
            }
        }
    }
}
