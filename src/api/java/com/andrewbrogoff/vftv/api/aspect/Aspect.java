package com.andrewbrogoff.vftv.api.aspect;

import com.andrewbrogoff.vftv.api.Stable;
import java.util.List;
import java.util.OptionalInt;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

/**
 * An entry of the synced datapack registry {@code vftv:aspect} ({@link AspectKeys#REGISTRY}),
 * exposing its registry fields. Resolve entries through the registry access; never cache a holder
 * across world loads. See the aspects spec, "Registry format".
 *
 * <p>Not for implementation: vftv implements this interface; an addon only reads it.
 */
@Stable
public interface Aspect {
    /**
     * The colour used for icons and essentia tints.
     *
     * @return the RGB colour, {@code 0x000000} to {@code 0xFFFFFF}
     */
    int color();

    /**
     * The aspect's parents in the true view: none for a primal (hidden primals included), two
     * distinct aspects for a compound.
     *
     * @return an unmodifiable list of 0 or 2 aspect keys
     */
    List<ResourceKey<Aspect>> parents();

    /**
     * The aspect's GUI sprite id, with the default ({@code <ns>:aspect/<path>}) resolved.
     *
     * @return the sprite id
     */
    Identifier icon();

    /**
     * The hidden-primal data: what the notebook teaches before the reveal, and the research entry
     * that reveals it.
     *
     * @return the hidden-primal data, or {@code null} for an ordinary aspect
     */
    @Nullable HiddenPrimal hiddenPrimal();

    /**
     * The points of this aspect granted when a player discovers it.
     *
     * @return the bonus, or empty when the tuning default applies
     */
    OptionalInt discoveryBonus();
}
