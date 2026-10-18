package com.andrewbrogoff.vftv.core.crucible;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;

/**
 * The {@code vftv:crucible} recipe display (crucible spec, "The recipe type"): the catalyst, the
 * result and the crucible as the station. It carries no cost: costs come from the preview.
 *
 * @param catalyst the catalyst's slot display
 * @param result the result's slot display
 * @param craftingStation the crucible item
 */
public record CrucibleDisplay(SlotDisplay catalyst, SlotDisplay result, SlotDisplay craftingStation)
        implements RecipeDisplay {
    /** The JSON codec. */
    public static final MapCodec<CrucibleDisplay> MAP_CODEC =
            RecordCodecBuilder.mapCodec(
                    i ->
                            i.group(
                                            SlotDisplay.CODEC
                                                    .fieldOf("catalyst")
                                                    .forGetter(CrucibleDisplay::catalyst),
                                            SlotDisplay.CODEC
                                                    .fieldOf("result")
                                                    .forGetter(CrucibleDisplay::result),
                                            SlotDisplay.CODEC
                                                    .fieldOf("crafting_station")
                                                    .forGetter(CrucibleDisplay::craftingStation))
                                    .apply(i, CrucibleDisplay::new));

    /** The network codec. */
    public static final StreamCodec<RegistryFriendlyByteBuf, CrucibleDisplay> STREAM_CODEC =
            StreamCodec.composite(
                    SlotDisplay.STREAM_CODEC,
                    CrucibleDisplay::catalyst,
                    SlotDisplay.STREAM_CODEC,
                    CrucibleDisplay::result,
                    SlotDisplay.STREAM_CODEC,
                    CrucibleDisplay::craftingStation,
                    CrucibleDisplay::new);

    /** The registered type. */
    public static final RecipeDisplay.Type<CrucibleDisplay> TYPE =
            new RecipeDisplay.Type<>(MAP_CODEC, STREAM_CODEC);

    @Override
    public RecipeDisplay.Type<CrucibleDisplay> type() {
        return TYPE;
    }
}
