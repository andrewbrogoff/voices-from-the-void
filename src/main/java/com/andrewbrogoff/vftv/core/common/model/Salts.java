package com.andrewbrogoff.vftv.core.common.model;

/**
 * The salt table (conventions, "Salts"). Every {@code fold} in the mod starts with one of these.
 * Salts are fixed in code and never tuned; a new salt amends the conventions table first.
 */
public final class Salts {
    /** Research note seed. */
    public static final int NOTE = 0x4E4F5445;

    /** Research marginalia. */
    public static final int MARG = 0x4D415247;

    /** Warp per-player phase. */
    public static final int WARP = 0x57415250;

    /** Warp Voice check. */
    public static final int VOIC = 0x564F4943;

    /** Hush chunk phase and attempt stream. */
    public static final int HUSH = 0x48555348;

    /** Pealing Bell phase. */
    public static final int PEAL = 0x5045414C;

    /** {@code FluxRules} generation. */
    public static final int FLUX = 0x464C5558;

    /** The shared block phase: crucible step, Alembic pour, essentia pulse, infusion bowl. */
    public static final int CRUC = 0x43525543;

    /** Infusion upset seed. */
    public static final int INFU = 0x494E4655;

    /** {@code InfusionRules} generation. */
    public static final int INFR = 0x494E4652;

    /** Golem think phase. */
    public static final int GOLE = 0x474F4C45;

    /** Natural node spread. */
    public static final int NODE = 0x4E4F4445;

    /** Starter ring start. */
    public static final int STAR = 0x53544152;

    /** Choir structure node roll. */
    public static final int CHOR = 0x43484F52;

    private Salts() {}

    /** The fold word of a salt: its four ASCII bytes as a non-negative int, widened. */
    public static long word(int salt) {
        return salt & 0xFFFFFFFFL;
    }
}
