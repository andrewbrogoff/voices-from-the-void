package com.andrewbrogoff.vftv.core.research;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.andrewbrogoff.vftv.core.common.RawRecordCodec.Retained;
import com.andrewbrogoff.vftv.core.research.model.Note;
import com.andrewbrogoff.vftv.core.research.model.ResearchDelta;
import com.andrewbrogoff.vftv.core.research.model.ResearchRecord;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.VarInt;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

class ResearchAttachmentTest {
    private static Note note(String entry) {
        return new Note(
                entry,
                new Note.Snapshot(3, 5, Note.PaletteHelp.NEIGHBOURS, 2, 3),
                4,
                List.of(
                        new Note.Placed(new Note.Cell(0, 0), "vftv:aer"),
                        new Note.Placed(new Note.Cell(-3, 1), "vftv:ignis")),
                List.of(new Note.Cell(4, -4)),
                List.of(new Note.Placed(new Note.Cell(1, -1), "vftv:lux")),
                7);
    }

    /** Every field set, with distinct values. */
    private static ResearchRecord full() {
        return new ResearchRecord(
                1,
                2,
                new TreeSet<>(List.of("vftv:a", "pack:gone")),
                new TreeSet<>(List.of("vftv:r")),
                new TreeSet<>(List.of("vftv:l")),
                new TreeSet<>(List.of("vftv:rl")),
                new TreeMap<>(Map.of("vftv:n", note("vftv:n"))),
                List.of("vftv:b2", "vftv:b1"),
                11,
                new TreeSet<>(List.of("vftv:paid")),
                new TreeMap<>(Map.of("vftv:n", 3)));
    }

    private static Tag save(ResearchAttachment a) {
        return ResearchAttachment.CODEC.encodeStart(NbtOps.INSTANCE, a).getOrThrow();
    }

    private static ResearchAttachment load(Tag tag) {
        return ResearchAttachment.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
    }

    private static RegistryFriendlyByteBuf buf() {
        return new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
    }

    @Test
    void theRecordRoundTripsAndTheChangeIsNeverSaved() {
        ResearchAttachment a =
                new ResearchAttachment(
                        new Retained.Read<>(full()),
                        ResearchDelta.between(ResearchRecord.EMPTY, full()));
        ResearchAttachment loaded = load(save(a));
        assertEquals(full(), loaded.readable());
        assertNull(loaded.change());
        assertEquals(ResearchAttachment.EMPTY, load(save(ResearchAttachment.EMPTY)));
    }

    private static void unreadableAfter(Consumer<CompoundTag> change) {
        CompoundTag tag =
                (CompoundTag) save(new ResearchAttachment(new Retained.Read<>(full()), null));
        change.accept(tag);
        ResearchAttachment loaded = load(tag);
        assertInstanceOf(Retained.Unreadable.class, loaded.record());
        assertNull(loaded.readable());
        assertEquals(tag, save(loaded), "written back unchanged");
    }

    @Test
    void unknownFormatsKeysAndOutOfBoundsAreKeptRaw() {
        unreadableAfter(t -> t.putInt("format", 2));
        unreadableAfter(t -> t.remove("format"));
        unreadableAfter(t -> t.putString("from_the_future", "kept"));
        unreadableAfter(t -> t.putInt("stage", 5));
        unreadableAfter(t -> t.putLong("revision", -1));
        unreadableAfter(t -> t.getListOrEmpty("learned").add(StringTag.valueOf("Not An Id")));
        unreadableAfter(t -> t.getListOrEmpty("learned").add(StringTag.valueOf("vftv:a")));
        unreadableAfter(t -> t.getListOrEmpty("learned").add(StringTag.valueOf("unqualified")));
        unreadableAfter(
                t -> {
                    ListTag bookmarks = new ListTag();
                    for (int i = 0; i < 10; i++) {
                        bookmarks.add(StringTag.valueOf("vftv:b" + i));
                    }
                    t.put("bookmarks", bookmarks);
                });
        unreadableAfter(t -> t.getListOrEmpty("notes").getCompoundOrEmpty(0).putInt("radius", 9));
    }

    @Test
    void aBaselineCarriesTheSyncedFieldsOnly() {
        ResearchAttachment a = new ResearchAttachment(new Retained.Read<>(full()), null);
        RegistryFriendlyByteBuf buf = buf();
        ResearchAttachment.SYNC.write(buf, a, true);
        ResearchAttachment read = ResearchAttachment.SYNC.read(buf, null);
        assertEquals(full().synced(), read.readable());
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void aDeltaAppliesToTheClientsRecord() {
        ResearchRecord before = full();
        ResearchRecord after = before.learn("vftv:new").withNotes(Map.of()).withStage(3);
        ResearchAttachment changed =
                new ResearchAttachment(
                        new Retained.Read<>(after), ResearchDelta.between(before, after));
        RegistryFriendlyByteBuf buf = buf();
        ResearchAttachment.SYNC.write(buf, changed, false);
        ResearchAttachment client =
                new ResearchAttachment(new Retained.Read<>(before.synced()), null);
        assertEquals(after.synced(), ResearchAttachment.SYNC.read(buf, client).readable());
        // A delta before any baseline applies to an empty record.
        RegistryFriendlyByteBuf again = buf();
        ResearchAttachment.SYNC.write(again, changed, false);
        assertEquals(
                3,
                java.util.Objects.requireNonNull(
                                ResearchAttachment.SYNC.read(again, null).readable())
                        .stage());
        // A baseline request writes the whole record even when a change is known.
        RegistryFriendlyByteBuf baseline = buf();
        ResearchAttachment.SYNC.write(baseline, changed, true);
        assertEquals(after.synced(), ResearchAttachment.SYNC.read(baseline, null).readable());
    }

    @Test
    void anUnreadableRecordSendsOnlyTheFlag() {
        ResearchAttachment raw =
                new ResearchAttachment(new Retained.Unreadable<>(new CompoundTag(), "test"), null);
        RegistryFriendlyByteBuf buf = buf();
        ResearchAttachment.SYNC.write(buf, raw, true);
        assertEquals(1, buf.readableBytes());
        assertNull(ResearchAttachment.SYNC.read(buf, null).readable());
    }

    @Test
    void hostileInputIsRejectedAtDecode() {
        RegistryFriendlyByteBuf unknown = buf();
        unknown.writeByte(9);
        assertThrows(DecoderException.class, () -> ResearchAttachment.SYNC.read(unknown, null));
        RegistryFriendlyByteBuf oversized = buf();
        oversized.writeByte(1);
        VarInt.write(oversized, 0);
        oversized.writeByte(0); // revision 0 as a VarLong
        VarInt.write(oversized, ResearchRecord.MAX_IDS + 1);
        assertThrows(DecoderException.class, () -> ResearchAttachment.SYNC.read(oversized, null));
        RegistryFriendlyByteBuf badStage = buf();
        badStage.writeByte(1);
        VarInt.write(badStage, 7);
        badStage.writeByte(0);
        for (int i = 0; i < 6; i++) {
            VarInt.write(badStage, 0);
        }
        assertThrows(DecoderException.class, () -> ResearchAttachment.SYNC.read(badStage, null));
        RegistryFriendlyByteBuf badId = buf();
        badId.writeByte(1);
        VarInt.write(badId, 0);
        badId.writeByte(0);
        VarInt.write(badId, 1);
        badId.writeUtf("Not An Id");
        assertThrows(DecoderException.class, () -> ResearchAttachment.SYNC.read(badId, null));
    }
}
