package com.andrewbrogoff.vftv.core.hush;

import com.andrewbrogoff.vftv.api.hush.HushKeys;
import com.andrewbrogoff.vftv.api.hush.HushedForm;
import com.andrewbrogoff.vftv.core.text.model.LangEntry;
import java.util.ArrayList;
import java.util.List;

/**
 * The Hush's lang keys (Hush spec, "Lang keys"). Every key is authored in {@code
 * content/lang/<lang>/hush.json}; this table gives the validator their argument counts.
 */
public final class HushLang {
    /** The owner's file. */
    public static final String FILE = "hush.json";

    /** HUD line 1: no rules or no status yet. */
    public static final String HUD_UNKNOWN = "hud.vftv.hush.unknown";

    /** HUD line 1: unreadable. */
    public static final String HUD_UNREADABLE = "hud.vftv.hush.unreadable";

    /** HUD line 1: nothing pools. */
    public static final String HUD_NONE = "hud.vftv.hush.none";

    /** HUD line 1: below the threshold: flux, threshold. */
    public static final String HUD_QUIET = "hud.vftv.hush.quiet";

    /** HUD line 1: hushing: flux, blocks. */
    public static final String HUD_HUSHING = "hud.vftv.hush.hushing";

    /** HUD line 2: x, y, z. */
    public static final String HUD_SEEP = "hud.vftv.hush.seep";

    /** The command keys' prefix. */
    public static final String COMMANDS = "commands.vftv.flux.";

    /** The {@code hush} switch facts' prefix: {@code .on}, {@code .off}. */
    public static final String FACT = "hush.vftv.fact.";

    /** The lang table. */
    public static final List<LangEntry> TABLE = table();

    private HushLang() {}

    /** A sound event's subtitle key. */
    public static String subtitle(net.minecraft.resources.Identifier sound) {
        return "subtitles.vftv." + sound.getPath();
    }

    private static List<LangEntry> table() {
        List<LangEntry> rows = new ArrayList<>();
        for (HushedForm f : HushKeys.FORMS) {
            row(rows, "block.vftv." + f.form().getPath(), 0);
        }
        row(rows, "block.vftv.pealing_bell", 0);
        row(rows, "item.vftv.clearing_bell", 0);
        row(rows, "item.vftv.hush_ash", 0);
        row(rows, "entity.vftv.hollow", 0);
        row(rows, "node_type.vftv.hushed", 0);
        row(rows, HUD_UNKNOWN, 0);
        row(rows, HUD_UNREADABLE, 0);
        row(rows, HUD_NONE, 0);
        row(rows, HUD_QUIET, 2);
        row(rows, HUD_HUSHING, 2);
        row(rows, HUD_SEEP, 3);
        row(rows, HushBells.NOTHING, 0);
        row(rows, HushBells.STATUS, 2);
        row(rows, HushBells.STATUS_UNREADABLE, 0);
        row(rows, COMMANDS + "get", 9);
        row(rows, COMMANDS + "added", 4);
        row(rows, COMMANDS + "cleared", 1);
        row(rows, "commands.vftv.hush.restored", 2);
        row(rows, "commands.vftv.hush.restored_done", 1);
        for (String k : List.of("repaired", "not_loaded", "not_unreadable")) {
            row(rows, COMMANDS + k, 0);
        }
        for (String k : List.of("readable", "unreadable", "none", "saturated", "waiting")) {
            row(rows, COMMANDS + "status." + k, 0);
        }
        row(rows, "options.vftv.hush.muffle", 0);
        row(rows, "options.vftv.hush.hud", 0);
        row(rows, FACT + "on", 0);
        row(rows, FACT + "off", 0);
        HushContent.SOUNDS.forEach(s -> row(rows, subtitle(s), 0));
        return List.copyOf(rows);
    }

    private static void row(List<LangEntry> rows, String key, int args) {
        rows.add(LangEntry.authored(key, args, FILE, LangEntry.Kind.TEMPLATE));
    }
}
