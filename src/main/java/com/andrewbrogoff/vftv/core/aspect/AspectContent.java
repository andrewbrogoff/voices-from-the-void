package com.andrewbrogoff.vftv.core.aspect;

import com.andrewbrogoff.vftv.core.common.Registrar;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * The aspect feature's content holders: the Tuning Fork, its three sound events, and the {@code
 * vftv:strike} recipe type with its serializer and recipe-book category.
 */
public final class AspectContent {
    /** {@code vftv:tuning_fork}. */
    public static final ResourceKey<Item> TUNING_FORK_KEY =
            ResourceKey.create(Registries.ITEM, id("tuning_fork"));

    /** {@code vftv:fork.strike}: every ringing strike. */
    public static final Identifier STRIKE_SOUND = id("fork.strike");

    /** {@code vftv:fork.answer}: a node answering a sounding. */
    public static final Identifier ANSWER_SOUND = id("fork.answer");

    /** {@code vftv:fork.discover}: a discovery. */
    public static final Identifier DISCOVER_SOUND = id("fork.discover");

    /** {@code vftv:strike}, the strike recipe type's id. */
    public static final Identifier STRIKE = id("strike");

    private static final Supplier<Item> NO_ITEM = AspectContent::missingItem;
    private static final Supplier<SoundEvent> NO_SOUND = AspectContent::missingSound;

    private static Supplier<Item> tuningFork = NO_ITEM;
    private static Supplier<SoundEvent> strikeSound = NO_SOUND;
    private static Supplier<SoundEvent> answerSound = NO_SOUND;
    private static Supplier<SoundEvent> discoverSound = NO_SOUND;
    private static final RecipeType<StrikeRecipe> STRIKE_TYPE = RecipeType.simple(STRIKE);
    private static final RecipeBookCategory STRIKE_CATEGORY = new RecipeBookCategory();

    private AspectContent() {}

    /** The Tuning Fork. */
    public static Item tuningFork() {
        return tuningFork.get();
    }

    /** The strike sound. */
    public static SoundEvent strikeSound() {
        return strikeSound.get();
    }

    /** The node answer. */
    public static SoundEvent answerSound() {
        return answerSound.get();
    }

    /** The discovery chime. */
    public static SoundEvent discoverSound() {
        return discoverSound.get();
    }

    /** {@code vftv:strike}. */
    public static RecipeType<StrikeRecipe> strikeType() {
        return STRIKE_TYPE;
    }

    /** The strike recipes' recipe-book category. */
    public static RecipeBookCategory strikeCategory() {
        return STRIKE_CATEGORY;
    }

    static void register(Registrar registrar) {
        tuningFork =
                registrar.register(
                        Registries.ITEM,
                        "tuning_fork",
                        () ->
                                new TuningForkItem(
                                        new Item.Properties().setId(TUNING_FORK_KEY).stacksTo(1)));
        strikeSound = sound(registrar, STRIKE_SOUND);
        answerSound = sound(registrar, ANSWER_SOUND);
        discoverSound = sound(registrar, DISCOVER_SOUND);
        registrar.<RecipeType<?>>register(
                Registries.RECIPE_TYPE, STRIKE.getPath(), () -> STRIKE_TYPE);
        registrar.<RecipeSerializer<?>>register(
                Registries.RECIPE_SERIALIZER, STRIKE.getPath(), () -> StrikeRecipe.SERIALIZER);
        registrar.register(
                Registries.RECIPE_BOOK_CATEGORY, STRIKE.getPath(), () -> STRIKE_CATEGORY);
    }

    private static Supplier<SoundEvent> sound(Registrar registrar, Identifier id) {
        return registrar.register(
                Registries.SOUND_EVENT,
                id.getPath(),
                () -> SoundEvent.createVariableRangeEvent(id));
    }

    private static Item missingItem() {
        throw new IllegalStateException("AspectFeature.init has not run");
    }

    private static SoundEvent missingSound() {
        throw new IllegalStateException("AspectFeature.init has not run");
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("vftv", path);
    }
}
