package com.andrewbrogoff.vftv.api.aspect;

import com.andrewbrogoff.vftv.api.Internal;
import com.andrewbrogoff.vftv.api.Stable;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * A hidden primal's data: a primal the notebook teaches as a compound until a research entry
 * reveals it (Vacuos, taught as Aer and Perditio). See the aspects spec, "Vacuos".
 *
 * @param taughtParents the two aspects the notebook draws as its parents before the reveal,
 *     unmodifiable
 * @param revealedBy the id of the research entry whose Learn reveals it
 */
@Stable
public record HiddenPrimal(List<ResourceKey<Aspect>> taughtParents, Identifier revealedBy) {
    /** Constructed by vftv only. */
    @Internal
    public HiddenPrimal {
        taughtParents = List.copyOf(taughtParents);
    }
}
