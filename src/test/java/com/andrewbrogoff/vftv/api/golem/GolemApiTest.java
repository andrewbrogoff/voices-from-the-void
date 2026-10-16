package com.andrewbrogoff.vftv.api.golem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andrewbrogoff.vftv.api.TestItems;
import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.essentia.SpillCause;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The golem API's rules: {@link GolemJob}'s constructor and codec (API spec, "Golem jobs"), and the
 * payload rules (API spec, "Immutability and access").
 */
// No server exists in a unit test, so live handles (players, levels) are passed as null.
@SuppressWarnings("NullAway")
class GolemApiTest {
    @BeforeAll
    static void bindItemComponents() {
        TestItems.bindComponents();
    }

    private static final UUID GOLEM = new UUID(1, 2);
    private static final UUID OWNER = new UUID(3, 4);
    private static final ResourceKey<Aspect> AQUA =
            ResourceKey.create(
                    ResourceKey.createRegistryKey(
                            Identifier.fromNamespaceAndPath("vftv", "aspect")),
                    Identifier.fromNamespaceAndPath("vftv", "aqua"));

    // --- GolemJob ---

    @Test
    void jobAcceptsTheBuiltInShapesAndCopiesItsRoles() {
        List<GolemRole> roles =
                new ArrayList<>(
                        List.of(
                                GolemRole.TAKE,
                                GolemRole.GIVE,
                                GolemRole.FEED,
                                GolemRole.TIP,
                                GolemRole.COLLECT));
        GolemJob carry = new GolemJob(GolemCarryKind.ITEMS, roles, GolemRole.TAKE);
        roles.clear();

        assertEquals(5, carry.roles().size());
        assertThrows(UnsupportedOperationException.class, () -> carry.roles().add(GolemRole.TAKE));
        GolemJob decant =
                new GolemJob(
                        GolemCarryKind.ESSENTIA,
                        List.of(GolemRole.TAKE, GolemRole.GIVE),
                        GolemRole.GIVE);
        assertEquals(GolemRole.GIVE, decant.defaultRole());
    }

    @Test
    void jobRejectsEmptyRoles() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new GolemJob(GolemCarryKind.ITEMS, List.of(), GolemRole.TAKE));
    }

    @Test
    void jobRejectsDuplicateRoles() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new GolemJob(
                                GolemCarryKind.ITEMS,
                                List.of(GolemRole.TAKE, GolemRole.TAKE),
                                GolemRole.TAKE));
    }

    @Test
    void jobRejectsRolesOutsideItsKind() {
        for (GolemRole itemsOnly : List.of(GolemRole.FEED, GolemRole.TIP, GolemRole.COLLECT)) {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new GolemJob(
                                    GolemCarryKind.ESSENTIA,
                                    List.of(GolemRole.TAKE, itemsOnly),
                                    GolemRole.TAKE));
        }
    }

    @Test
    void jobRejectsADefaultOutsideItsRoles() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new GolemJob(GolemCarryKind.ITEMS, List.of(GolemRole.TAKE), GolemRole.GIVE));
    }

    @Test
    void jobCodecRoundTrips() {
        GolemJob job =
                new GolemJob(
                        GolemCarryKind.ITEMS,
                        List.of(GolemRole.GIVE, GolemRole.TAKE, GolemRole.COLLECT),
                        GolemRole.COLLECT);

        JsonElement json = GolemJob.CODEC.encodeStart(JsonOps.INSTANCE, job).getOrThrow();

        assertEquals(
                JsonParser.parseString(
                        "{\"carry\":\"items\",\"roles\":[\"give\",\"take\",\"collect\"],"
                                + "\"default_role\":\"collect\"}"),
                json);
        assertEquals(job, GolemJob.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }

    @Test
    void jobCodecRejectsInvalidJobs() {
        for (String bad :
                List.of(
                        "{\"carry\":\"items\",\"roles\":[],\"default_role\":\"take\"}",
                        "{\"carry\":\"items\",\"roles\":[\"take\",\"take\"],\"default_role\":\"take\"}",
                        "{\"carry\":\"essentia\",\"roles\":[\"take\",\"feed\"],\"default_role\":\"take\"}",
                        "{\"carry\":\"items\",\"roles\":[\"take\"],\"default_role\":\"give\"}",
                        "{\"carry\":\"items\",\"roles\":[\"dance\"],\"default_role\":\"dance\"}",
                        "{\"carry\":\"fluids\",\"roles\":[\"take\"],\"default_role\":\"take\"}",
                        "{\"carry\":\"items\",\"roles\":[\"take\"]}",
                        "{\"carry\":\"items\",\"roles\":\"take\",\"default_role\":\"take\"}")) {
            assertTrue(
                    GolemJob.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(bad)).isError(),
                    bad);
        }
    }

    // --- payloads ---

    @Test
    void itemResourceCopiesItsStack() {
        ItemStack input = new ItemStack(Items.STONE, 3);
        GolemResource.Item resource = new GolemResource.Item(input);
        input.setCount(1);

        assertStackCopies(resource::stack, Items.STONE, 3);
    }

    @Test
    void essentiaResourceHoldsItsAspect() {
        assertEquals(AQUA, new GolemResource.Essentia(AQUA).aspect());
    }

    @Test
    void workPayloadCopiesItsPosition() {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(1, 2, 3);
        GolemWorkPayload payload =
                new GolemWorkPayload(
                        GOLEM,
                        OWNER,
                        null,
                        pos,
                        Direction.UP,
                        GolemRole.TAKE,
                        new GolemResource.Essentia(AQUA),
                        4);
        pos.set(9, 9, 9);

        assertEquals(new BlockPos(1, 2, 3), payload.pos());
        assertFalse(payload.pos() instanceof BlockPos.MutableBlockPos);
    }

    @Test
    void placedPayloadCopiesItsPosition() {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(1, 2, 3);
        GolemPayload.Placed payload =
                new GolemPayload.Placed(
                        GOLEM,
                        OWNER,
                        null,
                        pos,
                        GolemKeys.BRASS,
                        BuiltInRegistries.ITEM.getResourceKey(Items.STONE).orElseThrow());
        pos.set(9, 9, 9);

        assertEquals(new BlockPos(1, 2, 3), payload.pos());
    }

    @Test
    void pickedUpPayloadCopiesItsPosition() {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(1, 2, 3);
        GolemPayload.PickedUp payload = new GolemPayload.PickedUp(GOLEM, OWNER, null, pos);
        pos.set(9, 9, 9);

        assertEquals(new BlockPos(1, 2, 3), payload.pos());
    }

    @Test
    void movedPayloadCopiesItsPositionAndItems() {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(1, 2, 3);
        ItemStack input = new ItemStack(Items.STONE, 3);
        GolemPayload.Moved payload =
                new GolemPayload.Moved(
                        GOLEM,
                        OWNER,
                        null,
                        pos,
                        Direction.NORTH,
                        GolemRole.GIVE,
                        new GolemResource.Item(input),
                        3);
        pos.set(9, 9, 9);
        input.setCount(1);

        assertEquals(new BlockPos(1, 2, 3), payload.pos());
        GolemResource.Item resource =
                assertInstanceOf(GolemResource.Item.class, payload.resource());
        assertStackCopies(resource::stack, Items.STONE, 3);
    }

    @Test
    void spilledPayloadCopiesItsPosition() {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(1, 2, 3);
        GolemPayload.Spilled payload =
                new GolemPayload.Spilled(GOLEM, null, pos, AQUA, 7, SpillCause.DIED);
        pos.set(9, 9, 9);

        assertEquals(new BlockPos(1, 2, 3), payload.pos());
        assertEquals(SpillCause.DIED, payload.cause());
    }

    @Test
    void diedPayloadCopiesItsDrops() {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(1, 2, 3);
        ItemStack drop = new ItemStack(Items.STONE, 3);
        List<ItemStack> drops = new ArrayList<>(List.of(drop));
        GolemPayload.Died payload = new GolemPayload.Died(GOLEM, OWNER, null, pos, drops);
        pos.set(9, 9, 9);
        drop.setCount(1);
        drops.add(new ItemStack(Items.DIRT));

        assertEquals(new BlockPos(1, 2, 3), payload.pos());
        assertEquals(1, payload.drops().size());
        assertThrows(
                UnsupportedOperationException.class,
                () -> payload.drops().add(new ItemStack(Items.DIRT)));
        assertStackCopies(() -> payload.drops().getFirst(), Items.STONE, 3);
        List<ItemStack> first = payload.drops();
        List<ItemStack> second = payload.drops();
        assertNotSame(first.getFirst(), second.getFirst());
    }

    /**
     * Two calls give distinct copies, and mutating one changes neither the other nor a later call.
     */
    private static void assertStackCopies(Supplier<ItemStack> accessor, Item item, int count) {
        ItemStack first = accessor.get();
        ItemStack second = accessor.get();
        assertNotSame(first, second);
        first.setCount(count + 10);
        assertEquals(count, second.getCount());
        assertEquals(count, accessor.get().getCount());
        assertTrue(accessor.get().is(item));
    }
}
