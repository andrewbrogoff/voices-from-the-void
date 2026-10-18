package com.andrewbrogoff.vftv.core.vis;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.api.flux.FluxRules;
import com.andrewbrogoff.vftv.api.vis.VisContext;
import com.andrewbrogoff.vftv.api.vis.VisKeys;
import com.andrewbrogoff.vftv.api.vis.VisLimits;
import com.andrewbrogoff.vftv.api.vis.VisQuote;
import com.andrewbrogoff.vftv.api.vis.VisSpentPayload;
import com.andrewbrogoff.vftv.core.aspect.AspectLang;
import com.andrewbrogoff.vftv.core.common.RawRecordCodec.Retained;
import com.andrewbrogoff.vftv.core.flux.FluxRulesService;
import com.andrewbrogoff.vftv.core.research.ResearchService;
import com.andrewbrogoff.vftv.core.text.model.SharedMessages;
import com.andrewbrogoff.vftv.core.vis.model.QuoteMath;
import com.andrewbrogoff.vftv.core.vis.model.VisTuning;
import com.andrewbrogoff.vftv.core.warp.WarpDials;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The one vis economy's spend side (wands spec, "Spending vis"): a pure {@link #quote} and a
 * checked {@link #spend}, used by every consumer. Vis leaves a wand only through {@code spend}.
 */
public final class VisService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile Supplier<@Nullable FluxRules> clientFlux = () -> null;
    private static boolean warnedNonFinite;

    private VisService() {}

    /** Installs the client's flux rules, which price a client quote's spill. */
    public static void installClientFlux(Supplier<@Nullable FluxRules> rules) {
        clientFlux = rules;
    }

    /**
     * One slot a spend consumes or fills.
     *
     * @param container the container holding the slot
     * @param slot the slot's index in it
     * @param expectedBefore what the slot must hold now ({@code ItemStack.matches})
     * @param after what it holds after the spend
     */
    public record SlotChange(
            Container container, int slot, ItemStack expectedBefore, ItemStack after) {
        /** Copies the stacks. */
        public SlotChange {
            expectedBefore = expectedBefore.copy();
            after = after.copy();
        }
    }

    /** A consumer's apply boundary, told once when the commit's writes (or rollback) finished. */
    @FunctionalInterface
    public interface Boundary {
        /**
         * The commit finished: {@code committed} true after every write, false after a rollback.
         */
        void applied(boolean committed);
    }

    /** What a spend did. */
    public enum Outcome {
        /** Vis, materials and output were committed. */
        SPENT,
        /** Committed, but a {@code VisSpentEvent} listener (or the boundary) threw. */
        COMMITTED_NOTIFY_FAILED,
        /** Refused: the stack is not a ready wand. */
        INERT,
        /** Refused: the context's research is not learned. */
        UNLEARNED,
        /** Refused: the expected quote or a slot changed. */
        STALE,
        /** Refused: some pool is below its cost. */
        SHORT;

        /** Whether vis, materials and output were committed. */
        public boolean committed() {
            return this == SPENT || this == COMMITTED_NOTIFY_FAILED;
        }
    }

    /**
     * A spend's result.
     *
     * @param outcome what happened
     * @param message the refusal's text, or null for a committed spend
     * @param quote the quote recomputed at the spend, or null when refused before it
     */
    public record SpendResult(
            Outcome outcome, @Nullable Component message, @Nullable VisQuote quote) {}

    /** The {@code vftv:vis_discount} attribute's holder, or null before it is registered. */
    private static Holder.@Nullable Reference<Attribute> discountAttribute() {
        return BuiltInRegistries.ATTRIBUTE.get(VisKeys.VIS_DISCOUNT).orElse(null);
    }

    /**
     * {@code VisService.quote(player, wand, base, ctx)}: resolves the inputs from synced data and
     * prices the base cost. Both sides compute it identically.
     *
     * @throws IllegalArgumentException for a base naming a non-vis aspect or out of range
     */
    public static VisQuote quote(
            Player player, ItemStack wand, Map<ResourceKey<Aspect>, Integer> base, VisContext ctx) {
        WandParts parts = WandParts.of(player.level().registryAccess());
        SortedMap<String, Integer> baseIds = new TreeMap<>();
        base.forEach(
                (a, n) -> {
                    if (!parts.visAspects().contains(a)) {
                        throw new IllegalArgumentException(a.identifier() + " is not a vis aspect");
                    }
                    baseIds.put(a.identifier().toString(), n);
                });
        WandState state =
                wand.is(VisContent.wand()) ? WandState.of(wand, parts) : new WandState.Unfinished();
        double cost = 1.0;
        int capacity = 0;
        SortedMap<String, Integer> pools = new TreeMap<>();
        if (state instanceof WandState.Ready ready) {
            cost = ready.cap().costMultiplier();
            capacity = ready.rod().capacity();
            pools = ready.pools(parts);
        }
        Holder.Reference<Attribute> attribute = discountAttribute();
        double discount =
                attribute != null && player.getAttributes().hasAttribute(attribute)
                        ? player.getAttributeValue(attribute)
                        : 0;
        double warp = WarpDials.visFactor(player, ctx);
        if (!Double.isFinite(warp) && !warnedNonFinite) {
            warnedNonFinite = true;
            LOGGER.warn("The warp vis factor is not finite ({}); pricing at 1.0", warp);
        }
        VisTuning t = VisRulesService.tuning(player.level());
        FluxRules flux =
                player.level().isClientSide() ? clientFlux.get() : FluxRulesService.current();
        QuoteMath.Result r =
                QuoteMath.quote(
                        new QuoteMath.Inputs(
                                baseIds,
                                cost,
                                discount,
                                t.decimal("quote.max_discount"),
                                warp,
                                t.decimal("quote.min_multiplier"),
                                t.decimal("quote.max_multiplier"),
                                ctx.kind(),
                                flux,
                                capacity,
                                pools,
                                state.inert()));
        return new VisQuote(
                keys(r.base()),
                keys(r.effective()),
                r.m(),
                r.spill(),
                r.cap(),
                r.discount(),
                r.warp(),
                r.discountCapped(),
                r.clamped(),
                r.exceedsCapacity(),
                r.inert(),
                r.affordable(),
                r.shortAspects().stream().map(VisService::key).toList());
    }

    /**
     * Echo equality: the same cost iff base, effective, {@code m}, spill and the three modifier and
     * capacity flags are equal; the factors are not compared.
     */
    public static boolean sameCost(VisQuote a, VisQuote b) {
        return a.base().equals(b.base())
                && a.effective().equals(b.effective())
                && a.m() == b.m()
                && a.spill() == b.spill()
                && a.discountCapped() == b.discountCapped()
                && a.clamped() == b.clamped()
                && a.exceedsCapacity() == b.exceedsCapacity();
    }

    /**
     * {@code VisService.spend(player, wand, base, ctx, expected, effect, boundary)}: one checked
     * commit over vis, materials and output, on the server thread.
     *
     * @param expected the quote the player confirmed, or null for the unchecked form (a cast)
     * @param effect the slot changes, at most 64, no slot twice
     * @param boundary the consumer's apply boundary, or null
     * @throws IllegalArgumentException for more than 64 changes or a slot named twice
     * @throws RuntimeException rethrown from a failed commit, which changed nothing
     */
    public static SpendResult spend(
            ServerPlayer player,
            ItemStack wand,
            Map<ResourceKey<Aspect>, Integer> base,
            VisContext ctx,
            @Nullable VisQuote expected,
            List<SlotChange> effect,
            @Nullable Boundary boundary) {
        if (effect.size() > VisLimits.MAX_SLOT_CHANGES) {
            throw new IllegalArgumentException(effect.size() + " slot changes, more than 64");
        }
        Set<SlotKey> slots = new HashSet<>();
        for (SlotChange c : effect) {
            if (!slots.add(new SlotKey(c.container(), c.slot()))) {
                throw new IllegalArgumentException("slot " + c.slot() + " named twice");
            }
        }
        WandParts parts = WandParts.of(player.level().registryAccess());
        if (!wand.is(VisContent.wand())
                || !(WandState.of(wand, parts) instanceof WandState.Ready ready)) {
            return refuse(Outcome.INERT, VisLang.SPEND_INERT, null);
        }
        if (ctx.research() != null && !ResearchService.isLearned(player, ctx.research())) {
            return refuse(Outcome.UNLEARNED, VisLang.SPEND_UNLEARNED, null);
        }
        VisQuote quote = quote(player, wand, base, ctx);
        if (expected != null && !sameCost(expected, quote)) {
            return refuse(Outcome.STALE, SharedMessages.COST_CHANGED, quote);
        }
        if (!quote.affordable()) {
            return new SpendResult(
                    Outcome.SHORT,
                    Component.translatable(
                            VisLang.SPEND_SHORT,
                            ComponentUtils.formatList(quote.shortAspects(), AspectLang::name)),
                    quote);
        }
        for (SlotChange c : effect) {
            if (!ItemStack.matches(c.container().getItem(c.slot()), c.expectedBefore())) {
                return refuse(Outcome.STALE, SharedMessages.COST_CHANGED, quote);
            }
        }
        boolean notified = commit(wand, ready, quote, effect, boundary, parts);
        notified &=
                VisEvents.spent(
                        new VisSpentPayload(
                                player, player.level(), player.blockPosition(), ctx, quote));
        return new SpendResult(
                notified ? Outcome.SPENT : Outcome.COMMITTED_NOTIFY_FAILED, null, quote);
    }

    private record SlotKey(Container container, int slot) {
        // A slot is one container instance's index: two equal containers are still two slots.
        @SuppressWarnings("ReferenceEquality")
        @Override
        public boolean equals(Object o) {
            return o instanceof SlotKey k && k.container == container && k.slot == slot;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(container) * 31 + slot;
        }
    }

    /** Step 6: the commit under a rollback scope; false when the boundary threw after it. */
    private static boolean commit(
            ItemStack wand,
            WandState.Ready ready,
            VisQuote quote,
            List<SlotChange> effect,
            @Nullable Boundary boundary,
            WandParts parts) {
        Retained<VisPools> visBefore = wand.get(VisContent.vis());
        List<ItemStack> slotsBefore = new ArrayList<>();
        for (SlotChange c : effect) {
            slotsBefore.add(c.container().getItem(c.slot()).copy());
        }
        try {
            Map<String, Integer> debited = new TreeMap<>();
            quote.effective()
                    .forEach(
                            (a, cost) ->
                                    debited.put(a.identifier().toString(), ready.pool(a) - cost));
            WandState.write(wand, ready, debited, parts);
            for (SlotChange c : effect) {
                c.container().setItem(c.slot(), c.after().copy());
            }
        } catch (RuntimeException e) {
            if (visBefore == null) {
                wand.remove(VisContent.vis());
            } else {
                wand.set(VisContent.vis(), visBefore);
            }
            // Each slot is restored on its own: one throwing restore must not skip the rest.
            for (int i = 0; i < effect.size(); i++) {
                try {
                    effect.get(i).container().setItem(effect.get(i).slot(), slotsBefore.get(i));
                } catch (RuntimeException suppressed) {
                    e.addSuppressed(suppressed);
                }
            }
            LOGGER.error("A vis spend failed mid-commit; every change was rolled back", e);
            if (boundary != null) {
                try {
                    boundary.applied(false);
                } catch (RuntimeException suppressed) {
                    e.addSuppressed(suppressed);
                }
            }
            throw e;
        }
        if (boundary != null) {
            try {
                boundary.applied(true);
            } catch (RuntimeException e) {
                LOGGER.error("A spend's apply boundary threw after the commit; it stands", e);
                return false;
            }
        }
        return true;
    }

    private static SpendResult refuse(Outcome outcome, String key, @Nullable VisQuote quote) {
        return new SpendResult(outcome, Component.translatable(key), quote);
    }

    private static SortedMap<ResourceKey<Aspect>, Integer> keys(Map<String, Integer> byId) {
        SortedMap<ResourceKey<Aspect>, Integer> out = new TreeMap<>();
        byId.forEach((a, n) -> out.put(key(a), n));
        return out;
    }

    private static ResourceKey<Aspect> key(String id) {
        return ResourceKey.create(AspectKeys.REGISTRY, Identifier.parse(id));
    }
}
