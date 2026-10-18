package com.andrewbrogoff.vftv.core.crucible;

import com.andrewbrogoff.vftv.core.text.model.LangEntry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;

/**
 * The crucible's lang keys (crucible spec, "Lang keys"). Every key is authored in {@code
 * content/lang/<lang>/crucible.json}; this table gives the validator their argument counts.
 */
public final class CrucibleLang {
    /** The owner's file. */
    public static final String FILE = "crucible.json";

    /** HUD header, boiling: total, capacity. */
    public static final String HUD_BOILING = "hud.vftv.crucible.boiling";

    /** HUD header, cold: total, capacity. */
    public static final String HUD_COLD = "hud.vftv.crucible.cold";

    /** HUD header, dry: total, capacity. */
    public static final String HUD_DRY = "hud.vftv.crucible.dry";

    /** HUD header, unreadable. */
    public static final String HUD_UNREADABLE = "hud.vftv.crucible.unreadable";

    /** HUD spill line: flux. */
    public static final String HUD_SPILL = "hud.vftv.crucible.spill";

    /** HUD more line: entries not drawn. */
    public static final String HUD_MORE = "hud.vftv.crucible.more";

    /** An aspect the reader hasn't discovered. */
    public static final String UNHEARD = "crucible.vftv.unheard";

    /** The preview's context: x, y, z. */
    public static final String CHECKED_AT = "crucible.vftv.checked_at";

    /** The {@code status} fact's prefix: {@code .ready}, {@code .short} and the absent ones. */
    public static final String STATUS = "crucible.vftv.status.";

    /** The JEI category title (used by the JEI ticket). */
    public static final String JEI = "jei.vftv.category.crucible";

    /** The lang table. */
    public static final List<LangEntry> TABLE = table();

    private CrucibleLang() {}

    /** A sound event's subtitle key. */
    public static String subtitle(Identifier sound) {
        return "subtitles.vftv." + sound.getPath();
    }

    private static List<LangEntry> table() {
        List<LangEntry> rows = new ArrayList<>();
        row(rows, "block.vftv.crucible", 0);
        row(rows, "item.vftv.resonant_brass_ingot", 0);
        row(rows, HUD_BOILING, 2);
        row(rows, HUD_COLD, 2);
        row(rows, HUD_DRY, 2);
        row(rows, HUD_UNREADABLE, 0);
        row(rows, HUD_SPILL, 1);
        row(rows, HUD_MORE, 1);
        for (CrucibleRefusal refusal : CrucibleRefusal.values()) {
            row(rows, refusal.key(), refusal.args());
        }
        row(rows, UNHEARD, 0);
        row(rows, CHECKED_AT, 3);
        row(rows, STATUS + "ready", 0);
        row(rows, STATUS + "short", 1);
        for (String absent : List.of("dry", "cold", "no_crucible", "unreadable")) {
            row(rows, STATUS + absent, 0);
        }
        row(rows, JEI, 0);
        CrucibleContent.SOUNDS.forEach(s -> row(rows, subtitle(s), 0));
        return List.copyOf(rows);
    }

    private static void row(List<LangEntry> rows, String key, int args) {
        rows.add(LangEntry.authored(key, args, FILE, LangEntry.Kind.TEMPLATE));
    }
}
