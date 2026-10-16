package com.andrewbrogoff.vftv.api.road;

/**
 * What a {@link RoadEncounterPayload} reports about a Step's encounter. See the Eldritch tier spec,
 * "Encounters".
 *
 * <p>Grows in minor releases: a switch over it needs a default arm.
 */
public enum EncounterOutcome {
    /** A Chorister rose and the encounter began. */
    STARTED,
    /** The Chorister's death was confirmed and its credit paid. */
    DEFEATED,
    /** The encounter ended without a defeat; the Step stays unpassed. */
    RESET
}
