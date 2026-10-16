package com.andrewbrogoff.vftv.core.research;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;

/**
 * The research feature's {@code /vftv} subtree, {@code research grant|revoke|stage|reset}
 * (configuration, "Commands"), built from vanilla Brigadier types only. {@code Vftv.commandRoot}
 * attaches it; the root already requires level 2.
 */
public final class ResearchCommands {
    private ResearchCommands() {}

    /** Attaches the feature's literals to {@code root}. */
    public static void attach(
            LiteralArgumentBuilder<CommandSourceStack> root, CommandBuildContext context) {}
}
