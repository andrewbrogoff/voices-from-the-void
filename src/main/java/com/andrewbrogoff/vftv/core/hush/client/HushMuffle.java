package com.andrewbrogoff.vftv.core.hush.client;

import com.andrewbrogoff.vftv.core.hush.HushPlaceQuery;
import com.andrewbrogoff.vftv.core.hush.HushStatuses;
import com.google.gson.JsonObject;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ChunkPos;

/**
 * Muffling (Hush spec, "Muffling"): once per client tick, while the place query holds for the local
 * player and {@code hush.muffle} is on, seven categories play at gain 0.5; leaving, turning the
 * option off, a level change and disconnect restore 1.0, and an engine reset reapplies it. The
 * client tick also prunes the chunk statuses to the render square when the player's chunk changes.
 */
public final class HushMuffle {
    /** The gain inside the Hush. */
    public static final float GAIN = 0.5F;

    /** The muffled categories: never master, music, voice or UI. */
    public static final List<SoundSource> CATEGORIES =
            List.of(
                    SoundSource.BLOCKS,
                    SoundSource.HOSTILE,
                    SoundSource.NEUTRAL,
                    SoundSource.PLAYERS,
                    SoundSource.AMBIENT,
                    SoundSource.WEATHER,
                    SoundSource.RECORDS);

    private static boolean applied;
    private static long lastChunk = Long.MIN_VALUE;

    private HushMuffle() {}

    /** The client tick. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        boolean want =
                HushHud.config().muffle()
                        && player != null
                        && mc.level != null
                        && HushPlaceQuery.inHush(mc.level, player.blockPosition());
        if (want != applied) {
            apply(mc, want);
        }
        if (player != null) {
            long chunk = ChunkPos.pack(player.blockPosition());
            if (chunk != lastChunk) {
                lastChunk = chunk;
                HushStatuses.prune(
                        ChunkPos.getX(chunk),
                        ChunkPos.getZ(chunk),
                        mc.options.getEffectiveRenderDistance() + 1);
            }
        }
    }

    private static void apply(Minecraft mc, boolean muffled) {
        applied = muffled;
        for (SoundSource source : CATEGORIES) {
            mc.getSoundManager().updateCategoryVolume(source, muffled ? GAIN : 1.0F);
        }
    }

    /** A level change or disconnect: gains back to 1.0, statuses and the reading forgotten. */
    public static void reset() {
        if (applied) {
            apply(Minecraft.getInstance(), false);
        }
        lastChunk = Long.MIN_VALUE;
        HushStatuses.clear();
        HushHud.invalidate();
    }

    /** The sound engine reloaded, clearing every gain: the next tick reapplies the muffle. */
    public static void engineReset() {
        applied = false;
    }

    static void dump(JsonObject data) {
        data.addProperty("muffled", applied);
        JsonObject gains = new JsonObject();
        Minecraft mc = Minecraft.getInstance();
        for (SoundSource s : CATEGORIES) {
            gains.addProperty(s.getName(), applied ? GAIN : 1.0F);
        }
        data.add("gains", gains);
        data.addProperty(
                "inHush",
                mc.player != null
                        && mc.level != null
                        && HushPlaceQuery.inHush(mc.level, mc.player.blockPosition()));
    }
}
