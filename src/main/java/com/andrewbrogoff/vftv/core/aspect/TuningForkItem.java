package com.andrewbrogoff.vftv.core.aspect;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The Tuning Fork (aspects spec, "The Tuning Fork"): no durability, nothing consumed. Using it with
 * nothing targeted sounds for nodes; a right-click with it on the cursor strikes an inventory
 * stack. Blocks and entities are struck by the platform's right-click listeners, which take
 * priority over the target's own use.
 */
public final class TuningForkItem extends Item {
    /** The fork's tooltip, the inventory-strike instruction. */
    public static final String TOOLTIP = "tooltip.vftv.tuning_fork";

    /** A fork. */
    public TuningForkItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer server) {
            Strikes.sound(server, player.getItemInHand(hand));
        }
        return InteractionResult.SUCCESS;
    }

    /** The fork on the cursor, right-clicked onto a stack: strikes that stack. */
    @Override
    public boolean overrideStackedOnOther(
            ItemStack self, Slot slot, ClickAction action, Player player) {
        if (action != ClickAction.SECONDARY || slot.getItem().isEmpty()) {
            return false;
        }
        if (player instanceof ServerPlayer server
                && player.containerMenu.getCarried() == self
                && player.containerMenu.slots.contains(slot)
                && slot.isActive()) {
            Strikes.strikeStack(
                    server,
                    self,
                    SlotAccess.of(slot::getItem, slot::set),
                    stack -> slot.mayPickup(player) && slot.mayPlace(stack));
        }
        return true;
    }

    /** A stack on the cursor, right-clicked onto the fork: strikes the carried stack. */
    @Override
    public boolean overrideOtherStackedOnMe(
            ItemStack self,
            ItemStack other,
            Slot slot,
            ClickAction action,
            Player player,
            SlotAccess carried) {
        if (action != ClickAction.SECONDARY || other.isEmpty()) {
            return false;
        }
        if (player instanceof ServerPlayer server
                && player.containerMenu.slots.contains(slot)
                && slot.getItem() == self
                && slot.isActive()) {
            Strikes.strikeStack(server, self, carried, stack -> true);
        }
        return true;
    }
}
