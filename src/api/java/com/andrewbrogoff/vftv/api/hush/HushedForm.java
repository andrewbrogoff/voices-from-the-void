package com.andrewbrogoff.vftv.api.hush;

import com.andrewbrogoff.vftv.api.Internal;
import com.andrewbrogoff.vftv.api.Stable;
import net.minecraft.resources.Identifier;

/**
 * One row of the fixed hushed-form table: a source block and the block the Hush turns it into.
 * Converting and restoring copy the block state's properties both ways. See the Hush spec, "The
 * forms".
 *
 * @param source the source block's id, for example {@code minecraft:stone}
 * @param form the hushed form's block id, for example {@code vftv:hushed_stone}
 */
@Stable
public record HushedForm(Identifier source, Identifier form) {
    /** Constructed by vftv only. */
    @Internal
    public HushedForm {}
}
