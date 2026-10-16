package com.andrewbrogoff.vftv.core.common;

import com.andrewbrogoff.vftv.core.common.RawRecordCodec.Retained;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.SavedDataStorage;
import org.slf4j.Logger;

/**
 * One-shot world migrations (architecture, "State and sync"), keyed off {@code vftv_data_version}:
 * an int in the server's world-wide saved data, absent meaning 0.
 *
 * <p>At server start every pending step runs in order, and the version is written after each, so a
 * crash mid-step reruns only that step. Steps must be idempotent. A version above the steps this
 * build knows was written by a newer build and is left alone; an unreadable record is kept raw and
 * runs nothing.
 */
public final class WorldMigrations {
    /** The record's format. */
    public static final int FORMAT = 1;

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String VERSION_KEY = "vftv_data_version";

    // Strict at the tag level: Codec.INT would coerce a double or long, and a record codec would
    // drop unknown keys, so either would make a malformed record "readable".
    private static final Codec<Integer> BOUNDED =
            CompoundTag.CODEC.comapFlatMap(WorldMigrations::decode, WorldMigrations::encode);

    private static DataResult<Integer> decode(CompoundTag tag) {
        if (!tag.keySet().equals(Set.of("format", VERSION_KEY))) {
            return DataResult.error(() -> "unexpected keys " + tag.keySet());
        }
        if (!(tag.get("format") instanceof IntTag(int format)) || format != FORMAT) {
            return DataResult.error(() -> "unknown format " + tag.get("format"));
        }
        if (!(tag.get(VERSION_KEY) instanceof IntTag(int version)) || version < 0) {
            return DataResult.error(() -> "bad version " + tag.get(VERSION_KEY));
        }
        return DataResult.success(version);
    }

    private static CompoundTag encode(int version) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("format", FORMAT);
        tag.putInt(VERSION_KEY, version);
        return tag;
    }

    static final Codec<Retained<Integer>> VERSION_CODEC = RawRecordCodec.wrap(BOUNDED);

    /** The stored version, in {@code data/vftv/data_version.dat}. */
    public static final SavedDataSlot<Retained<Integer>> VERSION =
            new SavedDataSlot<>(
                    Identifier.fromNamespaceAndPath("vftv", "data_version"),
                    VERSION_CODEC,
                    () -> new Retained.Read<>(0));

    private WorldMigrations() {}

    /**
     * Runs the pending steps against {@code context}: step {@code i} moves the version from {@code
     * i} to {@code i + 1}. Returns how many ran.
     */
    public static <C> int runPending(
            SavedDataStorage storage, List<? extends Consumer<? super C>> steps, C context) {
        int version;
        switch (VERSION.read(storage)) {
            case Retained.Unreadable<Integer> u -> {
                LOGGER.warn(
                        "vftv_data_version is unreadable ({}), so no migration runs", u.reason());
                return 0;
            }
            case Retained.Read<Integer> r -> version = r.value();
        }
        if (version > steps.size()) {
            LOGGER.warn(
                    "vftv_data_version {} is newer than this build's {}; no migration runs",
                    version,
                    steps.size());
            return 0;
        }
        for (int i = version; i < steps.size(); i++) {
            steps.get(i).accept(context);
            VERSION.write(storage, new Retained.Read<>(i + 1));
        }
        return steps.size() - version;
    }
}
