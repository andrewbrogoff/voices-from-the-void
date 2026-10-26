package com.andrewbrogoff.vftv.core.worldgen;

import com.andrewbrogoff.vftv.core.common.RawRecordCodec;
import com.andrewbrogoff.vftv.core.common.RawRecordCodec.Retained;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The Reliquary's block entity (worldgen spec, "Sync and state"): its owned record is the saved tag
 * minus vanilla's and NeoForge's own keys, {@code {format: 1, site}}, kept raw when unreadable.
 * Only a genuinely data-free placement (an absent or empty owned record) initializes, to the
 * built-in site, on its first server load. Never synced: the client needs only the block state.
 */
public final class ReliquaryBlockEntity extends BlockEntity {
    /** The keys every block entity writes that are not ours. */
    static final Set<String> NOT_OWNED =
            Set.of("id", "x", "y", "z", "components", "NeoForgeData", "neoforge:attachments");

    private static final Codec<Retained<WorldgenCodecs.Reliquary>> RETAINED =
            RawRecordCodec.wrap(WorldgenCodecs.Reliquary.CODEC);
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The whole input map as a compound, so the owned keys can be read at the top level. */
    private static final MapCodec<CompoundTag> RAW =
            new MapCodec<>() {
                @Override
                public <T> Stream<T> keys(DynamicOps<T> ops) {
                    return Stream.empty();
                }

                @Override
                public <T> DataResult<CompoundTag> decode(DynamicOps<T> ops, MapLike<T> input) {
                    CompoundTag tag = new CompoundTag();
                    input.entries()
                            .forEach(
                                    (Pair<T, T> e) ->
                                            ops.getStringValue(e.getFirst())
                                                    .ifSuccess(
                                                            k ->
                                                                    tag.put(
                                                                            k,
                                                                            ops.convertTo(
                                                                                    NbtOps.INSTANCE,
                                                                                    e
                                                                                            .getSecond()))));
                    return DataResult.success(tag);
                }

                @Override
                public <T> RecordBuilder<T> encode(
                        CompoundTag input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
                    for (String key : input.keySet()) {
                        Tag value = input.get(key);
                        if (value != null) {
                            prefix.add(key, NbtOps.INSTANCE.convertTo(ops, value));
                        }
                    }
                    return prefix;
                }
            };

    private @Nullable Retained<WorldgenCodecs.Reliquary> record;
    private boolean warned;
    private boolean warnedSilent;

    /** A Reliquary block entity with no record yet. */
    public ReliquaryBlockEntity(BlockPos pos, BlockState state) {
        super(WorldgenContent.reliquaryType(), pos, state);
    }

    /** The record, or null for a data-free placement that has not initialized yet. */
    public @Nullable Retained<WorldgenCodecs.Reliquary> record() {
        return record;
    }

    /** Whether row 3 has been logged for this block. */
    boolean warnSilentOnce() {
        boolean first = !warnedSilent;
        warnedSilent = true;
        return first;
    }

    @Override
    @SuppressWarnings("deprecation") // the owned record is top-level: only a map codec reads it
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        CompoundTag owned = input.read(RAW).orElseGet(CompoundTag::new);
        NOT_OWNED.forEach(owned::remove);
        record =
                owned.isEmpty()
                        ? null
                        : RETAINED.parse(NbtOps.INSTANCE, owned).result().orElse(null);
        warnIfUnreadable();
    }

    @Override
    @SuppressWarnings("deprecation") // the owned record is top-level: only a map codec writes it
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (record == null) {
            return;
        }
        Tag tag = RETAINED.encodeStart(NbtOps.INSTANCE, record).getOrThrow();
        if (tag instanceof CompoundTag compound) {
            output.store(RAW, compound);
        }
    }

    @Override
    public void onLoad() {
        if (level instanceof ServerLevel) {
            initialized();
            warnIfUnreadable();
        }
    }

    /**
     * The record on the server, a data-free placement initialized to the built-in site first: on
     * its first server load, or at a use that comes before it.
     */
    @Nullable Retained<WorldgenCodecs.Reliquary> initialized() {
        if (record == null && level instanceof ServerLevel) {
            record = new Retained.Read<>(WorldgenCodecs.Reliquary.BUILT_IN);
            setChanged();
        }
        return record;
    }

    private void warnIfUnreadable() {
        if (record instanceof Retained.Unreadable<WorldgenCodecs.Reliquary> u
                && level instanceof ServerLevel
                && !warned) {
            warned = true;
            LOGGER.warn("Reliquary at {} has an unreadable record: {}", worldPosition, u.reason());
        }
    }
}
