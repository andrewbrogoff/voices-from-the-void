package com.andrewbrogoff.vftv.api.neoforge.armor;

import com.andrewbrogoff.vftv.api.Stable;
import com.andrewbrogoff.vftv.api.armor.BaffleView;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.capabilities.EntityCapability;
import org.jspecify.annotations.Nullable;

/** The armor feature's capabilities. */
@Stable
public final class ArmorCapabilities {
    private ArmorCapabilities() {}

    /**
     * {@code vftv:baffle_view}: the live {@link BaffleView} of a player, registered by vftv for the
     * player entity type. It answers for a server player and is null for any client-side player.
     * Query it on the logical server's thread only.
     */
    public static final EntityCapability<BaffleView, @Nullable Void> BAFFLE_VIEW =
            EntityCapability.createVoid(
                    Identifier.fromNamespaceAndPath("vftv", "baffle_view"), BaffleView.class);
}
