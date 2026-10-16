package com.andrewbrogoff.vftv.core.common;

import java.util.function.UnaryOperator;

/**
 * Persistent state on a holder (architecture, "State and sync"): a player, a chunk or a level.
 * Setting a value marks the holder dirty and syncs it where the slot syncs.
 *
 * <p>Values are immutable records: change one with {@link #set} or {@link #update}, never in place.
 */
public interface DataSlot<H, T> {
    /** The holder's value, or the slot's default. */
    T get(H holder);

    /** Replaces the holder's value. */
    void set(H holder, T value);

    /** Replaces the holder's value with {@code change} applied to it, and returns the new value. */
    default T update(H holder, UnaryOperator<T> change) {
        T next = change.apply(get(holder));
        set(holder, next);
        return next;
    }
}
