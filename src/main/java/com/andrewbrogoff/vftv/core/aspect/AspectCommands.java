package com.andrewbrogoff.vftv.core.aspect;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;

/**
 * The aspect feature's {@code /vftv} subtree, {@code aspects derive} (a level-4 leaf)
 * (configuration, "Commands"), built from vanilla Brigadier types only. {@code Vftv.commandRoot}
 * attaches it; the root already requires level 2.
 */
public final class AspectCommands {
    private AspectCommands() {}

    /** Attaches the feature's literals to {@code root}. */
    public static void attach(
            LiteralArgumentBuilder<CommandSourceStack> root, CommandBuildContext context) {}
}
