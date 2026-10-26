package com.andrewbrogoff.vftv.gametest;

import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.api.crucible.CruciblePayload;
import com.andrewbrogoff.vftv.api.essentia.EssentiaPayload;
import com.andrewbrogoff.vftv.api.essentia.SpillCause;
import com.andrewbrogoff.vftv.api.flux.FluxRules;
import com.andrewbrogoff.vftv.api.flux.FluxSource;
import com.andrewbrogoff.vftv.api.golem.GolemPayload;
import com.andrewbrogoff.vftv.api.hush.FluxPayload;
import com.andrewbrogoff.vftv.api.hush.HushKeys;
import com.andrewbrogoff.vftv.api.hush.HushLimits;
import com.andrewbrogoff.vftv.api.hush.HushedForm;
import com.andrewbrogoff.vftv.api.infusion.InfusionPayload;
import com.andrewbrogoff.vftv.api.neoforge.crucible.CrucibleEvent;
import com.andrewbrogoff.vftv.api.neoforge.essentia.EssentiaCapabilities;
import com.andrewbrogoff.vftv.api.neoforge.essentia.EssentiaEvent;
import com.andrewbrogoff.vftv.api.neoforge.essentia.EssentiaResource;
import com.andrewbrogoff.vftv.api.neoforge.golem.GolemEvent;
import com.andrewbrogoff.vftv.api.neoforge.hush.FluxEvent;
import com.andrewbrogoff.vftv.api.neoforge.hush.HushSpreadEvent;
import com.andrewbrogoff.vftv.api.neoforge.infusion.InfusionEvent;
import com.andrewbrogoff.vftv.api.neoforge.vis.VisSpentEvent;
import com.andrewbrogoff.vftv.api.vis.VisContext;
import com.andrewbrogoff.vftv.api.vis.VisQuote;
import com.andrewbrogoff.vftv.api.vis.VisSpentPayload;
import com.andrewbrogoff.vftv.core.common.RawRecordCodec.Retained;
import com.andrewbrogoff.vftv.core.flux.FluxRulesService;
import com.andrewbrogoff.vftv.core.flux.model.FluxMath;
import com.andrewbrogoff.vftv.core.flux.model.FluxTuning;
import com.andrewbrogoff.vftv.core.hush.Aura;
import com.andrewbrogoff.vftv.core.hush.FluxService;
import com.andrewbrogoff.vftv.core.hush.Hollow;
import com.andrewbrogoff.vftv.core.hush.HushBells;
import com.andrewbrogoff.vftv.core.hush.HushContent;
import com.andrewbrogoff.vftv.core.hush.HushPlaceQuery;
import com.andrewbrogoff.vftv.core.hush.HushRecords;
import com.andrewbrogoff.vftv.core.hush.HushService;
import com.andrewbrogoff.vftv.core.hush.HushedForms;
import com.andrewbrogoff.vftv.core.hush.PealingBellBlockEntity;
import com.andrewbrogoff.vftv.core.text.StatusLines;
import com.andrewbrogoff.vftv.core.vis.NodeState;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LightEngine;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The hush feature's GameTests. Name each test {@code hush_<what>}, and list its id {@code
 * vftv:hush_<what>} in {@code src/gameTest/expected/hush.txt}.
 */
final class HushGameTests {
    /** Cells whose Hush mutations a test cancels. */
    private static final Set<BlockPos> CANCEL_AT = new HashSet<>();

    /** Flux events seen, for tests that listen. */
    private static final List<FluxPayload> FLUX = new ArrayList<>();

    /** Positions whose sibling events a later listener throws on (once-only delivery). */
    private static final Set<BlockPos> THROW_AT = new HashSet<>();

    private static final List<Map.Entry<ServerPlayer, Component>> LINES = new ArrayList<>();
    private static boolean capturing;

    private HushGameTests() {}

    /**
     * Adds the feature's tests by name; {@code modBus} is for any test-only registration they need.
     */
    static void register(Map<String, Consumer<GameTestHelper>> tests, IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(
                HushSpreadEvent.class,
                e -> {
                    if (CANCEL_AT.contains(e.payload().pos())) {
                        e.setCanceled(true);
                    }
                });
        NeoForge.EVENT_BUS.addListener(FluxEvent.class, e -> FLUX.add(e.payload()));
        NeoForge.EVENT_BUS.addListener(
                EventPriority.LOWEST,
                CrucibleEvent.class,
                e -> {
                    if (e.payload() instanceof CruciblePayload.Spilled s
                            && THROW_AT.contains(s.pos())) {
                        throw new IllegalStateException("a test's later throwing spill listener");
                    }
                });
        tests.put("hush_registered", HushGameTests::registered);
        tests.put("hush_forms_swap_exact", HushGameTests::formsSwap);
        tests.put("hush_support_parity", HushGameTests::supportParity);
        tests.put("hush_deposit_zero_noop", HushGameTests::depositZero);
        tests.put("hush_deposit_retains", HushGameTests::depositRetains);
        tests.put("hush_deposit_caps", HushGameTests::depositCaps);
        tests.put("hush_deposit_unloaded_pending", HushGameTests::depositUnloaded);
        tests.put("hush_producers_generate", HushGameTests::producers);
        tests.put("hush_once_with_throwing_listener", HushGameTests::onceWithThrower);
        tests.put("hush_attempt_converts", HushGameTests::attemptConverts);
        tests.put("hush_onset_and_queue", HushGameTests::onsetAndQueue);
        tests.put("hush_cancelled_spread_spends_nothing", HushGameTests::cancelledSpread);
        tests.put("hush_saturation_and_exits", HushGameTests::saturation);
        tests.put("hush_hollow_rises", HushGameTests::hollowRises);
        tests.put("hush_hollow_stats_and_targets", HushGameTests::hollowStats);
        tests.put("hush_node_hushed_and_restored", HushGameTests::nodeHushed);
        tests.put("hush_wellspring_never_hushed", HushGameTests::anchoredNode);
        tests.put("hush_clearing_bell_rings", HushGameTests::clearingBell);
        tests.put("hush_clearing_bell_nothing", HushGameTests::clearingBellNothing);
        tests.put("hush_pealing_bell_feeds_and_peals", HushGameTests::pealingBell);
        tests.put("hush_pealing_bell_removal_spills", HushGameTests::pealingRemoval);
        tests.put("hush_place_query", HushGameTests::placeQuery);
        tests.put("hush_aura_persists_and_retains", HushGameTests::auraPersistence);
        tests.put("hush_stores_retain_unreadable", HushGameTests::storesRetain);
        tests.put("hush_commands", HushGameTests::commands);
        tests.put("hush_recipes_loaded", HushGameTests::recipes);
        tests.put("hush_switches_and_silence", HushGameTests::switches);
    }

    // --- helpers ------------------------------------------------------------------------------

    private static ChunkPos chunk(GameTestHelper helper, BlockPos rel) {
        return ChunkPos.containing(helper.absolutePos(rel));
    }

    private static LevelChunk levelChunk(GameTestHelper helper, BlockPos rel) {
        return Objects.requireNonNull(
                FluxService.chunkNow(helper.getLevel(), chunk(helper, rel).pack()));
    }

    private static Aura aura(GameTestHelper helper, BlockPos rel) {
        return Objects.requireNonNull(FluxService.aura(levelChunk(helper, rel)), "an aura");
    }

    private static void clearAura(GameTestHelper helper, BlockPos rel) {
        FluxService.clear(
                helper.getLevel(),
                levelChunk(helper, rel),
                HushLimits.FLUX_FORMAT_MAX,
                com.andrewbrogoff.vftv.api.flux.FluxClearSource.COMMAND,
                null);
    }

    private static FluxService.DepositResult deposit(
            GameTestHelper helper, BlockPos rel, int flux) {
        clearAura(helper, rel);
        return FluxService.deposit(
                helper.getLevel(), helper.absolutePos(rel), flux, new FluxSource.Command(), null);
    }

    /** A 5 × 5 × 5 cube of dirt with an air cell at its centre (2, 3, 2): the seep. */
    private static BlockPos dirtCube(GameTestHelper helper) {
        for (int x = 0; x < 5; x++) {
            for (int y = 1; y < 6; y++) {
                for (int z = 0; z < 5; z++) {
                    helper.setBlock(x, y, z, Blocks.DIRT);
                }
            }
        }
        BlockPos seep = new BlockPos(2, 3, 2);
        helper.setBlock(seep, Blocks.AIR);
        return seep;
    }

    private static int hushedIn(
            GameTestHelper helper, int x0, int y0, int z0, int x1, int y1, int z1) {
        int n = 0;
        for (BlockPos p : BlockPos.betweenClosed(x0, y0, z0, x1, y1, z1)) {
            if (helper.getBlockState(p).is(HushKeys.HUSHED)) {
                n++;
            }
        }
        return n;
    }

    private static List<Component> lines(ServerPlayer player) {
        if (!capturing) {
            capturing = true;
            var real =
                    new java.util.concurrent.atomic.AtomicReference<
                            java.util.function.BiConsumer<
                                    ServerPlayer, com.andrewbrogoff.vftv.core.text.StatusLine>>();
            real.set(
                    StatusLines.swapSender(
                            (p, line) -> {
                                LINES.add(Map.entry(p, line.text()));
                                real.get().accept(p, line);
                            }));
        }
        return LINES.stream()
                .filter(e -> e.getKey().equals(player))
                .map(Map.Entry::getValue)
                .toList();
    }

    private static boolean says(List<Component> lines, String key) {
        return lines.stream()
                .anyMatch(
                        c ->
                                c.getContents() instanceof TranslatableContents t
                                        && t.getKey().equals(key));
    }

    // --- registration and the forms -----------------------------------------------------------

    /** The forms, items, bell durability and the Hollow's registration. */
    private static void registered(GameTestHelper helper) {
        helper.assertValueEqual(HushContent.forms().size(), 13, "13 hushed forms");
        for (HushedForm f : HushKeys.FORMS) {
            Block form = BuiltInRegistries.BLOCK.getValue(f.form());
            helper.assertTrue(form.equals(HushContent.form(f.form())), "registered " + f.form());
            helper.assertTrue(
                    form.defaultBlockState().is(HushKeys.HUSHED), f.form() + " in #vftv:hushed");
            helper.assertTrue(
                    !form.defaultBlockState().isRandomlyTicking(), f.form() + " is inert");
        }
        helper.assertValueEqual(
                new ItemStack(HushContent.clearingBell()).getMaxDamage(),
                HushContent.BELL_DURABILITY,
                "64");
        helper.assertValueEqual(
                new ItemStack(HushContent.hushAsh()).getMaxStackSize(), 64, "Ash stacks");
        helper.assertTrue(!HushContent.hollow().isAllowedInPeaceful(), "not in peaceful");
        helper.succeed();
    }

    /** Every form converted and restored, state for state, with its source's light properties. */
    @SuppressWarnings("deprecation") // the light check is exactly the engine's own comparison
    private static void formsSwap(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
        for (HushedForm f : HushKeys.FORMS) {
            Block source = BuiltInRegistries.BLOCK.getValue(f.source());
            for (BlockState state : source.getStateDefinition().getPossibleStates()) {
                BlockState hushed = Objects.requireNonNull(HushedForms.hushed(state));
                helper.assertTrue(
                        hushed.getBlock().equals(HushContent.form(f.form())),
                        "the form of " + state);
                helper.assertTrue(
                        !LightEngine.hasDifferentLightProperties(state, hushed),
                        "light properties of " + state);
                for (Property<?> p : state.getProperties()) {
                    helper.assertValueEqual(hushed.getValue(p), state.getValue(p), p.getName());
                }
                helper.assertTrue(
                        Objects.equals(state, HushedForms.restored(hushed)), "restored exactly");
                helper.assertValueEqual(
                        hushed.getFluidState().getType(), state.getFluidState().getType(), "fluid");
            }
            BlockState state = source.defaultBlockState();
            level.setBlock(at, state, Block.UPDATE_ALL);
            helper.assertTrue(
                    HushService.swap(level, at, Objects.requireNonNull(HushedForms.hushed(state))),
                    "swap in");
            helper.assertTrue(HushService.swap(level, at, state), "swap back");
            helper.assertValueEqual(level.getBlockState(at), state, "the cell after both swaps");
        }
        // restored sand over air: the swap itself schedules no falling tick
        BlockPos sand = helper.absolutePos(new BlockPos(3, 2, 3));
        level.setBlock(sand.below(), Blocks.AIR.defaultBlockState(), HushService.SWAP_FLAGS);
        level.setBlock(
                sand,
                Objects.requireNonNull(HushedForms.hushed(Blocks.SAND.defaultBlockState())),
                HushService.SWAP_FLAGS);
        HushService.swap(level, sand, Blocks.SAND.defaultBlockState());
        helper.runAfterDelay(
                5,
                () -> {
                    helper.assertBlockPresent(Blocks.SAND, new BlockPos(3, 2, 3));
                    helper.succeed();
                });
    }

    /**
     * Plants survive on hushed soil, leaves beside hushed logs don't decay, forms aren't in broad
     * tags.
     */
    private static void supportParity(GameTestHelper helper) {
        BlockState dirt = HushContent.form(id("hushed_dirt")).defaultBlockState();
        helper.assertTrue(
                dirt.is(net.minecraft.tags.BlockTags.SUPPORTS_VEGETATION), "dirt supports plants");
        helper.assertTrue(!dirt.is(net.minecraft.tags.BlockTags.DIRT), "not #minecraft:dirt");
        BlockState log = HushContent.form(id("hushed_oak_log")).defaultBlockState();
        helper.assertValueEqual(
                LeavesBlock.getOptionalDistanceAt(log).orElse(-1), 0, "logs hold leaves");
        helper.assertTrue(!log.is(net.minecraft.tags.BlockTags.LOGS), "not #minecraft:logs");
        helper.setBlock(1, 1, 1, dirt);
        helper.setBlock(1, 2, 1, Blocks.POPPY);
        helper.assertTrue(
                Blocks.POPPY
                        .defaultBlockState()
                        .canSurvive(helper.getLevel(), helper.absolutePos(new BlockPos(1, 2, 1))),
                "a flower on hushed dirt survives");
        helper.succeed();
    }

    // --- deposits ----------------------------------------------------------------------------

    private static void depositZero(GameTestHelper helper) {
        BlockPos rel = new BlockPos(1, 2, 1);
        ChunkPos c = chunk(helper, rel);
        LevelChunk chunk = levelChunk(helper, rel);
        Aura before = FluxService.aura(chunk);
        int flux = before == null ? 0 : before.flux();
        int events = FLUX.size();
        FluxService.DepositResult r =
                FluxService.deposit(
                        helper.getLevel(),
                        helper.absolutePos(rel),
                        0,
                        new FluxSource.Command(),
                        null);
        helper.assertValueEqual(r.generated(), 0, "zero");
        helper.assertValueEqual(FLUX.size(), events, "no event");
        Aura after = FluxService.aura(chunk);
        helper.assertValueEqual(after == null ? 0 : after.flux(), flux, "unchanged");
        try {
            FluxService.deposit(
                    helper.getLevel(), helper.absolutePos(rel), -1, new FluxSource.Command(), null);
            helper.fail("a negative deposit throws");
        } catch (IllegalArgumentException expected) {
            // a caller's data error
        }
        helper.assertTrue(c != null, "chunk");
        helper.succeed();
    }

    private static void depositRetains(GameTestHelper helper) {
        BlockPos rel = new BlockPos(1, 2, 1);
        ServerPlayer player = ResearchTestSupport.player(helper);
        clearAura(helper, rel);
        int events = FLUX.size();
        FluxService.DepositResult r =
                FluxService.deposit(
                        helper.getLevel(),
                        helper.absolutePos(rel),
                        40,
                        new FluxSource.Command(),
                        player);
        helper.assertValueEqual(r.retained(), 40, "retained");
        Aura aura = aura(helper, rel);
        helper.assertTrue(Objects.equals(helper.absolutePos(rel), aura.seep()), "seep");
        helper.assertTrue(player.getUUID().equals(aura.spiller()), "spiller");
        FluxService.deposit(
                helper.getLevel(),
                helper.absolutePos(rel.east()),
                5,
                new FluxSource.Command(),
                null);
        helper.assertTrue(
                player.getUUID().equals(aura.spiller()),
                "an unattributed deposit keeps the spiller");
        helper.assertValueEqual(aura.flux(), 45, "pooled");
        helper.assertTrue(FLUX.size() >= events + 2, "FluxEvent(ADDED) posted");
        helper.assertTrue(
                FLUX.get(FLUX.size() - 1) instanceof FluxPayload.Added a && a.generated() == 5,
                "the event reports the amounts");
        helper.succeed();
    }

    private static void depositCaps(GameTestHelper helper) {
        BlockPos rel = new BlockPos(1, 2, 1);
        deposit(helper, rel, HushLimits.FLUX_FORMAT_MAX - 1);
        long overflow = FluxService.pendingAndOverflow(helper.getLevel())[1];
        FluxService.DepositResult r =
                FluxService.deposit(
                        helper.getLevel(),
                        helper.absolutePos(rel),
                        10,
                        new FluxSource.Command(),
                        null);
        helper.assertValueEqual(r.retained(), 1, "retains 1");
        helper.assertValueEqual(r.overflow(), 9, "overflows 9");
        helper.assertTrue(
                FluxService.pendingAndOverflow(helper.getLevel())[1] >= overflow + 9,
                "the level's total");
        helper.assertValueEqual(
                aura(helper, rel).flux(), HushLimits.FLUX_FORMAT_MAX, "at the bound");
        clearAura(helper, rel);
        helper.succeed();
    }

    /** A deposit for an unloaded chunk is held pending, and nothing is loaded. */
    private static void depositUnloaded(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos far = new BlockPos(29_000_000 - 37, 64, 29_000_000 - 53);
        ChunkPos c = ChunkPos.containing(far);
        helper.assertTrue(FluxService.chunkNow(level, c.pack()) == null, "unloaded");
        long pending = FluxService.pendingAndOverflow(level)[0];
        FluxService.DepositResult r =
                FluxService.deposit(level, far, 12, new FluxSource.Command(), null);
        helper.assertValueEqual(r.pending(), 12, "pending");
        helper.assertTrue(FluxService.pendingAndOverflow(level)[0] >= pending, "an entry");
        helper.assertTrue(FluxService.chunkNow(level, c.pack()) == null, "still unloaded");
        helper.succeed();
    }

    /** Each sibling's spill generates its own priced flux, once. */
    private static void producers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FluxRules rules = FluxRulesService.current();
        java.util.SequencedMap<
                        net.minecraft.resources.ResourceKey<
                                com.andrewbrogoff.vftv.api.aspect.Aspect>,
                        Integer>
                aspects = new TreeMap<>(Map.of(AspectKeys.AER, 120, AspectKeys.TERRA, 80));
        BlockPos rel = new BlockPos(1, 2, 1);
        BlockPos at = helper.absolutePos(rel);
        int expect = FluxMath.essentiaFlux(new int[] {120, 80}, SpillCause.EMPTIED, rules);
        clearAura(helper, rel);
        NeoForge.EVENT_BUS.post(
                new CrucibleEvent(
                        new CruciblePayload.Spilled(level, at, null, aspects, SpillCause.EMPTIED)));
        helper.assertValueEqual(aura(helper, rel).flux(), expect, "crucible EMPTIED");
        clearAura(helper, rel);
        NeoForge.EVENT_BUS.post(
                new EssentiaEvent(
                        new EssentiaPayload.Spilled(level, at, aspects, SpillCause.DESTROYED)));
        helper.assertValueEqual(aura(helper, rel).flux(), expect, "jar DESTROYED");
        clearAura(helper, rel);
        int[] tuning = FluxTuning.defaults();
        tuning[0] = 500;
        FluxRules frozen = FluxTuning.rules(tuning, true, true, false);
        NeoForge.EVENT_BUS.post(
                new InfusionEvent(
                        new InfusionPayload.Spilled(level, at, aspects, SpillCause.UPSET), frozen));
        helper.assertValueEqual(aura(helper, rel).flux(), 100, "an upset under its frozen rules");
        clearAura(helper, rel);
        NeoForge.EVENT_BUS.post(
                new InfusionEvent(
                        new InfusionPayload.Spilled(level, at, aspects, SpillCause.UPSET), null));
        helper.assertValueEqual(aura(helper, rel).flux(), 0, "a pre-Hush run's upset generates 0");
        NeoForge.EVENT_BUS.post(
                new GolemEvent(
                        new GolemPayload.Spilled(
                                UUID.randomUUID(),
                                level,
                                at,
                                AspectKeys.AER,
                                30,
                                SpillCause.DIED)));
        helper.assertValueEqual(aura(helper, rel).flux(), 30, "a golem's death");
        clearAura(helper, rel);
        ServerPlayer player = ResearchTestSupport.player(helper);
        VisQuote quote =
                new VisQuote(
                        new TreeMap<>(),
                        new TreeMap<>(),
                        1000,
                        7,
                        1000,
                        0,
                        1000,
                        false,
                        false,
                        false,
                        false,
                        true,
                        List.of());
        NeoForge.EVENT_BUS.post(
                new VisSpentEvent(
                        new VisSpentPayload(
                                player,
                                level,
                                at,
                                new VisContext(
                                        id("test_spend"),
                                        com.andrewbrogoff.vftv.api.vis.VisKind.ORDINARY,
                                        null),
                                quote)));
        helper.assertValueEqual(aura(helper, rel).flux(), 7, "the quote's spill, never recomputed");
        clearAura(helper, rel);
        helper.succeed();
    }

    /** A later throwing listener on the sibling's event still leaves exactly one deposit. */
    private static void onceWithThrower(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos rel = new BlockPos(1, 2, 1);
        BlockPos at = helper.absolutePos(rel);
        clearAura(helper, rel);
        THROW_AT.add(at);
        try {
            NeoForge.EVENT_BUS.post(
                    new CrucibleEvent(
                            new CruciblePayload.Spilled(
                                    level,
                                    at,
                                    null,
                                    new TreeMap<>(Map.of(AspectKeys.AER, 10)),
                                    SpillCause.REMOVED)));
        } catch (RuntimeException expected) {
            // the bus rethrows the later listener's failure
        } finally {
            THROW_AT.remove(at);
        }
        helper.assertValueEqual(aura(helper, rel).flux(), 10, "deposited once");
        clearAura(helper, rel);
        helper.succeed();
    }

    // --- the spread ----------------------------------------------------------------------------

    /** Attempts convert blocks next to the seep, each debiting the block cost and counting. */
    private static void attemptConverts(GameTestHelper helper) {
        BlockPos seep = dirtCube(helper);
        deposit(helper, seep, 200);
        Aura aura = aura(helper, seep);
        int made = 0;
        for (int i = 0; i < 4; i++) {
            made += Math.max(0, HushService.attempt(helper.getLevel(), chunk(helper, seep)));
        }
        helper.assertValueEqual(aura.steps(), 4L, "steps + 1 per attempt");
        helper.assertValueEqual(aura.flux(), 200 - 2 * (int) aura.taken(), "block cost each");
        helper.assertTrue(aura.taken() >= 1 && made >= aura.taken(), "converted");
        helper.assertValueEqual(
                hushedIn(helper, 0, 1, 0, 4, 5, 4), (int) aura.taken(), "in the cube");
        clearAura(helper, seep);
        helper.succeed();
    }

    /** Below the threshold a chunk is no candidate; at it, it is queued once. */
    private static void onsetAndQueue(GameTestHelper helper) {
        BlockPos rel = new BlockPos(1, 2, 1);
        deposit(helper, rel, 49);
        helper.assertTrue(
                !HushService.queued(helper.getLevel(), chunk(helper, rel)), "49 < 50: idle");
        FluxService.deposit(
                helper.getLevel(), helper.absolutePos(rel), 1, new FluxSource.Command(), null);
        helper.assertTrue(HushService.queued(helper.getLevel(), chunk(helper, rel)), "50: queued");
        clearAura(helper, rel);
        helper.assertTrue(
                !HushService.queued(helper.getLevel(), chunk(helper, rel)), "cleared: leaves");
        helper.succeed();
    }

    /** A cancelled HushSpreadEvent changes and spends nothing. */
    private static void cancelledSpread(GameTestHelper helper) {
        BlockPos seep = dirtCube(helper);
        deposit(helper, seep, 200);
        BlockPos below = helper.absolutePos(seep.below());
        CANCEL_AT.add(below);
        try {
            int made = HushService.spreadTo(helper.getLevel(), chunk(helper, seep), below);
            helper.assertValueEqual(made, 0, "a miss");
        } finally {
            CANCEL_AT.remove(below);
        }
        helper.assertBlockPresent(Blocks.DIRT, seep.below());
        helper.assertValueEqual(aura(helper, seep).flux(), 200, "nothing debited");
        helper.assertValueEqual(
                HushService.spreadTo(helper.getLevel(), chunk(helper, seep), below),
                1,
                "uncancelled");
        helper.assertTrue(helper.getBlockState(seep.below()).is(HushKeys.HUSHED), "converted");
        clearAura(helper, seep);
        helper.succeed();
    }

    /** Max misses saturate a seep with nothing convertible; a retaining deposit wakes it. */
    private static void saturation(GameTestHelper helper) {
        BlockPos rel = new BlockPos(1, 30, 1); // high in the air: nothing in reach converts
        deposit(helper, rel, 100);
        ChunkPos c = chunk(helper, rel);
        helper.assertTrue(HushService.queued(helper.getLevel(), c), "queued");
        for (int i = 0; i < FluxRulesService.current().hushMaxMisses(); i++) {
            HushService.attempt(helper.getLevel(), c);
        }
        helper.assertTrue(HushService.saturated(helper.getLevel(), c), "saturated");
        helper.assertTrue(!HushService.queued(helper.getLevel(), c), "left the queue");
        FluxService.deposit(
                helper.getLevel(), helper.absolutePos(rel), 1, new FluxSource.Command(), null);
        helper.assertTrue(!HushService.saturated(helper.getLevel(), c), "a deposit clears it");
        helper.assertTrue(HushService.queued(helper.getLevel(), c), "and queues it");
        clearAura(helper, rel);
        helper.succeed();
    }

    // --- Hollows -------------------------------------------------------------------------------

    /** On a Hollow turn a converted block raises a Hollow, paid for in flux, homed on the seep. */
    private static void hollowRises(GameTestHelper helper) {
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                helper.setBlock(x, 1, z, Blocks.DIRT);
            }
        }
        BlockPos seep = new BlockPos(2, 2, 2);
        deposit(helper, seep, 200);
        Aura aura = aura(helper, seep);
        aura.setTaken(FluxRulesService.current().hollowEvery() - 1);
        int made =
                HushService.spreadTo(
                        helper.getLevel(),
                        chunk(helper, seep),
                        helper.absolutePos(new BlockPos(2, 1, 2)));
        helper.assertValueEqual(made, 2, "a block and a rise");
        helper.assertValueEqual(
                aura.flux(), 200 - 2 - FluxRulesService.current().hollowFlux(), "paid");
        List<Hollow> hollows =
                helper.getLevel()
                        .getEntitiesOfClass(
                                Hollow.class,
                                new net.minecraft.world.phys.AABB(helper.absolutePos(seep))
                                        .inflate(3));
        helper.assertValueEqual(hollows.size(), 1, "one Hollow");
        Hollow h = hollows.getFirst();
        helper.assertTrue(h.isPersistenceRequired(), "persistent");
        helper.assertValueEqual(h.getHomePosition(), helper.absolutePos(seep), "homed on the seep");
        h.discard();
        clearAura(helper, seep);
        helper.succeed();
    }

    /** The shipped stats, and who a Hollow may hunt. */
    private static void hollowStats(GameTestHelper helper) {
        Hollow h = helper.spawn(HushContent.hollow(), new BlockPos(1, 2, 1));
        helper.assertValueEqual(h.getAttributeValue(Attributes.MAX_HEALTH), 30.0, "health");
        helper.assertValueEqual(h.getAttributeValue(Attributes.ATTACK_DAMAGE), 5.0, "attack");
        helper.assertValueEqual(h.getAttributeValue(Attributes.MOVEMENT_SPEED), 0.26, "speed");
        h.setHomeTo(helper.absolutePos(new BlockPos(1, 2, 1)), 4);
        ServerPlayer near = ResearchTestSupport.player(helper);
        near.snapTo(
                net.minecraft.world.phys.Vec3.atCenterOf(
                        helper.absolutePos(new BlockPos(2, 2, 1))));
        helper.assertTrue(
                helper.getLevel().getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL
                        || Hollow.validTarget(h, near),
                "a player inside its home");
        near.snapTo(
                net.minecraft.world.phys.Vec3.atCenterOf(
                        helper.absolutePos(new BlockPos(1, 2, 12))));
        helper.assertTrue(!Hollow.validTarget(h, near), "outside its home: not hunted");
        h.discard();
        helper.succeed();
    }

    // --- hushed nodes --------------------------------------------------------------------------

    private static void nodeHushed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos rel = new BlockPos(1, 2, 1);
        BlockPos at = helper.absolutePos(rel);
        long id =
                VisGameTests.node(
                        level,
                        at,
                        Identifier.fromNamespaceAndPath("vftv", "windward"),
                        new TreeMap<>(Map.of("vftv:aer", 10)));
        NodeState before = Objects.requireNonNull(VisGameTests.be(level, at).state());
        deposit(helper, rel.above(), 100);
        int made = HushService.spreadTo(level, chunk(helper, rel), at);
        helper.assertValueEqual(made, 1, "hushed");
        NodeState hushed = Objects.requireNonNull(VisGameTests.be(level, at).state());
        helper.assertValueEqual(hushed.type(), HushKeys.HUSHED_NODE.identifier(), "type");
        helper.assertValueEqual(hushed.clarity(), NodeState.Clarity.FAINT, "faint");
        helper.assertValueEqual(hushed.id(), id, "same id");
        helper.assertValueEqual(aura(helper, rel).flux(), 50, "node cost");
        helper.assertTrue(
                Objects.requireNonNull(
                                HushService.JOURNAL.get(level.getServer().overworld())
                                                instanceof Retained.Read<HushRecords.Journal> r
                                        ? r.value()
                                        : null)
                        .nodes()
                        .containsKey(id),
                "journaled");
        clearAura(helper, rel);
        level.getServer()
                .getCommands()
                .performPrefixedCommand(
                        level.getServer()
                                .createCommandSourceStack()
                                .withLevel(level)
                                .withSuppressedOutput(),
                        "vftv hush restore "
                                + at.getX()
                                + " "
                                + at.getY()
                                + " "
                                + at.getZ()
                                + " 0");
        NodeState restored = Objects.requireNonNull(VisGameTests.be(level, at).state());
        helper.assertValueEqual(restored.type(), before.type(), "type restored");
        helper.assertValueEqual(restored.clarity(), before.clarity(), "clarity restored");
        helper.succeed();
    }

    private static void anchoredNode(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos rel = new BlockPos(1, 2, 1);
        BlockPos at = helper.absolutePos(rel);
        VisGameTests.node(
                level,
                at,
                Identifier.fromNamespaceAndPath("vftv", "wellspring"),
                new TreeMap<>(Map.of("vftv:aer", 10)));
        deposit(helper, rel.above(), 100);
        helper.assertValueEqual(
                HushService.spreadTo(level, chunk(helper, rel), at), 0, "never hushed");
        helper.assertValueEqual(aura(helper, rel).flux(), 100, "nothing spent");
        clearAura(helper, rel);
        helper.succeed();
    }

    // --- the bells -----------------------------------------------------------------------------

    private static void clearingBell(GameTestHelper helper) {
        BlockPos rel = new BlockPos(2, 2, 2);
        deposit(helper, rel, 80);
        BlockState hushed =
                Objects.requireNonNull(HushedForms.hushed(Blocks.STONE.defaultBlockState()));
        helper.setBlock(1, 1, 2, hushed);
        helper.setBlock(3, 1, 2, hushed);
        ServerPlayer player = ResearchTestSupport.player(helper);
        player.snapTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(helper.absolutePos(rel)));
        ItemStack bell = new ItemStack(HushContent.clearingBell());
        player.setItemInHand(InteractionHand.MAIN_HAND, bell);
        HushBells.ring(player, InteractionHand.MAIN_HAND);
        helper.assertValueEqual(aura(helper, rel).flux(), 30, "cleared bell.flux");
        helper.assertBlockPresent(Blocks.STONE, new BlockPos(1, 1, 2));
        helper.assertBlockPresent(Blocks.STONE, new BlockPos(3, 1, 2));
        helper.assertValueEqual(bell.getDamageValue(), 1, "one point");
        helper.assertTrue(player.getCooldowns().isOnCooldown(bell), "cooldown");
        player.getCooldowns().removeCooldown(BuiltInRegistries.ITEM.getKey(bell.getItem()));
        HushBells.ring(player, InteractionHand.MAIN_HAND);
        helper.assertValueEqual(aura(helper, rel).flux(), 30, "one ring attempt per tick");
        clearAura(helper, rel);
        helper.succeed();
    }

    private static void clearingBellNothing(GameTestHelper helper) {
        BlockPos rel = new BlockPos(2, 2, 2);
        clearAura(helper, rel);
        ServerPlayer player = ResearchTestSupport.player(helper);
        player.snapTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(helper.absolutePos(rel)));
        ItemStack bell = new ItemStack(HushContent.clearingBell());
        player.setItemInHand(InteractionHand.MAIN_HAND, bell);
        List<Component> before = lines(player);
        HushBells.ring(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(
                says(lines(player), HushBells.NOTHING) && lines(player).size() > before.size(),
                "nothing is hushed");
        helper.assertValueEqual(bell.getDamageValue(), 0, "no cost");
        helper.assertTrue(!player.getCooldowns().isOnCooldown(bell), "no cooldown");
        helper.succeed();
    }

    private static PealingBellBlockEntity bell(GameTestHelper helper, BlockPos rel) {
        helper.setBlock(
                rel,
                HushContent.pealingBell()
                        .defaultBlockState()
                        .setValue(
                                net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING,
                                Direction.NORTH));
        return Objects.requireNonNull(helper.getBlockEntity(rel, PealingBellBlockEntity.class));
    }

    private static int feed(GameTestHelper helper, BlockPos rel, int amount, boolean commit) {
        ResourceHandler<EssentiaResource> handler =
                Objects.requireNonNull(
                        helper.getLevel()
                                .getCapability(
                                        EssentiaCapabilities.HANDLER,
                                        helper.absolutePos(rel),
                                        null));
        EssentiaResource sonus =
                new EssentiaResource(
                        helper.getLevel()
                                .registryAccess()
                                .lookupOrThrow(AspectKeys.REGISTRY)
                                .getOrThrow(AspectKeys.SONUS));
        try (Transaction tx = Transaction.openRoot()) {
            int n = handler.insert(sonus, amount, tx);
            if (commit) {
                tx.commit();
            }
            return n;
        }
    }

    private static void pealingBell(GameTestHelper helper) {
        BlockPos rel = new BlockPos(2, 2, 2);
        PealingBellBlockEntity be = bell(helper, rel);
        helper.assertValueEqual(
                feed(helper, rel, 10, false), 10, "accepted inside the transaction");
        helper.assertValueEqual(be.sonus(), 0, "rolled back");
        helper.assertValueEqual(
                feed(helper, rel, 1000, true),
                FluxRulesService.current().pealCapacity(),
                "to capacity");
        helper.assertValueEqual(be.sonus(), FluxRulesService.current().pealCapacity(), "committed");
        int suction =
                Objects.requireNonNull(
                                helper.getLevel()
                                        .getCapability(
                                                EssentiaCapabilities.SUCTION,
                                                helper.absolutePos(rel),
                                                null))
                        .suction(AspectKeys.SONUS);
        helper.assertValueEqual(suction, 0, "full: no suction");
        deposit(helper, rel, 80);
        BlockState hushed =
                Objects.requireNonNull(HushedForms.hushed(Blocks.DIRT.defaultBlockState()));
        helper.setBlock(1, 1, 1, hushed);
        HushBells.peal(helper.getLevel(), be, FluxRulesService.current());
        helper.assertValueEqual(aura(helper, rel).flux(), 30, "cleared peal.flux");
        helper.assertValueEqual(
                be.sonus(), FluxRulesService.current().pealCapacity() - 1, "one Sonus used");
        int fullSweep = 16;
        for (int i = 0;
                i < fullSweep && helper.getBlockState(new BlockPos(1, 1, 1)).is(HushKeys.HUSHED);
                i++) {
            HushBells.peal(helper.getLevel(), be, FluxRulesService.current());
        }
        helper.assertBlockPresent(Blocks.DIRT, new BlockPos(1, 1, 1));
        clearAura(helper, rel);
        helper.succeed();
    }

    private static void pealingRemoval(GameTestHelper helper) {
        BlockPos rel = new BlockPos(2, 2, 2);
        bell(helper, rel);
        feed(helper, rel, 12, true);
        clearAura(helper, rel);
        helper.setBlock(rel, Blocks.AIR);
        helper.assertValueEqual(aura(helper, rel).flux(), 12, "its Sonus spills once");
        clearAura(helper, rel);
        helper.succeed();
    }

    // --- place, persistence, commands ----------------------------------------------------------

    private static void placeQuery(GameTestHelper helper) {
        BlockPos rel = new BlockPos(1, 2, 1);
        BlockPos at = helper.absolutePos(rel);
        helper.setBlock(rel.below(), Blocks.STONE);
        helper.assertTrue(!HushPlaceQuery.INSTANCE.test(helper.getLevel(), at), "untouched ground");
        helper.setBlock(
                rel.below(),
                Objects.requireNonNull(HushedForms.hushed(Blocks.STONE.defaultBlockState())));
        helper.assertTrue(
                HushPlaceQuery.INSTANCE.test(helper.getLevel(), at), "standing on hushed ground");
        helper.assertTrue(
                !HushPlaceQuery.INSTANCE.test(
                        helper.getLevel(), new BlockPos(29_000_000 - 37, 64, 29_000_000 - 53)),
                "an unloaded chunk answers false");
        helper.succeed();
    }

    private static void auraPersistence(GameTestHelper helper) {
        Codec<Aura> codec = Aura.CODEC;
        BlockPos rel = new BlockPos(1, 2, 1);
        deposit(helper, rel, 77);
        Aura aura = aura(helper, rel);
        Tag tag = codec.encodeStart(NbtOps.INSTANCE, aura).getOrThrow();
        Aura back = codec.parse(NbtOps.INSTANCE, tag).getOrThrow();
        helper.assertValueEqual(back.flux(), 77, "flux");
        helper.assertTrue(Objects.equals(back.seep(), aura.seep()), "seep");
        CompoundTag future = ((CompoundTag) tag).copy();
        future.putInt("format", 2);
        Aura unreadable = codec.parse(NbtOps.INSTANCE, future).getOrThrow();
        helper.assertTrue(!unreadable.readable(), "a future format is unreadable");
        helper.assertValueEqual(
                codec.encodeStart(NbtOps.INSTANCE, unreadable).getOrThrow(),
                future,
                "written back unchanged");
        clearAura(helper, rel);
        helper.succeed();
    }

    private static void storesRetain(GameTestHelper helper) {
        var pending = com.andrewbrogoff.vftv.core.common.RawRecordCodec.wrap(HushRecords.PENDING);
        CompoundTag bad = new CompoundTag();
        bad.putInt("format", 1);
        bad.putLong("overflow", -1);
        bad.put("entries", new net.minecraft.nbt.ListTag());
        helper.assertTrue(
                pending.parse(NbtOps.INSTANCE, bad).getOrThrow() instanceof Retained.Unreadable<?>,
                "negative overflow");
        var journal = com.andrewbrogoff.vftv.core.common.RawRecordCodec.wrap(HushRecords.JOURNAL);
        CompoundTag j = new CompoundTag();
        j.putInt("format", 1);
        CompoundTag nodes = new CompoundTag();
        CompoundTag entry = new CompoundTag();
        entry.putString("type", "vftv:windward");
        entry.putString("clarity", "dim");
        nodes.put("12", entry);
        j.put("nodes", nodes);
        helper.assertTrue(
                journal.parse(NbtOps.INSTANCE, j).getOrThrow() instanceof Retained.Unreadable<?>,
                "unknown clarity");
        entry.putString("clarity", "clear");
        helper.assertTrue(
                journal.parse(NbtOps.INSTANCE, j).getOrThrow() instanceof Retained.Read<?>,
                "readable");
        helper.succeed();
    }

    private static void commands(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
        clearAura(helper, new BlockPos(1, 2, 1));
        var source =
                level.getServer()
                        .createCommandSourceStack()
                        .withLevel(level)
                        .withSuppressedOutput();
        var commands = level.getServer().getCommands();
        String xyz = at.getX() + " " + at.getY() + " " + at.getZ();
        commands.performPrefixedCommand(source, "vftv flux add " + xyz + " 60");
        helper.assertValueEqual(aura(helper, new BlockPos(1, 2, 1)).flux(), 60, "flux add");
        commands.performPrefixedCommand(source, "vftv flux get " + xyz);
        commands.performPrefixedCommand(source, "vftv flux clear " + xyz + " 1");
        helper.assertValueEqual(aura(helper, new BlockPos(1, 2, 1)).flux(), 0, "flux clear");
        commands.performPrefixedCommand(source, "vftv flux add " + xyz + " 0");
        helper.assertValueEqual(
                aura(helper, new BlockPos(1, 2, 1)).flux(), 0, "amount 0 is rejected");
        helper.setBlock(
                1,
                1,
                1,
                Objects.requireNonNull(HushedForms.hushed(Blocks.DIRT.defaultBlockState())));
        commands.performPrefixedCommand(source, "vftv hush restore " + xyz + " 2");
        helper.assertBlockPresent(Blocks.DIRT, new BlockPos(1, 1, 1));
        helper.succeed();
    }

    private static void recipes(GameTestHelper helper) {
        var recipes = helper.getLevel().getServer().getRecipeManager();
        for (String id : List.of("clearing_bell", "pealing_bell")) {
            helper.assertTrue(
                    recipes.byKey(
                                    net.minecraft.resources.ResourceKey.create(
                                            net.minecraft.core.registries.Registries.RECIPE,
                                            id(id)))
                            .isPresent(),
                    id + " loads");
        }
        helper.succeed();
    }

    /**
     * {@code hush.spread} off makes no attempt; attendance follows {@code hush.unattended} and
     * excludes a Silenced player, who still attends a bell and is never hunted; {@code
     * hush.hollows} off raises nothing.
     */
    private static void switches(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos rel = new BlockPos(1, 2, 1);
        ChunkPos c = chunk(helper, rel);
        com.andrewbrogoff.vftv.core.flux.HushConfig before = FluxRulesService.config();
        ServerPlayer player = null;
        try {
            FluxRulesService.installConfig(
                    new com.andrewbrogoff.vftv.core.flux.HushConfig(false, true, true));
            deposit(helper, rel, 10_000);
            HushService.serve(level);
            helper.assertValueEqual(aura(helper, rel).steps(), 0L, "spread off: no attempt at all");
            int[] tuning = FluxTuning.defaults();
            FluxRules attendedOnly = FluxTuning.rules(tuning, true, true, false);
            FluxRules unattended = FluxTuning.rules(tuning, true, true, true);
            helper.assertTrue(
                    !HushService.attended(
                            level, ChunkPos.pack(1_000_000, 1_000_000), attendedOnly, false),
                    "nobody near: idle");
            helper.assertTrue(
                    HushService.attended(
                            level, ChunkPos.pack(1_000_000, 1_000_000), unattended, false),
                    "unattended on");
            player = ResearchTestSupport.player(helper);
            level.addNewPlayer(player);
            // far from every other test's players: only this one can attend
            long far = ChunkPos.pack(1_000_000, 1_000_000);
            player.snapTo(16_000_008, 100, 16_000_008);
            helper.assertTrue(
                    HushService.attended(level, far, attendedOnly, false), "a player attends");
            com.andrewbrogoff.vftv.core.warp.WarpService.silence(
                    player, new com.andrewbrogoff.vftv.api.warp.WarpPreview(0, 0, 0, false));
            helper.assertTrue(
                    com.andrewbrogoff.vftv.core.warp.WarpService.isSilenced(player), "silenced");
            helper.assertTrue(
                    !HushService.attended(level, far, attendedOnly, false),
                    "the Hush can't hear them");
            helper.assertTrue(
                    HushService.attended(level, far, attendedOnly, true), "a bell still can");
            player.snapTo(net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(rel)));
            Hollow h = helper.spawn(HushContent.hollow(), rel);
            h.setHomeTo(helper.absolutePos(rel), 8);
            helper.assertTrue(!Hollow.validTarget(h, player), "never hunts the Silenced");
            h.discard();
            FluxRulesService.installConfig(
                    new com.andrewbrogoff.vftv.core.flux.HushConfig(true, false, false));
            for (int x = 0; x < 3; x++) {
                for (int z = 0; z < 3; z++) {
                    helper.setBlock(x, 1, z, Blocks.DIRT);
                }
            }
            aura(helper, rel).setTaken(FluxRulesService.current().hollowEvery() - 1);
            BlockPos target = helper.absolutePos(new BlockPos(1, 1, 1));
            player.snapTo(
                    net.minecraft.world.phys.Vec3.atCenterOf(
                            helper.absolutePos(rel).offset(20, 0, 0)));
            helper.assertValueEqual(
                    HushService.spreadTo(level, c, target), 1, "hollows off: no rise");
        } finally {
            FluxRulesService.installConfig(before);
            if (player != null) {
                player.discard();
                level.removePlayerImmediately(
                        player, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
            }
            clearAura(helper, rel);
        }
        helper.succeed();
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("vftv", path);
    }
}
