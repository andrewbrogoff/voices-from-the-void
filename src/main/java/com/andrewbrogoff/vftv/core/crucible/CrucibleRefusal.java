package com.andrewbrogoff.vftv.core.crucible;

/** Why a crucible refused an entity (crucible spec, "Refusal and ejection"). */
public enum CrucibleRefusal {
    /** The stack is protected. */
    PROTECTED("message.vftv.crucible.protected", 0),
    /** The item has no aspects. */
    NOTHING("message.vftv.crucible.nothing", 0),
    /** The fit rule admits none. */
    FULL("message.vftv.crucible.full", 0),
    /** The lowest-id learned candidate is unaffordable; the argument is the shortfall. */
    SHORT("message.vftv.crucible.short", 1),
    /** A craft failed before its commit. */
    ERROR("message.vftv.crucible.error", 0);

    private final String key;
    private final int args;

    CrucibleRefusal(String key, int args) {
        this.key = key;
        this.args = args;
    }

    /** The status line's lang key. */
    public String key() {
        return key;
    }

    /** The key's argument count. */
    public int args() {
        return args;
    }
}
