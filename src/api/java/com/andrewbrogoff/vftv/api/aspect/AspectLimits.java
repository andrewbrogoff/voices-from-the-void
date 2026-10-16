package com.andrewbrogoff.vftv.api.aspect;

import com.andrewbrogoff.vftv.api.Stable;

/**
 * The aspect feature's fixed limits: data formats and graph-validator rules, not balance. See the
 * aspects spec, "Tuning".
 *
 * <p>javac inlines these constants into addon code: an addon keeps the value it compiled against,
 * even after a later minor changes ours.
 */
@Stable
public final class AspectLimits {
    private AspectLimits() {}

    /**
     * The most aspects one value of {@code vftv:aspects} or {@code vftv:aspect_defaults} may hold
     * (one HUD row); a count, 0 to this. A data-format bound: may only widen within a major.
     */
    public static final int MAX_ASPECTS_PER_ENTRY = 5;

    /**
     * The deepest tier any aspect may sit at, counted in parent steps from the primals (primals are
     * depth 0), checked on both the true and the taught view. A data-format bound: may only widen
     * within a major.
     */
    public static final int MAX_DEPTH = 3;

    /**
     * The most children any compound may be a parent of; primals are exempt. A data-format bound:
     * may only widen within a major.
     */
    public static final int MAX_FAN_OUT = 5;

    /**
     * The fewest tier-1 aspects every exposed primal must be a parent of; hidden primals are
     * exempt. A data-format bound: may only widen within a major (for this minimum, widening means
     * lowering it).
     */
    public static final int MIN_TIER_ONE_CHILDREN = 2;

    /**
     * The largest sounding radius, in blocks, that the {@code sounding.radius} tuning key accepts
     * (its range is 1 to this). A data-format bound: may only widen within a major.
     */
    public static final int MAX_SOUNDING_RADIUS = 256;
}
