package com.andrewbrogoff.vftv.core.essentia;

import com.andrewbrogoff.vftv.api.essentia.EssentiaLimits;
import com.andrewbrogoff.vftv.core.essentia.model.EssentiaTuning;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.StreamCodec;

/**
 * The client's copy of the essentia tuning (essentia spec, "Sync and state"; conventions, "Rules
 * records"): a fixed record carrying no server config. The derived {@code tube.idle_ticks} and the
 * fixed {@code limits.*} values the research grammar lists are read through it too.
 *
 * @param format the record's format, 1
 * @param tuning the tuning in force
 */
public record EssentiaRules(int format, EssentiaTuning tuning) {
    /** The record's format. */
    public static final int FORMAT = 1;

    /** The rules of the shipped tuning. */
    public static final EssentiaRules DEFAULTS = new EssentiaRules(FORMAT, EssentiaTuning.DEFAULTS);

    /** The persisted form: {@code format} and every tuning key by name. */
    public static final Codec<EssentiaRules> CODEC =
            Codec.unboundedMap(Codec.STRING, Codec.INT)
                    .comapFlatMap(EssentiaRules::fromMap, EssentiaRules::toMap);

    /** The wire form: the format, then every tuning value, as VarInts; checked at decode. */
    public static final StreamCodec<ByteBuf, EssentiaRules> STREAM_CODEC =
            StreamCodec.of(EssentiaRules::write, EssentiaRules::read);

    /** Checks the format. */
    public EssentiaRules {
        if (format != FORMAT) {
            throw new IllegalArgumentException("unknown format " + format);
        }
    }

    /** {@code tube.idle_ticks}: a sleeping line's delay. */
    public int tubeIdleTicks() {
        return tuning.tubeIdleTicks();
    }

    /** {@code limits.max_members}. */
    public int maxMembers() {
        return EssentiaLimits.MAX_MEMBERS;
    }

    /** {@code limits.max_endpoints}. */
    public int maxEndpoints() {
        return EssentiaLimits.MAX_ENDPOINTS;
    }

    /** {@code limits.max_suction}. */
    public int maxSuction() {
        return EssentiaLimits.MAX_SUCTION;
    }

    /** {@code limits.storage_max}. */
    public int storageMax() {
        return EssentiaLimits.STORAGE_MAX;
    }

    /** {@code limits.max_handler_slots}. */
    public int maxHandlerSlots() {
        return EssentiaLimits.MAX_HANDLER_SLOTS;
    }

    private static DataResult<EssentiaRules> fromMap(Map<String, Integer> map) {
        Map<String, Integer> expected = toMap(DEFAULTS);
        if (!map.keySet().equals(expected.keySet())) {
            return DataResult.error(() -> "essentia rules fields " + map.keySet());
        }
        int[] v = new int[EssentiaTuning.KEYS.size()];
        for (int i = 0; i < v.length; i++) {
            v[i] = map.getOrDefault(EssentiaTuning.KEYS.get(i).name(), 0);
        }
        try {
            return DataResult.success(
                    new EssentiaRules(map.getOrDefault("format", 0), EssentiaTuning.of(v)));
        } catch (IllegalArgumentException e) {
            return DataResult.error(() -> "essentia rules " + e.getMessage());
        }
    }

    private static Map<String, Integer> toMap(EssentiaRules rules) {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("format", rules.format());
        int[] v = rules.tuning().values();
        for (int i = 0; i < v.length; i++) {
            map.put(EssentiaTuning.KEYS.get(i).name(), v[i]);
        }
        return map;
    }

    private static void write(ByteBuf buf, EssentiaRules rules) {
        VarInt.write(buf, rules.format());
        for (int value : rules.tuning().values()) {
            VarInt.write(buf, value);
        }
    }

    private static EssentiaRules read(ByteBuf buf) {
        int format = VarInt.read(buf);
        int[] v = new int[EssentiaTuning.KEYS.size()];
        for (int i = 0; i < v.length; i++) {
            v[i] = VarInt.read(buf);
        }
        try {
            return new EssentiaRules(format, EssentiaTuning.of(v));
        } catch (IllegalArgumentException e) {
            throw new DecoderException("essentia rules " + e.getMessage(), e);
        }
    }
}
