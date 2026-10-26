package com.andrewbrogoff.vftv.datagen;

import com.andrewbrogoff.vftv.api.infusion.InfusionKeys;
import com.andrewbrogoff.vftv.core.Vftv;
import com.andrewbrogoff.vftv.core.common.model.Schema;
import com.andrewbrogoff.vftv.core.common.model.StrictJson;
import com.andrewbrogoff.vftv.core.infusion.InfusionAltars;
import com.andrewbrogoff.vftv.core.infusion.InfusionContent;
import com.andrewbrogoff.vftv.core.infusion.InfusionLang;
import com.andrewbrogoff.vftv.core.infusion.model.InfusionTuning;
import com.andrewbrogoff.vftv.core.text.model.LangEntry;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * The infusion feature's data generation (infusion spec, "Authored content" and the build-phase
 * data): the lang table, the mineable and infusion tags, the self-dropping loot tables, {@code
 * data/vftv/tuning/infusion.json}, and {@code content/infusion}: the recipes, the Echo parts, the
 * altar's multiblock display file, and the bootstrap's lint.
 */
final class InfusionData implements FeatureData {
    /** The authored directory under {@code content/}. */
    static final String DIR = "infusion";

    static final String BOWL = "vftv:singing_bowl";
    static final String PEDESTAL = "vftv:pedestal";
    static final String ECHO_ROD = "vftv:echo_rod";
    static final String ECHO_CAP = "vftv:echo_cap";
    static final String ENCHANTING = "vftv:infused_enchanting/";
    static final int PRICE_FLOOR = 100;
    static final int MAX_BUILT_IN_ENCHANT_INSTABILITY = 30;

    private static final Pattern ID = ArcaneBodies.ID;

    @Override
    public List<LangEntry> lang() {
        return InfusionLang.TABLE;
    }

    @Override
    public void tags(Tags tags) {
        tags.tag(BlockTags.MINEABLE_WITH_PICKAXE)
                .add(InfusionContent.BOWL_KEY)
                .add(InfusionContent.PEDESTAL_KEY);
        tags.tag(InfusionKeys.INFUSION_STABILISERS)
                .add(BuiltInRegistries.BLOCK.getResourceKey(Blocks.AMETHYST_CLUSTER).orElseThrow())
                .addTag(BlockTags.CANDLES);
        tags.tag(InfusionKeys.INFUSION_UNENCHANTABLE)
                .add(BuiltInRegistries.ITEM.getResourceKey(Items.BOOK).orElseThrow())
                .add(BuiltInRegistries.ITEM.getResourceKey(Items.ENCHANTED_BOOK).orElseThrow());
    }

    @Override
    public List<LootTableProvider.SubProviderEntry> loot() {
        return List.of(
                new LootTableProvider.SubProviderEntry(
                        registries ->
                                new BlockLootSubProvider(
                                        Set.of(), FeatureFlags.REGISTRY.allFlags(), registries) {
                                    @Override
                                    protected void generate() {
                                        dropSelf(InfusionContent.bowl());
                                        dropSelf(InfusionContent.pedestal());
                                    }

                                    @Override
                                    protected Iterable<Block> getKnownBlocks() {
                                        return List.of(InfusionContent.bowl(), InfusionContent.pedestal());
                                    }
                                },
                        LootContextParamSets.BLOCK));
    }

    @Override
    public void providers(GatherDataEvent event) {
        ResourceManager data = event.getResourceManager(PackType.SERVER_DATA);
        event.createProvider(TuningProvider::new);
        event.createProvider((output, lookup) -> new Content(output, lookup, data));
    }

    // --- Schemas --------------------------------------------------------------------------

    /** One recipe body of either type this file may hold, checked by its type's schema. */
    private static List<String> body(JsonElement value) {
        if (!(value instanceof JsonObject o) || !(o.get("type") instanceof JsonPrimitive t)) {
            return List.of("not a recipe body with a type");
        }
        Schema.Obj shape =
                switch (t.getAsString()) {
                    case "vftv:arcane" -> ArcaneBodies.BODY;
                    case "vftv:infusion" -> InfusionBodies.BODY;
                    default -> null;
                };
        if (shape == null) {
            return List.of("type " + t.getAsString() + " is neither vftv:arcane nor vftv:infusion");
        }
        JsonObject copy = o.deepCopy();
        copy.addProperty("schema", "vftv:body/1");
        return new Schema("vftv:body/1", shape.fields(), r -> List.of()).validate(copy);
    }

    /** {@code vftv:infusion_recipes/1} with its "Rejected:" rules. */
    static Schema recipesSchema(int chimeCapacity) {
        return Schema.of(
                        "vftv:infusion_recipes/1",
                        Schema.required(
                                "recipes",
                                new Schema.MapOf(
                                        new Schema.Str(3, 256, ID),
                                        new Schema.Checked(InfusionData::body),
                                        1,
                                        64)))
                .withExtra(root -> recipeRules(root.getAsJsonObject("recipes"), chimeCapacity));
    }

    static List<String> recipeRules(JsonObject recipes, int chimeCapacity) {
        List<String> problems = new ArrayList<>();
        for (String required : List.of(BOWL, PEDESTAL, ECHO_ROD, ECHO_CAP)) {
            if (!recipes.has(required)) {
                problems.add("$.recipes: " + required + " is required");
            }
        }
        int enchanting = 0;
        for (Map.Entry<String, JsonElement> e : recipes.entrySet()) {
            String id = e.getKey();
            JsonObject body = e.getValue().getAsJsonObject();
            String at = "$.recipes." + id;
            String type = body.get("type").getAsString();
            String kind = body.has("kind") ? body.get("kind").getAsString() : "ordinary";
            if (!kind.equals("ordinary")) {
                problems.add(at + ".kind: must be ordinary");
            }
            if (id.equals(BOWL) || id.equals(PEDESTAL)) {
                expect(problems, at + ".type", type, "vftv:arcane");
                expect(problems, at + ".research", body.get("research").getAsString(), "vftv:infusion");
                if (!type.equals("vftv:arcane")) {
                    continue;
                }
                JsonObject result = body.getAsJsonObject("result");
                expect(problems, at + ".result.id", result.get("id").getAsString(), id);
                if (result.has("count") && result.get("count").getAsInt() != 1) {
                    problems.add(at + ".result.count: must be 1");
                }
                body.getAsJsonObject("vis")
                        .entrySet()
                        .forEach(
                                v -> {
                                    if (v.getValue().getAsInt() > chimeCapacity) {
                                        problems.add(at + ".vis." + v.getKey() + ": over the chime rod's capacity " + chimeCapacity);
                                    }
                                });
            } else if (id.equals(ECHO_ROD) || id.equals(ECHO_CAP)) {
                expect(problems, at + ".type", type, "vftv:infusion");
                expect(problems, at + ".research", body.get("research").getAsString(), "vftv:echo_wands");
                if (!type.equals("vftv:infusion")) {
                    continue;
                }
                expect(
                        problems,
                        at + ".centre",
                        body.get("centre").getAsString(),
                        id.equals(ECHO_ROD) ? "vftv:chime_rod" : "vftv:resonant_brass_cap");
                JsonObject result = body.getAsJsonObject("result");
                if (!result.has("item")) {
                    problems.add(at + ".result: must be the item " + id);
                } else {
                    expect(problems, at + ".result.item.id", result.getAsJsonObject("item").get("id").getAsString(), id);
                }
                if (result.has("transmute") && result.get("transmute").getAsBoolean()) {
                    problems.add(at + ".result.transmute: must be omitted or false");
                }
                int instability = body.get("instability").getAsInt();
                if (instability < 5 || instability > 25) {
                    problems.add(at + ".instability: " + instability + " outside 5..25");
                }
            } else if (id.startsWith(ENCHANTING)) {
                enchanting++;
                expect(problems, at + ".type", type, "vftv:infusion");
                expect(problems, at + ".research", body.get("research").getAsString(), "vftv:infused_enchanting");
                if (!type.equals("vftv:infusion")) {
                    continue;
                }
                JsonObject result = body.getAsJsonObject("result");
                String path = id.substring(ENCHANTING.length());
                if (!result.has("enchantment")
                        || !result.get("enchantment").getAsString().equals("minecraft:" + path)) {
                    problems.add(at + ".result: must enchant minecraft:" + path);
                    continue;
                }
                int maxLevel = result.get("max_level").getAsInt();
                body.getAsJsonObject("essentia")
                        .entrySet()
                        .forEach(
                                c -> {
                                    if ((long) c.getValue().getAsInt() * maxLevel < PRICE_FLOOR) {
                                        problems.add(at + ".essentia." + c.getKey() + ": times max_level under the price floor " + PRICE_FLOOR);
                                    }
                                });
                int instability = body.get("instability").getAsInt();
                if (instability > MAX_BUILT_IN_ENCHANT_INSTABILITY) {
                    problems.add(at + ".instability: over " + MAX_BUILT_IN_ENCHANT_INSTABILITY);
                }
            } else {
                problems.add(at + ": not an id this file may hold");
            }
        }
        if (enchanting < 6 || enchanting > 10) {
            problems.add("$.recipes: " + enchanting + " enchanting recipes, not 6..10");
        }
        return problems;
    }

    private static final Schema.Obj PART_ROD =
            new Schema.Obj(
                    List.of(
                            Schema.required("item", new Schema.Id()),
                            Schema.required("capacity", new Schema.Int(1, 1000))));
    private static final Schema.Obj PART_CAP =
            new Schema.Obj(
                    List.of(
                            Schema.required("item", new Schema.Id()),
                            Schema.required("cost_multiplier", new Schema.Num(0.25, 2.0))));

    /** {@code vftv:wand_parts/1}, here exactly the Echo rod and cap at the shipped values. */
    static final Schema PARTS =
            Schema.of(
                            "vftv:wand_parts/1",
                            Schema.required("rods", new Schema.MapOf(new Schema.Str(3, 256, ID), PART_ROD, 1, 1)),
                            Schema.required("caps", new Schema.MapOf(new Schema.Str(3, 256, ID), PART_CAP, 1, 1)))
                    .withExtra(InfusionData::partRules);

    private static List<String> partRules(JsonObject root) {
        List<String> problems = new ArrayList<>();
        JsonObject rod = root.getAsJsonObject("rods").getAsJsonObject("vftv:echo");
        JsonObject cap = root.getAsJsonObject("caps").getAsJsonObject("vftv:echo");
        if (rod == null || cap == null) {
            return List.of("$: exactly the rod vftv:echo and the cap vftv:echo");
        }
        expect(problems, "$.rods.vftv:echo.item", rod.get("item").getAsString(), ECHO_ROD);
        if (rod.get("capacity").getAsInt() != 300) {
            problems.add("$.rods.vftv:echo.capacity: must be 300");
        }
        expect(problems, "$.caps.vftv:echo.item", cap.get("item").getAsString(), ECHO_CAP);
        if (cap.get("cost_multiplier").getAsDouble() != 0.70) {
            problems.add("$.caps.vftv:echo.cost_multiplier: must be 0.70");
        }
        return problems;
    }

    private static final Pattern STATE =
            Pattern.compile("([a-z0-9_.-]+:[a-z0-9_./-]+)(\\[([a-z0-9_]+=[a-z0-9_]+)(,[a-z0-9_]+=[a-z0-9_]+)*\\])?");

    /** {@code vftv:multiblocks/1}, here exactly {@code vftv:infusion_altar}. */
    static final Schema MULTIBLOCKS =
            Schema.of(
                            "vftv:multiblocks/1",
                            Schema.required(
                                    "multiblocks",
                                    new Schema.MapOf(
                                            new Schema.Str(3, 256, ID),
                                            new Schema.Obj(
                                                    List.of(
                                                            Schema.required(
                                                                    "key",
                                                                    new Schema.MapOf(
                                                                            new Schema.Str(1, 1, null),
                                                                            new Schema.Str(3, 512, STATE),
                                                                            1,
                                                                            64)),
                                                            Schema.required(
                                                                    "layers",
                                                                    new Schema.ListOf(
                                                                            new Schema.ListOf(new Schema.Str(1, 9, null), 1, 9, false),
                                                                            1,
                                                                            4,
                                                                            false)))),
                                            1,
                                            1)))
                    .withExtra(root -> multiblockRules(root.getAsJsonObject("multiblocks")));

    static List<String> multiblockRules(JsonObject multiblocks) {
        JsonObject altar = multiblocks.getAsJsonObject("vftv:infusion_altar");
        if (altar == null) {
            return List.of("$.multiblocks: exactly vftv:infusion_altar");
        }
        List<String> problems = new ArrayList<>();
        JsonObject key = altar.getAsJsonObject("key");
        if (key.has(".")) {
            problems.add("$.key: '.' is air and may not be keyed");
        }
        String bowlChar = null;
        for (Map.Entry<String, JsonElement> e : key.entrySet()) {
            java.util.regex.Matcher m = STATE.matcher(e.getValue().getAsString());
            if (!m.matches()) {
                continue;
            }
            Identifier id = Identifier.parse(m.group(1));
            Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(id);
            if (block.isEmpty()) {
                problems.add("$.key." + e.getKey() + ": unknown block " + id);
                continue;
            }
            if (m.group(2) != null) {
                for (String pair :
                        com.google.common.base.Splitter.on(',')
                                .split(m.group(2).substring(1, m.group(2).length() - 1))) {
                    String name = pair.substring(0, pair.indexOf('='));
                    String value = pair.substring(pair.indexOf('=') + 1);
                    Property<?> property = block.get().getStateDefinition().getProperty(name);
                    if (property == null || property.getValue(value).isEmpty()) {
                        problems.add("$.key." + e.getKey() + ": " + pair + " is not a property of " + id);
                    }
                }
            }
            if (id.toString().equals(BOWL)) {
                if (bowlChar != null) {
                    problems.add("$.key: exactly one character maps to " + BOWL);
                }
                bowlChar = e.getKey();
            }
        }
        if (bowlChar == null) {
            return List.of("$.key: one character must map to " + BOWL);
        }
        JsonArray layers = altar.getAsJsonArray("layers");
        int depth = layers.get(0).getAsJsonArray().size();
        int width = layers.get(0).getAsJsonArray().get(0).getAsString().length();
        if (depth % 2 == 0 || width % 2 == 0) {
            problems.add("$.layers: width and depth must be odd");
        }
        int bowls = 0;
        boolean pedestalsBesideBowl = false;
        for (int y = 0; y < layers.size(); y++) {
            JsonArray rows = layers.get(y).getAsJsonArray();
            if (rows.size() != depth) {
                problems.add("$.layers[" + y + "]: every layer has " + depth + " rows");
                continue;
            }
            boolean bowlHere = false;
            boolean pedestalHere = false;
            for (int z = 0; z < rows.size(); z++) {
                String row = rows.get(z).getAsString();
                if (row.length() != width) {
                    problems.add("$.layers[" + y + "][" + z + "]: every row has " + width + " characters");
                    continue;
                }
                for (int x = 0; x < width; x++) {
                    String c = String.valueOf(row.charAt(x));
                    if (c.equals(".")) {
                        continue;
                    }
                    if (!key.has(c)) {
                        problems.add("$.layers[" + y + "][" + z + "]: " + c + " is not keyed");
                    } else if (c.equals(bowlChar)) {
                        bowls++;
                        bowlHere = true;
                        if (x != width / 2 || z != depth / 2) {
                            problems.add("$.layers[" + y + "]: the bowl is off its layer's centre column");
                        }
                    } else if (key.get(c).getAsString().startsWith(PEDESTAL)) {
                        pedestalHere = true;
                    }
                }
            }
            pedestalsBesideBowl |= bowlHere && pedestalHere;
        }
        if (bowls != 1) {
            problems.add("$.layers: the bowl is used " + bowls + " times, not once");
        }
        if (!pedestalsBesideBowl) {
            problems.add("$.layers: the bowl's layer has no pedestals");
        }
        return problems;
    }

    /** The generated display file: positions from the low corner, air omitted, in (y, z, x). */
    static JsonObject multiblock(JsonObject altar) {
        JsonArray layers = altar.getAsJsonArray("layers");
        JsonObject key = altar.getAsJsonObject("key");
        int depth = layers.get(0).getAsJsonArray().size();
        int width = layers.get(0).getAsJsonArray().get(0).getAsString().length();
        JsonArray blocks = new JsonArray();
        int[] bowl = new int[3];
        for (int y = 0; y < layers.size(); y++) {
            JsonArray rows = layers.get(y).getAsJsonArray();
            for (int z = 0; z < depth; z++) {
                String row = rows.get(z).getAsString();
                for (int x = 0; x < width; x++) {
                    String c = String.valueOf(row.charAt(x));
                    if (c.equals(".")) {
                        continue;
                    }
                    String state = key.get(c).getAsString();
                    if (state.equals(BOWL)) {
                        bowl = new int[] {x, y, z};
                    }
                    JsonArray entry = new JsonArray();
                    entry.add(x);
                    entry.add(y);
                    entry.add(z);
                    entry.add(state);
                    blocks.add(entry);
                }
            }
        }
        JsonObject out = new JsonObject();
        out.addProperty("format", 1);
        out.add("size", ints(width, layers.size(), depth));
        out.add("bowl", ints(bowl[0], bowl[1], bowl[2]));
        out.add("blocks", blocks);
        return out;
    }

    private static JsonArray ints(int... values) {
        JsonArray a = new JsonArray();
        for (int v : values) {
            a.add(v);
        }
        return a;
    }

    private static final Schema.Obj ENTRY_ITEM =
            new Schema.Obj(
                    List.of(
                            Schema.required("item", new Schema.Id()),
                            Schema.required("why", new Schema.Str(1, 512, null))));
    private static final Schema.Obj ENTRY_ASPECT =
            new Schema.Obj(
                    List.of(
                            Schema.required("aspect", new Schema.Id()),
                            Schema.required("why", new Schema.Str(1, 512, null))));

    /** {@code vftv:infusion_bootstrap/1}, exactly the two Echo recipes. */
    static final Schema BOOTSTRAP =
            Schema.of(
                            "vftv:infusion_bootstrap/1",
                            Schema.required(
                                    "recipes",
                                    new Schema.MapOf(
                                            new Schema.Str(3, 256, ID),
                                            new Schema.Obj(
                                                    List.of(
                                                            Schema.required("items", new Schema.ListOf(ENTRY_ITEM, 1, 17, false)),
                                                            Schema.required("aspects", new Schema.ListOf(ENTRY_ASPECT, 1, 8, false)))),
                                            2,
                                            2)))
                    .withExtra(
                            root -> {
                                JsonObject recipes = root.getAsJsonObject("recipes");
                                return recipes.keySet().equals(Set.of(ECHO_ROD, ECHO_CAP))
                                        ? List.of()
                                        : List.of("$.recipes: exactly " + ECHO_ROD + " and " + ECHO_CAP);
                            });

    /** The bootstrap covers every centre and component item and every aspect of each Echo recipe. */
    static List<String> bootstrapCoverage(JsonObject bootstrap, JsonObject recipes) {
        List<String> problems = new ArrayList<>();
        for (String id : List.of(ECHO_ROD, ECHO_CAP)) {
            JsonObject entry = bootstrap.getAsJsonObject(id);
            JsonObject recipe = recipes.getAsJsonObject(id);
            if (entry == null || recipe == null) {
                continue;
            }
            Set<String> items = new HashSet<>();
            entry.getAsJsonArray("items").forEach(i -> items.add(i.getAsJsonObject().get("item").getAsString()));
            Set<String> aspects = new HashSet<>();
            entry.getAsJsonArray("aspects").forEach(a -> aspects.add(a.getAsJsonObject().get("aspect").getAsString()));
            List<String> wanted = new ArrayList<>();
            wanted.add(recipe.get("centre").getAsString());
            recipe.getAsJsonArray("components").forEach(c -> wanted.add(c.getAsString()));
            for (String w : wanted) {
                if (!w.startsWith("#") && !items.contains(w)) {
                    problems.add("bootstrap " + id + ": " + w + " is not covered");
                }
            }
            for (String a : recipe.getAsJsonObject("essentia").keySet()) {
                if (!aspects.contains(a)) {
                    problems.add("bootstrap " + id + ": aspect " + a + " is not covered");
                }
            }
            for (String i : items) {
                if (!BuiltInRegistries.ITEM.containsKey(Identifier.parse(i))) {
                    problems.add("bootstrap " + id + ": unknown item " + i);
                }
            }
        }
        return problems;
    }

    private static void expect(List<String> problems, String at, String value, String want) {
        if (!want.equals(value)) {
            problems.add(at + ": must be " + want);
        }
    }

    // --- Providers ------------------------------------------------------------------------

    /** {@code data/vftv/tuning/infusion.json}, every key at its default. */
    private record TuningProvider(PackOutput output) implements DataProvider {
        @Override
        public CompletableFuture<?> run(CachedOutput cache) {
            return DataProvider.saveStable(
                    cache,
                    InfusionTuning.DEFAULTS.toJson(),
                    output.getOutputFolder(PackOutput.Target.DATA_PACK)
                            .resolve(Vftv.MOD_ID)
                            .resolve(InfusionAltars.TUNING_PATH));
        }

        @Override
        public String getName() {
            return "vftv infusion tuning";
        }
    }

    private static Path contentRoot() {
        String root = System.getProperty(ContentProvider.CONTENT_DIR);
        if (root == null) {
            throw new IllegalStateException("the system property " + ContentProvider.CONTENT_DIR + " is not set");
        }
        return Path.of(root);
    }

    /** The chime rod's capacity in {@code content/wands/parts.json}, which the lint reads. */
    static int chimeCapacity(Path content) {
        try {
            return StrictJson.parse(Files.readString(content.resolve("wands/parts.json")))
                    .getAsJsonObject()
                    .getAsJsonObject("rods")
                    .getAsJsonObject("vftv:chime")
                    .get("capacity")
                    .getAsInt();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Reads, checks and writes {@code content/infusion}. */
    private static final class Content extends ContentProvider {
        private final CompletableFuture<HolderLookup.Provider> lookup;
        private final ResourceManager data;

        Content(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup, ResourceManager data) {
            super(output, DIR, List.of(recipesSchema(chimeCapacity(contentRoot())), PARTS, MULTIBLOCKS, BOOTSTRAP));
            this.lookup = lookup;
            this.data = data;
        }

        @Override
        protected CompletableFuture<?> generate(List<Envelope> envelopes, CachedOutput cache) {
            return lookup.thenCompose(registries -> generate(envelopes, cache, registries));
        }

        private CompletableFuture<?> generate(
                List<Envelope> envelopes, CachedOutput cache, HolderLookup.Provider registries) {
            Path content = contentRoot();
            ResearchContent.Content loaded = ResearchContent.read(content);
            Set<String> research = loaded.entries().keySet();
            List<String> problems = new ArrayList<>();
            List<CompletableFuture<?>> writes = new ArrayList<>();
            JsonObject recipes = null;
            JsonObject bootstrap = null;
            Set<String> inTable = tagMembers(data, Identifier.withDefaultNamespace("in_enchanting_table"), new TreeSet<>());
            for (Envelope e : envelopes) {
                JsonObject json = e.json().getAsJsonObject();
                switch (e.schema().id()) {
                    case "vftv:infusion_recipes/1" -> {
                        recipes = json.getAsJsonObject("recipes");
                        for (Map.Entry<String, JsonElement> r : recipes.entrySet()) {
                            JsonObject body = r.getValue().getAsJsonObject();
                            if (body.get("type").getAsString().equals("vftv:arcane")) {
                                problems.addAll(ArcaneBodies.check(r.getKey(), body, registries, research));
                                writes.add(ArcaneBodies.write(cache, output, r.getKey(), body));
                            } else {
                                problems.addAll(InfusionBodies.check(r.getKey(), body, registries, research));
                                problems.addAll(enchantRules(r.getKey(), body, registries, inTable));
                                problems.addAll(voidGate(r.getKey(), body, loaded));
                                writes.add(InfusionBodies.write(cache, output, r.getKey(), body));
                            }
                        }
                        problems.addAll(duplicates(content, recipes.keySet(), e.path()));
                    }
                    case "vftv:wand_parts/1" -> {
                        problems.addAll(partDuplicates(content, e.path()));
                        for (String table : List.of("rods", "caps")) {
                            json.getAsJsonObject(table)
                                    .entrySet()
                                    .forEach(
                                            p -> writes.add(
                                                    save(
                                                            cache,
                                                            PackOutput.Target.DATA_PACK,
                                                            Identifier.parse(p.getKey()),
                                                            table.equals("rods") ? "vftv/wand_rod" : "vftv/wand_cap",
                                                            p.getValue())));
                        }
                    }
                    case "vftv:multiblocks/1" ->
                            json.getAsJsonObject("multiblocks")
                                    .entrySet()
                                    .forEach(
                                            m -> writes.add(
                                                    save(
                                                            cache,
                                                            PackOutput.Target.RESOURCE_PACK,
                                                            Identifier.parse(m.getKey()),
                                                            "multiblock",
                                                            multiblock(m.getValue().getAsJsonObject()))));
                    default -> bootstrap = json.getAsJsonObject("recipes");
                }
            }
            if (recipes != null && bootstrap != null) {
                problems.addAll(bootstrapCoverage(bootstrap, recipes));
            }
            if (!problems.isEmpty()) {
                throw new IllegalStateException("Rejected infusion content:\n" + String.join("\n", problems));
            }
            return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
        }

        private CompletableFuture<?> save(
                CachedOutput cache, PackOutput.Target target, Identifier id, String dir, JsonElement json) {
            return DataProvider.saveStable(
                    cache,
                    json,
                    output.getOutputFolder(target).resolve(id.getNamespace()).resolve(dir).resolve(id.getPath() + ".json"));
        }

        @Override
        public String getName() {
            return "vftv infusion content";
        }
    }

    /** The built-in enchanting lints: in the enchanting table's tag, within the vanilla maximum. */
    static List<String> enchantRules(
            String id, JsonObject body, HolderLookup.Provider registries, Set<String> inTable) {
        JsonObject result = body.getAsJsonObject("result");
        if (!result.has("enchantment")) {
            return List.of();
        }
        String where = "recipe " + id + ": ";
        String enchantment = result.get("enchantment").getAsString();
        List<String> problems = new ArrayList<>();
        if (!inTable.contains(enchantment) && !enchantment.equals("vftv:baffling")) {
            problems.add(where + enchantment + " is not in #minecraft:in_enchanting_table");
        }
        Optional<Enchantment> value =
                registries.lookupOrThrow(Registries.ENCHANTMENT)
                        .get(ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(enchantment)))
                        .map(h -> h.value());
        if (value.isPresent() && result.get("max_level").getAsInt() > value.get().getMaxLevel()) {
            problems.add(where + "max_level over " + enchantment + "'s maximum " + value.get().getMaxLevel());
        }
        return problems;
    }

    /** A built-in {@code void} body needs a Stage 4 gate. */
    static List<String> voidGate(String id, JsonObject body, ResearchContent.Content research) {
        if (!body.has("kind") || !body.get("kind").getAsString().equals("void")) {
            return List.of();
        }
        ResearchContent.Sourced entry = research.entries().get(body.get("research").getAsString());
        return entry != null && entry.entry().stage() == 4
                ? List.of()
                : List.of("recipe " + id + ": a void body needs a Stage 4 research gate");
    }

    /** A recipe id declared in two {@code content/} files fails. */
    static List<String> duplicates(Path content, Set<String> ids, String own) {
        List<String> problems = new ArrayList<>();
        Map<String, String> seen = new LinkedHashMap<>();
        for (Path file : jsonFiles(content)) {
            String rel = content.relativize(file).toString().replace('\\', '/');
            if (rel.equals(DIR + "/" + own)) {
                continue;
            }
            if (read(file) instanceof JsonObject root && root.get("recipes") instanceof JsonObject all) {
                for (String id : all.keySet()) {
                    if (ids.contains(id)
                            && all.get(id) instanceof JsonObject b
                            && b.has("type")) { // a recipe body, not a bootstrap entry
                        seen.put(id, rel);
                    }
                }
            }
        }
        seen.forEach((id, file) -> problems.add("recipe " + id + " is also declared in content/" + file));
        return problems;
    }

    /** A wand part id declared in another {@code content/} parts file fails. */
    static List<String> partDuplicates(Path content, String own) {
        List<String> problems = new ArrayList<>();
        for (Path file : jsonFiles(content)) {
            String rel = content.relativize(file).toString().replace('\\', '/');
            if (rel.equals(DIR + "/" + own)) {
                continue;
            }
            if (read(file) instanceof JsonObject root
                    && root.get("schema") instanceof JsonPrimitive s
                    && s.getAsString().equals("vftv:wand_parts/1")) {
                for (String table : List.of("rods", "caps")) {
                    if (root.getAsJsonObject(table).has("vftv:echo")) {
                        problems.add("wand part vftv:echo is also declared in content/" + rel);
                    }
                }
            }
        }
        return problems;
    }

    private static List<Path> jsonFiles(Path content) {
        try (Stream<Path> walk = Files.walk(content)) {
            return walk.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static JsonElement read(Path file) {
        try {
            return StrictJson.parse(Files.readString(file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** An enchantment tag's members from the server data, following tag references. */
    static Set<String> tagMembers(ResourceManager data, Identifier tag, Set<String> into) {
        Identifier file = Identifier.fromNamespaceAndPath(tag.getNamespace(), "tags/enchantment/" + tag.getPath() + ".json");
        for (Resource resource : data.getResourceStack(file)) {
            try (InputStream in = resource.open()) {
                JsonObject json = StrictJson.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                for (JsonElement v : json.getAsJsonArray("values")) {
                    String entry = v.isJsonObject() ? v.getAsJsonObject().get("id").getAsString() : v.getAsString();
                    if (entry.startsWith("#")) {
                        tagMembers(data, Identifier.parse(entry.substring(1)), into);
                    } else {
                        into.add(entry);
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return into;
    }
}
