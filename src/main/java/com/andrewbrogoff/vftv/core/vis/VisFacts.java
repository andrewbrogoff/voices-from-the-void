package com.andrewbrogoff.vftv.core.vis;

import com.andrewbrogoff.vftv.core.research.FactSource;
import com.andrewbrogoff.vftv.core.vis.model.VisTuning;
import java.math.BigDecimal;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * The book's {@code vis} fact source, {@code {fact:vis:<key>}} (wands spec, "Inspection and
 * teaching"): any key of the synced {@link VisRules}, re-resolved whenever it arrives; {@code [?]}
 * before the first.
 */
final class VisFacts implements FactSource {
    static final VisFacts SOURCE = new VisFacts();

    private VisFacts() {}

    private static boolean known(String key) {
        return key.equals("starter")
                || key.equals(VisRules.NETWORK_MEMBERS)
                || key.equals(VisRules.NODES_PER_CELL)
                || key.equals(VisRules.ASPECTS_PER_NODE)
                || VisTuning.DEFAULTS.values().containsKey(key);
    }

    @Override
    public @Nullable String malformed(List<String> args) {
        return known(args.getFirst()) ? null : "no vis rule is named " + args.getFirst();
    }

    @Override
    public @Nullable Component resolve(Player reader, List<String> args) {
        VisRules rules =
                reader.level() instanceof net.minecraft.server.level.ServerLevel server
                        ? VisRulesService.current(server.getServer())
                        : VisRulesService.client();
        String key = args.getFirst();
        if (rules == null || !known(key)) {
            return null;
        }
        if (key.equals("starter")) {
            return Component.translatable(
                    rules.starter() ? VisLang.FACT_STARTER_PRESENT : VisLang.FACT_STARTER_ABSENT);
        }
        var value = rules.toJson().get(key);
        return value == null
                ? null
                : Component.literal(
                        new BigDecimal(value.getAsString()).stripTrailingZeros().toPlainString());
    }
}
