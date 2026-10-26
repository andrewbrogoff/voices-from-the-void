package com.andrewbrogoff.vftv.core.infusion;

import com.andrewbrogoff.vftv.api.infusion.InfusionLimits;
import com.andrewbrogoff.vftv.core.common.model.Hashes;
import com.andrewbrogoff.vftv.core.common.model.Salts;
import com.andrewbrogoff.vftv.core.infusion.model.InfusionTuning;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.StreamCodec;

/**
 * The client's copy of the infusion tuning (infusion spec, "Sync and state"; conventions, "Rules
 * records"): a fixed record carrying no server config, with a generation {@code fold([INFR, format,
 * every value in key order])} that arms and the commit's fingerprint compare. The fixed {@code
 * limits.*} the research grammar lists are read through it too.
 *
 * @param format the record's format, 1
 * @param tuning the tuning in force
 */
public record InfusionRules(int format, InfusionTuning tuning) {
    /** The record's format. */
    public static final int FORMAT = 1;

    /** The rules of the shipped tuning. */
    public static final InfusionRules DEFAULTS = new InfusionRules(FORMAT, InfusionTuning.DEFAULTS);

    /** The wire form: the format, then every tuning value, as VarInts; checked at decode. */
    public static final StreamCodec<ByteBuf, InfusionRules> STREAM_CODEC =
            StreamCodec.of(InfusionRules::write, InfusionRules::read);

    /** Checks the format. */
    public InfusionRules {
        if (format != FORMAT) {
            throw new IllegalArgumentException("unknown format " + format);
        }
    }

    /** The generation: equal rules share it across restarts. */
    public long generation() {
        List<Integer> v = tuning.values();
        long[] words = new long[v.size() + 2];
        words[0] = Salts.word(Salts.INFR);
        words[1] = format;
        for (int i = 0; i < v.size(); i++) {
            words[i + 2] = v.get(i);
        }
        return Hashes.fold(words);
    }

    /**
     * A research {@code infusion} key's value: a tuning key or a fixed limit; null for an unknown
     * key.
     */
    public @org.jspecify.annotations.Nullable Integer value(String key) {
        return switch (key) {
            case "limits.box_radius" -> InfusionLimits.BOX_RADIUS;
            case "limits.box_below" -> InfusionLimits.BOX_BELOW;
            case "limits.box_above" -> InfusionLimits.BOX_ABOVE;
            case "limits.max_pedestals" -> InfusionLimits.MAX_PEDESTALS;
            case "limits.max_sources" -> InfusionLimits.MAX_SOURCES;
            default ->
                    InfusionTuning.KEYS.stream().anyMatch(k -> k.name().equals(key))
                            ? tuning.get(key)
                            : null;
        };
    }

    private static void write(ByteBuf buf, InfusionRules rules) {
        VarInt.write(buf, rules.format());
        for (int value : rules.tuning().values()) {
            VarInt.write(buf, value);
        }
    }

    private static InfusionRules read(ByteBuf buf) {
        int format = VarInt.read(buf);
        List<Integer> v = new ArrayList<>();
        for (int i = 0; i < InfusionTuning.KEYS.size(); i++) {
            v.add(VarInt.read(buf));
        }
        try {
            return new InfusionRules(format, new InfusionTuning(v));
        } catch (IllegalArgumentException e) {
            throw new DecoderException("infusion rules " + e.getMessage(), e);
        }
    }
}
