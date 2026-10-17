package com.andrewbrogoff.vftv.core.aspect.model;

import org.jspecify.annotations.Nullable;

/**
 * The sounding maths (aspects spec, "Sounding for nodes"): the distance band and the bearing that
 * reach the client, never a node's position. North is {@code -z} and east {@code +x}, as in the
 * world.
 */
public final class Sounding {
    /** Above this elevation, in degrees, the answer is "above" or "below" rather than level. */
    static final double LEVEL_DEGREES = 22.5;

    private Sounding() {}

    /** A distance band. */
    public enum Band {
        /** {@code d < near}. */
        NEAR,
        /** {@code near ≤ d < mid}. */
        MID,
        /** {@code mid ≤ d ≤ radius}. */
        FAR
    }

    /** Whether the answer lies above, below or level with the eye. */
    public enum Vertical {
        /** Above the eye. */
        UP,
        /** Below the eye. */
        DOWN,
        /** Within {@link #LEVEL_DEGREES} of level. */
        LEVEL
    }

    /**
     * The band of a distance.
     *
     * @return the band, or null beyond {@code radius} (which is inclusive)
     */
    public static @Nullable Band band(double distance, int near, int mid, int radius) {
        if (distance > radius) {
            return null;
        }
        return distance < near ? Band.NEAR : distance < mid ? Band.MID : Band.FAR;
    }

    /**
     * The compass direction of a horizontal offset: 0 north, then clockwise to 7 north-west. A
     * point straight above or below reads as north.
     */
    public static int direction(double dx, double dz) {
        double degrees = Math.toDegrees(Math.atan2(dx, 0.0 - dz)); // 0.0 - dz: never -0.0
        return Math.floorMod((int) Math.round(degrees / 45.0), 8);
    }

    /** Whether an offset lies above, below or level with the eye. */
    public static Vertical vertical(double dx, double dy, double dz) {
        double elevation = Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        return elevation > LEVEL_DEGREES
                ? Vertical.UP
                : elevation < -LEVEL_DEGREES ? Vertical.DOWN : Vertical.LEVEL;
    }
}
