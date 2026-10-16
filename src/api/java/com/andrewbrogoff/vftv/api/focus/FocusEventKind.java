package com.andrewbrogoff.vftv.api.focus;

/**
 * What a {@link FocusPayload} reports. See the foci spec, "Events".
 *
 * <p>Grows in minor releases: a switch over it needs a default arm.
 */
public enum FocusEventKind {
    /** A committed cast: a launch, a Touch, a Burst, or a Beam pulse that paid. */
    CAST,
    /** A focus composed at the Lathe. */
    COMPOSED,
    /** A focus ranked up at the Lathe. Its vis price is reported by the rank-up's vis event. */
    RANKED
}
