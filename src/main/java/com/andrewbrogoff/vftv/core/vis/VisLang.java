package com.andrewbrogoff.vftv.core.vis;

import com.andrewbrogoff.vftv.core.text.model.LangEntry;
import java.util.ArrayList;
import java.util.List;

/**
 * The wands spec's lang keys (wands spec, "Lang keys"): authored in {@code
 * content/lang/<lang>/wands.json}, each required in {@code en_us} with its placeholder count. The
 * sounding's no-Wellspring line is the aspect feature's code-defined key.
 */
public final class VisLang {
    /** The owner's file. */
    public static final String FILE = "wands.json";

    /** An inert node's type, "Unknown". */
    public static final String UNKNOWN_TYPE = "node.vftv.unknown_type";

    /** The tap refusals and stops. */
    public static final String TAP_UNLEARNED = "message.vftv.tap.unlearned";

    /** "This wand is missing a part." */
    public static final String TAP_INERT = "message.vftv.tap.inert";

    /** "It rings in a key I don't know." */
    public static final String TAP_UNKNOWN = "message.vftv.tap.unknown";

    /** "Nothing here I can draw." */
    public static final String TAP_NOTHING = "message.vftv.tap.nothing";

    /** "My wand can hold no more of this." */
    public static final String TAP_FULL = "message.vftv.tap.full";

    /** "It has nothing left to give." */
    public static final String TAP_EMPTY = "message.vftv.tap.empty";

    /** The spend's unlearned refusal. */
    public static final String SPEND_UNLEARNED = "message.vftv.spend.unlearned";

    /** The spend's inert refusal. */
    public static final String SPEND_INERT = "message.vftv.spend.inert";

    /** "Not enough %s." */
    public static final String SPEND_SHORT = "message.vftv.spend.short";

    /** "%s rod, %s caps". */
    public static final String TOOLTIP_PARTS = "tooltip.vftv.wand.parts";

    /** "Holds %s of each". */
    public static final String TOOLTIP_CAPACITY = "tooltip.vftv.wand.capacity";

    /** "Costs ×%s". */
    public static final String TOOLTIP_COST = "tooltip.vftv.wand.cost";

    /** "%s: %s / %s". */
    public static final String TOOLTIP_POOL = "tooltip.vftv.wand.pool";

    /** "Hold Shift to read it". */
    public static final String TOOLTIP_MORE = "tooltip.vftv.wand.more";

    /** "This wand was never finished." */
    public static final String TOOLTIP_UNFINISHED = "tooltip.vftv.wand.unfinished";

    /** "Its rod is missing: %s". */
    public static final String TOOLTIP_MISSING_ROD = "tooltip.vftv.wand.missing_rod";

    /** "Its cap is missing: %s". */
    public static final String TOOLTIP_MISSING_CAP = "tooltip.vftv.wand.missing_cap";

    /** The HUD's unfinished line. */
    public static final String HUD_UNFINISHED = "hud.vftv.vis.unfinished";

    /** The HUD's missing-part line. */
    public static final String HUD_MISSING = "hud.vftv.vis.missing";

    /** The book's starter fact: present. */
    public static final String FACT_STARTER_PRESENT = "fact.vftv.vis.starter.present";

    /** The book's starter fact: absent. */
    public static final String FACT_STARTER_ABSENT = "fact.vftv.vis.starter.absent";

    /** {@code /vftv node remove}: removed. */
    public static final String CMD_REMOVED = "commands.vftv.node.removed";

    /** {@code /vftv node remove}: unknown id. */
    public static final String CMD_UNKNOWN = "commands.vftv.node.unknown";

    /** {@code /vftv node starter}: placed. */
    public static final String CMD_STARTER_PLACED = "commands.vftv.node.starter_placed";

    /** {@code /vftv node starter}: one already stands. */
    public static final String CMD_STARTER_EXISTS = "commands.vftv.node.starter_exists";

    /** {@code /vftv node starter}: not the overworld. */
    public static final String CMD_STARTER_OVERWORLD = "commands.vftv.node.starter_overworld";

    /** {@code /vftv node starter}: the position can't hold it. */
    public static final String CMD_STARTER_BLOCKED = "commands.vftv.node.starter_blocked";

    /** Node commands while {@code vftv_nodes} is unreadable. */
    public static final String CMD_UNREADABLE = "commands.vftv.node.unreadable";

    /** {@code vis_hud.x}'s comment, verbatim from the configuration spec; also its tooltip. */
    public static final String HUD_X_COMMENT =
            "Left edge of the vis HUD, in GUI pixels. Range: 0 to 4096. Default: 4.";

    /** {@code vis_hud.y}'s comment. */
    public static final String HUD_Y_COMMENT =
            "Top edge of the vis HUD, in GUI pixels. Range: 0 to 4096. Default: 4.";

    /** Every key with its placeholder count. */
    public static final List<LangEntry> TABLE = table();

    private VisLang() {}

    private static List<LangEntry> table() {
        List<LangEntry> t = new ArrayList<>();
        for (String name :
                List.of(
                        "item.vftv.wand",
                        "item.vftv.chime_rod",
                        "item.vftv.resonant_brass_cap",
                        "item.vftv.bell_jar",
                        "block.vftv.node",
                        "block.vftv.node_tap",
                        "block.vftv.vis_relay",
                        "block.vftv.wand_cradle",
                        "node.vftv.clarity.faint",
                        "node.vftv.clarity.steady",
                        "node.vftv.clarity.clear",
                        UNKNOWN_TYPE,
                        "attribute.name.vftv.vis_discount",
                        TOOLTIP_MORE,
                        TOOLTIP_UNFINISHED,
                        "tooltip.vftv.bell_jar.empty",
                        HUD_UNFINISHED,
                        TAP_UNLEARNED,
                        TAP_INERT,
                        TAP_UNKNOWN,
                        TAP_NOTHING,
                        TAP_FULL,
                        TAP_EMPTY,
                        SPEND_UNLEARNED,
                        SPEND_INERT,
                        "message.vftv.jar.unlearned",
                        "message.vftv.jar.anchored",
                        "message.vftv.jar.unknown",
                        "message.vftv.jar.stale",
                        "message.vftv.jar.no_room",
                        "message.vftv.jar.crowded",
                        "message.vftv.relay.no_node",
                        "message.vftv.relay.taken",
                        "status.vftv.node_tap.idle",
                        "status.vftv.vis_relay.unlinked",
                        "status.vftv.wand_cradle.empty",
                        "status.vftv.wand_cradle.full",
                        "status.vftv.wand_cradle.idle",
                        NodeService.STARTER_MISSING,
                        FACT_STARTER_PRESENT,
                        FACT_STARTER_ABSENT,
                        CMD_STARTER_OVERWORLD,
                        CMD_STARTER_BLOCKED,
                        CMD_UNREADABLE)) {
            t.add(authored(name, 0));
        }
        t.add(authored(TOOLTIP_PARTS, 2));
        t.add(authored(TOOLTIP_CAPACITY, 1));
        t.add(authored(TOOLTIP_COST, 1));
        t.add(authored(TOOLTIP_POOL, 3));
        t.add(authored(TOOLTIP_MISSING_ROD, 1));
        t.add(authored(TOOLTIP_MISSING_CAP, 1));
        t.add(authored("tooltip.vftv.bell_jar.holds", 3));
        t.add(authored(HUD_MISSING, 1));
        t.add(authored(SPEND_SHORT, 1));
        t.add(authored("message.vftv.jar.too_close", 1));
        t.add(authored("message.vftv.relay.too_many", 2));
        t.add(authored("status.vftv.network", 4));
        t.add(authored("status.vftv.node_tap.listening", 5));
        t.add(authored("status.vftv.wand_cradle.charging", 2));
        t.add(authored(CMD_REMOVED, 1));
        t.add(authored(CMD_UNKNOWN, 1));
        t.add(authored(CMD_STARTER_PLACED, 2));
        t.add(authored(CMD_STARTER_EXISTS, 1));
        for (var sound : VisContent.SOUNDS) {
            t.add(authored("subtitles.vftv." + sound.getPath(), 0));
        }
        t.add(pattern("wand_rod\\.[a-z0-9_.-]+\\.[a-z0-9_./-]+"));
        t.add(pattern("wand_cap\\.[a-z0-9_.-]+\\.[a-z0-9_./-]+"));
        t.add(pattern("node_type\\.[a-z0-9_.-]+\\.[a-z0-9_./-]+"));
        return List.copyOf(t);
    }

    private static LangEntry authored(String key, int args) {
        return LangEntry.authored(key, args, FILE, LangEntry.Kind.TEMPLATE);
    }

    private static LangEntry pattern(String regex) {
        return LangEntry.pattern(regex, 0, FILE, LangEntry.Kind.TEMPLATE);
    }
}
