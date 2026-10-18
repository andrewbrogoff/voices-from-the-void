package com.andrewbrogoff.vftv.gametest;

import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.api.neoforge.vis.NodeEvent;
import com.andrewbrogoff.vftv.api.neoforge.vis.VisSpentEvent;
import com.andrewbrogoff.vftv.api.vis.NodeCause;
import com.andrewbrogoff.vftv.api.vis.NodeEventKind;
import com.andrewbrogoff.vftv.api.vis.NodePayload;
import com.andrewbrogoff.vftv.api.vis.NodeTypeKeys;
import com.andrewbrogoff.vftv.api.vis.VisContext;
import com.andrewbrogoff.vftv.api.vis.VisKind;
import com.andrewbrogoff.vftv.api.vis.VisQuote;
import com.andrewbrogoff.vftv.api.vis.WandPartKeys;
import com.andrewbrogoff.vftv.core.aspect.AspectContent;
import com.andrewbrogoff.vftv.core.aspect.ScanResult;
import com.andrewbrogoff.vftv.core.aspect.Strikes;
import com.andrewbrogoff.vftv.core.common.RawRecordCodec.Retained;
import com.andrewbrogoff.vftv.core.research.ResearchService;
import com.andrewbrogoff.vftv.core.research.model.ResearchRecord;
import com.andrewbrogoff.vftv.core.text.StatusLines;
import com.andrewbrogoff.vftv.core.text.model.SharedMessages;
import com.andrewbrogoff.vftv.core.vis.NodeBlockEntity;
import com.andrewbrogoff.vftv.core.vis.NodeLedger;
import com.andrewbrogoff.vftv.core.vis.NodeService;
import com.andrewbrogoff.vftv.core.vis.NodeState;
import com.andrewbrogoff.vftv.core.vis.Tapping;
import com.andrewbrogoff.vftv.core.vis.VisConfig;
import com.andrewbrogoff.vftv.core.vis.VisContent;
import com.andrewbrogoff.vftv.core.vis.VisFeature;
import com.andrewbrogoff.vftv.core.vis.VisLang;
import com.andrewbrogoff.vftv.core.vis.VisPools;
import com.andrewbrogoff.vftv.core.vis.VisRulesService;
import com.andrewbrogoff.vftv.core.vis.VisService;
import com.andrewbrogoff.vftv.core.vis.WandAssemblyRecipe;
import com.andrewbrogoff.vftv.core.vis.WandParts;
import com.andrewbrogoff.vftv.core.vis.WandState;
import com.andrewbrogoff.vftv.core.vis.model.VisTuning;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.Nullable;

/**
 * The vis feature's GameTests. Name each test {@code vis_<what>}, and list its id {@code
 * vftv:vis_<what>} in {@code src/gameTest/expected/vis.txt}.
 *
 * <p>Tests that place nodes do so in a cell of their own, far from the test grid, so the 8-per-cell
 * cap never couples two tests; each removes its nodes before it returns. Global state a test swaps
 * (the tuning, the config, the senders) is restored in {@code finally}.
 */
final class VisGameTests {
    private static final AtomicInteger CELLS = new AtomicInteger();
    private static volatile Consumer<Object> spy = e -> {};

    private VisGameTests() {}

    /**
     * Adds the feature's tests by name; {@code modBus} is for any test-only registration they need.
     */
    static void register(Map<String, Consumer<GameTestHelper>> tests, IEventBus modBus) {
        tests.put("vis_registries_and_tuning_load", VisGameTests::registriesAndTuningLoad);
        tests.put("vis_wand_assembly", VisGameTests::wandAssembly);
        tests.put("vis_wand_assembly_crafter", VisGameTests::wandAssemblyCrafter);
        tests.put("vis_tap_refusals", VisGameTests::tapRefusals);
        tests.put("vis_tap_pulses", VisGameTests::tapPulses);
        tests.put("vis_tap_stops", VisGameTests::tapStops);
        tests.put("vis_tap_two_players", VisGameTests::tapTwoPlayers);
        tests.put("vis_spend_refusals", VisGameTests::spendRefusals);
        tests.put("vis_spend_commit_and_rollback", VisGameTests::spendCommitAndRollback);
        tests.put("vis_starter_modes", VisGameTests::starterModes);
        tests.put("vis_starter_command", VisGameTests::starterCommand);
        tests.put("vis_node_remove", VisGameTests::nodeRemove);
        tests.put("vis_reconciliation", VisGameTests::reconciliation);
        tests.put("vis_update_boundary", VisGameTests::updateBoundary);
        tests.put("vis_node_strike_and_sounding", VisGameTests::nodeStrikeAndSounding);
        tests.put("vis_persistence", VisGameTests::persistence);
        tests.put("vis_natural_types_cover_biomes", VisGameTests::naturalTypesCoverBiomes);
        NeoForge.EVENT_BUS.addListener(VisSpentEvent.class, e -> spy.accept(e.payload()));
        NeoForge.EVENT_BUS.addListener(NodeEvent.class, e -> spy.accept(e.payload()));
    }

    // --- fixtures -----------------------------------------------------------------------------

    /** A loaded, forced chunk in a cell of its own, far from the test grid: its origin at y 100. */
    private static BlockPos ownCell(GameTestHelper helper) {
        int n = CELLS.incrementAndGet();
        BlockPos origin = new BlockPos(64 * (400 + n) + 8, 100, 64 * 400 + 8);
        ServerLevel level = helper.getLevel();
        ChunkPos chunk = ChunkPos.containing(origin);
        level.setChunkForced(chunk.x(), chunk.z(), true);
        level.getChunk(chunk.x(), chunk.z());
        return origin;
    }

    private static void release(GameTestHelper helper, BlockPos origin) {
        ChunkPos chunk = ChunkPos.containing(origin);
        helper.getLevel().setChunkForced(chunk.x(), chunk.z(), false);
    }

    private static SortedAspects six(int each) {
        SortedAspects m = new SortedAspects();
        for (String p :
                List.of(
                        "vftv:aer",
                        "vftv:aqua",
                        "vftv:ignis",
                        "vftv:ordo",
                        "vftv:perditio",
                        "vftv:terra")) {
            m.put(p, each);
        }
        return m;
    }

    /** A sorted aspect map, by id text. */
    private static final class SortedAspects extends TreeMap<String, Integer> {
        private static final long serialVersionUID = 1L;
    }

    /**
     * Places a node with id 0 at {@code pos} and reconciles its chunk, which mints it.
     *
     * @return the minted id
     */
    private static long node(
            ServerLevel level,
            BlockPos pos,
            Identifier type,
            java.util.SortedMap<String, Integer> maxes) {
        level.setBlock(pos, VisContent.node().defaultBlockState(), 3);
        NodeBlockEntity be = (NodeBlockEntity) Objects.requireNonNull(level.getBlockEntity(pos));
        be.setState(
                NodeState.born(
                        0,
                        NodeState.Origin.STARTER,
                        type,
                        NodeState.Clarity.CLEAR,
                        maxes,
                        level.getGameTime(),
                        VisRulesService.tuning()));
        NodeService.queue(level, ChunkPos.containing(pos));
        NodeService.tickStart(level.getServer());
        return Objects.requireNonNull(be.state()).id();
    }

    private static NodeBlockEntity be(ServerLevel level, BlockPos pos) {
        return (NodeBlockEntity) Objects.requireNonNull(level.getBlockEntity(pos));
    }

    private static ItemStack wand() {
        return WandAssemblyRecipe.wand(WandPartKeys.WOODEN_ROD, WandPartKeys.COPPER_CAP);
    }

    /** A player standing two blocks west of {@code node}, looking at it, holding a fresh wand. */
    private static ServerPlayer tapper(GameTestHelper helper, BlockPos node) {
        ServerPlayer player = ResearchTestSupport.player(helper);
        player.snapTo(node.getX() - 1.5, node.getY() - 1.0, node.getZ() + 0.5, 0, 0);
        player.lookAt(
                net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
                Vec3.atCenterOf(node));
        player.setItemInHand(InteractionHand.MAIN_HAND, wand());
        return player;
    }

    private static InteractionResult use(ServerPlayer player, BlockPos pos) {
        return player.gameMode.useItemOn(
                player,
                player.level(),
                player.getMainHandItem(),
                InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.WEST, pos, false));
    }

    private static int pool(ItemStack wand, String aspect) {
        return WandState.of(wand, WandParts.last()) instanceof WandState.Ready r
                ? r.pool(ResourceKey.create(AspectKeys.REGISTRY, Identifier.parse(aspect)))
                : -1;
    }

    private static int current(ServerLevel level, BlockPos pos, String aspect) {
        return Objects.requireNonNull(
                        Objects.requireNonNull(be(level, pos).state()).byAspect().get(aspect))
                .current();
    }

    /** One use tick with the tap interval at 1, so every tick pulses. */
    private static void tick(ServerPlayer player) {
        ItemStack held = player.getUseItem();
        if (player.isUsingItem()) {
            held.onUseTick(player.level(), player, player.getUseItemRemainingTicks());
        }
    }

    private static void withFastTaps(Runnable body) {
        VisTuning before = VisRulesService.tuning();
        VisRulesService.applyTuning(
                DataResult.success(VisTuning.parse("{\"tap.interval\": 1}")), true);
        try {
            body.run();
        } finally {
            VisRulesService.applyTuning(DataResult.success(before), true);
        }
    }

    private static List<String> keys(List<Component> lines) {
        List<String> out = new ArrayList<>();
        lines.forEach(
                l ->
                        out.add(
                                l.getContents() instanceof TranslatableContents t
                                        ? t.getKey()
                                        : l.getString()));
        return out;
    }

    private static List<Component> capture(Runnable body) {
        List<Component> lines = new ArrayList<>();
        var previous = StatusLines.swapSender((p, line) -> lines.add(line.text()));
        try {
            body.run();
        } finally {
            StatusLines.swapSender(previous);
        }
        return lines;
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

    private static final Identifier WELLSPRING = NodeTypeKeys.WELLSPRING.identifier();
    private static final VisContext ORDINARY =
            new VisContext(Identifier.fromNamespaceAndPath("vftv", "test"), VisKind.ORDINARY, null);

    // --- registries ---------------------------------------------------------------------------

    /** The parts, types and tuning load and pass the world-open rules; a bad reload is kept out. */
    private static void registriesAndTuningLoad(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        WandParts parts = WandParts.of(access);
        helper.assertValueEqual(parts.rods().size() >= 2, true, "the built-in rods");
        helper.assertValueEqual(
                Objects.requireNonNull(parts.caps().get(WandPartKeys.RESONANT_BRASS_CAP))
                        .costMultiplier(),
                0.85,
                "Resonant Brass");
        helper.assertValueEqual(
                Objects.requireNonNull(parts.rods().get(WandPartKeys.WOODEN_ROD)).capacity(),
                40,
                "wooden");
        helper.assertTrue(parts.types().containsKey(NodeTypeKeys.WELLSPRING), "the Wellspring");
        helper.assertValueEqual(parts.visAspects().size(), 6, "six vis aspects");
        helper.assertTrue(
                VisFeature.validate(access).isEmpty(),
                "the loaded registries pass: " + VisFeature.validate(access));
        helper.assertValueEqual(VisRulesService.tuning(), VisTuning.DEFAULTS, "the shipped tuning");
        helper.assertTrue(VisRulesService.parse("{\"tap.interval\": 41}").isError(), "a bad value");
        VisTuning before = VisRulesService.tuning();
        VisRulesService.applyTuning(VisRulesService.parse("{\"nope\": 1}"), true);
        helper.assertValueEqual(VisRulesService.tuning(), before, "a /reload keeps the previous");
        boolean refused = false;
        try {
            VisRulesService.applyTuning(VisRulesService.parse("{\"nope\": 1}"), false);
        } catch (IllegalStateException e) {
            refused = String.valueOf(e.getMessage()).contains("vis.json");
        }
        helper.assertTrue(refused, "an invalid file refuses the world at open");
        helper.succeed();
    }

    // --- wand assembly ------------------------------------------------------------------------

    private static @Nullable RecipeHolder<?> craft(ServerLevel level, CraftingInput input) {
        return level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, level).orElse(null);
    }

    private static CraftingInput grid(ItemStack... nine) {
        return CraftingInput.of(3, 3, List.of(nine));
    }

    /** Caps must match; any column works; the result is an empty wand of those parts. */
    private static void wandAssembly(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ItemStack e = ItemStack.EMPTY;
        ItemStack nugget = new ItemStack(Items.COPPER_NUGGET);
        ItemStack stick = new ItemStack(Items.STICK);
        for (int column = 0; column < 3; column++) {
            ItemStack[] cells = {e, e, e, e, e, e, e, e, e};
            cells[column] = nugget;
            cells[3 + column] = stick;
            cells[6 + column] = nugget;
            CraftingInput input = grid(cells);
            RecipeHolder<?> recipe = craft(level, input);
            helper.assertTrue(
                    recipe != null && recipe.value() instanceof WandAssemblyRecipe,
                    "column " + column + " matches ours");
            ItemStack result =
                    ((WandAssemblyRecipe) Objects.requireNonNull(recipe).value()).assemble(input);
            helper.assertValueEqual(
                    Objects.requireNonNull(result.get(VisContent.rod())),
                    WandPartKeys.WOODEN_ROD,
                    "the rod");
            helper.assertValueEqual(
                    Objects.requireNonNull(result.get(VisContent.cap())),
                    WandPartKeys.COPPER_CAP,
                    "the cap");
            helper.assertValueEqual(
                    Objects.requireNonNull(result.get(VisContent.vis())),
                    new Retained.Read<>(VisPools.EMPTY),
                    "empty");
        }
        ItemStack brass = new ItemStack(VisContent.resonantBrassCap());
        helper.assertTrue(
                craft(level, grid(nugget, e, e, stick, e, e, brass, e, e)) == null,
                "mismatched caps");
        helper.assertTrue(
                craft(level, grid(nugget, e, e, stick, e, e, nugget, stick, e)) == null,
                "a fourth item");
        helper.assertTrue(
                craft(level, grid(stick, e, e, nugget, e, e, stick, e, e)) == null,
                "rod and cap swapped");
        long matching =
                level.recipeAccess().getRecipes().stream()
                        .filter(
                                h ->
                                        h.value()
                                                        instanceof
                                                        net.minecraft.world.item.crafting
                                                                        .CraftingRecipe
                                                                c
                                                && c.matches(
                                                        grid(
                                                                nugget, e, e, stick, e, e, nugget,
                                                                e, e),
                                                        level))
                        .count();
        helper.assertValueEqual(matching, 1L, "no vanilla recipe matches the built-in pattern");
        helper.succeed();
    }

    /** The vanilla Crafter takes the same result: an empty wooden-copper wand. */
    private static void wandAssemblyCrafter(GameTestHelper helper) {
        BlockPos crafter = new BlockPos(1, 1, 1);
        helper.setBlock(crafter, Blocks.CRAFTER);
        CrafterBlockEntity be = helper.getBlockEntity(crafter, CrafterBlockEntity.class);
        be.setItem(1, new ItemStack(Items.COPPER_NUGGET));
        be.setItem(4, new ItemStack(Items.STICK));
        be.setItem(7, new ItemStack(Items.COPPER_NUGGET));
        helper.setBlock(new BlockPos(1, 1, 2), Blocks.REDSTONE_BLOCK);
        helper.succeedWhen(
                () -> {
                    List<ItemEntity> drops =
                            helper.getLevel()
                                    .getEntitiesOfClass(
                                            ItemEntity.class,
                                            new net.minecraft.world.phys.AABB(
                                                            helper.absolutePos(crafter))
                                                    .inflate(3),
                                            d -> d.getItem().is(VisContent.wand()));
                    helper.assertTrue(!drops.isEmpty(), "the crafter made a wand");
                    helper.assertValueEqual(
                            Objects.requireNonNull(
                                    drops.getFirst().getItem().get(VisContent.rod())),
                            WandPartKeys.WOODEN_ROD,
                            "wooden");
                });
    }

    // --- tapping ------------------------------------------------------------------------------

    /** Each refusal shows its line and consumes the click (no air use follows). */
    private static void tapRefusals(GameTestHelper helper) {
        BlockPos origin = ownCell(helper);
        ServerLevel level = helper.getLevel();
        BlockPos pos = origin.above();
        long id = node(level, pos, WELLSPRING, six(32));
        BlockPos stone = origin.above(3);
        long silent = node(level, stone, WELLSPRING, new TreeMap<>(Map.of("vftv:sonus", 4)));
        BlockPos lost = origin.above(5);
        long unknown = node(level, lost, Identifier.parse("mod:gone"), six(8));
        try {
            ServerPlayer player = tapper(helper, pos);
            List<String> lines =
                    keys(
                            capture(
                                    () ->
                                            helper.assertValueEqual(
                                                    use(player, pos),
                                                    InteractionResult.CONSUME,
                                                    "consumed")));
            helper.assertValueEqual(lines, List.of(VisLang.TAP_UNLEARNED), "unlearned");
            ResearchTestSupport.learn(player, Tapping.TAPPING);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VisContent.wand()));
            helper.assertValueEqual(
                    keys(capture(() -> use(player, pos))), List.of(VisLang.TAP_INERT), "inert");
            player.setItemInHand(InteractionHand.MAIN_HAND, wand());
            ServerPlayer atLost = tapper(helper, lost);
            ResearchTestSupport.learn(atLost, Tapping.TAPPING);
            helper.assertValueEqual(
                    keys(capture(() -> use(atLost, lost))),
                    List.of(VisLang.TAP_UNKNOWN),
                    "unknown type");
            ServerPlayer atSilent = tapper(helper, stone);
            ResearchTestSupport.learn(atSilent, Tapping.TAPPING);
            helper.assertValueEqual(
                    keys(capture(() -> use(atSilent, stone))),
                    List.of(VisLang.TAP_NOTHING),
                    "nothing to draw");
            ItemStack full = wand();
            full.set(VisContent.vis(), new Retained.Read<>(new VisPools(six(40))));
            player.setItemInHand(InteractionHand.MAIN_HAND, full);
            helper.assertValueEqual(
                    keys(capture(() -> use(player, pos))), List.of(VisLang.TAP_FULL), "full");
            helper.assertTrue(!player.isUsingItem(), "no refusal starts a use");
        } finally {
            for (long n : new long[] {id, silent, unknown}) {
                NodeService.remove(level.getServer(), n, NodeCause.API);
            }
            release(helper, origin);
        }
        helper.succeed();
    }

    /** Pulses move 2 per aspect; the tap's own writes never stop it; an emptied node stops it. */
    private static void tapPulses(GameTestHelper helper) {
        BlockPos origin = ownCell(helper);
        ServerLevel level = helper.getLevel();
        BlockPos pos = origin.above();
        long id = node(level, pos, WELLSPRING, six(32));
        try {
            withFastTaps(
                    () -> {
                        ServerPlayer player = tapper(helper, pos);
                        ResearchTestSupport.learn(player, Tapping.TAPPING);
                        List<Object> seen = events(() -> use(player, pos));
                        helper.assertTrue(player.isUsingItem(), "the tap starts");
                        helper.assertTrue(seen.isEmpty(), "no event for a tap");
                        ItemStack held = player.getMainHandItem();
                        tick(player);
                        helper.assertValueEqual(pool(held, "vftv:aer"), 2, "two per pulse");
                        helper.assertValueEqual(
                                current(level, pos, "vftv:aer"), 30, "taken from the node");
                        for (int i = 0; i < 15; i++) {
                            tick(player);
                        }
                        helper.assertTrue(player.isUsingItem(), "in-place writes keep the tap");
                        helper.assertValueEqual(
                                pool(player.getMainHandItem(), "vftv:terra"), 32, "32 of each");
                        List<String> lines = keys(capture(() -> tick(player)));
                        helper.assertTrue(!player.isUsingItem(), "an empty node stops the tap");
                        helper.assertValueEqual(
                                lines, List.of(VisLang.TAP_EMPTY), "It has nothing left to give.");
                        helper.assertTrue(
                                Tapping.session(player) == null, "the session is cleared");
                    });
        } finally {
            NodeService.remove(level.getServer(), id, NodeCause.API);
            release(helper, origin);
        }
        helper.succeed();
    }

    /**
     * Every stop: another identical wand, another slot, a revoked entry, a lost raycast, a swap.
     */
    private static void tapStops(GameTestHelper helper) {
        BlockPos origin = ownCell(helper);
        ServerLevel level = helper.getLevel();
        BlockPos pos = origin.above();
        long id = node(level, pos, WELLSPRING, six(32));
        BlockPos other = origin.above(4);
        long second = node(level, other, WELLSPRING, six(32));
        try {
            withFastTaps(
                    () -> {
                        ServerPlayer player = tapper(helper, pos);
                        ResearchTestSupport.learn(player, Tapping.TAPPING);
                        use(player, pos);
                        player.setItemInHand(
                                InteractionHand.MAIN_HAND, player.getMainHandItem().copy());
                        tick(player);
                        helper.assertTrue(!player.isUsingItem(), "an identical copy stops it");

                        player.setItemInHand(InteractionHand.MAIN_HAND, wand());
                        use(player, pos);
                        player.getInventory().setSelectedSlot(3);
                        player.getInventory().setItem(3, player.getInventory().getItem(0));
                        tick(player);
                        helper.assertTrue(!player.isUsingItem(), "another hotbar slot stops it");

                        player.getInventory().setSelectedSlot(0);
                        player.setItemInHand(InteractionHand.MAIN_HAND, wand());
                        use(player, pos);
                        revoke(player);
                        tick(player);
                        helper.assertTrue(!player.isUsingItem(), "a revoked entry stops it");

                        ResearchTestSupport.learn(player, Tapping.TAPPING);
                        use(player, pos);
                        player.lookAt(
                                net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
                                player.getEyePosition().add(-10, 0, 0));
                        tick(player);
                        helper.assertTrue(!player.isUsingItem(), "a lost raycast stops it");

                        player.lookAt(
                                net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
                                Vec3.atCenterOf(pos));
                        use(player, pos);
                        // Swap the node for another id: the session's id no longer matches.
                        NodeBlockEntity node = be(level, pos);
                        node.setState(Objects.requireNonNull(node.state()).withId(second));
                        tick(player);
                        helper.assertTrue(
                                !player.isUsingItem(), "a node swapped for another id stops it");
                        node.setState(Objects.requireNonNull(node.state()).withId(id));
                    });
        } finally {
            NodeService.remove(level.getServer(), id, NodeCause.API);
            NodeService.remove(level.getServer(), second, NodeCause.API);
            release(helper, origin);
        }
        helper.succeed();
    }

    /** Two players on one node pulse on the same tick, each taking their share. */
    private static void tapTwoPlayers(GameTestHelper helper) {
        BlockPos origin = ownCell(helper);
        ServerLevel level = helper.getLevel();
        BlockPos pos = origin.above();
        long id = node(level, pos, WELLSPRING, six(32));
        try {
            withFastTaps(
                    () -> {
                        ServerPlayer a = tapper(helper, pos);
                        ServerPlayer b = tapper(helper, pos);
                        ResearchTestSupport.learn(a, Tapping.TAPPING);
                        ResearchTestSupport.learn(b, Tapping.TAPPING);
                        use(a, pos);
                        use(b, pos);
                        tick(a);
                        tick(b);
                        helper.assertValueEqual(
                                current(level, pos, "vftv:ordo"), 28, "both took 2");
                        helper.assertValueEqual(pool(a.getMainHandItem(), "vftv:ordo"), 2, "a");
                        helper.assertValueEqual(pool(b.getMainHandItem(), "vftv:ordo"), 2, "b");
                    });
        } finally {
            NodeService.remove(level.getServer(), id, NodeCause.API);
            release(helper, origin);
        }
        helper.succeed();
    }

    // --- spending -----------------------------------------------------------------------------

    private static ItemStack charged(int each) {
        ItemStack w = wand();
        w.set(VisContent.vis(), new Retained.Read<>(new VisPools(six(each))));
        return w;
    }

    private static final Map<ResourceKey<com.andrewbrogoff.vftv.api.aspect.Aspect>, Integer> BASE =
            Map.of(AspectKeys.AER, 10, AspectKeys.ORDO, 4);

    /** Inert, unlearned, short and stale spends change nothing. */
    private static void spendRefusals(GameTestHelper helper) {
        ServerPlayer player = ResearchTestSupport.player(helper);
        SimpleContainer box = new SimpleContainer(2);
        box.setItem(0, new ItemStack(Items.STONE));
        List<VisService.SlotChange> effect =
                List.of(
                        new VisService.SlotChange(
                                box, 0, new ItemStack(Items.STONE), ItemStack.EMPTY));
        ItemStack inert = new ItemStack(VisContent.wand());
        helper.assertValueEqual(
                VisService.spend(player, inert, BASE, ORDINARY, null, effect, null).outcome(),
                VisService.Outcome.INERT,
                "inert");
        ItemStack w = charged(40);
        ItemStack snapshot = w.copy();
        VisContext gated = new VisContext(ORDINARY.source(), VisKind.ORDINARY, Tapping.TAPPING);
        helper.assertValueEqual(
                VisService.spend(player, w, BASE, gated, null, effect, null).outcome(),
                VisService.Outcome.UNLEARNED,
                "unlearned");
        ItemStack poor = charged(3);
        VisService.SpendResult shortResult =
                VisService.spend(player, poor, BASE, ORDINARY, null, effect, null);
        helper.assertValueEqual(shortResult.outcome(), VisService.Outcome.SHORT, "short");
        helper.assertTrue(
                shortResult.message() != null
                        && shortResult.message().getContents() instanceof TranslatableContents t
                        && t.getKey().equals(VisLang.SPEND_SHORT),
                "Not enough …");
        VisQuote quote = VisService.quote(player, w, BASE, ORDINARY);
        VisQuote stale =
                VisService.quote(
                        player, w, Map.of(AspectKeys.AER, 11, AspectKeys.ORDO, 4), ORDINARY);
        VisService.SpendResult staleResult =
                VisService.spend(player, w, BASE, ORDINARY, stale, effect, null);
        helper.assertValueEqual(
                staleResult.outcome(), VisService.Outcome.STALE, "a stale expected");
        helper.assertTrue(
                staleResult.message() != null
                        && staleResult.message().getContents() instanceof TranslatableContents t2
                        && t2.getKey().equals(SharedMessages.COST_CHANGED),
                "cost changed");
        box.setItem(0, new ItemStack(Items.DIRT));
        helper.assertValueEqual(
                VisService.spend(player, w, BASE, ORDINARY, quote, effect, null).outcome(),
                VisService.Outcome.STALE,
                "a changed slot");
        helper.assertTrue(ItemStack.matches(w, snapshot), "the wand is unchanged");
        helper.assertTrue(box.getItem(0).is(Items.DIRT), "the slot is unchanged");
        VisContext voidWork = new VisContext(ORDINARY.source(), VisKind.VOID, null);
        helper.assertValueEqual(
                VisService.quote(player, w, Map.of(AspectKeys.AER, 40), voidWork).spill(),
                10,
                "Void work spills a quarter");
        helper.assertValueEqual(
                VisService.quote(player, w, Map.of(AspectKeys.AER, 40), ORDINARY).spill(),
                0,
                "ordinary work at no warp spills nothing");
        helper.succeed();
    }

    /** A commit, a rollback mid-commit, the boundary's order and a throwing listener. */
    private static void spendCommitAndRollback(GameTestHelper helper) {
        ServerPlayer player = ResearchTestSupport.player(helper);
        SimpleContainer box = new SimpleContainer(3);
        box.setItem(0, new ItemStack(Items.STONE));
        ItemStack w = charged(40);
        VisQuote quote = VisService.quote(player, w, BASE, ORDINARY);
        List<String> order = new ArrayList<>();
        List<Object> seen = new ArrayList<>();
        spy =
                e -> {
                    seen.add(e);
                    order.add("event:" + box.getItem(1).getItem());
                };
        try {
            VisService.SpendResult ok =
                    VisService.spend(
                            player,
                            w,
                            BASE,
                            ORDINARY,
                            quote,
                            List.of(
                                    new VisService.SlotChange(
                                            box, 0, new ItemStack(Items.STONE), ItemStack.EMPTY),
                                    new VisService.SlotChange(
                                            box, 1, ItemStack.EMPTY, new ItemStack(Items.DIAMOND))),
                            committed -> order.add("boundary:" + committed));
            helper.assertValueEqual(ok.outcome(), VisService.Outcome.SPENT, "spent");
            helper.assertValueEqual(pool(w, "vftv:aer"), 30, "aer debited");
            helper.assertValueEqual(pool(w, "vftv:ordo"), 36, "ordo debited");
            helper.assertTrue(
                    box.getItem(0).isEmpty() && box.getItem(1).is(Items.DIAMOND),
                    "materials and output");
            helper.assertValueEqual(
                    order,
                    List.of("boundary:true", "event:" + Items.DIAMOND),
                    "boundary first, then the event, which sees the commit");
            helper.assertValueEqual(seen.size(), 1, "one event");

            // A throwing second of three slot changes: everything restored, boundary false, no
            // event.
            seen.clear();
            order.clear();
            SimpleContainer broken =
                    new SimpleContainer(3) {
                        @Override
                        public void setItem(int slot, ItemStack stack) {
                            if (slot == 1 && stack.is(Items.EMERALD)) {
                                throw new IllegalStateException("deliberate");
                            }
                            super.setItem(slot, stack);
                        }
                    };
            broken.setItem(0, new ItemStack(Items.COAL));
            ItemStack before = w.copy();
            boolean thrown = false;
            try {
                VisService.spend(
                        player,
                        w,
                        BASE,
                        ORDINARY,
                        null,
                        List.of(
                                new VisService.SlotChange(
                                        broken, 0, new ItemStack(Items.COAL), ItemStack.EMPTY),
                                new VisService.SlotChange(
                                        broken, 1, ItemStack.EMPTY, new ItemStack(Items.EMERALD)),
                                new VisService.SlotChange(
                                        broken,
                                        2,
                                        ItemStack.EMPTY,
                                        new ItemStack(Items.GOLD_INGOT))),
                        committed -> order.add("boundary:" + committed));
            } catch (IllegalStateException e) {
                thrown = true;
            }
            helper.assertTrue(thrown, "the failure is rethrown");
            helper.assertTrue(ItemStack.matches(w, before), "the vis is restored");
            helper.assertTrue(
                    broken.getItem(0).is(Items.COAL)
                            && broken.getItem(1).isEmpty()
                            && broken.getItem(2).isEmpty(),
                    "materials and output restored");
            helper.assertValueEqual(
                    order, List.of("boundary:false"), "the boundary hears the rollback once");
            helper.assertTrue(seen.isEmpty(), "no event after a rollback");

            boolean rejected = false;
            try {
                VisService.spend(
                        player,
                        w,
                        BASE,
                        ORDINARY,
                        null,
                        List.of(
                                new VisService.SlotChange(box, 2, ItemStack.EMPTY, ItemStack.EMPTY),
                                new VisService.SlotChange(
                                        box, 2, ItemStack.EMPTY, ItemStack.EMPTY)),
                        null);
            } catch (IllegalArgumentException e) {
                rejected = true;
            }
            helper.assertTrue(rejected, "a slot named twice is rejected");

            // A throwing listener: the commit stands.
            spy =
                    e -> {
                        throw new IllegalStateException("deliberate listener failure");
                    };
            VisService.SpendResult notified =
                    VisService.spend(player, w, BASE, ORDINARY, null, List.of(), null);
            helper.assertValueEqual(
                    notified.outcome(),
                    VisService.Outcome.COMMITTED_NOTIFY_FAILED,
                    "notify failed");
            helper.assertValueEqual(pool(w, "vftv:aer"), 20, "and the vis was taken");
        } finally {
            spy = e -> {};
        }
        helper.succeed();
    }

    // --- the starter --------------------------------------------------------------------------

    /**
     * Each mode with no valid candidate (protection 180): best_effort warns and places nothing,
     * required throws naming the remedies and the file, off is silent; with a candidate the search
     * places the Wellspring and flips the rules' flag; an existing starter is untouched by every
     * mode.
     */
    private static void starterModes(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        NodeLedger ledger = Objects.requireNonNull(NodeService.ledger(server));
        VisConfig before = VisRulesService.config();
        try {
            NodeLedger.Starter existing = ledger.starter();
            if (existing != null && ledger.starterPlaced()) {
                for (var mode : VisConfig.StarterMode.values()) {
                    VisRulesService.installConfig(new VisConfig(mode));
                    helper.assertTrue(
                            !NodeService.placeStarter(server, 0),
                            "an existing starter is untouched: " + mode);
                }
                NodeService.remove(server, existing.id(), NodeCause.COMMAND);
            }
            VisRulesService.installConfig(new VisConfig(VisConfig.StarterMode.BEST_EFFORT));
            helper.assertTrue(
                    NodeService.starterMissing(server),
                    "the starter is missing: "
                            + VisRulesService.config()
                            + " "
                            + ledger.starter()
                            + " "
                            + ledger.starterPlaced()
                            + " "
                            + (ledger.starter() == null
                                    ? null
                                    : NodeService.get(server, ledger.starter().id())));
            VisRulesService.installConfig(new VisConfig(VisConfig.StarterMode.BEST_EFFORT));
            helper.assertTrue(
                    !NodeService.placeStarter(server, 180), "best_effort: no candidate past 180");
            helper.assertTrue(NodeService.starterMissing(server), "still missing");
            VisRulesService.installConfig(new VisConfig(VisConfig.StarterMode.REQUIRED));
            @Nullable String message = "";
            try {
                NodeService.placeStarter(server, 180);
            } catch (IllegalStateException e) {
                message = e.getMessage();
            }
            for (String part :
                    List.of(
                            "spawn-protection",
                            "move the world spawn",
                            "best_effort or off",
                            "vftv-server.toml")) {
                helper.assertTrue(
                        String.valueOf(message).contains(part),
                        "required names: " + part + " in " + message);
            }
            VisRulesService.installConfig(new VisConfig(VisConfig.StarterMode.OFF));
            helper.assertTrue(!NodeService.placeStarter(server, 0), "off places nothing");
            helper.assertTrue(
                    !NodeService.starterMissing(server), "off reports no missing starter");
            VisRulesService.installConfig(new VisConfig(VisConfig.StarterMode.BEST_EFFORT));
            List<Object> seen = events(() -> NodeService.placeStarter(server, 0));
            if (NodeService.starterPlaced(server)) {
                helper.assertTrue(
                        VisRulesService.current(server).starter(), "the rules' starter flag");
                helper.assertTrue(
                        seen.stream()
                                .anyMatch(
                                        e ->
                                                e instanceof NodePayload p
                                                        && p.kind() == NodeEventKind.CREATED
                                                        && p.cause() == NodeCause.STARTER),
                        "CREATED, STARTER");
                NodeBlockEntity be =
                        be(
                                server.overworld(),
                                Objects.requireNonNull(
                                                Objects.requireNonNull(
                                                                NodeService.get(
                                                                        server,
                                                                        Objects.requireNonNull(
                                                                                        ledger
                                                                                                .starter())
                                                                                .id()))
                                                        .where())
                                        .pos());
                helper.assertValueEqual(
                        Objects.requireNonNull(be.state()).aspects().size(), 6, "six primals");
                helper.assertValueEqual(
                        Objects.requireNonNull(be.state()).aspects().getFirst().max(), 32, "at 32");
            }
        } finally {
            VisRulesService.installConfig(before);
        }
        helper.succeed();
    }

    /** {@code /vftv node starter}: each refusal reserves no id; the placement is one birth. */
    private static void starterCommand(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos origin = ownCell(helper);
        CommandSourceStack source =
                server.createCommandSourceStack()
                        .withLevel(server.overworld())
                        .withPermission(LevelBasedPermissionSet.GAMEMASTER)
                        .withSuppressedOutput();
        NodeLedger ledger = Objects.requireNonNull(NodeService.ledger(server));
        try {
            NodeLedger.Starter existing = ledger.starter();
            if (existing != null && ledger.starterPlaced()) {
                helper.assertValueEqual(
                        run(server, source, "vftv node starter " + coords(origin)),
                        0,
                        "a second is refused");
                NodeService.remove(server, existing.id(), NodeCause.COMMAND);
            }
            long next = ledger.nextId();
            helper.assertValueEqual(
                    run(
                            server,
                            source.withLevel(
                                    Objects.requireNonNull(
                                            server.getLevel(
                                                    net.minecraft.world.level.Level.NETHER))),
                            "vftv node starter 0 64 0"),
                    0,
                    "the nether");
            level.setBlock(origin, Blocks.STONE.defaultBlockState(), 3);
            helper.assertValueEqual(
                    run(server, source, "vftv node starter " + coords(origin)), 0, "not air");
            helper.assertValueEqual(ledger.nextId(), next, "refusals reserve no id");
            BlockPos at = origin.above(2);
            List<Object> seen =
                    events(
                            () ->
                                    helper.assertValueEqual(
                                            run(server, source, "vftv node starter " + coords(at)),
                                            1,
                                            "placed"));
            helper.assertTrue(level.getBlockState(at).is(VisContent.node()), "the node stands");
            helper.assertTrue(VisRulesService.current(server).starter(), "the flag flips");
            helper.assertValueEqual(seen.size(), 1, "one event");
            helper.assertTrue(
                    seen.getFirst() instanceof NodePayload p
                            && p.kind() == NodeEventKind.CREATED
                            && p.cause() == NodeCause.COMMAND,
                    "CREATED, COMMAND");
            helper.assertValueEqual(
                    run(server, source, "vftv node starter " + coords(at.above(5))),
                    0,
                    "now one exists");
            helper.assertValueEqual(
                    run(
                            server,
                            source,
                            "vftv node remove " + Objects.requireNonNull(ledger.starter()).id()),
                    1,
                    "removed");
            helper.assertTrue(!VisRulesService.current(server).starter(), "the flag flips back");
            helper.assertTrue(
                    level.getBlockState(at).isAir(), "the removed starter's block went at once");
        } finally {
            release(helper, origin);
            NodeService.placeStarter(server, 0);
        }
        helper.succeed();
    }

    private static int run(MinecraftServer server, CommandSourceStack source, String command) {
        try {
            return server.getCommands().getDispatcher().execute(command, source);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            return 0;
        }
    }

    private static String coords(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    /**
     * {@code /vftv node remove}: a loaded node goes at once, with one REMOVED; unknown ids refuse.
     */
    private static void nodeRemove(GameTestHelper helper) {
        BlockPos origin = ownCell(helper);
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos pos = origin.above();
        long id = node(level, pos, WELLSPRING, six(8));
        try {
            CommandSourceStack source =
                    server.createCommandSourceStack()
                            .withPermission(LevelBasedPermissionSet.GAMEMASTER)
                            .withSuppressedOutput();
            List<Object> seen =
                    events(
                            () ->
                                    helper.assertValueEqual(
                                            run(server, source, "vftv node remove " + id),
                                            1,
                                            "removed"));
            helper.assertTrue(level.getBlockState(pos).isAir(), "the block went at once");
            helper.assertValueEqual(
                    seen.size(), 1, "REMOVED once, not again from the removal hook");
            helper.assertValueEqual(
                    Objects.requireNonNull(NodeService.get(server, id)).state(),
                    NodeLedger.State.REMOVED,
                    "tombstoned");
            helper.assertValueEqual(
                    run(server, source, "vftv node remove " + id),
                    0,
                    "a second removal is unknown");
            helper.assertValueEqual(
                    run(server, source, "vftv node remove 99999999"), 0, "an unknown id");
            helper.assertTrue(
                    NodeService.nearest(level, Vec3.atCenterOf(pos), 4) == null,
                    "it left the index");
        } finally {
            release(helper, origin);
        }
        helper.succeed();
    }

    // --- reconciliation -----------------------------------------------------------------------

    /** Mint, duplicate, ghost, carried and tombstoned ids, recovery, and the structure quota. */
    private static void reconciliation(GameTestHelper helper) {
        BlockPos origin = ownCell(helper);
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        NodeLedger ledger = Objects.requireNonNull(NodeService.ledger(server));
        List<Long> made = new ArrayList<>();
        try {
            BlockPos a = origin.above();
            long id = node(level, a, WELLSPRING, six(8));
            made.add(id);
            helper.assertValueEqual(
                    Objects.requireNonNull(NodeService.get(server, id)).state(),
                    NodeLedger.State.PLACED,
                    "minted");
            // A duplicate (a clone of a placed node) is removed; the original keeps its entry.
            BlockPos copy = origin.above(3);
            CompoundTag data = be(level, a).saveCustomOnly(level.registryAccess());
            level.setBlock(copy, VisContent.node().defaultBlockState(), 3);
            be(level, copy)
                    .loadCustomOnly(
                            TagValueInput.create(
                                    ProblemReporter.DISCARDING, level.registryAccess(), data));
            List<Object> seen =
                    events(
                            () -> {
                                NodeService.queue(level, ChunkPos.containing(copy));
                                NodeService.tickStart(server);
                            });
            helper.assertTrue(level.getBlockState(copy).isAir(), "the duplicate is removed");
            helper.assertTrue(level.getBlockState(a).is(VisContent.node()), "the original stays");
            helper.assertTrue(
                    Objects.requireNonNull(NodeService.get(server, id)).where() != null,
                    "and keeps its entry");
            helper.assertTrue(seen.isEmpty(), "no event for a duplicate");
            // A ghost: the entry says PLACED, the block is gone without the hook.
            level.setBlock(a, Blocks.AIR.defaultBlockState(), 3 | 64);
            if (level.getBlockState(a).isAir()
                    && Objects.requireNonNull(NodeService.get(server, id)).state()
                            == NodeLedger.State.PLACED) {
                seen =
                        events(
                                () -> {
                                    NodeService.queue(level, ChunkPos.containing(a));
                                    NodeService.tickStart(server);
                                });
                helper.assertValueEqual(
                        Objects.requireNonNull(NodeService.get(server, id)).state(),
                        NodeLedger.State.REMOVED,
                        "a ghost is tombstoned");
                helper.assertTrue(
                        seen.stream()
                                .anyMatch(
                                        e ->
                                                e instanceof NodePayload p
                                                        && p.kind() == NodeEventKind.REMOVED
                                                        && p.cause() == NodeCause.RECONCILE),
                        "REMOVED, RECONCILE");
            }
            // A block whose id is REMOVED is removed at its chunk's next reconciliation.
            level.setBlock(copy, VisContent.node().defaultBlockState(), 3);
            be(level, copy)
                    .loadCustomOnly(
                            TagValueInput.create(
                                    ProblemReporter.DISCARDING, level.registryAccess(), data));
            NodeService.queue(level, ChunkPos.containing(copy));
            NodeService.tickStart(server);
            helper.assertTrue(level.getBlockState(copy).isAir(), "a tombstoned id's block goes");
            // Recovery: an id with no entry registers and advances next_id past it.
            long unknown = ledger.nextId() + 50;
            BlockPos r = origin.above(5);
            level.setBlock(r, VisContent.node().defaultBlockState(), 3);
            be(level, r)
                    .setState(
                            NodeState.born(
                                    unknown,
                                    NodeState.Origin.STARTER,
                                    WELLSPRING,
                                    NodeState.Clarity.CLEAR,
                                    six(8),
                                    level.getGameTime(),
                                    VisRulesService.tuning()));
            seen =
                    events(
                            () -> {
                                NodeService.queue(level, ChunkPos.containing(r));
                                NodeService.tickStart(server);
                            });
            made.add(unknown);
            helper.assertValueEqual(
                    Objects.requireNonNull(NodeService.get(server, unknown)).state(),
                    NodeLedger.State.PLACED,
                    "recovered");
            helper.assertTrue(ledger.nextId() > unknown, "next_id advanced past it");
            helper.assertTrue(
                    seen.stream()
                            .anyMatch(
                                    e ->
                                            e instanceof NodePayload p
                                                    && p.kind() == NodeEventKind.CREATED
                                                    && p.cause() == NodeCause.RECONCILE),
                    "CREATED, RECONCILE");
            // Structure quota: two structure nodes mint, a third is removed.
            for (int i = 0; i < 3; i++) {
                BlockPos s = origin.offset(10 + 2 * i, 2, 0);
                level.setBlock(s, VisContent.node().defaultBlockState(), 3);
                be(level, s)
                        .setState(
                                NodeState.born(
                                        0,
                                        NodeState.Origin.STRUCTURE,
                                        WELLSPRING,
                                        NodeState.Clarity.CLEAR,
                                        six(8),
                                        level.getGameTime(),
                                        VisRulesService.tuning()));
            }
            NodeService.queue(level, ChunkPos.containing(origin.offset(10, 0, 0)));
            NodeService.tickStart(server);
            int standing = 0;
            for (int i = 0; i < 3; i++) {
                BlockPos s = origin.offset(10 + 2 * i, 2, 0);
                if (level.getBlockState(s).is(VisContent.node())) {
                    standing++;
                    made.add(Objects.requireNonNull(be(level, s).state()).id());
                }
            }
            helper.assertValueEqual(standing, 2, "a third structure node in one cell is removed");
        } finally {
            made.forEach(n -> NodeService.remove(server, n, NodeCause.API));
            release(helper, origin);
        }
        helper.succeed();
    }

    // --- update ---------------------------------------------------------------------------------

    /** The boundary runs once after the write and before CHANGED; failures still commit. */
    private static void updateBoundary(GameTestHelper helper) {
        BlockPos origin = ownCell(helper);
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos pos = origin.above();
        long id = node(level, pos, WELLSPRING, six(32));
        try {
            List<String> order = new ArrayList<>();
            spy =
                    e ->
                            order.add(
                                    "changed:"
                                            + Objects.requireNonNull(be(level, pos).state())
                                                    .clarity());
            var faint =
                    new NodeService.NodeChange(
                            java.util.Optional.empty(),
                            java.util.Optional.empty(),
                            java.util.Optional.of(NodeState.Clarity.FAINT));
            var result =
                    NodeService.update(
                            server,
                            id,
                            faint,
                            NodeCause.API,
                            (b, a) -> order.add("boundary:" + a.clarity()));
            helper.assertValueEqual(result, NodeService.UpdateResult.UPDATED, "updated");
            helper.assertValueEqual(
                    order, List.of("boundary:FAINT", "changed:FAINT"), "write, boundary, event");
            helper.assertValueEqual(
                    Objects.requireNonNull(be(level, pos).state()).regen().interval(),
                    160,
                    "rebased to faint's interval");
            order.clear();
            helper.assertValueEqual(
                    NodeService.update(
                            server,
                            999_999_999L,
                            faint,
                            NodeCause.API,
                            (b, a) -> order.add("boundary")),
                    NodeService.UpdateResult.NOT_LOADED,
                    "unknown id");
            var badType =
                    new NodeService.NodeChange(
                            java.util.Optional.of(
                                    ResourceKey.create(
                                            com.andrewbrogoff.vftv.api.vis.VisKeys
                                                    .NODE_TYPE_REGISTRY,
                                            Identifier.parse("mod:gone"))),
                            java.util.Optional.empty(),
                            java.util.Optional.empty());
            helper.assertValueEqual(
                    NodeService.update(
                            server, id, badType, NodeCause.API, (b, a) -> order.add("boundary")),
                    NodeService.UpdateResult.INVALID,
                    "unregistered type");
            helper.assertTrue(order.isEmpty(), "a refusal never calls the boundary");
            spy =
                    e -> {
                        throw new IllegalStateException("deliberate");
                    };
            var steady =
                    new NodeService.NodeChange(
                            java.util.Optional.empty(),
                            java.util.Optional.empty(),
                            java.util.Optional.of(NodeState.Clarity.STEADY));
            helper.assertValueEqual(
                    NodeService.update(
                            server, id, steady, NodeCause.API, (b, a) -> order.add("bk")),
                    NodeService.UpdateResult.COMMITTED_NOTIFY_FAILED,
                    "a throwing listener");
            helper.assertValueEqual(
                    Objects.requireNonNull(be(level, pos).state()).clarity(),
                    NodeState.Clarity.STEADY,
                    "the write stands");
            helper.assertValueEqual(order, List.of("bk"), "the boundary's bookkeeping stands");
            List<Object> seen = new ArrayList<>();
            spy = seen::add;
            var clear =
                    new NodeService.NodeChange(
                            java.util.Optional.empty(),
                            java.util.Optional.of(
                                    new TreeMap<>(Map.of(AspectKeys.AER, 10, AspectKeys.SONUS, 4))),
                            java.util.Optional.of(NodeState.Clarity.CLEAR));
            helper.assertValueEqual(
                    NodeService.update(
                            server,
                            id,
                            clear,
                            NodeCause.API,
                            (b, a) -> {
                                throw new IllegalStateException("deliberate boundary");
                            }),
                    NodeService.UpdateResult.COMMITTED_NOTIFY_FAILED,
                    "a throwing boundary");
            helper.assertValueEqual(seen.size(), 1, "CHANGED is still posted");
            helper.assertValueEqual(
                    current(level, pos, "vftv:aer"), 10, "current clamps to the lowered max");
            helper.assertValueEqual(
                    current(level, pos, "vftv:sonus"), 0, "an added aspect starts at 0");
        } finally {
            spy = e -> {};
            NodeService.remove(server, id, NodeCause.API);
            release(helper, origin);
        }
        helper.succeed();
    }

    // --- the fork on real nodes (aspects' deferred tests) ---------------------------------------

    /**
     * A strike reads type, clarity and current / max; a sounding answers; none names no Wellspring.
     */
    private static void nodeStrikeAndSounding(GameTestHelper helper) {
        BlockPos origin = ownCell(helper);
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos pos = origin.above();
        long id = node(level, pos, WELLSPRING, six(32));
        List<ScanResult> results = new ArrayList<>();
        var previous = Strikes.swapSender((p, r) -> results.add(r));
        try {
            ServerPlayer player = tapper(helper, pos);
            player.setItemInHand(
                    InteractionHand.MAIN_HAND, new ItemStack(AspectContent.tuningFork()));
            use(player, pos);
            ScanResult read = results.getLast();
            helper.assertValueEqual(read.state(), ScanResult.State.SCANNED, "scanned");
            helper.assertValueEqual(read.entries().size(), 6, "six aspects");
            helper.assertValueEqual(read.entries().getFirst().max(), 32, "max");
            helper.assertValueEqual(read.entries().getFirst().amount(), 32, "current");
            helper.assertTrue(
                    read.node().getFirst().getContents() instanceof TranslatableContents t
                            && t.getKey().equals("node_type.vftv.wellspring"),
                    "the type");
            player.getCooldowns()
                    .removeCooldown(
                            player.getCooldowns().getCooldownGroup(player.getMainHandItem()));
            int loaded = level.getChunkSource().getLoadedChunksCount();
            player.gameMode.useItem(
                    player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND);
            helper.assertValueEqual(
                    results.getLast().state(), ScanResult.State.ANSWER, "a sounding answers");
            helper.assertValueEqual(
                    level.getChunkSource().getLoadedChunksCount(),
                    loaded,
                    "sounding loads no chunk");
            NodeService.remove(server, id, NodeCause.API);
            NodeLedger ledger = Objects.requireNonNull(NodeService.ledger(server));
            NodeLedger.Starter starter = ledger.starter();
            boolean placed = ledger.starterPlaced();
            player.snapTo(origin.getX() + 2000, 120, origin.getZ() + 2000, 0, 0);
            if (placed && starter != null) {
                NodeService.remove(server, starter.id(), NodeCause.API);
            }
            player.getCooldowns()
                    .removeCooldown(
                            player.getCooldowns().getCooldownGroup(player.getMainHandItem()));
            player.gameMode.useItem(
                    player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND);
            helper.assertValueEqual(
                    results.getLast().state(),
                    ScanResult.State.NO_WELLSPRING,
                    "no Wellspring stands");
        } finally {
            Strikes.swapSender(previous);
            NodeService.placeStarter(server, 0);
            release(helper, origin);
        }
        helper.succeed();
    }

    // --- persistence --------------------------------------------------------------------------

    /** A node block entity, the wand's components and the registry survive a save and load. */
    private static void persistence(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        level.setBlock(pos, VisContent.node().defaultBlockState(), 3);
        NodeState state =
                NodeState.born(
                        42,
                        NodeState.Origin.NATURAL,
                        WELLSPRING,
                        NodeState.Clarity.STEADY,
                        six(9),
                        5,
                        VisTuning.DEFAULTS);
        be(level, pos).setState(state);
        CompoundTag saved = be(level, pos).saveWithFullMetadata(level.registryAccess());
        NodeBlockEntity loaded = new NodeBlockEntity(pos, VisContent.node().defaultBlockState());
        loaded.loadWithComponents(
                TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved));
        helper.assertValueEqual(Objects.requireNonNull(loaded.state()), state, "the node state");
        CompoundTag future = saved.copy();
        future.getCompoundOrEmpty("node").putInt("format", 9);
        NodeBlockEntity unreadable =
                new NodeBlockEntity(pos, VisContent.node().defaultBlockState());
        unreadable.loadWithComponents(
                TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), future));
        helper.assertTrue(unreadable.unreadable(), "a future format is unreadable");
        helper.assertValueEqual(
                unreadable.saveWithFullMetadata(level.registryAccess()).getCompoundOrEmpty("node"),
                future.getCompoundOrEmpty("node"),
                "kept raw");
        helper.assertTrue(
                unreadable.getUpdateTag(level.registryAccess()).getBooleanOr("unreadable", false),
                "its sync carries only a flag");
        level.removeBlock(pos, false);

        ItemStack w = charged(17);
        var out =
                TagValueOutput.createWithContext(
                        ProblemReporter.DISCARDING, level.registryAccess());
        out.store("stack", ItemStack.CODEC, w);
        ItemStack back =
                TagValueInput.create(
                                ProblemReporter.DISCARDING,
                                level.registryAccess(),
                                out.buildResult())
                        .read("stack", ItemStack.CODEC)
                        .orElseThrow();
        helper.assertTrue(ItemStack.matches(w, back), "the wand's components");
        helper.assertTrue(
                NodeService.SLOT.get(level.getServer().overworld())
                        instanceof Retained.Read<NodeLedger>,
                "the registry reads");
        helper.succeed();
    }

    // --- coverage -------------------------------------------------------------------------------

    /**
     * Every built-in natural type's biome tags resolve, and every vanilla overworld biome in the
     * fixtures has its listed eligible types.
     */
    private static void naturalTypesCoverBiomes(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var biomes = access.lookupOrThrow(Registries.BIOME);
        WandParts parts = WandParts.of(access);
        Map<String, Set<String>> eligible = new TreeMap<>();
        parts.types()
                .forEach(
                        (key, type) -> {
                            if (!type.natural()) {
                                return;
                            }
                            for (var tag : type.biomes()) {
                                HolderSet.Named<Biome> set = biomes.get(tag).orElse(null);
                                helper.assertTrue(
                                        set != null && set.size() > 0,
                                        key.identifier()
                                                + ": "
                                                + tag.location()
                                                + " resolves to a biome");
                                if (set != null) {
                                    for (Holder<Biome> b : set) {
                                        eligible.computeIfAbsent(
                                                        b.unwrapKey()
                                                                .orElseThrow()
                                                                .identifier()
                                                                .toString(),
                                                        k -> new HashSet<>())
                                                .add(key.identifier().toString());
                                    }
                                }
                            }
                        });
        JsonObject fixtures = load();
        Set<String> listed = new HashSet<>();
        for (JsonElement row : fixtures.getAsJsonArray("coverage")) {
            String biome = row.getAsJsonObject().get("biome").getAsString();
            listed.add(biome);
            for (JsonElement t : row.getAsJsonObject().getAsJsonArray("must_include")) {
                helper.assertTrue(
                        eligible.getOrDefault(biome, Set.of()).contains(t.getAsString()),
                        biome + " makes " + t.getAsString() + " eligible");
            }
        }
        biomes.get(net.minecraft.tags.BiomeTags.IS_OVERWORLD)
                .orElseThrow()
                .forEach(
                        b -> {
                            String id = b.unwrapKey().orElseThrow().identifier().toString();
                            if (id.startsWith("minecraft:")) {
                                helper.assertTrue(
                                        listed.contains(id), id + " is in the coverage list");
                            }
                        });
        helper.succeed();
    }

    private static JsonObject load() {
        java.nio.file.Path root =
                com.andrewbrogoff.vftv.core.common.model.PendingAudio.findProject(
                        java.nio.file.Path.of("."));
        try {
            return com.andrewbrogoff.vftv.core.common.model.StrictJson.parse(
                            java.nio.file.Files.readString(
                                    Objects.requireNonNull(root, "the project")
                                            .resolve("content/nodes/fixtures.json")))
                    .getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
