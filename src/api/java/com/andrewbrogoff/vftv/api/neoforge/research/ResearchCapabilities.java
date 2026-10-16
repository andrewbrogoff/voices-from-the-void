package com.andrewbrogoff.vftv.api.neoforge.research;

import com.andrewbrogoff.vftv.api.Stable;
import com.andrewbrogoff.vftv.api.research.ResearchView;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.capabilities.EntityCapability;
import org.jspecify.annotations.Nullable;

/**
 * The research feature's capabilities. Stable: no capability here is removed or renamed within a
 * major. See the API spec, "ResearchView and BaffleView".
 */
@Stable
public final class ResearchCapabilities {
    private ResearchCapabilities() {}

    /**
     * {@code vftv:research_view}: a player's {@link ResearchView}, registered by vftv for players.
     * It answers a view for a {@code ServerPlayer} and {@code null} for any client-side player.
     */
    public static final EntityCapability<ResearchView, @Nullable Void> VIEW =
            EntityCapability.createVoid(
                    Identifier.fromNamespaceAndPath("vftv", "research_view"), ResearchView.class);
}
