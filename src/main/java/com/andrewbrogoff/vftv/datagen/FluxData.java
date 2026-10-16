package com.andrewbrogoff.vftv.datagen;

import com.andrewbrogoff.vftv.core.Vftv;
import com.andrewbrogoff.vftv.core.flux.FluxRulesService;
import com.andrewbrogoff.vftv.core.flux.HushConfig;
import com.andrewbrogoff.vftv.core.flux.model.FluxTuning;
import com.andrewbrogoff.vftv.core.text.model.LangEntry;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * The flux feature's data generation: override the {@link FeatureData} hooks it needs. Listed once
 * in {@link VftvData#FEATURES}.
 */
final class FluxData implements FeatureData {
    /** The configuration spec's file for these keys' translations. */
    private static final String CONFIG = "config.json";

    /**
     * The {@code hush} section and keys 13-15 of the server config, with their tooltips (the
     * comments, verbatim). The section key is shared with the client file's {@code hush} section.
     */
    @Override
    public List<LangEntry> lang() {
        return List.of(
                LangEntry.codeDefined("options.vftv.hush", 0, CONFIG, "The Hush"),
                LangEntry.codeDefined("options.vftv.hush.spread", 0, CONFIG, "Hush spreads"),
                LangEntry.codeDefined(
                        "options.vftv.hush.spread.tooltip", 0, CONFIG, HushConfig.SPREAD_COMMENT),
                LangEntry.codeDefined("options.vftv.hush.hollows", 0, CONFIG, "Hollows rise"),
                LangEntry.codeDefined(
                        "options.vftv.hush.hollows.tooltip", 0, CONFIG, HushConfig.HOLLOWS_COMMENT),
                LangEntry.codeDefined(
                        "options.vftv.hush.unattended", 0, CONFIG, "Spread without players"),
                LangEntry.codeDefined(
                        "options.vftv.hush.unattended.tooltip",
                        0,
                        CONFIG,
                        HushConfig.UNATTENDED_COMMENT));
    }

    /** {@code data/vftv/tuning/hush.json}, every key at its default. */
    @Override
    public void providers(GatherDataEvent event) {
        event.createProvider(TuningProvider::new);
    }

    private record TuningProvider(PackOutput output) implements DataProvider {
        @Override
        public CompletableFuture<?> run(CachedOutput cache) {
            JsonObject json = new JsonObject();
            FluxTuning.KEYS.forEach(key -> json.addProperty(key.name(), key.fallback()));
            return DataProvider.saveStable(
                    cache,
                    json,
                    output.getOutputFolder(PackOutput.Target.DATA_PACK)
                            .resolve(Vftv.MOD_ID)
                            .resolve(FluxRulesService.TUNING_PATH));
        }

        @Override
        public String getName() {
            return "vftv hush tuning";
        }
    }
}
