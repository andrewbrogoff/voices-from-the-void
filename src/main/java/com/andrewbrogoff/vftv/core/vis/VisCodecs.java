package com.andrewbrogoff.vftv.core.vis;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Codec helpers the vis feature's records share. */
final class VisCodecs {
    private VisCodecs() {}

    /**
     * A codec that refuses an object with a key outside {@code keys}: record codecs ignore unknown
     * fields, and the wands spec refuses them.
     */
    static <T> Codec<T> strict(Codec<T> codec, Set<String> keys) {
        return new Codec<>() {
            @Override
            public <O> DataResult<Pair<T, O>> decode(DynamicOps<O> ops, O input) {
                List<String> unknown = new ArrayList<>();
                ops.getMapValues(input)
                        .ifSuccess(
                                entries ->
                                        entries.forEach(
                                                e ->
                                                        ops.getStringValue(e.getFirst())
                                                                .ifSuccess(
                                                                        k -> {
                                                                            if (!keys.contains(k)) {
                                                                                unknown.add(k);
                                                                            }
                                                                        })));
                if (!unknown.isEmpty()) {
                    return DataResult.error(() -> "unknown field " + String.join(", ", unknown));
                }
                return codec.decode(ops, input);
            }

            @Override
            public <O> DataResult<O> encode(T input, DynamicOps<O> ops, O prefix) {
                return codec.encode(input, ops, prefix);
            }
        };
    }
}
