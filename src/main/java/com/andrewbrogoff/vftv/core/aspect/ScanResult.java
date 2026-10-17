package com.andrewbrogoff.vftv.core.aspect;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.core.aspect.model.Sounding;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

/**
 * S2C, to the striker only: what a strike revealed (aspects spec, "What a strike reveals", "Sync
 * and state"). An unheard aspect travels without its identity: only its exact amount and which of
 * its prerequisites the player knows. A sounding carries a direction and a band, never a node's
 * position. The readout hold rides along so the tuning file stays server-only.
 *
 * @param state the feedback state
 * @param name the target's name; empty for a sounding
 * @param node a node's type and clarity, empty for anything else
 * @param entries the aspects, in ascending id (unheard ones by their snapshot's order)
 * @param gained the points gained per aspect, in ascending id
 * @param bearing a sounding's answer, or null
 * @param holdTicks the readout hold, before the player's notification time scales it
 */
public record ScanResult(
        State state,
        Component name,
        List<Component> node,
        List<Entry> entries,
        SortedMap<ResourceKey<Aspect>, Integer> gained,
        @Nullable Bearing bearing,
        int holdTicks)
        implements CustomPacketPayload {
    /** The most entries a result carries. */
    public static final int MAX_ENTRIES = 16;

    /** The payload type, {@code vftv:scan_result}. */
    public static final Type<ScanResult> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("vftv", "scan_result"));

    /** The feedback states, each with its own line. */
    public enum State {
        /** Nothing answers: an empty value or no entry. */
        NOTHING,
        /** A scan: aspects, with the gained, known or partial line the entries imply. */
        SCANNED,
        /** A written Book and Quill, refused. */
        INK,
        /** A sounding that found no node. */
        NO_ANSWER,
        /** A sounding that found no node while no starter node exists. */
        NO_WELLSPRING,
        /** A sounding answered: see the bearing. */
        ANSWER
    }

    /**
     * One aspect on the card.
     *
     * @param aspect the aspect, or empty when it is unheard
     * @param amount its exact amount (a node's current amount)
     * @param max a node's capacity, or 0 for anything else
     * @param hint an unheard aspect's prerequisites: each known one, or empty for a blank
     */
    public record Entry(
            Optional<ResourceKey<Aspect>> aspect,
            int amount,
            int max,
            List<Optional<ResourceKey<Aspect>>> hint) {
        /** Copies the hint. */
        public Entry {
            hint = List.copyOf(hint);
        }

        /** Whether the aspect is unheard. */
        public boolean unheard() {
            return aspect.isEmpty();
        }
    }

    /**
     * A sounding's answer.
     *
     * @param direction the compass direction, 0 north to 7 north-west
     * @param vertical above, below or level
     * @param band the distance band
     */
    public record Bearing(int direction, Sounding.Vertical vertical, Sounding.Band band) {
        /** Checks the direction. */
        public Bearing {
            if (direction < 0 || direction > 7) {
                throw new IllegalArgumentException("direction " + direction);
            }
        }
    }

    /** Copies the collections and checks the bounds. */
    public ScanResult {
        node = List.copyOf(node);
        entries = List.copyOf(entries);
        gained = new TreeMap<>(gained);
        if (entries.size() > MAX_ENTRIES || node.size() > 2 || gained.size() > 2 * MAX_ENTRIES) {
            throw new IllegalArgumentException("a scan result is out of bounds");
        }
    }

    /** A result with no aspects: nothing to learn, the ink refusal or an empty sounding. */
    public static ScanResult plain(State state, Component name, int holdTicks) {
        return new ScanResult(state, name, List.of(), List.of(), new TreeMap<>(), null, holdTicks);
    }

    private static final StreamCodec<ByteBuf, ResourceKey<Aspect>> KEY =
            ResourceKey.streamCodec(AspectKeys.REGISTRY);

    private static final StreamCodec<ByteBuf, Optional<ResourceKey<Aspect>>> MAYBE_KEY =
            ByteBufCodecs.optional(KEY);

    private static final StreamCodec<ByteBuf, Entry> ENTRY =
            StreamCodec.composite(
                    MAYBE_KEY,
                    Entry::aspect,
                    ByteBufCodecs.VAR_INT,
                    Entry::amount,
                    ByteBufCodecs.VAR_INT,
                    Entry::max,
                    MAYBE_KEY.apply(ByteBufCodecs.list(2)),
                    Entry::hint,
                    Entry::new);

    private static final StreamCodec<FriendlyByteBuf, Bearing> BEARING =
            StreamCodec.of(
                    (buf, b) -> {
                        buf.writeVarInt(b.direction());
                        buf.writeEnum(b.vertical());
                        buf.writeEnum(b.band());
                    },
                    buf ->
                            new Bearing(
                                    buf.readVarInt(),
                                    buf.readEnum(Sounding.Vertical.class),
                                    buf.readEnum(Sounding.Band.class)));

    /** The wire codec: bounded, with the components in vanilla's bounded codec. */
    public static final StreamCodec<RegistryFriendlyByteBuf, ScanResult> STREAM_CODEC =
            StreamCodec.of(ScanResult::write, ScanResult::read);

    private static void write(RegistryFriendlyByteBuf buf, ScanResult r) {
        buf.writeEnum(r.state);
        ComponentSerialization.STREAM_CODEC.encode(buf, r.name);
        ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list(2)).encode(buf, r.node);
        ENTRY.apply(ByteBufCodecs.list(MAX_ENTRIES)).encode(buf, r.entries);
        buf.writeVarInt(r.gained.size());
        r.gained.forEach(
                (aspect, n) -> {
                    KEY.encode(buf, aspect);
                    buf.writeVarInt(n);
                });
        ByteBufCodecs.optional(BEARING).encode(buf, Optional.ofNullable(r.bearing));
        buf.writeVarInt(r.holdTicks);
    }

    private static ScanResult read(RegistryFriendlyByteBuf buf) {
        State state = buf.readEnum(State.class);
        Component name = ComponentSerialization.STREAM_CODEC.decode(buf);
        List<Component> node =
                ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list(2)).decode(buf);
        List<Entry> entries =
                new ArrayList<>(ENTRY.apply(ByteBufCodecs.list(MAX_ENTRIES)).decode(buf));
        int count = buf.readVarInt();
        if (count < 0 || count > 2 * MAX_ENTRIES) {
            throw new DecoderException("too many gained aspects: " + count);
        }
        SortedMap<ResourceKey<Aspect>, Integer> gained = new TreeMap<>();
        for (int i = 0; i < count; i++) {
            gained.put(KEY.decode(buf), buf.readVarInt());
        }
        Bearing bearing = ByteBufCodecs.optional(BEARING).decode(buf).orElse(null);
        int hold = buf.readVarInt();
        try {
            return new ScanResult(state, name, node, entries, gained, bearing, hold);
        } catch (IllegalArgumentException e) {
            throw new DecoderException(e);
        }
    }

    @Override
    public Type<ScanResult> type() {
        return TYPE;
    }
}
