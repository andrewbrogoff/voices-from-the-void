package com.andrewbrogoff.vftv.core.warp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.andrewbrogoff.vftv.core.common.RawRecordCodec.Retained;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

/** The {@code vftv:warp} record's codecs and raw retention (conventions, "Tests"). */
class WarpRecordTest {
    private static final Identifier SILENCE =
            Identifier.fromNamespaceAndPath("vftv", "under_the_silence");
    private static final WarpRecord RECORD =
            new WarpRecord(
                    1, 3, 4, 2, 6, 90, 12, List.of(WarpService.JOINING, SILENCE), 4000, true, 7);

    private static Tag save(WarpAttachment value) {
        return WarpAttachment.CODEC.encodeStart(NbtOps.INSTANCE, value).getOrThrow();
    }

    private static WarpAttachment load(Tag tag) {
        return WarpAttachment.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
    }

    private static WarpAttachment sync(WarpAttachment value) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        WarpAttachment.STREAM_CODEC.encode(buf, value);
        return WarpAttachment.STREAM_CODEC.decode(buf);
    }

    @Test
    void aRecordRoundTripsAndKeepsItsMilestonesSorted() {
        WarpAttachment value = new WarpAttachment(new Retained.Read<>(RECORD), null);
        assertEquals(value, load(save(value)));
        assertEquals(List.of(WarpService.JOINING, SILENCE), RECORD.milestonesPaid());
        assertEquals(15, RECORD.total());
        assertEquals(true, RECORD.joined());
        assertEquals(WarpAttachment.EMPTY, load(save(WarpAttachment.EMPTY)));
    }

    @Test
    void theOfferIsSyncedButNeverSaved() {
        WarpAttachment.Offer offer =
                new WarpAttachment.Offer(
                        1,
                        Identifier.fromNamespaceAndPath("vftv", "keen_eyes"),
                        Identifier.withDefaultNamespace("night_vision"),
                        0,
                        6000,
                        3,
                        0,
                        12_000);
        WarpAttachment value = new WarpAttachment(new Retained.Read<>(RECORD), offer);
        assertEquals(value, sync(value));
        assertNull(load(save(value)).offer(), "an offer never outlives a save or a death");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new WarpAttachment.Offer(
                                0, offer.bargain(), offer.effect(), 0, 6000, 3, 0, 1));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new WarpAttachment.Offer(
                                1, offer.bargain(), offer.effect(), 3, 6000, 3, 0, 1));
    }

    /** Each out-of-bounds field, an unknown or missing key, a future format: unreadable, kept. */
    @Test
    void anUnreadableRecordIsKeptByteIdentical() {
        List<Consumer<CompoundTag>> breaks =
                List.of(
                        t -> t.putInt("format", 2),
                        t -> t.remove("format"),
                        t -> t.putInt("temporary", 1001),
                        t -> t.putInt("lasting", -1),
                        t -> t.putInt("permanent", 101),
                        t -> t.putInt("gear", 101),
                        t -> t.putInt("counter", 10_001),
                        t -> t.putLong("checks", -1),
                        t -> t.putLong("last_listen", -2),
                        t -> t.putLong("revision", -1),
                        t -> t.putString("from_the_future", "kept"),
                        t -> t.putString("temporary", "three"),
                        t -> t.putLong("temporary", 4_294_967_296L),
                        t -> t.putDouble("counter", 1.5),
                        t -> t.putDouble("checks", 1.5),
                        t -> t.putDouble("format", 1.25),
                        t -> {
                            ListTag paid = new ListTag();
                            paid.add(StringTag.valueOf("vftv:joining"));
                            paid.add(StringTag.valueOf("vftv:joining"));
                            t.put("milestones_paid", paid);
                        },
                        t -> {
                            ListTag paid = new ListTag();
                            paid.add(StringTag.valueOf("Not An Id"));
                            t.put("milestones_paid", paid);
                        });
        for (Consumer<CompoundTag> broken : breaks) {
            CompoundTag tag =
                    (CompoundTag) save(new WarpAttachment(new Retained.Read<>(RECORD), null));
            broken.accept(tag);
            WarpAttachment loaded = load(tag);
            assertInstanceOf(Retained.Unreadable.class, loaded.record(), tag.toString());
            assertNull(loaded.readable());
            assertEquals(tag, save(loaded), "written back unchanged");
            WarpAttachment synced = sync(loaded);
            assertInstanceOf(
                    Retained.Unreadable.class, synced.record(), "a flag, never the raw tag");
        }
    }

    @Test
    void theConstructorChecksEveryBound() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new WarpRecord(1, 0, 0, 0, 0, 0, 0, List.of(), -1, false, -1));
        List<Identifier> seventeen = new java.util.ArrayList<>();
        for (int i = 0; i < 17; i++) {
            seventeen.add(Identifier.fromNamespaceAndPath("vftv", "m" + i));
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> new WarpRecord(1, 0, 0, 0, 0, 0, 0, seventeen, -1, false, 0));
        Identifier tooLong = Identifier.fromNamespaceAndPath("vftv", "x".repeat(260));
        assertThrows(
                IllegalArgumentException.class,
                () -> new WarpRecord(1, 0, 0, 0, 0, 0, 0, List.of(tooLong), -1, false, 0));
    }

    @Test
    void aHostileSyncIsRejectedAtDecode() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBoolean(true);
        buf.writeVarInt(1);
        buf.writeVarInt(5000); // temporary over its bound
        assertThrows(RuntimeException.class, () -> WarpAttachment.STREAM_CODEC.decode(buf));
        FriendlyByteBuf many = new FriendlyByteBuf(Unpooled.buffer());
        many.writeBoolean(true);
        for (int i = 0; i < 6; i++) {
            many.writeVarInt(i == 0 ? 1 : 0);
        }
        many.writeVarLong(0);
        many.writeVarInt(17); // milestones over their bound
        assertThrows(RuntimeException.class, () -> WarpAttachment.STREAM_CODEC.decode(many));
    }
}
