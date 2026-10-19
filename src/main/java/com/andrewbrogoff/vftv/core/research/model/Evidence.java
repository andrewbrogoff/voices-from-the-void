package com.andrewbrogoff.vftv.core.research.model;

import java.util.function.Predicate;

/**
 * Something that happened to a player that a trigger can match (research spec, "Triggers"): an
 * advancement earned, a thing scanned, an aspect discovered, a Choir page tipped in or the player's
 * warp after a commit.
 */
public sealed interface Evidence {
    /** Whether {@code trigger} fires on this. */
    boolean matches(Trigger trigger);

    /**
     * An advancement earned.
     *
     * @param id the advancement id
     */
    record Advancement(String id) implements Evidence {
        @Override
        public boolean matches(Trigger trigger) {
            return trigger instanceof Trigger.Advancement a && a.advancement().equals(id);
        }
    }

    /**
     * A thing scanned, with any outcome.
     *
     * @param key the scan key, e.g. {@code item:minecraft:amethyst_shard} or {@code node:7}
     * @param tagged whether the thing is in a tagged key, e.g. {@code item:#c:gems}, read as the
     *     tags are now
     */
    record Scanned(String key, Predicate<String> tagged) implements Evidence {
        @Override
        public boolean matches(Trigger trigger) {
            if (!(trigger instanceof Trigger.Scanned s)) {
                return false;
            }
            String tag = s.tag();
            return s.key() != null
                    ? s.key().equals(key)
                    : tag != null ? tag.startsWith(kind() + ":#") && tagged.test(tag) : node();
        }

        /** The key's kind: {@code item}, {@code block}, {@code entity} or {@code node}. */
        public String kind() {
            return key.substring(0, Math.max(0, key.indexOf(':')));
        }

        /** Whether this is a node key. */
        public boolean node() {
            return key.startsWith("node:");
        }
    }

    /**
     * An aspect discovered.
     *
     * @param aspect the aspect id
     */
    record Discovered(String aspect) implements Evidence {
        @Override
        public boolean matches(Trigger trigger) {
            return trigger instanceof Trigger.Discovered d && d.aspect().equals(aspect);
        }
    }

    /**
     * A Choir page tipped in.
     *
     * @param page the page entry's id
     */
    record ChoirPage(String page) implements Evidence {
        @Override
        public boolean matches(Trigger trigger) {
            return trigger instanceof Trigger.ChoirPage p && p.page().equals(page);
        }
    }

    /**
     * The player's warp after a commit.
     *
     * @param total every kind together
     * @param lastingPlusPermanent lasting plus permanent
     */
    record Warp(int total, int lastingPlusPermanent) implements Evidence {
        @Override
        public boolean matches(Trigger trigger) {
            return trigger instanceof Trigger.WarpAtLeast w
                    && (w.kind() == Trigger.WarpKind.TOTAL ? total : lastingPlusPermanent)
                            >= w.value();
        }
    }
}
