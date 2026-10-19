package com.andrewbrogoff.vftv.core.arcane;

import com.andrewbrogoff.vftv.core.text.model.SharedMessages;
import com.andrewbrogoff.vftv.core.vis.VisLang;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The Worktable's status and refusal codes (arcane spec, "Status and refusals"). The code is the
 * wire value of the menu's data slot 0; the client computes the status line in the precedence of
 * the declaration order (after the empty-grid rule), and the server's refusals use the same codes.
 */
public enum WorktableStatus {
    /** Ready to craft; as a refusal, none. */
    READY(0, ArcaneLang.STATUS + "ready"),
    /** The wand slot is empty or holds no wand. */
    NO_WAND(1, ArcaneLang.STATUS + "no_wand"),
    /** The wand is inert. */
    INERT_WAND(2, VisLang.SPEND_INERT),
    /** Nothing the player has learned matches the grid. */
    NO_RECIPE(3, ArcaneLang.STATUS + "no_recipe"),
    /** A consumed slot would leave a remainder it cannot hold. */
    LEFTOVER(4, ArcaneLang.STATUS + "leftover"),
    /** Some effective cost is over the rod's capacity. */
    EXCEEDS_CAPACITY(5, ArcaneLang.STATUS + "exceeds_capacity"),
    /** Some pool is below its effective cost. */
    SHORT(6, VisLang.SPEND_SHORT),
    /** The output slot cannot take the result. */
    OUTPUT_FULL(7, ArcaneLang.STATUS + "output_full"),
    /** Server only: the revision, recipe id or echo was stale. */
    STALE(8, SharedMessages.COST_CHANGED),
    /** Server only: the spend threw and was rolled back. */
    ERROR(9, ArcaneLang.STATUS + "error");

    private static final List<WorktableStatus> BY_CODE = List.of(values());

    private final int code;
    private final String key;

    WorktableStatus(int code, String key) {
        this.code = code;
        this.key = key;
    }

    /** The wire value. */
    public int code() {
        return code;
    }

    /** The status line's lang key ({@code READY}'s untaxed form). */
    public String key() {
        return key;
    }

    /** The status for a wire value, or null for none in range. */
    public static @Nullable WorktableStatus of(int code) {
        return code >= 0 && code < BY_CODE.size() ? BY_CODE.get(code) : null;
    }
}
