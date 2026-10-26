package com.andrewbrogoff.vftv.core.infusion;

import com.andrewbrogoff.vftv.api.infusion.InfusionLimits;
import com.andrewbrogoff.vftv.core.common.RawRecordCodec;
import com.andrewbrogoff.vftv.core.common.RawRecordCodec.Retained;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The Singing Bowl's block entity (infusion spec, "The blocks", "Sync and state"): the centre, the
 * bowl id, the run counter and the run, in {@code {"format": 1, "id", "runs", "item"?, "run"?}}
 * with raw retention. Trackers get the item, ringing, unreadable and the run's view.
 */
public final class BowlBlockEntity extends AltarBlockEntity {
    /** The saved record's key in the block entity's data. */
    static final String KEY = "infusion";

    /** The record's format. */
    static final int FORMAT = 1;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String SYNC_RINGING = "ringing";
    private static final String SYNC_VIEW = "view";

    /**
     * The bowl record.
     *
     * @param id the bowl id, never 0
     * @param runs runs started on this id
     * @param item the centre or output, if any
     * @param run the run, if ringing
     */
    record Saved(long id, int runs, Optional<ItemStack> item, Optional<InfusionRun> run) {}

    /** The bounded record codec: format 1, every bound and invariant. */
    static final Codec<Saved> RECORD =
            RecordCodecBuilder.<Saved>create(
                            i ->
                                    i.group(
                                                    Codec.INT
                                                            .validate(
                                                                    f ->
                                                                            f == FORMAT
                                                                                    ? DataResult
                                                                                            .success(
                                                                                                    f)
                                                                                    : DataResult
                                                                                            .error(
                                                                                                    () ->
                                                                                                            "unknown format "
                                                                                                                    + f))
                                                            .fieldOf("format")
                                                            .forGetter(r -> FORMAT),
                                                    Codec.LONG
                                                            .validate(
                                                                    id ->
                                                                            id != 0
                                                                                    ? DataResult
                                                                                            .success(
                                                                                                    id)
                                                                                    : DataResult
                                                                                            .error(
                                                                                                    () ->
                                                                                                            "bowl id 0"))
                                                            .fieldOf("id")
                                                            .forGetter(Saved::id),
                                                    Codec.intRange(0, InfusionLimits.MAX_RUNS)
                                                            .fieldOf("runs")
                                                            .forGetter(Saved::runs),
                                                    ONE.optionalFieldOf("item")
                                                            .forGetter(Saved::item),
                                                    InfusionRun.CODEC
                                                            .optionalFieldOf("run")
                                                            .forGetter(Saved::run))
                                            .apply(
                                                    i,
                                                    (f, id, runs, item, run) ->
                                                            new Saved(id, runs, item, run)))
                    .validate(
                            r ->
                                    r.run().isEmpty() || r.run().get().seq() <= r.runs()
                                            ? DataResult.success(r)
                                            : DataResult.error(() -> "seq over runs"));

    /** The retained record codec. */
    static final Codec<Retained<Saved>> RETAINED = RawRecordCodec.wrap(RECORD);

    long id;
    int runs;
    @Nullable InfusionRun run;
    private Retained.@Nullable Unreadable<Saved> unreadable;

    /** A run operation (a step, a finish or an abandon) is open: the step guard. */
    boolean operation;

    /** Why the run last waited, for its readout. */
    InfusionReadout.Stall stall = InfusionReadout.Stall.NONE;

    /** The stalled component's pedestal, for {@code COMPONENT}. */
    @Nullable BlockPos missing;

    private boolean syncedRinging;
    private BowlView view = BowlView.NONE;

    /** A Singing Bowl's block entity. */
    public BowlBlockEntity(BlockPos pos, BlockState state) {
        super(InfusionContent.bowlType(), pos, state);
    }

    @Override
    public boolean unreadable() {
        return unreadable != null || syncedUnreadable;
    }

    /** Whether a run is under way (on a client: the synced flag). */
    public boolean ringing() {
        return run != null || syncedRinging;
    }

    /** The run, or null; server only. */
    public @Nullable InfusionRun run() {
        return run;
    }

    /** The bowl id; 0 until first loaded on a server. */
    public long id() {
        return id;
    }

    /** Runs started on this id. */
    public int runs() {
        return runs;
    }

    /** The synced view (client), or the live one (server). */
    public BowlView view() {
        return level instanceof ServerLevel ? InfusionAltars.view(this) : view;
    }

    @Override
    public boolean unavailable() {
        return super.unavailable() || operation;
    }

    /** Idle and empty: any item. */
    @Override
    public boolean mayInsert(ItemStack stack) {
        return !ringing() && item.isEmpty();
    }

    @Override
    public boolean takingLocked() {
        return ringing();
    }

    /** The raw record, for the removal's WARN; null when readable. */
    @Nullable Tag raw() {
        Retained.@Nullable Unreadable<Saved> u = unreadable;
        return u == null ? null : u.raw();
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        Optional<Retained<Saved>> read = input.read(KEY, RETAINED);
        if (read.isEmpty()) {
            unreadable = null;
            item = ItemStack.EMPTY;
            run = null;
            return; // a fresh bowl: its id is minted at load
        }
        switch (read.get()) {
            case Retained.Read<Saved> r -> {
                unreadable = null;
                id = r.value().id();
                runs = r.value().runs();
                item = r.value().item().map(ItemStack::copy).orElse(ItemStack.EMPTY);
                run = r.value().run().orElse(null);
            }
            case Retained.Unreadable<Saved> u -> {
                unreadable = u;
                item = ItemStack.EMPTY;
                run = null;
                if (!warned) {
                    warned = true;
                    LOGGER.warn(
                            "Singing Bowl at {} has an unreadable record ({}); keeping it as it is",
                            worldPosition,
                            u.reason());
                }
            }
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        Retained.@Nullable Unreadable<Saved> u = unreadable;
        if (u != null) {
            output.store(KEY, RETAINED, u);
        } else if (id != 0) {
            output.store(
                    KEY,
                    RETAINED,
                    new Retained.Read<>(
                            new Saved(
                                    id,
                                    runs,
                                    item.isEmpty() ? Optional.empty() : Optional.of(item),
                                    Optional.ofNullable(run))));
        }
    }

    @Override
    void writeSync(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean(SYNC_RINGING, run != null);
        if (run != null) {
            BowlView.CODEC
                    .encodeStart(NbtOps.INSTANCE, InfusionAltars.view(this))
                    .ifSuccess(t -> tag.put(SYNC_VIEW, t));
        }
    }

    @Override
    void readSync(ValueInput input) {
        syncedRinging = input.getBooleanOr(SYNC_RINGING, false);
        view = input.read(SYNC_VIEW, BowlView.CODEC).orElse(BowlView.NONE);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) {
            InfusionAltars.onBowlLoad(server, this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel server) {
            InfusionAltars.onBowlUnload(server, this);
        }
    }

    @Override
    public String toString() {
        return "SingingBowl["
                + worldPosition.toShortString()
                + ", id="
                + id
                + ", runs="
                + runs
                + ", run="
                + (run == null ? "none" : run.seq())
                + ", item="
                + item
                + "]";
    }
}
