package com.andrewbrogoff.vftv.gametest;

import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.api.crucible.CrucibleKeys;
import com.andrewbrogoff.vftv.api.crucible.CrucibleLimits;
import com.andrewbrogoff.vftv.api.crucible.CruciblePayload;
import com.andrewbrogoff.vftv.api.essentia.SpillCause;
import com.andrewbrogoff.vftv.api.neoforge.essentia.EssentiaCapabilities;
import com.andrewbrogoff.vftv.api.neoforge.essentia.EssentiaResource;
import com.andrewbrogoff.vftv.api.research.Research;
import com.andrewbrogoff.vftv.api.research.ResearchKeys;
import com.andrewbrogoff.vftv.core.crucible.CrucibleBlock;
import com.andrewbrogoff.vftv.core.crucible.CrucibleBlockEntity;
import com.andrewbrogoff.vftv.core.crucible.CrucibleContent;
import com.andrewbrogoff.vftv.core.crucible.CrucibleHooks;
import com.andrewbrogoff.vftv.core.crucible.CrucibleRecipe;
import com.andrewbrogoff.vftv.core.crucible.CrucibleRecipes;
import com.andrewbrogoff.vftv.core.crucible.CrucibleService;
import com.andrewbrogoff.vftv.core.text.StatusLines;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * The crucible feature's GameTests. Name each test {@code crucible_<what>}, and list its id {@code
 * vftv:crucible_<what>} in {@code src/gameTest/expected/crucible.txt}.
 */
final class CrucibleGameTests {
    private static final BlockPos HEAT = new BlockPos(1, 1, 1);
    private static final BlockPos AT = new BlockPos(1, 2, 1);
    private static final ResourceKey<Research> BRASS =
            ResourceKey.create(
                    ResearchKeys.REGISTRY,
                    Identifier.fromNamespaceAndPath("vftv", "resonant_brass"));

    /** Crucibles whose results' spawns are cancelled (the provisional-spawn test). */
    private static final java.util.Set<BlockPos> CANCEL_AT = new java.util.HashSet<>();

    private CrucibleGameTests() {}

    static void register(Map<String, Consumer<GameTestHelper>> tests, IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(
                EntityJoinLevelEvent.class,
                event -> {
                    if (event.getEntity() instanceof ItemEntity item
                            && CANCEL_AT.contains(item.blockPosition().below())) {
                        event.setCanceled(true);
                    }
                });
        tests.put("crucible_registered", CrucibleGameTests::registered);
        tests.put("crucible_fill_and_empty", CrucibleGameTests::fillAndEmpty);
        tests.put("crucible_heat_sources", CrucibleGameTests::heatSources);
        tests.put("crucible_cold_ignores_items", CrucibleGameTests::coldIgnores);
        tests.put("crucible_dissolves_stack", CrucibleGameTests::dissolvesStack);
        tests.put("crucible_full_partial", CrucibleGameTests::fullPartial);
        tests.put("crucible_nothing_refused", CrucibleGameTests::nothingRefused);
        tests.put("crucible_protected_refused", CrucibleGameTests::protectedRefused);
        tests.put("crucible_learned_thrower_crafts", CrucibleGameTests::learnedCrafts);
        tests.put("crucible_unlearned_dissolves", CrucibleGameTests::unlearnedDissolves);
        tests.put("crucible_short_refused", CrucibleGameTests::shortRefused);
        tests.put("crucible_sneak_drop_dissolves", CrucibleGameTests::sneakDrop);
        tests.put("crucible_cancelled_spawn_errors", CrucibleGameTests::cancelledSpawn);
        tests.put("crucible_throwing_listener_stands", CrucibleGameTests::throwingListener);
        tests.put("crucible_empty_spills_once", CrucibleGameTests::emptySpills);
        tests.put("crucible_break_spills_removed", CrucibleGameTests::breakSpills);
        tests.put("crucible_extract_transactions", CrucibleGameTests::extractTransactions);
        tests.put("crucible_busy_refuses_bucket", CrucibleGameTests::busyRefusesBucket);
        tests.put("crucible_removed_while_busy", CrucibleGameTests::removedWhileBusy);
        tests.put("crucible_persistence", CrucibleGameTests::persistence);
        tests.put("crucible_unreadable_record", CrucibleGameTests::unreadableRecord);
        tests.put("crucible_recipe_codec", CrucibleGameTests::recipeCodec);
        tests.put("crucible_recipe_cap", CrucibleGameTests::recipeCap);
        tests.put("crucible_sync_sends_recipes", CrucibleGameTests::syncSendsRecipes);
        tests.put("crucible_flood_sixteen_per_step", CrucibleGameTests::flood);
        tests.put("crucible_refresh_queue", CrucibleGameTests::refreshQueue);
    }

    // --- Fixtures ------------------------------------------------------------------------

    private static BlockState state(boolean water, boolean boiling) {
        return CrucibleContent.crucible()
                .defaultBlockState()
                .setValue(CrucibleBlock.WATER, water)
                .setValue(CrucibleBlock.BOILING, boiling);
    }

    /** A boiling crucible over magma. */
    private static CrucibleBlockEntity boiling(GameTestHelper helper) {
        helper.setBlock(HEAT, Blocks.MAGMA_BLOCK);
        helper.setBlock(AT, state(true, true));
        return be(helper);
    }

    private static CrucibleBlockEntity be(GameTestHelper helper) {
        BlockEntity be = helper.getLevel().getBlockEntity(helper.absolutePos(AT));
        if (!(be instanceof CrucibleBlockEntity crucible)) {
            throw helper.assertionException("no crucible block entity");
        }
        return crucible;
    }

    private static void setPool(CrucibleBlockEntity be, Object... kv) {
        Map<String, Integer> pool = new TreeMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            pool.put((String) kv[i], (Integer) kv[i + 1]);
        }
        be.pool().restore(pool);
    }

    /** An item entity resting inside the crucible, thrown by {@code thrower} if given. */
    private static ItemEntity drop(
            GameTestHelper helper, ItemStack stack, @Nullable Player thrower) {
        BlockPos pos = helper.absolutePos(AT);
        ItemEntity item =
                new ItemEntity(
                        helper.getLevel(),
                        pos.getX() + 0.5,
                        pos.getY() + 0.3,
                        pos.getZ() + 0.5,
                        stack);
        item.setDeltaMovement(Vec3.ZERO);
        if (thrower != null) {
            item.setThrower(thrower);
        }
        helper.getLevel().addFreshEntity(item);
        return item;
    }

    private static List<ItemEntity> items(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(AT);
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3));
    }

    private static InteractionResult use(GameTestHelper helper, Player player, ItemStack held) {
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        BlockPos pos = helper.absolutePos(AT);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        return helper.getLevel()
                .getBlockState(pos)
                .useItemOn(held, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
    }

    /**
     * Records every payload and craft notification, delegating to the platform's hooks. Installed
     * once and shared: tests run side by side, so each reads only its own crucible's records.
     */
    private static final class Recording implements CrucibleHooks {
        final CrucibleHooks inner;
        final List<CruciblePayload> posts = new ArrayList<>();
        final List<Player> crafters = new ArrayList<>();
        final java.util.Set<BlockPos> throwAt = new java.util.HashSet<>();

        Recording(CrucibleHooks inner) {
            this.inner = inner;
        }

        @Override
        public void post(CruciblePayload payload) {
            posts.add(payload);
            if (throwAt.contains(pos(payload))) {
                throw new IllegalStateException("a test listener throws");
            }
            inner.post(payload);
        }

        @Override
        public void crafted(Player player, ItemStack result, Container catalyst) {
            crafters.add(player);
            inner.crafted(player, result, catalyst);
        }

        @Override
        public boolean refused(ItemEntity entity) {
            return inner.refused(entity);
        }

        @Override
        public void markRefused(ItemEntity entity) {
            inner.markRefused(entity);
        }

        @Override
        public boolean dissolve(ItemEntity entity) {
            return inner.dissolve(entity);
        }

        @Override
        public void markDissolve(ItemEntity entity) {
            inner.markDissolve(entity);
        }

        @Override
        public void invalidate(net.minecraft.world.level.Level level, BlockPos pos) {
            inner.invalidate(level, pos);
        }

        private static BlockPos pos(CruciblePayload payload) {
            return switch (payload) {
                case CruciblePayload.Dissolved d -> d.pos();
                case CruciblePayload.Crafted c -> c.pos();
                case CruciblePayload.Spilled s -> s.pos();
            };
        }
    }

    /** One test's view of the shared recorder: its crucible's records only. */
    private record Events(Recording recording, BlockPos pos) {
        <T extends CruciblePayload> List<T> of(Class<T> type) {
            return recording.posts.stream()
                    .filter(p -> Recording.pos(p).equals(pos))
                    .filter(type::isInstance)
                    .map(type::cast)
                    .toList();
        }

        long crafted(Player player) {
            return recording.crafters.stream().filter(player::equals).count();
        }
    }

    private static @Nullable Recording recording;

    /** The shared recorder, installed over the platform's hooks on first use. */
    private static Events record(GameTestHelper helper) {
        Recording r = recording;
        if (r == null) {
            r = new Recording(CrucibleService.hooks());
            CrucibleService.install(r);
            recording = r;
        }
        return new Events(r, helper.absolutePos(AT));
    }

    private static final List<Map.Entry<ServerPlayer, Component>> LINES = new ArrayList<>();
    private static boolean capturing;

    /** The status lines sent to {@code player}, captured by a shared sender. */
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
        return new java.util.AbstractList<>() {
            private List<Component> mine() {
                return LINES.stream()
                        .filter(e -> e.getKey().equals(player))
                        .map(Map.Entry::getValue)
                        .toList();
            }

            @Override
            public Component get(int index) {
                return mine().get(index);
            }

            @Override
            public int size() {
                return mine().size();
            }
        };
    }

    private static boolean says(List<Component> lines, String key) {
        return lines.stream()
                .anyMatch(
                        l ->
                                l.getContents()
                                                instanceof
                                                net.minecraft.network.chat.contents
                                                                .TranslatableContents
                                                        t
                                        && t.getKey().equals(key));
    }

    /** {@code vftv_gametest:crucible_iron}: iron ingot, gated by the Commonplace Book, Aer 2. */
    private static void testRecipes(GameTestHelper helper) {
        helper.assertTrue(
                CrucibleRecipes.server()
                                .get(
                                        Identifier.fromNamespaceAndPath(
                                                "vftv_gametest", "crucible_iron"))
                        != null,
                "the test recipe loaded");
    }

    private static RecipeManager manager(MinecraftServer server, int count) {
        CrucibleRecipe recipe =
                new CrucibleRecipe(
                        ResearchKeys.COMMONPLACE_BOOK,
                        Ingredient.of(Items.IRON_INGOT),
                        Map.of(AspectKeys.AER, 2),
                        new ItemStackTemplate(Items.GOLD_INGOT));
        List<RecipeHolder<?>> holders = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            holders.add(
                    new RecipeHolder<>(
                            ResourceKey.create(
                                    Registries.RECIPE,
                                    Identifier.fromNamespaceAndPath(
                                            "vftv_gametest", "crucible/" + i)),
                            recipe));
        }
        RecipeMap map = RecipeMap.create(holders);
        HolderLookup.Provider registries = server.registryAccess();
        return new RecipeManager(registries) {
            @Override
            public RecipeMap recipeMap() {
                return map;
            }
        };
    }

    @SuppressWarnings("removal") // the only helper that puts a mock player on the player list
    private static ServerPlayer onlinePlayer(GameTestHelper helper) {
        // Creative, like every mock player in a level; it stays online for the run.
        return helper.makeMockServerPlayerInLevel();
    }

    // --- Tests ---------------------------------------------------------------------------

    /** The content holders, recipe type, display type, sounds and tags are registered. */
    private static void registered(GameTestHelper helper) {
        helper.assertTrue(
                CrucibleRecipe.TYPE.equals(
                        BuiltInRegistries.RECIPE_TYPE.getValue(CrucibleRecipe.ID)),
                "the recipe type");
        helper.assertTrue(
                CrucibleRecipe.SERIALIZER.equals(
                        BuiltInRegistries.RECIPE_SERIALIZER.getValue(CrucibleRecipe.ID)),
                "the serializer");
        helper.assertTrue(
                BuiltInRegistries.RECIPE_DISPLAY.containsKey(CrucibleRecipe.ID), "the display");
        CrucibleContent.SOUNDS.forEach(
                s -> helper.assertTrue(BuiltInRegistries.SOUND_EVENT.containsKey(s), s.toString()));
        helper.assertTrue(
                Blocks.MAGMA_BLOCK.defaultBlockState().is(CrucibleKeys.CRUCIBLE_HEAT),
                "magma heats");
        helper.assertTrue(
                CrucibleContent.crucibleItem().getDefaultMaxStackSize() == 64, "stacks to 64");
        helper.succeed();
    }

    /** Fill, a second fill, empty, and an empty on a dry crucible. */
    private static void fillAndEmpty(GameTestHelper helper) {
        helper.setBlock(AT, state(false, false));
        ServerPlayer player = ResearchTestSupport.player(helper);
        helper.assertValueEqual(
                use(helper, player, new ItemStack(Items.WATER_BUCKET)),
                InteractionResult.SUCCESS,
                "fill");
        helper.assertTrue(helper.getBlockState(AT).getValue(CrucibleBlock.WATER), "has water");
        helper.assertTrue(player.getMainHandItem().is(Items.BUCKET), "the bucket is emptied");
        helper.assertValueEqual(
                use(helper, player, new ItemStack(Items.WATER_BUCKET)),
                InteractionResult.CONSUME,
                "a second fill");
        helper.assertTrue(player.getMainHandItem().is(Items.WATER_BUCKET), "the bucket is kept");
        helper.assertValueEqual(
                use(helper, player, new ItemStack(Items.BUCKET)),
                InteractionResult.SUCCESS,
                "empty");
        helper.assertFalse(helper.getBlockState(AT).getValue(CrucibleBlock.WATER), "dry");
        helper.assertTrue(player.getMainHandItem().is(Items.WATER_BUCKET), "the bucket is filled");
        helper.assertValueEqual(
                use(helper, player, new ItemStack(Items.BUCKET)),
                InteractionResult.CONSUME,
                "empty a dry one");
        helper.assertValueEqual(
                use(helper, player, new ItemStack(Items.STICK)),
                InteractionResult.TRY_WITH_EMPTY_HAND,
                "anything else");
        helper.succeed();
    }

    /** Magma and lava heat; a campfire heats only when lit; a filled crucible over them boils. */
    private static void heatSources(GameTestHelper helper) {
        helper.setBlock(
                HEAT, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false));
        helper.setBlock(AT, state(true, false));
        helper.runAfterDelay(
                2,
                () -> {
                    helper.assertFalse(
                            helper.getBlockState(AT).getValue(CrucibleBlock.BOILING),
                            "unlit: cold");
                    helper.setBlock(
                            HEAT,
                            Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
                    helper.assertTrue(
                            helper.getBlockState(AT).getValue(CrucibleBlock.BOILING), "lit: boils");
                    helper.setBlock(HEAT, Blocks.STONE);
                    helper.assertFalse(
                            helper.getBlockState(AT).getValue(CrucibleBlock.BOILING),
                            "stone: cold");
                    for (BlockState heat :
                            List.of(
                                    Blocks.MAGMA_BLOCK.defaultBlockState(),
                                    Blocks.LAVA.defaultBlockState())) {
                        helper.assertTrue(CrucibleBlock.heats(heat), heat + " heats");
                    }
                    helper.setBlock(HEAT, Blocks.MAGMA_BLOCK);
                    helper.assertTrue(
                            helper.getBlockState(AT).getValue(CrucibleBlock.BOILING), "magma");
                    helper.succeed();
                });
    }

    /** A cold crucible ignores items until it boils. */
    private static void coldIgnores(GameTestHelper helper) {
        helper.setBlock(HEAT, Blocks.STONE);
        helper.setBlock(AT, state(true, false));
        ItemEntity stone = drop(helper, new ItemStack(Items.STONE, 4), null);
        helper.runAfterDelay(
                12,
                () -> {
                    helper.assertTrue(be(helper).pool().isEmpty(), "nothing dissolves while cold");
                    helper.setBlock(HEAT, Blocks.MAGMA_BLOCK);
                    helper.succeedWhen(
                            () -> {
                                helper.assertValueEqual(
                                        be(helper).pool().amount("vftv:terra"), 8, "terra");
                                helper.assertFalse(stone.isAlive(), "the resting stack dissolved");
                            });
                });
    }

    /** A whole stack dissolves at 1x and posts one DISSOLVED. */
    private static void dissolvesStack(GameTestHelper helper) {
        Events events = record(helper);
        boiling(helper);
        ItemEntity stone = drop(helper, new ItemStack(Items.STONE, 64), null);
        helper.succeedWhen(
                () -> {
                    helper.assertValueEqual(
                            be(helper).pool().amount("vftv:terra"), 128, "terra 128");
                    helper.assertFalse(stone.isAlive(), "none left");
                    List<CruciblePayload.Dissolved> d = events.of(CruciblePayload.Dissolved.class);
                    helper.assertValueEqual(d.size(), 1, "one DISSOLVED");
                    helper.assertValueEqual(d.getFirst().item().getCount(), 64, "64 dissolved");
                });
    }

    /** Terra 190 of 200 takes 5 stone; 59 are refused FULL and marked. */
    private static void fullPartial(GameTestHelper helper) {
        CrucibleBlockEntity be = boiling(helper);
        setPool(be, "vftv:terra", 190);
        ServerPlayer player = onlinePlayer(helper);
        List<Component> lines = lines(player);
        ItemEntity stone = drop(helper, new ItemStack(Items.STONE, 64), player);
        helper.succeedWhen(
                () -> {
                    helper.assertValueEqual(be(helper).pool().amount("vftv:terra"), 200, "full");
                    helper.assertValueEqual(stone.getItem().getCount(), 59, "59 left");
                    helper.assertTrue(CrucibleService.hooks().refused(stone), "marked");
                    helper.assertTrue(says(lines, "message.vftv.crucible.full"), "FULL said");
                });
    }

    /** An item with an empty value is refused NOTHING. */
    private static void nothingRefused(GameTestHelper helper) {
        boiling(helper);
        ItemEntity cobble = drop(helper, new ItemStack(Items.COBBLESTONE, 3), null);
        helper.succeedWhen(
                () -> {
                    helper.assertTrue(CrucibleService.hooks().refused(cobble), "refused");
                    helper.assertValueEqual(cobble.getItem().getCount(), 3, "unchanged");
                    helper.assertTrue(be(helper).pool().isEmpty(), "nothing added");
                });
    }

    /** A written book and a shulker box with contents come out unchanged and marked. */
    private static void protectedRefused(GameTestHelper helper) {
        boiling(helper);
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, WrittenBookContent.EMPTY);
        ItemStack box = new ItemStack(Items.SHULKER_BOX);
        box.set(
                DataComponents.CONTAINER,
                ItemContainerContents.fromItems(List.of(new ItemStack(Items.STONE))));
        helper.assertTrue(CrucibleService.isProtected(book), "a written book is protected");
        helper.assertTrue(CrucibleService.isProtected(box), "a filled shulker box is protected");
        helper.assertFalse(
                CrucibleService.isProtected(new ItemStack(Items.SHULKER_BOX)),
                "an empty one isn't");
        ItemEntity b = drop(helper, book, null);
        ItemEntity s = drop(helper, box, null);
        helper.succeedWhen(
                () -> {
                    helper.assertTrue(CrucibleService.hooks().refused(b), "book refused");
                    helper.assertTrue(CrucibleService.hooks().refused(s), "box refused");
                    helper.assertTrue(be(helper).pool().isEmpty(), "nothing dissolved");
                });
    }

    /** A learned, online thrower's copper ingot crafts Resonant Brass from the pool. */
    private static void learnedCrafts(GameTestHelper helper) {
        Events events = record(helper);
        CrucibleBlockEntity be = boiling(helper);
        setPool(be, "vftv:metallum", 4, "vftv:sonus", 7);
        ServerPlayer player = onlinePlayer(helper);
        ResearchTestSupport.learn(player, BRASS);
        ItemEntity copper = drop(helper, new ItemStack(Items.COPPER_INGOT, 2), player);
        helper.succeedWhen(
                () -> {
                    helper.assertValueEqual(be(helper).pool().amount("vftv:sonus"), 1, "cost paid");
                    helper.assertValueEqual(
                            be(helper).pool().amount("vftv:metallum"), 0, "cost paid");
                    helper.assertValueEqual(copper.getItem().getCount(), 1, "one catalyst used");
                    ItemEntity brass =
                            items(helper).stream()
                                    .filter(i -> i.getItem().is(CrucibleContent.ingot()))
                                    .findFirst()
                                    .orElseThrow(() -> helper.assertionException("no brass"));
                    helper.assertTrue(
                            CrucibleService.hooks().refused(brass), "the result is marked");
                    helper.assertValueEqual(
                            events.of(CruciblePayload.Crafted.class).size(), 1, "one CRAFTED");
                    helper.assertValueEqual(events.crafted(player), 1L, "ItemCraftedEvent once");
                });
    }

    /** An unlearned thrower's catalyst dissolves like any item. */
    private static void unlearnedDissolves(GameTestHelper helper) {
        testRecipes(helper);
        boiling(helper);
        ServerPlayer player = onlinePlayer(helper);
        ItemEntity iron = drop(helper, new ItemStack(Items.IRON_INGOT), player);
        helper.succeedWhen(
                () -> {
                    helper.assertValueEqual(
                            be(helper).pool().amount("vftv:metallum"), 4, "dissolved");
                    helper.assertFalse(iron.isAlive(), "gone");
                });
    }

    /** A learned catalyst with too little in the pool is refused SHORT, never melted. */
    private static void shortRefused(GameTestHelper helper) {
        testRecipes(helper);
        boiling(helper);
        ServerPlayer player = onlinePlayer(helper);
        List<Component> lines = lines(player);
        ResearchTestSupport.learn(player, ResearchKeys.COMMONPLACE_BOOK);
        ItemEntity iron = drop(helper, new ItemStack(Items.IRON_INGOT), player);
        helper.succeedWhen(
                () -> {
                    helper.assertTrue(CrucibleService.hooks().refused(iron), "refused");
                    helper.assertTrue(iron.isAlive(), "not melted");
                    helper.assertTrue(says(lines, "message.vftv.crucible.short"), "SHORT said");
                    helper.assertTrue(be(helper).pool().isEmpty(), "nothing dissolved");
                });
    }

    /** A sneak-drop captured at the toss dissolves a learned catalyst. */
    private static void sneakDrop(GameTestHelper helper) {
        testRecipes(helper);
        boiling(helper);
        ServerPlayer player = onlinePlayer(helper);
        ResearchTestSupport.learn(player, ResearchKeys.COMMONPLACE_BOOK);
        ItemEntity iron = drop(helper, new ItemStack(Items.IRON_INGOT), player);
        player.setShiftKeyDown(true);
        CrucibleService.onToss(player, iron);
        player.setShiftKeyDown(false); // standing up later changes nothing
        helper.succeedWhen(
                () -> {
                    helper.assertValueEqual(
                            be(helper).pool().amount("vftv:metallum"), 4, "dissolved");
                    helper.assertFalse(iron.isAlive(), "gone");
                });
    }

    /** A cancelled result spawn debits nothing and refuses the catalyst ERROR. */
    private static void cancelledSpawn(GameTestHelper helper) {
        testRecipes(helper);
        CrucibleBlockEntity be = boiling(helper);
        setPool(be, "vftv:aer", 5);
        ServerPlayer player = onlinePlayer(helper);
        List<Component> lines = lines(player);
        ResearchTestSupport.learn(player, ResearchKeys.COMMONPLACE_BOOK);
        CANCEL_AT.add(helper.absolutePos(AT));
        ItemEntity iron = drop(helper, new ItemStack(Items.IRON_INGOT), player);
        helper.succeedWhen(
                () -> {
                    helper.assertTrue(says(lines, "message.vftv.crucible.error"), "ERROR said");
                    helper.assertValueEqual(
                            be(helper).pool().amount("vftv:aer"), 5, "nothing debited");
                    helper.assertTrue(
                            iron.isAlive() && iron.getItem().getCount() == 1, "catalyst kept");
                    helper.assertTrue(
                            items(helper).stream().noneMatch(i -> i.getItem().is(Items.GOLD_INGOT)),
                            "no result");
                });
    }

    /** A throwing CRAFTED listener: the craft stands and the later notifications still run. */
    private static void throwingListener(GameTestHelper helper) {
        testRecipes(helper);
        Events events = record(helper);
        events.recording().throwAt.add(events.pos());
        CrucibleBlockEntity be = boiling(helper);
        setPool(be, "vftv:aer", 5);
        ServerPlayer player = onlinePlayer(helper);
        ResearchTestSupport.learn(player, ResearchKeys.COMMONPLACE_BOOK);
        drop(helper, new ItemStack(Items.IRON_INGOT), player);
        helper.succeedWhen(
                () -> {
                    helper.assertValueEqual(be(helper).pool().amount("vftv:aer"), 3, "debited");
                    helper.assertValueEqual(
                            events.crafted(player), 1L, "ItemCraftedEvent still ran");
                });
    }

    /** Emptying by bucket spills the exact pool once, as EMPTIED. */
    private static void emptySpills(GameTestHelper helper) {
        Events events = record(helper);
        CrucibleBlockEntity be = boiling(helper);
        setPool(be, "vftv:aer", 3, "vftv:gone", 2);
        ServerPlayer player = ResearchTestSupport.player(helper);
        helper.assertValueEqual(
                use(helper, player, new ItemStack(Items.BUCKET)),
                InteractionResult.SUCCESS,
                "emptied");
        List<CruciblePayload.Spilled> spills = events.of(CruciblePayload.Spilled.class);
        helper.assertValueEqual(spills.size(), 1, "one spill");
        helper.assertValueEqual(spills.getFirst().cause(), SpillCause.EMPTIED, "EMPTIED");
        helper.assertValueEqual(spills.getFirst().aspects().size(), 2, "missing ids included");
        helper.assertTrue(be.pool().isEmpty(), "cleared");
        helper.succeed();
    }

    /** Breaking spills as REMOVED, once. */
    private static void breakSpills(GameTestHelper helper) {
        Events events = record(helper);
        CrucibleBlockEntity be = boiling(helper);
        setPool(be, "vftv:aer", 3);
        helper.getLevel().destroyBlock(helper.absolutePos(AT), true);
        List<CruciblePayload.Spilled> spills = events.of(CruciblePayload.Spilled.class);
        helper.assertValueEqual(spills.size(), 1, "one spill");
        helper.assertValueEqual(spills.getFirst().cause(), SpillCause.REMOVED, "REMOVED");
        helper.succeed();
    }

    private static ResourceHandler<EssentiaResource> handler(
            GameTestHelper helper, @Nullable Direction side) {
        ResourceHandler<EssentiaResource> handler =
                helper.getLevel()
                        .getCapability(EssentiaCapabilities.HANDLER, helper.absolutePos(AT), side);
        if (handler == null) {
            throw helper.assertionException("no handler on " + side);
        }
        return handler;
    }

    private static EssentiaResource aer(GameTestHelper helper) {
        return new EssentiaResource(
                helper.getLevel()
                        .registryAccess()
                        .lookupOrThrow(AspectKeys.REGISTRY)
                        .getOrThrow(AspectKeys.AER));
    }

    /** Extract-only: commit, abort, a child undone by its root's abort, insert 0, no UP face. */
    private static void extractTransactions(GameTestHelper helper) {
        CrucibleBlockEntity be = boiling(helper);
        setPool(be, "vftv:aer", 5);
        ResourceHandler<EssentiaResource> h = handler(helper, Direction.NORTH);
        EssentiaResource aer = aer(helper);
        helper.assertTrue(
                helper.getLevel()
                                .getCapability(
                                        EssentiaCapabilities.HANDLER,
                                        helper.absolutePos(AT),
                                        Direction.UP)
                        == null,
                "no UP face");
        try (Transaction tx = Transaction.openRoot()) {
            helper.assertValueEqual(h.extract(aer, 2, tx), 2, "extracted");
            helper.assertValueEqual(h.insert(aer, 2, tx), 0, "nothing inserts");
        }
        helper.assertValueEqual(be.pool().amount("vftv:aer"), 5, "aborted: restored");
        try (Transaction root = Transaction.openRoot()) {
            try (Transaction child = Transaction.open(root)) {
                h.extract(aer, 3, child);
                child.commit();
            }
            helper.assertValueEqual(be.pool().amount("vftv:aer"), 2, "child committed");
        }
        helper.assertValueEqual(be.pool().amount("vftv:aer"), 5, "root abort undid the child");
        try (Transaction tx = Transaction.openRoot()) {
            h.extract(aer, 9, tx);
            tx.commit();
        }
        helper.assertTrue(be.pool().isEmpty(), "committed: all 5 taken");
        boolean threw = false;
        try (Transaction tx = Transaction.openRoot()) {
            h.extract(aer, -1, tx);
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        helper.assertTrue(threw, "a negative amount throws");
        helper.assertValueEqual(h.getAmountAsLong(-1), 0L, "an invalid index reads 0");
        helper.assertValueEqual(h.getCapacityAsLong(0, aer), 0L, "no capacity");
        helper.succeed();
    }

    /** While a root is open: buckets refuse busy, and the abort restores the pool with water. */
    private static void busyRefusesBucket(GameTestHelper helper) {
        CrucibleBlockEntity be = boiling(helper);
        setPool(be, "vftv:aer", 5);
        ResourceHandler<EssentiaResource> h = handler(helper, null);
        ServerPlayer player = ResearchTestSupport.player(helper);
        List<Component> lines = lines(player);
        try (Transaction tx = Transaction.openRoot()) {
            h.extract(aer(helper), 2, tx);
            helper.assertTrue(be.busy(), "busy");
            helper.assertValueEqual(
                    use(helper, player, new ItemStack(Items.BUCKET)),
                    InteractionResult.CONSUME,
                    "refused");
        }
        helper.assertFalse(be.busy(), "released on abort");
        helper.assertTrue(says(lines, "message.vftv.busy"), "busy said");
        helper.assertValueEqual(be.pool().amount("vftv:aer"), 5, "restored");
        helper.assertTrue(
                helper.getBlockState(AT).getValue(CrucibleBlock.WATER), "still has water");
        helper.succeed();
    }

    /** A removal while busy spills once at the root's close, the pool the transaction left. */
    private static void removedWhileBusy(GameTestHelper helper) {
        Events events = record(helper);
        CrucibleBlockEntity be = boiling(helper);
        setPool(be, "vftv:aer", 5);
        ResourceHandler<EssentiaResource> h = handler(helper, null);
        try (Transaction tx = Transaction.openRoot()) {
            h.extract(aer(helper), 2, tx);
            helper.getLevel().destroyBlock(helper.absolutePos(AT), false);
            helper.assertTrue(events.of(CruciblePayload.Spilled.class).isEmpty(), "deferred");
            tx.commit();
        }
        List<CruciblePayload.Spilled> spills = events.of(CruciblePayload.Spilled.class);
        helper.assertValueEqual(spills.size(), 1, "one spill");
        helper.assertValueEqual(
                spills.getFirst().aspects().getOrDefault(AspectKeys.AER, 0),
                3,
                "the pool the commit left");
        helper.succeed();
    }

    private static CompoundTag save(GameTestHelper helper, CrucibleBlockEntity be) {
        return be.saveWithFullMetadata(helper.getLevel().registryAccess());
    }

    private static CrucibleBlockEntity load(GameTestHelper helper, CompoundTag tag) {
        BlockEntity loaded =
                BlockEntity.loadStatic(
                        helper.absolutePos(AT),
                        state(true, true),
                        tag,
                        helper.getLevel().registryAccess());
        if (!(loaded instanceof CrucibleBlockEntity be)) {
            throw helper.assertionException("did not load");
        }
        return be;
    }

    /** The pool, missing ids included, survives a save and load. */
    private static void persistence(GameTestHelper helper) {
        CrucibleBlockEntity be = boiling(helper);
        setPool(be, "vftv:aer", 3, "vftv:gone", 7);
        CrucibleBlockEntity loaded = load(helper, save(helper, be));
        helper.assertFalse(loaded.unreadable(), "readable");
        helper.assertValueEqual(loaded.pool().snapshot(), be.pool().snapshot(), "same pool");
        helper.succeed();
    }

    /** A future format is kept byte-identical, refuses buckets and spills nothing on removal. */
    private static void unreadableRecord(GameTestHelper helper) {
        Events events = record(helper);
        CrucibleBlockEntity be = boiling(helper);
        CompoundTag tag = save(helper, be);
        tag.getCompound("crucible").orElseThrow().put("format", IntTag.valueOf(2));
        CrucibleBlockEntity loaded = load(helper, tag);
        helper.assertTrue(loaded.unreadable(), "unreadable");
        helper.assertValueEqual(
                save(helper, loaded).getCompound("crucible"),
                tag.getCompound("crucible"),
                "kept as it is");
        helper.getLevel().setBlockEntity(loaded);
        ServerPlayer player = ResearchTestSupport.player(helper);
        List<Component> lines = lines(player);
        helper.assertValueEqual(
                use(helper, player, new ItemStack(Items.BUCKET)),
                InteractionResult.CONSUME,
                "refused");
        helper.assertTrue(says(lines, "message.vftv.unreadable"), "unreadable said");
        helper.getLevel().destroyBlock(helper.absolutePos(AT), false);
        helper.assertTrue(events.of(CruciblePayload.Spilled.class).isEmpty(), "no spill");
        helper.succeed();
    }

    private static DataResult<CrucibleRecipe> decode(
            GameTestHelper helper, String research, String aspects) {
        JsonObject body =
                JsonParser.parseString(
                                ("{'research': '"
                                                + research
                                                + "', 'catalyst': 'minecraft:stick',"
                                                + " 'aspects': {"
                                                + aspects
                                                + "}, 'result': {'id': 'minecraft:bell'}}")
                                        .replace('\'', '"'))
                        .getAsJsonObject();
        return CrucibleRecipe.MAP_CODEC
                .codec()
                .parse(
                        helper.getLevel()
                                .registryAccess()
                                .createSerializationContext(JsonOps.INSTANCE),
                        body);
    }

    /** Research and cost keys are checked against the loaded registries; costs are bounded. */
    private static void recipeCodec(GameTestHelper helper) {
        String book = "vftv:commonplace_book";
        helper.assertTrue(
                decode(helper, book, "'vftv:sonus': 1, 'vftv:vacuos': 1000").isSuccess(),
                "compound and Vacuos");
        helper.assertTrue(decode(helper, book, "'vftv:nowhere': 1").isError(), "unknown aspect");
        helper.assertTrue(
                decode(helper, "vftv:nothing_here", "'vftv:aer': 1").isError(), "unknown research");
        helper.assertTrue(decode(helper, book, "'vftv:aer': 0").isError(), "0");
        helper.assertTrue(decode(helper, book, "'vftv:aer': 1001").isError(), "1001");
        helper.assertTrue(decode(helper, book, "").isError(), "no cost");
        String nine =
                "'vftv:aer': 1, 'vftv:aqua': 1, 'vftv:ignis': 1, 'vftv:terra': 1, 'vftv:ordo': 1,"
                        + " 'vftv:perditio': 1, 'vftv:lux': 1, 'vftv:sonus': 1, 'vftv:motus': 1";
        helper.assertTrue(decode(helper, book, nine).isError(), "a ninth entry");
        helper.assertTrue(
                CrucibleRecipes.server()
                                .get(
                                        Identifier.fromNamespaceAndPath(
                                                "vftv", "resonant_brass_ingot"))
                        != null,
                "the built-in recipe loaded");
        helper.assertValueEqual(
                CrucibleRecipes.server()
                        .candidates(new ItemStack(Items.COPPER_INGOT), key -> true)
                        .size(),
                1,
                "a copper ingot matches Resonant Brass through #c:ingots/copper");
        helper.succeed();
    }

    /** Over the cap: startup fails and a reload empties the snapshot. */
    private static void recipeCap(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        int loaded = CrucibleRecipes.server().recipes().size();
        boolean refused = false;
        try {
            CrucibleRecipes.beforeSync(manager(server, CrucibleLimits.MAX_CRUCIBLE_RECIPES + 1));
            helper.assertValueEqual(
                    CrucibleRecipes.server().recipes().size(), 0, "over the cap: empty");
            try {
                CrucibleRecipes.onServerStarting(
                        manager(server, CrucibleLimits.MAX_CRUCIBLE_RECIPES + 1));
            } catch (IllegalStateException e) {
                refused =
                        String.valueOf(e.getMessage())
                                .contains("4097 crucible recipes exceed the cap");
            }
            CrucibleRecipes.beforeSync(manager(server, CrucibleLimits.MAX_CRUCIBLE_RECIPES));
            helper.assertValueEqual(
                    CrucibleRecipes.server().recipes().size(),
                    CrucibleLimits.MAX_CRUCIBLE_RECIPES,
                    "at the cap");
        } finally {
            CrucibleRecipes.onServerStarting(server.getRecipeManager());
        }
        helper.assertTrue(refused, "startup fails, naming the count and the cap");
        helper.assertValueEqual(CrucibleRecipes.server().recipes().size(), loaded, "restored");
        helper.succeed();
    }

    /** The sync listener asks NeoForge to send crucible recipes. */
    private static void syncSendsRecipes(GameTestHelper helper) {
        OnDatapackSyncEvent event =
                NeoForge.EVENT_BUS.post(
                        new OnDatapackSyncEvent(
                                helper.getLevel().getServer().getPlayerList(), null));
        helper.assertTrue(event.getRecipeTypesToSend().contains(CrucibleRecipe.TYPE), "sent");
        helper.succeed();
    }

    /** Forty resting entities: at most sixteen actions per step, in id order. */
    private static void flood(GameTestHelper helper) {
        Events events = record(helper);
        boiling(helper);
        List<ItemEntity> drops = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            // Distinct names keep vanilla from merging the stacks.
            ItemStack paper = new ItemStack(Items.PAPER);
            paper.set(DataComponents.CUSTOM_NAME, Component.literal("paper " + i));
            drops.add(drop(helper, paper, null));
        }
        Map<Long, Integer> perTick = new TreeMap<>();
        helper.onEachTick(
                () ->
                        events.of(CruciblePayload.Dissolved.class).stream()
                                .skip(perTick.values().stream().mapToInt(i -> i).sum())
                                .forEach(
                                        d ->
                                                perTick.merge(
                                                        helper.getLevel().getGameTime(),
                                                        1,
                                                        Integer::sum)));
        helper.succeedWhen(
                () -> {
                    helper.assertTrue(
                            drops.stream().noneMatch(Entity::isAlive),
                            "all dissolved; alive "
                                    + drops.stream().filter(Entity::isAlive).count()
                                    + ", pool "
                                    + be(helper).pool()
                                    + ", refused "
                                    + drops.stream()
                                            .filter(CrucibleService.hooks()::refused)
                                            .count()
                                    + ", at "
                                    + drops.getFirst().position());
                    helper.assertValueEqual(be(helper).pool().amount("vftv:aer"), 40, "aer 40");
                    helper.assertTrue(
                            perTick.values().stream().allMatch(n -> n <= 16),
                            "≤16 per step: " + perTick);
                });
    }

    /** A queued crucible processes nothing until its refresh; the refresh re-derives heat. */
    private static void refreshQueue(GameTestHelper helper) {
        helper.setBlock(HEAT, Blocks.MAGMA_BLOCK);
        // Written boiling=false over a heat source, as a stale save under other tags would be.
        helper.setBlock(AT, state(true, false));
        ServerLevel level = helper.getLevel();
        helper.runAfterDelay(
                2,
                () -> {
                    helper.assertTrue(
                            helper.getBlockState(AT).getValue(CrucibleBlock.BOILING),
                            "the load's refresh re-derived heat");
                    // A tag reload queues every loaded crucible; a stale state is corrected.
                    level.setBlock(helper.absolutePos(AT), state(true, false), 2);
                    CrucibleService.queueAll(level);
                    helper.runAfterDelay(
                            1,
                            () -> {
                                helper.assertTrue(
                                        helper.getBlockState(AT).getValue(CrucibleBlock.BOILING),
                                        "the reload's refresh re-derived heat");
                                helper.succeed();
                            });
                });
    }
}
