package com.andrewbrogoff.vftv.core;

import com.andrewbrogoff.vftv.core.arcane.ArcaneFeature;
import com.andrewbrogoff.vftv.core.arcane.client.ArcaneClientFeature;
import com.andrewbrogoff.vftv.core.armor.ArmorFeature;
import com.andrewbrogoff.vftv.core.armor.client.ArmorClientFeature;
import com.andrewbrogoff.vftv.core.aspect.AspectCommands;
import com.andrewbrogoff.vftv.core.aspect.AspectFeature;
import com.andrewbrogoff.vftv.core.aspect.client.AspectClientFeature;
import com.andrewbrogoff.vftv.core.common.Platform;
import com.andrewbrogoff.vftv.core.common.WorldMigrations;
import com.andrewbrogoff.vftv.core.common.client.ClientNetwork;
import com.andrewbrogoff.vftv.core.crucible.CrucibleFeature;
import com.andrewbrogoff.vftv.core.crucible.client.CrucibleClientFeature;
import com.andrewbrogoff.vftv.core.essentia.EssentiaFeature;
import com.andrewbrogoff.vftv.core.essentia.client.EssentiaClientFeature;
import com.andrewbrogoff.vftv.core.flux.FluxFeature;
import com.andrewbrogoff.vftv.core.flux.client.FluxClientFeature;
import com.andrewbrogoff.vftv.core.focus.FocusFeature;
import com.andrewbrogoff.vftv.core.focus.client.FocusClientFeature;
import com.andrewbrogoff.vftv.core.golem.GolemFeature;
import com.andrewbrogoff.vftv.core.golem.client.GolemClientFeature;
import com.andrewbrogoff.vftv.core.hush.HushCommands;
import com.andrewbrogoff.vftv.core.hush.HushFeature;
import com.andrewbrogoff.vftv.core.hush.client.HushClientFeature;
import com.andrewbrogoff.vftv.core.infusion.InfusionFeature;
import com.andrewbrogoff.vftv.core.infusion.client.InfusionClientFeature;
import com.andrewbrogoff.vftv.core.research.ResearchCommands;
import com.andrewbrogoff.vftv.core.research.ResearchFeature;
import com.andrewbrogoff.vftv.core.research.client.ResearchClientFeature;
import com.andrewbrogoff.vftv.core.road.RoadCommands;
import com.andrewbrogoff.vftv.core.road.RoadFeature;
import com.andrewbrogoff.vftv.core.road.client.RoadClientFeature;
import com.andrewbrogoff.vftv.core.text.TextFeature;
import com.andrewbrogoff.vftv.core.text.client.TextClientFeature;
import com.andrewbrogoff.vftv.core.vis.VisCommands;
import com.andrewbrogoff.vftv.core.vis.VisFeature;
import com.andrewbrogoff.vftv.core.vis.client.VisClientFeature;
import com.andrewbrogoff.vftv.core.warp.WarpCommands;
import com.andrewbrogoff.vftv.core.warp.WarpFeature;
import com.andrewbrogoff.vftv.core.warp.client.WarpClientFeature;
import com.andrewbrogoff.vftv.core.worldgen.WorldgenFeature;
import com.andrewbrogoff.vftv.core.worldgen.client.WorldgenClientFeature;
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
 * <p>Platform listeners call the other methods here in one line each. Every feature is wired here
 * once; a feature ticket edits only its own {@code <F>Feature}, {@code <F>ClientFeature} and {@code
 * <F>Commands}, never this class.
 */
public final class Vftv {
    /** The mod id and namespace. */
    public static final String MOD_ID = "vftv";

    /**
     * The world migration steps, in order: step {@code i} moves the data version to {@code i + 1}.
     */
    private static final List<Consumer<MinecraftServer>> MIGRATIONS = List.of();

    private Vftv() {}

    /** Wires every feature to the platform, leaves first. */
    public static void init(Platform platform) {
        TextFeature.init(platform);
        FluxFeature.init(platform);
        AspectFeature.init(platform);
        WarpFeature.init(platform);
        ResearchFeature.init(platform);
        VisFeature.init(platform);
        ArcaneFeature.init(platform);
        CrucibleFeature.init(platform);
        EssentiaFeature.init(platform);
        InfusionFeature.init(platform);
        FocusFeature.init(platform);
        GolemFeature.init(platform);
        ArmorFeature.init(platform);
        HushFeature.init(platform);
        WorldgenFeature.init(platform);
        RoadFeature.init(platform);
    }

    /** Wires every feature's client half. */
    public static void initClient(ClientNetwork network) {
        TextClientFeature.init(network);
        FluxClientFeature.init(network);
        AspectClientFeature.init(network);
        WarpClientFeature.init(network);
        ResearchClientFeature.init(network);
        VisClientFeature.init(network);
        ArcaneClientFeature.init(network);
        CrucibleClientFeature.init(network);
        EssentiaClientFeature.init(network);
        InfusionClientFeature.init(network);
        FocusClientFeature.init(network);
        GolemClientFeature.init(network);
        ArmorClientFeature.init(network);
        HushClientFeature.init(network);
        WorldgenClientFeature.init(network);
        RoadClientFeature.init(network);
    }

    /** Runs the pending world migrations; called once at server start. */
    public static void onServerStarting(MinecraftServer server) {
        WorldMigrations.runPending(server.getDataStorage(), MIGRATIONS, server);
    }

    /**
     * The {@code /vftv} command root at permission level 2 (configuration, "Commands"), with each
     * owning feature's subtree attached. Brigadier hides the whole tree below level 2.
     */
    public static LiteralArgumentBuilder<CommandSourceStack> commandRoot(
            CommandBuildContext context) {
        LiteralArgumentBuilder<CommandSourceStack> root =
                Commands.literal(MOD_ID)
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
        ResearchCommands.attach(root, context);
        WarpCommands.attach(root, context);
        HushCommands.attach(root, context);
        VisCommands.attach(root, context);
        RoadCommands.attach(root, context);
        AspectCommands.attach(root, context);
        return root;
    }
}
