package com.andrewbrogoff.vftv.core.warp.model;

/**
 * The colour drain's intensity over time (warp spec, "Perception effects"): it ramps linearly to
 * the band's peak over 50 ticks, holds 200 and ramps out over 50, so it never changes by more than
 * 1.3% a tick. A new cue ramps from the current intensity to its own peak. With {@code
 * voices.colour_shift} off it falls by 0.013 a tick until it is gone.
 */
public final class ColourRamp {
    /** Ticks to ramp in, or to a new cue's peak. */
    public static final int IN = 50;

    /** Ticks held at the peak. */
    public static final int HOLD = 200;

    /** Ticks to ramp out. */
    public static final int OUT = 50;

    /** The fall per tick with the option off. */
    public static final float OFF_STEP = 0.013F;

    private static final float[] PEAKS = {0F, 0.25F, 0.45F, 0.65F};

    private float intensity;
    private float from;
    private float peak;
    private int age = -1;
    private boolean fading;

    /** The peak at a band: nothing at band 0, then 25%, 45% and 65%. */
    public static float peak(int band) {
        return PEAKS[Math.clamp(band, 0, 3)];
    }

    /** Starts a drain toward the band's peak, from the current intensity. */
    public void start(int band) {
        from = intensity;
        peak = peak(band);
        age = 0;
        fading = false;
    }

    /**
     * One tick. Once {@code enabled} goes false the drain only falls, until it is gone, even if the
     * option comes back on meanwhile (on affects the next cue).
     */
    public void tick(boolean enabled) {
        if (!enabled && intensity > 0) {
            fading = true;
        }
        if (fading || !enabled) {
            intensity = Math.max(0F, intensity - OFF_STEP);
            if (intensity == 0F) {
                fading = false;
                age = -1;
            }
            return;
        }
        if (age < 0) {
            return;
        }
        age++;
        if (age <= IN) {
            intensity = from + (peak - from) * age / IN;
        } else if (age <= IN + HOLD) {
            intensity = peak;
        } else if (age <= IN + HOLD + OUT) {
            intensity = peak * (IN + HOLD + OUT - age) / OUT;
        } else {
            intensity = 0F;
            age = -1;
        }
    }

    /** The intensity, 0–0.65. */
    public float intensity() {
        return intensity;
    }

    /** Ends at once (a level change, {@code VoiceClear}). */
    public void clear() {
        intensity = 0F;
        age = -1;
        fading = false;
    }
}
