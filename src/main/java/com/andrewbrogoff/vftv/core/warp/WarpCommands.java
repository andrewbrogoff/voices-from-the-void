package com.andrewbrogoff.vftv.core.warp;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;

/**
 * The warp feature's {@code /vftv} subtree, {@code warp get|set|add|clear|fire} (configuration,
 * "Commands"), built from vanilla Brigadier types only. {@code Vftv.commandRoot} attaches it; the
 * root already requires level 2.
 */
public final class WarpCommands {
    private WarpCommands() {}

    /** Attaches the feature's literals to {@code root}. */
    public static void attach(
            LiteralArgumentBuilder<CommandSourceStack> root, CommandBuildContext context) {}
}
