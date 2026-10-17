package com.andrewbrogoff.vftv.core.research.model;

import static com.andrewbrogoff.vftv.core.research.model.Fixtures.b;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class RenderStateTest {
    private static final ResearchEntry FREE =
            b(1, "c:c", 0, 0, EntryKind.FREE).parents("p:p").build();
    private static final ResearchEntry FIND =
            b(1, "c:c", 0, 0, EntryKind.FIND).opens(0, "a:a").build();
    private static final ResearchEntry NOTE = b(1, "c:c", 0, 0, EntryKind.NOTE).build();
    private static final ResearchEntry LISTEN = b(3, "c:c", 0, 0, EntryKind.LISTEN).build();

    private static RenderState of(ResearchEntry e, ResearchRecord r, boolean canBegin) {
        return RenderState.of("x:e", e, r, entry -> canBegin);
    }

    @Test
    void hiddenAboveTheStageOrSecretAndUnrevealedEvenIfLearned() {
        ResearchRecord stage1 = ResearchRecord.EMPTY.withStage(1).learn("x:e");
        assertEquals(RenderState.HIDDEN, of(LISTEN, stage1, true));
        Fixtures.B secret = b(1, "c:c", 0, 0, EntryKind.FREE);
        secret.secret = true;
        assertEquals(RenderState.HIDDEN, of(secret.build(), stage1, true));
        ResearchRecord revealed =
                new ResearchRecord(
                        1,
                        1,
                        stage1.learned(),
                        new TreeSet<>(List.of("x:e")),
                        stage1.latched(),
                        stage1.revealLatched(),
                        stage1.notes(),
                        List.of(),
                        3,
                        stage1.rewarded(),
                        stage1.attempts());
        assertEquals(RenderState.LEARNED, of(secret.build(), revealed, true));
    }

    @Test
    void openByKindOnceParentsAreLearned() {
        ResearchRecord r = ResearchRecord.EMPTY.withStage(3);
        assertEquals(RenderState.LOCKED, of(FREE, r, true));
        assertEquals(RenderState.OPEN, of(FREE, r.learn("p:p"), true));
        assertEquals(RenderState.LOCKED, of(FIND, r, true));
        assertEquals(RenderState.OPEN, of(FIND, r.latch("x:e"), true));
        assertEquals(RenderState.LOCKED, of(NOTE, r, false));
        assertEquals(RenderState.OPEN, of(NOTE, r, true));
        assertEquals(
                RenderState.OPEN,
                of(NOTE, r.withNotes(Map.of("x:e", ResearchRecordTest.note("x:e", 1))), false));
        assertEquals(RenderState.OPEN, of(LISTEN, r, false));
    }

    @Test
    void kindsAndOpeners() {
        assertEquals("listen", EntryKind.LISTEN.id());
        assertTrue(EntryKind.NOTE.committed());
        assertFalse(EntryKind.FIND.committed());
        assertTrue(b(1, "c:c", 0, 0, EntryKind.FIND).opens(1, "a:a").build().opener());
        assertFalse(FREE.opener());
        assertFalse(ResearchEntry.Warp.NONE.any());
        assertTrue(new ResearchEntry.Warp(0, 0, 1).any());
        assertEquals("lasting_plus_permanent", Trigger.WarpKind.LASTING_PLUS_PERMANENT.id());
    }
}
