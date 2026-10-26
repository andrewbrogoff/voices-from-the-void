package com.andrewbrogoff.vftv.core.research;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.api.aspect.HiddenPrimal;
import com.andrewbrogoff.vftv.api.research.Research;
import com.andrewbrogoff.vftv.api.research.ResearchKeys;
import com.andrewbrogoff.vftv.api.research.ResearchView;
import com.andrewbrogoff.vftv.api.warp.WarpRules;
import com.andrewbrogoff.vftv.core.common.DataSlot;
import com.andrewbrogoff.vftv.core.common.RawRecordCodec.Retained;
import com.andrewbrogoff.vftv.core.research.model.AspectGraph;
import com.andrewbrogoff.vftv.core.research.model.CategoryEntry;
import com.andrewbrogoff.vftv.core.research.model.EntryKind;
import com.andrewbrogoff.vftv.core.research.model.EntryRules;
import com.andrewbrogoff.vftv.core.research.model.Notes;
import com.andrewbrogoff.vftv.core.research.model.ResearchDelta;
import com.andrewbrogoff.vftv.core.research.model.ResearchEntry;
import com.andrewbrogoff.vftv.core.research.model.ResearchRecord;
import com.andrewbrogoff.vftv.core.research.model.ResearchTuning;
import com.andrewbrogoff.vftv.core.research.model.Trigger;
import com.andrewbrogoff.vftv.core.research.model.TriggerIndex;
import com.andrewbrogoff.vftv.core.warp.WarpRulesService;
import com.andrewbrogoff.vftv.core.warp.WarpService;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The research feature's service (research spec, "Events" and "Sync and state"): the plain queries
 * {@link #isLearned}, {@link #isLatchedOrLearned} and {@link #stage}, which the {@link
 * ResearchView} answers live; the one mutation path for the player's record; the server switch; and
 * the load validation of both registries. Server thread only.
 */
public final class ResearchService {
    /** The built-in entries that must exist ({@code ResearchKeys}). */
    public static final Set<String> REQUIRED_ENTRIES =
            ids(
                    ResearchKeys.RESONANCE,
                    ResearchKeys.DISTILLATION,
                    ResearchKeys.RUINS,
                    ResearchKeys.CHOIRS_ROAD,
                    ResearchKeys.COMMONPLACE_BOOK,
                    ResearchKeys.SILENCE_BENEATH,
                    ResearchKeys.BENEATH_THE_NOTES);

    /** The built-in categories that must exist ({@code ResearchKeys}). */
    public static final Set<String> REQUIRED_CATEGORIES =
            ids(
                    ResearchKeys.CATEGORY_RESONANCE,
                    ResearchKeys.CATEGORY_DISTILLATION,
                    ResearchKeys.CATEGORY_RUINS,
                    ResearchKeys.CATEGORY_CHOIRS_ROAD,
                    ResearchKeys.CATEGORY_CHOIR_PAGES);

    private static final Logger LOGGER = LogUtils.getLogger();

    private static @Nullable DataSlot<Player, ResearchAttachment> slot;
    private static volatile ResearchConfig config = ResearchConfig.DEFAULT;
    private static volatile @Nullable Data data;
    private static volatile ResearchTuning tuning = ResearchTuning.DEFAULTS;
    private static volatile @Nullable Prices prices;

    /** The tuning file under {@code data/vftv/}. */
    public static final String TUNING_PATH = "tuning/research.json";

    private ResearchService() {}

    static void init(DataSlot<Player, ResearchAttachment> research) {
        slot = research;
    }

    private static DataSlot<Player, ResearchAttachment> slot() {
        DataSlot<Player, ResearchAttachment> s = slot;
        if (s == null) {
            throw new IllegalStateException("ResearchFeature.init has not run");
        }
        return s;
    }

    /** The player's attachment value: their record, raw when unreadable. */
    public static ResearchAttachment get(Player player) {
        return slot().get(player);
    }

    /** The player's readable record, or null when it is kept raw. */
    public static @Nullable ResearchRecord record(Player player) {
        return get(player).readable();
    }

    /** Whether the entry is in the player's stored learned set; false while unreadable. */
    public static boolean isLearned(Player player, ResourceKey<Research> entry) {
        ResearchRecord r = record(player);
        return r != null && r.isLearned(entry.identifier().toString());
    }

    /** Whether the entry is latched or learned; false while unreadable. */
    public static boolean isLatchedOrLearned(Player player, ResourceKey<Research> entry) {
        ResearchRecord r = record(player);
        return r != null && r.isLatchedOrLearned(entry.identifier().toString());
    }

    /** The player's Stage, 0-4; 0 while unreadable. */
    public static int stage(Player player) {
        ResearchRecord r = record(player);
        return r == null ? 0 : r.stage();
    }

    /**
     * Changes the player's record and sends its owner the delta. An unreadable record is never
     * changed (conventions, "Raw-record retention").
     *
     * @return false if the record is unreadable, so nothing changed
     */
    public static boolean update(Player player, UnaryOperator<ResearchRecord> change) {
        ResearchRecord before = record(player);
        if (before == null) {
            return false;
        }
        ResearchRecord after = change.apply(before);
        if (!after.equals(before)) {
            slot().set(
                            player,
                            new ResearchAttachment(
                                    new Retained.Read<>(after),
                                    ResearchDelta.between(before, after)));
        }
        return true;
    }

    /** The live read-only view of a server player's research. */
    public static ResearchView view(ServerPlayer player) {
        return new ResearchView() {
            @Override
            public boolean isLearned(ResourceKey<Research> research) {
                return ResearchService.isLearned(player, research);
            }

            @Override
            public boolean isLatchedOrLearned(ResourceKey<Research> research) {
                return ResearchService.isLatchedOrLearned(player, research);
            }

            @Override
            public int stage() {
                return ResearchService.stage(player);
            }
        };
    }

    /** The server switch in force. */
    public static ResearchConfig config() {
        return config;
    }

    /** Swaps the server switch, as the config publisher does. */
    public static void installConfig(ResearchConfig next) {
        config = next;
    }

    /**
     * Re-sends quotes, with the new mode, to every open Commonplace Book after a changed {@code
     * research.mode}, even if no price changed.
     */
    public static void resendQuotes(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.containerMenu instanceof CommonplaceMenu menu) {
                menu.quote(player, true);
            }
        }
    }

    // --- Tuning (research spec, "Tuning"). ---

    /** The tuning in force. */
    public static ResearchTuning tuning() {
        return tuning;
    }

    /** Reads a tuning file's text, or why it is invalid. */
    public static DataResult<ResearchTuning> parseTuning(String json) {
        try {
            return DataResult.success(ResearchTuning.parse(json));
        } catch (IllegalArgumentException e) {
            return DataResult.error(e::getMessage);
        }
    }

    /**
     * Applies a tuning read: an invalid one refuses the world at open and keeps the previous record
     * on {@code /reload}. Prices rebuild on next use; open books re-quote at their change check,
     * and open notes keep their snapshot.
     *
     * @throws IllegalStateException for an invalid read while the world opens
     */
    public static synchronized void applyTuning(
            DataResult<ResearchTuning> read, boolean reloading) {
        read.ifSuccess(t -> tuning = t)
                .ifError(
                        error -> {
                            String message =
                                    "Invalid data/vftv/" + TUNING_PATH + ": " + error.message();
                            if (!reloading) {
                                throw new IllegalStateException(message);
                            }
                            LOGGER.error("{}; keeping the previous research tuning", message);
                        });
    }

    /**
     * The preset a mode's new notes use: its own, or the standard preset's numbers for skip
     * eligibility, which has no preset of its own.
     */
    public static ResearchTuning.Preset preset(ResearchConfig.Mode mode) {
        ResearchTuning t = tuning;
        return switch (mode) {
            case GENTLE -> t.gentle();
            case HARD -> t.hard();
            case STANDARD, SKIP -> t.standard();
        };
    }

    /**
     * An entry's skip price under the tuning in force: its authored {@code skip_cost}, else the
     * scaled reference vector. Computed once per tuning and world, not per player.
     */
    // Identity on purpose: a new world's data or a reload's tuning is a new instance.
    @SuppressWarnings("ReferenceEquality")
    public static SortedMap<String, Integer> skipPrice(Data data, String entry) {
        ResearchTuning t = tuning;
        Prices p = prices;
        if (p == null || p.tuning() != t || p.data() != data) {
            Map<String, SortedMap<String, Integer>> out = new TreeMap<>();
            data.entries()
                    .forEach(
                            (id, e) -> {
                                if (e.kind() == EntryKind.NOTE) {
                                    out.put(id, Notes.skipPrice(e, data.aspects(), t));
                                }
                            });
            p = new Prices(t, data, Map.copyOf(out));
            prices = p;
        }
        return p.prices().getOrDefault(entry, new TreeMap<>());
    }

    private record Prices(
            ResearchTuning tuning, Data data, Map<String, SortedMap<String, Integer>> prices) {}

    // --- Load validation (research spec, "Registry format"). ---

    /** Every entry by id, from the world's registries. */
    public static Map<String, ResearchEntry> entries(HolderLookup.Provider access) {
        Map<String, ResearchEntry> out = new TreeMap<>();
        access.lookupOrThrow(ResearchKeys.REGISTRY)
                .listElements()
                .forEach(
                        h -> {
                            if (h.value() instanceof ResearchEntry e) {
                                out.put(h.key().identifier().toString(), e);
                            }
                        });
        return out;
    }

    /** Every category by id, from the world's registries. */
    public static Map<String, CategoryEntry> categories(HolderLookup.Provider access) {
        Map<String, CategoryEntry> out = new TreeMap<>();
        access.lookupOrThrow(ResearchKeys.CATEGORY_REGISTRY)
                .listElements()
                .forEach(
                        h -> {
                            if (h.value() instanceof CategoryEntry c) {
                                out.put(h.key().identifier().toString(), c);
                            }
                        });
        return out;
    }

    /**
     * The aspect set as the notebook teaches it, with each hidden primal's {@code revealed_by},
     * from the world's {@code vftv:aspect} registry.
     */
    public static AspectGraph aspects(HolderLookup.Provider access) {
        Map<String, List<String>> taught = new TreeMap<>();
        Map<String, String> revealedBy = new TreeMap<>();
        access.lookupOrThrow(AspectKeys.REGISTRY)
                .listElements()
                .forEach(
                        h -> {
                            Aspect aspect = h.value();
                            HiddenPrimal hidden = aspect.hiddenPrimal();
                            List<ResourceKey<Aspect>> parents =
                                    hidden != null ? hidden.taughtParents() : aspect.parents();
                            String id = h.key().identifier().toString();
                            taught.put(
                                    id,
                                    parents.stream().map(k -> k.identifier().toString()).toList());
                            if (hidden != null) {
                                revealedBy.put(id, hidden.revealedBy().toString());
                            }
                        });
        return new AspectGraph(taught, revealedBy);
    }

    /**
     * The world's research data, fixed while it is open: every entry, the aspect graph and the
     * trigger index.
     *
     * @param entries every entry by id
     * @param aspects the aspect graph, with its reveals
     * @param triggers every trigger and reveal, indexed
     */
    public record Data(
            Map<String, ResearchEntry> entries, AspectGraph aspects, TriggerIndex triggers) {
        /** Reads it from the world's registries. */
        public static Data of(HolderLookup.Provider access) {
            Map<String, ResearchEntry> entries = Map.copyOf(ResearchService.entries(access));
            return new Data(entries, ResearchService.aspects(access), new TriggerIndex(entries));
        }

        /** A player's view of the links (taught, or true after their reveal). */
        public AspectGraph.LinkView view(ResearchRecord record) {
            return aspects.view(record::isLearned);
        }
    }

    /**
     * The open world's research data: indexed once when the world opens (the registries are fixed
     * until it reopens), or on first use.
     */
    public static Data data(HolderLookup.Provider access) {
        Data d = data;
        if (d == null) {
            d = Data.of(access);
            data = d;
        }
        return d;
    }

    /** The server stopped: the next world indexes its own data and reads its own tuning. */
    public static void onServerStopped() {
        data = null;
        prices = null;
        tuning = ResearchTuning.DEFAULTS;
    }

    /**
     * The milestone rules against {@code rules}, as the warp tuning's reload check: empty before a
     * world has indexed its data, since the world-open validation checks them itself.
     */
    public static List<String> milestoneProblems(WarpRules rules) {
        Data d = data;
        return d == null ? List.of() : milestoneProblems(d.entries(), rules);
    }

    private static List<String> milestoneProblems(
            Map<String, ResearchEntry> entries, WarpRules rules) {
        return EntryRules.milestoneProblems(
                entries, rules.permanentMilestones(), WarpService.JOINING.toString());
    }

    /** Runs the load validation over the world's registries. */
    public static EntryRules.Report validate(HolderLookup.Provider access) {
        Map<String, ResearchEntry> entries = entries(access);
        EntryRules.Report report =
                EntryRules.validate(
                        entries,
                        categories(access),
                        aspects(access),
                        REQUIRED_ENTRIES,
                        REQUIRED_CATEGORIES);
        List<String> errors = new ArrayList<>(report.errors());
        errors.addAll(scanKeyProblems(entries));
        errors.addAll(milestoneProblems(entries, WarpRulesService.current()));
        return new EntryRules.Report(errors, report.warnings());
    }

    /**
     * The exact {@code vftv:scanned} keys naming an item, block or entity type that isn't
     * registered: an id in a trigger that doesn't exist refuses the world, like any other.
     */
    public static List<String> scanKeyProblems(Map<String, ResearchEntry> entries) {
        List<String> problems = new ArrayList<>();
        entries.forEach(
                (id, e) ->
                        Stream.concat(e.triggers().stream(), e.reveal().stream())
                                .<String>mapMulti(
                                        (t, out) -> {
                                            if (t instanceof Trigger.Scanned s && s.key() != null) {
                                                out.accept(s.key());
                                            }
                                        })
                                .filter(key -> !scanKeyExists(key))
                                .forEach(
                                        key ->
                                                problems.add(
                                                        "research entry "
                                                                + id
                                                                + ": its trigger's key "
                                                                + key
                                                                + " names nothing registered")));
        return problems;
    }

    private static boolean scanKeyExists(String key) {
        int colon = key.indexOf(':');
        Identifier thing = Identifier.parse(key.substring(colon + 1));
        return switch (key.substring(0, colon)) {
            // Air (item or block) is never struck, and a dropped item is keyed as its item.
            case "item" ->
                    BuiltInRegistries.ITEM.containsKey(thing)
                            && !thing.equals(BuiltInRegistries.ITEM.getDefaultKey());
            case "block" ->
                    BuiltInRegistries.BLOCK
                            .getOptional(thing)
                            .filter(b -> !b.defaultBlockState().isAir())
                            .isPresent();
            case "entity" ->
                    BuiltInRegistries.ENTITY_TYPE.containsKey(thing)
                            && !thing.equals(EntityType.getKey(EntityTypes.ITEM));
            default -> true; // a node key: nodes live in the world, not a registry
        };
    }

    /**
     * The world-open check: logs every warning, and refuses the world with every error, naming each
     * entry. An advancement trigger naming no loaded advancement is a warning, since advancements
     * follow {@code /reload}.
     *
     * @param advancementLoaded whether an advancement id is loaded
     * @throws IllegalStateException when any rule refuses
     */
    public static void validateAtOpen(
            HolderLookup.Provider access, Predicate<Identifier> advancementLoaded) {
        EntryRules.Report report = validate(access);
        report.warnings().forEach(w -> LOGGER.warn("Research data: {}", w));
        entries(access)
                .forEach(
                        (id, e) ->
                                Stream.concat(e.triggers().stream(), e.reveal().stream())
                                        .<Trigger.Advancement>mapMulti(
                                                (t, out) -> {
                                                    if (t instanceof Trigger.Advancement a) {
                                                        out.accept(a);
                                                    }
                                                })
                                        .filter(
                                                a ->
                                                        !advancementLoaded.test(
                                                                Identifier.parse(a.advancement())))
                                        .forEach(
                                                a ->
                                                        LOGGER.warn(
                                                                "Research data: research entry {}:"
                                                                        + " its trigger's advancement"
                                                                        + " {} is not loaded",
                                                                id,
                                                                a.advancement())));
        if (!report.errors().isEmpty()) {
            throw new IllegalStateException(
                    "Invalid vftv research data:\n" + String.join("\n", report.errors()));
        }
        data = Data.of(access);
    }

    private static Set<String> ids(ResourceKey<?>... keys) {
        return Stream.of(keys)
                .map(k -> k.identifier().toString())
                .collect(Collectors.toUnmodifiableSet());
    }
}
