package com.andrewbrogoff.vftv.core.text.model;

import java.util.List;

/**
 * The shared message keys (conventions, "Message keys"): code-defined, written into {@code en_us}
 * by datagen, with translations in each locale's {@code accessibility.json}.
 */
public final class SharedMessages {
    /** The refusal when an operation's guard is already held. */
    public static final String BUSY = "message.vftv.busy";

    /** The refusal when a record cannot be read. */
    public static final String UNREADABLE = "message.vftv.unreadable";

    /** The refusal when a checked commit is stale. */
    public static final String COST_CHANGED = "message.vftv.cost_changed";

    private static final String FILE = "accessibility.json";

    /** The lang table rows for these keys. */
    public static final List<LangEntry> TABLE =
            List.of(
                    LangEntry.codeDefined(BUSY, 0, FILE, "Not now."),
                    LangEntry.codeDefined(
                            UNREADABLE, 0, FILE, "I can't read it, so I won't touch it."),
                    LangEntry.codeDefined(COST_CHANGED, 0, FILE, "The cost has changed."));

    private SharedMessages() {}
}
