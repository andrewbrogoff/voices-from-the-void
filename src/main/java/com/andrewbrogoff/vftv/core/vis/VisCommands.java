package com.andrewbrogoff.vftv.core.vis;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;

/**
 * The vis feature's {@code /vftv} subtree, {@code node remove|starter} (configuration, "Commands"),
 * built from vanilla Brigadier types only. {@code Vftv.commandRoot} attaches it; the root already
 * requires level 2.
 */
public final class VisCommands {
    private VisCommands() {}

    /** Attaches the feature's literals to {@code root}. */
    public static void attach(
            LiteralArgumentBuilder<CommandSourceStack> root, CommandBuildContext context) {}
}
