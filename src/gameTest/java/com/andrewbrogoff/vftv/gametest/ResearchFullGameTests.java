package com.andrewbrogoff.vftv.gametest;

import com.andrewbrogoff.vftv.api.aspect.ScanKey;
import com.andrewbrogoff.vftv.api.aspect.ScanTargetKind;
import com.andrewbrogoff.vftv.api.neoforge.research.ResearchLearnedEvent;
import com.andrewbrogoff.vftv.api.neoforge.research.StageReachedEvent;
import com.andrewbrogoff.vftv.api.research.ResearchKeys;
import com.andrewbrogoff.vftv.api.research.ResearchLearnedPayload;
import com.andrewbrogoff.vftv.api.research.ResearchSource;
import com.andrewbrogoff.vftv.api.research.StageReachedPayload;
import com.andrewbrogoff.vftv.api.warp.CommitResult;
import com.andrewbrogoff.vftv.api.warp.WarpCost;
import com.andrewbrogoff.vftv.api.warp.WarpPreview;
import com.andrewbrogoff.vftv.api.warp.WarpSource;
import com.andrewbrogoff.vftv.api.warp.WarpSourceKind;
import com.andrewbrogoff.vftv.core.aspect.AspectContent;
import com.andrewbrogoff.vftv.core.aspect.AspectSet;
import com.andrewbrogoff.vftv.core.aspect.KnowledgeService;
import com.andrewbrogoff.vftv.core.aspect.model.Knowledge;
import com.andrewbrogoff.vftv.core.research.ChoirPageItem;
import com.andrewbrogoff.vftv.core.research.ResearchCodecs;
import com.andrewbrogoff.vftv.core.research.ResearchItems;
import com.andrewbrogoff.vftv.core.research.ResearchService;
import com.andrewbrogoff.vftv.core.research.Resolver;
import com.andrewbrogoff.vftv.core.research.model.EntryRules;
import com.andrewbrogoff.vftv.core.research.model.Note;
import com.andrewbrogoff.vftv.core.research.model.ResearchEntry;
import com.andrewbrogoff.vftv.core.research.model.ResearchRecord;
import com.andrewbrogoff.vftv.core.text.StatusLine;
import com.andrewbrogoff.vftv.core.text.StatusLines;
import com.andrewbrogoff.vftv.core.warp.WarpRecord;
import com.andrewbrogoff.vftv.core.warp.WarpRulesService;
import com.andrewbrogoff.vftv.core.warp.WarpService;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The full research ticket's GameTests (research spec, "Tests"): triggers and latching, the Stage
 * cascade, Choir pages, rewards, the commands' effects, the Vacuos listen and the login catch-up.
 */
final class ResearchFullGameTests {
    private static final String PAGE = "vftv:choir/beneath_the_notes";
    private static final Map<UUID, List<Object>> EVENTS = new HashMap<>();

    private ResearchFullGameTests() {}

    static void register(Map<String, Consumer<GameTestHelper>> tests) {
        NeoForge.EVENT_BUS.addListener(
                ResearchLearnedEvent.class, e -> record(e.payload().player(), e.payload()));
        NeoForge.EVENT_BUS.addListener(
                StageReachedEvent.class, e -> record(e.payload().player(), e.payload()));
        tests.put(
                "research_stage1_from_a_shard",
                h -> stageOne(h, new ItemStack(Items.AMETHYST_SHARD)));
        tests.put(
                "research_stage1_from_a_fork",
                h -> stageOne(h, new ItemStack(AspectContent.tuningFork())));
        tests.put("research_opener_bootstrap", ResearchFullGameTests::openerBootstrap);
        tests.put("research_trigger_types", ResearchFullGameTests::triggerTypes);
        tests.put("research_choir_page_cases", ResearchFullGameTests::choirPageCases);
        tests.put("research_rewards_never_twice", ResearchFullGameTests::rewardsNeverTwice);
        tests.put("research_grant_revoke_stage_reset", ResearchFullGameTests::commands);
        tests.put("research_vacuos_listen", ResearchFullGameTests::vacuosListen);
        tests.put("research_login_catch_up", ResearchFullGameTests::loginCatchUp);
        tests.put("research_cross_registry_rules", ResearchFullGameTests::crossRegistryRules);
    }

    private static synchronized void record(ServerPlayer player, Object payload) {
        EVENTS.computeIfAbsent(player.getUUID(), k -> new ArrayList<>()).add(payload);
    }

    private static synchronized List<Object> events(ServerPlayer player) {
        return List.copyOf(EVENTS.getOrDefault(player.getUUID(), List.of()));
    }

    private static ResearchRecord rec(ServerPlayer player) {
        return Objects.requireNonNull(ResearchService.record(player), "readable");
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        player.getInventory().add(stack.copy());
        CriteriaTriggers.INVENTORY_CHANGED.trigger(player, player.getInventory(), stack);
    }

    private static ItemStack page(String id, int count) {
        ItemStack stack = new ItemStack(ResearchItems.choirPage(), count);
        stack.set(
                ResearchItems.page(),
                ResourceKey.create(ResearchKeys.REGISTRY, Identifier.parse(id)));
        return stack;
    }

    private static Knowledge knowledge(ServerPlayer player) {
        return Objects.requireNonNull(KnowledgeService.get(player), "readable knowledge");
    }

    /** Stage 1 is recorded the first time a player holds a shard or a fork. */
    private static void stageOne(GameTestHelper helper, ItemStack stack) {
        ServerPlayer player = ResearchTestSupport.player(helper);
        give(player, stack);
        ResearchRecord r = rec(player);
        helper.assertValueEqual(r.stage(), 1, "Stage 1");
        for (String id :
                List.of(
                        "vftv:resonance",
                        "vftv:commonplace_book",
                        "vftv:notes_of_things",
                        "vftv:sounding")) {
            helper.assertTrue(r.isLearned(id), id + " learned");
        }
        List<Object> events = events(player);
        helper.assertTrue(
                events.getFirst() instanceof StageReachedPayload s
                        && s.stage() == 1
                        && s.previousStage() == 0
                        && s.source() == ResearchSource.FOUND,
                "the Stage step first: " + events);
        helper.assertTrue(
                events.get(1) instanceof ResearchLearnedPayload l
                        && l.research().equals(ResearchKeys.RESONANCE)
                        && l.source() == ResearchSource.FOUND,
                "then the opener");
        helper.assertTrue(
                events.stream()
                        .anyMatch(
                                e ->
                                        e instanceof ResearchLearnedPayload l
                                                && l.research()
                                                        .equals(ResearchKeys.COMMONPLACE_BOOK)
                                                && l.source() == ResearchSource.FREE),
                "the free entries learn themselves");
        helper.succeed();
    }

    /** A later opener's find made early stays latched and cascades once its Stage opens. */
    private static void openerBootstrap(GameTestHelper helper) {
        ServerPlayer player = ResearchTestSupport.player(helper);
        give(player, page(PAGE, 1)); // holding any page latches vftv:find/ruins
        helper.assertTrue(rec(player).latched().contains("vftv:ruins"), "latched before visible");
        helper.assertValueEqual(rec(player).stage(), 0, "still Stage 0");
        give(player, new ItemStack(Items.AMETHYST_SHARD));
        helper.assertValueEqual(rec(player).stage(), 1, "Stage 1, not 3");
        give(player, new ItemStack(Items.POINTED_DRIPSTONE));
        helper.assertValueEqual(rec(player).stage(), 3, "Stages 2 and 3 cascade in order");
        helper.assertTrue(rec(player).isLearned("vftv:ruins"), "the early opener learned");
        List<Integer> steps =
                events(player).stream()
                        .filter(e -> e instanceof StageReachedPayload)
                        .map(e -> ((StageReachedPayload) e).stage())
                        .toList();
        helper.assertValueEqual(steps, List.of(1, 2, 3), "one event per step");
        helper.succeed();
    }

    /** A node scan, from a real strike, and a warp gate, each latch and resolve. */
    private static void triggerTypes(GameTestHelper helper) {
        ServerPlayer player = ResearchTestSupport.player(helper);
        give(player, new ItemStack(Items.AMETHYST_SHARD));
        ResearchTestSupport.learn(
                player,
                ResourceKey.create(ResearchKeys.REGISTRY, Identifier.parse("vftv:taking_notes")));
        KnowledgeService.strike(
                player,
                new ScanKey.Node(7),
                ScanTargetKind.NODE,
                Map.of(AspectSet.key("vftv:aer"), 2));
        helper.assertTrue(
                rec(player).latched().contains("vftv:knots_in_the_air"),
                "latched during the strike");
        helper.assertFalse(
                rec(player).isLearned("vftv:knots_in_the_air"),
                "resolved after the knowledge write");
        Resolver.drainDeferred();
        helper.assertTrue(
                rec(player).isLearned("vftv:knots_in_the_air"), "learned at the tick's end");
        WarpService.apply(
                player, new WarpCost(0, 4, 0), new WarpSource(WarpSourceKind.COMMAND, null));
        helper.assertTrue(
                rec(player).revealed().containsAll(Set.of("vftv:void_wands", "vftv:void_armor")),
                "lasting 4 reveals the Stage 4 secrets early: " + rec(player).revealed());
        helper.succeed();
    }

    /** Every tip-in case of the table, on the status line. */
    private static void choirPageCases(GameTestHelper helper) {
        ServerPlayer player = ResearchTestSupport.player(helper);
        List<String> lines = new ArrayList<>();
        BiConsumer<ServerPlayer, StatusLine> previous =
                StatusLines.swapSender(
                        (p, line) -> {
                            if (p.getUUID().equals(player.getUUID())
                                    && line.text().getContents()
                                            instanceof TranslatableContents t) {
                                lines.add(t.getKey());
                            }
                        });
        try {
            ItemStack blank = new ItemStack(ResearchItems.choirPage());
            helper.assertValueEqual(
                    ChoirPageItem.tipIn(player, blank), ChoirPageItem.BLANK, "no component");
            ItemStack nowhere = page("vftv:choir/nowhere", 1);
            helper.assertValueEqual(
                    ChoirPageItem.tipIn(player, nowhere), ChoirPageItem.BLANK, "no entry");
            ItemStack pages = page(PAGE, 2);
            helper.assertValueEqual(
                    ChoirPageItem.tipIn(player, pages), ChoirPageItem.TOO_OLD, "Stage 0");
            helper.assertValueEqual(pages.getCount(), 2, "kept");
            ResearchTestSupport.stage(player, 3);
            helper.assertValueEqual(
                    ChoirPageItem.tipIn(player, pages), ChoirPageItem.TIPPED, "tipped");
            helper.assertValueEqual(pages.getCount(), 1, "consumed");
            ResearchRecord r = rec(player);
            helper.assertTrue(r.isLearned(PAGE), "the page entry learned");
            helper.assertTrue(r.revealed().contains("vftv:silence_beneath"), "its secret revealed");
            helper.assertValueEqual(
                    ChoirPageItem.tipIn(player, pages), ChoirPageItem.ALREADY, "already");
            helper.assertValueEqual(pages.getCount(), 1, "kept to hand on");
            helper.assertValueEqual(
                    lines,
                    List.of(
                            ChoirPageItem.BLANK,
                            ChoirPageItem.BLANK,
                            ChoirPageItem.TOO_OLD,
                            ChoirPageItem.TIPPED,
                            ChoirPageItem.ALREADY),
                    "each answer on the status line");
        } finally {
            StatusLines.swapSender(previous);
        }
        helper.succeed();
    }

    /** A reward teaches its aspects once per entry, ever: not again after revoke or reset. */
    private static void rewardsNeverTwice(GameTestHelper helper) {
        ServerPlayer player = ResearchTestSupport.player(helper);
        ResearchTestSupport.stage(player, 3);
        ChoirPageItem.tipIn(player, page(PAGE, 1));
        Knowledge paid = knowledge(player);
        helper.assertTrue(
                paid.known().containsAll(Set.of("vftv:silentium", "vftv:sonus", "vftv:vacuos")),
                "Silentium taught with its missing prerequisites: " + paid.known());
        helper.assertTrue(paid.pool().getOrDefault("vftv:silentium", 0) >= 4, "its points paid");
        helper.assertTrue(rec(player).rewarded().contains(PAGE), "recorded");
        Resolver.revoke(player, List.of(PAGE));
        helper.assertTrue(rec(player).isLearned(PAGE), "the latched find learns itself again");
        helper.assertValueEqual(knowledge(player).pool(), paid.pool(), "not paid twice");
        Resolver.reset(player);
        helper.assertTrue(rec(player).rewarded().contains(PAGE), "reset keeps rewarded");
        Resolver.stage(player, 3);
        helper.assertValueEqual(
                ChoirPageItem.tipIn(player, page(PAGE, 1)), ChoirPageItem.TIPPED, "tipped again");
        helper.assertValueEqual(knowledge(player).pool(), paid.pool(), "still not paid twice");
        helper.succeed();
    }

    /** grant, revoke, stage and reset, each through the resolution with source COMMAND. */
    private static void commands(GameTestHelper helper) {
        ServerPlayer player = ResearchTestSupport.player(helper);
        ServerPlayer other = ResearchTestSupport.player(helper);
        give(player, new ItemStack(Items.AMETHYST_SHARD));
        Note note =
                new Note(
                        "vftv:taking_notes",
                        new Note.Snapshot(2, 0, Note.PaletteHelp.PARENTS, 1, 1),
                        3,
                        List.of(
                                new Note.Placed(new Note.Cell(0, 0), "vftv:aer"),
                                new Note.Placed(new Note.Cell(2, 0), "vftv:ignis")),
                        List.of(),
                        List.of(),
                        rec(player).revision());
        ResearchService.update(player, r -> r.withNotes(Map.of("vftv:taking_notes", note)));
        int granted = Resolver.grant(player, List.of("vftv:silence_beneath", "vftv:taking_notes"));
        ResearchRecord r = rec(player);
        helper.assertValueEqual(r.stage(), 3, "the Stage raised to the entry's");
        for (String id :
                List.of(
                        "vftv:distillation",
                        "vftv:ruins",
                        "vftv:being_heard",
                        "vftv:silence_beneath")) {
            helper.assertTrue(r.isLearned(id), id + " granted with its closure and openers");
        }
        helper.assertTrue(granted >= 5, "the count changed: " + granted);
        helper.assertTrue(r.notes().isEmpty(), "the granted entry's note dropped");
        helper.assertFalse(r.rewarded().contains("vftv:silence_beneath"), "no reward added");
        helper.assertTrue(
                events(player).stream()
                        .anyMatch(
                                e ->
                                        e instanceof StageReachedPayload s
                                                && s.stage() == 3
                                                && s.source() == ResearchSource.COMMAND),
                "Stage steps with source COMMAND");
        helper.assertValueEqual(rec(other).stage(), 0, "another player's research is untouched");

        int before = events(player).size();
        helper.assertValueEqual(
                Resolver.revoke(player, List.of("vftv:silence_beneath")), 1, "revoked");
        helper.assertFalse(rec(player).isLearned("vftv:silence_beneath"), "the flag removed");
        helper.assertValueEqual(rec(player).stage(), 3, "the Stage is never lowered");
        helper.assertValueEqual(events(player).size(), before, "no event for a revocation");

        Resolver.stage(player, 1);
        helper.assertValueEqual(rec(player).stage(), 1, "set");
        helper.assertTrue(rec(player).isLearned("vftv:ruins"), "learns and clears nothing");

        WarpService.apply(
                player, new WarpCost(2, 0, 0), new WarpSource(WarpSourceKind.COMMAND, null));
        Resolver.reset(player);
        ResearchRecord reset = rec(player);
        helper.assertTrue(
                reset.stage() == 0 && reset.learned().isEmpty() && reset.latched().isEmpty(),
                "reset clears the Stage, learned and latches");
        WarpRecord warp = Objects.requireNonNull(WarpService.record(player));
        helper.assertValueEqual(warp.temporary(), 2, "warp kept");
        AdvancementHolder find =
                Objects.requireNonNull(
                        player.level()
                                .getServer()
                                .getAdvancements()
                                .get(Identifier.parse("vftv:find/resonance")));
        helper.assertFalse(
                player.getAdvancements().getOrStartProgress(find).isDone(), "the find revoked");
        helper.succeed();
    }

    /**
     * The Vacuos listen: a stale echo is refused, the commit applies lasting 4 and switches the
     * view; stilling to 0 afterwards keeps the Stage 4 reveal it latched.
     */
    private static void vacuosListen(GameTestHelper helper) {
        ServerPlayer player = ResearchTestSupport.player(helper);
        Resolver.grant(player, List.of("vftv:being_heard"));
        ChoirPageItem.tipIn(player, page(PAGE, 1));
        ResearchService.Data data = ResearchService.data(player.registryAccess());
        helper.assertFalse(data.view(rec(player)).revealed("vftv:vacuos"), "the taught view");
        CommitResult stale =
                Resolver.listen(player, "vftv:silence_beneath", new WarpPreview(0, 3, 0, false));
        helper.assertValueEqual(stale.status(), CommitResult.Status.STALE, "a mismatched echo");
        helper.assertFalse(rec(player).isLearned("vftv:silence_beneath"), "nothing learned");
        WarpPreview cost = new WarpPreview(0, 4, 0, false);
        helper.assertValueEqual(
                Resolver.listen(player, "vftv:silence_beneath", cost).status(),
                CommitResult.Status.OK,
                "committed");
        helper.assertTrue(rec(player).isLearned("vftv:silence_beneath"), "learned");
        helper.assertValueEqual(
                Objects.requireNonNull(WarpService.record(player)).lasting(), 4, "lasting 4");
        helper.assertTrue(data.view(rec(player)).revealed("vftv:vacuos"), "the true view");
        helper.assertTrue(
                data.view(rec(player)).linked("vftv:vacuos", "vftv:terra"), "Vacuos meets Terra");
        helper.assertTrue(
                events(player).stream()
                        .anyMatch(
                                e ->
                                        e instanceof ResearchLearnedPayload l
                                                && l.research().equals(ResearchKeys.SILENCE_BENEATH)
                                                && l.source() == ResearchSource.LISTENED
                                                && l.warp().equals(cost)),
                "LISTENED with the warp applied");
        helper.assertValueEqual(
                Resolver.listen(player, "vftv:silence_beneath", cost).status(),
                CommitResult.Status.INVALID,
                "never twice");
        WarpService.apply(
                player, new WarpCost(0, -4, 0), new WarpSource(WarpSourceKind.STILLING, null));
        Resolver.learn(
                player,
                "vftv:hollow_bell",
                ResearchSource.COMMAND,
                Resolver.NO_WARP,
                Map.of(),
                x -> x);
        helper.assertTrue(
                rec(player).revealed().contains("vftv:void_wands"),
                "a stilled reveal still latches");
        helper.succeed();
    }

    /** Login latches what holds now, drops gone bookmarks and keeps inert ids. */
    private static void loginCatchUp(GameTestHelper helper) {
        ServerPlayer player = ResearchTestSupport.player(helper);
        ResearchService.update(
                player,
                r ->
                        r.withStage(1)
                                .learn("vftv:resonance", "vftv:taking_notes", "vftv:gone")
                                .withBookmarks(List.of("vftv:gone", "vftv:resonance")));
        KnowledgeService.set(
                player,
                new Knowledge(
                        Set.of("vftv:aer"),
                        Map.of("node:3", Knowledge.Scan.COMPLETE),
                        Map.of(),
                        null));
        Resolver.login(player);
        ResearchRecord r = rec(player);
        helper.assertTrue(r.isLearned("vftv:knots_in_the_air"), "the ledger's node key latched");
        helper.assertValueEqual(
                r.bookmarks(), List.of("vftv:resonance"), "a gone bookmark dropped");
        helper.assertTrue(r.isLearned("vftv:gone"), "a gone learned id stays, inert");
        helper.succeed();
    }

    /**
     * Vacuos's revealed_by and the warp milestones are checked across registries, and a warp tuning
     * reload that breaks a milestone keeps the previous record.
     */
    private static void crossRegistryRules(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        Map<String, ResearchEntry> entries = new TreeMap<>(ResearchService.entries(access));
        entries.remove("vftv:silence_beneath");
        List<String> errors =
                EntryRules.validate(
                                entries,
                                ResearchService.categories(access),
                                ResearchService.aspects(access),
                                Set.of(),
                                Set.of())
                        .errors();
        helper.assertTrue(
                errors.stream()
                        .anyMatch(
                                e -> e.contains("revealed_by vftv:silence_beneath does not exist")),
                "Vacuos's revealed_by must exist: " + errors);
        Map<String, ResearchEntry> broken = new TreeMap<>(ResearchService.entries(access));
        broken.put(
                "vftv:bad",
                ResearchCodecs.ENTRY
                        .parse(
                                JsonOps.INSTANCE,
                                JsonParser.parseString(
                                        "{\"stage\": 3, \"category\": \"vftv:ruins\", \"position\": [9, 9],"
                                                + " \"icon\": {\"item\": \"a:b\"}, \"kind\": \"listen\","
                                                + " \"warp\": {\"permanent\": 1}, \"pages\":"
                                                + " [{\"type\": \"vftv:action\"}]}"))
                        .getOrThrow());
        helper.assertTrue(
                EntryRules.milestoneProblems(
                                broken,
                                WarpRulesService.current().permanentMilestones(),
                                "vftv:joining")
                        .stream()
                        .anyMatch(p -> p.contains("vftv:bad")),
                "an unlisted permanent refuses");
        Map<String, Integer> before = WarpRulesService.current().permanentMilestones();
        WarpRulesService.applyTuning(
                WarpRulesService.parse(
                        "{\"permanent.milestones\": {\"vftv:under_the_silence\": 3,"
                                + " \"vftv:learning_the_tongue\": 3, \"vftv:joining\": 4}}"),
                true);
        helper.assertValueEqual(
                WarpRulesService.current().permanentMilestones(),
                before,
                "a reload that breaks a milestone keeps the previous tuning");
        helper.succeed();
    }
}
