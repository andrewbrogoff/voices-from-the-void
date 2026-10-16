package com.andrewbrogoff.vftv.core;

import com.andrewbrogoff.vftv.core.common.WorldMigrations;
import com.andrewbrogoff.vftv.core.common.client.ClientNetwork;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;

/**
 * The one entry into {@code core}. The platform's entrypoint calls {@link #init} once, during mod
 * construction, and each feature's init hangs off it; there is no service discovery (ADR 0002).
 *
 * <p>Platform listeners call the other methods here in one line each.
 */
public final class Vftv {
    /** The mod id and namespace. */
    public static final String MOD_ID = "vftv";

    /**
     * The world migration steps, in order: step {@code i} moves the data version to {@code i + 1}.
     */
    private static final List<Consumer<MinecraftServer>> MIGRATIONS = List.of();

    private Vftv() {}

    /** Wires every feature to the platform. Features add their init calls here. */
    public static void init(Platform platform) {
        // No feature exists yet; each system ticket adds its feature's init(platform) here.
    }

    /** Wires every feature's client half. Features add their client init calls here. */
    public static void initClient(ClientNetwork network) {
        // No feature has a client half yet.
    }

    /** Runs the pending world migrations; called once at server start. */
    public static void onServerStarting(MinecraftServer server) {
        WorldMigrations.runPending(server.getDataStorage(), MIGRATIONS, server);
    }

    /**
     * The {@code /vftv} command root at permission level 2 (configuration, "Commands"), with each
     * feature's subtree attached. Brigadier hides the whole tree below level 2.
     */
    public static LiteralArgumentBuilder<CommandSourceStack> commandRoot(
            CommandBuildContext context) {
        // Each system ticket attaches its feature's subtree here.
        return Commands.literal(MOD_ID)
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
    }
}
