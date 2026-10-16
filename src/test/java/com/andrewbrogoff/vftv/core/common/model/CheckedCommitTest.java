package com.andrewbrogoff.vftv.core.common.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.andrewbrogoff.vftv.core.common.model.CheckedCommit.Outcome;
import org.junit.jupiter.api.Test;

class CheckedCommitTest {
    @Test
    void aStaleRevisionIsStale() {
        assertEquals(Outcome.OK, CheckedCommit.revision(4, 4));
        assertEquals(Outcome.STALE, CheckedCommit.revision(5, 4));
    }

    @Test
    void aStaleEchoIsStale() {
        assertEquals(Outcome.OK, CheckedCommit.quote(() -> "12 vis", "12 vis"));
        assertEquals(Outcome.STALE, CheckedCommit.quote(() -> "13 vis", "12 vis"));
    }

    @Test
    void theRevisionWraps() {
        assertEquals(1, CheckedCommit.bump(0));
        assertEquals(Integer.MIN_VALUE, CheckedCommit.bump(Integer.MAX_VALUE));
    }
}
