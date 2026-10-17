package com.andrewbrogoff.vftv.core.aspect;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.core.aspect.model.Sounding;
import com.andrewbrogoff.vftv.core.text.model.LangEntry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;

/**
 * The aspect feature's code-defined keys (aspects spec, "Lang keys"), with their final English.
 * Datagen writes them into {@code en_us}; translations go in {@code content/lang/<lang>/
 * aspects.json}.
 */
public final class AspectLang {
    /** The owner's translation file. */
    public static final String FILE = "aspects.json";

    /** An unheard aspect's name in narration and tooltips. */
    public static final String UNHEARD = "aspect.vftv.unheard";

    /** A Shift tooltip row: name, exact amount. */
    public static final String TOOLTIP_ENTRY = "tooltip.vftv.aspects.entry";

    /** An aspect's name and gloss. */
    public static final String TOOLTIP_ASPECT = "tooltip.vftv.aspect";

    /** "Nothing answers." */
    public static final String NOTHING = "hud.vftv.strike.nothing";

    /** "Gained: %s". */
    public static final String GAINED = "hud.vftv.strike.gained";

    /** "In your notes". */
    public static final String KNOWN = "hud.vftv.strike.known";

    /** "Some of it I can't yet hear." */
    public static final String PARTIAL = "hud.vftv.strike.partial";

    /** A node aspect's "%s / %s". */
    public static final String OF = "hud.vftv.strike.of";

    /** "The ink would be lost." */
    public static final String INK = "hud.vftv.strike.ink";

    /** "No answer." */
    public static final String NO_ANSWER = "hud.vftv.sounding.none";

    /** The wands spec's "No answer. No Wellspring stands in this world." */
    public static final String NO_WELLSPRING = "hud.vftv.sounding.no_wellspring";

    /** One aspect narrated: name (or unheard), exact amount. */
    public static final String NARRATE_ASPECT = "narration.vftv.strike.aspect";

    /** "An answer: %s, %s, %s." */
    public static final String NARRATE_SOUNDING = "narration.vftv.sounding";

    /** The bands' words, near to far. */
    public static final List<String> BANDS =
            List.of("hud.vftv.sounding.near", "hud.vftv.sounding.mid", "hud.vftv.sounding.far");

    /** The eight directions, north clockwise. */
    public static final List<String> DIRECTIONS =
            List.of("n", "ne", "e", "se", "s", "sw", "w", "nw").stream()
                    .map(d -> "hud.vftv.sounding.dir." + d)
                    .toList();

    /** Above, below, level. */
    public static final List<String> VERTICALS =
            List.of("hud.vftv.sounding.up", "hud.vftv.sounding.down", "hud.vftv.sounding.level");

    /** The Latin names and glosses, in the spec's order: path, name, gloss. */
    static final List<String[]> NAMES =
            List.of(
                    new String[] {"aer", "Aer", "air, breath, wind"},
                    new String[] {"terra", "Terra", "earth, stone, soil"},
                    new String[] {"ignis", "Ignis", "fire, heat"},
                    new String[] {"aqua", "Aqua", "water, liquid"},
                    new String[] {"ordo", "Ordo", "order, law, pattern"},
                    new String[] {"perditio", "Perditio", "entropy, decay, undoing"},
                    new String[] {"vacuos", "Vacuos", "void, emptiness"},
                    new String[] {"lux", "Lux", "light"},
                    new String[] {"gelum", "Gelum", "cold, ice"},
                    new String[] {"motus", "Motus", "motion"},
                    new String[] {"potentia", "Potentia", "power, energy"},
                    new String[] {"victus", "Victus", "life"},
                    new String[] {"vitreus", "Vitreus", "crystal, glass"},
                    new String[] {"venenum", "Venenum", "poison"},
                    new String[] {"permutatio", "Permutatio", "change, exchange"},
                    new String[] {"sonus", "Sonus", "sound, tone"},
                    new String[] {"metallum", "Metallum", "metal"},
                    new String[] {"tenebrae", "Tenebrae", "darkness"},
                    new String[] {"herba", "Herba", "plants"},
                    new String[] {"bestia", "Bestia", "beasts"},
                    new String[] {"mortuus", "Mortuus", "death"},
                    new String[] {"spiritus", "Spiritus", "soul, spirit"},
                    new String[] {"praecantatio", "Praecantatio", "magic, incantation"},
                    new String[] {"fames", "Fames", "hunger"},
                    new String[] {"instrumentum", "Instrumentum", "tools"},
                    new String[] {"silentium", "Silentium", "silence"},
                    new String[] {"arbor", "Arbor", "wood, trees"},
                    new String[] {"sano", "Sano", "healing"},
                    new String[] {"corpus", "Corpus", "flesh, body"},
                    new String[] {"exanimis", "Exanimis", "undeath"},
                    new String[] {"cognitio", "Cognitio", "mind, knowledge"},
                    new String[] {"humanus", "Humanus", "humankind"},
                    new String[] {"telum", "Telum", "weapons"},
                    new String[] {"tutamen", "Tutamen", "protection"},
                    new String[] {"pannus", "Pannus", "cloth"},
                    new String[] {"machina", "Machina", "machines"},
                    new String[] {"alienis", "Alienis", "the Choir, the otherworldly"});

    /** Every key with its English. */
    public static final List<LangEntry> TABLE = table();

    private AspectLang() {}

    /** A band's word key. */
    public static String band(Sounding.Band band) {
        return switch (band) {
            case NEAR -> BANDS.get(0);
            case MID -> BANDS.get(1);
            case FAR -> BANDS.get(2);
        };
    }

    /** Above, below or level's key. */
    public static String vertical(Sounding.Vertical vertical) {
        return switch (vertical) {
            case UP -> VERTICALS.get(0);
            case DOWN -> VERTICALS.get(1);
            case LEVEL -> VERTICALS.get(2);
        };
    }

    /** An aspect's name: {@code aspect.<ns>.<path>}. */
    public static Component name(ResourceKey<Aspect> aspect) {
        return Component.translatable(
                "aspect."
                        + aspect.identifier().getNamespace()
                        + "."
                        + aspect.identifier().getPath());
    }

    /** An aspect's gloss: {@code aspect.<ns>.<path>.gloss}. */
    public static Component gloss(ResourceKey<Aspect> aspect) {
        return Component.translatable(
                "aspect."
                        + aspect.identifier().getNamespace()
                        + "."
                        + aspect.identifier().getPath()
                        + ".gloss");
    }

    private static List<LangEntry> table() {
        List<LangEntry> t = new ArrayList<>();
        t.add(code("item.vftv.tuning_fork", 0, "Tuning Fork"));
        for (String[] n : NAMES) {
            t.add(code("aspect.vftv." + n[0], 0, n[1]));
            t.add(code("aspect.vftv." + n[0] + ".gloss", 0, n[2]));
        }
        t.add(code(UNHEARD, 0, "something unheard"));
        t.add(
                code(
                        TuningForkItem.TOOLTIP,
                        0,
                        "Carry the fork and right-click an item to strike it."));
        t.add(code(TOOLTIP_ASPECT, 2, "%s: %s"));
        t.add(code(TOOLTIP_ENTRY, 2, "%s %s"));
        t.add(code(NOTHING, 0, "Nothing answers."));
        t.add(code(GAINED, 1, "Gained: %s"));
        t.add(code(KNOWN, 0, "In your notes"));
        t.add(code(PARTIAL, 0, "Some of it I can't yet hear."));
        t.add(code(OF, 2, "%s / %s"));
        t.add(code(INK, 0, "The ink would be lost."));
        t.add(code(NO_ANSWER, 0, "No answer."));
        String[] bands = {"near", "farther", "far"};
        for (int i = 0; i < bands.length; i++) {
            t.add(code(BANDS.get(i), 0, bands[i]));
        }
        String[] directions = {
            "north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"
        };
        for (int i = 0; i < directions.length; i++) {
            t.add(code(DIRECTIONS.get(i), 0, directions[i]));
        }
        String[] verticals = {"above", "below", "level"};
        for (int i = 0; i < verticals.length; i++) {
            t.add(code(VERTICALS.get(i), 0, verticals[i]));
        }
        t.add(code(NARRATE_ASPECT, 2, "%s: %s"));
        t.add(code(NARRATE_SOUNDING, 3, "An answer: %s, %s, %s."));
        t.add(code("subtitles.vftv.fork.strike", 0, "Tuning fork rings"));
        t.add(code("subtitles.vftv.fork.answer", 0, "A node answers"));
        t.add(code("subtitles.vftv.fork.discover", 0, "Something is understood"));
        return List.copyOf(t);
    }

    private static LangEntry code(String key, int args, String english) {
        return LangEntry.codeDefined(key, args, FILE, english);
    }
}
