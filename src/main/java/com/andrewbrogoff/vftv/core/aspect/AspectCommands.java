package com.andrewbrogoff.vftv.core.aspect;

import com.andrewbrogoff.vftv.core.aspect.model.Derivation;
import com.andrewbrogoff.vftv.core.text.model.NumberFormats;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

/**
 * The aspect feature's {@code /vftv} subtree, {@code aspects derive} (a level-4 leaf)
 * (configuration, "Commands"), built from vanilla Brigadier types only. {@code Vftv.commandRoot}
 * attaches it; the root already requires level 2, and the leaf writes files, so it requires level 4
 * as vanilla's {@code /perf} does.
 */
public final class AspectCommands {
    private static final Logger LOGGER = LogUtils.getLogger();

    private AspectCommands() {}

    /** Attaches the feature's literals to {@code root}. */
    public static void attach(
            LiteralArgumentBuilder<CommandSourceStack> root, CommandBuildContext context) {
        root.then(
                Commands.literal("aspects")
                        .requires(Commands.hasPermission(Commands.LEVEL_OWNERS))
                        .then(Commands.literal("derive").executes(c -> derive(c.getSource()))));
    }

    private static int derive(CommandSourceStack source) {
        DerivationExport.Outcome outcome = DerivationExport.run(source.getServer());
        switch (outcome) {
            case DerivationExport.Outcome.Installed(var ids) -> {
                source.sendFailure(
                        Component.translatable(
                                AspectLang.DERIVE_INSTALLED, String.join(", ", ids)));
                return 0;
            }
            case DerivationExport.Outcome.TooLarge(var recipes, var edges) -> {
                source.sendFailure(
                        Component.translatable(
                                AspectLang.DERIVE_TOO_LARGE,
                                NumberFormats.integer(recipes),
                                NumberFormats.integer(edges),
                                NumberFormats.integer(Derivation.MAX_RECIPES),
                                NumberFormats.integer(Derivation.MAX_EDGES)));
                return 0;
            }
            case DerivationExport.Outcome.Failed(var message, var previous) -> {
                LOGGER.error(
                        "The aspect derivation export failed ({}); the previous export is at {}",
                        message,
                        previous);
                source.sendFailure(
                        Component.translatable(
                                AspectLang.DERIVE_FAILED, message, previous.toString()));
                return 0;
            }
            case DerivationExport.Outcome.Done(var values, var packs, var dir, var leftover) -> {
                if (leftover != null) {
                    LOGGER.warn("Could not remove the previous aspect export at {}", leftover);
                    source.sendSuccess(
                            () ->
                                    Component.translatable(
                                            AspectLang.DERIVE_CLEANUP_FAILED,
                                            NumberFormats.integer(values),
                                            NumberFormats.integer(packs),
                                            leftover.toString()),
                            true);
                } else {
                    source.sendSuccess(
                            () ->
                                    Component.translatable(
                                            AspectLang.DERIVE_SUCCESS,
                                            NumberFormats.integer(values),
                                            NumberFormats.integer(packs),
                                            dir.toString()),
                            true);
                }
                return values;
            }
        }
    }
}
