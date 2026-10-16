package com.andrewbrogoff.vftv.core.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.andrewbrogoff.vftv.core.common.RawRecordCodec.Retained;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

/** The raw-retention template (conventions, "Tests") at the codec level. */
class RawRecordCodecTest {
    record Jar(int format, int amount) {
        Jar {
            if (amount > 64) {
                throw new IllegalArgumentException("amount over 64");
            }
        }

        static final Codec<Jar> BOUNDED =
                RecordCodecBuilder.<Jar>create(
                                i ->
                                        i.group(
                                                        Codec.INT
                                                                .fieldOf("format")
                                                                .forGetter(Jar::format),
                                                        Codec.intRange(0, 1000)
                                                                .fieldOf("amount")
                                                                .forGetter(Jar::amount))
                                                .apply(i, Jar::new))
                        .validate(
                                j ->
                                        j.format() == 1
                                                ? DataResult.success(j)
                                                : DataResult.error(
                                                        () -> "unknown format " + j.format()));
    }

    private static final Codec<Retained<Jar>> CODEC = RawRecordCodec.wrap(Jar.BOUNDED);

    private static CompoundTag tag(int format, int amount) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("format", format);
        tag.putInt("amount", amount);
        return tag;
    }

    private static Retained<Jar> decode(Tag tag) {
        return CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
    }

    private static Tag encode(Retained<Jar> value) {
        return CODEC.encodeStart(NbtOps.INSTANCE, value).getOrThrow();
    }

    @Test
    void aReadableRecordRoundTrips() {
        Retained<Jar> read = decode(tag(1, 5));
        assertEquals(new Retained.Read<>(new Jar(1, 5)), read);
        assertEquals(tag(1, 5), encode(read));
    }

    @Test
    void unreadableRecordsAreKeptAndWrittenBackByteIdentical() {
        CompoundTag missingFormat = new CompoundTag();
        missingFormat.putInt("amount", 3);
        CompoundTag wrongType = tag(1, 0);
        wrongType.putString("amount", "lots");
        for (Tag raw : new Tag[] {tag(2, 5), tag(99, 5), missingFormat, wrongType, tag(1, 1001)}) {
            Retained<Jar> decoded = decode(raw);
            assertTrue(!unreadable(decoded).reason().isEmpty());
            assertEquals(raw, encode(decoded), "written back unchanged");
        }
    }

    @Test
    void aFailedInvariantInTheConstructorIsUnreadable() {
        Retained<Jar> decoded = decode(tag(1, 65));
        String reason = unreadable(decoded).reason();
        assertTrue(reason.contains("amount over 64"), reason);
        assertEquals(tag(1, 65), encode(decoded));
    }

    private static Retained.Unreadable<Jar> unreadable(Retained<Jar> decoded) {
        return switch (decoded) {
            case Retained.Unreadable<Jar> u -> u;
            case Retained.Read<Jar> r -> fail("expected unreadable, got " + r);
        };
    }
}
