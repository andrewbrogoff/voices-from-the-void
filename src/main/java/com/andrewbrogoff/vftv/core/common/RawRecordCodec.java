package com.andrewbrogoff.vftv.core.common;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.jspecify.annotations.Nullable;

/**
 * Raw-record retention (conventions, "Raw-record retention"): a persisted record that this build
 * cannot read is kept exactly, and written back unchanged.
 *
 * <p>Owners supply the bounded codec for their record, which checks format bounds only, and never
 * catch decode errors themselves. Decoding through {@link #wrap} never fails.
 */
public final class RawRecordCodec {
    private RawRecordCodec() {}

    /** A decoded record: either readable, or kept raw. */
    public sealed interface Retained<T> {
        /**
         * A record this build reads: the conventions' "readable" case. Not named {@code Readable},
         * which would shadow {@link java.lang.Readable}.
         */
        record Read<T>(T value) implements Retained<T> {}

        /**
         * A record this build cannot read: the raw tag, written back unchanged, and why it failed.
         */
        record Unreadable<T>(Tag raw, String reason) implements Retained<T> {}
    }

    /** Wraps a bounded codec so that every decode succeeds, keeping unreadable input raw. */
    public static <T> Codec<Retained<T>> wrap(Codec<T> bounded) {
        return new Codec<>() {
            @Override
            public <O> DataResult<Pair<Retained<T>, O>> decode(DynamicOps<O> ops, O input) {
                Tag raw = ops.convertTo(NbtOps.INSTANCE, input);
                String reason;
                try {
                    DataResult<Pair<T, O>> result = bounded.decode(ops, input);
                    if (result.isSuccess()) {
                        T value = result.getOrThrow().getFirst();
                        // Record codecs ignore extra keys, so re-encode: a key the encoding
                        // lacks is unknown, and dropping it on the next save would lose it.
                        DataResult<Tag> encoded = bounded.encodeStart(NbtOps.INSTANCE, value);
                        String unknown =
                                encoded.isSuccess()
                                        ? unknownKey(raw, encoded.getOrThrow(), "$")
                                        : "does not re-encode";
                        if (unknown == null) {
                            return DataResult.success(
                                    Pair.of(new Retained.Read<>(value), ops.empty()));
                        }
                        reason = unknown;
                    } else {
                        reason = result.error().map(DataResult.Error::message).orElse("unreadable");
                    }
                } catch (RuntimeException e) {
                    // A record constructor that throws on a failed invariant is unreadable too.
                    reason = String.valueOf(e.getMessage());
                }
                return DataResult.success(
                        Pair.of(new Retained.Unreadable<>(raw, reason), ops.empty()));
            }

            @Override
            public <O> DataResult<O> encode(Retained<T> input, DynamicOps<O> ops, O prefix) {
                return switch (input) {
                    case Retained.Read<T> r -> bounded.encode(r.value(), ops, prefix);
                    case Retained.Unreadable<T> u ->
                            ops.mergeToPrimitive(prefix, NbtOps.INSTANCE.convertTo(ops, u.raw()));
                };
            }

            @Override
            public String toString() {
                return "RawRecordCodec[" + bounded + "]";
            }
        };
    }

    /** The first key of {@code input} that {@code encoded} lacks, recursively, or null. */
    private static @Nullable String unknownKey(Tag input, Tag encoded, String path) {
        if (input instanceof CompoundTag in && encoded instanceof CompoundTag out) {
            for (String key : in.keySet()) {
                Tag child = out.get(key);
                String found =
                        child == null
                                ? "unknown key " + path + "." + key
                                : unknownKey(
                                        Objects.requireNonNull(in.get(key)),
                                        child,
                                        path + "." + key);
                if (found != null) {
                    return found;
                }
            }
        } else if (input instanceof ListTag in
                && encoded instanceof ListTag out
                && in.size() == out.size()) {
            for (int i = 0; i < in.size(); i++) {
                String found = unknownKey(in.get(i), out.get(i), path + "[" + i + "]");
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
