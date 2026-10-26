package com.andrewbrogoff.vftv.core.research;

import com.andrewbrogoff.vftv.api.research.ResearchLimits;
import com.andrewbrogoff.vftv.api.research.ResearchSource;
import com.andrewbrogoff.vftv.api.warp.CommitResult;
import com.andrewbrogoff.vftv.api.warp.WarpCost;
import com.andrewbrogoff.vftv.api.warp.WarpPreview;
import com.andrewbrogoff.vftv.api.warp.WarpSource;
import com.andrewbrogoff.vftv.api.warp.WarpSourceKind;
import com.andrewbrogoff.vftv.core.aspect.KnowledgeService;
import com.andrewbrogoff.vftv.core.aspect.model.Knowledge;
import com.andrewbrogoff.vftv.core.common.Network;
import com.andrewbrogoff.vftv.core.common.TickBudget;
import com.andrewbrogoff.vftv.core.common.model.CheckedCommit;
import com.andrewbrogoff.vftv.core.research.ResearchService.Data;
import com.andrewbrogoff.vftv.core.research.model.AspectGraph;
import com.andrewbrogoff.vftv.core.research.model.DebitPlan;
import com.andrewbrogoff.vftv.core.research.model.EntryKind;
import com.andrewbrogoff.vftv.core.research.model.Note;
import com.andrewbrogoff.vftv.core.research.model.NoteGrid;
import com.andrewbrogoff.vftv.core.research.model.Notes;
import com.andrewbrogoff.vftv.core.research.model.RenderState;
import com.andrewbrogoff.vftv.core.research.model.ResearchEntry;
import com.andrewbrogoff.vftv.core.research.model.ResearchRecord;
import com.andrewbrogoff.vftv.core.research.model.ResearchTuning;
import com.andrewbrogoff.vftv.core.text.model.SharedMessages;
import com.andrewbrogoff.vftv.core.warp.WarpAttachment;
import com.andrewbrogoff.vftv.core.warp.WarpService;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The Commonplace Book's server side (research spec, "Sync and state"): the quotes an open book
 * prices its commits from, and the handler of {@link ResearchAction}, which runs the server checks
 * in the spec's order, the first failure refusing with nothing changed:
 *
 * <ol>
 *   <li>rate: at most 8 actions per player per tick, at most 1 of them a commit; excess dropped;
 *   <li>the request id rises within the connection, else {@code message.vftv.research.stale};
 *   <li>the book's menu is open with that container id and still valid, else dropped;
 *   <li>a quoted commit echoes the current quotes' revision, else {@code STALE};
 *   <li>the variant's eligibility, its note revision included;
 *   <li>the debit plan, where the variant needs one;
 *   <li>the echoes match the current quote, else {@code STALE}.
 * </ol>
 *
 * {@code STALE} answers {@code message.vftv.cost_changed} and sends fresh quotes. Server thread
 * only.
 */
public final class ResearchActions {
    /** "This note will not lie flat in my book.": generation refused. */
    public static final String NOT_FLAT = "message.vftv.research.not_flat";

    /** "Not enough %s.": the first short leaf, as its aspect id. */
    public static final String SHORT = "message.vftv.research.short";

    /** "That note is already open in my book." */
    public static final String ALREADY_OPEN = "message.vftv.research.already_open";

    /** "My book has no room for another open note." */
    public static final String NO_ROOM = "message.vftv.research.no_room";

    /** "My notes have moved on. I look again.": a stale request id or note revision. */
    public static final String STALE = "message.vftv.research.stale";

    /** "I can't do that yet.": any other eligibility failure. */
    public static final String NOT_NOW = "message.vftv.research.not_now";

    /** The reveal that opens the Listen rite. */
    static final String SILENCE_BENEATH = "vftv:silence_beneath";

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Per connection, which a respawn keeps and a reconnect replaces (weak, so no cleanup). */
    private static final Map<Object, Window> WINDOWS = new WeakHashMap<>();

    private static @Nullable Network network;
    private static BiConsumer<ServerPlayer, CustomPacketPayload> sender =
            (player, payload) ->
                    Objects.requireNonNull(network, "ResearchFeature.init")
                            .toPlayer(player, payload);

    private ResearchActions() {}

    static void init(Network seam) {
        network = seam;
    }

    /** Sends a feedback payload to its owner. */
    static void send(ServerPlayer player, CustomPacketPayload payload) {
        sender.accept(player, payload);
    }

    /** Replaces where feedback goes and returns the previous; for GameTests. */
    public static BiConsumer<ServerPlayer, CustomPacketPayload> swapSender(
            BiConsumer<ServerPlayer, CustomPacketPayload> next) {
        BiConsumer<ServerPlayer, CustomPacketPayload> previous = sender;
        sender = next;
        return previous;
    }

    /** One connection's rate window (one server tick's actions and commits) and last request. */
    private static final class Window {
        long tick = -1;
        int actions;
        int commits;
        int lastRequest = -1;
    }

    // ---- Quotes --------------------------------------------------------------------------------

    /**
     * The book's quotes at {@code revision}: the mode, and every non-HIDDEN note's skip price and
     * every non-HIDDEN note or listen entry's warp preview. Empty while the record is unreadable.
     */
    public static ResearchFeedback.Quotes quotes(ServerPlayer player, int revision) {
        int mode =
                switch (ResearchService.config().mode()) {
                    case GENTLE -> 0;
                    case STANDARD -> 1;
                    case HARD -> 2;
                    case SKIP -> 3;
                };
        ResearchRecord record = ResearchService.record(player);
        List<ResearchFeedback.EntryQuote> out = new ArrayList<>();
        if (record != null) {
            Data data = data(player);
            new TreeMap<>(data.entries())
                    .forEach(
                            (id, e) -> {
                                if (e.kind().committed() && !RenderState.hidden(id, e, record)) {
                                    SortedMap<String, Integer> price =
                                            e.kind() == EntryKind.NOTE
                                                    ? ResearchService.skipPrice(data, id)
                                                    : new TreeMap<>();
                                    out.add(
                                            new ResearchFeedback.EntryQuote(
                                                    id, price, preview(player, id, e)));
                                }
                            });
        }
        ResearchFeedback.OfferQuote offer = null;
        WarpAttachment.Offer live = WarpService.offer(player);
        WarpPreview offerWarp = WarpService.offerPreview(player);
        if (live != null && offerWarp != null) {
            offer = new ResearchFeedback.OfferQuote(live.seq(), offerWarp);
        }
        WarpPreview riteWarp = WarpService.ritePreview(player);
        ResearchFeedback.RiteQuote rite =
                riteWarp == null
                        ? null
                        : new ResearchFeedback.RiteQuote(
                                riteWarp, WarpService.riteRefusal(player).key());
        return new ResearchFeedback.Quotes(revision, mode, out, offer, rite);
    }

    /** The player's preview of an entry's warp, or null when it carries none or can't apply. */
    private static @Nullable WarpPreview preview(ServerPlayer player, String id, ResearchEntry e) {
        if (!e.warp().any()) {
            return null;
        }
        CommitResult r = WarpService.preview(player, cost(e), source(id));
        return r.status() == CommitResult.Status.OK ? r.preview() : null;
    }

    private static WarpCost cost(ResearchEntry e) {
        return new WarpCost(e.warp().temporary(), e.warp().lasting(), e.warp().permanent());
    }

    private static WarpSource source(String id) {
        return new WarpSource(WarpSourceKind.ENTRY, Identifier.parse(id));
    }

    // ---- The handler ---------------------------------------------------------------------------

    /** What a variant did: done, or refused with a key and its arguments, or {@code STALE}. */
    private record Reply(String reason, List<String> args, boolean stale) {
        static final Reply OK = new Reply("", List.of(), false);
        static final Reply STALE_COMMIT = new Reply(SharedMessages.COST_CHANGED, List.of(), true);

        static Reply refuse(String reason, String... args) {
            return new Reply(reason, List.of(args), false);
        }
    }

    /** The server handler of {@link ResearchAction}, on the server thread. */
    public static void onAction(ResearchAction action, ServerPlayer player) {
        try (var _ = TickBudget.enter("research")) {
            handle(action, player);
        }
    }

    private static void handle(ResearchAction action, ServerPlayer player) {
        long tick = player.level().getServer().getTickCount();
        Window rate = WINDOWS.computeIfAbsent(player.connection, k -> new Window());
        if (rate.tick != tick) {
            rate.tick = tick;
            rate.actions = 0;
            rate.commits = 0;
        }
        boolean commit = action.variant().commit();
        if (rate.actions >= ResearchLimits.MAX_ACTIONS_PER_TICK
                || (commit && rate.commits >= ResearchLimits.MAX_COMMITS_PER_TICK)) {
            return; // check 1: excess is dropped with no reply
        }
        rate.actions++;
        if (commit) {
            rate.commits++;
        }
        if (action.requestId() <= rate.lastRequest) {
            reply(player, action, Reply.refuse(STALE)); // check 2
            return;
        }
        rate.lastRequest = action.requestId();
        if (!(player.containerMenu instanceof CommonplaceMenu menu)
                || menu.containerId != action.containerId()
                || !menu.stillValid(player)) {
            return; // check 3: a context failure has nothing to report to
        }
        menu.quote(player, false); // the change check at the start of every action
        Reply reply = variant(player, menu, action.variant());
        reply(player, action, reply);
        menu.quote(player, reply.stale());
    }

    private static void reply(ServerPlayer player, ResearchAction action, Reply reply) {
        send(
                player,
                new ResearchFeedback.Result(
                        action.requestId(),
                        reply.reason().isEmpty(),
                        reply.reason(),
                        reply.args()));
    }

    private static Reply variant(
            ServerPlayer player, CommonplaceMenu menu, ResearchAction.Variant variant) {
        ResearchRecord record = ResearchService.record(player);
        Knowledge knowledge = KnowledgeService.get(player);
        if (record == null || knowledge == null) {
            return Reply.refuse(SharedMessages.UNREADABLE);
        }
        int quoted =
                switch (variant) {
                    case ResearchAction.Learn v -> v.revision();
                    case ResearchAction.Buy v -> v.revision();
                    case ResearchAction.Listen v -> v.revision();
                    case ResearchAction.AcceptOffer v -> v.revision();
                    case ResearchAction.ListenRite v -> v.revision();
                    default -> menu.revision();
                };
        if (CheckedCommit.revision(menu.revision(), quoted) == CheckedCommit.Outcome.STALE) {
            return Reply.STALE_COMMIT; // check 4
        }
        Data data = data(player);
        Context c = new Context(player, data, record, knowledge, data.view(record));
        return switch (variant) {
            case ResearchAction.Begin v -> begin(c, v);
            case ResearchAction.Place v -> place(c, v);
            case ResearchAction.Remove v -> remove(c, v);
            case ResearchAction.Abandon v -> abandon(c, v);
            case ResearchAction.Learn v -> learn(c, v);
            case ResearchAction.Buy v -> buy(c, v);
            case ResearchAction.Listen v -> listen(c, v);
            case ResearchAction.Bookmark v -> bookmark(c, v);
            case ResearchAction.AcceptOffer v ->
                    answer(
                            c.player(),
                            WarpService.acceptOffer(c.player(), v.offerSeq(), v.echoedWarp()));
            case ResearchAction.RefuseOffer v ->
                    answer(c.player(), WarpService.refuseOffer(c.player(), v.offerSeq()));
            case ResearchAction.ListenRite v -> listenRite(c, v);
        };
    }

    /** What every variant reads, once. */
    private record Context(
            ServerPlayer player,
            Data data,
            ResearchRecord record,
            Knowledge knowledge,
            AspectGraph.LinkView view) {
        @Nullable ResearchEntry entry(String id) {
            return data.entries().get(id);
        }

        /** Not HIDDEN, and its parents and hidden parents learned. */
        boolean reachable(String id, ResearchEntry e) {
            return !RenderState.hidden(id, e, record)
                    && e.allParents().stream().allMatch(record::isLearned);
        }

        boolean canBegin(ResearchEntry e, ResearchTuning.Preset preset) {
            return Notes.canBegin(Notes.used(e, preset), knowledge.known(), view);
        }

        DebitPlan plan(Map<String, Integer> needs, int parentCost) {
            return DebitPlan.of(needs, knowledge.pool(), data.aspects(), view, parentCost);
        }
    }

    private static Reply begin(Context c, ResearchAction.Begin v) {
        ResearchEntry e = c.entry(v.entry());
        if (e == null
                || e.kind() != EntryKind.NOTE
                || c.record().isLearned(v.entry())
                || !c.reachable(v.entry(), e)) {
            return Reply.refuse(NOT_NOW);
        }
        if (c.record().notes().containsKey(v.entry())) {
            return Reply.refuse(ALREADY_OPEN);
        }
        ResearchConfig.Mode mode = ResearchService.config().mode();
        ResearchTuning.Preset preset = ResearchService.preset(mode);
        if (mode == ResearchConfig.Mode.SKIP || !c.canBegin(e, preset)) {
            return Reply.refuse(NOT_NOW);
        }
        if (c.record().notes().size() >= ResearchLimits.MAX_OPEN_NOTES) {
            return Reply.refuse(NO_ROOM);
        }
        ServerPlayer player = c.player();
        long seed =
                Notes.seed(
                        player.level().getServer().overworld().getSeed(),
                        player.getUUID(),
                        c.record().attempts().getOrDefault(v.entry(), 0),
                        v.entry());
        Notes.Generated g =
                Notes.generate(
                        seed,
                        v.entry(),
                        Notes.used(e, preset),
                        c.knowledge().known(),
                        c.view(),
                        ResearchService.tuning(),
                        preset,
                        0,
                        LOGGER::error);
        Note note = g.note();
        if (note == null) {
            LOGGER.error("The note of research entry {} will not lie flat; refused", v.entry());
            return Reply.refuse(NOT_FLAT);
        }
        if (g.fellBack()) {
            LOGGER.debug("The note of research entry {} was laid by the fallback", v.entry());
        }
        ResearchService.update(player, r -> r.withNote(note));
        return Reply.OK;
    }

    /** The open note of the entry at the echoed revision, or null. */
    private static @Nullable Note note(Context c, String entry, long revision) {
        Note note = c.record().notes().get(entry);
        return note != null && note.revision() == revision ? note : null;
    }

    private static Reply place(Context c, ResearchAction.Place v) {
        Note note = note(c, v.entry(), v.noteRevision());
        if (note == null) {
            return Reply.refuse(STALE);
        }
        Note.Cell cell = new Note.Cell(v.q(), v.r());
        if (!NoteGrid.inside(cell, note.radius())
                || note.silent().contains(cell)
                || Notes.occupied(note).containsKey(cell)
                || !c.knowledge().known().contains(v.aspect())
                || !c.data().aspects().contains(v.aspect())) {
            return Reply.refuse(NOT_NOW);
        }
        List<Note.Placed> placements = new ArrayList<>(note.placements());
        placements.add(new Note.Placed(cell, v.aspect()));
        Note next = Notes.with(note, placements, note.revision());
        DebitPlan plan = c.plan(Notes.needs(next), note.snapshot().parentCost());
        if (!plan.ok()) {
            return Reply.refuse(SHORT, Objects.requireNonNull(plan.shortLeaf()));
        }
        ResearchService.update(c.player(), r -> r.withNote(next));
        return Reply.OK;
    }

    private static Reply remove(Context c, ResearchAction.Remove v) {
        Note note = note(c, v.entry(), v.noteRevision());
        if (note == null) {
            return Reply.refuse(STALE);
        }
        Note.Cell cell = new Note.Cell(v.q(), v.r());
        List<Note.Placed> placements =
                note.placements().stream().filter(p -> !p.cell().equals(cell)).toList();
        if (placements.size() == note.placements().size()) {
            return Reply.refuse(NOT_NOW);
        }
        Note next = Notes.with(note, placements, note.revision());
        ResearchService.update(c.player(), r -> r.withNote(next));
        return Reply.OK;
    }

    private static Reply abandon(Context c, ResearchAction.Abandon v) {
        if (note(c, v.entry(), v.noteRevision()) == null) {
            return Reply.refuse(STALE);
        }
        ResearchService.update(c.player(), r -> r.abandon(v.entry()));
        return Reply.OK;
    }

    private static Reply learn(Context c, ResearchAction.Learn v) {
        Note open = c.record().notes().get(v.entry());
        ResearchEntry e = c.entry(v.entry());
        if (open == null || e == null || c.record().isLearned(v.entry())) {
            return Reply.refuse(NOT_NOW);
        }
        if (open.revision() != v.noteRevision()) {
            return Reply.STALE_COMMIT;
        }
        if (!Notes.solved(open, c.view())) {
            return Reply.refuse(NOT_NOW);
        }
        DebitPlan plan = c.plan(Notes.needs(open), open.snapshot().parentCost());
        return commit(c, v.entry(), e, plan, v.echoedWarp(), ResearchSource.SOLVED);
    }

    private static Reply buy(Context c, ResearchAction.Buy v) {
        ResearchEntry e = c.entry(v.entry());
        if (ResearchService.config().mode() != ResearchConfig.Mode.SKIP
                || e == null
                || e.kind() != EntryKind.NOTE
                || c.record().isLearned(v.entry())
                || !c.reachable(v.entry(), e)
                || !c.canBegin(e, ResearchService.preset(ResearchConfig.Mode.SKIP))) {
            return Reply.refuse(NOT_NOW);
        }
        SortedMap<String, Integer> price = ResearchService.skipPrice(c.data(), v.entry());
        DebitPlan plan = c.plan(price, ResearchService.tuning().parentCost());
        if (plan.ok() && !price.equals(v.echoedPrice())) {
            return Reply.STALE_COMMIT;
        }
        return commit(c, v.entry(), e, plan, v.echoedWarp(), ResearchSource.BOUGHT);
    }

    /**
     * Checks 6 and 7 and the write for Learn and Buy: the plan, the warp echo, then the warp
     * commit, the debit and the learning (which drops any open note), with no foreign code between
     * the checks and the warp commit.
     */
    private static Reply commit(
            Context c,
            String id,
            ResearchEntry e,
            DebitPlan plan,
            WarpPreview echo,
            ResearchSource source) {
        if (!plan.ok()) {
            return Reply.refuse(SHORT, Objects.requireNonNull(plan.shortLeaf()));
        }
        WarpPreview applied = Resolver.NO_WARP;
        if (e.warp().any()) {
            WarpPreview current = preview(c.player(), id, e);
            if (current == null) {
                return Reply.refuse(NOT_NOW);
            }
            if (!current.equals(echo)) {
                return Reply.STALE_COMMIT;
            }
            CommitResult result = WarpService.commit(c.player(), cost(e), source(id), echo);
            Reply refused = refusal(result);
            if (refused != null) {
                return refused;
            }
            applied = Objects.requireNonNull(result.preview());
        }
        if (!KnowledgeService.debit(c.player(), plan.debits())) {
            LOGGER.error(
                    "Research entry {}: the planned debit {} failed after its checks",
                    id,
                    plan.debits());
            return Reply.refuse(SharedMessages.UNREADABLE);
        }
        Resolver.learn(c.player(), id, source, applied, plan.debits(), r -> r);
        return Reply.OK;
    }

    /** A warp commit's refusal, or null when it went through. */
    private static @Nullable Reply refusal(CommitResult result) {
        return switch (result.status()) {
            case OK -> result.preview() == null ? Reply.refuse(NOT_NOW) : null;
            case STALE -> Reply.STALE_COMMIT;
            case BUSY -> Reply.refuse(SharedMessages.BUSY);
            case UNREADABLE -> Reply.refuse(SharedMessages.UNREADABLE);
            default -> Reply.refuse(NOT_NOW);
        };
    }

    private static Reply listen(Context c, ResearchAction.Listen v) {
        ResearchEntry e = c.entry(v.entry());
        if (e == null
                || e.kind() != EntryKind.LISTEN
                || RenderState.of(v.entry(), e, c.record(), x -> false) != RenderState.OPEN) {
            return Reply.refuse(NOT_NOW);
        }
        Reply refused = refusal(Resolver.listen(c.player(), v.entry(), v.echoedWarp()));
        if (refused != null) {
            return refused;
        }
        playListen(c.player());
        return Reply.OK;
    }

    /** {@code vftv:book.listen}, to the listener alone. */
    private static void playListen(ServerPlayer player) {
        player.connection.send(
                new ClientboundSoundPacket(
                        BuiltInRegistries.SOUND_EVENT.wrapAsHolder(ResearchItems.sound("listen")),
                        SoundSource.PLAYERS,
                        player.getX(),
                        player.getY(),
                        player.getZ(),
                        1.0F,
                        1.0F,
                        player.getRandom().nextLong()));
    }

    /** The silence-beneath gate, then the warp spec's rite; on OK the sound and the book closes. */
    private static Reply listenRite(Context c, ResearchAction.ListenRite v) {
        if (!c.record().isLearned(SILENCE_BENEATH)) {
            return Reply.refuse(WarpService.Answer.CLOSED.key());
        }
        ServerPlayer player = c.player();
        WarpService.Answer answer = WarpService.listen(player, v.echoedWarp());
        if (answer == WarpService.Answer.OK) {
            playListen(player);
            player.closeContainer();
        }
        return answer(player, answer);
    }

    /** A warp answer as a reply: {@code STALE} as a checked commit's, the cooldown with seconds. */
    private static Reply answer(ServerPlayer player, WarpService.Answer answer) {
        return switch (answer) {
            case OK -> Reply.OK;
            case STALE -> Reply.STALE_COMMIT;
            case COOLDOWN ->
                    Reply.refuse(
                            answer.key(), Long.toString(WarpService.listenCooldownSeconds(player)));
            default -> Reply.refuse(answer.key());
        };
    }

    private static Reply bookmark(Context c, ResearchAction.Bookmark v) {
        List<String> marks = c.record().bookmarks();
        if (!v.on()) {
            if (marks.contains(v.entry())) {
                List<String> next = new ArrayList<>(marks);
                next.remove(v.entry());
                ResearchService.update(c.player(), r -> r.withBookmarks(next));
            }
            return Reply.OK;
        }
        ResearchEntry e = c.entry(v.entry());
        if (e == null || RenderState.hidden(v.entry(), e, c.record())) {
            return Reply.refuse(NOT_NOW);
        }
        if (marks.contains(v.entry())) {
            return Reply.OK;
        }
        if (marks.size() >= ResearchLimits.MAX_BOOKMARKS) {
            return Reply.refuse(NOT_NOW);
        }
        List<String> next = new ArrayList<>(marks);
        next.add(v.entry());
        ResearchService.update(c.player(), r -> r.withBookmarks(next));
        return Reply.OK;
    }

    private static Data data(ServerPlayer player) {
        return ResearchService.data(player.level().getServer().registryAccess());
    }
}
