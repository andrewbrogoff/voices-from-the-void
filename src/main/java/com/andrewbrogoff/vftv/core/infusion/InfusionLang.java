package com.andrewbrogoff.vftv.core.infusion;

import com.andrewbrogoff.vftv.core.text.model.LangEntry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;

/**
 * The infusion lang keys (infusion spec, "Lang keys"). Every key is authored in {@code
 * content/lang/<lang>/infusion.json}; this table gives the validator their argument counts.
 */
public final class InfusionLang {
    /** The owner's file. */
    public static final String FILE = "infusion.json";

    /** The readout statuses' prefix (HUD line 1). */
    public static final String STATUS = "status.vftv.infusion.";

    /** The HUD lines' prefix. */
    public static final String HUD = "hud.vftv.infusion.";

    /** The refusals' prefix. */
    public static final String MESSAGE = "message.vftv.infusion.";

    /** The preview's availability texts' prefix. */
    public static final String PREVIEW_STATUS = "infusion.vftv.status.";

    /** An aspect the reader hasn't discovered. */
    public static final String UNHEARD = "infusion.vftv.unheard";

    /** The preview's context: x, y, z. */
    public static final String CHECKED_AT = "infusion.vftv.checked_at";

    /** "%s, raised to %s in one run". */
    public static final String FACT_ENCHANT = "infusion.vftv.fact.enchant";

    /** The per-level suffix of an enchanting cost without a context. */
    public static final String FACT_PER_LEVEL = "infusion.vftv.fact.per_level";

    /** "yes". */
    public static final String YES = "infusion.vftv.fact.yes";

    /** "no". */
    public static final String NO = "infusion.vftv.fact.no";

    /** The JEI category title (used by the JEI ticket). */
    public static final String JEI = "jei.vftv.category.infusion";

    /** The lang table. */
    public static final List<LangEntry> TABLE = table();

    private InfusionLang() {}

    /** A sound event's subtitle key. */
    public static String subtitle(Identifier sound) {
        return "subtitles.vftv." + sound.getPath();
    }

    private static List<LangEntry> table() {
        List<LangEntry> rows = new ArrayList<>();
        row(rows, "block.vftv.singing_bowl", 0);
        row(rows, "block.vftv.pedestal", 0);
        row(rows, "item.vftv.echo_rod", 0);
        row(rows, "item.vftv.echo_cap", 0);
        row(rows, "wand_rod.vftv.echo", 0);
        row(rows, "wand_cap.vftv.echo", 0);
        for (String s : List.of("unreadable", "unloaded", "empty", "busy")) {
            row(rows, STATUS + s, 0);
        }
        row(rows, STATUS + "ringing", 1);
        row(rows, STATUS + "ready", 1);
        row(rows, STATUS + "too_many", 1);
        row(rows, STATUS + "no_recipe", 1);
        row(rows, STATUS + "pedestal_unreadable", 3);
        row(rows, STATUS + "promised", 3);
        row(rows, STATUS + "enchant.unstorable", 1);
        row(rows, STATUS + "enchant.unsupported", 2);
        row(rows, STATUS + "enchant.at_max", 2);
        row(rows, STATUS + "enchant.conflict", 2);
        row(rows, HUD + "ready", 1);
        row(rows, HUD + "ringing", 1);
        row(rows, HUD + "stability", 1);
        row(rows, HUD + "breakdown", 7);
        row(rows, HUD + "chance", 2);
        row(rows, HUD + "clean", 2);
        row(rows, HUD + "worst", 3);
        row(rows, HUD + "strike_again", 0);
        row(rows, HUD + "changed", 0);
        row(rows, HUD + "progress", 4);
        row(rows, HUD + "frozen", 2);
        row(rows, HUD + "upsets", 2);
        row(rows, HUD + "missing", 3);
        row(rows, HUD + "paused", 0);
        row(rows, HUD + "waiting", 0);
        row(rows, HUD + "abandon", 1);
        row(rows, HUD + "abandon_confirm", 0);
        for (String m :
                List.of("ringing", "strike_first", "abandon_pending", "not_yours", "too_busy")) {
            row(rows, MESSAGE + m, 0);
        }
        row(rows, MESSAGE + "promised", 3);
        row(rows, MESSAGE + "abandon_confirm", 1);
        row(rows, YES, 0);
        row(rows, NO, 0);
        row(rows, PREVIEW_STATUS + "ringing_only", 0);
        row(rows, PREVIEW_STATUS + "not_ringing", 0);
        row(rows, PREVIEW_STATUS + "no_altar", 0);
        row(rows, PREVIEW_STATUS + "short_now", 0);
        row(rows, PREVIEW_STATUS + "ready", 0);
        row(rows, UNHEARD, 0);
        row(rows, FACT_ENCHANT, 2);
        row(rows, FACT_PER_LEVEL, 0);
        row(rows, CHECKED_AT, 3);
        row(rows, JEI, 0);
        InfusionContent.SOUNDS.forEach(s -> row(rows, subtitle(s), 0));
        return List.copyOf(rows);
    }

    private static void row(List<LangEntry> rows, String key, int args) {
        rows.add(LangEntry.authored(key, args, FILE, LangEntry.Kind.TEMPLATE));
    }
}
