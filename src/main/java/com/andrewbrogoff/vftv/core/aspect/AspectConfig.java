package com.andrewbrogoff.vftv.core.aspect;

/**
 * The aspect feature's server switch, key 19 of {@code vftv-server.toml} (configuration, "Server
 * keys"). It applies at the next server data load; nothing is re-sent when it changes.
 *
 * @param deriveModded {@code aspects.derive_modded}: off, the {@code DerivedAspects} table is empty
 */
public record AspectConfig(boolean deriveModded) {
    /** The default: derivation on. */
    public static final AspectConfig DEFAULT = new AspectConfig(true);

    /**
     * {@code aspects.derive_modded}'s file comment, verbatim from the configuration spec's "Lang
     * keys"; also its tooltip's English.
     */
    public static final String DERIVE_MODDED_COMMENT =
            "Give items without authored aspects values derived from their recipes, at each data"
                    + " load. Authored values win; {} suppresses. Off: no derived values. Default:"
                    + " true.";
}
