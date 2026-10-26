package com.andrewbrogoff.vftv.core.hush;

import com.andrewbrogoff.vftv.api.hush.HushKeys;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

/**
 * The harness's {@code vftv:hush} server dump: for the first player (or {@code {"pos": [x, y,
 * z]}}), that chunk's aura, its saturation, the level's pending count and overflow, the hushed
 * cells within {@code radius} (default 16) of the seep, and the Hollows near it.
 */
public final class HushDump {
    private HushDump() {}

    /** The dump. */
    public static String dump(MinecraftServer server, String args) {
        JsonObject data = new JsonObject();
        if (server.getPlayerList().getPlayers().isEmpty()) {
            return data.toString();
        }
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        ServerLevel level = player.level();
        JsonObject request =
                args.isBlank() ? new JsonObject() : JsonParser.parseString(args).getAsJsonObject();
        BlockPos at = player.blockPosition();
        if (request.has("pos")) {
            JsonArray p = request.getAsJsonArray("pos");
            at = new BlockPos(p.get(0).getAsInt(), p.get(1).getAsInt(), p.get(2).getAsInt());
        }
        int radius = request.has("radius") ? request.get("radius").getAsInt() : 16;
        LevelChunk chunk = FluxService.chunkNow(level, ChunkPos.pack(at));
        data.addProperty("loaded", chunk != null);
        data.addProperty("inHush", HushPlaceQuery.inHush(level, player.blockPosition()));
        long[] pending = FluxService.pendingAndOverflow(level);
        data.addProperty("pending", pending[0]);
        data.addProperty("overflow", pending[1]);
        if (chunk == null) {
            return data.toString();
        }
        Aura aura = FluxService.aura(chunk);
        data.addProperty(
                "status", aura == null ? "none" : aura.readable() ? "readable" : "unreadable");
        data.addProperty("flux", aura == null ? 0 : aura.flux());
        data.addProperty("steps", aura == null ? 0 : aura.steps());
        data.addProperty("taken", aura == null ? 0 : aura.taken());
        data.addProperty("saturated", HushService.saturated(level, chunk.getPos()));
        BlockPos seep = aura == null ? null : aura.seep();
        BlockPos centre = seep == null ? at : seep;
        if (seep != null) {
            data.addProperty("seep", seep.getX() + "," + seep.getY() + "," + seep.getZ());
        }
        JsonArray hushed = new JsonArray();
        for (BlockPos p :
                BlockPos.betweenClosed(
                        centre.offset(-radius, -radius, -radius),
                        centre.offset(radius, radius, radius))) {
            if (HushService.ticking(level, p) != null
                    && level.getBlockState(p).is(HushKeys.HUSHED)) {
                hushed.add(p.getX() + "," + p.getY() + "," + p.getZ());
            }
        }
        data.add("hushed", hushed);
        data.addProperty("hushedCount", hushed.size());
        List<Hollow> hollows = level.getEntitiesOfClass(Hollow.class, new AABB(centre).inflate(48));
        data.addProperty("hollows", hollows.size());
        data.addProperty(
                "hollowsTargeting",
                hollows.stream()
                        .filter(
                                h ->
                                        player.equals(
                                                java.util.Objects.requireNonNullElse(
                                                        h.getTarget(), h)))
                        .count());
        data.addProperty("hollowsHome", hollows.stream().filter(h -> h.isWithinHome()).count());
        return data.toString();
    }
}
