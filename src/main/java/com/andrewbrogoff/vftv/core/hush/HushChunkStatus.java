package com.andrewbrogoff.vftv.core.hush;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import java.util.OptionalLong;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * S2C {@code vftv:hush_chunk_status} (Hush spec, "Sync and state"): a watched chunk's aura status,
 * sent on watch for every chunk, one with no aura included. Its envelope, {@link View}, is also the
 * aura attachment's sync form.
 *
 * @param chunk the chunk's {@code pack()}
 * @param view the status, flux and seep
 */
public record HushChunkStatus(long chunk, View view) implements CustomPacketPayload {
    /** No aura. */
    public static final byte NONE = 0;

    /** A readable aura. */
    public static final byte READABLE = 1;

    /** An unreadable aura. */
    public static final byte UNREADABLE = 2;

    /** The payload's type. */
    public static final Type<HushChunkStatus> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("vftv", "hush_chunk_status"));

    /**
     * The synced envelope {@code {status, flux, seep?}}: never the raw tag.
     *
     * @param status {@link #NONE}, {@link #READABLE} or {@link #UNREADABLE}
     * @param flux 0 to {@code FLUX_FORMAT_MAX}; 0 unless readable
     * @param seep the seep's {@code asLong()}, if readable and set
     */
    public record View(byte status, int flux, OptionalLong seep) {
        /** No aura. */
        public static final View NONE_VIEW = new View(NONE, 0, OptionalLong.empty());

        /** The view of an aura, or of none. */
        public static View of(@org.jspecify.annotations.Nullable Aura aura) {
            if (aura == null) {
                return NONE_VIEW;
            }
            if (!aura.readable()) {
                return new View(UNREADABLE, 0, OptionalLong.empty());
            }
            return new View(
                    READABLE,
                    aura.flux(),
                    aura.hasSeep ? OptionalLong.of(aura.seep) : OptionalLong.empty());
        }

        /** Writes the envelope: one status byte, the flux, a seep flag and the seep. */
        public void write(ByteBuf buf) {
            buf.writeByte(status);
            VarInt.write(buf, flux);
            buf.writeBoolean(seep.isPresent());
            if (seep.isPresent()) {
                buf.writeLong(seep.getAsLong());
            }
        }

        /** Reads an envelope, rejecting anything out of bounds. */
        public static View read(ByteBuf buf) {
            byte status = buf.readByte();
            int flux = VarInt.read(buf);
            boolean hasSeep = buf.readBoolean();
            OptionalLong seep = hasSeep ? OptionalLong.of(buf.readLong()) : OptionalLong.empty();
            if (status < NONE || status > UNREADABLE) {
                throw new DecoderException("hush status " + status);
            }
            if (flux < 0 || flux > com.andrewbrogoff.vftv.api.hush.HushLimits.FLUX_FORMAT_MAX) {
                throw new DecoderException("hush flux " + flux);
            }
            if (status != READABLE && (flux != 0 || hasSeep)) {
                throw new DecoderException("a non-readable status carries no flux or seep");
            }
            return new View(status, flux, seep);
        }
    }

    /** The bounded wire form. */
    public static final StreamCodec<ByteBuf, HushChunkStatus> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeLong(p.chunk());
                        p.view().write(buf);
                    },
                    buf -> new HushChunkStatus(buf.readLong(), View.read(buf)));

    @Override
    public Type<HushChunkStatus> type() {
        return TYPE;
    }
}
