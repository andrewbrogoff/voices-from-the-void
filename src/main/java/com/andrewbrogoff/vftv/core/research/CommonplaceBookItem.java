package com.andrewbrogoff.vftv.core.research;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * The Commonplace Book (research spec, "The item"): a viewer of its holder's own research. Using
 * it, in either hand, opens {@link CommonplaceMenu}; a book given to another player opens their
 * notebook.
 */
public final class CommonplaceBookItem extends Item {
    /** A book. */
    public CommonplaceBookItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer server) {
            open(server);
        }
        return InteractionResult.SUCCESS;
    }

    /** Opens the player's notebook and sends its first quotes. */
    public static void open(ServerPlayer player) {
        player.openMenu(
                new SimpleMenuProvider(
                        (id, inventory, p) -> new CommonplaceMenu(id, inventory),
                        Component.translatable("item.vftv.commonplace_book")));
        if (player.containerMenu instanceof CommonplaceMenu menu) {
            menu.quote(player, true);
        }
    }
}
