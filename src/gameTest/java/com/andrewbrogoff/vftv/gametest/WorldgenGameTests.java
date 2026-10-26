package com.andrewbrogoff.vftv.gametest;

import com.andrewbrogoff.vftv.api.research.Research;
import com.andrewbrogoff.vftv.api.research.ResearchKeys;
import com.andrewbrogoff.vftv.api.worldgen.WorldgenKeys;
import com.andrewbrogoff.vftv.core.common.RawRecordCodec.Retained;
import com.andrewbrogoff.vftv.core.research.ResearchItems;
import com.andrewbrogoff.vftv.core.research.Resolver;
import com.andrewbrogoff.vftv.core.text.StatusLine;
import com.andrewbrogoff.vftv.core.text.StatusLines;
import com.andrewbrogoff.vftv.core.vis.NodeBlockEntity;
import com.andrewbrogoff.vftv.core.vis.NodeState;
import com.andrewbrogoff.vftv.core.vis.VisContent;
import com.andrewbrogoff.vftv.core.worldgen.Answerer;
import com.andrewbrogoff.vftv.core.worldgen.Cantor;
import com.andrewbrogoff.vftv.core.worldgen.ChoirSitePlaceQuery;
import com.andrewbrogoff.vftv.core.worldgen.ReliquaryBlockEntity;
import com.andrewbrogoff.vftv.core.worldgen.ReliquaryService;
import com.andrewbrogoff.vftv.core.worldgen.WorldgenCodecs;
import com.andrewbrogoff.vftv.core.worldgen.WorldgenContent;
import com.andrewbrogoff.vftv.core.worldgen.WorldgenService;
import com.andrewbrogoff.vftv.core.worldgen.model.ChoirClaims;
import com.andrewbrogoff.vftv.core.worldgen.model.NodeRoll;
import com.andrewbrogoff.vftv.core.worldgen.model.Offers;
import com.andrewbrogoff.vftv.core.worldgen.model.WorldgenTuning;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.DataResult;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * The worldgen feature's GameTests. Name each test {@code worldgen_<what>}, and list its id {@code
 * vftv:worldgen_<what>} in {@code src/gameTest/expected/worldgen.txt}.
 *
 * <p>Ruins and templates are placed far from the test grid, each test in its own column, so a
 * 105-block ruin never lands on a neighbour's test. Tuning and difficulty are global, so a test
 * that changes either restores it before the tick ends, or owns its whole sequence.
 */
final class WorldgenGameTests {
    private static final ResourceKey<Research> PAGE =
            ResourceKey.create(
                    ResearchKeys.REGISTRY,
                    Identifier.fromNamespaceAndPath("vftv", "choir/beneath_the_notes"));

    private enum Toss {
        NONE,
        CANCEL,
        THROW,
        REENTER
    }

    private static volatile Toss toss = Toss.NONE;
    private static volatile UUID tosser = new UUID(0, 0);
    private static volatile BlockPos tossAt = BlockPos.ZERO;
    private static final List<Offers.Outcome> REENTRY = Collections.synchronizedList(new ArrayList<>());
    private static volatile UUID cancelUse = new UUID(0, 0);

    private static final List<Map.Entry<ServerPlayer, Component>> LINES =
            Collections.synchronizedList(new ArrayList<>());
    private static boolean capturing;

    private WorldgenGameTests() {}

    /**
     * Adds the feature's tests by name; {@code modBus} is for any test-only registration they need.
     */
    static void register(Map<String, Consumer<GameTestHelper>> tests, IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(ItemTossEvent.class, WorldgenGameTests::onToss);
        NeoForge.EVENT_BUS.addListener(
                PlayerInteractEvent.RightClickBlock.class,
                e -> {
                    if (e.getEntity().getUUID().equals(cancelUse)) {
                        e.setCanceled(true);
                    }
                });
        tests.put("worldgen_registered", WorldgenGameTests::registered);
        tests.put("worldgen_reliquary_two_players", WorldgenGameTests::twoPlayers);
        tests.put("worldgen_reliquary_129_claimants", WorldgenGameTests::claimants);
        tests.put("worldgen_reliquary_rows", WorldgenGameTests::rows);
        tests.put("worldgen_reliquary_reissue_after_loss", WorldgenGameTests::reissueAfterLoss);
        tests.put("worldgen_reliquary_cancelled_toss", WorldgenGameTests::cancelledToss);
        tests.put("worldgen_reliquary_throwing_callback", WorldgenGameTests::throwingCallback);
        tests.put("worldgen_reliquary_reentry", WorldgenGameTests::reentry);
        tests.put("worldgen_reliquary_attempt_cap", WorldgenGameTests::attemptCap);
        tests.put("worldgen_reliquary_claims_persist", WorldgenGameTests::claimsPersist);
        tests.put("worldgen_reliquary_unreadable_claims", WorldgenGameTests::unreadableClaims);
        tests.put("worldgen_reliquary_record_cases", WorldgenGameTests::recordCases);
        tests.put("worldgen_reliquary_cancelled_use", WorldgenGameTests::cancelledUse);
        tests.put("worldgen_reliquary_unmovable", WorldgenGameTests::unmovable);
        tests.put("worldgen_structure_place", WorldgenGameTests::structurePlace);
        tests.put("worldgen_structure_protects_and_obstructs", WorldgenGameTests::protects);
        tests.put("worldgen_structure_third_node_removed", WorldgenGameTests::thirdNode);
        tests.put("worldgen_structure_missing_type", WorldgenGameTests::missingType);
        tests.put("worldgen_answerer_persistent", WorldgenGameTests::persistent);
        tests.put("worldgen_answerer_home_and_range", WorldgenGameTests::homeAndRange);
        tests.put("worldgen_answerer_ignores_non_players", WorldgenGameTests::ignoresZombie);
        tests.put("worldgen_answerer_retaliation", WorldgenGameTests::retaliation);
        tests.put("worldgen_answerer_alerts", WorldgenGameTests::alerts);
        tests.put("worldgen_cantor_chant", WorldgenGameTests::chant);
        tests.put("worldgen_answerer_peaceful", WorldgenGameTests::peaceful);
        tests.put("worldgen_answerer_drops", WorldgenGameTests::drops);
        tests.put("worldgen_find_advancements", WorldgenGameTests::finds);
        tests.put("worldgen_find_relatch_after_reset", WorldgenGameTests::relatch);
        tests.put("worldgen_kill_predicate", WorldgenGameTests::killPredicate);
        tests.put("worldgen_place_query", WorldgenGameTests::placeQuery);
        tests.put("worldgen_tuning_reload", WorldgenGameTests::tuningReload);
        tests.put("worldgen_site_registry", WorldgenGameTests::siteRegistry);
    }

    // --- helpers ------------------------------------------------------------------------------

    private static void onToss(ItemTossEvent event) {
        if (!event.getPlayer().getUUID().equals(tosser)) {
            return;
        }
        switch (toss) {
            case CANCEL -> event.setCanceled(true);
            case THROW -> throw new IllegalStateException("a test's throwing toss listener");
            case REENTER ->
                    REENTRY.add(ReliquaryService.use((ServerPlayer) event.getPlayer(), tossAt));
            case NONE -> {}
        }
    }

    private static BlockPos reliquary(GameTestHelper helper, BlockPos rel) {
        helper.setBlock(rel, WorldgenContent.reliquary());
        return helper.absolutePos(rel);
    }

    private static InteractionResult use(ServerPlayer player, BlockPos abs) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
        return player.gameMode.useItemOn(
                player, player.level(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
    }

    private static int pages(ServerPlayer player) {
        int n = 0;
        for (ItemStack s : player.getInventory()) {
            if (s.is(ResearchItems.choirPage()) && PAGE.equals(s.get(ResearchItems.page()))) {
                n += s.getCount();
            }
        }
        return n;
    }

    private static ChoirClaims claims(ServerPlayer player) {
        return ReliquaryService.claims().get(player) instanceof Retained.Read<ChoirClaims> r
                ? r.value()
                : ChoirClaims.EMPTY;
    }

    private static List<Component> lines(ServerPlayer player) {
        synchronized (WorldgenGameTests.class) {
            if (!capturing) {
                capturing = true;
                AtomicReference<BiConsumer<ServerPlayer, StatusLine>> real = new AtomicReference<>();
                real.set(
                        StatusLines.swapSender(
                                (p, line) -> {
                                    LINES.add(Map.entry(p, line.text()));
                                    real.get().accept(p, line);
                                }));
            }
        }
        List<Component> out = new ArrayList<>();
        synchronized (LINES) {
            LINES.stream().filter(e -> e.getKey().equals(player)).forEach(e -> out.add(e.getValue()));
        }
        return out;
    }

    private static boolean said(ServerPlayer player, String key) {
        return lines(player).stream()
                .anyMatch(l -> l.getContents() instanceof TranslatableContents t && t.getKey().equals(key));
    }

    /** A far column of its own for a test that places a ruin or a template. */
    private static BlockPos far(GameTestHelper helper, int column) {
        BlockPos o = helper.absolutePos(BlockPos.ZERO);
        return new BlockPos(20_000 + column * 512, o.getY(), 20_000 + Math.floorMod(o.getZ(), 64));
    }

    private static void load(ServerLevel level, BoundingBox box) {
        for (int cx = SectionPos.blockToSectionCoord(box.minX()); cx <= SectionPos.blockToSectionCoord(box.maxX()); cx++) {
            for (int cz = SectionPos.blockToSectionCoord(box.minZ()); cz <= SectionPos.blockToSectionCoord(box.maxZ()); cz++) {
                level.getChunk(cx, cz);
            }
        }
    }

    /** {@code /place structure}'s own steps: generate a start and place it in every chunk. */
    private static StructureStart placeRuin(ServerLevel level, BlockPos at) {
        var holder =
                level.registryAccess()
                        .lookupOrThrow(Registries.STRUCTURE)
                        .getOrThrow(WorldgenKeys.CHOIR_RUIN);
        var generator = level.getChunkSource().getGenerator();
        load(level, new BoundingBox(at).inflatedBy(64));
        StructureStart start =
                holder.value()
                        .generate(
                                holder,
                                level.dimension(),
                                level.registryAccess(),
                                generator,
                                generator.getBiomeSource(),
                                level.getChunkSource().randomState(),
                                level.getStructureManager(),
                                level.getSeed(),
                                ChunkPos.containing(at),
                                0,
                                level,
                                b -> true);
        BoundingBox box = start.getBoundingBox();
        ChunkPos.rangeClosed(
                        new ChunkPos(SectionPos.blockToSectionCoord(box.minX()), SectionPos.blockToSectionCoord(box.minZ())),
                        new ChunkPos(SectionPos.blockToSectionCoord(box.maxX()), SectionPos.blockToSectionCoord(box.maxZ())))
                .forEach(
                        c ->
                                start.placeInChunk(
                                        level,
                                        level.structureManager(),
                                        generator,
                                        level.getRandom(),
                                        new BoundingBox(c.getMinBlockX(), level.getMinY(), c.getMinBlockZ(), c.getMaxBlockX(), level.getMaxY() + 1, c.getMaxBlockZ()),
                                        c));
        return start;
    }

    /** A chancel template placed with the ruin's processor list, as a pool element places it. */
    private static StructurePlaceSettings settings(ServerLevel level) {
        StructureProcessorList list =
                level.registryAccess()
                        .lookupOrThrow(Registries.PROCESSOR_LIST)
                        .getValueOrThrow(ResourceKey.create(Registries.PROCESSOR_LIST, Identifier.fromNamespaceAndPath("vftv", "choir_ruin")));
        StructurePlaceSettings s = new StructurePlaceSettings().setFinalizeEntities(true);
        list.list().forEach(s::addProcessor);
        return s;
    }

    private static StructureTemplate chancel(ServerLevel level) {
        return level.getStructureManager()
                .getOrCreate(Identifier.fromNamespaceAndPath("vftv", "choir_ruin/chancel_a"));
    }

    private static List<BlockPos> find(ServerLevel level, BoundingBox box, net.minecraft.world.level.block.Block block) {
        List<BlockPos> out = new ArrayList<>();
        BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())
                .forEach(
                        p -> {
                            if (level.getBlockState(p).is(block)) {
                                out.add(p.immutable());
                            }
                        });
        return out;
    }

    private static Answerer answerer(GameTestHelper helper, BlockPos rel, boolean cantor) {
        return helper.spawn(cantor ? WorldgenContent.cantor() : WorldgenContent.answerer(), rel, EntitySpawnReason.STRUCTURE);
    }

    private static ServerPlayer survivor(GameTestHelper helper, BlockPos rel) {
        ServerPlayer p = ResearchTestSupport.player(helper);
        Vec3 at = helper.absoluteVec(Vec3.atBottomCenterOf(rel));
        p.setPos(at.x, at.y, at.z);
        return p;
    }

    private static CompoundTag save(Entity entity) {
        TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, entity.registryAccess());
        entity.saveWithoutId(out);
        return out.buildResult();
    }

    // --- registration and data ----------------------------------------------------------------

    /** Everything registered, tagged and loaded; the site kind in the server-only registry. */
    private static void registered(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.assertTrue(WorldgenContent.reliquary().defaultBlockState().is(net.minecraft.tags.BlockTags.FEATURES_CANNOT_REPLACE), "the Reliquary cannot be replaced by features");
        helper.assertTrue(WorldgenContent.reliquary().defaultBlockState().is(net.minecraft.tags.BlockTags.WITHER_IMMUNE), "wither-immune");
        helper.assertTrue(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENTITY_TYPE).getOrThrow(WorldgenContent.ANSWERER_KEY).is(WorldgenKeys.ANSWERERS), "#vftv:answerers");
        helper.assertTrue(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENTITY_TYPE).getOrThrow(WorldgenContent.CANTOR_KEY).is(WorldgenKeys.ANSWERERS), "#vftv:answerers");
        helper.assertTrue(WorldgenContent.answerer().isAllowedInPeaceful(), "allowed in peaceful");
        helper.assertTrue(Blocks.TUFF_BRICKS.defaultBlockState().is(WorldgenKeys.CHOIR_WEATHERING), "#vftv:choir_weathering");
        helper.assertTrue(level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(WorldgenKeys.CHOIR_RUIN).isPresent(), "the structure");
        helper.assertTrue(level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET).get(ResourceKey.create(Registries.STRUCTURE_SET, WorldgenKeys.CHOIR_RUIN.identifier())).isPresent(), "the structure set");
        helper.assertValueEqual(ChoirSitePlaceQuery.resolved(), 1, "#vftv:choir_sites resolved");
        helper.assertTrue(level.registryAccess().lookupOrThrow(WorldgenKeys.CHOIR_SITE).get(WorldgenKeys.CHOIR_RUIN_SITE).isPresent(), "the site kind");
        helper.assertTrue(level.registryAccess().lookupOrThrow(com.andrewbrogoff.vftv.api.vis.VisKeys.NODE_TYPE_REGISTRY).get(WorldgenKeys.CHOIR_NODE_TYPE).isPresent(), "the vftv:choir node type");
        helper.assertValueEqual(WorldgenService.tuning(), WorldgenTuning.DEFAULTS, "the shipped tuning");
        helper.succeed();
    }

    /** The site kinds validate, and the built-in kind passes its own rules. */
    private static void siteRegistry(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var sites = WorldgenService.sites(access);
        var entries = WorldgenService.entries(access);
        helper.assertValueEqual(com.andrewbrogoff.vftv.core.worldgen.model.SiteRules.check(sites, entries), List.of(), "load rules");
        helper.assertValueEqual(com.andrewbrogoff.vftv.core.worldgen.model.SiteRules.builtIn(Objects.requireNonNull(sites.get("vftv:choir_ruin")), entries), List.of(), "built-in rules");
        helper.assertValueEqual(Objects.requireNonNull(sites.get("vftv:choir_ruin")).perClaim(), 6, "pages_per_claim");
        boolean refused = false;
        try {
            WorldgenService.validateAtOpen(access);
        } catch (IllegalStateException e) {
            refused = true;
        }
        helper.assertFalse(refused, "the shipped data opens");
        helper.succeed();
    }

    // --- the Reliquary ------------------------------------------------------------------------

    /** Two players each get beneath_the_notes from one Reliquary: two claims, neither touching the other. */
    private static void twoPlayers(GameTestHelper helper) {
        BlockPos abs = reliquary(helper, new BlockPos(1, 1, 1));
        ServerPlayer a = survivor(helper, new BlockPos(1, 1, 0));
        ServerPlayer b = survivor(helper, new BlockPos(1, 1, 0));
        use(a, abs);
        use(b, abs);
        helper.assertValueEqual(pages(a), 1, "a's page");
        helper.assertValueEqual(pages(b), 1, "b's page");
        String dim = helper.getLevel().dimension().identifier().toString();
        helper.assertValueEqual(Objects.requireNonNull(claims(a).at(dim, abs.asLong())), List.of(PAGE.identifier().toString()), "a's claim");
        helper.assertValueEqual(Objects.requireNonNull(claims(b).at(dim, abs.asLong())), List.of(PAGE.identifier().toString()), "b's claim");
        helper.assertTrue(said(a, "reliquary.vftv.given") && said(b, "reliquary.vftv.given"), "both given");
        helper.succeed();
    }

    /** 129 distinct fake players each claim theirs; the 129th's claim is the first's. */
    private static void claimants(GameTestHelper helper) {
        BlockPos abs = reliquary(helper, new BlockPos(1, 1, 1));
        List<ServerPlayer> players = new ArrayList<>();
        for (int i = 0; i < 129; i++) {
            ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(new UUID(0x7417, i), "claimant" + i));
            p.setPos(abs.getX() + 0.5, abs.getY(), abs.getZ() - 0.5);
            ReliquaryService.forget(p);
            ReliquaryService.claims().set(p, new Retained.Read<>(ChoirClaims.EMPTY));
            players.add(p);
        }
        List<Offers.Outcome> outcomes = new ArrayList<>();
        for (ServerPlayer p : players) {
            outcomes.add(ReliquaryService.use(p, abs));
        }
        helper.assertTrue(outcomes.stream().allMatch(o -> o == Offers.Outcome.GIVEN), "all given: " + outcomes);
        helper.assertValueEqual(claims(players.get(128)), claims(players.get(0)), "the 129th claim is the first's");
        players.forEach(ReliquaryService::forget);
        helper.succeed();
    }

    /** Rows 2 to 8 with real players: given, reissued, done, all_held, silent and unreadable. */
    private static void rows(GameTestHelper helper) {
        BlockPos abs = reliquary(helper, new BlockPos(1, 1, 1));
        BlockPos silent = reliquary(helper, new BlockPos(3, 1, 1));
        ReliquaryBlockEntity be = Objects.requireNonNull(helper.getLevel().getBlockEntity(silent, WorldgenContent.reliquaryType()).orElse(null));
        CompoundTag pack = new CompoundTag();
        pack.putString("id", "vftv:reliquary");
        pack.putInt("format", 1);
        pack.putString("site", "pack:none");
        be.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), pack));
        ServerPlayer a = survivor(helper, new BlockPos(1, 1, 0));
        ServerPlayer c = survivor(helper, new BlockPos(1, 1, 0));
        ResearchTestSupport.latch(c, PAGE);
        helper.startSequence()
                .thenExecute(() -> helper.assertValueEqual(ReliquaryService.use(a, abs), Offers.Outcome.GIVEN, "row 8"))
                .thenExecuteAfter(1, () -> {
                    a.getInventory().clearContent();
                    helper.assertValueEqual(ReliquaryService.use(a, abs), Offers.Outcome.REISSUED, "row 5");
                    helper.assertValueEqual(pages(a), 1, "the lost page back");
                })
                .thenExecuteAfter(1, () -> {
                    ResearchTestSupport.latch(a, PAGE);
                    helper.assertValueEqual(ReliquaryService.use(a, abs), Offers.Outcome.DONE, "row 4");
                    helper.assertValueEqual(ReliquaryService.use(c, abs), Offers.Outcome.ALL_HELD, "row 6");
                })
                .thenExecuteAfter(1, () -> {
                    helper.assertValueEqual(ReliquaryService.use(c, abs), Offers.Outcome.ALL_HELD, "row 6 again: nothing recorded");
                    helper.assertValueEqual(claims(c).sites().size(), 0, "no claim recorded");
                    helper.assertValueEqual(ReliquaryService.use(a, silent), Offers.Outcome.SILENT, "row 3");
                    helper.assertTrue(said(a, "reliquary.vftv.silent"), ".silent");
                    helper.assertTrue(said(a, "reliquary.vftv.done") && said(a, "reliquary.vftv.reissued"), "the messages");
                    helper.assertTrue(said(c, "reliquary.vftv.all_held"), ".all_held");
                })
                .thenSucceed();
    }

    /** Reissue at once after the page is tossed, and after a full inventory dropped it. */
    private static void reissueAfterLoss(GameTestHelper helper) {
        BlockPos abs = reliquary(helper, new BlockPos(1, 1, 1));
        ServerPlayer a = survivor(helper, new BlockPos(1, 1, 0));
        for (int i = 0; i < a.getInventory().getContainerSize(); i++) {
            a.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
        }
        helper.startSequence()
                .thenExecute(() -> {
                    helper.assertValueEqual(ReliquaryService.use(a, abs), Offers.Outcome.GIVEN, "given into a full inventory");
                    helper.assertValueEqual(pages(a), 0, "dropped, not held");
                    List<ItemEntity> dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(abs).inflate(4), e -> e.getItem().is(ResearchItems.choirPage()));
                    helper.assertValueEqual(dropped.size(), 1, "the dropped copy");
                    dropped.forEach(Entity::discard);
                    a.getInventory().clearContent();
                })
                .thenExecuteAfter(1, () -> {
                    helper.assertValueEqual(ReliquaryService.use(a, abs), Offers.Outcome.REISSUED, "reissued at once");
                    helper.assertValueEqual(pages(a), 1, "held");
                    a.getInventory().clearContent(); // tossed and gone
                })
                .thenExecuteAfter(1, () -> {
                    helper.assertValueEqual(ReliquaryService.use(a, abs), Offers.Outcome.REISSUED, "reissued after a toss");
                    helper.assertValueEqual(pages(a), 1, "held again");
                })
                .thenSucceed();
    }

    /** A cancelled toss loses that copy; the claim stands and the next use gives it back. */
    private static void cancelledToss(GameTestHelper helper) {
        BlockPos abs = reliquary(helper, new BlockPos(1, 1, 1));
        ServerPlayer a = survivor(helper, new BlockPos(1, 1, 0));
        for (int i = 0; i < a.getInventory().getContainerSize(); i++) {
            a.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
        }
        tosser = a.getUUID();
        toss = Toss.CANCEL;
        Offers.Outcome first = ReliquaryService.use(a, abs);
        toss = Toss.NONE;
        helper.assertValueEqual(first, Offers.Outcome.GIVEN, "given");
        helper.assertValueEqual(claims(a).sites().size(), 1, "the claim stands");
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(abs).inflate(4), e -> e.getItem().is(ResearchItems.choirPage())).isEmpty(), "the copy is lost");
        a.getInventory().clearContent();
        helper.runAfterDelay(1, () -> {
            helper.assertValueEqual(ReliquaryService.use(a, abs), Offers.Outcome.REISSUED, "given back");
            helper.assertValueEqual(pages(a), 1, "held");
            helper.succeed();
        });
    }

    /** A throwing callback: logged, the claim kept, the hand-out goes on. */
    private static void throwingCallback(GameTestHelper helper) {
        BlockPos abs = reliquary(helper, new BlockPos(1, 1, 1));
        ServerPlayer a = survivor(helper, new BlockPos(1, 1, 0));
        for (int i = 0; i < a.getInventory().getContainerSize(); i++) {
            a.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
        }
        tosser = a.getUUID();
        toss = Toss.THROW;
        Offers.Outcome first;
        try {
            first = ReliquaryService.use(a, abs);
        } finally {
            toss = Toss.NONE;
        }
        helper.assertValueEqual(first, Offers.Outcome.GIVEN, "given despite the throw");
        helper.assertValueEqual(claims(a).sites().size(), 1, "the claim is kept");
        helper.assertTrue(said(a, "reliquary.vftv.given"), "the message still sent");
        helper.succeed();
    }

    /** Re-entry from a callback during a hand-out is refused busy, with message.vftv.busy. */
    private static void reentry(GameTestHelper helper) {
        BlockPos abs = reliquary(helper, new BlockPos(1, 1, 1));
        ServerPlayer a = survivor(helper, new BlockPos(1, 1, 0));
        for (int i = 0; i < a.getInventory().getContainerSize(); i++) {
            a.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
        }
        REENTRY.clear();
        tosser = a.getUUID();
        tossAt = abs;
        toss = Toss.REENTER;
        Offers.Outcome first;
        try {
            first = ReliquaryService.use(a, abs);
        } finally {
            toss = Toss.NONE;
        }
        helper.assertValueEqual(first, Offers.Outcome.GIVEN, "the outer use");
        helper.assertValueEqual(List.copyOf(REENTRY), List.of(Offers.Outcome.BUSY), "the inner use");
        helper.assertTrue(said(a, "message.vftv.busy"), "message.vftv.busy");
        helper.assertValueEqual(claims(a).sites().size(), 1, "one claim");
        helper.succeed();
    }

    /** Two uses in one tick: the second is attempt_cap, silent, and scans nothing. */
    private static void attemptCap(GameTestHelper helper) {
        BlockPos abs = reliquary(helper, new BlockPos(1, 1, 1));
        ServerPlayer a = survivor(helper, new BlockPos(1, 1, 0));
        use(a, abs);
        int before = lines(a).size();
        a.getInventory().clearContent();
        helper.assertValueEqual(ReliquaryService.use(a, abs), Offers.Outcome.ATTEMPT_CAP, "the second use");
        helper.assertValueEqual(lines(a).size(), before, "silent");
        helper.assertValueEqual(pages(a), 0, "nothing handed out");
        helper.succeed();
    }

    /** Claims survive a save and reload, and death. */
    private static void claimsPersist(GameTestHelper helper) {
        BlockPos abs = reliquary(helper, new BlockPos(1, 1, 1));
        ServerPlayer a = survivor(helper, new BlockPos(1, 1, 0));
        use(a, abs);
        ChoirClaims before = claims(a);
        helper.assertValueEqual(before.sites().size(), 1, "claimed");
        TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, a.registryAccess());
        a.saveWithoutId(out);
        ServerPlayer reloaded = ResearchTestSupport.player(helper);
        reloaded.load(TagValueInput.create(ProblemReporter.DISCARDING, a.registryAccess(), out.buildResult()));
        helper.assertValueEqual(claims(reloaded), before, "after save and reload");
        ServerPlayer respawned = ResearchTestSupport.player(helper);
        respawned.restoreFrom(a, false);
        helper.assertValueEqual(claims(respawned), before, "kept through death");
        helper.succeed();
    }

    /** An unreadable claims record refuses the use (row 2) and is kept as it is. */
    private static void unreadableClaims(GameTestHelper helper) {
        BlockPos abs = reliquary(helper, new BlockPos(1, 1, 1));
        ServerPlayer a = survivor(helper, new BlockPos(1, 1, 0));
        CompoundTag raw = new CompoundTag();
        raw.putInt("format", 9);
        ReliquaryService.claims().set(a, new Retained.Unreadable<>(raw, "a future format"));
        helper.assertValueEqual(ReliquaryService.use(a, abs), Offers.Outcome.UNREADABLE, "row 2");
        helper.assertTrue(said(a, "message.vftv.unreadable"), "message.vftv.unreadable");
        helper.assertTrue(ReliquaryService.claims().get(a) instanceof Retained.Unreadable<ChoirClaims>, "kept");
        helper.assertValueEqual(pages(a), 0, "nothing given");
        helper.succeed();
    }

    /** The block record, case by case: data-free initializes; every other bad record is unreadable. */
    private static void recordCases(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos fresh = reliquary(helper, new BlockPos(1, 1, 1));
        ReliquaryBlockEntity be = Objects.requireNonNull(level.getBlockEntity(fresh, WorldgenContent.reliquaryType()).orElse(null));
        helper.assertValueEqual(Objects.requireNonNull(be.record()), new Retained.Read<>(WorldgenCodecs.Reliquary.BUILT_IN), "a data-free placement initializes");
        ServerPlayer a = survivor(helper, new BlockPos(1, 1, 0));
        int x = 1;
        for (String bad : List.of("{site:\"pack:road\"}", "{format:1}", "{format:1,site:3}", "{format:2,site:\"vftv:choir_ruin\"}")) {
            x += 2;
            BlockPos abs = reliquary(helper, new BlockPos(x, 1, 1));
            ReliquaryBlockEntity b = Objects.requireNonNull(level.getBlockEntity(abs, WorldgenContent.reliquaryType()).orElse(null));
            CompoundTag tag;
            try {
                tag = net.minecraft.nbt.TagParser.parseCompoundFully(bad);
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                throw new IllegalStateException(e);
            }
            tag.putString("id", "vftv:reliquary");
            b.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
            helper.assertTrue(b.record() instanceof Retained.Unreadable<WorldgenCodecs.Reliquary>, bad + " is unreadable");
            CompoundTag saved = b.saveCustomOnly(level.registryAccess());
            for (String vanilla : List.of("components", "NeoForgeData", "neoforge:attachments")) {
                saved.remove(vanilla);
            }
            CompoundTag expected = tag.copy();
            expected.remove("id");
            helper.assertValueEqual(saved, expected, bad + " written back unchanged");
            if (x == 3) {
                helper.assertValueEqual(ReliquaryService.use(a, abs), Offers.Outcome.UNREADABLE, "row 2 for the block");
            }
        }
        helper.succeed();
    }

    /** A cancelled RightClickBlock changes nothing. */
    private static void cancelledUse(GameTestHelper helper) {
        BlockPos abs = reliquary(helper, new BlockPos(1, 1, 1));
        ServerPlayer a = survivor(helper, new BlockPos(1, 1, 0));
        cancelUse = a.getUUID();
        try {
            use(a, abs);
        } finally {
            cancelUse = new UUID(0, 0);
        }
        helper.assertValueEqual(claims(a).sites().size(), 0, "no claim");
        helper.assertValueEqual(pages(a), 0, "no page");
        helper.succeed();
    }

    /** Survival breaking, a creeper's explosion and a piston leave it in place. */
    private static void unmovable(GameTestHelper helper) {
        BlockPos rel = new BlockPos(2, 1, 2);
        BlockPos abs = reliquary(helper, rel);
        ServerPlayer a = survivor(helper, new BlockPos(2, 1, 1));
        helper.assertFalse(a.gameMode.destroyBlock(abs), "survival can't break it");
        helper.getLevel().explode(null, abs.getX() + 0.5, abs.getY() + 1, abs.getZ() - 1.5, 3.0F, net.minecraft.world.level.Level.ExplosionInteraction.MOB);
        helper.assertBlockPresent(WorldgenContent.reliquary(), rel);
        helper.setBlock(new BlockPos(0, 1, 2), Blocks.PISTON.defaultBlockState().setValue(net.minecraft.world.level.block.piston.PistonBaseBlock.FACING, Direction.EAST));
        helper.setBlock(new BlockPos(1, 1, 2), Blocks.AIR);
        helper.setBlock(new BlockPos(0, 2, 2), Blocks.REDSTONE_BLOCK);
        helper.runAfterDelay(4, () -> {
            helper.assertBlockPresent(WorldgenContent.reliquary(), rel);
            helper.succeed();
        });
    }

    // --- the structure ------------------------------------------------------------------------

    /** /place's steps: the envelope, the node minted and rolled, chests, Answerers with homes. */
    private static void structurePlace(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = far(helper, 0);
        StructureStart start = placeRuin(level, at);
        StructureStart second = placeRuin(level, at.offset(256, 0, 0));
        BoundingBox box = start.getBoundingBox();
        ChunkPos chunk = ChunkPos.containing(at);
        int cx = chunk.getMinBlockX();
        int cz = chunk.getMinBlockZ();
        helper.assertTrue(start.getPieces().size() <= 13, "at most 13 pieces: " + start.getPieces().size());
        helper.assertTrue(box.minX() >= cx - 40 - 12 && box.maxX() <= cx + 40 + 12 && box.minZ() >= cz - 40 - 12 && box.maxZ() <= cz + 40 + 12, "within the envelope: " + box);
        List<BlockPos> reliquaries = find(level, box, WorldgenContent.reliquary());
        helper.assertValueEqual(reliquaries.size(), 1, "one Reliquary");
        List<BlockPos> nodes = find(level, box, VisContent.node());
        helper.assertValueEqual(nodes.size(), 1, "one node");
        List<Answerer> mobs = level.getEntitiesOfClass(Answerer.class, AABB.of(box).inflate(2));
        long cantors = mobs.stream().filter(Answerer::isCantor).count();
        helper.assertValueEqual(cantors, 1L, "one Cantor");
        helper.assertTrue(mobs.size() >= 2 && mobs.size() <= 10, "2 to 10 Answerers: " + mobs.size());
        for (Answerer m : mobs) {
            helper.assertTrue(m.hasHome() && m.getHomeRadius() == 24, "a home of 24");
            helper.assertValueEqual(m.getHealth(), m.getMaxHealth(), "full health");
        }
        BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()).forEach(p -> {
            if (level.getBlockEntity(p) instanceof ChestBlockEntity chest) {
                helper.assertValueEqual(Objects.requireNonNull(chest.getLootTable()), ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath("vftv", "chests/choir_ruin")), "the chest's table");
            }
        });
        BlockPos node = nodes.getFirst();
        BlockPos node2 = find(level, second.getBoundingBox(), VisContent.node()).getFirst();
        helper.runAfterDelay(3, () -> {
            NodeState state = Objects.requireNonNull(((NodeBlockEntity) Objects.requireNonNull(level.getBlockEntity(node))).state());
            NodeState state2 = Objects.requireNonNull(((NodeBlockEntity) Objects.requireNonNull(level.getBlockEntity(node2))).state());
            helper.assertTrue(state.id() != 0 && state2.id() != 0 && state.id() != state2.id(), "two distinct ids");
            helper.assertValueEqual(state.origin(), NodeState.Origin.STRUCTURE, "origin");
            NodeRoll.Result r = NodeRoll.roll(level.getSeed(), node.asLong(), new int[] {0, 60, 40},
                    List.of(new NodeRoll.Range("vftv:aer", 16, 32), new NodeRoll.Range("vftv:ordo", 24, 48), new NodeRoll.Range("vftv:sonus", 4, 12), new NodeRoll.Range("vftv:vacuos", 4, 12)));
            helper.assertValueEqual(state.clarity(), NodeState.Clarity.byIndex(r.clarity()), "the rolled clarity");
            helper.assertValueEqual(state.aspects().stream().map(NodeState.NodeAspect::max).toList(), r.maxes(), "the rolled maxes");
            helper.assertValueEqual(state.aspects().stream().map(NodeState.NodeAspect::current).toList(), r.maxes(), "current = max");
            helper.assertValueEqual(state.type(), WorldgenKeys.CHOIR_NODE_TYPE.identifier(), "the type");
            helper.assertValueEqual(state.cultivation(), NodeState.Cultivation.NONE, "every other field as pinned");
            helper.succeed();
        });
    }

    /** An existing node under a template block is kept; an obstructed belfry places no node. */
    private static void protects(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = far(helper, 1);
        load(level, new BoundingBox(at).inflatedBy(32));
        StructureTemplate template = chancel(level);
        StructurePlaceSettings settings = settings(level);
        BlockPos nodeAt = template.filterBlocks(at, settings, VisContent.node()).getFirst().pos();
        BlockPos floor = at.offset(5, 0, 5);
        level.setBlock(nodeAt, Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(floor, VisContent.node().defaultBlockState(), 3);
        template.placeInWorld(level, at, at, settings, level.getRandom(), 2);
        helper.assertTrue(level.getBlockState(floor).is(VisContent.node()), "the existing node is kept");
        helper.assertTrue(level.getBlockState(nodeAt).is(Blocks.STONE), "the obstructed belfry keeps its terrain");
        helper.assertValueEqual(find(level, BoundingBox.fromCorners(at, at.offset(22, 25, 22)), VisContent.node()).size(), 1, "only the existing node");
        helper.succeed();
    }

    /** A third structure node in one 64 × 64 cell is removed with a warning. */
    private static void thirdNode(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = far(helper, 2);
        BlockPos cell = new BlockPos(Math.floorDiv(base.getX(), 64) * 64 + 8, base.getY() + 4, Math.floorDiv(base.getZ(), 64) * 64 + 8);
        load(level, new BoundingBox(cell).inflatedBy(40));
        List<BlockPos> at = List.of(cell, cell.offset(10, 0, 10), cell.offset(20, 0, 20));
        for (BlockPos p : at) {
            level.setBlock(p, VisContent.node().defaultBlockState(), 3);
            NodeBlockEntity be = (NodeBlockEntity) Objects.requireNonNull(level.getBlockEntity(p));
            be.setState(new NodeState(0, NodeState.Origin.STRUCTURE, WorldgenKeys.CHOIR_NODE_TYPE.identifier(), NodeState.Clarity.STEADY,
                    List.of(new NodeState.NodeAspect(Identifier.parse("vftv:aer"), 16, 16)), new NodeState.RegenClock(0, 1, 1), NodeState.Cultivation.NONE));
            com.andrewbrogoff.vftv.core.vis.NodeService.queue(level, ChunkPos.containing(p));
        }
        helper.runAfterDelay(3, () -> {
            long kept = at.stream().filter(p -> level.getBlockState(p).is(VisContent.node())).count();
            helper.assertValueEqual(kept, 2L, "two kept, the third removed");
            helper.succeed();
        });
    }

    /** A missing type keeps the complete template record unrolled. */
    private static void missingType(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StructureTemplate template = chancel(level);
        StructureTemplate.StructureBlockInfo info =
                template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), VisContent.node()).getFirst();
        CompoundTag nbt = Objects.requireNonNull(info.nbt()).copy();
        nbt.getCompoundOrEmpty("node").putString("type", "vftv:removed_by_a_pack");
        BlockPos target = far(helper, 3).above(40);
        load(level, new BoundingBox(target));
        var processor = com.andrewbrogoff.vftv.core.worldgen.RollNodeProcessor.INSTANCE;
        var out = processor.process(level, target, target, info, new StructureTemplate.StructureBlockInfo(target, info.state(), nbt), new StructurePlaceSettings(), template);
        helper.assertValueEqual(Objects.requireNonNull(Objects.requireNonNull(out).nbt()), nbt, "unrolled, field for field");
        helper.succeed();
    }

    // --- the Answerers ------------------------------------------------------------------------

    /** Persistent beyond 128 blocks: never despawned, and they don't count toward the cap. */
    private static void persistent(GameTestHelper helper) {
        Answerer a = answerer(helper, new BlockPos(1, 1, 1), false);
        a.checkDespawn();
        helper.assertTrue(a.isAlive() && a.requiresCustomPersistence(), "kept");
        helper.succeed();
    }

    /** Return home, and drop a target that leaves home or range. */
    private static void homeAndRange(GameTestHelper helper) {
        Answerer a = answerer(helper, new BlockPos(1, 1, 1), false);
        ServerPlayer p = survivor(helper, new BlockPos(3, 1, 1));
        helper.assertTrue(Answerer.validTarget(a, p), "valid at home");
        a.setTarget(p);
        Vec3 out = Vec3.atBottomCenterOf(a.getHomePosition().offset(30, 0, 0));
        p.setPos(out.x, out.y, out.z);
        helper.assertFalse(Answerer.validTarget(a, p), "outside home and range");
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(a.getTarget() == null, "the target dropped");
            a.setHomeTo(a.blockPosition().offset(30, 0, 0), 4);
            helper.runAfterDelay(20, () -> {
                helper.assertTrue(a.getNavigation().isInProgress() || a.isWithinHome(), "walking home");
                helper.succeed();
            });
        });
    }

    /** Hurt by a zombie, they change nothing; set on a zombie, the target is refused. */
    private static void ignoresZombie(GameTestHelper helper) {
        Answerer a = answerer(helper, new BlockPos(1, 1, 1), false);
        Zombie z = helper.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, new BlockPos(3, 1, 1));
        a.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(z), 1.0F);
        a.setTarget(z);
        helper.assertTrue(a.getTarget() == null, "no target");
        z.discard();
        helper.succeed();
    }

    /** Retaliation by melee and by arrow, with the player valid and inside home but beyond range. */
    private static void retaliation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Answerer a = answerer(helper, new BlockPos(1, 1, 1), false);
        Answerer b = answerer(helper, new BlockPos(1, 1, 3), false);
        ServerPlayer p = survivor(helper, new BlockPos(3, 1, 1));
        a.hurtServer(level, level.damageSources().playerAttack(p), 1.0F);
        helper.assertTrue(Objects.equals(p, a.getTarget()), "melee: targets the player");
        helper.assertTrue(Objects.equals(p, b.getTarget()), "and alerts its neighbour");
        a.setTarget(null);
        b.setTarget(null);
        Arrow arrow = new Arrow(level, p, new ItemStack(Items.ARROW), null);
        a.invulnerableTime = 0;
        a.hurtServer(level, level.damageSources().arrow(arrow, p), 1.0F);
        helper.assertTrue(Objects.equals(p, a.getTarget()), "arrow: targets the owner");
        a.setTarget(null);
        b.setTarget(null);
        a.setHomeTo(a.blockPosition(), 48);
        Vec3 far = a.position().add(30, 0, 0);
        p.setPos(far.x, far.y, far.z);
        a.invulnerableTime = 0;
        a.hurtServer(level, level.damageSources().playerAttack(p), 1.0F);
        helper.assertTrue(a.getTarget() == null && b.getTarget() == null, "beyond range: no target, no alert");
        helper.succeed();
    }

    /** Hitting the Cantor alerts Answerers for which the player is valid, and no others. */
    private static void alerts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Answerer cantor = answerer(helper, new BlockPos(1, 1, 1), true);
        Answerer near = answerer(helper, new BlockPos(1, 1, 3), false);
        Answerer elsewhere = answerer(helper, new BlockPos(3, 1, 3), false);
        elsewhere.setHomeTo(elsewhere.blockPosition().offset(0, 0, 60), 8);
        Answerer outOfRange = answerer(helper, new BlockPos(3, 1, 1), false);
        Objects.requireNonNull(outOfRange.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE)).setBaseValue(1.0);
        ServerPlayer p = survivor(helper, new BlockPos(1, 1, 5));
        cantor.hurtServer(level, level.damageSources().playerAttack(p), 1.0F);
        helper.assertTrue(Objects.equals(p, cantor.getTarget()), "the Cantor retaliates");
        helper.assertTrue(Objects.equals(p, near.getTarget()), "a valid Answerer is alerted");
        helper.assertTrue(elsewhere.getTarget() == null, "home doesn't contain the player");
        helper.assertTrue(outOfRange.getTarget() == null, "home contains the player, but out of range");
        helper.succeed();
    }

    /** The chant: concurrent with melee, at most max_targets within the radius, a full interval after a reload, a lowered radius at the next chant. */
    private static void chant(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        WorldgenService.applyTuning(DataResult.success(WorldgenService.parse("{\"cantor.interval\": 20, \"cantor.max_targets\": 3}").getOrThrow()), true);
        Answerer cantor = answerer(helper, new BlockPos(2, 1, 2), true);
        List<Answerer> crowd = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            crowd.add(answerer(helper, new BlockPos(1 + i, 1, 4), false));
        }
        Answerer distant = answerer(helper, new BlockPos(2, 1, 2), false);
        distant.teleportTo(distant.getX() + 12, distant.getY(), distant.getZ());
        ServerPlayer p = survivor(helper, new BlockPos(2, 1, 3));
        cantor.setTarget(p);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(cantor.chanting(), "it chants"))
                .thenExecute(() -> {
                    long given = crowd.stream().filter(m -> m.hasEffect(MobEffects.RESISTANCE)).count()
                            + (cantor.hasEffect(MobEffects.RESISTANCE) ? 1 : 0);
                    helper.assertValueEqual(given, 3L, "max_targets, the Cantor included");
                    helper.assertFalse(distant.hasEffect(MobEffects.RESISTANCE), "outside the radius");
                    helper.assertTrue(Objects.equals(p, cantor.getTarget()), "still fighting: the chant runs beside melee");
                    crowd.forEach(m -> m.removeEffect(MobEffects.RESISTANCE));
                    cantor.removeEffect(MobEffects.RESISTANCE);
                    WorldgenService.applyTuning(DataResult.success(WorldgenService.parse("{\"cantor.interval\": 20, \"cantor.max_targets\": 3, \"cantor.radius\": 2}").getOrThrow()), true);
                })
                .thenWaitUntil(() -> helper.assertTrue(cantor.hasEffect(MobEffects.RESISTANCE), "chanted again"))
                .thenExecute(() -> {
                    long far = crowd.stream().filter(m -> m.distanceTo(cantor) > 2.5 && m.hasEffect(MobEffects.RESISTANCE)).count();
                    helper.assertValueEqual(far, 0L, "the lowered radius applies at the next chant");
                    Cantor reloaded = (Cantor) Objects.requireNonNull(WorldgenContent.cantor().create(level, EntitySpawnReason.LOAD));
                    reloaded.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), save(cantor)));
                    helper.assertFalse(reloaded.chanting(), "chanting is transient");
                    helper.assertValueEqual(reloaded.nextChant(), Long.MIN_VALUE, "no schedule until it ticks");
                    WorldgenService.applyTuning(DataResult.success(WorldgenTuning.DEFAULTS), true);
                })
                .thenSucceed();
    }

    /** Peaceful: placed and kept; no target even when hit; a hit on a player deals 0. */
    private static void peaceful(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        Difficulty before = level.getDifficulty();
        Answerer a = answerer(helper, new BlockPos(1, 1, 1), false);
        ServerPlayer p = survivor(helper, new BlockPos(2, 1, 1));
        BlockPos at = far(helper, 4);
        load(level, new BoundingBox(at).inflatedBy(32));
        try {
            server.setDifficulty(Difficulty.PEACEFUL, true);
            chancel(level).placeInWorld(level, at, at, settings(level), level.getRandom(), 2);
            helper.assertValueEqual(level.getEntitiesOfClass(Answerer.class, new AABB(at).inflate(32)).size(), 2, "a ruin placed in peaceful has its Answerers");
            a.checkDespawn();
            helper.assertTrue(a.isAlive(), "kept through a switch to peaceful");
            a.hurtServer(level, level.damageSources().playerAttack(p), 1.0F);
            helper.assertTrue(a.getTarget() == null, "no target in peaceful, even when hit");
            float health = p.getHealth();
            a.doHurtTarget(level, p);
            helper.assertValueEqual(p.getHealth(), health, "a hit deals 0");
        } finally {
            server.setDifficulty(before, true);
        }
        helper.assertTrue(a.isAlive(), "and back");
        level.getEntitiesOfClass(Answerer.class, new AABB(at).inflate(32)).forEach(Entity::discard);
        helper.succeed();
    }

    /** The Cantor drops its book. */
    private static void drops(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Answerer c = answerer(helper, new BlockPos(1, 1, 1), true);
        ServerPlayer p = survivor(helper, new BlockPos(2, 1, 1));
        c.hurtServer(level, level.damageSources().playerAttack(p), 1000.0F);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(!level.getEntitiesOfClass(ItemEntity.class, new AABB(c.blockPosition()).inflate(3), e -> e.getItem().is(Items.BOOK)).isEmpty(), "a book");
            helper.succeed();
        });
    }

    // --- the finds ----------------------------------------------------------------------------

    private static boolean done(ServerPlayer player, String find) {
        AdvancementHolder holder = Objects.requireNonNull(player.level().getServer().getAdvancements().get(Identifier.fromNamespaceAndPath("vftv", "find/" + find)));
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
        return progress.isDone();
    }

    @SuppressWarnings("removal") // the only helper that puts a mock player on the player list
    private static ServerPlayer online(GameTestHelper helper) {
        return helper.makeMockServerPlayerInLevel();
    }

    /** Dripstone, a page, and a kill by sword and by arrow each grant their find. */
    private static void finds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer p = online(helper);
        p.getInventory().add(new ItemStack(Items.POINTED_DRIPSTONE));
        ItemStack page = new ItemStack(ResearchItems.choirPage());
        page.set(ResearchItems.page(), PAGE);
        p.getInventory().add(page);
        Answerer a = answerer(helper, new BlockPos(1, 1, 1), false);
        a.hurtServer(level, level.damageSources().playerAttack(p), 1000.0F);
        ServerPlayer q = online(helper);
        Answerer b = answerer(helper, new BlockPos(3, 1, 1), true);
        Arrow arrow = new Arrow(level, q, new ItemStack(Items.ARROW), null);
        b.hurtServer(level, level.damageSources().arrow(arrow, q), 1000.0F);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(done(p, "distillation"), "find/distillation");
            helper.assertTrue(done(p, "ruins"), "find/ruins");
            helper.assertTrue(done(p, "answerers"), "find/answerers by sword");
            helper.assertTrue(done(q, "answerers"), "find/answerers by arrow");
            p.discard();
            q.discard();
            helper.succeed();
        });
    }

    /** After research's reset, moving the dripstone stack relatches; an unrelated slot doesn't. */
    private static void relatch(GameTestHelper helper) {
        ServerPlayer p = online(helper);
        p.getInventory().setItem(0, new ItemStack(Items.POINTED_DRIPSTONE));
        helper.runAfterDelay(1, () -> {
            helper.assertTrue(done(p, "distillation"), "latched");
            Resolver.reset(p);
            helper.assertFalse(done(p, "distillation"), "revoked by reset");
            p.getInventory().setItem(5, new ItemStack(Items.STONE));
            helper.runAfterDelay(1, () -> {
                helper.assertFalse(done(p, "distillation"), "an unrelated slot doesn't relatch");
                ItemStack held = p.getInventory().removeItemNoUpdate(0);
                p.getInventory().setItem(1, held);
                helper.runAfterDelay(1, () -> {
                    helper.assertTrue(done(p, "distillation"), "moving the stack relatches");
                    p.discard();
                    helper.succeed();
                });
            });
        });
    }

    /** The kill predicate decodes, matches an Answerer and a Cantor, and not a zombie. */
    private static void killPredicate(GameTestHelper helper) {
        ServerPlayer p = online(helper);
        AdvancementHolder holder = Objects.requireNonNull(helper.getLevel().getServer().getAdvancements().get(Identifier.fromNamespaceAndPath("vftv", "find/answerers")));
        var trigger = (net.minecraft.advancements.triggers.KilledTrigger.TriggerInstance) Objects.requireNonNull(holder.value().criteria().get("killed")).triggerInstance();
        var predicate = trigger.entity().orElseThrow();
        Answerer a = answerer(helper, new BlockPos(1, 1, 1), false);
        Answerer c = answerer(helper, new BlockPos(2, 1, 1), true);
        Zombie z = helper.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, new BlockPos(3, 1, 1));
        helper.assertTrue(predicate.matches(net.minecraft.advancements.predicates.entity.EntityPredicate.createContext(p, a)), "an Answerer");
        helper.assertTrue(predicate.matches(net.minecraft.advancements.predicates.entity.EntityPredicate.createContext(p, c)), "a Cantor");
        helper.assertFalse(predicate.matches(net.minecraft.advancements.predicates.entity.EntityPredicate.createContext(p, z)), "not a zombie");
        z.discard();
        p.discard();
        helper.succeed();
    }

    // --- the place query ----------------------------------------------------------------------

    /** True in a referenced chunk at any y, false one chunk outside and at an unloaded chunk; allocates nothing. */
    private static void placeQuery(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(new BlockPos(1, 1, 1));
        LevelChunk chunk = level.getChunkAt(abs);
        Structure ruin = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValueOrThrow(WorldgenKeys.CHOIR_RUIN);
        BlockPos outside = abs.offset(0, 0, 16 * 40);
        level.getChunkAt(outside);
        helper.assertFalse(ChoirSitePlaceQuery.INSTANCE.test(level, outside), "false with no reference");
        chunk.addReferenceForStructure(ruin, ChunkPos.containing(abs).pack());
        try {
            helper.assertTrue(ChoirSitePlaceQuery.INSTANCE.test(level, abs.atY(level.getMaxY())), "true at the top");
            helper.assertTrue(ChoirSitePlaceQuery.INSTANCE.test(level, abs.atY(level.getMinY())), "true at the bottom");
            helper.assertTrue(com.andrewbrogoff.vftv.core.warp.WarpService.inVoidTouchedPlace(level, abs), "a Void-touched place");
            BlockPos unloaded = new BlockPos(29_000_000, 64, 29_000_000);
            helper.assertFalse(ChoirSitePlaceQuery.INSTANCE.test(level, unloaded), "false at an unloaded chunk");
            helper.assertTrue(level.getChunkSource().getChunkNow(unloaded.getX() >> 4, unloaded.getZ() >> 4) == null, "and nothing was loaded");
            var threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
            long id = Thread.currentThread().threadId();
            boolean zero = false;
            for (int round = 0; round < 50 && !zero; round++) {
                long before = threads.getThreadAllocatedBytes(id);
                for (int i = 0; i < 1000; i++) {
                    ChoirSitePlaceQuery.INSTANCE.test(level, abs);
                }
                zero = threads.getThreadAllocatedBytes(id) - before == 0;
            }
            helper.assertTrue(zero, "zero allocation");
        } finally {
            Map<Structure, it.unimi.dsi.fastutil.longs.LongSet> refs = new java.util.HashMap<>(chunk.getAllReferences());
            refs.remove(ruin);
            chunk.setAllReferences(refs);
        }
        helper.assertFalse(ChoirSitePlaceQuery.INSTANCE.test(level, abs), "false again");
        helper.succeed();
    }

    /** An invalid tuning file is refused at open and keeps the previous record on reload. */
    private static void tuningReload(GameTestHelper helper) {
        boolean refused = false;
        try {
            WorldgenService.applyTuning(WorldgenService.parse("{\"cantor.radius\": 99}"), false);
        } catch (IllegalStateException e) {
            refused = true;
        }
        helper.assertTrue(refused, "refused at open");
        WorldgenService.applyTuning(WorldgenService.parse("{\"cantor.radius\": 99}"), true);
        helper.assertValueEqual(WorldgenService.tuning(), WorldgenTuning.DEFAULTS, "kept on reload");
        helper.succeed();
    }
}
