package com.andrewbrogoff.vftv.core.vis.client;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.api.vis.VisLimits;
import com.andrewbrogoff.vftv.core.aspect.AspectIcons;
import com.andrewbrogoff.vftv.core.aspect.AspectLang;
import com.andrewbrogoff.vftv.core.common.RawRecordCodec.Retained;
import com.andrewbrogoff.vftv.core.text.model.NumberFormats;
import com.andrewbrogoff.vftv.core.text.model.SharedMessages;
import com.andrewbrogoff.vftv.core.vis.CapturedNode;
import com.andrewbrogoff.vftv.core.vis.NodeService;
import com.andrewbrogoff.vftv.core.vis.VisContent;
import com.andrewbrogoff.vftv.core.vis.VisLang;
import com.andrewbrogoff.vftv.core.vis.VisRulesService;
import com.andrewbrogoff.vftv.core.vis.WandParts;
import com.andrewbrogoff.vftv.core.vis.WandState;
import com.andrewbrogoff.vftv.core.vis.model.QuoteMath;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The wand's display (wands spec, "Display"): the HUD layer {@code vftv:vis} for the wand in the
 * main hand, else the offhand, with one row per vis aspect (icon and exact current) or an inert
 * wand's wrapped text; and the Shift tooltip. The layout is rebuilt only when the held wand, its
 * hand, the parts or the rules change.
 */
public final class VisHud {
    /** The frame sprite, nine-slice with border 4. */
    public static final Identifier FRAME = Identifier.fromNamespaceAndPath("vftv", "hud/vis_frame");

    /** {@code hush3}: the HUD's text. */
    private static final int TEXT = 0xFFC6C2C4;

    private static volatile VisClientConfig config = VisClientConfig.DEFAULT;

    /** The cached layout, and what it was built from. */
    private static @Nullable Layout layout;

    private record Layout(
            ItemStack stack,
            InteractionHand hand,
            WandParts parts,
            int rulesRevision,
            List<Identifier> icons,
            List<String> numbers,
            List<FormattedCharSequence> text,
            int width,
            int height) {}

    private VisHud() {}

    /** Takes a published client config. */
    public static void installConfig(VisClientConfig next) {
        config = next;
    }

    /** Drops the cached layout, on resource reload or disconnect. */
    public static void invalidate() {
        layout = null;
    }

    /** The layer. */
    public static void layer(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null
                || mc.gui.hud.isHidden()
                || mc.gui.screen() != null
                || mc.debugEntries.isOverlayVisible()
                || player.isSpectator()) {
            return;
        }
        Layout l = layout(player, mc.font);
        if (l == null) {
            return;
        }
        int x = Math.max(0, Math.min(config.x(), graphics.guiWidth() - l.width()));
        int y = Math.max(0, Math.min(config.y(), graphics.guiHeight() - l.height()));
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FRAME, x, y, l.width(), l.height());
        int pad = VisLimits.HUD_PADDING;
        if (!l.text().isEmpty()) {
            for (int i = 0; i < l.text().size(); i++) {
                graphics.text(mc.font, l.text().get(i), x + pad, y + pad + 9 * i, TEXT, false);
            }
            return;
        }
        for (int i = 0; i < l.icons().size(); i++) {
            int rowY = y + pad + i * (VisLimits.HUD_ROW_HEIGHT + VisLimits.HUD_ROW_GAP) - 1;
            graphics.blitSprite(
                    RenderPipelines.GUI_TEXTURED,
                    l.icons().get(i),
                    x + VisLimits.HUD_ICON_X,
                    rowY + 1,
                    16,
                    16);
            graphics.text(
                    mc.font,
                    l.numbers().get(i),
                    x + VisLimits.HUD_NUMBER_X,
                    rowY + 1 + (16 - mc.font.lineHeight) / 2 + 1,
                    TEXT,
                    false);
        }
    }

    private static @Nullable Layout layout(Player player, Font font) {
        InteractionHand hand = InteractionHand.MAIN_HAND;
        ItemStack stack = player.getMainHandItem();
        if (!stack.is(VisContent.wand())) {
            hand = InteractionHand.OFF_HAND;
            stack = player.getOffhandItem();
        }
        if (!stack.is(VisContent.wand())) {
            layout = null;
            return null;
        }
        WandParts parts = WandParts.of(player.level().registryAccess());
        Layout l = layout;
        if (l != null
                && l.hand() == hand
                && same(l.parts(), parts)
                && l.rulesRevision() == VisRulesService.clientRevision()
                && ItemStack.matches(l.stack(), stack)) {
            return l;
        }
        l = build(stack, hand, parts, player, font);
        layout = l;
        return l;
    }

    // The part set is rebuilt only when the registries change: identity is the change check.
    @SuppressWarnings("ReferenceEquality")
    private static boolean same(WandParts a, WandParts b) {
        return a == b;
    }

    private static Layout build(
            ItemStack stack, InteractionHand hand, WandParts parts, Player player, Font font) {
        WandState state = WandState.of(stack, parts);
        List<Identifier> icons = new ArrayList<>();
        List<String> numbers = new ArrayList<>();
        if (state instanceof WandState.Ready ready) {
            var registry = player.level().registryAccess().lookupOrThrow(AspectKeys.REGISTRY);
            for (ResourceKey<Aspect> aspect : parts.visAspects()) {
                icons.add(registry.get(aspect).map(AspectIcons::of).orElse(AspectIcons.UNHEARD));
                numbers.add(NumberFormats.integer(ready.pool(aspect)));
            }
            int n = icons.size();
            return new Layout(
                    stack.copy(),
                    hand,
                    parts,
                    VisRulesService.clientRevision(),
                    icons,
                    numbers,
                    List.of(),
                    VisLimits.HUD_FRAME_WIDTH,
                    6 + 18 * n);
        }
        Component text = inertLine(state, true);
        int pad = VisLimits.HUD_PADDING;
        List<FormattedCharSequence> lines = font.split(text, VisLimits.HUD_WRAP_WIDTH);
        int widest = 0;
        for (FormattedCharSequence line : lines) {
            widest = Math.max(widest, font.width(line));
        }
        int width = Math.min(VisLimits.HUD_MAX_FRAME_WIDTH, widest + 2 * pad);
        return new Layout(
                stack.copy(),
                hand,
                parts,
                VisRulesService.clientRevision(),
                icons,
                numbers,
                lines,
                width,
                8 + 9 * lines.size());
    }

    /** An inert wand's line: unfinished, a missing part with its id, or unreadable vis. */
    static Component inertLine(WandState state, boolean hud) {
        return switch (state) {
            case WandState.MissingPart m ->
                    hud
                            ? Component.translatable(VisLang.HUD_MISSING, m.id().toString())
                            : Component.translatable(
                                    m.rod()
                                            ? VisLang.TOOLTIP_MISSING_ROD
                                            : VisLang.TOOLTIP_MISSING_CAP,
                                    m.id().toString());
            case WandState.Unreadable u -> Component.translatable("message.vftv.unreadable");
            default ->
                    Component.translatable(
                            hud ? VisLang.HUD_UNFINISHED : VisLang.TOOLTIP_UNFINISHED);
        };
    }

    /** The wand's and the Bell Jar's tooltip lines, after the name. */
    public static void tooltip(ItemStack stack, List<Component> tooltip, @Nullable Player player) {
        if (stack.is(VisContent.bellJar())) {
            tooltip.add(
                    Math.min(1, tooltip.size()), bellJarLine(stack).withStyle(ChatFormatting.GRAY));
            return;
        }
        if (!stack.is(VisContent.wand()) || player == null) {
            return;
        }
        int at = Math.min(1, tooltip.size());
        WandParts parts = WandParts.of(player.level().registryAccess());
        WandState state = WandState.of(stack, parts);
        if (!(state instanceof WandState.Ready ready)) {
            tooltip.add(at, inertLine(state, false).copy().withStyle(ChatFormatting.GRAY));
            return;
        }
        if (!Minecraft.getInstance().hasShiftDown()) {
            tooltip.add(
                    at,
                    Component.translatable(VisLang.TOOLTIP_MORE).withStyle(ChatFormatting.GRAY));
            return;
        }
        Identifier rod = ready.rodKey().identifier();
        Identifier cap = ready.capKey().identifier();
        tooltip.add(
                at++,
                Component.translatable(
                                VisLang.TOOLTIP_PARTS,
                                Component.translatable(
                                        "wand_rod." + rod.getNamespace() + "." + rod.getPath()),
                                Component.translatable(
                                        "wand_cap." + cap.getNamespace() + "." + cap.getPath()))
                        .withStyle(ChatFormatting.GRAY));
        String capacity = NumberFormats.integer(ready.rod().capacity());
        tooltip.add(
                at++,
                Component.translatable(VisLang.TOOLTIP_CAPACITY, capacity)
                        .withStyle(ChatFormatting.GRAY));
        tooltip.add(
                at++,
                Component.translatable(
                                VisLang.TOOLTIP_COST,
                                NumberFormats.factor(
                                        QuoteMath.perMille(ready.cap().costMultiplier())))
                        .withStyle(ChatFormatting.GRAY));
        for (ResourceKey<Aspect> aspect : parts.visAspects()) {
            tooltip.add(
                    at++,
                    Component.translatable(
                                    VisLang.TOOLTIP_POOL,
                                    AspectLang.name(aspect),
                                    NumberFormats.integer(ready.pool(aspect)),
                                    capacity)
                            .withStyle(ChatFormatting.GRAY));
        }
    }

    /** The Bell Jar's line: how to use it, the node it holds, or that it can't be read. */
    static MutableComponent bellJarLine(ItemStack stack) {
        return switch (stack.get(VisContent.captured())) {
            case null -> Component.translatable(VisLang.JAR_EMPTY);
            case Retained.Read<CapturedNode> r ->
                    Component.translatable(
                            VisLang.JAR_HOLDS,
                            Component.translatable(r.value().node().clarity().langKey()),
                            NodeService.typeName(r.value().node().type()),
                            NumberFormats.integer(r.value().node().id()));
            case Retained.Unreadable<CapturedNode> u ->
                    Component.translatable(SharedMessages.UNREADABLE);
        };
    }

    /** The harness's {@code vftv:vis_client} dump: the held wand's state and the HUD layout. */
    public static String dump(String args) {
        JsonObject data = new JsonObject();
        LocalPlayer player = Minecraft.getInstance().player;
        data.addProperty("rulesReceived", VisRulesService.client() != null);
        if (player == null) {
            return data.toString();
        }
        Layout l = layout(player, Minecraft.getInstance().font);
        data.addProperty("shown", l != null);
        if (l != null) {
            data.addProperty("hand", l.hand().name());
            data.addProperty("width", l.width());
            data.addProperty("height", l.height());
            JsonArray rows = new JsonArray();
            l.numbers().forEach(rows::add);
            data.add("rows", rows);
            data.addProperty("inert", !l.text().isEmpty());
            data.addProperty("lines", l.text().size());
        }
        return data.toString();
    }
}
