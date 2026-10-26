package com.andrewbrogoff.vftv.datagen;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.api.vis.VisKind;
import com.andrewbrogoff.vftv.core.arcane.ArcaneContent;
import com.andrewbrogoff.vftv.core.arcane.ArcaneRecipe;
import com.andrewbrogoff.vftv.core.aspect.model.Derivation;
import com.andrewbrogoff.vftv.core.common.model.Schema;
import com.andrewbrogoff.vftv.core.common.model.StrictJson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.Tags;
import org.jspecify.annotations.Nullable;

/**
 * The {@code vftv:arcane} recipe body in authored content (arcane spec, "Authored content"), for
 * every owner whose content holds arcane recipes: embed {@link #BODY} in the owner's schema, run
 * {@link #check} on each body, and {@link #write} it to {@code data/<ns>/recipe/<path>.json}.
 */
public final class ArcaneBodies {
    /** A namespaced id. */
    public static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    /** {@code ordinary} and {@code void}. */
    private static final Set<String> KINDS =
            Stream.of(VisKind.values())
                    .map(k -> k.name().toLowerCase(Locale.ROOT))
                    .collect(Collectors.toUnmodifiableSet());

    private static final Schema.Str INGREDIENT =
            new Schema.Str(3, 257, Pattern.compile("#?" + ID.pattern()));

    /**
     * One arcane body: every field of the recipe type's table and nothing else ({@code group},
     * {@code category} and {@code show_notification} included). In content an ingredient is one
     * item id or {@code #tag}, and a result has no components.
     */
    public static final Schema.Obj BODY =
            new Schema.Obj(
                    List.of(
                            Schema.required("type", new Schema.OneOf(Set.of("vftv:arcane"))),
                            Schema.required("research", new Schema.Id()),
                            Schema.optional("kind", new Schema.OneOf(KINDS)),
                            Schema.optional(
                                    "pattern",
                                    new Schema.ListOf(new Schema.Str(1, 3, null), 1, 3, false)),
                            Schema.optional(
                                    "key",
                                    new Schema.MapOf(new Schema.Str(1, 1, null), INGREDIENT, 1, 9)),
                            Schema.optional(
                                    "ingredients", new Schema.ListOf(INGREDIENT, 1, 9, false)),
                            Schema.required(
                                    "result",
                                    new Schema.Obj(
                                            List.of(
                                                    Schema.required("id", new Schema.Id()),
                                                    Schema.optional(
                                                            "count", new Schema.Int(1, 99))))),
                            Schema.required(
                                    "vis",
                                    new Schema.MapOf(
                                            new Schema.Str(3, 256, ID),
                                            new Schema.Int(1, 1000),
                                            1,
                                            8))));

    /**
     * The compatibility spec's ingredient widening ("Consumed"): an ingredient naming exactly one
     * of these items is written as its convention tag.
     */
    static final Map<String, TagKey<Item>> WIDENED =
            Map.ofEntries(
                    Map.entry("minecraft:copper_ingot", Tags.Items.INGOTS_COPPER),
                    Map.entry("minecraft:iron_ingot", Tags.Items.INGOTS_IRON),
                    Map.entry("minecraft:gold_ingot", Tags.Items.INGOTS_GOLD),
                    Map.entry("minecraft:netherite_ingot", Tags.Items.INGOTS_NETHERITE),
                    Map.entry("minecraft:iron_nugget", Tags.Items.NUGGETS_IRON),
                    Map.entry("minecraft:gold_nugget", Tags.Items.NUGGETS_GOLD),
                    Map.entry("minecraft:diamond", Tags.Items.GEMS_DIAMOND),
                    Map.entry("minecraft:emerald", Tags.Items.GEMS_EMERALD),
                    Map.entry("minecraft:amethyst_shard", Tags.Items.GEMS_AMETHYST),
                    Map.entry("minecraft:quartz", Tags.Items.GEMS_QUARTZ),
                    Map.entry("minecraft:lapis_lazuli", Tags.Items.GEMS_LAPIS),
                    Map.entry("minecraft:redstone", Tags.Items.DUSTS_REDSTONE),
                    Map.entry("minecraft:glowstone_dust", Tags.Items.DUSTS_GLOWSTONE));

    private ArcaneBodies() {}

    /**
     * The rules a {@link #BODY}-valid body must also meet: exactly one of {@code pattern}+{@code
     * key} or {@code ingredients}, then the recipe type's load validation through its codec (the
     * pattern rules, known items and tags, the result) with each {@code vis} key a vis aspect of
     * the generated aspect registry, and {@code research} one of the authored research entries
     * (generated JSON, so not in the datagen lookup).
     *
     * @param research every research entry id in {@code content/research}
     * @return the problems, each naming the recipe; empty when the body is valid
     */
    public static List<String> check(
            String id, JsonObject body, HolderLookup.Provider registries, Set<String> research) {
        String where = "recipe " + id + ": ";
        boolean shaped = body.has("pattern") || body.has("key");
        if (shaped == body.has("ingredients")) {
            return List.of(where + "exactly one of pattern+key or ingredients is required");
        }
        List<String> problems = new ArrayList<>();
        // Datagen's lookup binds no tags, so a #tag stands in as a plain item here; the runtime
        // decode resolves the real tag.
        JsonObject untagged =
                mapIngredients(
                        body,
                        i ->
                                i.getAsString().startsWith("#")
                                        ? new JsonPrimitive("minecraft:stick")
                                        : i);
        ArcaneRecipe.BODY_CODEC
                .codec()
                .parse(registries.createSerializationContext(JsonOps.INSTANCE), untagged)
                .ifError(e -> problems.add(where + e.message()))
                .ifSuccess(
                        recipe -> {
                            if (!research.contains(recipe.research().identifier().toString())) {
                                problems.add(
                                        where
                                                + "unknown research entry "
                                                + recipe.research().identifier());
                            }
                            Optional<? extends HolderLookup.RegistryLookup<Aspect>> aspects =
                                    registries.lookup(AspectKeys.REGISTRY);
                            for (ResourceKey<Aspect> key : recipe.vis().keySet()) {
                                boolean vis =
                                        aspects.flatMap(a -> a.get(key))
                                                .filter(a -> ArcaneRecipe.isVisAspect(a.value()))
                                                .isPresent();
                                if (!vis) {
                                    problems.add(where + key.identifier() + " is not a vis aspect");
                                }
                            }
                            if (recipe.result().item().areComponentsBound()) {
                                recipe.createResult()
                                        .ifError(e -> problems.add(where + e.message()));
                            }
                        });
        return problems;
    }

    /** A copy of the body with the ingredient widening applied to {@code key} and ingredients. */
    public static JsonObject widen(JsonObject body) {
        return mapIngredients(body, ArcaneBodies::widen);
    }

    /** A copy of the body with {@code change} applied to each {@code key} value and ingredient. */
    private static JsonObject mapIngredients(JsonObject body, UnaryOperator<JsonElement> change) {
        JsonObject copy = body.deepCopy();
        if (copy.get("key") instanceof JsonObject key) {
            for (Map.Entry<String, JsonElement> entry : key.entrySet()) {
                entry.setValue(change.apply(entry.getValue()));
            }
        }
        if (copy.get("ingredients") instanceof JsonArray ingredients) {
            for (int i = 0; i < ingredients.size(); i++) {
                ingredients.set(i, change.apply(ingredients.get(i)));
            }
        }
        return copy;
    }

    /** Writes the widened body as the recipe {@code id}: {@code data/<ns>/recipe/<path>.json}. */
    public static CompletableFuture<?> write(
            CachedOutput cache, PackOutput output, String id, JsonObject body) {
        return DataProvider.saveStable(
                cache,
                widen(body),
                output.createRegistryElementsPathProvider(Registries.RECIPE)
                        .json(Identifier.parse(id)));
    }

    /** The crafting table → Worktable conversion's id in the non-amplification report. */
    static final String CONVERSION = "vftv:worktable_conversion";

    /**
     * The non-amplification gate's arcane round trips (aspects spec, "Coverage and supply"): every
     * {@code vftv:arcane} body under {@code content/}, with vis counted as no material input, and
     * the crafting table → Worktable conversion. A {@code #tag} ingredient expands to the members
     * bound in the static item registry.
     *
     * @param content the {@code content/} directory
     */
    static List<Derivation.Recipe> roundTrips(Path content) {
        List<Derivation.Recipe> trips = new ArrayList<>();
        bodies(content).forEach((id, body) -> trips.add(roundTrip(id, body)));
        trips.add(
                new Derivation.Recipe(
                        CONVERSION,
                        List.of(
                                List.of(
                                        new Derivation.Alternative(
                                                "minecraft:crafting_table", null))),
                        ArcaneContent.WORKTABLE_ITEM_KEY.identifier().toString(),
                        1,
                        false));
        return trips;
    }

    /** Every {@code vftv:arcane} body under {@code content/}, by recipe id, in file order. */
    static Map<String, JsonObject> bodies(Path content) {
        Map<String, JsonObject> bodies = new LinkedHashMap<>();
        List<Path> files;
        try (Stream<Path> walk = Files.walk(content)) {
            files = walk.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        for (Path file : files) {
            JsonElement json;
            try {
                json = StrictJson.parse(Files.readString(file));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            if (json instanceof JsonObject root && root.get("recipes") instanceof JsonObject all) {
                all.asMap()
                        .forEach(
                                (id, body) -> {
                                    if (body instanceof JsonObject b
                                            && "vftv:arcane".equals(string(b, "type"))) {
                                        bodies.put(id, b);
                                    }
                                });
            }
        }
        return bodies;
    }

    /** One arcane body as the derivation's recipe: every occupied cell is one ingredient. */
    static Derivation.Recipe roundTrip(String id, JsonObject body) {
        List<String> cells = new ArrayList<>();
        if (body.get("pattern") instanceof JsonArray rows
                && body.get("key") instanceof JsonObject key) {
            for (JsonElement row : rows) {
                for (char symbol : row.getAsString().toCharArray()) {
                    if (symbol != ' ') {
                        cells.add(key.get(String.valueOf(symbol)).getAsString());
                    }
                }
            }
        } else if (body.get("ingredients") instanceof JsonArray ingredients) {
            ingredients.forEach(i -> cells.add(i.getAsString()));
        }
        List<List<Derivation.Alternative>> ingredients = new ArrayList<>();
        for (String cell : cells) {
            List<Derivation.Alternative> alternatives = new ArrayList<>();
            AspectTable.ids(cell, BuiltInRegistries.ITEM).stream()
                    .sorted()
                    .forEach(item -> alternatives.add(new Derivation.Alternative(item, null)));
            if (alternatives.isEmpty()) {
                // An unbound tag would make the gate skip this round trip unchecked.
                throw new IllegalStateException(
                        "Arcane recipe " + id + ": ingredient " + cell + " names no items");
            }
            ingredients.add(alternatives);
        }
        JsonObject result = body.getAsJsonObject("result");
        return new Derivation.Recipe(
                id,
                ingredients,
                result.get("id").getAsString(),
                result.has("count") ? result.get("count").getAsInt() : 1,
                false);
    }

    private static @Nullable String string(JsonObject object, String field) {
        return object.get(field) instanceof JsonPrimitive p && p.isString()
                ? p.getAsString()
                : null;
    }

    private static JsonElement widen(JsonElement ingredient) {
        if (ingredient instanceof JsonPrimitive p && p.isString()) {
            TagKey<Item> tag = WIDENED.get(p.getAsString());
            if (tag != null) {
                return new JsonPrimitive("#" + tag.location());
            }
        }
        return ingredient;
    }
}
