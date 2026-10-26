package com.andrewbrogoff.vftv.core.warp.client;

import com.andrewbrogoff.vftv.core.text.client.StatusLineHud;
import com.andrewbrogoff.vftv.core.text.client.TextClient;
import com.andrewbrogoff.vftv.core.text.client.TextFit;
import com.andrewbrogoff.vftv.core.text.model.CaptionBand;
import com.andrewbrogoff.vftv.core.text.model.Palette;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

/**
 * The Voice text layer (warp spec, "Voice lines"; localisation, "Voice text"): the line centred in
 * the caption band above any status line, wrapped at 240 px and never cut, italic, amethyst for a
 * line with a Void segment and hush otherwise, on our own black backing at alpha 0.8 with 2 px of
 * padding. Text needing more than 4 lines is drawn at the scale that fits 4 lines' height. The
 * layout is cached, so a frame allocates nothing.
 */
public final class VoiceHud {
    private static final int BACKING_ALPHA = 204;

    private static @Nullable Component laidOut;
    private static int layoutFont = -1;
    private static FormattedCharSequence[] lines = new FormattedCharSequence[0];
    private static int[] widths = new int[0];
    private static int boxWidth;

    private VoiceHud() {}

    /** {@code vftv:voice_text}: draws the line, if one shows. */
    public static void layer(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        Component text = VoicesClient.text();
        if (text == null || minecraft.gui.hud.isHidden()) {
            return;
        }
        layout(minecraft.font, text);
        if (lines.length == 0) {
            return;
        }
        float fade =
                CaptionBand.fade(
                        VoicesClient.textTicks(), deltaTracker.getGameTimeDeltaPartialTick(false));
        int alpha = (int) (255 * fade);
        if (alpha < 4) {
            return;
        }
        CaptionBand band = StatusLineHud.BAND;
        int shown = Math.min(lines.length, CaptionBand.VOICE_MAX_LINES);
        // More than 4 wrapped lines: scale so all of them fit 4 lines' height, never cut.
        float scale = shown * 1F / lines.length * band.voiceScale();
        int height = CaptionBand.PADDING + CaptionBand.LINE * lines.length;
        int centre = graphics.guiWidth() / 2;
        graphics.pose().pushMatrix();
        graphics.pose().translate(centre, band.voiceBottom());
        graphics.pose().scale(scale, scale);
        graphics.fill(-boxWidth / 2, -height, boxWidth / 2, 0, (BACKING_ALPHA * alpha / 255) << 24);
        int colour = (alpha << 24) | (VoicesClient.textVoid() ? Palette.AMETHYST3 : Palette.HUSH3);
        for (int i = 0; i < lines.length; i++) {
            graphics.text(
                    minecraft.font,
                    lines[i],
                    -widths[i] / 2,
                    -height + 2 + CaptionBand.LINE * i,
                    colour,
                    true);
        }
        graphics.pose().popMatrix();
    }

    private static void layout(Font font, Component text) {
        int fontKey = TextClient.fontKey();
        if (text == laidOut && fontKey == layoutFont) {
            return;
        }
        laidOut = text;
        layoutFont = fontKey;
        List<FormattedText> wrapped =
                TextFit.wrap(font, text.copy().withStyle(Style.EMPTY.withItalic(true)), VoicesClient.TEXT_WIDTH);
        lines = new FormattedCharSequence[wrapped.size()];
        widths = new int[wrapped.size()];
        int widest = 0;
        for (int i = 0; i < lines.length; i++) {
            lines[i] = TextFit.visual(wrapped.get(i));
            widths[i] = font.width(lines[i]);
            widest = Math.max(widest, widths[i]);
        }
        boxWidth = widest + CaptionBand.PADDING;
    }
}
