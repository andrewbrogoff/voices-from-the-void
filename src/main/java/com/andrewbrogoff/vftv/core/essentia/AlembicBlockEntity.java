package com.andrewbrogoff.vftv.core.essentia;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.essentia.EssentiaLimits;
import com.andrewbrogoff.vftv.core.common.model.Hashes;
import com.andrewbrogoff.vftv.core.common.model.Salts;
import com.andrewbrogoff.vftv.core.essentia.EssentiaSeam.Probe;
import com.andrewbrogoff.vftv.core.essentia.EssentiaService.AspectIndex;
import com.andrewbrogoff.vftv.core.essentia.model.EssentiaMath;
import com.andrewbrogoff.vftv.core.essentia.model.LineModel;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The Alembic's block entity (essentia spec, "The pour"): the label, and the pour on its {@code
 * CRUC} phase ticks from the base's touching face into the block below.
 */
public final class AlembicBlockEntity extends LabelledBlockEntity {
    private @Nullable Probe base;
    private @Nullable Probe target;
    private long lastDrip = Long.MIN_VALUE;

    /** An Alembic at {@code pos}. */
    public AlembicBlockEntity(BlockPos pos, BlockState state) {
        super(EssentiaContent.alembicType(), pos, state);
    }

    @Override
    protected void onLabelChanged(ServerLevel server) {}

    /** The server ticker: one phase compare, and a pour on the phase tick. */
    void serverTick(ServerLevel level) {
        int interval = EssentiaService.rules().tuning().alembicInterval();
        long phase = Hashes.phase(Salts.CRUC, new long[] {worldPosition.asLong()}, interval);
        if (Long.remainderUnsigned(level.getGameTime(), interval) == phase) {
            pour(level);
        }
    }

    /** Whether the pour's guards hold, and what aspect it would pour; for status and tests. */
    @Nullable ResourceKey<Aspect> next(ServerLevel level) {
        Choice c = choose(level);
        return c == null ? null : c.aspect;
    }

    private record Choice(ResourceKey<Aspect> aspect, int index, Probe base, Probe target) {}

    private @Nullable Choice choose(ServerLevel level) {
        if (!readable() || isRemoved()) {
            return null;
        }
        Direction facing = getBlockState().getValue(AlembicBlock.FACING);
        BlockPos basePos = worldPosition.relative(facing);
        BlockPos below = worldPosition.below();
        if (!ticking(level, basePos)
                || !ticking(level, below)
                || !AlembicBlock.baseAt(level, worldPosition, facing)) {
            return null;
        }
        Probe b = base;
        if (b == null) {
            b =
                    EssentiaService.seam()
                            .probe(
                                    level,
                                    basePos,
                                    facing.getOpposite(),
                                    () -> !isRemoved(),
                                    () -> {});
            base = b;
        }
        Probe t = target;
        if (t == null) {
            t =
                    EssentiaService.seam()
                            .probe(level, below, Direction.UP, () -> !isRemoved(), () -> {});
            target = t;
        }
        int slots = b.slots();
        int targetSlots = t.slots();
        if (slots < 0
                || slots > EssentiaLimits.MAX_HANDLER_SLOTS
                || targetSlots < 0
                || targetSlots > EssentiaLimits.MAX_HANDLER_SLOTS) {
            return null;
        }
        AspectIndex index = EssentiaService.index(level.registryAccess());
        EssentiaLines lines = EssentiaService.lines(level);
        List<ResourceKey<Aspect>> keys = new ArrayList<>();
        List<int[]> data = new ArrayList<>();
        for (int s = 0; s < slots; s++) {
            ResourceKey<Aspect> a = b.aspect(s);
            int x = index.index(a);
            long amount = b.amount(s);
            if (a != null && x >= 0 && amount > 0) {
                keys.add(a);
                data.add(
                        new int[] {
                            x,
                            (int) Math.min(Integer.MAX_VALUE, amount),
                            lines.clamp(b.hold(a), basePos),
                            lines.clamp(t.suction(a), below)
                        });
            }
        }
        int n = data.size();
        int[] aspects = new int[n];
        int[] amounts = new int[n];
        int[] holds = new int[n];
        int[] suctions = new int[n];
        for (int i = 0; i < n; i++) {
            aspects[i] = data.get(i)[0];
            amounts[i] = data.get(i)[1];
            holds[i] = data.get(i)[2];
            suctions[i] = data.get(i)[3];
        }
        ResourceKey<Aspect> label = label();
        int labelIndex =
                label == null
                        ? EssentiaMath.LABEL_NONE
                        : index.index(label) < 0 ? EssentiaMath.LABEL_NOTHING : index.index(label);
        int pick = EssentiaMath.alembicChoice(aspects, amounts, holds, suctions, labelIndex);
        return pick < 0 ? null : new Choice(keys.get(pick), aspects[pick], b, t);
    }

    private static boolean ticking(ServerLevel level, BlockPos pos) {
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        return level.getChunkSource().getChunkNow(cx, cz) != null
                && level.shouldTickBlocksAt(ChunkPos.pack(cx, cz));
    }

    /** One pour (essentia spec, "The pour"): guards, ends, the aspect, one contribution. */
    void pour(ServerLevel level) {
        if (EssentiaLines.running() || !EssentiaService.seam().idle()) {
            return;
        }
        Choice c = choose(level);
        if (c == null) {
            return;
        }
        // Recheck the inequality immediately before the move.
        if (EssentiaService.lines(level).clamp(c.target.suction(c.aspect), worldPosition.below())
                <= EssentiaService.lines(level).clamp(c.base.hold(c.aspect), worldPosition)) {
            return;
        }
        AspectIndex index = EssentiaService.index(level.registryAccess());
        EssentiaLines.setRunning(true);
        int moved;
        try {
            LineModel.Port port =
                    EssentiaService.lines(level)
                            .port(
                                    new Probe[] {c.base, c.target},
                                    index,
                                    new long[] {
                                        worldPosition
                                                .relative(
                                                        getBlockState()
                                                                .getValue(AlembicBlock.FACING))
                                                .asLong(),
                                        worldPosition.below().asLong()
                                    });
            port.beginRoot();
            int got =
                    LineModel.contribute(
                            port, 0, 1, c.index, EssentiaService.rules().tuning().alembicRate());
            port.commitRoot();
            moved = got < 0 ? -got - 1 : got;
        } finally {
            EssentiaService.seam().closeAll();
            EssentiaLines.setRunning(false);
        }
        long time = level.getGameTime();
        if (moved > 0 && time - lastDrip >= 40) {
            lastDrip = time;
            level.playSound(
                    null,
                    worldPosition,
                    EssentiaContent.alembicDrip(),
                    SoundSource.BLOCKS,
                    1.0f,
                    1.0f);
        }
    }
}
