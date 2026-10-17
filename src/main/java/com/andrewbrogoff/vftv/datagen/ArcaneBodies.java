package com.andrewbrogoff.vftv.datagen;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.api.vis.VisKind;
import com.andrewbrogoff.vftv.core.arcane.ArcaneRecipe;
import com.andrewbrogoff.vftv.core.common.model.Schema;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.Tags;

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
     * the generated aspect registry. The {@code research} entry is checked by the runtime decode,
     * since research entries are generated, not bootstrapped.
     *
     * @return the problems, each naming the recipe; empty when the body is valid
     */
    public static List<String> check(String id, JsonObject body, HolderLookup.Provider registries) {
        String where = "recipe " + id + ": ";
        boolean shaped = body.has("pattern") || body.has("key");
        if (shaped == body.has("ingredients")) {
            return List.of(where + "exactly one of pattern+key or ingredients is required");
        }
        List<String> problems = new ArrayList<>();
        ArcaneRecipe.BODY_CODEC
                .codec()
                .parse(registries.createSerializationContext(JsonOps.INSTANCE), body)
                .ifError(e -> problems.add(where + e.message()))
                .ifSuccess(
                        recipe -> {
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
        JsonObject copy = body.deepCopy();
        if (copy.get("key") instanceof JsonObject key) {
            for (Map.Entry<String, JsonElement> entry : key.entrySet()) {
                entry.setValue(widen(entry.getValue()));
            }
        }
        if (copy.get("ingredients") instanceof JsonArray ingredients) {
            for (int i = 0; i < ingredients.size(); i++) {
                ingredients.set(i, widen(ingredients.get(i)));
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
