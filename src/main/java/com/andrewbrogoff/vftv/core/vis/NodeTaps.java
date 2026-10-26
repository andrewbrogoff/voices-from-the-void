package com.andrewbrogoff.vftv.core.vis;

import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.api.vis.NodeCause;
import com.andrewbrogoff.vftv.api.vis.NodeEventKind;
import com.andrewbrogoff.vftv.api.vis.VisLimits;
import com.andrewbrogoff.vftv.core.essentia.EssentiaService;
import com.andrewbrogoff.vftv.core.text.model.NumberFormats;
import com.andrewbrogoff.vftv.core.vis.model.Cultivate;
import com.andrewbrogoff.vftv.core.vis.model.VisTuning;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The Node Tap's link to its node (wands spec, "Members", "Cultivation"): which tap is active for a
 * node, what the node would accept now, the state after an insert, and the root commit's
 * publication. The platform's essentia adapter journals the node's state around these calls.
 */
public final class NodeTaps {
    /** The absorb sound's cooldown per node, in ticks (a fixed feedback constant). */
    public static final int ABSORB_COOLDOWN = 20;

    private static final Identifier SONUS = AspectKeys.SONUS.identifier();
    private static final Map<Long, Long> LAST_ABSORB = new HashMap<>();

    private NodeTaps() {}

    /**
     * The tap facing {@code node} with the lowest {@code asLong()}, other than {@code except}; null
     * when none faces it.
     */
    public static @Nullable BlockPos tappedBy(
            BlockGetter level, BlockPos node, @Nullable BlockPos except) {
        BlockPos best = null;
        for (Direction d : Direction.values()) {
            BlockPos p = node.relative(d);
            if (level instanceof Level l && !l.isLoaded(p)) {
                continue; // never load a chunk to look
            }
            BlockState s = level.getBlockState(p);
            if (s.is(VisContent.nodeTap())
                    && s.getValue(NodeTapBlock.FACING) == d.getOpposite()
                    && (except == null || !p.equals(except))
                    && (best == null || p.asLong() < best.asLong())) {
                best = p.immutable();
            }
        }
        return best;
    }

    /** Whether the tap at {@code tap} is the active one for the node it faces. */
    public static boolean active(BlockGetter level, BlockPos tap) {
        BlockState s = level.getBlockState(tap);
        if (!s.is(VisContent.nodeTap())) {
            return false;
        }
        BlockPos node = tap.relative(s.getValue(NodeTapBlock.FACING));
        if (level instanceof Level l && !l.isLoaded(node)) {
            return false;
        }
        BlockPos first = tappedBy(level, node, null);
        return level.getBlockState(node).is(VisContent.node()) && first != null && tap.equals(first);
    }

    /**
     * The node the tap at {@code tap} feeds: the tap is active, and the node's entry is {@code
     * PLACED} there with its type present. Null otherwise (the tap is idle).
     */
    public static @Nullable NodeBlockEntity node(ServerLevel level, BlockPos tap) {
        if (!active(level, tap)) {
            return null;
        }
        BlockPos at = tap.relative(level.getBlockState(tap).getValue(NodeTapBlock.FACING));
        NodeBlockEntity be = NodeService.placedNode(level, at);
        NodeState state = be == null ? null : be.state();
        return state != null && NodeService.type(level, state.type()) != null ? be : null;
    }

    /** The units of {@code aspect} the node would accept now, from its settled state. */
    public static int room(ServerLevel level, NodeBlockEntity be, Identifier aspect) {
        NodeState state = be.state();
        if (state == null) {
            return 0;
        }
        return room(
                state.settle(level.getGameTime(), VisRulesService.tuning()),
                aspect,
                WandParts.of(level.registryAccess()).isVis(aspect.toString()),
                VisRulesService.tuning());
    }

    /** The room of a settled state (wands spec, "Cultivation"). */
    static int room(NodeState s, Identifier aspect, boolean vis, VisTuning t) {
        if (aspect.equals(SONUS)) {
            return Cultivate.sonusRoom(
                    s.clarity().index(),
                    s.cultivation().sonus(),
                    t.integer("cultivate.clarity_cost"));
        }
        if (!vis) {
            return 0;
        }
        NodeState.NodeAspect held = s.byAspect().get(aspect.toString());
        if (held == null && s.aspects().size() >= VisLimits.MAX_NODE_ASPECTS) {
            return 0;
        }
        return Cultivate.visRoom(
                held == null ? 0 : held.max(),
                s.cultivation().counters().getOrDefault(aspect, 0),
                t.integer("cultivate.max_capacity"),
                t.integer("cultivate.per_max"));
    }

    /**
     * The settled state after accepting {@code k} units of {@code aspect} (at most its room): a
     * missing vis aspect is added at max 0 and current 0; a clarity step settles, changes and
     * rebases.
     */
    static NodeState accept(NodeState settled, Identifier aspect, int k, long now, VisTuning t) {
        if (aspect.equals(SONUS)) {
            Cultivate.Clarity c =
                    Cultivate.insertSonus(
                            settled.clarity().index(),
                            settled.cultivation().sonus(),
                            k,
                            t.integer("cultivate.clarity_cost"));
            return new NodeState(
                            settled.id(),
                            settled.origin(),
                            settled.type(),
                            NodeState.Clarity.byIndex(c.clarity()),
                            settled.aspects(),
                            settled.regen(),
                            new NodeState.Cultivation(
                                    settled.cultivation().counters(), c.sonus()))
                    .settle(now, t);
        }
        List<NodeState.NodeAspect> aspects = new ArrayList<>(settled.aspects());
        int at = -1;
        for (int i = 0; i < aspects.size(); i++) {
            if (aspects.get(i).aspect().equals(aspect)) {
                at = i;
            }
        }
        if (at < 0) {
            aspects.add(new NodeState.NodeAspect(aspect, 0, 0));
            aspects.sort((x, y) -> x.aspect().toString().compareTo(y.aspect().toString()));
            for (int i = 0; i < aspects.size(); i++) {
                if (aspects.get(i).aspect().equals(aspect)) {
                    at = i;
                }
            }
        }
        NodeState.NodeAspect held = aspects.get(at);
        SortedMap<Identifier, Integer> counters = new TreeMap<>(settled.cultivation().counters());
        Cultivate.Vis v =
                Cultivate.insertVis(
                        held.max(),
                        counters.getOrDefault(aspect, 0),
                        k,
                        t.integer("cultivate.max_capacity"),
                        t.integer("cultivate.per_max"));
        aspects.set(at, new NodeState.NodeAspect(aspect, v.max(), held.current()));
        counters.put(aspect, v.counter());
        return new NodeState(
                settled.id(),
                settled.origin(),
                settled.type(),
                settled.clarity(),
                aspects,
                settled.regen(),
                new NodeState.Cultivation(counters, settled.cultivation().sonus()));
    }

    /**
     * Accepts up to {@code amount} units of {@code aspect} into the node in place: settles, then
     * writes the state after the insert. The caller has journalled the state first.
     *
     * @return the units accepted
     */
    public static int insert(ServerLevel level, NodeBlockEntity be, Identifier aspect, int amount) {
        int k = Math.min(amount, room(level, be, aspect));
        if (k <= 0) {
            return 0;
        }
        VisTuning t = VisRulesService.tuning();
        NodeState settled = NodeService.settle(level, be);
        be.setState(accept(settled, aspect, k, level.getGameTime(), t));
        return k;
    }

    /** Restores a journalled state (a transaction's abort). */
    public static void restore(NodeBlockEntity be, NodeState state) {
        be.setState(state);
    }

    /**
     * A cultivation transaction's root commit: the node is marked for sync, {@code
     * NodeEvent(CULTIVATED)} is posted, and the absorb sound plays (at most once per node per 20
     * ticks), with the clarity sound if clarity rose.
     *
     * @param before the node's state before the transaction
     */
    public static void committed(ServerLevel level, NodeBlockEntity be, NodeState before) {
        NodeState after = be.state();
        if (be.isRemoved() || after == null) {
            return;
        }
        BlockPos pos = be.getBlockPos();
        NodeService.markForSync(level, pos);
        NodeService.post(
                NodeEventKind.CULTIVATED,
                after.id(),
                GlobalPos.of(level.dimension(), pos.immutable()),
                NodeCause.PLAYER);
        long now = level.getGameTime();
        Long last = LAST_ABSORB.get(after.id());
        if (last == null || now - last >= ABSORB_COOLDOWN || now < last) {
            LAST_ABSORB.put(after.id(), now);
            play(level, pos, VisContent.ABSORB);
        }
        if (after.clarity().index() > before.clarity().index()) {
            play(level, pos, VisContent.CLARITY_RISE);
        }
    }

    /** Wakes the essentia lines at every tap facing {@code node}: what it accepts changed. */
    public static void wakeAround(ServerLevel level, BlockPos node) {
        for (Direction d : Direction.values()) {
            BlockPos p = node.relative(d);
            if (!level.isLoaded(p)) {
                continue;
            }
            BlockState s = level.getBlockState(p);
            if (s.is(VisContent.nodeTap()) && s.getValue(NodeTapBlock.FACING) == d.getOpposite()) {
                EssentiaService.wakeAt(level, p);
            }
        }
    }

    /** The tap's status line: its node's type, clarity and id and its Sonus progress, or idle. */
    static Component status(ServerLevel level, BlockPos tap) {
        NodeBlockEntity be = node(level, tap);
        if (be == null) {
            return Component.translatable(VisLang.STATUS_TAP_IDLE);
        }
        NodeState s = NodeService.settle(level, be);
        return Component.translatable(
                VisLang.STATUS_TAP_LISTENING,
                Component.translatable(s.clarity().langKey()),
                NodeService.typeName(s.type()),
                NumberFormats.integer(s.id()),
                NumberFormats.integer(s.cultivation().sonus()),
                NumberFormats.integer(VisRulesService.tuning().integer("cultivate.clarity_cost")));
    }

    /** Forgets the sound cooldowns, at server stop. */
    static void clear() {
        LAST_ABSORB.clear();
    }

    private static void play(ServerLevel level, BlockPos pos, int sound) {
        level.playSound(null, pos, VisContent.sound(sound), SoundSource.BLOCKS, 1, 1);
    }
}
