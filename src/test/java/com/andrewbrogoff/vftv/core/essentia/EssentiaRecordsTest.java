package com.andrewbrogoff.vftv.core.essentia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.core.common.RawRecordCodec.Retained;
import com.andrewbrogoff.vftv.core.essentia.EssentiaRecords.JarRecord;
import com.andrewbrogoff.vftv.core.essentia.EssentiaRecords.JarStack;
import com.andrewbrogoff.vftv.core.essentia.EssentiaRecords.LabelRecord;
import com.andrewbrogoff.vftv.core.essentia.model.EssentiaMath.Contents;
import com.andrewbrogoff.vftv.core.essentia.model.EssentiaTuning;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.ByteBufCodecs;
import org.junit.jupiter.api.Test;

/** The essentia records' bounded codecs, the rules record and the flow payload's bounds. */
class EssentiaRecordsTest {
    private static Retained<JarRecord> decodeJar(CompoundTag tag) {
        return JarRecord.RETAINED.parse(NbtOps.INSTANCE, tag).getOrThrow();
    }

    private static CompoundTag jarTag(int format, String aspect, int amount, String label) {
        CompoundTag t = new CompoundTag();
        t.putInt("format", format);
        if (!aspect.isEmpty()) {
            t.putString("aspect", aspect);
        }
        if (amount != 0) {
            t.putInt("amount", amount);
        }
        if (!label.isEmpty()) {
            t.putString("label", label);
        }
        return t;
    }

    @Test
    void jarRecordRoundTripAndZeroNeverWritten() {
        JarRecord full = JarRecord.of(new Contents<>(AspectKeys.AER, 40), AspectKeys.AER);
        Tag tag = JarRecord.BOUNDED.encodeStart(NbtOps.INSTANCE, full).getOrThrow();
        assertEquals(full, JarRecord.BOUNDED.parse(NbtOps.INSTANCE, tag).getOrThrow());
        Tag empty = JarRecord.BOUNDED.encodeStart(NbtOps.INSTANCE, JarRecord.EMPTY).getOrThrow();
        assertEquals(1, ((CompoundTag) empty).size(), "only the format");
        assertEquals(Contents.empty(), JarRecord.EMPTY.contents());
    }

    @Test
    void jarRecordBoundsAreUnreadableAndKeptRaw() {
        for (CompoundTag bad :
                new CompoundTag[] {
                    jarTag(2, "vftv:aer", 1, ""),
                    jarTag(1, "vftv:aer", 10_001, ""),
                    jarTag(1, "vftv:aer", 0, ""),
                    jarTag(1, "", 5, ""),
                    jarTag(1, "vftv:aer", 5, "vftv:terra"),
                    jarTag(1, "Not An Id", 5, ""),
                }) {
            Retained<JarRecord> r = decodeJar(bad);
            assertInstanceOf(Retained.Unreadable.class, r, bad.toString());
            assertEquals(
                    bad,
                    JarRecord.RETAINED.encodeStart(NbtOps.INSTANCE, r).getOrThrow(),
                    "written back");
        }
        CompoundTag extra = jarTag(1, "vftv:aer", 5, "");
        extra.putString("from_the_future", "kept");
        assertInstanceOf(Retained.Unreadable.class, decodeJar(extra), "an unknown key");
        assertInstanceOf(
                Retained.Read.class,
                decodeJar(jarTag(1, "vftv:ghost", 5, "")),
                "a missing id is inert, not unreadable");
        String longId = "vftv:" + "a".repeat(260);
        assertInstanceOf(
                Retained.Unreadable.class, decodeJar(jarTag(1, longId, 5, "")), "an id over 256");
    }

    @Test
    void jarStackAndLabelRecord() {
        JarStack s = new JarStack(1, AspectKeys.IGNIS, 10_000);
        Retained<JarStack> read = new Retained.Read<>(s);
        Tag tag = JarStack.RETAINED.encodeStart(NbtOps.INSTANCE, read).getOrThrow();
        assertEquals(read, JarStack.RETAINED.parse(NbtOps.INSTANCE, tag).getOrThrow());
        CompoundTag zero = (CompoundTag) tag.copy();
        zero.putInt("amount", 0);
        assertInstanceOf(
                Retained.Unreadable.class,
                JarStack.RETAINED.parse(NbtOps.INSTANCE, zero).getOrThrow());
        ByteBuf buf = Unpooled.buffer();
        JarStack.STREAM.encode(buf, read);
        assertEquals(read, JarStack.STREAM.decode(buf), "the component travels whole");
        LabelRecord label = new LabelRecord(1, Optional.of(AspectKeys.AER));
        Tag lt = LabelRecord.BOUNDED.encodeStart(NbtOps.INSTANCE, label).getOrThrow();
        assertEquals(label, LabelRecord.BOUNDED.parse(NbtOps.INSTANCE, lt).getOrThrow());
        CompoundTag future = new CompoundTag();
        future.putInt("format", 3);
        assertInstanceOf(
                Retained.Unreadable.class,
                LabelRecord.RETAINED.parse(NbtOps.INSTANCE, future).getOrThrow());
    }

    @Test
    void rulesCodecs() {
        EssentiaRules rules = new EssentiaRules(1, EssentiaTuning.parse("{\"jar.capacity\": 16}"));
        Tag tag = EssentiaRules.CODEC.encodeStart(NbtOps.INSTANCE, rules).getOrThrow();
        assertEquals(rules, EssentiaRules.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow());
        ((CompoundTag) tag).putInt("tube.rate", 0);
        assertTrue(EssentiaRules.CODEC.parse(NbtOps.INSTANCE, tag).isError(), "out of range");
        ByteBuf buf = Unpooled.buffer();
        EssentiaRules.STREAM_CODEC.encode(buf, rules);
        assertEquals(rules, EssentiaRules.STREAM_CODEC.decode(buf));
        ByteBuf bad = Unpooled.buffer();
        VarInt.write(bad, 2);
        for (int v : EssentiaTuning.DEFAULTS.values()) {
            VarInt.write(bad, v);
        }
        assertThrows(
                DecoderException.class,
                () -> EssentiaRules.STREAM_CODEC.decode(bad),
                "a future format");
        assertEquals(100, rules.tubeIdleTicks());
        assertEquals(256, rules.maxMembers());
        assertEquals(64, rules.maxEndpoints());
        assertEquals(128, rules.maxSuction());
        assertEquals(15, rules.storageMax());
        assertEquals(128, rules.maxHandlerSlots());
    }

    @Test
    void flowPayloadBounds() {
        EssentiaPayloads.Flow flow =
                new EssentiaPayloads.Flow(
                        7L, BlockPos.ZERO, new BlockPos(1, 2, 3), AspectKeys.AER, 1000);
        ByteBuf buf = Unpooled.buffer();
        EssentiaPayloads.Flow.STREAM_CODEC.encode(buf, flow);
        assertEquals(flow, EssentiaPayloads.Flow.STREAM_CODEC.decode(buf));
        for (int amount : new int[] {0, 1001}) {
            ByteBuf b = Unpooled.buffer();
            b.writeLong(7L);
            BlockPos.STREAM_CODEC.encode(b, BlockPos.ZERO);
            BlockPos.STREAM_CODEC.encode(b, BlockPos.ZERO);
            ByteBufCodecs.STRING_UTF8.encode(b, "vftv:aer");
            VarInt.write(b, amount);
            assertThrows(
                    DecoderException.class, () -> EssentiaPayloads.Flow.STREAM_CODEC.decode(b));
        }
        ByteBuf longId = Unpooled.buffer();
        longId.writeLong(7L);
        BlockPos.STREAM_CODEC.encode(longId, BlockPos.ZERO);
        BlockPos.STREAM_CODEC.encode(longId, BlockPos.ZERO);
        ByteBufCodecs.STRING_UTF8.encode(longId, "vftv:" + "a".repeat(300));
        VarInt.write(longId, 4);
        assertThrows(
                DecoderException.class, () -> EssentiaPayloads.Flow.STREAM_CODEC.decode(longId));
    }
}
