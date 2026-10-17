package com.andrewbrogoff.vftv.gametest;

import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.api.aspect.AspectValues;
import com.andrewbrogoff.vftv.api.neoforge.aspect.AspectDataMaps;
import com.andrewbrogoff.vftv.core.Vftv;
import com.andrewbrogoff.vftv.core.aspect.AspectConfig;
import com.andrewbrogoff.vftv.core.aspect.AspectLookup;
import com.andrewbrogoff.vftv.core.aspect.DerivationExport;
import com.andrewbrogoff.vftv.core.aspect.DerivedAspects;
import com.andrewbrogoff.vftv.core.aspect.DerivedAspectsSync;
import com.mojang.brigadier.tree.CommandNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jspecify.annotations.Nullable;

/**
 * The derivation at load and the export (aspects spec, "Derived values at load"; compatibility,
 * "The derivation at load", "The derivation export"), over this mod's own items and recipes: its
 * namespace is neither {@code minecraft} nor {@code vftv}, so it stands in for a modded one.
 *
 * <p>Fixtures: {@code derived_widget} and {@code authored_widget} and {@code suppressed_widget},
 * each crafted from two oak logs (Arbor 4 each); the authored one has {Ordo 2} and the suppressed
 * one {}; {@code cycle_a} and {@code cycle_b} craft into each other, and {@code downstream_widget}
 * is crafted from {@code cycle_a}.
 */
final class AspectDerivationGameTests {
    private static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems("vftv_gametest");
    private static final DeferredItem<Item> DERIVED = ITEMS.registerSimpleItem("derived_widget");
    private static final DeferredItem<Item> AUTHORED = ITEMS.registerSimpleItem("authored_widget");
    private static final DeferredItem<Item> SUPPRESSED =
            ITEMS.registerSimpleItem("suppressed_widget");
    private static final DeferredItem<Item> CYCLE_A = ITEMS.registerSimpleItem("cycle_a");
    private static final DeferredItem<Item> CYCLE_B = ITEMS.registerSimpleItem("cycle_b");
    private static final DeferredItem<Item> DOWNSTREAM =
            ITEMS.registerSimpleItem("downstream_widget");

    /** {@code derived_widget}'s value: two logs of Arbor 4 at the factor 0.75. */
    private static final AspectValues DERIVED_VALUE =
            new AspectValues(new TreeMap<>(Map.of(AspectKeys.ARBOR, 6)));

    private static final String PACK = "vftv_derived_vftv_gametest";

    private AspectDerivationGameTests() {}

    static void register(Map<String, Consumer<GameTestHelper>> tests, IEventBus modBus) {
        ITEMS.register(modBus);
        tests.put("aspect_derived_at_load", AspectDerivationGameTests::derivedAtLoad);
        tests.put("aspect_derive_modded_switch", AspectDerivationGameTests::deriveModdedSwitch);
        tests.put("aspect_derived_sync", AspectDerivationGameTests::derivedSync);
        tests.put("aspect_derive_permission", AspectDerivationGameTests::derivePermission);
        tests.put("aspect_derive_round_trip", AspectDerivationGameTests::deriveRoundTrip);
    }

    /**
     * At server start the modded item with neither an entry nor a default reads its derived value;
     * an authored entry and an authored {} win; a cycle is excluded and what it feeds is
     * unresolved; vanilla's own items are never derived over at load.
     */
    private static void derivedAtLoad(GameTestHelper helper) {
        Map<Item, AspectValues> table = AspectLookup.derived();
        same(helper, table.get(DERIVED.get()), DERIVED_VALUE, "the derived value");
        same(helper, AspectLookup.item(DERIVED.get()), DERIVED_VALUE, "the lookup");
        same(
                helper,
                AspectLookup.item(AUTHORED.get()),
                new AspectValues(new TreeMap<>(Map.of(AspectKeys.ORDO, 2))),
                "an authored entry wins");
        same(
                helper,
                AspectLookup.item(SUPPRESSED.get()),
                new AspectValues(new TreeMap<>()),
                "an authored {} answers nothing");
        for (Item never :
                List.of(
                        AUTHORED.get(),
                        SUPPRESSED.get(),
                        CYCLE_A.get(),
                        CYCLE_B.get(),
                        DOWNSTREAM.get())) {
            helper.assertFalse(table.containsKey(never), never + " is not derived");
        }
        helper.assertFalse(table.containsKey(Items.TORCH), "vanilla is datagen's, not the table's");
        helper.assertTrue(
                AspectLookup.item(Items.TORCH) != null, "the torch has its generated value");
        helper.succeed();
    }

    /**
     * {@code aspects.derive_modded} off empties the table at the next data load; on rebuilds it.
     */
    private static void deriveModdedSwitch(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        AspectConfig before = DerivedAspects.config();
        try {
            DerivedAspects.installConfig(new AspectConfig(false));
            load(server);
            helper.assertTrue(AspectLookup.derived().isEmpty(), "off: the table is empty");
            same(helper, AspectLookup.item(DERIVED.get()), null, "off: no value");
            same(
                    helper,
                    AspectLookup.item(AUTHORED.get()),
                    new AspectValues(new TreeMap<>(Map.of(AspectKeys.ORDO, 2))),
                    "an authored entry stays");
            DerivedAspects.serverStarted();
            helper.assertTrue(AspectLookup.derived().isEmpty(), "start with it off: empty");
            DerivedAspects.installConfig(new AspectConfig(true));
            load(server);
            same(helper, AspectLookup.item(DERIVED.get()), DERIVED_VALUE, "on again: rebuilt");
        } finally {
            DerivedAspects.installConfig(before);
            load(server);
        }
        helper.succeed();
    }

    /** What a data load does: rebuild from the live recipes. */
    private static void load(MinecraftServer server) {
        DerivedAspects.rebuild(server.getRecipeManager().getRecipes(), server.registryAccess());
    }

    /** A player is sent the whole table, ascending, the last payload flagged. */
    private static void derivedSync(GameTestHelper helper) {
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        List<DerivedAspectsSync> sent = new ArrayList<>();
        var previous = DerivedAspects.swapSender((p, payload) -> sent.add(payload));
        try {
            DerivedAspects.sendTo(player);
        } finally {
            DerivedAspects.swapSender(previous);
        }
        helper.assertFalse(sent.isEmpty(), "something was sent");
        helper.assertTrue(sent.getLast().last(), "the last payload is flagged");
        Map<Item, AspectValues> received = new java.util.LinkedHashMap<>();
        sent.forEach(p -> p.entries().forEach(e -> received.put(e.item(), e.values())));
        same(helper, received, AspectLookup.derived(), "the whole table");
        helper.succeed();
    }

    /** A level-2 source can neither run nor see {@code aspects derive}; level 4 can. */
    private static void derivePermission(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        CommandNode<CommandSourceStack> root =
                server.getCommands().getDispatcher().getRoot().getChild(Vftv.MOD_ID);
        CommandNode<CommandSourceStack> aspects = root == null ? null : root.getChild("aspects");
        helper.assertTrue(aspects != null, "/vftv aspects is registered");
        if (aspects == null) {
            return;
        }
        CommandSourceStack source = server.createCommandSourceStack();
        helper.assertFalse(
                aspects.canUse(source.withPermission(LevelBasedPermissionSet.GAMEMASTER)),
                "level 2 can neither see nor run it");
        helper.assertTrue(
                aspects.canUse(source.withPermission(LevelBasedPermissionSet.OWNER)),
                "level 4 can");
        helper.assertTrue(aspects.getChild("derive") != null, "the derive leaf");
        helper.succeed();
    }

    /**
     * The round trip: export, install, {@code /reload}, read back through {@code
     * AspectDataMaps.ITEM}, refuse while installed, disable, {@code /reload}, export again with
     * byte-identical files. The export's files equal the loaded table entry for entry.
     */
    private static void deriveRoundTrip(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Path derived = server.getWorldPath(LevelResource.ROOT).resolve("vftv/derived").normalize();
        Path installed = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve(PACK);
        String id = "file/" + PACK;
        try {
            var first = DerivationExport.run(server);
            helper.assertTrue(first instanceof DerivationExport.Outcome.Done, "exported: " + first);
            SortedMap<String, String> files = read(derived);
            String map = files.get(PACK + "/data/vftv/data_maps/item/aspects.json");
            helper.assertTrue(map != null, "a pack for the vftv_gametest namespace");
            if (map == null) {
                return;
            }
            helper.assertTrue(
                    map.contains("\"modid\": \"vftv_gametest\""), "its mod_loaded condition");
            helper.assertTrue(map.contains("\"vftv_gametest:derived_widget\""), "the derived item");
            for (String never :
                    List.of(
                            "authored_widget",
                            "suppressed_widget",
                            "cycle_a",
                            "cycle_b",
                            "downstream_widget")) {
                helper.assertFalse(
                        map.contains("vftv_gametest:" + never + "\""), never + " is not emitted");
            }
            String report = files.get("report.txt");
            helper.assertTrue(
                    report != null
                            && report.contains(
                                    "vftv_gametest:cycle_a [vftv_gametest:cycle_a, vftv_gametest:cycle_b]")
                            && report.contains(
                                    "vftv_gametest:downstream_widget blocked by vftv_gametest:cycle_a (excluded)"),
                    "the report lists the cycle and what it blocks");
            same(helper, emitted(files), AspectLookup.derived().size(), "entry for entry");

            var second = DerivationExport.run(server);
            helper.assertTrue(second instanceof DerivationExport.Outcome.Done, "exported again");
            same(helper, read(derived), files, "byte-identical");

            copy(derived.resolve(PACK), installed);
            server.getPackRepository().reload();
            List<String> enabled = new ArrayList<>(server.getPackRepository().getSelectedIds());
            enabled.add(id);
            server.reloadResources(enabled).join();
            same(
                    helper,
                    BuiltInRegistries.ITEM.wrapAsHolder(DERIVED.get()).getData(AspectDataMaps.ITEM),
                    DERIVED_VALUE,
                    "installed: the value reads back as an authored entry");
            helper.assertFalse(
                    AspectLookup.derived().containsKey(DERIVED.get()),
                    "installed: resolved, not derived");
            same(
                    helper,
                    DerivationExport.run(server),
                    new DerivationExport.Outcome.Installed(List.of(id)),
                    "refused while installed");

            enabled.remove(id);
            server.reloadResources(enabled).join();
            same(
                    helper,
                    AspectLookup.derived().get(DERIVED.get()),
                    DERIVED_VALUE,
                    "disabled: derived again");
            DerivationExport.run(server);
            same(helper, read(derived), files, "a full rebuild gives the same files");
        } finally {
            delete(installed);
        }
        helper.succeed();
    }

    /** Asserts equality, nulls included. */
    private static void same(
            GameTestHelper helper,
            @Nullable Object actual,
            @Nullable Object expected,
            String what) {
        helper.assertTrue(
                Objects.equals(actual, expected), what + ": " + actual + ", expected " + expected);
    }

    private static int emitted(SortedMap<String, String> files) {
        int n = 0;
        for (Map.Entry<String, String> f : files.entrySet()) {
            if (f.getKey().endsWith("aspects.json")) {
                n +=
                        (int)
                                f.getValue()
                                        .lines()
                                        .filter(l -> l.matches("    \"[^\"]+\": \\{"))
                                        .count();
            }
        }
        return n;
    }

    private static SortedMap<String, String> read(Path dir) {
        SortedMap<String, String> out = new TreeMap<>();
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : walk.filter(Files::isRegularFile).toList()) {
                out.put(
                        dir.relativize(p).toString().replace('\\', '/'),
                        Files.readString(p, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }

    private static void copy(Path from, Path to) {
        read(from)
                .forEach(
                        (rel, text) -> {
                            try {
                                DerivationExport.DISK.write(to.resolve(rel), text);
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        });
    }

    private static void delete(Path tree) {
        if (!Files.exists(tree)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(tree)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(p);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
