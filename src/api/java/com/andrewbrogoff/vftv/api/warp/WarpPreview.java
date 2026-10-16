package com.andrewbrogoff.vftv.api.warp;

import com.andrewbrogoff.vftv.api.Internal;

/**
 * The actual warp deltas of a change, after clamping to the format bounds and the permanent cap.
 * See the warp spec, "The commit" and "Preview".
 *
 * @param dT the actual change of temporary warp, signed
 * @param dL the actual change of lasting warp, signed
 * @param dP the actual change of permanent warp, signed
 * @param clipped whether the actual deltas differ from the cost asked for
 */
public record WarpPreview(int dT, int dL, int dP, boolean clipped) {

    /** Constructed by vftv only. */
    @Internal
    public WarpPreview {}
}
