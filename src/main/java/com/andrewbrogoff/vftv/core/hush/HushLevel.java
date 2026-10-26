package com.andrewbrogoff.vftv.core.hush;

import com.andrewbrogoff.vftv.core.hush.model.DueQueue;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * One level's transient Hush state (Hush spec, "Sync and state": memory, rebuilt from loaded chunks
 * and block entities): the due queue, saturation and misses, the merge queue, the coalesced sync's
 * dirty set, and the Pealing Bells' index and queue. Server thread only.
 */
final class HushLevel {
    final DueQueue due = new DueQueue();
    final Set<Long> saturated = new HashSet<>();
    final Map<Long, Integer> misses = new HashMap<>();
    final TreeSet<Long> loaded = new TreeSet<>();
    final Set<Long> dirty = new LinkedHashSet<>();

    /** Loaded chunks holding an aura: a tuning reload reconsiders every one. */
    final Set<Long> auras = new java.util.HashSet<>();

    final DueQueue bells = new DueQueue();
    final Map<Long, PealingBellBlockEntity> bellIndex = new HashMap<>();

    /** Overflow counted while {@code vftv_flux_pending} is unreadable, for the session only. */
    long sessionOverflow;

    boolean pendingWarned;

    /** Clears a chunk's saturation and misses. */
    void unsaturate(long pack) {
        saturated.remove(pack);
        misses.remove(pack);
    }
}
