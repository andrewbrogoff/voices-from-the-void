package com.andrewbrogoff.vftv.core.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andrewbrogoff.vftv.api.worldgen.WorldgenKeys;
import com.andrewbrogoff.vftv.core.common.RawRecordCodec;
import com.andrewbrogoff.vftv.core.common.RawRecordCodec.Retained;
import com.andrewbrogoff.vftv.core.worldgen.model.ChoirClaims;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

/** The claims record, the Reliquary's record and the site kind, against raw retention. */
class WorldgenCodecsTest {
    private static final Codec<Retained<ChoirClaims>> CLAIMS =
            RawRecordCodec.wrap(WorldgenCodecs.CLAIMS);
    private static final Codec<Retained<WorldgenCodecs.Reliquary>> RELIQUARY =
            RawRecordCodec.wrap(WorldgenCodecs.Reliquary.CODEC);

    private static CompoundTag site(String dim, long pos, String... pages) {
        CompoundTag s = new CompoundTag();
        s.putString("dim", dim);
        s.putLong("pos", pos);
        ListTag list = new ListTag();
        for (String p : pages) {
            list.add(StringTag.valueOf(p));
        }
        s.put("pages", list);
        return s;
    }

    private static CompoundTag claims(int format, CompoundTag... sites) {
        CompoundTag t = new CompoundTag();
        t.putInt("format", format);
        ListTag list = new ListTag();
        for (CompoundTag s : sites) {
            list.add(s);
        }
        t.put("sites", list);
        return t;
    }

    private static <T> Retained<T> decode(Codec<Retained<T>> codec, Tag tag) {
        return codec.parse(NbtOps.INSTANCE, tag).getOrThrow();
    }

    @Test
    void claimsRoundTripKeepingUnknownPages() {
        CompoundTag tag =
                claims(1, site("minecraft:overworld", -5, "vftv:choir/beneath_the_notes", "pack:gone"));
        Retained<ChoirClaims> read = decode(CLAIMS, tag);
        assertInstanceOf(Retained.Read.class, read);
        ChoirClaims c = ((Retained.Read<ChoirClaims>) read).value();
        assertEquals(List.of("vftv:choir/beneath_the_notes", "pack:gone"), c.at("minecraft:overworld", -5));
        assertEquals(tag, CLAIMS.encodeStart(NbtOps.INSTANCE, read).getOrThrow());
        assertEquals(
                claims(1),
                CLAIMS.encodeStart(NbtOps.INSTANCE, new Retained.Read<>(ChoirClaims.EMPTY)).getOrThrow());
    }

    @Test
    void claimsUnreadableAndKeptRaw() {
        CompoundTag extra = claims(1);
        extra.putString("unknown", "x");
        CompoundTag missing = claims(1);
        missing.remove("format");
        for (CompoundTag bad :
                List.of(
                        claims(2),
                        missing,
                        extra,
                        claims(1, site("Bad Dim", 0, "a:b")),
                        claims(1, site("a:b", 0)),
                        claims(1, site("a:b", 0, "a:1", "a:1")),
                        claims(1, site("a:b", 0, "a:1", "a:2", "a:3", "a:4", "a:5", "a:6", "a:7", "a:8", "a:9")),
                        claims(1, site("a:b", 2, "a:1"), site("a:b", 1, "a:1")))) {
            Retained<ChoirClaims> read = decode(CLAIMS, bad);
            assertInstanceOf(Retained.Unreadable.class, read, bad.toString());
            assertEquals(bad, CLAIMS.encodeStart(NbtOps.INSTANCE, read).getOrThrow(), "kept raw");
        }
    }

    @Test
    void theReliquaryRecordCaseByCase() {
        CompoundTag good = new CompoundTag();
        good.putInt("format", 1);
        good.putString("site", "vftv:choir_ruin");
        Retained<WorldgenCodecs.Reliquary> read = decode(RELIQUARY, good);
        assertEquals(new Retained.Read<>(WorldgenCodecs.Reliquary.BUILT_IN), read);
        assertEquals(good, RELIQUARY.encodeStart(NbtOps.INSTANCE, read).getOrThrow());
        assertEquals(WorldgenKeys.CHOIR_RUIN_SITE, WorldgenCodecs.Reliquary.BUILT_IN.site());

        CompoundTag noFormat = new CompoundTag();
        noFormat.putString("site", "pack:road");
        CompoundTag noSite = new CompoundTag();
        noSite.putInt("format", 1);
        CompoundTag wrongType = good.copy();
        wrongType.putInt("site", 3);
        CompoundTag future = good.copy();
        future.putInt("format", 2);
        CompoundTag tooLong = good.copy();
        tooLong.putString("site", "a:" + "b".repeat(300));
        for (CompoundTag bad : List.of(noFormat, noSite, wrongType, future, tooLong)) {
            Retained<WorldgenCodecs.Reliquary> r = decode(RELIQUARY, bad);
            assertInstanceOf(Retained.Unreadable.class, r, bad.toString());
            assertEquals(bad, RELIQUARY.encodeStart(NbtOps.INSTANCE, r).getOrThrow());
        }
        CompoundTag pack = good.copy();
        pack.putString("site", "pack:road");
        assertInstanceOf(Retained.Read.class, decode(RELIQUARY, pack), "a parseable unknown site is inert");
    }

    @Test
    void theSiteKind() {
        var good =
                WorldgenCodecs.SiteEntry.CODEC.parse(
                        JsonOps.INSTANCE,
                        JsonParser.parseString(
                                "{\"pages\": [\"vftv:choir/beneath_the_notes\"], \"pages_per_claim\": 6}"));
        assertEquals(List.of("vftv:choir/beneath_the_notes"), good.getOrThrow().pageIds());
        for (String bad :
                List.of(
                        "{\"pages\": [], \"pages_per_claim\": 6}",
                        "{\"pages\": [\"a:b\"], \"pages_per_claim\": 9}",
                        "{\"pages\": [\"a:b\"], \"pages_per_claim\": 0}",
                        "{\"pages\": [\"a:b\"], \"pages_per_claim\": 6, \"extra\": 1}",
                        "{\"pages\": [\"a:b\"]}")) {
            assertTrue(
                    WorldgenCodecs.SiteEntry.CODEC
                            .parse(JsonOps.INSTANCE, JsonParser.parseString(bad))
                            .isError(),
                    bad);
        }
    }
}
