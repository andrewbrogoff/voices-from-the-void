package com.andrewbrogoff.vftv.gametest;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.api.essentia.EssentiaSuction;
import com.andrewbrogoff.vftv.api.neoforge.essentia.EssentiaCapabilities;
import com.andrewbrogoff.vftv.api.neoforge.essentia.EssentiaResource;
import com.andrewbrogoff.vftv.api.vis.NodeCause;
import com.andrewbrogoff.vftv.api.vis.NodeEventKind;
import com.andrewbrogoff.vftv.api.vis.NodePayload;
import com.andrewbrogoff.vftv.api.vis.NodeTypeKeys;
import com.andrewbrogoff.vftv.core.common.RawRecordCodec.Retained;
import com.andrewbrogoff.vftv.core.essentia.EssentiaContent;
import com.andrewbrogoff.vftv.core.essentia.EssentiaService;
import com.andrewbrogoff.vftv.core.essentia.JarBlockEntity;
import com.andrewbrogoff.vftv.core.essentia.model.EssentiaMath.Contents;
import com.andrewbrogoff.vftv.core.vis.BellJar;
import com.andrewbrogoff.vftv.core.vis.CapturedNode;
import com.andrewbrogoff.vftv.core.vis.NodeBlockEntity;
import com.andrewbrogoff.vftv.core.vis.NodeLedger;
import com.andrewbrogoff.vftv.core.vis.NodeService;
import com.andrewbrogoff.vftv.core.vis.NodeState;
import com.andrewbrogoff.vftv.core.vis.NodeTapBlock;
import com.andrewbrogoff.vftv.core.vis.RelayLedger;
import com.andrewbrogoff.vftv.core.vis.RelayService;
import com.andrewbrogoff.vftv.core.vis.VisContent;
import com.andrewbrogoff.vftv.core.vis.VisLang;
import com.andrewbrogoff.vftv.core.vis.VisRulesService;
import com.andrewbrogoff.vftv.core.vis.WandCradleBlockEntity;
import com.andrewbrogoff.vftv.core.vis.model.RelayNetwork;
import com.andrewbrogoff.vftv.core.vis.model.VisTuning;
import com.mojang.serialization.DataResult;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * The vis feature's Stage 2 GameTests (wands spec, "Tests": identity, cancellation, cultivation,
 * relays, packets aside): the Bell Jar, the Node Tap and cultivation, relays and the Wand Cradle.
 * Registered from {@link VisGameTests}; names {@code vis_<what>}, ids in {@code vis.txt}.
 *
 * <p>Relay tests run in one tick: they process the membership queue and pulse synchronously through
 * {@link RelayService#processQueue} and {@link RelayService#pulse}, so no natural pulse
 * interleaves, and they remove their members before returning.
 */
final class RelayGameTests {
    private static final Identifier WINDWARD = Identifier.fromNamespaceAndPath("vftv", "windward");
    private static volatile Predicate<BreakBlockEvent> breaks = e -> false;
    private static volatile Consumer<BlockEvent.EntityPlaceEvent> places = e -> {};

    private RelayGameTests() {}

    static void register(Map<String, Consumer<GameTestHelper>> tests) {
        tests.put("vis_jar_capture_release", RelayGameTests::jarCaptureRelease);
        tests.put("vis_jar_refusals", RelayGameTests::jarRefusals);
        tests.put("vis_jar_cancellation", RelayGameTests::jarCancellation);
        tests.put("vis_tap_placement_and_status", RelayGameTests::tapPlacementAndStatus);
        tests.put("vis_cultivation_transactions", RelayGameTests::cultivationTransactions);
        tests.put("vis_cultivation_line", RelayGameTests::cultivationLine);
        tests.put("vis_relay_pulse", RelayGameTests::relayPulse);
        tests.put("vis_relay_unloaded", RelayGameTests::relayUnloaded);
        tests.put("vis_relay_membership", RelayGameTests::relayMembership);
        tests.put("vis_relay_range_reload", RelayGameTests::relayRangeReload);
        tests.put("vis_cradle_use", RelayGameTests::cradleUse);
        tests.put("vis_relay_persistence", RelayGameTests::relayPersistence);
        NeoForge.EVENT_BUS.addListener(
                BreakBlockEvent.class,
                e -> {
                    if (breaks.test(e)) {
                        e.setCanceled(true);
                    }
                });
        NeoForge.EVENT_BUS.addListener(BlockEvent.EntityPlaceEvent.class, e -> places.accept(e));
    }

    // --- fixtures -----------------------------------------------------------------------------

    private static VisGameTests.SortedAspects windward(int aer, int terra) {
        VisGameTests.SortedAspects m = new VisGameTests.SortedAspects();
        m.put("vftv:aer", aer);
        m.put("vftv:terra", terra);
        return m;
    }

    private static ServerPlayer player(GameTestHelper helper, ResourceKey<?>... learned) {
        ServerPlayer p = ResearchTestSupport.player(helper);
        ResearchTestSupport.learn(p, learned);
        return p;
    }

    /** A creative mock player with a connection, as {@link ResearchTestSupport#player} makes. */
    private static ServerPlayer creative(GameTestHelper helper) {
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.CREATIVE);
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        new ServerGamePacketListenerImpl(
                helper.getLevel().getServer(),
                connection,
                player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false));
        return player;
    }

    /** Uses the main-hand stack on {@code face} of {@code pos}. */
    private static InteractionResult useOn(ServerPlayer p, BlockPos pos, Direction face) {
        return p.gameMode.useItemOn(
                p,
                p.level(),
                p.getMainHandItem(),
                InteractionHand.MAIN_HAND,
                new BlockHitResult(
                        Vec3.atCenterOf(pos)
                                .add(Vec3.atLowerCornerOf(face.getUnitVec3i()).scale(0.5)),
                        face,
                        pos,
                        false));
    }

    private static List<String> lines(Runnable body) {
        return VisGameTests.keys(VisGameTests.capture(body));
    }

    private static List<NodePayload> nodeEvents(Runnable body) {
        List<NodePayload> out = new ArrayList<>();
        for (Object e : VisGameTests.events(body)) {
            if (e instanceof NodePayload p) {
                out.add(p);
            }
        }
        return out;
    }

    private static NodeLedger ledger(ServerLevel level) {
        return Objects.requireNonNull(NodeService.ledger(level.getServer()));
    }

    private static @Nullable NodeState nodeAt(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof NodeBlockEntity be ? be.state() : null;
    }

    private static BlockState tap(Direction facing) {
        return VisContent.nodeTap().defaultBlockState().setValue(NodeTapBlock.FACING, facing);
    }

    private static Holder<Aspect> aspect(ServerLevel level, ResourceKey<Aspect> key) {
        return level.registryAccess().lookupOrThrow(AspectKeys.REGISTRY).getOrThrow(key);
    }

    // NullAway can't match the capability's @Nullable Direction context type argument.
    @SuppressWarnings("NullAway")
    private static @Nullable ResourceHandler<EssentiaResource> handler(
            ServerLevel level, BlockPos pos, @Nullable Direction side) {
        return level.getCapability(EssentiaCapabilities.HANDLER, pos, side);
    }

    @SuppressWarnings("NullAway")
    private static @Nullable EssentiaSuction suction(
            ServerLevel level, BlockPos pos, @Nullable Direction side) {
        return level.getCapability(EssentiaCapabilities.SUCTION, pos, side);
    }

    private static void withTuning(String json, Runnable body) {
        VisTuning before = VisRulesService.tuning();
        VisRulesService.applyTuning(DataResult.success(VisTuning.parse(json)), true);
        try {
            body.run();
        } finally {
            VisRulesService.applyTuning(DataResult.success(before), true);
        }
    }

    private static void clear(ServerLevel level, List<BlockPos> blocks) {
        blocks.forEach(p -> level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL));
        RelayService.processQueue(level);
    }

    // --- the Bell Jar -------------------------------------------------------------------------

    /** Capture then release keeps the id and the state; a copied jar is stale for good. */
    private static void jarCaptureRelease(GameTestHelper helper) {
        BlockPos origin = VisGameTests.ownCell(helper);
        ServerLevel level = helper.getLevel();
        BlockPos pos = origin.above();
        long id = VisGameTests.node(level, pos, WINDWARD, windward(30, 12));
        BlockPos floor = origin.east(5);
        level.setBlock(floor, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        try {
            ServerPlayer p = player(helper, BellJar.MOVING_NODES);
            ItemStack jar = new ItemStack(VisContent.bellJar());
            p.setItemInHand(InteractionHand.MAIN_HAND, jar);
            NodeState before = Objects.requireNonNull(nodeAt(level, pos));
            List<NodePayload> seen = nodeEvents(() -> useOn(p, pos, Direction.UP));
            helper.assertTrue(level.getBlockState(pos).isAir(), "the block is taken up");
            NodeLedger.Entry carried = Objects.requireNonNull(ledger(level).get(id));
            helper.assertValueEqual(carried.state(), NodeLedger.State.CARRIED, "carried");
            CapturedNode held =
                    ((Retained.Read<CapturedNode>)
                                    Objects.requireNonNull(jar.get(VisContent.captured())))
                            .value();
            helper.assertValueEqual(held.token(), carried.token(), "the jar holds the token");
            helper.assertValueEqual(held.node().id(), id, "the id travels");
            helper.assertValueEqual(
                    held.node().aspects(), before.aspects(), "vis and maxes intact");
            helper.assertTrue(
                    NodeService.nearest(level, Vec3.atCenterOf(pos), 16) == null,
                    "a carried node leaves the index");
            helper.assertValueEqual(seen.size(), 1, "one event");
            helper.assertValueEqual(seen.get(0).kind(), NodeEventKind.CAPTURED, "CAPTURED");
            helper.assertTrue(seen.get(0).node().where() == null, "no position while carried");

            ItemStack copy = jar.copy();
            BlockPos target = floor.above();
            seen = nodeEvents(() -> useOn(p, floor, Direction.UP));
            NodeState placed = Objects.requireNonNull(nodeAt(level, target), "set down");
            helper.assertValueEqual(placed.id(), id, "the same id");
            helper.assertTrue(
                    Objects.requireNonNull(ledger(level).get(id))
                            .placedAt(level.dimension(), target),
                    "PLACED at the target");
            helper.assertTrue(!jar.has(VisContent.captured()), "the jar is empty again");
            helper.assertValueEqual(seen.size(), 1, "one event");
            helper.assertValueEqual(seen.get(0).kind(), NodeEventKind.PLACED, "PLACED");
            helper.assertValueEqual(
                    Objects.requireNonNull(NodeService.nearest(level, Vec3.atCenterOf(target), 4))
                            .id(),
                    id,
                    "indexed again");

            // The copy is stale after the release, and again after a recapture.
            p.setItemInHand(InteractionHand.MAIN_HAND, copy);
            BlockPos elsewhere = origin.west(5);
            level.setBlock(elsewhere, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertValueEqual(
                    lines(() -> useOn(p, elsewhere, Direction.UP)),
                    List.of(VisLang.JAR_STALE),
                    "a copy after the release");
            p.setItemInHand(InteractionHand.MAIN_HAND, jar);
            useOn(p, target, Direction.UP);
            helper.assertTrue(jar.has(VisContent.captured()), "recaptured");
            p.setItemInHand(InteractionHand.MAIN_HAND, copy);
            helper.assertValueEqual(
                    lines(() -> useOn(p, elsewhere, Direction.UP)),
                    List.of(VisLang.JAR_STALE),
                    "a copy after a recapture");
            helper.assertTrue(level.getBlockState(elsewhere.above()).isAir(), "nothing placed");
        } finally {
            NodeService.remove(level.getServer(), id, NodeCause.API);
            VisGameTests.release(helper, origin);
        }
        helper.succeed();
    }

    /** Every capture and release refusal, in order, with spacing equality allowed. */
    private static void jarRefusals(GameTestHelper helper) {
        BlockPos origin = VisGameTests.ownCell(helper);
        ServerLevel level = helper.getLevel();
        List<Long> ids = new ArrayList<>();
        BlockPos plain = origin.above();
        BlockPos anchored = origin.above().east(4);
        BlockPos gone = origin.above().west(4);
        ids.add(VisGameTests.node(level, plain, WINDWARD, windward(20, 8)));
        ids.add(
                VisGameTests.node(
                        level,
                        anchored,
                        NodeTypeKeys.WELLSPRING.identifier(),
                        VisGameTests.six(32)));
        ids.add(VisGameTests.node(level, gone, Identifier.parse("mod:gone"), windward(8, 8)));
        try {
            ServerPlayer novice = player(helper);
            novice.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VisContent.bellJar()));
            helper.assertValueEqual(
                    lines(() -> useOn(novice, plain, Direction.UP)),
                    List.of(VisLang.JAR_UNLEARNED),
                    "unlearned");
            ServerPlayer p = player(helper, BellJar.MOVING_NODES);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VisContent.bellJar()));
            helper.assertValueEqual(
                    lines(() -> useOn(p, anchored, Direction.UP)),
                    List.of(VisLang.JAR_ANCHORED),
                    "anchored");
            helper.assertValueEqual(
                    lines(() -> useOn(p, gone, Direction.UP)),
                    List.of(VisLang.JAR_UNKNOWN),
                    "a missing type");
            helper.assertTrue(!p.getMainHandItem().has(VisContent.captured()), "still empty");
            // Capture the plain node, then try to set it down.
            useOn(p, plain, Direction.UP);
            helper.assertTrue(p.getMainHandItem().has(VisContent.captured()), "captured");
            BlockPos wall = origin.north(6).above();
            level.setBlock(wall, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(wall.above(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertValueEqual(
                    lines(() -> useOn(p, wall, Direction.UP)),
                    List.of(VisLang.JAR_NO_ROOM),
                    "no room");
            // The anchored node is at anchored; 2 blocks away is too close, exactly 3 is allowed.
            BlockPos near = anchored.east(2).below();
            level.setBlock(near, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            List<String> close = lines(() -> useOn(p, near, Direction.UP));
            helper.assertValueEqual(close, List.of(VisLang.JAR_TOO_CLOSE), "too close");
            BlockPos edge = anchored.east(3).below();
            level.setBlock(edge, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertValueEqual(
                    lines(() -> useOn(p, edge, Direction.UP)), List.of(), "spacing equality");
            helper.assertTrue(nodeAt(level, edge.above()) != null, "released at exactly 3");
            // A full cell: eight nodes elsewhere, then a release into it.
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VisContent.bellJar()));
            useOn(p, edge.above(), Direction.UP);
            BlockPos crowd = VisGameTests.ownCell(helper);
            for (int i = 0; i < 8; i++) {
                ids.add(
                        VisGameTests.node(
                                level,
                                crowd.above().east((i % 4) * 3).north((i / 4) * 3),
                                WINDWARD,
                                windward(4, 4)));
            }
            BlockPos inCrowd = crowd.south(6);
            level.setBlock(inCrowd, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertValueEqual(
                    lines(() -> useOn(p, inCrowd, Direction.UP)),
                    List.of(VisLang.JAR_CROWDED),
                    "a full cell");
            VisGameTests.release(helper, crowd);
            // A jar naming an id the registry never held: refused in survival.
            ItemStack forged = new ItemStack(VisContent.bellJar());
            NodeState unknown =
                    Objects.requireNonNull(
                                    ((Retained.Read<CapturedNode>)
                                                    Objects.requireNonNull(
                                                            p.getMainHandItem()
                                                                    .get(VisContent.captured())))
                                            .value())
                            .node()
                            .withId(ledger(level).nextId() + 1000);
            forged.set(
                    VisContent.captured(),
                    new Retained.Read<>(
                            new CapturedNode(ledger(level).nextToken() + 1000, unknown)));
            p.setItemInHand(InteractionHand.MAIN_HAND, forged);
            helper.assertValueEqual(
                    lines(() -> useOn(p, edge, Direction.UP)),
                    List.of(VisLang.JAR_STALE),
                    "an unknown id in survival");
        } finally {
            ids.forEach(id -> NodeService.remove(level.getServer(), id, NodeCause.API));
            VisGameTests.release(helper, origin);
        }
        helper.succeed();
    }

    /**
     * Cancelled break and placement events change nothing; the placement listener sees the node
     * placed over air by the real player; a refused creative recovery leaves the counters alone.
     */
    private static void jarCancellation(GameTestHelper helper) {
        BlockPos origin = VisGameTests.ownCell(helper);
        ServerLevel level = helper.getLevel();
        BlockPos pos = origin.above();
        long id = VisGameTests.node(level, pos, WINDWARD, windward(10, 10));
        BlockPos floor = origin.east(5);
        level.setBlock(floor, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        BlockPos target = floor.above();
        List<Long> extra = new ArrayList<>();
        try {
            ServerPlayer p = player(helper, BellJar.MOVING_NODES);
            ItemStack jar = new ItemStack(VisContent.bellJar());
            p.setItemInHand(InteractionHand.MAIN_HAND, jar);
            breaks = e -> e.getPos().equals(pos);
            List<NodePayload> seen;
            try {
                seen = nodeEvents(() -> useOn(p, pos, Direction.UP));
            } finally {
                breaks = e -> false;
            }
            helper.assertTrue(seen.isEmpty(), "no event");
            helper.assertTrue(nodeAt(level, pos) != null, "the node stays");
            helper.assertTrue(!jar.has(VisContent.captured()), "the jar stays empty");
            helper.assertTrue(
                    Objects.requireNonNull(ledger(level).get(id)).placedAt(level.dimension(), pos),
                    "the entry stays");

            useOn(p, pos, Direction.UP);
            helper.assertTrue(jar.has(VisContent.captured()), "captured once allowed");
            long nextToken = ledger(level).nextToken();
            List<Object> placedState = new ArrayList<>();
            places =
                    e -> {
                        if (e.getPos().equals(target)) {
                            placedState.add(e.getPlacedBlock());
                            placedState.add(e.getBlockSnapshot().getState());
                            placedState.add(e.getEntity());
                            e.setCanceled(true);
                        }
                    };
            try {
                seen = nodeEvents(() -> useOn(p, floor, Direction.UP));
            } finally {
                places = e -> {};
            }
            helper.assertValueEqual(placedState.size(), 3, "the listener ran");
            helper.assertTrue(
                    ((BlockState) placedState.get(0)).is(VisContent.node()), "the node is placed");
            helper.assertTrue(((BlockState) placedState.get(1)).isAir(), "over air");
            helper.assertTrue(p.equals(placedState.get(2)), "by the real player");
            helper.assertTrue(level.getBlockState(target).isAir(), "restored to air");
            helper.assertTrue(seen.isEmpty(), "no event");
            helper.assertTrue(jar.has(VisContent.captured()), "the jar keeps its node");
            helper.assertValueEqual(
                    Objects.requireNonNull(ledger(level).get(id)).state(),
                    NodeLedger.State.CARRIED,
                    "still carried");
            helper.assertValueEqual(ledger(level).nextToken(), nextToken, "no token taken");
            helper.assertTrue(
                    NodeService.nearest(level, Vec3.atCenterOf(target), 4) == null, "not indexed");

            // A creative operator's recovery of an unknown id: refused by the event, then made.
            CapturedNode held =
                    ((Retained.Read<CapturedNode>)
                                    Objects.requireNonNull(jar.get(VisContent.captured())))
                            .value();
            long ghost = ledger(level).nextId() + 500;
            long ghostToken = ledger(level).nextToken() + 500;
            ItemStack forged = new ItemStack(VisContent.bellJar());
            forged.set(
                    VisContent.captured(),
                    new Retained.Read<>(new CapturedNode(ghostToken, held.node().withId(ghost))));
            ServerPlayer op = creative(helper);
            ResearchTestSupport.learn(op, BellJar.MOVING_NODES);
            level.getServer()
                    .getPlayerList()
                    .op(
                            op.nameAndId(),
                            Optional.of(LevelBasedPermissionSet.GAMEMASTER),
                            Optional.empty());
            try {
                op.setItemInHand(InteractionHand.MAIN_HAND, forged);
                long nextId = ledger(level).nextId();
                places = e -> e.setCanceled(true);
                try {
                    seen = nodeEvents(() -> useOn(op, floor, Direction.UP));
                } finally {
                    places = e -> {};
                }
                helper.assertTrue(seen.isEmpty(), "no CREATED");
                helper.assertTrue(ledger(level).get(ghost) == null, "nothing registered");
                helper.assertValueEqual(ledger(level).nextId(), nextId, "next_id unchanged");
                helper.assertValueEqual(ledger(level).nextToken(), nextToken, "next_token too");
                // A later check refusing (spacing) changes nothing either.
                BlockPos close = origin.west(6);
                level.setBlock(close, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
                extra.add(
                        VisGameTests.node(level, close.east(2).above(), WINDWARD, windward(4, 4)));
                helper.assertValueEqual(
                        lines(() -> useOn(op, close, Direction.UP)),
                        List.of(VisLang.JAR_TOO_CLOSE),
                        "spacing");
                helper.assertTrue(ledger(level).get(ghost) == null, "still nothing registered");
                seen = nodeEvents(() -> useOn(op, floor, Direction.UP));
                helper.assertValueEqual(seen.size(), 1, "one event");
                helper.assertValueEqual(seen.get(0).kind(), NodeEventKind.CREATED, "CREATED");
                helper.assertValueEqual(seen.get(0).cause(), NodeCause.COMMAND, "a recovery");
                helper.assertTrue(ledger(level).nextId() > ghost, "next_id raised past it");
                helper.assertTrue(ledger(level).nextToken() > ghostToken, "next_token too");
                extra.add(ghost);
            } finally {
                level.getServer().getPlayerList().deop(op.nameAndId());
            }
        } finally {
            NodeService.remove(level.getServer(), id, NodeCause.API);
            extra.forEach(x -> NodeService.remove(level.getServer(), x, NodeCause.API));
            VisGameTests.release(helper, origin);
        }
        helper.succeed();
    }

    // --- the Node Tap and cultivation ---------------------------------------------------------

    /** A tap needs a node, one per node; its status line names the node or says it is idle. */
    private static void tapPlacementAndStatus(GameTestHelper helper) {
        BlockPos origin = VisGameTests.ownCell(helper);
        ServerLevel level = helper.getLevel();
        BlockPos pos = origin.above();
        long id = VisGameTests.node(level, pos, WINDWARD, windward(20, 8));
        BlockPos stone = origin.west(4);
        level.setBlock(stone, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        try {
            ServerPlayer p = player(helper);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VisContent.nodeTap()));
            helper.assertValueEqual(
                    lines(() -> useOn(p, stone, Direction.UP)),
                    List.of(VisLang.RELAY_NO_NODE),
                    "nothing to listen to");
            helper.assertTrue(level.getBlockState(stone.above()).isAir(), "not placed");
            useOn(p, pos, Direction.EAST);
            BlockState placed = level.getBlockState(pos.east());
            helper.assertTrue(placed.is(VisContent.nodeTap()), "placed against the node");
            helper.assertValueEqual(
                    placed.getValue(NodeTapBlock.FACING), Direction.WEST, "facing its node");
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VisContent.nodeTap()));
            helper.assertValueEqual(
                    lines(() -> useOn(p, pos, Direction.NORTH)),
                    List.of(VisLang.RELAY_TAKEN),
                    "one tap per node");
            helper.assertTrue(level.getBlockState(pos.north()).isAir(), "not placed");
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            p.setShiftKeyDown(true);
            List<net.minecraft.network.chat.Component> status =
                    VisGameTests.capture(() -> useOn(p, pos.east(), Direction.UP));
            helper.assertValueEqual(
                    VisGameTests.keys(status), List.of(VisLang.STATUS_TAP_LISTENING), "listening");
            List<Object> args =
                    List.of(
                            ((net.minecraft.network.chat.contents.TranslatableContents)
                                            status.get(0).getContents())
                                    .getArgs());
            helper.assertValueEqual(args.get(2), Long.toString(id), "names the id");
            helper.assertValueEqual(args.get(4), "48", "the clarity cost");
            NodeService.remove(level.getServer(), id, NodeCause.API);
            helper.assertValueEqual(
                    lines(() -> useOn(p, pos.east(), Direction.UP)),
                    List.of(VisLang.STATUS_TAP_IDLE),
                    "idle once the node leaves");
            clear(level, List.of(pos.east()));
        } finally {
            NodeService.remove(level.getServer(), id, NodeCause.API);
            VisGameTests.release(helper, origin);
        }
        helper.succeed();
    }

    /**
     * A rolled-back transaction leaves the node identical; a committed one raises it and posts one
     * event; the capabilities are on every face but the node's; other essentia is refused; suction
     * 0 still takes a direct insert.
     */
    private static void cultivationTransactions(GameTestHelper helper) {
        BlockPos origin = VisGameTests.ownCell(helper);
        ServerLevel level = helper.getLevel();
        BlockPos pos = origin.above();
        long id = VisGameTests.node(level, pos, WINDWARD, windward(30, 12));
        BlockPos tapPos = pos.east();
        try {
            NodeBlockEntity be = VisGameTests.be(level, pos);
            NodeState steady = Objects.requireNonNull(be.state());
            be.setState(
                    new NodeState(
                            steady.id(),
                            steady.origin(),
                            steady.type(),
                            NodeState.Clarity.STEADY,
                            steady.aspects(),
                            steady.regen(),
                            steady.cultivation()));
            level.setBlock(tapPos, tap(Direction.WEST), Block.UPDATE_ALL);
            helper.assertTrue(
                    handler(level, tapPos, Direction.WEST) == null, "not on the node's face");
            helper.assertTrue(suction(level, tapPos, Direction.WEST) == null, "nor suction");
            for (Direction d : List.of(Direction.EAST, Direction.UP, Direction.NORTH)) {
                helper.assertTrue(handler(level, tapPos, d) != null, "a handler on " + d);
                helper.assertTrue(suction(level, tapPos, d) != null, "suction on " + d);
            }
            ResourceHandler<EssentiaResource> h =
                    Objects.requireNonNull(handler(level, tapPos, null), "the null side");
            EssentiaSuction s = Objects.requireNonNull(suction(level, tapPos, null));
            EssentiaResource aer = new EssentiaResource(aspect(level, AspectKeys.AER));
            EssentiaResource sonus = new EssentiaResource(aspect(level, AspectKeys.SONUS));
            EssentiaResource metallum = new EssentiaResource(aspect(level, AspectKeys.METALLUM));
            int rank = VisRulesService.tuning().integer("cultivate.suction");
            helper.assertValueEqual(s.suction(AspectKeys.AER), rank, "it would accept Aer");
            helper.assertValueEqual(s.suction(AspectKeys.METALLUM), 0, "never Metallum");
            helper.assertValueEqual(s.hold(AspectKeys.AER), 0, "holds nothing");
            helper.assertValueEqual(h.size(), 0, "nothing to extract");
            NodeState before = be.state();
            List<NodePayload> seen =
                    nodeEvents(
                            () -> {
                                try (Transaction root = Transaction.openRoot()) {
                                    helper.assertValueEqual(h.insert(aer, 9, root), 9, "Aer in");
                                    helper.assertValueEqual(
                                            h.insert(metallum, 5, root), 0, "Metallum refused");
                                }
                            });
            helper.assertTrue(seen.isEmpty(), "an abort posts nothing");
            helper.assertValueEqual(be.state(), before, "the node state is identical");
            seen =
                    nodeEvents(
                            () -> {
                                try (Transaction root = Transaction.openRoot()) {
                                    h.insert(aer, 9, root);
                                    try (Transaction child = Transaction.open(root)) {
                                        h.insert(sonus, 48, child);
                                        child.commit();
                                    }
                                    root.commit();
                                }
                            });
            NodeState after = Objects.requireNonNull(be.state());
            helper.assertValueEqual(
                    Objects.requireNonNull(after.byAspect().get("vftv:aer")).max(), 32, "+2 max");
            helper.assertValueEqual(
                    after.cultivation().counters().getOrDefault(AspectKeys.AER.identifier(), 0),
                    1,
                    "1 over");
            helper.assertValueEqual(after.clarity(), NodeState.Clarity.CLEAR, "a clearer ring");
            helper.assertValueEqual(seen.size(), 1, "one event for the root");
            helper.assertValueEqual(seen.get(0).kind(), NodeEventKind.CULTIVATED, "CULTIVATED");
            helper.assertValueEqual(seen.get(0).node().id(), id, "the node");
            helper.assertValueEqual(s.suction(AspectKeys.SONUS), 0, "clear takes no more Sonus");
            withTuning(
                    "{\"cultivate.suction\": 0}",
                    () -> {
                        helper.assertValueEqual(s.suction(AspectKeys.AER), 0, "no pulling");
                        try (Transaction root = Transaction.openRoot()) {
                            helper.assertValueEqual(
                                    h.insert(aer, 4, root), 4, "a direct insert still works");
                            root.commit();
                        }
                    });
        } finally {
            level.setBlock(tapPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            NodeService.remove(level.getServer(), id, NodeCause.API);
            VisGameTests.release(helper, origin);
        }
        helper.succeed();
    }

    /** An essentia line feeds a Node Tap; with {@code cultivate.suction} 0 it doesn't pull. */
    private static void cultivationLine(GameTestHelper helper) {
        BlockPos origin = VisGameTests.ownCell(helper);
        ServerLevel level = helper.getLevel();
        BlockPos jarPos = origin.above();
        BlockPos tubePos = jarPos.east();
        BlockPos tapPos = tubePos.east();
        BlockPos nodePos = tapPos.east();
        long id = VisGameTests.node(level, nodePos, WINDWARD, windward(30, 12));
        level.setBlock(jarPos, EssentiaContent.jar().defaultBlockState(), Block.UPDATE_ALL);
        JarBlockEntity jar = (JarBlockEntity) Objects.requireNonNull(level.getBlockEntity(jarPos));
        jar.setLive(new Contents<>(AspectKeys.AER, 20));
        level.setBlock(tapPos, tap(Direction.EAST), Block.UPDATE_ALL);
        level.setBlock(
                tubePos,
                Block.updateFromNeighbourShapes(
                        EssentiaContent.tube().defaultBlockState(), level, tubePos),
                Block.UPDATE_ALL);
        VisTuning before = VisRulesService.tuning();
        VisRulesService.applyTuning(
                DataResult.success(VisTuning.parse("{\"cultivate.suction\": 0}")), true);
        AtomicBoolean pulling = new AtomicBoolean();
        helper.runAfterDelay(
                30,
                () -> {
                    try {
                        helper.assertValueEqual(
                                jar.contents().amount(), 20, "suction 0: the line doesn't pull");
                    } finally {
                        VisRulesService.applyTuning(DataResult.success(before), true);
                        EssentiaService.wakeAt(level, tapPos);
                        pulling.set(true);
                    }
                });
        helper.succeedWhen(
                () -> {
                    helper.assertTrue(pulling.get(), "waiting for the suction to return");
                    helper.assertTrue(jar.contents().amount() < 20, "the line feeds the tap");
                    NodeState s = Objects.requireNonNull(nodeAt(level, nodePos));
                    int counter =
                            s.cultivation().counters().getOrDefault(AspectKeys.AER.identifier(), 0);
                    int max = Objects.requireNonNull(s.byAspect().get("vftv:aer")).max();
                    helper.assertTrue(
                            (max - 30) * 4 + counter == 20 - jar.contents().amount(),
                            "every unit cultivates");
                    level.setBlock(tubePos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    level.setBlock(tapPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    level.setBlock(jarPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    NodeService.remove(level.getServer(), id, NodeCause.API);
                    VisGameTests.release(helper, origin);
                });
    }

    // --- relays and cradles -------------------------------------------------------------------

    private static WandCradleBlockEntity cradle(ServerLevel level, BlockPos pos, ItemStack wand) {
        level.setBlock(pos, VisContent.wandCradle().defaultBlockState(), Block.UPDATE_ALL);
        WandCradleBlockEntity c =
                (WandCradleBlockEntity) Objects.requireNonNull(level.getBlockEntity(pos));
        c.setWand(wand);
        return c;
    }

    private static void setAer(ServerLevel level, BlockPos node, int aer) {
        NodeBlockEntity be = VisGameTests.be(level, node);
        NodeState s = NodeService.settle(level, be);
        List<NodeState.NodeAspect> next = new ArrayList<>();
        for (NodeState.NodeAspect a : s.aspects()) {
            next.add(
                    a.aspect().toString().equals("vftv:aer")
                            ? new NodeState.NodeAspect(a.aspect(), a.max(), aer)
                            : new NodeState.NodeAspect(a.aspect(), a.max(), 0));
        }
        be.setState(s.withAspects(next));
    }

    /** The pulse vector's rotating start reaching every cradle, and conservation. */
    private static void relayPulse(GameTestHelper helper) {
        BlockPos origin = VisGameTests.ownCell(helper);
        ServerLevel level = helper.getLevel();
        BlockPos node = origin.above();
        long id = VisGameTests.node(level, node, WINDWARD, windward(30, 12));
        BlockPos tapPos = node.east();
        List<BlockPos> sinks = List.of(origin.east(3), origin.east(5), origin.east(7));
        List<BlockPos> placed = new ArrayList<>(List.of(tapPos));
        placed.addAll(sinks);
        try {
            level.setBlock(tapPos, tap(Direction.WEST), Block.UPDATE_ALL);
            List<WandCradleBlockEntity> cradles = new ArrayList<>();
            for (BlockPos s : sinks) {
                cradles.add(cradle(level, s, VisGameTests.wand()));
            }
            RelayService.processQueue(level);
            helper.assertValueEqual(
                    Objects.requireNonNull(RelayService.membership(level, tapPos)).size(),
                    4,
                    "one network of four");
            int period = VisRulesService.tuning().integer("relay.pulse");
            for (int start = 0; start < 3; start++) {
                setAer(level, node, 5);
                for (int i = 0; i < 3; i++) {
                    cradles.get(i).setWand(VisGameTests.wand());
                }
                RelayService.pulse(level, (long) start * period);
                int first = VisGameTests.pool(cradles.get(start).wand(), "vftv:aer");
                int second = VisGameTests.pool(cradles.get((start + 1) % 3).wand(), "vftv:aer");
                int third = VisGameTests.pool(cradles.get((start + 2) % 3).wand(), "vftv:aer");
                helper.assertValueEqual(first, 4, "start " + start + ": the first cradle takes 4");
                helper.assertValueEqual(second, 1, "the next takes what is left");
                helper.assertValueEqual(third, 0, "the last gets none");
                helper.assertValueEqual(
                        VisGameTests.current(level, node, "vftv:aer"), 0, "conservation: 5 out");
            }
            // A cradle's status line reads its fill.
            ServerPlayer p = player(helper);
            p.setShiftKeyDown(true);
            setAer(level, node, 5);
            helper.assertValueEqual(
                    lines(() -> useOn(p, sinks.get(0), Direction.UP)),
                    List.of(VisLang.STATUS_CRADLE_CHARGING),
                    "charging");
        } finally {
            clear(level, placed);
            NodeService.remove(level.getServer(), id, NodeCause.API);
            VisGameTests.release(helper, origin);
        }
        helper.succeed();
    }

    /**
     * A pulse runs through an unloaded intermediate relay; a source in an unloaded chunk gives
     * nothing; nothing loads a chunk.
     */
    private static void relayUnloaded(GameTestHelper helper) {
        BlockPos a = VisGameTests.ownCell(helper);
        ServerLevel level = helper.getLevel();
        // Chunk A holds the node and tap, B the relay and a second (Ignis) source, C the cradle.
        // Radius-0 tickets keep each one FULL without lending its neighbours a FULL level, so B
        // unloads once its own ticket goes while A and C stay.
        BlockPos base = new BlockPos((a.getX() >> 4) << 4, a.getY(), (a.getZ() >> 4) << 4);
        ChunkPos ca = ChunkPos.containing(base);
        ChunkPos b = new ChunkPos(ca.x() + 1, ca.z());
        ChunkPos c = new ChunkPos(ca.x() + 2, ca.z());
        for (ChunkPos ch : List.of(ca, b, c)) {
            level.getChunkSource().addTicketWithRadius(TicketType.PLAYER_LOADING, ch, 0);
            level.getChunk(ch.x(), ch.z());
        }
        VisGameTests.release(helper, a);
        BlockPos node = base.offset(13, 1, 4);
        BlockPos tapPos = base.offset(12, 1, 4);
        BlockPos relay = base.offset(22, 1, 4);
        BlockPos node2 = base.offset(24, 1, 4);
        BlockPos tap2 = base.offset(23, 1, 4);
        BlockPos sink = base.offset(32, 1, 4);
        long id = VisGameTests.node(level, node, WINDWARD, windward(30, 12));
        VisGameTests.SortedAspects ignis = new VisGameTests.SortedAspects();
        ignis.put("vftv:ignis", 20);
        long id2 = VisGameTests.node(level, node2, Identifier.parse("vftv:hearth"), ignis);
        level.setBlock(tapPos, tap(Direction.EAST), Block.UPDATE_ALL);
        level.setBlock(relay, VisContent.visRelay().defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(tap2, tap(Direction.EAST), Block.UPDATE_ALL);
        WandCradleBlockEntity cradle = cradle(level, sink, VisGameTests.wand());
        RelayService.processQueue(level);
        helper.assertValueEqual(
                Objects.requireNonNull(RelayService.membership(level, sink)).size(),
                4,
                "tap, relay, second tap and cradle link");
        level.getChunkSource().removeTicketWithRadius(TicketType.PLAYER_LOADING, b, 0);
        AtomicBoolean pulsed = new AtomicBoolean();
        helper.succeedWhen(
                () -> {
                    helper.assertTrue(
                            level.getChunkSource().getChunkNow(b.x(), b.z()) == null,
                            "waiting for the relay's chunk to unload");
                    if (pulsed.compareAndSet(false, true)) {
                        cradle.setWand(VisGameTests.wand());
                        setAer(level, node, 4);
                        RelayService.pulse(level, 0);
                    }
                    helper.assertValueEqual(
                            VisGameTests.pool(cradle.wand(), "vftv:aer"),
                            4,
                            "vis crossed the unloaded relay");
                    helper.assertValueEqual(
                            VisGameTests.pool(cradle.wand(), "vftv:ignis"),
                            0,
                            "the unloaded source gave nothing");
                    helper.assertTrue(
                            level.getChunkSource().getChunkNow(b.x(), b.z()) == null,
                            "the pulse loaded no chunk");
                    // Clean up: reload B to remove its blocks, then the rest.
                    level.getChunkSource().addTicketWithRadius(TicketType.PLAYER_LOADING, b, 0);
                    level.getChunk(b.x(), b.z());
                    clear(level, List.of(tapPos, relay, tap2, sink));
                    NodeService.remove(level.getServer(), id, NodeCause.API);
                    NodeService.remove(level.getServer(), id2, NodeCause.API);
                    for (ChunkPos ch : List.of(ca, b, c)) {
                        level.getChunkSource()
                                .removeTicketWithRadius(TicketType.PLAYER_LOADING, ch, 0);
                    }
                });
    }

    /**
     * The 32 cap through placement and the queue, a cancelled placement leaving no membership,
     * {@code /setblock} and {@code /clone} members, a kind replacement and a ghost member.
     */
    private static void relayMembership(GameTestHelper helper) {
        BlockPos origin = VisGameTests.ownCell(helper);
        ServerLevel level = helper.getLevel();
        BlockPos base =
                new BlockPos((origin.getX() >> 4) << 4, origin.getY(), (origin.getZ() >> 4) << 4);
        List<BlockPos> members = new ArrayList<>();
        for (int i = 0; i < 32; i++) {
            members.add(base.offset(i % 16, 1, (i / 16) * 2));
        }
        try {
            members.forEach(
                    p ->
                            level.setBlock(
                                    p,
                                    VisContent.visRelay().defaultBlockState(),
                                    Block.UPDATE_ALL));
            RelayService.processQueue(level);
            helper.assertValueEqual(
                    Objects.requireNonNull(RelayService.membership(level, members.get(0))).size(),
                    32,
                    "setblock members are admitted, up to 32");
            ServerPlayer p = player(helper);
            BlockPos floor = base.offset(3, 0, 6);
            level.setBlock(floor, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VisContent.visRelay()));
            List<String> refused = lines(() -> useOn(p, floor, Direction.UP));
            helper.assertValueEqual(refused, List.of(VisLang.RELAY_TOO_MANY), "the 33rd placement");
            helper.assertTrue(level.getBlockState(floor.above()).isAir(), "refused early");
            // A 33rd by setblock is broken by the queue (its drop is vanilla's destroyBlock).
            BlockPos extra = floor.above();
            level.setBlock(extra, VisContent.visRelay().defaultBlockState(), Block.UPDATE_ALL);
            RelayService.processQueue(level);
            helper.assertTrue(level.getBlockState(extra).isAir(), "broken past the cap");
            helper.assertTrue(RelayService.membership(level, extra) == null, "not a member");
            // A kind replaced by another: updated in place.
            level.setBlock(
                    members.get(5), VisContent.wandCradle().defaultBlockState(), Block.UPDATE_ALL);
            RelayService.processQueue(level);
            helper.assertValueEqual(
                    Objects.requireNonNull(RelayService.membership(level, members.get(5))).kind(),
                    RelayNetwork.Kind.CRADLE,
                    "the kind updated");
            // A ghost: the block goes without its removal callback; the chunk's load finds it.
            level.setBlock(
                    members.get(31),
                    Blocks.AIR.defaultBlockState(),
                    Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS);
            RelayService.processQueue(level);
            helper.assertTrue(
                    RelayService.membership(level, members.get(31)) != null, "still persisted");
            RelayService.queueChunk(level, ChunkPos.containing(members.get(31)));
            RelayService.processQueue(level);
            helper.assertTrue(
                    RelayService.membership(level, members.get(31)) == null, "the ghost removed");
            members.remove(31);
            clear(level, members);
            // A cancelled placement leaves no membership.
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VisContent.visRelay()));
            places = e -> e.setCanceled(true);
            try {
                useOn(p, floor, Direction.UP);
            } finally {
                places = e -> {};
            }
            RelayService.processQueue(level);
            helper.assertTrue(level.getBlockState(extra).isAir(), "rolled back");
            helper.assertTrue(RelayService.membership(level, extra) == null, "no membership");
            // /clone copies a member, which the queue admits.
            level.setBlock(extra, VisContent.visRelay().defaultBlockState(), Block.UPDATE_ALL);
            BlockPos copy = extra.east(2);
            level.getServer()
                    .getCommands()
                    .performPrefixedCommand(
                            level.getServer()
                                    .createCommandSourceStack()
                                    .withLevel(level)
                                    .withSuppressedOutput(),
                            String.format(
                                    java.util.Locale.ROOT,
                                    "clone %d %d %d %d %d %d %d %d %d",
                                    extra.getX(),
                                    extra.getY(),
                                    extra.getZ(),
                                    extra.getX(),
                                    extra.getY(),
                                    extra.getZ(),
                                    copy.getX(),
                                    copy.getY(),
                                    copy.getZ()));
            RelayService.processQueue(level);
            helper.assertTrue(level.getBlockState(copy).is(VisContent.visRelay()), "cloned");
            helper.assertValueEqual(
                    Objects.requireNonNull(RelayService.membership(level, copy)).size(),
                    2,
                    "the clone is admitted and links");
            clear(level, List.of(extra, copy));
        } finally {
            clear(level, members);
            VisGameTests.release(helper, origin);
        }
        helper.succeed();
    }

    /**
     * A range change merging two 20-member components keeps the 32 lowest linked and leaves 8
     * unlinked, which say so; the old range relinks them.
     */
    private static void relayRangeReload(GameTestHelper helper) {
        BlockPos origin = VisGameTests.ownCell(helper);
        ServerLevel level = helper.getLevel();
        BlockPos base =
                new BlockPos((origin.getX() >> 4) << 4, origin.getY(), (origin.getZ() >> 4) << 4);
        ChunkPos east = new ChunkPos((base.getX() >> 4) + 1, base.getZ() >> 4);
        level.setChunkForced(east.x(), east.z(), true);
        level.getChunk(east.x(), east.z());
        List<BlockPos> members = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            members.add(base.offset(i, 1, 2));
            members.add(base.offset(i, 1, 6));
        }
        try {
            withTuning(
                    "{\"relay.range\": 2.0}",
                    () -> {
                        members.forEach(
                                p ->
                                        level.setBlock(
                                                p,
                                                VisContent.visRelay().defaultBlockState(),
                                                Block.UPDATE_ALL));
                        RelayService.processQueue(level);
                        helper.assertValueEqual(
                                Objects.requireNonNull(
                                                RelayService.membership(level, members.get(0)))
                                        .size(),
                                20,
                                "two components of 20");
                        withTuning(
                                "{\"relay.range\": 4.0}",
                                () -> {
                                    RelayService.processQueue(level);
                                    List<BlockPos> unlinked = new ArrayList<>();
                                    for (BlockPos m : members) {
                                        if (Objects.requireNonNull(
                                                        RelayService.membership(level, m))
                                                .unlinked()) {
                                            unlinked.add(m);
                                        }
                                    }
                                    helper.assertValueEqual(unlinked.size(), 8, "8 unlinked");
                                    List<Long> keys = new ArrayList<>();
                                    members.forEach(m -> keys.add(m.asLong()));
                                    keys.sort(null);
                                    for (BlockPos u : unlinked) {
                                        helper.assertTrue(
                                                keys.indexOf(u.asLong()) >= 32,
                                                "only the highest keys are unlinked");
                                    }
                                    ServerPlayer p = player(helper);
                                    p.setShiftKeyDown(true);
                                    helper.assertValueEqual(
                                            lines(() -> useOn(p, unlinked.get(0), Direction.UP)),
                                            List.of(VisLang.STATUS_UNLINKED),
                                            "an unlinked member says so");
                                    helper.assertTrue(
                                            level.getBlockState(unlinked.get(0))
                                                    .is(VisContent.visRelay()),
                                            "it stays placed");
                                });
                        RelayService.processQueue(level);
                        helper.assertTrue(
                                members.stream()
                                        .noneMatch(
                                                m ->
                                                        Objects.requireNonNull(
                                                                        RelayService.membership(
                                                                                level, m))
                                                                .unlinked()),
                                "relinked at the old range");
                        clear(level, members);
                    });
            RelayService.processQueue(level);
        } finally {
            clear(level, members);
            level.setChunkForced(east.x(), east.z(), false);
            VisGameTests.release(helper, origin);
        }
        helper.succeed();
    }

    /** Insert, take, the status lines and the drop on breaking. */
    private static void cradleUse(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        level.setBlock(pos, VisContent.wandCradle().defaultBlockState(), Block.UPDATE_ALL);
        try {
            WandCradleBlockEntity c =
                    (WandCradleBlockEntity) Objects.requireNonNull(level.getBlockEntity(pos));
            ServerPlayer p = player(helper);
            p.setShiftKeyDown(true);
            helper.assertValueEqual(
                    lines(() -> useOn(p, pos, Direction.UP)),
                    List.of(VisLang.STATUS_CRADLE_EMPTY),
                    "empty");
            p.setShiftKeyDown(false);
            ItemStack w = VisGameTests.wand();
            p.setItemInHand(InteractionHand.MAIN_HAND, w);
            useOn(p, pos, Direction.UP);
            helper.assertTrue(c.wand().is(VisContent.wand()), "inserted");
            helper.assertTrue(p.getMainHandItem().isEmpty(), "out of the hand");
            p.setItemInHand(InteractionHand.MAIN_HAND, VisGameTests.wand());
            helper.assertValueEqual(useOn(p, pos, Direction.UP), InteractionResult.PASS, "passes");
            helper.assertTrue(p.getMainHandItem().is(VisContent.wand()), "a second wand stays");
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            p.setShiftKeyDown(true);
            helper.assertValueEqual(
                    lines(() -> useOn(p, pos, Direction.UP)),
                    List.of(VisLang.STATUS_CRADLE_IDLE),
                    "nothing reaches it");
            p.setShiftKeyDown(false);
            useOn(p, pos, Direction.UP);
            helper.assertTrue(p.getMainHandItem().is(VisContent.wand()), "taken into the hand");
            helper.assertTrue(c.wand().isEmpty(), "the cradle is empty");
            c.setWand(VisGameTests.wand());
            level.destroyBlock(pos, false);
            helper.assertTrue(
                    level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1)).stream()
                            .anyMatch(e -> e.getItem().is(VisContent.wand())),
                    "breaking drops the wand");
            level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1))
                    .forEach(ItemEntity::discard);
        } finally {
            clear(level, List.of(pos));
        }
        helper.succeed();
    }

    /** The cradle, the jar's component and {@code vftv_relays} save and reload; unreadable kept. */
    private static void relayPersistence(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        level.setBlock(pos, VisContent.wandCradle().defaultBlockState(), Block.UPDATE_ALL);
        WandCradleBlockEntity c =
                (WandCradleBlockEntity) Objects.requireNonNull(level.getBlockEntity(pos));
        c.setWand(VisGameTests.wand());
        CompoundTag saved = c.saveWithFullMetadata(level.registryAccess());
        helper.assertValueEqual(
                saved.getCompoundOrEmpty("cradle").getIntOr("format", 0), 1, "format 1");
        WandCradleBlockEntity loaded =
                new WandCradleBlockEntity(pos, VisContent.wandCradle().defaultBlockState());
        loaded.loadWithComponents(
                TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved));
        helper.assertTrue(ItemStack.matches(loaded.wand(), c.wand()), "the wand reloads");
        CompoundTag future = saved.copy();
        future.getCompoundOrEmpty("cradle").putInt("format", 7);
        future.getCompoundOrEmpty("cradle").putString("from_the_future", "kept");
        WandCradleBlockEntity unreadable =
                new WandCradleBlockEntity(pos, VisContent.wandCradle().defaultBlockState());
        unreadable.loadWithComponents(
                TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), future));
        helper.assertTrue(unreadable.unreadable(), "a future format is unreadable");
        CompoundTag resaved = unreadable.saveWithFullMetadata(level.registryAccess());
        helper.assertValueEqual(
                resaved.getCompoundOrEmpty("cradle"),
                future.getCompoundOrEmpty("cradle"),
                "the whole record kept raw");
        CompoundTag badType = saved.copy();
        badType.getCompoundOrEmpty("cradle").putString("format", "future");
        WandCradleBlockEntity typed =
                new WandCradleBlockEntity(pos, VisContent.wandCradle().defaultBlockState());
        typed.loadWithComponents(
                TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), badType));
        helper.assertTrue(typed.unreadable(), "a malformed format is unreadable");
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1))
                .forEach(ItemEntity::discard);
        RelayService.processQueue(level);

        ItemStack jar = new ItemStack(VisContent.bellJar());
        NodeState node =
                NodeState.born(
                        9,
                        NodeState.Origin.NATURAL,
                        WINDWARD,
                        NodeState.Clarity.FAINT,
                        windward(20, 10),
                        3,
                        VisTuning.DEFAULTS);
        jar.set(VisContent.captured(), new Retained.Read<>(new CapturedNode(4, node)));
        var out =
                TagValueOutput.createWithContext(
                        ProblemReporter.DISCARDING, level.registryAccess());
        out.store("stack", ItemStack.CODEC, jar);
        ItemStack back =
                TagValueInput.create(
                                ProblemReporter.DISCARDING,
                                level.registryAccess(),
                                out.buildResult())
                        .read("stack", ItemStack.CODEC)
                        .orElseThrow();
        helper.assertTrue(ItemStack.matches(jar, back), "the captured node reloads");
        helper.assertTrue(
                RelayService.SLOT.get(level) instanceof Retained.Read<RelayLedger>,
                "vftv_relays reads");
        helper.succeed();
    }
}
