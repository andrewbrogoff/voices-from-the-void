package com.andrewbrogoff.vftv.core.infusion.client;

import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.api.infusion.InfusionCost;
import com.andrewbrogoff.vftv.api.infusion.InfusionLimits;
import com.andrewbrogoff.vftv.api.infusion.InfusionPlan;
import com.andrewbrogoff.vftv.core.aspect.AspectIcons;
import com.andrewbrogoff.vftv.core.aspect.AspectLang;
import com.andrewbrogoff.vftv.core.aspect.KnowledgeService;
import com.andrewbrogoff.vftv.core.aspect.client.AspectHud;
import com.andrewbrogoff.vftv.core.aspect.model.Knowledge;
import com.andrewbrogoff.vftv.core.common.client.ClientNetwork;
import com.andrewbrogoff.vftv.core.infusion.InfusionAltars;
import com.andrewbrogoff.vftv.core.infusion.InfusionLang;
import com.andrewbrogoff.vftv.core.infusion.InfusionPayloads;
import com.andrewbrogoff.vftv.core.infusion.InfusionPreview;
import com.andrewbrogoff.vftv.core.infusion.InfusionReadout;
import com.andrewbrogoff.vftv.core.infusion.InfusionRules;
import com.andrewbrogoff.vftv.core.text.client.CrosshairCard;
import com.andrewbrogoff.vftv.core.text.client.Narration;
import com.andrewbrogoff.vftv.core.text.client.TextFit;
import com.andrewbrogoff.vftv.core.text.model.CardLayout;
import com.andrewbrogoff.vftv.core.text.model.Palette;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

/**
 * The {@code vftv:infusion} HUD layer (infusion spec, "The HUD"): the player's current readout,
 * shown while they look at its bowl. The client never assesses; it shows what the server read,
 * acknowledges each {@code ARMED} readout once ({@code InfusionSeen}), and narrates lines 1 and 2
 * of a solicited readout once. The content is rebuilt only when a readout arrives or clears, or the
 * viewer's knowledge or the rules change; drawing allocates nothing.
 */
public final class InfusionHud {
    /** The plain cost cell (the arcane spec's shared sprite; never the short one here). */
    public static final Identifier CELL =
            Identifier.fromNamespaceAndPath("vftv", "worktable/cost_cell");

    private static final int TEXT = Palette.opaque(Palette.HUSH3);
    private static final int INK = Palette.opaque(Palette.INK1);
    private static final CrosshairCard CARD = new CrosshairCard(new Body());

    private static InfusionPreview.@Nullable Context current;
    private static @Nullable ClientNetwork network;
    private static @Nullable Knowledge shownKnowledge;
    private static @Nullable InfusionRules shownRules;
    private static int serial;
    private static int shownSerial = -1;
    private static List<Component> lines = List.of();

    private static Identifier[] icons = new Identifier[0];
    private static FormattedCharSequence[] costs = new FormattedCharSequence[0];
    private static int[] costWidths = new int[0];
    private static FormattedCharSequence[] helds = new FormattedCharSequence[0];
    private static Component[] spoken = new Component[0];
    private static int cellWidth = 40;
    private static int columns = 1;

    private InfusionHud() {}

    /** Takes the client sender for the seen ack. */
    static void init(ClientNetwork sender) {
        network = sender;
    }

    /** The current readout, as a preview context; null with none. */
    public static InfusionPreview.@Nullable Context current() {
        return current;
    }

    /** Drops every readout at logout. */
    public static void clear() {
        current = null;
        shownKnowledge = null;
        shownRules = null;
        lines = List.of();
        serial++;
    }

    /**
     * A readout arrived (infusion spec, "Arms and revisions"): store it, acknowledge an {@code
     * ARMED} one once, and narrate a solicited one's first two lines once.
     */
    static void receive(InfusionPayloads.Readout payload) {
        Minecraft mc = Minecraft.getInstance();
        if (payload.kind() == InfusionPayloads.Kind.CLEARED || payload.value() == null) {
            InfusionPreview.@Nullable Context c = current;
            if (c != null && c.pos().equals(payload.pos())) {
                current = null;
                serial++;
            }
            return;
        }
        InfusionReadout readout = payload.value();
        current =
                new InfusionPreview.Context(
                        payload.kind(),
                        readout.dimension(),
                        payload.pos(),
                        payload.revision(),
                        readout);
        serial++;
        ClientNetwork sender = network;
        if (payload.kind() == InfusionPayloads.Kind.ARMED
                && sender != null
                && mc.getConnection() != null) {
            sender.toServer(new InfusionPayloads.Seen(payload.pos(), payload.revision()));
        }
        if (payload.solicited()) {
            List<Component> text = build(readout, payload.kind(), knowledge(), rules());
            MutableComponent first = text.getFirst().copy();
            if (text.size() > 1) {
                first.append(CommonComponents.SPACE).append(text.get(1));
            }
            Narration.say(first);
        }
    }

    private static @Nullable Knowledge knowledge() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? null : KnowledgeService.get(player);
    }

    private static InfusionRules rules() {
        InfusionRules r = InfusionAltars.clientRules();
        return r == null ? InfusionRules.DEFAULTS : r;
    }

    /** The bowl the HUD describes now, or null when it doesn't show. */
    public static @Nullable BlockPos target() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        InfusionPreview.@Nullable Context c = current;
        if (c == null
                || player == null
                || mc.level == null
                || !mc.level.dimension().equals(c.dimension())
                || mc.gui.screen() != null
                || mc.gui.hud.isHidden()
                || mc.getDebugOverlay().showDebugScreen()
                || player.isSpectator()
                || AspectHud.cardShowing()
                || !(mc.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK
                || !hit.getBlockPos().equals(c.pos())
                || !player.isWithinBlockInteractionRange(c.pos(), 0)) {
            return null;
        }
        return c.pos();
    }

    /** The layer. */
    @SuppressWarnings("ReferenceEquality") // instance identity is the change signal
    public static void layer(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        BlockPos pos = target();
        InfusionPreview.@Nullable Context c = current;
        if (pos == null || c == null) {
            return;
        }
        Knowledge known = knowledge();
        InfusionRules r = rules();
        if (serial != shownSerial || known != shownKnowledge || r != shownRules) {
            rebuild(c, known, r);
        }
        CARD.draw(graphics);
    }

    private static void rebuild(
            InfusionPreview.Context c, @Nullable Knowledge known, InfusionRules r) {
        shownSerial = serial;
        shownKnowledge = known;
        shownRules = r;
        lines = build(c.readout(), c.kind(), known, r);
        List<InfusionCost> cells = cells(c.readout());
        boolean held = c.readout().ready() != null;
        icons = new Identifier[cells.size()];
        costs = new FormattedCharSequence[cells.size()];
        costWidths = new int[cells.size()];
        helds = new FormattedCharSequence[cells.size()];
        spoken = new Component[cells.size()];
        Font font = Minecraft.getInstance().font;
        for (int i = 0; i < cells.size(); i++) {
            InfusionCost cost = cells.get(i);
            boolean heard =
                    known != null && known.known().contains(cost.aspect().identifier().toString());
            icons[i] = heard ? icon(cost) : AspectIcons.UNHEARD;
            Component digits = Component.literal(Integer.toString(cost.cost()));
            costs[i] = TextFit.visual(digits);
            costWidths[i] = font.width(digits);
            String h =
                    cost.heldNow() >= InfusionLimits.HELD_NOW_MAX
                            ? InfusionLimits.HELD_NOW_MAX + "+"
                            : Integer.toString(cost.heldNow());
            helds[i] = held ? TextFit.visual(Component.literal(h)) : FormattedCharSequence.EMPTY;
            spoken[i] =
                    (heard
                                    ? AspectLang.name(cost.aspect()).copy()
                                    : Component.translatable(InfusionLang.UNHEARD))
                            .append(" " + cost.cost() + (held ? " (" + h + ")" : ""));
        }
        List<Component> head = lines.isEmpty() ? List.of() : List.of(lines.getFirst());
        List<Component> tail = new ArrayList<>();
        List<Component> foot = new ArrayList<>();
        boolean hasFoot = lines.size() > 1 && footKey(c.readout()) != null;
        for (int i = 1; i < lines.size(); i++) {
            if (hasFoot && i == lines.size() - 1) {
                foot.add(lines.get(i));
            } else {
                tail.add(lines.get(i));
            }
        }
        CARD.content(c.pos(), head, cells.size(), tail, foot);
    }

    private static Identifier icon(InfusionCost cost) {
        var level = Minecraft.getInstance().level;
        return level == null
                ? AspectIcons.UNHEARD
                : level.registryAccess()
                        .lookup(AspectKeys.REGISTRY)
                        .flatMap(reg -> reg.get(cost.aspect()))
                        .map(AspectIcons::of)
                        .orElse(AspectIcons.UNHEARD);
    }

    private static List<InfusionCost> cells(InfusionReadout readout) {
        InfusionPlan plan = readout.ready();
        InfusionReadout.Run run = readout.run();
        return plan != null ? plan.costs() : run != null ? run.cells() : List.of();
    }

    private static @Nullable String footKey(InfusionReadout readout) {
        if (readout.ready() != null) {
            return readout.changed() ? "changed" : "strike_again";
        }
        InfusionReadout.Run run = readout.run();
        return run != null && run.abandonArmed() ? "abandon_confirm" : null;
    }

    /** The HUD's lines, in the spec's order (line 7, if any, last). */
    static List<Component> build(
            InfusionReadout readout,
            InfusionPayloads.Kind kind,
            @Nullable Knowledge known,
            InfusionRules rules) {
        List<Component> out = new ArrayList<>();
        InfusionPlan plan = readout.ready();
        InfusionReadout.Run run = readout.run();
        String h = InfusionLang.HUD;
        if (plan != null) {
            int lp = rules.tuning().get("symmetry.lone_pedestal");
            int li = rules.tuning().get("symmetry.lone_item");
            out.add(Component.translatable(h + "ready", plan.output().getHoverName()));
            out.add(
                    Component.translatable(
                            h + "clean",
                            InfusionPreview.perMille(plan.cleanFloor()),
                            InfusionPreview.perMille(plan.cleanFullSupply())));
            out.add(Component.translatable(h + "stability", plan.stability() + "%"));
            out.add(
                    Component.translatable(
                            h + "breakdown",
                            n(plan.instability()),
                            n((long) plan.lonePedestals() * lp),
                            n((long) plan.loneItems() * li),
                            n(plan.warpPenalty()),
                            n(plan.stabiliserBonus()),
                            n(plan.stabiliserPairs()),
                            n(plan.bonusCap())));
            out.add(
                    Component.translatable(
                            h + "chance",
                            InfusionPreview.perMille(plan.upsetChance()),
                            n(plan.nominal())));
            out.add(
                    Component.translatable(
                            h + "worst",
                            n(plan.upsetMax()),
                            n(plan.upsetSpill()),
                            Long.toString(plan.upsetFluxBound())));
            out.add(Component.translatable(h + footKey(readout)));
        } else if (run != null) {
            int total = run.costs().stream().mapToInt(InfusionCost::cost).sum();
            out.add(Component.translatable(h + "ringing", run.output().getHoverName()));
            out.add(
                    Component.translatable(
                            h + "progress",
                            n(run.drawn()),
                            n(total),
                            n(run.absorbed()),
                            n(run.components())));
            out.add(
                    Component.translatable(
                            h + "frozen",
                            run.stability() + "%",
                            InfusionPreview.perMille(run.chance())));
            out.add(Component.translatable(h + "upsets", n(run.upsets()), n(run.upsetMax())));
            BlockPos missing = run.missing();
            switch (run.stall()) {
                case COMPONENT ->
                        out.add(
                                missing == null
                                        ? Component.translatable(h + "waiting")
                                        : Component.translatable(
                                                h + "missing",
                                                n(missing.getX()),
                                                n(missing.getY()),
                                                n(missing.getZ())));
                case PAUSED -> out.add(Component.translatable(h + "paused"));
                case ESSENTIA -> out.add(Component.translatable(h + "waiting"));
                case NONE -> {}
            }
            out.add(Component.translatable(h + "abandon", n(run.abandonFlux())));
            if (run.abandonArmed()) {
                out.add(Component.translatable(h + "abandon_confirm"));
            }
        } else {
            out.add(Component.translatable(readout.key(), readout.args().toArray()));
        }
        return out;
    }

    private static String n(long v) {
        return Long.toString(v);
    }

    /** The harness's {@code vftv:infusion} dump: the readout, the HUD's lines and cells. */
    public static String dump(String args) {
        JsonObject data = new JsonObject();
        InfusionPreview.@Nullable Context c = current;
        data.addProperty("showing", target() != null);
        data.addProperty("has_readout", c != null);
        if (c != null) {
            BlockPos pos = c.pos();
            data.addProperty("pos", pos.getX() + "," + pos.getY() + "," + pos.getZ());
            data.addProperty("kind", c.kind().name());
            data.addProperty("revision", c.revision());
            data.addProperty("status", c.readout().status().name());
            JsonArray text = new JsonArray();
            build(c.readout(), c.kind(), knowledge(), rules())
                    .forEach(l -> text.add(l.getString()));
            data.add("lines", text);
            data.addProperty("cells", cells(c.readout()).size());
        }
        return data.toString();
    }

    /** The grid: a plain cost cell with the cost, then "held now" beside it on a READY readout. */
    private static final class Body implements CrosshairCard.Body {
        @Override
        public int cellWidth(Font font) {
            int widest = 0;
            for (FormattedCharSequence held : helds) {
                widest = Math.max(widest, font.width(held));
            }
            cellWidth = 40 + (widest > 0 ? 2 + widest : 0);
            return cellWidth;
        }

        @Override
        public void draw(
                GuiGraphicsExtractor graphics, int fromRow, int toRow, int cols, int x, int y) {
            Font font = Minecraft.getInstance().font;
            columns = cols;
            for (int row = fromRow; row < toRow; row++) {
                for (int col = 0; col < cols; col++) {
                    int i = row * cols + col;
                    if (i >= icons.length) {
                        return;
                    }
                    int cx = x + col * (cellWidth + CardLayout.CELL_GAP);
                    int cy = y + (row - fromRow) * CardLayout.ROW;
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, CELL, cx, cy, 40, 18);
                    graphics.blitSprite(
                            RenderPipelines.GUI_TEXTURED, icons[i], cx + 1, cy + 1, 16, 16);
                    if (costWidths[i] <= 20) {
                        graphics.text(font, costs[i], cx + 38 - costWidths[i], cy + 5, INK, false);
                    } else {
                        float scale = 20f / costWidths[i];
                        graphics.pose().pushMatrix();
                        graphics.pose().translate(cx + 38f, cy + 5f + font.lineHeight / 2f);
                        graphics.pose().scale(scale, scale);
                        graphics.text(
                                font, costs[i], -costWidths[i], -font.lineHeight / 2, INK, false);
                        graphics.pose().popMatrix();
                    }
                    graphics.text(font, helds[i], cx + 42, cy + 5, TEXT, false);
                }
            }
        }

        @Override
        public Component narrate(int fromRow, int toRow) {
            MutableComponent text = Component.empty();
            int to = Math.min(spoken.length, toRow * columns);
            for (int i = fromRow * columns; i < to; i++) {
                text.append(spoken[i]).append(CommonComponents.SPACE);
            }
            return text;
        }
    }
}
