package com.andrewbrogoff.vftv.core.essentia;

import com.andrewbrogoff.vftv.core.essentia.model.EssentiaTuning;
import com.andrewbrogoff.vftv.core.research.FactSource;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * The book's {@code essentia} fact source, {@code {fact:essentia:<key>}} (essentia spec,
 * "Inspection and teaching"; research spec's {@code essentia} keys): every tuning key, the derived
 * {@code tube.idle_ticks} and the five {@code limits.*}, read from the synced {@link
 * EssentiaRules}; {@code [?]} before the first arrives.
 */
final class EssentiaFacts implements FactSource {
    static final EssentiaFacts SOURCE = new EssentiaFacts();

    private static final List<String> EXTRA =
            List.of(
                    "tube.idle_ticks",
                    "limits.max_members",
                    "limits.max_endpoints",
                    "limits.max_suction",
                    "limits.storage_max",
                    "limits.max_handler_slots");

    private EssentiaFacts() {}

    private static boolean known(String key) {
        return EXTRA.contains(key)
                || EssentiaTuning.KEYS.stream().anyMatch(k -> k.name().equals(key));
    }

    @Override
    public @Nullable String malformed(List<String> args) {
        return known(args.getFirst()) ? null : "no essentia rule is named " + args.getFirst();
    }

    @Override
    public @Nullable Component resolve(Player reader, List<String> args) {
        EssentiaRules rules =
                reader.level() instanceof ServerLevel
                        ? EssentiaService.rules()
                        : EssentiaService.clientRules();
        String key = args.getFirst();
        if (rules == null || !known(key)) {
            return null;
        }
        long value =
                switch (key) {
                    case "tube.idle_ticks" -> rules.tubeIdleTicks();
                    case "limits.max_members" -> rules.maxMembers();
                    case "limits.max_endpoints" -> rules.maxEndpoints();
                    case "limits.max_suction" -> rules.maxSuction();
                    case "limits.storage_max" -> rules.storageMax();
                    case "limits.max_handler_slots" -> rules.maxHandlerSlots();
                    default -> rules.tuning().toJson().get(key).getAsInt();
                };
        return Component.literal(Long.toString(value));
    }
}
