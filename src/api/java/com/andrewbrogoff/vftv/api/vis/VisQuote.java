package com.andrewbrogoff.vftv.api.vis;

import com.andrewbrogoff.vftv.api.Internal;
import com.andrewbrogoff.vftv.api.aspect.Aspect;
import java.util.Collections;
import java.util.List;
import java.util.SequencedMap;
import java.util.TreeMap;
import net.minecraft.resources.ResourceKey;

/**
 * A priced vis cost: the base and effective costs, the multiplier and the factors behind it, the
 * spill, and the flags. Integer factors are per mille. See the wands spec, "The quote".
 *
 * @param base the base cost per vis aspect, each 0 to 1000, unmodifiable and in ascending id
 * @param effective the effective cost per vis aspect, unmodifiable and in ascending id
 * @param m the clamped cost multiplier, per mille
 * @param spill the flux the spend spills, 0 to 1,000,000
 * @param cap the cap factor, per mille ({@code cost_multiplier} times 1000)
 * @param discount the applied discount {@code d}, per mille, after the {@code max_discount} clamp
 * @param warp the warp factor {@code w}, per mille, 0 to 10,000
 * @param discountCapped whether the discount attribute exceeded {@code max_discount}
 * @param clamped whether the multiplier was clamped to the tuning's bounds
 * @param exceedsCapacity whether some effective cost exceeds the rod's capacity
 * @param inert whether the wand is inert (unfinished or missing a part)
 * @param affordable whether the wand is not inert and every pool covers its effective cost
 * @param shortAspects the aspects whose pool is below their cost, unmodifiable and in ascending id
 */
public record VisQuote(
        SequencedMap<ResourceKey<Aspect>, Integer> base,
        SequencedMap<ResourceKey<Aspect>, Integer> effective,
        int m,
        int spill,
        int cap,
        int discount,
        int warp,
        boolean discountCapped,
        boolean clamped,
        boolean exceedsCapacity,
        boolean inert,
        boolean affordable,
        List<ResourceKey<Aspect>> shortAspects) {
    /** Constructed by vftv only. */
    @Internal
    public VisQuote {
        base = Collections.unmodifiableSequencedMap(new TreeMap<>(base));
        effective = Collections.unmodifiableSequencedMap(new TreeMap<>(effective));
        shortAspects = shortAspects.stream().sorted().toList();
    }
}
