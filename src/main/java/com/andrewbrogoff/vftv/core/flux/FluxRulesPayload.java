package com.andrewbrogoff.vftv.core.flux;

import com.andrewbrogoff.vftv.api.flux.FluxRules;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * S2C: the {@link FluxRules} in force, sent to every player on join, after every {@code /reload}
 * and after a Hush config change (conventions, "Rules records").
 *
 * @param rules the rules in force
 */
public record FluxRulesPayload(FluxRules rules) implements CustomPacketPayload {
    /** The payload's type, {@code vftv:flux_rules}. */
    public static final Type<FluxRulesPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("vftv", "flux_rules"));

    /** The fixed-size wire form. */
    public static final StreamCodec<ByteBuf, FluxRulesPayload> STREAM_CODEC =
            FluxRulesCodecs.STREAM_CODEC.map(FluxRulesPayload::new, FluxRulesPayload::rules);

    @Override
    public Type<FluxRulesPayload> type() {
        return TYPE;
    }
}
