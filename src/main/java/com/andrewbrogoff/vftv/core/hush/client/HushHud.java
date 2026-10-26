package com.andrewbrogoff.vftv.core.hush.client;

import com.andrewbrogoff.vftv.api.flux.FluxRules;
import com.andrewbrogoff.vftv.core.flux.client.ClientFluxRules;
import com.andrewbrogoff.vftv.core.hush.HushChunkStatus;
import com.andrewbrogoff.vftv.core.hush.HushContent;
import com.andrewbrogoff.vftv.core.hush.HushLang;
import com.andrewbrogoff.vftv.core.hush.HushStatuses;
import com.andrewbrogoff.vftv.core.hush.model.HushMath;
import com.andrewbrogoff.vftv.core.text.model.NumberFormats;
import com.andrewbrogoff.vftv.core.text.model.Palette;
import com.andrewbrogoff.vftv.core.vis.client.VisHud;
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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

/**
 * The {@code vftv:hush} HUD layer (Hush spec, "Reading"): while a Clearing Bell is held, the
 * player's chunk's exact reading in a nine-slice frame at (4, 4), or 2 px below the vis frame.
 * Rebuilt only when the chunk, its status, the rules, the language or resources, or the screen
 * change; drawing allocates nothing.
 */
public final class HushHud {
    /** The frame, nine-slice with border 4. */
    public static final Identifier FRAME =
            Identifier.fromNamespaceAndPath("vftv", "hud/hush_frame");

    private static final int TEXT = Palette.opaque(Palette.HUSH3);
    private static final int PAD = 4;
    private static final int LINE = 9;

    private static volatile HushClientConfig config = HushClientConfig.DEFAULT;

    private static boolean built;
    private static long shownChunk;
    private static HushChunkStatus.@Nullable View shownView;
    private static @Nullable FluxRules shownRules;
    private static int shownWidth;
    private static int shownHeight;
    private static List<Component> lines = List.of();
    private static FormattedCharSequence[] text = new FormattedCharSequence[0];
    private static int width;
    private static int height;

    private HushHud() {}

    /** Takes a published client config. */
    public static void installConfig(HushClientConfig next) {
        config = next;
    }

    /** The config in force. */
    public static HushClientConfig config() {
        return config;
    }

    /** Drops the cached layout: a resource or language reload, a level change, logout. */
    public static void invalidate() {
        built = false;
        shownView = null;
        shownRules = null;
        lines = List.of();
        text = new FormattedCharSequence[0];
    }

    /** Whether the reading shows: a bell held, the HUD up, no screen, not a spectator. */
    public static boolean showing() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        return config.hud()
                && player != null
                && mc.level != null
                && !mc.gui.hud.isHidden()
                && mc.gui.screen() == null
                && !mc.debugEntries.isOverlayVisible()
                && !player.isSpectator()
                && (player.getMainHandItem().is(HushContent.clearingBell())
                        || player.getOffhandItem().is(HushContent.clearingBell()));
    }

    /** The layer. */
    @SuppressWarnings("ReferenceEquality") // instance identity is the change signal
    public static void layer(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        if (!showing()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = java.util.Objects.requireNonNull(mc.player);
        long chunk = ChunkPos.pack(player.blockPosition());
        HushChunkStatus.View view = HushStatuses.get(chunk);
        FluxRules rules = ClientFluxRules.get();
        int w = graphics.guiWidth();
        int h = graphics.guiHeight();
        if (!built
                || chunk != shownChunk
                || view != shownView
                || rules != shownRules
                || w != shownWidth
                || h != shownHeight) {
            rebuild(mc.font, chunk, view, rules, w, h);
        }
        int below = VisHud.frameBottom();
        int x = 4;
        int y = below < 0 ? 4 : below + 2;
        float scale = 1;
        if (width > w - 4 || height > h - 4) {
            scale = Math.min((w - 4) / (float) width, (h - 4) / (float) height);
        }
        x = Math.max(2, Math.min(x, (int) (w - 2 - width * scale)));
        y = Math.max(2, Math.min(y, (int) (h - 2 - height * scale)));
        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FRAME, 0, 0, width, height);
        for (int i = 0; i < text.length; i++) {
            graphics.text(mc.font, text[i], PAD, PAD + LINE * i, TEXT, true);
        }
        pose.popMatrix();
    }

    private static void rebuild(
            Font font,
            long chunk,
            HushChunkStatus.@Nullable View view,
            @Nullable FluxRules rules,
            int w,
            int h) {
        built = true;
        shownChunk = chunk;
        shownView = view;
        shownRules = rules;
        shownWidth = w;
        shownHeight = h;
        lines = lines(view, rules);
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        int wrap = Math.min(200, w - 12);
        for (Component line : lines) {
            wrapped.addAll(font.split(line, Math.max(1, wrap)));
        }
        text = wrapped.toArray(FormattedCharSequence[]::new);
        int widest = 0;
        for (FormattedCharSequence line : text) {
            widest = Math.max(widest, font.width(line));
        }
        width = 2 * PAD + widest;
        height = 2 * PAD + LINE * text.length;
    }

    /** The reading's lines: line 1 by first match, line 2 the seep iff quiet or hushing. */
    static List<Component> lines(HushChunkStatus.@Nullable View view, @Nullable FluxRules rules) {
        if (rules == null || view == null) {
            return List.of(Component.translatable(HushLang.HUD_UNKNOWN));
        }
        if (view.status() == HushChunkStatus.UNREADABLE) {
            return List.of(Component.translatable(HushLang.HUD_UNREADABLE));
        }
        if (view.status() == HushChunkStatus.NONE || view.flux() == 0) {
            return List.of(Component.translatable(HushLang.HUD_NONE));
        }
        int threshold = HushMath.threshold(rules.hushOnset(), rules.hushBlockCost());
        Component first =
                view.flux() < threshold
                        ? Component.translatable(
                                HushLang.HUD_QUIET,
                                NumberFormats.integer(view.flux()),
                                NumberFormats.integer(threshold))
                        : Component.translatable(
                                HushLang.HUD_HUSHING,
                                NumberFormats.integer(view.flux()),
                                NumberFormats.integer(
                                        HushMath.blocks(
                                                view.flux(),
                                                rules.hushOnset(),
                                                rules.hushBlockCost())));
        if (view.seep().isEmpty()) {
            return List.of(first);
        }
        BlockPos seep = BlockPos.of(view.seep().getAsLong());
        return List.of(
                first,
                Component.translatable(
                        HushLang.HUD_SEEP,
                        NumberFormats.integer(seep.getX()),
                        NumberFormats.integer(seep.getY()),
                        NumberFormats.integer(seep.getZ())));
    }

    /** The harness's {@code vftv:hush_client} dump: the reading, the statuses and the gains. */
    public static String dump(String args) {
        JsonObject data = new JsonObject();
        data.addProperty("showing", showing());
        JsonArray shown = new JsonArray();
        lines.forEach(l -> shown.add(l.getString()));
        data.add("lines", shown);
        data.addProperty("wrapped", text.length);
        data.addProperty("width", width);
        data.addProperty("height", height);
        data.addProperty("statuses", HushStatuses.size());
        data.addProperty("rules", ClientFluxRules.get() != null);
        HushMuffle.dump(data);
        return data.toString();
    }
}
