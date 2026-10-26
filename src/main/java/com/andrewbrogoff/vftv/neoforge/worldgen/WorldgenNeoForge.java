package com.andrewbrogoff.vftv.neoforge.worldgen;

import com.andrewbrogoff.vftv.core.common.RawRecordCodec.Retained;
import com.andrewbrogoff.vftv.core.research.ResearchItems;
import com.andrewbrogoff.vftv.core.worldgen.Answerer;
import com.andrewbrogoff.vftv.core.worldgen.Cantor;
import com.andrewbrogoff.vftv.core.worldgen.ChoirSitePlaceQuery;
import com.andrewbrogoff.vftv.core.worldgen.ReliquaryBlockEntity;
import com.andrewbrogoff.vftv.core.worldgen.ReliquaryService;
import com.andrewbrogoff.vftv.core.worldgen.WorldgenCodecs;
import com.andrewbrogoff.vftv.core.worldgen.model.ChoirClaims;
import com.andrewbrogoff.vftv.core.worldgen.WorldgenContent;
import com.andrewbrogoff.vftv.core.worldgen.WorldgenService;
import com.andrewbrogoff.vftv.core.worldgen.model.WorldgenTuning;
import com.andrewbrogoff.vftv.neoforge.common.NeoForgeRegistrar;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.Identifier;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.InterModComms;
import net.neoforged.fml.event.lifecycle.InterModEnqueueEvent;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * The worldgen feature's NeoForge wiring, called once from the {@code @Mod} constructor: the
 * Answerers' attributes, the tuning file, the site kinds' load validation, the place query's tag
 * resolution and the Reliquary's per-player memory. Each listener calls {@code core} in one line.
 */
public final class WorldgenNeoForge {
    private static final Identifier TUNING =
            Identifier.fromNamespaceAndPath("vftv", WorldgenService.TUNING_PATH);
    private static final Identifier VALIDATION =
            Identifier.fromNamespaceAndPath("vftv", "worldgen_validation");

    private WorldgenNeoForge() {}

    /** Subscribes the feature's listeners and registers its NeoForge-only types. */
    public static void register(IEventBus modBus, IEventBus gameBus, NeoForgeRegistrar registrar) {
        modBus.addListener(
                EntityAttributeCreationEvent.class,
                event -> {
                    event.put(WorldgenContent.answerer(), Answerer.createAttributes().build());
                    event.put(WorldgenContent.cantor(), Cantor.createCantorAttributes().build());
                });
        gameBus.addListener(
                AddServerReloadListenersEvent.class,
                event -> {
                    boolean opening = ServerLifecycleHooks.getCurrentServer() == null;
                    event.addListener(
                            Identifier.fromNamespaceAndPath("vftv", "worldgen_tuning"),
                            new TuningListener(opening));
                    if (opening) {
                        event.addListener(VALIDATION, validation(event.getServerResources()));
                    }
                });
        gameBus.addListener(
                TagsUpdatedEvent.ServerDataLoad.class,
                event -> ChoirSitePlaceQuery.resolve(event.getRegistries()));
        gameBus.addListener(
                ServerStartedEvent.class,
                event -> ChoirSitePlaceQuery.resolve(event.getServer().registryAccess()));
        gameBus.addListener(
                PlayerEvent.PlayerLoggedInEvent.class,
                event -> {
                    if (event.getEntity() instanceof ServerPlayer player) {
                        ReliquaryService.login(player);
                    }
                });
        gameBus.addListener(
                PlayerEvent.PlayerLoggedOutEvent.class,
                event -> ReliquaryService.forget(event.getEntity()));
        modBus.addListener(
                InterModEnqueueEvent.class,
                event ->
                        InterModComms.sendTo(
                                "mc_agent_harness",
                                "dump_server",
                                () ->
                                        Map.entry(
                                                "vftv:worldgen",
                                                (Function<String, String>) WorldgenNeoForge::dump)));
        gameBus.addListener(
                ServerStoppedEvent.class,
                event -> {
                    ReliquaryService.clear();
                    ChoirSitePlaceQuery.clear();
                    WorldgenService.onServerStopped();
                });
    }

    /**
     * The harness's {@code vftv:worldgen} dump, for the first player: their claims, the Choir pages
     * they hold, whether they stand in a Choir site, and the Answerers within 48 blocks; with {@code
     * pos}, that block's Reliquary record.
     */
    private static String dump(String args) {
        JsonObject data = new JsonObject();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.getPlayerList().getPlayers().isEmpty()) {
            return data.toString();
        }
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        JsonArray claims = new JsonArray();
        boolean readable = ReliquaryService.claims().get(player) instanceof Retained.Read<?>;
        if (ReliquaryService.claims().get(player) instanceof Retained.Read<ChoirClaims> r) {
            r.value()
                    .sites()
                    .forEach(
                            site -> {
                                JsonObject o = new JsonObject();
                                o.addProperty("dim", site.dim());
                                o.addProperty("pos", BlockPos.of(site.pos()).toShortString());
                                JsonArray pages = new JsonArray();
                                site.pages().forEach(pages::add);
                                o.add("pages", pages);
                                claims.add(o);
                            });
        }
        data.addProperty("claimsReadable", readable);
        data.add("claims", claims);
        int pages = 0;
        for (ItemStack stack : player.getInventory()) {
            if (stack.is(ResearchItems.choirPage())) {
                pages += stack.getCount();
            }
        }
        data.addProperty("pagesHeld", pages);
        ServerLevel level = player.level();
        data.addProperty("choirSite", ChoirSitePlaceQuery.INSTANCE.test(level, player.blockPosition()));
        List<Answerer> near =
                level.getEntitiesOfClass(Answerer.class, player.getBoundingBox().inflate(48));
        data.addProperty("answerers", near.size());
        data.addProperty("cantors", near.stream().filter(Answerer::isCantor).count());
        data.addProperty("chanting", near.stream().anyMatch(Answerer::chanting));
        data.addProperty("targeting", near.stream().filter(a -> java.util.Objects.equals(player, a.getTarget())).count());
        JsonObject request = JsonParser.parseString(args).getAsJsonObject();
        if (request.has("pos")) {
            JsonArray p = request.getAsJsonArray("pos");
            BlockPos pos = new BlockPos(p.get(0).getAsInt(), p.get(1).getAsInt(), p.get(2).getAsInt());
            if (level.getBlockEntity(pos) instanceof ReliquaryBlockEntity be) {
                data.addProperty(
                        "reliquary",
                        be.record() instanceof Retained.Read<WorldgenCodecs.Reliquary> rec
                                ? rec.value().site().identifier().toString()
                                : "unreadable");
            }
        }
        return data.toString();
    }

    private static ResourceManagerReloadListener validation(ReloadableServerResources resources) {
        return manager -> WorldgenService.validateAtOpen(resources.getRegistryLookup());
    }

    /** Reads {@code worldgen.json} off-thread and applies it on the reload's own thread. */
    private static final class TuningListener
            extends SimplePreparableReloadListener<DataResult<WorldgenTuning>> {
        private final boolean opening;

        TuningListener(boolean opening) {
            this.opening = opening;
        }

        @Override
        protected DataResult<WorldgenTuning> prepare(
                ResourceManager manager, ProfilerFiller profiler) {
            Optional<Resource> resource = manager.getResource(TUNING);
            if (resource.isEmpty()) {
                return DataResult.success(WorldgenTuning.DEFAULTS);
            }
            try (InputStream in = resource.get().open()) {
                return WorldgenService.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                return DataResult.error(e::toString);
            }
        }

        @Override
        protected void apply(
                DataResult<WorldgenTuning> read, ResourceManager manager, ProfilerFiller profiler) {
            WorldgenService.applyTuning(read, !opening);
        }
    }
}
