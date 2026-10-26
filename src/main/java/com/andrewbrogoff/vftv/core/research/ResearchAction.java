package com.andrewbrogoff.vftv.core.research;

import com.andrewbrogoff.vftv.api.research.ResearchLimits;
import com.andrewbrogoff.vftv.api.warp.WarpPreview;
import io.netty.handler.codec.DecoderException;
import java.util.SortedMap;
import java.util.TreeMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * {@code ResearchAction(containerId, requestId, variant)} (research spec, "Sync and state"): every
 * change the Commonplace Book asks for. Every size and range is checked at decode, before any
 * planning; out-of-range or malformed input is rejected there.
 *
 * @param containerId the open {@code CommonplaceMenu}'s container id
 * @param requestId the client's request id, rising within a connection
 * @param variant what is asked
 */
public record ResearchAction(int containerId, int requestId, Variant variant)
        implements CustomPacketPayload {
    /** {@code vftv:research_action}. */
    public static final Type<ResearchAction> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("vftv", "research_action"));

    /** The bounded codec. */
    public static final StreamCodec<RegistryFriendlyByteBuf, ResearchAction> STREAM_CODEC =
            StreamCodec.of(ResearchAction::write, ResearchAction::read);

    /** The longest id text accepted. */
    public static final int MAX_ID_CHARS = 256;

    /** The most entries in an echoed price. */
    public static final int MAX_PRICE_ENTRIES = 64;

    /** The largest amount in an echoed price. */
    public static final int MAX_PRICE = 4096;

    /** The largest echoed warp delta. */
    public static final int MAX_WARP = 100;

    @Override
    public Type<ResearchAction> type() {
        return TYPE;
    }

    /** What an action asks. */
    public sealed interface Variant {
        /** Whether it is a commit, of which at most one per player per tick is processed. */
        default boolean commit() {
            return false;
        }
    }

    /**
     * Begin a note.
     *
     * @param entry the note entry
     */
    public record Begin(String entry) implements Variant {
        @Override
        public boolean commit() {
            return true;
        }
    }

    /**
     * Place an aspect in an empty cell.
     *
     * @param entry the note entry
     * @param noteRevision the note's revision as the client saw it
     * @param q the cell's column
     * @param r the cell's row
     * @param aspect the aspect id
     */
    public record Place(String entry, long noteRevision, int q, int r, String aspect)
            implements Variant {}

    /**
     * Remove a placement.
     *
     * @param entry the note entry
     * @param noteRevision the note's revision as the client saw it
     * @param q the cell's column
     * @param r the cell's row
     */
    public record Remove(String entry, long noteRevision, int q, int r) implements Variant {}

    /**
     * Abandon a note, solved or not, at no cost.
     *
     * @param entry the note entry
     * @param noteRevision the note's revision as the client saw it
     */
    public record Abandon(String entry, long noteRevision) implements Variant {
        @Override
        public boolean commit() {
            return true;
        }
    }

    /**
     * Learn a solved note: a quoted commit.
     *
     * @param entry the note entry
     * @param noteRevision the note's revision as the client saw it
     * @param revision the quotes' revision the client holds
     * @param echoedWarp the warp preview the confirm showed
     */
    public record Learn(String entry, long noteRevision, int revision, WarpPreview echoedWarp)
            implements Variant {
        @Override
        public boolean commit() {
            return true;
        }
    }

    /**
     * Buy an entry in skip mode: a quoted commit.
     *
     * @param entry the note entry
     * @param revision the quotes' revision the client holds
     * @param echoedPrice the price the confirm showed
     * @param echoedWarp the warp preview the confirm showed
     */
    public record Buy(
            String entry,
            int revision,
            SortedMap<String, Integer> echoedPrice,
            WarpPreview echoedWarp)
            implements Variant {
        /** Copies the price. */
        public Buy {
            echoedPrice = new TreeMap<>(echoedPrice);
        }

        @Override
        public boolean commit() {
            return true;
        }
    }

    /**
     * Listen: a {@code listen} entry's commit, a quoted commit.
     *
     * @param entry the listen entry
     * @param revision the quotes' revision the client holds
     * @param echoedWarp the warp preview the confirm showed
     */
    public record Listen(String entry, int revision, WarpPreview echoedWarp) implements Variant {
        @Override
        public boolean commit() {
            return true;
        }
    }

    /**
     * Set or clear a bookmark.
     *
     * @param entry the entry
     * @param on whether to set it
     */
    public record Bookmark(String entry, boolean on) implements Variant {}

    /**
     * Accept the live offer: a quoted commit, whose offer checks are the warp spec's.
     *
     * @param offerSeq the offer's sequence number, positive
     * @param revision the quotes' revision the client holds
     * @param echoedWarp the offer's warp preview the confirm showed
     */
    public record AcceptOffer(long offerSeq, int revision, WarpPreview echoedWarp)
            implements Variant {
        @Override
        public boolean commit() {
            return true;
        }
    }

    /**
     * Refuse the live offer, at no cost.
     *
     * @param offerSeq the offer's sequence number, positive
     */
    public record RefuseOffer(long offerSeq) implements Variant {
        @Override
        public boolean commit() {
            return true;
        }
    }

    /**
     * The Listen rite: a quoted commit passed to {@code WarpService.listen}.
     *
     * @param revision the quotes' revision the client holds
     * @param echoedWarp the rite's warp preview the card showed
     */
    public record ListenRite(int revision, WarpPreview echoedWarp) implements Variant {
        @Override
        public boolean commit() {
            return true;
        }
    }

    private static void write(RegistryFriendlyByteBuf buf, ResearchAction a) {
        buf.writeVarInt(a.containerId);
        buf.writeVarInt(a.requestId);
        switch (a.variant) {
            case Begin v -> {
                buf.writeByte(0);
                buf.writeUtf(v.entry(), MAX_ID_CHARS);
            }
            case Place v -> {
                buf.writeByte(1);
                buf.writeUtf(v.entry(), MAX_ID_CHARS);
                buf.writeVarLong(v.noteRevision());
                buf.writeByte(v.q());
                buf.writeByte(v.r());
                buf.writeUtf(v.aspect(), MAX_ID_CHARS);
            }
            case Remove v -> {
                buf.writeByte(2);
                buf.writeUtf(v.entry(), MAX_ID_CHARS);
                buf.writeVarLong(v.noteRevision());
                buf.writeByte(v.q());
                buf.writeByte(v.r());
            }
            case Abandon v -> {
                buf.writeByte(3);
                buf.writeUtf(v.entry(), MAX_ID_CHARS);
                buf.writeVarLong(v.noteRevision());
            }
            case Learn v -> {
                buf.writeByte(4);
                buf.writeUtf(v.entry(), MAX_ID_CHARS);
                buf.writeVarLong(v.noteRevision());
                buf.writeInt(v.revision());
                writeWarp(buf, v.echoedWarp());
            }
            case Buy v -> {
                buf.writeByte(5);
                buf.writeUtf(v.entry(), MAX_ID_CHARS);
                buf.writeInt(v.revision());
                buf.writeVarInt(v.echoedPrice().size());
                v.echoedPrice()
                        .forEach(
                                (aspect, n) -> {
                                    buf.writeUtf(aspect, MAX_ID_CHARS);
                                    buf.writeVarInt(n);
                                });
                writeWarp(buf, v.echoedWarp());
            }
            case Listen v -> {
                buf.writeByte(6);
                buf.writeUtf(v.entry(), MAX_ID_CHARS);
                buf.writeInt(v.revision());
                writeWarp(buf, v.echoedWarp());
            }
            case Bookmark v -> {
                buf.writeByte(7);
                buf.writeUtf(v.entry(), MAX_ID_CHARS);
                buf.writeBoolean(v.on());
            }
            case AcceptOffer v -> {
                buf.writeByte(8);
                buf.writeVarLong(v.offerSeq());
                buf.writeInt(v.revision());
                writeWarp(buf, v.echoedWarp());
            }
            case RefuseOffer v -> {
                buf.writeByte(9);
                buf.writeVarLong(v.offerSeq());
            }
            case ListenRite v -> {
                buf.writeByte(10);
                buf.writeInt(v.revision());
                writeWarp(buf, v.echoedWarp());
            }
        }
    }

    private static ResearchAction read(RegistryFriendlyByteBuf buf) {
        int containerId = nonNegative(buf.readVarInt(), "container id");
        int requestId = nonNegative(buf.readVarInt(), "request id");
        int tag = buf.readByte();
        Variant variant =
                switch (tag) {
                    case 0 -> new Begin(id(buf));
                    case 1 ->
                            new Place(id(buf), noteRevision(buf), coord(buf), coord(buf), id(buf));
                    case 2 -> new Remove(id(buf), noteRevision(buf), coord(buf), coord(buf));
                    case 3 -> new Abandon(id(buf), noteRevision(buf));
                    case 4 -> new Learn(id(buf), noteRevision(buf), buf.readInt(), warp(buf));
                    case 5 -> new Buy(id(buf), buf.readInt(), price(buf), warp(buf));
                    case 6 -> new Listen(id(buf), buf.readInt(), warp(buf));
                    case 7 -> new Bookmark(id(buf), buf.readBoolean());
                    case 8 -> new AcceptOffer(offerSeq(buf), buf.readInt(), warp(buf));
                    case 9 -> new RefuseOffer(offerSeq(buf));
                    case 10 -> new ListenRite(buf.readInt(), warp(buf));
                    default -> throw new DecoderException("unknown research action " + tag);
                };
        return new ResearchAction(containerId, requestId, variant);
    }

    static void writeWarp(FriendlyByteBuf buf, WarpPreview w) {
        buf.writeVarInt(w.dT());
        buf.writeVarInt(w.dL());
        buf.writeVarInt(w.dP());
        buf.writeBoolean(w.clipped());
    }

    /** Three deltas, each 0-100, and the clipped flag. */
    static WarpPreview warp(FriendlyByteBuf buf) {
        return new WarpPreview(
                bounded(buf.readVarInt(), 0, MAX_WARP, "warp"),
                bounded(buf.readVarInt(), 0, MAX_WARP, "warp"),
                bounded(buf.readVarInt(), 0, MAX_WARP, "warp"),
                buf.readBoolean());
    }

    private static SortedMap<String, Integer> price(FriendlyByteBuf buf) {
        int size = bounded(buf.readVarInt(), 0, MAX_PRICE_ENTRIES, "price entries");
        SortedMap<String, Integer> price = new TreeMap<>();
        for (int i = 0; i < size; i++) {
            String aspect = id(buf);
            if (price.put(aspect, bounded(buf.readVarInt(), 1, MAX_PRICE, "price")) != null) {
                throw new DecoderException("duplicate price aspect " + aspect);
            }
        }
        return price;
    }

    /** A {@code namespace:path} id of at most {@value #MAX_ID_CHARS} chars. */
    static String id(FriendlyByteBuf buf) {
        String text = buf.readUtf(MAX_ID_CHARS);
        Identifier id = Identifier.tryParse(text);
        if (id == null) {
            throw new DecoderException("not an id: " + text);
        }
        return id.toString();
    }

    private static long noteRevision(FriendlyByteBuf buf) {
        long revision = buf.readVarLong();
        if (revision < 0) {
            throw new DecoderException("note revision " + revision);
        }
        return revision;
    }

    /** A positive offer sequence number. */
    private static long offerSeq(FriendlyByteBuf buf) {
        long seq = buf.readVarLong();
        if (seq < 1) {
            throw new DecoderException("offer seq " + seq);
        }
        return seq;
    }

    private static int coord(FriendlyByteBuf buf) {
        int max = ResearchLimits.MAX_GRID_RADIUS;
        return bounded(buf.readByte(), -max, max, "cell coordinate");
    }

    private static int nonNegative(int value, String what) {
        return bounded(value, 0, Integer.MAX_VALUE, what);
    }

    static int bounded(int value, int min, int max, String what) {
        if (value < min || value > max) {
            throw new DecoderException(what + " " + value + " outside " + min + ".." + max);
        }
        return value;
    }
}
