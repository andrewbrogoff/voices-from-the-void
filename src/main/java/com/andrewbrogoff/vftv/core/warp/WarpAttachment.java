package com.andrewbrogoff.vftv.core.warp;

import com.andrewbrogoff.vftv.api.warp.WarpLimits;
import com.andrewbrogoff.vftv.core.common.RawRecordCodec;
import com.andrewbrogoff.vftv.core.common.RawRecordCodec.Retained;
import com.mojang.serialization.Codec;
import java.util.Optional;
import net.minecraft.nbt.EndTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * The value of the {@code vftv:warp} player attachment: the persisted record, kept raw when it is
 * unreadable, and the transient offer, which is synced with the record but never saved (warp spec,
 * "Kinds and the record", "Sync and state"). Saving and the death copy go through {@link #CODEC},
 * which drops the offer, so an offer never outlives a session or a death.
 *
 * @param record the record, readable or kept raw
 * @param offer the standing Bargain offer, or null
 */
public record WarpAttachment(Retained<WarpRecord> record, @Nullable Offer offer) {
    /** A new player's value. */
    public static final WarpAttachment EMPTY =
            new WarpAttachment(new Retained.Read<>(WarpRecord.EMPTY), null);

    /** The saved form: the record alone, raw when unreadable; an offer is never written. */
    public static final Codec<WarpAttachment> CODEC =
            RawRecordCodec.wrap(WarpRecord.CODEC)
                    .xmap(record -> new WarpAttachment(record, null), WarpAttachment::record);

    /**
     * The sync form, to the owner only: an unreadable flag (never the raw tag), the record when
     * readable, then the offer if one stands.
     */
    public static final StreamCodec<FriendlyByteBuf, WarpAttachment> STREAM_CODEC =
            StreamCodec.of(WarpAttachment::write, WarpAttachment::read);

    /** The readable record, or null when it is unreadable. */
    public @Nullable WarpRecord readable() {
        return record instanceof Retained.Read<WarpRecord> r ? r.value() : null;
    }

    /** This value with {@code next} as its record and the same offer. */
    public WarpAttachment with(WarpRecord next) {
        return new WarpAttachment(new Retained.Read<>(next), offer);
    }

    private static void write(FriendlyByteBuf buf, WarpAttachment value) {
        WarpRecord readable = value.readable();
        buf.writeBoolean(readable != null);
        if (readable != null) {
            WarpRecord.STREAM_CODEC.encode(buf, readable);
        }
        OFFER.encode(buf, Optional.ofNullable(value.offer));
    }

    private static WarpAttachment read(FriendlyByteBuf buf) {
        Retained<WarpRecord> record =
                buf.readBoolean()
                        ? new Retained.Read<>(WarpRecord.STREAM_CODEC.decode(buf))
                        : new Retained.Unreadable<>(EndTag.INSTANCE, "unreadable on the server");
        return new WarpAttachment(record, OFFER.decode(buf).orElse(null));
    }

    /**
     * A standing Bargain offer (warp spec, "The offer"): transient, at most one, synced with the
     * record. Bounds are checked on construction, so a bad packet fails at decode.
     *
     * @param seq the per-login sequence number, ≥ 1
     * @param bargain the bargain's id
     * @param effect the resolved power's mob effect id
     * @param amplifier the power's amplifier, 0–2
     * @param duration the power's duration in ticks, 200–24,000
     * @param costTemporary the price in temporary warp, 0–20
     * @param costLasting the price in lasting warp, 0–20
     * @param expires the overworld game time it fades at
     */
    public record Offer(
            long seq,
            Identifier bargain,
            Identifier effect,
            int amplifier,
            int duration,
            int costTemporary,
            int costLasting,
            long expires) {
        /** Checks the bounds. */
        public Offer {
            if (seq < 1
                    || amplifier < 0
                    || amplifier > 2
                    || duration < 200
                    || duration > 24_000
                    || costTemporary < 0
                    || costTemporary > 20
                    || costLasting < 0
                    || costLasting > 20
                    || bargain.toString().length() > WarpLimits.MAX_ID_CHARS
                    || effect.toString().length() > WarpLimits.MAX_ID_CHARS) {
                throw new IllegalArgumentException("warp offer out of bounds");
            }
        }

        private static final StreamCodec<FriendlyByteBuf, Offer> STREAM_CODEC =
                StreamCodec.of(
                        (buf, o) -> {
                            buf.writeVarLong(o.seq);
                            WarpRecord.ID.encode(buf, o.bargain);
                            WarpRecord.ID.encode(buf, o.effect);
                            buf.writeVarInt(o.amplifier);
                            buf.writeVarInt(o.duration);
                            buf.writeVarInt(o.costTemporary);
                            buf.writeVarInt(o.costLasting);
                            buf.writeVarLong(o.expires);
                        },
                        buf ->
                                new Offer(
                                        buf.readVarLong(),
                                        WarpRecord.ID.decode(buf),
                                        WarpRecord.ID.decode(buf),
                                        buf.readVarInt(),
                                        buf.readVarInt(),
                                        buf.readVarInt(),
                                        buf.readVarInt(),
                                        buf.readVarLong()));
    }

    private static final StreamCodec<FriendlyByteBuf, Optional<Offer>> OFFER =
            ByteBufCodecs.optional(Offer.STREAM_CODEC);
}
