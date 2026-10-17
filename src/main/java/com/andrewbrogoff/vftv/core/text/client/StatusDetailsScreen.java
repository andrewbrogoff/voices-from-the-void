package com.andrewbrogoff.vftv.core.text.client;

import com.andrewbrogoff.vftv.core.text.model.AccessibilityKeys;
import com.andrewbrogoff.vftv.core.text.model.Palette;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * The status line's details view (accessibility, R5): the whole of the latest status line, which
 * can be read after it has faded. A 240 × 120 panel, centred: the title at (8, 6), the body (an R1
 * viewport) at (8, 20), 220 × 72, with its scrollbar at (230, 20), and Close, 54 × 18, at (93, 96).
 * Focus starts on the body, then Close; Esc closes. It narrates the title and the full text.
 *
 * <p>The panel is drawn in palette fills until the research spec's confirm-panel and button sprites
 * exist: {@code ink1} text on a {@code brass5} body (12.17:1).
 */
public final class StatusDetailsScreen extends Screen {
    private static final int WIDTH = 240;
    private static final int HEIGHT = 120;
    private static final int INK = Palette.opaque(Palette.INK1);
    private static final int BODY = Palette.opaque(Palette.BRASS5);

    private final Component text;
    private int left;
    private int top;
    private FormattedCharSequence titleLine = FormattedCharSequence.EMPTY;

    /** A view of {@code text}, the latest status line. */
    public StatusDetailsScreen(Component text) {
        super(Component.translatable(AccessibilityKeys.STATUS_DETAILS_TITLE));
        this.text = text;
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        TextViewport body = new TextViewport(left + 8, top + 20, 220, 72, text, font, INK);
        addRenderableWidget(body);
        addRenderableWidget(
                Button.builder(Component.translatable(AccessibilityKeys.CLOSE), button -> onClose())
                        .bounds(left + 93, top + 96, 54, 18)
                        .build());
        if (TextFit.clipped(font, title, WIDTH - 16)) {
            // R2: a clipped title shows its full text on hover and focus, after the frozen order.
            ClippedLabel label = new ClippedLabel(left + 8, top + 6, WIDTH - 16, font, INK);
            label.setMessage(title);
            addRenderableWidget(label);
            titleLine = FormattedCharSequence.EMPTY;
        } else {
            titleLine = TextFit.visual(title);
        }
        setInitialFocus(body);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        graphics.fill(left, top, left + WIDTH, top + HEIGHT, INK);
        graphics.fill(left + 1, top + 1, left + WIDTH - 1, top + HEIGHT - 1, BODY);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.text(font, titleLine, left + 8, top + 6, INK, false);
        super.extractRenderState(graphics, mouseX, mouseY, a);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
