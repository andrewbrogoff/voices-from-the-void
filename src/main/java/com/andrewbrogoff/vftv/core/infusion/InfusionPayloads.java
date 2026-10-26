package com.andrewbrogoff.vftv.core.infusion;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/** The infusion feature's payloads: the rules and the readout (S2C), and the seen ack (C2S). */
public final class InfusionPayloads {
    private InfusionPayloads() {}

    /**
     * S2C {@code vftv:infusion_rules}: the {@link InfusionRules} in force, on join and after every
     * {@code /reload}.
     *
     * @param rules the rules in force
     */
    public record Rules(InfusionRules rules) implements CustomPacketPayload {
        /** The payload's type. */
        public static final Type<Rules> TYPE = new Type<>(id("infusion_rules"));

        /** The fixed wire form. */
        public static final StreamCodec<ByteBuf, Rules> STREAM_CODEC =
                InfusionRules.STREAM_CODEC.map(Rules::new, Rules::rules);

        @Override
        public Type<Rules> type() {
            return TYPE;
        }
    }

    /** A readout's kind. */
    public enum Kind {
        /** An idle bowl's assessment, from a strike or an arm's refresh. */
        ARMED,
        /** A ringing bowl's run readout. */
        RUN,
        /** The arm or subscription ended. */
        CLEARED
    }

    /**
     * S2C {@code vftv:infusion_readout}, to one player: the arm's position, revision and kind,
     * whether it answers the player's own strike or commit, and the readout ({@code CLEARED}
     * carries none).
     *
     * @param pos the bowl's position
     * @param revision the arm's revision
     * @param kind the kind
     * @param solicited true only for the answer to the player's own strike or commit
     * @param readout the readout, absent for {@code CLEARED}
     */
    public record Readout(
            BlockPos pos,
            int revision,
            Kind kind,
            boolean solicited,
            Optional<InfusionReadout> readout)
            implements CustomPacketPayload {
        /** The payload's type. */
        public static final Type<Readout> TYPE = new Type<>(id("infusion_readout"));

        /** The bounded wire form; the kind travels as its index, checked at decode. */
        @SuppressWarnings("EnumOrdinal") // the wire index of a fixed three-value enum
        public static final StreamCodec<RegistryFriendlyByteBuf, Readout> STREAM_CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC,
                        Readout::pos,
                        ByteBufCodecs.VAR_INT,
                        Readout::revision,
                        ByteBufCodecs.idMapper(i -> Kind.values()[bounded(i)], Kind::ordinal),
                        Readout::kind,
                        ByteBufCodecs.BOOL,
                        Readout::solicited,
                        ByteBufCodecs.optional(InfusionReadout.STREAM_CODEC),
                        Readout::readout,
                        Readout::new);

        /** The readout, or null for {@code CLEARED}. */
        public @Nullable InfusionReadout value() {
            return readout.orElse(null);
        }

        @Override
        public Type<Readout> type() {
            return TYPE;
        }

        private static int bounded(int i) {
            if (i < 0 || i >= Kind.values().length) {
                throw new DecoderException("readout kind " + i);
            }
            return i;
        }
    }

    /**
     * C2S {@code vftv:infusion_seen}: the client displayed the {@code ARMED} readout of this
     * revision at this bowl. A {@code BlockPos} and a VarInt; anything else is rejected at decode.
     * It records an acknowledgement and authorises nothing.
     *
     * @param pos the bowl's position
     * @param revision the readout's revision
     */
    public record Seen(BlockPos pos, int revision) implements CustomPacketPayload {
        /** The payload's type. */
        public static final Type<Seen> TYPE = new Type<>(id("infusion_seen"));

        /** The wire form. */
        public static final StreamCodec<ByteBuf, Seen> STREAM_CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC,
                        Seen::pos,
                        ByteBufCodecs.VAR_INT,
                        Seen::revision,
                        Seen::new);

        @Override
        public Type<Seen> type() {
            return TYPE;
        }
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("vftv", path);
    }
}
