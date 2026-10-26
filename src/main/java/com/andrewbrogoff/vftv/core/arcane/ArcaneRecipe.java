package com.andrewbrogoff.vftv.core.arcane;

import com.andrewbrogoff.vftv.api.arcane.ArcaneLimits;
import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.api.research.Research;
import com.andrewbrogoff.vftv.api.research.ResearchKeys;
import com.andrewbrogoff.vftv.api.vis.VisKind;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * A {@code vftv:arcane} recipe (arcane spec, "The recipe type"): a shaped or shapeless grid of at
 * most 3x3, a research gate, a kind and a base vis cost. Matching reuses vanilla's shaped pattern
 * and shapeless recipe, so the mirror rule and NeoForge's custom ingredients work unchanged.
 *
 * <p>Not vanilla crafting: the type is not {@code minecraft:crafting}, the recipe is special (never
 * in the recipe book), shows no notification and cannot be placed.
 */
public final class ArcaneRecipe implements Recipe<CraftingInput> {
    /** {@code vftv:arcane}: the recipe type's and the serializer's id. */
    public static final Identifier ID = Identifier.fromNamespaceAndPath("vftv", "arcane");

    /** The recipe type {@code vftv:arcane}. */
    public static final RecipeType<ArcaneRecipe> TYPE = RecipeType.simple(ID);

    /** Ascending id, by {@code String.compareTo} on {@code ns:path}. */
    static final Comparator<ResourceKey<?>> BY_ID =
            Comparator.comparing(k -> k.identifier().toString());

    private static final Codec<Identifier> BOUNDED_ID =
            Identifier.CODEC.validate(
                    id ->
                            id.toString().length() <= ArcaneLimits.MAX_ID_CHARS
                                    ? DataResult.success(id)
                                    : DataResult.error(
                                            () ->
                                                    "id longer than "
                                                            + ArcaneLimits.MAX_ID_CHARS
                                                            + " characters: "
                                                            + id));

    private static final Codec<ResourceKey<Research>> RESEARCH =
            BOUNDED_ID.xmap(
                    id -> ResourceKey.create(ResearchKeys.REGISTRY, id), ResourceKey::identifier);

    private static final Codec<VisKind> KIND =
            Codec.STRING.comapFlatMap(
                    s ->
                            switch (s) {
                                case "ordinary" -> DataResult.success(VisKind.ORDINARY);
                                case "void" -> DataResult.success(VisKind.VOID);
                                default ->
                                        DataResult.error(
                                                () -> "kind must be ordinary or void, not " + s);
                            },
                    k -> k.name().toLowerCase(Locale.ROOT));

    private static final Codec<Map<ResourceKey<Aspect>, Integer>> VIS =
            Codec.unboundedMap(
                            BOUNDED_ID.xmap(
                                    id -> ResourceKey.create(AspectKeys.REGISTRY, id),
                                    ResourceKey::identifier),
                            Codec.intRange(ArcaneLimits.MIN_BASE_COST, ArcaneLimits.MAX_BASE_COST))
                    .comapFlatMap(ArcaneRecipe::checkVis, m -> m);

    private static final MapCodec<List<String>> ROWS = Codec.STRING.listOf().fieldOf("pattern");

    private static final MapCodec<List<Ingredient>> INGREDIENTS =
            Ingredient.CODEC
                    .listOf(1, ArcaneLimits.GRID_SIZE * ArcaneLimits.GRID_SIZE)
                    .fieldOf("ingredients");

    /** Exactly one of {@code pattern}+{@code key} (left) or {@code ingredients} (right). */
    private static final MapCodec<Either<ShapedRecipePattern, List<Ingredient>>> SHAPE =
            new MapCodec<>() {
                @Override
                public <T> Stream<T> keys(DynamicOps<T> ops) {
                    return Stream.of("pattern", "key", "ingredients").map(ops::createString);
                }

                @Override
                public <T> DataResult<Either<ShapedRecipePattern, List<Ingredient>>> decode(
                        DynamicOps<T> ops, MapLike<T> input) {
                    boolean shaped = input.get("pattern") != null || input.get("key") != null;
                    boolean shapeless = input.get("ingredients") != null;
                    if (shaped == shapeless) {
                        return DataResult.error(
                                () -> "exactly one of pattern+key or ingredients is required");
                    }
                    if (shapeless) {
                        return INGREDIENTS.decode(ops, input).map(Either::right);
                    }
                    return ROWS.decode(ops, input)
                            .flatMap(ArcaneRecipe::checkRows)
                            .flatMap(rows -> ShapedRecipePattern.MAP_CODEC.decode(ops, input))
                            .map(Either::left);
                }

                @Override
                public <T> RecordBuilder<T> encode(
                        Either<ShapedRecipePattern, List<Ingredient>> shape,
                        DynamicOps<T> ops,
                        RecordBuilder<T> prefix) {
                    return shape.map(
                            p -> ShapedRecipePattern.MAP_CODEC.encode(p, ops, prefix),
                            i -> INGREDIENTS.encode(i, ops, prefix));
                }
            };

    /**
     * The body without the registry checks: every format rule except "{@code research} names an
     * entry" and "each {@code vis} key is a vis aspect". Datagen validates authored bodies with it,
     * where the research registry is not yet loaded; {@link #MAP_CODEC} adds both checks.
     */
    public static final MapCodec<ArcaneRecipe> BODY_CODEC =
            RecordCodecBuilder.mapCodec(
                    i ->
                            i.group(
                                            RESEARCH.fieldOf("research")
                                                    .forGetter(ArcaneRecipe::research),
                                            KIND.optionalFieldOf("kind", VisKind.ORDINARY)
                                                    .forGetter(ArcaneRecipe::kind),
                                            SHAPE.forGetter(r -> r.shape),
                                            ItemStackTemplate.CODEC
                                                    .fieldOf("result")
                                                    .forGetter(ArcaneRecipe::result),
                                            VIS.fieldOf("vis").forGetter(ArcaneRecipe::vis))
                                    .apply(i, ArcaneRecipe::new));

    /**
     * The registry-aware JSON codec: {@link #BODY_CODEC}, then {@code research} must name an entry
     * of {@code vftv:research} and each {@code vis} key must be a vis aspect of {@code
     * vftv:aspect}, both read through the decoding ops' registry access.
     */
    public static final MapCodec<ArcaneRecipe> MAP_CODEC =
            new MapCodec<>() {
                @Override
                public <T> Stream<T> keys(DynamicOps<T> ops) {
                    return BODY_CODEC.keys(ops);
                }

                @Override
                public <T> DataResult<ArcaneRecipe> decode(DynamicOps<T> ops, MapLike<T> input) {
                    return BODY_CODEC.decode(ops, input).flatMap(r -> checkRegistries(ops, r));
                }

                @Override
                public <T> RecordBuilder<T> encode(
                        ArcaneRecipe recipe, DynamicOps<T> ops, RecordBuilder<T> prefix) {
                    return BODY_CODEC.encode(recipe, ops, prefix);
                }
            };

    /**
     * The bounded network codec the full recipe sync encodes through: ids at most {@value
     * ArcaneLimits#MAX_ID_CHARS} characters, the pattern at most 3x3, at most 9 ingredients, 1-8
     * cost entries of 1-1000. Out of bounds throws a {@link DecoderException} before allocating.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, ArcaneRecipe> STREAM_CODEC =
            StreamCodec.of(ArcaneRecipe::write, ArcaneRecipe::read);

    /** The serializer, registered as {@code vftv:arcane}. */
    public static final RecipeSerializer<ArcaneRecipe> SERIALIZER =
            new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    private final ResourceKey<Research> research;
    private final VisKind kind;
    private final Either<ShapedRecipePattern, List<Ingredient>> shape;
    private final ItemStackTemplate result;
    private final Map<ResourceKey<Aspect>, Integer> vis;
    private final @Nullable ShapelessRecipe shapeless;

    /**
     * A recipe as the codecs read it.
     *
     * @param research the gate
     * @param kind the quote context's kind
     * @param shape the shaped pattern (left) or the shapeless ingredients (right)
     * @param result the static output
     * @param vis the base cost, 1-8 entries of 1-1000; kept in ascending aspect id
     */
    public ArcaneRecipe(
            ResourceKey<Research> research,
            VisKind kind,
            Either<ShapedRecipePattern, List<Ingredient>> shape,
            ItemStackTemplate result,
            Map<ResourceKey<Aspect>, Integer> vis) {
        this.research = research;
        this.kind = kind;
        this.shape = shape;
        this.result = result;
        this.vis = sorted(vis);
        this.shapeless =
                shape.right()
                        .map(
                                ingredients ->
                                        new ShapelessRecipe(
                                                new Recipe.CommonInfo(false),
                                                new CraftingRecipe.CraftingBookInfo(
                                                        CraftingBookCategory.MISC, ""),
                                                result,
                                                ingredients))
                        .orElse(null);
    }

    /** The research entry that gates this recipe. */
    public ResourceKey<Research> research() {
        return research;
    }

    /** The quote context's kind. */
    public VisKind kind() {
        return kind;
    }

    /** The shaped pattern, empty for a shapeless recipe. */
    public Optional<ShapedRecipePattern> pattern() {
        return shape.left();
    }

    /** The shapeless ingredients in data order, empty for a shaped recipe. */
    public List<Ingredient> ingredients() {
        return shape.right().orElse(List.of());
    }

    /** The static output, as authored; {@link #createResult} makes and checks a stack of it. */
    public ItemStackTemplate result() {
        return result;
    }

    /** The base cost, in ascending aspect id. */
    public Map<ResourceKey<Aspect>, Integer> vis() {
        return vis;
    }

    /**
     * Whether the grid matches, by vanilla's rules: {@code ShapedRecipePattern.matches} (with the
     * mirror) or {@code ShapelessRecipe.matches}. The player is never in it.
     *
     * @param grid the grid as {@code CraftingInput.ofPositioned} builds it, trimmed to its occupied
     *     area
     * @param level the level, which vanilla's shapeless match takes and ignores
     */
    @Override
    public boolean matches(CraftingInput grid, Level level) {
        ShapelessRecipe matcher = shapeless;
        if (matcher != null) {
            return matcher.matches(grid, level);
        }
        return shape.left().orElseThrow().matches(grid);
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        return result.create();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public boolean showNotification() {
        return false;
    }

    @Override
    public String group() {
        return "";
    }

    @Override
    public RecipeSerializer<ArcaneRecipe> getSerializer() {
        return SERIALIZER;
    }

    @Override
    public RecipeType<ArcaneRecipe> getType() {
        return TYPE;
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    /** Never shown: the recipe is special, so no recipe book holds it. */
    @Override
    public RecipeBookCategory recipeBookCategory() {
        return RecipeBookCategories.CRAFTING_MISC;
    }

    /**
     * Vanilla's shaped or shapeless crafting display, for the book and JEI, with the Worktable as
     * the crafting station.
     */
    @Override
    public List<RecipeDisplay> display() {
        SlotDisplay output = new SlotDisplay.ItemStackSlotDisplay(result);
        SlotDisplay station = new SlotDisplay.ItemSlotDisplay(ArcaneContent.worktableItem());
        return List.of(
                shape.<RecipeDisplay>map(
                        p ->
                                new ShapedCraftingRecipeDisplay(
                                        p.width(),
                                        p.height(),
                                        p.ingredients().stream()
                                                .map(Ingredient::optionalIngredientToDisplay)
                                                .toList(),
                                        output,
                                        station),
                        i ->
                                new ShapelessCraftingRecipeDisplay(
                                        i.stream().map(Ingredient::display).toList(),
                                        output,
                                        station)));
    }

    /**
     * Creates the result and checks it is non-empty. Creation validates strictly, so a count over
     * the stack's own maximum (resolved with its components) or invalid components also give an
     * empty stack. Needs item components bound, so the snapshot runs it, not the decode (see {@link
     * ArcaneRecipeSnapshot}).
     */
    public DataResult<ItemStack> createResult() {
        ItemStack stack = result.create();
        return stack.isEmpty()
                ? DataResult.error(
                        () ->
                                "the result "
                                        + result
                                        + " creates an empty stack (invalid, or over its maximum"
                                        + " stack size)")
                : DataResult.success(stack);
    }

    /**
     * Whether an aspect is a vis aspect: an exposed primal, with no parents and no hidden-primal
     * data (wands spec, "Vis storage").
     */
    public static boolean isVisAspect(Aspect aspect) {
        return aspect.parents().isEmpty() && aspect.hiddenPrimal() == null;
    }

    private static <T> DataResult<ArcaneRecipe> checkRegistries(
            DynamicOps<T> ops, ArcaneRecipe recipe) {
        if (!(ops instanceof RegistryOps<T> registryOps)) {
            return DataResult.error(() -> "an arcane recipe decodes only with registry access");
        }
        Optional<HolderGetter<Aspect>> aspects = registryOps.getter(AspectKeys.REGISTRY);
        for (ResourceKey<Aspect> key : recipe.vis.keySet()) {
            Optional<Holder.Reference<Aspect>> aspect = aspects.flatMap(getter -> getter.get(key));
            if (aspect.isEmpty() || !isVisAspect(aspect.get().value())) {
                return DataResult.error(() -> key.identifier() + " is not a vis aspect");
            }
        }
        Optional<HolderGetter<Research>> entries = registryOps.getter(ResearchKeys.REGISTRY);
        if (entries.isEmpty() || entries.get().get(recipe.research).isEmpty()) {
            return DataResult.error(() -> "unknown research entry " + recipe.research.identifier());
        }
        return DataResult.success(recipe);
    }

    private static DataResult<List<String>> checkRows(List<String> rows) {
        int size = ArcaneLimits.GRID_SIZE;
        if (rows.isEmpty() || rows.size() > size) {
            return DataResult.error(() -> "a pattern has 1-" + size + " rows, not " + rows.size());
        }
        for (String row : rows) {
            if (row.isEmpty() || row.length() > size) {
                return DataResult.error(
                        () -> "a pattern row has 1-" + size + " symbols: '" + row + "'");
            }
        }
        if (rows.stream().allMatch(row -> row.isBlank())) {
            return DataResult.error(() -> "a pattern needs at least one symbol");
        }
        return DataResult.success(rows);
    }

    private static DataResult<Map<ResourceKey<Aspect>, Integer>> checkVis(
            Map<ResourceKey<Aspect>, Integer> vis) {
        if (vis.isEmpty() || vis.size() > ArcaneLimits.MAX_COST_ENTRIES) {
            return DataResult.error(
                    () ->
                            "vis has 1-"
                                    + ArcaneLimits.MAX_COST_ENTRIES
                                    + " entries, not "
                                    + vis.size());
        }
        return DataResult.success(vis);
    }

    private static Map<ResourceKey<Aspect>, Integer> sorted(Map<ResourceKey<Aspect>, Integer> vis) {
        Map<ResourceKey<Aspect>, Integer> byId = new TreeMap<>(BY_ID);
        byId.putAll(vis);
        return Collections.unmodifiableMap(new LinkedHashMap<>(byId));
    }

    private static void write(RegistryFriendlyByteBuf buf, ArcaneRecipe recipe) {
        buf.writeUtf(recipe.research.identifier().toString(), ArcaneLimits.MAX_ID_CHARS);
        buf.writeByte(recipe.kind == VisKind.VOID ? 1 : 0);
        recipe.shape.ifLeft(
                pattern -> {
                    buf.writeByte(pattern.width());
                    buf.writeByte(pattern.height());
                    pattern.ingredients()
                            .forEach(i -> Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC.encode(buf, i));
                });
        recipe.shape.ifRight(
                ingredients -> {
                    buf.writeByte(0);
                    buf.writeByte(ingredients.size());
                    ingredients.forEach(i -> Ingredient.CONTENTS_STREAM_CODEC.encode(buf, i));
                });
        ItemStackTemplate.STREAM_CODEC.encode(buf, recipe.result);
        buf.writeByte(recipe.vis.size());
        recipe.vis.forEach(
                (aspect, cost) -> {
                    buf.writeUtf(aspect.identifier().toString(), ArcaneLimits.MAX_ID_CHARS);
                    buf.writeVarInt(cost);
                });
    }

    private static ArcaneRecipe read(RegistryFriendlyByteBuf buf) {
        ResourceKey<Research> research = ResourceKey.create(ResearchKeys.REGISTRY, readId(buf));
        VisKind kind = bounded(buf.readByte(), 0, 1, "kind") == 1 ? VisKind.VOID : VisKind.ORDINARY;
        int size = ArcaneLimits.GRID_SIZE;
        int width = bounded(buf.readByte(), 0, size, "pattern width");
        Either<ShapedRecipePattern, List<Ingredient>> shape;
        if (width > 0) {
            int height = bounded(buf.readByte(), 1, size, "pattern height");
            List<Optional<Ingredient>> cells = new ArrayList<>(width * height);
            for (int i = 0; i < width * height; i++) {
                cells.add(Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC.decode(buf));
            }
            if (cells.stream().allMatch(Optional::isEmpty)) {
                throw new DecoderException("an arcane pattern with no ingredient");
            }
            shape =
                    Either.left(
                            new ShapedRecipePattern(
                                    width, height, List.copyOf(cells), Optional.empty()));
        } else {
            int count = bounded(buf.readByte(), 1, size * size, "ingredient count");
            List<Ingredient> ingredients = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                ingredients.add(Ingredient.CONTENTS_STREAM_CODEC.decode(buf));
            }
            shape = Either.right(List.copyOf(ingredients));
        }
        ItemStackTemplate result = ItemStackTemplate.STREAM_CODEC.decode(buf);
        int entries = bounded(buf.readByte(), 1, ArcaneLimits.MAX_COST_ENTRIES, "vis entries");
        Map<ResourceKey<Aspect>, Integer> vis = new LinkedHashMap<>();
        for (int i = 0; i < entries; i++) {
            ResourceKey<Aspect> aspect = ResourceKey.create(AspectKeys.REGISTRY, readId(buf));
            int cost =
                    bounded(
                            buf.readVarInt(),
                            ArcaneLimits.MIN_BASE_COST,
                            ArcaneLimits.MAX_BASE_COST,
                            "vis cost");
            if (vis.put(aspect, cost) != null) {
                throw new DecoderException("duplicate vis aspect " + aspect.identifier());
            }
        }
        return new ArcaneRecipe(research, kind, shape, result, vis);
    }

    private static Identifier readId(RegistryFriendlyByteBuf buf) {
        String text = buf.readUtf(ArcaneLimits.MAX_ID_CHARS);
        Identifier id = Identifier.tryParse(text);
        if (id == null) {
            throw new DecoderException("not an id: " + text);
        }
        return id;
    }

    private static int bounded(int value, int min, int max, String what) {
        if (value < min || value > max) {
            throw new DecoderException(what + " " + value + " outside " + min + ".." + max);
        }
        return value;
    }
}
