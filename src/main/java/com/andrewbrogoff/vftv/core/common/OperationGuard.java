package com.andrewbrogoff.vftv.core.common;

import java.util.HashSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * One owner's operation guard (conventions, "Operation guards"): a transient flag per holder, never
 * persisted. The guard is keyed by (owner, holder): each owner has its own instance, so the warp
 * guard and the road guard on one player are separate.
 *
 * <p>Use it on the server thread only:
 *
 * <pre>{@code
 * try (OperationGuard.Scope scope = guard.tryEnter(player.getUUID())) {
 *     if (scope == null) {
 *         return BUSY; // re-entry: refuse, never drop silently
 *     }
 *     // authorise, revalidate, write, notify
 * }
 * }</pre>
 *
 * Holders compare by {@code equals}: a UUID by value, a block entity or level by identity.
 */
public final class OperationGuard {
    private final String owner;
    private final Set<Object> held = new HashSet<>();

    /** A guard for one owner, named for diagnostics. */
    public OperationGuard(String owner) {
        this.owner = owner;
    }

    /** Takes the guard for {@code holder}, or returns null when it is already held. */
    public @Nullable Scope tryEnter(Object holder) {
        return held.add(holder) ? new Scope(holder) : null;
    }

    /** Whether the guard for {@code holder} is held. */
    public boolean isHeld(Object holder) {
        return held.contains(holder);
    }

    @Override
    public String toString() {
        return "OperationGuard[" + owner + ", held=" + held.size() + "]";
    }

    /** A held guard, released by {@link #close} on every path. Closing twice is harmless. */
    public final class Scope implements AutoCloseable {
        private final Object holder;
        private boolean open = true;

        private Scope(Object holder) {
            this.holder = holder;
        }

        @Override
        public void close() {
            if (open) {
                open = false;
                held.remove(holder);
            }
        }
    }
}
