package com.andrewbrogoff.vftv.core.vis.client;

import java.util.Optional;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.ParticleLimit;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

/**
 * {@code vftv:node_glint}: a four-frame cutout spark, full-bright. Every glint, ambient and tapping
 * alike, shares one vanilla particle limit of 64, so the engine drops any new one while 64 are
 * alive (wands spec, "Global glint cap"), and its own accounting covers rejection and clearing.
 */
public final class NodeGlintParticle extends SingleQuadParticle {
    /** The most glints alive at once (a fixed limit). */
    public static final ParticleLimit CAP = new ParticleLimit(64);

    private final SpriteSet sprites;

    private NodeGlintParticle(
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
        this.friction = 0.9f;
        this.gravity = 0;
        this.hasPhysics = false;
        this.quadSize = 0.06f;
        this.lifetime = NodeAmbience.numbers().glintLifetime();
        setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        setSpriteFromAge(sprites);
    }

    @Override
    public Optional<ParticleLimit> getParticleLimit() {
        return Optional.of(CAP);
    }

    @Override
    protected SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.OPAQUE;
    }

    @Override
    public int getLightCoords(float partialTick) {
        return 15728880;
    }

    /** The provider over the glint's frames. */
    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        /** A provider over the glint's frames. */
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
            return new NodeGlintParticle(level, x, y, z, dx, dy, dz, sprites);
        }
    }
}
