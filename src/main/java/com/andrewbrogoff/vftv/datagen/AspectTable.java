package com.andrewbrogoff.vftv.datagen;

import com.andrewbrogoff.vftv.core.aspect.AspectSet;
import com.andrewbrogoff.vftv.core.aspect.BuiltinAspects;
import com.andrewbrogoff.vftv.core.aspect.DerivationRecipes;
import com.andrewbrogoff.vftv.core.aspect.model.Derivation;
import com.andrewbrogoff.vftv.core.aspect.model.ScanningTuning;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * The authored aspect table from every {@code content/aspects/**.json}, the vanilla recipe
 * derivation over it, and the datagen gates (aspects spec, "Vanilla values", "Coverage and
 * supply"): coverage of every {@code minecraft} and {@code vftv} item, block and entity, no object
 * keyed twice in one map, the semantic overrides, supply and non-amplification. Every failure is
 * listed at once and fails the data run, naming the file to fix.
 */
final class AspectTable {
    /** The namespaces the coverage gate covers. */
    private static final Set<String> COVERED = Set.of("minecraft", "vftv");

    /** Each semantic override: the tag, and the aspect every item in it must hold. */
    private static final Map<TagKey<Item>, String> SEMANTIC =
            Map.ofEntries(
                    Map.entry(ItemTags.PICKAXES, "vftv:instrumentum"),
                    Map.entry(ItemTags.AXES, "vftv:instrumentum"),
                    Map.entry(ItemTags.SHOVELS, "vftv:instrumentum"),
                    Map.entry(ItemTags.HOES, "vftv:instrumentum"),
                    Map.entry(ItemTags.SWORDS, "vftv:telum"),
                    Map.entry(ItemTags.SPEARS, "vftv:telum"),
                    Map.entry(ItemTags.HEAD_ARMOR, "vftv:tutamen"),
                    Map.entry(ItemTags.CHEST_ARMOR, "vftv:tutamen"),
                    Map.entry(ItemTags.LEG_ARMOR, "vftv:tutamen"),
                    Map.entry(ItemTags.FOOT_ARMOR, "vftv:tutamen"));

    /** The semantic overrides for tools and weapons no tag gathers: item, and its aspect. */
    private static final Map<Item, String> SEMANTIC_ITEMS =
            Map.of(
                    Items.BOW, "vftv:telum",
                    Items.CROSSBOW, "vftv:telum",
                    Items.MACE, "vftv:telum",
                    Items.SHIELD, "vftv:tutamen",
                    Items.SHEARS, "vftv:instrumentum",
                    Items.FLINT_AND_STEEL, "vftv:instrumentum",
                    Items.FISHING_ROD, "vftv:instrumentum",
                    Items.BRUSH, "vftv:instrumentum");

    /** The minimum sources per aspect (aspects spec, "Tuning", test thresholds). */
    static final int MIN_SOURCES = 3;

    /** One authored map: key ({@code ns:path} or {@code #ns:path}) to value, and its file. */
    record Entry(SortedMap<String, Integer> value, String file) {}

    final Map<String, SortedMap<String, Entry>> tables = new TreeMap<>();
    final Map<String, SortedMap<String, String>> exempt = new TreeMap<>();
    final List<String> problems = new ArrayList<>();

    /** Reads the envelopes' tables; keys are checked when the registries are known. */
    void read(List<ContentProvider.Envelope> files) {
        for (ContentProvider.Envelope file : files) {
            JsonObject json = file.json().getAsJsonObject();
            for (String table : AspectData.TABLES) {
                JsonElement entries = json.get(table);
                if (entries == null) {
                    continue;
                }
                SortedMap<String, Entry> into = tables.computeIfAbsent(table, t -> new TreeMap<>());
                for (Map.Entry<String, JsonElement> e : entries.getAsJsonObject().entrySet()) {
                    SortedMap<String, Integer> value = new TreeMap<>();
                    e.getValue()
                            .getAsJsonObject()
                            .entrySet()
                            .forEach(a -> value.put(a.getKey(), a.getValue().getAsInt()));
                    Entry previous = into.put(e.getKey(), new Entry(value, file.path()));
                    if (previous != null) {
                        problems.add(
                                where(file.path(), table, e.getKey())
                                        + ": keyed twice (also in "
                                        + previous.file()
                                        + ")");
                    }
                }
            }
            JsonElement ex = json.get("exempt");
            if (ex != null) {
                for (Map.Entry<String, JsonElement> t : ex.getAsJsonObject().entrySet()) {
                    SortedMap<String, String> into =
                            exempt.computeIfAbsent(t.getKey(), k -> new TreeMap<>());
                    t.getValue()
                            .getAsJsonObject()
                            .entrySet()
                            .forEach(e -> into.put(e.getKey(), e.getValue().getAsString()));
                }
            }
        }
    }

    /** The authored entries of a table. */
    SortedMap<String, Entry> table(String table) {
        return tables.getOrDefault(table, new TreeMap<>());
    }

    /**
     * Every object a table's keys cover, by object id, each key expanded through the bound tags; an
     * object two keys cover, or a key naming nothing, is a problem.
     */
    <T> SortedMap<String, Entry> expand(String table, Registry<T> registry) {
        SortedMap<String, Entry> out = new TreeMap<>();
        Map<String, String> coveredBy = new TreeMap<>();
        table(table)
                .forEach(
                        (key, entry) -> {
                            List<String> ids = ids(key, registry);
                            if (ids.isEmpty()) {
                                problems.add(
                                        where(entry.file(), table, key)
                                                + ": names no "
                                                + registry.key().identifier().getPath());
                            }
                            for (String id : ids) {
                                String other = coveredBy.put(id, key);
                                if (other != null) {
                                    problems.add(
                                            where(entry.file(), table, key)
                                                    + ": "
                                                    + id
                                                    + " is also covered by "
                                                    + other);
                                }
                                out.put(id, entry);
                            }
                        });
        return out;
    }

    /** The object ids a key names: one id, or a tag's bound members. */
    static <T> List<String> ids(String key, Registry<T> registry) {
        if (key.startsWith("#")) {
            TagKey<T> tag = TagKey.create(registry.key(), Identifier.parse(key.substring(1)));
            List<String> ids = new ArrayList<>();
            registry.getTagOrEmpty(tag)
                    .forEach(h -> ids.add(h.unwrapKey().orElseThrow().identifier().toString()));
            return ids;
        }
        Identifier id = Identifier.parse(key);
        return registry.containsKey(id) ? List.of(id.toString()) : List.of();
    }

    /**
     * Derives the vanilla and VFTV items no entry or default covers, runs every gate, and returns
     * the derived values to write. Throws listing every problem.
     *
     * @param capture the captured vanilla and VFTV recipes, adapted
     * @param roundTrips the built-in {@code vftv:arcane} recipes and the table conversion, which
     *     the non-amplification gate replays with the captured recipes (never derived from)
     */
    SortedMap<String, SortedMap<String, Integer>> derive(
            DerivationRecipes.Capture capture, List<Derivation.Recipe> roundTrips) {
        SortedMap<String, Entry> items = expand("items", BuiltInRegistries.ITEM);
        SortedMap<String, Entry> defaults = expand("defaults", BuiltInRegistries.ITEM);
        SortedMap<String, Entry> blocks = expand("blocks", BuiltInRegistries.BLOCK);
        SortedMap<String, Entry> entities = expand("entities", BuiltInRegistries.ENTITY_TYPE);
        SortedMap<String, SortedMap<String, Integer>> resolved = new TreeMap<>();
        defaults.forEach((id, e) -> resolved.put(id, e.value()));
        items.forEach((id, e) -> resolved.put(id, e.value()));

        ScanningTuning t = ScanningTuning.DEFAULTS;
        Derivation.Result result =
                Derivation.derive(
                        resolved,
                        id -> true,
                        capture.recipes(),
                        new Derivation.Tuning(t.derivationFactor(), t.smeltBonus()));
        SortedMap<String, SortedMap<String, Integer>> all = new TreeMap<>(resolved);
        all.putAll(result.values());

        coverItems(all, result);
        coverBlocks(blocks);
        coverEntities(entities);
        checkExempt(all, blocks, entities);
        checkSemantic(all);
        checkSupply(all, blocks, entities);
        problems.addAll(nonAmplification(all, capture.recipes(), roundTrips));
        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                    "Rejected aspect values ("
                            + problems.size()
                            + "):\n"
                            + String.join("\n", problems));
        }
        return result.values();
    }

    /**
     * The non-amplification gate (aspects spec, "Coverage and supply"): no round trip through the
     * captured recipes, every built-in {@code vftv:arcane} recipe (vis counting as no material
     * input) and the crafting table → Worktable conversion yields more aspects than went in.
     */
    static List<String> nonAmplification(
            Map<String, ? extends SortedMap<String, Integer>> values,
            List<Derivation.Recipe> captured,
            List<Derivation.Recipe> roundTrips) {
        List<Derivation.Recipe> all = new ArrayList<>(captured);
        all.addAll(roundTrips);
        List<String> problems = new ArrayList<>();
        Derivation.amplifying(values, all)
                .forEach(
                        a ->
                                problems.add(
                                        "non-amplification: "
                                                + a
                                                + ". Hand-enter both sides of the conversion as"
                                                + " exact multiples in content/aspects/"));
        return problems;
    }

    private void coverItems(
            SortedMap<String, SortedMap<String, Integer>> all, Derivation.Result result) {
        Set<String> exempted = exempted("items", BuiltInRegistries.ITEM);
        for (Item item : BuiltInRegistries.ITEM) {
            String id = DerivationRecipes.id(item);
            if (item.equals(Items.AIR)
                    || !covered(id)
                    || all.containsKey(id)
                    || exempted.contains(id)) {
                continue;
            }
            String why = "";
            if (result.excluded().containsKey(id)) {
                why = " It is on a conversion cycle " + result.excluded().get(id) + ".";
            } else if (result.unresolved().containsKey(id)) {
                Derivation.Blocked b = result.unresolved().get(id);
                why = " Its recipes are blocked by " + b.item() + " (" + b.reason().word() + ").";
            } else if (result.empty().contains(id)) {
                why = " Its recipes derive nothing.";
            }
            problems.add(
                    "coverage: item "
                            + id
                            + " has no aspect value."
                            + why
                            + " Add it under \"items\" in "
                            + file(id)
                            + " (schema vftv:aspects/1), or under \"exempt\".\"items\" with a"
                            + " reason if survival play cannot obtain it.");
        }
    }

    private void coverBlocks(SortedMap<String, Entry> blocks) {
        Set<String> exempted = exempted("blocks", BuiltInRegistries.BLOCK);
        for (Block block : BuiltInRegistries.BLOCK) {
            String id = BuiltInRegistries.BLOCK.getKey(block).toString();
            if (block.equals(Blocks.AIR)
                    || !covered(id)
                    || !block.asItem().equals(Items.AIR)
                    || blocks.containsKey(id)
                    || exempted.contains(id)) {
                continue;
            }
            problems.add(
                    "coverage: itemless block "
                            + id
                            + " has no aspect value. Add it under \"blocks\" in "
                            + file(id)
                            + " (schema vftv:aspects/1), or under \"exempt\".\"blocks\" with"
                            + " a reason.");
        }
    }

    private void coverEntities(SortedMap<String, Entry> entities) {
        Set<String> exempted = exempted("entities", BuiltInRegistries.ENTITY_TYPE);
        BuiltInRegistries.ENTITY_TYPE
                .keySet()
                .forEach(
                        key -> {
                            String id = key.toString();
                            if (covered(id)
                                    && !entities.containsKey(id)
                                    && !exempted.contains(id)) {
                                problems.add(
                                        "coverage: entity "
                                                + id
                                                + " has no aspect value. Add it under"
                                                + " \"entities\" in "
                                                + file(id)
                                                + " (schema vftv:aspects/1), or under"
                                                + " \"exempt\".\"entities\" with a reason.");
                            }
                        });
    }

    private void checkExempt(
            Map<String, SortedMap<String, Integer>> items,
            Map<String, Entry> blocks,
            Map<String, Entry> entities) {
        exempted("items", BuiltInRegistries.ITEM).stream()
                .filter(items::containsKey)
                .forEach(id -> problems.add("exempt: item " + id + " is exempt but has a value"));
        exempted("blocks", BuiltInRegistries.BLOCK).stream()
                .filter(blocks::containsKey)
                .forEach(id -> problems.add("exempt: block " + id + " is exempt but has a value"));
        exempted("entities", BuiltInRegistries.ENTITY_TYPE).stream()
                .filter(entities::containsKey)
                .forEach(id -> problems.add("exempt: entity " + id + " is exempt but has a value"));
    }

    private void checkSemantic(Map<String, SortedMap<String, Integer>> items) {
        Map<String, String> required = new TreeMap<>();
        SEMANTIC.forEach(
                (tag, aspect) ->
                        BuiltInRegistries.ITEM
                                .getTagOrEmpty(tag)
                                .forEach(
                                        h ->
                                                required.put(
                                                        DerivationRecipes.id(h.value()), aspect)));
        SEMANTIC_ITEMS.forEach((item, aspect) -> required.put(DerivationRecipes.id(item), aspect));
        required.forEach(
                (id, aspect) -> {
                    SortedMap<String, Integer> value = items.get(id);
                    if (covered(id) && value != null && !value.containsKey(aspect)) {
                        problems.add(
                                "semantic: "
                                        + id
                                        + " must hold "
                                        + aspect
                                        + "; hand-enter it in content/aspects/");
                    }
                });
    }

    private void checkSupply(
            SortedMap<String, SortedMap<String, Integer>> items,
            SortedMap<String, Entry> blocks,
            SortedMap<String, Entry> entities) {
        Map<String, Set<String>> sources = new TreeMap<>();
        items.forEach((id, v) -> v.keySet().forEach(a -> source(sources, a, "item:" + id)));
        blocks.forEach(
                (id, e) -> e.value().keySet().forEach(a -> source(sources, a, "block:" + id)));
        entities.forEach(
                (id, e) -> e.value().keySet().forEach(a -> source(sources, a, "entity:" + id)));
        BuiltinAspects.ALL.keySet().stream()
                .map(AspectSet::text)
                .sorted()
                .forEach(
                        aspect -> {
                            int n = sources.getOrDefault(aspect, Set.of()).size();
                            if (n < MIN_SOURCES) {
                                problems.add(
                                        "supply: "
                                                + aspect
                                                + " has "
                                                + n
                                                + " sources, at least "
                                                + MIN_SOURCES
                                                + " needed");
                            }
                        });
    }

    private static void source(Map<String, Set<String>> sources, String aspect, String object) {
        sources.computeIfAbsent(aspect, a -> new TreeSet<>()).add(object);
    }

    private <T> Set<String> exempted(String table, Registry<T> registry) {
        Set<String> ids = new TreeSet<>();
        exempt.getOrDefault(table, new TreeMap<>())
                .keySet()
                .forEach(
                        key -> {
                            List<String> named = ids(key, registry);
                            if (named.isEmpty()) {
                                problems.add("exempt: " + table + " key " + key + " names nothing");
                            }
                            ids.addAll(named);
                        });
        return ids;
    }

    private static boolean covered(String id) {
        return COVERED.contains(id.substring(0, id.indexOf(':')));
    }

    /**
     * The file a missing value belongs in: vanilla's base table, or the file of the VFTV feature
     * that adds the object.
     */
    private static String file(String id) {
        return id.startsWith("minecraft:")
                ? "content/aspects/vanilla/base.json"
                : "content/aspects/<feature>.json, the file of the feature that adds it (e.g."
                        + " content/aspects/research.json)";
    }

    private static String where(String file, String table, String key) {
        return AspectData.DIR + "/" + file + ": $." + table + "." + key;
    }
}
