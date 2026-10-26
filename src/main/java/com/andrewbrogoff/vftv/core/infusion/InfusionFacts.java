package com.andrewbrogoff.vftv.core.infusion;

import com.andrewbrogoff.vftv.core.research.FactSource;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * The book's {@code infusion} fact source, {@code {fact:infusion:<key>}} (research spec's {@code
 * infusion} keys): every tuning key and the five {@code limits.*}, read from the synced {@link
 * InfusionRules}; {@code [?]} before the first arrives.
 */
final class InfusionFacts implements FactSource {
    static final InfusionFacts SOURCE = new InfusionFacts();

    private InfusionFacts() {}

    @Override
    public @Nullable String malformed(List<String> args) {
        return InfusionRules.DEFAULTS.value(args.getFirst()) != null
                ? null
                : "no infusion rule is named " + args.getFirst();
    }

    @Override
    public @Nullable Component resolve(Player reader, List<String> args) {
        InfusionRules rules =
                reader.level() instanceof ServerLevel
                        ? InfusionAltars.rules()
                        : InfusionAltars.clientRules();
        Integer value = rules == null ? null : rules.value(args.getFirst());
        return value == null ? null : Component.literal(Integer.toString(value));
    }
}
