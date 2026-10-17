package com.andrewbrogoff.vftv.core.research;

import com.andrewbrogoff.vftv.api.research.Research;
import com.andrewbrogoff.vftv.api.research.ResearchCategory;
import com.andrewbrogoff.vftv.core.research.model.CategoryEntry;
import com.andrewbrogoff.vftv.core.research.model.EntryKind;
import com.andrewbrogoff.vftv.core.research.model.Page;
import com.andrewbrogoff.vftv.core.research.model.ResearchEntry;
import com.andrewbrogoff.vftv.core.research.model.Trigger;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.resources.Identifier;

/**
 * The codecs of the two research registries (research spec, "Registry format"). Each field's range
 * and every typed trigger and page are checked here, strictly: an unknown field or type is an
 * error. Cross-field and cross-entry rules are {@code EntryRules}'.
 */
public final class ResearchCodecs {
    /** A namespaced id, kept as its {@code ns:path} string. */
    public static final Codec<String> ID =
            Identifier.CODEC.xmap(Identifier::toString, Identifier::parse);

    private static final String ID_TEXT = "[a-z0-9_.-]+:[a-z0-9_./-]+";

    /** A scan key: {@code item:}, {@code block:} or {@code entity:} and an id, or {@code node:}. */
    static final Codec<String> SCAN_KEY =
            pattern("(item|block|entity):" + ID_TEXT + "|node:[0-9]+", "a scan key");

    /** A scan key of a thing (never a node). */
    static final Codec<String> THING_KEY = pattern("(item|block|entity):" + ID_TEXT, "a scan key");

    /** A tagged scan key, e.g. {@code item:#c:gems}. */
    static final Codec<String> SCAN_TAG = pattern("(item|block|entity):#" + ID_TEXT, "a scan tag");

    private static final Codec<ResearchEntry.Icon> ICON =
            strict(
                            RecordCodecBuilder.<Pair<Optional<String>, Optional<String>>>mapCodec(
                                    i ->
                                            i.group(
                                                            ID.optionalFieldOf("item")
                                                                    .forGetter(Pair::getFirst),
                                                            ID.optionalFieldOf("sprite")
                                                                    .forGetter(Pair::getSecond))
                                                    .apply(i, Pair::of)))
                    .codec()
                    .comapFlatMap(
                            p ->
                                    p.getFirst().isPresent() == p.getSecond().isPresent()
                                            ? DataResult.error(
                                                    () ->
                                                            "an icon needs exactly one of item or sprite")
                                            : DataResult.success(
                                                    new ResearchEntry.Icon(
                                                            p.getSecond().isPresent(),
                                                            p.getFirst()
                                                                    .or(p::getSecond)
                                                                    .orElseThrow())),
                            icon ->
                                    icon.sprite()
                                            ? Pair.of(Optional.empty(), Optional.of(icon.id()))
                                            : Pair.of(Optional.of(icon.id()), Optional.empty()));

    private static final Map<String, MapCodec<? extends Trigger>> TRIGGERS =
            Map.<String, MapCodec<? extends Trigger>>of(
                    "vftv:advancement",
                    strict(
                            ID.fieldOf("advancement")
                                    .xmap(
                                            Trigger.Advancement::new,
                                            Trigger.Advancement::advancement),
                            "type"),
                    "vftv:scanned",
                    strict(
                            RecordCodecBuilder
                                    .<Pair<Pair<Optional<String>, Optional<String>>, Boolean>>
                                            mapCodec(
                                                    i ->
                                                            i.group(
                                                                            SCAN_KEY.optionalFieldOf(
                                                                                            "key")
                                                                                    .forGetter(
                                                                                            p ->
                                                                                                    p.getFirst()
                                                                                                            .getFirst()),
                                                                            SCAN_TAG.optionalFieldOf(
                                                                                            "tag")
                                                                                    .forGetter(
                                                                                            p ->
                                                                                                    p.getFirst()
                                                                                                            .getSecond()),
                                                                            Codec.BOOL
                                                                                    .optionalFieldOf(
                                                                                            "node",
                                                                                            false)
                                                                                    .forGetter(
                                                                                            Pair
                                                                                                    ::getSecond))
                                                                    .apply(
                                                                            i,
                                                                            (k, t, n) ->
                                                                                    Pair.of(
                                                                                            Pair.of(
                                                                                                    k,
                                                                                                    t),
                                                                                            n)))
                                    .flatXmap(
                                            ResearchCodecs::scanned, ResearchCodecs::scannedParts),
                            "type"),
                    "vftv:discovered",
                    strict(
                            ID.fieldOf("aspect")
                                    .xmap(Trigger.Discovered::new, Trigger.Discovered::aspect),
                            "type"),
                    "vftv:choir_page",
                    strict(
                            ID.fieldOf("page")
                                    .xmap(Trigger.ChoirPage::new, Trigger.ChoirPage::page),
                            "type"),
                    "vftv:warp_at_least",
                    strict(
                            RecordCodecBuilder.<Trigger.WarpAtLeast>mapCodec(
                                    i ->
                                            i.group(
                                                            enumCodec(
                                                                            Trigger.WarpKind
                                                                                    .values(),
                                                                            Trigger.WarpKind::id)
                                                                    .fieldOf("kind")
                                                                    .forGetter(
                                                                            Trigger.WarpAtLeast
                                                                                    ::kind),
                                                            Codec.intRange(1, 1000)
                                                                    .fieldOf("value")
                                                                    .forGetter(
                                                                            Trigger.WarpAtLeast
                                                                                    ::value))
                                                    .apply(i, Trigger.WarpAtLeast::new)),
                            "type"));

    /** A typed trigger, dispatched on {@code type}. */
    public static final Codec<Trigger> TRIGGER =
            Codec.STRING.partialDispatch(
                    "type",
                    t -> DataResult.success(t.type()),
                    type -> known(TRIGGERS, type, "trigger"));

    private static final Map<String, MapCodec<? extends Page>> PAGES =
            Map.<String, MapCodec<? extends Page>>of(
                    "vftv:text",
                    strict(MapCodec.unit(new Page.Text()), "type"),
                    "vftv:recipe",
                    strict(
                            RecordCodecBuilder.<Page.Recipe>mapCodec(
                                    i ->
                                            i.group(
                                                            ID.fieldOf("recipe")
                                                                    .forGetter(Page.Recipe::recipe),
                                                            Codec.BOOL
                                                                    .optionalFieldOf(
                                                                            "preview", false)
                                                                    .forGetter(
                                                                            Page.Recipe::preview))
                                                    .apply(i, Page.Recipe::new)),
                            "type"),
                    "vftv:aspects",
                    strict(
                            THING_KEY.fieldOf("of").xmap(Page.Aspects::new, Page.Aspects::of),
                            "type"),
                    "vftv:structure",
                    strict(
                            ID.fieldOf("multiblock")
                                    .xmap(Page.Structure::new, Page.Structure::multiblock),
                            "type"),
                    "vftv:image",
                    strict(ID.fieldOf("sprite").xmap(Page.Image::new, Page.Image::sprite), "type"),
                    "vftv:action",
                    strict(MapCodec.unit(new Page.Action()), "type"),
                    "vftv:choir",
                    strict(MapCodec.unit(new Page.Choir()), "type"));

    /** A typed page descriptor, dispatched on {@code type}. */
    public static final Codec<Page> PAGE =
            Codec.STRING.partialDispatch(
                    "type", p -> DataResult.success(p.type()), type -> known(PAGES, type, "page"));

    private static final Codec<ResearchEntry.Warp> WARP =
            strict(
                            RecordCodecBuilder.<ResearchEntry.Warp>mapCodec(
                                    i ->
                                            i.group(
                                                            warpKind(
                                                                    "temporary",
                                                                    ResearchEntry.Warp::temporary),
                                                            warpKind(
                                                                    "lasting",
                                                                    ResearchEntry.Warp::lasting),
                                                            warpKind(
                                                                    "permanent",
                                                                    ResearchEntry.Warp::permanent))
                                                    .apply(i, ResearchEntry.Warp::new)))
                    .codec();

    /** {@code rewards}: its {@code points} object, aspect to 1-256, paid on first learning. */
    private static final Codec<Map<String, Integer>> REWARDS =
            strict(Codec.unboundedMap(ID, Codec.intRange(1, 256)).fieldOf("points")).codec();

    private static final Codec<List<Integer>> POSITION = sized(Codec.intRange(-16, 16), 2, 2);

    /** The fields every entry has, the first half of {@link #ENTRY}. */
    private record Head(
            int stage,
            String category,
            List<Integer> position,
            ResearchEntry.Icon icon,
            EntryKind kind,
            List<String> parents,
            List<String> hiddenParents,
            List<Page> pages) {}

    /** The kind-specific fields, the second half of {@link #ENTRY}. */
    private record Tail(
            List<String> anchors,
            Map<String, Integer> skipCost,
            List<Trigger> triggers,
            boolean secret,
            List<Trigger> reveal,
            Optional<String> hintedBy,
            int opensStage,
            ResearchEntry.Warp warp,
            Map<String, Integer> rewards) {}

    private static final MapCodec<Head> HEAD =
            RecordCodecBuilder.mapCodec(
                    i ->
                            i.group(
                                            Codec.intRange(1, 4)
                                                    .fieldOf("stage")
                                                    .forGetter(Head::stage),
                                            ID.fieldOf("category").forGetter(Head::category),
                                            POSITION.fieldOf("position").forGetter(Head::position),
                                            ICON.fieldOf("icon").forGetter(Head::icon),
                                            enumCodec(EntryKind.values(), EntryKind::id)
                                                    .fieldOf("kind")
                                                    .forGetter(Head::kind),
                                            ID.listOf()
                                                    .optionalFieldOf("parents", List.of())
                                                    .forGetter(Head::parents),
                                            ID.listOf()
                                                    .optionalFieldOf("hidden_parents", List.of())
                                                    .forGetter(Head::hiddenParents),
                                            sized(PAGE, 1, 32)
                                                    .fieldOf("pages")
                                                    .forGetter(Head::pages))
                                    .apply(i, Head::new));

    private static final MapCodec<Tail> TAIL =
            RecordCodecBuilder.mapCodec(
                    i ->
                            i.group(
                                            sized(ID, 0, 5)
                                                    .optionalFieldOf("anchors", List.of())
                                                    .forGetter(Tail::anchors),
                                            Codec.unboundedMap(ID, Codec.intRange(1, 4096))
                                                    .optionalFieldOf("skip_cost", Map.of())
                                                    .forGetter(Tail::skipCost),
                                            sized(TRIGGER, 0, 16)
                                                    .optionalFieldOf("triggers", List.of())
                                                    .forGetter(Tail::triggers),
                                            Codec.BOOL
                                                    .optionalFieldOf("secret", false)
                                                    .forGetter(Tail::secret),
                                            sized(TRIGGER, 0, 16)
                                                    .optionalFieldOf("reveal", List.of())
                                                    .forGetter(Tail::reveal),
                                            ID.optionalFieldOf("hinted_by")
                                                    .forGetter(Tail::hintedBy),
                                            Codec.intRange(1, 4)
                                                    .optionalFieldOf("opens_stage")
                                                    .xmap(
                                                            o -> o.orElse(0),
                                                            s ->
                                                                    s == 0
                                                                            ? Optional.empty()
                                                                            : Optional.of(s))
                                                    .forGetter(Tail::opensStage),
                                            WARP.optionalFieldOf("warp", ResearchEntry.Warp.NONE)
                                                    .forGetter(Tail::warp),
                                            REWARDS.optionalFieldOf("rewards", Map.of())
                                                    .forGetter(Tail::rewards))
                                    .apply(i, Tail::new));

    /** One research entry, strict about its fields. */
    public static final Codec<ResearchEntry> ENTRY =
            strict(
                            RecordCodecBuilder.<ResearchEntry>mapCodec(
                                    i ->
                                            i.group(
                                                            HEAD.forGetter(ResearchCodecs::head),
                                                            TAIL.forGetter(ResearchCodecs::tail))
                                                    .apply(i, ResearchCodecs::entry)))
                    .codec();

    /**
     * One category, strict about its fields; a missing or null {@code stage} is the Choir pages'.
     */
    public static final Codec<CategoryEntry> CATEGORY =
            strict(
                            RecordCodecBuilder.<CategoryEntry>mapCodec(
                                    i ->
                                            i.group(
                                                            Codec.intRange(1, 4)
                                                                    .optionalFieldOf("stage")
                                                                    .forGetter(
                                                                            c ->
                                                                                    Optional
                                                                                            .ofNullable(
                                                                                                    c
                                                                                                            .stage())),
                                                            ICON.fieldOf("icon")
                                                                    .forGetter(CategoryEntry::icon),
                                                            Codec.INT
                                                                    .fieldOf("order")
                                                                    .forGetter(
                                                                            CategoryEntry::order))
                                                    .apply(
                                                            i,
                                                            (stage, icon, order) ->
                                                                    new CategoryEntry(
                                                                            stage.orElse(null),
                                                                            icon,
                                                                            order))))
                    .codec();

    /** {@link #ENTRY} as the registry's element type, checking the down-cast on encode. */
    public static final Codec<Research> RESEARCH =
            ENTRY.flatComapMap(
                    e -> e,
                    r ->
                            r instanceof ResearchEntry e
                                    ? DataResult.success(e)
                                    : DataResult.error(() -> "not a vftv research entry: " + r));

    /** {@link #CATEGORY} as the registry's element type, checking the down-cast on encode. */
    public static final Codec<ResearchCategory> RESEARCH_CATEGORY =
            CATEGORY.flatComapMap(
                    c -> c,
                    r ->
                            r instanceof CategoryEntry c
                                    ? DataResult.success(c)
                                    : DataResult.error(() -> "not a vftv research category: " + r));

    private ResearchCodecs() {}

    private static Head head(ResearchEntry e) {
        return new Head(
                e.stage(),
                e.category(),
                List.of(e.x(), e.y()),
                e.icon(),
                e.kind(),
                e.parents(),
                e.hiddenParents(),
                e.pages());
    }

    private static Tail tail(ResearchEntry e) {
        return new Tail(
                e.anchors(),
                e.skipCost(),
                e.triggers(),
                e.secret(),
                e.reveal(),
                Optional.ofNullable(e.hintedBy()),
                e.opensStage(),
                e.warp(),
                e.rewards());
    }

    private static ResearchEntry entry(Head h, Tail t) {
        return new ResearchEntry(
                h.stage(),
                h.category(),
                h.position().get(0),
                h.position().get(1),
                h.icon(),
                h.kind(),
                h.parents(),
                h.hiddenParents(),
                t.anchors(),
                t.skipCost(),
                t.triggers(),
                t.secret(),
                t.reveal(),
                t.hintedBy().orElse(null),
                t.opensStage(),
                t.warp(),
                t.rewards(),
                h.pages());
    }

    private static DataResult<Trigger> scanned(
            Pair<Pair<Optional<String>, Optional<String>>, Boolean> p) {
        Optional<String> key = p.getFirst().getFirst();
        Optional<String> tag = p.getFirst().getSecond();
        boolean node = p.getSecond();
        if ((key.isPresent() ? 1 : 0) + (tag.isPresent() ? 1 : 0) + (node ? 1 : 0) != 1) {
            return DataResult.error(
                    () -> "a scanned trigger needs exactly one of key, tag or node: true");
        }
        return DataResult.success(new Trigger.Scanned(key.orElse(null), tag.orElse(null), node));
    }

    private static DataResult<Pair<Pair<Optional<String>, Optional<String>>, Boolean>> scannedParts(
            Trigger t) {
        Trigger.Scanned s = (Trigger.Scanned) t;
        return DataResult.success(
                Pair.of(
                        Pair.of(Optional.ofNullable(s.key()), Optional.ofNullable(s.tag())),
                        s.node()));
    }

    private static RecordCodecBuilder<ResearchEntry.Warp, Integer> warpKind(
            String name, Function<ResearchEntry.Warp, Integer> getter) {
        return Codec.intRange(0, 100).optionalFieldOf(name, 0).forGetter(getter);
    }

    private static <T> DataResult<MapCodec<? extends T>> known(
            Map<String, MapCodec<? extends T>> types, String type, String what) {
        MapCodec<? extends T> codec = types.get(type);
        return codec == null
                ? DataResult.error(() -> "unknown " + what + " type " + type)
                : DataResult.success(codec);
    }

    /**
     * A list of {@code min}..{@code max} elements. Unlike {@code listOf(min, max)}, a bad element's
     * own error is kept in the message, so the refusal names what is wrong.
     */
    private static <T> Codec<List<T>> sized(Codec<T> element, int min, int max) {
        return element.listOf()
                .validate(
                        l ->
                                l.size() >= min && l.size() <= max
                                        ? DataResult.success(l)
                                        : DataResult.error(
                                                () ->
                                                        l.size()
                                                                + " elements, outside "
                                                                + min
                                                                + ".."
                                                                + max));
    }

    /** A string matching {@code regex}. */
    private static Codec<String> pattern(String regex, String what) {
        Pattern compiled = Pattern.compile(regex);
        return Codec.STRING.validate(
                s ->
                        compiled.matcher(s).matches()
                                ? DataResult.success(s)
                                : DataResult.error(() -> s + " is not " + what));
    }

    /** An enum by its JSON name. */
    static <E extends Enum<E>> Codec<E> enumCodec(E[] values, Function<E, String> id) {
        Map<String, E> byId = Arrays.stream(values).collect(Collectors.toMap(id, v -> v));
        return Codec.STRING.comapFlatMap(
                s -> {
                    E value = byId.get(s);
                    return value == null
                            ? DataResult.error(() -> s + " is not one of " + byId.keySet())
                            : DataResult.success(value);
                },
                id);
    }

    /**
     * {@code codec}, refusing any input key it doesn't read (other than {@code also}): the common
     * list's "unknown field".
     */
    static <T> MapCodec<T> strict(MapCodec<T> codec, String... also) {
        return new MapCodec<>() {
            @Override
            public <O> Stream<O> keys(DynamicOps<O> ops) {
                return Stream.concat(codec.keys(ops), Arrays.stream(also).map(ops::createString));
            }

            @Override
            public <O> DataResult<T> decode(DynamicOps<O> ops, MapLike<O> input) {
                Set<String> known =
                        Stream.concat(
                                        codec.keys(ops)
                                                .map(
                                                        k ->
                                                                ops.getStringValue(k)
                                                                        .result()
                                                                        .orElse("")),
                                        Arrays.stream(also))
                                .collect(Collectors.toSet());
                List<String> unknown =
                        input.entries()
                                .map(e -> ops.getStringValue(e.getFirst()).result().orElse("?"))
                                .filter(k -> !known.contains(k))
                                .sorted()
                                .toList();
                return unknown.isEmpty()
                        ? codec.decode(ops, input)
                        : DataResult.error(() -> "unknown fields " + unknown);
            }

            @Override
            public <O> RecordBuilder<O> encode(
                    T input, DynamicOps<O> ops, RecordBuilder<O> prefix) {
                return codec.encode(input, ops, prefix);
            }

            @Override
            public String toString() {
                return "Strict[" + codec + "]";
            }
        };
    }
}
