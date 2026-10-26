package com.andrewbrogoff.vftv.core.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import org.junit.jupiter.api.Test;

/**
 * The placement vectors (worldgen spec, "Placement") through vanilla's own {@code
 * getPotentialStructureChunk}, so a vanilla change to the algorithm is caught; and the separation
 * proof over 6,400 regions.
 */
class WorldgenPlacementTest {
    private static JsonObject fixtures() throws IOException {
        Path file =
                Path.of(System.getProperty("vftv.projectDir", "."), "content/worldgen/fixtures.json");
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                .getAsJsonObject();
    }

    @Test
    void everyPlacementRow() throws IOException {
        JsonArray rows = fixtures().getAsJsonArray("placement");
        assertEquals(6, rows.size(), "the spec's six rows");
        for (var e : rows) {
            JsonObject row = e.getAsJsonObject();
            int spacing = row.get("spacing").getAsInt();
            RandomSpreadStructurePlacement placement =
                    new RandomSpreadStructurePlacement(
                            spacing,
                            row.get("separation").getAsInt(),
                            RandomSpreadType.LINEAR,
                            row.get("salt").getAsInt());
            long seed = new BigInteger(row.get("seed").getAsString().substring(2), 16).longValue();
            JsonArray region = row.getAsJsonArray("region");
            ChunkPos start =
                    placement.getPotentialStructureChunk(
                            seed, region.get(0).getAsInt() * spacing, region.get(1).getAsInt() * spacing);
            JsonArray expect = row.getAsJsonArray("expect");
            assertEquals(expect.get(0).getAsInt(), start.x(), "x of " + region);
            assertEquals(expect.get(1).getAsInt(), start.z(), "z of " + region);
        }
    }

    @Test
    void startsOfDistinctRegionsAreAtLeastFifteenChunksApart() {
        RandomSpreadStructurePlacement placement =
                new RandomSpreadStructurePlacement(36, 14, RandomSpreadType.LINEAR, 1128812370);
        long seed = 0x0123456789ABCDEFL;
        int min = Integer.MAX_VALUE;
        for (int rx = -40; rx < 40; rx++) {
            for (int rz = -40; rz < 40; rz++) {
                ChunkPos a = placement.getPotentialStructureChunk(seed, rx * 36, rz * 36);
                ChunkPos b = placement.getPotentialStructureChunk(seed, (rx + 1) * 36, rz * 36);
                ChunkPos c = placement.getPotentialStructureChunk(seed, rx * 36, (rz + 1) * 36);
                ChunkPos d = placement.getPotentialStructureChunk(seed, (rx + 1) * 36, (rz + 1) * 36);
                for (ChunkPos o : new ChunkPos[] {b, c, d}) {
                    min = Math.min(min, Math.max(Math.abs(a.x() - o.x()), Math.abs(a.z() - o.z())));
                }
            }
        }
        assertTrue(min >= 15, "minimum Chebyshev separation " + min);
    }
}
