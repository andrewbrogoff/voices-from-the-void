package com.andrewbrogoff.vftv.core.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** The test task sets {@value TickBudget#PROPERTY}, so the budget is on here. */
class TickBudgetTest {
    private static TickBudget.Summary summary(String name) {
        return TickBudget.snapshot().stream()
                .filter(s -> s.section().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no section " + name));
    }

    private static void spin(long nanos) {
        long until = System.nanoTime() + nanos;
        while (System.nanoTime() < until) {
            Thread.onSpinWait();
        }
    }

    @Test
    void measuresTheServerThreadOnlyAndCountsNestingOnce() throws InterruptedException {
        assertTrue(TickBudget.ENABLED);
        TickBudget.closeTick(); // marks this thread as the server thread
        TickBudget.reset();

        try (var _ = TickBudget.enter("unit")) {
            try (var _ = TickBudget.enter("unit_nested")) {
                spin(1_000_000);
            }
        }
        AtomicReference<TickBudget.Scope> other = new AtomicReference<>();
        Thread client = new Thread(() -> other.set(TickBudget.enter("unit_client")));
        client.start();
        client.join();
        other.get().close();
        TickBudget.closeTick();

        TickBudget.Summary unit = summary("unit");
        assertEquals(1, unit.ticks());
        assertTrue(unit.max() >= 1_000_000, () -> "max " + unit.max());
        assertTrue(TickBudget.snapshot().stream().noneMatch(s -> s.section().startsWith("unit_")));
        TickBudget.Summary total = summary(TickBudget.TOTAL);
        assertEquals(1, total.ticks());
        assertEquals(unit.max(), total.max(), "the total is the sum of the sections");

        TickBudget.closeTick(); // an idle tick records zero for every section
        assertEquals(2, summary("unit").ticks());
        TickBudget.reset();
        assertEquals(0, summary(TickBudget.TOTAL).ticks());
    }
}
