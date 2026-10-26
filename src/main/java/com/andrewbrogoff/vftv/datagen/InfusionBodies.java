package com.andrewbrogoff.vftv.datagen;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.api.infusion.InfusionLimits;
import com.andrewbrogoff.vftv.core.aspect.model.Derivation;
import com.andrewbrogoff.vftv.core.common.model.Schema;
import com.andrewbrogoff.vftv.core.common.model.StrictJson;
import com.andrewbrogoff.vftv.core.infusion.InfusionRecipe;
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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
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

/**
 * The {@code vftv:infusion} recipe body in authored content (infusion spec, "The recipe type"), for
 * every owner whose content holds infusion recipes (Hush, Eldritch, Armor): embed {@link #BODY} in
 * the owner's schema, run {@link #check} on each body, and {@link #write} it to {@code
 * data/<ns>/recipe/<path>.json}. {@link #roundTrips} feeds the non-amplification gate.
 */
public final class InfusionBodies {
    private static final Schema.Str INGREDIENT =
            new Schema.Str(3, 257, Pattern.compile("#?" + ArcaneBodies.ID.pattern()));

    /**
     * One infusion body: every field of the recipe type's table and nothing else. In content an
     * ingredient is one item id or {@code #tag}, and an item result has no components.
     */
    public static final Schema.Obj BODY =
            new Schema.Obj(
                    List.of(
                            Schema.required("type", new Schema.OneOf(Set.of("vftv:infusion"))),
                            Schema.required("research", new Schema.Id()),
                            Schema.optional("kind", new Schema.OneOf(Set.of("ordinary", "void"))),
                            Schema.required("centre", INGREDIENT),
                            Schema.required(
                                    "components",
                                    new Schema.ListOf(
                                            INGREDIENT, 1, InfusionLimits.MAX_COMPONENTS, false)),
                            Schema.required(
                                    "essentia",
                                    new Schema.MapOf(
                                            new Schema.Str(3, 256, ArcaneBodies.ID),
                                            new Schema.Int(
                                                    InfusionLimits.MIN_COST,
                                                    InfusionLimits.MAX_COST),
                                            1,
                                            InfusionLimits.MAX_COST_ENTRIES)),
                            Schema.required(
                                    "instability",
                                    new Schema.Int(0, InfusionLimits.MAX_INSTABILITY)),
                            Schema.required(
                                    "result",
                                    new Schema.Obj(
                                            List.of(
                                                    Schema.optional(
                                                            "item",
                                                            new Schema.Obj(
                                                                    List.of(
                                                                            Schema.required(
                                                                                    "id",
                                                                                    new Schema.Id()),
                                                                            Schema.optional(
                                                                                    "count",
                                                                                    new Schema.Int(
                                                                                            1,
                                                                                            1))))),
                                                    Schema.optional("transmute", new Schema.Bool()),
                                                    Schema.optional("enchantment", new Schema.Id()),
                                                    Schema.optional(
                                                            "max_level", new Schema.Int(1, 255)))))));

    private InfusionBodies() {}

    /**
     * The rules a {@link #BODY}-valid body must also meet: exactly one result form ({@code
     * transmute} only with an item, {@code max_level} only with an enchantment), then the recipe
     * type's load validation through its codec with every essentia key an aspect of the generated
     * registry, the enchanting bound, and {@code research} one of the authored entries.
     *
     * @param research every research entry id in {@code content/research}
     * @return the problems, each naming the recipe; empty when the body is valid
     */
    public static List<String> check(
            String id, JsonObject body, HolderLookup.Provider registries, Set<String> research) {
        String where = "recipe " + id + ": ";
        JsonObject result = body.getAsJsonObject("result");
        if (result.has("item") == result.has("enchantment")) {
            return List.of(where + "the result is exactly one of item or enchantment");
        }
        if (result.has("item") && result.has("max_level")) {
            return List.of(where + "max_level belongs to an enchantment result");
        }
        if (result.has("enchantment") && (result.has("transmute") || !result.has("max_level"))) {
            return List.of(where + "an enchantment result takes max_level and no transmute");
        }
        List<String> problems = new ArrayList<>();
        JsonObject untagged = mapIngredients(body, true);
        InfusionRecipe.BODY_CODEC
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
                            var aspects = registries.lookup(AspectKeys.REGISTRY);
                            for (ResourceKey<Aspect> key : recipe.essentia().keySet()) {
                                if (aspects.flatMap(a -> a.get(key)).isEmpty()) {
                                    problems.add(where + "unknown aspect " + key.identifier());
                                }
                            }
                            if (!(recipe.result() instanceof InfusionRecipe.Result.Item item)
                                    || item.item().item().areComponentsBound()) {
                                recipe.checkResult().ifError(e -> problems.add(where + e.message()));
                            }
                            recipe.checkEnchantBound().ifError(e -> problems.add(where + e.message()));
                        });
        return problems;
    }

    /** Writes the widened body as the recipe {@code id}: {@code data/<ns>/recipe/<path>.json}. */
    public static CompletableFuture<?> write(
            CachedOutput cache, PackOutput output, String id, JsonObject body) {
        return DataProvider.saveStable(
                cache,
                mapIngredients(body, false),
                output.createRegistryElementsPathProvider(Registries.RECIPE)
                        .json(Identifier.parse(id)));
    }

    /**
     * A copy with every ingredient changed: a {@code #tag} stood in by a plain item for datagen's
     * tag-free lookup ({@code untag}), or the compatibility spec's widening applied.
     */
    private static JsonObject mapIngredients(JsonObject body, boolean untag) {
        JsonObject copy = body.deepCopy();
        copy.add("centre", map(copy.get("centre"), untag));
        JsonArray components = copy.getAsJsonArray("components");
        for (int i = 0; i < components.size(); i++) {
            components.set(i, map(components.get(i), untag));
        }
        return copy;
    }

    private static JsonElement map(JsonElement ingredient, boolean untag) {
        String text = ingredient.getAsString();
        if (untag) {
            return text.startsWith("#") ? new JsonPrimitive("minecraft:stick") : ingredient;
        }
        TagKey<Item> tag = ArcaneBodies.WIDENED.get(text);
        return tag == null ? ingredient : new JsonPrimitive("#" + tag.location());
    }

    /**
     * The non-amplification gate's infusion round trips (infusion spec, contract on the aspect
     * table): every built-in {@code vftv:infusion} body with a plain item result, the centre and
     * components as its ingredients. Enchanting and transmuting add no value and are skipped; the
     * essentia is left out, so the gate is the stricter for it.
     *
     * @param content the {@code content/} directory
     */
    static List<Derivation.Recipe> roundTrips(Path content) {
        List<Derivation.Recipe> trips = new ArrayList<>();
        bodies(content)
                .forEach(
                        (id, body) -> {
                            JsonObject result = body.getAsJsonObject("result");
                            if (!result.has("item")
                                    || (result.has("transmute")
                                            && result.get("transmute").getAsBoolean())) {
                                return;
                            }
                            List<List<Derivation.Alternative>> ingredients = new ArrayList<>();
                            List<String> cells = new ArrayList<>();
                            cells.add(body.get("centre").getAsString());
                            body.getAsJsonArray("components").forEach(c -> cells.add(c.getAsString()));
                            for (String cell : cells) {
                                List<Derivation.Alternative> alternatives = new ArrayList<>();
                                AspectTable.ids(cell, BuiltInRegistries.ITEM).stream()
                                        .sorted()
                                        .forEach(i -> alternatives.add(new Derivation.Alternative(i, null)));
                                if (alternatives.isEmpty()) {
                                    throw new IllegalStateException(
                                            "Infusion recipe " + id + ": ingredient " + cell + " names no items");
                                }
                                ingredients.add(alternatives);
                            }
                            trips.add(
                                    new Derivation.Recipe(
                                            id,
                                            ingredients,
                                            result.getAsJsonObject("item").get("id").getAsString(),
                                            1,
                                            false));
                        });
        return trips;
    }

    /** Every {@code vftv:infusion} body under {@code content/}, by recipe id, in file order. */
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
                                            && b.get("type") instanceof JsonPrimitive t
                                            && "vftv:infusion".equals(t.getAsString())) {
                                        bodies.put(id, b);
                                    }
                                });
            }
        }
        return bodies;
    }
}
