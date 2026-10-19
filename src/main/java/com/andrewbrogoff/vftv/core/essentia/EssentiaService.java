package com.andrewbrogoff.vftv.core.essentia;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.api.essentia.EssentiaLimits;
import com.andrewbrogoff.vftv.api.essentia.EssentiaPayload;
import com.andrewbrogoff.vftv.api.essentia.SpillCause;
import com.andrewbrogoff.vftv.core.aspect.AspectSet;
import com.andrewbrogoff.vftv.core.aspect.KnowledgeService;
import com.andrewbrogoff.vftv.core.aspect.model.Knowledge;
import com.andrewbrogoff.vftv.core.common.Network;
import com.andrewbrogoff.vftv.core.common.TickBudget;
import com.andrewbrogoff.vftv.core.essentia.model.EssentiaTuning;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The essentia feature's server state: the installed seam, the tuning and its {@link
 * EssentiaRules}, the aspect index, each level's lines and coalesced sync set, and the spill post.
 * Server thread only, except the tuning swap, which the reload listener makes.
 */
public final class EssentiaService {
    /** The tuning file, {@code data/vftv/tuning/essentia.json}. */
    public static final String TUNING_PATH = "tuning/essentia.json";

    private static final Logger LOGGER = LogUtils.getLogger();

    private static @Nullable EssentiaSeam seam;
    private static @Nullable Network network;
    private static volatile EssentiaRules rules = EssentiaRules.DEFAULTS;
    private static volatile boolean tuningReloaded;
    private static volatile @Nullable EssentiaRules clientRules;
    private static @Nullable AspectIndex index;
    private static final IdentityHashMap<ServerLevel, EssentiaLines> LINES =
            new IdentityHashMap<>();
    private static final IdentityHashMap<ServerLevel, Set<BlockEntity>> DIRTY =
            new IdentityHashMap<>();
    private static boolean running;

    private EssentiaService() {}

    /** The registry's aspects in ascending id: the model's aspect indices. */
    public record AspectIndex(
            RegistryAccess access,
            List<Holder<Aspect>> holders,
            Map<ResourceKey<Aspect>, Integer> of) {
        /** The index of {@code key}, or -1 for a missing id. */
        public int index(@Nullable ResourceKey<Aspect> key) {
            return key == null ? -1 : of.getOrDefault(key, -1);
        }

        /** How many aspects. */
        public int size() {
            return holders.size();
        }
    }

    static void init(Network seamNetwork) {
        network = seamNetwork;
    }

    /** Installs the transfer seam; called once from the platform's wiring. */
    public static void install(EssentiaSeam platform) {
        seam = platform;
    }

    /** The installed seam. */
    public static EssentiaSeam seam() {
        return Objects.requireNonNull(seam, "EssentiaService.install has not run");
    }

    /** The rules in force. */
    public static EssentiaRules rules() {
        return rules;
    }

    /** The client's last received rules, or null before the first and after disconnect. */
    public static @Nullable EssentiaRules clientRules() {
        return clientRules;
    }

    /** Sets or drops the client's rules (the client feature, on arrival and at disconnect). */
    public static void setClientRules(@Nullable EssentiaRules received) {
        clientRules = received;
    }

    /** Reads a tuning file's text. */
    public static DataResult<EssentiaTuning> parse(String json) {
        try {
            return DataResult.success(EssentiaTuning.parse(json));
        } catch (IllegalArgumentException e) {
            return DataResult.error(e::getMessage);
        }
    }

    /**
     * Applies a tuning read. A valid one replaces the rules (and wakes every line at the next
     * tick). An invalid one throws while the world opens, which refuses it; on {@code /reload} it
     * logs an error and keeps the previous tuning.
     *
     * @throws IllegalStateException for an invalid read while the world opens
     */
    public static synchronized void applyTuning(
            DataResult<EssentiaTuning> read, boolean reloading) {
        read.ifSuccess(
                        t -> {
                            rules = new EssentiaRules(EssentiaRules.FORMAT, t);
                            tuningReloaded = true;
                        })
                .ifError(
                        error -> {
                            String message =
                                    "Invalid data/vftv/" + TUNING_PATH + ": " + error.message();
                            if (!reloading) {
                                throw new IllegalStateException(message);
                            }
                            LOGGER.error("{}; keeping the previous essentia tuning", message);
                        });
    }

    /** Sends the rules in force to one player, as on join and after {@code /reload}. */
    public static void sendTo(ServerPlayer player) {
        send(player, new EssentiaPayloads.Rules(rules));
    }

    static void send(ServerPlayer player, CustomPacketPayload payload) {
        Objects.requireNonNull(network, "EssentiaFeature.init has not run")
                .toPlayer(player, payload);
    }

    static void sendTracking(ServerLevel level, BlockPos pos, CustomPacketPayload payload) {
        Objects.requireNonNull(network, "EssentiaFeature.init has not run")
                .toTracking(level, ChunkPos.containing(pos), payload);
    }

    /** The aspect index of the live server's registries. */
    public static AspectIndex index(RegistryAccess access) {
        AspectIndex i = index;
        if (i == null || !i.access().equals(access)) {
            List<Holder<Aspect>> holders = new ArrayList<>();
            access.lookupOrThrow(AspectKeys.REGISTRY).listElements().forEach(holders::add);
            holders.sort(
                    Comparator.comparing(h -> h.unwrapKey().orElseThrow().identifier().toString()));
            Map<ResourceKey<Aspect>, Integer> of = new HashMap<>();
            for (int k = 0; k < holders.size(); k++) {
                of.put(holders.get(k).unwrapKey().orElseThrow(), k);
            }
            i = new AspectIndex(access, List.copyOf(holders), Map.copyOf(of));
            index = i;
        }
        return i;
    }

    /** Whether a label admits anything: absent, or naming an aspect the server registry holds. */
    public static boolean labelAdmits(@Nullable ResourceKey<Aspect> label) {
        AspectIndex i = index;
        return label == null || i == null || i.index(label) >= 0;
    }

    /** Whether a label admits anything, against a level's registries (client or server). */
    public static boolean labelAdmits(@Nullable ResourceKey<Aspect> label, Level level) {
        return label == null
                || level.registryAccess().lookupOrThrow(AspectKeys.REGISTRY).get(label).isPresent();
    }

    /** Whether the player has heard an aspect, so it may be named to them. */
    public static boolean knows(ServerPlayer player, ResourceKey<Aspect> aspect) {
        Knowledge knowledge = KnowledgeService.get(player);
        return knowledge != null && knowledge.known().contains(AspectSet.text(aspect));
    }

    /** A level's lines. */
    public static EssentiaLines lines(ServerLevel level) {
        return LINES.computeIfAbsent(level, EssentiaLines::new);
    }

    /** Something at an endpoint changed (a crucible dissolved or crafted): wakes its lines. */
    public static void wakeAt(ServerLevel level, BlockPos pos) {
        lines(level).wakeAt(pos);
    }

    /** Marks a block entity for the coalesced update after this tick. */
    public static void markDirty(ServerLevel level, BlockEntity be) {
        DIRTY.computeIfAbsent(level, l -> new LinkedHashSet<>()).add(be);
    }

    /**
     * Posts one {@code EssentiaEvent(SPILLED)} after the settled state. A throwing listener is
     * logged; the spill stands.
     */
    static void spill(
            ServerLevel level,
            BlockPos pos,
            ResourceKey<Aspect> aspect,
            long amount,
            SpillCause cause) {
        int bounded = (int) Math.min(amount, EssentiaLimits.MAX_ESSENTIA_SPILL);
        TreeMap<ResourceKey<Aspect>, Integer> map = new TreeMap<>();
        map.put(aspect, bounded);
        try {
            seam().post(new EssentiaPayload.Spilled(level, pos, map, cause));
        } catch (RuntimeException e) {
            LOGGER.error("An essentia spill listener threw at {}", pos, e);
        }
    }

    /** The server started: logs the supported-size policy once if the registry is too large. */
    public static void onServerStarted(MinecraftServer server) {
        running = true;
        AspectIndex i = index(server.registryAccess());
        if (i.size() > EssentiaLimits.MAX_HANDLER_SLOTS) {
            LOGGER.warn(
                    "The aspect registry holds {} entries, over {}: every crucible is a skipped"
                            + " essentia endpoint",
                    i.size(),
                    EssentiaLimits.MAX_HANDLER_SLOTS);
        }
    }

    /** End of each server tick: lines, reservations, then the coalesced block-entity updates. */
    public static void onServerTickEnd(MinecraftServer server) {
        if (!running) {
            return;
        }
        try (var _ = TickBudget.enter("essentia")) {
            boolean wake = tuningReloaded;
            tuningReloaded = false;
            for (ServerLevel level : List.copyOf(LINES.keySet())) {
                EssentiaLines lines = LINES.get(level);
                if (lines != null) {
                    if (wake) {
                        lines.wakeAll();
                    }
                    lines.tick();
                }
            }
            DIRTY.forEach(
                    (level, set) ->
                            set.forEach(
                                    be -> {
                                        if (!be.isRemoved()
                                                && be.equals(
                                                        level.getBlockEntity(be.getBlockPos()))) {
                                            level.sendBlockUpdated(
                                                    be.getBlockPos(),
                                                    be.getBlockState(),
                                                    be.getBlockState(),
                                                    3);
                                        }
                                    }));
            DIRTY.clear();
        }
    }

    /** A tag reload: every level's lines freeze and refresh. */
    public static void onTagsReloaded() {
        LINES.values().forEach(EssentiaLines::tagsReloaded);
    }

    /** A level unloaded: its lines and queues go with it. */
    public static void onLevelUnload(ServerLevel level) {
        LINES.remove(level);
        DIRTY.remove(level);
    }

    /** The server stopped: every per-server state resets. */
    public static void onServerStopped() {
        running = false;
        LINES.clear();
        DIRTY.clear();
        index = null;
        rules = EssentiaRules.DEFAULTS;
        EssentiaStatus.clear();
    }
}
