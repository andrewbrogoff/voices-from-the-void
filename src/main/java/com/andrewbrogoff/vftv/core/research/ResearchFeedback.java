package com.andrewbrogoff.vftv.core.research;

import com.andrewbrogoff.vftv.api.warp.WarpPreview;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * {@code ResearchFeedback} (research spec, "Sync and state"), to the book's owner only: the {@link
 * Quotes} an open book prices its commits from, and the {@link Result} of each action.
 */
public final class ResearchFeedback {
    /** The most quoted entries (the id-set bound). */
    public static final int MAX_QUOTED = 4096;

    /** The most reason arguments. */
    public static final int MAX_ARGS = 4;

    /** The longest reason argument. */
    public static final int MAX_ARG_CHARS = 256;

    /** The largest quoted price amount. */
    public static final int MAX_QUOTED_PRICE = 1_000_000;

    private ResearchFeedback() {}

    /**
     * One non-HIDDEN {@code note} or {@code listen} entry's quote.
     *
     * @param entry the entry id
     * @param price a note's skip price, aspect to amount; empty for a listen
     * @param warp the player's preview of the entry's warp, or null when it carries none
     */
    public record EntryQuote(
            String entry, SortedMap<String, Integer> price, @Nullable WarpPreview warp) {
        /** Copies the price. */
        public EntryQuote {
            price = new TreeMap<>(price);
        }
    }

    /**
     * The live offer's quote.
     *
     * @param seq the offer's sequence number
     * @param warp the player's preview of its cost, with {@code clipped}
     */
    public record OfferQuote(long seq, WarpPreview warp) {}

    /**
     * The Listen rite's quote.
     *
     * @param warp the player's preview of {@code listen.cost}, with {@code clipped}
     * @param refusal the rite's current refusal key from the warp spec's rows 3–6, or empty
     */
    public record RiteQuote(WarpPreview warp, String refusal) {}

    /**
     * The book's quotes.
     *
     * @param revision the open book's surface revision, bumped on every re-send
     * @param mode {@code research.mode}: 0 gentle, 1 standard, 2 hard, 3 skip
     * @param entries every non-HIDDEN note and listen entry's quote, in ascending id order
     * @param offer the live offer's quote, or null with none
     * @param rite the Listen rite's quote, or null while the warp record is unreadable
     */
    public record Quotes(
            int revision,
            int mode,
            List<EntryQuote> entries,
            @Nullable OfferQuote offer,
            @Nullable RiteQuote rite)
            implements CustomPacketPayload {
        /** {@code vftv:research_quotes}. */
        public static final Type<Quotes> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath("vftv", "research_quotes"));

        /** The bounded codec. */
        public static final StreamCodec<RegistryFriendlyByteBuf, Quotes> STREAM_CODEC =
                StreamCodec.of(Quotes::write, Quotes::read);

        /** Copies the entries. */
        public Quotes {
            entries = List.copyOf(entries);
        }

        /** Quotes with no offer and no rite. */
        public Quotes(int revision, int mode, List<EntryQuote> entries) {
            this(revision, mode, entries, null, null);
        }

        /** The quote of an entry, or null when it isn't quoted. */
        public @Nullable EntryQuote of(String entry) {
            for (EntryQuote q : entries) {
                if (q.entry().equals(entry)) {
                    return q;
                }
            }
            return null;
        }

        /** Whether the priced content (mode and entries) equals another quote's. */
        public boolean samePrices(Quotes other) {
            return mode == other.mode
                    && entries.equals(other.entries)
                    && java.util.Objects.equals(offer, other.offer)
                    && java.util.Objects.equals(rite, other.rite);
        }

        @Override
        public Type<Quotes> type() {
            return TYPE;
        }

        private static void write(RegistryFriendlyByteBuf buf, Quotes q) {
            buf.writeInt(q.revision);
            buf.writeByte(q.mode);
            buf.writeVarInt(q.entries.size());
            for (EntryQuote e : q.entries) {
                buf.writeUtf(e.entry(), ResearchAction.MAX_ID_CHARS);
                buf.writeVarInt(e.price().size());
                e.price()
                        .forEach(
                                (a, n) -> {
                                    buf.writeUtf(a, ResearchAction.MAX_ID_CHARS);
                                    buf.writeVarInt(n);
                                });
                WarpPreview w = e.warp();
                buf.writeBoolean(w != null);
                if (w != null) {
                    ResearchAction.writeWarp(buf, w);
                }
            }
            buf.writeBoolean(q.offer != null);
            if (q.offer != null) {
                buf.writeVarLong(q.offer.seq());
                ResearchAction.writeWarp(buf, q.offer.warp());
            }
            buf.writeBoolean(q.rite != null);
            if (q.rite != null) {
                ResearchAction.writeWarp(buf, q.rite.warp());
                buf.writeUtf(q.rite.refusal(), MAX_ARG_CHARS);
            }
        }

        private static Quotes read(RegistryFriendlyByteBuf buf) {
            int revision = buf.readInt();
            int mode = ResearchAction.bounded(buf.readByte(), 0, 3, "mode");
            int size = ResearchAction.bounded(buf.readVarInt(), 0, MAX_QUOTED, "quoted entries");
            List<EntryQuote> entries = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                String entry = ResearchAction.id(buf);
                int prices =
                        ResearchAction.bounded(
                                buf.readVarInt(), 0, ResearchAction.MAX_PRICE_ENTRIES, "price");
                SortedMap<String, Integer> price = new TreeMap<>();
                for (int p = 0; p < prices; p++) {
                    String aspect = ResearchAction.id(buf);
                    int n = ResearchAction.bounded(buf.readVarInt(), 1, MAX_QUOTED_PRICE, "price");
                    if (price.put(aspect, n) != null) {
                        throw new DecoderException("duplicate price aspect " + aspect);
                    }
                }
                WarpPreview warp = buf.readBoolean() ? ResearchAction.warp(buf) : null;
                entries.add(new EntryQuote(entry, price, warp));
            }
            OfferQuote offer = null;
            if (buf.readBoolean()) {
                long seq = buf.readVarLong();
                if (seq < 1) {
                    throw new DecoderException("offer seq " + seq);
                }
                offer = new OfferQuote(seq, ResearchAction.warp(buf));
            }
            RiteQuote rite =
                    buf.readBoolean()
                            ? new RiteQuote(ResearchAction.warp(buf), buf.readUtf(MAX_ARG_CHARS))
                            : null;
            return new Quotes(revision, mode, entries, offer, rite);
        }
    }

    /**
     * One action's result: attachments alone can't report a refusal that changes no state.
     *
     * @param requestId the action's request id
     * @param ok whether it was done
     * @param reason a refusal's message key; empty when done
     * @param args the reason's arguments, at most 4 of at most 256 chars (aspect and entry ids stay
     *     ids, for the book to name)
     */
    public record Result(int requestId, boolean ok, String reason, List<String> args)
            implements CustomPacketPayload {
        /** {@code vftv:research_result}. */
        public static final Type<Result> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath("vftv", "research_result"));

        /** The bounded codec. */
        public static final StreamCodec<RegistryFriendlyByteBuf, Result> STREAM_CODEC =
                StreamCodec.of(Result::write, Result::read);

        /** Copies and bounds the arguments. */
        public Result {
            args = List.copyOf(args);
            if (args.size() > MAX_ARGS || args.stream().anyMatch(a -> a.length() > MAX_ARG_CHARS)) {
                throw new IllegalArgumentException("at most 4 arguments of 256 chars");
            }
        }

        @Override
        public Type<Result> type() {
            return TYPE;
        }

        private static void write(FriendlyByteBuf buf, Result r) {
            buf.writeVarInt(r.requestId);
            buf.writeBoolean(r.ok);
            buf.writeUtf(r.reason, MAX_ARG_CHARS);
            buf.writeVarInt(r.args.size());
            r.args.forEach(a -> buf.writeUtf(a, MAX_ARG_CHARS));
        }

        private static Result read(FriendlyByteBuf buf) {
            int requestId = buf.readVarInt();
            boolean ok = buf.readBoolean();
            String reason = buf.readUtf(MAX_ARG_CHARS);
            int size = ResearchAction.bounded(buf.readVarInt(), 0, MAX_ARGS, "reason args");
            List<String> args = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                args.add(buf.readUtf(MAX_ARG_CHARS));
            }
            return new Result(requestId, ok, reason, args);
        }
    }
}
