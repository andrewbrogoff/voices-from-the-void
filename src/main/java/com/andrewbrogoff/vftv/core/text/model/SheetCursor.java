package com.andrewbrogoff.vftv.core.text.model;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A card's current sheet (crucible, "The HUD", turning sheets): never automatic, reset to the first
 * sheet when the target changes, clamped to the new count on a relayout, and stopping at the ends.
 */
public final class SheetCursor {
    private @Nullable Object target;
    private int sheet;

    /** The current sheet, 0-based. */
    public int sheet() {
        return sheet;
    }

    /** Names the card's target; a different target resets to the first sheet. */
    public void target(@Nullable Object key) {
        if (!Objects.equals(key, target)) {
            target = key;
            sheet = 0;
        }
    }

    /** Clamps the sheet to a relayout's count. */
    public void clamp(int sheets) {
        sheet = Math.clamp(sheet, 0, Math.max(0, sheets - 1));
    }

    /** Turns by {@code delta} sheets within {@code sheets}; returns whether the sheet changed. */
    public boolean turn(int delta, int sheets) {
        int next = Math.clamp((long) sheet + delta, 0, Math.max(0, sheets - 1));
        if (next == sheet) {
            return false;
        }
        sheet = next;
        return true;
    }
}
