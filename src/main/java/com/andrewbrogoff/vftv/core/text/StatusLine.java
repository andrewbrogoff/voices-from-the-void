package com.andrewbrogoff.vftv.core.text;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * S2C: one status line for one player (accessibility, R5). The text travels in vanilla's
 * <b>bounded</b> component codec, never its trusted variant, so a component too large to decode is
 * rejected on the client.
 *
 * @param text the message, a translatable component with its arguments
 */
public record StatusLine(Component text) implements CustomPacketPayload {
    /** The payload type, {@code vftv:status_line}. */
    public static final Type<StatusLine> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("vftv", "status_line"));

    /** The wire codec. */
    public static final StreamCodec<RegistryFriendlyByteBuf, StatusLine> STREAM_CODEC =
            StreamCodec.composite(
                    ComponentSerialization.STREAM_CODEC, StatusLine::text, StatusLine::new);

    @Override
    public Type<StatusLine> type() {
        return TYPE;
    }
}
