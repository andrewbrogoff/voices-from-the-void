package com.andrewbrogoff.vftv.gametest;

import com.andrewbrogoff.vftv.api.neoforge.armor.ArmorDataMaps;
import com.andrewbrogoff.vftv.api.neoforge.aspect.AspectDataMaps;
import com.andrewbrogoff.vftv.api.neoforge.warp.WarpDataMaps;
import com.andrewbrogoff.vftv.core.Vftv;
import com.andrewbrogoff.vftv.core.common.DataSlot;
import com.andrewbrogoff.vftv.core.common.RawRecordCodec;
import com.andrewbrogoff.vftv.core.common.RawRecordCodec.Retained;
import com.andrewbrogoff.vftv.core.common.SavedDataSlot;
import com.andrewbrogoff.vftv.core.common.TickBudget;
import com.andrewbrogoff.vftv.neoforge.common.NeoForgeDataSlots;
import com.andrewbrogoff.vftv.neoforge.common.NeoForgeRegistrar;
import com.andrewbrogoff.vftv.neoforge.config.ServerConfigPublisher;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.SavedDataStorage;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegistryManager;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import org.jspecify.annotations.Nullable;

/**
 * The vftv GameTests: a dev-only mod loaded by {@code runGameTestServer}, never shipped. Every test
 * id here is listed in {@code src/gameTest/expected/common.txt}, and each feature's {@code
 * <F>GameTests} ids in {@code expected/<f>.txt}: together, the discovery guard's set. Every feature
 * is wired here once; a feature ticket edits only its own class and list.
 */
@Mod("vftv_gametest")
public final class VftvGameTests {
    /** A tiny persisted record with a format, standing in for a feature's record. */
    record Sample(int format, int count, String label) {
        static final int FORMAT = 1;

        static final Codec<Sample> BOUNDED =
                RecordCodecBuilder.<Sample>create(
                                i ->
                                        i.group(
                                                        Codec.INT
                                                                .fieldOf("format")
                                                                .forGetter(Sample::format),
                                                        Codec.INT
                                                                .fieldOf("count")
                                                                .forGetter(Sample::count),
                                                        Codec.STRING
                                                                .fieldOf("label")
                                                                .forGetter(Sample::label))
                                                .apply(i, Sample::new))
                        .validate(
                                s ->
                                        s.format() == FORMAT
                                                ? DataResult.success(s)
                                                : DataResult.error(
                                                        () -> "unknown format " + s.format()));
    }

    private static final Codec<Retained<Sample>> RETAINED = RawRecordCodec.wrap(Sample.BOUNDED);
    private static final Retained<Sample> EMPTY =
            new Retained.Read<>(new Sample(Sample.FORMAT, 0, ""));
    private static final String SLOT_NAME = "gametest_sample";

    private static final SavedDataSlot<Retained<Sample>> LEVEL_SLOT =
            new SavedDataSlot<>(id(SLOT_NAME), RETAINED, () -> EMPTY);

    private final Map<String, Consumer<GameTestHelper>> tests = new LinkedHashMap<>();
    private final DataSlot<Player, Retained<Sample>> playerSlot;

    /** Registers the test functions, the sample slot and the test instances. */
    public VftvGameTests(IEventBus modBus) {
        NeoForgeRegistrar registrar = new NeoForgeRegistrar(Vftv.MOD_ID, modBus);
        playerSlot =
                new NeoForgeDataSlots(registrar).player(SLOT_NAME, RETAINED, () -> EMPTY, null);

        tests.put("player_slot_round_trip", this::playerSlotRoundTrip);
        tests.put("player_slot_keeps_unreadable", this::playerSlotKeepsUnreadable);
        tests.put("level_slot_round_trip", VftvGameTests::levelSlotRoundTrip);
        tests.put("command_root_permission", VftvGameTests::commandRootPermission);
        tests.put("tick_budget_records", VftvGameTests::tickBudgetRecords);
        tests.put("server_config_publish", VftvGameTests::serverConfigPublish);
        tests.put("api_data_maps_registered", VftvGameTests::apiDataMapsRegistered);
        TextGameTests.register(tests, modBus);
        FluxGameTests.register(tests, modBus);
        AspectGameTests.register(tests, modBus);
        WarpGameTests.register(tests, modBus);
        ResearchGameTests.register(tests, modBus);
        VisGameTests.register(tests, modBus);
        ArcaneGameTests.register(tests, modBus);
        CrucibleGameTests.register(tests, modBus);
        EssentiaGameTests.register(tests, modBus);
        InfusionGameTests.register(tests, modBus);
        FocusGameTests.register(tests, modBus);
        GolemGameTests.register(tests, modBus);
        ArmorGameTests.register(tests, modBus);
        HushGameTests.register(tests, modBus);
        WorldgenGameTests.register(tests, modBus);
        RoadGameTests.register(tests, modBus);
        tests.forEach(
                (name, test) -> registrar.register(Registries.TEST_FUNCTION, name, () -> test));
        modBus.addListener(RegisterGameTestsEvent.class, this::registerInstances);
    }

    private void registerInstances(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(id("default"));
        tests.keySet()
                .forEach(
                        name ->
                                event.registerTest(
                                        id(name),
                                        new FunctionGameTestInstance(
                                                ResourceKey.create(
                                                        Registries.TEST_FUNCTION, id(name)),
                                                new TestData<>(
                                                        environment,
                                                        Identifier.withDefaultNamespace("empty"),
                                                        100,
                                                        0,
                                                        true))));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Vftv.MOD_ID, path);
    }

    /** A player slot survives a real entity save and load, through the attachment's codec. */
    private void playerSlotRoundTrip(GameTestHelper helper) {
        Player player = helper.makeMockServerPlayer(GameType.SURVIVAL);
        Retained<Sample> value = new Retained.Read<>(new Sample(Sample.FORMAT, 7, "seven"));
        playerSlot.set(player, value);

        Player reloaded = helper.makeMockServerPlayer(GameType.SURVIVAL);
        reloaded.load(
                TagValueInput.create(
                        ProblemReporter.DISCARDING, player.registryAccess(), save(player)));

        helper.assertValueEqual(playerSlot.get(reloaded), value, "the reloaded slot");
        helper.succeed();
    }

    /** An unreadable record decodes as unreadable and is written back byte-identical. */
    private void playerSlotKeepsUnreadable(GameTestHelper helper) {
        Player player = helper.makeMockServerPlayer(GameType.SURVIVAL);
        playerSlot.set(player, new Retained.Read<>(new Sample(Sample.FORMAT, 3, "three")));
        CompoundTag saved = save(player);
        CompoundTag record =
                saved.getCompoundOrEmpty("neoforge:attachments")
                        .getCompoundOrEmpty(id(SLOT_NAME).toString())
                        .getCompoundOrEmpty("data");
        record.putInt("format", 99);
        record.putString("from_the_future", "kept");

        Player reloaded = helper.makeMockServerPlayer(GameType.SURVIVAL);
        reloaded.load(
                TagValueInput.create(ProblemReporter.DISCARDING, player.registryAccess(), saved));
        helper.assertTrue(
                playerSlot.get(reloaded) instanceof Retained.Unreadable<Sample>,
                "a future format is unreadable");

        CompoundTag resaved = save(reloaded);
        helper.assertValueEqual(
                resaved.getCompoundOrEmpty("neoforge:attachments"),
                saved.getCompoundOrEmpty("neoforge:attachments"),
                "the attachments written back");
        helper.succeed();
    }

    /** A level slot is marked dirty on write and survives a save and a fresh storage's load. */
    private static void levelSlotRoundTrip(GameTestHelper helper) {
        // Inside the test world, which the next run's universe reset removes.
        Path dir =
                helper.getLevel()
                        .getServer()
                        .getWorldPath(LevelResource.ROOT)
                        .resolve("vftv_gametest_level_slot");
        Retained<Sample> value = new Retained.Read<>(new Sample(Sample.FORMAT, 12, "twelve"));
        try (SavedDataStorage first = storage(helper, dir)) {
            LEVEL_SLOT.write(first, value);
        }
        try (SavedDataStorage second = storage(helper, dir)) {
            helper.assertValueEqual(LEVEL_SLOT.read(second), value, "the reloaded level slot");
        }
        helper.succeed();
    }

    /** {@code /vftv} exists and is visible from permission level 2, not from 0 or 1. */
    private static void commandRootPermission(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        CommandNode<CommandSourceStack> root =
                server.getCommands().getDispatcher().getRoot().getChild(Vftv.MOD_ID);
        helper.assertTrue(root != null, "/vftv is registered");
        if (root == null) {
            return;
        }
        CommandSourceStack source = server.createCommandSourceStack();
        helper.assertTrue(
                !root.canUse(source.withPermission(PermissionSet.NO_PERMISSIONS)),
                "level 0 cannot use it");
        helper.assertTrue(
                !root.canUse(source.withPermission(LevelBasedPermissionSet.MODERATOR)),
                "level 1 cannot use it");
        helper.assertTrue(
                root.canUse(source.withPermission(LevelBasedPermissionSet.GAMEMASTER)),
                "level 2 can");
        helper.succeed();
    }

    /** The tick listener closes ticks, and a section entered on the server thread is timed. */
    private static void tickBudgetRecords(GameTestHelper helper) {
        helper.assertTrue(TickBudget.ENABLED, "the run enables the tick budget");
        long before = ticks(TickBudget.TOTAL);
        try (var _ = TickBudget.enter("gametest")) {
            try (var _ = TickBudget.enter("gametest_nested")) {
                long until = System.nanoTime() + 2_000_000;
                while (System.nanoTime() < until) {
                    Thread.onSpinWait();
                }
            }
        }
        helper.runAfterDelay(
                3,
                () -> {
                    helper.assertTrue(ticks(TickBudget.TOTAL) >= before + 2, "ticks were closed");
                    TickBudget.Summary section = summary("gametest");
                    helper.assertTrue(
                            section != null && section.max() >= 2_000_000, "the section was timed");
                    helper.assertTrue(
                            summary("gametest_nested") == null,
                            "a nested entry counts to the outermost");
                    helper.succeed();
                });
    }

    /**
     * A publish on the server thread applies inline and re-sends only a changed record; one from
     * another thread is queued to the server thread; a stop resets to the default.
     */
    private static void serverConfigPublish(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        AtomicReference<String> spec = new AtomicReference<>("on");
        AtomicReference<String> installed = new AtomicReference<>("default");
        AtomicInteger resends = new AtomicInteger();
        ServerConfigPublisher publisher =
                new ServerConfigPublisher(
                        new ModConfigSpec.Builder().build(),
                        List.of(
                                new ServerConfigPublisher.ServerRecord<>(
                                        spec::get,
                                        installed::get,
                                        installed::set,
                                        "default",
                                        s -> resends.incrementAndGet())));

        publisher.publish(null);
        helper.assertValueEqual(installed.get(), "default", "no server publishes nothing");
        publisher.publish(server);
        helper.assertValueEqual(installed.get(), "on", "inline on the server thread");
        helper.assertValueEqual(resends.get(), 1, "a changed record re-sends");
        publisher.publish(server);
        helper.assertValueEqual(resends.get(), 1, "an unchanged record sends nothing");

        spec.set("off");
        Thread other = new Thread(() -> publisher.publish(server), "vftv-config-test");
        other.start();
        join(other);
        helper.assertValueEqual(installed.get(), "on", "off-thread publish is queued");
        helper.runAfterDelay(
                2,
                () -> {
                    helper.assertValueEqual(installed.get(), "off", "the queued publish applied");
                    helper.assertValueEqual(resends.get(), 2, "and re-sent once");
                    publisher.reset();
                    helper.assertValueEqual(installed.get(), "default", "a stop resets the record");
                    helper.succeed();
                });
    }

    /**
     * Every API data-map type is registered under its id with a network codec (API spec, "Data maps
     * and other data": all four maps are synced).
     */
    private static void apiDataMapsRegistered(GameTestHelper helper) {
        for (DataMapType<?, ?> type :
                List.of(
                        AspectDataMaps.ITEM,
                        AspectDataMaps.BLOCK,
                        AspectDataMaps.ENTITY_TYPE,
                        AspectDataMaps.DEFAULTS,
                        WarpDataMaps.GEAR_WARP,
                        ArmorDataMaps.WEARABLE)) {
            helper.assertTrue(
                    RegistryManager.getDataMap(type.registryKey(), type.id()) == type,
                    "registered: " + type.id() + " on " + type.registryKey().identifier());
            helper.assertTrue(type.networkCodec() != null, type.id() + " is synced");
        }
        helper.succeed();
    }

    private static CompoundTag save(Player player) {
        TagValueOutput out =
                TagValueOutput.createWithContext(
                        ProblemReporter.DISCARDING, player.registryAccess());
        player.saveWithoutId(out);
        return out.buildResult();
    }

    private static SavedDataStorage storage(GameTestHelper helper, Path dir) {
        MinecraftServer server = helper.getLevel().getServer();
        return new SavedDataStorage(
                helper.getLevel(), dir, server.getFixerUpper(), server.registryAccess());
    }

    private static long ticks(String name) {
        TickBudget.Summary s = summary(name);
        return s == null ? 0 : s.ticks();
    }

    private static TickBudget.@Nullable Summary summary(String name) {
        return TickBudget.snapshot().stream()
                .filter(s -> s.section().equals(name))
                .findFirst()
                .orElse(null);
    }

    private static void join(Thread thread) {
        try {
            thread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
