package com.andrewbrogoff.vftv.core.text.client;

import com.andrewbrogoff.vftv.core.text.model.AccessibilityKeys;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * The accessibility key mappings (accessibility, "Lang keys"), rebindable in vanilla's Controls
 * screen and registered by the {@code neoforge.text.client} listener. Each acts only in the world,
 * with no screen open.
 */
public final class TextKeys {
    /** The mod's key-mapping category, labelled {@code key.category.vftv.main}. */
    public static final KeyMapping.Category CATEGORY =
            new KeyMapping.Category(Identifier.fromNamespaceAndPath("vftv", "main"));

    /** J: opens {@link StatusDetailsScreen} on the latest status line. */
    public static final KeyMapping STATUS_DETAILS =
            new KeyMapping(AccessibilityKeys.STATUS_DETAILS, InputConstants.KEY_J, CATEGORY);

    /** Page Up: the crosshair card's previous sheet. */
    public static final KeyMapping PREVIOUS_SHEET =
            new KeyMapping(AccessibilityKeys.PREVIOUS_SHEET, InputConstants.KEY_PAGEUP, CATEGORY);

    /** Page Down: the crosshair card's next sheet. */
    public static final KeyMapping NEXT_SHEET =
            new KeyMapping(AccessibilityKeys.NEXT_SHEET, InputConstants.KEY_PAGEDOWN, CATEGORY);

    /** Every mapping, for registration. */
    public static final List<KeyMapping> ALL = List.of(STATUS_DETAILS, PREVIOUS_SHEET, NEXT_SHEET);

    private TextKeys() {}

    /** Acts on this tick's presses; clicks made while a screen is open are dropped. */
    static void tick(Minecraft minecraft) {
        boolean inWorld = minecraft.gui.screen() == null && minecraft.level != null;
        while (STATUS_DETAILS.consumeClick()) {
            Component latest = StatusLineHud.latest();
            if (inWorld && latest != null) {
                minecraft.gui.setScreen(new StatusDetailsScreen(latest));
                inWorld = false;
            }
        }
        while (PREVIOUS_SHEET.consumeClick()) {
            if (inWorld) {
                CrosshairCard.turnShown(-1);
            }
        }
        while (NEXT_SHEET.consumeClick()) {
            if (inWorld) {
                CrosshairCard.turnShown(1);
            }
        }
    }
}
