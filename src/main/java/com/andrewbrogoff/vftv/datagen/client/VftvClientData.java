package com.andrewbrogoff.vftv.datagen.client;

import com.andrewbrogoff.vftv.core.Vftv;
import java.util.List;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * The client-asset providers: one model provider calling every feature's {@link
 * FeatureClientData#models}. Every feature is listed here once; a feature ticket edits only its own
 * {@code <F>ClientData}.
 */
public final class VftvClientData {
    /** Every feature's client data, leaves first. */
    static final List<FeatureClientData> FEATURES =
            List.of(
                    new FluxClientData(),
                    new AspectClientData(),
                    new WarpClientData(),
                    new ResearchClientData(),
                    new VisClientData(),
                    new ArcaneClientData(),
                    new CrucibleClientData(),
                    new EssentiaClientData(),
                    new InfusionClientData(),
                    new FocusClientData(),
                    new GolemClientData(),
                    new ArmorClientData(),
                    new HushClientData(),
                    new WorldgenClientData(),
                    new RoadClientData());

    private VftvClientData() {}

    /** Adds the client-asset providers to {@code event}. */
    public static void gather(GatherDataEvent event) {
        event.createProvider(
                output ->
                        new ModelProvider(output, Vftv.MOD_ID) {
                            @Override
                            protected void registerModels(
                                    BlockModelGenerators blocks, ItemModelGenerators items) {
                                FEATURES.forEach(f -> f.models(blocks, items));
                            }
                        });
    }
}
