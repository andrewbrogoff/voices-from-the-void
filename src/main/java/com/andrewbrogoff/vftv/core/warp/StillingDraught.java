package com.andrewbrogoff.vftv.core.warp;

import com.andrewbrogoff.vftv.api.warp.CommitResult;
import com.andrewbrogoff.vftv.api.warp.WarpCost;
import com.andrewbrogoff.vftv.api.warp.WarpSourceKind;
import com.andrewbrogoff.vftv.core.text.StatusLines;
import com.andrewbrogoff.vftv.core.text.model.SharedMessages;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * {@code vftv:stilling_draught} (warp spec, "Stilling"): drunk over 32 ticks, it always stills
 * {@code still.amount} lasting warp, or what is left. Starting to drink is refused on both sides
 * when the synced lasting warp is 0; on finishing, the server reads it again and applies the
 * stilling, consuming the draught only when it went through.
 */
public final class StillingDraught extends Item {
    /** "Nothing in me to still." */
    public static final String NOTHING = "message.vftv.warp.nothing_to_still";

    /** The tooltip: the amount stilled and the reader's lasting warp. */
    public static final String TOOLTIP = "tooltip.vftv.stilling_draught";

    /** The drinking time in ticks. */
    public static final int DRINK_TICKS = 32;

    /** A draught with {@code properties}. */
    public StillingDraught(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (lasting(player) <= 0) {
            if (player instanceof ServerPlayer server) {
                StatusLines.send(server, Component.translatable(NOTHING));
            }
            return InteractionResult.FAIL;
        }
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) {
            return stack;
        }
        int lasting = lasting(player);
        if (lasting <= 0) {
            StatusLines.send(player, Component.translatable(NOTHING));
            return stack;
        }
        int amount = Math.min(WarpRulesService.current().stillAmount(), lasting);
        CommitResult result =
                WarpService.apply(
                        player,
                        new WarpCost(0, -amount, 0),
                        WarpService.source(WarpSourceKind.STILLING));
        if (result.status() != CommitResult.Status.OK) {
            StatusLines.send(
                    player,
                    Component.translatable(
                            result.status() == CommitResult.Status.BUSY
                                    ? SharedMessages.BUSY
                                    : SharedMessages.UNREADABLE));
            return stack;
        }
        VoiceChecks.playToOwner(player, WarpContent.sound(WarpContent.STILLING_DRINK_END));
        if (player.hasInfiniteMaterials()) {
            return stack;
        }
        return ItemUtils.createFilledResult(
                stack, player, new ItemStack(Items.GLASS_BOTTLE), false);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return DRINK_TICKS;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.DRINK;
    }

    /** The holder's lasting warp: the synced copy on a client, 0 when unreadable. */
    private static int lasting(Player player) {
        WarpRecord r = WarpService.get(player).readable();
        return r == null ? 0 : r.lasting();
    }
}
