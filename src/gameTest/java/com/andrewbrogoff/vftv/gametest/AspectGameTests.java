package com.andrewbrogoff.vftv.gametest;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.aspect.AspectDiscoveredPayload;
import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.api.aspect.AspectValues;
import com.andrewbrogoff.vftv.api.aspect.ScanKey;
import com.andrewbrogoff.vftv.api.aspect.ScanOutcome;
import com.andrewbrogoff.vftv.api.aspect.ThingScannedPayload;
import com.andrewbrogoff.vftv.api.neoforge.aspect.AspectDiscoveredEvent;
import com.andrewbrogoff.vftv.api.neoforge.aspect.ThingScannedEvent;
import com.andrewbrogoff.vftv.core.aspect.AspectContent;
import com.andrewbrogoff.vftv.core.aspect.AspectEntry;
import com.andrewbrogoff.vftv.core.aspect.AspectLookup;
import com.andrewbrogoff.vftv.core.aspect.AspectSet;
import com.andrewbrogoff.vftv.core.aspect.KnowledgeService;
import com.andrewbrogoff.vftv.core.aspect.NodeSource;
import com.andrewbrogoff.vftv.core.aspect.ScanResult;
import com.andrewbrogoff.vftv.core.aspect.ScanningService;
import com.andrewbrogoff.vftv.core.aspect.Strikes;
import com.andrewbrogoff.vftv.core.aspect.model.AspectGraph;
import com.andrewbrogoff.vftv.core.aspect.model.Knowledge;
import com.andrewbrogoff.vftv.core.aspect.model.ScanningTuning;
import com.andrewbrogoff.vftv.core.aspect.model.Sounding;
import com.andrewbrogoff.vftv.core.common.RawRecordCodec.Retained;
import com.andrewbrogoff.vftv.core.text.StatusLines;
import com.andrewbrogoff.vftv.core.text.model.SharedMessages;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.Filterable;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.Nullable;

/**
 * The aspect feature's GameTests. Name each test {@code aspect_<what>}, and list its id {@code
 * vftv:aspect_<what>} in {@code src/gameTest/expected/aspect.txt}.
 *
 * <p>Fixtures (this mod's data, so they override the shipped table): items stone {Terra 2},
 * cobblestone {} (nothing to learn), obsidian {Tenebrae 3, Terra 2} (partly heard), iron ingot
 * {Metallum 4}, glass {Vitreus 1, Terra 1}; the {@code #c:ingots} default {Metallum 1}; blocks
 * glass {Vitreus 2} and water {Aqua 3}; cow {Aer 1, Terra 1}; and the strike recipe paper → map.
 */
final class AspectGameTests {
    private static final Map<UUID, List<ScanResult>> RESULTS = new ConcurrentHashMap<>();
    private static final Map<UUID, List<Object>> EVENTS = new ConcurrentHashMap<>();

    private AspectGameTests() {}

    /**
     * Adds the feature's tests by name; {@code modBus} is for any test-only registration they need.
     */
    static void register(Map<String, Consumer<GameTestHelper>> tests, IEventBus modBus) {
        tests.put("aspect_registry_and_maps_load", AspectGameTests::registryAndMapsLoad);
        tests.put("aspect_bad_entries_rejected", AspectGameTests::badEntriesRejected);
        tests.put("aspect_lookup_order", AspectGameTests::lookupOrder);
        tests.put("aspect_strike_block", AspectGameTests::strikeBlock);
        tests.put("aspect_strike_entity_and_drop", AspectGameTests::strikeEntityAndDrop);
        tests.put("aspect_chest_stays_shut", AspectGameTests::chestStaysShut);
        tests.put("aspect_inventory_strike", AspectGameTests::inventoryStrike);
        tests.put("aspect_partial_then_complete", AspectGameTests::partialThenComplete);
        tests.put("aspect_strike_conversion", AspectGameTests::strikeConversion);
        tests.put("aspect_knowledge_persists", AspectGameTests::knowledgePersists);
        tests.put("aspect_two_player_privacy", AspectGameTests::twoPlayerPrivacy);
        tests.put("aspect_phantom_is_silent", AspectGameTests::phantomIsSilent);
        tests.put("aspect_sounding_and_nodes", AspectGameTests::soundingAndNodes);
        tests.put("aspect_credit_never_teaches", AspectGameTests::creditNeverTeaches);
        tests.put(
                "aspect_unreadable_warns_per_session", AspectGameTests::unreadableWarnsPerSession);
        // Record what each mock player would be sent, and which events name them.
        Strikes.swapSender(
                (player, result) ->
                        RESULTS.computeIfAbsent(player.getUUID(), k -> new ArrayList<>())
                                .add(result));
        NeoForge.EVENT_BUS.addListener(
                ThingScannedEvent.class, e -> record(e.payload().player().getUUID(), e.payload()));
        NeoForge.EVENT_BUS.addListener(
                AspectDiscoveredEvent.class,
                e -> record(e.payload().player().getUUID(), e.payload()));
    }

    private static void record(UUID player, Object payload) {
        EVENTS.computeIfAbsent(player, k -> new ArrayList<>()).add(payload);
    }

    // --- fixtures -----------------------------------------------------------------------------

    /** A mock player with a connection that negotiated nothing, never joined, holding a fork. */
    private static ServerPlayer player(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        new ServerGamePacketListenerImpl(
                server,
                connection,
                player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false));
        Vec3 at = helper.absoluteVec(new Vec3(0.5, 1, 0.5));
        player.snapTo(at.x, at.y, at.z, 0, 0);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AspectContent.tuningFork()));
        return player;
    }

    private static ItemStack fork(ServerPlayer player) {
        return player.getMainHandItem();
    }

    /** Ends the fork's cooldown, so a test can strike again in the same tick. */
    private static void cool(ServerPlayer player) {
        player.getCooldowns()
                .removeCooldown(
                        player.getCooldowns()
                                .getCooldownGroup(new ItemStack(AspectContent.tuningFork())));
    }

    private static List<ScanResult> results(ServerPlayer player) {
        return RESULTS.getOrDefault(player.getUUID(), List.of());
    }

    private static @Nullable ScanResult last(ServerPlayer player) {
        List<ScanResult> r = results(player);
        return r.isEmpty() ? null : r.getLast();
    }

    private static List<Object> events(ServerPlayer player) {
        return EVENTS.getOrDefault(player.getUUID(), List.of());
    }

    private static List<ThingScannedPayload> scans(ServerPlayer player) {
        return events(player).stream()
                .filter(ThingScannedPayload.class::isInstance)
                .map(ThingScannedPayload.class::cast)
                .toList();
    }

    private static Knowledge knowledge(ServerPlayer player) {
        return Objects.requireNonNull(KnowledgeService.get(player), "readable knowledge");
    }

    private static String id(net.minecraft.resources.ResourceKey<Aspect> key) {
        return AspectSet.text(key);
    }

    /** A right-click on a block through the server's own path, as the packet handler calls it. */
    private static void useOn(GameTestHelper helper, ServerPlayer player, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        player.gameMode.useItemOn(
                player,
                helper.getLevel(),
                fork(player),
                InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }

    // --- tests --------------------------------------------------------------------------------

    /**
     * The registry holds the 37 built-ins with a valid graph, the maps hold the shipped fork value,
     * and the tuning in force is the shipped file's defaults.
     */
    private static void registryAndMapsLoad(GameTestHelper helper) {
        Registry<Aspect> registry =
                helper.getLevel().registryAccess().lookupOrThrow(AspectKeys.REGISTRY);
        helper.assertValueEqual(registry.size() >= 37, true, "37 built-in aspects");
        helper.assertTrue(
                AspectSet.of(registry).validate().stream().noneMatch(AspectGraph.Problem::fatal),
                "the loaded graph is valid");
        helper.assertTrue(
                registry.getValue(AspectKeys.VACUOS) instanceof AspectEntry e
                        && e.hiddenPrimal() != null
                        && e.icon().toString().equals("vftv:aspect/vacuos"),
                "Vacuos is a hidden primal with its default icon bound");
        AspectValues fork = AspectLookup.item(AspectContent.tuningFork());
        helper.assertTrue(
                fork != null && fork.aspects().getOrDefault(AspectKeys.SONUS, 0) == 2,
                "the fork's value");
        helper.assertValueEqual(
                ScanningService.tuning(), ScanningTuning.DEFAULTS, "the scanning tuning");
        helper.succeed();
    }

    /**
     * Broken entries fail with a clear error: the entry codec, the graph, the value, the tuning.
     */
    private static void badEntriesRejected(GameTestHelper helper) {
        for (String entry :
                List.of(
                        "{\"color\": 1, \"parents\": [\"vftv:aer\"]}",
                        "{\"color\": 1, \"parents\": [\"vftv:aer\", \"vftv:aer\"]}")) {
            helper.assertTrue(
                    AspectEntry.ELEMENT_CODEC
                            .parse(JsonOps.INSTANCE, JsonParser.parseString(entry))
                            .isError(),
                    "rejected: " + entry);
        }
        Map<String, AspectGraph.Node> graph = new java.util.HashMap<>();
        graph.put("vftv:x", AspectGraph.Node.of("vftv:aer", "vftv:y"));
        graph.put("vftv:y", AspectGraph.Node.of("vftv:aer", "vftv:x"));
        graph.put("vftv:z", AspectGraph.Node.of("vftv:aer", "vftv:ghost"));
        graph.put("vftv:aer", AspectGraph.Node.of());
        List<String> problems =
                AspectGraph.validate(graph, List.of("vftv:aer")).stream()
                        .map(AspectGraph.Problem::message)
                        .toList();
        helper.assertTrue(
                problems.stream().anyMatch(p -> p.contains("unknown aspect vftv:ghost")),
                "an unknown parent is named: " + problems);
        graph.remove("vftv:z");
        helper.assertTrue(
                AspectGraph.validate(graph, List.of()).stream()
                        .anyMatch(p -> p.fatal() && p.message().contains("cycle")),
                "a cycle refuses the world");
        for (String value :
                List.of(
                        "{\"vftv:aer\": 0}",
                        "{\"vftv:aer\": -2}",
                        "{\"a:a\": 1, \"a:b\": 1, \"a:c\": 1, \"a:d\": 1, \"a:e\": 1, \"a:f\": 1}")) {
            helper.assertTrue(
                    AspectValues.CODEC
                            .parse(JsonOps.INSTANCE, JsonParser.parseString(value))
                            .isError(),
                    "rejected value: " + value);
        }
        helper.assertTrue(
                ScanningService.parse("{\"sounding.radius\": 257}").isError(),
                "a radius of 257 is refused");
        ScanningTuning before = ScanningService.tuning();
        ScanningService.applyTuning(ScanningService.parse("{\"sounding.near\": 99}"), true);
        helper.assertValueEqual(ScanningService.tuning(), before, "a /reload keeps the previous");
        helper.succeed();
    }

    /** Block entry over item entry over defaults; {} answers nothing; an itemless block reads. */
    private static void lookupOrder(GameTestHelper helper) {
        helper.assertValueEqual(
                vitreus(AspectLookup.block(Blocks.GLASS.defaultBlockState())), 2, "block entry");
        helper.assertValueEqual(vitreus(AspectLookup.item(Items.GLASS)), 1, "item entry");
        helper.assertValueEqual(
                AspectLookup.key(Blocks.GLASS.defaultBlockState()).toString(),
                "block:minecraft:glass",
                "a block with its own entry keys as itself");
        helper.assertValueEqual(
                AspectLookup.key(Blocks.STONE.defaultBlockState()).toString(),
                "item:minecraft:stone",
                "a placed block with an item form keys as its item");
        helper.assertValueEqual(
                metallum(AspectLookup.item(Items.IRON_INGOT)), 4, "an entry beats the default");
        helper.assertValueEqual(
                metallum(AspectLookup.item(Items.GOLD_INGOT)), 1, "the #c:ingots default");
        AspectValues nothing = AspectLookup.item(Items.COBBLESTONE);
        helper.assertTrue(nothing != null && nothing.aspects().isEmpty(), "{} is an entry");
        helper.assertTrue(AspectLookup.item(Items.BEDROCK) == null, "no entry is null");
        AspectValues water = AspectLookup.block(Blocks.WATER.defaultBlockState());
        helper.assertTrue(
                water != null && water.aspects().getOrDefault(AspectKeys.AQUA, 0) == 3,
                "itemless water");
        helper.succeed();
    }

    private static int vitreus(@Nullable AspectValues v) {
        return v == null ? -1 : v.aspects().getOrDefault(AspectKeys.VITREUS, -1);
    }

    private static int metallum(@Nullable AspectValues v) {
        return v == null ? -1 : v.aspects().getOrDefault(AspectKeys.METALLUM, -1);
    }

    /**
     * A block strike scans the block as its item, pays and discovers Terra, rings (cooldown on) and
     * posts FIRST; a click during the cooldown does nothing; nothing to learn reads so and posts
     * nothing.
     */
    private static void strikeBlock(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.STONE);
        helper.setBlock(new BlockPos(2, 1, 1), Blocks.COBBLESTONE);
        useOn(helper, player, new BlockPos(1, 1, 1));
        ScanResult r = Objects.requireNonNull(last(player), "a readout");
        helper.assertValueEqual(r.state(), ScanResult.State.SCANNED, "scanned");
        helper.assertValueEqual(
                r.gained().getOrDefault(AspectKeys.TERRA, 0), 2 + 4, "points and the bonus");
        helper.assertTrue(player.getCooldowns().isOnCooldown(fork(player)), "the fork rings");
        helper.assertTrue(
                knowledge(player).scans().containsKey("item:minecraft:stone"), "the key is noted");
        helper.assertValueEqual(scans(player).size(), 1, "one scan event");
        helper.assertValueEqual(scans(player).getFirst().outcome(), ScanOutcome.FIRST, "FIRST");
        helper.assertTrue(
                events(player).stream().anyMatch(AspectDiscoveredPayload.class::isInstance),
                "Terra's discovery is posted");

        useOn(helper, player, new BlockPos(2, 1, 1));
        helper.assertValueEqual(results(player).size(), 1, "a click during the cooldown is spent");
        cool(player);
        useOn(helper, player, new BlockPos(2, 1, 1));
        helper.assertValueEqual(
                Objects.requireNonNull(last(player)).state(),
                ScanResult.State.NOTHING,
                "Nothing answers.");
        helper.assertValueEqual(scans(player).size(), 1, "nothing to learn posts no event");
        helper.succeed();
    }

    /** An entity strike reads its type; a dropped item reads as the item it carries. */
    private static void strikeEntityAndDrop(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        Cow cow = helper.spawn(EntityTypes.COW, new BlockPos(1, 1, 1));
        player.interactOn(cow, InteractionHand.MAIN_HAND, Vec3.ZERO);
        helper.assertTrue(
                knowledge(player).scans().containsKey("entity:minecraft:cow"), "the cow is noted");
        cool(player);
        ItemEntity drop = helper.spawnItem(Items.STONE, new BlockPos(2, 1, 1));
        com.andrewbrogoff.vftv.core.aspect.Strikes.strikeEntity(player, fork(player), drop);
        helper.assertTrue(
                knowledge(player).scans().containsKey("item:minecraft:stone"),
                "the drop is noted as its item");
        helper.assertValueEqual(
                scans(player).getLast().kind(),
                com.andrewbrogoff.vftv.api.aspect.ScanTargetKind.ENTITY,
                "an entity strike");
        helper.succeed();
    }

    /** The fork takes priority over a chest's own use: no menu opens, even during the cooldown. */
    private static void chestStaysShut(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.CHEST);
        useOn(helper, player, new BlockPos(1, 1, 1));
        helper.assertTrue(
                player.containerMenu.equals(player.inventoryMenu), "the chest stays shut");
        useOn(helper, player, new BlockPos(1, 1, 1));
        helper.assertTrue(
                player.containerMenu.equals(player.inventoryMenu), "and stays shut on cooldown");
        helper.assertValueEqual(results(player).size(), 1, "one readout");
        helper.succeed();
    }

    /**
     * With the fork on the cursor, a right-click strikes the slot's stack; the server checks its
     * own cooldown; a left-click and a non-fork cursor do nothing of ours.
     */
    private static void inventoryStrike(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        ItemStack fork = player.getMainHandItem().copy();
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.getInventory().setItem(9, new ItemStack(Items.GLASS));
        var menu = player.inventoryMenu;
        int slot = 9; // the first main-inventory slot of the player's menu
        menu.setCarried(fork);
        menu.clicked(slot, 1, ContainerInput.PICKUP, player);
        helper.assertTrue(
                knowledge(player).scans().containsKey("item:minecraft:glass"), "struck in a slot");
        helper.assertTrue(menu.getCarried().is(AspectContent.tuningFork()), "the fork stays held");
        helper.assertTrue(menu.getSlot(slot).getItem().is(Items.GLASS), "the glass stays put");
        player.getInventory().setItem(10, new ItemStack(Items.STONE));
        menu.clicked(10, 1, ContainerInput.PICKUP, player);
        helper.assertTrue(
                !knowledge(player).scans().containsKey("item:minecraft:stone"),
                "the server's own cooldown holds");
        helper.assertValueEqual(
                scans(player).getLast().kind(),
                com.andrewbrogoff.vftv.api.aspect.ScanTargetKind.ITEM,
                "an item strike");
        helper.succeed();
    }

    /** Obsidian's Tenebrae is unheard: partial, unchanged-partial, then completed and repeat. */
    private static void partialThenComplete(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.OBSIDIAN);
        useOn(helper, player, new BlockPos(1, 1, 1));
        ScanResult first = Objects.requireNonNull(last(player));
        helper.assertTrue(
                first.entries().stream().anyMatch(e -> e.unheard() && e.amount() == 3),
                "Tenebrae is unheard with its exact amount");
        cool(player);
        useOn(helper, player, new BlockPos(1, 1, 1));
        for (var aspect : List.of(AspectKeys.AER, AspectKeys.PERDITIO, AspectKeys.VACUOS)) {
            KnowledgeService.discover(player, aspect);
        }
        cool(player);
        useOn(helper, player, new BlockPos(1, 1, 1));
        cool(player);
        useOn(helper, player, new BlockPos(1, 1, 1));
        List<ThingScannedPayload> s = scans(player);
        helper.assertValueEqual(
                s.stream().map(ThingScannedPayload::outcome).toList(),
                List.of(
                        ScanOutcome.FIRST,
                        ScanOutcome.PARTIAL,
                        ScanOutcome.COMPLETED,
                        ScanOutcome.REPEAT),
                "the outcomes");
        helper.assertValueEqual(
                s.stream().map(ThingScannedPayload::complete).toList(),
                List.of(false, false, true, true),
                "the complete flags");
        helper.assertValueEqual(
                s.get(0).points().getOrDefault(AspectKeys.TERRA, 0), 2, "first pays Terra");
        helper.assertTrue(s.get(1).points().isEmpty(), "unchanged-partial pays nothing");
        helper.assertValueEqual(
                s.get(2).points().getOrDefault(AspectKeys.TENEBRAE, 0),
                3,
                "completion pays Tenebrae");
        helper.assertTrue(s.get(3).points().isEmpty(), "repeat pays nothing");
        helper.succeed();
    }

    /**
     * A strike recipe converts one item: a single item in place; from a stack with a full
     * inventory, the result drops; writing refuses and nothing changes; no scan event fires.
     */
    private static void strikeConversion(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        ItemStack fork = player.getMainHandItem().copy();
        var menu = player.inventoryMenu;
        player.getInventory().setItem(9, new ItemStack(Items.PAPER));
        menu.setCarried(fork);
        menu.clicked(9, 1, ContainerInput.PICKUP, player);
        helper.assertTrue(menu.getSlot(9).getItem().is(Items.MAP), "a single paper becomes a map");

        cool(player);
        for (int i = 0; i < 36; i++) {
            player.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));
        }
        player.getInventory().setItem(9, new ItemStack(Items.PAPER, 5));
        menu.clicked(9, 1, ContainerInput.PICKUP, player);
        helper.assertValueEqual(menu.getSlot(9).getItem().getCount(), 4, "the rest stays");
        helper.assertTrue(
                !helper.getLevel()
                        .getEntitiesOfClass(
                                ItemEntity.class,
                                player.getBoundingBox().inflate(3),
                                e -> e.getItem().is(Items.MAP))
                        .isEmpty(),
                "the map drops at the player's feet");

        cool(player);
        ItemStack written = new ItemStack(Items.PAPER);
        written.set(
                DataComponents.WRITABLE_BOOK_CONTENT,
                new WritableBookContent(List.of(Filterable.passThrough("my notes"))));
        player.getInventory().setItem(9, written);
        menu.clicked(9, 1, ContainerInput.PICKUP, player);
        helper.assertTrue(
                ItemStack.matches(menu.getSlot(9).getItem(), written), "the writing is untouched");
        helper.assertValueEqual(
                Objects.requireNonNull(last(player)).state(),
                ScanResult.State.INK,
                "The ink would be lost.");
        helper.assertTrue(scans(player).isEmpty(), "a conversion posts no scan event");
        helper.succeed();
    }

    /** Knowledge survives a real save and load, and death; an unreadable record is kept raw. */
    private static void knowledgePersists(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        Knowledge k =
                Objects.requireNonNull(Knowledge.EMPTY.discover(id(AspectKeys.AER), 4))
                        .strike(
                                "item:minecraft:obsidian",
                                Map.of(id(AspectKeys.AER), 1, id(AspectKeys.TENEBRAE), 2),
                                AspectSet.of(helper.getLevel().registryAccess()).rules(4))
                        .next();
        KnowledgeService.set(player, k);
        CompoundTag saved = save(player);
        ServerPlayer reloaded = player(helper);
        reloaded.load(
                TagValueInput.create(ProblemReporter.DISCARDING, player.registryAccess(), saved));
        helper.assertValueEqual(knowledge(reloaded), k.baseline(), "after save and load");

        ServerPlayer respawned = player(helper);
        respawned.restoreFrom(player, false);
        helper.assertValueEqual(knowledge(respawned), k.baseline(), "after death");

        saved.getCompoundOrEmpty("neoforge:attachments")
                .getCompoundOrEmpty("vftv:knowledge")
                .getCompoundOrEmpty("data")
                .putInt("format", 9);
        ServerPlayer future = player(helper);
        future.load(
                TagValueInput.create(ProblemReporter.DISCARDING, player.registryAccess(), saved));
        helper.assertTrue(
                KnowledgeService.raw(future) instanceof Retained.Unreadable<Knowledge>,
                "a future format is unreadable");
        List<Component> lines = new ArrayList<>();
        var previous = StatusLines.swapSender((p, line) -> lines.add(line.text()));
        try {
            future.setItemInHand(
                    InteractionHand.MAIN_HAND, new ItemStack(AspectContent.tuningFork()));
            helper.setBlock(new BlockPos(1, 1, 1), Blocks.STONE);
            useOn(helper, future, new BlockPos(1, 1, 1));
        } finally {
            StatusLines.swapSender(previous);
        }
        helper.assertTrue(
                lines.stream()
                        .anyMatch(
                                l ->
                                        l.getContents() instanceof TranslatableContents t
                                                && t.getKey().equals(SharedMessages.UNREADABLE)),
                "a strike is refused as unreadable");
        helper.assertValueEqual(
                save(future).getCompoundOrEmpty("neoforge:attachments"),
                saved.getCompoundOrEmpty("neoforge:attachments"),
                "written back unchanged");
        helper.succeed();
    }

    /**
     * An unreadable record WARNs once per player per server session (conventions, "What an
     * unreadable record does", rule 2): again after it has decoded, and again after the server
     * stops.
     */
    private static void unreadableWarnsPerSession(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        KnowledgeService.set(p, Knowledge.EMPTY);
        CompoundTag good = save(p);
        CompoundTag bad = good.copy();
        bad.getCompoundOrEmpty("neoforge:attachments")
                .getCompoundOrEmpty("vftv:knowledge")
                .getCompoundOrEmpty("data")
                .putInt("format", 9);
        // Each use loads the tag into the same player, then tries a write.
        Consumer<CompoundTag> use =
                tag -> {
                    p.load(
                            TagValueInput.create(
                                    ProblemReporter.DISCARDING, p.registryAccess(), tag));
                    KnowledgeService.credit(p, AspectKeys.AER, 1);
                };
        // Tests run one at a time on the server thread, so every WARN until the swap back is ours.
        List<String> warns = new ArrayList<>();
        Consumer<String> previous = KnowledgeService.swapWarner(warns::add);
        try {
            use.accept(bad);
            use.accept(bad);
            helper.assertValueEqual(warns.size(), 1, "one WARN while it stays unreadable");
            use.accept(good);
            use.accept(bad);
            helper.assertValueEqual(warns.size(), 2, "again once it has decoded");
            use.accept(bad);
            KnowledgeService.onServerStopped();
            use.accept(bad);
            helper.assertValueEqual(warns.size(), 3, "again in the next session");
        } finally {
            KnowledgeService.swapWarner(previous);
        }
        helper.succeed();
    }

    /** One player's strike changes nothing for another and sends them nothing. */
    private static void twoPlayerPrivacy(GameTestHelper helper) {
        ServerPlayer a = player(helper);
        ServerPlayer b = player(helper);
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.STONE);
        useOn(helper, a, new BlockPos(1, 1, 1));
        helper.assertValueEqual(knowledge(b), Knowledge.EMPTY, "b knows nothing new");
        helper.assertTrue(results(b).isEmpty(), "b was sent no readout");
        helper.assertValueEqual(results(a).size(), 1, "a was");
        helper.succeed();
    }

    /** A block strike at a position the server holds as air is silence: no ring, no card. */
    private static void phantomIsSilent(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.AIR);
        useOn(helper, player, new BlockPos(1, 1, 1));
        helper.assertTrue(results(player).isEmpty(), "no card");
        helper.assertTrue(!player.getCooldowns().isOnCooldown(fork(player)), "no ring");
        helper.assertTrue(scans(player).isEmpty(), "no event");
        player.gameMode.useItem(player, helper.getLevel(), fork(player), InteractionHand.MAIN_HAND);
        helper.assertValueEqual(
                Objects.requireNonNull(last(player)).state(),
                ScanResult.State.NO_ANSWER,
                "an ordinary air use sounds");
        helper.succeed();
    }

    /**
     * Sounding with a test node source: an answer carries only a bearing and band; beyond the
     * radius there is none. A node strike pays its first-strike reward once, even after it moves.
     * The node source is restored before the test returns, so no other test sees it.
     */
    private static void soundingAndNodes(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        ServerLevel level = helper.getLevel();
        BlockPos[] nodeAt = {helper.absolutePos(new BlockPos(2, 1, 2))};
        Vec3[] centre = {player.getEyePosition().add(30, 0, 0)};
        NodeSource test =
                new NodeSource() {
                    @Override
                    public @Nullable Readout readout(ServerLevel l, BlockPos pos) {
                        return pos.equals(nodeAt[0])
                                ? new Readout(
                                        7,
                                        Component.literal("Wellspring"),
                                        Component.literal("bright"),
                                        List.of(new NodeAspect(AspectKeys.AER, 3, 20)),
                                        OptionalInt.empty())
                                : null;
                    }

                    @Override
                    public @Nullable Nearest nearest(ServerLevel l, Vec3 point, int radius) {
                        return point.distanceTo(centre[0]) <= radius
                                ? new Nearest(7, centre[0])
                                : null;
                    }

                    @Override
                    public boolean starterMissing(MinecraftServer server) {
                        return false;
                    }
                };
        Strikes.registerNodeSource(test);
        try {
            player.gameMode.useItem(player, level, fork(player), InteractionHand.MAIN_HAND);
            ScanResult answer = Objects.requireNonNull(last(player));
            helper.assertValueEqual(answer.state(), ScanResult.State.ANSWER, "an answer");
            helper.assertValueEqual(
                    Objects.requireNonNull(answer.bearing()),
                    new ScanResult.Bearing(2, Sounding.Vertical.LEVEL, Sounding.Band.MID),
                    "east, level, farther");
            centre[0] = player.getEyePosition().add(0, 0, -300);
            cool(player);
            player.gameMode.useItem(player, level, fork(player), InteractionHand.MAIN_HAND);
            helper.assertValueEqual(
                    Objects.requireNonNull(last(player)).state(),
                    ScanResult.State.NO_ANSWER,
                    "nothing beyond the radius");

            helper.setBlock(new BlockPos(2, 1, 2), Blocks.GLASS);
            cool(player);
            useOn(helper, player, new BlockPos(2, 1, 2));
            ScanResult node = Objects.requireNonNull(last(player));
            helper.assertValueEqual(
                    node.entries().getFirst().max(), 20, "a node aspect reads current / max");
            helper.assertValueEqual(
                    scans(player).getLast().key(), new ScanKey.Node(7), "keyed by stable id");
            helper.assertValueEqual(
                    scans(player).getLast().points().getOrDefault(AspectKeys.AER, 0),
                    3,
                    "ceil(20 / 8) of Aer");
            // The node moves; its id stays, so it never pays its first-strike reward twice.
            nodeAt[0] = helper.absolutePos(new BlockPos(3, 1, 2));
            helper.setBlock(new BlockPos(3, 1, 2), Blocks.GLASS);
            cool(player);
            useOn(helper, player, new BlockPos(3, 1, 2));
            helper.assertValueEqual(
                    scans(player).getLast().outcome(), ScanOutcome.REPEAT, "a moved node repeats");
        } finally {
            Strikes.registerNodeSource(NodeSource.NONE);
        }
        helper.succeed();
    }

    /** A credit pays only a known aspect, never teaches, and posts no event. */
    private static void creditNeverTeaches(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        helper.assertTrue(
                !KnowledgeService.credit(player, AspectKeys.ALIENIS, 5), "unknown: no credit");
        KnowledgeService.discover(player, AspectKeys.AER);
        int events = events(player).size();
        helper.assertTrue(KnowledgeService.credit(player, AspectKeys.AER, 5), "known: credited");
        helper.assertValueEqual(
                knowledge(player).pool().getOrDefault(id(AspectKeys.AER), 0), 4 + 5, "pool");
        helper.assertValueEqual(events(player).size(), events, "no event");
        helper.assertTrue(
                !knowledge(player).known().contains(id(AspectKeys.ALIENIS)), "never taught");
        helper.succeed();
    }

    private static CompoundTag save(ServerPlayer player) {
        TagValueOutput out =
                TagValueOutput.createWithContext(
                        ProblemReporter.DISCARDING, player.registryAccess());
        player.saveWithoutId(out);
        return out.buildResult();
    }
}
