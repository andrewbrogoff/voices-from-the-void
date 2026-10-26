package com.andrewbrogoff.vftv.core.infusion;

import com.andrewbrogoff.vftv.api.infusion.InfusionLimits;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;

/**
 * The {@code vftv:infusion} recipe display (infusion spec, "The recipe type"): the centre, the
 * components, the result (for enchanting, an enchanted book at the cap a run reaches) and the bowl
 * as the station. It carries no cost: costs come from the preview.
 *
 * @param centre the centre's slot display
 * @param components each component's slot display, in recipe order
 * @param result the result's slot display
 * @param craftingStation the Singing Bowl item
 */
public record InfusionDisplay(
        SlotDisplay centre,
        List<SlotDisplay> components,
        SlotDisplay result,
        SlotDisplay craftingStation)
        implements RecipeDisplay {
    /** The JSON codec. */
    public static final MapCodec<InfusionDisplay> MAP_CODEC =
            RecordCodecBuilder.mapCodec(
                    i ->
                            i.group(
                                            SlotDisplay.CODEC
                                                    .fieldOf("centre")
                                                    .forGetter(InfusionDisplay::centre),
                                            SlotDisplay.CODEC
                                                    .listOf(1, InfusionLimits.MAX_COMPONENTS)
                                                    .fieldOf("components")
                                                    .forGetter(InfusionDisplay::components),
                                            SlotDisplay.CODEC
                                                    .fieldOf("result")
                                                    .forGetter(InfusionDisplay::result),
                                            SlotDisplay.CODEC
                                                    .fieldOf("crafting_station")
                                                    .forGetter(InfusionDisplay::craftingStation))
                                    .apply(i, InfusionDisplay::new));

    /** The network codec, at most 16 components. */
    public static final StreamCodec<RegistryFriendlyByteBuf, InfusionDisplay> STREAM_CODEC =
            StreamCodec.composite(
                    SlotDisplay.STREAM_CODEC,
                    InfusionDisplay::centre,
                    SlotDisplay.STREAM_CODEC.apply(
                            ByteBufCodecs.list(InfusionLimits.MAX_COMPONENTS)),
                    InfusionDisplay::components,
                    SlotDisplay.STREAM_CODEC,
                    InfusionDisplay::result,
                    SlotDisplay.STREAM_CODEC,
                    InfusionDisplay::craftingStation,
                    InfusionDisplay::new);

    /** The registered type. */
    public static final RecipeDisplay.Type<InfusionDisplay> TYPE =
            new RecipeDisplay.Type<>(MAP_CODEC, STREAM_CODEC);

    /** Copies the list. */
    public InfusionDisplay {
        components = List.copyOf(components);
    }

    @Override
    public RecipeDisplay.Type<InfusionDisplay> type() {
        return TYPE;
    }
}
