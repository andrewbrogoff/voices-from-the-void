package com.andrewbrogoff.vftv.core.aspect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.core.aspect.model.Knowledge;
import com.andrewbrogoff.vftv.core.aspect.model.Sounding;
import com.andrewbrogoff.vftv.core.common.RawRecordCodec.Retained;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

/** The knowledge attachment's codecs and the {@code ScanResult} payload. */
class AspectCodecsTest {
    private static final Knowledge SAMPLE =
            new Knowledge(
                    Set.of("vftv:aer", "vftv:ignis"),
                    Map.of(
                            "item:minecraft:stone",
                            Knowledge.Scan.COMPLETE,
                            "entity:minecraft:cow",
                            new Knowledge.Scan(
                                    false,
                                    Map.of("vftv:aer", 2, "vftv:victus", 3),
                                    Set.of("vftv:aer")),
                            "node:42",
                            Knowledge.Scan.COMPLETE),
                    Map.of("vftv:aer", 6, "vftv:ignis", 1),
                    null);

    private static RegistryFriendlyByteBuf buf() {
        return new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
    }

    private static Retained<Knowledge> decode(Tag tag) {
        return KnowledgeCodecs.RETAINED.parse(NbtOps.INSTANCE, tag).getOrThrow();
    }

    @Test
    void theRecordRoundTripsThroughNbt() {
        Tag tag = KnowledgeCodecs.BOUNDED.encodeStart(NbtOps.INSTANCE, SAMPLE).getOrThrow();
        assertEquals(new Retained.Read<>(SAMPLE), decode(tag));
        CompoundTag compound = (CompoundTag) tag;
        assertEquals(1, compound.getIntOr("format", 0));
    }

    @Test
    void outOfBoundsRecordsAreUnreadableAndKeptRaw() {
        CompoundTag good =
                (CompoundTag)
                        KnowledgeCodecs.BOUNDED.encodeStart(NbtOps.INSTANCE, SAMPLE).getOrThrow();
        for (java.util.function.Consumer<CompoundTag> breakIt :
                List.<java.util.function.Consumer<CompoundTag>>of(
                        t -> t.putInt("format", 2),
                        t -> t.remove("format"),
                        t -> t.putString("future", "x"),
                        t -> t.getCompoundOrEmpty("pool").putInt("vftv:aer", 0),
                        t -> t.getCompoundOrEmpty("scans").put("bogus:key", new CompoundTag()),
                        t ->
                                t.getCompoundOrEmpty("scans")
                                        .getCompoundOrEmpty("entity:minecraft:cow")
                                        .putString("paid", "x"))) {
            CompoundTag broken = good.copy();
            breakIt.accept(broken);
            Retained<Knowledge> read = decode(broken);
            if (!(read instanceof Retained.Unreadable<Knowledge> unreadable)) {
                throw new AssertionError("readable: " + broken);
            }
            assertEquals(broken, unreadable.raw());
            assertEquals(
                    broken,
                    KnowledgeCodecs.RETAINED.encodeStart(NbtOps.INSTANCE, read).getOrThrow(),
                    "written back unchanged");
        }
    }

    @Test
    void theOwnerSyncSendsABaselineThenDeltas() {
        Retained<Knowledge> server = new Retained.Read<>(SAMPLE);
        RegistryFriendlyByteBuf b = buf();
        KnowledgeCodecs.SYNC.write(b, server, true);
        Retained<Knowledge> client = KnowledgeCodecs.SYNC.read(b, null);
        assertEquals(server, client);

        Knowledge next = java.util.Objects.requireNonNull(SAMPLE.credit("vftv:aer", 4));
        Knowledge.Delta delta = java.util.Objects.requireNonNull(next.delta());
        RegistryFriendlyByteBuf d = buf();
        KnowledgeCodecs.SYNC.write(d, new Retained.Read<>(next), false);
        int deltaBytes = d.readableBytes();
        client = KnowledgeCodecs.SYNC.read(d, client);
        assertEquals(new Retained.Read<>(next), client);
        RegistryFriendlyByteBuf full = buf();
        KnowledgeCodecs.SYNC.write(full, new Retained.Read<>(next), true);
        assertTrue(deltaBytes < full.readableBytes(), "a delta is smaller than a baseline");

        RegistryFriendlyByteBuf u = buf();
        KnowledgeCodecs.SYNC.write(u, new Retained.Unreadable<>(new CompoundTag(), "x"), false);
        assertInstanceOf(Retained.Unreadable.class, KnowledgeCodecs.SYNC.read(u, client));
        RegistryFriendlyByteBuf onto = buf();
        KnowledgeCodecs.SYNC.write(onto, new Retained.Read<>(next), false);
        assertEquals(
                Knowledge.EMPTY.apply(delta),
                ((Retained.Read<Knowledge>) KnowledgeCodecs.SYNC.read(onto, null)).value());
    }

    @Test
    void scanResultsRoundTripAndAreBounded() {
        ResourceKey<Aspect> aer = AspectKeys.AER;
        ScanResult scanned =
                new ScanResult(
                        ScanResult.State.SCANNED,
                        Component.literal("Stone"),
                        List.of(Component.literal("type"), Component.literal("bright")),
                        List.of(
                                new ScanResult.Entry(Optional.of(aer), 3, 0, List.of()),
                                new ScanResult.Entry(
                                        Optional.empty(),
                                        2,
                                        8,
                                        List.of(Optional.of(aer), Optional.empty()))),
                        new TreeMap<>(Map.of(aer, 7)),
                        null,
                        60);
        ScanResult sounding =
                new ScanResult(
                        ScanResult.State.ANSWER,
                        Component.empty(),
                        List.of(),
                        List.of(),
                        new TreeMap<>(),
                        new ScanResult.Bearing(5, Sounding.Vertical.DOWN, Sounding.Band.FAR),
                        120);
        for (ScanResult r : List.of(scanned, sounding)) {
            RegistryFriendlyByteBuf b = buf();
            ScanResult.STREAM_CODEC.encode(b, r);
            assertEquals(r, ScanResult.STREAM_CODEC.decode(b));
            assertFalse(b.isReadable());
        }
        assertTrue(scanned.entries().get(1).unheard());
        assertEquals(ScanResult.TYPE, sounding.type());
        assertThrows(
                IllegalArgumentException.class,
                () -> new ScanResult.Bearing(8, Sounding.Vertical.UP, Sounding.Band.NEAR));
        List<ScanResult.Entry> many =
                java.util.Collections.nCopies(
                        ScanResult.MAX_ENTRIES + 1,
                        new ScanResult.Entry(Optional.of(aer), 1, 0, List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ScanResult(
                                ScanResult.State.SCANNED,
                                Component.empty(),
                                List.of(),
                                many,
                                new TreeMap<>(),
                                null,
                                1));
    }
}
