package com.andrewbrogoff.vftv.core.warp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

/** The Voices' packets: round trips and rejection at decode. */
class VoicePayloadsTest {
    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(
                Unpooled.buffer(),
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY),
                ConnectionType.NEOFORGE);
    }

    @Test
    void roundTrips() {
        VoicePayloads.VoiceCue cue =
                new VoicePayloads.VoiceCue("vftv:insight", "vftv:insight", 3, -7L, "vftv:aer", 3);
        RegistryFriendlyByteBuf buf = buffer();
        VoicePayloads.VoiceCue.STREAM_CODEC.encode(buf, cue);
        assertEquals(cue, VoicePayloads.VoiceCue.STREAM_CODEC.decode(buf));
        buf = buffer();
        VoicePayloads.VoiceQuiet.STREAM_CODEC.encode(buf, new VoicePayloads.VoiceQuiet(true));
        assertEquals(
                new VoicePayloads.VoiceQuiet(true),
                VoicePayloads.VoiceQuiet.STREAM_CODEC.decode(buf));
        buf = buffer();
        VoicePayloads.VoiceClear.STREAM_CODEC.encode(buf, new VoicePayloads.VoiceClear());
        assertEquals(
                new VoicePayloads.VoiceClear(), VoicePayloads.VoiceClear.STREAM_CODEC.decode(buf));
    }

    @Test
    void outOfBoundsIsRejectedAtDecode() {
        RegistryFriendlyByteBuf band = buffer();
        band.writeUtf("vftv:whisper");
        band.writeUtf("vftv:whisper");
        band.writeByte(4);
        band.writeLong(1);
        band.writeUtf("");
        band.writeByte(0);
        assertThrows(
                RuntimeException.class, () -> VoicePayloads.VoiceCue.STREAM_CODEC.decode(band));
        RegistryFriendlyByteBuf longId = buffer();
        longId.writeUtf("v".repeat(300));
        assertThrows(
                RuntimeException.class, () -> VoicePayloads.VoiceCue.STREAM_CODEC.decode(longId));
        RegistryFriendlyByteBuf points = buffer();
        points.writeUtf("vftv:insight");
        points.writeUtf("vftv:insight");
        points.writeByte(1);
        points.writeLong(1);
        points.writeUtf("vftv:aer");
        points.writeByte(65);
        assertThrows(
                RuntimeException.class, () -> VoicePayloads.VoiceCue.STREAM_CODEC.decode(points));
    }
}
