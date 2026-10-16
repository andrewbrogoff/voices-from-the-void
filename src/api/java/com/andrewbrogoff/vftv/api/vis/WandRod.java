package com.andrewbrogoff.vftv.api.vis;

import com.andrewbrogoff.vftv.api.Stable;

/**
 * A wand rod: an entry of the synced datapack registry {@code vftv:wand_rod} ({@link
 * VisKeys#WAND_ROD_REGISTRY}), the part that sets a wand's capacity. See the wands spec, "Parts".
 *
 * <p>A marker in v1: it gives id and holder access only, and an addon reads the entry through its
 * JSON. Accessors may arrive in a minor as {@code default} methods.
 *
 * <p>Not for implementation: vftv implements this interface; an addon only reads it.
 */
@Stable
public interface WandRod {}
