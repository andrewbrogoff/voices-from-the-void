package com.andrewbrogoff.vftv.core.essentia;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.essentia.EssentiaKeys;
import com.andrewbrogoff.vftv.core.common.RawRecordCodec.Retained;
import com.andrewbrogoff.vftv.core.common.Registrar;
import com.andrewbrogoff.vftv.core.essentia.EssentiaRecords.JarStack;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;

/**
 * The essentia feature's content holders: the jar, Alembic, tube and valve blocks with their items
 * and block entities, the label item, the two item components and the six sound events.
 */
public final class EssentiaContent {
    /** {@code vftv:essentia.jar_fill}. */
    public static final Identifier JAR_FILL = id("essentia.jar_fill");

    /** {@code vftv:essentia.label_apply}. */
    public static final Identifier LABEL_APPLY = id("essentia.label_apply");

    /** {@code vftv:essentia.label_cut}. */
    public static final Identifier LABEL_CUT = id("essentia.label_cut");

    /** {@code vftv:essentia.valve_open}. */
    public static final Identifier VALVE_OPEN = id("essentia.valve_open");

    /** {@code vftv:essentia.valve_close}. */
    public static final Identifier VALVE_CLOSE = id("essentia.valve_close");

    /** {@code vftv:essentia.alembic_drip}. */
    public static final Identifier ALEMBIC_DRIP = id("essentia.alembic_drip");

    private static Supplier<EssentiaJarBlock> jar = new Unset<>();
    private static Supplier<AlembicBlock> alembic = new Unset<>();
    private static Supplier<EssentiaTubeBlock> tube = new Unset<>();
    private static Supplier<TubeValveBlock> valve = new Unset<>();
    private static Supplier<Item> jarItem = new Unset<>();
    private static Supplier<Item> alembicItem = new Unset<>();
    private static Supplier<Item> tubeItem = new Unset<>();
    private static Supplier<Item> valveItem = new Unset<>();
    private static Supplier<Item> label = new Unset<>();
    private static Supplier<BlockEntityType<JarBlockEntity>> jarType = new Unset<>();
    private static Supplier<BlockEntityType<LabelledBlockEntity>> tubeType = new Unset<>();
    private static Supplier<BlockEntityType<AlembicBlockEntity>> alembicType = new Unset<>();
    private static Supplier<BlockEntityType<ValveBlockEntity>> valveType = new Unset<>();
    private static Supplier<DataComponentType<Retained<JarStack>>> contents = new Unset<>();
    private static Supplier<DataComponentType<ResourceKey<Aspect>>> labelComponent = new Unset<>();
    private static Supplier<SoundEvent> jarFill = new Unset<>();
    private static Supplier<SoundEvent> labelApply = new Unset<>();
    private static Supplier<SoundEvent> labelCut = new Unset<>();
    private static Supplier<SoundEvent> valveOpen = new Unset<>();
    private static Supplier<SoundEvent> valveClose = new Unset<>();
    private static Supplier<SoundEvent> alembicDrip = new Unset<>();

    private EssentiaContent() {}

    /** {@code vftv:essentia_jar}. */
    public static EssentiaJarBlock jar() {
        return jar.get();
    }

    /** {@code vftv:alembic}. */
    public static AlembicBlock alembic() {
        return alembic.get();
    }

    /** {@code vftv:essentia_tube}. */
    public static EssentiaTubeBlock tube() {
        return tube.get();
    }

    /** {@code vftv:tube_valve}. */
    public static TubeValveBlock valve() {
        return valve.get();
    }

    /** The jar's item. */
    public static Item jarItem() {
        return jarItem.get();
    }

    /** The Alembic's item. */
    public static Item alembicItem() {
        return alembicItem.get();
    }

    /** The tube's item. */
    public static Item tubeItem() {
        return tubeItem.get();
    }

    /** The valve's item. */
    public static Item valveItem() {
        return valveItem.get();
    }

    /** {@code vftv:label}. */
    public static Item label() {
        return label.get();
    }

    /** The jar's block entity type. */
    public static BlockEntityType<JarBlockEntity> jarType() {
        return jarType.get();
    }

    /** The tube's block entity type. */
    public static BlockEntityType<LabelledBlockEntity> tubeType() {
        return tubeType.get();
    }

    /** The Alembic's block entity type. */
    public static BlockEntityType<AlembicBlockEntity> alembicType() {
        return alembicType.get();
    }

    /** The valve's block entity type. */
    public static BlockEntityType<ValveBlockEntity> valveType() {
        return valveType.get();
    }

    /** {@code vftv:jar_contents}. */
    public static DataComponentType<Retained<JarStack>> contents() {
        return contents.get();
    }

    /** {@code vftv:label}. */
    public static DataComponentType<ResourceKey<Aspect>> labelComponent() {
        return labelComponent.get();
    }

    /** The jar-fill sound. */
    public static SoundEvent jarFill() {
        return jarFill.get();
    }

    /** The label-applied sound. */
    public static SoundEvent labelApply() {
        return labelApply.get();
    }

    /** The label-cut sound. */
    public static SoundEvent labelCut() {
        return labelCut.get();
    }

    /** The valve-opens sound. */
    public static SoundEvent valveOpen() {
        return valveOpen.get();
    }

    /** The valve-closes sound. */
    public static SoundEvent valveClose() {
        return valveClose.get();
    }

    /** The Alembic's drip. */
    public static SoundEvent alembicDrip() {
        return alembicDrip.get();
    }

    static void register(Registrar r) {
        contents =
                typed(
                        r,
                        Registries.DATA_COMPONENT_TYPE,
                        EssentiaKeys.JAR_CONTENTS_COMPONENT.getPath(),
                        () ->
                                DataComponentType.<Retained<JarStack>>builder()
                                        .persistent(JarStack.RETAINED)
                                        .networkSynchronized(JarStack.STREAM)
                                        .build());
        labelComponent =
                typed(
                        r,
                        Registries.DATA_COMPONENT_TYPE,
                        EssentiaKeys.LABEL_COMPONENT.getPath(),
                        () ->
                                DataComponentType.<ResourceKey<Aspect>>builder()
                                        .persistent(EssentiaRecords.ASPECT)
                                        .networkSynchronized(EssentiaRecords.ASPECT_STREAM)
                                        .build());
        jar =
                block(
                        r,
                        "essentia_jar",
                        p -> new EssentiaJarBlock(p.strength(0.3f).sound(SoundType.GLASS)));
        alembic =
                block(r, "alembic", p -> new AlembicBlock(p.strength(0.5f).sound(SoundType.GLASS)));
        tube =
                block(
                        r,
                        "essentia_tube",
                        p -> new EssentiaTubeBlock(p.strength(0.5f).sound(SoundType.COPPER)));
        valve =
                block(
                        r,
                        "tube_valve",
                        p -> new TubeValveBlock(p.strength(0.5f).sound(SoundType.COPPER)));
        jarItem =
                item(r, "essentia_jar", p -> new JarItem(jar.get(), p.useBlockDescriptionPrefix()));
        alembicItem =
                item(
                        r,
                        "alembic",
                        p -> new BlockItem(alembic.get(), p.useBlockDescriptionPrefix()));
        tubeItem =
                item(
                        r,
                        "essentia_tube",
                        p -> new BlockItem(tube.get(), p.useBlockDescriptionPrefix()));
        valveItem =
                item(
                        r,
                        "tube_valve",
                        p -> new BlockItem(valve.get(), p.useBlockDescriptionPrefix()));
        label = item(r, "label", LabelItem::new);
        jarType =
                typed(
                        r,
                        Registries.BLOCK_ENTITY_TYPE,
                        "essentia_jar",
                        () -> new BlockEntityType<>(JarBlockEntity::new, jar.get()));
        tubeType =
                typed(
                        r,
                        Registries.BLOCK_ENTITY_TYPE,
                        "essentia_tube",
                        () -> new BlockEntityType<>(LabelledBlockEntity::new, tube.get()));
        alembicType =
                typed(
                        r,
                        Registries.BLOCK_ENTITY_TYPE,
                        "alembic",
                        () -> new BlockEntityType<>(AlembicBlockEntity::new, alembic.get()));
        valveType =
                typed(
                        r,
                        Registries.BLOCK_ENTITY_TYPE,
                        "tube_valve",
                        () -> new BlockEntityType<>(ValveBlockEntity::new, valve.get()));
        jarFill = sound(r, JAR_FILL);
        labelApply = sound(r, LABEL_APPLY);
        labelCut = sound(r, LABEL_CUT);
        valveOpen = sound(r, VALVE_OPEN);
        valveClose = sound(r, VALVE_CLOSE);
        alembicDrip = sound(r, ALEMBIC_DRIP);
    }

    private static <B extends Block> Supplier<B> block(
            Registrar r, String name, Function<BlockBehaviour.Properties, B> factory) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id(name));
        return typed(
                r,
                Registries.BLOCK,
                name,
                () ->
                        factory.apply(
                                BlockBehaviour.Properties.of()
                                        .setId(key)
                                        .noOcclusion()
                                        .pushReaction(PushReaction.BLOCK)));
    }

    private static Supplier<Item> item(
            Registrar r, String name, Function<Item.Properties, Item> factory) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(name));
        return r.register(
                Registries.ITEM, name, () -> factory.apply(new Item.Properties().setId(key)));
    }

    private static Supplier<SoundEvent> sound(Registrar r, Identifier id) {
        return r.register(
                Registries.SOUND_EVENT,
                id.getPath(),
                () -> SoundEvent.createVariableRangeEvent(id));
    }

    /**
     * Registers under a registry whose element type is a supertype (a wildcard type, or {@code
     * Block}) and keeps the concrete value typed.
     */
    private static <T, R extends T> Supplier<R> typed(
            Registrar r,
            ResourceKey<? extends Registry<T>> registry,
            String name,
            Supplier<R> factory) {
        AtomicReference<R> made = new AtomicReference<>();
        Supplier<T> registered =
                r.register(
                        registry,
                        name,
                        () -> {
                            R value = factory.get();
                            made.set(value);
                            return value;
                        });
        return () -> {
            T value = registered.get();
            R typed = made.get();
            if (typed == null || !typed.equals(value)) {
                throw new IllegalStateException(name + " is not registered");
            }
            return typed;
        };
    }

    /** A holder before registration: init has not run. */
    private static final class Unset<T> implements Supplier<T> {
        @Override
        public T get() {
            throw new IllegalStateException("EssentiaFeature.init has not run");
        }
    }

    static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("vftv", path);
    }
}
