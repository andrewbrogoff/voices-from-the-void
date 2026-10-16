package com.andrewbrogoff.vftv.core.common.model;

import java.util.function.Supplier;

/**
 * Checks 4 and 6 of the checked-commit protocol (conventions, "Checked commits") as pure compares.
 * The owner runs its change check before {@link #revision}, its own eligibility between the two,
 * and the write with no foreign code after {@link #quote}.
 */
public final class CheckedCommit {
    /** The result of a check. */
    public enum Outcome {
        /** The check passed; go on to the next. */
        OK,
        /** The client saw an older surface or price; refuse and re-quote. */
        STALE
    }

    private CheckedCommit() {}

    /** Check 4: the echoed revision equals the surface's current one. */
    public static Outcome revision(int current, int echoed) {
        return current == echoed ? Outcome.OK : Outcome.STALE;
    }

    /** Check 6: the quote recomputed from live state equals the echo the client confirmed. */
    public static <Q> Outcome quote(Supplier<Q> recompute, Q echo) {
        return recompute.get().equals(echo) ? Outcome.OK : Outcome.STALE;
    }

    /** The next revision: adds 1, wrapping at the int bound. */
    public static int bump(int revision) {
        return revision + 1;
    }
}
