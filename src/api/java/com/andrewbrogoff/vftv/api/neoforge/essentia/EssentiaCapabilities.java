package com.andrewbrogoff.vftv.api.neoforge.essentia;

import com.andrewbrogoff.vftv.api.Stable;
import com.andrewbrogoff.vftv.api.essentia.EssentiaKeys;
import com.andrewbrogoff.vftv.api.essentia.EssentiaSuction;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.transfer.ResourceHandler;
import org.jspecify.annotations.Nullable;

/**
 * The two essentia block capabilities. An addon block may expose both, on the faces it chooses (the
 * null side included), and its routing follows the {@link EssentiaSuction} rule.
 */
@Stable
public final class EssentiaCapabilities {
    private EssentiaCapabilities() {}

    /** The essentia handler, id {@code vftv:essentia}: what an endpoint holds and moves. */
    public static final BlockCapability<ResourceHandler<EssentiaResource>, @Nullable Direction>
            HANDLER =
                    BlockCapability.createSided(
                            EssentiaKeys.HANDLER_CAPABILITY,
                            ResourceHandler.<EssentiaResource>asClass());

    /** The suction ranks, id {@code vftv:essentia_suction}: how an endpoint routes. */
    public static final BlockCapability<EssentiaSuction, @Nullable Direction> SUCTION =
            BlockCapability.createSided(EssentiaKeys.SUCTION_CAPABILITY, EssentiaSuction.class);
}
