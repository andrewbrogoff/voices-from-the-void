package com.andrewbrogoff.vftv.core.text.model;

import java.util.List;

/**
 * The localisation and accessibility spec's own code-defined keys ("Lang keys"): written into
 * {@code en_us} by datagen, with translations in each locale's {@code accessibility.json}.
 */
public final class AccessibilityKeys {
    /**
     * The key-mapping category. Vanilla derives a category's label as {@code
     * key.category.<namespace>.<path>}, so the spec's {@code key.categories.vftv} can't be
     * expressed; this is its nearest form.
     */
    public static final String CATEGORY = "key.category.vftv.main";

    /** Opens the details view on the latest status line. */
    public static final String STATUS_DETAILS = "key.vftv.status_details";

    /** Turns the crosshair card back one sheet. */
    public static final String PREVIOUS_SHEET = "key.vftv.card.previous_sheet";

    /** Turns the crosshair card on one sheet. */
    public static final String NEXT_SHEET = "key.vftv.card.next_sheet";

    /** The status line's hint when it shows only 2 of its lines; arg: the details key's name. */
    public static final String STATUS_MORE = "hud.vftv.status.more";

    /** A paged card's indicator; args: sheet, sheets, the previous and next keys' names. */
    public static final String CARD_SHEET = "hud.vftv.card.sheet";

    /** The details view's title. */
    public static final String STATUS_DETAILS_TITLE = "screen.vftv.status_details";

    /** The details view's Close button. */
    public static final String CLOSE = "screen.vftv.close";

    private static final String FILE = "accessibility.json";

    /** The lang table rows for these keys. */
    public static final List<LangEntry> TABLE =
            List.of(
                    LangEntry.codeDefined(CATEGORY, 0, FILE, "Voices From The Void"),
                    LangEntry.codeDefined(STATUS_DETAILS, 0, FILE, "Read the last message"),
                    LangEntry.codeDefined(PREVIOUS_SHEET, 0, FILE, "Previous card sheet"),
                    LangEntry.codeDefined(NEXT_SHEET, 0, FILE, "Next card sheet"),
                    LangEntry.codeDefined(STATUS_MORE, 1, FILE, "More: press %s"),
                    LangEntry.codeDefined(CARD_SHEET, 4, FILE, "Sheet %s of %s (%s, %s)"),
                    LangEntry.codeDefined(STATUS_DETAILS_TITLE, 0, FILE, "The last message"),
                    LangEntry.codeDefined(CLOSE, 0, FILE, "Close"));

    private AccessibilityKeys() {}
}
