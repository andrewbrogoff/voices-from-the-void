package com.andrewbrogoff.vftv.core.text.client;

import com.andrewbrogoff.vftv.core.text.model.AccessibilityKeys;
import com.andrewbrogoff.vftv.core.text.model.CaptionBand;
import com.andrewbrogoff.vftv.core.text.model.CardLayout;
import com.andrewbrogoff.vftv.core.text.model.NumberFormats;
import com.andrewbrogoff.vftv.core.text.model.Palette;
import com.andrewbrogoff.vftv.core.text.model.SheetCursor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

/**
 * A crosshair card on the client (crucible, "The HUD", "Shared geometry"): the frame, the R3 wrap
 * of its head, tail and foot lines, {@code H′} above the caption band, the pose-scale fallback,
 * sheets with their indicator and keys, and the clamp. The owner supplies the content when its data
 * changes and draws the grid rows; the layout is rebuilt only when the content, the scaled screen,
 * the caption band's revision, the resources or the font options change, and drawing allocates
 * nothing.
 */
public final class CrosshairCard {
    /** The shared frame, the wands spec's nine-slice (16×16, border 4). */
    public static final Identifier FRAME = Identifier.fromNamespaceAndPath("vftv", "hud/vis_frame");

    private static final int TEXT = Palette.opaque(Palette.HUSH3);
    private static final FormattedCharSequence[] NONE = new FormattedCharSequence[0];

    private static @Nullable CrosshairCard shown;
    private static int shownTick = Integer.MIN_VALUE;

    /** The owner's body: its grid rows, drawn and narrated. */
    public interface Body {
        /**
         * Draws grid rows {@code [fromRow, toRow)}, each 18 px tall with cells at a pitch of the
         * cell width + 4, from (x, y) in the frame's unscaled space. Allocates nothing.
         */
        void draw(GuiGraphicsExtractor graphics, int fromRow, int toRow, int columns, int x, int y);

        /** The text of grid rows {@code [fromRow, toRow)}, narrated once on a sheet turn. */
        Component narrate(int fromRow, int toRow);
    }

    private final Body body;
    private final SheetCursor cursor = new SheetCursor();
    private List<Component> head = List.of();
    private List<Component> tail = List.of();
    private List<Component> foot = List.of();
    private int cells;
    private int cellWidth;
    private int contentSerial;

    private @Nullable CardLayout layout;
    private int layoutContent = -1;
    private int layoutWidth;
    private int layoutHeight;
    private int layoutBand;
    private int layoutFont;
    private FormattedCharSequence[] headLines = NONE;
    private FormattedCharSequence[] tailLines = NONE;
    private FormattedCharSequence[] footLines = NONE;

    // Each wrapped tail line's text, narrated with its sheet; one per component before a layout.
    private String[] tailText = new String[0];

    private @Nullable FormattedCharSequence indicator;

    /** A card drawing its grid through {@code body}. */
    public CrosshairCard(Body body) {
        this.body = body;
    }

    /**
     * Sets what the card shows; call when the owner's data changes, never per frame.
     *
     * @param target what the card describes (e.g. a block position); a different target resets the
     *     card to its first sheet
     * @param head lines repeated on every sheet, unwrapped
     * @param cells the grid's cell count
     * @param cellWidth one cell's width: 18 + the widest amount, measured with the active font
     * @param tail body lines after the grid (e.g. a "more" line), unwrapped
     * @param foot lines repeated on every sheet (e.g. a spill line), unwrapped
     */
    public void content(
            @Nullable Object target,
            List<Component> head,
            int cells,
            int cellWidth,
            List<Component> tail,
            List<Component> foot) {
        cursor.target(target);
        this.head = List.copyOf(head);
        this.cells = cells;
        this.cellWidth = cellWidth;
        this.tail = List.copyOf(tail);
        tailText = new String[0];
        this.foot = List.copyOf(foot);
        contentSerial++;
    }

    /** Draws the card at the crosshair; the owner calls it from its HUD layer while it shows. */
    public void draw(GuiGraphicsExtractor graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        CaptionBand band = StatusLineHud.BAND;
        int fontKey = TextClient.fontKey();
        CardLayout l = layout;
        if (l == null
                || layoutContent != contentSerial
                || layoutWidth != width
                || layoutHeight != height
                || layoutBand != band.revision()
                || layoutFont != fontKey) {
            l = relayout(font, width, height, band, fontKey);
        }
        FormattedCharSequence sheetLine = indicator;
        if (l.paged() && sheetLine == null) {
            sheetLine = TextFit.visual(indicatorText(cursor.sheet() + 1, l.sheets()));
            indicator = sheetLine;
        }
        shown = this;
        shownTick = TextClient.ticks();

        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(l.x(), l.y());
        pose.scale(l.scale(), l.scale());
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FRAME, 0, 0, l.width(), l.height());
        int x = CardLayout.PADDING;
        int y = CardLayout.PADDING;
        for (FormattedCharSequence line : headLines) {
            graphics.text(font, line, x, y, TEXT, false);
            y += CaptionBand.LINE;
        }
        int sheet = cursor.sheet();
        int from = l.sheetStart(sheet);
        int to = l.sheetEnd(sheet);
        int gridTo = Math.min(to, l.rows());
        if (from < gridTo) {
            body.draw(graphics, from, gridTo, l.columns(), x, y);
        }
        int lineY = y + CardLayout.ROW * Math.max(0, gridTo - from);
        for (int unit = Math.max(from, l.rows()); unit < to; unit++) {
            if (unit == l.rows()) {
                lineY += CardLayout.BLOCK_GAP;
            }
            graphics.text(font, tailLines[unit - l.rows()], x, lineY, TEXT, false);
            lineY += CaptionBand.LINE;
        }
        int footY = y + l.bodyHeight();
        if (footLines.length > 0) {
            footY += CardLayout.BLOCK_GAP;
            for (FormattedCharSequence line : footLines) {
                graphics.text(font, line, x, footY, TEXT, false);
                footY += CaptionBand.LINE;
            }
        }
        if (sheetLine != null && l.paged()) {
            graphics.text(font, sheetLine, x, footY + CardLayout.BLOCK_GAP, TEXT, false);
        }
        pose.popMatrix();
    }

    /** Turns the card drawn this tick or the last, if it pages; the sheet keys call this. */
    static void turnShown(int delta) {
        CrosshairCard card = shown;
        if (card != null && shownTick >= TextClient.ticks() - 1) {
            card.turn(delta);
        }
    }

    /** Turns one way, stopping at the ends; a turn narrates the new sheet's body once. */
    boolean turn(int delta) {
        CardLayout l = layout;
        if (l == null || !l.paged() || !cursor.turn(delta, l.sheets())) {
            return false;
        }
        indicator = null;
        int sheet = cursor.sheet();
        int from = l.sheetStart(sheet);
        int to = l.sheetEnd(sheet);
        MutableComponent text =
                from < l.rows()
                        ? body.narrate(from, Math.min(to, l.rows())).copy()
                        : Component.empty();
        for (int unit = Math.max(from, l.rows()); unit < to; unit++) {
            int line = unit - l.rows();
            Component segment =
                    line < tailText.length
                            ? Component.literal(tailText[line])
                            : line < tail.size() ? tail.get(line) : Component.empty();
            text.append(CommonComponents.SPACE).append(segment);
        }
        Narration.say(text);
        return true;
    }

    /** The current sheet, 0-based. */
    public int sheet() {
        return cursor.sheet();
    }

    /** Installs a layout directly, as a relayout would; for tests without a font. */
    void install(CardLayout l) {
        layout = l;
        cursor.clamp(l.sheets());
        indicator = null;
    }

    private CardLayout relayout(Font font, int width, int height, CaptionBand band, int fontKey) {
        int inner = CardLayout.maxInner(width);
        headLines = wrap(font, head, inner);
        List<FormattedText> wrappedTail = new ArrayList<>();
        for (Component line : tail) {
            wrappedTail.addAll(TextFit.wrap(font, line, inner));
        }
        tailLines = wrappedTail.stream().map(TextFit::visual).toArray(FormattedCharSequence[]::new);
        tailText = wrappedTail.stream().map(FormattedText::getString).toArray(String[]::new);
        footLines = wrap(font, foot, inner);
        CardLayout.Card card =
                new CardLayout.Card(
                        headLines.length,
                        widest(font, headLines),
                        cells,
                        cellWidth,
                        tailLines.length,
                        widest(font, tailLines),
                        footLines.length,
                        widest(font, footLines));
        CardLayout l =
                CardLayout.compute(
                        width,
                        height,
                        band.usableHeight(height),
                        card,
                        sheets -> font.width(indicatorText(sheets, sheets)));
        layoutContent = contentSerial;
        layoutWidth = width;
        layoutHeight = height;
        layoutBand = band.revision();
        layoutFont = fontKey;
        install(l);
        return l;
    }

    private static Component indicatorText(int sheet, int sheets) {
        return Component.translatable(
                AccessibilityKeys.CARD_SHEET,
                NumberFormats.integer(sheet),
                NumberFormats.integer(sheets),
                TextKeys.PREVIOUS_SHEET.getTranslatedKeyMessage(),
                TextKeys.NEXT_SHEET.getTranslatedKeyMessage());
    }

    private static FormattedCharSequence[] wrap(Font font, List<Component> lines, int width) {
        return lines.stream()
                .flatMap(line -> TextFit.wrap(font, line, width).stream())
                .map(TextFit::visual)
                .toArray(FormattedCharSequence[]::new);
    }

    private static int widest(Font font, FormattedCharSequence[] lines) {
        int widest = 0;
        for (FormattedCharSequence line : lines) {
            widest = Math.max(widest, font.width(line));
        }
        return widest;
    }
}
