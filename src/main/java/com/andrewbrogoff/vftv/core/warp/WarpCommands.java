package com.andrewbrogoff.vftv.core.warp;

import com.andrewbrogoff.vftv.api.warp.CommitResult;
import com.andrewbrogoff.vftv.api.warp.WarpCost;
import com.andrewbrogoff.vftv.api.warp.WarpKeys;
import com.andrewbrogoff.vftv.api.warp.WarpLimits;
import com.andrewbrogoff.vftv.api.warp.WarpRules;
import com.andrewbrogoff.vftv.api.warp.WarpSource;
import com.andrewbrogoff.vftv.api.warp.WarpSourceKind;
import com.andrewbrogoff.vftv.core.text.model.NumberFormats;
import com.andrewbrogoff.vftv.core.text.model.SharedMessages;
import com.andrewbrogoff.vftv.core.warp.model.WarpMath;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import java.util.Locale;
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
 * The warp feature's {@code /vftv} subtree, {@code warp get|set|add|clear|fire} (warp spec,
 * "Commands"; configuration, "Commands"), built from vanilla Brigadier types only. {@code
 * Vftv.commandRoot} attaches it; the root already requires level 2. Every change goes through
 * {@code apply} with source {@code COMMAND}; {@code warp fire} runs one Voice event.
 */
public final class WarpCommands {
    /** {@code get}'s reply: the player, the kinds, gear, total, band, counter and chance. */
    public static final String GET = "commands.vftv.warp.get";

    /** A change's reply: the player and the new value. */
    public static final String SET = "commands.vftv.warp.set";

    /** {@code fire}'s reply: the player and the outcome. */
    public static final String FIRED = "commands.vftv.warp.fired";

    private static final DynamicCommandExceptionType UNKNOWN_EVENT =
            new DynamicCommandExceptionType(
                    id -> Component.translatableEscape("argument.id.unknown", id));

    private enum Kind {
        TEMPORARY,
        LASTING,
        PERMANENT,
        COUNTER
    }

    private WarpCommands() {}

    /** Attaches the feature's literals to {@code root}. */
    public static void attach(
            LiteralArgumentBuilder<CommandSourceStack> root, CommandBuildContext context) {
        LiteralArgumentBuilder<CommandSourceStack> set = Commands.literal("set");
        LiteralArgumentBuilder<CommandSourceStack> add = Commands.literal("add");
        for (Kind kind : Kind.values()) {
            String name = kind.name().toLowerCase(Locale.ROOT);
            set.then(
                    Commands.argument("player", EntityArgument.player())
                            .then(
                                    Commands.literal(name)
                                            .then(
                                                    Commands.argument(
                                                                    "n",
                                                                    IntegerArgumentType.integer(
                                                                            0,
                                                                            WarpLimits
                                                                                    .COUNTER_FORMAT_MAX))
                                                            .executes(c -> set(c, kind)))));
            if (kind != Kind.COUNTER) {
                add.then(
                        Commands.argument("player", EntityArgument.player())
                                .then(
                                        Commands.literal(name)
                                                .then(
                                                        Commands.argument(
                                                                        "n",
                                                                        IntegerArgumentType.integer(
                                                                                -WarpMath.COST_MAX,
                                                                                WarpMath.COST_MAX))
                                                                .executes(c -> add(c, kind)))));
            }
        }
        root.then(
                Commands.literal("warp")
                        .then(
                                Commands.literal("get")
                                        .then(
                                                Commands.argument("player", EntityArgument.player())
                                                        .executes(WarpCommands::get)))
                        .then(set)
                        .then(add)
                        .then(
                                Commands.literal("clear")
                                        .then(
                                                Commands.argument("player", EntityArgument.player())
                                                        .executes(WarpCommands::clear)))
                        .then(
                                Commands.literal("fire")
                                        .then(
                                                Commands.argument("player", EntityArgument.player())
                                                        .then(
                                                                Commands.argument(
                                                                                "event",
                                                                                IdentifierArgument
                                                                                        .id())
                                                                        .suggests(
                                                                                (c, b) ->
                                                                                        SharedSuggestionProvider
                                                                                                .suggestResource(
                                                                                                        c.getSource()
                                                                                                                .registryAccess()
                                                                                                                .lookupOrThrow(
                                                                                                                        WarpKeys
                                                                                                                                .VOICE_EVENT_REGISTRY)
                                                                                                                .keySet(),
                                                                                                        b))
                                                                        .executes(
                                                                                WarpCommands
                                                                                        ::fire)))));
    }

    private static int get(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(c, "player");
        WarpRecord r = WarpService.record(player);
        if (r == null) {
            c.getSource().sendFailure(Component.translatable(SharedMessages.UNREADABLE));
            return 0;
        }
        WarpRules rules = WarpRulesService.current();
        int total = r.total();
        int chance = WarpMath.chancePm(total, r.permanent(), r.counter(), rules);
        String percent = NumberFormats.percent(chance);
        c.getSource()
                .sendSuccess(
                        () ->
                                Component.translatable(
                                        GET,
                                        player.getDisplayName(),
                                        r.temporary(),
                                        r.lasting(),
                                        r.permanent(),
                                        r.gear(),
                                        total,
                                        WarpMath.band(total, rules),
                                        WarpMath.counter(r.counter(), rules),
                                        percent),
                        false);
        return 1;
    }

    private static int set(CommandContext<CommandSourceStack> c, Kind kind)
            throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(c, "player");
        int n = IntegerArgumentType.getInteger(c, "n");
        if (kind == Kind.COUNTER) {
            return report(c, player, kind, WarpService.setCounter(player, n));
        }
        WarpRecord r = WarpService.record(player);
        if (r == null) {
            return report(c, player, kind, WarpService.apply(player, cost(kind, 0), command()));
        }
        int max = kind == Kind.PERMANENT ? WarpLimits.P_FORMAT_MAX : WarpLimits.T_MAX;
        int delta = Math.min(n, max) - value(r, kind);
        return report(c, player, kind, WarpService.apply(player, cost(kind, delta), command()));
    }

    private static int add(CommandContext<CommandSourceStack> c, Kind kind)
            throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(c, "player");
        int n = IntegerArgumentType.getInteger(c, "n");
        return report(c, player, kind, WarpService.apply(player, cost(kind, n), command()));
    }

    /**
     * {@code fire}: the event's server part and cue, past the roll and eligibility but not the
     * suppression rows; replies with the outcome.
     */
    private static int fire(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(c, "player");
        Identifier event = IdentifierArgument.getId(c, "event");
        VoiceChecks.Outcome outcome = VoiceChecks.fire(player, event);
        if (outcome == null) {
            throw UNKNOWN_EVENT.create(event);
        }
        String name = outcome.name().toLowerCase(Locale.ROOT);
        c.getSource()
                .sendSuccess(
                        () -> Component.translatable(FIRED, player.getDisplayName(), name), true);
        return outcome == VoiceChecks.Outcome.FIRED ? 1 : 0;
    }

    private static int clear(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(c, "player");
        return report(c, player, null, WarpService.clear(player));
    }

    /** Replies with the new value (the total for {@code clear}), or the refusal. */
    private static int report(
            CommandContext<CommandSourceStack> c,
            ServerPlayer player,
            @Nullable Kind kind,
            CommitResult result) {
        WarpRecord r = WarpService.get(player).readable();
        if (result.status() != CommitResult.Status.OK || r == null) {
            String key =
                    result.status() == CommitResult.Status.BUSY
                            ? SharedMessages.BUSY
                            : SharedMessages.UNREADABLE;
            c.getSource().sendFailure(Component.translatable(key));
            return 0;
        }
        int value = kind == null ? r.total() : value(r, kind);
        c.getSource()
                .sendSuccess(
                        () -> Component.translatable(SET, player.getDisplayName(), value), true);
        return 1;
    }

    private static int value(WarpRecord r, Kind kind) {
        return switch (kind) {
            case TEMPORARY -> r.temporary();
            case LASTING -> r.lasting();
            case PERMANENT -> r.permanent();
            case COUNTER -> r.counter();
        };
    }

    private static WarpCost cost(Kind kind, int n) {
        return switch (kind) {
            case TEMPORARY -> new WarpCost(n, 0, 0);
            case LASTING -> new WarpCost(0, n, 0);
            case PERMANENT, COUNTER -> new WarpCost(0, 0, n);
        };
    }

    private static WarpSource command() {
        return new WarpSource(WarpSourceKind.COMMAND, null);
    }
}
