package com.andrewbrogoff.vftv.api.worldgen;

import com.andrewbrogoff.vftv.api.Stable;

/**
 * The worldgen feature's fixed limits: formats and invariants, not balance. See the worldgen spec,
 * "Tuning and limits".
 *
 * <p>javac inlines these constants into addon code, so an addon keeps the value it compiled against
 * even after a later release changes ours.
 */
@Stable
public final class WorldgenLimits {
    private WorldgenLimits() {}

    /**
     * The nodes a ruin's chancel template authors, and the most one ruin places: 1. A data-format
     * bound: may only widen within a major.
     */
    public static final int RUIN_NODES = 1;

    /**
     * The largest side of any structure template, in blocks: 48. A data-format bound: may only
     * widen within a major.
     */
    public static final int MAX_TEMPLATE_SIDE = 48;

    /**
     * The most weathering rules in a ruin's processor list: 8. A data-format bound: may only widen
     * within a major.
     */
    public static final int MAX_WEATHERING_RULES = 8;

    /**
     * The most pages one {@code vftv:choir_site} entry lists: 64. A data-format bound: may only
     * widen within a major.
     */
    public static final int MAX_PAGES_PER_SITE = 64;

    /**
     * The largest {@code pages_per_claim} of a {@code vftv:choir_site} entry, and the most pages
     * one claim records: 8. A data-format bound: may only widen within a major.
     */
    public static final int MAX_PAGES_PER_CLAIM = 8;

    /**
     * The most sites one player's {@code vftv:choir_claims} record holds: 65,536. A data-format
     * bound: may only widen within a major.
     */
    public static final int MAX_CLAIM_SITES = 65_536;

    /**
     * The Reliquary attempts one player may make in one server tick: 1. A runtime cap: may change
     * in any minor.
     */
    public static final int MAX_USES_PER_PLAYER_PER_TICK = 1;

    /**
     * The most structures of {@code #vftv:choir_sites} the Choir-site place query reads, the first
     * by id: 8. A data-format bound: may only widen within a major.
     */
    public static final int MAX_SITE_STRUCTURES = 8;

    /** The most Answerers one retaliation alerts: 16. A runtime cap: may change in any minor. */
    public static final int MAX_ALERT = 16;

    /**
     * How long a Cantor shows its chant after chanting, in ticks: 40. A runtime cap: may change in
     * any minor.
     */
    public static final int CHANT_SHOW_TICKS = 40;
}
