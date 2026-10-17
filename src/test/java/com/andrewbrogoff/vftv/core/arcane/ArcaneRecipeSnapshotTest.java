package com.andrewbrogoff.vftv.core.arcane;

import static com.andrewbrogoff.vftv.core.arcane.ArcaneTestData.ENTRY;
import static com.andrewbrogoff.vftv.core.arcane.ArcaneTestData.OTHER;
import static com.andrewbrogoff.vftv.core.arcane.ArcaneTestData.grid;
import static com.andrewbrogoff.vftv.core.arcane.ArcaneTestData.holder;
import static com.andrewbrogoff.vftv.core.arcane.ArcaneTestData.shaped;
import static com.andrewbrogoff.vftv.core.arcane.ArcaneTestData.shapeless;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andrewbrogoff.vftv.api.arcane.ArcaneLimits;
import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.api.research.Research;
import com.andrewbrogoff.vftv.api.vis.VisKind;
import com.mojang.datafixers.util.Either;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import org.junit.jupiter.api.Test;

class ArcaneRecipeSnapshotTest {
    static {
        ArcaneTestData.registries(); // binds item components before any stack is made
    }

    @SuppressWarnings("UnnecessaryLambda")
    private static final Predicate<ResourceKey<Research>> ALL = r -> true;

    private static final ItemStack E = ItemStack.EMPTY;
    private static final ItemStack STICK = new ItemStack(Items.STICK);
    private static final ItemStack COPPER = new ItemStack(Items.COPPER_INGOT);

    private static ArcaneRecipe stickOverCopper(ResourceKey<Research> research) {
        return shaped(
                research,
                Items.BELL,
                Map.of('s', Ingredient.of(Items.STICK), 'c', Ingredient.of(Items.COPPER_INGOT)),
                "s",
                "c");
    }

    private static ArcaneRecipeSnapshot snapshot(List<RecipeHolder<ArcaneRecipe>> holders) {
        return ArcaneRecipeSnapshot.of(holders).getOrThrow();
    }

    @SuppressWarnings("NullAway") // vanilla's matchers take the level and ignore it
    private static String selected(
            ArcaneRecipeSnapshot snapshot,
            Predicate<ResourceKey<Research>> learned,
            ItemStack... cells) {
        return snapshot.select(grid(cells), null, learned)
                .map(h -> h.id().identifier().toString())
                .orElse("none");
    }

    @Test
    void sortsById() {
        ArcaneRecipeSnapshot s =
                snapshot(
                        List.of(
                                holder("b", stickOverCopper(ENTRY)),
                                holder("a/z", stickOverCopper(ENTRY)),
                                holder("a", stickOverCopper(ENTRY))));
        assertEquals(
                List.of("vftv:a", "vftv:a/z", "vftv:b"),
                s.recipes().stream().map(h -> h.id().identifier().toString()).toList());
    }

    @Test
    void lowestLearnedIdWins() {
        ArcaneRecipeSnapshot s =
                snapshot(
                        List.of(
                                holder("c", stickOverCopper(ENTRY)),
                                holder("a", stickOverCopper(OTHER)),
                                holder("b", stickOverCopper(ENTRY))));
        assertEquals("vftv:a", selected(s, ALL, STICK, E, E, COPPER));
        // An unlearned overlapping recipe never hides a learned one.
        assertEquals("vftv:b", selected(s, ENTRY::equals, STICK, E, E, COPPER));
        assertEquals("none", selected(s, r -> false, STICK, E, E, COPPER));
        // The input is trimmed, so the shape matches anywhere in the grid.
        assertEquals("vftv:a", selected(s, ALL, E, E, STICK, E, E, COPPER));
        assertEquals("none", selected(s, ALL, COPPER, E, E, STICK));
        assertEquals("none", selected(s, ALL));
    }

    @Test
    void shapedMatchesItsMirror() {
        ArcaneRecipeSnapshot s =
                snapshot(
                        List.of(
                                holder(
                                        "l",
                                        shaped(
                                                ENTRY,
                                                Items.BELL,
                                                Map.of(
                                                        's', Ingredient.of(Items.STICK),
                                                        'c', Ingredient.of(Items.COPPER_INGOT)),
                                                "sc",
                                                "s "))));
        assertEquals("vftv:l", selected(s, ALL, STICK, COPPER, E, STICK));
        assertEquals("vftv:l", selected(s, ALL, COPPER, STICK, E, E, STICK));
        assertEquals("none", selected(s, ALL, STICK, COPPER, E, E, STICK));
    }

    @Test
    void shapelessWithACustomIngredient() {
        ItemStack named = STICK.copy();
        named.set(DataComponents.CUSTOM_NAME, Component.literal("tuned"));
        Ingredient tunedStick =
                DataComponentIngredient.of(
                        false, DataComponents.CUSTOM_NAME, Component.literal("tuned"), Items.STICK);
        assertTrue(!tunedStick.isSimple(), "exercises NeoForge's matcher");
        ArcaneRecipeSnapshot s =
                snapshot(
                        List.of(
                                holder(
                                        "s",
                                        shapeless(
                                                ENTRY,
                                                Items.BELL,
                                                tunedStick,
                                                Ingredient.of(Items.COPPER_INGOT)))));
        assertEquals("vftv:s", selected(s, ALL, COPPER, E, E, E, named));
        assertEquals("none", selected(s, ALL, COPPER, E, E, E, STICK));
        assertEquals("none", selected(s, ALL, COPPER, named, named));
    }

    private static List<RecipeHolder<ArcaneRecipe>> many(int count) {
        List<RecipeHolder<ArcaneRecipe>> holders = new ArrayList<>(count);
        ArcaneRecipe recipe = stickOverCopper(ENTRY);
        for (int i = 0; i < count; i++) {
            holders.add(holder("r" + i, recipe));
        }
        return holders;
    }

    @Test
    void theCapRejectsTheSet() {
        assertEquals(
                ArcaneLimits.MAX_ARCANE_RECIPES,
                snapshot(many(ArcaneLimits.MAX_ARCANE_RECIPES)).recipes().size());
        String message =
                ArcaneRecipeSnapshot.of(many(ArcaneLimits.MAX_ARCANE_RECIPES + 1))
                        .error()
                        .orElseThrow()
                        .message();
        assertEquals("4097 arcane recipes exceed the cap of 4096", message);
    }

    @Test
    void aBadResultIsLeftOut() {
        ArcaneRecipe over =
                new ArcaneRecipe(
                        ENTRY,
                        VisKind.ORDINARY,
                        Either.right(List.of(Ingredient.of(Items.STICK))),
                        new ItemStackTemplate(
                                Items.BELL,
                                2,
                                DataComponentPatch.builder()
                                        .set(DataComponents.MAX_STACK_SIZE, 1)
                                        .build()),
                        Map.of(AspectKeys.AER, 1));
        ArcaneRecipeSnapshot s =
                snapshot(List.of(holder("over", over), holder("ok", stickOverCopper(ENTRY))));
        assertEquals(1, s.recipes().size());
        assertEquals("vftv:ok", s.recipes().getFirst().id().identifier().toString());
    }

    /** A recipe manager answering a fixed map, as a loaded one would. */
    private static RecipeManager manager(List<RecipeHolder<ArcaneRecipe>> holders) {
        RecipeMap map = RecipeMap.create(List.<RecipeHolder<?>>copyOf(holders));
        return new RecipeManager(HolderLookup.Provider.create(Stream.empty())) {
            @Override
            public RecipeMap recipeMap() {
                return map;
            }
        };
    }

    @Test
    void serverStartRefusesOverTheCapAndReloadEmpties() {
        IllegalStateException e =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                ArcaneRecipes.onServerStarting(
                                        manager(many(ArcaneLimits.MAX_ARCANE_RECIPES + 1))));
        assertTrue(String.valueOf(e.getMessage()).contains("4097"), e.getMessage());

        RecipeManager loaded = manager(many(2));
        ArcaneRecipes.onServerStarting(loaded);
        assertEquals(2, ArcaneRecipes.server().recipes().size());
        ArcaneRecipeSnapshot before = ArcaneRecipes.server();
        ArcaneRecipes.beforeSync(loaded);
        assertSame(before, ArcaneRecipes.server(), "an ordinary join keeps the snapshot");
        ArcaneRecipes.beforeSync(manager(many(3)));
        assertEquals(3, ArcaneRecipes.server().recipes().size(), "a reload rebuilds it");
        ArcaneRecipes.beforeSync(manager(many(ArcaneLimits.MAX_ARCANE_RECIPES + 1)));
        assertEquals(List.of(), ArcaneRecipes.server().recipes(), "over the cap: empty");
        assertEquals(ArcaneRecipes.server(), ArcaneRecipes.of(false));
    }

    @Test
    @SuppressWarnings("NullAway") // as in selected
    void clientKeepsWhatItReceivesUntilLogout() {
        ArcaneRecipes.onRecipesReceived(RecipeMap.create(List.<RecipeHolder<?>>copyOf(many(2))));
        assertEquals(2, ArcaneRecipes.client().recipes().size());
        assertEquals(ArcaneRecipes.client(), ArcaneRecipes.of(true));
        ArcaneRecipes.onRecipesReceived(
                RecipeMap.create(
                        List.<RecipeHolder<?>>copyOf(many(ArcaneLimits.MAX_ARCANE_RECIPES + 1))));
        assertEquals(List.of(), ArcaneRecipes.client().recipes(), "over the cap: empty");
        ArcaneRecipes.onRecipesReceived(RecipeMap.create(List.<RecipeHolder<?>>copyOf(many(1))));
        ArcaneRecipes.clearClient();
        assertEquals(ArcaneRecipeSnapshot.EMPTY, ArcaneRecipes.client());
        assertEquals(Optional.empty(), ArcaneRecipes.client().select(grid(STICK), null, ALL));
    }
}
