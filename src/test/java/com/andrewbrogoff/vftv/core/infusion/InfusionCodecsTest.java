package com.andrewbrogoff.vftv.core.infusion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andrewbrogoff.vftv.api.TestItems;
import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.api.infusion.InfusionCost;
import com.andrewbrogoff.vftv.api.infusion.InfusionPlan;
import com.andrewbrogoff.vftv.api.research.ResearchKeys;
import com.andrewbrogoff.vftv.api.vis.VisKind;
import com.andrewbrogoff.vftv.core.common.RawRecordCodec.Retained;
import com.andrewbrogoff.vftv.core.infusion.model.InfusionMath;
import com.andrewbrogoff.vftv.core.infusion.model.InfusionTuning;
import com.andrewbrogoff.vftv.core.infusion.model.RunRules;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The infusion records' and payloads' codecs: round trips, bounds and invariants. */
class InfusionCodecsTest {
    private static final RunRules RULES = InfusionTuning.DEFAULTS.runRules();

    @BeforeAll
    static void bind() {
        TestItems.bindComponents();
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(
                Unpooled.buffer(),
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY),
                ConnectionType.NEOFORGE);
    }

    private static InfusionRun run(int[] drawn, int s, int upsets, int[][] offsets) {
        List<InfusionRun.Part> parts = new ArrayList<>();
        for (int[] o : offsets) {
            parts.add(new InfusionRun.Part(o[0], o[1], new ItemStack(Items.FLINT)));
        }
        return new InfusionRun(
                1,
                42L,
                new UUID(1, 2),
                Identifier.fromNamespaceAndPath("vftv", "test"),
                ResourceKey.create(
                        ResearchKeys.REGISTRY, Identifier.fromNamespaceAndPath("vftv", "infusion")),
                VisKind.ORDINARY,
                new ItemStack(Items.BLAZE_ROD),
                parts,
                new boolean[parts.size()],
                List.of(AspectKeys.AER, AspectKeys.IGNIS),
                new int[] {4, 6},
                drawn,
                new InfusionRun.Stability(
                        20, 0, 0, 2, 6, 0, s, InfusionMath.chance(s, RULES.maxChance())),
                RULES,
                null,
                3,
                upsets,
                null);
    }

    @Test
    void runRoundTripsAndChecksInvariants() {
        InfusionRun good = run(new int[] {2, 0}, 86, 1, new int[][] {{2, 0}, {-2, 0}});
        JsonElement json = InfusionRun.CODEC.encodeStart(JsonOps.INSTANCE, good).getOrThrow();
        assertTrue(
                !json.getAsJsonObject().getAsJsonObject("drawn").has("vftv:ignis"),
                "zero amounts are never written");
        InfusionRun back = InfusionRun.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(json, InfusionRun.CODEC.encodeStart(JsonOps.INSTANCE, back).getOrThrow());
        assertEquals(2, back.drawn(0));
        assertEquals(10 + 2 + 6 * 50, back.ceiling());
        for (InfusionRun bad :
                List.of(
                        run(new int[] {5, 0}, 86, 0, new int[][] {{2, 0}}),
                        run(new int[] {0, 0}, 85, 0, new int[][] {{2, 0}}),
                        run(new int[] {0, 0}, 86, 7, new int[][] {{2, 0}}),
                        run(new int[] {0, 0}, 86, 0, new int[][] {{0, 0}}),
                        run(new int[] {0, 0}, 86, 0, new int[][] {{2, 0}, {2, 0}}))) {
            JsonElement j = InfusionRun.CODEC.encodeStart(JsonOps.INSTANCE, bad).getOrThrow();
            assertTrue(InfusionRun.CODEC.parse(JsonOps.INSTANCE, j).isError(), j.toString());
        }
        JsonObject far = json.deepCopy().getAsJsonObject();
        far.getAsJsonArray("components")
                .get(0)
                .getAsJsonObject()
                .getAsJsonArray("at")
                .set(0, new com.google.gson.JsonPrimitive(Integer.MIN_VALUE));
        assertTrue(
                InfusionRun.CODEC.parse(JsonOps.INSTANCE, far).isError(), "an overflowing offset");
        JsonObject stray = json.deepCopy().getAsJsonObject();
        stray.getAsJsonObject("drawn").addProperty("vftv:terra", 1);
        assertTrue(
                InfusionRun.CODEC.parse(JsonOps.INSTANCE, stray).isError(), "drawn outside costs");
        JsonObject rules = json.deepCopy().getAsJsonObject();
        rules.getAsJsonObject("rules").addProperty("max", 64);
        rules.getAsJsonObject("rules").addProperty("spill", 1000);
        assertTrue(
                InfusionRun.CODEC.parse(JsonOps.INSTANCE, rules).isError(),
                "max x spill over 10000");
    }

    @Test
    void bowlRecordKeepsUnknownFormatsAndChecksSeq() {
        InfusionRun r = run(new int[] {0, 0}, 86, 0, new int[][] {{2, 0}});
        var saved =
                new BowlBlockEntity.Saved(
                        7L, 1, Optional.of(new ItemStack(Items.STICK)), Optional.of(r));
        JsonElement json =
                BowlBlockEntity.RETAINED
                        .encodeStart(JsonOps.INSTANCE, new Retained.Read<>(saved))
                        .getOrThrow();
        assertInstanceOf(
                Retained.Read.class,
                BowlBlockEntity.RETAINED.parse(JsonOps.INSTANCE, json).getOrThrow());
        for (String field : List.of("format:2", "runs:0", "id:0")) {
            JsonObject bad = json.deepCopy().getAsJsonObject();
            String[] kv = field.split(":", -1);
            bad.addProperty(kv[0], Integer.parseInt(kv[1]));
            assertInstanceOf(
                    Retained.Unreadable.class,
                    BowlBlockEntity.RETAINED.parse(JsonOps.INSTANCE, bad).getOrThrow(),
                    field);
        }
        JsonObject two = json.deepCopy().getAsJsonObject();
        two.add(
                "item",
                ItemStack.CODEC
                        .encodeStart(JsonOps.INSTANCE, new ItemStack(Items.STICK, 2))
                        .getOrThrow());
        assertInstanceOf(
                Retained.Unreadable.class,
                BowlBlockEntity.RETAINED.parse(JsonOps.INSTANCE, two).getOrThrow(),
                "one item");
        var claim =
                new PedestalBlockEntity.Saved(
                        Optional.empty(), Optional.of(new PedestalBlockEntity.Claim(1L, 9L, 1)));
        JsonObject pj =
                PedestalBlockEntity.RETAINED
                        .encodeStart(JsonOps.INSTANCE, new Retained.Read<>(claim))
                        .getOrThrow()
                        .getAsJsonObject();
        assertInstanceOf(
                Retained.Read.class,
                PedestalBlockEntity.RETAINED.parse(JsonOps.INSTANCE, pj).getOrThrow());
        pj.getAsJsonObject("claim").addProperty("id", 0);
        assertInstanceOf(
                Retained.Unreadable.class,
                PedestalBlockEntity.RETAINED.parse(JsonOps.INSTANCE, pj).getOrThrow(),
                "claim id 0");
    }

    @Test
    void rulesRoundTripWithAGeneration() {
        RegistryFriendlyByteBuf buf = buffer();
        InfusionRules.STREAM_CODEC.encode(buf, InfusionRules.DEFAULTS);
        assertEquals(InfusionRules.DEFAULTS, InfusionRules.STREAM_CODEC.decode(buf));
        InfusionRules other = new InfusionRules(1, InfusionTuning.parse("{\"run.rate\": 5}"));
        assertNotEquals(InfusionRules.DEFAULTS.generation(), other.generation());
        assertEquals(
                InfusionRules.DEFAULTS.generation(),
                new InfusionRules(1, InfusionTuning.parse("{}")).generation());
        assertEquals(4, InfusionRules.DEFAULTS.value("limits.box_radius"));
        assertEquals(10, InfusionRules.DEFAULTS.value("run.interval"));
        assertEquals(null, InfusionRules.DEFAULTS.value("nope"));
        RegistryFriendlyByteBuf bad = buffer();
        net.minecraft.network.VarInt.write(bad, 2);
        InfusionTuning.DEFAULTS.values().forEach(v -> net.minecraft.network.VarInt.write(bad, v));
        assertThrows(DecoderException.class, () -> InfusionRules.STREAM_CODEC.decode(bad));
        assertThrows(
                IllegalArgumentException.class,
                () -> new InfusionRules(2, InfusionTuning.DEFAULTS));
    }

    @Test
    void seenRoundTrips() {
        RegistryFriendlyByteBuf buf = buffer();
        var seen = new InfusionPayloads.Seen(new BlockPos(-37, 70, 512), -5);
        InfusionPayloads.Seen.STREAM_CODEC.encode(buf, seen);
        assertEquals(seen, InfusionPayloads.Seen.STREAM_CODEC.decode(buf));
    }

    private static InfusionReadout ready(int cost, int held) {
        List<InfusionCost> costs = List.of(new InfusionCost(AspectKeys.AER, cost, held));
        InfusionPlan plan =
                new InfusionPlan(
                        ResourceKey.create(
                                Registries.RECIPE, Identifier.fromNamespaceAndPath("vftv", "test")),
                        VisKind.ORDINARY,
                        new ItemStack(Items.BLAZE_ROD),
                        20,
                        0,
                        0,
                        0,
                        2,
                        6,
                        8,
                        86,
                        21,
                        19,
                        49,
                        349,
                        353,
                        668,
                        6,
                        50,
                        300L,
                        costs,
                        false,
                        false);
        return new InfusionReadout(
                InfusionStatus.READY,
                null,
                List.of(Component.literal("x")),
                Level.OVERWORLD,
                BlockPos.ZERO,
                plan,
                null,
                false);
    }

    @Test
    void readoutRoundTripsWithinBounds() {
        RegistryFriendlyByteBuf buf = buffer();
        var payload =
                new InfusionPayloads.Readout(
                        BlockPos.ZERO,
                        3,
                        InfusionPayloads.Kind.ARMED,
                        true,
                        Optional.of(ready(8000, 99_999)));
        InfusionPayloads.Readout.STREAM_CODEC.encode(buf, payload);
        var back = InfusionPayloads.Readout.STREAM_CODEC.decode(buf);
        assertEquals(3, back.revision());
        assertTrue(back.solicited());
        InfusionPlan plan = java.util.Objects.requireNonNull(back.readout().orElseThrow().ready());
        assertEquals(353, plan.cleanFloor());
        assertEquals(99_999, plan.costs().getFirst().heldNow());
        InfusionReadout.Run run =
                new InfusionReadout.Run(
                        ResourceKey.create(
                                Registries.RECIPE, Identifier.fromNamespaceAndPath("vftv", "test")),
                        new ItemStack(Items.BLAZE_ROD),
                        20,
                        86,
                        21,
                        2,
                        8,
                        List.of(new InfusionCost(AspectKeys.AER, 10, 0)),
                        List.of(new InfusionCost(AspectKeys.AER, 8, 0)),
                        0,
                        1,
                        0,
                        6,
                        InfusionReadout.Stall.COMPONENT,
                        new BlockPos(1, 2, 3),
                        300L,
                        1,
                        true);
        RegistryFriendlyByteBuf rb = buffer();
        var cleared =
                new InfusionPayloads.Readout(
                        BlockPos.ZERO,
                        4,
                        InfusionPayloads.Kind.RUN,
                        false,
                        Optional.of(
                                new InfusionReadout(
                                        InfusionStatus.RINGING,
                                        null,
                                        List.of(),
                                        Level.OVERWORLD,
                                        BlockPos.ZERO,
                                        null,
                                        run,
                                        false)));
        InfusionPayloads.Readout.STREAM_CODEC.encode(rb, cleared);
        InfusionReadout.Run rr =
                java.util.Objects.requireNonNull(
                                InfusionPayloads.Readout.STREAM_CODEC.decode(rb).value())
                        .run();
        assertEquals(new BlockPos(1, 2, 3), java.util.Objects.requireNonNull(rr).missing());
        assertEquals(8, rr.cells().getFirst().cost());
        assertTrue(rr.abandonArmed());
        assertEquals(20, rr.instability());
        RegistryFriendlyByteBuf over = buffer();
        InfusionPayloads.Readout.STREAM_CODEC.encode(
                over,
                new InfusionPayloads.Readout(
                        BlockPos.ZERO,
                        3,
                        InfusionPayloads.Kind.ARMED,
                        true,
                        Optional.of(ready(8001, 0))));
        assertThrows(
                DecoderException.class,
                () -> InfusionPayloads.Readout.STREAM_CODEC.decode(over),
                "a cost over 8000");
        RegistryFriendlyByteBuf held = buffer();
        InfusionPayloads.Readout.STREAM_CODEC.encode(
                held,
                new InfusionPayloads.Readout(
                        BlockPos.ZERO,
                        3,
                        InfusionPayloads.Kind.ARMED,
                        true,
                        Optional.of(ready(1, 100_000))));
        assertThrows(
                DecoderException.class,
                () -> InfusionPayloads.Readout.STREAM_CODEC.decode(held),
                "held over 99999");
    }

    @Test
    void viewPacksOffsets() {
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                int p = BowlView.pack(dx, dz, (dx + dz) % 2 == 0);
                assertEquals(dx, BowlView.dx(p));
                assertEquals(dz, BowlView.dz(p));
                assertEquals((dx + dz) % 2 == 0, BowlView.present(p));
            }
        }
        BowlView v = new BowlView(List.of(BowlView.pack(2, 0, true)), Optional.empty(), 1, 2, 0, 1);
        assertEquals(
                v,
                BowlView.CODEC
                        .parse(
                                JsonOps.INSTANCE,
                                BowlView.CODEC.encodeStart(JsonOps.INSTANCE, v).getOrThrow())
                        .getOrThrow());
    }

    @Test
    void statusKeys() {
        assertEquals("status.vftv.infusion.ready", InfusionStatus.READY.key(null));
        assertEquals(
                "status.vftv.infusion.enchant.at_max",
                InfusionStatus.ENCHANT_REFUSED.key(InfusionStatus.EnchantRefusal.AT_MAX));
        assertEquals(
                InfusionLang.TABLE.size(),
                InfusionLang.TABLE.stream().map(e -> e.key()).distinct().count());
    }
}
