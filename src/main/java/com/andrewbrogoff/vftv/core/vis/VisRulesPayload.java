package com.andrewbrogoff.vftv.core.vis;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * S2C: the {@link VisRules} in force, sent on join, after every {@code /reload} and when the {@code
 * starter} flag changes (wands spec, "Sync and state").
 *
 * @param rules the rules in force
 */
public record VisRulesPayload(VisRules rules) implements CustomPacketPayload {
    /** The payload's type, {@code vftv:vis_rules}. */
    public static final Type<VisRulesPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("vftv", "vis_rules"));

    /** The wire codec. */
    public static final StreamCodec<ByteBuf, VisRulesPayload> STREAM_CODEC =
            VisRules.STREAM_CODEC.map(VisRulesPayload::new, VisRulesPayload::rules);

    @Override
    public Type<VisRulesPayload> type() {
        return TYPE;
    }
}
