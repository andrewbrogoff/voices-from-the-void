package com.andrewbrogoff.vftv.core.vis;

import com.andrewbrogoff.vftv.api.research.Research;
import com.andrewbrogoff.vftv.api.research.ResearchKeys;
import com.andrewbrogoff.vftv.api.vis.NodeCause;
import com.andrewbrogoff.vftv.api.vis.NodeEventKind;
import com.andrewbrogoff.vftv.api.vis.VisLimits;
import com.andrewbrogoff.vftv.core.common.OperationGuard;
import com.andrewbrogoff.vftv.core.common.RawRecordCodec.Retained;
import com.andrewbrogoff.vftv.core.research.ResearchService;
import com.andrewbrogoff.vftv.core.text.StatusLines;
import com.andrewbrogoff.vftv.core.text.model.NumberFormats;
import com.andrewbrogoff.vftv.core.text.model.SharedMessages;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Moving nodes (wands spec, "Moving nodes: the Bell Jar"): a capture takes a node whole into an
 * empty jar under a new token; a release sets it down at the clicked face, provisionally installed
 * while the placement event is asked, then committed. Both run in the jar's {@code
 * onItemUseFirst}, so this class controls every mutation and its order.
 */
public final class BellJar {
    /** {@code vftv:moving_nodes}: the gate on capture and release. */
    public static final ResourceKey<Research> MOVING_NODES =
            ResourceKey.create(
                    ResearchKeys.REGISTRY, Identifier.fromNamespaceAndPath("vftv", "moving_nodes"));

    /** Neither neighbour updates, nor a send to clients, nor shape updates. */
    private static final int QUIET = Block.UPDATE_KNOWN_SHAPE;

    /** The provisional-install scope, per level. */
    private static final OperationGuard INSTALL = new OperationGuard("bell_jar");

    private BellJar() {}

    /**
     * Whether a provisional install is open in this level: the node block entity's load queues no
     * reconciliation and the removal hook does nothing.
     */
    static boolean installing(ServerLevel level) {
        return INSTALL.isHeld(level);
    }

    /**
     * A capture (an empty jar on a node). Each check refuses in order; a claim mod's cancellation
     * of the break event speaks for itself.
     */
    public static InteractionResult capture(ServerPlayer player, ItemStack jar, BlockPos pos) {
        ServerLevel level = player.level();
        MinecraftServer server = level.getServer();
        if (!ResearchService.isLearned(player, MOVING_NODES)) {
            return refuse(player, VisLang.JAR_UNLEARNED);
        }
        NodeBlockEntity be = NodeService.placedNode(level, pos);
        NodeState found = be == null ? null : be.state();
        NodeTypeEntry type = found == null ? null : NodeService.type(level, found.type());
        if (be == null || type == null) {
            return refuse(player, VisLang.JAR_UNKNOWN);
        }
        if (type.anchored()) {
            return refuse(player, VisLang.JAR_ANCHORED);
        }
        if (!level.mayInteract(player, pos) || server.isUnderSpawnProtection(level, pos, player)) {
            return InteractionResult.CONSUME;
        }
        if (!VisEvents.protection().mayBreak(player, level, pos, level.getBlockState(pos))
                || NodeService.placedNode(level, pos) != be) {
            return InteractionResult.CONSUME;
        }
        NodeLedger ledger = java.util.Objects.requireNonNull(NodeService.ledger(server));
        NodeState settled = NodeService.settle(level, be);
        long id = settled.id();
        boolean starter = NodeService.isStarter(ledger, id);
        long token = ledger.token();
        ledger.put(java.util.Objects.requireNonNull(ledger.get(id)).carried(token));
        NodeService.indexRemove(level.dimension(), id, pos);
        jar.set(VisContent.captured(), new Retained.Read<>(new CapturedNode(token, settled)));
        level.removeBlock(pos, false); // the removal hook sees CARRIED and does nothing
        NodeService.markDirty(server);
        NodeService.post(NodeEventKind.CAPTURED, id, null, NodeCause.PLAYER);
        play(level, pos, VisContent.JAR_CAPTURE);
        NodeTaps.wakeAround(level, pos);
        if (starter) {
            VisRulesService.sendToAll(server);
        }
        return InteractionResult.CONSUME;
    }

    /**
     * A release (a filled jar on any block face) into the adjacent {@code target}. The click is
     * consumed even when refused.
     *
     * @param against the clicked block's state, for the placement event
     */
    public static InteractionResult release(
            ServerPlayer player, ItemStack jar, BlockPos target, BlockState against) {
        ServerLevel level = player.level();
        MinecraftServer server = level.getServer();
        if (!ResearchService.isLearned(player, MOVING_NODES)) {
            return refuse(player, VisLang.JAR_UNLEARNED);
        }
        NodeLedger ledger = NodeService.ledger(server);
        if (!(jar.get(VisContent.captured()) instanceof Retained.Read<CapturedNode> read)
                || ledger == null) {
            return refuse(player, SharedMessages.UNREADABLE);
        }
        CapturedNode held = read.value();
        long id = held.node().id();
        NodeLedger.Entry entry = ledger.get(id);
        boolean recovery = entry == null;
        boolean valid =
                entry == null
                        ? player.isCreative()
                                && Commands.LEVEL_GAMEMASTERS.check(player.permissions())
                        : entry.state() == NodeLedger.State.CARRIED
                                && entry.token() == held.token();
        if (!valid) {
            return refuse(player, VisLang.JAR_STALE);
        }
        if (!level.getBlockState(target).isAir()
                || level.isOutsideBuildHeight(target)
                || !level.getWorldBorder().isWithinBounds(target)) {
            return refuse(player, VisLang.JAR_NO_ROOM);
        }
        double spacing = VisRulesService.tuning().minSpacing();
        var index = NodeService.index(level.dimension());
        if (index.closest(
                        target.getX() + 0.5,
                        target.getY() + 0.5,
                        target.getZ() + 0.5,
                        (int) Math.ceil(spacing))
                < spacing) {
            return refuse(
                    player,
                    Component.translatable(
                            VisLang.JAR_TOO_CLOSE,
                            NumberFormats.trimmed(Math.round(spacing * 100), 2)));
        }
        if (index.count(target.getX(), target.getZ()) >= VisLimits.MAX_NODES_PER_CELL) {
            return refuse(player, VisLang.JAR_CROWDED);
        }
        if (!level.mayInteract(player, target)
                || server.isUnderSpawnProtection(level, target, player)) {
            return InteractionResult.CONSUME;
        }
        NodeState state = held.node().settle(level.getGameTime(), VisRulesService.tuning());
        try (OperationGuard.Scope scope = INSTALL.tryEnter(level)) {
            if (scope == null) {
                return refuse(player, SharedMessages.BUSY);
            }
            boolean placed =
                    VisEvents.protection()
                            .mayPlace(
                                    player,
                                    level,
                                    target,
                                    against,
                                    () -> {
                                        level.setBlock(
                                                target,
                                                VisContent.node().defaultBlockState(),
                                                QUIET);
                                        if (level.getBlockEntity(target)
                                                instanceof NodeBlockEntity be) {
                                            be.setState(state);
                                        }
                                    });
            if (!placed
                    || !installed(level, target, id)
                    || !java.util.Objects.equals(ledger.get(id), entry)) {
                if (level.getBlockState(target).is(VisContent.node())) {
                    level.setBlock(target, Blocks.AIR.defaultBlockState(), QUIET);
                }
                return InteractionResult.CONSUME; // the claim mod speaks
            }
        }
        NodeState.Origin origin = entry == null ? held.node().origin() : entry.origin();
        boolean starter = NodeService.isStarter(ledger, id);
        ledger.put(NodeLedger.Entry.placed(id, origin, level.dimension(), target));
        if (recovery) {
            ledger.advancePast(id);
            ledger.advanceTokenPast(held.token());
        }
        NodeService.indexAdd(level.dimension(), id, target);
        jar.remove(VisContent.captured());
        NodeService.markDirty(server);
        BlockState node = level.getBlockState(target);
        level.sendBlockUpdated(target, node, node, Block.UPDATE_CLIENTS);
        GlobalPos where = GlobalPos.of(level.dimension(), target.immutable());
        if (recovery) {
            NodeService.post(NodeEventKind.CREATED, id, where, NodeCause.COMMAND);
        } else {
            NodeService.post(NodeEventKind.PLACED, id, where, NodeCause.PLAYER);
        }
        play(level, target, VisContent.JAR_RELEASE);
        NodeTaps.wakeAround(level, target);
        if (starter) {
            VisRulesService.sendToAll(server);
        }
        return InteractionResult.CONSUME;
    }

    /** Whether the provisional node still stands at {@code target} holding {@code id}. */
    private static boolean installed(ServerLevel level, BlockPos target, long id) {
        return level.getBlockEntity(target) instanceof NodeBlockEntity be
                && be.state() != null
                && be.state().id() == id;
    }

    private static InteractionResult refuse(ServerPlayer player, String key) {
        return refuse(player, Component.translatable(key));
    }

    private static InteractionResult refuse(ServerPlayer player, Component line) {
        StatusLines.send(player, line);
        return InteractionResult.CONSUME;
    }

    private static void play(ServerLevel level, BlockPos pos, int sound) {
        level.playSound(null, pos, VisContent.sound(sound), SoundSource.BLOCKS, 1, 1);
    }
}
