package com.andrewbrogoff.vftv.api.focus;

/**
 * The gesture that started a cast. See the foci spec, "Entry points".
 *
 * <p>Grows in minor releases: a switch over it needs a default arm.
 */
public enum FocusGesture {
    /** A use on air or an entity, or on a block whose own use didn't consume the click. */
    ENTITY,
    /** A sneak-use on a block that isn't a node. */
    BLOCK
}
