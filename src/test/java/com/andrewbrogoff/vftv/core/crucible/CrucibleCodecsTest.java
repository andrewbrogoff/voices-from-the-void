package com.andrewbrogoff.vftv.core.crucible;

import static com.andrewbrogoff.vftv.core.arcane.ArcaneTestData.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.api.crucible.CrucibleLimits;
import com.andrewbrogoff.vftv.core.arcane.ArcaneTestData;
import com.andrewbrogoff.vftv.core.common.RawRecordCodec.Retained;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Consumer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.common.crafting.CompoundIngredient;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

/** The crucible's record, recipe and snapshot codecs. */
class CrucibleCodecsTest {
    private static CompoundTag record(int format, Map<String, Integer> pool) {
        CompoundTag tag = new CompoundTag();
        tag.put("format", IntTag.valueOf(format));
        CompoundTag p = new CompoundTag();
        pool.forEach((k, v) -> p.put(k, IntTag.valueOf(v)));
        tag.put("pool", p);
        return tag;
    }

    private static Retained<SortedMap<String, Integer>> decode(Tag tag) {
        return CrucibleBlockEntity.RETAINED.parse(NbtOps.INSTANCE, tag).getOrThrow();
    }

    @Test
    void aReadableRecordRoundTripsAndKeepsMissingIds() {
        CompoundTag tag = record(1, Map.of("vftv:aer", 3, "vftv:gone", 7));
        var read = assertInstanceOf(Retained.Read.class, decode(tag));
        assertEquals(new TreeMap<>(Map.of("vftv:aer", 3, "vftv:gone", 7)), read.value());
        Tag back =
                CrucibleBlockEntity.RETAINED.encodeStart(NbtOps.INSTANCE, decode(tag)).getOrThrow();
        assertEquals(tag, back);
    }

    @Test
    void outOfBoundsRecordsAreUnreadableAndKeptRaw() {
        Map<String, Integer> big = new TreeMap<>();
        for (int i = 0; i < 65; i++) {
            big.put("vftv:a" + i, 1);
        }
        CompoundTag extra = record(1, Map.of("vftv:aer", 1));
        extra.putString("surprise", "x");
        CompoundTag missing = new CompoundTag();
        missing.put("pool", new CompoundTag());
        for (Tag bad :
                List.of(
                        record(2, Map.of("vftv:aer", 1)),
                        record(0, Map.of()),
                        missing,
                        record(1, big),
                        record(1, Map.of("vftv:aer", 0)),
                        record(1, Map.of("vftv:aer", 10_001)),
                        record(1, Map.of("vftv:aer", 6000, "vftv:terra", 5000)),
                        record(1, Map.of("Not An Id", 1)),
                        extra)) {
            Retained<SortedMap<String, Integer>> r = decode(bad);
            assertInstanceOf(Retained.Unreadable.class, r, bad.toString());
            assertEquals(
                    bad, CrucibleBlockEntity.RETAINED.encodeStart(NbtOps.INSTANCE, r).getOrThrow());
        }
        assertInstanceOf(Retained.Read.class, decode(record(1, Map.of("vftv:aer", 10_000))));
    }

    private static DataResult<CrucibleRecipe> recipe(String research, String aspects) {
        return CrucibleRecipe.MAP_CODEC
                .codec()
                .parse(
                        ArcaneTestData.registries().createSerializationContext(JsonOps.INSTANCE),
                        json(
                                "{'research': '"
                                        + research
                                        + "', 'catalyst': 'minecraft:stick', 'aspects': {"
                                        + aspects
                                        + "}, 'result': {'id': 'minecraft:bell'}}"));
    }

    @Test
    void theRecipeCodecChecksRegistriesAndBounds() {
        String entry = ArcaneTestData.ENTRY.identifier().toString();
        assertTrue(recipe(entry, "'vftv:sonus': 1, 'vftv:vacuos': 1000").isSuccess());
        assertTrue(recipe(entry, "'vftv:nowhere': 1").isError(), "unknown aspect");
        assertTrue(recipe("vftv:nothing", "'vftv:aer': 1").isError(), "unknown research");
        assertTrue(recipe(entry, "'vftv:aer': 0").isError());
        assertTrue(recipe(entry, "'vftv:aer': 1001").isError());
        assertTrue(recipe(entry, "").isError());
        StringBuilder nine = new StringBuilder();
        for (int i = 0; i < 9; i++) {
            nine.append(i == 0 ? "" : ", ").append("'vftv:a").append(i).append("': 1");
        }
        assertTrue(recipe(entry, nine.toString()).isError(), "a ninth entry");
        CrucibleRecipe r = recipe(entry, "'vftv:terra': 2, 'vftv:aer': 3").getOrThrow();
        assertEquals(List.of("vftv:aer", "vftv:terra"), List.copyOf(r.cost().keySet()));
        assertEquals(5, r.total());
        assertTrue(r.matches(new ItemStack(Items.STICK, 64)), "matched on a count-one copy");
        assertTrue(r.isSpecial());
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(
                Unpooled.buffer(),
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY),
                ConnectionType.NEOFORGE);
    }

    private static CrucibleRecipe sample() {
        return new CrucibleRecipe(
                ArcaneTestData.ENTRY,
                Ingredient.of(Items.COPPER_INGOT),
                Map.of(AspectKeys.SONUS, 6, AspectKeys.AER, 4),
                new ItemStackTemplate(Items.BELL));
    }

    @Test
    void theStreamCodecRoundTrips() {
        ArcaneTestData.registries();
        RegistryFriendlyByteBuf buf = buffer();
        CrucibleRecipe.STREAM_CODEC.encode(buf, sample());
        CrucibleRecipe back = CrucibleRecipe.STREAM_CODEC.decode(buf);
        assertEquals(0, buf.readableBytes());
        assertEquals(sample().research(), back.research());
        assertEquals(sample().cost(), back.cost());
        assertEquals(sample().result(), back.result());
    }

    private static RegistryFriendlyByteBuf wire(Consumer<RegistryFriendlyByteBuf> body) {
        RegistryFriendlyByteBuf buf = buffer();
        body.accept(buf);
        return buf;
    }

    private static void head(RegistryFriendlyByteBuf buf, int entries) {
        buf.writeUtf("vftv:test_entry");
        Ingredient.CONTENTS_STREAM_CODEC.encode(buf, Ingredient.of(Items.STICK));
        buf.writeByte(entries);
    }

    @Test
    void theStreamCodecRejectsOutOfBounds() {
        ArcaneTestData.registries();
        for (RegistryFriendlyByteBuf bad :
                List.of(
                        wire(b -> head(b, 0)),
                        wire(b -> head(b, 9)),
                        wire(
                                b -> {
                                    head(b, 1);
                                    b.writeUtf("vftv:aer");
                                    b.writeVarInt(1001);
                                }),
                        wire(
                                b -> {
                                    head(b, 2);
                                    b.writeUtf("vftv:aer");
                                    b.writeVarInt(1);
                                    b.writeUtf("vftv:aer");
                                    b.writeVarInt(1);
                                }),
                        wire(b -> b.writeUtf("NOT AN ID")))) {
            assertThrows(DecoderException.class, () -> CrucibleRecipe.STREAM_CODEC.decode(bad));
        }
    }

    private static RecipeHolder<CrucibleRecipe> holder(String path, Ingredient catalyst) {
        return new RecipeHolder<>(
                ResourceKey.create(
                        Registries.RECIPE, Identifier.fromNamespaceAndPath("vftv", path)),
                new CrucibleRecipe(
                        ArcaneTestData.ENTRY,
                        catalyst,
                        Map.of(AspectKeys.AER, 1),
                        new ItemStackTemplate(Items.BELL)));
    }

    @Test
    void theSnapshotMergesSimpleAndCustomCatalystsInIdOrder() {
        ArcaneTestData.registries();
        Ingredient custom =
                CompoundIngredient.of(
                        Ingredient.of(Items.IRON_INGOT), Ingredient.of(Items.GOLD_INGOT));
        CrucibleRecipeSnapshot snapshot =
                CrucibleRecipeSnapshot.of(
                                List.of(
                                        holder("c_simple", Ingredient.of(Items.IRON_INGOT)),
                                        holder("a_custom", custom),
                                        holder("b_simple", Ingredient.of(Items.IRON_INGOT)),
                                        holder("d_other", Ingredient.of(Items.STICK))))
                        .getOrThrow();
        assertEquals(
                List.of("vftv:a_custom", "vftv:b_simple", "vftv:c_simple"),
                snapshot.candidates(new ItemStack(Items.IRON_INGOT, 5), k -> true).stream()
                        .map(h -> h.id().identifier().toString())
                        .toList());
        assertEquals(
                List.of(),
                snapshot.candidates(new ItemStack(Items.IRON_INGOT), k -> false),
                "nothing unlearned");
        assertEquals(
                List.of("vftv:a_custom"),
                snapshot.candidates(new ItemStack(Items.GOLD_INGOT), k -> true).stream()
                        .map(h -> h.id().identifier().toString())
                        .toList());
        assertEquals(4, snapshot.recipes().size());
        assertTrue(snapshot.get(Identifier.fromNamespaceAndPath("vftv", "d_other")) != null);
    }

    @Test
    void theSnapshotCapRejectsTheSet() {
        ArcaneTestData.registries();
        List<RecipeHolder<CrucibleRecipe>> many = new ArrayList<>();
        for (int i = 0; i <= CrucibleLimits.MAX_CRUCIBLE_RECIPES; i++) {
            many.add(holder("r" + i, Ingredient.of(Items.STICK)));
        }
        assertTrue(CrucibleRecipeSnapshot.of(many).isError());
        assertTrue(
                CrucibleRecipeSnapshot.of(many.subList(0, CrucibleLimits.MAX_CRUCIBLE_RECIPES))
                        .isSuccess());
    }
}
