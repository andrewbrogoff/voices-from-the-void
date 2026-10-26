package com.andrewbrogoff.vftv.datagen;

import com.andrewbrogoff.vftv.api.arcane.ArcaneKeys;
import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.core.arcane.ArcaneContent;
import com.andrewbrogoff.vftv.core.arcane.ArcaneLang;
import com.andrewbrogoff.vftv.core.common.model.Schema;
import com.andrewbrogoff.vftv.core.common.model.StrictJson;
import com.andrewbrogoff.vftv.core.text.model.LangEntry;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * The arcane feature's data generation: override the {@link FeatureData} hooks it needs. Listed
 * once in {@link VftvData#FEATURES}.
 */
final class ArcaneData implements FeatureData {
    /** The six built-in primals, the only aspects a {@code recipes.json} cost may name. */
    static final List<String> PRIMALS =
            Stream.of(
                            AspectKeys.AER,
                            AspectKeys.AQUA,
                            AspectKeys.IGNIS,
                            AspectKeys.ORDO,
                            AspectKeys.PERDITIO,
                            AspectKeys.TERRA)
                    .map(ResourceKey::identifier)
                    .map(Object::toString)
                    .toList();

    /** The Wellspring's per-aspect amount while {@code content/nodes/types.json} is absent. */
    static final int WELLSPRING_DEFAULT = 32;

    /**
     * Pre-iron ingredients: copper, amethyst and wood items or tags of vanilla and the convention
     * namespace. ponytail: a name pattern, not a curated item list; tighten if a match surprises.
     */
    static final Pattern PRE_IRON =
            Pattern.compile(
                    "#?(minecraft|c):(.*copper.*|.*amethyst.*|(.*_)?planks|.*_logs?|logs(_.*)?"
                            + "|.*_wood|wooden_.*|stick)");

    /** {@code content/arcane/recipes.json} → each recipe's {@code data/<ns>/recipe/<path>.json}. */
    @Override
    public void providers(GatherDataEvent event) {
        event.createProvider(Recipes::new);
    }

    @Override
    public List<LangEntry> lang() {
        return ArcaneLang.TABLE;
    }

    /** {@code #vftv:converts_to_worktable} (the crafting table) and the Worktable on the axe. */
    @Override
    public void tags(Tags tags) {
        tags.tag(ArcaneKeys.CONVERTS_TO_WORKTABLE)
                .add(BuiltInRegistries.BLOCK.getResourceKey(Blocks.CRAFTING_TABLE).orElseThrow());
        tags.tag(BlockTags.MINEABLE_WITH_AXE).add(ArcaneContent.WORKTABLE_BLOCK_KEY);
    }

    /** {@code vftv:blocks/worktable}: drops itself. */
    @Override
    public List<LootTableProvider.SubProviderEntry> loot() {
        return List.of(
                new LootTableProvider.SubProviderEntry(
                        registries ->
                                new BlockLootSubProvider(
                                        Set.of(), FeatureFlags.REGISTRY.allFlags(), registries) {
                                    @Override
                                    protected void generate() {
                                        dropSelf(ArcaneContent.worktable());
                                    }

                                    @Override
                                    protected Iterable<Block> getKnownBlocks() {
                                        return List.of(ArcaneContent.worktable());
                                    }
                                },
                        LootContextParamSets.BLOCK));
    }

    private static final int BELL_MAX_STACK = 64;

    /** The {@code vftv:arcane_recipes/1} schema, with its "Rejected:" rules for this bound. */
    static Schema schema(Map<String, Integer> wellspring) {
        return Schema.of(
                        "vftv:arcane_recipes/1",
                        Schema.required(
                                "recipes",
                                new Schema.MapOf(
                                        new Schema.Str(3, 256, ArcaneBodies.ID),
                                        ArcaneBodies.BODY,
                                        1,
                                        1)))
                .withExtra(root -> bellRules(root, wellspring));
    }

    private static List<String> bellRules(JsonObject root, Map<String, Integer> wellspring) {
        JsonObject recipes = root.getAsJsonObject("recipes");
        JsonObject bell = recipes.getAsJsonObject("vftv:bell");
        if (bell == null) {
            return List.of("$.recipes: holds exactly vftv:bell, not " + recipes.keySet());
        }
        String at = "$.recipes.vftv:bell";
        List<String> problems = new ArrayList<>();
        expect(problems, at + ".research", bell.get("research"), "vftv:arcane_crafting");
        if (bell.has("kind")) {
            expect(problems, at + ".kind", bell.get("kind"), "ordinary");
        }
        expect(
                problems,
                at + ".result.id",
                bell.getAsJsonObject("result").get("id"),
                "minecraft:bell");
        // ponytail: datagen never binds item components, so ArcaneBodies.check can't read the
        // result's maximum stack here; the result is pinned to minecraft:bell, which stacks to 64.
        JsonElement count = bell.getAsJsonObject("result").get("count");
        if (count != null && count.getAsInt() > BELL_MAX_STACK) {
            problems.add(
                    at + ".result.count: over minecraft:bell's maximum stack " + BELL_MAX_STACK);
        }
        bell.getAsJsonObject("vis")
                .entrySet()
                .forEach(
                        e -> {
                            Integer bound = wellspring.get(e.getKey());
                            if (!PRIMALS.contains(e.getKey())) {
                                problems.add(at + ".vis." + e.getKey() + ": not a built-in primal");
                            } else if (bound == null) {
                                problems.add(at + ".vis." + e.getKey() + ": not in the Wellspring");
                            } else if (e.getValue().getAsInt() > bound) {
                                problems.add(
                                        at
                                                + ".vis."
                                                + e.getKey()
                                                + ": over the Wellspring's "
                                                + bound);
                            }
                        });
        Stream.concat(
                        bell.has("key")
                                ? bell.getAsJsonObject("key").asMap().values().stream()
                                : Stream.empty(),
                        bell.has("ingredients")
                                ? bell.getAsJsonArray("ingredients").asList().stream()
                                : Stream.empty())
                .map(JsonElement::getAsString)
                .filter(i -> !PRE_IRON.matcher(i).matches())
                .forEach(i -> problems.add(at + ": " + i + " is not a pre-iron ingredient"));
        return problems;
    }

    private static void expect(List<String> problems, String at, JsonElement value, String want) {
        if (!want.equals(value.getAsString())) {
            problems.add(at + ": must be " + want);
        }
    }

    /**
     * The Wellspring's per-aspect amount from {@code content/nodes/types.json}, or {@value
     * #WELLSPRING_DEFAULT} for each built-in primal while that file is absent.
     */
    static Map<String, Integer> wellspring(Path content) {
        Path file = content.resolve("nodes/types.json");
        Map<String, Integer> amounts = new LinkedHashMap<>();
        if (!Files.exists(file)) {
            PRIMALS.forEach(p -> amounts.put(p, WELLSPRING_DEFAULT));
            return amounts;
        }
        try {
            JsonObject types =
                    StrictJson.parse(Files.readString(file))
                            .getAsJsonObject()
                            .getAsJsonObject("types");
            types.getAsJsonObject("vftv:wellspring")
                    .getAsJsonArray("aspects")
                    .forEach(
                            a ->
                                    amounts.put(
                                            a.getAsJsonObject().get("aspect").getAsString(),
                                            a.getAsJsonObject()
                                                    .getAsJsonArray("range")
                                                    .get(1)
                                                    .getAsInt()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return amounts;
    }

    /** Reads, checks and writes {@code content/arcane}. */
    private static final class Recipes extends ContentProvider {
        private final CompletableFuture<HolderLookup.Provider> lookup;

        Recipes(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
            super(output, "arcane", List.of(schema(wellspring(contentRoot()))));
            this.lookup = lookup;
        }

        private static Path contentRoot() {
            String root = System.getProperty(CONTENT_DIR);
            if (root == null) {
                throw new IllegalStateException(
                        "the system property " + CONTENT_DIR + " is not set");
            }
            return Path.of(root);
        }

        @Override
        protected CompletableFuture<?> generate(List<Envelope> envelopes, CachedOutput cache) {
            return lookup.thenCompose(
                    registries -> {
                        Map<String, JsonObject> recipes = new LinkedHashMap<>();
                        List<String> problems = new ArrayList<>();
                        Set<String> research =
                                envelopes.isEmpty()
                                        ? Set.of()
                                        : ResearchContent.read(contentRoot()).entries().keySet();
                        for (Envelope envelope : envelopes) {
                            envelope.json()
                                    .getAsJsonObject()
                                    .getAsJsonObject("recipes")
                                    .asMap()
                                    .forEach(
                                            (id, body) -> {
                                                problems.addAll(
                                                        ArcaneBodies.check(
                                                                id,
                                                                body.getAsJsonObject(),
                                                                registries,
                                                                research));
                                                recipes.put(id, body.getAsJsonObject());
                                            });
                        }
                        if (!problems.isEmpty()) {
                            throw new IllegalStateException(
                                    "Rejected arcane content:\n" + String.join("\n", problems));
                        }
                        return CompletableFuture.allOf(
                                recipes.entrySet().stream()
                                        .map(
                                                e ->
                                                        ArcaneBodies.write(
                                                                cache,
                                                                output,
                                                                e.getKey(),
                                                                e.getValue()))
                                        .toArray(CompletableFuture[]::new));
                    });
        }

        @Override
        public String getName() {
            return "vftv arcane recipes";
        }
    }
}
