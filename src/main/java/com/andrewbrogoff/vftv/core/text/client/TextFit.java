package com.andrewbrogoff.vftv.core.text.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/**
 * The shared text-overflow measuring (accessibility, "Text overflow"): every width is the active
 * font's, styles included. Call these when an input changes and cache the result; never per frame.
 */
public final class TextFit {
    /** R6: the width mod-owned tooltips wrap at, vanilla widgets' own. */
    public static final int TOOLTIP_WIDTH = 170;

    private static final FormattedText ELLIPSIS = FormattedText.of("…");

    private TextFit() {}

    /** R3: wraps {@code text} to {@code width} in logical order; nothing is ever cut. */
    public static List<FormattedText> wrap(Font font, FormattedText text, int width) {
        return font.getSplitter().splitLines(text, Math.max(1, width), Style.EMPTY);
    }

    /**
     * R2: {@code text} on one line, clipped at a character boundary so that it plus "…" fits {@code
     * width}; unchanged when it fits. The caller shows the full text as a tooltip and narrates it
     * in full.
     */
    public static FormattedText clip(Font font, FormattedText text, int width) {
        return font.width(text) <= width ? text : ellipsize(font, text, width);
    }

    /**
     * {@code text} ending in "…", clipped at a character boundary so that the whole fits {@code
     * width}: R5's second line when a status line shows only two of its lines.
     */
    public static FormattedText ellipsize(Font font, FormattedText text, int width) {
        FormattedText head = font.substrByWidth(text, Math.max(0, width - font.width(ELLIPSIS)));
        return FormattedText.composite(head, ELLIPSIS);
    }

    /** Whether {@link #clip} would cut {@code text}. */
    public static boolean clipped(Font font, FormattedText text, int width) {
        return font.width(text) > width;
    }

    /** R6: mod-owned tooltip lines, each split at 170 px keeping its components and styles. */
    public static List<FormattedCharSequence> tooltip(
            Font font, List<? extends FormattedText> lines) {
        List<FormattedCharSequence> out = new ArrayList<>();
        for (FormattedText line : lines) {
            out.addAll(font.split(line, TOOLTIP_WIDTH));
        }
        return out;
    }

    /** One logical line in visual order, ready to draw. */
    public static FormattedCharSequence visual(FormattedText text) {
        return Language.getInstance().getVisualOrder(text);
    }
}
