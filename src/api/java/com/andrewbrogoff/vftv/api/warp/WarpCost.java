package com.andrewbrogoff.vftv.api.warp;

import com.andrewbrogoff.vftv.api.Internal;

/**
 * The warp change a commit or apply asks for, before clamping. Internal: the argument of vftv's
 * warp commit, public only for cross-feature wiring. See the warp spec, "The commit".
 *
 * @param temporary the asked change of temporary warp, −1,000–1,000
 * @param lasting the asked change of lasting warp, −1,000–1,000
 * @param permanent the asked change of permanent warp, −1,000–1,000; negative only for a command
 */
@Internal
public record WarpCost(int temporary, int lasting, int permanent) {

    /** Constructed by vftv only. */
    @Internal
    public WarpCost {}
}
