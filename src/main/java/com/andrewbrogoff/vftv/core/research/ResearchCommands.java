package com.andrewbrogoff.vftv.core.research;

import com.andrewbrogoff.vftv.core.text.model.SharedMessages;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * The research feature's {@code /vftv} subtree, {@code research grant|revoke|stage|reset} (research
 * spec, "Admin commands"; configuration, "Commands"), built from vanilla Brigadier types only.
 * {@code Vftv.commandRoot} attaches it; the root already requires level 2. Each change runs the
 * resolution and fires events with source {@code COMMAND}.
 */
public final class ResearchCommands {
    /** "%s: learned %s." (the player, then the entry or {@code all}). */
    public static final String GRANTED = "commands.vftv.research.granted";

    /** "%s: forgot %s." */
    public static final String REVOKED = "commands.vftv.research.revoked";

    /** "%s: Stage %s." */
    public static final String STAGE = "commands.vftv.research.stage";

    /** "%s: research reset." */
    public static final String RESET = "commands.vftv.research.reset";

    /** "No research entry %s." */
    public static final String UNKNOWN = "commands.vftv.research.unknown";

    private static final String ALL = "all";

    private static final SuggestionProvider<CommandSourceStack> ENTRIES =
            (c, builder) ->
                    SharedSuggestionProvider.suggestResource(
                            ResearchService.entries(c.getSource().registryAccess())
                                    .keySet()
                                    .stream()
                                    .map(Identifier::parse),
                            builder);

    private ResearchCommands() {}

    /** Attaches the feature's literals to {@code root}. */
    public static void attach(
            LiteralArgumentBuilder<CommandSourceStack> root, CommandBuildContext context) {
        root.then(
                Commands.literal("research")
                        .then(entries("grant", GRANTED, Resolver::grant))
                        .then(entries("revoke", REVOKED, Resolver::revoke))
                        .then(
                                Commands.literal("stage")
                                        .then(
                                                Commands.argument("player", EntityArgument.player())
                                                        .then(
                                                                Commands.argument(
                                                                                "stage",
                                                                                IntegerArgumentType
                                                                                        .integer(
                                                                                                0,
                                                                                                4))
                                                                        .executes(
                                                                                ResearchCommands
                                                                                        ::stage))))
                        .then(
                                Commands.literal("reset")
                                        .then(
                                                Commands.argument("player", EntityArgument.player())
                                                        .executes(ResearchCommands::reset))));
    }

    /** {@code <verb> <player> <entry|all>}, changing entries through {@code change}. */
    private static LiteralArgumentBuilder<CommandSourceStack> entries(
            String verb,
            String reply,
            BiFunction<ServerPlayer, Collection<String>, Integer> change) {
        return Commands.literal(verb)
                .then(
                        Commands.argument("player", EntityArgument.player())
                                .then(
                                        Commands.literal(ALL)
                                                .executes(c -> change(c, null, reply, change)))
                                .then(
                                        Commands.argument("entry", IdentifierArgument.id())
                                                .suggests(ENTRIES)
                                                .executes(
                                                        c ->
                                                                change(
                                                                        c,
                                                                        IdentifierArgument.getId(
                                                                                        c, "entry")
                                                                                .toString(),
                                                                        reply,
                                                                        change))));
    }

    private static int change(
            CommandContext<CommandSourceStack> c,
            @Nullable String entry,
            String reply,
            BiFunction<ServerPlayer, Collection<String>, Integer> change)
            throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(c, "player");
        Collection<String> ids = ResearchService.entries(c.getSource().registryAccess()).keySet();
        if (entry != null) {
            if (!ids.contains(entry)) {
                c.getSource().sendFailure(Component.translatable(UNKNOWN, entry));
                return 0;
            }
            ids = List.of(entry);
        }
        int changed = change.apply(player, ids);
        if (changed < 0) {
            c.getSource().sendFailure(Component.translatable(SharedMessages.UNREADABLE));
            return 0;
        }
        String what = entry == null ? ALL : entry;
        c.getSource()
                .sendSuccess(
                        () -> Component.translatable(reply, player.getDisplayName(), what), true);
        return Math.max(1, changed);
    }

    private static int stage(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(c, "player");
        int stage = IntegerArgumentType.getInteger(c, "stage");
        if (!Resolver.stage(player, stage)) {
            c.getSource().sendFailure(Component.translatable(SharedMessages.UNREADABLE));
            return 0;
        }
        c.getSource()
                .sendSuccess(
                        () -> Component.translatable(STAGE, player.getDisplayName(), stage), true);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(c, "player");
        if (!Resolver.reset(player)) {
            c.getSource().sendFailure(Component.translatable(SharedMessages.UNREADABLE));
            return 0;
        }
        c.getSource()
                .sendSuccess(() -> Component.translatable(RESET, player.getDisplayName()), true);
        return 1;
    }
}
