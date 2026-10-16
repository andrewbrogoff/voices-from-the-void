package com.andrewbrogoff.vftv.api.focus;

import com.andrewbrogoff.vftv.api.Stable;

/**
 * An entry of the synced datapack registry {@code vftv:focus_effect} ({@link
 * FocusKeys#EFFECT_REGISTRY}): what a working does to its targets. A marker in v1: it gives id and
 * holder access only, and an addon reads the entry through its JSON. Accessors may arrive in a
 * minor as {@code default} methods. See the foci spec, "Composition".
 *
 * <p>Not for implementation: vftv implements this interface; an addon only reads it.
 */
@Stable
public interface FocusEffect {}
