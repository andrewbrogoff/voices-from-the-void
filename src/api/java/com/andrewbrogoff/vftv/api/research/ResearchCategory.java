package com.andrewbrogoff.vftv.api.research;

import com.andrewbrogoff.vftv.api.Stable;

/**
 * An element of the synced datapack registry {@code vftv:research_category}: one tab of the
 * Commonplace Book. A marker in v1: it gives id and holder access only, and an addon reads a
 * category's fields through its JSON. Accessors may arrive in a minor as {@code default} methods.
 *
 * <p>Not for implementation: only vftv implements this interface. See the research spec, "Registry
 * format".
 */
@Stable
public interface ResearchCategory {}
