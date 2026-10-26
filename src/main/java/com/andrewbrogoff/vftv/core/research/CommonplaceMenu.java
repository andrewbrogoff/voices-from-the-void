package com.andrewbrogoff.vftv.core.research;

import com.andrewbrogoff.vftv.core.common.model.CheckedCommit;
import com.andrewbrogoff.vftv.core.warp.WarpAttachment;
import com.andrewbrogoff.vftv.core.warp.WarpRecord;
import com.andrewbrogoff.vftv.core.warp.WarpRulesService;
import com.andrewbrogoff.vftv.core.warp.WarpService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The Commonplace Book's server menu (research spec, "The item", "Sync and state"): no slots; it
 * stays valid while the book is in a hand and the player is alive, and is the context every {@link
 * ResearchAction} names by its container id. It owns the open book's quotes and their surface
 * revision (conventions, "The revision"): an O(1) change check every tick compares the quote's
 * inputs (the research and warp records, the warp rules, the tuning, the server switch, the Listen
 * rite's cooldown and the offer's expiry), and a
 * changed price or entry set bumps the revision and re-sends the quotes.
 */
public final class CommonplaceMenu extends AbstractContainerMenu {
    private final Player owner;
    private int revision;
    private ResearchFeedback.@Nullable Quotes sent;
    private @Nullable Inputs seen;

    /** A menu for the player whose inventory this is. */
    public CommonplaceMenu(int containerId, Inventory inventory) {
        super(ResearchItems.menu(), containerId);
        owner = inventory.player;
    }

    /** The open book's surface revision. */
    public int revision() {
        return revision;
    }

    /** The quotes last sent, or null before the first. */
    public ResearchFeedback.@Nullable Quotes quotes() {
        return sent;
    }

    /**
     * The change check: rebuilds the quotes when an input changed, and sends them with the next
     * revision when their prices or entries differ from what was sent, or always when forced (the
     * menu opening, a changed {@code research.mode}, a {@code STALE} refusal).
     */
    public void quote(ServerPlayer player, boolean force) {
        Inputs now = Inputs.of(player);
        if (!force && sent != null && now.same(seen)) {
            return;
        }
        seen = now;
        ResearchFeedback.Quotes fresh = ResearchActions.quotes(player, revision);
        if (force || sent == null || !fresh.samePrices(sent)) {
            revision = CheckedCommit.bump(revision);
            sent =
                    new ResearchFeedback.Quotes(
                            revision, fresh.mode(), fresh.entries(), fresh.offer(), fresh.rite());
            ResearchActions.send(player, sent);
        }
    }

    /** The per-tick change check: vanilla broadcasts the open menu once a tick. */
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (owner instanceof ServerPlayer player) {
            quote(player, false);
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive()
                && (book(player.getMainHandItem()) || book(player.getOffhandItem()));
    }

    private static boolean book(ItemStack stack) {
        return stack.is(ResearchItems.commonplaceBook());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    /** What the quotes are made from, compared by identity: each change replaces its instance. */
    private record Inputs(
            Object research,
            Object warp,
            Object rules,
            Object tuning,
            Object config,
            boolean riteReady,
            boolean offerLive) {
        static Inputs of(ServerPlayer player) {
            WarpAttachment warp = WarpService.get(player);
            WarpRecord r = warp.readable();
            long g = player.level().getServer().overworld().getGameTime();
            WarpAttachment.Offer offer = warp.offer();
            return new Inputs(
                    ResearchService.get(player),
                    warp,
                    WarpRulesService.current(),
                    ResearchService.tuning(),
                    ResearchService.config(),
                    r != null && WarpService.riteReady(r, g, WarpRulesService.current()),
                    offer != null && g < offer.expires());
        }

        @SuppressWarnings("ReferenceEquality") // the O(1) change check compares instances
        boolean same(@Nullable Inputs other) {
            return other != null
                    && research == other.research
                    && warp == other.warp
                    && rules == other.rules
                    && tuning == other.tuning
                    && config == other.config
                    && riteReady == other.riteReady
                    && offerLive == other.offerLive;
        }
    }
}
