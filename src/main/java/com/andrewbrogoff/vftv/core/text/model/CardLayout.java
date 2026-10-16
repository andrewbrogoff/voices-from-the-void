package com.andrewbrogoff.vftv.core.text.model;

import static java.lang.Math.ceilDiv;

import java.util.Arrays;
import java.util.function.IntUnaryOperator;

/**
 * The shared crosshair-card geometry (crucible, "The HUD": frame, wrap widths, {@code H′},
 * pose-scale fallback, sheets and clamp), which the crucible, infusion, golem, Spectacles and
 * strike cards reuse unchanged. All sizes are GUI-scaled pixels; the caller measures text with the
 * active font and passes widths in.
 *
 * <p>A card has a <b>head</b> (lines repeated on every sheet), a <b>body</b> (a grid of 16-px cells
 * in 18-px rows, then tail lines such as a "more" line) and a <b>foot</b> (lines repeated on every
 * sheet). The body is a sequence of <i>units</i>: each grid row, then each tail line. When the pose
 * scale would fall below 0.75 the body is split into sheets of whole units, every sheet repeats the
 * head and foot and ends with the sheet indicator, and every sheet draws at sheet 1's size.
 */
public final class CardLayout {
    /** The frame's padding, on each side. */
    public static final int PADDING = 4;

    /** A grid row's pitch. */
    public static final int ROW = 18;

    /** The horizontal gap between cells. */
    public static final int CELL_GAP = 4;

    /** The gap above the tail, the foot and the sheet indicator. */
    public static final int BLOCK_GAP = 2;

    /** The lowest pose scale before a card pages, as a fraction. */
    private static final int MIN_NUM = 3;

    private static final int MIN_DEN = 4;

    /**
     * What a card holds, measured.
     *
     * @param headLines the head's wrapped line count
     * @param headWidth the widest head line
     * @param cells the number of grid cells drawn
     * @param cellWidth one cell's width: the 16-px icon, a 2-px gap and the widest amount
     * @param tailLines the tail's wrapped line count (0 for none)
     * @param tailWidth the widest tail line
     * @param footLines the foot's wrapped line count (0 for none)
     * @param footWidth the widest foot line
     */
    public record Card(
            int headLines,
            int headWidth,
            int cells,
            int cellWidth,
            int tailLines,
            int tailWidth,
            int footLines,
            int footWidth) {}

    private final int columns;
    private final int rows;
    private final int tailLines;
    private final int width;
    private final int height;
    private final int bodyHeight;
    private final int scaleNum;
    private final int scaleDen;
    private final int x;
    private final int y;
    private final int[] sheetStarts;
    private final int units;

    private CardLayout(
            int columns,
            int rows,
            int tailLines,
            int width,
            int height,
            int bodyHeight,
            int scaleNum,
            int scaleDen,
            int x,
            int y,
            int[] sheetStarts) {
        this.columns = columns;
        this.rows = rows;
        this.tailLines = tailLines;
        this.width = width;
        this.height = height;
        this.bodyHeight = bodyHeight;
        this.scaleNum = scaleNum;
        this.scaleDen = scaleDen;
        this.x = x;
        this.y = y;
        this.sheetStarts = sheetStarts;
        this.units = rows + tailLines;
    }

    /**
     * The widest inner width a card's text wraps to: {@code W − 12} (a 2-px screen margin and the
     * 4-px padding on each side).
     */
    public static int maxInner(int screenWidth) {
        return screenWidth - 12;
    }

    /**
     * Lays a card out.
     *
     * @param screenWidth the scaled screen width {@code W}
     * @param screenHeight the scaled screen height {@code H}
     * @param usable {@code H′}, from {@link CaptionBand#usableHeight}
     * @param card the measured content
     * @param indicatorWidth the sheet indicator's width for a given sheet count, measured with its
     *     widest sheet number; asked only when the card pages
     */
    public static CardLayout compute(
            int screenWidth,
            int screenHeight,
            int usable,
            Card card,
            IntUnaryOperator indicatorWidth) {
        int n = card.cells();
        int columns = 0;
        if (n > 0) {
            int pitch = card.cellWidth() + CELL_GAP;
            int maxFit = Math.max(1, (maxInner(screenWidth) + CELL_GAP) / pitch);
            int hi = Math.min(n, maxFit);
            columns = hi;
            for (int c = Math.min(Math.min(n, 4), maxFit); c <= hi; c++) {
                if (height(card, ceilDiv(n, c)) <= usable - 4) {
                    columns = c;
                    break;
                }
            }
        }
        int rows = columns == 0 ? 0 : ceilDiv(n, columns);
        int gridWidth = columns == 0 ? 0 : columns * card.cellWidth() + (columns - 1) * CELL_GAP;
        int textWidth =
                Math.max(
                        card.headLines() > 0 ? card.headWidth() : 0,
                        Math.max(
                                card.tailLines() > 0 ? card.tailWidth() : 0,
                                card.footLines() > 0 ? card.footWidth() : 0));
        int width = 2 * PADDING + Math.max(textWidth, gridWidth);
        int height = height(card, rows);
        int[] scale = scale(screenWidth, usable, width, height);
        int units = rows + card.tailLines();
        int[] starts = new int[Math.max(1, units)];
        int sheets = 1;
        int fixed = fixedHeight(card);
        int perSheet = Math.max(1, Math.floorDiv(4 * (usable - 4) - 3 * fixed, 3 * ROW));
        int capacity = perSheet * ROW;
        if (scale[0] * MIN_DEN < MIN_NUM * scale[1]) {
            int used = 0;
            for (int u = 0; u < units; u++) {
                int h = unitHeight(u, rows);
                if (u > 0 && used + h > capacity) {
                    starts[sheets++] = u;
                    used = 0;
                }
                used += h;
            }
        }
        if (sheets == 1) {
            return place(
                    screenWidth,
                    screenHeight,
                    usable,
                    columns,
                    rows,
                    card,
                    width,
                    height,
                    scale,
                    new int[] {0});
        }
        int[] sheetStarts = Arrays.copyOf(starts, sheets);
        int sheetHeight = fixed + capacity;
        int sheetWidth = Math.max(width, 2 * PADDING + indicatorWidth.applyAsInt(sheets));
        return place(
                screenWidth,
                screenHeight,
                usable,
                columns,
                rows,
                card,
                sheetWidth,
                sheetHeight,
                scale(screenWidth, usable, sheetWidth, sheetHeight),
                sheetStarts);
    }

    private static CardLayout place(
            int screenWidth,
            int screenHeight,
            int usable,
            int columns,
            int rows,
            Card card,
            int width,
            int height,
            int[] scale,
            int[] sheetStarts) {
        int drawnWidth = ceilDiv(width * scale[0], scale[1]);
        int drawnHeight = ceilDiv(height * scale[0], scale[1]);
        int x = Math.max(2, Math.min(screenWidth / 2 + 8, screenWidth - drawnWidth - 2));
        int y = Math.max(2, Math.min(screenHeight / 2 + 8, usable - drawnHeight - 2));
        int bodyHeight =
                sheetStarts.length > 1
                        ? height - fixedHeight(card)
                        : ROW * rows + block(card.tailLines());
        return new CardLayout(
                columns,
                rows,
                card.tailLines(),
                width,
                height,
                bodyHeight,
                scale[0],
                scale[1],
                x,
                y,
                sheetStarts);
    }

    /** The unpaged frame height for a grid of {@code rows}. */
    private static int height(Card card, int rows) {
        return 2 * PADDING
                + CaptionBand.LINE * card.headLines()
                + ROW * rows
                + block(card.tailLines())
                + block(card.footLines());
    }

    /** A sheet's height without its body: frame, head, foot and the indicator line. */
    private static int fixedHeight(Card card) {
        return 2 * PADDING
                + CaptionBand.LINE * card.headLines()
                + block(card.footLines())
                + BLOCK_GAP
                + CaptionBand.LINE;
    }

    private static int block(int lines) {
        return lines > 0 ? BLOCK_GAP + CaptionBand.LINE * lines : 0;
    }

    private static int unitHeight(int unit, int rows) {
        if (unit < rows) {
            return ROW;
        }
        return unit == rows ? BLOCK_GAP + CaptionBand.LINE : CaptionBand.LINE;
    }

    /** {@code min(1, (W − 4) / width, (H′ − 4) / height)} as an exact fraction {num, den}. */
    private static int[] scale(int screenWidth, int usable, int width, int height) {
        int num = 1;
        int den = 1;
        int wNum = Math.max(1, screenWidth - 4);
        if ((long) wNum * den < (long) num * width) {
            num = wNum;
            den = width;
        }
        int hNum = Math.max(1, usable - 4);
        if ((long) hNum * den < (long) num * height) {
            num = hNum;
            den = height;
        }
        return new int[] {num, den};
    }

    /** The grid's columns, 0 with no cells. */
    public int columns() {
        return columns;
    }

    /** The grid's rows, 0 with no cells. */
    public int rows() {
        return rows;
    }

    /** The frame's width, unscaled. */
    public int width() {
        return width;
    }

    /** The frame's height, unscaled; a paged card's is sheet 1's. */
    public int height() {
        return height;
    }

    /**
     * The body area's height: the drawn body unpaged, or a sheet's whole body capacity paged, so
     * the foot and indicator sit at the same place on every sheet.
     */
    public int bodyHeight() {
        return bodyHeight;
    }

    /** The pose scale, at most 1. */
    public float scale() {
        return scaleNum / (float) scaleDen;
    }

    /** The drawn width, rounded up so a fractional edge never eats the margin. */
    public int drawnWidth() {
        return ceilDiv(width * scaleNum, scaleDen);
    }

    /** The drawn height, rounded up. */
    public int drawnHeight() {
        return ceilDiv(height * scaleNum, scaleDen);
    }

    /** The frame's left edge, clamped 2 px inside the screen. */
    public int x() {
        return x;
    }

    /** The frame's top edge, clamped 2 px inside the screen and above the caption band. */
    public int y() {
        return y;
    }

    /** The number of sheets: 1 unless the card pages. */
    public int sheets() {
        return sheetStarts.length;
    }

    /** Whether the card pages, and so draws the sheet indicator. */
    public boolean paged() {
        return sheetStarts.length > 1;
    }

    /** The body units: grid rows then tail lines. */
    public int units() {
        return units;
    }

    /** The tail's line count. */
    public int tailLines() {
        return tailLines;
    }

    /** The first body unit on {@code sheet} (0-based). */
    public int sheetStart(int sheet) {
        return sheetStarts[sheet];
    }

    /** One past the last body unit on {@code sheet}. */
    public int sheetEnd(int sheet) {
        return sheet + 1 < sheetStarts.length ? sheetStarts[sheet + 1] : units;
    }
}
