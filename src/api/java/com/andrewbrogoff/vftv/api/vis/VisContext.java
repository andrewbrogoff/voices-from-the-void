package com.andrewbrogoff.vftv.api.vis;

import com.andrewbrogoff.vftv.api.Internal;
import com.andrewbrogoff.vftv.api.research.Research;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

/**
 * What a vis quote or spend is for. See the wands spec, "The context".
 *
 * @param source the recipe or focus id the spend is for, for events and logs
 * @param kind whether the spend is ordinary magic or Void work
 * @param research the research entry the action needs, or {@code null} for none
 */
public record VisContext(
        Identifier source, VisKind kind, @Nullable ResourceKey<Research> research) {
    /** Constructed by vftv only. */
    @Internal
    public VisContext {}
}
