package com.andrewbrogoff.vftv.core.warp;

import com.andrewbrogoff.vftv.api.vis.VisContext;
import com.andrewbrogoff.vftv.api.vis.VisKind;
import com.andrewbrogoff.vftv.api.warp.WarpRules;
import com.andrewbrogoff.vftv.core.warp.model.WarpMath;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * The three dials (warp spec, "The dials"): pure functions of the owner's synced record and the
 * synced {@link WarpRules}, so a client and the server compute the same value. On a client before
 * the rules arrive, and for an unreadable record, every dial reads neutral.
 */
public final class WarpDials {
    private WarpDials() {}

    /**
     * The vis factor for a spend in {@code ctx}: {@code (1000 + min(tax.max_pm, total ×
     * tax.per_warp_pm)) / 1000.0} for ordinary vis with the tax on, else 1.0. The wands quote
     * recovers the per mille with {@code Math.round(factor × 1000)}.
     */
    public static double visFactor(Player player, VisContext ctx) {
        WarpRules rules = rules(player);
        WarpRecord r = WarpService.get(player).readable();
        if (rules == null || r == null) {
            return 1.0;
        }
        return WarpMath.visFactorPm(r.total(), ctx.kind() == VisKind.ORDINARY, rules) / 1000.0;
    }

    /** The infusion stability penalty for work of {@code kind}; 0 for {@code VOID}. */
    public static int infusionPenalty(Player player, VisKind kind) {
        WarpRules rules = rules(player);
        WarpRecord r = WarpService.get(player).readable();
        if (rules == null || r == null) {
            return 0;
        }
        return WarpMath.infusionPenalty(r.total(), kind == VisKind.ORDINARY, rules);
    }

    /**
     * The Void-tier amplifier's warp: {@code total}, plus the Joining bonus for a Joined Listener;
     * 0 with {@code warp.amplifier} off.
     */
    public static int amplifierWarp(Player player) {
        WarpRules rules = rules(player);
        WarpRecord r = WarpService.get(player).readable();
        if (rules == null || r == null) {
            return 0;
        }
        return WarpMath.amplifierWarp(r.total(), r.joined(), rules);
    }

    private static @Nullable WarpRules rules(Player player) {
        return player.level().isClientSide()
                ? WarpRulesService.client()
                : WarpRulesService.current();
    }
}
