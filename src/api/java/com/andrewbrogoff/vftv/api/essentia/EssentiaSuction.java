package com.andrewbrogoff.vftv.api.essentia;

import com.andrewbrogoff.vftv.api.Stable;
import com.andrewbrogoff.vftv.api.aspect.Aspect;
import net.minecraft.resources.ResourceKey;

/**
 * An essentia endpoint's routing ranks, exposed per face as the {@code vftv:essentia_suction} block
 * capability beside the {@code vftv:essentia} handler. Loader-free, and implementable by addons.
 *
 * <p>The rule: a mover (a line or an Alembic, or an addon consumer that routes like one) moves
 * aspect X from endpoint A to endpoint B only if {@code B.suction(X) > A.hold(X)}, strictly. So
 * essentia only flows uphill in rank, and endpoints of equal rank never trade. An endpoint without
 * this capability has suction 0 and hold 0: a pure source, or inert.
 *
 * <p>The implementer's contract:
 *
 * <ul>
 *   <li>Both methods return a rank from 0 to {@link EssentiaLimits#MAX_SUCTION}; a value outside
 *       that range is clamped and logged once per block entity type.
 *   <li>Ranks 1 to {@link EssentiaLimits#STORAGE_MAX} are the storage band (jars and addon
 *       buffers); ranks above it up to {@link EssentiaLimits#MAX_SUCTION} are the consumer band
 *       (the Node Tap, infusion and addon machines); 0 is a source or an opted-out sink.
 *   <li>A positive {@link #suction} for X means the endpoint would accept at least one unit of X
 *       now. An endpoint that would accept may still answer 0, which opts it out of automatic
 *       pulling.
 *   <li>{@link #hold} does not change with fullness, so a full store never drains into an empty
 *       peer of equal rank.
 *   <li>Both are cheap, side-effect-free queries called on the server thread.
 *   <li>This is a routing policy only: no essentia handler enforces it, so the endpoint's handler
 *       must still refuse what it can't take.
 * </ul>
 */
@Stable
public interface EssentiaSuction {
    /**
     * How strongly this endpoint pulls an aspect now.
     *
     * @param aspect the aspect a mover would deliver
     * @return the suction rank, 0 to {@link EssentiaLimits#MAX_SUCTION}; positive only if the
     *     endpoint would accept at least one unit now
     */
    int suction(ResourceKey<Aspect> aspect);

    /**
     * The rank at which this endpoint keeps the aspect it holds; it does not change with fullness.
     * The default is 0: the endpoint gives up what it holds to any positive suction.
     *
     * @param aspect the aspect a mover would take
     * @return the hold rank, 0 to {@link EssentiaLimits#MAX_SUCTION}
     */
    default int hold(ResourceKey<Aspect> aspect) {
        return 0;
    }
}
