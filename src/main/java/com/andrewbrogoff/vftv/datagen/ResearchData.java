package com.andrewbrogoff.vftv.datagen;

import com.andrewbrogoff.vftv.api.research.Research;
import com.andrewbrogoff.vftv.api.research.ResearchKeys;
import com.andrewbrogoff.vftv.core.Vftv;
import com.andrewbrogoff.vftv.core.aspect.AspectContent;
import com.andrewbrogoff.vftv.core.aspect.StrikeRecipe;
import com.andrewbrogoff.vftv.core.research.ResearchCodecs;
import com.andrewbrogoff.vftv.core.research.ResearchConfig;
import com.andrewbrogoff.vftv.core.research.ResearchItems;
import com.andrewbrogoff.vftv.core.research.ResearchService;
import com.andrewbrogoff.vftv.core.research.model.EntryRules;
import com.andrewbrogoff.vftv.core.research.model.ResearchTuning;
import com.andrewbrogoff.vftv.core.research.model.Trigger;
import com.andrewbrogoff.vftv.core.text.model.LangEntry;
import com.andrewbrogoff.vftv.core.warp.model.WarpTuning;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.predicates.DataComponentMatchers;
import net.minecraft.advancements.predicates.ItemPredicate;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.advancements.triggers.Criterion;
import net.minecraft.advancements.triggers.ImpossibleTrigger;
import net.minecraft.advancements.triggers.InventoryChangeTrigger;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentExactPredicate;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * The research feature's data generation: override the {@link FeatureData} hooks it needs. Listed
 * once in {@link VftvData#FEATURES}.
 */
final class ResearchData implements FeatureData {
    /** The book's code-defined UI keys' file for translations (research spec, "Book UI keys"). */
    private static final String UI = "research_ui.json";

    /** The configuration spec's file for these keys' translations. */
    private static final String CONFIG = "config.json";

    /**
     * The book's code-defined UI keys, whose English is final, then key 1's config keys, then the
     * rows the authored research content declares.
     */
    @Override
    public List<LangEntry> lang() {
        List<LangEntry> rows = new ArrayList<>(uiKeys());
        rows.add(LangEntry.codeDefined("options.vftv.research", 0, CONFIG, "Research"));
        rows.add(
                LangEntry.codeDefined(
                        "options.vftv.research.mode", 0, CONFIG, "Research difficulty"));
        rows.add(
                LangEntry.codeDefined(
                        "options.vftv.research.mode.tooltip",
                        0,
                        CONFIG,
                        ResearchConfig.MODE_COMMENT));
        rows.add(LangEntry.codeDefined("options.vftv.research.mode.gentle", 0, CONFIG, "Gentle"));
        rows.add(
                LangEntry.codeDefined(
                        "options.vftv.research.mode.standard", 0, CONFIG, "Standard"));
        rows.add(LangEntry.codeDefined("options.vftv.research.mode.hard", 0, CONFIG, "Hard"));
        rows.add(
                LangEntry.codeDefined(
                        "options.vftv.research.mode.skip",
                        0,
                        CONFIG,
                        "Skip: buy research with points"));
        rows.addAll(ResearchContent.lang(content()));
        return rows;
    }

    private static List<LangEntry> uiKeys() {
        List<LangEntry> rows = new ArrayList<>();
        Object[][] table = {
            {"item.vftv.commonplace_book", 0, "Commonplace Book"},
            {"item.vftv.choir_page", 0, "Choir Page"},
            {"book.vftv.ledger", 0, "Ledger"},
            {"book.vftv.search", 0, "Search my notes"},
            {"book.vftv.search.none", 0, "Nothing in my notes by that name."},
            {"book.vftv.back", 0, "Back"},
            {"book.vftv.page.previous", 0, "Previous page"},
            {"book.vftv.page.next", 0, "Next page"},
            {"book.vftv.page", 2, "Page %s of %s"},
            {"book.vftv.bookmark.set", 0, "Keep my place here"},
            {"book.vftv.bookmark.clear", 0, "Let this place go"},
            {"book.vftv.bookmark.empty", 0, "empty"},
            {"book.vftv.blank", 0, "The pages are blank. I have heard nothing yet."},
            {"book.vftv.missing", 0, "Something is missing from this page."},
            {"book.vftv.recipe.preview", 0, "Not yet within my reach"},
            {"book.vftv.kind.free", 0, "an entry"},
            {"book.vftv.kind.find", 0, "a find"},
            {"book.vftv.kind.note", 0, "a note"},
            {"book.vftv.kind.listen", 0, "a listening"},
            {"book.vftv.kind.opener", 0, "the way into a new chapter"},
            {"book.vftv.state.locked", 0, "not yet open"},
            {"book.vftv.state.open", 0, "open"},
            {"book.vftv.state.learned", 0, "learned"},
            {"book.vftv.hint.parents", 1, "First: %s."},
            {"book.vftv.hint.hidden", 0, "Something else must come first."},
            {"book.vftv.hint.anchors", 2, "I can't yet hear how %s meets %s."},
            {"book.vftv.unheard", 0, "something unheard"},
            {"book.vftv.action.open_note", 0, "Open the note"},
            {"book.vftv.action.begin", 0, "Begin the note"},
            {"book.vftv.action.learn", 0, "Learn"},
            {"book.vftv.action.abandon", 0, "Abandon"},
            {"book.vftv.action.buy", 0, "Buy"},
            {"book.vftv.action.listen", 0, "Listen"},
            {"book.vftv.action.learn_cost", 1, "Learning spends %s."},
            {"book.vftv.action.price", 1, "It costs %s."},
            {"book.vftv.action.warp", 1, "It will cost me %s."},
            {"book.vftv.action.solved", 0, "The note is solved."},
            {"book.vftv.confirm.title", 0, "Am I certain?"},
            {"book.vftv.confirm.confirm", 0, "Confirm"},
            {"book.vftv.confirm.cancel", 0, "Cancel"},
            {"ledger.vftv.pools", 0, "What I hold"},
            {"ledger.vftv.notes", 0, "Open notes"},
            {"ledger.vftv.known", 0, "What I have heard"},
            {"ledger.vftv.pool", 2, "%s: %s"},
            {"message.vftv.research.not_flat", 0, "This note will not lie flat in my book."},
            {"message.vftv.research.short", 1, "Not enough %s."},
            {"message.vftv.research.already_open", 0, "That note is already open in my book."},
            {"message.vftv.research.no_room", 0, "My book has no room for another open note."},
            {"message.vftv.research.stale", 0, "My notes have moved on. I look again."},
            {"message.vftv.research.not_now", 0, "I can't do that yet."},
            {"message.vftv.research.pending", 0, "Waiting for an answer."},
            {"message.vftv.choir_page.blank", 0, "The page is blank to me."},
            {"message.vftv.choir_page.too_old", 0, "The hand is too old for me yet."},
            {"message.vftv.choir_page.already", 0, "Already in my book."},
            {"message.vftv.choir_page.tipped", 0, "Tipped in."},
            {"narration.vftv.book.entry", 4, "%s: %s, %s. %s"},
            {"narration.vftv.book.cell", 4, "Cell %s, %s: %s. Linked to %s."},
            {"book.vftv.cell.empty", 0, "empty"},
            {"book.vftv.cell.silent", 0, "silent"},
            {"book.vftv.cell.no_links", 0, "nothing"},
            {"book.vftv.cell.anchor", 1, "the anchor %s"},
            {"book.vftv.cell.placed", 1, "%s, placed"},
            {"book.vftv.cell.unanswered", 1, "%s, unanswered"},
            {"narration.vftv.book.palette", 3, "%s, %s: I hold %s."},
            {"narration.vftv.book.bookmark", 2, "Bookmark %s: %s"},
            {"subtitles.vftv.book.page_turn", 0, "Page turns"},
            {"subtitles.vftv.book.place", 0, "Note placed"},
            {"subtitles.vftv.book.link", 0, "Notes link"},
            {"subtitles.vftv.book.solve", 0, "Note resolves"},
            {"subtitles.vftv.book.learn", 0, "Entry learned"},
            {"subtitles.vftv.book.stage_1", 0, "A new chapter opens"},
            {"subtitles.vftv.book.stage_2", 0, "A new chapter opens"},
            {"subtitles.vftv.book.stage_3", 0, "A new chapter opens"},
            {"subtitles.vftv.book.stage_4", 0, "A new chapter opens"},
            {"subtitles.vftv.book.tip_in", 0, "Page tipped in"},
            {"subtitles.vftv.book.listen", 0, "The silence answers"},
            {"commands.vftv.research.granted", 2, "%s: learned %s."},
            {"commands.vftv.research.revoked", 2, "%s: forgot %s."},
            {"commands.vftv.research.stage", 2, "%s: Stage %s."},
            {"commands.vftv.research.reset", 1, "%s: research reset."},
            {"commands.vftv.research.unknown", 1, "No research entry %s."},
        };
        for (Object[] row : table) {
            rows.add(LangEntry.codeDefined((String) row[0], (Integer) row[1], UI, (String) row[2]));
        }
        return rows;
    }

    /**
     * The two built-in {@code vftv:strike} instances (aspects spec, "The Commonplace Book"): a
     * plain book, and a Book and Quill, which the strike refuses while it holds writing.
     */
    @Override
    public void recipes(HolderLookup.Provider registries, RecipeOutput output) {
        strike(output, "commonplace_book", Items.BOOK);
        strike(output, "commonplace_book_from_book_and_quill", Items.WRITABLE_BOOK);
    }

    private static void strike(RecipeOutput output, String name, Item from) {
        output.accept(
                ResourceKey.create(
                        Registries.RECIPE, Identifier.fromNamespaceAndPath(Vftv.MOD_ID, name)),
                new StrikeRecipe(
                        new Recipe.CommonInfo(false),
                        Ingredient.of(from),
                        new ItemStackTemplate(ResearchItems.commonplaceBook())),
                null);
    }

    /** The hidden find and ending advancements: no display, no parent, no rewards. */
    @Override
    public void advancements(HolderLookup.Provider registries, Consumer<AdvancementHolder> output) {
        HolderGetter<Item> items = registries.lookupOrThrow(Registries.ITEM);
        find(
                output,
                "find/resonance",
                "geode",
                has(items, Items.AMETHYST_SHARD, AspectContent.tuningFork()));
        find(
                output,
                "find/distillation",
                "dripstone",
                has(items, Items.POINTED_DRIPSTONE, Items.DRIPSTONE_BLOCK));
        find(output, "find/ruins", "page", has(items, ResearchItems.choirPage()));
        find(
                output,
                "find/choirs_road",
                "page",
                InventoryChangeTrigger.TriggerInstance.hasItems(
                        ItemPredicate.Builder.item()
                                .of(items, ResearchItems.choirPage())
                                .withComponents(
                                        DataComponentMatchers.Builder.components()
                                                .exact(
                                                        DataComponentExactPredicate.builder()
                                                                .expect(
                                                                        ResearchItems.page(),
                                                                        page(
                                                                                "choir/where_they_went"))
                                                                .build())
                                                .build())));
        Criterion<ImpossibleTrigger.TriggerInstance> impossible =
                CriteriaTriggers.IMPOSSIBLE.createCriterion(
                        new ImpossibleTrigger.TriggerInstance());
        find(output, "end/joined", "chosen", impossible);
        find(output, "end/silenced", "chosen", impossible);
    }

    private static void find(
            Consumer<AdvancementHolder> output,
            String path,
            String criterion,
            Criterion<?> trigger) {
        output.accept(
                Advancement.Builder.advancement()
                        .addCriterion(criterion, trigger)
                        .build(Identifier.fromNamespaceAndPath(Vftv.MOD_ID, path)));
    }

    private static Criterion<InventoryChangeTrigger.TriggerInstance> has(
            HolderGetter<Item> items, ItemLike... any) {
        return InventoryChangeTrigger.TriggerInstance.hasItems(
                ItemPredicate.Builder.item().of(items, any));
    }

    private static ResourceKey<Research> page(String path) {
        return ResourceKey.create(
                ResearchKeys.REGISTRY, Identifier.fromNamespaceAndPath(Vftv.MOD_ID, path));
    }

    /** The registry instances from {@code content/research}, validated as a world open would. */
    @Override
    public void providers(GatherDataEvent event) {
        event.createProvider((output, lookup) -> new RegistryProvider(output, lookup, content()));
        event.createProvider(TuningProvider::new);
    }

    private static ResearchContent.Content content() {
        String root = System.getProperty(ContentProvider.CONTENT_DIR);
        if (root == null) {
            throw new IllegalStateException(
                    "the system property " + ContentProvider.CONTENT_DIR + " is not set");
        }
        return ResearchContent.read(Path.of(root));
    }

    private record RegistryProvider(
            PackOutput output,
            CompletableFuture<HolderLookup.Provider> lookup,
            ResearchContent.Content content)
            implements DataProvider {
        @Override
        public CompletableFuture<?> run(CachedOutput cache) {
            return lookup.thenCompose(
                    registries -> {
                        EntryRules.Report report =
                                EntryRules.validate(
                                        content.plainEntries(),
                                        content.categories(),
                                        ResearchService.aspects(registries),
                                        ResearchService.REQUIRED_ENTRIES,
                                        ResearchService.REQUIRED_CATEGORIES);
                        // Built-ins: every warning is a failure, and so is a total warp gate.
                        List<String> problems = new ArrayList<>(report.errors());
                        problems.addAll(report.warnings());
                        problems.addAll(ResearchService.scanKeyProblems(content.plainEntries()));
                        problems.addAll(
                                EntryRules.milestoneProblems(
                                        content.plainEntries(),
                                        WarpTuning.DEFAULTS.permanentMilestones(),
                                        WarpTuning.JOINING));
                        content.plainEntries()
                                .forEach(
                                        (id, e) -> {
                                            if (Stream.concat(
                                                            e.triggers().stream(),
                                                            e.reveal().stream())
                                                    .anyMatch(
                                                            t ->
                                                                    t
                                                                                    instanceof
                                                                                    Trigger
                                                                                                    .WarpAtLeast
                                                                                            w
                                                                            && w.kind()
                                                                                    == Trigger
                                                                                            .WarpKind
                                                                                            .TOTAL)) {
                                                problems.add(
                                                        "research entry "
                                                                + id
                                                                + ": a built-in warp gate counts lasting_plus_permanent");
                                            }
                                        });
                        if (!problems.isEmpty()) {
                            throw new IllegalStateException(
                                    "Rejected research content:\n" + String.join("\n", problems));
                        }
                        List<CompletableFuture<?>> writes = new ArrayList<>();
                        content.categories()
                                .forEach(
                                        (id, c) ->
                                                writes.add(
                                                        write(
                                                                cache,
                                                                "research_category",
                                                                id,
                                                                ResearchCodecs.CATEGORY,
                                                                c)));
                        content.entries()
                                .forEach(
                                        (id, s) ->
                                                writes.add(
                                                        write(
                                                                cache,
                                                                "research",
                                                                id,
                                                                ResearchCodecs.ENTRY,
                                                                s.entry())));
                        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
                    });
        }

        private <T> CompletableFuture<?> write(
                CachedOutput cache, String registry, String id, Codec<T> codec, T value) {
            Identifier parsed = Identifier.parse(id);
            JsonElement json = codec.encodeStart(JsonOps.INSTANCE, value).getOrThrow();
            Path path =
                    output.getOutputFolder(PackOutput.Target.DATA_PACK)
                            .resolve(parsed.getNamespace())
                            .resolve(Vftv.MOD_ID)
                            .resolve(registry)
                            .resolve(parsed.getPath() + ".json");
            return DataProvider.saveStable(cache, json, path);
        }

        @Override
        public String getName() {
            return "vftv research registries";
        }
    }

    /** {@code data/vftv/tuning/research.json}, every key at its default. */
    private record TuningProvider(PackOutput output) implements DataProvider {
        @Override
        public CompletableFuture<?> run(CachedOutput cache) {
            return DataProvider.saveStable(
                    cache,
                    ResearchTuning.DEFAULTS.toJson(),
                    output.getOutputFolder(PackOutput.Target.DATA_PACK)
                            .resolve(Vftv.MOD_ID)
                            .resolve(ResearchService.TUNING_PATH));
        }

        @Override
        public String getName() {
            return "vftv research tuning";
        }
    }
}
