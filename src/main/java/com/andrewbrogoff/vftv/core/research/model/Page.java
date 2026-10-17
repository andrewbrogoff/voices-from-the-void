package com.andrewbrogoff.vftv.core.research.model;

/**
 * One typed page descriptor of an entry (research spec, "Page descriptors"). Page {@code i} takes
 * its prose from {@code research.<ns>.<path>.page.<i>}.
 */
public sealed interface Page {
    /** The {@code vftv:page_type} id. */
    String type();

    /** Whether the page's prose key is required, optional (a caption) or absent. */
    Prose prose();

    /** How a page uses its lang key. */
    enum Prose {
        /** The page is prose: its key is required. */
        REQUIRED,
        /** An optional caption. */
        OPTIONAL,
        /** No key. */
        NONE
    }

    /** {@code vftv:text}: the prose. */
    record Text() implements Page {
        @Override
        public String type() {
            return "vftv:text";
        }

        @Override
        public Prose prose() {
            return Prose.REQUIRED;
        }
    }

    /**
     * {@code vftv:recipe}: a synced recipe through its owner's preview.
     *
     * @param recipe the recipe id
     * @param preview whether it is a locked preview implying no craftability
     */
    record Recipe(String recipe, boolean preview) implements Page {
        @Override
        public String type() {
            return "vftv:recipe";
        }

        @Override
        public Prose prose() {
            return Prose.OPTIONAL;
        }
    }

    /**
     * {@code vftv:aspects}: a thing's aspects.
     *
     * @param of the thing's scan key
     */
    record Aspects(String of) implements Page {
        @Override
        public String type() {
            return "vftv:aspects";
        }

        @Override
        public Prose prose() {
            return Prose.OPTIONAL;
        }
    }

    /**
     * {@code vftv:structure}: a rotatable multiblock preview.
     *
     * @param multiblock the client resource id
     */
    record Structure(String multiblock) implements Page {
        @Override
        public String type() {
            return "vftv:structure";
        }

        @Override
        public Prose prose() {
            return Prose.OPTIONAL;
        }
    }

    /**
     * {@code vftv:image}: a GUI sprite.
     *
     * @param sprite the sprite id
     */
    record Image(String sprite) implements Page {
        @Override
        public String type() {
            return "vftv:image";
        }

        @Override
        public Prose prose() {
            return Prose.OPTIONAL;
        }
    }

    /** {@code vftv:action}: the note grid, Buy or Listen, by kind. */
    record Action() implements Page {
        @Override
        public String type() {
            return "vftv:action";
        }

        @Override
        public Prose prose() {
            return Prose.NONE;
        }
    }

    /** {@code vftv:choir}: prose on Choir paper, never degraded. */
    record Choir() implements Page {
        @Override
        public String type() {
            return "vftv:choir";
        }

        @Override
        public Prose prose() {
            return Prose.REQUIRED;
        }
    }
}
