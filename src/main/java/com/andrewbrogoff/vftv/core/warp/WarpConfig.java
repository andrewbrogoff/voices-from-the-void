package com.andrewbrogoff.vftv.core.warp;

/**
 * The warp server switches that {@code WarpRules} carries, keys 2 and 10-12 of {@code
 * vftv-server.toml} (configuration, "Server keys"). The seven per-type Voice switches (keys 3-9)
 * are server-only, so they are not in this record: {@link VoiceChecks#installTypesOff} takes them.
 *
 * @param voices {@code warp.voices.enabled}: off, no Voice check rolls
 * @param tax {@code warp.tax}: off, the vis factor and the infusion penalty go neutral
 * @param amplifier {@code warp.amplifier}: off, the amplifier's warp is 0
 * @param gear {@code warp.gear}: off, the gear sample is 0
 */
public record WarpConfig(boolean voices, boolean tax, boolean amplifier, boolean gear) {
    /**
     * The per-type Voice switches, keys 3-9 under {@code warp.voices}: each config key, its
     * {@code vftv:voice_event_type}, its option name and its comment (verbatim, also the tooltip).
     *
     * @param key the key under {@code warp.voices}
     * @param type the event type it switches
     * @param name the option's English name
     * @param comment the file comment
     */
    public record TypeSwitch(String key, String type, String name, String comment) {}

    /** Keys 3-9, in the configuration spec's order. Server-only: never sent. */
    public static final java.util.List<TypeSwitch> TYPE_SWITCHES =
            java.util.List.of(
                    new TypeSwitch(
                            "whisper",
                            "vftv:whisper",
                            "Whispers",
                            "Whisper events. Off: never chosen. Default: true."),
                    new TypeSwitch(
                            "insight",
                            "vftv:insight",
                            "Insights",
                            "Insight events (aspect points). Off: never chosen. Default: true."),
                    new TypeSwitch(
                            "sound",
                            "vftv:sound",
                            "Sounds",
                            "Phantom sound events. Off: never chosen. Default: true."),
                    new TypeSwitch(
                            "colour",
                            "vftv:colour",
                            "Colour drain",
                            "Colour drain events. Off: never chosen. Default: true."),
                    new TypeSwitch(
                            "phantom_mob",
                            "vftv:phantom_mob",
                            "Apparitions",
                            "Apparition events. Off: never chosen. Default: true."),
                    new TypeSwitch(
                            "bargain",
                            "vftv:bargain",
                            "Bargains",
                            "Bargain offers. Off: no new offer; a standing one stays. Default:"
                                    + " true."),
                    new TypeSwitch(
                            "status",
                            "vftv:status",
                            "Status effects",
                            "Status-effect events in context. Off: never chosen. Default: true."));

    /** The defaults: every switch on. */
    public static final WarpConfig DEFAULT = new WarpConfig(true, true, true, true);

    /**
     * {@code warp.voices.enabled}'s file comment, verbatim from the configuration spec's "Lang
     * keys"; also its tooltip's English.
     */
    public static final String VOICES_COMMENT =
            "Voice events at all. Off: no check rolls. Default: true.";

    /** {@code warp.tax}'s comment. */
    public static final String TAX_COMMENT =
            "Warp raises vis costs and lowers infusion stability. Off: neither. Running infusions"
                    + " keep their plan. Default: true.";

    /** {@code warp.amplifier}'s comment. */
    public static final String AMPLIFIER_COMMENT =
            "Warp strengthens Void workings and Void armour. Off: no amplification. Default: true.";

    /** {@code warp.gear}'s comment. */
    public static final String GEAR_COMMENT =
            "Warp from worn and carried gear. Off: gear adds none. Default: true.";
}
