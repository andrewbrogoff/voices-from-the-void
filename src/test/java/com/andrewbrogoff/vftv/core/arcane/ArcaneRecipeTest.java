package com.andrewbrogoff.vftv.core.arcane;

import static com.andrewbrogoff.vftv.core.arcane.ArcaneTestData.decode;
import static com.andrewbrogoff.vftv.core.arcane.ArcaneTestData.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.api.vis.VisKind;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

class ArcaneRecipeTest {
    private static final String SHAPED =
            "'research': 'vftv:test_entry', 'pattern': ['sc', 'cs'],"
                    + " 'key': {'s': 'minecraft:stick', 'c': 'minecraft:copper_ingot'},"
                    + " 'result': {'id': 'minecraft:bell'}, 'vis': {'vftv:aer': 8, 'vftv:ordo': 6}";
    private static final String SHAPELESS =
            "'research': 'vftv:test_entry', 'ingredients': ['minecraft:stick', 'minecraft:feather'],"
                    + " 'result': {'id': 'minecraft:arrow', 'count': 4}, 'vis': {'vftv:terra': 1000}";

    private static String error(DataResult<?> result) {
        return result.error().orElseThrow(() -> new AssertionError("decoded")).message();
    }

    private static void rejects(String body, String reason) {
        String message = error(decode("{" + body + "}"));
        assertTrue(message.contains(reason), message);
    }

    @Test
    void decodesShaped() {
        ArcaneRecipe r = decode("{" + SHAPED + "}").getOrThrow();
        assertEquals(ArcaneTestData.ENTRY, r.research());
        assertEquals(VisKind.ORDINARY, r.kind(), "kind defaults to ordinary");
        ShapedRecipePattern p = r.pattern().orElseThrow();
        assertEquals(2, p.width());
        assertEquals(2, p.height());
        assertEquals(List.of(), r.ingredients());
        assertEquals(List.of(AspectKeys.AER, AspectKeys.ORDO), List.copyOf(r.vis().keySet()));
        assertTrue(r.display().getFirst() instanceof ShapedCraftingRecipeDisplay);
    }

    @Test
    void decodesShapelessAndVoid() {
        ArcaneRecipe r = decode("{'kind': 'void', " + SHAPELESS + "}").getOrThrow();
        assertEquals(VisKind.VOID, r.kind());
        assertTrue(r.pattern().isEmpty());
        assertEquals(2, r.ingredients().size());
        assertEquals(4, r.result().count());
        assertTrue(r.display().getFirst() instanceof ShapelessCraftingRecipeDisplay);
    }

    @Test
    void costsAreSortedByAspectId() {
        ArcaneRecipe r =
                decode(
                                "{'research': 'vftv:test_entry', 'ingredients': ['minecraft:stick'],"
                                        + " 'result': {'id': 'minecraft:bell'},"
                                        + " 'vis': {'vftv:terra': 1, 'vftv:aer': 2, 'vftv:ordo': 3}}")
                        .getOrThrow();
        assertEquals(
                List.of(AspectKeys.AER, AspectKeys.ORDO, AspectKeys.TERRA),
                List.copyOf(r.vis().keySet()));
    }

    @Test
    void shapedXorShapeless() {
        rejects(SHAPED + ", 'ingredients': ['minecraft:stick']", "exactly one");
        rejects(
                "'research': 'vftv:test_entry', 'result': {'id': 'minecraft:bell'},"
                        + " 'vis': {'vftv:aer': 1}",
                "exactly one");
        rejects(
                "'research': 'vftv:test_entry', 'key': {'s': 'minecraft:stick'},"
                        + " 'result': {'id': 'minecraft:bell'}, 'vis': {'vftv:aer': 1}",
                "pattern");
    }

    @Test
    void patternRules() {
        String rest =
                ", 'key': {'s': 'minecraft:stick'}, 'result': {'id': 'minecraft:bell'},"
                        + " 'vis': {'vftv:aer': 1}, 'research': 'vftv:test_entry'";
        rejects("'pattern': ['s', 's', 's', 's']" + rest, "1-3 rows");
        rejects("'pattern': ['ssss']" + rest, "1-3 symbols");
        rejects("'pattern': []" + rest, "1-3 rows");
        rejects("'pattern': ['   ']" + rest, "at least one symbol");
        rejects("'pattern': ['sx']" + rest, "not defined in the key");
        rejects("'pattern': ['s', 'ss']" + rest, "same width");
        rejects(
                "'pattern': ['s'], 'key': {'s': 'minecraft:stick', 'u': 'minecraft:stick'},"
                        + " 'result': {'id': 'minecraft:bell'}, 'vis': {'vftv:aer': 1},"
                        + " 'research': 'vftv:test_entry'",
                "aren't used");
        rejects(
                "'ingredients': [], 'result': {'id': 'minecraft:bell'}, 'vis': {'vftv:aer': 1},"
                        + " 'research': 'vftv:test_entry'",
                "too short");
    }

    @Test
    void threeByThreeEvenWhenVanillaWidens() {
        ShapedRecipePattern.setCraftingSize(4, 4);
        rejects(
                "'pattern': ['s', 's', 's', 's'], 'key': {'s': 'minecraft:stick'},"
                        + " 'result': {'id': 'minecraft:bell'}, 'vis': {'vftv:aer': 1},"
                        + " 'research': 'vftv:test_entry'",
                "1-3 rows");
        rejects(
                "'pattern': ['ssss'], 'key': {'s': 'minecraft:stick'},"
                        + " 'result': {'id': 'minecraft:bell'}, 'vis': {'vftv:aer': 1},"
                        + " 'research': 'vftv:test_entry'",
                "1-3 symbols");
    }

    @Test
    void researchIsRequiredAndMustExist() {
        rejects(SHAPED.replace("'research': 'vftv:test_entry', ", ""), "research");
        rejects(
                SHAPED.replace("vftv:test_entry", "vftv:nowhere"),
                "unknown research entry vftv:nowhere");
        rejects(SHAPED.replace("vftv:test_entry", "vftv:" + "x".repeat(260)), "longer than 256");
    }

    @Test
    void kindIsOrdinaryOrVoid() {
        rejects("'kind': 'eldritch', " + SHAPED, "kind must be ordinary or void");
    }

    @Test
    void costKeysMustBeVisAspects() {
        rejects(SHAPED.replace("vftv:ordo", "vftv:sonus"), "vftv:sonus is not a vis aspect");
        rejects(SHAPED.replace("vftv:ordo", "vftv:vacuos"), "vftv:vacuos is not a vis aspect");
        rejects(SHAPED.replace("vftv:ordo", "vftv:nothing"), "vftv:nothing is not a vis aspect");
    }

    @Test
    void costValuesAndEntries() {
        rejects(SHAPED.replace("'vftv:aer': 8", "'vftv:aer': 0"), "outside of range");
        rejects(SHAPED.replace("'vftv:aer': 8", "'vftv:aer': 1001"), "outside of range");
        rejects(SHAPED.replace("{'vftv:aer': 8, 'vftv:ordo': 6}", "{}"), "1-8 entries");
        StringBuilder nine = new StringBuilder("{");
        for (int i = 0; i < 9; i++) {
            nine.append(i == 0 ? "" : ", ").append("'vftv:a").append(i).append("': 1");
        }
        rejects(SHAPED.replace("{'vftv:aer': 8, 'vftv:ordo': 6}", nine + "}"), "1-8 entries");
    }

    @Test
    void decodingNeedsTheRegistries() {
        assertTrue(
                ArcaneRecipe.MAP_CODEC
                        .codec()
                        .parse(JsonOps.INSTANCE, json("{" + SHAPED + "}"))
                        .isError(),
                "plain ops");
        // Registry access without the research and aspect registries.
        HolderLookup.Provider items =
                HolderLookup.Provider.create(
                        Stream.<HolderLookup.RegistryLookup<?>>of(BuiltInRegistries.ITEM));
        String message =
                error(
                        ArcaneRecipe.MAP_CODEC
                                .codec()
                                .parse(
                                        items.createSerializationContext(JsonOps.INSTANCE),
                                        json("{" + SHAPED + "}")));
        assertTrue(message.contains("unknown research entry vftv:test_entry"), message);
    }

    @Test
    void encodesBackToTheBody() {
        ArcaneRecipe r = decode("{" + SHAPELESS + "}").getOrThrow();
        var encoded =
                ArcaneRecipe.MAP_CODEC
                        .codec()
                        .encodeStart(
                                ArcaneTestData.registries()
                                        .createSerializationContext(JsonOps.INSTANCE),
                                r)
                        .getOrThrow();
        assertEquals(json("{" + SHAPELESS + "}"), encoded);
    }

    @Test
    void createdResultIsChecked() {
        assertTrue(decode("{" + SHAPED + "}").getOrThrow().createResult().isSuccess());
        // A component-resolved maximum of 1, and 2 asked for.
        ArcaneRecipe over =
                decode(
                                "{"
                                        + SHAPED.replace(
                                                "{'id': 'minecraft:bell'}",
                                                "{'id': 'minecraft:bell', 'count': 2,"
                                                        + " 'components': {'minecraft:max_stack_size': 1}}")
                                        + "}")
                        .getOrThrow();
        assertTrue(error(over.createResult()).contains("empty stack"));
        // Damageable and stackable fails strict validation, so creation gives an empty stack.
        ArcaneRecipe empty =
                decode(
                                "{"
                                        + SHAPED.replace(
                                                "{'id': 'minecraft:bell'}",
                                                "{'id': 'minecraft:bell',"
                                                        + " 'components': {'minecraft:max_damage': 5}}")
                                        + "}")
                        .getOrThrow();
        assertTrue(error(empty.createResult()).contains("empty stack"));
    }

    @Test
    void isNotVanillaCrafting() {
        ArcaneRecipe r = decode("{" + SHAPED + "}").getOrThrow();
        assertTrue(r.isSpecial());
        assertFalse(r.showNotification());
        assertTrue(r.placementInfo().isImpossibleToPlace());
        assertEquals(ArcaneRecipe.TYPE, r.getType());
        assertEquals(ArcaneRecipe.SERIALIZER, r.getSerializer());
        assertEquals("", r.group());
        assertEquals(
                new ItemStack(Items.BELL).getItem(), r.assemble(ArcaneTestData.grid()).getItem());
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(
                Unpooled.buffer(),
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY),
                ConnectionType.NEOFORGE);
    }

    private static byte[] encode(ArcaneRecipe recipe) {
        RegistryFriendlyByteBuf buf = buffer();
        ArcaneRecipe.STREAM_CODEC.encode(buf, recipe);
        return ByteBufUtil.getBytes(buf);
    }

    private static ArcaneRecipe roundTrip(ArcaneRecipe recipe) {
        RegistryFriendlyByteBuf buf = buffer();
        ArcaneRecipe.STREAM_CODEC.encode(buf, recipe);
        ArcaneRecipe back = ArcaneRecipe.STREAM_CODEC.decode(buf);
        assertEquals(0, buf.readableBytes());
        return back;
    }

    @Test
    void streamRoundTrip() {
        for (String body : List.of(SHAPED, "'kind': 'void', " + SHAPELESS)) {
            ArcaneRecipe r = decode("{" + body + "}").getOrThrow();
            ArcaneRecipe back = roundTrip(r);
            assertEquals(r.research(), back.research());
            assertEquals(r.kind(), back.kind());
            assertEquals(r.vis(), back.vis());
            assertEquals(r.result(), back.result());
            assertEquals(r.ingredients(), back.ingredients());
            assertEquals(
                    r.pattern().map(ShapedRecipePattern::ingredients),
                    back.pattern().map(ShapedRecipePattern::ingredients));
            assertTrue(Arrays.equals(encode(r), encode(back)));
        }
    }

    /** A buffer holding whatever {@code body} writes. */
    private static RegistryFriendlyByteBuf wire(Consumer<RegistryFriendlyByteBuf> body) {
        RegistryFriendlyByteBuf buf = buffer();
        body.accept(buf);
        return buf;
    }

    private static void writeTail(RegistryFriendlyByteBuf buf, int entries, int cost) {
        ItemStackTemplate.STREAM_CODEC.encode(buf, new ItemStackTemplate(Items.BELL));
        buf.writeByte(entries);
        for (int i = 0; i < entries; i++) {
            buf.writeUtf("vftv:a" + i);
            buf.writeVarInt(cost);
        }
    }

    private static void shapelessHead(RegistryFriendlyByteBuf buf, int kind, int count) {
        buf.writeUtf("vftv:test_entry");
        buf.writeByte(kind);
        buf.writeByte(0);
        buf.writeByte(count);
        for (int i = 0; i < count; i++) {
            Ingredient.CONTENTS_STREAM_CODEC.encode(buf, Ingredient.of(Items.STICK));
        }
    }

    private static void decodeRejects(RegistryFriendlyByteBuf buf, String reason) {
        DecoderException e =
                assertThrows(DecoderException.class, () -> ArcaneRecipe.STREAM_CODEC.decode(buf));
        assertTrue(String.valueOf(e.getMessage()).contains(reason), e.getMessage());
    }

    @Test
    void streamBounds() {
        // A valid frame decodes.
        ArcaneRecipe.STREAM_CODEC.decode(
                wire(
                        b -> {
                            shapelessHead(b, 1, 9);
                            writeTail(b, 8, 1000);
                        }));
        decodeRejects(wire(b -> b.writeUtf("vftv:" + "x".repeat(260))), "");
        decodeRejects(wire(b -> b.writeUtf("Not An Id")), "not an id");
        decodeRejects(wire(b -> shapelessHead(b, 2, 1)), "kind 2");
        decodeRejects(wire(b -> shapelessHead(b, 0, 10)), "ingredient count 10");
        decodeRejects(wire(b -> shapelessHead(b, 0, 0)), "ingredient count 0");
        decodeRejects(
                wire(
                        b -> {
                            b.writeUtf("vftv:test_entry");
                            b.writeByte(0);
                            b.writeByte(4);
                        }),
                "pattern width 4");
        decodeRejects(
                wire(
                        b -> {
                            b.writeUtf("vftv:test_entry");
                            b.writeByte(0);
                            b.writeByte(1);
                            b.writeByte(0);
                        }),
                "pattern height 0");
        decodeRejects(
                wire(
                        b -> {
                            b.writeUtf("vftv:test_entry");
                            b.writeByte(0);
                            b.writeByte(1);
                            b.writeByte(1);
                            Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC.encode(b, Optional.empty());
                        }),
                "no ingredient");
        decodeRejects(
                wire(
                        b -> {
                            shapelessHead(b, 0, 1);
                            writeTail(b, 9, 1);
                        }),
                "vis entries 9");
        decodeRejects(
                wire(
                        b -> {
                            shapelessHead(b, 0, 1);
                            writeTail(b, 0, 1);
                        }),
                "vis entries 0");
        decodeRejects(
                wire(
                        b -> {
                            shapelessHead(b, 0, 1);
                            writeTail(b, 1, 1001);
                        }),
                "vis cost 1001");
        decodeRejects(
                wire(
                        b -> {
                            shapelessHead(b, 0, 1);
                            writeTail(b, 1, 0);
                        }),
                "vis cost 0");
        decodeRejects(
                wire(
                        b -> {
                            shapelessHead(b, 0, 1);
                            ItemStackTemplate.STREAM_CODEC.encode(
                                    b, new ItemStackTemplate(Items.BELL));
                            b.writeByte(2);
                            b.writeUtf("vftv:aer");
                            b.writeVarInt(1);
                            b.writeUtf("vftv:aer");
                            b.writeVarInt(2);
                        }),
                "duplicate vis aspect vftv:aer");
    }
}
