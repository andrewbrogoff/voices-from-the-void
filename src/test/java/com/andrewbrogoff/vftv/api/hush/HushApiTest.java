package com.andrewbrogoff.vftv.api.hush;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.andrewbrogoff.vftv.api.essentia.SpillCause;
import com.andrewbrogoff.vftv.api.flux.FluxClearSource;
import com.andrewbrogoff.vftv.api.flux.FluxSource;
import com.andrewbrogoff.vftv.api.vis.NodeType;
import java.util.HashSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

// No server exists in a unit test, so live handles (players, levels) are passed as null.
@SuppressWarnings("NullAway")
class HushApiTest {
    // Live handles (ServerLevel, ServerPlayer) are passed as null: no constructor dereferences
    // them.

    @Test
    void hushSpreadPayloadCopiesItsPosition() {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(1, 64, 2);
        HushChange change =
                new HushChange.Block(
                        Blocks.STONE.defaultBlockState(), Blocks.DIRT.defaultBlockState());
        HushOrigin origin = new HushOrigin.Seep(new ChunkPos(0, 0), null);
        HushSpreadPayload payload =
                new HushSpreadPayload(HushSpreadKind.CONVERT, null, pos, change, origin);
        pos.set(9, 9, 9);
        assertEquals(new BlockPos(1, 64, 2), payload.pos());
        assertEquals(HushSpreadKind.CONVERT, payload.kind());
        assertSame(change, payload.change());
        assertSame(origin, payload.origin());
    }

    @Test
    void blockChangeKeepsItsStates() {
        BlockState before = Blocks.STONE.defaultBlockState();
        BlockState after = Blocks.DIRT.defaultBlockState();
        HushChange.Block change = new HushChange.Block(before, after);
        assertSame(before, change.before());
        assertSame(after, change.after());
    }

    @Test
    void nodeChangeKeepsItsKeys() {
        ResourceKey<NodeType> wellspring =
                ResourceKey.create(
                        HushKeys.HUSHED_NODE.registryKey(),
                        Identifier.fromNamespaceAndPath("vftv", "wellspring"));
        HushChange.Node change = new HushChange.Node(7L, wellspring, HushKeys.HUSHED_NODE);
        assertEquals(7L, change.id());
        assertEquals(wellspring, change.before());
        assertEquals(HushKeys.HUSHED_NODE, change.after());
    }

    @Test
    void riseChangeKeepsItsType() {
        assertSame(EntityTypes.ZOMBIE, new HushChange.Rise(EntityTypes.ZOMBIE).type());
    }

    @Test
    void seepOriginKeepsChunkAndSpiller() {
        UUID spiller = UUID.randomUUID();
        HushOrigin.Seep seep = new HushOrigin.Seep(new ChunkPos(3, -4), spiller);
        assertEquals(new ChunkPos(3, -4), seep.chunk());
        assertEquals(spiller, seep.spiller());
        assertNull(new HushOrigin.Seep(new ChunkPos(0, 0), null).spiller());
    }

    @Test
    void bellOriginCopiesItsPosition() {
        BlockPos.MutableBlockPos bell = new BlockPos.MutableBlockPos(5, 70, 5);
        HushOrigin.Bell origin = new HushOrigin.Bell(bell);
        bell.set(0, 0, 0);
        assertEquals(new BlockPos(5, 70, 5), origin.bell());
    }

    @Test
    void fluxAddedCopiesItsSeep() {
        BlockPos.MutableBlockPos seep = new BlockPos.MutableBlockPos(8, 63, 8);
        FluxSource source = new FluxSource.Essentia(SpillCause.EMPTIED);
        FluxPayload.Added added =
                new FluxPayload.Added(null, new ChunkPos(0, 0), seep, 10, 7, 2, 1, source, null);
        seep.set(0, 0, 0);
        assertEquals(new BlockPos(8, 63, 8), added.seep());
        assertEquals(new ChunkPos(0, 0), added.chunk());
        assertEquals(10, added.generated());
        assertEquals(7, added.retained());
        assertEquals(2, added.pending());
        assertEquals(1, added.overflow());
        assertEquals(new FluxSource.Essentia(SpillCause.EMPTIED), added.source());
        assertNull(added.player());
        assertNull(
                new FluxPayload.Added(
                                null,
                                new ChunkPos(0, 0),
                                null,
                                1,
                                1,
                                0,
                                0,
                                new FluxSource.Vis(),
                                null)
                        .seep());
    }

    @Test
    void fluxClearedCopiesItsSeep() {
        BlockPos.MutableBlockPos seep = new BlockPos.MutableBlockPos(8, 63, 8);
        FluxPayload.Cleared cleared =
                new FluxPayload.Cleared(
                        null, new ChunkPos(1, 1), seep, 50, FluxClearSource.PEAL, null, 120, 70);
        seep.set(0, 0, 0);
        assertEquals(new BlockPos(8, 63, 8), cleared.seep());
        assertEquals(new ChunkPos(1, 1), cleared.chunk());
        assertEquals(50, cleared.amount());
        assertEquals(FluxClearSource.PEAL, cleared.source());
        assertNull(cleared.player());
        assertEquals(120, cleared.fluxBefore());
        assertEquals(70, cleared.fluxAfter());
    }

    @Test
    void fluxSourcesCompareByValue() {
        assertEquals(new FluxSource.Vis(), new FluxSource.Vis());
        assertEquals(new FluxSource.Command(), new FluxSource.Command());
        assertEquals(SpillCause.DIED, new FluxSource.Essentia(SpillCause.DIED).cause());
    }

    @Test
    void formsAreTheFixedUnmodifiableTable() {
        assertEquals(13, HushKeys.FORMS.size());
        assertEquals(
                13, new HashSet<>(HushKeys.FORMS.stream().map(HushedForm::source).toList()).size());
        assertEquals(
                new HushedForm(
                        Identifier.withDefaultNamespace("grass_block"),
                        Identifier.fromNamespaceAndPath("vftv", "hushed_grass_block")),
                HushKeys.FORMS.getFirst());
        assertThrows(
                UnsupportedOperationException.class,
                () -> HushKeys.FORMS.add(HushKeys.FORMS.getFirst()));
    }
}
