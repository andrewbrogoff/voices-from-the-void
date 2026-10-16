package com.andrewbrogoff.vftv.testaddon;

import com.andrewbrogoff.vftv.api.armor.ArmorKeys;
import com.andrewbrogoff.vftv.api.armor.ArmorLimits;
import com.andrewbrogoff.vftv.api.armor.BaffleView;
import com.andrewbrogoff.vftv.api.armor.Wearable;
import com.andrewbrogoff.vftv.api.armor.WearableSlot;
import com.andrewbrogoff.vftv.api.neoforge.armor.ArmorCapabilities;
import com.andrewbrogoff.vftv.api.neoforge.armor.ArmorDataMaps;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * The example addon's use of the armor API: a {@link Wearable} built through its constructor, and
 * the Baffle read through its view when a player logs in.
 */
final class ArmorAddon {
    /** Every Stable constant of the feature, referenced so a rename breaks this build. */
    static final List<Object> CONSTANTS =
            List.of(
                    ArmorKeys.BAFFLE,
                    ArmorKeys.BAFFLE_ATTACHMENT,
                    ArmorKeys.WEARABLE,
                    ArmorKeys.VOID_ARMOR,
                    ArmorKeys.REVEALS_ASPECTS,
                    ArmorKeys.REPAIRS_VOID_ARMOR,
                    ArmorKeys.BAFFLE_IGNORES,
                    ArmorKeys.BAFFLING,
                    ArmorLimits.BAFFLE_FORMAT_MAX,
                    ArmorLimits.WEARABLE_MAX_ARMOR,
                    ArmorLimits.WEARABLE_MAX_BAFFLE,
                    ArmorLimits.HUD_PIPS_PER_ROW,
                    ArmorLimits.HUD_MAX_ROWS,
                    ArmorDataMaps.WEARABLE,
                    ArmorCapabilities.BAFFLE_VIEW,
                    WearableSlot.CODEC,
                    Wearable.CODEC);

    private ArmorAddon() {}

    /** Builds the example wearable and reads the Baffle view of each player who logs in. */
    static void register(IEventBus modBus, IEventBus gameBus) {
        VftvAddon.consume(ring());
        gameBus.addListener(
                PlayerEvent.PlayerLoggedInEvent.class,
                e -> {
                    if (e.getEntity() instanceof ServerPlayer player) {
                        readBaffle(player);
                    }
                });
    }

    /** An off-hand ring entry, as an addon's datagen would write it into {@code vftv:wearable}. */
    static Wearable ring() {
        return new Wearable(WearableSlot.OFFHAND, 0, 0.0, 0.0, 0.05, 2);
    }

    /** Reads every method of the player's Baffle view, which is null on a client-side player. */
    static void readBaffle(ServerPlayer player) {
        BaffleView view = player.getCapability(ArmorCapabilities.BAFFLE_VIEW);
        if (view != null) {
            VftvAddon.consume(view.readable(), view.current(), view.capacity());
        }
    }
}
