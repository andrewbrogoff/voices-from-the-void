package com.andrewbrogoff.vftv.api.road;

/**
 * The final choice a Listener makes at the Heart. See the Eldritch tier spec, "The Heart".
 *
 * <p>Grows in minor releases: a switch over it needs a default arm.
 */
public enum Ending {
    /** Joining: permanent warp and a lasting Void power; the Voices go on. */
    JOINED,
    /** Silencing: temporary and lasting warp stilled; the Voices end. */
    SILENCED
}
