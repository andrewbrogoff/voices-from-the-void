package com.andrewbrogoff.vftv.core.hush.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

/** {@code vftv:hush_mote}: a four-frame grey mote that drifts up and fades out of sight. */
public final class HushMoteParticle extends SingleQuadParticle {
    private final SpriteSet sprites;

    private HushMoteParticle(
            ClientLevel level,
            double x,
            double y,
            double z,
            double dx,
            double dy,
            double dz,
            SpriteSet sprites) {
        super(level, x, y, z, sprites.first());
        this.sprites = sprites;
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;
        this.friction = 0.96f;
        this.gravity = -0.002f;
        this.hasPhysics = false;
        this.quadSize = 0.05f;
        this.lifetime = 40;
        setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        setSpriteFromAge(sprites);
    }

    @Override
    protected SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.OPAQUE;
    }

    /** The provider over the mote's frames. */
    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        /** A provider over the mote's frames. */
        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(
                SimpleParticleType options,
                ClientLevel level,
                double x,
                double y,
                double z,
                double dx,
                double dy,
                double dz,
                RandomSource random) {
            return new HushMoteParticle(level, x, y, z, dx, dy, dz, sprites);
        }
    }
}
