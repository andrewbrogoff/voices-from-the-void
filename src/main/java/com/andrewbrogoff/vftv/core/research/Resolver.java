package com.andrewbrogoff.vftv.core.research;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.research.ResearchKeys;
import com.andrewbrogoff.vftv.api.research.ResearchLearnedPayload;
import com.andrewbrogoff.vftv.api.research.ResearchSource;
import com.andrewbrogoff.vftv.api.research.StageReachedPayload;
import com.andrewbrogoff.vftv.api.warp.CommitResult;
import com.andrewbrogoff.vftv.api.warp.WarpCost;
import com.andrewbrogoff.vftv.api.warp.WarpPreview;
import com.andrewbrogoff.vftv.api.warp.WarpSource;
import com.andrewbrogoff.vftv.api.warp.WarpSourceKind;
import com.andrewbrogoff.vftv.core.aspect.AspectSet;
import com.andrewbrogoff.vftv.core.aspect.KnowledgeService;
import com.andrewbrogoff.vftv.core.aspect.model.Knowledge;
import com.andrewbrogoff.vftv.core.common.OperationGuard;
import com.andrewbrogoff.vftv.core.research.ResearchService.Data;
import com.andrewbrogoff.vftv.core.research.model.EntryKind;
import com.andrewbrogoff.vftv.core.research.model.Evidence;
import com.andrewbrogoff.vftv.core.research.model.RenderState;
import com.andrewbrogoff.vftv.core.research.model.ResearchEntry;
import com.andrewbrogoff.vftv.core.research.model.ResearchRecord;
import com.andrewbrogoff.vftv.core.research.model.Resolution;
import com.andrewbrogoff.vftv.core.research.model.Trigger;
import com.andrewbrogoff.vftv.core.warp.WarpRecord;
import com.andrewbrogoff.vftv.core.warp.WarpService;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.SequencedSet;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import org.jspecify.annotations.Nullable;

/**
 * The research resolution engine (research spec, "Triggers", "Stage gates", "Rewards", "Events"):
 * every change to a player's research runs through here, under the research guard. A change is
 * applied, then {@link Resolution} runs to its fixed point, rewards are paid for first learnings,
 * the record is committed once, and the events are posted in learning order. A change caused by a
 * listener (or by a reward's discoveries) is queued and resolved after the current one finishes.
 * Server thread only.
 *
 * <p>Evidence that arrives while a knowledge write is posting it (a strike's {@code
 * ThingScannedEvent} and {@code AspectDiscoveredEvent}) is latched at once, and its resolution,
 * which can pay rewards through the knowledge service, runs at the end of the server tick.
 */
public final class Resolver {
    /** No warp applied. */
    public static final WarpPreview NO_WARP = new WarpPreview(0, 0, 0, false);

    private static final OperationGuard GUARD = new OperationGuard("research");
    private static final Map<UUID, Deque<Job>> QUEUED = new HashMap<>();
    private static final Map<UUID, ServerPlayer> DEFERRED = new LinkedHashMap<>();

    private Resolver() {}

    /** A direct change: the record after it, recording what it learned itself; null refuses. */
    @FunctionalInterface
    private interface Job {
        @Nullable ResearchRecord apply(ResearchRecord record, Data data, Changes changes);
    }

    /** What a direct change did itself, in order, and what each of its learnings paid. */
    private static final class Changes {
        final List<Resolution.Step> steps = new ArrayList<>();
        final Map<String, Paid> paid = new HashMap<>();
    }

    /** What a learning paid, for its event: the warp applied and the points spent. */
    private record Paid(WarpPreview warp, Map<String, Integer> points) {}

    // ---- Entry points ------------------------------------------------------------------------

    /**
     * Something happened that triggers can match: latches every trigger it fires and resolves.
     *
     * @return false when the record is unreadable, so nothing changed
     */
    public static boolean evidence(ServerPlayer player, Evidence evidence) {
        if (!GUARD.isHeld(player.getUUID()) && KnowledgeService.isBusy(player)) {
            boolean latched =
                    ResearchService.update(player, r -> data(player).triggers().latch(r, evidence));
            if (latched) {
                DEFERRED.put(player.getUUID(), player);
            }
            return latched;
        }
        return run(player, (r, d, c) -> d.triggers().latch(r, evidence));
    }

    /**
     * Learns one entry by a commit (a solved note's Learn, a purchase, a listen) and resolves; its
     * rewards are paid if it has never been rewarded, and its event reports what it paid.
     *
     * @param warp the warp the commit applied, its actual deltas
     * @param points the points the commit spent per aspect
     * @param also any other change made in the same action (the note dropped, the points' plan)
     * @return false when the record is unreadable, so nothing changed
     */
    public static boolean learn(
            ServerPlayer player,
            String entry,
            ResearchSource source,
            WarpPreview warp,
            Map<String, Integer> points,
            UnaryOperator<ResearchRecord> also) {
        return run(
                player,
                (r, d, c) -> {
                    ResearchRecord next = also.apply(r);
                    if (!next.isLearned(entry)) {
                        next = next.learn(entry);
                        c.steps.add(new Resolution.Learned(entry, source));
                        c.paid.put(entry, new Paid(warp, points));
                    }
                    return next;
                });
    }

    /**
     * A {@code listen} entry's commit (research spec, "Warp on entries"): the entry must be OPEN
     * and not learned; its warp is committed through {@link WarpService#commit} against the preview
     * the player saw, then it is learned with source {@code LISTENED}.
     *
     * @param expected the warp preview the player confirmed
     * @return the warp commit's result; {@code INVALID} when the entry can't be listened to now
     */
    public static CommitResult listen(ServerPlayer player, String entry, WarpPreview expected) {
        ResearchRecord record = ResearchService.record(player);
        if (record == null) {
            return new CommitResult(CommitResult.Status.UNREADABLE, null, OptionalLong.empty());
        }
        ResearchEntry e = data(player).entries().get(entry);
        if (e == null
                || e.kind() != EntryKind.LISTEN
                || RenderState.of(entry, e, record, x -> false) != RenderState.OPEN) {
            return new CommitResult(CommitResult.Status.INVALID, null, OptionalLong.empty());
        }
        CommitResult result =
                e.warp().any()
                        ? WarpService.commit(
                                player,
                                new WarpCost(
                                        e.warp().temporary(),
                                        e.warp().lasting(),
                                        e.warp().permanent()),
                                new WarpSource(WarpSourceKind.ENTRY, Identifier.parse(entry)),
                                expected)
                        : new CommitResult(CommitResult.Status.OK, NO_WARP, OptionalLong.empty());
        WarpPreview applied = result.preview();
        if (result.status() == CommitResult.Status.OK && applied != null) {
            learn(player, entry, ResearchSource.LISTENED, applied, Map.of(), r -> r);
        }
        return result;
    }

    /**
     * {@code research grant}: learns the entries and their whole {@code parents} ∪ {@code
     * hidden_parents} closure, raising the Stage and learning the openers it passes. Nothing is
     * paid, applied or added to {@code rewarded}; any open note of a granted entry is dropped.
     *
     * @return how many entries were newly learned, or -1 when the record is unreadable
     */
    public static int grant(ServerPlayer player, Collection<String> ids) {
        int[] count = {0};
        boolean ok =
                run(
                        player,
                        (r, d, c) -> {
                            int stage = r.stage();
                            for (String id : ids) {
                                ResearchEntry e = d.entries().get(id);
                                stage = Math.max(stage, e == null ? 0 : e.stage());
                            }
                            List<String> wanted = new ArrayList<>(ids);
                            for (int s = r.stage() + 1; s <= stage; s++) {
                                String opener = opener(d, s);
                                if (opener != null) {
                                    wanted.add(opener);
                                }
                            }
                            ResearchRecord next = r;
                            for (int s = r.stage(); s < stage; s++) {
                                next = next.withStage(s + 1);
                                c.steps.add(
                                        new Resolution.Staged(s, s + 1, ResearchSource.COMMAND));
                            }
                            for (String id : closure(d, wanted)) {
                                if (!next.isLearned(id)) {
                                    next = next.learn(id);
                                    c.steps.add(new Resolution.Learned(id, ResearchSource.COMMAND));
                                    count[0]++;
                                }
                            }
                            return next;
                        });
        return ok ? count[0] : -1;
    }

    /**
     * {@code research revoke}: removes the learned flag only, with no cascade, refund or warp
     * change, and never lowers the Stage; posts no learned event.
     *
     * @return how many entries were learned before, or -1 when the record is unreadable
     */
    public static int revoke(ServerPlayer player, Collection<String> ids) {
        int[] count = {0};
        boolean ok =
                run(
                        player,
                        (r, d, c) -> {
                            count[0] = (int) ids.stream().filter(r::isLearned).count();
                            return r.unlearn(ids);
                        });
        return ok ? count[0] : -1;
    }

    /**
     * {@code research stage}: sets the Stage; learns nothing and clears no latch. Each step up
     * posts {@code StageReachedEvent}.
     *
     * @return false when the record is unreadable
     */
    public static boolean stage(ServerPlayer player, int stage) {
        return run(
                player,
                (r, d, c) -> {
                    for (int s = r.stage(); s < stage; s++) {
                        c.steps.add(new Resolution.Staged(s, s + 1, ResearchSource.COMMAND));
                    }
                    return r.withStage(stage);
                });
    }

    /**
     * {@code research reset}: every note dropped, then the Stage, learned, revealed, latches,
     * attempts and bookmarks cleared, and the player's {@code vftv:find/*} advancements revoked.
     * {@code rewarded}, warp and the Joining or Silencing choice are kept.
     *
     * @return false when the record is unreadable
     */
    public static boolean reset(ServerPlayer player) {
        if (!run(player, (r, d, c) -> r.reset())) {
            return false;
        }
        MinecraftServer server = player.level().getServer();
        for (AdvancementHolder holder : server.getAdvancements().getAllAdvancements()) {
            Identifier id = holder.id();
            if (id.getNamespace().equals("vftv") && id.getPath().startsWith("find/")) {
                List<String> done = new ArrayList<>();
                player.getAdvancements()
                        .getOrStartProgress(holder)
                        .getCompletedCriteria()
                        .forEach(done::add);
                done.forEach(criterion -> player.getAdvancements().revoke(holder, criterion));
            }
        }
        return true;
    }

    /**
     * The login pass (research spec, "Triggers", "Reconciliation"): bookmarks naming an entry that
     * is gone are dropped, every trigger whose condition holds now is latched, and the record is
     * resolved. Ids of entries that are gone stay in the sets, inert.
     */
    public static void login(ServerPlayer player) {
        Predicate<Trigger> holds = holds(player);
        run(
                player,
                (r, d, c) -> {
                    List<String> kept =
                            r.bookmarks().stream().filter(d.entries()::containsKey).toList();
                    ResearchRecord next =
                            kept.size() == r.bookmarks().size() ? r : r.withBookmarks(kept);
                    return d.triggers().catchUp(next, holds);
                });
    }

    /** The player left: nothing of theirs stays queued. */
    public static void logout(ServerPlayer player) {
        DEFERRED.remove(player.getUUID());
        QUEUED.remove(player.getUUID());
    }

    /** Resolves the evidence latched during knowledge writes; the end of each server tick. */
    public static void drainDeferred() {
        if (DEFERRED.isEmpty()) {
            return;
        }
        List<ServerPlayer> players = List.copyOf(DEFERRED.values());
        DEFERRED.clear();
        for (ServerPlayer player : players) {
            if (!player.isRemoved()) {
                run(player, (r, d, c) -> r);
            }
        }
    }

    /** The server stopped: nothing stays queued. */
    public static void onServerStopped() {
        DEFERRED.clear();
        QUEUED.clear();
    }

    // ---- The transaction ---------------------------------------------------------------------

    private static boolean run(ServerPlayer player, Job job) {
        UUID id = player.getUUID();
        try (OperationGuard.Scope scope = GUARD.tryEnter(id)) {
            if (scope == null) {
                // A listener inside this player's resolution: resolved once it finishes.
                QUEUED.computeIfAbsent(id, k -> new ArrayDeque<>()).add(job);
                return true;
            }
            boolean ok = execute(player, job);
            for (Job next = poll(id); next != null; next = poll(id)) {
                execute(player, next);
            }
            return ok;
        }
    }

    private static @Nullable Job poll(UUID id) {
        Deque<Job> queue = QUEUED.get(id);
        Job next = queue == null ? null : queue.pollFirst();
        if (queue != null && queue.isEmpty()) {
            QUEUED.remove(id);
        }
        return next;
    }

    private static boolean execute(ServerPlayer player, Job job) {
        ResearchRecord before = ResearchService.record(player);
        if (before == null) {
            return false;
        }
        Data data = data(player);
        Changes changes = new Changes();
        ResearchRecord changed = job.apply(before, data, changes);
        if (changed == null) {
            return false;
        }
        Resolution.Outcome outcome = Resolution.resolve(data.entries(), changed);
        List<Resolution.Step> steps = new ArrayList<>(changes.steps);
        steps.addAll(outcome.steps());
        ResearchRecord after = outcome.record();
        for (Resolution.Step step : steps) {
            if (step instanceof Resolution.Learned l && l.source() != ResearchSource.COMMAND) {
                after = reward(player, data, after, l.entry());
            }
        }
        ResearchRecord done = after;
        ResearchService.update(player, r -> done);
        for (Resolution.Step step : steps) {
            switch (step) {
                case Resolution.Learned l -> {
                    Paid paid = changes.paid.getOrDefault(l.entry(), new Paid(NO_WARP, Map.of()));
                    TreeMap<ResourceKey<Aspect>, Integer> points = new TreeMap<>();
                    paid.points().forEach((a, n) -> points.put(AspectSet.key(a), n));
                    ResearchEvents.learned(
                            new ResearchLearnedPayload(
                                    player,
                                    ResourceKey.create(
                                            ResearchKeys.REGISTRY, Identifier.parse(l.entry())),
                                    l.source(),
                                    paid.warp(),
                                    points));
                }
                case Resolution.Staged s ->
                        ResearchEvents.staged(
                                new StageReachedPayload(
                                        player, s.stage(), s.previous(), s.source()));
            }
        }
        return true;
    }

    /** Pays an entry's rewards once per entry, ever (research spec, "Rewards"). */
    private static ResearchRecord reward(
            ServerPlayer player, Data data, ResearchRecord record, String id) {
        ResearchEntry e = data.entries().get(id);
        if (e == null || e.rewards().isEmpty() || record.rewarded().contains(id)) {
            return record;
        }
        Map<ResourceKey<Aspect>, Integer> points = new TreeMap<>();
        e.rewards().forEach((a, n) -> points.put(AspectSet.key(a), n));
        return KnowledgeService.reward(player, points) ? record.reward(id) : record;
    }

    // ---- Helpers -----------------------------------------------------------------------------

    private static Data data(ServerPlayer player) {
        return ResearchService.data(player.level().getServer().registryAccess());
    }

    private static @Nullable String opener(Data data, int stage) {
        return new TreeMap<>(data.entries())
                .entrySet().stream()
                        .filter(e -> e.getValue().opensStage() == stage)
                        .map(Map.Entry::getKey)
                        .findFirst()
                        .orElse(null);
    }

    /**
     * {@code ids} with every entry they need first, each after its parents, ids that exist only.
     */
    private static SequencedSet<String> closure(Data data, List<String> ids) {
        SequencedSet<String> out = new LinkedHashSet<>();
        ids.stream().sorted().forEach(id -> visit(data, id, out, new LinkedHashSet<>()));
        return out;
    }

    private static void visit(
            Data data, String id, SequencedSet<String> out, SequencedSet<String> path) {
        ResearchEntry e = data.entries().get(id);
        if (e == null || out.contains(id) || !path.add(id)) {
            return; // validated acyclic; the path guard only keeps bad data finite
        }
        e.allParents().stream().sorted().forEach(p -> visit(data, p, out, path));
        out.add(id);
    }

    /** What the login catch-up asks of the world, for every type but {@code choir_page}. */
    private static Predicate<Trigger> holds(ServerPlayer player) {
        Knowledge knowledge = KnowledgeService.get(player);
        List<Evidence.Scanned> scanned =
                knowledge == null
                        ? List.of()
                        : knowledge.scans().keySet().stream()
                                .map(key -> new Evidence.Scanned(key, tag -> inTag(key, tag)))
                                .toList();
        WarpRecord warp = WarpService.record(player);
        MinecraftServer server = player.level().getServer();
        return trigger ->
                switch (trigger) {
                    case Trigger.Advancement a -> {
                        AdvancementHolder holder =
                                server.getAdvancements().get(Identifier.parse(a.advancement()));
                        yield holder != null
                                && player.getAdvancements().getOrStartProgress(holder).isDone();
                    }
                    case Trigger.Scanned s -> scanned.stream().anyMatch(e -> e.matches(s));
                    case Trigger.Discovered d ->
                            knowledge != null && knowledge.known().contains(d.aspect());
                    case Trigger.WarpAtLeast w ->
                            warp != null
                                    && new Evidence.Warp(
                                                    warp.total(), warp.lasting() + warp.permanent())
                                            .matches(w);
                    case Trigger.ChoirPage p -> false; // the index reads the record itself
                };
    }

    /**
     * Whether the thing a scan key names is in a tagged key's tag, read as the tags are now: {@code
     * item:minecraft:amethyst_shard} against {@code item:#c:gems}.
     */
    public static boolean inTag(String key, String tag) {
        int colon = key.indexOf(':');
        String kind = colon < 0 ? "" : key.substring(0, colon);
        if (!tag.startsWith(kind + ":#")) {
            return false;
        }
        Identifier thing = Identifier.tryParse(key.substring(colon + 1));
        Identifier tagId = Identifier.tryParse(tag.substring(kind.length() + 2));
        if (thing == null || tagId == null) {
            return false;
        }
        return switch (kind) {
            case "item" ->
                    BuiltInRegistries.ITEM
                            .get(thing)
                            .map(h -> h.is(TagKey.create(Registries.ITEM, tagId)))
                            .orElse(false);
            case "block" ->
                    BuiltInRegistries.BLOCK
                            .get(thing)
                            .map(h -> h.is(TagKey.create(Registries.BLOCK, tagId)))
                            .orElse(false);
            case "entity" ->
                    BuiltInRegistries.ENTITY_TYPE
                            .get(thing)
                            .map(h -> h.is(TagKey.create(Registries.ENTITY_TYPE, tagId)))
                            .orElse(false);
            default -> false;
        };
    }
}
