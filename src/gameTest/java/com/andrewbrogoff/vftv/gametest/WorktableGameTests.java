package com.andrewbrogoff.vftv.gametest;

import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.api.neoforge.vis.VisSpentEvent;
import com.andrewbrogoff.vftv.api.vis.VisKind;
import com.andrewbrogoff.vftv.api.vis.VisQuote;
import com.andrewbrogoff.vftv.api.vis.WandPartKeys;
import com.andrewbrogoff.vftv.core.arcane.ArcaneContent;
import com.andrewbrogoff.vftv.core.arcane.ArcanePreview;
import com.andrewbrogoff.vftv.core.arcane.ArcaneRecipe;
import com.andrewbrogoff.vftv.core.arcane.ArcaneRecipes;
import com.andrewbrogoff.vftv.core.arcane.WorktableCraft;
import com.andrewbrogoff.vftv.core.arcane.WorktableMenu;
import com.andrewbrogoff.vftv.core.arcane.WorktableStatus;
import com.andrewbrogoff.vftv.core.arcane.Worktables;
import com.andrewbrogoff.vftv.core.common.RawRecordCodec.Retained;
import com.andrewbrogoff.vftv.core.research.ResearchService;
import com.andrewbrogoff.vftv.core.research.model.ResearchRecord;
import com.andrewbrogoff.vftv.core.vis.VisContent;
import com.andrewbrogoff.vftv.core.vis.VisPools;
import com.andrewbrogoff.vftv.core.vis.WandAssemblyRecipe;
import com.andrewbrogoff.vftv.core.vis.WandParts;
import com.andrewbrogoff.vftv.core.vis.WandState;
import com.mojang.datafixers.util.Either;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.TriState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import org.jspecify.annotations.Nullable;

/**
 * The Worktable's GameTests (arcane spec, "Tests", GameTest): turning a table, the craft payload
 * end to end, hostile payloads, the batch and its stops, reloads, and the menu's vanilla paths.
 * Each payload a test sends after the first goes in a later tick, past the one-per-tick rate.
 */
final class WorktableGameTests {
    private static final BlockPos AT = new BlockPos(1, 1, 1);
    private static final Identifier TEST_RECIPE =
            Identifier.fromNamespaceAndPath("vftv_gametest", "arcane_test");
    private static final Set<BlockPos> DENY_ITEM = new HashSet<>();
    private static final Set<BlockPos> CANCEL_BREAK = new HashSet<>();
    private static final Set<BlockPos> REPLACE_ON_BREAK = new HashSet<>();
    private static volatile Consumer<Object> spy = e -> {};

    private WorktableGameTests() {}

    static void register(Map<String, Consumer<GameTestHelper>> tests) {
        tests.put("arcane_worktable_registered", WorktableGameTests::registered);
        tests.put("arcane_turn_learned", WorktableGameTests::turnLearned);
        tests.put("arcane_turn_refusals", WorktableGameTests::turnRefusals);
        tests.put("arcane_turn_protection", WorktableGameTests::turnProtection);
        tests.put("arcane_craft_end_to_end", WorktableGameTests::craftEndToEnd);
        tests.put("arcane_bell_in_world", WorktableGameTests::bellInWorld);
        tests.put("arcane_hostile_payloads", WorktableGameTests::hostile);
        tests.put("arcane_flood_past_rate", WorktableGameTests::flood);
        tests.put("arcane_batch", WorktableGameTests::batch);
        tests.put("arcane_batch_stops", WorktableGameTests::batchStops);
        tests.put("arcane_notify_failures", WorktableGameTests::notifyFailures);
        tests.put("arcane_reload_same_id", WorktableGameTests::reloadSameId);
        tests.put("arcane_status_precedence", WorktableGameTests::statusPrecedence);
        tests.put("arcane_vanilla_paths", WorktableGameTests::vanillaPaths);
        tests.put("arcane_close_returns_items", WorktableGameTests::closeReturnsItems);
        tests.put("arcane_two_players", WorktableGameTests::twoPlayers);
        tests.put("arcane_preview_reference_wand", WorktableGameTests::referenceWand);
        NeoForge.EVENT_BUS.addListener(VisSpentEvent.class, e -> spy.accept(e));
        NeoForge.EVENT_BUS.addListener(PlayerEvent.ItemCraftedEvent.class, e -> spy.accept(e));
        NeoForge.EVENT_BUS.addListener(
                EventPriority.HIGH,
                PlayerInteractEvent.RightClickBlock.class,
                e -> {
                    if (DENY_ITEM.contains(e.getPos())) {
                        e.setUseItem(TriState.FALSE);
                    }
                });
        NeoForge.EVENT_BUS.addListener(
                BreakBlockEvent.class,
                e -> {
                    if (CANCEL_BREAK.contains(e.getPos())) {
                        e.setCanceled(true);
                    } else if (REPLACE_ON_BREAK.contains(e.getPos())) {
                        e.getLevel().setBlock(e.getPos(), Blocks.STONE.defaultBlockState(), 3);
                    }
                });
    }

    // --- fixtures -----------------------------------------------------------------------------

    private static ItemStack wand(int each) {
        ItemStack w = WandAssemblyRecipe.wand(WandPartKeys.WOODEN_ROD, WandPartKeys.COPPER_CAP);
        TreeMap<String, Integer> pools = new TreeMap<>();
        for (String p :
                List.of(
                        "vftv:aer",
                        "vftv:aqua",
                        "vftv:ignis",
                        "vftv:ordo",
                        "vftv:perditio",
                        "vftv:terra")) {
            pools.put(p, each);
        }
        w.set(VisContent.vis(), new Retained.Read<>(new VisPools(pools)));
        return w;
    }

    private static int pool(ItemStack wand, String aspect) {
        return WandState.of(wand, WandParts.last()) instanceof WandState.Ready r
                ? r.pool(ResourceKey.create(AspectKeys.REGISTRY, Identifier.parse(aspect)))
                : -1;
    }

    /** A Worktable at {@link #AT}, a player beside it, and their open menu. */
    private record Bench(ServerPlayer player, WorktableMenu menu, BlockPos pos) {}

    private static Bench bench(GameTestHelper helper, boolean learned) {
        helper.setBlock(AT, ArcaneContent.worktable());
        BlockPos pos = helper.absolutePos(AT);
        ServerPlayer player = ResearchTestSupport.player(helper);
        player.snapTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 2.5, 0, 0);
        if (learned) {
            ResearchTestSupport.learn(player, ArcaneContent.ARCANE_CRAFTING);
        }
        WorktableMenu menu =
                new WorktableMenu(
                        7,
                        player.getInventory(),
                        ContainerLevelAccess.create(helper.getLevel(), pos));
        player.containerMenu = menu;
        return new Bench(player, menu, pos);
    }

    /** The test recipe (a stick and a copper ingot make a bell; Aer 2, Ordo 1). */
    private static void simple(WorktableMenu menu, int count) {
        menu.grid().setItem(0, new ItemStack(Items.STICK, count));
        menu.grid().setItem(1, new ItemStack(Items.COPPER_INGOT, count));
    }

    /** The payload the client would send for the menu's current state. */
    private static WorktableCraft payload(WorktableMenu menu, boolean batch) {
        Worktables.Evaluation e = Worktables.evaluate(menu);
        RecipeHolder<ArcaneRecipe> recipe = Objects.requireNonNull(e.recipe(), "a recipe");
        return new WorktableCraft(
                menu.containerId,
                menu.revision(),
                recipe.id().identifier(),
                WorktableCraft.Echo.of(Objects.requireNonNull(e.quote(), "a quote")),
                batch);
    }

    private static List<Object> events(Runnable body) {
        List<Object> seen = new ArrayList<>();
        spy = seen::add;
        try {
            body.run();
        } finally {
            spy = e -> {};
        }
        return seen;
    }

    private static long count(List<Object> events, Class<?> type) {
        return events.stream().filter(type::isInstance).count();
    }

    /** A snapshot of everything a refusal must leave unchanged: grid, wand and output. */
    private static List<ItemStack> state(WorktableMenu menu) {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i <= WorktableMenu.OUTPUT; i++) {
            out.add(menu.getSlot(i).getItem().copy());
        }
        return out;
    }

    private static boolean same(List<ItemStack> a, List<ItemStack> b) {
        for (int i = 0; i < a.size(); i++) {
            if (!ItemStack.matches(a.get(i), b.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static InteractionResult use(ServerPlayer player, BlockPos pos) {
        return player.gameMode.useItemOn(
                player,
                player.level(),
                player.getMainHandItem(),
                InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }

    // --- the block ----------------------------------------------------------------------------

    /** The block, its item, the menu type, the conversion tag and the axe tag. */
    private static void registered(GameTestHelper helper) {
        helper.assertTrue(
                Blocks.CRAFTING_TABLE
                        .defaultBlockState()
                        .is(com.andrewbrogoff.vftv.api.arcane.ArcaneKeys.CONVERTS_TO_WORKTABLE),
                "the crafting table converts");
        helper.assertTrue(
                ArcaneContent.worktable()
                        .defaultBlockState()
                        .is(net.minecraft.tags.BlockTags.MINEABLE_WITH_AXE),
                "the Worktable is axe-mineable");
        helper.assertValueEqual(
                ArcaneContent.worktableItem().getDefaultMaxStackSize(), 64, "stacks to 64");
        helper.assertTrue(
                !ArcaneContent.worktable().defaultBlockState().hasBlockEntity(), "no block entity");
        helper.succeed();
    }

    /** A learned player with any wand turns a crafting table; the click is consumed. */
    private static void turnLearned(GameTestHelper helper) {
        helper.setBlock(AT, Blocks.CRAFTING_TABLE);
        BlockPos pos = helper.absolutePos(AT);
        ServerPlayer player = ResearchTestSupport.player(helper);
        player.snapTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 2.5, 0, 0);
        ResearchTestSupport.learn(player, ArcaneContent.ARCANE_CRAFTING);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VisContent.wand()));
        helper.assertValueEqual(use(player, pos), InteractionResult.SUCCESS, "consumed");
        helper.assertBlockPresent(ArcaneContent.worktable(), AT);
        helper.succeed();
    }

    /**
     * Unlearned opens the crafting table; sneaking, a spectator, adventure mode, no wand and a
     * tagged block with a block entity change nothing.
     */
    private static void turnRefusals(GameTestHelper helper) {
        helper.setBlock(AT, Blocks.CRAFTING_TABLE);
        BlockPos pos = helper.absolutePos(AT);
        ServerPlayer player = ResearchTestSupport.player(helper);
        player.snapTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 2.5, 0, 0);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VisContent.wand()));
        use(player, pos);
        helper.assertBlockPresent(Blocks.CRAFTING_TABLE, AT);
        helper.assertTrue(
                !(player.containerMenu instanceof WorktableMenu)
                        && !(player.containerMenu
                                instanceof net.minecraft.world.inventory.InventoryMenu),
                "unlearned: the crafting table opened as usual");
        player.closeContainer();

        ResearchTestSupport.learn(player, ArcaneContent.ARCANE_CRAFTING);
        player.setShiftKeyDown(true);
        use(player, pos);
        helper.assertBlockPresent(Blocks.CRAFTING_TABLE, AT);
        player.setShiftKeyDown(false);

        player.setGameMode(GameType.ADVENTURE);
        use(player, pos);
        helper.assertBlockPresent(Blocks.CRAFTING_TABLE, AT);
        player.setGameMode(GameType.SPECTATOR);
        use(player, pos);
        helper.assertBlockPresent(Blocks.CRAFTING_TABLE, AT);
        player.setGameMode(GameType.SURVIVAL);

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        use(player, pos);
        helper.assertBlockPresent(Blocks.CRAFTING_TABLE, AT);
        player.closeContainer();

        // The gametest pack tags the barrel; its block entity keeps it from converting.
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VisContent.wand()));
        helper.setBlock(AT, Blocks.BARREL);
        use(player, pos);
        helper.assertBlockPresent(Blocks.BARREL, AT);
        player.closeContainer();
        helper.succeed();
    }

    /**
     * A denied item use, a cancelled {@code BreakBlockEvent}, and a {@code BreakBlockEvent}
     * listener that replaces the block each leave the turn undone.
     */
    private static void turnProtection(GameTestHelper helper) {
        helper.setBlock(AT, Blocks.CRAFTING_TABLE);
        BlockPos pos = helper.absolutePos(AT);
        ServerPlayer player = ResearchTestSupport.player(helper);
        player.snapTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 2.5, 0, 0);
        ResearchTestSupport.learn(player, ArcaneContent.ARCANE_CRAFTING);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VisContent.wand()));
        try {
            DENY_ITEM.add(pos);
            use(player, pos);
            helper.assertBlockPresent(Blocks.CRAFTING_TABLE, AT);
            DENY_ITEM.remove(pos);
            player.closeContainer();

            CANCEL_BREAK.add(pos);
            helper.assertValueEqual(use(player, pos), InteractionResult.FAIL, "cancelled");
            helper.assertBlockPresent(Blocks.CRAFTING_TABLE, AT);
            CANCEL_BREAK.remove(pos);

            REPLACE_ON_BREAK.add(pos);
            helper.assertValueEqual(use(player, pos), InteractionResult.FAIL, "rechecked");
            helper.assertBlockPresent(Blocks.STONE, AT);
        } finally {
            DENY_ITEM.remove(pos);
            CANCEL_BREAK.remove(pos);
            REPLACE_ON_BREAK.remove(pos);
        }
        helper.succeed();
    }

    // --- crafting -----------------------------------------------------------------------------

    /** One craft: grid consumed, vis spent, the result in the output, one of each event. */
    private static void craftEndToEnd(GameTestHelper helper) {
        Bench b = bench(helper, true);
        b.menu().wandSlot().setItem(0, wand(20));
        simple(b.menu(), 1);
        Worktables.Evaluation e = Worktables.evaluate(b.menu());
        helper.assertValueEqual(e.status(), WorktableStatus.READY, "ready");
        helper.assertValueEqual(
                Objects.requireNonNull(e.recipe()).id().identifier(), TEST_RECIPE, "the recipe");
        WorktableCraft p = payload(b.menu(), false);
        List<Object> seen = events(() -> Worktables.onCraft(p, b.player()));
        helper.assertTrue(b.menu().output().getItem(0).is(Items.BELL), "the bell");
        helper.assertTrue(b.menu().grid().isEmpty(), "the grid consumed");
        ItemStack w = b.menu().wandSlot().getItem(0);
        helper.assertValueEqual(pool(w, "vftv:aer"), 18, "Aer spent");
        helper.assertValueEqual(pool(w, "vftv:ordo"), 19, "Ordo spent");
        helper.assertValueEqual(count(seen, VisSpentEvent.class), 1L, "one VisSpentEvent");
        helper.assertValueEqual(
                count(seen, PlayerEvent.ItemCraftedEvent.class), 1L, "one ItemCraftedEvent");
        helper.assertValueEqual(b.menu().refusal(), 0, "no refusal");
        helper.succeed();
    }

    /** The shipped {@code vftv:bell} crafts in-world from its Stage 1 ingredients. */
    private static void bellInWorld(GameTestHelper helper) {
        Bench b = bench(helper, true);
        b.menu().wandSlot().setItem(0, wand(32));
        List<ItemStack> grid =
                List.of(
                        new ItemStack(Items.OAK_SLAB),
                        new ItemStack(Items.OAK_SLAB),
                        new ItemStack(Items.OAK_SLAB),
                        new ItemStack(Items.COPPER_INGOT),
                        new ItemStack(Items.AMETHYST_SHARD),
                        new ItemStack(Items.COPPER_INGOT),
                        new ItemStack(Items.COPPER_INGOT),
                        ItemStack.EMPTY,
                        new ItemStack(Items.COPPER_INGOT));
        for (int i = 0; i < 9; i++) {
            b.menu().grid().setItem(i, grid.get(i));
        }
        Worktables.Evaluation e = Worktables.evaluate(b.menu());
        helper.assertValueEqual(e.status(), WorktableStatus.READY, "ready");
        helper.assertValueEqual(
                Objects.requireNonNull(e.recipe()).id().identifier(),
                Identifier.fromNamespaceAndPath("vftv", "bell"),
                "vftv:bell");
        Worktables.onCraft(payload(b.menu(), false), b.player());
        helper.assertTrue(b.menu().output().getItem(0).is(Items.BELL), "the Bell");
        helper.assertValueEqual(pool(b.menu().wandSlot().getItem(0), "vftv:aer"), 24, "Aer 8");
        helper.succeed();
    }

    /**
     * Hostile payloads, one per tick: no menu, a wrong container id, a stale revision, a stale
     * echo, a stale recipe id, a spectator, an unlearned player and a broken table. Each leaves
     * grid, wand and output unchanged and posts no event; each in-menu refusal sets its code.
     */
    private static void hostile(GameTestHelper helper) {
        Bench b = bench(helper, true);
        b.menu().wandSlot().setItem(0, wand(20));
        simple(b.menu(), 1);
        List<ItemStack> before = state(b.menu());
        WorktableCraft good = payload(b.menu(), false);
        Consumer<WorktableCraft> refusedSilently =
                p -> {
                    List<Object> seen = events(() -> Worktables.onCraft(p, b.player()));
                    helper.assertTrue(same(before, state(b.menu())), "nothing changed");
                    helper.assertTrue(seen.isEmpty(), "no event");
                };
        WorktableCraft.Echo echo = good.echo();
        helper.startSequence()
                .thenExecute(
                        () -> {
                            b.player().containerMenu = b.player().inventoryMenu;
                            refusedSilently.accept(good);
                            b.player().containerMenu = b.menu();
                        })
                .thenExecuteAfter(
                        1,
                        () ->
                                refusedSilently.accept(
                                        new WorktableCraft(
                                                good.containerId() + 1,
                                                good.revision(),
                                                good.recipeId(),
                                                echo,
                                                false)))
                .thenExecuteAfter(
                        1,
                        () -> {
                            refusedSilently.accept(
                                    new WorktableCraft(
                                            good.containerId(),
                                            good.revision() + 1,
                                            good.recipeId(),
                                            echo,
                                            false));
                            helper.assertValueEqual(
                                    b.menu().refusal(), WorktableStatus.STALE.code(), "revision");
                        })
                .thenExecuteAfter(
                        1,
                        () -> {
                            WorktableCraft.Echo changed =
                                    new WorktableCraft.Echo(
                                            echo.base(),
                                            echo.effective(),
                                            echo.m() + 1,
                                            echo.spill(),
                                            echo.discountCapped(),
                                            echo.clamped(),
                                            echo.exceedsCapacity());
                            refusedSilently.accept(
                                    new WorktableCraft(
                                            good.containerId(),
                                            b.menu().revision(),
                                            good.recipeId(),
                                            changed,
                                            false));
                            helper.assertValueEqual(
                                    b.menu().refusal(), WorktableStatus.STALE.code(), "echo");
                        })
                .thenExecuteAfter(
                        1,
                        () -> {
                            refusedSilently.accept(
                                    new WorktableCraft(
                                            good.containerId(),
                                            b.menu().revision(),
                                            Identifier.fromNamespaceAndPath("vftv", "bell"),
                                            echo,
                                            false));
                            helper.assertValueEqual(
                                    b.menu().refusal(), WorktableStatus.STALE.code(), "id");
                        })
                .thenExecuteAfter(
                        1,
                        () -> {
                            WorktableCraft fresh = payload(b.menu(), false);
                            revoke(b.player());
                            b.menu().broadcastChanges();
                            refusedSilently.accept(
                                    new WorktableCraft(
                                            fresh.containerId(),
                                            b.menu().revision(),
                                            fresh.recipeId(),
                                            fresh.echo(),
                                            false));
                            helper.assertValueEqual(
                                    b.menu().refusal(),
                                    WorktableStatus.NO_RECIPE.code(),
                                    "unlearned: selection is the research gate");
                            ResearchTestSupport.learn(b.player(), ArcaneContent.ARCANE_CRAFTING);
                        })
                .thenExecuteAfter(
                        1,
                        () -> {
                            WorktableCraft fresh = payload(b.menu(), false);
                            helper.setBlock(AT, Blocks.AIR);
                            refusedSilently.accept(fresh);
                            helper.setBlock(AT, ArcaneContent.worktable());
                        })
                .thenExecuteAfter(
                        1,
                        () -> {
                            // Last: becoming a spectator closes the menu, returning its items.
                            WorktableCraft fresh = payload(b.menu(), false);
                            b.player().setGameMode(GameType.SPECTATOR);
                            b.player().containerMenu = b.menu();
                            List<Object> seen = events(() -> Worktables.onCraft(fresh, b.player()));
                            helper.assertTrue(
                                    b.menu().output().getItem(0).isEmpty(), "spectator: no craft");
                            helper.assertTrue(seen.isEmpty(), "spectator: no event");
                        })
                .thenSucceed();
    }

    /** Two payloads in one tick: the second is dropped. */
    private static void flood(GameTestHelper helper) {
        Bench b = bench(helper, true);
        b.menu().wandSlot().setItem(0, wand(20));
        simple(b.menu(), 2);
        WorktableCraft p = payload(b.menu(), false);
        Worktables.onCraft(p, b.player());
        Worktables.onCraft(payload(b.menu(), false), b.player());
        helper.assertValueEqual(b.menu().output().getItem(0).getCount(), 1, "one craft");
        helper.succeed();
    }

    /** A batch repeats the same recipe until the grid runs out, each iteration its own spend. */
    private static void batch(GameTestHelper helper) {
        Bench b = bench(helper, true);
        b.menu().wandSlot().setItem(0, wand(40));
        simple(b.menu(), 5);
        Worktables.Evaluation e = Worktables.evaluate(b.menu());
        helper.assertValueEqual(e.batchSize(b.menu()), 5, "the batch total's n");
        WorktableCraft p = payload(b.menu(), true);
        List<Object> seen = events(() -> Worktables.onCraft(p, b.player()));
        helper.assertValueEqual(b.menu().output().getItem(0).getCount(), 5, "five bells");
        helper.assertValueEqual(count(seen, VisSpentEvent.class), 5L, "an event per iteration");
        helper.assertValueEqual(
                count(seen, PlayerEvent.ItemCraftedEvent.class), 5L, "crafted per iteration");
        helper.assertValueEqual(pool(b.menu().wandSlot().getItem(0), "vftv:aer"), 30, "Aer 10");
        helper.succeed();
    }

    /**
     * A {@code VisSpentEvent} listener changing a grid slot (to an identical stack, so the grid
     * still matches) or closing the menu stops the next iteration: an external bump.
     */
    private static void batchStops(GameTestHelper helper) {
        Bench b = bench(helper, true);
        b.menu().wandSlot().setItem(0, wand(40));
        simple(b.menu(), 5);
        WorktableCraft p = payload(b.menu(), true);
        spy =
                e -> {
                    if (e instanceof VisSpentEvent) {
                        b.menu().grid().setItem(0, b.menu().grid().getItem(0).copy());
                    }
                };
        try {
            Worktables.onCraft(p, b.player());
        } finally {
            spy = e -> {};
        }
        helper.assertValueEqual(b.menu().output().getItem(0).getCount(), 1, "stopped after one");
        helper.assertValueEqual(b.menu().refusal(), 0, "a batch stop never refuses");

        Bench c = bench(helper, true);
        c.menu().wandSlot().setItem(0, wand(40));
        simple(c.menu(), 5);
        WorktableCraft q = payload(c.menu(), true);
        spy =
                e -> {
                    if (e instanceof PlayerEvent.ItemCraftedEvent) {
                        c.player().containerMenu = c.player().inventoryMenu;
                    }
                };
        try {
            Worktables.onCraft(q, c.player());
        } finally {
            spy = e -> {};
        }
        helper.assertValueEqual(c.menu().output().getItem(0).getCount(), 1, "closed: stopped");
        helper.succeed();
    }

    /**
     * A throwing {@code ItemCraftedEvent} listener and a throwing {@code VisSpentEvent} listener:
     * the craft stands, the remaining notifications run, the batch stops, no refusal.
     */
    private static void notifyFailures(GameTestHelper helper) {
        Bench b = bench(helper, true);
        b.menu().wandSlot().setItem(0, wand(40));
        simple(b.menu(), 5);
        WorktableCraft p = payload(b.menu(), true);
        spy =
                e -> {
                    if (e instanceof PlayerEvent.ItemCraftedEvent) {
                        throw new IllegalStateException("deliberate crafted-listener failure");
                    }
                };
        try {
            Worktables.onCraft(p, b.player());
        } finally {
            spy = e -> {};
        }
        helper.assertValueEqual(b.menu().output().getItem(0).getCount(), 1, "the craft stands");
        helper.assertValueEqual(b.menu().refusal(), 0, "no refusal");

        Bench c = bench(helper, true);
        c.menu().wandSlot().setItem(0, wand(40));
        simple(c.menu(), 5);
        WorktableCraft q = payload(c.menu(), true);
        List<Object> crafted = new ArrayList<>();
        spy =
                e -> {
                    if (e instanceof VisSpentEvent) {
                        throw new IllegalStateException("deliberate spent-listener failure");
                    }
                    crafted.add(e);
                };
        try {
            Worktables.onCraft(q, c.player());
        } finally {
            spy = e -> {};
        }
        helper.assertValueEqual(c.menu().output().getItem(0).getCount(), 1, "committed once");
        helper.assertValueEqual(crafted.size(), 1, "ItemCraftedEvent still ran once");
        helper.assertValueEqual(pool(c.menu().wandSlot().getItem(0), "vftv:aer"), 38, "spent");
        helper.assertValueEqual(c.menu().refusal(), 0, "no ERROR, no refusal");
        helper.succeed();
    }

    /**
     * A reload that changes a recipe's result at the same id and price bumps open menus, so the old
     * echo is refused as stale; a reload that removes it leaves the items and shows no recipe.
     */
    private static void reloadSameId(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Bench b = bench(helper, true);
        b.menu().wandSlot().setItem(0, wand(20));
        simple(b.menu(), 1);
        WorktableCraft old = payload(b.menu(), false);
        List<ItemStack> before = state(b.menu());
        try {
            ArcaneRecipe gong =
                    new ArcaneRecipe(
                            ArcaneContent.ARCANE_CRAFTING,
                            VisKind.ORDINARY,
                            Either.right(
                                    List.of(
                                            Ingredient.of(Items.STICK),
                                            Ingredient.of(Items.COPPER_INGOT))),
                            new ItemStackTemplate(Items.NOTE_BLOCK),
                            Map.of(AspectKeys.AER, 2, AspectKeys.ORDO, 1));
            ArcaneRecipes.beforeSync(manager(server, gong));
            Worktables.onCraft(old, b.player());
            helper.assertValueEqual(
                    b.menu().refusal(), WorktableStatus.STALE.code(), "the old echo is stale");
            helper.assertTrue(same(before, state(b.menu())), "nothing changed");
            ArcaneRecipes.beforeSync(manager(server, null));
            b.menu().broadcastChanges();
            helper.assertValueEqual(
                    Worktables.evaluate(b.menu()).status(),
                    WorktableStatus.NO_RECIPE,
                    "removed: no recipe");
            helper.assertTrue(same(before, state(b.menu())), "the menu keeps its items");
        } finally {
            ArcaneRecipes.beforeSync(server.getRecipeManager());
        }
        helper.succeed();
    }

    private static RecipeManager manager(MinecraftServer server, @Nullable ArcaneRecipe recipe) {
        List<RecipeHolder<?>> holders = new ArrayList<>();
        if (recipe != null) {
            holders.add(
                    new RecipeHolder<>(ResourceKey.create(Registries.RECIPE, TEST_RECIPE), recipe));
        }
        RecipeMap map = RecipeMap.create(holders);
        return new RecipeManager(server.registryAccess()) {
            @Override
            public RecipeMap recipeMap() {
                return map;
            }
        };
    }

    /** The status precedence over the states a menu can reach. */
    private static void statusPrecedence(GameTestHelper helper) {
        Bench b = bench(helper, true);
        WorktableMenu m = b.menu();
        helper.assertValueEqual(
                Worktables.evaluate(m).status(), WorktableStatus.NO_WAND, "no wand");
        helper.assertTrue(Worktables.evaluate(m).empty(), "empty grid");
        m.wandSlot().setItem(0, new ItemStack(VisContent.wand()));
        helper.assertValueEqual(
                Worktables.evaluate(m).status(), WorktableStatus.INERT_WAND, "inert");
        m.wandSlot().setItem(0, wand(1));
        m.grid().setItem(4, new ItemStack(Items.DIRT));
        helper.assertValueEqual(Worktables.evaluate(m).status(), WorktableStatus.NO_RECIPE, "none");
        m.grid().setItem(4, ItemStack.EMPTY);
        simple(m, 1);
        helper.assertValueEqual(Worktables.evaluate(m).status(), WorktableStatus.SHORT, "short");
        m.wandSlot().setItem(0, wand(20));
        m.output().setItem(0, new ItemStack(Items.STICK));
        helper.assertValueEqual(
                Worktables.evaluate(m).status(), WorktableStatus.OUTPUT_FULL, "output full");
        m.output().setItem(0, new ItemStack(Items.BELL, 63));
        helper.assertValueEqual(Worktables.evaluate(m).status(), WorktableStatus.READY, "merges");
        Bench unlearned = bench(helper, false);
        unlearned.menu().wandSlot().setItem(0, wand(20));
        simple(unlearned.menu(), 1);
        helper.assertValueEqual(
                Worktables.evaluate(unlearned.menu()).status(),
                WorktableStatus.NO_RECIPE,
                "an unlearned recipe is invisible");
        helper.succeed();
    }

    /**
     * No vanilla path inserts into the output: a click with a carried stack, a shift-click from the
     * inventory; taking committed output spends nothing and posts nothing.
     */
    private static void vanillaPaths(GameTestHelper helper) {
        Bench b = bench(helper, true);
        WorktableMenu m = b.menu();
        m.setCarried(new ItemStack(Items.BELL));
        m.clicked(WorktableMenu.OUTPUT, 0, ContainerInput.PICKUP, b.player());
        helper.assertTrue(m.output().getItem(0).isEmpty(), "a click cannot insert");
        m.setCarried(ItemStack.EMPTY);
        m.getSlot(WorktableMenu.INVENTORY).set(new ItemStack(Items.BELL));
        m.clicked(WorktableMenu.INVENTORY, 0, ContainerInput.QUICK_MOVE, b.player());
        helper.assertTrue(m.output().getItem(0).isEmpty(), "a shift-click cannot insert");
        helper.assertTrue(m.grid().getItem(0).is(Items.BELL), "it goes to the grid");
        m.grid().setItem(0, ItemStack.EMPTY);
        m.getSlot(WorktableMenu.INVENTORY).set(wand(5));
        m.clicked(WorktableMenu.INVENTORY, 0, ContainerInput.QUICK_MOVE, b.player());
        helper.assertTrue(m.wandSlot().getItem(0).is(VisContent.wand()), "a wand to the wand slot");
        m.output().setItem(0, new ItemStack(Items.BELL, 3));
        List<Object> seen =
                events(
                        () ->
                                m.clicked(
                                        WorktableMenu.OUTPUT,
                                        0,
                                        ContainerInput.QUICK_MOVE,
                                        b.player()));
        helper.assertTrue(m.output().getItem(0).isEmpty(), "taken");
        helper.assertTrue(seen.isEmpty(), "taking output posts nothing");
        helper.assertValueEqual(pool(m.wandSlot().getItem(0), "vftv:aer"), 5, "and spends nothing");
        helper.succeed();
    }

    /** Closing returns grid, wand and output to the player exactly once. */
    private static void closeReturnsItems(GameTestHelper helper) {
        Bench b = bench(helper, true);
        b.menu().wandSlot().setItem(0, wand(5));
        simple(b.menu(), 2);
        b.menu().output().setItem(0, new ItemStack(Items.BELL));
        b.menu().removed(b.player());
        b.menu().removed(b.player());
        helper.assertValueEqual(b.player().getInventory().countItem(Items.STICK), 2, "sticks");
        helper.assertValueEqual(b.player().getInventory().countItem(Items.BELL), 1, "output");
        helper.assertValueEqual(
                b.player().getInventory().countItem(VisContent.wand()), 1, "the wand once");
        helper.succeed();
    }

    /** Two players at one table each have their own grid, wand and research. */
    private static void twoPlayers(GameTestHelper helper) {
        Bench a = bench(helper, true);
        Bench b = bench(helper, false);
        a.menu().wandSlot().setItem(0, wand(20));
        b.menu().wandSlot().setItem(0, wand(20));
        simple(a.menu(), 1);
        simple(b.menu(), 1);
        helper.assertValueEqual(Worktables.evaluate(a.menu()).status(), WorktableStatus.READY, "a");
        helper.assertValueEqual(
                Worktables.evaluate(b.menu()).status(), WorktableStatus.NO_RECIPE, "b unlearned");
        Worktables.onCraft(payload(a.menu(), false), a.player());
        helper.assertTrue(a.menu().output().getItem(0).is(Items.BELL), "a crafted");
        helper.assertTrue(!b.menu().grid().isEmpty(), "b's grid untouched");
        helper.succeed();
    }

    /**
     * The reference wand: hand, offhand, then inventory; an inert wand is skipped; with only inert
     * wands the first is named; with none, no wand. The facts read the absent text unpriced.
     */
    private static void referenceWand(GameTestHelper helper) {
        ServerPlayer player = ResearchTestSupport.player(helper);
        RecipeHolder<ArcaneRecipe> bell =
                Objects.requireNonNull(
                        ArcaneRecipes.server()
                                .get(Identifier.fromNamespaceAndPath("vftv", "bell")));
        helper.assertTrue(ArcanePreview.reference(player) == null, "no wand");
        ArcanePreview none = ArcanePreview.of(player, bell, null);
        helper.assertValueEqual(none.availability(), ArcanePreview.Availability.NO_WAND, "none");
        helper.assertValueEqual(
                key(none.fact(bell.value(), "vis_effective")), "arcane.vftv.no_wand", "absent");
        player.getInventory().setItem(20, new ItemStack(VisContent.wand()));
        ArcanePreview.Wand inert = Objects.requireNonNull(ArcanePreview.reference(player));
        helper.assertValueEqual(inert.where(), ArcanePreview.Where.INVENTORY, "only inert");
        helper.assertValueEqual(
                ArcanePreview.of(player, bell, inert).availability(),
                ArcanePreview.Availability.INERT,
                "inert");
        player.getInventory().setItem(30, wand(10));
        helper.assertValueEqual(
                Objects.requireNonNull(ArcanePreview.reference(player)).slot(), 30, "skips inert");
        player.setItemInHand(InteractionHand.OFF_HAND, wand(10));
        helper.assertValueEqual(
                Objects.requireNonNull(ArcanePreview.reference(player)).where(),
                ArcanePreview.Where.OFFHAND,
                "offhand before inventory");
        player.setItemInHand(InteractionHand.MAIN_HAND, wand(10));
        ArcanePreview.Wand hand = Objects.requireNonNull(ArcanePreview.reference(player));
        helper.assertValueEqual(hand.where(), ArcanePreview.Where.MAIN_HAND, "hand first");
        ArcanePreview priced = ArcanePreview.of(player, bell, hand);
        VisQuote quote = Objects.requireNonNull(priced.quote());
        helper.assertValueEqual(quote.effective().getOrDefault(AspectKeys.AER, 0), 8, "priced");
        helper.assertValueEqual(priced.inputs().size(), 9, "the 3x3 slot displays");
        helper.assertValueEqual(
                Objects.requireNonNull(priced.fact(bell.value(), "flux")).getString(), "0", "flux");
        helper.succeed();
    }

    private static String key(@Nullable Component c) {
        return c != null && c.getContents() instanceof TranslatableContents t ? t.getKey() : "";
    }

    /** Revokes every learned entry, as an admin's revoke does, bumping the research revision. */
    private static void revoke(ServerPlayer player) {
        ResearchService.update(
                player,
                r ->
                        new ResearchRecord(
                                r.format(),
                                r.stage(),
                                new TreeSet<>(),
                                r.revealed(),
                                r.latched(),
                                r.revealLatched(),
                                r.notes(),
                                r.bookmarks(),
                                r.revision() + 1,
                                r.rewarded(),
                                r.attempts()));
    }
}
