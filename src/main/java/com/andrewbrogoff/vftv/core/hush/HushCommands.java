package com.andrewbrogoff.vftv.core.hush;

import com.andrewbrogoff.vftv.api.flux.FluxClearSource;
import com.andrewbrogoff.vftv.api.flux.FluxSource;
import com.andrewbrogoff.vftv.api.hush.HushLimits;
import com.andrewbrogoff.vftv.core.hush.model.BallTables;
import com.andrewbrogoff.vftv.core.text.model.NumberFormats;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.UUID;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * The hush feature's {@code /vftv} subtree, {@code flux get|add|clear|repair} and {@code hush
 * restore} (Hush spec, "Commands"), built from vanilla Brigadier types only. {@code
 * Vftv.commandRoot} attaches it; the root already requires level 2. No command loads a chunk.
 */
public final class HushCommands {
    private static final String P = HushLang.COMMANDS;

    private HushCommands() {}

    /** Attaches the feature's literals to {@code root}. */
    public static void attach(
            LiteralArgumentBuilder<CommandSourceStack> root, CommandBuildContext context) {
        root.then(
                Commands.literal("flux")
                        .then(
                                Commands.literal("get")
                                        .executes(
                                                c ->
                                                        get(
                                                                c,
                                                                BlockPos.containing(
                                                                        c.getSource()
                                                                                .getPosition())))
                                        .then(
                                                Commands.argument(
                                                                "pos", BlockPosArgument.blockPos())
                                                        .executes(
                                                                c ->
                                                                        get(
                                                                                c,
                                                                                BlockPosArgument
                                                                                        .getBlockPos(
                                                                                                c,
                                                                                                "pos")))))
                        .then(
                                Commands.literal("add")
                                        .then(
                                                Commands.argument(
                                                                "pos", BlockPosArgument.blockPos())
                                                        .then(
                                                                Commands.argument(
                                                                                "amount",
                                                                                IntegerArgumentType
                                                                                        .integer(
                                                                                                1,
                                                                                                HushLimits
                                                                                                        .FLUX_FORMAT_MAX))
                                                                        .executes(
                                                                                HushCommands
                                                                                        ::add))))
                        .then(
                                Commands.literal("clear")
                                        .then(
                                                Commands.argument(
                                                                "pos", BlockPosArgument.blockPos())
                                                        .executes(c -> clear(c, 0))
                                                        .then(
                                                                Commands.argument(
                                                                                "radius",
                                                                                IntegerArgumentType
                                                                                        .integer(
                                                                                                0,
                                                                                                4))
                                                                        .executes(
                                                                                c ->
                                                                                        clear(
                                                                                                c,
                                                                                                IntegerArgumentType
                                                                                                        .getInteger(
                                                                                                                c,
                                                                                                                "radius"))))))
                        .then(
                                Commands.literal("repair")
                                        .then(
                                                Commands.argument(
                                                                "pos", BlockPosArgument.blockPos())
                                                        .executes(HushCommands::repair))));
        root.then(
                Commands.literal("hush")
                        .then(
                                Commands.literal("restore")
                                        .then(
                                                Commands.argument(
                                                                "pos", BlockPosArgument.blockPos())
                                                        .then(
                                                                Commands.argument(
                                                                                "radius",
                                                                                IntegerArgumentType
                                                                                        .integer(
                                                                                                0,
                                                                                                BallTables
                                                                                                        .MAX))
                                                                        .executes(
                                                                                c -> restore(c, 0))
                                                                        .then(
                                                                                Commands.argument(
                                                                                                "from",
                                                                                                IntegerArgumentType
                                                                                                        .integer(
                                                                                                                0))
                                                                                        .executes(
                                                                                                c ->
                                                                                                        restore(
                                                                                                                c,
                                                                                                                IntegerArgumentType
                                                                                                                        .getInteger(
                                                                                                                                c,
                                                                                                                                "from"))))))));
    }

    private static @org.jspecify.annotations.Nullable LevelChunk loaded(
            CommandSourceStack source, BlockPos pos) {
        return FluxService.chunkNow(source.getLevel(), ChunkPos.pack(pos));
    }

    private static int notLoaded(CommandSourceStack source) {
        source.sendFailure(Component.translatable(P + "not_loaded"));
        return 0;
    }

    private static Component status(String key) {
        return Component.translatable(P + "status." + key);
    }

    private static int get(CommandContext<CommandSourceStack> c, BlockPos pos) {
        CommandSourceStack source = c.getSource();
        ServerLevel level = source.getLevel();
        LevelChunk chunk = loaded(source, pos);
        if (chunk == null) {
            return notLoaded(source);
        }
        Aura aura = FluxService.aura(chunk);
        long[] pending = FluxService.pendingAndOverflow(level);
        BlockPos seep = aura == null ? null : aura.seep();
        UUID spiller = aura == null ? null : aura.spiller();
        ServerPlayer known =
                spiller == null ? null : source.getServer().getPlayerList().getPlayer(spiller);
        Component line =
                Component.translatable(
                        P + "get",
                        status(aura == null ? "none" : aura.readable() ? "readable" : "unreadable"),
                        NumberFormats.integer(aura == null ? 0 : aura.flux()),
                        seep == null ? "-" : seep.getX() + ", " + seep.getY() + ", " + seep.getZ(),
                        known != null
                                ? known.getName()
                                : Component.literal(spiller == null ? "-" : spiller.toString()),
                        Long.toUnsignedString(aura == null ? 0 : aura.steps()),
                        Long.toUnsignedString(aura == null ? 0 : aura.taken()),
                        status(
                                HushService.saturated(level, chunk.getPos())
                                        ? "saturated"
                                        : "waiting"),
                        NumberFormats.integer(pending[0]),
                        NumberFormats.integer(pending[1]));
        source.sendSuccess(() -> line, false);
        return 1;
    }

    private static int add(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        CommandSourceStack source = c.getSource();
        BlockPos pos = BlockPosArgument.getBlockPos(c, "pos");
        if (loaded(source, pos) == null) {
            return notLoaded(source);
        }
        FluxService.DepositResult r =
                FluxService.deposit(
                        source.getLevel(),
                        pos,
                        IntegerArgumentType.getInteger(c, "amount"),
                        new FluxSource.Command(),
                        source.getPlayer());
        source.sendSuccess(
                () ->
                        Component.translatable(
                                P + "added",
                                NumberFormats.integer(r.generated()),
                                NumberFormats.integer(r.retained()),
                                NumberFormats.integer(r.pending()),
                                NumberFormats.integer(r.overflow())),
                true);
        return 1;
    }

    private static int clear(CommandContext<CommandSourceStack> c, int radius)
            throws CommandSyntaxException {
        CommandSourceStack source = c.getSource();
        BlockPos pos = BlockPosArgument.getBlockPos(c, "pos");
        if (loaded(source, pos) == null) {
            return notLoaded(source);
        }
        ServerLevel level = source.getLevel();
        ChunkPos centre = ChunkPos.containing(pos);
        long total = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                LevelChunk chunk =
                        FluxService.chunkNow(
                                level, ChunkPos.pack(centre.x() + dx, centre.z() + dz));
                if (chunk != null) {
                    total +=
                            FluxService.clear(
                                    level,
                                    chunk,
                                    HushLimits.FLUX_FORMAT_MAX,
                                    FluxClearSource.COMMAND,
                                    source.getPlayer());
                }
            }
        }
        long cleared = total;
        source.sendSuccess(
                () -> Component.translatable(P + "cleared", NumberFormats.integer(cleared)), true);
        return 1;
    }

    private static int repair(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        CommandSourceStack source = c.getSource();
        BlockPos pos = BlockPosArgument.getBlockPos(c, "pos");
        LevelChunk chunk = loaded(source, pos);
        if (chunk == null) {
            return notLoaded(source);
        }
        if (!FluxService.repair(source.getLevel(), chunk)) {
            source.sendFailure(Component.translatable(P + "not_unreadable"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(P + "repaired"), true);
        return 1;
    }

    /**
     * {@code hush restore <pos> <radius> [<from>]}: the ball in the tables' order from index {@code
     * from}, at most {@code MAX_COMMAND_CELLS} examined and {@code MAX_COMMAND_MUTATIONS} restores;
     * cells of unloaded chunks count as examined. No claim events.
     */
    private static int restore(CommandContext<CommandSourceStack> c, int from)
            throws CommandSyntaxException {
        CommandSourceStack source = c.getSource();
        BlockPos centre = BlockPosArgument.getBlockPos(c, "pos");
        if (loaded(source, centre) == null) {
            return notLoaded(source);
        }
        ServerLevel level = source.getLevel();
        int radius = IntegerArgumentType.getInteger(c, "radius");
        int size = BallTables.size(radius);
        int restored = 0;
        int index = Math.min(from, size);
        try (var guard = HushService.GUARD.tryEnter(level.dimension())) {
            if (guard == null) {
                source.sendFailure(
                        Component.translatable(
                                com.andrewbrogoff.vftv.core.text.model.SharedMessages.BUSY));
                return 0;
            }
            int examined = 0;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            while (index < size
                    && examined < HushLimits.MAX_COMMAND_CELLS
                    && restored < HushLimits.MAX_COMMAND_MUTATIONS) {
                pos.set(
                        centre.getX() + BallTables.dx(radius, index),
                        centre.getY() + BallTables.dy(radius, index),
                        centre.getZ() + BallTables.dz(radius, index));
                index++;
                examined++;
                if (HushService.ticking(level, pos) == null) {
                    continue;
                }
                if (HushService.restoreBlock(level, pos, HushService.Restorer.COMMAND)
                        || (HushService.journaled(level, pos)
                                && HushService.restoreNode(
                                        level, pos, HushService.Restorer.COMMAND))) {
                    restored++;
                }
            }
        }
        int n = restored;
        int next = index;
        source.sendSuccess(
                () ->
                        next >= size
                                ? Component.translatable(
                                        "commands.vftv.hush.restored_done",
                                        NumberFormats.integer(n))
                                : Component.translatable(
                                        "commands.vftv.hush.restored",
                                        NumberFormats.integer(n),
                                        NumberFormats.integer(next)),
                true);
        return n;
    }
}
