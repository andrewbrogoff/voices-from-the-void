package com.andrewbrogoff.vftv.core.research.model;

import com.andrewbrogoff.vftv.api.research.ResearchCategory;
import org.jspecify.annotations.Nullable;

/**
 * One {@code vftv:research_category} (research spec, "Registry format").
 *
 * @param stage 1-4, or null for the Choir pages, whose entries keep their own Stage
 * @param icon the tab icon
 * @param order the tab order
 */
public record CategoryEntry(@Nullable Integer stage, ResearchEntry.Icon icon, int order)
        implements ResearchCategory {}
