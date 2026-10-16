package com.andrewbrogoff.vftv.api.aspect;

/**
 * How a scan changed the player's ledger entry for its key. See the aspects spec, "Events".
 *
 * <p>Grows in minor releases: a switch over it needs a default arm.
 */
public enum ScanOutcome {
    /** The key had no ledger entry. The scan may still be partial: check {@code complete}. */
    FIRST,
    /** An existing partial entry is still incomplete. */
    PARTIAL,
    /** An existing partial entry became complete. */
    COMPLETED,
    /** The entry was already complete. */
    REPEAT
}
