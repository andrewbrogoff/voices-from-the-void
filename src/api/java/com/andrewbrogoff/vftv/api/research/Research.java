package com.andrewbrogoff.vftv.api.research;

import com.andrewbrogoff.vftv.api.Stable;

/**
 * An element of the synced datapack registry {@code vftv:research}: one research entry. A marker in
 * v1: it gives id and holder access only, and an addon reads an entry's fields through its JSON.
 * Accessors may arrive in a minor as {@code default} methods.
 *
 * <p>Not for implementation: only vftv implements this interface. See the research spec, "Registry
 * format".
 */
@Stable
public interface Research {}
