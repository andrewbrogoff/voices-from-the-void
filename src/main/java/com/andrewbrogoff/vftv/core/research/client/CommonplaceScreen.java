package com.andrewbrogoff.vftv.core.research.client;

import com.andrewbrogoff.vftv.core.research.CommonplaceMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The Commonplace Book's screen: an empty placeholder, so the server's {@link CommonplaceMenu}
 * always has a client screen that closes it.
 *
 * <p>ponytail: blank until the book UI ticket (T22) draws the spread, tabs and note view.
 */
public final class CommonplaceScreen extends AbstractContainerScreen<CommonplaceMenu> {
    /** The screen for an open book. */
    public CommonplaceScreen(CommonplaceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 320, 240);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {}
}
