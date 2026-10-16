package com.andrewbrogoff.vftv.api.warp;

import com.andrewbrogoff.vftv.api.Internal;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * What changed a player's warp. See the warp spec, "The commit".
 *
 * @param kind the kind of source
 * @param id the research entry, bargain or milestone id: present for {@link WarpSourceKind#ENTRY},
 *     {@link WarpSourceKind#BARGAIN} and {@link WarpSourceKind#MILESTONE}, absent otherwise
 */
public record WarpSource(WarpSourceKind kind, @Nullable Identifier id) {

    /** Constructed by vftv only. */
    @Internal
    public WarpSource {}
}
