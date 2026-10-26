package com.andrewbrogoff.vftv.core.warp;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * {@code vftv:apparition} (warp spec, "Phantoms"): a glimpse of a Choir figure that exists only on
 * its Listener's client, under a negative id. No health, no AI, never pushed or collided with, but
 * pickable, so a strike at it can be caught on the client and sends nothing. The server never makes
 * one; one that exists server-side is discarded on its first tick.
 */
public final class Apparition extends Entity {
    /** An apparition of {@code type} in {@code level}. */
    public Apparition(EntityType<? extends Apparition> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    @Override
    public void tick() {
        if (!level().isClientSide()) {
            discard();
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {}

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith(@Nullable Entity other) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {}

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {}
}
