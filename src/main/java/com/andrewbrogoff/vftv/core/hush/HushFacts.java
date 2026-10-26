package com.andrewbrogoff.vftv.core.hush;

import com.andrewbrogoff.vftv.api.flux.FluxRules;
import com.andrewbrogoff.vftv.core.flux.FluxRulesService;
import com.andrewbrogoff.vftv.core.flux.client.ClientFluxRules;
import com.andrewbrogoff.vftv.core.flux.model.FluxTuning;
import com.andrewbrogoff.vftv.core.hush.model.HushMath;
import com.andrewbrogoff.vftv.core.research.FactSource;
import com.andrewbrogoff.vftv.core.text.model.NumberFormats;
import java.util.List;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * The book's {@code hush} fact source, {@code {fact:hush:<key>}} (research spec's {@code hush}
 * keys): every tuning key from the synced {@link FluxRules}, the derived threshold and seconds, the
 * switches, and the registration facts; {@code [?]} before the rules arrive.
 */
final class HushFacts implements FactSource {
    static final HushFacts SOURCE = new HushFacts();

    private static final Set<String> PER_MILLE =
            Set.of("flux.essentia_pm", "flux.tax_pm", "flux.void_pm");
    private static final List<String> SECONDS =
            List.of("hush.interval_seconds", "bell.cooldown_seconds", "peal.interval_seconds");
    private static final List<String> REGISTRATION =
            List.of(
                    "bell.durability",
                    "hollow.health",
                    "hollow.attack",
                    "hollow.xp",
                    "hollow.drops_max");
    private static final List<String> SWITCHES =
            List.of("switch.spread", "switch.hollows", "switch.unattended");

    private HushFacts() {}

    private static int keyIndex(String key) {
        for (int i = 0; i < FluxTuning.KEYS.size(); i++) {
            if (FluxTuning.KEYS.get(i).name().equals(key)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean known(String key) {
        return keyIndex(key) >= 0
                || key.equals("hush.threshold")
                || SECONDS.contains(key)
                || REGISTRATION.contains(key)
                || SWITCHES.contains(key);
    }

    @Override
    public @Nullable String malformed(List<String> args) {
        return known(args.getFirst()) ? null : "no hush fact is named " + args.getFirst();
    }

    @Override
    public @Nullable Component resolve(Player reader, List<String> args) {
        String key = args.getFirst();
        switch (key) {
            case "bell.durability" -> {
                return number(HushContent.BELL_DURABILITY);
            }
            case "hollow.health" -> {
                return number((long) Hollow.HEALTH);
            }
            case "hollow.attack" -> {
                return number((long) Hollow.ATTACK);
            }
            case "hollow.xp" -> {
                return number(Hollow.XP);
            }
            case "hollow.drops_max" -> {
                return number(Hollow.DROPS_MAX);
            }
            default -> {}
        }
        FluxRules rules =
                reader.level() instanceof ServerLevel
                        ? FluxRulesService.current()
                        : ClientFluxRules.get();
        if (rules == null || !known(key)) {
            return null;
        }
        if (SWITCHES.contains(key)) {
            boolean on =
                    switch (key) {
                        case "switch.spread" -> rules.spread();
                        case "switch.hollows" -> rules.hollows();
                        default -> rules.unattended();
                    };
            return Component.translatable(HushLang.FACT + (on ? "on" : "off"));
        }
        int[] values = FluxTuning.values(rules);
        if (key.equals("hush.threshold")) {
            return number(HushMath.threshold(rules.hushOnset(), rules.hushBlockCost()));
        }
        if (SECONDS.contains(key)) {
            String ticks = key.substring(0, key.length() - "_seconds".length());
            return Component.literal(NumberFormats.seconds(values[keyIndex(ticks) + 1]));
        }
        int value = values[keyIndex(key) + 1];
        return PER_MILLE.contains(key)
                ? Component.literal(NumberFormats.percent(value))
                : number(value);
    }

    private static Component number(long value) {
        return Component.literal(NumberFormats.integer(value));
    }
}
