package com.andrewbrogoff.vftv.neoforge.arcane;

import com.andrewbrogoff.vftv.core.arcane.ArcaneHooks;
import com.andrewbrogoff.vftv.core.arcane.ArcaneRecipe;
import com.andrewbrogoff.vftv.core.arcane.ArcaneRecipes;
import com.andrewbrogoff.vftv.core.arcane.WorktableConversion;
import com.andrewbrogoff.vftv.core.arcane.Worktables;
import com.andrewbrogoff.vftv.core.common.TickBudget;
import com.andrewbrogoff.vftv.neoforge.common.NeoForgeRegistrar;
import net.minecraft.core.BlockPos;
import net.minecraft.util.TriState;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * The arcane feature's NeoForge wiring, called once from the {@code @Mod} constructor: game-bus and
 * mod-bus listeners, capabilities and data-map types. Each listener calls {@code core} in one line.
 */
public final class ArcaneNeoForge {
    private ArcaneNeoForge() {}

    /**
     * Builds the recipe snapshot at start and on reload, sends arcane recipes to clients, installs
     * the platform hooks and listens for a wand turning a table.
     */
    public static void register(IEventBus modBus, IEventBus gameBus, NeoForgeRegistrar registrar) {
        Worktables.install(new Hooks());
        // Posted by dedicated and integrated servers alike before any player can join.
        gameBus.addListener(
                ServerStartingEvent.class,
                event -> ArcaneRecipes.onServerStarting(event.getServer().getRecipeManager()));
        // On join and after every /reload: rebuild first (only if the recipes changed), then send.
        gameBus.addListener(
                OnDatapackSyncEvent.class,
                event -> {
                    ArcaneRecipes.beforeSync(event.getPlayerList().getServer().getRecipeManager());
                    event.sendRecipes(ArcaneRecipe.TYPE);
                });
        gameBus.addListener(ServerStoppedEvent.class, event -> ArcaneRecipes.clearServer());
        // Lowest priority and never for a cancelled event, so a claim mod's cancel wins.
        gameBus.addListener(
                EventPriority.LOWEST,
                false,
                PlayerInteractEvent.RightClickBlock.class,
                event -> {
                    try (var _ = TickBudget.enter("arcane")) {
                        InteractionResult result =
                                WorktableConversion.onRightClick(
                                        event.getEntity(),
                                        event.getHand(),
                                        event.getPos(),
                                        event.getUseBlock() == TriState.FALSE
                                                || event.getUseItem() == TriState.FALSE);
                        if (result != null) {
                            event.setCanceled(true);
                            event.setCancellationResult(result);
                        }
                    }
                });
    }

    /** The platform half of the arcane seam. */
    private static final class Hooks implements ArcaneHooks {
        @Override
        public boolean breakCancelled(Level level, BlockPos pos, BlockState state, Player player) {
            return NeoForge.EVENT_BUS
                    .post(new BreakBlockEvent(level, pos, state, player))
                    .isCanceled();
        }

        @Override
        public void crafted(Player player, ItemStack result, Container grid) {
            EventHooks.firePlayerCraftingEvent(player, result, grid);
        }
    }
}
