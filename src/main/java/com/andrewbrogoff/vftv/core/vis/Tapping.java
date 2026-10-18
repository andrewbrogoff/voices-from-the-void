package com.andrewbrogoff.vftv.core.vis;

import com.andrewbrogoff.vftv.api.research.ResearchKeys;
import com.andrewbrogoff.vftv.core.research.ResearchService;
import com.andrewbrogoff.vftv.core.text.StatusLines;
import com.andrewbrogoff.vftv.core.vis.model.VisTuning;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Hand-tapping (wands spec, "Hand-tapping"): hold use on a node with a wand, and vis moves from the
 * node into the wand's pools on the aligned pulses. One session per player, in transient server
 * memory; the slot holds a tap session or another feature's session (the foci spec's beam).
 */
public final class Tapping {
    /** {@code vftv:tapping}: the gate on every draw. */
    public static final ResourceKey<com.andrewbrogoff.vftv.api.research.Research> TAPPING =
            ResourceKey.create(
                    ResearchKeys.REGISTRY, Identifier.fromNamespaceAndPath("vftv", "tapping"));

    /** The drone's retrigger period, in ticks (a fixed feedback constant). */
    public static final int DRONE_TICKS = 40;

    /** Tap glints per pulse (a fixed feedback constant). */
    public static final int GLINTS_PER_PULSE = 2;

    private static final int START = 0;
    private static final int STOP = 1;
    private static final int FULL = 2;
    private static final int DRONE = 3;

    /** A player's session: a tap, or another feature's (the foci spec's beam). */
    public interface Session {
        /** The session's kind, so the foci feature can sync its beam state; a tap sends nothing. */
        String kind();

        /** One use tick on the server. */
        void useTick(ServerPlayer player, ItemStack stack);

        /** The use ended (released, stopped, logout); clears nothing itself. */
        void stopped(ServerPlayer player);
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private Tapping() {}

    /** The player's session, or null. */
    public static @Nullable Session session(ServerPlayer player) {
        return SESSIONS.get(player.getUUID());
    }

    /** Puts a session in the player's slot, replacing any. */
    public static void open(ServerPlayer player, Session session) {
        SESSIONS.put(player.getUUID(), session);
    }

    /** Ends the player's session: it is told once, then cleared. */
    public static void close(ServerPlayer player) {
        Session s = SESSIONS.remove(player.getUUID());
        if (s != null) {
            s.stopped(player);
        }
    }

    /** Clears the player's slot at logout. */
    public static void onLogout(ServerPlayer player) {
        close(player);
    }

    /** Forgets every session, at server stop. */
    public static void clear() {
        SESSIONS.clear();
    }

    /** A tap session: the node, the hand, the server's stack object and slot, and the start. */
    static final class Tap implements Session {
        final long nodeId;
        final ResourceKey<Level> dimension;
        final BlockPos pos;
        final InteractionHand hand;
        final ItemStack stack;
        final int slot; // the selected hotbar index, or -1 for the offhand
        final long started;
        boolean full;

        Tap(
                long nodeId,
                ResourceKey<Level> dimension,
                BlockPos pos,
                InteractionHand hand,
                ItemStack stack,
                int slot,
                long started) {
            this.nodeId = nodeId;
            this.dimension = dimension;
            this.pos = pos;
            this.hand = hand;
            this.stack = stack;
            this.slot = slot;
            this.started = started;
        }

        @Override
        public String kind() {
            return "tap";
        }

        @Override
        public void useTick(ServerPlayer player, ItemStack stack) {
            pulse(player, this);
        }

        @Override
        public void stopped(ServerPlayer player) {
            play(player, full ? FULL : STOP);
        }
    }

    /**
     * The start (wands spec, "Start"), from the wand's use on a node: each refusal shows on the
     * status line and consumes the click.
     */
    public static InteractionResult start(
            ServerPlayer player, InteractionHand hand, ItemStack stack, BlockPos pos) {
        ServerLevel level = player.level();
        WandParts parts = WandParts.of(level.registryAccess());
        if (!ResearchService.isLearned(player, TAPPING)) {
            return refuse(player, VisLang.TAP_UNLEARNED);
        }
        if (!(WandState.of(stack, parts) instanceof WandState.Ready wand)) {
            return refuse(player, VisLang.TAP_INERT);
        }
        NodeBlockEntity be = NodeService.placedNode(level, pos);
        NodeState state = be == null ? null : be.state();
        if (be == null || state == null || NodeService.type(level, state.type()) == null) {
            return refuse(player, VisLang.TAP_UNKNOWN);
        }
        state = NodeService.settle(level, be);
        boolean drawable = false;
        boolean room = false;
        for (NodeState.NodeAspect a : state.aspects()) {
            if (a.max() > 0 && parts.isVis(a.aspect().toString())) {
                drawable = true;
                room |= wand.pool(aspectKey(a.aspect())) < wand.rod().capacity();
            }
        }
        if (!drawable) {
            return refuse(player, VisLang.TAP_NOTHING);
        }
        if (!room) {
            return refuse(player, VisLang.TAP_FULL);
        }
        close(player);
        player.startUsingItem(hand);
        int slot = hand == InteractionHand.MAIN_HAND ? player.getInventory().getSelectedSlot() : -1;
        open(
                player,
                new Tap(
                        state.id(),
                        level.dimension(),
                        pos.immutable(),
                        hand,
                        player.getItemInHand(hand),
                        slot,
                        level.getGameTime()));
        play(player, START);
        return InteractionResult.CONSUME;
    }

    private static InteractionResult refuse(ServerPlayer player, String key) {
        StatusLines.send(player, Component.translatable(key));
        return InteractionResult.CONSUME;
    }

    /** Whether the client should start its use on a node, from its synced state. */
    public static boolean clientMayStart(
            net.minecraft.world.entity.player.Player player, ItemStack stack, BlockPos pos) {
        Level level = player.level();
        WandParts parts = WandParts.of(level.registryAccess());
        if (!ResearchService.isLearned(player, TAPPING)
                || !(WandState.of(stack, parts) instanceof WandState.Ready wand)
                || !(level.getBlockEntity(pos) instanceof NodeBlockEntity be)
                || be.state() == null
                || NodeService.type(level, be.state().type()) == null) {
            return false;
        }
        for (NodeState.NodeAspect a : be.state().aspects()) {
            if (a.max() > 0
                    && parts.isVis(a.aspect().toString())
                    && wand.pool(aspectKey(a.aspect())) < wand.rod().capacity()) {
                return true;
            }
        }
        return false;
    }

    /** One server use tick of a tap: a pulse on the aligned ticks, the drone every 40 ticks. */
    static void pulse(ServerPlayer player, Tap tap) {
        ServerLevel level = player.level();
        long now = level.getGameTime();
        VisTuning tuning = VisRulesService.tuning();
        if (now > tap.started && (now - tap.started) % DRONE_TICKS == 0) {
            play(player, DRONE);
        }
        if (now % tuning.tapInterval() != 0) {
            return;
        }
        WandParts parts = WandParts.of(level.registryAccess());
        ItemStack held = player.getItemInHand(tap.hand);
        boolean sameSlot =
                tap.hand == InteractionHand.OFF_HAND
                        || player.getInventory().getSelectedSlot() == tap.slot;
        if (!sameObject(held, tap.stack)
                || !sameSlot
                || !(WandState.of(held, parts) instanceof WandState.Ready wand)
                || !ResearchService.isLearned(player, TAPPING)
                || !level.dimension().equals(tap.dimension)
                || !level.mayInteract(player, tap.pos)
                || level.getServer().isUnderSpawnProtection(level, tap.pos, player)
                || !looksAt(player, tap.pos)) {
            player.stopUsingItem();
            return;
        }
        NodeBlockEntity be = NodeService.placedNode(level, tap.pos);
        NodeState state = be == null ? null : be.state();
        if (be == null
                || state == null
                || state.id() != tap.nodeId
                || NodeService.type(level, state.type()) == null) {
            player.stopUsingItem();
            return;
        }
        state = NodeService.settle(level, be);
        Map<String, Integer> pools = new HashMap<>();
        List<NodeState.NodeAspect> next = new ArrayList<>();
        boolean moved = false;
        boolean full = true;
        for (NodeState.NodeAspect a : state.aspects()) {
            String id = a.aspect().toString();
            if (!parts.isVis(id) || a.max() == 0) {
                next.add(a);
                continue;
            }
            int pool = wand.pool(aspectKey(a.aspect()));
            int room = wand.rod().capacity() - pool;
            full &= room == 0;
            int take = Math.min(tuning.tapAmount(), Math.min(a.current(), room));
            if (take > 0) {
                moved = true;
                pools.put(id, pool + take);
            }
            next.add(new NodeState.NodeAspect(a.aspect(), a.max(), a.current() - take));
        }
        if (moved) {
            be.setState(state.withAspects(next));
            WandState.write(held, wand, pools, parts);
            NodeService.markForSync(level, tap.pos);
            return;
        }
        // Nothing moved: every aspect is full in the wand, or those with room are empty.
        if (full) {
            tap.full = true;
        } else {
            StatusLines.send(player, Component.translatable(VisLang.TAP_EMPTY));
        }
        player.stopUsingItem();
    }

    /**
     * Whether the hand still holds the session's own stack object: an equal copy (another wand, a
     * resync) does not count, while the tap's in-place writes keep the object.
     */
    @SuppressWarnings("ReferenceEquality")
    static boolean sameObject(ItemStack held, ItemStack session) {
        return held == session;
    }

    /** Whether a block raycast from the eye, within reach, hits the position. */
    static boolean looksAt(ServerPlayer player, BlockPos pos) {
        double reach = player.blockInteractionRange();
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0f).scale(reach));
        BlockHitResult hit =
                player.level()
                        .clip(
                                new ClipContext(
                                        eye,
                                        end,
                                        ClipContext.Block.OUTLINE,
                                        ClipContext.Fluid.NONE,
                                        player));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos);
    }

    private static void play(ServerPlayer player, int sound) {
        SoundEvent event = VisContent.sound(sound);
        player.level()
                .playSound(
                        null,
                        player.getX(),
                        player.getEyeY(),
                        player.getZ(),
                        event,
                        SoundSource.PLAYERS,
                        1,
                        1);
    }

    private static ResourceKey<com.andrewbrogoff.vftv.api.aspect.Aspect> aspectKey(Identifier id) {
        return ResourceKey.create(com.andrewbrogoff.vftv.api.aspect.AspectKeys.REGISTRY, id);
    }
}
