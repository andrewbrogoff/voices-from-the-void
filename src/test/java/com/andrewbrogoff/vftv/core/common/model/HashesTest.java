package com.andrewbrogoff.vftv.core.common.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

/** Replays the conventions' "Vectors" table and pins each hash building block. */
class HashesTest {
    private static final UUID PLAYER = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");

    @Test
    void hushChunkVector() {
        long h = Hashes.fold(Salts.word(Salts.HUSH), ChunkPos.pack(0, 0));
        assertEquals(0xC21D48E4BBC2BCC8L, h);
        assertEquals(2, Hashes.phase(Salts.HUSH, new long[] {ChunkPos.pack(0, 0)}, 10));
    }

    @Test
    void crucibleVectorsIncludingNegativeCoordinates() {
        long origin = BlockPos.asLong(0, 64, 0);
        assertEquals(0xE0F53741245D77A5L, Hashes.fold(Salts.word(Salts.CRUC), origin));
        assertEquals(2, Hashes.phase(Salts.CRUC, new long[] {origin}, 5));
        assertEquals(37, Hashes.phase(Salts.CRUC, new long[] {origin}, 40));

        long negative = BlockPos.asLong(-37, 70, 512);
        assertEquals(0x28DE383757949BE2L, Hashes.fold(Salts.word(Salts.CRUC), negative));
        assertEquals(4, Hashes.phase(Salts.CRUC, new long[] {negative}, 5));
        assertEquals(34, Hashes.phase(Salts.CRUC, new long[] {negative}, 40));
    }

    @Test
    void warpPhaseVectors() {
        long[] uuid = Hashes.uuidWords(PLAYER);
        assertEquals(805, Hashes.phase(Salts.WARP, uuid, 1200));
        assertEquals(5, Hashes.phase(Salts.WARP, uuid, 400));
        assertEquals(5, Hashes.phase(Salts.WARP, uuid, 20));
    }

    @Test
    void noteSeedVector() {
        long seed =
                Hashes.fold(
                        Hashes.words(
                                new long[] {Salts.word(Salts.NOTE), 0x0123456789ABCDEFL},
                                Hashes.uuidWords(PLAYER),
                                new long[] {0},
                                Hashes.idWords("vftv:taking_notes")));
        assertEquals(0xF206817A7B0403F2L, seed);
    }

    @Test
    void idWordsPadAndAppendLength() {
        assertArrayEquals(new long[] {0}, Hashes.idWords(""));
        assertArrayEquals(new long[] {0x6100000000000000L, 1}, Hashes.idWords("a"));
        assertArrayEquals(new long[] {0x6162636465666768L, 8}, Hashes.idWords("abcdefgh"));
        assertArrayEquals(
                new long[] {0x6162636465666768L, 0x6900000000000000L, 9},
                Hashes.idWords("abcdefghi"));
        // UTF-8, not UTF-16: one code point, two bytes.
        assertArrayEquals(new long[] {0xC3A9000000000000L, 2}, Hashes.idWords("é"));
    }

    @Test
    void streamAndAtAgree() {
        long h = 0x1234L;
        Hashes.Stream stream = new Hashes.Stream(h);
        for (int n = 1; n <= 5; n++) {
            assertEquals(Hashes.at(h, n), stream.next());
        }
    }

    @Test
    void boundedStaysInRange() {
        for (long d : new long[] {0, 1, -1, Long.MIN_VALUE, Long.MAX_VALUE, 0x9E3779B97F4A7C15L}) {
            long v = Hashes.bounded(d, -3, 7);
            assertTrue(v >= -3 && v <= 7, () -> "out of range: " + v);
        }
        assertEquals(5, Hashes.bounded(-1, 5, 5));
        assertThrows(IllegalArgumentException.class, () -> Hashes.bounded(0, 2, 1));
    }

    @Test
    void weightedPicksByRunningTotal() {
        int[] weights = {1, 0, 3};
        // t = (d >>> 1) % 4: 0 picks index 0; 1, 2 and 3 pick index 2; index 1 has no weight.
        assertEquals(0, Hashes.weighted(0, weights));
        assertEquals(2, Hashes.weighted(2, weights));
        assertEquals(2, Hashes.weighted(6, weights));
        assertThrows(IllegalArgumentException.class, () -> Hashes.weighted(0, new int[] {0, 0}));
        assertThrows(IllegalArgumentException.class, () -> Hashes.weighted(0, new int[] {2, -1}));
    }

    @Test
    void phaseNeedsAPositiveInterval() {
        assertThrows(
                IllegalArgumentException.class, () -> Hashes.phase(Salts.WARP, new long[0], 0));
        assertEquals(0, Hashes.phase(Salts.WARP, new long[0], 1));
    }

    @Test
    void mix64OfZeroIsZero() {
        assertEquals(0, Hashes.mix64(0));
        assertEquals(Hashes.mix64(Hashes.GOLDEN), Hashes.fold(0));
    }

    @Test
    void saltsAreTheirAsciiBytes() {
        assertEquals(0x57415250L, Salts.word(Salts.WARP));
        assertEquals('W', Salts.WARP >>> 24);
    }
}
