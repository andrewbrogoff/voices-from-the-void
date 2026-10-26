package com.andrewbrogoff.vftv.core.arcane.client;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.api.vis.VisQuote;
import com.andrewbrogoff.vftv.core.arcane.ArcaneLang;
import com.andrewbrogoff.vftv.core.aspect.AspectIcons;
import com.andrewbrogoff.vftv.core.aspect.AspectLang;
import com.andrewbrogoff.vftv.core.text.client.TextFit;
import com.andrewbrogoff.vftv.core.text.model.NumberFormats;
import com.andrewbrogoff.vftv.core.text.model.Palette;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

/**
 * The shared cost-cell rule (arcane spec, "The screen"; accessibility, R4): a 40x18 cell with the
 * aspect's icon at (1, 1) and the cost right-aligned to x = 38 at y = 5 in the number field x =
 * 18...38, drawn at min(1, 20 / its width) so it never reaches the icon. A cost above 9,999 draws
 * the {@code beyond} glyph at (29, 4). The cell is {@code cost_cell_short} when the wand's pool is
 * below the effective cost. The tooltip is always exact and unscaled. The Worktable, the Lathe and
 * JEI's arcane and crucible categories draw their cells with this class.
 */
public final class CostCells {
    /** A cell's width. */
    public static final int WIDTH = 40;

    /** A cell's height. */
    public static final int HEIGHT = 18;

    /** The highest cost drawn as digits. */
    public static final int MAX_DIGITS_COST = 9_999;

    /** The plain cell. */
    public static final Identifier CELL = sprite("cost_cell");

    /** The short cell, with its 3x3 {@code ink1} notch in the top-right corner. */
    public static final Identifier CELL_SHORT = sprite("cost_cell_short");

    /** The glyph for a cost above 9,999. */
    public static final Identifier BEYOND = sprite("beyond");

    private static final int NUMBER_FIELD = 20;
    private static final int INK = Palette.opaque(Palette.INK1);
    private static final Identifier MISSING = AspectIcons.UNHEARD;

    private CostCells() {}

    private static Identifier sprite(String name) {
        return Identifier.fromNamespaceAndPath("vftv", "worktable/" + name);
    }

    /**
     * One cost entry, as every surface prices it.
     *
     * @param aspect the aspect
     * @param base the base cost
     * @param quote the quote, or null when unpriced (no wand or an inert one)
     * @param pool the wand's pool for the aspect, or 0 when unpriced
     * @param capacity the rod's capacity, or 0 when unpriced
     * @param absent the absent text when unpriced ({@code arcane.vftv.no_wand} or {@code
     *     .inert_wand}), else null
     */
    public record Cost(
            ResourceKey<Aspect> aspect,
            int base,
            @Nullable VisQuote quote,
            int pool,
            int capacity,
            @Nullable Component absent) {
        /** The cost the cell shows: effective when priced, else base. */
        public int shown() {
            VisQuote q = quote;
            return q == null ? base : q.effective().getOrDefault(aspect, base);
        }

        /** Whether the wand's pool is below the effective cost. */
        public boolean isShort() {
            return quote != null && pool < shown();
        }

        /** The cost tooltip's lines (arcane spec, "Cost tooltip"), in order. */
        public List<Component> tooltip() {
            List<Component> lines = new ArrayList<>();
            Component name = AspectLang.name(aspect);
            VisQuote q = quote;
            if (q == null) {
                MutableComponent line =
                        Component.translatable(ArcaneLang.BASE, name, NumberFormats.integer(base));
                Component a = absent;
                if (a != null) {
                    line.append(" ").append(a);
                }
                lines.add(line);
                return lines;
            }
            lines.add(
                    Component.translatable(
                            ArcaneLang.COST,
                            name,
                            NumberFormats.integer(shown()),
                            NumberFormats.integer(base)));
            lines.add(
                    Component.translatable(
                            ArcaneLang.POOL,
                            NumberFormats.integer(pool),
                            NumberFormats.integer(capacity)));
            lines.add(
                    Component.translatable(
                            ArcaneLang.FACTORS,
                            NumberFormats.factor(q.cap()),
                            NumberFormats.percent(q.discount()),
                            NumberFormats.factor(q.warp()),
                            NumberFormats.factor(q.m())));
            if (q.discountCapped()) {
                lines.add(Component.translatable(ArcaneLang.DISCOUNT_CAPPED));
            }
            if (q.clamped()) {
                lines.add(Component.translatable(ArcaneLang.CLAMPED));
            }
            if (q.exceedsCapacity()) {
                lines.add(Component.translatable(ArcaneLang.STATUS + "exceeds_capacity"));
            }
            if (q.spill() > 0) {
                lines.add(
                        Component.translatable(ArcaneLang.FLUX, NumberFormats.integer(q.spill())));
            }
            return lines;
        }

        /**
         * {@code narration.vftv.worktable.cost}: aspect, cost, pool; unpriced, the base cost and
         * the absent text.
         */
        public Component narration() {
            Component a = absent;
            return Component.translatable(
                    ArcaneLang.NARRATE_COST,
                    AspectLang.name(aspect),
                    NumberFormats.integer(shown()),
                    quote == null && a != null ? a : NumberFormats.integer(pool));
        }
    }

    /**
     * A cell laid out for one font, prepared when its inputs change so drawing allocates nothing.
     *
     * @param cost the cost entry
     * @param icon the aspect's sprite
     * @param number the cost's digits, or null for the {@code beyond} glyph
     * @param width the digits' measured width
     * @param scale the digits' scale, min(1, 20 / width)
     * @param tooltip the tooltip, wrapped at 170 px
     */
    public record Cell(
            Cost cost,
            Identifier icon,
            @Nullable FormattedCharSequence number,
            int width,
            float scale,
            List<FormattedCharSequence> tooltip) {}

    /** Lays out a cost entry for a font. */
    public static Cell prepare(Font font, Cost cost) {
        int shown = cost.shown();
        FormattedCharSequence number = null;
        int width = 0;
        if (shown <= MAX_DIGITS_COST) {
            Component digits = Component.literal(NumberFormats.integer(shown));
            number = TextFit.visual(digits);
            width = font.width(digits);
        }
        float scale = width <= NUMBER_FIELD ? 1f : NUMBER_FIELD / (float) width;
        return new Cell(
                cost,
                icon(cost.aspect()),
                number,
                width,
                scale,
                TextFit.tooltip(font, cost.tooltip()));
    }

    private static Identifier icon(ResourceKey<Aspect> aspect) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return MISSING;
        }
        return level.registryAccess()
                .lookup(AspectKeys.REGISTRY)
                .flatMap(r -> r.get(aspect))
                .map(AspectIcons::of)
                .orElse(MISSING);
    }

    /** Draws a prepared cell with its top-left at (x, y). */
    public static void draw(GuiGraphicsExtractor graphics, Font font, Cell cell, int x, int y) {
        graphics.blitSprite(
                RenderPipelines.GUI_TEXTURED,
                cell.cost().isShort() ? CELL_SHORT : CELL,
                x,
                y,
                WIDTH,
                HEIGHT);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, cell.icon(), x + 1, y + 1, 16, 16);
        FormattedCharSequence number = cell.number();
        if (number == null) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BEYOND, x + 29, y + 4, 9, 9);
            return;
        }
        if (cell.scale() >= 1f) {
            graphics.text(font, number, x + 38 - cell.width(), y + 5, INK, false);
            return;
        }
        // Only the number scales, right-aligned to x = 38 and centred on the unscaled line.
        graphics.pose().pushMatrix();
        graphics.pose().translate(x + 38f, y + 5f + font.lineHeight / 2f);
        graphics.pose().scale(cell.scale(), cell.scale());
        graphics.text(font, number, -cell.width(), -font.lineHeight / 2, INK, false);
        graphics.pose().popMatrix();
    }
}
