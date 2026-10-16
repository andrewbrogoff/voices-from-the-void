package com.andrewbrogoff.vftv.api.aspect;

/**
 * What a strike that recorded a scan was aimed at. See the aspects spec, "Striking".
 *
 * <p>Grows in minor releases: a switch over it needs a default arm.
 */
public enum ScanTargetKind {
    /** A placed block. */
    BLOCK,
    /** An entity, a dropped item entity included. */
    ENTITY,
    /** An item in an inventory slot. */
    ITEM,
    /** A node. */
    NODE
}
