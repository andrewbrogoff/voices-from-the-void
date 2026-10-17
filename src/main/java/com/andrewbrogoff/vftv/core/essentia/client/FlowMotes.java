package com.andrewbrogoff.vftv.core.essentia.client;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.essentia.EssentiaLimits;
import com.andrewbrogoff.vftv.core.essentia.EssentiaPayloads;
import com.andrewbrogoff.vftv.core.essentia.EssentiaTubeBlock;
import com.andrewbrogoff.vftv.core.essentia.LabelledBlockEntity;
import com.andrewbrogoff.vftv.core.essentia.TubeValveBlock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The flow motes (essentia spec, "Flow motes"): one per payload, travelling a path the client finds
 * over its own tube states, honouring the aspect's gates. Cosmetic: no path or missing chunks means
 * no mote. At most {@link EssentiaLimits#MAX_LIVE_MOTES} live; none at the minimal particle
 * setting.
 */
public final class FlowMotes {
    /** A live mote: its glyph, its path of member positions, and when it started. */
    private record Mote(Identifier sprite, List<Long> path, double start) {}

    /**
     * Where to draw a mote this frame, relative to the cell being rendered.
     *
     * @param sprite the glyph in the GUI atlas
     * @param x offset in blocks
     * @param y offset in blocks
     * @param z offset in blocks
     */
    public record Drawn(Identifier sprite, double x, double y, double z) {}

    private static final List<Mote> LIVE = new ArrayList<>();

    private FlowMotes() {}

    /** A flow payload arrived: finds the path and starts a mote. */
    static void add(EssentiaPayloads.Flow flow) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null
                || mc.options.particles().get() == ParticleStatus.MINIMAL
                || LIVE.size() >= EssentiaLimits.MAX_LIVE_MOTES) {
            return;
        }
        long[] path = path(level, flow.source(), flow.sink(), flow.aspect());
        if (path != null) {
            LIVE.add(
                    new Mote(
                            ClientEssentia.glyph(flow.aspect()),
                            Arrays.stream(path).boxed().toList(),
                            level.getGameTime()));
        }
    }

    static void clear() {
        LIVE.clear();
    }

    /** How many motes are live (diagnostics). */
    public static int live() {
        return LIVE.size();
    }

    private static boolean member(BlockState s) {
        return s.getBlock() instanceof EssentiaTubeBlock
                || (s.getBlock() instanceof TubeValveBlock && TubeValveBlock.passes(s));
    }

    private static boolean connects(BlockState s, Direction d) {
        if (s.getBlock() instanceof EssentiaTubeBlock) {
            return s.getValue(EssentiaTubeBlock.face(d)) == EssentiaTubeBlock.Face.CONNECTED;
        }
        return s.getBlock() instanceof TubeValveBlock
                && s.getValue(TubeValveBlock.AXIS) == d.getAxis();
    }

    private static boolean admits(ClientLevel level, BlockPos pos, ResourceKey<Aspect> aspect) {
        if (level.getBlockEntity(pos) instanceof LabelledBlockEntity be
                && level.getBlockState(pos).getBlock() instanceof EssentiaTubeBlock) {
            return be.readable() && (be.label() == null || aspect.equals(be.label()));
        }
        return true;
    }

    /**
     * A BFS over the client's members, in Direction order, from the member next to the source to
     * the member next to the sink, visiting at most {@link EssentiaLimits#MAX_FLOW_PATH} members.
     */
    static long @Nullable [] path(
            ClientLevel level, BlockPos source, BlockPos sink, ResourceKey<Aspect> aspect) {
        Map<Long, Long> parent = new HashMap<>();
        ArrayDeque<BlockPos> frontier = new ArrayDeque<>();
        for (Direction d : Direction.values()) {
            BlockPos m = source.relative(d);
            if (level.isLoaded(m)) {
                BlockState s = level.getBlockState(m);
                if (member(s) && connects(s, d.getOpposite()) && admits(level, m, aspect)) {
                    parent.put(m.asLong(), Long.MIN_VALUE);
                    frontier.add(m);
                }
            }
        }
        while (!frontier.isEmpty() && parent.size() <= EssentiaLimits.MAX_FLOW_PATH) {
            BlockPos at = frontier.poll();
            BlockState s = level.getBlockState(at);
            for (Direction d : Direction.values()) {
                if (!connects(s, d)) {
                    continue;
                }
                BlockPos n = at.relative(d);
                if (n.equals(sink)) {
                    return unwind(parent, at.asLong());
                }
                if (!level.isLoaded(n) || parent.containsKey(n.asLong())) {
                    continue;
                }
                BlockState ns = level.getBlockState(n);
                if (member(ns) && connects(ns, d.getOpposite()) && admits(level, n, aspect)) {
                    parent.put(n.asLong(), at.asLong());
                    frontier.add(n);
                }
            }
        }
        return null;
    }

    private static long[] unwind(Map<Long, Long> parent, long last) {
        List<Long> out = new ArrayList<>();
        for (long at = last; at != Long.MIN_VALUE; at = parent.getOrDefault(at, Long.MIN_VALUE)) {
            out.add(0, at);
        }
        return out.stream().mapToLong(Long::longValue).toArray();
    }

    /** Drops finished motes; called each client tick. */
    public static void tick() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            LIVE.clear();
            return;
        }
        double now = level.getGameTime();
        LIVE.removeIf(m -> (now - m.start()) * ClientEssentia.moteSpeed() > m.path().size());
    }

    /** Collects the motes inside the cell at {@code pos} this frame. */
    static void inCell(BlockPos pos, float partialTicks, List<Drawn> out) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || LIVE.isEmpty()) {
            return;
        }
        double now = level.getGameTime() + partialTicks;
        long cell = pos.asLong();
        for (Mote m : LIVE) {
            double t = (now - m.start()) * ClientEssentia.moteSpeed() - 0.5;
            List<Long> path = m.path();
            int i = (int) Math.floor(t + 0.5);
            if (i < 0 || i >= path.size() || path.get(i) != cell) {
                continue;
            }
            int a = (int) Math.max(0, Math.min(path.size() - 1, Math.floor(t)));
            int b = Math.min(path.size() - 1, a + 1);
            double f = Math.max(0, Math.min(1, t - Math.floor(t)));
            BlockPos pa = BlockPos.of(path.get(a));
            BlockPos pb = BlockPos.of(path.get(b));
            out.add(
                    new Drawn(
                            m.sprite(),
                            pa.getX() + (pb.getX() - pa.getX()) * f - pos.getX() + 0.5,
                            pa.getY() + (pb.getY() - pa.getY()) * f - pos.getY() + 0.5,
                            pa.getZ() + (pb.getZ() - pa.getZ()) * f - pos.getZ() + 0.5));
        }
    }
}
