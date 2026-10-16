package com.andrewbrogoff.vftv.api.aspect;

import com.andrewbrogoff.vftv.api.Stable;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Collections;
import java.util.Map;
import java.util.SequencedMap;
import java.util.TreeMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ExtraCodecs;

/**
 * The value of the {@code vftv:aspects} and {@code vftv:aspect_defaults} data maps: what a thing is
 * made of. In JSON it is an object from aspect id to amount, for example {@code {"vftv:ignis": 3}}.
 * An empty value ({@code {}}) is an explicit "nothing to learn", distinct from no entry. See the
 * aspects spec, "Data maps".
 *
 * <p>Addons may construct it. Aspect ids are not resolved here: an id the aspect registry lacks is
 * the loader's to reject.
 *
 * @param aspects the amount of each aspect, unmodifiable and in ascending aspect id; at most {@link
 *     AspectLimits#MAX_ASPECTS_PER_ENTRY} entries, each amount at least 1
 */
@Stable
public record AspectValues(SequencedMap<ResourceKey<Aspect>, Integer> aspects) {
    /**
     * The JSON and network codec: an object from aspect id to a positive amount, with at most
     * {@link AspectLimits#MAX_ASPECTS_PER_ENTRY} entries. Anything else is an error result.
     */
    public static final Codec<AspectValues> CODEC =
            Codec.unboundedMap(ResourceKey.codec(AspectKeys.REGISTRY), ExtraCodecs.POSITIVE_INT)
                    .validate(
                            map ->
                                    map.size() <= AspectLimits.MAX_ASPECTS_PER_ENTRY
                                            ? DataResult.success(map)
                                            : DataResult.error(
                                                    () ->
                                                            "At most "
                                                                    + AspectLimits
                                                                            .MAX_ASPECTS_PER_ENTRY
                                                                    + " aspects per value, got "
                                                                    + map.size()))
                    .xmap(map -> new AspectValues(new TreeMap<>(map)), AspectValues::aspects);

    /**
     * Checks and copies the amounts.
     *
     * @throws IllegalArgumentException if there are more than {@link
     *     AspectLimits#MAX_ASPECTS_PER_ENTRY} aspects or an amount is below 1
     */
    public AspectValues {
        if (aspects.size() > AspectLimits.MAX_ASPECTS_PER_ENTRY) {
            throw new IllegalArgumentException(
                    "At most "
                            + AspectLimits.MAX_ASPECTS_PER_ENTRY
                            + " aspects per value, got "
                            + aspects.size());
        }
        for (Map.Entry<ResourceKey<Aspect>, Integer> entry : aspects.entrySet()) {
            if (entry.getValue() < 1) {
                throw new IllegalArgumentException(
                        "Amount of " + entry.getKey().identifier() + " must be positive");
            }
        }
        aspects = Collections.unmodifiableSequencedMap(new TreeMap<>(aspects));
    }
}
