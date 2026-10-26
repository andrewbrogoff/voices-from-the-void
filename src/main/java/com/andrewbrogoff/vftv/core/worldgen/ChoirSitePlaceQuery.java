package com.andrewbrogoff.vftv.core.worldgen;

import com.andrewbrogoff.vftv.api.worldgen.WorldgenKeys;
import com.andrewbrogoff.vftv.api.worldgen.WorldgenLimits;
import com.andrewbrogoff.vftv.core.warp.VoidPlaceQuery;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.slf4j.Logger;

/**
 * {@code ChoirSitePlaceQuery} (worldgen spec, "The Choir-site place query"): true iff the position's
 * chunk is loaded and holds a reference to a start of some structure in the resolved {@code
 * #vftv:choir_sites}. It reads that the reference set is non-empty: one {@code getChunkNow} and at
 * most {@value WorldgenLimits#MAX_SITE_STRUCTURES} map lookups, allocating nothing.
 */
public final class ChoirSitePlaceQuery implements VoidPlaceQuery {
    /** The one instance, registered with {@code WarpService.registerPlaceQuery}. */
    public static final ChoirSitePlaceQuery INSTANCE = new ChoirSitePlaceQuery();

    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile Structure[] sites = new Structure[0];

    private ChoirSitePlaceQuery() {}

    @Override
    public boolean test(ServerLevel level, BlockPos pos) {
        ChunkAccess chunk =
                level.getChunkSource()
                        .getChunkNow(
                                SectionPos.blockToSectionCoord(pos.getX()),
                                SectionPos.blockToSectionCoord(pos.getZ()));
        return chunk != null && referenced(chunk, sites);
    }

    /** Whether {@code chunk} references a start of any of {@code structures}. */
    static boolean referenced(ChunkAccess chunk, Structure[] structures) {
        for (Structure s : structures) {
            if (!chunk.getReferencesForStructure(s).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves the tag on the server: at start and on a server data load, never on a client's
     * notification. Keeps the first {@value WorldgenLimits#MAX_SITE_STRUCTURES} by id, warning once
     * if it holds more.
     */
    public static void resolve(HolderLookup.Provider access) {
        List<Holder<Structure>> members = new ArrayList<>();
        access.lookupOrThrow(Registries.STRUCTURE)
                .get(WorldgenKeys.CHOIR_SITES)
                .ifPresent(set -> set.forEach(members::add));
        members.sort(
                Comparator.comparing(
                        h -> h.unwrapKey().map(k -> k.identifier().toString()).orElse("")));
        if (members.size() > WorldgenLimits.MAX_SITE_STRUCTURES) {
            LOGGER.warn(
                    "#vftv:choir_sites holds {} structures; only the first {} by id are read",
                    members.size(),
                    WorldgenLimits.MAX_SITE_STRUCTURES);
        }
        sites =
                members.stream()
                        .limit(WorldgenLimits.MAX_SITE_STRUCTURES)
                        .map(Holder::value)
                        .toArray(Structure[]::new);
    }

    /** The resolved structures' count, for diagnostics and tests. */
    public static int resolved() {
        return sites.length;
    }

    /** Forgets the resolved set when the server stops. */
    public static void clear() {
        sites = new Structure[0];
    }
}
