package com.andrewbrogoff.vftv.core.text.client;

import com.andrewbrogoff.vftv.core.text.model.CaptionBand;
import com.andrewbrogoff.vftv.core.text.model.Palette;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractTextAreaWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * R1 (accessibility, "Text overflow"): multi-line text in a clipped, vertically scrolling viewport.
 * Nothing is cut and nothing pages automatically. It is a focus target: Up and Down scroll one
 * line, Home and End go to the ends, the wheel scrolls it when hovered, and it narrates its full
 * text. The scrollbar sits 2 px right of the text column.
 *
 * <p>The scrollbar sprites are vanilla's until the research spec's sprites exist (the book ticket
 * swaps {@link #SCROLLBAR}).
 */
public final class TextViewport extends AbstractTextAreaWidget {
    /** The scrollbar: one line per step. */
    public static final AbstractScrollArea.ScrollbarSettings SCROLLBAR =
            AbstractScrollArea.defaultSettings(CaptionBand.LINE);

    private static final int FOCUS = Palette.opaque(Palette.INK1);

    private final Font font;
    private final int colour;
    private List<FormattedCharSequence> lines = List.of();

    /**
     * A viewport at (x, y) whose text column is {@code width} × {@code height}.
     *
     * @param colour the text's ARGB colour, drawn without a shadow
     */
    public TextViewport(
            int x, int y, int width, int height, Component text, Font font, int colour) {
        super(x, y, width, height, text, SCROLLBAR, false, true);
        this.font = font;
        this.colour = colour;
        setMessage(text);
    }

    @Override
    public void setMessage(Component message) {
        super.setMessage(message);
        lines = font.split(message, getWidth());
        refreshScrollAmount();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_HOME) {
            setScrollAmount(0);
            return true;
        }
        if (event.key() == InputConstants.KEY_END) {
            setScrollAmount(maxScrollAmount());
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    protected int innerPadding() {
        return 0;
    }

    @Override
    protected int getInnerHeight() {
        return lines.size() * CaptionBand.LINE;
    }

    @Override
    protected int scrollBarX() {
        return getRight() + 2;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        for (int i = 0; i < lines.size(); i++) {
            int top = getY() + i * CaptionBand.LINE;
            if (withinContentAreaTopBottom(top, top + CaptionBand.LINE)) {
                graphics.text(font, lines.get(i), getX(), top, colour, false);
            }
        }
    }

    /** The focus state differs from idle by shape: a 1-px {@code ink1} outline. */
    @Override
    protected void extractDecorations(GuiGraphicsExtractor graphics) {
        if (isFocused()) {
            graphics.outline(getX() - 1, getY() - 1, getWidth() + 2, getHeight() + 2, FOCUS);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
    }
}
