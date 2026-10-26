package com.andrewbrogoff.vftv.core.infusion;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.api.infusion.InfusionLimits;
import com.andrewbrogoff.vftv.api.research.Research;
import com.andrewbrogoff.vftv.api.research.ResearchKeys;
import com.andrewbrogoff.vftv.api.vis.VisKind;
import com.andrewbrogoff.vftv.core.infusion.model.InfusionMath;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;

/**
 * A {@code vftv:infusion} recipe (infusion spec, "The recipe type"): a research gate, a kind, a
 * centre, 1-16 components, an essentia cost and instability, and an item or enchantment result. Not
 * vanilla crafting: special, no notification, not placeable. The altar matches it; {@link
 * #matches(SingleRecipeInput, Level)} tests the centre only.
 */
public final class InfusionRecipe implements Recipe<SingleRecipeInput> {
    /** {@code vftv:infusion}: the recipe type's, the serializer's and the display type's id. */
    public static final Identifier ID = Identifier.fromNamespaceAndPath("vftv", "infusion");

    /** The recipe type {@code vftv:infusion}. */
    public static final RecipeType<InfusionRecipe> TYPE = RecipeType.simple(ID);

    private static final Comparator<ResourceKey<?>> BY_ID =
            Comparator.comparing(k -> k.identifier().toString());

    /** The result: an item (optionally transmuted from the centre) or an enchantment. */
    public sealed interface Result {
        /**
         * An item result.
         *
         * @param item the output, count 1
         * @param transmute whether it carries the centre copy's component patch
         */
        record Item(ItemStackTemplate item, boolean transmute) implements Result {}

        /**
         * An enchantment result: one run raises it straight to its cap.
         *
         * @param enchantment the enchantment
         * @param maxLevel the recipe's cap, 1-255
         */
        record Enchant(Holder<Enchantment> enchantment, int maxLevel) implements Result {
            /** The cap a run reaches: {@code min(max_level, getMaxLevel())}. */
            public int cap() {
                return Math.min(maxLevel, enchantment.value().getMaxLevel());
            }
        }
    }

    private static final Codec<Identifier> BOUNDED_ID =
            Identifier.CODEC.validate(
                    id ->
                            id.toString().length() <= InfusionLimits.MAX_ID_CHARS
                                    ? DataResult.success(id)
                                    : DataResult.error(
                                            () -> "id longer than 256 characters: " + id));

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

    private static final Codec<SortedMap<ResourceKey<Aspect>, Integer>> ESSENTIA =
            Codec.unboundedMap(
                            BOUNDED_ID.xmap(
                                    id -> ResourceKey.create(AspectKeys.REGISTRY, id),
                                    ResourceKey::identifier),
                            Codec.intRange(InfusionLimits.MIN_COST, InfusionLimits.MAX_COST))
                    .comapFlatMap(InfusionRecipe::checkEssentia, m -> m);

    /** The result's two forms, exactly one present; {@code transmute} only with an item. */
    private static final Codec<Result> RESULT =
            RecordCodecBuilder.<RawResult>create(
                            i ->
                                    i.group(
                                                    ItemStackTemplate.CODEC
                                                            .optionalFieldOf("item")
                                                            .forGetter(RawResult::item),
                                                    Codec.BOOL
                                                            .optionalFieldOf("transmute")
                                                            .forGetter(RawResult::transmute),
                                                    Enchantment.CODEC
                                                            .optionalFieldOf("enchantment")
                                                            .forGetter(RawResult::enchantment),
                                                    Codec.intRange(1, 255)
                                                            .optionalFieldOf("max_level")
                                                            .forGetter(RawResult::maxLevel))
                                            .apply(i, RawResult::new))
                    .comapFlatMap(RawResult::result, RawResult::of);

    private record RawResult(
            Optional<ItemStackTemplate> item,
            Optional<Boolean> transmute,
            Optional<Holder<Enchantment>> enchantment,
            Optional<Integer> maxLevel) {
        DataResult<Result> result() {
            if (item.isPresent() == enchantment.isPresent()) {
                return DataResult.error(() -> "a result is exactly one of item or enchantment");
            }
            if (item.isPresent()) {
                return maxLevel.isPresent()
                        ? DataResult.error(() -> "max_level belongs to an enchantment result")
                        : DataResult.success(new Result.Item(item.get(), transmute.orElse(false)));
            }
            if (transmute.isPresent()) {
                return DataResult.error(() -> "transmute is rejected with an enchantment result");
            }
            return maxLevel.isEmpty()
                    ? DataResult.error(() -> "an enchantment result needs max_level")
                    : DataResult.success(new Result.Enchant(enchantment.get(), maxLevel.get()));
        }

        static RawResult of(Result result) {
            return switch (result) {
                case Result.Item it ->
                        new RawResult(
                                Optional.of(it.item()),
                                it.transmute() ? Optional.of(true) : Optional.empty(),
                                Optional.empty(),
                                Optional.empty());
                case Result.Enchant e ->
                        new RawResult(
                                Optional.empty(),
                                Optional.empty(),
                                Optional.of(e.enchantment()),
                                Optional.of(e.maxLevel()));
            };
        }
    }

    /** The body with only the format bounds; {@link #MAP_CODEC} adds the registry checks. */
    public static final MapCodec<InfusionRecipe> BODY_CODEC =
            RecordCodecBuilder.mapCodec(
                    i ->
                            i.group(
                                            BOUNDED_ID
                                                    .xmap(
                                                            id ->
                                                                    ResourceKey.create(
                                                                            ResearchKeys.REGISTRY,
                                                                            id),
                                                            ResourceKey::identifier)
                                                    .fieldOf("research")
                                                    .forGetter(InfusionRecipe::research),
                                            KIND.optionalFieldOf("kind", VisKind.ORDINARY)
                                                    .forGetter(InfusionRecipe::kind),
                                            Ingredient.CODEC
                                                    .fieldOf("centre")
                                                    .forGetter(InfusionRecipe::centre),
                                            Ingredient.CODEC
                                                    .listOf(1, InfusionLimits.MAX_COMPONENTS)
                                                    .fieldOf("components")
                                                    .forGetter(InfusionRecipe::components),
                                            ESSENTIA.fieldOf("essentia")
                                                    .forGetter(InfusionRecipe::essentia),
                                            Codec.intRange(0, InfusionLimits.MAX_INSTABILITY)
                                                    .fieldOf("instability")
                                                    .forGetter(InfusionRecipe::instability),
                                            RESULT.fieldOf("result")
                                                    .forGetter(InfusionRecipe::result))
                                    .apply(i, InfusionRecipe::new));

    /**
     * The registry-aware JSON codec: {@link #BODY_CODEC}, then {@code research} must name an entry
     * of {@code vftv:research}, every cost key an aspect, and an enchanting cost must stay within
     * 1000 per cell over the whole run to the cap.
     */
    public static final MapCodec<InfusionRecipe> MAP_CODEC =
            new MapCodec<>() {
                @Override
                public <T> Stream<T> keys(DynamicOps<T> ops) {
                    return BODY_CODEC.keys(ops);
                }

                @Override
                public <T> DataResult<InfusionRecipe> decode(DynamicOps<T> ops, MapLike<T> input) {
                    return BODY_CODEC.decode(ops, input).flatMap(r -> checkRegistries(ops, r));
                }

                @Override
                public <T> RecordBuilder<T> encode(
                        InfusionRecipe recipe, DynamicOps<T> ops, RecordBuilder<T> prefix) {
                    return BODY_CODEC.encode(recipe, ops, prefix);
                }
            };

    /**
     * The bounded network codec: ids at most 256 characters, at most 16 components, 1-8 cost
     * entries of 1-1000, instability 0-100, max_level 1-255, the ingredients and result through
     * vanilla's codecs. Out of bounds throws a {@link DecoderException}.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, InfusionRecipe> STREAM_CODEC =
            StreamCodec.of(InfusionRecipe::write, InfusionRecipe::read);

    /** The serializer, registered as {@code vftv:infusion}. */
    public static final RecipeSerializer<InfusionRecipe> SERIALIZER =
            new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    private final ResourceKey<Research> research;
    private final VisKind kind;
    private final Ingredient centre;
    private final List<Ingredient> components;
    private final SortedMap<ResourceKey<Aspect>, Integer> essentia;
    private final int instability;
    private final Result result;

    /**
     * A recipe as the codecs read it.
     *
     * @param research the gate
     * @param kind passed to warp's penalty
     * @param centre the bowl's item
     * @param components the pedestal items, an exact multiset
     * @param essentia the cost (per level for enchanting), kept in ascending id
     * @param instability the recipe's own instability, 0-100
     * @param result the output
     */
    public InfusionRecipe(
            ResourceKey<Research> research,
            VisKind kind,
            Ingredient centre,
            List<Ingredient> components,
            Map<ResourceKey<Aspect>, Integer> essentia,
            int instability,
            Result result) {
        this.research = research;
        this.kind = kind;
        this.centre = centre;
        this.components = List.copyOf(components);
        TreeMap<ResourceKey<Aspect>, Integer> sorted = new TreeMap<>(BY_ID);
        sorted.putAll(essentia);
        this.essentia = Collections.unmodifiableSortedMap(sorted);
        this.instability = instability;
        this.result = result;
    }

    /** The research entry that gates this recipe. */
    public ResourceKey<Research> research() {
        return research;
    }

    /** The kind, passed to warp's penalty. */
    public VisKind kind() {
        return kind;
    }

    /** The centre ingredient. */
    public Ingredient centre() {
        return centre;
    }

    /** The component ingredients, in recipe order. */
    public List<Ingredient> components() {
        return components;
    }

    /** The cost (per level for enchanting), in ascending aspect id. */
    public SortedMap<ResourceKey<Aspect>, Integer> essentia() {
        return essentia;
    }

    /** The recipe's own instability. */
    public int instability() {
        return instability;
    }

    /** The result form. */
    public Result result() {
        return result;
    }

    /** Whether the centre ingredient accepts a count-one copy of {@code stack}. */
    public boolean matchesCentre(ItemStack stack) {
        return centre.test(stack.copyWithCount(1));
    }

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return matchesCentre(input.item());
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input) {
        return result instanceof Result.Item item ? item.item().create() : ItemStack.EMPTY;
    }

    /**
     * Creates an item result and checks it: non-empty, count 1, within its maximum stack size. An
     * enchantment result has nothing to check here. Needs components bound, so the snapshot runs
     * it.
     */
    public DataResult<InfusionRecipe> checkResult() {
        if (!(result instanceof Result.Item item)) {
            return DataResult.success(this);
        }
        ItemStack stack = item.item().create();
        return stack.isEmpty() || stack.getCount() != 1
                ? DataResult.error(() -> "the result " + item.item() + " is not one item")
                : DataResult.success(this);
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
    public RecipeSerializer<InfusionRecipe> getSerializer() {
        return SERIALIZER;
    }

    @Override
    public RecipeType<InfusionRecipe> getType() {
        return TYPE;
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    /** Never shown: the recipe is special. */
    @Override
    public RecipeBookCategory recipeBookCategory() {
        return RecipeBookCategories.CRAFTING_MISC;
    }

    @Override
    public List<RecipeDisplay> display() {
        List<SlotDisplay> parts = new ArrayList<>();
        components.forEach(c -> parts.add(c.display()));
        return List.of(
                new InfusionDisplay(
                        centre.display(),
                        parts,
                        new SlotDisplay.ItemStackSlotDisplay(displayResult()),
                        new SlotDisplay.ItemSlotDisplay(InfusionContent.bowlItem())));
    }

    /** The output a display shows: the item, or an enchanted book at the cap a run reaches. */
    public ItemStackTemplate displayResult() {
        return switch (result) {
            case Result.Item item -> item.item();
            case Result.Enchant e -> {
                ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
                EnchantmentHelper.updateEnchantments(book, m -> m.set(e.enchantment(), e.cap()));
                yield ItemStackTemplate.fromNonEmptyStack(book);
            }
        };
    }

    private static <T> DataResult<InfusionRecipe> checkRegistries(
            DynamicOps<T> ops, InfusionRecipe recipe) {
        if (!(ops instanceof RegistryOps<T> registryOps)) {
            return DataResult.error(() -> "an infusion recipe decodes only with registry access");
        }
        Optional<HolderGetter<Aspect>> aspects = registryOps.getter(AspectKeys.REGISTRY);
        for (ResourceKey<Aspect> key : recipe.essentia.keySet()) {
            if (aspects.flatMap(g -> g.get(key)).isEmpty()) {
                return DataResult.error(() -> "unknown aspect " + key.identifier());
            }
        }
        Optional<HolderGetter<Research>> entries = registryOps.getter(ResearchKeys.REGISTRY);
        if (entries.isEmpty() || entries.get().get(recipe.research).isEmpty()) {
            return DataResult.error(() -> "unknown research entry " + recipe.research.identifier());
        }
        return recipe.checkEnchantBound();
    }

    /** {@code essentia[a] × c(c + 1)/2 ≤ 1000} for every aspect of an enchanting recipe. */
    DataResult<InfusionRecipe> checkEnchantBound() {
        if (result instanceof Result.Enchant e) {
            int levels = InfusionMath.levels(0, e.cap());
            for (Map.Entry<ResourceKey<Aspect>, Integer> cost : essentia.entrySet()) {
                if ((long) cost.getValue() * levels > InfusionLimits.MAX_COST) {
                    return DataResult.error(
                            () ->
                                    cost.getKey().identifier()
                                            + " costs "
                                            + (long) cost.getValue() * levels
                                            + " over a whole run, above 1000");
                }
            }
        }
        return DataResult.success(this);
    }

    private static DataResult<SortedMap<ResourceKey<Aspect>, Integer>> checkEssentia(
            Map<ResourceKey<Aspect>, Integer> essentia) {
        if (essentia.isEmpty() || essentia.size() > InfusionLimits.MAX_COST_ENTRIES) {
            return DataResult.error(() -> "essentia has 1-8 entries, not " + essentia.size());
        }
        TreeMap<ResourceKey<Aspect>, Integer> sorted = new TreeMap<>(BY_ID);
        sorted.putAll(essentia);
        return DataResult.success(sorted);
    }

    private static void write(RegistryFriendlyByteBuf buf, InfusionRecipe recipe) {
        buf.writeUtf(recipe.research.identifier().toString(), InfusionLimits.MAX_ID_CHARS);
        buf.writeByte(recipe.kind == VisKind.VOID ? 1 : 0);
        Ingredient.CONTENTS_STREAM_CODEC.encode(buf, recipe.centre);
        buf.writeByte(recipe.components.size());
        recipe.components.forEach(c -> Ingredient.CONTENTS_STREAM_CODEC.encode(buf, c));
        buf.writeByte(recipe.essentia.size());
        recipe.essentia.forEach(
                (aspect, c) -> {
                    buf.writeUtf(aspect.identifier().toString(), InfusionLimits.MAX_ID_CHARS);
                    buf.writeVarInt(c);
                });
        buf.writeByte(recipe.instability);
        switch (recipe.result) {
            case Result.Item item -> {
                buf.writeByte(0);
                ItemStackTemplate.STREAM_CODEC.encode(buf, item.item());
                buf.writeBoolean(item.transmute());
            }
            case Result.Enchant e -> {
                buf.writeByte(1);
                Enchantment.STREAM_CODEC.encode(buf, e.enchantment());
                buf.writeVarInt(e.maxLevel());
            }
        }
    }

    private static InfusionRecipe read(RegistryFriendlyByteBuf buf) {
        ResourceKey<Research> research = ResourceKey.create(ResearchKeys.REGISTRY, readId(buf));
        VisKind kind = bounded(buf.readByte(), 0, 1, "kind") == 1 ? VisKind.VOID : VisKind.ORDINARY;
        Ingredient centre = Ingredient.CONTENTS_STREAM_CODEC.decode(buf);
        int n = bounded(buf.readByte(), 1, InfusionLimits.MAX_COMPONENTS, "components");
        List<Ingredient> components = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            components.add(Ingredient.CONTENTS_STREAM_CODEC.decode(buf));
        }
        int entries = bounded(buf.readByte(), 1, InfusionLimits.MAX_COST_ENTRIES, "cost entries");
        TreeMap<ResourceKey<Aspect>, Integer> essentia = new TreeMap<>(BY_ID);
        for (int i = 0; i < entries; i++) {
            ResourceKey<Aspect> aspect = ResourceKey.create(AspectKeys.REGISTRY, readId(buf));
            int c =
                    bounded(
                            buf.readVarInt(),
                            InfusionLimits.MIN_COST,
                            InfusionLimits.MAX_COST,
                            "cost");
            if (essentia.put(aspect, c) != null) {
                throw new DecoderException("duplicate aspect " + aspect.identifier());
            }
        }
        int instability = bounded(buf.readByte(), 0, InfusionLimits.MAX_INSTABILITY, "instability");
        Result result =
                switch (bounded(buf.readByte(), 0, 1, "result form")) {
                    case 0 ->
                            new Result.Item(
                                    ItemStackTemplate.STREAM_CODEC.decode(buf), buf.readBoolean());
                    default ->
                            new Result.Enchant(
                                    Enchantment.STREAM_CODEC.decode(buf),
                                    bounded(buf.readVarInt(), 1, 255, "max_level"));
                };
        return new InfusionRecipe(
                research, kind, centre, components, essentia, instability, result);
    }

    private static int bounded(int value, int min, int max, String what) {
        if (value < min || value > max) {
            throw new DecoderException(what + " " + value + " outside " + min + ".." + max);
        }
        return value;
    }

    private static Identifier readId(RegistryFriendlyByteBuf buf) {
        String text = buf.readUtf(InfusionLimits.MAX_ID_CHARS);
        Identifier id = Identifier.tryParse(text);
        if (id == null) {
            throw new DecoderException("not an id: " + text);
        }
        return id;
    }
}
