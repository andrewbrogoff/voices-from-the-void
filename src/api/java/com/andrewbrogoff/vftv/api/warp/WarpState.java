package com.andrewbrogoff.vftv.api.warp;

import com.andrewbrogoff.vftv.api.Internal;

/**
 * A player's warp at one moment, or, as a delta, the signed change between two such moments. See
 * the warp spec, "Kinds and the record" and "Total warp".
 *
 * @param temporary temporary warp {@code T}, 0–1,000 (signed as a delta)
 * @param lasting lasting warp {@code L}, 0–1,000 (signed as a delta)
 * @param permanent permanent warp {@code P}, 0–100 (signed as a delta)
 * @param gear gear warp {@code G}, the latest gear sample, 0–100 (signed as a delta)
 * @param total {@code T + L + P + G}, 0–2,200 (signed as a delta)
 */
public record WarpState(int temporary, int lasting, int permanent, int gear, int total) {

    /** Constructed by vftv only. */
    @Internal
    public WarpState {}
}
