package com.andrewbrogoff.vftv.core.hush;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andrewbrogoff.vftv.core.common.RawRecordCodec;
import com.andrewbrogoff.vftv.core.common.RawRecordCodec.Retained;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.OptionalLong;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

/** The Hush's records and payloads: bounds, defaults, unreadable retention, the wire envelope. */
class HushRecordsTest {
    @Test
    void auraRoundTripsAndKeepsAnUnreadableRecord() {
        Aura aura = new Aura();
        aura.flux = 120;
        aura.hasSeep = true;
        aura.seep = net.minecraft.core.BlockPos.asLong(3, 64, 5);
        aura.steps = -1; // a wrapped word stays readable
        Tag tag = Aura.CODEC.encodeStart(NbtOps.INSTANCE, aura).getOrThrow();
        Aura back = Aura.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
        assertTrue(back.readable());
        assertEquals(120, back.flux());
        assertEquals(-1L, back.steps());
        for (String change : new String[] {"format=2", "flux=1000001", "flux=-1", "extra"}) {
            CompoundTag bad = ((CompoundTag) tag).copy();
            switch (change) {
                case "format=2" -> bad.putInt("format", 2);
                case "flux=1000001" -> bad.putInt("flux", 1_000_001);
                case "flux=-1" -> bad.putInt("flux", -1);
                default -> bad.putString("extra", "kept");
            }
            Aura u = Aura.CODEC.parse(NbtOps.INSTANCE, bad).getOrThrow();
            assertFalse(u.readable(), change);
            assertEquals(0, u.flux(), change);
            assertEquals(bad, Aura.CODEC.encodeStart(NbtOps.INSTANCE, u).getOrThrow(), change);
        }
    }

    @Test
    void aSeepOutsideItsChunkIsUnreadable() {
        Aura aura = new Aura();
        aura.flux = 5;
        aura.hasSeep = true;
        aura.seep = net.minecraft.core.BlockPos.asLong(40, 64, 5);
        assertTrue(aura.checkSeep(new net.minecraft.world.level.ChunkPos(0, 0)));
        assertFalse(aura.readable());
        aura.repair();
        assertTrue(aura.readable());
        assertEquals(0, aura.flux());
    }

    @Test
    void theBellsBounds() {
        var codec = RawRecordCodec.wrap(HushRecords.BELL);
        CompoundTag t = new CompoundTag();
        t.putInt("format", 1);
        t.putInt("sonus", 1000);
        t.putInt("cursor", HushRecords.MAX_CURSOR);
        assertTrue(codec.parse(NbtOps.INSTANCE, t).getOrThrow() instanceof Retained.Read<?>);
        t.putInt("sonus", 1001);
        assertTrue(codec.parse(NbtOps.INSTANCE, t).getOrThrow() instanceof Retained.Unreadable<?>);
        t.putInt("sonus", 0);
        t.putInt("cursor", HushRecords.MAX_CURSOR + 1);
        assertTrue(codec.parse(NbtOps.INSTANCE, t).getOrThrow() instanceof Retained.Unreadable<?>);
    }

    @Test
    void pendingRejectsDuplicatesAndOutOfBounds() {
        var codec = RawRecordCodec.wrap(HushRecords.PENDING);
        CompoundTag entry = new CompoundTag();
        entry.putLong("chunk", 7);
        entry.putInt("flux", 10);
        entry.putLong("seep", 0);
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        list.add(entry);
        CompoundTag p = new CompoundTag();
        p.putInt("format", 1);
        p.putLong("overflow", 3);
        p.put("entries", list);
        assertTrue(codec.parse(NbtOps.INSTANCE, p).getOrThrow() instanceof Retained.Read<?>);
        list.add(entry.copy());
        assertTrue(codec.parse(NbtOps.INSTANCE, p).getOrThrow() instanceof Retained.Unreadable<?>);
        list.remove(1);
        entry.putInt("flux", 0);
        assertTrue(codec.parse(NbtOps.INSTANCE, p).getOrThrow() instanceof Retained.Unreadable<?>);
    }

    @Test
    void theStatusEnvelopeIsBounded() {
        HushChunkStatus status =
                new HushChunkStatus(
                        9,
                        new HushChunkStatus.View(
                                HushChunkStatus.READABLE, 300, OptionalLong.of(42)));
        ByteBuf buf = Unpooled.buffer();
        HushChunkStatus.STREAM_CODEC.encode(buf, status);
        assertEquals(status, HushChunkStatus.STREAM_CODEC.decode(buf));
        for (HushChunkStatus.View bad :
                new HushChunkStatus.View[] {
                    new HushChunkStatus.View((byte) 3, 0, OptionalLong.empty()),
                    new HushChunkStatus.View(
                            HushChunkStatus.READABLE, 1_000_001, OptionalLong.empty()),
                    new HushChunkStatus.View(HushChunkStatus.NONE, 5, OptionalLong.empty()),
                    new HushChunkStatus.View(HushChunkStatus.UNREADABLE, 0, OptionalLong.of(1))
                }) {
            ByteBuf b = Unpooled.buffer();
            bad.write(b);
            assertThrows(
                    DecoderException.class, () -> HushChunkStatus.View.read(b), bad.toString());
        }
        assertEquals(HushChunkStatus.View.NONE_VIEW, HushChunkStatus.View.of(null));
    }
}
