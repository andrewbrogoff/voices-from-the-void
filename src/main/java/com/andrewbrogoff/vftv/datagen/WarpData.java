package com.andrewbrogoff.vftv.datagen;

import com.andrewbrogoff.vftv.core.Vftv;
import com.andrewbrogoff.vftv.core.text.model.LangEntry;
import com.andrewbrogoff.vftv.core.warp.WarpCommands;
import com.andrewbrogoff.vftv.core.warp.WarpConfig;
import com.andrewbrogoff.vftv.core.warp.WarpRulesService;
import com.andrewbrogoff.vftv.core.warp.WarpService;
import com.andrewbrogoff.vftv.core.warp.model.WarpTuning;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * The warp feature's data generation: override the {@link FeatureData} hooks it needs. Listed once
 * in {@link VftvData#FEATURES}.
 */
final class WarpData implements FeatureData {
    /** The configuration spec's file for the option keys' translations. */
    private static final String CONFIG = "config.json";

    /** The warp spec's lang file. */
    private static final String WARP = "warp.json";

    /**
     * The {@code warp} sections and keys 2 and 10-12 of the server config, with their tooltips (the
     * comments, verbatim); and the warp spec's keys this feature uses so far.
     */
    @Override
    public List<LangEntry> lang() {
        return List.of(
                LangEntry.codeDefined("options.vftv.warp", 0, CONFIG, "Warp"),
                LangEntry.codeDefined("options.vftv.warp.voices", 0, CONFIG, "The Voices"),
                LangEntry.codeDefined("options.vftv.warp.voices.enabled", 0, CONFIG, "Voices"),
                LangEntry.codeDefined(
                        "options.vftv.warp.voices.enabled.tooltip",
                        0,
                        CONFIG,
                        WarpConfig.VOICES_COMMENT),
                LangEntry.codeDefined("options.vftv.warp.tax", 0, CONFIG, "Warp vis tax"),
                LangEntry.codeDefined(
                        "options.vftv.warp.tax.tooltip", 0, CONFIG, WarpConfig.TAX_COMMENT),
                LangEntry.codeDefined("options.vftv.warp.amplifier", 0, CONFIG, "Warp amplifier"),
                LangEntry.codeDefined(
                        "options.vftv.warp.amplifier.tooltip",
                        0,
                        CONFIG,
                        WarpConfig.AMPLIFIER_COMMENT),
                LangEntry.codeDefined("options.vftv.warp.gear", 0, CONFIG, "Gear warp"),
                LangEntry.codeDefined(
                        "options.vftv.warp.gear.tooltip", 0, CONFIG, WarpConfig.GEAR_COMMENT),
                LangEntry.authored(WarpCommands.GET, 9, WARP, LangEntry.Kind.TEMPLATE),
                LangEntry.authored(WarpCommands.SET, 2, WARP, LangEntry.Kind.TEMPLATE),
                LangEntry.authored(WarpService.GEAR_TOOLTIP, 1, WARP, LangEntry.Kind.TEMPLATE));
    }

    /** {@code data/vftv/tuning/warp.json}, every key at its default. */
    @Override
    public void providers(GatherDataEvent event) {
        event.createProvider(TuningProvider::new);
    }

    private record TuningProvider(PackOutput output) implements DataProvider {
        @Override
        public CompletableFuture<?> run(CachedOutput cache) {
            return DataProvider.saveStable(
                    cache,
                    WarpTuning.toJson(WarpTuning.DEFAULTS, false),
                    output.getOutputFolder(PackOutput.Target.DATA_PACK)
                            .resolve(Vftv.MOD_ID)
                            .resolve(WarpRulesService.TUNING_PATH));
        }

        @Override
        public String getName() {
            return "vftv warp tuning";
        }
    }
}
