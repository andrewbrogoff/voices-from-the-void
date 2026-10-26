package com.andrewbrogoff.vftv.core.research;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.andrewbrogoff.vftv.api.warp.WarpPreview;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

/** The book's payload codecs: round trips, and rejection at decode of out-of-range input. */
class ResearchActionTest {
    private static final String NOTE = "vftv:taking_notes";
    private static final WarpPreview NONE = new WarpPreview(0, 0, 0, false);

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(
                Unpooled.buffer(),
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY),
                ConnectionType.NEOFORGE);
    }

    private static ResearchAction roundTrip(ResearchAction a) {
        RegistryFriendlyByteBuf buf = buffer();
        ResearchAction.STREAM_CODEC.encode(buf, a);
        return ResearchAction.STREAM_CODEC.decode(buf);
    }

    @Test
    void everyVariantRoundTrips() {
        List<ResearchAction.Variant> variants =
                List.of(
                        new ResearchAction.Begin(NOTE),
                        new ResearchAction.Place(NOTE, 4, -3, 2, "vftv:aer"),
                        new ResearchAction.Remove(NOTE, 4, 7, -7),
                        new ResearchAction.Abandon(NOTE, 4),
                        new ResearchAction.Learn(NOTE, 4, -9, new WarpPreview(1, 2, 3, true)),
                        new ResearchAction.Buy(NOTE, 3, new TreeMap<>(Map.of("vftv:lux", 2)), NONE),
                        new ResearchAction.Listen(NOTE, 3, NONE),
                        new ResearchAction.Bookmark(NOTE, true),
                        new ResearchAction.AcceptOffer(7, 3, new WarpPreview(2, 1, 0, false)),
                        new ResearchAction.RefuseOffer(7),
                        new ResearchAction.ListenRite(3, new WarpPreview(0, 2, 0, true)));
        for (ResearchAction.Variant v : variants) {
            ResearchAction a = new ResearchAction(5, 9, v);
            assertEquals(a, roundTrip(a));
        }
    }

    @Test
    void outOfRangeInputIsRejectedAtDecode() {
        TreeMap<String, Integer> oversized = new TreeMap<>();
        for (int i = 0; i <= ResearchAction.MAX_PRICE_ENTRIES; i++) {
            oversized.put("vftv:a" + i, 1);
        }
        List<ResearchAction> bad =
                List.of(
                        new ResearchAction(5, 9, new ResearchAction.Buy(NOTE, 3, oversized, NONE)),
                        new ResearchAction(
                                5,
                                9,
                                new ResearchAction.Buy(
                                        NOTE, 3, new TreeMap<>(Map.of("vftv:lux", 4097)), NONE)),
                        new ResearchAction(
                                5,
                                9,
                                new ResearchAction.Listen(
                                        NOTE, 3, new WarpPreview(101, 0, 0, false))),
                        new ResearchAction(
                                5, 9, new ResearchAction.Place(NOTE, 4, 8, 0, "vftv:aer")),
                        new ResearchAction(
                                5, 9, new ResearchAction.Place(NOTE, -1, 0, 0, "vftv:aer")),
                        new ResearchAction(5, 9, new ResearchAction.Begin("Not An Id")),
                        new ResearchAction(5, 9, new ResearchAction.AcceptOffer(0, 3, NONE)),
                        new ResearchAction(5, 9, new ResearchAction.RefuseOffer(-4)),
                        new ResearchAction(
                                5,
                                9,
                                new ResearchAction.ListenRite(3, new WarpPreview(0, -2, 0, false))),
                        new ResearchAction(-1, 9, new ResearchAction.Begin(NOTE)),
                        new ResearchAction(5, -2, new ResearchAction.Begin(NOTE)));
        for (ResearchAction a : bad) {
            assertThrows(RuntimeException.class, () -> roundTrip(a), a.toString());
        }
        RegistryFriendlyByteBuf unknown = buffer();
        unknown.writeVarInt(1);
        unknown.writeVarInt(1);
        unknown.writeByte(11);
        assertThrows(RuntimeException.class, () -> ResearchAction.STREAM_CODEC.decode(unknown));
    }

    @Test
    void feedbackRoundTripsAndRejectsABadMode() {
        ResearchFeedback.Quotes q =
                new ResearchFeedback.Quotes(
                        7,
                        3,
                        List.of(
                                new ResearchFeedback.EntryQuote(
                                        NOTE, new TreeMap<>(Map.of("vftv:lux", 2)), null),
                                new ResearchFeedback.EntryQuote(
                                        "vftv:being_heard",
                                        new TreeMap<>(),
                                        new WarpPreview(3, 0, 0, false))));
        RegistryFriendlyByteBuf buf = buffer();
        ResearchFeedback.Quotes.STREAM_CODEC.encode(buf, q);
        assertEquals(q, ResearchFeedback.Quotes.STREAM_CODEC.decode(buf));
        ResearchFeedback.Quotes withOffer =
                new ResearchFeedback.Quotes(
                        8,
                        1,
                        List.of(),
                        new ResearchFeedback.OfferQuote(4, new WarpPreview(2, 1, 0, false)),
                        new ResearchFeedback.RiteQuote(
                                new WarpPreview(0, 2, 0, false),
                                "message.vftv.warp.listen_cooldown"));
        buf = buffer();
        ResearchFeedback.Quotes.STREAM_CODEC.encode(buf, withOffer);
        assertEquals(withOffer, ResearchFeedback.Quotes.STREAM_CODEC.decode(buf));
        RegistryFriendlyByteBuf badSeq = buffer();
        ResearchFeedback.Quotes.STREAM_CODEC.encode(
                badSeq,
                new ResearchFeedback.Quotes(
                        8,
                        1,
                        List.of(),
                        new ResearchFeedback.OfferQuote(0, new WarpPreview(0, 0, 0, false)),
                        null));
        assertThrows(
                RuntimeException.class, () -> ResearchFeedback.Quotes.STREAM_CODEC.decode(badSeq));
        ResearchFeedback.Result r =
                new ResearchFeedback.Result(3, false, ResearchActions.SHORT, List.of("vftv:aer"));
        buf = buffer();
        ResearchFeedback.Result.STREAM_CODEC.encode(buf, r);
        assertEquals(r, ResearchFeedback.Result.STREAM_CODEC.decode(buf));
        RegistryFriendlyByteBuf badMode = buffer();
        badMode.writeInt(1);
        badMode.writeByte(4);
        badMode.writeVarInt(0);
        assertThrows(
                RuntimeException.class, () -> ResearchFeedback.Quotes.STREAM_CODEC.decode(badMode));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ResearchFeedback.Result(1, false, "x", List.of("a", "b", "c", "d", "e")));
    }
}
