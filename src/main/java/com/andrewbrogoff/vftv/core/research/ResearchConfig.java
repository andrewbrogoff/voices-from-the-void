package com.andrewbrogoff.vftv.core.research;

/**
 * The research server switch, key 1 of {@code vftv-server.toml} (configuration, "Server keys").
 *
 * @param mode {@code research.mode}: the preset new notes use, or skip
 */
public record ResearchConfig(Mode mode) {
    /** The default: standard notes. */
    public static final ResearchConfig DEFAULT = new ResearchConfig(Mode.STANDARD);

    /** The key's file comment, verbatim from the configuration spec; also its tooltip's English. */
    public static final String MODE_COMMENT =
            "gentle, standard or hard notes, or skip: buy research with aspect points instead. Open"
                    + " notes keep their difficulty. Default: standard.";

    /** {@code research.mode}: a note preset, or skip mode (research spec, "Presets and modes"). */
    public enum Mode {
        /** The gentle preset. */
        GENTLE,
        /** The standard preset. */
        STANDARD,
        /** The hard preset. */
        HARD,
        /** No new note can be begun; eligible note entries are bought with points. */
        SKIP
    }
}
