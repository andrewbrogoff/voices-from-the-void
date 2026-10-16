package com.andrewbrogoff.vftv.api.research;

import com.andrewbrogoff.vftv.api.Stable;
import net.minecraft.resources.ResourceKey;

/**
 * A read-only view of one server player's research, obtained through the {@code vftv:research_view}
 * entity capability. Answers are live, computed at the call by the research feature's own queries;
 * call it on the server thread only. An unreadable record answers as the research spec states. It
 * exposes no storage, no operation guard and no mutation.
 *
 * <p>Not for implementation: only vftv implements this interface. See the research spec, "Events",
 * and the API spec, "ResearchView and BaffleView".
 */
@Stable
public interface ResearchView {
    /**
     * Whether the entry is in the player's stored learned set, the set every gate reads.
     *
     * @param research the entry
     * @return true if the entry is learned
     */
    boolean isLearned(ResourceKey<Research> research);

    /**
     * Whether the entry's trigger has latched or the entry is learned.
     *
     * @param research the entry
     * @return true if the entry is latched or learned
     */
    boolean isLatchedOrLearned(ResourceKey<Research> research);

    /**
     * The player's Stage.
     *
     * @return the Stage, 0–4
     */
    int stage();
}
