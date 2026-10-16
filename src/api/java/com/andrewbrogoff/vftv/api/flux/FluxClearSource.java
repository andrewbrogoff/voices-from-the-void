package com.andrewbrogoff.vftv.api.flux;

/**
 * What cleared flux from a chunk. See the Hush spec, "The Clearing Bell", "The Pealing Bell" and
 * "Commands".
 *
 * <p>Grows in minor releases: a switch over it needs a default arm.
 */
public enum FluxClearSource {
    /** A player ringing a Clearing Bell. */
    BELL,
    /** A Pealing Bell's peal. */
    PEAL,
    /** An operator's command. */
    COMMAND
}
