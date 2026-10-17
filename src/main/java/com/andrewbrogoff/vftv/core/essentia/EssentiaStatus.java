package com.andrewbrogoff.vftv.core.essentia;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.essentia.EssentiaLimits;
import com.andrewbrogoff.vftv.core.aspect.AspectLang;
import com.andrewbrogoff.vftv.core.essentia.EssentiaLines.State;
import com.andrewbrogoff.vftv.core.essentia.EssentiaLines.View;
import com.andrewbrogoff.vftv.core.essentia.model.EssentiaTuning;
import com.andrewbrogoff.vftv.core.text.StatusLines;
import com.andrewbrogoff.vftv.core.text.model.NumberFormats;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Inspection (essentia spec, "Inspection and teaching"): with both hands empty while sneaking, each
 * use shows the next status page of a jar, tube, valve or Alembic, built on the server from live
 * values. The cursor is per player and resets after 100 ticks or on another block.
 */
public final class EssentiaStatus {
    /** Lang keys, as the spec's table names them. */
    public static final String JAR_CONTENTS = "status.vftv.essentia_jar.contents";

    /** "Empty, holds %s". */
    public static final String JAR_EMPTY = "status.vftv.essentia_jar.empty";

    /** "I can't read what's in it." */
    public static final String JAR_UNREADABLE = "status.vftv.essentia_jar.unreadable";

    /** "Label: %s · pulls at %s · holds at %s". */
    public static final String JAR_RANKS = "status.vftv.essentia_jar.ranks";

    /** The line page's key prefix; the state follows. */
    public static final String LINE = "status.vftv.essentia_line.";

    /** "This tube's label: %s". */
    public static final String TUBE_LABEL = "status.vftv.essentia_tube.label";

    /** "From %s into %s". */
    public static final String ALEMBIC_ENDS = "status.vftv.alembic.ends";

    /** "Label: %s · next: %s". */
    public static final String ALEMBIC_NEXT = "status.vftv.alembic.next";

    /** "something unheard". */
    public static final String UNHEARD = "essentia.vftv.unheard";

    /** "an aspect I no longer know". */
    public static final String MISSING = "essentia.vftv.missing";

    /** "nothing". */
    public static final String NOTHING = "essentia.vftv.nothing";

    /** "none". */
    public static final String LABEL_NONE = "essentia.vftv.label_none";

    /** "a label I can't read". */
    public static final String LABEL_UNREADABLE = "essentia.vftv.label_unreadable";

    private record Cursor(ServerLevel level, BlockPos pos, int page, long time) {}

    private static final Map<UUID, Cursor> CURSORS = new HashMap<>();

    private EssentiaStatus() {}

    /** A use with an empty main hand: the next page if sneaking with both hands empty. */
    public static InteractionResult use(
            Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty()
                || !player.getOffhandItem().isEmpty()
                || !player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
            Component page = page(server, pos, sp, hit.getDirection());
            if (page != null) {
                StatusLines.send(sp, page);
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** Builds the next page for the player at {@code pos}, advancing their cursor. */
    static @Nullable Component page(
            ServerLevel level, BlockPos pos, ServerPlayer player, Direction clicked) {
        long now = level.getGameTime();
        Cursor c = CURSORS.get(player.getUUID());
        int page =
                c == null
                                || !c.level().equals(level)
                                || !c.pos().equals(pos)
                                || now - c.time() > 100
                        ? 0
                        : c.page() + 1;
        BlockState state = level.getBlockState(pos);
        Component[] pages;
        if (level.getBlockEntity(pos) instanceof JarBlockEntity jar) {
            pages = jarPages(jar, player);
        } else if (level.getBlockEntity(pos) instanceof AlembicBlockEntity alembic) {
            pages = alembicPages(level, pos, alembic, player);
        } else if (state.getBlock() instanceof EssentiaTubeBlock
                && level.getBlockEntity(pos) instanceof LabelledBlockEntity tube) {
            View v = EssentiaService.lines(level).of(pos);
            pages =
                    new Component[] {
                        line(v),
                        Component.translatable(TUBE_LABEL, label(tube, player)),
                        last(v, now, player),
                        faults(v)
                    };
        } else if (state.getBlock() instanceof TubeValveBlock) {
            View v = valveView(level, pos, state, clicked);
            pages = new Component[] {line(v), last(v, now, player), faults(v)};
        } else {
            return null;
        }
        page = page % pages.length;
        CURSORS.put(player.getUUID(), new Cursor(level, pos.immutable(), page, now));
        return pages[page];
    }

    private static View valveView(
            ServerLevel level, BlockPos pos, BlockState state, Direction clicked) {
        EssentiaLines lines = EssentiaService.lines(level);
        if (TubeValveBlock.passes(state)) {
            return lines.of(pos);
        }
        Direction.Axis axis = state.getValue(TubeValveBlock.AXIS);
        if (clicked.getAxis() == axis) {
            return lines.of(pos.relative(clicked));
        }
        for (Direction d : Direction.values()) {
            if (d.getAxis() == axis) {
                View v = lines.of(pos.relative(d));
                if (!v.equals(View.NONE)) {
                    return v;
                }
            }
        }
        return View.NONE;
    }

    private static Component[] jarPages(JarBlockEntity jar, ServerPlayer player) {
        if (!jar.readable()) {
            Component unreadable = Component.translatable(JAR_UNREADABLE);
            return new Component[] {unreadable, unreadable};
        }
        EssentiaTuning t = EssentiaService.rules().tuning();
        var contents = jar.contents();
        ResourceKey<Aspect> aspect = contents.aspect();
        Component first =
                aspect == null
                        ? Component.translatable(JAR_EMPTY, NumberFormats.integer(t.jarCapacity()))
                        : Component.translatable(
                                JAR_CONTENTS,
                                name(aspect, player),
                                NumberFormats.integer(contents.amount()),
                                NumberFormats.integer(t.jarCapacity()));
        ResourceKey<Aspect> label = jar.label();
        ResourceKey<Aspect> probe = aspect != null ? aspect : label;
        int suction =
                probe == null ? (jar.label() == null ? t.suctionJar() : 0) : jar.suction(probe);
        int hold = probe == null ? 0 : jar.hold(probe);
        Component second =
                Component.translatable(
                        JAR_RANKS,
                        label == null ? Component.translatable(LABEL_NONE) : name(label, player),
                        NumberFormats.integer(suction),
                        NumberFormats.integer(hold));
        return new Component[] {first, second};
    }

    private static Component[] alembicPages(
            ServerLevel level, BlockPos pos, AlembicBlockEntity alembic, ServerPlayer player) {
        BlockState state = level.getBlockState(pos);
        BlockPos base = pos.relative(state.getValue(AlembicBlock.FACING));
        BlockPos below = pos.below();
        Component ends =
                Component.translatable(
                        ALEMBIC_ENDS, blockName(level, base), blockName(level, below));
        ResourceKey<Aspect> next = alembic.next(level);
        Component second =
                Component.translatable(
                        ALEMBIC_NEXT,
                        label(alembic, player),
                        next == null ? Component.translatable(NOTHING) : name(next, player));
        return new Component[] {ends, second};
    }

    private static Component blockName(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return s.isAir() ? Component.translatable(NOTHING) : s.getBlock().getName();
    }

    private static Component line(View v) {
        String state =
                v.state() == State.READY && v.asleep()
                        ? "asleep"
                        : v.state().name().toLowerCase(Locale.ROOT);
        return Component.translatable(
                LINE + state,
                NumberFormats.integer(v.members()),
                NumberFormats.integer(EssentiaLimits.MAX_MEMBERS),
                NumberFormats.integer(v.endpoints()),
                NumberFormats.integer(EssentiaLimits.MAX_ENDPOINTS));
    }

    private static Component last(View v, long now, ServerPlayer player) {
        ResourceKey<Aspect> a = v.lastAspect();
        return a == null || v.lastTick() < 0
                ? Component.translatable(LINE + "no_moves")
                : Component.translatable(
                        LINE + "last",
                        NumberFormats.integer(v.lastAmount()),
                        name(a, player),
                        NumberFormats.integer(now - v.lastTick()));
    }

    private static Component faults(View v) {
        return Component.translatable(
                LINE + "faults",
                NumberFormats.integer(v.deafArms()),
                NumberFormats.integer(v.stalled()),
                NumberFormats.integer(v.skipped()));
    }

    private static Component label(LabelledBlockEntity be, ServerPlayer player) {
        if (!be.readable()) {
            return Component.translatable(LABEL_UNREADABLE);
        }
        ResourceKey<Aspect> label = be.label();
        if (label == null) {
            return Component.translatable(LABEL_NONE);
        }
        return EssentiaService.labelAdmits(label)
                ? name(label, player)
                : Component.translatable(LABEL_UNREADABLE);
    }

    /** An aspect's name, masked for the viewer: unheard, or missing. */
    public static Component name(ResourceKey<Aspect> aspect, ServerPlayer player) {
        if (!EssentiaService.labelAdmits(aspect, player.level())) {
            return Component.translatable(MISSING);
        }
        return EssentiaService.knows(player, aspect)
                ? AspectLang.name(aspect)
                : Component.translatable(UNHEARD);
    }

    /** Drops every cursor (server stop). */
    static void clear() {
        CURSORS.clear();
    }
}
