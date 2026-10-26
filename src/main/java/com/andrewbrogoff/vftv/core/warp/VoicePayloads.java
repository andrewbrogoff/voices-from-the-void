package com.andrewbrogoff.vftv.core.warp;

import com.andrewbrogoff.vftv.api.warp.WarpLimits;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** The Voices' packets (warp spec, "Sync and state"), each bounded at decode. */
public final class VoicePayloads {
    private VoicePayloads() {}

    /**
     * S2C {@code VoiceCue}, to the owner only: what to perceive.
     *
     * @param event the Voice event's id
     * @param kind its type's id
     * @param band the band, 0–3
     * @param seed the cue seed the client draws from
     * @param aspect an insight's aspect id, else empty
     * @param points an insight's points, 0–64
     */
    public record VoiceCue(
            String event, String kind, int band, long seed, String aspect, int points)
            implements CustomPacketPayload {
        /** {@code vftv:voice_cue}. */
        public static final Type<VoiceCue> TYPE = new Type<>(id("voice_cue"));

        /** The bounded codec. */
        public static final StreamCodec<RegistryFriendlyByteBuf, VoiceCue> STREAM_CODEC =
                StreamCodec.of(VoiceCue::write, VoiceCue::read);

        /** Checks the bounds. */
        public VoiceCue {
            if (band < 0 || band > 3 || points < 0 || points > 64) {
                throw new DecoderException("voice cue out of bounds");
            }
        }

        @Override
        public Type<VoiceCue> type() {
            return TYPE;
        }

        private static void write(FriendlyByteBuf buf, VoiceCue c) {
            buf.writeUtf(c.event, WarpLimits.MAX_ID_CHARS);
            buf.writeUtf(c.kind, WarpLimits.MAX_ID_CHARS);
            buf.writeByte(c.band);
            buf.writeLong(c.seed);
            buf.writeUtf(c.aspect, WarpLimits.MAX_ID_CHARS);
            buf.writeByte(c.points);
        }

        private static VoiceCue read(FriendlyByteBuf buf) {
            return new VoiceCue(
                    buf.readUtf(WarpLimits.MAX_ID_CHARS),
                    buf.readUtf(WarpLimits.MAX_ID_CHARS),
                    buf.readByte(),
                    buf.readLong(),
                    buf.readUtf(WarpLimits.MAX_ID_CHARS),
                    buf.readByte());
        }
    }

    /** S2C {@code VoiceClear}, sent by Silencing: every presentation stops. */
    public record VoiceClear() implements CustomPacketPayload {
        /** {@code vftv:voice_clear}. */
        public static final Type<VoiceClear> TYPE = new Type<>(id("voice_clear"));

        /** The empty codec. */
        public static final StreamCodec<RegistryFriendlyByteBuf, VoiceClear> STREAM_CODEC =
                StreamCodec.unit(new VoiceClear());

        @Override
        public Type<VoiceClear> type() {
            return TYPE;
        }
    }

    /**
     * C2S {@code VoiceQuiet}: whether a screen is open on the client.
     *
     * @param open a screen is open
     */
    public record VoiceQuiet(boolean open) implements CustomPacketPayload {
        /** {@code vftv:voice_quiet}. */
        public static final Type<VoiceQuiet> TYPE = new Type<>(id("voice_quiet"));

        /** One boolean. */
        public static final StreamCodec<RegistryFriendlyByteBuf, VoiceQuiet> STREAM_CODEC =
                StreamCodec.of(
                        (buf, q) -> buf.writeBoolean(q.open),
                        buf -> new VoiceQuiet(buf.readBoolean()));

        @Override
        public Type<VoiceQuiet> type() {
            return TYPE;
        }
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("vftv", path);
    }
}
