package com.andrewbrogoff.vftv.datagen;

import com.andrewbrogoff.vftv.core.Vftv;
import com.andrewbrogoff.vftv.core.common.model.Schema;
import com.andrewbrogoff.vftv.core.common.model.StrictJson;
import com.andrewbrogoff.vftv.core.text.model.LangEntry;
import com.andrewbrogoff.vftv.core.warp.StillingDraught;
import com.andrewbrogoff.vftv.core.warp.WarpCommands;
import com.andrewbrogoff.vftv.core.warp.WarpConfig;
import com.andrewbrogoff.vftv.core.warp.WarpContent;
import com.andrewbrogoff.vftv.core.warp.WarpRulesService;
import com.andrewbrogoff.vftv.core.warp.WarpService;
import com.andrewbrogoff.vftv.core.warp.client.WarpClientConfig;
import com.andrewbrogoff.vftv.core.warp.model.VoiceContent;
import com.andrewbrogoff.vftv.core.warp.model.WarpTuning;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import org.jspecify.annotations.Nullable;

/**
 * The warp feature's data generation: override the {@link FeatureData} hooks it needs. Listed once
 * in {@link VftvData#FEATURES}.
 */
final class WarpData implements FeatureData {
    /** The configuration spec's file for the option keys' translations. */
    private static final String CONFIG = "config.json";

    /** The warp spec's lang file. */
    private static final String WARP = "warp.json";

    /** The Voice line texts' lang file. */
    private static final String VOICES = "voices.json";

    /** The authored keys of {@code warp.json} with their argument counts. */
    private static final Map<String, Integer> AUTHORED = authored();

    /**
     * The {@code warp} and {@code voices} option sections and keys with their tooltips (the
     * comments, verbatim); every key of the warp spec's "Lang keys"; and the Voice lines.
     */
    @Override
    public List<LangEntry> lang() {
        List<LangEntry> t = new ArrayList<>();
        t.add(LangEntry.codeDefined("options.vftv.warp", 0, CONFIG, "Warp"));
        t.add(LangEntry.codeDefined("options.vftv.warp.voices", 0, CONFIG, "The Voices"));
        option(t, "warp.voices.enabled", "Voices", WarpConfig.VOICES_COMMENT);
        for (WarpConfig.TypeSwitch s : WarpConfig.TYPE_SWITCHES) {
            option(t, "warp.voices." + s.key(), s.name(), s.comment());
        }
        option(t, "warp.tax", "Warp vis tax", WarpConfig.TAX_COMMENT);
        option(t, "warp.amplifier", "Warp amplifier", WarpConfig.AMPLIFIER_COMMENT);
        option(t, "warp.gear", "Gear warp", WarpConfig.GEAR_COMMENT);
        t.add(LangEntry.codeDefined("options.vftv.voices", 0, CONFIG, "The Voices"));
        WarpClientConfig.COMMENTS.forEach(
                (key, comment) ->
                        t.add(
                                LangEntry.codeDefined(
                                        "options.vftv.voices." + key + ".tooltip",
                                        0,
                                        CONFIG,
                                        comment)));
        AUTHORED.forEach(
                (key, args) -> t.add(LangEntry.authored(key, args, WARP, LangEntry.Kind.TEMPLATE)));
        t.add(
                LangEntry.pattern(
                        "voice\\.vftv\\.(band[0-3]|insight|offer|joining|silencing)_[0-9]+",
                        0,
                        VOICES,
                        LangEntry.Kind.TEMPLATE));
        return List.copyOf(t);
    }

    private static void option(List<LangEntry> t, String key, String name, String comment) {
        t.add(LangEntry.codeDefined("options.vftv." + key, 0, CONFIG, name));
        t.add(LangEntry.codeDefined("options.vftv." + key + ".tooltip", 0, CONFIG, comment));
    }

    private static Map<String, Integer> authored() {
        Map<String, Integer> keys = new TreeMap<>();
        for (String k :
                List.of(
                        "item.vftv.stilling_draught",
                        "entity.vftv.apparition",
                        "message.vftv.warp.listen_closed",
                        "message.vftv.warp.listen_place",
                        "message.vftv.warp.nothing_to_still",
                        "message.vftv.warp.offer_gone",
                        "message.vftv.warp.offer_full",
                        "message.vftv.warp.offer_cannot",
                        "ledger.vftv.listen.button",
                        "warp.vftv.fact.none",
                        "warp.vftv.fact.clipped",
                        "warp.vftv.fact.on",
                        "warp.vftv.fact.off",
                        "ledger.vftv.warp.title",
                        "ledger.vftv.warp.silenced",
                        "ledger.vftv.offer.title",
                        "ledger.vftv.offer.none",
                        "ledger.vftv.offer.expired",
                        "ledger.vftv.offer.accept",
                        "ledger.vftv.offer.refuse",
                        "options.vftv.voices.text",
                        "options.vftv.voices.colour_shift",
                        "options.vftv.voices.phantoms",
                        "options.vftv.voices.phantom_sounds")) {
            keys.put(k, 0);
        }
        for (String k :
                List.of(
                        "message.vftv.warp.listen_cooldown",
                        "narration.vftv.listen",
                        "warp.vftv.kind.temporary",
                        "warp.vftv.kind.lasting",
                        "warp.vftv.kind.permanent",
                        "ledger.vftv.warp.temporary",
                        "ledger.vftv.warp.lasting",
                        "ledger.vftv.warp.gear",
                        "ledger.vftv.warp.total",
                        "ledger.vftv.warp.next_band",
                        "ledger.vftv.warp.still",
                        "ledger.vftv.warp.joined",
                        "ledger.vftv.offer.cost",
                        "ledger.vftv.offer.expires",
                        WarpService.GEAR_TOOLTIP)) {
            keys.put(k, 1);
        }
        for (String k :
                List.of(
                        StillingDraught.TOOLTIP,
                        "voice.vftv.insight_gain",
                        "ledger.vftv.warp.permanent",
                        "ledger.vftv.warp.band",
                        "ledger.vftv.warp.counter",
                        "ledger.vftv.warp.chance",
                        "narration.vftv.offer",
                        WarpCommands.SET,
                        WarpCommands.FIRED)) {
            keys.put(k, 2);
        }
        keys.put("ledger.vftv.offer.effect", 3);
        keys.put("ledger.vftv.warp.chance_parts", 4);
        keys.put(WarpCommands.GET, 9);
        for (Identifier sound : WarpContent.SOUNDS) {
            keys.put("subtitles.vftv." + sound.getPath(), 0);
        }
        return keys;
    }

    /**
     * {@code data/vftv/tuning/warp.json}, every key at its default; and the authored content: the
     * Voice events, the bargains, the Stilling Draught, the phantoms and the lines.
     */
    @Override
    public void providers(GatherDataEvent event) {
        event.createProvider(TuningProvider::new);
        event.createProvider(VoicesProvider::new);
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

    /**
     * {@code content/warp}, with the lexicon and {@code voices.json} it is checked against: every
     * schema's rules, then the outputs.
     */
    private static final class VoicesProvider extends ContentProvider {
        VoicesProvider(PackOutput output) {
            super(output, "warp", VoiceContent.WARP_SCHEMAS);
        }

        @Override
        protected CompletableFuture<?> generate(List<Envelope> envelopes, CachedOutput cache) {
            Path root = Path.of(System.getProperty(CONTENT_DIR));
            Map<String, JsonObject> bySchema = new TreeMap<>();
            List<String> problems = new ArrayList<>();
            for (Envelope e : envelopes) {
                if (bySchema.put(e.schema().id(), e.json().getAsJsonObject()) != null) {
                    problems.add("warp/" + e.path() + ": a second " + e.schema().id());
                }
            }
            for (Schema s : VoiceContent.WARP_SCHEMAS) {
                if (!bySchema.containsKey(s.id())) {
                    problems.add("warp: no " + s.id() + " file");
                }
            }
            JsonObject lexicon =
                    one(root.resolve("lexicon/void_tongue.json"), VoiceContent.LEXICON, problems);
            JsonObject voices = lang(root.resolve("lang/en_us/" + VOICES), problems);
            if (!problems.isEmpty()) {
                throw new IllegalStateException(
                        "Rejected warp content:\n" + String.join("\n", problems));
            }
            JsonObject script = bySchema.get(VoiceContent.VOICE_LINES.id());
            Map<String, String> texts = new TreeMap<>();
            voices.asMap().forEach((k, v) -> texts.put(k, v.getAsString()));
            problems.addAll(VoiceContent.scriptRules(script, lexicon, texts));
            for (JsonElement sound :
                    bySchema.get(VoiceContent.PHANTOMS.id()).getAsJsonArray("sounds")) {
                if (!BuiltInRegistries.SOUND_EVENT.containsKey(
                        Identifier.parse(sound.getAsString()))) {
                    problems.add("warp/phantoms.json: unknown sound " + sound.getAsString());
                }
            }
            JsonObject bootstrap = bySchema.get(VoiceContent.BOOTSTRAP.id());
            bootstrap
                    .getAsJsonArray("items")
                    .forEach(
                            i -> {
                                String item = i.getAsJsonObject().get("item").getAsString();
                                if (!BuiltInRegistries.ITEM.containsKey(Identifier.parse(item))) {
                                    problems.add("warp/bootstrap.json: unknown item " + item);
                                }
                            });
            bootstrap
                    .getAsJsonArray("aspects")
                    .forEach(
                            a ->
                                    a.getAsJsonObject()
                                            .getAsJsonArray("sources")
                                            .forEach(
                                                    s -> {
                                                        String id = s.getAsString();
                                                        if (!id.startsWith("#")
                                                                && !BuiltInRegistries.ITEM
                                                                        .containsKey(
                                                                                Identifier.parse(
                                                                                        id))) {
                                                            problems.add(
                                                                    "warp/bootstrap.json: unknown"
                                                                            + " item "
                                                                            + id);
                                                        }
                                                    }));
            if (!problems.isEmpty()) {
                throw new IllegalStateException(
                        "Rejected warp content:\n" + String.join("\n", problems));
            }
            List<CompletableFuture<?>> writes = new ArrayList<>();
            Path data = output.getOutputFolder(PackOutput.Target.DATA_PACK);
            Path assets = output.getOutputFolder(PackOutput.Target.RESOURCE_PACK).resolve("vftv");
            bySchema.get(VoiceContent.EVENTS.id())
                    .getAsJsonObject("events")
                    .asMap()
                    .forEach(
                            (id, body) ->
                                    writes.add(
                                            save(cache, data, id, "voice_event", body)));
            bySchema.get(VoiceContent.BARGAINS.id())
                    .getAsJsonObject("bargains")
                    .asMap()
                    .forEach((id, body) -> writes.add(save(cache, data, id, "bargain", body)));
            JsonElement recipe =
                    bySchema.get(VoiceContent.RECIPES.id())
                            .getAsJsonObject("recipes")
                            .get(VoiceContent.STILLING);
            writes.add(
                    DataProvider.saveStable(
                            cache, recipe, data.resolve("vftv/recipe/stilling_draught.json")));
            JsonObject phantoms = new JsonObject();
            phantoms.add("sounds", bySchema.get(VoiceContent.PHANTOMS.id()).get("sounds"));
            writes.add(DataProvider.saveStable(cache, phantoms, assets.resolve("voices/phantoms.json")));
            JsonObject lines = new JsonObject();
            script.getAsJsonObject("lines")
                    .asMap()
                    .forEach(
                            (id, e) -> {
                                JsonObject line = e.getAsJsonObject();
                                JsonObject out = new JsonObject();
                                out.addProperty("pool", line.get("pool").getAsString());
                                out.addProperty("void", VoiceContent.hasVoid(line));
                                out.addProperty(
                                        "ticks",
                                        (int)
                                                Math.ceil(
                                                        line.get("perform_seconds").getAsDouble()
                                                                * 20));
                                lines.add(id, out);
                            });
            JsonObject linesFile = new JsonObject();
            linesFile.add("lines", lines);
            writes.add(DataProvider.saveStable(cache, linesFile, assets.resolve("voices/lines.json")));
            return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
        }

        private static CompletableFuture<?> save(
                CachedOutput cache, Path data, String id, String registry, JsonElement body) {
            Identifier parsed = Identifier.parse(id);
            return DataProvider.saveStable(
                    cache,
                    body,
                    data.resolve(parsed.getNamespace())
                            .resolve("vftv")
                            .resolve(registry)
                            .resolve(parsed.getPath() + ".json"));
        }

        /** Reads and checks one envelope outside {@code content/warp}. */
        private static JsonObject one(Path file, Schema schema, List<String> problems) {
            JsonObject json = read(file, problems);
            if (json != null) {
                Schema.check(json, Map.of(schema.id(), schema))
                        .forEach(p -> problems.add(file.getFileName() + ": " + p));
            }
            return json == null ? new JsonObject() : json;
        }

        /** The strings of a {@code vftv:lang/1} file. */
        private static JsonObject lang(Path file, List<String> problems) {
            JsonObject json = read(file, problems);
            return json != null && json.has("strings")
                    ? json.getAsJsonObject("strings")
                    : new JsonObject();
        }

        private static @Nullable JsonObject read(Path file, List<String> problems) {
            try {
                return StrictJson.parse(Files.readString(file, StandardCharsets.UTF_8))
                        .getAsJsonObject();
            } catch (IOException | StrictJson.MalformedJsonException e) {
                problems.add(file.getFileName() + ": " + e.getMessage());
                return null;
            }
        }
    }
}
