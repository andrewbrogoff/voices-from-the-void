package com.andrewbrogoff.vftv.api.infusion;

import com.andrewbrogoff.vftv.api.Internal;
import com.andrewbrogoff.vftv.api.vis.VisKind;
import java.util.List;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;

/**
 * A snapshot of the committed {@code READY} assessment of an altar: every field and flag of the
 * readout, frozen at the start. See the infusion spec, "The readout" and "Stability".
 *
 * @param recipe the recipe's key
 * @param kind the recipe's vis kind
 * @param output the frozen output for both result forms: an item result (with {@code transmute}
 *     applied), or the enchanted centre copy; copied on every access
 * @param instability {@code R}, the recipe's instability in percentage points, 0 to {@link
 *     InfusionLimits#MAX_INSTABILITY}
 * @param warpPenalty {@code Wp}, the starter's warp penalty in percentage points, 0 to {@link
 *     InfusionLimits#MAX_WARP_PENALTY}
 * @param lonePedestals {@code L}, pedestals whose mirror isn't a pedestal
 * @param loneItems {@code I}, mirrored pedestal pairs in which exactly one holds an item
 * @param stabiliserPairs {@code P}, the matched stabiliser pairs
 * @param stabiliserBonus {@code B}, the stabiliser bonus in percentage points
 * @param bonusCap the most stabiliser pairs that count
 * @param stability {@code S}, the stability in percent, 0 to 100
 * @param upsetChance {@code p}, the upset chance per rolled step, per mille
 * @param nominal the rolled steps if every draw is full
 * @param worst the most rolled steps without an upset
 * @param ceiling the most rolled steps in any run
 * @param cleanFloor at least this chance of a run with no upset, per mille, whatever the vessels
 *     supply
 * @param cleanFullSupply the chance of a run with no upset if every draw is full, per mille
 * @param upsetMax the most upsets of the run
 * @param upsetSpill the most units one upset spills
 * @param upsetFluxBound the most flux the run's upsets can generate; 0 while flux rules are absent
 * @param costs the aspect costs, in ascending aspect id
 * @param stabilisersCapped whether more stabiliser pairs matched than count
 * @param clamped whether the stability was clamped to 0 to 100
 */
public record InfusionPlan(
        ResourceKey<Recipe<?>> recipe,
        VisKind kind,
        ItemStack output,
        int instability,
        int warpPenalty,
        int lonePedestals,
        int loneItems,
        int stabiliserPairs,
        int stabiliserBonus,
        int bonusCap,
        int stability,
        int upsetChance,
        int nominal,
        int worst,
        int ceiling,
        int cleanFloor,
        int cleanFullSupply,
        int upsetMax,
        int upsetSpill,
        long upsetFluxBound,
        List<InfusionCost> costs,
        boolean stabilisersCapped,
        boolean clamped) {
    /** Constructed by vftv only. */
    @Internal
    public InfusionPlan {
        output = output.copy();
        costs = List.copyOf(costs);
    }

    /**
     * A copy of the frozen output, fresh on every call.
     *
     * @return a copy of the output
     */
    @Override
    public ItemStack output() {
        return output.copy();
    }
}
