package com.andrewbrogoff.vftv.core.flux;

import com.andrewbrogoff.vftv.api.flux.FluxRules;
import com.andrewbrogoff.vftv.core.flux.model.FluxTuning;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.StreamCodec;

/**
 * The codecs of {@link FluxRules}: a fixed record of {@link FluxTuning#FIELDS}, each within its
 * range. The generation is never carried; it is folded again from the values, so it can't disagree
 * with them. Out of bounds is rejected at decode.
 */
public final class FluxRulesCodecs {
    private static final List<FluxTuning.Field> FIELDS = FluxTuning.FIELDS;
    private static final Set<String> NAMES =
            FIELDS.stream().map(FluxTuning.Field::name).collect(Collectors.toUnmodifiableSet());

    /**
     * The persisted form, as an infusion run freezes it: an object of every field by name, the
     * switches as 0 or 1.
     */
    public static final Codec<FluxRules> CODEC =
            Codec.unboundedMap(Codec.STRING, Codec.INT)
                    .comapFlatMap(FluxRulesCodecs::fromMap, FluxRulesCodecs::toMap);

    /** The wire form: every field as a VarInt, in order. */
    public static final StreamCodec<ByteBuf, FluxRules> STREAM_CODEC =
            StreamCodec.of(FluxRulesCodecs::write, FluxRulesCodecs::read);

    private FluxRulesCodecs() {}

    private static DataResult<FluxRules> fromMap(Map<String, Integer> map) {
        if (!map.keySet().equals(NAMES)) {
            return DataResult.error(() -> "flux rules fields " + map.keySet() + ", not " + NAMES);
        }
        int[] values = new int[FIELDS.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = Objects.requireNonNull(map.get(FIELDS.get(i).name())); // keys checked
        }
        try {
            return DataResult.success(FluxTuning.fromValues(values));
        } catch (IllegalArgumentException e) {
            return DataResult.error(() -> "flux rules " + e.getMessage());
        }
    }

    private static Map<String, Integer> toMap(FluxRules rules) {
        int[] values = FluxTuning.values(rules);
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i++) {
            map.put(FIELDS.get(i).name(), values[i]);
        }
        return map;
    }

    private static void write(ByteBuf buf, FluxRules rules) {
        for (int value : FluxTuning.values(rules)) {
            VarInt.write(buf, value);
        }
    }

    private static FluxRules read(ByteBuf buf) {
        int[] values = new int[FIELDS.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = VarInt.read(buf);
        }
        try {
            return FluxTuning.fromValues(values);
        } catch (IllegalArgumentException e) {
            throw new DecoderException("flux rules " + e.getMessage(), e);
        }
    }
}
