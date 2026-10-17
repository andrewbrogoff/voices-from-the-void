package com.andrewbrogoff.vftv.core.crucible.client;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.api.crucible.CrucibleLimits;
import com.andrewbrogoff.vftv.api.essentia.SpillCause;
import com.andrewbrogoff.vftv.api.flux.FluxRules;
import com.andrewbrogoff.vftv.core.aspect.AspectIcons;
import com.andrewbrogoff.vftv.core.aspect.AspectLang;
import com.andrewbrogoff.vftv.core.aspect.KnowledgeService;
import com.andrewbrogoff.vftv.core.aspect.client.AspectHud;
import com.andrewbrogoff.vftv.core.aspect.model.Knowledge;
import com.andrewbrogoff.vftv.core.crucible.CrucibleBlock;
import com.andrewbrogoff.vftv.core.crucible.CrucibleBlockEntity;
import com.andrewbrogoff.vftv.core.crucible.CrucibleLang;
import com.andrewbrogoff.vftv.core.crucible.model.CrucibleMath;
import com.andrewbrogoff.vftv.core.flux.client.ClientFluxRules;
import com.andrewbrogoff.vftv.core.flux.model.FluxMath;
import com.andrewbrogoff.vftv.core.text.client.CrosshairCard;
import com.andrewbrogoff.vftv.core.text.client.TextFit;
import com.andrewbrogoff.vftv.core.text.model.CardLayout;
import com.andrewbrogoff.vftv.core.text.model.NumberFormats;
import com.andrewbrogoff.vftv.core.text.model.Palette;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

/**
 * The {@code vftv:crucible} HUD layer (crucible spec, "The HUD"): the targeted crucible's state, up
 * to twelve pool cells, the more line and the spill preview, on the shared crosshair card. The
 * content is rebuilt only when the crucible, its synced data, the viewer's knowledge or the flux
 * rules change; drawing allocates nothing.
 */
public final class CrucibleHud {
    /** A missing aspect id's cell. */
    public static final Identifier MISSING =
            Identifier.fromNamespaceAndPath("vftv", "hud/crucible_missing");

    private static final int TEXT = Palette.opaque(Palette.HUSH3);
    private static final CrosshairCard CARD = new CrosshairCard(new Body());

    private static @Nullable CrucibleBlockEntity shown;
    private static int shownRevision;
    private static int shownCapacity;
    private static @Nullable BlockState shownState;
    private static @Nullable Knowledge shownKnowledge;
    private static @Nullable FluxRules shownRules;
    private static boolean shownUnreadable;
    private static List<Component> lines = List.of();

    private static Identifier[] icons = new Identifier[0];
    private static FormattedCharSequence[] amounts = new FormattedCharSequence[0];
    private static Component[] spoken = new Component[0];
    private static int cellWidth;
    private static int columns = 1;

    private CrucibleHud() {}

    /** Drops the cached content at logout, so no closed level stays held. */
    public static void clear() {
        shown = null;
        shownState = null;
        shownKnowledge = null;
        shownRules = null;
        lines = List.of();
        icons = new Identifier[0];
        amounts = new FormattedCharSequence[0];
        spoken = new Component[0];
    }

    /** The crucible the HUD describes now, or null when it doesn't show. */
    public static @Nullable CrucibleBlockEntity target() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null
                || mc.level == null
                || mc.gui.screen() != null
                || mc.gui.hud.isHidden()
                || mc.getDebugOverlay().showDebugScreen()
                || player.isSpectator()
                || AspectHud.cardShowing()
                || !(mc.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        BlockPos pos = hit.getBlockPos();
        return player.isWithinBlockInteractionRange(pos, 0)
                        && mc.level.getBlockEntity(pos) instanceof CrucibleBlockEntity be
                ? be
                : null;
    }

    /** The layer. */
    @SuppressWarnings("ReferenceEquality") // instance identity is the change signal
    public static void layer(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        CrucibleBlockEntity be = target();
        if (be == null) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        Knowledge known = player == null ? null : KnowledgeService.get(player);
        FluxRules rules = ClientFluxRules.get();
        if (be != shown
                || be.pool().revision() != shownRevision
                || be.capacity() != shownCapacity
                || be.getBlockState() != shownState
                || known != shownKnowledge
                || rules != shownRules
                || be.unreadable() != shownUnreadable) {
            rebuild(be, known, rules);
        }
        CARD.draw(graphics);
    }

    private static void rebuild(
            CrucibleBlockEntity be, @Nullable Knowledge known, @Nullable FluxRules rules) {
        shown = be;
        shownRevision = be.pool().revision();
        shownCapacity = be.capacity();
        shownState = be.getBlockState();
        shownKnowledge = known;
        shownRules = rules;
        shownUnreadable = be.unreadable();
        List<Component> head = new ArrayList<>();
        List<Component> tail = new ArrayList<>();
        List<Component> foot = new ArrayList<>();
        SortedMap<String, Integer> pool = be.pool().snapshot();
        boolean water = shownState.getValue(CrucibleBlock.WATER);
        if (shownUnreadable) {
            head.add(Component.translatable(CrucibleLang.HUD_UNREADABLE));
            icons = new Identifier[0];
            amounts = new FormattedCharSequence[0];
            spoken = new Component[0];
        } else {
            String key =
                    !water
                            ? CrucibleLang.HUD_DRY
                            : shownState.getValue(CrucibleBlock.BOILING)
                                    ? CrucibleLang.HUD_BOILING
                                    : CrucibleLang.HUD_COLD;
            head.add(
                    Component.translatable(
                            key,
                            NumberFormats.integer(be.pool().total()),
                            NumberFormats.integer(shownCapacity)));
            List<String> cells = CrucibleMath.cells(pool, CrucibleLimits.MAX_CARD_ENTRIES);
            icons = new Identifier[cells.size()];
            amounts = new FormattedCharSequence[cells.size()];
            spoken = new Component[cells.size()];
            for (int i = 0; i < cells.size(); i++) {
                String id = cells.get(i);
                int amount = pool.getOrDefault(id, 0);
                ResourceKey<Aspect> aspect =
                        ResourceKey.create(AspectKeys.REGISTRY, Identifier.parse(id));
                Optional<Holder.Reference<Aspect>> holder = holder(aspect);
                boolean heard = known != null && known.known().contains(id);
                icons[i] =
                        holder.isEmpty()
                                ? MISSING
                                : heard ? AspectIcons.of(holder.get()) : AspectIcons.UNHEARD;
                amounts[i] = TextFit.visual(Component.literal(NumberFormats.integer(amount)));
                spoken[i] =
                        (holder.isPresent() && heard
                                        ? AspectLang.name(aspect).copy()
                                        : Component.translatable(CrucibleLang.UNHEARD))
                                .append(" " + NumberFormats.integer(amount));
            }
            if (pool.size() > cells.size()) {
                tail.add(
                        Component.translatable(
                                CrucibleLang.HUD_MORE,
                                NumberFormats.integer(pool.size() - cells.size())));
            }
            if (water && !pool.isEmpty()) {
                int[] spilled = pool.values().stream().mapToInt(Integer::intValue).toArray();
                foot.add(
                        Component.translatable(
                                CrucibleLang.HUD_SPILL,
                                NumberFormats.integer(
                                        FluxMath.essentiaFlux(
                                                spilled, SpillCause.EMPTIED, rules))));
            }
        }
        List<Component> all = new ArrayList<>(head);
        all.addAll(tail);
        all.addAll(foot);
        lines = List.copyOf(all);
        CARD.content(be.getBlockPos(), head, icons.length, tail, foot);
    }

    private static Optional<Holder.Reference<Aspect>> holder(ResourceKey<Aspect> aspect) {
        var level = Minecraft.getInstance().level;
        return level == null
                ? Optional.empty()
                : level.registryAccess().lookupOrThrow(AspectKeys.REGISTRY).get(aspect);
    }

    /** The harness's {@code vftv:crucible} dump: the targeted crucible and the card's lines. */
    public static String dump(String args) {
        JsonObject data = new JsonObject();
        CrucibleBlockEntity be = target();
        data.addProperty("showing", be != null);
        if (be != null) {
            BlockPos pos = be.getBlockPos();
            data.addProperty("pos", pos.getX() + "," + pos.getY() + "," + pos.getZ());
            data.addProperty("water", be.getBlockState().getValue(CrucibleBlock.WATER));
            data.addProperty("boiling", be.getBlockState().getValue(CrucibleBlock.BOILING));
            data.addProperty("unreadable", be.unreadable());
            data.addProperty("capacity", be.capacity());
            data.addProperty("total", be.pool().total());
            JsonObject pool = new JsonObject();
            be.pool().snapshot().forEach(pool::addProperty);
            data.add("pool", pool);
        }
        JsonArray text = new JsonArray();
        lines.forEach(l -> text.add(l.getString()));
        data.add("lines", text);
        data.addProperty("cells", icons.length);
        return data.toString();
    }

    /** The grid: a 16-px icon and the exact amount per cell. */
    private static final class Body implements CrosshairCard.Body {
        @Override
        public int cellWidth(Font font) {
            int widest = 0;
            for (FormattedCharSequence amount : amounts) {
                widest = Math.max(widest, font.width(amount));
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
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, icons[i], cx, cy, 16, 16);
                    graphics.text(font, amounts[i], cx + 18, cy + 4, TEXT, false);
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
