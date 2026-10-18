package com.andrewbrogoff.vftv.core.vis;

import com.andrewbrogoff.vftv.api.vis.NodeCause;
import com.andrewbrogoff.vftv.core.text.model.NumberFormats;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * The vis feature's {@code /vftv} subtree, {@code node remove|starter} (configuration, "Commands";
 * wands spec, "Creation, removal and sharing"), built from vanilla Brigadier types only. {@code
 * Vftv.commandRoot} attaches it; the root already requires level 2.
 */
public final class VisCommands {
    private VisCommands() {}

    /** Attaches the feature's literals to {@code root}. */
    public static void attach(
            LiteralArgumentBuilder<CommandSourceStack> root, CommandBuildContext context) {
        root.then(
                Commands.literal("node")
                        .then(
                                Commands.literal("remove")
                                        .then(
                                                Commands.argument("id", LongArgumentType.longArg(1))
                                                        .executes(VisCommands::remove)))
                        .then(
                                Commands.literal("starter")
                                        .then(
                                                Commands.argument(
                                                                "pos", BlockPosArgument.blockPos())
                                                        .executes(VisCommands::starter))));
    }

    private static int remove(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        long id = LongArgumentType.getLong(ctx, "id");
        String n = NumberFormats.integer(id);
        return switch (NodeService.remove(source.getServer(), id, NodeCause.COMMAND)) {
            case REMOVED -> {
                source.sendSuccess(() -> Component.translatable(VisLang.CMD_REMOVED, n), true);
                yield 1;
            }
            case UNKNOWN -> {
                source.sendFailure(Component.translatable(VisLang.CMD_UNKNOWN, n));
                yield 0;
            }
            case UNREADABLE -> {
                source.sendFailure(Component.translatable(VisLang.CMD_UNREADABLE));
                yield 0;
            }
        };
    }

    /**
     * {@code /vftv node starter <pos>} (configuration, "Placing the Wellspring by command"): checks
     * in order, the first failure refusing and reserving no id; then one birth.
     */
    private static int starter(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        if (!Level.OVERWORLD.equals(level.dimension())) {
            source.sendFailure(Component.translatable(VisLang.CMD_STARTER_OVERWORLD));
            return 0;
        }
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        NodeLedger ledger = NodeService.ledger(source.getServer());
        if (ledger == null) {
            source.sendFailure(Component.translatable(VisLang.CMD_UNREADABLE));
            return 0;
        }
        NodeLedger.Starter existing = ledger.starter();
        if (!ledger.starterMissing() && existing != null) {
            source.sendFailure(
                    Component.translatable(
                            VisLang.CMD_STARTER_EXISTS, NumberFormats.integer(existing.id())));
            return 0;
        }
        if (!level.getWorldBorder().isWithinBounds(pos)
                || !level.getBlockState(pos).isAir()
                || !NodeService.admitsStarter(level, pos)) {
            source.sendFailure(Component.translatable(VisLang.CMD_STARTER_BLOCKED));
            return 0;
        }
        long id = NodeService.birthStarter(level, pos, NodeCause.COMMAND);
        source.sendSuccess(
                () ->
                        Component.translatable(
                                VisLang.CMD_STARTER_PLACED,
                                NumberFormats.integer(id),
                                pos.toShortString()),
                true);
        return 1;
    }
}
