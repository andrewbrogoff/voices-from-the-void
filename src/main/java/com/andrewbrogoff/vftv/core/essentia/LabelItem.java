package com.andrewbrogoff.vftv.core.essentia;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

/**
 * The label (essentia spec, "Labels"): its {@code vftv:label} component names an aspect, absent
 * meaning blank. Sneaking with a blank label on a jar copies the jar's aspect onto a new label.
 */
public final class LabelItem extends Item {
    /** A label, stacking to 64. */
    public LabelItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        ItemStack held = context.getItemInHand();
        if (player == null
                || !player.isSecondaryUseActive()
                || held.has(EssentiaContent.labelComponent())
                || !(context.getLevel().getBlockEntity(context.getClickedPos())
                        instanceof JarBlockEntity jar)
                || !jar.readable()
                || jar.busy()) {
            return InteractionResult.PASS;
        }
        ResourceKey<Aspect> x = jar.held() != null ? jar.held() : jar.label();
        if (x == null) {
            return InteractionResult.PASS;
        }
        if (!context.getLevel().isClientSide()) {
            if (!player.hasInfiniteMaterials()) {
                held.shrink(1);
            }
            player.getInventory().placeItemBackInInventory(Labels.labelStack(x));
        }
        return InteractionResult.SUCCESS;
    }
}
