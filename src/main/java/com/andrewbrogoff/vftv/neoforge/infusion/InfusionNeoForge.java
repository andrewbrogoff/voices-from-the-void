package com.andrewbrogoff.vftv.neoforge.infusion;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.api.essentia.EssentiaKeys;
import com.andrewbrogoff.vftv.api.essentia.EssentiaLimits;
import com.andrewbrogoff.vftv.api.flux.FluxRules;
import com.andrewbrogoff.vftv.api.infusion.InfusionLimits;
import com.andrewbrogoff.vftv.api.infusion.InfusionPayload;
import com.andrewbrogoff.vftv.api.neoforge.essentia.EssentiaCapabilities;
import com.andrewbrogoff.vftv.api.neoforge.essentia.EssentiaResource;
import com.andrewbrogoff.vftv.api.neoforge.infusion.InfusionEvent;
import com.andrewbrogoff.vftv.core.common.TickBudget;
import com.andrewbrogoff.vftv.core.infusion.InfusionAltars;
import com.andrewbrogoff.vftv.core.infusion.InfusionContent;
import com.andrewbrogoff.vftv.core.infusion.InfusionHooks;
import com.andrewbrogoff.vftv.core.infusion.InfusionRecipe;
import com.andrewbrogoff.vftv.core.infusion.InfusionRecipes;
import com.andrewbrogoff.vftv.core.infusion.model.InfusionTuning;
import com.andrewbrogoff.vftv.neoforge.common.NeoForgeRegistrar;
import com.mojang.serialization.DataResult;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * The infusion feature's NeoForge wiring, called once from the {@code @Mod} constructor: the item
 * capability on both blocks, the recipe snapshot and sync, the rules sync, the tuning file, the
 * level tick, and the platform half of the seam (the event post, the crafted event, the transaction
 * lifecycle and the draw from the box's vessels). Each listener calls {@code core} in one line.
 */
public final class InfusionNeoForge {
    private static final Identifier TUNING =
            Identifier.fromNamespaceAndPath("vftv", InfusionAltars.TUNING_PATH);

    private InfusionNeoForge() {}

    /** Subscribes the feature's listeners and registers its NeoForge-only types. */
    public static void register(IEventBus modBus, IEventBus gameBus, NeoForgeRegistrar registrar) {
        InfusionAltars.install(new Hooks());
        modBus.addListener(
                RegisterCapabilitiesEvent.class,
                event -> {
                    event.registerBlockEntity(
                            Capabilities.Item.BLOCK,
                            InfusionContent.bowlType(),
                            (be, side) -> AltarItemHandler.of(be));
                    event.registerBlockEntity(
                            Capabilities.Item.BLOCK,
                            InfusionContent.pedestalType(),
                            (be, side) -> AltarItemHandler.of(be));
                });
        gameBus.addListener(
                LevelTickEvent.Post.class,
                event -> {
                    if (event.getLevel() instanceof ServerLevel level) {
                        try (var _ = TickBudget.enter("infusion")) {
                            InfusionAltars.levelTick(level);
                        }
                    }
                });
        gameBus.addListener(
                AddServerReloadListenersEvent.class,
                event ->
                        event.addListener(
                                Identifier.fromNamespaceAndPath("vftv", "infusion_tuning"),
                                new TuningListener(
                                        ServerLifecycleHooks.getCurrentServer() != null)));
        gameBus.addListener(
                ServerStartingEvent.class,
                event -> InfusionRecipes.onServerStarting(event.getServer().getRecipeManager()));
        gameBus.addListener(
                OnDatapackSyncEvent.class,
                event -> {
                    InfusionRecipes.beforeSync(
                            event.getPlayerList().getServer().getRecipeManager());
                    event.sendRecipes(InfusionRecipe.TYPE);
                    event.getRelevantPlayers().forEach(InfusionAltars::sendRules);
                });
        gameBus.addListener(
                PlayerEvent.PlayerLoggedOutEvent.class,
                event -> {
                    if (event.getEntity() instanceof ServerPlayer player) {
                        InfusionAltars.onLogout(player);
                    }
                });
        gameBus.addListener(
                LevelEvent.Unload.class,
                event -> {
                    if (event.getLevel() instanceof ServerLevel level) {
                        InfusionAltars.onLevelUnload(level);
                    }
                });
        gameBus.addListener(
                ServerStoppedEvent.class,
                event -> {
                    InfusionRecipes.clearServer();
                    InfusionAltars.clear();
                });
    }

    /** The platform half of the infusion seam. */
    private static final class Hooks implements InfusionHooks {
        @Override
        public void post(InfusionPayload payload, @Nullable FluxRules rules) {
            NeoForge.EVENT_BUS.post(new InfusionEvent(payload, rules));
        }

        @Override
        public void crafted(Player player, ItemStack result, Container centre) {
            EventHooks.firePlayerCraftingEvent(player, result, centre);
        }

        @Override
        public boolean idle() {
            return Transaction.getLifecycle() == Transaction.Lifecycle.NONE;
        }

        @Override
        public long[] heldNow(ServerLevel level, BlockPos bowl, List<ResourceKey<Aspect>> aspects) {
            long[] held = new long[aspects.size()];
            for (ResourceHandler<EssentiaResource> source : sources(level, bowl)) {
                for (int slot = 0; slot < source.size(); slot++) {
                    Holder<Aspect> aspect = source.getResource(slot).aspect();
                    int i =
                            aspect == null
                                    ? -1
                                    : aspect.unwrapKey().map(aspects::indexOf).orElse(-1);
                    if (i >= 0) {
                        long amount = Math.max(0, source.getAmountAsLong(slot));
                        held[i] =
                                held[i] > Long.MAX_VALUE - amount
                                        ? Long.MAX_VALUE
                                        : held[i] + amount;
                    }
                }
            }
            return held;
        }

        @Override
        public void draw(
                ServerLevel level,
                BlockPos bowl,
                List<ResourceKey<Aspect>> aspects,
                int[] need,
                Ledger ledger) {
            List<ResourceHandler<EssentiaResource>> sources = sources(level, bowl);
            if (sources.isEmpty()) {
                return;
            }
            HolderLookup.RegistryLookup<Aspect> registry =
                    level.registryAccess().lookupOrThrow(AspectKeys.REGISTRY);
            LedgerJournal journal = new LedgerJournal(ledger);
            try (Transaction root = Transaction.openRoot()) {
                for (int i = 0; i < aspects.size(); i++) {
                    Optional<Holder.Reference<Aspect>> aspect = registry.get(aspects.get(i));
                    if (need[i] <= 0 || aspect.isEmpty()) {
                        continue;
                    }
                    EssentiaResource resource = new EssentiaResource(aspect.get());
                    int left = need[i];
                    for (ResourceHandler<EssentiaResource> source : sources) {
                        if (left == 0) {
                            break;
                        }
                        try (Transaction nested = Transaction.open(root)) {
                            int e = Math.min(left, source.extract(resource, left, nested));
                            if (e > 0) {
                                journal.updateSnapshots(nested);
                                ledger.credit(i, e);
                                left -= e;
                            }
                            nested.commit();
                        }
                    }
                    if (left < need[i]) {
                        break; // the first aspect that yields anything ends the search
                    }
                }
                root.commit();
            }
        }

        @Override
        public void invalidate(Level level, BlockPos pos) {
            level.invalidateCapabilities(pos);
        }
    }

    /**
     * The box's sources (infusion spec, "Drawing essentia"): positions in the endpoint tag whose
     * block exposes the essentia handler on the null side, in ascending signed {@code asLong}, the
     * first {@value InfusionLimits#MAX_SOURCES}; a handler over 128 slots is skipped.
     */
    static List<ResourceHandler<EssentiaResource>> sources(ServerLevel level, BlockPos bowl) {
        int r = InfusionLimits.BOX_RADIUS;
        List<BlockPos> box = new ArrayList<>();
        for (BlockPos p :
                BlockPos.betweenClosed(
                        bowl.offset(-r, -InfusionLimits.BOX_BELOW, -r),
                        bowl.offset(r, InfusionLimits.BOX_ABOVE, r))) {
            box.add(p.immutable());
        }
        box.sort((a, b) -> Long.compare(a.asLong(), b.asLong()));
        List<ResourceHandler<EssentiaResource>> sources = new ArrayList<>();
        for (BlockPos p : box) {
            if (sources.size() >= InfusionLimits.MAX_SOURCES) {
                break;
            }
            if (!level.getBlockState(p).is(EssentiaKeys.ESSENTIA_ENDPOINTS)) {
                continue;
            }
            ResourceHandler<EssentiaResource> handler =
                    level.getCapability(EssentiaCapabilities.HANDLER, p, null);
            if (handler != null && handler.size() <= EssentiaLimits.MAX_HANDLER_SLOTS) {
                sources.add(handler);
            }
        }
        return sources;
    }

    /** The run's ledger, journalled in the draw's root transaction. */
    private static final class LedgerJournal extends SnapshotJournal<int[]> {
        private final InfusionHooks.Ledger ledger;

        LedgerJournal(InfusionHooks.Ledger ledger) {
            this.ledger = ledger;
        }

        @Override
        protected int[] createSnapshot() {
            return ledger.snapshot();
        }

        @Override
        protected void revertToSnapshot(int[] snapshot) {
            ledger.restore(snapshot);
        }
    }

    /** Reads {@code infusion.json} off-thread and applies it on the reload's own thread. */
    private static final class TuningListener
            extends SimplePreparableReloadListener<DataResult<InfusionTuning>> {
        private final boolean reloading;

        TuningListener(boolean reloading) {
            this.reloading = reloading;
        }

        @Override
        protected DataResult<InfusionTuning> prepare(
                ResourceManager manager, ProfilerFiller profiler) {
            Optional<Resource> resource = manager.getResource(TUNING);
            if (resource.isEmpty()) {
                return DataResult.success(InfusionTuning.DEFAULTS);
            }
            try (InputStream in = resource.get().open()) {
                return InfusionAltars.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                return DataResult.error(e::toString);
            }
        }

        @Override
        protected void apply(
                DataResult<InfusionTuning> read, ResourceManager manager, ProfilerFiller profiler) {
            InfusionAltars.applyTuning(read, reloading);
        }
    }
}
