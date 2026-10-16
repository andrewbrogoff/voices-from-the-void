package com.andrewbrogoff.vftv.core.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DataReloadsTest {
    @Test
    void manyMarksBeforeATickRebuildOnce() throws InterruptedException {
        AtomicInteger rebuilds = new AtomicInteger();
        DataReloads.onClientRebuild(rebuilds::incrementAndGet);
        DataReloads.clientTick(); // drain anything earlier tests marked
        int before = rebuilds.get();
        int counted = DataReloads.clientRebuilds();

        DataReloads.clientTick();
        assertEquals(before, rebuilds.get(), "no change, no rebuild");

        Thread server =
                new Thread(
                        () -> {
                            DataReloads.markChanged(); // one event per registry
                            DataReloads.markChanged();
                        });
        server.start();
        server.join();
        DataReloads.clientTick();
        DataReloads.clientTick();
        assertEquals(before + 1, rebuilds.get());
        assertEquals(counted + 1, DataReloads.clientRebuilds());
    }
}
