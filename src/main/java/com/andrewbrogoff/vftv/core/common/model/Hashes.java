package com.andrewbrogoff.vftv.core.common.model;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * The mod's one hash family: the SplitMix64 finaliser folded over 64-bit words, its streams, and
 * seeded phases (conventions, "Hashing and seeded phases").
 *
 * <p>All arithmetic wraps on {@code long}. Callers turn game values into words themselves: a {@code
 * BlockPos} is {@code asLong()}, a {@code ChunkPos} is {@code pack()}, a {@code UUID} is {@link
 * #uuidWords}, an id is {@link #idWords} and a boolean is 0 or 1. Every fold starts with a salt
 * from {@link Salts}.
 */
public final class Hashes {
    /** The golden-ratio increment SplitMix64 adds between draws. */
    public static final long GOLDEN = 0x9E3779B97F4A7C15L;

    private Hashes() {}

    /** The SplitMix64 finaliser. */
    public static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Folds the words in order: {@code h = mix64((h + GOLDEN) ^ w)}, starting from 0. */
    public static long fold(long... words) {
        long h = 0;
        for (long w : words) {
            h = mix64((h + GOLDEN) ^ w);
        }
        return h;
    }

    /**
     * The words of an id: the UTF-8 bytes of {@code "ns:path"} in 8-byte big-endian words, the last
     * zero-padded on the right, then one word holding the byte length.
     */
    public static long[] idWords(String id) {
        byte[] bytes = id.getBytes(StandardCharsets.UTF_8);
        int full = (bytes.length + 7) / 8;
        long[] words = new long[full + 1];
        for (int i = 0; i < bytes.length; i++) {
            words[i / 8] |= (bytes[i] & 0xFFL) << (56 - 8 * (i % 8));
        }
        words[full] = bytes.length;
        return words;
    }

    /** The two words of a UUID: most significant bits, then least. */
    public static long[] uuidWords(UUID uuid) {
        return new long[] {uuid.getMostSignificantBits(), uuid.getLeastSignificantBits()};
    }

    /** Concatenates word arrays, so a fold can mix scalars, UUIDs and ids. */
    public static long[] words(long[]... parts) {
        int length = 0;
        for (long[] part : parts) {
            length += part.length;
        }
        long[] out = new long[length];
        int at = 0;
        for (long[] part : parts) {
            System.arraycopy(part, 0, out, at, part.length);
            at += part.length;
        }
        return out;
    }

    /** The n-th draw of {@code stream(h)}, for n &ge; 1: {@code mix64(h + n * GOLDEN)}. */
    public static long at(long h, long n) {
        return mix64(h + n * GOLDEN);
    }

    /** A draw reduced to {@code [lo, hi]}: {@code lo + (d >>> 1) % (hi - lo + 1)}. */
    public static long bounded(long d, long lo, long hi) {
        if (hi < lo) {
            throw new IllegalArgumentException("hi < lo");
        }
        return lo + (d >>> 1) % (hi - lo + 1);
    }

    /**
     * A draw reduced to an index by weight: with {@code t = (d >>> 1) % sum}, the first {@code i}
     * whose running total exceeds {@code t}.
     */
    public static int weighted(long d, int[] weights) {
        long sum = 0;
        for (int w : weights) {
            if (w < 0) {
                throw new IllegalArgumentException("negative weight");
            }
            sum += w;
        }
        if (sum == 0) {
            throw new IllegalArgumentException("weights sum to 0");
        }
        long t = (d >>> 1) % sum;
        long running = 0;
        int i = 0;
        while (true) {
            running += weights[i];
            if (t < running) {
                return i;
            }
            i++;
        }
    }

    /**
     * The phase of a holder: {@code fold([salt] ++ words)} reduced unsigned by {@code interval}.
     * The holder is due on the ticks where {@code gameTime mod interval == phase}.
     */
    public static long phase(int salt, long[] words, long interval) {
        if (interval < 1) {
            throw new IllegalArgumentException("interval < 1");
        }
        long[] all = new long[words.length + 1];
        all[0] = Salts.word(salt);
        System.arraycopy(words, 0, all, 1, words.length);
        return Long.remainderUnsigned(fold(all), interval);
    }

    /** A sequential stream of draws, {@code next() = mix64(s += GOLDEN)}. Not thread-safe. */
    public static final class Stream {
        private long state;

        /** Starts a stream at the hash {@code h}. */
        public Stream(long h) {
            this.state = h;
        }

        /** The next draw. */
        public long next() {
            state += GOLDEN;
            return mix64(state);
        }
    }
}
