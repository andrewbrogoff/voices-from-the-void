package com.andrewbrogoff.vftv.core.common;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The re-entry template (conventions, "Tests") at the guard level. */
class OperationGuardTest {
    private final UUID player = UUID.randomUUID();

    @Test
    void reEntryIsRefusedWhileHeld() {
        OperationGuard warp = new OperationGuard("warp");
        try (OperationGuard.Scope scope = warp.tryEnter(player)) {
            assertNotNull(scope);
            assertTrue(warp.isHeld(player));
            assertNull(warp.tryEnter(player), "a re-entrant call finds the guard held");
            assertNull(
                    warp.tryEnter(UUID.fromString(player.toString())), "holders compare by equals");
        }
        assertFalse(warp.isHeld(player));
        assertNotNull(warp.tryEnter(player), "free again after release");
    }

    @Test
    void guardsArePerOwnerAndPerHolder() {
        OperationGuard warp = new OperationGuard("warp");
        OperationGuard road = new OperationGuard("road");
        try (OperationGuard.Scope held = road.tryEnter(player)) {
            assertNotNull(held);
            assertNotNull(warp.tryEnter(player), "the road guard does not hold the warp guard");
            assertNotNull(road.tryEnter(UUID.randomUUID()), "another holder is free");
        }
    }

    @Test
    void theGuardIsReleasedWhenTheOperationThrows() {
        OperationGuard guard = new OperationGuard("golem");
        assertThrows(
                IllegalStateException.class,
                () -> {
                    try (OperationGuard.Scope scope = guard.tryEnter(player)) {
                        assertNotNull(scope);
                        throw new IllegalStateException("listener threw");
                    }
                });
        assertFalse(guard.isHeld(player));
    }

    @Test
    void closingTwiceDoesNotReleaseANewerHold() {
        OperationGuard guard = new OperationGuard("hush");
        OperationGuard.Scope first = guard.tryEnter(player);
        assertNotNull(first);
        first.close();
        OperationGuard.Scope second = guard.tryEnter(player);
        assertNotNull(second);
        first.close();
        assertTrue(guard.isHeld(player), "the stale scope's second close changed nothing");
        second.close();
        assertTrue(guard.toString().contains("hush"));
    }
}
