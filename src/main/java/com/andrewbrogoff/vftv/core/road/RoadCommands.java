package com.andrewbrogoff.vftv.core.road;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;

/**
 * The road feature's {@code /vftv} subtree, {@code road get|progress|evacuate} (configuration,
 * "Commands"), built from vanilla Brigadier types only. {@code Vftv.commandRoot} attaches it; the
 * root already requires level 2.
 */
public final class RoadCommands {
    private RoadCommands() {}

    /** Attaches the feature's literals to {@code root}. */
    public static void attach(
            LiteralArgumentBuilder<CommandSourceStack> root, CommandBuildContext context) {}
}
