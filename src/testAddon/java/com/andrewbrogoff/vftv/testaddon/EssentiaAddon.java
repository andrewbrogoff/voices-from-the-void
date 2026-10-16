package com.andrewbrogoff.vftv.testaddon;

import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.api.essentia.EssentiaKeys;
import com.andrewbrogoff.vftv.api.essentia.EssentiaLimits;
import com.andrewbrogoff.vftv.api.essentia.EssentiaPayload;
import com.andrewbrogoff.vftv.api.essentia.EssentiaSuction;
import com.andrewbrogoff.vftv.api.neoforge.essentia.EssentiaCapabilities;
import com.andrewbrogoff.vftv.api.neoforge.essentia.EssentiaEvent;
import com.andrewbrogoff.vftv.api.neoforge.essentia.EssentiaResource;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The example addon's use of the essentia API: a sink block exposing {@link EssentiaSuction},
 * essentia resources built through their Stable constructor, and a spill listener.
 */
final class EssentiaAddon {
    /** Every Stable constant of the feature, referenced so a rename breaks this build. */
    static final List<Object> CONSTANTS =
            List.of(
                    EssentiaKeys.ESSENTIA_ENDPOINTS,
                    EssentiaKeys.ALEMBIC_BASES,
                    EssentiaKeys.HANDLER_CAPABILITY,
                    EssentiaKeys.SUCTION_CAPABILITY,
                    EssentiaKeys.JAR_CONTENTS_COMPONENT,
                    EssentiaKeys.LABEL_COMPONENT,
                    EssentiaCapabilities.HANDLER,
                    EssentiaCapabilities.SUCTION,
                    EssentiaLimits.MAX_SUCTION,
                    EssentiaLimits.STORAGE_MAX,
                    EssentiaLimits.MAX_MEMBERS,
                    EssentiaLimits.MAX_ENDPOINTS,
                    EssentiaLimits.MAX_HANDLER_SLOTS,
                    EssentiaLimits.MAX_TRIES_PER_PULSE,
                    EssentiaLimits.MAX_PULSES_PER_TICK,
                    EssentiaLimits.MAX_SCHEDULER_POPS_PER_TICK,
                    EssentiaLimits.MAX_TOPOLOGY_WORK_PER_TICK,
                    EssentiaLimits.MAX_FACE_REFRESHES_PER_TICK,
                    EssentiaLimits.MAX_JAR_AMOUNT,
                    EssentiaLimits.MAX_ESSENTIA_SPILL,
                    EssentiaLimits.MAX_LIVE_MOTES,
                    EssentiaLimits.MAX_FLOW_PATH,
                    EssentiaLimits.MAX_ID_CHARS);

    /**
     * A consumer that pulls every aspect at the bottom of the consumer band and holds nothing (the
     * default {@link EssentiaSuction#hold}), so any store gives up essentia to it.
     */
    private static final EssentiaSuction SINK = aspect -> EssentiaLimits.STORAGE_MAX + 1;

    private static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(VftvAddon.MOD_ID);

    private static final DeferredBlock<Block> SUCTION_BLOCK =
            BLOCKS.registerSimpleBlock("essentia_sink");

    private EssentiaAddon() {}

    /** Subscribes a listener per event and registers the feature's example content. */
    static void register(IEventBus modBus, IEventBus gameBus) {
        BLOCKS.register(modBus);
        modBus.addListener(RegisterCapabilitiesEvent.class, EssentiaAddon::registerCapabilities);
        gameBus.addListener(EssentiaEvent.class, e -> read(e.payload()));
        gameBus.addListener(
                ServerStartedEvent.class, e -> constructResources(e.getServer().registryAccess()));
    }

    /** Exposes the sink's suction on every face and the null side. */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlock(
                EssentiaCapabilities.SUCTION,
                (level, pos, state, blockEntity, side) -> SINK,
                SUCTION_BLOCK.get());
    }

    /**
     * Builds essentia resources through the Stable constructor, from a registry holder as an addon
     * would; the empty value is the constant.
     */
    private static void constructResources(HolderLookup.Provider registries) {
        EssentiaResource empty = EssentiaResource.EMPTY;
        List<EssentiaResource> ordo =
                registries.get(AspectKeys.ORDO).map(EssentiaResource::new).stream().toList();
        VftvAddon.consume(empty, empty.isEmpty(), new EssentiaResource(null), ordo);
    }

    /** Reads every accessor, as an addon would. */
    private static void read(EssentiaPayload payload) {
        switch (payload) {
            case EssentiaPayload.Spilled s ->
                    VftvAddon.consume(s.level(), s.pos(), s.aspects(), s.cause());
        }
    }
}
