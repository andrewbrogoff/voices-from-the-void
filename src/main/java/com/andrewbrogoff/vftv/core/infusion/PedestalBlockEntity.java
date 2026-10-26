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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The Pedestal's block entity (infusion spec, "Claims", "Sync and state"): one item and an optional
 * claim {@code {bowl position, bowl id, seq}}, in {@code {"format": 1, "item"?, "claim"?}} with raw
 * retention. Trackers get the item, claimed and unreadable.
 */
public final class PedestalBlockEntity extends AltarBlockEntity {
    /** The saved record's key in the block entity's data. */
    static final String KEY = "infusion";

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String SYNC_CLAIMED = "claimed";

    /**
     * A claim: the bowl's position, its id, and the run's {@code seq}.
     *
     * @param bowl the bowl's {@code asLong}
     * @param id the bowl id, never 0
     * @param seq the run, from 1
     */
    public record Claim(long bowl, long id, int seq) {}

    private static final Codec<Claim> CLAIM =
            RecordCodecBuilder.create(
                    i ->
                            i.group(
                                            Codec.LONG.fieldOf("bowl").forGetter(Claim::bowl),
                                            Codec.LONG
                                                    .validate(
                                                            id ->
                                                                    id != 0
                                                                            ? DataResult.success(id)
                                                                            : DataResult.error(
                                                                                    () ->
                                                                                            "bowl id 0"))
                                                    .fieldOf("id")
                                                    .forGetter(Claim::id),
                                            Codec.intRange(1, InfusionLimits.MAX_RUNS)
                                                    .fieldOf("seq")
                                                    .forGetter(Claim::seq))
                                    .apply(i, Claim::new));

    /**
     * The pedestal record.
     *
     * @param item the held item, if any
     * @param claim the claim, if any
     */
    record Saved(Optional<ItemStack> item, Optional<Claim> claim) {}

    /** The bounded record codec: format 1. */
    static final Codec<Saved> RECORD =
            RecordCodecBuilder.create(
                    i ->
                            i.group(
                                            Codec.INT
                                                    .validate(
                                                            f ->
                                                                    f == BowlBlockEntity.FORMAT
                                                                            ? DataResult.success(f)
                                                                            : DataResult.error(
                                                                                    () ->
                                                                                            "unknown format "
                                                                                                    + f))
                                                    .fieldOf("format")
                                                    .forGetter(r -> BowlBlockEntity.FORMAT),
                                            ONE.optionalFieldOf("item").forGetter(Saved::item),
                                            CLAIM.optionalFieldOf("claim").forGetter(Saved::claim))
                                    .apply(i, (f, item, claim) -> new Saved(item, claim)));

    /** The retained record codec. */
    static final Codec<Retained<Saved>> RETAINED = RawRecordCodec.wrap(RECORD);

    @Nullable Claim claim;
    private Retained.@Nullable Unreadable<Saved> unreadable;
    private boolean syncedClaimed;

    /** A Pedestal's block entity. */
    public PedestalBlockEntity(BlockPos pos, BlockState state) {
        super(InfusionContent.pedestalType(), pos, state);
    }

    @Override
    public boolean unreadable() {
        return unreadable != null || syncedUnreadable;
    }

    /** The claim, or null; server only. */
    public @Nullable Claim claim() {
        return claim;
    }

    /** The raw record, for the removal's WARN; null when readable. */
    net.minecraft.nbt.@Nullable Tag raw() {
        Retained.@Nullable Unreadable<Saved> u = unreadable;
        return u == null ? null : u.raw();
    }

    /** Whether it holds a claim (on a client: the synced flag). */
    public boolean claimed() {
        return claim != null || syncedClaimed;
    }

    /**
     * Unclaimed (or a dead claim): any item. Claimed and live: only the promised stack, while its
     * component is pending. Unknown or unresolved: nothing.
     */
    @Override
    public boolean mayInsert(ItemStack stack) {
        if (!(level instanceof ServerLevel server)) {
            return !claimed();
        }
        return InfusionAltars.mayRestore(server, this, stack);
    }

    @Override
    public boolean takingLocked() {
        if (!(level instanceof ServerLevel server)) {
            return claimed();
        }
        return InfusionAltars.claimHolds(server, this);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        Optional<Retained<Saved>> read = input.read(KEY, RETAINED);
        if (read.isEmpty()) {
            unreadable = null;
            item = ItemStack.EMPTY;
            claim = null;
            return;
        }
        switch (read.get()) {
            case Retained.Read<Saved> r -> {
                unreadable = null;
                item = r.value().item().map(ItemStack::copy).orElse(ItemStack.EMPTY);
                claim = r.value().claim().orElse(null);
            }
            case Retained.Unreadable<Saved> u -> {
                unreadable = u;
                item = ItemStack.EMPTY;
                claim = null;
                if (!warned) {
                    warned = true;
                    LOGGER.warn(
                            "Pedestal at {} has an unreadable record ({}); keeping it as it is",
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
        output.store(
                KEY,
                RETAINED,
                u != null
                        ? u
                        : new Retained.Read<>(
                                new Saved(
                                        item.isEmpty() ? Optional.empty() : Optional.of(item),
                                        Optional.ofNullable(claim))));
    }

    @Override
    void writeSync(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean(SYNC_CLAIMED, claim != null);
    }

    @Override
    void readSync(ValueInput input) {
        syncedClaimed = input.getBooleanOr(SYNC_CLAIMED, false);
    }

    @Override
    public String toString() {
        return "Pedestal["
                + worldPosition.toShortString()
                + ", claim="
                + claim
                + ", item="
                + item
                + "]";
    }
}
