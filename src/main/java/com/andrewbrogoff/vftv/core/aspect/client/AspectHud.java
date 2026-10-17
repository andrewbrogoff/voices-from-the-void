package com.andrewbrogoff.vftv.core.aspect.client;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.core.aspect.AspectIcons;
import com.andrewbrogoff.vftv.core.aspect.AspectLang;
import com.andrewbrogoff.vftv.core.aspect.KnowledgeService;
import com.andrewbrogoff.vftv.core.aspect.ScanResult;
import com.andrewbrogoff.vftv.core.aspect.Strikes;
import com.andrewbrogoff.vftv.core.aspect.model.Knowledge;
import com.andrewbrogoff.vftv.core.text.client.CrosshairCard;
import com.andrewbrogoff.vftv.core.text.client.Narration;
import com.andrewbrogoff.vftv.core.text.client.TextFit;
import com.andrewbrogoff.vftv.core.text.model.CaptionBand;
import com.andrewbrogoff.vftv.core.text.model.CardLayout;
import com.andrewbrogoff.vftv.core.text.model.NumberFormats;
import com.andrewbrogoff.vftv.core.text.model.Palette;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

/**
 * The fork's readouts on the client (aspects spec, "What a strike reveals", "Sounding for nodes"):
 * the strike card on the shared crosshair card, narrated once when a {@link ScanResult} arrives,
 * and the sounding marker. Everything drawn is built on arrival; drawing allocates nothing.
 */
public final class AspectHud {
    private static final int TEXT = Palette.opaque(Palette.HUSH3);
    private static final int BLANK = 0xff2a2a3e; // ink2: a prerequisite not yet heard
    private static final int HINT = 8;
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
    private static final FormattedCharSequence[] ARROW_TEXT = new FormattedCharSequence[8];
    private static final FormattedCharSequence UP = FormattedCharSequence.forward("▲", Style.EMPTY);
    private static final FormattedCharSequence DOWN =
            FormattedCharSequence.forward("▼", Style.EMPTY);

    /** A hint slot for a prerequisite not yet heard: drawn as a blank, never looked up. */
    private static final Identifier BLANK_HINT = Identifier.fromNamespaceAndPath("vftv", "blank");

    static {
        for (int i = 0; i < 8; i++) {
            ARROW_TEXT[i] = FormattedCharSequence.forward(ARROWS[i], Style.EMPTY);
        }
    }

    private static final CrosshairCard CARD = new CrosshairCard(new Body());

    private static int remaining;
    private static int arrivals;
    private static int cellWidth;
    private static int columns = 1;
    private static Identifier[] icons = new Identifier[0];
    private static FormattedCharSequence[] amounts = new FormattedCharSequence[0];
    private static int[] amountWidths = new int[0];
    private static Identifier[][] hints = new Identifier[0][];
    private static Component[] spoken = new Component[0];

    private static ScanResult.@Nullable Bearing bearing;
    private static FormattedCharSequence bandWord = FormattedCharSequence.EMPTY;
    private static @Nullable ClientLevel level;

    private static @Nullable ScanResult latest;
    private static List<Component> latestLines = List.of();
    private static String narrated = "";
    private static int narrations;

    private AspectHud() {}

    /** Narrates once, keeping what was said for the harness dump. */
    private static void say(Component text) {
        narrated = text.getString();
        narrations++;
        Narration.say(text);
    }

    /**
     * The E2E harness's {@code vftv:aspect} dump (client thread): the latest readout, the card's
     * hold, the marker, the narration attempts, the client's synced knowledge, and the main-hand
     * stack's tooltip lines as this feature adds them.
     */
    public static String dump(String args) {
        Minecraft minecraft = Minecraft.getInstance();
        JsonObject data = new JsonObject();
        ScanResult r = latest;
        data.addProperty("arrivals", arrivals);
        data.addProperty("cardShowing", remaining > 0);
        data.addProperty("remaining", remaining);
        data.addProperty("state", r == null ? "" : r.state().name());
        data.addProperty("name", r == null ? "" : r.name().getString());
        data.addProperty("entries", r == null ? 0 : r.entries().size());
        data.addProperty(
                "unheard",
                r == null ? 0 : r.entries().stream().filter(ScanResult.Entry::unheard).count());
        JsonArray lines = new JsonArray();
        latestLines.forEach(l -> lines.add(l.getString()));
        data.add("lines", lines);
        ScanResult.Bearing b = bearing;
        data.addProperty(
                "marker", b == null ? "" : b.direction() + "/" + b.vertical() + "/" + b.band());
        data.addProperty("narrated", narrated);
        data.addProperty("narrations", narrations);
        LocalPlayer player = minecraft.player;
        Knowledge known = player == null ? null : KnowledgeService.get(player);
        data.addProperty("knowledgeReadable", known != null);
        data.addProperty("known", known == null ? 0 : known.known().size());
        data.addProperty("scans", known == null ? 0 : known.scans().size());
        data.addProperty(
                "pool", known == null ? 0 : known.pool().values().stream().mapToInt(i -> i).sum());
        JsonArray tooltip = new JsonArray();
        if (player != null) {
            List<Component> out = new ArrayList<>(List.of(Component.empty()));
            AspectTooltips.append(player.getMainHandItem(), out, player);
            out.subList(1, out.size()).forEach(l -> tooltip.add(l.getString()));
        }
        data.add("tooltip", tooltip);
        return data.toString();
    }

    /** A result arrived: rebuild the card or the marker, and narrate it once. */
    public static void arrive(ScanResult result) {
        Minecraft minecraft = Minecraft.getInstance();
        arrivals++;
        latest = result;
        if (result.state() == ScanResult.State.ANSWER && result.bearing() != null) {
            ScanResult.Bearing b = result.bearing();
            bearing = b;
            Component band = Component.translatable(AspectLang.band(b.band()));
            bandWord = TextFit.visual(band);
            remaining = 0;
            latestLines = List.of();
            say(
                    Component.translatable(
                            AspectLang.NARRATE_SOUNDING,
                            Component.translatable(AspectLang.DIRECTIONS.get(b.direction())),
                            Component.translatable(AspectLang.vertical(b.vertical())),
                            band));
            return;
        }
        bearing = null;
        List<Component> head = new ArrayList<>();
        if (!result.name().getString().isEmpty()) {
            head.add(result.name());
        }
        if (result.node().size() == 2) {
            head.add(result.node().get(1));
        }
        List<Component> lines = lines(result);
        latestLines = lines;
        build(result);
        CARD.content(arrivals, head, icons.length, lines, List.of());
        remaining =
                CaptionBand.hold(
                        result.holdTicks(), minecraft.options.notificationDisplayTime().get());
        MutableComponent text = Component.empty();
        head.forEach(h -> text.append(h).append(CommonComponents.SPACE));
        lines.forEach(l -> text.append(l).append(CommonComponents.SPACE));
        for (Component s : spoken) {
            text.append(s).append(CommonComponents.SPACE);
        }
        say(text);
    }

    /** Counts the hold down, and drops the marker once the fork leaves the hand. */
    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!Objects.equals(minecraft.level, level)) {
            level = minecraft.level;
            remaining = 0;
            bearing = null;
        }
        if (remaining > 0) {
            remaining--;
        }
        LocalPlayer player = minecraft.player;
        if (bearing != null
                && (player == null
                        || !(Strikes.isFork(player.getMainHandItem())
                                || Strikes.isFork(player.getOffhandItem())))) {
            bearing = null;
        }
    }

    /** Whether the strike card shows. */
    public static boolean cardShowing() {
        return remaining > 0;
    }

    /** The sounding marker's bearing, or null when none shows. */
    public static ScanResult.@Nullable Bearing marker() {
        return bearing;
    }

    /** The {@code vftv:strike_card} layer. */
    public static void cardLayer(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        if (remaining > 0) {
            CARD.draw(graphics);
        }
    }

    /** The {@code vftv:sounding} layer: an arrow toward the answer, as seen now, and its band. */
    public static void markerLayer(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        ScanResult.Bearing b = bearing;
        LocalPlayer player = Minecraft.getInstance().player;
        if (b == null || player == null) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        float facing = player.getYRot() + 180f;
        int arrow = Math.floorMod(Math.round((b.direction() * 45f - facing) / 45f), 8);
        int x = graphics.guiWidth() / 2;
        int y = graphics.guiHeight() / 2 + 12;
        FormattedCharSequence glyph = ARROW_TEXT[arrow];
        int glyphWidth = font.width(glyph);
        graphics.text(font, glyph, x - glyphWidth / 2, y, TEXT, true);
        switch (b.vertical()) {
            case UP -> graphics.text(font, UP, x + glyphWidth / 2 + 2, y, TEXT, true);
            case DOWN -> graphics.text(font, DOWN, x + glyphWidth / 2 + 2, y, TEXT, true);
            default -> {}
        }
        graphics.text(
                font, bandWord, x - font.width(bandWord) / 2, y + CaptionBand.LINE + 1, TEXT, true);
    }

    private static List<Component> lines(ScanResult result) {
        return switch (result.state()) {
            case NOTHING -> List.of(Component.translatable(AspectLang.NOTHING));
            case INK -> List.of(Component.translatable(AspectLang.INK));
            case NO_ANSWER -> List.of(Component.translatable(AspectLang.NO_ANSWER));
            case NO_WELLSPRING -> List.of(Component.translatable(AspectLang.NO_WELLSPRING));
            case ANSWER -> List.of();
            case SCANNED -> {
                List<Component> out = new ArrayList<>();
                if (!result.gained().isEmpty()) {
                    MutableComponent list = Component.empty();
                    boolean first = true;
                    for (Map.Entry<ResourceKey<Aspect>, Integer> e : result.gained().entrySet()) {
                        if (!first) {
                            list.append(", ");
                        }
                        first = false;
                        list.append(AspectLang.name(e.getKey()))
                                .append(" ")
                                .append(NumberFormats.integer(e.getValue()));
                    }
                    out.add(Component.translatable(AspectLang.GAINED, list));
                }
                if (result.entries().stream().anyMatch(ScanResult.Entry::unheard)) {
                    out.add(Component.translatable(AspectLang.PARTIAL));
                } else if (result.gained().isEmpty()) {
                    out.add(Component.translatable(AspectLang.KNOWN));
                }
                yield out;
            }
        };
    }

    private static void build(ScanResult result) {
        List<ScanResult.Entry> entries = result.entries();
        int n = entries.size();
        icons = new Identifier[n];
        amounts = new FormattedCharSequence[n];
        amountWidths = new int[n];
        hints = new Identifier[n][];
        spoken = new Component[n];
        for (int i = 0; i < n; i++) {
            ScanResult.Entry e = entries.get(i);
            icons[i] = e.aspect().map(AspectHud::icon).orElse(AspectIcons.UNHEARD);
            Component amount =
                    e.max() > 0
                            ? Component.translatable(
                                    AspectLang.OF,
                                    NumberFormats.integer(e.amount()),
                                    NumberFormats.integer(e.max()))
                            : Component.literal(NumberFormats.integer(e.amount()));
            amounts[i] = TextFit.visual(amount);
            Identifier[] h = new Identifier[e.unheard() ? e.hint().size() : 0];
            for (int j = 0; j < h.length; j++) {
                h[j] = e.hint().get(j).map(AspectHud::icon).orElse(BLANK_HINT);
            }
            hints[i] = h;
            Component name =
                    e.aspect()
                            .<Component>map(AspectLang::name)
                            .orElse(Component.translatable(AspectLang.UNHEARD));
            spoken[i] = Component.translatable(AspectLang.NARRATE_ASPECT, name, amount);
        }
    }

    private static Identifier icon(ResourceKey<Aspect> aspect) {
        ClientLevel l = Minecraft.getInstance().level;
        Optional<Holder.Reference<Aspect>> holder =
                l == null
                        ? Optional.empty()
                        : l.registryAccess().lookupOrThrow(AspectKeys.REGISTRY).get(aspect);
        return holder.map(AspectIcons::of).orElse(AspectIcons.UNHEARD);
    }

    /** The card's grid: an icon, the exact amount, and an unheard aspect's hint. */
    private static final class Body implements CrosshairCard.Body {
        /** Measures every amount and hint with the font in use; asked at relayout only. */
        @Override
        public int cellWidth(Font font) {
            int widest = 0;
            for (int i = 0; i < amounts.length; i++) {
                amountWidths[i] = font.width(amounts[i]);
                widest = Math.max(widest, amountWidths[i] + hints[i].length * (HINT + 1));
            }
            cellWidth = 18 + widest;
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
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, icons[i], cx, cy + 1, 16, 16);
                    graphics.text(font, amounts[i], cx + 18, cy + 5, TEXT, false);
                    int hx = cx + 18 + amountWidths[i] + 1;
                    for (Identifier hint : hints[i]) {
                        if (BLANK_HINT.equals(hint)) {
                            graphics.fill(hx, cy + 5, hx + HINT, cy + 5 + HINT, BLANK);
                        } else {
                            graphics.blitSprite(
                                    RenderPipelines.GUI_TEXTURED, hint, hx, cy + 5, HINT, HINT);
                        }
                        hx += HINT + 1;
                    }
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
