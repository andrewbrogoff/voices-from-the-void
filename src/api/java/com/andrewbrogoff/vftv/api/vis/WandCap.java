package com.andrewbrogoff.vftv.api.vis;

import com.andrewbrogoff.vftv.api.Stable;

/**
 * A wand cap: an entry of the synced datapack registry {@code vftv:wand_cap} ({@link
 * VisKeys#WAND_CAP_REGISTRY}), the part that sets a wand's cost multiplier. See the wands spec,
 * "Parts".
 *
 * <p>A marker in v1: it gives id and holder access only, and an addon reads the entry through its
 * JSON. Accessors may arrive in a minor as {@code default} methods.
 *
 * <p>Not for implementation: vftv implements this interface; an addon only reads it.
 */
@Stable
public interface WandCap {}
