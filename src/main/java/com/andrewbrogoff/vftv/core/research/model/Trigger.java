package com.andrewbrogoff.vftv.core.research.model;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * One typed trigger of a {@code triggers} or {@code reveal} list (research spec, "Triggers"): the
 * types are code, the instances data. A list is an OR; the fields within one trigger are an AND.
 */
public sealed interface Trigger {
    /** The {@code vftv:research_trigger_type} id. */
    String type();

    /**
     * {@code vftv:advancement}: an advancement earned.
     *
     * @param advancement the advancement id
     */
    record Advancement(String advancement) implements Trigger {
        @Override
        public String type() {
            return "vftv:advancement";
        }
    }

    /**
     * {@code vftv:scanned}: a thing scanned, matched by exactly one of a scan key, a tag or any
     * node.
     *
     * @param key a scan key, e.g. {@code item:minecraft:amethyst_shard}, or null
     * @param tag a tagged scan key, e.g. {@code item:#c:gems}, or null
     * @param node whether any node key matches
     */
    record Scanned(@Nullable String key, @Nullable String tag, boolean node) implements Trigger {
        /** Checks that exactly one matcher is set. */
        public Scanned {
            int set = (key != null ? 1 : 0) + (tag != null ? 1 : 0) + (node ? 1 : 0);
            if (set != 1) {
                throw new IllegalArgumentException("exactly one of key, tag or node");
            }
        }

        @Override
        public String type() {
            return "vftv:scanned";
        }
    }

    /**
     * {@code vftv:discovered}: an aspect discovered.
     *
     * @param aspect the aspect id
     */
    record Discovered(String aspect) implements Trigger {
        @Override
        public String type() {
            return "vftv:discovered";
        }
    }

    /**
     * {@code vftv:choir_page}: a Choir page tipped in.
     *
     * @param page the page entry's id
     */
    record ChoirPage(String page) implements Trigger {
        @Override
        public String type() {
            return "vftv:choir_page";
        }
    }

    /**
     * {@code vftv:warp_at_least}: the player's warp reaching a value.
     *
     * @param kind which warp counts
     * @param value the threshold, 1-1000
     */
    record WarpAtLeast(WarpKind kind, int value) implements Trigger {
        @Override
        public String type() {
            return "vftv:warp_at_least";
        }
    }

    /** Which warp a {@code warp_at_least} trigger counts. */
    enum WarpKind {
        /** Every kind. */
        TOTAL,
        /** Lasting plus permanent: what stilling can't lower below a commit's reveal. */
        LASTING_PLUS_PERMANENT;

        /** The JSON name. */
        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
