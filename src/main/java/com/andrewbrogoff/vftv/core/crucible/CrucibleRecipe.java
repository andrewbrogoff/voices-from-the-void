package com.andrewbrogoff.vftv.core.crucible;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.api.crucible.CrucibleLimits;
import com.andrewbrogoff.vftv.api.research.Research;
import com.andrewbrogoff.vftv.api.research.ResearchKeys;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.handler.codec.DecoderException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Stream;
import net.minecraft.core.HolderGetter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
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
import net.minecraft.world.level.Level;

/**
 * A {@code vftv:crucible} recipe (crucible spec, "The recipe type"): a research gate, one catalyst
 * ingredient, an aspect cost drawn from the pool and a static result. Not vanilla crafting:
 * special, no notification, not placeable.
 */
public final class CrucibleRecipe implements Recipe<SingleRecipeInput> {
    /** {@code vftv:crucible}: the recipe type's, the serializer's and the display type's id. */
    public static final Identifier ID = Identifier.fromNamespaceAndPath("vftv", "crucible");

    /** The recipe type {@code vftv:crucible}. */
    public static final RecipeType<CrucibleRecipe> TYPE = RecipeType.simple(ID);

    private static final Comparator<ResourceKey<?>> BY_ID =
            Comparator.comparing(k -> k.identifier().toString());

    private static final Codec<Identifier> BOUNDED_ID =
            Identifier.CODEC.validate(
                    id ->
                            id.toString().length() <= CrucibleLimits.MAX_ID_CHARS
                                    ? DataResult.success(id)
                                    : DataResult.error(
                                            () ->
                                                    "id longer than "
                                                            + CrucibleLimits.MAX_ID_CHARS
                                                            + " characters: "
                                                            + id));

    private static final Codec<SortedMap<ResourceKey<Aspect>, Integer>> ASPECTS =
            Codec.unboundedMap(
                            BOUNDED_ID.xmap(
                                    id -> ResourceKey.create(AspectKeys.REGISTRY, id),
                                    ResourceKey::identifier),
                            Codec.intRange(CrucibleLimits.MIN_COST, CrucibleLimits.MAX_COST))
                    .comapFlatMap(CrucibleRecipe::checkAspects, m -> m);

    /** The body without the registry checks, for datagen, where research is not loaded. */
    public static final MapCodec<CrucibleRecipe> BODY_CODEC =
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
                                                    .forGetter(CrucibleRecipe::research),
                                            Ingredient.CODEC
                                                    .fieldOf("catalyst")
                                                    .forGetter(CrucibleRecipe::catalyst),
                                            ASPECTS.fieldOf("aspects")
                                                    .forGetter(CrucibleRecipe::aspects),
                                            ItemStackTemplate.CODEC
                                                    .fieldOf("result")
                                                    .forGetter(CrucibleRecipe::result))
                                    .apply(i, CrucibleRecipe::new));

    /**
     * The registry-aware JSON codec: {@link #BODY_CODEC}, then {@code research} must name an entry
     * of {@code vftv:research} and every cost key an aspect of {@code vftv:aspect}.
     */
    public static final MapCodec<CrucibleRecipe> MAP_CODEC =
            new MapCodec<>() {
                @Override
                public <T> Stream<T> keys(DynamicOps<T> ops) {
                    return BODY_CODEC.keys(ops);
                }

                @Override
                public <T> DataResult<CrucibleRecipe> decode(DynamicOps<T> ops, MapLike<T> input) {
                    return BODY_CODEC.decode(ops, input).flatMap(r -> checkRegistries(ops, r));
                }

                @Override
                public <T> RecordBuilder<T> encode(
                        CrucibleRecipe recipe, DynamicOps<T> ops, RecordBuilder<T> prefix) {
                    return BODY_CODEC.encode(recipe, ops, prefix);
                }
            };

    /**
     * The bounded network codec: the research id at most {@value CrucibleLimits#MAX_ID_CHARS}
     * characters, 1-8 cost entries of 1-1000, the catalyst and result through vanilla's codecs. Out
     * of bounds throws a {@link DecoderException}.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, CrucibleRecipe> STREAM_CODEC =
            StreamCodec.of(CrucibleRecipe::write, CrucibleRecipe::read);

    /** The serializer, registered as {@code vftv:crucible}. */
    public static final RecipeSerializer<CrucibleRecipe> SERIALIZER =
            new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    private final ResourceKey<Research> research;
    private final Ingredient catalyst;
    private final SortedMap<ResourceKey<Aspect>, Integer> aspects;
    private final SortedMap<String, Integer> cost;
    private final ItemStackTemplate result;

    /**
     * A recipe as the codecs read it.
     *
     * @param research the gate
     * @param catalyst the item thrown in
     * @param aspects the cost, 1-8 entries of 1-1000, kept in ascending id
     * @param result the static output
     */
    public CrucibleRecipe(
            ResourceKey<Research> research,
            Ingredient catalyst,
            Map<ResourceKey<Aspect>, Integer> aspects,
            ItemStackTemplate result) {
        this.research = research;
        this.catalyst = catalyst;
        TreeMap<ResourceKey<Aspect>, Integer> sorted = new TreeMap<>(BY_ID);
        sorted.putAll(aspects);
        this.aspects = Collections.unmodifiableSortedMap(sorted);
        TreeMap<String, Integer> byText = new TreeMap<>();
        aspects.forEach((k, v) -> byText.put(k.identifier().toString(), v));
        this.cost = Collections.unmodifiableSortedMap(byText);
        this.result = result;
    }

    /** The research entry that gates this recipe. */
    public ResourceKey<Research> research() {
        return research;
    }

    /** The catalyst ingredient. */
    public Ingredient catalyst() {
        return catalyst;
    }

    /** The cost, in ascending aspect id. */
    public SortedMap<ResourceKey<Aspect>, Integer> aspects() {
        return aspects;
    }

    /** The cost by aspect id text, in ascending id. */
    public SortedMap<String, Integer> cost() {
        return cost;
    }

    /** The cost's total. */
    public int total() {
        return cost.values().stream().mapToInt(Integer::intValue).sum();
    }

    /** The static output. */
    public ItemStackTemplate result() {
        return result;
    }

    /** Whether the catalyst matches a count-one copy of {@code stack}. */
    public boolean matches(ItemStack stack) {
        return catalyst.test(stack.copyWithCount(1));
    }

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return matches(input.item());
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input) {
        return result.create();
    }

    /**
     * Creates the result and checks it is non-empty, which also catches a count over its own
     * component-resolved maximum. Needs components bound, so the snapshot runs it.
     */
    public DataResult<ItemStack> createResult() {
        ItemStack stack = result.create();
        return stack.isEmpty()
                ? DataResult.error(
                        () -> "the result " + result + " creates an empty or over-full stack")
                : DataResult.success(stack);
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
    public RecipeSerializer<CrucibleRecipe> getSerializer() {
        return SERIALIZER;
    }

    @Override
    public RecipeType<CrucibleRecipe> getType() {
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
        return List.of(
                new CrucibleDisplay(
                        catalyst.display(),
                        new SlotDisplay.ItemStackSlotDisplay(result),
                        new SlotDisplay.ItemSlotDisplay(CrucibleContent.crucibleItem())));
    }

    private static <T> DataResult<CrucibleRecipe> checkRegistries(
            DynamicOps<T> ops, CrucibleRecipe recipe) {
        if (!(ops instanceof RegistryOps<T> registryOps)) {
            return DataResult.error(() -> "a crucible recipe decodes only with registry access");
        }
        Optional<HolderGetter<Aspect>> aspects = registryOps.getter(AspectKeys.REGISTRY);
        for (ResourceKey<Aspect> key : recipe.aspects.keySet()) {
            if (aspects.flatMap(g -> g.get(key)).isEmpty()) {
                return DataResult.error(() -> "unknown aspect " + key.identifier());
            }
        }
        Optional<HolderGetter<Research>> entries = registryOps.getter(ResearchKeys.REGISTRY);
        if (entries.isEmpty() || entries.get().get(recipe.research).isEmpty()) {
            return DataResult.error(() -> "unknown research entry " + recipe.research.identifier());
        }
        return DataResult.success(recipe);
    }

    private static DataResult<SortedMap<ResourceKey<Aspect>, Integer>> checkAspects(
            Map<ResourceKey<Aspect>, Integer> aspects) {
        if (aspects.isEmpty() || aspects.size() > CrucibleLimits.MAX_COST_ENTRIES) {
            return DataResult.error(
                    () ->
                            "aspects has 1-"
                                    + CrucibleLimits.MAX_COST_ENTRIES
                                    + " entries, not "
                                    + aspects.size());
        }
        TreeMap<ResourceKey<Aspect>, Integer> sorted = new TreeMap<>(BY_ID);
        sorted.putAll(aspects);
        return DataResult.success(sorted);
    }

    private static void write(RegistryFriendlyByteBuf buf, CrucibleRecipe recipe) {
        buf.writeUtf(recipe.research.identifier().toString(), CrucibleLimits.MAX_ID_CHARS);
        Ingredient.CONTENTS_STREAM_CODEC.encode(buf, recipe.catalyst);
        buf.writeByte(recipe.aspects.size());
        recipe.aspects.forEach(
                (aspect, c) -> {
                    buf.writeUtf(aspect.identifier().toString(), CrucibleLimits.MAX_ID_CHARS);
                    buf.writeVarInt(c);
                });
        ItemStackTemplate.STREAM_CODEC.encode(buf, recipe.result);
    }

    private static CrucibleRecipe read(RegistryFriendlyByteBuf buf) {
        ResourceKey<Research> research = ResourceKey.create(ResearchKeys.REGISTRY, readId(buf));
        Ingredient catalyst = Ingredient.CONTENTS_STREAM_CODEC.decode(buf);
        int entries = buf.readByte();
        if (entries < 1 || entries > CrucibleLimits.MAX_COST_ENTRIES) {
            throw new DecoderException("cost entries " + entries + " outside 1.." + 8);
        }
        TreeMap<ResourceKey<Aspect>, Integer> aspects = new TreeMap<>(BY_ID);
        for (int i = 0; i < entries; i++) {
            ResourceKey<Aspect> aspect = ResourceKey.create(AspectKeys.REGISTRY, readId(buf));
            int c = buf.readVarInt();
            if (c < CrucibleLimits.MIN_COST || c > CrucibleLimits.MAX_COST) {
                throw new DecoderException("cost " + c + " outside 1..1000");
            }
            if (aspects.put(aspect, c) != null) {
                throw new DecoderException("duplicate aspect " + aspect.identifier());
            }
        }
        ItemStackTemplate result = ItemStackTemplate.STREAM_CODEC.decode(buf);
        return new CrucibleRecipe(research, catalyst, aspects, result);
    }

    private static Identifier readId(RegistryFriendlyByteBuf buf) {
        String text = buf.readUtf(CrucibleLimits.MAX_ID_CHARS);
        Identifier id = Identifier.tryParse(text);
        if (id == null) {
            throw new DecoderException("not an id: " + text);
        }
        return id;
    }
}
