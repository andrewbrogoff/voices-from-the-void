package com.andrewbrogoff.vftv.api.infusion;

/**
 * Why an infusion run was abandoned.
 *
 * <p>Grows in minor releases: a switch over it needs a default arm.
 */
public enum AbandonCause {
    /** A player confirmed the abandon with a second empty-handed use. */
    PLAYER,
    /** The bowl was removed while ringing. */
    REMOVED
}
