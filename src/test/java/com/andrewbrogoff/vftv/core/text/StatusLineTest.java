package com.andrewbrogoff.vftv.core.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

/** The {@code StatusLine} payload through vanilla's bounded component codec (R5). */
class StatusLineTest {
    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
    }

    private static StatusLine roundTrip(StatusLine line) {
        RegistryFriendlyByteBuf buf = buffer();
        StatusLine.STREAM_CODEC.encode(buf, line);
        StatusLine decoded = StatusLine.STREAM_CODEC.decode(buf);
        assertEquals(0, buf.readableBytes(), "the whole payload is read");
        return decoded;
    }

    @Test
    void aTranslatableLineRoundTrips() {
        StatusLine line =
                new StatusLine(
                        Component.translatable(
                                "status.vftv.node_tap.drawn",
                                Component.translatable("aspect.vftv.aer"),
                                "12",
                                "40",
                                Component.translatable("block.minecraft.stone"),
                                "0.85"));
        assertEquals(line, roundTrip(line));
        assertEquals(StatusLine.TYPE, line.type());
    }

    @Test
    void aLiteralNameArrivesAsItsText() {
        // The codec flattens a plain literal argument to a string: the same text on screen.
        StatusLine line =
                new StatusLine(Component.translatable("status.vftv.x", Component.literal("Steve")));
        StatusLine decoded = roundTrip(line);
        assertEquals(line.text().getString(), decoded.text().getString());
        assertEquals(decoded, roundTrip(decoded));
    }

    @Test
    void aComponentBeyondTheBoundIsRejectedAtDecode() {
        MutableComponent huge = Component.empty();
        String chunk = "x".repeat(60_000);
        for (int i = 0; i < 40; i++) {
            huge.append(Component.literal(chunk));
        }
        RegistryFriendlyByteBuf buf = buffer();
        StatusLine.STREAM_CODEC.encode(buf, new StatusLine(huge));
        assertThrows(RuntimeException.class, () -> StatusLine.STREAM_CODEC.decode(buf));
    }
}
