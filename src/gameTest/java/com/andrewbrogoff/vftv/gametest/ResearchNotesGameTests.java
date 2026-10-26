package com.andrewbrogoff.vftv.gametest;

import com.andrewbrogoff.vftv.api.aspect.AspectValues;
import com.andrewbrogoff.vftv.api.research.ResearchLimits;
import com.andrewbrogoff.vftv.api.warp.WarpCost;
import com.andrewbrogoff.vftv.api.warp.WarpPreview;
import com.andrewbrogoff.vftv.api.warp.WarpSource;
import com.andrewbrogoff.vftv.api.warp.WarpSourceKind;
import com.andrewbrogoff.vftv.core.aspect.AspectLookup;
import com.andrewbrogoff.vftv.core.aspect.AspectSet;
import com.andrewbrogoff.vftv.core.aspect.KnowledgeService;
import com.andrewbrogoff.vftv.core.aspect.ScanningService;
import com.andrewbrogoff.vftv.core.aspect.model.Knowledge;
import com.andrewbrogoff.vftv.core.common.model.PendingAudio;
import com.andrewbrogoff.vftv.core.research.ChoirPageItem;
import com.andrewbrogoff.vftv.core.research.CommonplaceBookItem;
import com.andrewbrogoff.vftv.core.research.CommonplaceMenu;
import com.andrewbrogoff.vftv.core.research.ResearchAction;
import com.andrewbrogoff.vftv.core.research.ResearchActions;
import com.andrewbrogoff.vftv.core.research.ResearchConfig;
import com.andrewbrogoff.vftv.core.research.ResearchFeedback;
import com.andrewbrogoff.vftv.core.research.ResearchItems;
import com.andrewbrogoff.vftv.core.research.ResearchService;
import com.andrewbrogoff.vftv.core.research.Resolver;
import com.andrewbrogoff.vftv.core.research.model.AffordabilityPath;
import com.andrewbrogoff.vftv.core.research.model.DebitPlan;
import com.andrewbrogoff.vftv.core.research.model.Note;
import com.andrewbrogoff.vftv.core.research.model.Notes;
import com.andrewbrogoff.vftv.core.research.model.ResearchEntry;
import com.andrewbrogoff.vftv.core.research.model.ResearchRecord;
import com.andrewbrogoff.vftv.core.research.model.ResearchTuning;
import com.andrewbrogoff.vftv.core.text.model.SharedMessages;
import com.andrewbrogoff.vftv.core.warp.WarpRulesService;
import com.andrewbrogoff.vftv.core.warp.WarpService;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * The notes ticket's GameTests (research spec, "Tests"): the book's menu and quotes, a full note at
 * each preset, skip purchases, refusals that change nothing, checked commits, hostile input,
 * existing-note access, quote refresh and reconciliation. Each action runs one tick after the last,
 * so the one-commit-per-tick cap never drops a test's own commits.
 */
final class ResearchNotesGameTests {
    private static final String NOTE = "vftv:taking_notes";
    private static final List<String> PARENTS =
            List.of("vftv:commonplace_book", "vftv:notes_of_things", "vftv:sounding");
    private static final Set<String> KNOWN = Set.of("vftv:aer", "vftv:ignis", "vftv:lux");

    /** By player entity: mock players share one UUID. */
    private static final Map<ServerPlayer, List<CustomPacketPayload>> SENT = new WeakHashMap<>();

    private ResearchNotesGameTests() {}

    static void register(Map<String, Consumer<GameTestHelper>> tests) {
        ResearchActions.swapSender(ResearchNotesGameTests::capture);
        tests.put("research_book_opens_menu", ResearchNotesGameTests::bookOpensMenu);
        tests.put("research_note_gentle", h -> fullNote(h, ResearchConfig.Mode.GENTLE));
        tests.put("research_note_standard", h -> fullNote(h, ResearchConfig.Mode.STANDARD));
        tests.put("research_note_hard", h -> fullNote(h, ResearchConfig.Mode.HARD));
        tests.put("research_skip_purchase", ResearchNotesGameTests::skipPurchase);
        tests.put("research_skip_warp_purchase", ResearchNotesGameTests::skipWarpPurchase);
        tests.put("research_abandon_and_short", ResearchNotesGameTests::abandonAndShort);
        tests.put("research_mode_switch_keeps_note", ResearchNotesGameTests::modeSwitch);
        tests.put("research_checked_commits", ResearchNotesGameTests::checkedCommits);
        tests.put("research_hostile_actions", ResearchNotesGameTests::hostileActions);
        tests.put("research_existing_note_access", ResearchNotesGameTests::existingNoteAccess);
        tests.put("research_quote_refresh", ResearchNotesGameTests::quoteRefresh);
        tests.put("research_note_reconcile", ResearchNotesGameTests::reconcile);
        tests.put("research_listen_action", ResearchNotesGameTests::listenAction);
        tests.put("research_bookmarks", ResearchNotesGameTests::bookmarks);
        tests.put("research_affordability_paths", ResearchNotesGameTests::affordabilityPaths);
        tests.put("research_offer_actions", ResearchNotesGameTests::offerActions);
        tests.put("research_listen_rite_action", ResearchNotesGameTests::listenRiteAction);
    }

    private static synchronized void capture(ServerPlayer player, CustomPacketPayload payload) {
        SENT.computeIfAbsent(player, k -> new ArrayList<>()).add(payload);
    }

    private static synchronized List<CustomPacketPayload> sent(ServerPlayer player) {
        return List.copyOf(SENT.getOrDefault(player, List.of()));
    }

    // ---- The bench ---------------------------------------------------------------------------

    /** A player with the book open, Stage 1 and the tutorial note's parents learned. */
    private static final class Bench {
        final GameTestHelper helper;
        final ServerPlayer player;
        int request = 1;

        Bench(GameTestHelper helper, Map<String, Integer> pool) {
            this.helper = helper;
            player = ResearchTestSupport.player(helper);
            player.setItemInHand(
                    InteractionHand.MAIN_HAND, new ItemStack(ResearchItems.commonplaceBook()));
            Resolver.grant(player, PARENTS);
            knowledge(KNOWN, pool);
            CommonplaceBookItem.open(player);
        }

        void knowledge(Set<String> known, Map<String, Integer> pool) {
            KnowledgeService.set(player, new Knowledge(known, Map.of(), pool, null));
        }

        CommonplaceMenu menu() {
            if (!(player.containerMenu instanceof CommonplaceMenu menu)) {
                throw new IllegalStateException("the book's menu is not open");
            }
            return menu;
        }

        ResearchRecord rec() {
            return Objects.requireNonNull(ResearchService.record(player), "readable");
        }

        Map<String, Integer> pool() {
            return Objects.requireNonNull(KnowledgeService.get(player)).pool();
        }

        @Nullable Note note(String entry) {
            return rec().notes().get(entry);
        }

        long rev(String entry) {
            return Objects.requireNonNull(note(entry), "open note " + entry).revision();
        }

        int quotes() {
            return menu().revision();
        }

        /** Sends an action through the real handler; its result, or null when dropped. */
        ResearchFeedback.@Nullable Result act(ResearchAction.Variant variant) {
            return act(menu().containerId, request++, variant);
        }

        ResearchFeedback.@Nullable Result act(
                int containerId, int requestId, ResearchAction.Variant variant) {
            int before = sent(player).size();
            ResearchActions.onAction(new ResearchAction(containerId, requestId, variant), player);
            List<CustomPacketPayload> after = sent(player);
            for (CustomPacketPayload p : after.subList(before, after.size())) {
                if (p instanceof ResearchFeedback.Result r && r.requestId() == requestId) {
                    return r;
                }
            }
            return null;
        }

        void ok(ResearchFeedback.@Nullable Result r, String what) {
            helper.assertTrue(r != null && r.ok(), what + ": " + r);
        }

        void refused(ResearchFeedback.@Nullable Result r, String reason, String what) {
            helper.assertTrue(r != null && !r.ok() && r.reason().equals(reason), what + ": " + r);
        }

        /** The witness the server's note was planted with, regenerated from the same inputs. */
        List<Note.Placed> witness(String entry, ResearchConfig.Mode mode) {
            ResearchRecord r = rec();
            ResearchService.Data data = ResearchService.data(player.registryAccess());
            ResearchEntry e = Objects.requireNonNull(data.entries().get(entry));
            ResearchTuning.Preset preset = ResearchService.preset(mode);
            Notes.Generated g =
                    Notes.generate(
                            Notes.seed(
                                    player.level().getServer().overworld().getSeed(),
                                    player.getUUID(),
                                    r.attempts().getOrDefault(entry, 0),
                                    entry),
                            entry,
                            Notes.used(e, preset),
                            Objects.requireNonNull(KnowledgeService.get(player)).known(),
                            data.view(r),
                            ResearchService.tuning(),
                            preset,
                            0,
                            s -> {});
            Note note = Objects.requireNonNull(g.note());
            List<Note.Placed> out = new ArrayList<>();
            g.plant()
                    .forEach(
                            (cell, aspect) -> {
                                if (note.anchors().stream().noneMatch(a -> a.cell().equals(cell))) {
                                    out.add(new Note.Placed(cell, aspect));
                                }
                            });
            return out;
        }

        /** Places every witness cell, one action each; the pool is unchanged throughout. */
        void solve(String entry, ResearchConfig.Mode mode) {
            Map<String, Integer> pool = pool();
            for (Note.Placed p : witness(entry, mode)) {
                ok(
                        act(
                                new ResearchAction.Place(
                                        entry, rev(entry), p.cell().q(), p.cell().r(), p.aspect())),
                        "place " + p);
                helper.assertValueEqual(pool(), pool, "the pool is untouched by a placement");
            }
            helper.assertTrue(
                    Notes.solved(
                            Objects.requireNonNull(note(entry)),
                            ResearchService.data(player.registryAccess()).view(rec())),
                    "the note is solved");
        }
    }

    /**
     * Runs each step one tick after the last, then succeeds. A step does at most 8 actions, the
     * per-tick cap.
     */
    private static void steps(GameTestHelper helper, List<Runnable> steps) {
        for (int i = 0; i < steps.size(); i++) {
            helper.runAfterDelay(i + 1, steps.get(i));
        }
        helper.runAfterDelay(steps.size() + 1, helper::succeed);
    }

    /** Runs {@code body} with {@code research.mode} switched, then switches it back. */
    private static void inMode(ResearchConfig.Mode mode, Runnable body) {
        ResearchConfig previous = ResearchService.config();
        ResearchService.installConfig(new ResearchConfig(mode));
        try {
            body.run();
        } finally {
            ResearchService.installConfig(previous);
        }
    }

    private static Map<String, Integer> plenty() {
        Map<String, Integer> pool = new TreeMap<>();
        for (String a : List.of("aer", "ignis", "terra", "aqua", "ordo", "perditio")) {
            pool.put("vftv:" + a, 40);
        }
        return pool;
    }

    // ---- Tests -------------------------------------------------------------------------------

    /** Using the book opens its menu and sends quotes; a book in no hand closes it. */
    private static void bookOpensMenu(GameTestHelper helper) {
        Bench b = new Bench(helper, plenty());
        CommonplaceMenu menu = b.menu();
        ResearchFeedback.Quotes q = Objects.requireNonNull(menu.quotes(), "quotes on open");
        helper.assertValueEqual(q.mode(), 1, "standard mode");
        helper.assertTrue(q.revision() == menu.revision() && q.revision() > 0, "a revision");
        ResearchFeedback.EntryQuote tutorial = Objects.requireNonNull(q.of(NOTE), "quoted");
        helper.assertValueEqual(tutorial.price(), Map.of("vftv:lux", 2), "the skip price");
        helper.assertTrue(tutorial.warp() == null, "no warp on a Stage 1 note");
        helper.assertTrue(q.of("vftv:silence_beneath") == null, "a HIDDEN entry is never quoted");
        helper.assertTrue(
                sent(b.player).stream().anyMatch(p -> p instanceof ResearchFeedback.Quotes),
                "quotes sent to the owner");
        helper.assertTrue(menu.stillValid(b.player), "valid while the book is in a hand");
        b.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        helper.assertFalse(menu.stillValid(b.player), "invalid once the book is gone");
        helper.succeed();
    }

    /** Begin, place, remove, solve and learn at a preset, the pool unchanged until Learn. */
    private static void fullNote(GameTestHelper helper, ResearchConfig.Mode mode) {
        Bench b = new Bench(helper, plenty());
        Map<String, Integer> start = b.pool();
        steps(
                helper,
                List.of(
                        () ->
                                inMode(
                                        mode,
                                        () -> b.ok(b.act(new ResearchAction.Begin(NOTE)), "begin")),
                        () -> inMode(mode, () -> placeAll(b, mode, start)),
                        () -> inMode(mode, () -> learnNote(b, start))));
    }

    private static void placeAll(Bench b, ResearchConfig.Mode mode, Map<String, Integer> start) {
        Note note = Objects.requireNonNull(b.note(NOTE));
        ResearchTuning.Preset p = ResearchService.preset(mode);
        b.helper.assertValueEqual(
                note.snapshot(), ResearchService.tuning().snapshot(p), "snapshot");
        b.helper.assertValueEqual(note.silent().size(), p.silentCells(), "silent cells");
        b.helper.assertValueEqual(b.pool(), start, "begin spends nothing");
        b.solve(NOTE, mode);
        Note.Placed stray = stray(Objects.requireNonNull(b.note(NOTE)));
        b.ok(
                b.act(
                        new ResearchAction.Place(
                                NOTE,
                                b.rev(NOTE),
                                stray.cell().q(),
                                stray.cell().r(),
                                stray.aspect())),
                "a stray");
        b.ok(
                b.act(
                        new ResearchAction.Remove(
                                NOTE, b.rev(NOTE), stray.cell().q(), stray.cell().r())),
                "removed");
        b.helper.assertValueEqual(b.pool(), start, "remove spends nothing");
    }

    private static void learnNote(Bench b, Map<String, Integer> start) {
        Note note = Objects.requireNonNull(b.note(NOTE));
        ResearchService.Data data = ResearchService.data(b.player.registryAccess());
        DebitPlan plan =
                DebitPlan.of(
                        Notes.needs(note),
                        start,
                        data.aspects(),
                        data.view(b.rec()),
                        note.snapshot().parentCost());
        b.ok(
                b.act(
                        new ResearchAction.Learn(
                                NOTE, note.revision(), b.quotes(), Resolver.NO_WARP)),
                "learn");
        b.helper.assertTrue(b.rec().isLearned(NOTE), "learned");
        b.helper.assertTrue(b.note(NOTE) == null, "the note is gone");
        Map<String, Integer> left = new TreeMap<>(start);
        plan.debits().forEach((a, n) -> left.merge(a, -n, Integer::sum));
        left.values().removeIf(n -> n == 0);
        b.helper.assertTrue(!plan.debits().isEmpty(), "Learn spends something");
        b.helper.assertValueEqual(new TreeMap<>(b.pool()), left, "Learn spent its plan");
    }

    private static int sum(Map<String, Integer> m) {
        return m.values().stream().mapToInt(Integer::intValue).sum();
    }

    /** An empty cell of the note, neither silent nor an anchor, away from the centre, with Lux. */
    private static Note.Placed stray(Note note) {
        for (int q = -note.radius(); q <= note.radius(); q++) {
            for (int r = -note.radius(); r <= note.radius(); r++) {
                Note.Cell c = new Note.Cell(q, r);
                if (c.distance() <= note.radius()
                        && !Notes.occupied(note).containsKey(c)
                        && !note.silent().contains(c)
                        && Math.abs(q) + Math.abs(r) >= 2) {
                    return new Note.Placed(c, "vftv:lux");
                }
            }
        }
        throw new IllegalStateException("no empty cell");
    }

    /** Skip mode: no begin, Buy at the quoted price, an open note dropped by the purchase. */
    private static void skipPurchase(GameTestHelper helper) {
        Bench b = new Bench(helper, plenty());
        Map<String, Integer> start = b.pool();
        steps(
                helper,
                List.of(
                        () ->
                                inMode(
                                        ResearchConfig.Mode.SKIP,
                                        () -> {
                                            b.menu().quote(b.player, false);
                                            helper.assertValueEqual(
                                                    Objects.requireNonNull(b.menu().quotes())
                                                            .mode(),
                                                    3,
                                                    "quotes carry skip");
                                            b.refused(
                                                    b.act(new ResearchAction.Begin(NOTE)),
                                                    ResearchActions.NOT_NOW,
                                                    "no begin in skip");
                                        }),
                        () -> b.ok(b.act(new ResearchAction.Begin(NOTE)), "begin at standard"),
                        () ->
                                inMode(
                                        ResearchConfig.Mode.SKIP,
                                        () -> {
                                            b.refused(
                                                    b.act(
                                                            new ResearchAction.Buy(
                                                                    NOTE,
                                                                    b.quotes(),
                                                                    new TreeMap<>(
                                                                            Map.of("vftv:lux", 1)),
                                                                    Resolver.NO_WARP)),
                                                    SharedMessages.COST_CHANGED,
                                                    "a stale price echo");
                                            helper.assertValueEqual(b.pool(), start, "no debit");
                                        }),
                        () ->
                                inMode(
                                        ResearchConfig.Mode.SKIP,
                                        () -> {
                                            b.ok(
                                                    b.act(
                                                            new ResearchAction.Buy(
                                                                    NOTE,
                                                                    b.quotes(),
                                                                    new TreeMap<>(
                                                                            Map.of("vftv:lux", 2)),
                                                                    Resolver.NO_WARP)),
                                                    "bought");
                                            helper.assertTrue(b.rec().isLearned(NOTE), "learned");
                                            helper.assertTrue(b.note(NOTE) == null, "note dropped");
                                            helper.assertValueEqual(
                                                    sum(start) - sum(b.pool()),
                                                    4,
                                                    "Lux 2 synthesised from Aer 2 and Ignis 2");
                                        })));
    }

    /** A warp-bearing purchase applies the previewed warp. */
    private static void skipWarpPurchase(GameTestHelper helper) {
        Bench b = new Bench(helper, plenty());
        String bell = "vftv:hollow_bell";
        steps(
                helper,
                List.of(
                        () -> {
                            Resolver.grant(b.player, List.of("vftv:choirs_road"));
                            b.menu().quote(b.player, false);
                        },
                        () ->
                                inMode(
                                        ResearchConfig.Mode.SKIP,
                                        () -> {
                                            b.menu().quote(b.player, false);
                                            ResearchFeedback.EntryQuote q =
                                                    Objects.requireNonNull(
                                                            Objects.requireNonNull(
                                                                            b.menu().quotes())
                                                                    .of(bell));
                                            WarpPreview warp = Objects.requireNonNull(q.warp());
                                            helper.assertValueEqual(
                                                    warp, new WarpPreview(2, 2, 0, false), "warp");
                                            b.ok(
                                                    b.act(
                                                            new ResearchAction.Buy(
                                                                    bell,
                                                                    b.quotes(),
                                                                    q.price(),
                                                                    warp)),
                                                    "bought");
                                            helper.assertTrue(b.rec().isLearned(bell), "learned");
                                            var w =
                                                    Objects.requireNonNull(
                                                            WarpService.record(b.player));
                                            helper.assertTrue(
                                                    w.temporary() == 2 && w.lasting() == 2,
                                                    "the warp applied");
                                        })));
    }

    /** Abandon spends nothing; a short placement and a short Learn are refused, nothing debited. */
    private static void abandonAndShort(GameTestHelper helper) {
        Bench b = new Bench(helper, plenty());
        Map<String, Integer> start = b.pool();
        steps(
                helper,
                List.of(
                        () -> b.ok(b.act(new ResearchAction.Begin(NOTE)), "begin"),
                        () -> {
                            b.ok(b.act(new ResearchAction.Abandon(NOTE, b.rev(NOTE))), "abandon");
                            helper.assertTrue(b.note(NOTE) == null, "dropped");
                            helper.assertValueEqual(
                                    b.rec().attempts().getOrDefault(NOTE, 0), 1, "attempt counted");
                            helper.assertValueEqual(b.pool(), start, "abandon spends nothing");
                        },
                        () -> b.ok(b.act(new ResearchAction.Begin(NOTE)), "begin again"),
                        () -> {
                            b.solve(NOTE, ResearchConfig.Mode.STANDARD);
                            b.ok(
                                    b.act(new ResearchAction.Abandon(NOTE, b.rev(NOTE))),
                                    "abandon after solving");
                            helper.assertValueEqual(b.pool(), start, "still nothing spent");
                            helper.assertValueEqual(
                                    b.rec().attempts().getOrDefault(NOTE, 0), 2, "attempt");
                        },
                        () -> b.ok(b.act(new ResearchAction.Begin(NOTE)), "a third begin"),
                        () -> {
                            b.knowledge(KNOWN, Map.of("vftv:aer", 1));
                            Note.Placed first =
                                    b.witness(NOTE, ResearchConfig.Mode.STANDARD).get(0);
                            b.refused(
                                    b.act(
                                            new ResearchAction.Place(
                                                    NOTE,
                                                    b.rev(NOTE),
                                                    first.cell().q(),
                                                    first.cell().r(),
                                                    first.aspect())),
                                    ResearchActions.SHORT,
                                    "a short would-be Learn plan");
                            helper.assertTrue(
                                    Objects.requireNonNull(b.note(NOTE)).placements().isEmpty(),
                                    "nothing placed");
                            b.knowledge(KNOWN, plenty());
                            b.solve(NOTE, ResearchConfig.Mode.STANDARD);
                            b.knowledge(KNOWN, Map.of("vftv:aer", 1));
                        },
                        () -> {
                            ResearchFeedback.Result r =
                                    b.act(
                                            new ResearchAction.Learn(
                                                    NOTE,
                                                    b.rev(NOTE),
                                                    b.quotes(),
                                                    Resolver.NO_WARP));
                            b.refused(r, ResearchActions.SHORT, "a Learn short after synthesis");
                            helper.assertValueEqual(
                                    b.pool(), Map.of("vftv:aer", 1), "nothing debited");
                            helper.assertFalse(b.rec().isLearned(NOTE), "nothing learned");
                        }));
    }

    /** A mode switch mid-note keeps the note's snapshot and board. */
    private static void modeSwitch(GameTestHelper helper) {
        Bench b = new Bench(helper, plenty());
        Note[] begun = new Note[1];
        steps(
                helper,
                List.of(
                        () ->
                                inMode(
                                        ResearchConfig.Mode.HARD,
                                        () -> b.ok(b.act(new ResearchAction.Begin(NOTE)), "begin")),
                        () -> {
                            begun[0] = Objects.requireNonNull(b.note(NOTE));
                            helper.assertValueEqual(begun[0].silent().size(), 5, "hard: silent");
                            inMode(
                                    ResearchConfig.Mode.GENTLE,
                                    () -> {
                                        helper.assertValueEqual(
                                                Objects.requireNonNull(b.note(NOTE)),
                                                begun[0],
                                                "the board is kept");
                                        b.solve(NOTE, ResearchConfig.Mode.HARD);
                                    });
                            helper.assertValueEqual(
                                    Objects.requireNonNull(b.note(NOTE)).snapshot(),
                                    begun[0].snapshot(),
                                    "the snapshot is kept");
                        }));
    }

    /** The checked-commit template: stale revisions and echoes refuse and re-quote. */
    private static void checkedCommits(GameTestHelper helper) {
        Bench b = new Bench(helper, plenty());
        steps(
                helper,
                List.of(
                        () -> b.ok(b.act(new ResearchAction.Begin(NOTE)), "begin"),
                        () -> {
                            b.solve(NOTE, ResearchConfig.Mode.STANDARD);
                            b.refused(
                                    b.act(
                                            new ResearchAction.Place(
                                                    NOTE, b.rev(NOTE) - 1, 0, 0, "vftv:aer")),
                                    ResearchActions.STALE,
                                    "a stale note revision on place");
                        },
                        () -> {
                            int seen = b.quotes();
                            b.refused(
                                    b.act(
                                            new ResearchAction.Learn(
                                                    NOTE, b.rev(NOTE), seen - 1, Resolver.NO_WARP)),
                                    SharedMessages.COST_CHANGED,
                                    "a stale quotes revision");
                            helper.assertTrue(
                                    b.quotes() > seen, "fresh quotes with a new revision");
                            helper.assertFalse(b.rec().isLearned(NOTE), "nothing learned");
                        },
                        () ->
                                b.refused(
                                        b.act(
                                                new ResearchAction.Learn(
                                                        NOTE,
                                                        b.rev(NOTE) - 1,
                                                        b.quotes(),
                                                        Resolver.NO_WARP)),
                                        SharedMessages.COST_CHANGED,
                                        "a stale note revision on learn"),
                        () ->
                                b.ok(
                                        b.act(
                                                new ResearchAction.Learn(
                                                        NOTE,
                                                        b.rev(NOTE),
                                                        b.quotes(),
                                                        Resolver.NO_WARP)),
                                        "a retry with the fresh quote")));
    }

    /** Hostile input changes nothing: each case leaves both attachments and warp as they were. */
    private static void hostileActions(GameTestHelper helper) {
        Bench b = new Bench(helper, plenty());
        Object[] before = new Object[3];
        Runnable snapshot =
                () -> {
                    before[0] = b.rec();
                    before[1] = Objects.requireNonNull(KnowledgeService.get(b.player));
                    before[2] = Objects.requireNonNull(WarpService.record(b.player));
                };
        Runnable unchanged =
                () -> {
                    helper.assertValueEqual(b.rec(), before[0], "research unchanged");
                    helper.assertValueEqual(
                            Objects.requireNonNull(KnowledgeService.get(b.player)),
                            before[1],
                            "knowledge unchanged");
                    helper.assertValueEqual(
                            Objects.requireNonNull(WarpService.record(b.player)),
                            before[2],
                            "warp unchanged");
                };
        steps(
                helper,
                List.of(
                        () -> b.ok(b.act(new ResearchAction.Begin(NOTE)), "begin"),
                        () -> {
                            snapshot.run();
                            Note note = Objects.requireNonNull(b.note(NOTE));
                            long rev = note.revision();
                            Note.Placed anchor = note.anchors().get(0);
                            helper.assertTrue(
                                    b.act(
                                                    b.menu().containerId + 1,
                                                    b.request++,
                                                    new ResearchAction.Bookmark(NOTE, true))
                                            == null,
                                    "a wrong container id is dropped");
                            b.refused(
                                    b.act(new ResearchAction.Place(NOTE, rev, 7, 0, "vftv:aer")),
                                    ResearchActions.NOT_NOW,
                                    "a cell outside the grid");
                            b.refused(
                                    b.act(
                                            new ResearchAction.Place(
                                                    NOTE,
                                                    rev,
                                                    anchor.cell().q(),
                                                    anchor.cell().r(),
                                                    "vftv:aer")),
                                    ResearchActions.NOT_NOW,
                                    "an anchor cell");
                            Note.Placed free = stray(note);
                            b.refused(
                                    b.act(
                                            new ResearchAction.Place(
                                                    NOTE,
                                                    rev,
                                                    free.cell().q(),
                                                    free.cell().r(),
                                                    "vftv:tenebrae")),
                                    ResearchActions.NOT_NOW,
                                    "an unknown aspect");
                            b.refused(
                                    b.act(
                                            new ResearchAction.Remove(
                                                    NOTE, rev, free.cell().q(), free.cell().r())),
                                    ResearchActions.NOT_NOW,
                                    "nothing to remove");
                            b.refused(
                                    b.act(
                                            b.menu().containerId,
                                            1,
                                            new ResearchAction.Bookmark(NOTE, true)),
                                    ResearchActions.STALE,
                                    "a replayed request id");
                            unchanged.run();
                        },
                        () -> {
                            int results = 0;
                            for (int i = 0; i < ResearchLimits.MAX_ACTIONS_PER_TICK + 4; i++) {
                                if (b.act(new ResearchAction.Bookmark("vftv:nowhere", false))
                                        != null) {
                                    results++;
                                }
                            }
                            helper.assertValueEqual(
                                    results, ResearchLimits.MAX_ACTIONS_PER_TICK, "flood capped");
                        },
                        () -> {
                            snapshot.run();
                            b.ok(
                                    b.act(new ResearchAction.Abandon(NOTE, b.rev(NOTE))),
                                    "one commit");
                            helper.assertTrue(
                                    b.act(new ResearchAction.Begin(NOTE)) == null,
                                    "a second commit this tick is dropped");
                            helper.assertTrue(b.note(NOTE) == null, "only the abandon ran");
                        },
                        () -> {
                            snapshot.run();
                            b.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                            helper.assertTrue(
                                    b.act(new ResearchAction.Begin(NOTE)) == null,
                                    "no book: dropped");
                            unchanged.run();
                            b.player.closeContainer();
                            helper.assertFalse(
                                    b.player.containerMenu instanceof CommonplaceMenu, "closed");
                            ResearchActions.onAction(
                                    new ResearchAction(
                                            1, b.request++, new ResearchAction.Begin(NOTE)),
                                    b.player);
                            unchanged.run();
                        }));
    }

    /** Existing notes are not re-gated; a second begin is refused; a grant drops the note. */
    private static void existingNoteAccess(GameTestHelper helper) {
        Bench b = new Bench(helper, plenty());
        steps(
                helper,
                List.of(
                        () -> b.ok(b.act(new ResearchAction.Begin(NOTE)), "begin"),
                        () -> {
                            b.refused(
                                    b.act(new ResearchAction.Begin(NOTE)),
                                    ResearchActions.ALREADY_OPEN,
                                    "a second begin");
                            Resolver.revoke(b.player, PARENTS);
                            b.knowledge(Set.of("vftv:aer", "vftv:ignis", "vftv:lux"), plenty());
                        },
                        () -> {
                            b.solve(NOTE, ResearchConfig.Mode.STANDARD);
                        },
                        () ->
                                b.ok(
                                        b.act(
                                                new ResearchAction.Learn(
                                                        NOTE,
                                                        b.rev(NOTE),
                                                        b.quotes(),
                                                        Resolver.NO_WARP)),
                                        "learned from the note alone after its parents went"),
                        () ->
                                b.refused(
                                        b.act(new ResearchAction.Begin("vftv:tapping")),
                                        ResearchActions.NOT_NOW,
                                        "its parents first"),
                        () -> {
                            Resolver.grant(b.player, List.of("vftv:first_wand"));
                            b.ok(b.act(new ResearchAction.Begin("vftv:tapping")), "begin");
                        },
                        () -> {
                            helper.assertTrue(b.note("vftv:tapping") != null, "open");
                            Resolver.grant(b.player, List.of("vftv:tapping"));
                            helper.assertTrue(b.note("vftv:tapping") == null, "grant drops it");
                        }));
    }

    /** A resolution that reveals a secret listen delivers its preview at the next change check. */
    private static void quoteRefresh(GameTestHelper helper) {
        Bench b = new Bench(helper, plenty());
        Resolver.grant(b.player, List.of("vftv:being_heard"));
        CommonplaceMenu menu = b.menu();
        menu.broadcastChanges();
        int before = menu.revision();
        helper.assertTrue(
                Objects.requireNonNull(menu.quotes()).of("vftv:silence_beneath") == null, "hidden");
        ItemStack page = new ItemStack(ResearchItems.choirPage());
        page.set(
                ResearchItems.page(),
                ResourceKey.create(
                        com.andrewbrogoff.vftv.api.research.ResearchKeys.REGISTRY,
                        Identifier.parse("vftv:choir/beneath_the_notes")));
        ChoirPageItem.tipIn(b.player, page);
        menu.broadcastChanges();
        ResearchFeedback.EntryQuote q =
                Objects.requireNonNull(menu.quotes()).of("vftv:silence_beneath");
        helper.assertTrue(
                q != null && new WarpPreview(0, 4, 0, false).equals(q.warp()),
                "the revealed listen's preview arrived: " + q);
        helper.assertTrue(menu.revision() > before, "with a new revision");
        int after = menu.revision();
        menu.broadcastChanges();
        helper.assertValueEqual(menu.revision(), after, "no change, no re-send");
        helper.succeed();
    }

    /** Login reconciliation drops a note whose entry is gone and trims missing aspects. */
    private static void reconcile(GameTestHelper helper) {
        Bench b = new Bench(helper, plenty());
        steps(
                helper,
                List.of(
                        () -> b.ok(b.act(new ResearchAction.Begin(NOTE)), "begin"),
                        () -> {
                            Note real = Objects.requireNonNull(b.note(NOTE));
                            Note.Placed free = stray(real);
                            Note trimmed =
                                    Notes.with(
                                            real,
                                            List.of(new Note.Placed(free.cell(), "vftv:gone")),
                                            real.revision());
                            Note ghost =
                                    new Note(
                                            "vftv:gone",
                                            real.snapshot(),
                                            real.radius(),
                                            real.anchors(),
                                            List.of(),
                                            List.of(),
                                            1);
                            ResearchService.update(
                                    b.player, r -> r.withNote(trimmed).withNote(ghost));
                            int attempts = b.rec().attempts().getOrDefault(NOTE, 0);
                            Resolver.login(b.player);
                            helper.assertTrue(b.note("vftv:gone") == null, "a gone entry's note");
                            Note kept = Objects.requireNonNull(b.note(NOTE), "kept");
                            helper.assertTrue(kept.placements().isEmpty(), "a gone aspect removed");
                            helper.assertValueEqual(
                                    b.rec().attempts().getOrDefault(NOTE, 0), attempts, "attempts");
                        }));
    }

    /** The listen action commits through the warp preview the player saw. */
    private static void listenAction(GameTestHelper helper) {
        Bench b = new Bench(helper, plenty());
        String heard = "vftv:being_heard";
        steps(
                helper,
                List.of(
                        () -> {
                            Resolver.grant(b.player, List.of("vftv:ruins"));
                            b.menu().quote(b.player, false);
                            b.refused(
                                    b.act(
                                            new ResearchAction.Listen(
                                                    heard,
                                                    b.quotes(),
                                                    new WarpPreview(2, 0, 0, false))),
                                    SharedMessages.COST_CHANGED,
                                    "a mismatched echo");
                            helper.assertFalse(b.rec().isLearned(heard), "nothing learned");
                        },
                        () -> {
                            b.ok(
                                    b.act(
                                            new ResearchAction.Listen(
                                                    heard,
                                                    b.quotes(),
                                                    new WarpPreview(3, 0, 0, false))),
                                    "listened");
                            helper.assertTrue(b.rec().isLearned(heard), "learned");
                            helper.assertValueEqual(
                                    Objects.requireNonNull(WarpService.record(b.player))
                                            .temporary(),
                                    3,
                                    "temporary 3");
                        },
                        () ->
                                b.refused(
                                        b.act(
                                                new ResearchAction.Listen(
                                                        heard,
                                                        b.quotes(),
                                                        new WarpPreview(3, 0, 0, false))),
                                        ResearchActions.NOT_NOW,
                                        "never twice")));
    }

    /**
     * The offer actions (warp spec, "Accepting"): the live offer in the quotes; a stale seq refused
     * with the offer kept; acceptance; refusal; the cleanup exception for an expired offer; and a
     * second commit in one tick dropped.
     */
    private static void offerActions(GameTestHelper helper) {
        Bench b = new Bench(helper, plenty());
        long g = helper.getLevel().getServer().overworld().getGameTime();
        WarpPreview price = new WarpPreview(3, 0, 0, false);
        steps(
                helper,
                List.of(
                        () -> {
                            WarpService.putOffer(b.player, VoicesGameTests.live(5, g));
                            b.menu().quote(b.player, false);
                            ResearchFeedback.Quotes q = Objects.requireNonNull(b.menu().quotes());
                            helper.assertTrue(
                                    q.offer() != null
                                            && q.offer().seq() == 5
                                            && q.offer().warp().equals(price),
                                    "the offer is quoted: " + q.offer());
                            b.refused(
                                    b.act(new ResearchAction.AcceptOffer(4, b.quotes(), price)),
                                    "message.vftv.warp.offer_gone",
                                    "a stale seq");
                            helper.assertTrue(WarpService.offer(b.player) != null, "kept");
                        },
                        () -> {
                            b.ok(
                                    b.act(new ResearchAction.AcceptOffer(5, b.quotes(), price)),
                                    "accepted");
                            helper.assertValueEqual(
                                    Objects.requireNonNull(WarpService.record(b.player))
                                            .temporary(),
                                    3,
                                    "charged");
                            helper.assertTrue(
                                    Objects.requireNonNull(b.menu().quotes()).offer() == null,
                                    "the quotes refreshed");
                        },
                        () -> {
                            WarpService.putOffer(b.player, VoicesGameTests.live(6, g));
                            b.ok(b.act(new ResearchAction.RefuseOffer(6)), "refused");
                            helper.assertTrue(WarpService.get(b.player).offer() == null, "gone");
                        },
                        () -> {
                            WarpService.putOffer(b.player, VoicesGameTests.expired(7, g));
                            var before = Objects.requireNonNull(WarpService.record(b.player));
                            b.menu().quote(b.player, false);
                            b.refused(
                                    b.act(new ResearchAction.AcceptOffer(7, b.quotes(), price)),
                                    "message.vftv.warp.offer_gone",
                                    "expired");
                            helper.assertTrue(WarpService.get(b.player).offer() == null, "deleted");
                            helper.assertValueEqual(
                                    Objects.requireNonNull(WarpService.record(b.player)),
                                    before,
                                    "nothing charged");
                        },
                        () -> {
                            WarpService.putOffer(b.player, VoicesGameTests.live(8, g));
                            b.ok(b.act(new ResearchAction.RefuseOffer(8)), "the first commit");
                            helper.assertTrue(
                                    b.act(new ResearchAction.AcceptOffer(8, b.quotes(), price))
                                            == null,
                                    "the second commit of the tick is dropped");
                        }));
    }

    /**
     * The Listen rite through the book: refused before the reveal; quoted with no refusal at a
     * Void-touched place; on OK, the book closes; reopened, the quote carries the cooldown.
     */
    private static void listenRiteAction(GameTestHelper helper) {
        Bench b = new Bench(helper, plenty());
        WarpPreview cost = new WarpPreview(0, 2, 0, false);
        steps(
                helper,
                List.of(
                        () -> {
                            VoicesGameTests.place(helper, b.player);
                            b.refused(
                                    b.act(new ResearchAction.ListenRite(b.quotes(), cost)),
                                    "message.vftv.warp.listen_closed",
                                    "before the reveal");
                        },
                        () -> {
                            Resolver.grant(b.player, List.of("vftv:silence_beneath"));
                            WarpService.apply(
                                    b.player,
                                    new WarpCost(0, -1000, 0),
                                    new WarpSource(WarpSourceKind.COMMAND, null));
                            b.menu().quote(b.player, false);
                            ResearchFeedback.RiteQuote rite =
                                    Objects.requireNonNull(
                                            Objects.requireNonNull(b.menu().quotes()).rite());
                            helper.assertTrue(
                                    rite.warp().equals(cost) && rite.refusal().isEmpty(),
                                    "quoted: " + rite);
                            b.ok(b.act(new ResearchAction.ListenRite(b.quotes(), cost)), "rite");
                            helper.assertFalse(
                                    b.player.containerMenu instanceof CommonplaceMenu,
                                    "the book closed");
                            helper.assertValueEqual(
                                    Objects.requireNonNull(WarpService.record(b.player)).lasting(),
                                    2,
                                    "lasting 2");
                        },
                        () -> {
                            CommonplaceBookItem.open(b.player);
                            ResearchFeedback.RiteQuote rite =
                                    Objects.requireNonNull(
                                            Objects.requireNonNull(b.menu().quotes()).rite());
                            helper.assertValueEqual(
                                    rite.refusal(),
                                    "message.vftv.warp.listen_cooldown",
                                    "cooling, quoted");
                        }));
    }

    /** Up to nine bookmarks, never a HIDDEN entry; clearing always works. */
    private static void bookmarks(GameTestHelper helper) {
        Bench b = new Bench(helper, plenty());
        List<String> visible =
                ResearchService.data(b.player.registryAccess()).entries().entrySet().stream()
                        .filter(e -> e.getValue().stage() == 1)
                        .map(Map.Entry::getKey)
                        .sorted()
                        .toList();
        helper.assertTrue(visible.size() >= 10, "enough Stage 1 entries");
        steps(
                helper,
                List.of(
                        () -> {
                            for (String id : visible.subList(0, 8)) {
                                b.ok(b.act(new ResearchAction.Bookmark(id, true)), "set " + id);
                            }
                        },
                        () -> b.ok(b.act(new ResearchAction.Bookmark(visible.get(8), true)), "9th"),
                        () -> {
                            b.ok(b.act(new ResearchAction.Bookmark(visible.get(0), true)), "again");
                            b.refused(
                                    b.act(new ResearchAction.Bookmark(visible.get(9), true)),
                                    ResearchActions.NOT_NOW,
                                    "a tenth");
                            b.refused(
                                    b.act(new ResearchAction.Bookmark("vftv:ruins", true)),
                                    ResearchActions.NOT_NOW,
                                    "a HIDDEN entry");
                            b.ok(b.act(new ResearchAction.Bookmark(visible.get(0), false)), "off");
                            helper.assertValueEqual(
                                    b.rec().bookmarks(), visible.subList(1, 9), "kept in order");
                        }));
    }

    /**
     * Every shipped {@code content/research/path-stage<N>.json}, replayed with the world's real
     * data at the standard preset and in skip, each Stage from the previous one's end in the same
     * mode; and a smoke path over the shipped skeleton's Stage 1, so the harness itself is proved
     * against real aspect values, tags and node compositions.
     */
    private static void affordabilityPaths(GameTestHelper helper) {
        Path project =
                Objects.requireNonNull(PendingAudio.findProject(Path.of("").toAbsolutePath()));
        for (ResearchConfig.Mode mode :
                List.of(ResearchConfig.Mode.STANDARD, ResearchConfig.Mode.SKIP)) {
            AffordabilityPath.World world = replayWorld(helper, project, mode);
            AffordabilityPath.State state = AffordabilityPath.State.EMPTY;
            for (int stage = 1; stage <= 4; stage++) {
                Path file = project.resolve("content/research/path-stage" + stage + ".json");
                if (!Files.exists(file)) {
                    break; // the content tickets ship them in order
                }
                state = AffordabilityPath.parse(read(file)).replay(world, state);
            }
            AffordabilityPath smoke =
                    AffordabilityPath.parse(
                            """
                            {"schema": "vftv:affordability_path/1", "stage": 1,
                             "sources": {
                               "geode": {"advancement": "vftv:find/resonance", "count": 1,
                                         "why": "geodes generate in every overworld"},
                               "starter": {"node": "vftv:starter", "count": 1,
                                           "requires": {"research": ["vftv:sounding"]},
                                           "why": "the starter-node guarantee"},
                               "star": {"scan": "item:minecraft:firework_star", "count": 1,
                                        "why": "craftable from gunpowder and dye"},
                               "drip": {"advancement": "vftv:find/distillation", "count": 1,
                                        "why": "dripstone caves"}
                             },
                             "steps": [
                               {"use": "geode"}, {"use": "starter"}, {"use": "star"},
                               {"learn": "vftv:taking_notes"}, {"learn": "vftv:tapping"},
                               {"learn": "vftv:arcane_crafting"}, {"learn": "vftv:foci"},
                               {"learn": "vftv:tuned_spectacles"}, {"use": "drip"}
                             ]}""");
            AffordabilityPath.State end = smoke.replay(world, AffordabilityPath.State.EMPTY);
            helper.assertTrue(end.research().isLearned("vftv:first_wand"), "a free entry");
            helper.assertTrue(end.research().isLearned("vftv:knots_in_the_air"), "the node find");
            helper.assertValueEqual(end.research().stage(), 2, "Stage 2 opened");
        }
        helper.succeed();
    }

    private static AffordabilityPath.World replayWorld(
            GameTestHelper helper, Path project, ResearchConfig.Mode mode) {
        var access = helper.getLevel().registryAccess();
        ResearchService.Data data = ResearchService.data(access);
        JsonObject guarantees =
                JsonParser.parseString(read(project.resolve("content/nodes/guarantees.json")))
                        .getAsJsonObject()
                        .getAsJsonObject("guarantees");
        JsonObject types =
                JsonParser.parseString(read(project.resolve("content/nodes/types.json")))
                        .getAsJsonObject()
                        .getAsJsonObject("types");
        return new AffordabilityPath.World(
                data.entries(),
                data.aspects(),
                AspectSet.of(access).rules(ScanningService.tuning().discoveryBonus()),
                ResearchNotesGameTests::scanValues,
                id -> nodeValues(guarantees, types, id),
                Resolver::inTag,
                WarpRulesService.current(),
                ResearchService.preset(mode));
    }

    /** A scan key's values through the real lookup (authored, then derived). */
    private static Map<String, Integer> scanValues(String key) {
        int colon = key.indexOf(':');
        Identifier id = Identifier.tryParse(key.substring(colon + 1));
        AspectValues values =
                id == null
                        ? null
                        : switch (key.substring(0, colon)) {
                            case "item" ->
                                    BuiltInRegistries.ITEM
                                            .getOptional(id)
                                            .map(AspectLookup::item)
                                            .orElse(null);
                            case "block" ->
                                    BuiltInRegistries.BLOCK
                                            .getOptional(id)
                                            .map(b -> AspectLookup.block(b.defaultBlockState()))
                                            .orElse(null);
                            default -> null;
                        };
        Map<String, Integer> out = new TreeMap<>();
        if (values != null) {
            values.aspects().forEach((a, n) -> out.put(a.identifier().toString(), n));
        }
        return out;
    }

    /** A node guarantee's composition: its type's aspects, each at the low end of its range. */
    private static Map<String, Integer> nodeValues(
            JsonObject guarantees, JsonObject types, String id) {
        Map<String, Integer> out = new TreeMap<>();
        if (guarantees.get(id) instanceof JsonObject g
                && types.get(g.get("type").getAsString()) instanceof JsonObject type) {
            for (JsonElement a : type.getAsJsonArray("aspects")) {
                JsonObject o = a.getAsJsonObject();
                out.put(o.get("aspect").getAsString(), o.getAsJsonArray("range").get(0).getAsInt());
            }
        }
        return out;
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
