package com.andrewbrogoff.vftv.core.hush;

import com.andrewbrogoff.vftv.api.hush.HushKeys;
import com.andrewbrogoff.vftv.api.hush.HushedForm;
import com.andrewbrogoff.vftv.core.common.Registrar;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import org.jspecify.annotations.Nullable;

/**
 * The Hush's content holders (Hush spec): the thirteen hushed forms and their items, the Clearing
 * Bell, Hush Ash, the Pealing Bell with its item and block entity, the Hollow, the eleven sound
 * events, the {@code vftv:hushed} sound type and the {@code vftv:hush_mote} particle.
 */
public final class HushContent {
    /** {@code vftv:clearing_bell}. */
    public static final ResourceKey<Item> CLEARING_BELL_KEY = item("clearing_bell");

    /** {@code vftv:hush_ash}. */
    public static final ResourceKey<Item> HUSH_ASH_KEY = item("hush_ash");

    /** {@code vftv:pealing_bell}, the block. */
    public static final ResourceKey<Block> PEALING_BELL_KEY =
            ResourceKey.create(Registries.BLOCK, id("pealing_bell"));

    /** {@code vftv:pealing_bell}, the item. */
    public static final ResourceKey<Item> PEALING_BELL_ITEM_KEY = item("pealing_bell");

    /** The Clearing Bell's durability, fixed at registration. */
    public static final int BELL_DURABILITY = 64;

    /** {@code vftv:block.hushed.step}. */
    public static final Identifier HUSHED_STEP = id("block.hushed.step");

    /** {@code vftv:block.hushed.break}. */
    public static final Identifier HUSHED_BREAK = id("block.hushed.break");

    /** {@code vftv:block.hushed.place}. */
    public static final Identifier HUSHED_PLACE = id("block.hushed.place");

    /** {@code vftv:block.hushed.hit}. */
    public static final Identifier HUSHED_HIT = id("block.hushed.hit");

    /** {@code vftv:block.hushed.fall}. */
    public static final Identifier HUSHED_FALL = id("block.hushed.fall");

    /** {@code vftv:clearing_bell.ring}. */
    public static final Identifier CLEARING_RING = id("clearing_bell.ring");

    /** {@code vftv:pealing_bell.ring}. */
    public static final Identifier PEALING_RING = id("pealing_bell.ring");

    /** {@code vftv:hush.restore}. */
    public static final Identifier RESTORE = id("hush.restore");

    /** {@code vftv:hollow.rise}. */
    public static final Identifier HOLLOW_RISE = id("hollow.rise");

    /** {@code vftv:hollow.hurt}. */
    public static final Identifier HOLLOW_HURT = id("hollow.hurt");

    /** {@code vftv:hollow.death}. */
    public static final Identifier HOLLOW_DEATH = id("hollow.death");

    /** Every sound event, in the spec's order. */
    public static final List<Identifier> SOUNDS =
            List.of(
                    HUSHED_STEP,
                    HUSHED_BREAK,
                    HUSHED_PLACE,
                    HUSHED_HIT,
                    HUSHED_FALL,
                    CLEARING_RING,
                    PEALING_RING,
                    RESTORE,
                    HOLLOW_RISE,
                    HOLLOW_HURT,
                    HOLLOW_DEATH);

    /** {@code vftv:hush_mote}. */
    public static final Identifier MOTE = id("hush_mote");

    private static List<Supplier<SoundEvent>> sounds = List.of();
    private static Map<Identifier, Supplier<Block>> forms = Map.of();
    private static Map<Identifier, Supplier<Item>> formItems = Map.of();
    private static Supplier<Item> clearingBell = HushContent::missing;
    private static Supplier<Item> hushAsh = HushContent::missing;
    private static Supplier<Block> pealingBell = HushContent::missing;
    private static Supplier<Item> pealingBellItem = HushContent::missing;
    private static Supplier<BlockEntityType<PealingBellBlockEntity>> pealingBellType =
            HushContent::missing;
    private static Supplier<EntityType<Hollow>> hollow = HushContent::missing;
    private static Supplier<SimpleParticleType> mote = HushContent::missing;
    private static @Nullable SoundType hushedSound;

    private HushContent() {}

    /** The hushed form registered for {@code form} (a {@link HushKeys#FORMS} id). */
    public static Block form(Identifier form) {
        return java.util.Objects.requireNonNull(forms.get(form), form::toString).get();
    }

    /** The hushed form's item. */
    public static Item formItem(Identifier form) {
        return java.util.Objects.requireNonNull(formItems.get(form), form::toString).get();
    }

    /** Every hushed form, in the table's order. */
    public static List<Block> forms() {
        List<Block> out = new ArrayList<>();
        forms.values().forEach(s -> out.add(s.get()));
        return out;
    }

    /** The Clearing Bell. */
    public static Item clearingBell() {
        return clearingBell.get();
    }

    /** Hush Ash. */
    public static Item hushAsh() {
        return hushAsh.get();
    }

    /** The Pealing Bell. */
    public static Block pealingBell() {
        return pealingBell.get();
    }

    /** The Pealing Bell's item. */
    public static Item pealingBellItem() {
        return pealingBellItem.get();
    }

    /** The Pealing Bell's block entity type. */
    public static BlockEntityType<PealingBellBlockEntity> pealingBellType() {
        return pealingBellType.get();
    }

    /** The Hollow. */
    public static EntityType<Hollow> hollow() {
        return hollow.get();
    }

    /** {@code vftv:hush_mote}. */
    public static SimpleParticleType mote() {
        return mote.get();
    }

    /** The {@code vftv:hushed} sound type: volume 0.5, pitch 0.8. */
    // core is loader-free, so NeoForge's supplier-based DeferredSoundType isn't available here.
    @SuppressWarnings("deprecation")
    public static SoundType hushedSound() {
        SoundType built = hushedSound;
        if (built == null) {
            // Built on first use, by the first form's registration: the events register first.
            built =
                    new SoundType(
                            0.5F,
                            0.8F,
                            sound(HUSHED_BREAK),
                            sound(HUSHED_STEP),
                            sound(HUSHED_PLACE),
                            sound(HUSHED_HIT),
                            sound(HUSHED_FALL));
            hushedSound = built;
        }
        return built;
    }

    /** The sound event registered as {@code id}, one of {@link #SOUNDS}. */
    public static SoundEvent sound(Identifier id) {
        return sounds.get(SOUNDS.indexOf(id)).get();
    }

    static void register(Registrar r) {
        sounds =
                SOUNDS.stream()
                        .map(
                                s ->
                                        r.register(
                                                Registries.SOUND_EVENT,
                                                s.getPath(),
                                                () -> SoundEvent.createVariableRangeEvent(s)))
                        .toList();
        Supplier<ParticleType<?>> particle =
                r.<ParticleType<?>>register(
                        Registries.PARTICLE_TYPE,
                        MOTE.getPath(),
                        () -> new SimpleParticleType(false));
        mote = () -> (SimpleParticleType) particle.get();
        Map<Identifier, Supplier<Block>> blocks = new LinkedHashMap<>();
        Map<Identifier, Supplier<Item>> items = new LinkedHashMap<>();
        for (HushedForm f : HushKeys.FORMS) {
            ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, f.form());
            Supplier<Block> block =
                    r.register(
                            Registries.BLOCK,
                            f.form().getPath(),
                            () -> factory(f.source()).apply(properties(f.source(), key)));
            blocks.put(f.form(), block);
            items.put(
                    f.form(),
                    r.register(
                            Registries.ITEM,
                            f.form().getPath(),
                            () ->
                                    new BlockItem(
                                            block.get(),
                                            new Item.Properties()
                                                    .setId(
                                                            ResourceKey.create(
                                                                    Registries.ITEM, f.form()))
                                                    .useBlockDescriptionPrefix())));
        }
        forms = blocks;
        formItems = items;
        clearingBell =
                r.register(
                        Registries.ITEM,
                        CLEARING_BELL_KEY.identifier().getPath(),
                        () ->
                                new ClearingBellItem(
                                        new Item.Properties()
                                                .setId(CLEARING_BELL_KEY)
                                                .durability(BELL_DURABILITY)));
        hushAsh =
                r.register(
                        Registries.ITEM,
                        HUSH_ASH_KEY.identifier().getPath(),
                        () -> new Item(new Item.Properties().setId(HUSH_ASH_KEY)));
        pealingBell =
                r.register(
                        Registries.BLOCK,
                        PEALING_BELL_KEY.identifier().getPath(),
                        () ->
                                new PealingBellBlock(
                                        BlockBehaviour.Properties.of()
                                                .mapColor(MapColor.GOLD)
                                                .strength(3.0F, 6.0F)
                                                .requiresCorrectToolForDrops()
                                                .sound(SoundType.ANVIL)
                                                .noOcclusion()
                                                .pushReaction(PushReaction.BLOCK)
                                                .setId(PEALING_BELL_KEY)));
        pealingBellItem =
                r.register(
                        Registries.ITEM,
                        PEALING_BELL_ITEM_KEY.identifier().getPath(),
                        () ->
                                new BlockItem(
                                        pealingBell(),
                                        new Item.Properties()
                                                .setId(PEALING_BELL_ITEM_KEY)
                                                .useBlockDescriptionPrefix()));
        Supplier<BlockEntityType<?>> be =
                r.register(
                        Registries.BLOCK_ENTITY_TYPE,
                        PEALING_BELL_KEY.identifier().getPath(),
                        () -> new BlockEntityType<>(PealingBellBlockEntity::new, pealingBell()));
        // Registered from the typed BlockEntityType just above.
        @SuppressWarnings("unchecked")
        Supplier<BlockEntityType<PealingBellBlockEntity>> typed =
                () -> (BlockEntityType<PealingBellBlockEntity>) be.get();
        pealingBellType = typed;
        Supplier<EntityType<?>> type =
                r.register(
                        Registries.ENTITY_TYPE,
                        HushKeys.HOLLOW.identifier().getPath(),
                        () ->
                                EntityType.Builder.<Hollow>of(Hollow::new, MobCategory.MONSTER)
                                        .sized(0.9F, 0.9F)
                                        .eyeHeight(0.7F)
                                        .notInPeaceful()
                                        .clientTrackingRange(8)
                                        .build(HushKeys.HOLLOW));
        // Registered from the typed EntityType just above.
        @SuppressWarnings("unchecked")
        Supplier<EntityType<Hollow>> typedHollow = () -> (EntityType<Hollow>) type.get();
        hollow = typedHollow;
    }

    /** The source's full properties with the Hush's sound, colour and piston rule. */
    private static BlockBehaviour.Properties properties(Identifier source, ResourceKey<Block> key) {
        return BlockBehaviour.Properties.ofFullCopy(BuiltInRegistries.BLOCK.getValue(source))
                .sound(hushedSound())
                .mapColor(MapColor.COLOR_LIGHT_GRAY)
                .pushReaction(PushReaction.BLOCK)
                .setId(key);
    }

    private static Function<BlockBehaviour.Properties, Block> factory(Identifier source) {
        return switch (source.getPath()) {
            case "grass_block" -> HushedBlocks.Grass::new;
            case "deepslate", "oak_log", "birch_log", "spruce_log" -> HushedBlocks.Pillar::new;
            case "short_grass" -> HushedBlocks.Plant::new;
            case "oak_leaves", "birch_leaves", "spruce_leaves" -> HushedBlocks.Leaves::new;
            default -> HushedBlocks.Plain::new;
        };
    }

    private static ResourceKey<Item> item(String path) {
        return ResourceKey.create(Registries.ITEM, id(path));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("vftv", path);
    }

    @SuppressWarnings("TypeParameterUnusedInFormals") // it only throws, so no cast is hidden
    private static <T> T missing() {
        throw new IllegalStateException("HushFeature.init has not run");
    }
}
