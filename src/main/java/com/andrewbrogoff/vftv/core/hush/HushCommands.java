package com.andrewbrogoff.vftv.core.hush;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;

/**
 * The hush feature's {@code /vftv} subtree, {@code flux get|add|clear|repair} and {@code hush
 * restore} (configuration, "Commands"), built from vanilla Brigadier types only. {@code
 * Vftv.commandRoot} attaches it; the root already requires level 2.
 */
public final class HushCommands {
    private HushCommands() {}

    /** Attaches the feature's literals to {@code root}. */
    public static void attach(
            LiteralArgumentBuilder<CommandSourceStack> root, CommandBuildContext context) {}
}
