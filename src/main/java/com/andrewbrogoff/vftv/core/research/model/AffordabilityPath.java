package com.andrewbrogoff.vftv.core.research.model;

import com.andrewbrogoff.vftv.api.research.ResearchSource;
import com.andrewbrogoff.vftv.api.warp.WarpPreview;
import com.andrewbrogoff.vftv.api.warp.WarpRules;
import com.andrewbrogoff.vftv.api.warp.WarpSourceKind;
import com.andrewbrogoff.vftv.core.aspect.model.Knowledge;
import com.andrewbrogoff.vftv.core.common.model.StrictJson;
import com.andrewbrogoff.vftv.core.warp.model.WarpMath;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * An affordability path, {@code content/research/path-stage<N>.json} (research spec, "Affordability
 * paths"), and its replay: a prefix proof that every note and listen of a Stage is reachable from
 * the world's reviewed sources, by the real rules: discovery, eligibility and the full resolution.
 * The replay tracks knowledge, research and a warp ledger; it keeps no points ledger.
 *
 * @param stage the Stage, 1-4
 * @param sources the reviewed sources by id, in file order
 * @param steps the ordered steps
 */
public record AffordabilityPath(int stage, Map<String, Source> sources, List<Step> steps) {
    /** The schema id. */
    public static final String SCHEMA = "vftv:affordability_path/1";

    /** Copies both collections. */
    public AffordabilityPath {
        sources = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(sources));
        steps = List.copyOf(steps);
    }

    /** What a source gives: exactly one of these. */
    public enum Kind {
        /** A scan key struck. */
        SCAN,
        /** A first strike on a fresh node identity of a declared node guarantee. */
        NODE,
        /** An advancement granted. */
        ADVANCEMENT,
        /** A Choir page tipped in, claimed from a site. */
        CHOIR_PAGE
    }

    /**
     * A reviewed source.
     *
     * @param kind what it gives
     * @param id the scan key, node guarantee, advancement or page entry id
     * @param site a Choir page's site; null for the other kinds
     * @param count how many uses the world guarantees, 1-64
     * @param research research that must be learned before a use
     * @param aspects aspects that must be known before a use
     * @param why the one-line review note
     */
    public record Source(
            Kind kind,
            String id,
            @Nullable String site,
            int count,
            List<String> research,
            List<String> aspects,
            String why) {
        /** Copies the lists. */
        public Source {
            research = List.copyOf(research);
            aspects = List.copyOf(aspects);
        }
    }

    /** What a step does. */
    public enum Action {
        /** Spends one use of a source. */
        USE,
        /** Learns an eligible note. */
        LEARN,
        /** Commits an eligible listen. */
        LISTEN
    }

    /**
     * A step.
     *
     * @param action what it does
     * @param id the source id, or the note or listen entry id
     */
    public record Step(Action action, String id) {}

    // ---- Reading -------------------------------------------------------------------------------

    /**
     * Reads a path file: strict JSON in the {@value #SCHEMA} envelope.
     *
     * @throws IllegalArgumentException naming every problem
     */
    public static AffordabilityPath parse(String text) {
        JsonElement root;
        try {
            root = StrictJson.parse(text);
        } catch (StrictJson.MalformedJsonException e) {
            throw new IllegalArgumentException("malformed JSON: " + e.getMessage(), e);
        }
        List<String> problems = new ArrayList<>();
        JsonObject o = object(root, "the file", problems);
        if (o == null) {
            throw new IllegalArgumentException(String.join("; ", problems));
        }
        fields(o, "the file", Set.of("schema", "stage", "sources", "steps"), problems);
        if (!(o.get("schema") instanceof JsonPrimitive s && SCHEMA.equals(s.getAsString()))) {
            problems.add("schema: not " + SCHEMA);
        }
        int stage = integer(o.get("stage"), "stage", 1, 4, problems);
        Map<String, Source> sources = new LinkedHashMap<>();
        JsonObject srcs = object(o.get("sources"), "sources", problems);
        if (srcs != null) {
            srcs.entrySet()
                    .forEach(
                            e -> {
                                Source s = source(e.getValue(), "source " + e.getKey(), problems);
                                if (s != null) {
                                    sources.put(e.getKey(), s);
                                }
                            });
        }
        List<Step> steps = new ArrayList<>();
        if (o.get("steps") instanceof JsonArray array) {
            for (int i = 0; i < array.size(); i++) {
                Step step = step(array.get(i), "step " + i, problems);
                if (step != null) {
                    if (step.action() == Action.USE && srcs != null && !srcs.has(step.id())) {
                        problems.add("step " + i + ": unknown source " + step.id());
                    }
                    steps.add(step);
                }
            }
        } else {
            problems.add("steps: not an array");
        }
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", problems));
        }
        return new AffordabilityPath(stage, sources, steps);
    }

    private static @Nullable Source source(JsonElement e, String at, List<String> problems) {
        JsonObject o = object(e, at, problems);
        if (o == null) {
            return null;
        }
        fields(
                o,
                at,
                Set.of(
                        "scan",
                        "node",
                        "advancement",
                        "choir_page",
                        "site",
                        "count",
                        "requires",
                        "why"),
                problems);
        List<Kind> kinds = new ArrayList<>();
        for (Kind k : Kind.values()) {
            if (o.has(k.name().toLowerCase(java.util.Locale.ROOT))) {
                kinds.add(k);
            }
        }
        if (kinds.size() != 1) {
            problems.add(at + ": needs exactly one of scan, node, advancement and choir_page");
            return null;
        }
        Kind kind = kinds.getFirst();
        String id = string(o.get(kind.name().toLowerCase(java.util.Locale.ROOT)), at, problems);
        String site = null;
        if (kind == Kind.CHOIR_PAGE) {
            site = string(o.get("site"), at + " site", problems);
        } else if (o.has("site")) {
            problems.add(at + ": site only on a choir_page source");
        }
        int count = integer(o.get("count"), at + " count", 1, 64, problems);
        List<String> research = List.of();
        List<String> aspects = List.of();
        if (o.has("requires")) {
            JsonObject req = object(o.get("requires"), at + " requires", problems);
            if (req != null) {
                fields(req, at + " requires", Set.of("research", "aspects"), problems);
                research = strings(req.get("research"), at + " requires.research", problems);
                aspects = strings(req.get("aspects"), at + " requires.aspects", problems);
            }
        }
        String why = string(o.get("why"), at + " why", problems);
        return new Source(kind, id, site, count, research, aspects, why);
    }

    private static @Nullable Step step(JsonElement e, String at, List<String> problems) {
        JsonObject o = object(e, at, problems);
        if (o == null) {
            return null;
        }
        if (o.size() != 1) {
            problems.add(at + ": needs exactly one of use, learn and listen");
            return null;
        }
        String key = o.keySet().iterator().next();
        Action action =
                switch (key) {
                    case "use" -> Action.USE;
                    case "learn" -> Action.LEARN;
                    case "listen" -> Action.LISTEN;
                    default -> null;
                };
        if (action == null) {
            problems.add(at + ": unknown step " + key);
            return null;
        }
        return new Step(action, string(o.get(key), at, problems));
    }

    private static @Nullable JsonObject object(
            @Nullable JsonElement e, String at, List<String> problems) {
        if (e instanceof JsonObject o) {
            return o;
        }
        problems.add(at + ": not an object");
        return null;
    }

    private static void fields(
            JsonObject o, String at, Set<String> allowed, List<String> problems) {
        o.keySet().stream()
                .filter(k -> !allowed.contains(k))
                .sorted()
                .forEach(k -> problems.add(at + ": unknown field " + k));
    }

    private static String string(@Nullable JsonElement e, String at, List<String> problems) {
        if (e instanceof JsonPrimitive p && p.isString() && !p.getAsString().isBlank()) {
            return p.getAsString();
        }
        problems.add(at + ": not a non-empty string");
        return "";
    }

    private static List<String> strings(@Nullable JsonElement e, String at, List<String> problems) {
        if (e == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        if (e instanceof JsonArray a) {
            a.forEach(x -> out.add(string(x, at, problems)));
        } else {
            problems.add(at + ": not an array");
        }
        return out;
    }

    private static int integer(
            @Nullable JsonElement e, String at, int min, int max, List<String> problems) {
        if (e instanceof JsonPrimitive p
                && p.isNumber()
                && p.getAsBigDecimal().stripTrailingZeros().scale() <= 0
                && p.getAsBigDecimal().compareTo(java.math.BigDecimal.valueOf(min)) >= 0
                && p.getAsBigDecimal().compareTo(java.math.BigDecimal.valueOf(max)) <= 0) {
            return p.getAsInt();
        }
        problems.add(at + ": not an integer " + min + "-" + max);
        return min;
    }

    // ---- Replaying -----------------------------------------------------------------------------

    /**
     * What the replay reads from the shipped data.
     *
     * @param entries every research entry by id
     * @param aspects the aspect graph
     * @param rules the aspect set's discovery rules
     * @param scanValues a scan key's values; empty when it names nothing with values
     * @param nodeValues a node guarantee's declared composition at its capacity; empty if unknown
     * @param inTag whether a scan key is in a tagged key's tag ({@code item:#c:gems})
     * @param warp the warp rules, for the preview's clipping and milestones
     * @param preset the preset whose {@code anchors.max_used} the begin precondition reads
     */
    public record World(
            Map<String, ResearchEntry> entries,
            AspectGraph aspects,
            Knowledge.Rules rules,
            Function<String, Map<String, Integer>> scanValues,
            Function<String, Map<String, Integer>> nodeValues,
            BiPredicate<String, String> inTag,
            WarpRules warp,
            ResearchTuning.Preset preset) {}

    /**
     * The replay's state, carried from one Stage's path to the next in the same mode.
     *
     * @param research the research record
     * @param knowledge the knowledge
     * @param temporary temporary warp
     * @param lasting lasting warp
     * @param permanent permanent warp
     * @param milestones the warp milestones paid
     */
    public record State(
            ResearchRecord research,
            Knowledge knowledge,
            int temporary,
            int lasting,
            int permanent,
            Set<String> milestones) {
        /** An empty ledger: where Stage 1 starts. */
        public static final State EMPTY =
                new State(ResearchRecord.EMPTY, Knowledge.EMPTY, 0, 0, 0, Set.of());

        /** Copies the milestones. */
        public State {
            milestones = Set.copyOf(milestones);
        }
    }

    /**
     * Replays this path from {@code start}: an empty ledger for Stage 1, else the previous Stage's
     * end in the same mode.
     *
     * @return the state at the end, for the next Stage's path
     * @throws IllegalArgumentException naming the first step the rules reject, or the end check
     */
    public State replay(World world, State start) {
        if (start.research().stage() < stage - 1) {
            throw new IllegalArgumentException(
                    "path-stage" + stage + " starts at Stage " + start.research().stage());
        }
        Replay r = new Replay(world, start);
        Map<String, Integer> used = new HashMap<>();
        Set<String> committed = new HashSet<>();
        for (int i = 0; i < steps.size(); i++) {
            Step step = steps.get(i);
            String at = "path-stage" + stage + " step " + i + " (" + step.id() + ")";
            switch (step.action()) {
                case USE -> {
                    Source s = sources.get(step.id());
                    if (s == null) {
                        throw new IllegalArgumentException(at + ": unknown source");
                    }
                    int n = used.merge(step.id(), 1, Integer::sum);
                    if (n > s.count()) {
                        throw new IllegalArgumentException(at + ": used beyond its count");
                    }
                    if (!s.research().stream().allMatch(r.research::isLearned)
                            || !r.knowledge.known().containsAll(s.aspects())) {
                        throw new IllegalArgumentException(at + ": used before its requires hold");
                    }
                    r.use(step.id(), s, n, at);
                }
                case LEARN -> {
                    r.learn(step.id(), at);
                    committed.add(step.id());
                }
                case LISTEN -> {
                    r.listen(step.id(), at);
                    committed.add(step.id());
                }
            }
        }
        finish(world, r, committed);
        return r.state();
    }

    /** The end checks: every commit made, the next opener latched, every warp gate latched. */
    private void finish(World world, Replay r, Set<String> committed) {
        List<String> problems = new ArrayList<>();
        new TreeMap<>(world.entries())
                .forEach(
                        (id, e) -> {
                            if (e.stage() != stage) {
                                return;
                            }
                            if (e.kind().committed()
                                    && e.warp().permanent() == 0
                                    && !committed.contains(id)) {
                                problems.add(id + " is never committed");
                            }
                            boolean gated =
                                    java.util.stream.Stream.concat(
                                                    e.triggers().stream(), e.reveal().stream())
                                            .anyMatch(t -> t instanceof Trigger.WarpAtLeast);
                            if (gated
                                    && !r.research.latched().contains(id)
                                    && !r.research.revealLatched().contains(id)) {
                                problems.add(id + "'s warp gate never latches");
                            }
                        });
        if (stage <= 3) {
            String opener =
                    world.entries().entrySet().stream()
                            .filter(e -> e.getValue().opensStage() == stage + 1)
                            .map(Map.Entry::getKey)
                            .findFirst()
                            .orElse(null);
            if (opener == null || !r.research.isLatchedOrLearned(opener)) {
                problems.add("Stage " + (stage + 1) + "'s opener " + opener + " is not latched");
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(
                    "path-stage" + stage + ": " + String.join("; ", problems));
        }
    }

    /** One replay's mutable state, running the real rules. */
    private static final class Replay {
        final World world;
        final TriggerIndex triggers;
        ResearchRecord research;
        Knowledge knowledge;
        int t;
        int l;
        int p;
        Set<String> milestones;

        Replay(World world, State start) {
            this.world = world;
            triggers = new TriggerIndex(world.entries());
            research = start.research();
            knowledge = start.knowledge();
            t = start.temporary();
            l = start.lasting();
            p = start.permanent();
            milestones = new TreeSet<>(start.milestones());
        }

        State state() {
            return new State(research, knowledge, t, l, p, milestones);
        }

        void use(String sourceId, Source s, int n, String at) {
            switch (s.kind()) {
                case SCAN -> strike(s.id(), world.scanValues().apply(s.id()), at);
                case NODE ->
                        strike("node:" + sourceId + "#" + n, world.nodeValues().apply(s.id()), at);
                case ADVANCEMENT -> evidence(new Evidence.Advancement(s.id()));
                case CHOIR_PAGE -> {
                    ResearchEntry page = world.entries().get(s.id());
                    if (page == null
                            || !EntryRules.CHOIR_PAGES.equals(page.category())
                            || page.stage() > research.stage()
                            || research.isLatchedOrLearned(s.id())) {
                        throw new IllegalArgumentException(at + ": the page can't be tipped in");
                    }
                    evidence(new Evidence.ChoirPage(s.id()));
                }
            }
        }

        void strike(String key, Map<String, Integer> values, String at) {
            if (values.isEmpty()) {
                throw new IllegalArgumentException(at + ": " + key + " has no aspect values");
            }
            Knowledge.Strike strike = knowledge.strike(key, values, world.rules());
            knowledge = strike.next();
            List<Evidence> events = new ArrayList<>();
            strike.discovered().forEach(d -> events.add(new Evidence.Discovered(d.aspect())));
            events.add(new Evidence.Scanned(key, tag -> world.inTag().test(key, tag)));
            events.forEach(this::evidence);
        }

        void learn(String id, String at) {
            ResearchEntry e = world.entries().get(id);
            if (e == null || e.kind() != EntryKind.NOTE) {
                throw new IllegalArgumentException(at + ": a learn step names no note");
            }
            AspectGraph.LinkView view = world.aspects().view(research::isLearned);
            if (research.isLearned(id)
                    || RenderState.hidden(id, e, research)
                    || !e.allParents().stream().allMatch(research::isLearned)
                    || !Notes.canBegin(Notes.used(e, world.preset()), knowledge.known(), view)) {
                throw new IllegalArgumentException(at + ": the note is not eligible");
            }
            commit(id, e, ResearchSource.SOLVED, at);
        }

        void listen(String id, String at) {
            ResearchEntry e = world.entries().get(id);
            if (e == null
                    || e.kind() != EntryKind.LISTEN
                    || RenderState.of(id, e, research, x -> false) != RenderState.OPEN) {
                throw new IllegalArgumentException(at + ": the listen is not eligible");
            }
            commit(id, e, ResearchSource.LISTENED, at);
        }

        /** Applies the entry's warp to the ledger, learns it, then the warp evidence fires. */
        void commit(String id, ResearchEntry e, ResearchSource source, String at) {
            if (e.warp().permanent() > 0) {
                throw new IllegalArgumentException(at + ": a path never commits a milestone");
            }
            boolean warped = e.warp().any();
            if (warped) {
                WarpPreview applied =
                        WarpMath.preview(
                                t,
                                l,
                                p,
                                milestones,
                                e.warp().temporary(),
                                e.warp().lasting(),
                                e.warp().permanent(),
                                WarpSourceKind.ENTRY,
                                id,
                                world.warp());
                if (applied == null) {
                    throw new IllegalArgumentException(at + ": its warp can't apply");
                }
                t += applied.dT();
                l += applied.dL();
                p += applied.dP();
                if (WarpMath.isMilestone(
                        e.warp().permanent(), WarpSourceKind.ENTRY, id, world.warp())) {
                    milestones.add(id);
                }
            }
            resolve(research.learn(id), List.of(new Resolution.Learned(id, source)));
            if (warped) {
                evidence(new Evidence.Warp(t + l + p, l + p));
            }
        }

        void evidence(Evidence e) {
            resolve(triggers.latch(research, e), List.of());
        }

        /**
         * Resolves to the fixed point, paying each first learning's rewards (which teach), whose
         * discoveries are evidence resolved in turn.
         */
        void resolve(ResearchRecord changed, List<Resolution.Step> direct) {
            Deque<Evidence> queue = new ArrayDeque<>();
            Resolution.Outcome outcome = Resolution.resolve(world.entries(), changed);
            research = outcome.record();
            List<Resolution.Step> steps = new ArrayList<>(direct);
            steps.addAll(outcome.steps());
            for (Resolution.Step step : steps) {
                if (step instanceof Resolution.Learned learned) {
                    ResearchEntry e = world.entries().get(learned.entry());
                    if (e != null
                            && !e.rewards().isEmpty()
                            && !research.rewarded().contains(learned.entry())) {
                        Knowledge.Rewarded paid = knowledge.reward(e.rewards(), world.rules());
                        knowledge = paid.next();
                        research = research.reward(learned.entry());
                        paid.discovered()
                                .forEach(d -> queue.add(new Evidence.Discovered(d.aspect())));
                    }
                }
            }
            while (!queue.isEmpty()) {
                evidence(queue.removeFirst());
            }
        }
    }
}
