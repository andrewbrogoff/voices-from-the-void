package com.andrewbrogoff.vftv.core.aspect;

import static com.andrewbrogoff.vftv.api.aspect.AspectKeys.*;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.aspect.HiddenPrimal;
import com.andrewbrogoff.vftv.api.research.ResearchKeys;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * The 37 built-in aspects (aspects spec, "The aspects" and "Colours"), in the spec's table order:
 * each colour is its palette ramp's base step, and datagen writes them as {@code vftv:aspect}
 * entries. Datapacks may override them, never delete them.
 */
public final class BuiltinAspects {
    /** The research entry whose Learn reveals Vacuos (research spec, "The Vacuos reveal"). */
    public static final Identifier VACUOS_REVEAL = ResearchKeys.SILENCE_BENEATH.identifier();

    // Ramp base steps (art style §2): aspect families take step 1; the older ramps the step below.
    private static final int GALE = 0xb8d4ec;
    private static final int LOAM = 0x9a8838;
    private static final int EMBER = 0xe0862e;
    private static final int TIDE = 0x4a78c4;
    private static final int LUMEN = 0xf0dc64;
    private static final int BLIGHT = 0x9c4886;
    private static final int VITAL = 0xc8445c;
    private static final int VERDURE = 0x66a64e;
    private static final int AMETHYST = 0x8f74c4; // amethyst2
    private static final int MAUVE = 0x7d7189; // mauve2
    private static final int BRASS = 0xb89459; // brass3
    private static final int INK = 0x343849; // ink3
    private static final int HUSH = 0x9c99a0; // hush2

    /** Every built-in, in table order. */
    public static final Map<ResourceKey<Aspect>, AspectEntry> ALL = build();

    private BuiltinAspects() {}

    private static Map<ResourceKey<Aspect>, AspectEntry> build() {
        Map<ResourceKey<Aspect>, AspectEntry> m = new LinkedHashMap<>();
        primal(m, AER, GALE);
        primal(m, TERRA, LOAM);
        primal(m, IGNIS, EMBER);
        primal(m, AQUA, TIDE);
        primal(m, ORDO, LUMEN);
        primal(m, PERDITIO, BLIGHT);
        m.put(
                VACUOS,
                new AspectEntry(
                        INK,
                        List.of(),
                        Optional.empty(),
                        Optional.of(new HiddenPrimal(List.of(AER, PERDITIO), VACUOS_REVEAL)),
                        Optional.empty()));
        compound(m, LUX, LUMEN, AER, IGNIS);
        compound(m, GELUM, TIDE, AQUA, ORDO);
        compound(m, MOTUS, GALE, AER, AQUA);
        compound(m, POTENTIA, EMBER, IGNIS, ORDO);
        compound(m, VICTUS, VITAL, AQUA, TERRA);
        compound(m, VITREUS, LUMEN, TERRA, ORDO);
        compound(m, VENENUM, BLIGHT, AQUA, PERDITIO);
        compound(m, PERMUTATIO, BLIGHT, ORDO, PERDITIO);
        compound(m, SONUS, GALE, AER, ORDO);
        compound(m, METALLUM, BRASS, TERRA, IGNIS);
        compound(m, TENEBRAE, INK, TERRA, VACUOS);
        compound(m, HERBA, VERDURE, VICTUS, LUX);
        compound(m, BESTIA, VITAL, VICTUS, MOTUS);
        compound(m, MORTUUS, BLIGHT, VICTUS, PERDITIO);
        compound(m, SPIRITUS, AMETHYST, VICTUS, SONUS);
        compound(m, PRAECANTATIO, AMETHYST, POTENTIA, SONUS);
        compound(m, FAMES, INK, VICTUS, VACUOS);
        compound(m, INSTRUMENTUM, BRASS, METALLUM, ORDO);
        compound(m, SILENTIUM, HUSH, SONUS, VACUOS);
        compound(m, ARBOR, VERDURE, HERBA, TERRA);
        compound(m, SANO, VITAL, HERBA, ORDO);
        compound(m, CORPUS, VITAL, BESTIA, TERRA);
        compound(m, EXANIMIS, BLIGHT, MORTUUS, MOTUS);
        compound(m, COGNITIO, AMETHYST, SPIRITUS, ORDO);
        compound(m, HUMANUS, VITAL, SPIRITUS, BESTIA);
        compound(m, TELUM, BRASS, INSTRUMENTUM, PERDITIO);
        compound(m, TUTAMEN, BRASS, INSTRUMENTUM, VITREUS);
        compound(m, PANNUS, MAUVE, BESTIA, INSTRUMENTUM);
        compound(m, MACHINA, BRASS, INSTRUMENTUM, POTENTIA);
        compound(m, ALIENIS, INK, SPIRITUS, VACUOS);
        return Collections.unmodifiableMap(m);
    }

    private static void primal(
            Map<ResourceKey<Aspect>, AspectEntry> m, ResourceKey<Aspect> key, int color) {
        m.put(key, AspectEntry.of(color, List.of()));
    }

    private static void compound(
            Map<ResourceKey<Aspect>, AspectEntry> m,
            ResourceKey<Aspect> key,
            int color,
            ResourceKey<Aspect> a,
            ResourceKey<Aspect> b) {
        m.put(key, AspectEntry.of(color, List.of(a, b)));
    }
}
