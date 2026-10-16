package com.andrewbrogoff.vftv.core.common;

import com.andrewbrogoff.vftv.core.common.RawRecordCodec.Retained;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
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

    private static final Codec<Integer> BOUNDED =
            RecordCodecBuilder.create(
                    i ->
                            i.group(
                                            Codec.INT
                                                    .validate(WorldMigrations::knownFormat)
                                                    .fieldOf("format")
                                                    .forGetter(v -> FORMAT),
                                            ExtraCodecs.NON_NEGATIVE_INT
                                                    .fieldOf("vftv_data_version")
                                                    .forGetter(v -> v))
                                    .apply(i, (format, version) -> version));

    private static DataResult<Integer> knownFormat(int format) {
        return format == FORMAT
                ? DataResult.success(format)
                : DataResult.error(() -> "unknown format " + format);
    }

    /** The stored version, in {@code data/vftv/data_version.dat}. */
    public static final SavedDataSlot<Retained<Integer>> VERSION =
            new SavedDataSlot<>(
                    Identifier.fromNamespaceAndPath("vftv", "data_version"),
                    RawRecordCodec.wrap(BOUNDED),
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
