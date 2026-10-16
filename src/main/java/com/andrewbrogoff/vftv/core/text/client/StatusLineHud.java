package com.andrewbrogoff.vftv.core.text.client;

import com.andrewbrogoff.vftv.core.text.model.AccessibilityKeys;
import com.andrewbrogoff.vftv.core.text.model.CaptionBand;
import com.andrewbrogoff.vftv.core.text.model.Palette;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

/**
 * The status line on the client (accessibility, R5) and the caption band it shares with the Voice
 * text. Two HUD layers: {@code vftv:caption_band}, which draws nothing and publishes {@link #BAND}
 * once per frame, and {@code vftv:status} above it. The layout is built when a line arrives, the
 * resources or font options change, or the scaled screen changes; drawing allocates nothing.
 */
public final class StatusLineHud {
    /** The published caption band; every crosshair card reads it before its layout. */
    public static final CaptionBand BAND = new CaptionBand();

    /** Text wraps at {@code min(300, W − 12)}. */
    private static final int MAX_WIDTH = 300;

    /** The backing: black at alpha 0.8. */
    private static final int BACKING_ALPHA = 204;

    private static @Nullable Component latest;
    private static int remaining;
    private static int serial;
    private static int voiceLines;
    private static int voiceSerial;

    private static int layoutSerial = -1;
    private static int layoutWidth = -1;
    private static int layoutFont = -1;
    private static FormattedCharSequence[] lines = new FormattedCharSequence[0];
    private static int[] widths = new int[0];
    private static int boxWidth;

    private StatusLineHud() {}

    /**
     * A line arrived: it replaces the current one at once, stays {@code ceil(60 × max(1,
     * notificationDisplayTime))} ticks and is narrated once, in full.
     */
    public static void arrive(Component text, double notificationDisplayTime) {
        latest = text;
        remaining = CaptionBand.hold(CaptionBand.STATUS_TICKS, notificationDisplayTime);
        serial++;
        Narration.say(text);
    }

    /** Counts the line down; called once per client tick. */
    static void tick() {
        if (remaining > 0 && --remaining == 0) {
            serial++;
        }
    }

    /** Forgets the line, the latest included: at a level change and at disconnect. */
    static void clear() {
        latest = null;
        remaining = 0;
        serial++;
    }

    /** The latest line, kept after it fades for the details view; null after a level change. */
    public static @Nullable Component latest() {
        return latest;
    }

    /** Whether the line is still on screen (before F1). */
    public static boolean showing() {
        return remaining > 0;
    }

    /**
     * The Voice text's input to the band (warp's, as plain numbers): its wrapped line count, 0 when
     * none shows or {@code voices.text} hides it. Call on each arrival, replacement and expiry.
     */
    public static void voice(int wrappedLines) {
        voiceLines = wrappedLines;
        voiceSerial++;
    }

    /** {@code vftv:caption_band}: computes and publishes the band; draws nothing. */
    public static void bandLayer(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        Hud hud = minecraft.gui.hud;
        boolean hidden = hud.isHidden();
        int status = !hidden && remaining > 0 ? layout(minecraft.font, graphics.guiWidth()) : 0;
        BAND.update(
                graphics.guiHeight(),
                hud.leftHeight,
                hud.rightHeight,
                status,
                serial,
                hidden ? 0 : voiceLines,
                voiceSerial);
    }

    /** {@code vftv:status}: the backed, centred, wrapped line, its bottom on the band's bottom. */
    public static void statusLayer(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (remaining <= 0 || minecraft.gui.hud.isHidden() || lines.length == 0) {
            return;
        }
        float fade = CaptionBand.fade(remaining, deltaTracker.getGameTimeDeltaPartialTick(false));
        int alpha = (int) (255 * fade);
        if (alpha < 4) {
            return;
        }
        int centre = graphics.guiWidth() / 2;
        int bottom = BAND.bottom();
        int top = bottom - CaptionBand.PADDING - CaptionBand.LINE * lines.length;
        int left = centre - boxWidth / 2;
        graphics.fill(left, top, left + boxWidth, bottom, (BACKING_ALPHA * alpha / 255) << 24);
        int colour = (alpha << 24) | Palette.HUSH3;
        for (int i = 0; i < lines.length; i++) {
            graphics.text(
                    minecraft.font,
                    lines[i],
                    centre - widths[i] / 2,
                    top + 2 + CaptionBand.LINE * i,
                    colour,
                    true);
        }
    }

    /** Rebuilds the layout if an input changed; returns the visible line count. */
    private static int layout(Font font, int guiWidth) {
        Component text = latest;
        int fontKey = TextClient.fontKey();
        if (text == null) {
            return 0;
        }
        if (layoutSerial == serial && layoutWidth == guiWidth && layoutFont == fontKey) {
            return lines.length;
        }
        layoutSerial = serial;
        layoutWidth = guiWidth;
        layoutFont = fontKey;
        int width = Math.min(MAX_WIDTH, guiWidth - 12);
        List<FormattedText> wrapped = TextFit.wrap(font, text, width);
        int shown = CaptionBand.statusVisibleLines(wrapped.size());
        FormattedText[] logical = new FormattedText[shown];
        if (CaptionBand.statusTruncated(wrapped.size())) {
            logical[0] = wrapped.get(0);
            logical[1] = TextFit.ellipsize(font, wrapped.get(1), width);
            logical[2] =
                    Component.translatable(
                            AccessibilityKeys.STATUS_MORE,
                            TextKeys.STATUS_DETAILS.getTranslatedKeyMessage());
        } else {
            for (int i = 0; i < shown; i++) {
                logical[i] = wrapped.get(i);
            }
        }
        lines = new FormattedCharSequence[shown];
        widths = new int[shown];
        int widest = 0;
        for (int i = 0; i < shown; i++) {
            lines[i] = TextFit.visual(logical[i]);
            widths[i] = font.width(lines[i]);
            widest = Math.max(widest, widths[i]);
        }
        boxWidth = widest + CaptionBand.PADDING;
        return shown;
    }
}
