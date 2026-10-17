package com.andrewbrogoff.vftv.gametest;

import com.andrewbrogoff.vftv.api.arcane.ArcaneLimits;
import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.api.research.ResearchKeys;
import com.andrewbrogoff.vftv.api.vis.VisKind;
import com.andrewbrogoff.vftv.core.arcane.ArcaneRecipe;
import com.andrewbrogoff.vftv.core.arcane.ArcaneRecipeSnapshot;
import com.andrewbrogoff.vftv.core.arcane.ArcaneRecipes;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;

/**
 * The arcane feature's GameTests. Name each test {@code arcane_<what>}, and list its id {@code
 * vftv:arcane_<what>} in {@code src/gameTest/expected/arcane.txt}.
 */
final class ArcaneGameTests {
    private ArcaneGameTests() {}

    /**
     * Adds the feature's tests by name; {@code modBus} is for any test-only registration they need.
     */
    static void register(Map<String, Consumer<GameTestHelper>> tests, IEventBus modBus) {
        tests.put("arcane_type_registered", ArcaneGameTests::typeRegistered);
        tests.put("arcane_snapshot_at_start", ArcaneGameTests::snapshotAtStart);
        tests.put("arcane_sync_sends_recipes", ArcaneGameTests::syncSendsRecipes);
        tests.put("arcane_cap_on_reload", ArcaneGameTests::capOnReload);
        tests.put("arcane_cap_at_start", ArcaneGameTests::capAtStart);
        tests.put("arcane_vis_keys_checked", ArcaneGameTests::visKeysChecked);
    }

    private static DataResult<ArcaneRecipe> decode(GameTestHelper helper, String vis) {
        JsonObject body =
                JsonParser.parseString(
                                ("{'research': 'vftv:commonplace_book',"
                                                + " 'ingredients': ['minecraft:stick'],"
                                                + " 'result': {'id': 'minecraft:bell'},"
                                                + " 'vis': {"
                                                + vis
                                                + "}}")
                                        .replace('\'', '"'))
                        .getAsJsonObject();
        return ArcaneRecipe.MAP_CODEC
                .codec()
                .parse(
                        helper.getLevel()
                                .registryAccess()
                                .createSerializationContext(JsonOps.INSTANCE),
                        body);
    }

    private static String message(DataResult<ArcaneRecipe> result) {
        return result.error().map(DataResult.Error::message).orElse("decoded");
    }

    /**
     * Cost keys are checked against the loaded {@code vftv:aspect} registry: the six built-in
     * primals are vis aspects; a compound, Vacuos (hidden) and an unknown aspect are not.
     */
    private static void visKeysChecked(GameTestHelper helper) {
        for (String key : List.of("vftv:sonus", "vftv:vacuos", "vftv:nowhere")) {
            String found = message(decode(helper, "'" + key + "': 1"));
            helper.assertValueEqual(found, key + " is not a vis aspect", key);
        }
        String primals =
                "'vftv:aer': 1, 'vftv:aqua': 1, 'vftv:ignis': 1, 'vftv:ordo': 1,"
                        + " 'vftv:perditio': 1, 'vftv:terra': 1";
        helper.assertFalse(
                message(decode(helper, primals)).contains("vis aspect"), "the six primals pass");
        helper.succeed();
    }

    /** {@code vftv:arcane} is a recipe type and a serializer. */
    private static void typeRegistered(GameTestHelper helper) {
        Identifier id = ArcaneRecipe.ID;
        helper.assertTrue(
                ArcaneRecipe.TYPE.equals(BuiltInRegistries.RECIPE_TYPE.getValue(id)),
                "the recipe type");
        helper.assertTrue(
                ArcaneRecipe.SERIALIZER.equals(BuiltInRegistries.RECIPE_SERIALIZER.getValue(id)),
                "the serializer");
        helper.succeed();
    }

    /**
     * The server built its snapshot at start from the recipe manager in force, so a sync without a
     * reload (an ordinary join) keeps it.
     */
    @SuppressWarnings("ReferenceEquality") // a rebuild makes an equal but new snapshot
    private static void snapshotAtStart(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ArcaneRecipeSnapshot before = ArcaneRecipes.server();
        helper.assertValueEqual(
                before.recipes().size(),
                server.getRecipeManager().recipeMap().byType(ArcaneRecipe.TYPE).size(),
                "every loaded arcane recipe is in the snapshot");
        ArcaneRecipes.beforeSync(server.getRecipeManager());
        helper.assertTrue(before == ArcaneRecipes.server(), "a join does not rebuild it");
        helper.succeed();
    }

    /** The sync listener asks NeoForge to send {@code vftv:arcane} recipes. */
    private static void syncSendsRecipes(GameTestHelper helper) {
        OnDatapackSyncEvent event =
                NeoForge.EVENT_BUS.post(
                        new OnDatapackSyncEvent(
                                helper.getLevel().getServer().getPlayerList(), null));
        helper.assertTrue(
                event.getRecipeTypesToSend().contains(ArcaneRecipe.TYPE),
                "arcane recipes are sent on join and reload");
        helper.succeed();
    }

    /** A reload over the cap empties the snapshot; the next good reload restores it. */
    private static void capOnReload(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        int loaded = ArcaneRecipes.server().recipes().size();
        try {
            ArcaneRecipes.beforeSync(manager(server, ArcaneLimits.MAX_ARCANE_RECIPES + 1));
            helper.assertValueEqual(
                    ArcaneRecipes.server().recipes(), List.of(), "over the cap: empty");
            ArcaneRecipes.beforeSync(manager(server, ArcaneLimits.MAX_ARCANE_RECIPES));
            helper.assertValueEqual(
                    ArcaneRecipes.server().recipes().size(),
                    ArcaneLimits.MAX_ARCANE_RECIPES,
                    "at the cap: kept");
        } finally {
            ArcaneRecipes.beforeSync(server.getRecipeManager());
        }
        helper.assertValueEqual(ArcaneRecipes.server().recipes().size(), loaded, "restored");
        helper.succeed();
    }

    /**
     * Over the cap, the start hook throws (the integrated case is E2E). The live snapshot is
     * restored before the tick ends.
     */
    private static void capAtStart(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        boolean refused = false;
        try {
            ArcaneRecipes.onServerStarting(manager(server, ArcaneLimits.MAX_ARCANE_RECIPES + 1));
        } catch (IllegalStateException e) {
            refused = String.valueOf(e.getMessage()).contains("4097 arcane recipes exceed the cap");
        } finally {
            ArcaneRecipes.onServerStarting(server.getRecipeManager());
        }
        helper.assertTrue(refused, "startup fails, naming the count and the cap");
        helper.succeed();
    }

    /** A recipe manager answering {@code count} arcane recipes. */
    private static RecipeManager manager(MinecraftServer server, int count) {
        ArcaneRecipe recipe =
                new ArcaneRecipe(
                        ResearchKeys.COMMONPLACE_BOOK,
                        VisKind.ORDINARY,
                        Either.right(List.of(Ingredient.of(Items.STICK))),
                        new ItemStackTemplate(Items.BELL),
                        Map.of(AspectKeys.AER, 1));
        List<RecipeHolder<?>> holders = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            holders.add(
                    new RecipeHolder<>(
                            ResourceKey.create(
                                    Registries.RECIPE,
                                    Identifier.fromNamespaceAndPath(
                                            "vftv_gametest", "arcane/" + i)),
                            recipe));
        }
        RecipeMap map = RecipeMap.create(holders);
        HolderLookup.Provider registries = server.registryAccess();
        return new RecipeManager(registries) {
            @Override
            public RecipeMap recipeMap() {
                return map;
            }
        };
    }
}
