package com.andrewbrogoff.vftv.core.arcane.client;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.vis.VisContext;
import com.andrewbrogoff.vftv.api.vis.VisKeys;
import com.andrewbrogoff.vftv.api.vis.VisKind;
import com.andrewbrogoff.vftv.api.vis.VisQuote;
import com.andrewbrogoff.vftv.core.arcane.ArcaneLang;
import com.andrewbrogoff.vftv.core.arcane.ArcanePreview;
import com.andrewbrogoff.vftv.core.arcane.ArcaneRecipe;
import com.andrewbrogoff.vftv.core.arcane.ArcaneRecipes;
import com.andrewbrogoff.vftv.core.arcane.WorktableCraft;
import com.andrewbrogoff.vftv.core.arcane.WorktableMenu;
import com.andrewbrogoff.vftv.core.arcane.WorktableStatus;
import com.andrewbrogoff.vftv.core.arcane.Worktables;
import com.andrewbrogoff.vftv.core.research.ResearchService;
import com.andrewbrogoff.vftv.core.research.model.ResearchRecord;
import com.andrewbrogoff.vftv.core.text.client.ClippedLabel;
import com.andrewbrogoff.vftv.core.text.client.TextFit;
import com.andrewbrogoff.vftv.core.text.model.NumberFormats;
import com.andrewbrogoff.vftv.core.text.model.Palette;
import com.andrewbrogoff.vftv.core.vis.VisRulesService;
import com.andrewbrogoff.vftv.core.vis.WandParts;
import com.andrewbrogoff.vftv.core.vis.WandState;
import com.andrewbrogoff.vftv.core.warp.WarpDials;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jspecify.annotations.Nullable;

/**
 * The Worktable's screen (arcane spec, "The screen"): 176x220, the preview widget at (94, 31), the
 * 2x4 cost panel at (8, 74), and the clipped status line at (8, 112). Focus cycles the preview,
 * each cost cell, then the status line. Everything shown is recomputed only when an input changes.
 */
public final class WorktableScreen extends AbstractContainerScreen<WorktableMenu> {
    private static final Identifier BACKGROUND =
            Identifier.fromNamespaceAndPath("vftv", "textures/gui/container/worktable.png");
    private static final Identifier IDLE = sprite("preview_idle");
    private static final Identifier DISABLED = sprite("preview_disabled");
    private static final Identifier HOVER = sprite("preview_hover");
    private static final Identifier READY = sprite("preview_ready");
    private static final int INK = Palette.opaque(Palette.INK1);
    private static final int CELLS = 8;
    private static final VisContext ORDINARY =
            new VisContext(ArcaneRecipe.ID, VisKind.ORDINARY, null);
    private static final VisContext VOID = new VisContext(ArcaneRecipe.ID, VisKind.VOID, null);

    private final List<CostCells.Cell> cells = new ArrayList<>(CELLS);
    private final List<CellWidget> cellWidgets = new ArrayList<>(CELLS);
    private Worktables.@Nullable Evaluation evaluation;
    private @Nullable Inputs seen;
    private @Nullable Preview preview;
    private @Nullable ClippedLabel status;
    private List<FormattedCharSequence> previewTooltip = List.of();
    private Component previewNarration = Component.empty();
    private boolean sentThisTick;

    /** The screen for a Worktable menu. */
    public WorktableScreen(WorktableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 220);
        inventoryLabelY = 125;
    }

    private static Identifier sprite(String name) {
        return Identifier.fromNamespaceAndPath("vftv", "worktable/" + name);
    }

    @Override
    protected void init() {
        super.init();
        cellWidgets.clear();
        preview = addRenderableWidget(new Preview(leftPos + 94, topPos + 31));
        for (int i = 0; i < CELLS; i++) {
            cellWidgets.add(
                    addRenderableWidget(
                            new CellWidget(
                                    i, leftPos + 8 + 40 * (i % 4), topPos + 74 + 18 * (i / 4))));
        }
        status = addRenderableWidget(new ClippedLabel(leftPos + 8, topPos + 112, 160, font, INK));
        seen = null;
        refresh();
    }

    @Override
    protected void containerTick() {
        sentThisTick = false;
        refresh();
    }

    /** The preview's live dependencies (arcane spec, "The authoritative preview"). */
    private record Inputs(
            int slots,
            long research,
            Object snapshot,
            int rules,
            double discount,
            double ordinary,
            double voidFactor) {}

    private Inputs inputs() {
        Player player = menu.player();
        ResearchRecord record = ResearchService.record(player);
        Holder.Reference<Attribute> discount =
                BuiltInRegistries.ATTRIBUTE.get(VisKeys.VIS_DISCOUNT).orElse(null);
        return new Inputs(
                menu.clientVersion(),
                record == null ? -1 : record.revision(),
                ArcaneRecipes.client(),
                VisRulesService.clientRevision(),
                discount != null && player.getAttributes().hasAttribute(discount)
                        ? player.getAttributeValue(discount)
                        : 0,
                WarpDials.visFactor(player, ORDINARY),
                WarpDials.visFactor(player, VOID));
    }

    /** Recomputes the evaluation, cells, status and tooltips when an input changed. */
    private void refresh() {
        Inputs now = inputs();
        if (now.equals(seen)) {
            return;
        }
        seen = now;
        Worktables.Evaluation e = Worktables.evaluate(menu);
        evaluation = e;
        cells.clear();
        RecipeHolder<ArcaneRecipe> recipe = e.recipe();
        ItemStack wand = menu.wandSlot().getItem(0);
        ArcanePreview p =
                recipe == null
                        ? null
                        : ArcanePreview.of(menu.player(), recipe, ArcanePreview.station(wand));
        if (p != null) {
            WandParts parts = WandParts.of(menu.player().level().registryAccess());
            WandState.Ready ready =
                    WandState.of(wand, parts) instanceof WandState.Ready r ? r : null;
            for (Map.Entry<ResourceKey<Aspect>, Integer> cost : p.vis().entrySet()) {
                cells.add(
                        CostCells.prepare(
                                font,
                                new CostCells.Cost(
                                        cost.getKey(),
                                        cost.getValue(),
                                        p.quote(),
                                        ready == null ? 0 : ready.pool(cost.getKey()),
                                        ready == null ? 0 : ready.rod().capacity(),
                                        p.absent())));
            }
        }
        for (int i = 0; i < cellWidgets.size(); i++) {
            cellWidgets.get(i).visible = i < cells.size();
        }
        WorktableStatus shown = menu.shownRefusal();
        Component line = e.line(shown == null ? e.status() : shown);
        ClippedLabel label = status;
        if (label != null) {
            label.setMessage(line == null ? Component.empty() : line);
        }
        previewTooltip = TextFit.tooltip(font, previewLines(e));
        previewNarration = narration(e, line);
    }

    private List<Component> previewLines(Worktables.Evaluation e) {
        List<Component> lines = new ArrayList<>();
        ItemStack result = e.result();
        if (!result.isEmpty()) {
            lines.addAll(getTooltipFromItem(minecraft, result));
        }
        lines.add(Component.translatable(ArcaneLang.PREVIEW));
        VisQuote quote = e.quote();
        if (quote != null && quote.spill() > 0) {
            int n = e.batchSize(menu);
            lines.add(
                    Component.translatable(
                            ArcaneLang.BATCH_FLUX,
                            NumberFormats.integer(n),
                            NumberFormats.integer((long) n * quote.spill())));
        }
        return lines;
    }

    private Component narration(Worktables.Evaluation e, @Nullable Component line) {
        MutableComponent out = Component.empty();
        ItemStack result = e.result();
        if (!result.isEmpty()) {
            out.append(
                    Component.translatable(
                            ArcaneLang.NARRATE_RESULT,
                            NumberFormats.integer(result.getCount()),
                            result.getHoverName()));
        }
        for (CostCells.Cell cell : cells) {
            out.append(" ").append(cell.cost().narration());
        }
        if (line != null) {
            out.append(" ").append(line);
        }
        return out;
    }

    /** Crafts one, or a batch: only when the local prediction is ready, once per client tick. */
    private void activate(boolean batch) {
        Worktables.Evaluation e = evaluation;
        RecipeHolder<ArcaneRecipe> recipe = e == null ? null : e.recipe();
        VisQuote quote = e == null ? null : e.quote();
        if (sentThisTick || e == null || !e.ready() || recipe == null || quote == null) {
            return;
        }
        sentThisTick = true;
        menu.acknowledge();
        seen = null;
        ArcaneClientFeature.network()
                .toServer(
                        new WorktableCraft(
                                menu.containerId,
                                menu.revision(),
                                recipe.id().identifier(),
                                WorktableCraft.Echo.of(quote),
                                batch));
        refresh();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                BACKGROUND,
                leftPos,
                topPos,
                0f,
                0f,
                imageWidth,
                imageHeight,
                256,
                256);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
        graphics.text(font, title, titleLabelX, titleLabelY, INK, false);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, INK, false);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        Preview p = preview;
        if (p == null) {
            return;
        }
        if (p.isHovered()) {
            graphics.setTooltipForNextFrame(font, previewTooltip, mouseX, mouseY);
            return;
        }
        for (CellWidget cell : cellWidgets) {
            if (cell.visible && cell.isHovered()) {
                graphics.setTooltipForNextFrame(font, cell.tooltip(), mouseX, mouseY);
                return;
            }
        }
        if (p.isFocused()) {
            graphics.setTooltipForNextFrame(font, previewTooltip, p.getX(), p.getBottom());
            return;
        }
        for (CellWidget cell : cellWidgets) {
            if (cell.visible && cell.isFocused()) {
                graphics.setTooltipForNextFrame(
                        font, cell.tooltip(), cell.getX(), cell.getBottom());
                return;
            }
        }
    }

    /**
     * The harness's {@code vftv:worktable} dump: the open Worktable screen's status line, preview
     * state, cost cells and their tooltips, the menu's revision and refusal, and the output.
     */
    public static String dump(String arg) {
        JsonObject out = new JsonObject();
        if (!(Minecraft.getInstance().gui.screen() instanceof WorktableScreen screen)) {
            out.addProperty("open", false);
            return out.toString();
        }
        Worktables.Evaluation e = screen.evaluation;
        ClippedLabel label = screen.status;
        out.addProperty("open", true);
        out.addProperty("status", e == null ? "" : e.status().name());
        out.addProperty("line", label == null ? "" : label.getMessage().getString());
        out.addProperty("ready", e != null && e.ready());
        out.addProperty("revision", screen.menu.revision());
        out.addProperty("refusal", screen.menu.refusal());
        ItemStack output = screen.menu.output().getItem(0);
        out.addProperty(
                "output", output.isEmpty() ? "" : output.getCount() + " " + output.getItem());
        JsonArray cells = new JsonArray();
        for (CostCells.Cell cell : screen.cells) {
            JsonObject c = new JsonObject();
            c.addProperty("aspect", cell.cost().aspect().identifier().toString());
            c.addProperty("cost", cell.cost().shown());
            c.addProperty("short", cell.cost().isShort());
            JsonArray tooltip = new JsonArray();
            cell.cost().tooltip().forEach(line -> tooltip.add(line.getString()));
            c.add("tooltip", tooltip);
            cells.add(c);
        }
        out.add("cells", cells);
        JsonArray preview = new JsonArray();
        screen.previewLines(e == null ? Worktables.evaluate(screen.menu) : e)
                .forEach(line -> preview.add(line.getString()));
        out.add("preview", preview);
        return out.toString();
    }

    /** The preview: not a slot; it draws the would-be result and crafts on activation. */
    private final class Preview extends AbstractWidget {
        Preview(int x, int y) {
            super(x, y, 26, 26, Component.empty());
        }

        @Override
        protected void extractWidgetRenderState(
                GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            Worktables.Evaluation e = evaluation;
            Identifier frame;
            if (e == null || e.empty()) {
                frame = IDLE;
            } else if (!e.ready()) {
                frame = DISABLED;
            } else {
                frame = isHoveredOrFocused() ? HOVER : READY;
            }
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, frame, getX(), getY(), 26, 26);
            if (e != null && !e.empty() && !e.result().isEmpty()) {
                graphics.item(e.result(), getX() + 5, getY() + 5);
                graphics.itemDecorations(font, e.result(), getX() + 5, getY() + 5);
            }
        }

        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
            activate(event.hasShiftDown());
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            if (isFocused() && event.isSelection()) {
                activate(event.hasShiftDown());
                return true;
            }
            return false;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, previewNarration);
        }
    }

    /** One cost cell: a focus target whose tooltip shows on hover and on focus. */
    private final class CellWidget extends AbstractWidget {
        private final int index;

        CellWidget(int index, int x, int y) {
            super(x, y, CostCells.WIDTH, CostCells.HEIGHT, Component.empty());
            this.index = index;
            visible = false;
        }

        List<FormattedCharSequence> tooltip() {
            return index < cells.size() ? cells.get(index).tooltip() : List.of();
        }

        @Override
        protected void extractWidgetRenderState(
                GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            if (index >= cells.size()) {
                return;
            }
            CostCells.draw(graphics, font, cells.get(index), getX(), getY());
            if (isFocused()) {
                graphics.outline(getX(), getY(), getWidth(), getHeight(), INK);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            if (index >= cells.size()) {
                return;
            }
            CostCells.Cost cost = cells.get(index).cost();
            MutableComponent text = cost.narration().copy();
            cost.tooltip().forEach(line -> text.append(" ").append(line));
            output.add(NarratedElementType.TITLE, text);
        }
    }
}
