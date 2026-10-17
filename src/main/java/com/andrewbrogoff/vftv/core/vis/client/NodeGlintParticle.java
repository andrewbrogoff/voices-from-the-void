package com.andrewbrogoff.vftv.core.vis.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * {@code vftv:node_glint}: a four-frame cutout spark, full-bright. The client counts every live
 * glint, ambient and tapping alike, and drops any new one while 64 are alive (wands spec, "Global
 * glint cap").
 */
public final class NodeGlintParticle extends SingleQuadParticle {
    /** The most glints alive at once (a fixed limit). */
    public static final int CAP = 64;

    private static int alive;

    private final SpriteSet sprites;
    private boolean counted = true;

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
        alive++;
    }

    /** Forgets the count, when the level's particles are cleared (a disconnect or level change). */
    public static void reset() {
        alive = 0;
    }

    /** How many glints are alive. */
    public static int alive() {
        return alive;
    }

    @Override
    public void tick() {
        super.tick();
        setSpriteFromAge(sprites);
    }

    @Override
    public void remove() {
        super.remove();
        if (counted) {
            counted = false;
            alive--;
        }
    }

    @Override
    protected SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.OPAQUE;
    }

    @Override
    public int getLightCoords(float partialTick) {
        return 15728880;
    }

    /** The provider: refuses a new glint while the cap is reached. */
    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        /** A provider over the glint's frames. */
        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public @Nullable Particle createParticle(
                SimpleParticleType options,
                ClientLevel level,
                double x,
                double y,
                double z,
                double dx,
                double dy,
                double dz,
                RandomSource random) {
            return alive >= CAP ? null : new NodeGlintParticle(level, x, y, z, dx, dy, dz, sprites);
        }
    }
}
