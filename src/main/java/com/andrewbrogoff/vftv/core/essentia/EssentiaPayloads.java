package com.andrewbrogoff.vftv.core.essentia;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/** The essentia feature's two clientbound payloads. */
public final class EssentiaPayloads {
    private EssentiaPayloads() {}

    /**
     * S2C {@code vftv:essentia_rules}: the {@link EssentiaRules} in force, on join and after every
     * {@code /reload}.
     *
     * @param rules the rules in force
     */
    public record Rules(EssentiaRules rules) implements CustomPacketPayload {
        /** The payload's type. */
        public static final Type<Rules> TYPE = new Type<>(id("essentia_rules"));

        /** The fixed wire form. */
        public static final StreamCodec<ByteBuf, Rules> STREAM_CODEC =
                EssentiaRules.STREAM_CODEC.map(Rules::new, Rules::rules);

        @Override
        public Type<Rules> type() {
            return TYPE;
        }
    }

    /**
     * S2C {@code vftv:essentia_flow}: one move's mote, from the first source that contributed to
     * the sink (essentia spec, "Flow motes"). Cosmetic.
     *
     * @param line the line's key
     * @param source the first contributing source's position
     * @param sink the sink's position
     * @param aspect the aspect moved
     * @param amount that source's contribution, 1–1000
     */
    public record Flow(
            long line, BlockPos source, BlockPos sink, ResourceKey<Aspect> aspect, int amount)
            implements CustomPacketPayload {
        /** The payload's type. */
        public static final Type<Flow> TYPE = new Type<>(id("essentia_flow"));

        /** The bounded wire form: the decoder rejects an amount outside 1–1000. */
        public static final StreamCodec<ByteBuf, Flow> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.LONG,
                        Flow::line,
                        BlockPos.STREAM_CODEC,
                        Flow::source,
                        BlockPos.STREAM_CODEC,
                        Flow::sink,
                        EssentiaRecords.ASPECT_STREAM,
                        Flow::aspect,
                        ByteBufCodecs.VAR_INT,
                        Flow::amount,
                        Flow::new);

        /** Checks the amount. */
        public Flow {
            if (amount < 1 || amount > 1000) {
                throw new DecoderException("flow amount " + amount + " outside 1..1000");
            }
        }

        @Override
        public Type<Flow> type() {
            return TYPE;
        }
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("vftv", path);
    }
}
