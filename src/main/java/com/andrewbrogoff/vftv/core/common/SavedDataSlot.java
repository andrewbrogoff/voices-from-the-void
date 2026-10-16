package com.andrewbrogoff.vftv.core.common;

import com.mojang.serialization.Codec;
import java.util.function.Supplier;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.SavedDataStorage;

/**
 * A level-wide slot (architecture, "State and sync"): one value per dimension, stored as vanilla
 * {@link SavedData} in the level's data storage, so it needs no platform seam.
 */
public final class SavedDataSlot<T> implements DataSlot<ServerLevel, T> {
    private final SavedDataType<Box<T>> type;

    /** A slot saved as {@code data/<namespace>/<path>.dat} in each dimension's data folder. */
    public SavedDataSlot(Identifier id, Codec<T> codec, Supplier<T> defaultValue) {
        Codec<Box<T>> boxCodec = codec.xmap(Box::new, box -> box.value);
        this.type = new SavedDataType<>(id, () -> new Box<>(defaultValue.get()), boxCodec);
    }

    @Override
    public T get(ServerLevel level) {
        return read(level.getDataStorage());
    }

    @Override
    public void set(ServerLevel level, T value) {
        write(level.getDataStorage(), value);
    }

    /** The value in a storage, or the default. */
    public T read(SavedDataStorage storage) {
        return storage.computeIfAbsent(type).value;
    }

    /** Replaces the value in a storage and marks it dirty, so the next save writes it. */
    public void write(SavedDataStorage storage, T value) {
        Box<T> box = storage.computeIfAbsent(type);
        box.value = value;
        box.setDirty();
    }

    private static final class Box<T> extends SavedData {
        T value;

        Box(T value) {
            this.value = value;
        }
    }
}
