package com.andrewbrogoff.vftv.core.arcane;

import com.andrewbrogoff.vftv.core.text.model.LangEntry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;

/**
 * The arcane feature's lang keys (arcane spec, "Lang keys"). Every key is authored in {@code
 * content/lang/<lang>/arcane.json}; this table gives the validator their argument counts.
 */
public final class ArcaneLang {
    /** The owner's file. */
    public static final String FILE = "arcane.json";

    /** The screen title. */
    public static final String CONTAINER = "container.vftv.worktable";

    /** The status keys' prefix. */
    public static final String STATUS = "status.vftv.worktable.";

    /** Ready, taxed: the result's name, warp and spill. */
    public static final String READY_TAXED = STATUS + "ready_taxed";

    /** The tooltip keys' prefix. */
    public static final String TOOLTIP = "tooltip.vftv.worktable.";

    /** The preview's batch total: n, total spill. */
    public static final String BATCH_FLUX = TOOLTIP + "batch_flux";

    /** The preview's usage line. */
    public static final String PREVIEW = TOOLTIP + "preview";

    /** A priced cost: aspect, effective, base. */
    public static final String COST = TOOLTIP + "cost";

    /** An unpriced cost: aspect, base. */
    public static final String BASE = TOOLTIP + "base";

    /** The pool: current, capacity. */
    public static final String POOL = TOOLTIP + "pool";

    /** The factors: cap, discount, warp, m. */
    public static final String FACTORS = TOOLTIP + "factors";

    /** The discount was capped. */
    public static final String DISCOUNT_CAPPED = TOOLTIP + "discount_capped";

    /** The multiplier was clamped. */
    public static final String CLAMPED = TOOLTIP + "clamped";

    /** The spill: flux. */
    public static final String FLUX = TOOLTIP + "flux";

    /** The taxed line outside the station: warp, spill. */
    public static final String TAXED = "arcane.vftv.taxed";

    /** Absent value: no wand. */
    public static final String NO_WAND = "arcane.vftv.no_wand";

    /** Absent value: an inert wand. */
    public static final String INERT_WAND = "arcane.vftv.inert_wand";

    /** The pricing wand: its name, where. */
    public static final String PRICED_WITH = "arcane.vftv.priced_with";

    /** Where the reference wand is: {@code main_hand}, {@code offhand}, {@code inventory}. */
    public static final String WHERE = "arcane.vftv.where.";

    /** The preview's narration: count, name. */
    public static final String NARRATE_RESULT = "narration.vftv.worktable.result";

    /** A cost cell's narration: aspect, cost, pool. */
    public static final String NARRATE_COST = "narration.vftv.worktable.cost";

    /** The JEI category title (used by the JEI ticket). */
    public static final String JEI = "jei.vftv.category.arcane";

    /** The lang table. */
    public static final List<LangEntry> TABLE = table();

    private ArcaneLang() {}

    /** A sound event's subtitle key. */
    public static String subtitle(Identifier sound) {
        return "subtitles.vftv." + sound.getPath();
    }

    private static List<LangEntry> table() {
        List<LangEntry> rows = new ArrayList<>();
        row(rows, "block.vftv.worktable", 0);
        row(rows, CONTAINER, 0);
        row(rows, STATUS + "ready", 1);
        row(rows, READY_TAXED, 3);
        row(rows, BATCH_FLUX, 2);
        row(rows, TAXED, 2);
        for (String status :
                List.of("no_wand", "no_recipe", "leftover", "exceeds_capacity", "output_full")) {
            row(rows, STATUS + status, 0);
        }
        row(rows, STATUS + "error", 0);
        row(rows, PREVIEW, 0);
        row(rows, COST, 3);
        row(rows, BASE, 2);
        row(rows, POOL, 2);
        row(rows, FACTORS, 4);
        row(rows, DISCOUNT_CAPPED, 0);
        row(rows, CLAMPED, 0);
        row(rows, FLUX, 1);
        row(rows, NO_WAND, 0);
        row(rows, INERT_WAND, 0);
        row(rows, PRICED_WITH, 2);
        for (String where : List.of("main_hand", "offhand", "inventory")) {
            row(rows, WHERE + where, 0);
        }
        row(rows, NARRATE_RESULT, 2);
        row(rows, NARRATE_COST, 3);
        row(rows, JEI, 0);
        ArcaneContent.SOUNDS.forEach(s -> row(rows, subtitle(s), 0));
        return List.copyOf(rows);
    }

    private static void row(List<LangEntry> rows, String key, int args) {
        rows.add(LangEntry.authored(key, args, FILE, LangEntry.Kind.TEMPLATE));
    }
}
