package com.andrewbrogoff.vftv.core.text.client;

import com.andrewbrogoff.vftv.core.text.model.CaptionBand;
import com.andrewbrogoff.vftv.core.text.model.Palette;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

/**
 * R2 (accessibility, "Text overflow"): a one-line field with no widget of its own (a status, detail
 * or feedback line), clipped at a character boundary with "…". Its full text is its tooltip on
 * hover and on keyboard focus, narration reads the full text, and it is an inspection focus target
 * while it shows text.
 */
public final class ClippedLabel extends AbstractWidget {
    private static final int FOCUS = Palette.opaque(Palette.INK1);

    private final Font font;
    private final int colour;
    private FormattedCharSequence shown = FormattedCharSequence.EMPTY;

    /**
     * A field at (x, y), {@code width} wide and one line tall.
     *
     * @param colour the text's ARGB colour, drawn without a shadow
     */
    public ClippedLabel(int x, int y, int width, Font font, int colour) {
        super(x, y, width, CaptionBand.LINE, Component.empty());
        this.font = font;
        this.colour = colour;
    }

    @Override
    public void setMessage(Component message) {
        super.setMessage(message);
        shown = TextFit.visual(TextFit.clip(font, message, getWidth()));
        // The title narration already reads the full text; the tooltip adds no second reading.
        setTooltip(message.getString().isEmpty() ? null : Tooltip.create(message, null));
    }

    @Override
    public @Nullable ComponentPath nextFocusPath(FocusNavigationEvent navigationEvent) {
        return getMessage().getString().isEmpty() ? null : super.nextFocusPath(navigationEvent);
    }

    @Override
    protected void extractWidgetRenderState(
            GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.text(font, shown, getX(), getY(), colour, false);
        if (isFocused()) {
            graphics.outline(getX() - 1, getY() - 1, getWidth() + 2, getHeight() + 1, FOCUS);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
    }
}
