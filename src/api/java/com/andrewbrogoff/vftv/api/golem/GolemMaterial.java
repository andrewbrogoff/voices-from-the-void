package com.andrewbrogoff.vftv.api.golem;

import com.andrewbrogoff.vftv.api.Stable;

/**
 * An entry of the synced datapack registry {@code vftv:golem_material} ({@link
 * GolemKeys#MATERIAL_REGISTRY}): a golem body's stats. A marker in v1: it gives id and holder
 * access only, and an addon reads the entry through its JSON. Accessors may arrive in a minor as
 * {@code default} methods. See the golems spec, "Materials".
 *
 * <p>Not for implementation: vftv implements this interface; an addon only reads it.
 */
@Stable
public interface GolemMaterial {}
