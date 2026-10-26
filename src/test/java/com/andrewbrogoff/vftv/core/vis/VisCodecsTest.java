package com.andrewbrogoff.vftv.core.vis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.api.vis.VisQuote;
import com.andrewbrogoff.vftv.core.common.RawRecordCodec;
import com.andrewbrogoff.vftv.core.common.RawRecordCodec.Retained;
import com.andrewbrogoff.vftv.core.vis.model.VisTuning;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

/** The vis feature's persisted records and wire forms: bounds, formats and raw retention. */
class VisCodecsTest {
    private static final NodeState NODE =
            NodeState.born(
                    7,
                    NodeState.Origin.NATURAL,
                    Identifier.parse("vftv:windward"),
                    NodeState.Clarity.STEADY,
                    new TreeMap<>(Map.of("vftv:aer", 30, "vftv:terra", 12)),
                    1000,
                    VisTuning.DEFAULTS);

    private static <T> Tag encode(Codec<T> codec, T value) {
        return codec.encodeStart(NbtOps.INSTANCE, value).getOrThrow();
    }

    private static <T> Retained<T> decode(Codec<Retained<T>> codec, Tag tag) {
        return codec.parse(NbtOps.INSTANCE, tag).getOrThrow();
    }

    /** The raw-retention template: each breakage is unreadable and written back unchanged. */
    private static <T> void retention(Codec<T> bounded, T value, String field, Tag bad) {
        Codec<Retained<T>> codec = RawRecordCodec.wrap(bounded);
        CompoundTag good = (CompoundTag) encode(bounded, value);
        assertEquals(new Retained.Read<>(value), decode(codec, good), "a good record reads");
        for (CompoundTag broken :
                List.of(
                        with(good, "format", net.minecraft.nbt.IntTag.valueOf(2)),
                        without(good, "format"),
                        with(good, field, bad),
                        with(good, "from_the_future", net.minecraft.nbt.StringTag.valueOf("x")))) {
            Retained<T> read = decode(codec, broken);
            assertInstanceOf(Retained.Unreadable.class, read, broken.toString());
            assertEquals(broken, codec.encodeStart(NbtOps.INSTANCE, read).getOrThrow(), "kept raw");
        }
    }

    private static CompoundTag with(CompoundTag tag, String key, Tag value) {
        CompoundTag copy = tag.copy();
        copy.put(key, value);
        return copy;
    }

    private static CompoundTag without(CompoundTag tag, String key) {
        CompoundTag copy = tag.copy();
        copy.remove(key);
        return copy;
    }

    @Test
    void nodeStateRoundTripAndRetention() {
        retention(NodeState.BOUNDED, NODE, "clarity", net.minecraft.nbt.StringTag.valueOf("dim"));
        CompoundTag bad = (CompoundTag) encode(NodeState.BOUNDED, NODE);
        bad.getListOrEmpty("aspects").getCompoundOrEmpty(0).putInt("current", 31);
        assertInstanceOf(
                Retained.Unreadable.class,
                decode(NodeBlockEntity.CODEC, bad),
                "current above max is unreadable");
        CompoundTag reversed = (CompoundTag) encode(NodeState.BOUNDED, NODE);
        var aspects = reversed.getListOrEmpty("aspects");
        Tag first = aspects.get(0);
        aspects.set(0, aspects.get(1));
        aspects.set(1, first);
        assertInstanceOf(
                Retained.Unreadable.class, decode(NodeBlockEntity.CODEC, reversed), "order");
    }

    @Test
    void settlementRebasesAfterATuningChange() {
        NodeState drained =
                NODE.withAspects(
                        List.of(
                                new NodeState.NodeAspect(Identifier.parse("vftv:aer"), 30, 0),
                                new NodeState.NodeAspect(Identifier.parse("vftv:terra"), 12, 0)));
        // 80-tick steady interval, divisor 20: aer step 1, terra step 1; 160 ticks → 2 steps.
        NodeState settled = drained.settle(1160, VisTuning.DEFAULTS);
        assertEquals(2, settled.aspects().get(0).current());
        assertEquals(1160, settled.regen().tick());
        VisTuning faster = VisTuning.parse("{\"regen.interval.steady\": 10}");
        NodeState rebased = drained.settle(1160, faster);
        assertEquals(
                2, rebased.aspects().get(0).current(), "the past is priced at the old interval");
        assertEquals(10, rebased.regen().interval());
        assertEquals(1160, rebased.regen().tick(), "the clock restarts at the rebase");
    }

    @Test
    void visPoolsRetention() {
        VisPools pools = new VisPools(new TreeMap<>(Map.of("vftv:aer", 12, "mod:gone", 3)));
        retention(VisPools.BOUNDED, pools, "pools", net.minecraft.nbt.StringTag.valueOf("x"));
        CompoundTag over = (CompoundTag) encode(VisPools.BOUNDED, pools);
        over.getCompoundOrEmpty("pools").putInt("vftv:aer", 1001);
        assertInstanceOf(Retained.Unreadable.class, decode(VisContent.VIS_CODEC, over));
        CompoundTag many = (CompoundTag) encode(VisPools.BOUNDED, VisPools.EMPTY);
        for (int i = 0; i < 17; i++) {
            many.getCompoundOrEmpty("pools").putInt("mod:a" + i, 1);
        }
        assertInstanceOf(
                Retained.Unreadable.class, decode(VisContent.VIS_CODEC, many), "17 entries");
    }

    @Test
    void ledgerRetentionAndCounterRepair() {
        NodeLedger ledger = new NodeLedger();
        long id = ledger.mint();
        ledger.put(
                NodeLedger.Entry.placed(
                        id, NodeState.Origin.STARTER, Level.OVERWORLD, new BlockPos(1, 2, 3)));
        ledger.starter(new NodeLedger.Starter(id, BlockPos.ZERO));
        long carried = ledger.mint();
        ledger.put(
                NodeLedger.Entry.placed(
                                carried, NodeState.Origin.NATURAL, Level.OVERWORLD, BlockPos.ZERO)
                        .carried(ledger.token()));
        CompoundTag tag = (CompoundTag) encode(NodeLedger.BOUNDED, ledger);
        tag.putLong("next_id", 1);
        tag.putLong("next_token", 1);
        if (!(decode(RawRecordCodec.wrap(NodeLedger.BOUNDED), tag)
                instanceof Retained.Read<NodeLedger>(NodeLedger read))) {
            throw new AssertionError("the ledger reads");
        }
        assertEquals(3, read.nextId(), "repaired past the highest id");
        assertEquals(2, read.nextToken(), "repaired past the highest token");
        assertTrue(read.starterPlaced());
        assertFalse(read.starterMissing());
        read.put(java.util.Objects.requireNonNull(read.get(id)).removed());
        assertTrue(read.starterMissing());
        CompoundTag placedWithoutPos = (CompoundTag) encode(NodeLedger.BOUNDED, new NodeLedger());
        CompoundTag entry = new CompoundTag();
        entry.putLong("id", 5);
        entry.putString("origin", "natural");
        entry.putString("state", "placed");
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        list.add(entry);
        placedWithoutPos.put("nodes", list);
        assertInstanceOf(
                Retained.Unreadable.class,
                decode(RawRecordCodec.wrap(NodeLedger.BOUNDED), placedWithoutPos));
    }

    @Test
    void registryEntriesRefuseBadData() {
        for (String bad :
                List.of(
                        "{\"item\": \"minecraft:stick\", \"capacity\": 0}",
                        "{\"item\": \"minecraft:stick\", \"capacity\": 40, \"extra\": 1}",
                        "{\"capacity\": 40}")) {
            assertTrue(
                    WandRodEntry.CODEC
                            .parse(JsonOps.INSTANCE, JsonParser.parseString(bad))
                            .isError(),
                    bad);
        }
        assertTrue(
                WandCapEntry.CODEC
                        .parse(
                                JsonOps.INSTANCE,
                                JsonParser.parseString(
                                        "{\"item\": \"a:b\", \"cost_multiplier\": 0.851}"))
                        .isError());
        assertTrue(
                WandCapEntry.CODEC
                        .parse(
                                JsonOps.INSTANCE,
                                JsonParser.parseString(
                                        "{\"item\": \"a:b\", \"cost_multiplier\": 2.5}"))
                        .isError());
        assertEquals(
                0.85,
                WandCapEntry.CODEC
                        .parse(
                                JsonOps.INSTANCE,
                                JsonParser.parseString(
                                        "{\"item\": \"a:b\", \"cost_multiplier\": 0.85}"))
                        .getOrThrow()
                        .costMultiplier());
        String good =
                "{\"aspects\": [{\"aspect\": \"vftv:aer\", \"range\": [1, 2]}], \"main_aspect\": \"vftv:aer\","
                        + " \"clarity\": {\"faint\": 1, \"steady\": 0, \"clear\": 0}";
        assertTrue(
                NodeTypeEntry.CODEC
                        .parse(JsonOps.INSTANCE, JsonParser.parseString(good + "}"))
                        .isSuccess());
        for (String tail :
                List.of(
                        ", \"color\": 1}",
                        ", \"natural\": true}",
                        ", \"weight\": 5}",
                        ", \"node_reward_divisor\": 65}")) {
            assertTrue(
                    NodeTypeEntry.CODEC
                            .parse(JsonOps.INSTANCE, JsonParser.parseString(good + tail))
                            .isError(),
                    tail);
        }
        assertTrue(
                NodeTypeEntry.CODEC
                        .parse(
                                JsonOps.INSTANCE,
                                JsonParser.parseString(
                                        good.replace(
                                                        "\"main_aspect\": \"vftv:aer\"",
                                                        "\"main_aspect\": \"vftv:ordo\"")
                                                + "}"))
                        .isError());
    }

    @Test
    void rulesWireForm() {
        VisRules rules = new VisRules(VisTuning.parse("{\"tap.amount\": 5}"), true);
        ByteBuf buf = Unpooled.buffer();
        VisRules.STREAM_CODEC.encode(buf, rules);
        assertEquals(rules, VisRules.STREAM_CODEC.decode(buf));
        var json = rules.toJson();
        json.remove("starter");
        assertThrows(IllegalArgumentException.class, () -> VisRules.fromJson(json));
        ByteBuf junk = Unpooled.buffer();
        net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8.encode(junk, "{\"format\": 2}");
        assertThrows(
                io.netty.handler.codec.DecoderException.class,
                () -> VisRules.STREAM_CODEC.decode(junk));
    }

    @Test
    void echoEqualityComparesTheCostFieldsOnly() {
        var aer = AspectKeys.AER;
        VisQuote a =
                new VisQuote(
                        new TreeMap<>(Map.of(aer, 10)),
                        new TreeMap<>(Map.of(aer, 9)),
                        850,
                        0,
                        850,
                        0,
                        1000,
                        false,
                        false,
                        false,
                        false,
                        true,
                        List.of());
        VisQuote factors =
                new VisQuote(
                        new TreeMap<>(Map.of(aer, 10)),
                        new TreeMap<>(Map.of(aer, 9)),
                        850,
                        0,
                        1000,
                        150,
                        1000,
                        false,
                        false,
                        false,
                        false,
                        false,
                        List.of(aer));
        assertTrue(
                VisService.sameCost(a, factors), "the factors and affordability are not compared");
        for (VisQuote other :
                List.of(
                        new VisQuote(
                                new TreeMap<>(Map.of(aer, 11)),
                                new TreeMap<>(Map.of(aer, 9)),
                                850,
                                0,
                                850,
                                0,
                                1000,
                                false,
                                false,
                                false,
                                false,
                                true,
                                List.of()),
                        new VisQuote(
                                new TreeMap<>(Map.of(aer, 10)),
                                new TreeMap<>(Map.of(aer, 8)),
                                850,
                                0,
                                850,
                                0,
                                1000,
                                false,
                                false,
                                false,
                                false,
                                true,
                                List.of()),
                        new VisQuote(
                                new TreeMap<>(Map.of(aer, 10)),
                                new TreeMap<>(Map.of(aer, 9)),
                                851,
                                0,
                                850,
                                0,
                                1000,
                                false,
                                false,
                                false,
                                false,
                                true,
                                List.of()),
                        new VisQuote(
                                new TreeMap<>(Map.of(aer, 10)),
                                new TreeMap<>(Map.of(aer, 9)),
                                850,
                                1,
                                850,
                                0,
                                1000,
                                false,
                                false,
                                false,
                                false,
                                true,
                                List.of()),
                        new VisQuote(
                                new TreeMap<>(Map.of(aer, 10)),
                                new TreeMap<>(Map.of(aer, 9)),
                                850,
                                0,
                                850,
                                0,
                                1000,
                                true,
                                false,
                                false,
                                false,
                                true,
                                List.of()),
                        new VisQuote(
                                new TreeMap<>(Map.of(aer, 10)),
                                new TreeMap<>(Map.of(aer, 9)),
                                850,
                                0,
                                850,
                                0,
                                1000,
                                false,
                                true,
                                false,
                                false,
                                true,
                                List.of()),
                        new VisQuote(
                                new TreeMap<>(Map.of(aer, 10)),
                                new TreeMap<>(Map.of(aer, 9)),
                                850,
                                0,
                                850,
                                0,
                                1000,
                                false,
                                false,
                                true,
                                false,
                                true,
                                List.of()))) {
            assertFalse(VisService.sameCost(a, other), other.toString());
        }
    }

    // --- T08b: the jar, the relays and cultivation ------------------------------------------

    @Test
    void capturedNodeRetention() {
        CapturedNode jar = new CapturedNode(5, NODE);
        retention(CapturedNode.BOUNDED, jar, "token", net.minecraft.nbt.StringTag.valueOf("x"));
        CompoundTag zero = (CompoundTag) encode(CapturedNode.BOUNDED, jar);
        zero.putLong("token", 0);
        assertInstanceOf(
                Retained.Unreadable.class, decode(VisContent.CAPTURED_CODEC, zero), "token 0");
        CompoundTag badNode = (CompoundTag) encode(CapturedNode.BOUNDED, jar);
        badNode.getCompoundOrEmpty("node").putInt("format", 2);
        assertInstanceOf(
                Retained.Unreadable.class,
                decode(VisContent.CAPTURED_CODEC, badNode),
                "an unreadable node state makes the jar unreadable");
    }

    @Test
    void relayLedgerRetention() {
        RelayLedger ledger =
                new RelayLedger(
                        List.of(
                                new RelayLedger.Member(
                                        new BlockPos(1, 64, -3),
                                        com.andrewbrogoff.vftv.core.vis.model.RelayNetwork.Kind
                                                .TAP),
                                new RelayLedger.Member(
                                        new BlockPos(9, 64, -3),
                                        com.andrewbrogoff.vftv.core.vis.model.RelayNetwork.Kind
                                                .CRADLE)));
        retention(RelayLedger.BOUNDED, ledger, "members", net.minecraft.nbt.StringTag.valueOf("x"));
        CompoundTag badKind = (CompoundTag) encode(RelayLedger.BOUNDED, ledger);
        badKind.getListOrEmpty("members").getCompoundOrEmpty(0).putString("kind", "lantern");
        assertInstanceOf(
                Retained.Unreadable.class,
                decode(RawRecordCodec.wrap(RelayLedger.BOUNDED), badKind),
                "an unknown kind");
        assertEquals(
                "tap",
                ((CompoundTag) encode(RelayLedger.BOUNDED, ledger))
                        .getListOrEmpty("members")
                        .getCompoundOrEmpty(0)
                        .getStringOr("kind", ""));
    }

    private static final Identifier AER = Identifier.parse("vftv:aer");
    private static final Identifier SONUS = Identifier.parse("vftv:sonus");
    private static final Identifier ORDO = Identifier.parse("vftv:ordo");

    @Test
    void cultivationRoomAndAccept() {
        VisTuning t = VisTuning.DEFAULTS; // per_max 4, clarity_cost 48, max_capacity 250
        NodeState s = NODE.settle(1000, t);
        assertEquals((250 - 30) * 4, NodeTaps.room(s, AER, true, t));
        assertEquals(250 * 4, NodeTaps.room(s, ORDO, true, t), "a missing vis aspect from max 0");
        assertEquals(48, NodeTaps.room(s, SONUS, false, t), "steady: one step to clear");
        assertEquals(0, NodeTaps.room(s, Identifier.parse("vftv:metallum"), false, t));
        NodeState more = NodeTaps.accept(s, AER, 9, 1000, t);
        assertEquals(32, java.util.Objects.requireNonNull(more.byAspect().get("vftv:aer")).max());
        assertEquals(
                30,
                java.util.Objects.requireNonNull(more.byAspect().get("vftv:aer")).current(),
                "current unchanged");
        assertEquals(1, more.cultivation().counters().get(AER));
        NodeState added = NodeTaps.accept(s, ORDO, 1, 1000, t);
        assertEquals(3, added.aspects().size(), "the new aspect reserves its slot");
        assertEquals(0, java.util.Objects.requireNonNull(added.byAspect().get("vftv:ordo")).max());
        assertEquals(
                List.of("vftv:aer", "vftv:ordo", "vftv:terra"),
                added.aspects().stream().map(a -> a.aspect().toString()).toList(),
                "ascending id");
        NodeState clear = NodeTaps.accept(s, SONUS, 48, 1000, t);
        assertEquals(NodeState.Clarity.CLEAR, clear.clarity());
        assertEquals(0, clear.cultivation().sonus());
        assertEquals(40, clear.regen().interval(), "a clarity step rebases to clear's interval");
        assertEquals(0, NodeTaps.room(clear, SONUS, false, t));
    }

    @Test
    void settlementCarriesCountersAfterLoweredThresholds() {
        NodeState s =
                new NodeState(
                        NODE.id(),
                        NODE.origin(),
                        NODE.type(),
                        NODE.clarity(),
                        NODE.aspects(),
                        NODE.regen(),
                        new NodeState.Cultivation(new TreeMap<>(Map.of(AER, 3)), 30));
        VisTuning lowered =
                VisTuning.parse("{\"cultivate.per_max\": 2, \"cultivate.clarity_cost\": 20}");
        NodeState carried = s.settle(1000, lowered);
        assertEquals(
                31, java.util.Objects.requireNonNull(carried.byAspect().get("vftv:aer")).max());
        assertEquals(1, carried.cultivation().counters().get(AER));
        assertEquals(NodeState.Clarity.CLEAR, carried.clarity(), "steady + 30 Sonus at cost 20");
        assertEquals(0, carried.cultivation().sonus());
        assertEquals(s.settle(1000, VisTuning.DEFAULTS).cultivation(), s.cultivation());
    }
}
