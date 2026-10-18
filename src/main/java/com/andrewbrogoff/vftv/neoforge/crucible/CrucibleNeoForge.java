package com.andrewbrogoff.vftv.neoforge.crucible;

import com.andrewbrogoff.vftv.api.crucible.CrucibleKeys;
import com.andrewbrogoff.vftv.api.crucible.CruciblePayload;
import com.andrewbrogoff.vftv.api.essentia.EssentiaSuction;
import com.andrewbrogoff.vftv.api.neoforge.crucible.CrucibleEvent;
import com.andrewbrogoff.vftv.api.neoforge.essentia.EssentiaCapabilities;
import com.andrewbrogoff.vftv.core.common.TickBudget;
import com.andrewbrogoff.vftv.core.crucible.CrucibleContent;
import com.andrewbrogoff.vftv.core.crucible.CrucibleHooks;
import com.andrewbrogoff.vftv.core.crucible.CrucibleRecipe;
import com.andrewbrogoff.vftv.core.crucible.CrucibleRecipes;
import com.andrewbrogoff.vftv.core.crucible.CrucibleService;
import com.andrewbrogoff.vftv.core.crucible.model.CrucibleTuning;
import com.andrewbrogoff.vftv.neoforge.common.NeoForgeRegistrar;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * The crucible feature's NeoForge wiring, called once from the {@code @Mod} constructor: the two
 * entity marks, the essentia capabilities, the recipe snapshot and sync, the tuning file, the
 * refresh queue and flush, and the sneak-drop. Each listener calls {@code core} in one line.
 */
public final class CrucibleNeoForge {
    private static final Identifier TUNING =
            Identifier.fromNamespaceAndPath("vftv", CrucibleService.TUNING_PATH);
    private static final EssentiaSuction NO_SUCTION = aspect -> 0;

    private CrucibleNeoForge() {}

    /** Subscribes the feature's listeners and registers its NeoForge-only types. */
    public static void register(IEventBus modBus, IEventBus gameBus, NeoForgeRegistrar registrar) {
        Supplier<AttachmentType<Boolean>> refused =
                mark(registrar, CrucibleKeys.CRUCIBLE_REFUSED_ATTACHMENT);
        Supplier<AttachmentType<Boolean>> dissolve =
                mark(registrar, CrucibleKeys.CRUCIBLE_DISSOLVE_ATTACHMENT);
        CrucibleService.install(new Hooks(refused, dissolve));
        modBus.addListener(
                RegisterCapabilitiesEvent.class,
                event -> {
                    event.registerBlockEntity(
                            EssentiaCapabilities.HANDLER,
                            CrucibleContent.blockEntity(),
                            (be, side) -> side == Direction.UP ? null : CrucibleEssentia.of(be));
                    event.registerBlockEntity(
                            EssentiaCapabilities.SUCTION,
                            CrucibleContent.blockEntity(),
                            (be, side) -> side == Direction.UP ? null : NO_SUCTION);
                });
        gameBus.addListener(
                ItemTossEvent.class,
                event -> CrucibleService.onToss(event.getPlayer(), event.getEntity()));
        gameBus.addListener(
                TagsUpdatedEvent.ServerDataLoad.class, event -> CrucibleService.queueAll());
        gameBus.addListener(
                LevelTickEvent.Post.class,
                event -> {
                    if (event.getLevel() instanceof ServerLevel level) {
                        try (var _ = TickBudget.enter("crucible")) {
                            CrucibleService.levelTick(level);
                        }
                    }
                });
        gameBus.addListener(
                AddServerReloadListenersEvent.class,
                event ->
                        event.addListener(
                                Identifier.fromNamespaceAndPath("vftv", "crucible_tuning"),
                                new TuningListener(
                                        ServerLifecycleHooks.getCurrentServer() == null)));
        gameBus.addListener(
                ServerStartingEvent.class,
                event -> CrucibleRecipes.onServerStarting(event.getServer().getRecipeManager()));
        gameBus.addListener(
                OnDatapackSyncEvent.class,
                event -> {
                    CrucibleRecipes.beforeSync(
                            event.getPlayerList().getServer().getRecipeManager());
                    event.sendRecipes(CrucibleRecipe.TYPE);
                });
        gameBus.addListener(
                ServerStoppedEvent.class,
                event -> {
                    CrucibleRecipes.clearServer();
                    CrucibleService.clear();
                });
    }

    private static Supplier<AttachmentType<Boolean>> mark(
            NeoForgeRegistrar registrar, Identifier id) {
        Supplier<AttachmentType<?>> type =
                registrar.register(
                        NeoForgeRegistries.Keys.ATTACHMENT_TYPES,
                        id.getPath(),
                        () ->
                                AttachmentType.builder(() -> false)
                                        .serialize(Codec.BOOL.fieldOf("value"), b -> b)
                                        .build());
        // Registered from an AttachmentType<Boolean> builder just above.
        @SuppressWarnings("unchecked")
        Supplier<AttachmentType<Boolean>> typed = () -> (AttachmentType<Boolean>) type.get();
        return typed;
    }

    /** The platform half of the crucible's seam. */
    private record Hooks(
            Supplier<AttachmentType<Boolean>> refused, Supplier<AttachmentType<Boolean>> dissolve)
            implements CrucibleHooks {
        @Override
        public void post(CruciblePayload payload) {
            NeoForge.EVENT_BUS.post(new CrucibleEvent(payload));
        }

        @Override
        public void crafted(Player player, ItemStack result, Container catalyst) {
            EventHooks.firePlayerCraftingEvent(player, result, catalyst);
        }

        @Override
        public boolean refused(ItemEntity entity) {
            return entity.getData(refused.get());
        }

        @Override
        public void markRefused(ItemEntity entity) {
            entity.setData(refused.get(), true);
        }

        @Override
        public boolean dissolve(ItemEntity entity) {
            return entity.getData(dissolve.get());
        }

        @Override
        public void markDissolve(ItemEntity entity) {
            entity.setData(dissolve.get(), true);
        }

        @Override
        public void invalidate(Level level, BlockPos pos) {
            level.invalidateCapabilities(pos);
        }
    }

    /** Reads {@code crucible.json} off-thread and applies it on the reload's own thread. */
    private static final class TuningListener
            extends SimplePreparableReloadListener<DataResult<CrucibleTuning>> {
        private final boolean opening;

        TuningListener(boolean opening) {
            this.opening = opening;
        }

        @Override
        protected DataResult<CrucibleTuning> prepare(
                ResourceManager manager, ProfilerFiller profiler) {
            Optional<Resource> resource = manager.getResource(TUNING);
            if (resource.isEmpty()) {
                return DataResult.success(CrucibleTuning.DEFAULTS);
            }
            try (InputStream in = resource.get().open()) {
                return CrucibleService.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                return DataResult.error(e::toString);
            }
        }

        @Override
        protected void apply(
                DataResult<CrucibleTuning> read, ResourceManager manager, ProfilerFiller profiler) {
            CrucibleService.applyTuning(read, !opening);
        }
    }
}
