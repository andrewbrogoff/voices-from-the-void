package com.andrewbrogoff.vftv.gametest;

import com.andrewbrogoff.vftv.api.neoforge.research.ResearchCapabilities;
import com.andrewbrogoff.vftv.api.research.ResearchKeys;
import com.andrewbrogoff.vftv.api.research.ResearchView;
import com.andrewbrogoff.vftv.core.common.RawRecordCodec.Retained;
import com.andrewbrogoff.vftv.core.research.ResearchAttachment;
import com.andrewbrogoff.vftv.core.research.ResearchCodecs;
import com.andrewbrogoff.vftv.core.research.ResearchConfig;
import com.andrewbrogoff.vftv.core.research.ResearchItems;
import com.andrewbrogoff.vftv.core.research.ResearchService;
import com.andrewbrogoff.vftv.core.research.model.EntryRules;
import com.andrewbrogoff.vftv.core.research.model.ResearchEntry;
import com.andrewbrogoff.vftv.core.research.model.ResearchRecord;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.bus.api.IEventBus;

/**
 * The research feature's GameTests. Name each test {@code research_<what>}, and list its id {@code
 * vftv:research_<what>} in {@code src/gameTest/expected/research.txt}.
 */
final class ResearchGameTests {
    private ResearchGameTests() {}

    /**
     * Adds the feature's tests by name; {@code modBus} is for any test-only registration they need.
     */
    static void register(Map<String, Consumer<GameTestHelper>> tests, IEventBus modBus) {
        tests.put("research_registries_load", ResearchGameTests::registriesLoad);
        tests.put("research_load_rules_refuse", ResearchGameTests::loadRulesRefuse);
        tests.put("research_codec_rejects", ResearchGameTests::codecRejects);
        tests.put("research_view_live", ResearchGameTests::viewLive);
        tests.put("research_attachment_persists", ResearchGameTests::attachmentPersists);
        tests.put("research_attachment_unreadable", ResearchGameTests::attachmentUnreadable);
        tests.put("research_advancements", ResearchGameTests::advancements);
        tests.put("research_items", ResearchGameTests::items);
        tests.put("research_config_mode", ResearchGameTests::configMode);
    }

    /**
     * Both registries loaded the shipped skeleton, which passes every load rule with no warning (a
     * built-in warning is a failure).
     */
    private static void registriesLoad(GameTestHelper helper) {
        HolderLookup.Provider access = helper.getLevel().registryAccess();
        Map<String, ResearchEntry> entries = ResearchService.entries(access);
        helper.assertTrue(entries.size() >= 50, "the skeleton roster loaded: " + entries.size());
        helper.assertValueEqual(
                ResearchService.categories(access).size(), 5, "the five categories");
        for (String id : ResearchService.REQUIRED_ENTRIES) {
            helper.assertTrue(entries.containsKey(id), "required " + id);
        }
        EntryRules.Report report = ResearchService.validate(access);
        helper.assertValueEqual(report.errors(), List.of(), "no load error");
        helper.assertValueEqual(report.warnings(), List.of(), "no built-in warning");
        ResearchEntry silence = entries.get(ResearchKeys.SILENCE_BENEATH.identifier().toString());
        helper.assertTrue(
                silence != null && silence.warp().lasting() == 4 && silence.secret(),
                "the Vacuos listen: secret, lasting 4");
        helper.succeed();
    }

    /** Each load rule refuses a bad entry, naming it, against the world's real registries. */
    private static void loadRulesRefuse(GameTestHelper helper) {
        HolderLookup.Provider access = helper.getLevel().registryAccess();
        Map<String, String> cases = new LinkedHashMap<>();
        String base =
                "\"stage\": 1, \"category\": \"vftv:resonance\", \"icon\": {\"item\": \"a:b\"},";
        String text = "\"pages\": [{\"type\": \"vftv:text\"}]";
        String note = "\"pages\": [{\"type\": \"vftv:text\"}, {\"type\": \"vftv:action\"}]";
        cases.put(
                "{"
                        + base
                        + "\"position\": [9, 9], \"kind\": \"free\", \"parents\": [\"vftv:nowhere\"],"
                        + text
                        + "}",
                "its parent vftv:nowhere does not exist");
        cases.put(
                "{"
                        + base
                        + "\"position\": [9, 9], \"kind\": \"free\", \"parents\": [\"vftv:ruins\"],"
                        + text
                        + "}",
                "is in a later Stage");
        cases.put(
                "{"
                        + base
                        + "\"position\": [9, 9], \"kind\": \"free\", \"anchors\": [\"vftv:aer\", \"vftv:ignis\"],"
                        + text
                        + "}",
                "anchors on a free");
        cases.put(
                "{"
                        + base
                        + "\"position\": [9, 9], \"kind\": \"note\", \"anchors\": [\"vftv:aer\", \"vftv:nothing\"],"
                        + note
                        + "}",
                "are not aspects");
        cases.put(
                "{"
                        + base
                        + "\"position\": [9, 9], \"kind\": \"note\", \"anchors\": [\"vftv:aer\", \"vftv:lux\"],"
                        + note
                        + "}",
                "reference vector is empty");
        cases.put(
                "{"
                        + base
                        + "\"position\": [9, 9], \"kind\": \"free\", \"warp\": {\"temporary\": 1},"
                        + text
                        + "}",
                "warp on a free");
        cases.put(
                "{" + base + "\"position\": [9, 9], \"kind\": \"find\"," + text + "}",
                "a find with no triggers");
        cases.put(
                "{"
                        + base
                        + "\"position\": [9, 9], \"kind\": \"free\", \"secret\": true,"
                        + text
                        + "}",
                "secret with no reveal");
        cases.put(
                "{"
                        + base
                        + "\"position\": [9, 9], \"kind\": \"free\", \"hinted_by\": \"vftv:silence_beneath\","
                        + text
                        + "}",
                "is secret");
        cases.put(
                "{"
                        + base
                        + "\"position\": [9, 9], \"kind\": \"find\", \"opens_stage\": 1,"
                        + " \"triggers\": [{\"type\": \"vftv:advancement\", \"advancement\": \"vftv:find/resonance\"}],"
                        + text
                        + "}",
                "Stage 1 has 2 openers");
        cases.put(
                "{" + base + "\"position\": [0, 0], \"kind\": \"free\"," + text + "}",
                "its position collides with");
        cases.put(
                "{\"stage\": 2, \"category\": \"vftv:resonance\", \"icon\": {\"item\": \"a:b\"}, \"position\": [9, 9], \"kind\": \"free\","
                        + text
                        + "}",
                "differs from its category's 1");
        cases.put(
                "{" + base + "\"position\": [9, 9], \"kind\": \"listen\"," + text + "}",
                "0 action pages");
        cases.put(
                ("{" + base + "\"position\": [9, 9], \"kind\": \"free\"," + text + "}")
                        .replace("vftv:resonance", "vftv:nowhere"),
                "its category vftv:nowhere does not exist");
        cases.forEach(
                (json, expected) -> {
                    Map<String, ResearchEntry> entries =
                            new TreeMap<>(ResearchService.entries(access));
                    entries.put(
                            "vftv:bad",
                            ResearchCodecs.ENTRY
                                    .parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                                    .getOrThrow());
                    List<String> errors = validate(access, entries).errors();
                    helper.assertTrue(
                            errors.stream()
                                    .anyMatch(e -> e.contains("vftv:bad") && e.contains(expected)),
                            "expected \"" + expected + "\", got " + errors);
                });
        Map<String, ResearchEntry> missing = new TreeMap<>(ResearchService.entries(access));
        missing.remove(ResearchKeys.COMMONPLACE_BOOK.identifier().toString());
        helper.assertTrue(
                validate(access, missing).errors().stream()
                        .anyMatch(e -> e.contains("required built-in entry")),
                "a missing required key refuses");
        helper.succeed();
    }

    private static EntryRules.Report validate(
            HolderLookup.Provider access, Map<String, ResearchEntry> entries) {
        return EntryRules.validate(
                entries,
                ResearchService.categories(access),
                ResearchService.aspects(access),
                ResearchService.REQUIRED_ENTRIES,
                ResearchService.REQUIRED_CATEGORIES);
    }

    /** An unknown trigger or page type, or a number out of range, fails the registry codec. */
    private static void codecRejects(GameTestHelper helper) {
        String good =
                "{\"stage\": 1, \"category\": \"vftv:resonance\", \"position\": [0, 0],"
                        + " \"icon\": {\"item\": \"a:b\"}, \"kind\": \"free\", \"pages\": [{\"type\": \"vftv:text\"}]}";
        helper.assertTrue(parses(good), "the base entry parses");
        helper.assertFalse(parses(good.replace("vftv:text", "vftv:poem")), "an unknown page type");
        helper.assertFalse(
                parses(
                        good.replace(
                                "\"kind\": \"free\"",
                                "\"kind\": \"find\", \"triggers\": [{\"type\": \"vftv:dreamt\"}]")),
                "an unknown trigger type");
        helper.assertFalse(parses(good.replace("[0, 0]", "[0, 17]")), "a position out of range");
        helper.succeed();
    }

    private static boolean parses(String json) {
        return ResearchCodecs.RESEARCH
                .parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                .isSuccess();
    }

    /** The capability answers a live view for a server player, reflecting every change. */
    private static void viewLive(GameTestHelper helper) {
        ServerPlayer player = ResearchTestSupport.player(helper);
        ResearchView view = player.getCapability(ResearchCapabilities.VIEW);
        helper.assertTrue(view != null, "a server player has a view");
        if (view == null) {
            return;
        }
        helper.assertFalse(view.isLearned(ResearchKeys.RESONANCE), "nothing learned yet");
        helper.assertValueEqual(view.stage(), 0, "Stage 0");
        ResearchTestSupport.latch(player, ResearchKeys.RUINS);
        ResearchTestSupport.learn(player, ResearchKeys.RESONANCE);
        ResearchTestSupport.stage(player, 1);
        helper.assertTrue(view.isLearned(ResearchKeys.RESONANCE), "learned, live");
        helper.assertFalse(view.isLearned(ResearchKeys.RUINS), "latched is not learned");
        helper.assertTrue(view.isLatchedOrLearned(ResearchKeys.RUINS), "latched");
        helper.assertTrue(view.isLatchedOrLearned(ResearchKeys.RESONANCE), "learned counts");
        helper.assertValueEqual(view.stage(), 1, "Stage 1");
        helper.succeed();
    }

    /** The record survives a real save and load, and a death's copy. */
    private static void attachmentPersists(GameTestHelper helper) {
        ServerPlayer player = ResearchTestSupport.player(helper);
        ResearchTestSupport.learn(player, ResearchKeys.RESONANCE, ResearchKeys.COMMONPLACE_BOOK);
        ResearchTestSupport.latch(player, ResearchKeys.DISTILLATION);
        ResearchTestSupport.stage(player, 1);
        ResearchAttachment before = ResearchService.get(player);

        Player reloaded = ResearchTestSupport.player(helper);
        reloaded.load(
                TagValueInput.create(
                        ProblemReporter.DISCARDING, player.registryAccess(), save(player)));
        helper.assertValueEqual(
                ResearchService.get(reloaded).record(), before.record(), "the reloaded record");

        ServerPlayer respawned = ResearchTestSupport.player(helper);
        respawned.restoreFrom(player, false);
        helper.assertValueEqual(
                ResearchService.get(respawned).record(), before.record(), "kept through death");
        helper.succeed();
    }

    /**
     * An unreadable record is kept raw, written back unchanged, answers nothing and never changes.
     */
    private static void attachmentUnreadable(GameTestHelper helper) {
        ServerPlayer player = ResearchTestSupport.player(helper);
        ResearchTestSupport.learn(player, ResearchKeys.RESONANCE);
        CompoundTag saved = save(player);
        CompoundTag record =
                saved.getCompoundOrEmpty("neoforge:attachments")
                        .getCompoundOrEmpty(ResearchKeys.RESEARCH_ATTACHMENT.toString())
                        .getCompoundOrEmpty("data");
        record.putInt("format", 99);

        ServerPlayer reloaded = ResearchTestSupport.player(helper);
        reloaded.load(
                TagValueInput.create(ProblemReporter.DISCARDING, player.registryAccess(), saved));
        helper.assertTrue(
                ResearchService.get(reloaded).record()
                        instanceof Retained.Unreadable<ResearchRecord>,
                "a future format is unreadable");
        helper.assertFalse(
                ResearchService.isLearned(reloaded, ResearchKeys.RESONANCE), "answers nothing");
        helper.assertValueEqual(ResearchService.stage(reloaded), 0, "Stage 0");
        helper.assertFalse(ResearchService.update(reloaded, r -> r.withStage(2)), "never changes");
        helper.assertValueEqual(
                save(reloaded).getCompoundOrEmpty("neoforge:attachments"),
                saved.getCompoundOrEmpty("neoforge:attachments"),
                "written back unchanged");
        helper.succeed();
    }

    /** The hidden finds latch on holding their item; the endings only by code. */
    private static void advancements(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        for (String path :
                List.of(
                        "find/resonance",
                        "find/distillation",
                        "find/ruins",
                        "find/choirs_road",
                        "end/joined",
                        "end/silenced")) {
            AdvancementHolder holder =
                    server.getAdvancements().get(Identifier.fromNamespaceAndPath("vftv", path));
            helper.assertTrue(holder != null, "vftv:" + path + " is loaded");
            if (holder != null) {
                helper.assertTrue(
                        holder.value().display().isEmpty() && holder.value().parent().isEmpty(),
                        path + " is hidden with no parent");
            }
        }
        assertDone(helper, new ItemStack(Items.AMETHYST_SHARD), "find/resonance", true);
        assertDone(helper, new ItemStack(Items.POINTED_DRIPSTONE), "find/distillation", true);
        assertDone(helper, page("choir/beneath_the_notes"), "find/ruins", true);
        assertDone(helper, page("choir/beneath_the_notes"), "find/choirs_road", false);
        assertDone(helper, new ItemStack(ResearchItems.choirPage()), "find/choirs_road", false);
        assertDone(helper, page("choir/where_they_went"), "find/choirs_road", true);
        assertDone(helper, page("choir/where_they_went"), "end/joined", false);
        helper.succeed();
    }

    private static void assertDone(
            GameTestHelper helper, ItemStack stack, String path, boolean done) {
        ServerPlayer player = ResearchTestSupport.player(helper);
        player.getInventory().add(stack.copy());
        CriteriaTriggers.INVENTORY_CHANGED.trigger(player, player.getInventory(), stack);
        AdvancementHolder holder =
                helper.getLevel()
                        .getServer()
                        .getAdvancements()
                        .get(Identifier.fromNamespaceAndPath("vftv", path));
        helper.assertTrue(
                holder != null
                        && player.getAdvancements().getOrStartProgress(holder).isDone() == done,
                path + (done ? " is earned by " : " is not earned by ") + stack);
    }

    private static ItemStack page(String path) {
        ItemStack stack = new ItemStack(ResearchItems.choirPage());
        stack.set(
                ResearchItems.page(),
                ResourceKey.create(
                        ResearchKeys.REGISTRY, Identifier.fromNamespaceAndPath("vftv", path)));
        return stack;
    }

    /** The book stacks to 1; a page carries its entry id in {@code vftv:page}. */
    private static void items(GameTestHelper helper) {
        helper.assertValueEqual(
                new ItemStack(ResearchItems.commonplaceBook()).getMaxStackSize(),
                1,
                "the book stacks to 1");
        ItemStack page = page("choir/beneath_the_notes");
        helper.assertTrue(
                ResearchKeys.BENEATH_THE_NOTES.equals(page.get(ResearchItems.page())),
                "the page's entry");
        helper.assertTrue(
                new ItemStack(ResearchItems.choirPage()).get(ResearchItems.page()) == null,
                "a blank page");
        helper.succeed();
    }

    /** {@code research.mode} defaults to standard, and an installed switch is read back. */
    private static void configMode(GameTestHelper helper) {
        helper.assertValueEqual(
                ResearchService.config(), ResearchConfig.DEFAULT, "standard by default");
        try {
            ResearchService.installConfig(new ResearchConfig(ResearchConfig.Mode.SKIP));
            helper.assertValueEqual(
                    ResearchService.config().mode(), ResearchConfig.Mode.SKIP, "skip installed");
        } finally {
            ResearchService.installConfig(ResearchConfig.DEFAULT);
        }
        helper.succeed();
    }

    private static CompoundTag save(Player player) {
        TagValueOutput out =
                TagValueOutput.createWithContext(
                        ProblemReporter.DISCARDING, player.registryAccess());
        player.saveWithoutId(out);
        return out.buildResult();
    }
}
