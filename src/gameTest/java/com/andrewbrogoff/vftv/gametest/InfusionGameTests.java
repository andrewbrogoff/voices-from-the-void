package com.andrewbrogoff.vftv.gametest;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.api.essentia.SpillCause;
import com.andrewbrogoff.vftv.api.infusion.InfusionKeys;
import com.andrewbrogoff.vftv.api.infusion.InfusionPayload;
import com.andrewbrogoff.vftv.api.neoforge.infusion.InfusionEvent;
import com.andrewbrogoff.vftv.api.research.Research;
import com.andrewbrogoff.vftv.api.research.ResearchKeys;
import com.andrewbrogoff.vftv.core.essentia.EssentiaContent;
import com.andrewbrogoff.vftv.core.essentia.JarBlockEntity;
import com.andrewbrogoff.vftv.core.essentia.model.EssentiaMath.Contents;
import com.andrewbrogoff.vftv.core.infusion.BowlBlockEntity;
import com.andrewbrogoff.vftv.core.infusion.InfusionAltars;
import com.andrewbrogoff.vftv.core.infusion.InfusionContent;
import com.andrewbrogoff.vftv.core.infusion.InfusionPayloads;
import com.andrewbrogoff.vftv.core.infusion.InfusionPreview;
import com.andrewbrogoff.vftv.core.infusion.InfusionReadout;
import com.andrewbrogoff.vftv.core.infusion.InfusionRecipe;
import com.andrewbrogoff.vftv.core.infusion.InfusionRecipes;
import com.andrewbrogoff.vftv.core.infusion.InfusionStatus;
import com.andrewbrogoff.vftv.core.infusion.PedestalBlockEntity;
import com.andrewbrogoff.vftv.core.text.StatusLines;
import com.andrewbrogoff.vftv.core.vis.VisContent;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The infusion feature's GameTests. Name each test {@code infusion_<what>}, and list its id {@code
 * vftv:infusion_<what>} in {@code src/gameTest/expected/infusion.txt}.
 *
 * <p>Tests run side by side six blocks apart, closer than an altar's box, so each test builds its
 * altar in its own height band above the test's origin, where no neighbour builds.
 */
final class InfusionGameTests {
    private static final ResourceKey<Research> INFUSION =
            ResourceKey.create(
                    ResearchKeys.REGISTRY, Identifier.fromNamespaceAndPath("vftv", "infusion"));
    private static final int[][] RING = {
        {2, 0}, {-2, 0}, {0, 2}, {0, -2}, {2, 2}, {-2, -2}, {2, -2}, {-2, 2}
    };

    /** Every infusion event posted, with its flux rules; tests read their own bowl's. */
    private static final List<InfusionEvent> EVENTS =
            Collections.synchronizedList(new ArrayList<>());

    private static final List<Map.Entry<ServerPlayer, Component>> LINES =
            Collections.synchronizedList(new ArrayList<>());
    private static boolean capturing;

    private InfusionGameTests() {}

    static void register(Map<String, Consumer<GameTestHelper>> tests, IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(InfusionEvent.class, EVENTS::add);
        tests.put("infusion_registered", InfusionGameTests::registered);
        tests.put("infusion_hand_place_take", h -> handPlaceTake(h, 1));
        tests.put("infusion_strike_reads_ready", h -> strikeReadsReady(h, 2));
        tests.put("infusion_second_strike_starts", h -> secondStrikeStarts(h, 3));
        tests.put("infusion_first_strike_never_commits", h -> firstStrikeNeverCommits(h, 4));
        tests.put("infusion_stale_after_change", h -> staleAfterChange(h, 5));
        tests.put("infusion_seen_bad_input", h -> seenBadInput(h, 6));
        tests.put("infusion_run_completes", h -> runCompletes(h, 7));
        tests.put("infusion_unlearned_no_recipe", h -> unlearned(h, 8));
        tests.put("infusion_enchant_to_cap", h -> enchantToCap(h, 9));
        tests.put("infusion_enchant_refusals", h -> enchantRefusals(h, 10));
        tests.put("infusion_transmute_keeps_components", h -> transmute(h, 11));
        tests.put("infusion_claims_refuse_second_bowl", h -> claimsRefuse(h, 12));
        tests.put("infusion_item_handler_rules", h -> handlerRules(h, 13));
        tests.put("infusion_container_path", h -> containerPath(h, 14));
        tests.put("infusion_abandon_flow", h -> abandonFlow(h, 15));
        tests.put("infusion_removal_spills", h -> removalSpills(h, 16));
        tests.put("infusion_persistence", h -> persistence(h, 17));
        tests.put("infusion_unreadable_record", h -> unreadable(h, 18));
        tests.put("infusion_recipe_codec", InfusionGameTests::recipeCodec);
        tests.put("infusion_shown_altar", h -> shownAltar(h, 19));
        tests.put("infusion_draws_only_inside_box", h -> drawsInsideBox(h, 20));
        tests.put("infusion_preview_context", h -> preview(h, 21));
        tests.put("infusion_assessment_budget", online(h -> budget(h, 22)));
        tests.put("infusion_tuning_rules", InfusionGameTests::tuningRules);
    }

    // --- Helpers ----------------------------------------------------------------------------

    private record Altar(BlockPos rel, BlockPos bowl, ServerPlayer player) {}

    private static BlockPos rel(int band) {
        return new BlockPos(0, 4 + 5 * band, 0);
    }

    /** A bowl holding {@code centre}, with one pedestal per offering on the ring, and a player. */
    private static Altar altar(
            GameTestHelper helper, int band, ItemStack centre, ItemStack... offerings) {
        BlockPos rel = rel(band);
        helper.setBlock(rel, InfusionContent.bowl());
        bowl(helper, rel).setTheItem(centre);
        for (int i = 0; i < offerings.length; i++) {
            BlockPos p = rel.offset(RING[i][0], 0, RING[i][1]);
            helper.setBlock(p, InfusionContent.pedestal());
            pedestal(helper, p).setTheItem(offerings[i]);
        }
        ServerPlayer player = ResearchTestSupport.player(helper);
        BlockPos abs = helper.absolutePos(rel);
        player.setPos(abs.getX() + 0.5, abs.getY(), abs.getZ() + 2.5);
        ResearchTestSupport.learn(player, INFUSION);
        return new Altar(rel, abs, player);
    }

    private static BowlBlockEntity bowl(GameTestHelper helper, BlockPos rel) {
        return Objects.requireNonNull(helper.getBlockEntity(rel, BowlBlockEntity.class));
    }

    private static PedestalBlockEntity pedestal(GameTestHelper helper, BlockPos rel) {
        return Objects.requireNonNull(helper.getBlockEntity(rel, PedestalBlockEntity.class));
    }

    private static JarBlockEntity jar(
            GameTestHelper helper, BlockPos rel, ResourceKey<Aspect> aspect, int amount) {
        helper.setBlock(rel, EssentiaContent.jar());
        JarBlockEntity jar =
                Objects.requireNonNull(helper.getBlockEntity(rel, JarBlockEntity.class));
        jar.setLive(new Contents<>(aspect, amount));
        return jar;
    }

    private static InteractionResult use(
            GameTestHelper helper, ServerPlayer player, BlockPos abs, ItemStack held) {
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
        return helper.getLevel()
                .getBlockState(abs)
                .useItemOn(held, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
    }

    private static InteractionResult useEmpty(
            GameTestHelper helper, ServerPlayer player, BlockPos abs) {
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
        return helper.getLevel().getBlockState(abs).useWithoutItem(helper.getLevel(), player, hit);
    }

    private static ItemStack wand() {
        return new ItemStack(VisContent.wand());
    }

    private static int revision(ServerPlayer player) {
        return Objects.requireNonNull(InfusionAltars.armState(player))[1];
    }

    private static void seen(ServerPlayer player, BlockPos abs) {
        InfusionAltars.onSeen(new InfusionPayloads.Seen(abs, revision(player)), player);
    }

    /** Strike, acknowledge, strike: the commit. */
    private static void start(GameTestHelper helper, Altar a) {
        use(helper, a.player(), a.bowl(), wand());
        seen(a.player(), a.bowl());
        use(helper, a.player(), a.bowl(), wand());
    }

    private static List<InfusionPayload> events(BlockPos abs) {
        List<InfusionPayload> out = new ArrayList<>();
        synchronized (EVENTS) {
            for (InfusionEvent e : EVENTS) {
                if (pos(e.payload()).equals(abs)) {
                    out.add(e.payload());
                }
            }
        }
        return out;
    }

    private static BlockPos pos(InfusionPayload p) {
        return switch (p) {
            case InfusionPayload.Started s -> s.pos();
            case InfusionPayload.Upset u -> u.pos();
            case InfusionPayload.Spilled s -> s.pos();
            case InfusionPayload.Completed c -> c.pos();
            case InfusionPayload.Abandoned a -> a.pos();
        };
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
        return new java.util.AbstractList<>() {
            @Override
            public Component get(int i) {
                return mine().get(i);
            }

            @Override
            public int size() {
                return mine().size();
            }

            private List<Component> mine() {
                List<Component> out = new ArrayList<>();
                synchronized (LINES) {
                    LINES.stream()
                            .filter(e -> e.getKey().equals(player))
                            .forEach(e -> out.add(e.getValue()));
                }
                return out;
            }
        };
    }

    private static boolean says(List<Component> lines, String key) {
        return lines.stream()
                .anyMatch(
                        l ->
                                l.getContents() instanceof TranslatableContents t
                                        && t.getKey().equals(key));
    }

    @SuppressWarnings("removal") // the only helper that puts a mock player on the player list
    private static ServerPlayer onlinePlayer(GameTestHelper helper) {
        return helper.makeMockServerPlayerInLevel();
    }

    private static Consumer<GameTestHelper> online(Consumer<GameTestHelper> test) {
        return helper -> {
            if (net.neoforged.fml.ModList.get().isLoaded("curios")) {
                helper.succeed();
            } else {
                test.accept(helper);
            }
        };
    }

    private static <T> void eq(GameTestHelper helper, T actual, T expected, String what) {
        helper.assertValueEqual(actual, expected, what);
    }

    // --- Tests ------------------------------------------------------------------------------

    /** The content, recipe type, display type, sounds, tags and block properties. */
    private static void registered(GameTestHelper helper) {
        helper.assertTrue(
                InfusionRecipe.TYPE.equals(
                        BuiltInRegistries.RECIPE_TYPE.getValue(InfusionRecipe.ID)),
                "type");
        helper.assertTrue(
                InfusionRecipe.SERIALIZER.equals(
                        BuiltInRegistries.RECIPE_SERIALIZER.getValue(InfusionRecipe.ID)),
                "serializer");
        helper.assertTrue(
                BuiltInRegistries.RECIPE_DISPLAY.containsKey(InfusionRecipe.ID), "display");
        InfusionContent.SOUNDS.forEach(
                s -> helper.assertTrue(BuiltInRegistries.SOUND_EVENT.containsKey(s), s.toString()));
        eq(
                helper,
                InfusionContent.bowl().defaultBlockState().getPistonPushReaction(),
                PushReaction.BLOCK,
                "bowl pistons");
        eq(
                helper,
                InfusionContent.pedestal().defaultBlockState().getPistonPushReaction(),
                PushReaction.BLOCK,
                "pedestal pistons");
        helper.assertTrue(
                Blocks.AMETHYST_CLUSTER.defaultBlockState().is(InfusionKeys.INFUSION_STABILISERS),
                "stabiliser tag");
        helper.assertTrue(
                new ItemStack(Items.BOOK).is(InfusionKeys.INFUSION_UNENCHANTABLE),
                "unenchantable tag");
        helper.assertTrue(
                InfusionRecipes.server().get(Identifier.fromNamespaceAndPath("vftv", "echo_rod"))
                        != null,
                "the Echo rod recipe loaded");
        helper.assertTrue(
                InfusionRecipes.server().recipes().stream()
                                .filter(
                                        r ->
                                                r.id().identifier()
                                                        .getPath()
                                                        .startsWith("infused_enchanting/"))
                                .count()
                        >= 6,
                "enchanting recipes loaded");
        helper.succeed();
    }

    /**
     * Placing one item on a bowl and a pedestal, taking it back empty-handed; a wand on a pedestal
     * is an item.
     */
    private static void handPlaceTake(GameTestHelper helper, int band) {
        Altar a = altar(helper, band, ItemStack.EMPTY);
        eq(
                helper,
                use(helper, a.player(), a.bowl(), new ItemStack(Items.STICK, 3)),
                InteractionResult.SUCCESS,
                "placed");
        eq(helper, bowl(helper, a.rel()).getTheItem().getCount(), 1, "one placed");
        eq(
                helper,
                use(helper, a.player(), a.bowl(), new ItemStack(Items.FLINT)),
                InteractionResult.TRY_WITH_EMPTY_HAND,
                "occupied");
        eq(helper, useEmpty(helper, a.player(), a.bowl()), InteractionResult.SUCCESS, "taken");
        helper.assertTrue(bowl(helper, a.rel()).getTheItem().isEmpty(), "bowl empty");
        BlockPos p = a.rel().offset(2, 0, 0);
        helper.setBlock(p, InfusionContent.pedestal());
        eq(
                helper,
                use(helper, a.player(), helper.absolutePos(p), wand()),
                InteractionResult.SUCCESS,
                "a wand is an item on a pedestal");
        helper.assertTrue(
                pedestal(helper, p).getTheItem().is(VisContent.wand()), "wand on the pedestal");
        eq(
                helper,
                useEmpty(helper, a.player(), helper.absolutePos(a.rel().offset(1, 0, 1))),
                InteractionResult.PASS,
                "air passes");
        helper.succeed();
    }

    /** A strike reads READY with the model's figures. */
    private static void strikeReadsReady(GameTestHelper helper, int band) {
        Altar a = altar(helper, band, new ItemStack(Items.STICK), new ItemStack(Items.FLINT));
        InfusionReadout r = InfusionAltars.readout(helper.getLevel(), a.bowl(), a.player());
        eq(helper, r.status(), InfusionStatus.READY, "ready");
        var plan = Objects.requireNonNull(r.ready());
        eq(helper, plan.lonePedestals(), 1, "one lone pedestal");
        eq(helper, plan.loneItems(), 0, "no pair to be half-loaded");
        eq(helper, plan.stability(), 94, "100 - 6");
        eq(helper, plan.costs().getFirst().cost(), 2, "aer 2");
        use(helper, a.player(), a.bowl(), wand());
        int[] arm = Objects.requireNonNull(InfusionAltars.armState(a.player()));
        eq(helper, arm[0], 0, "an ARMED arm");
        helper.assertFalse(bowl(helper, a.rel()).ringing(), "a strike starts nothing");
        helper.succeed();
    }

    /** Strike, acknowledge, strike: the run starts, the pedestal is claimed, STARTED posts. */
    private static void secondStrikeStarts(GameTestHelper helper, int band) {
        Altar a = altar(helper, band, new ItemStack(Items.STICK), new ItemStack(Items.FLINT));
        start(helper, a);
        BowlBlockEntity bowl = bowl(helper, a.rel());
        helper.assertTrue(bowl.ringing(), "ringing");
        PedestalBlockEntity p = pedestal(helper, a.rel().offset(2, 0, 0));
        helper.assertTrue(
                p.claim() != null && p.claim().seq() == 1 && p.claim().id() == bowl.id(),
                "claimed for seq 1");
        helper.assertTrue(
                events(a.bowl()).stream().anyMatch(e -> e instanceof InfusionPayload.Started),
                "STARTED");
        eq(
                helper,
                Objects.requireNonNull(InfusionAltars.armState(a.player()))[0],
                1,
                "a run subscription");
        eq(
                helper,
                use(helper, a.player(), a.bowl(), new ItemStack(Items.FLINT)),
                InteractionResult.CONSUME,
                "ringing refuses an item");
        helper.succeed();
    }

    /** A forged seen before the first strike can't make that strike a commit. */
    private static void firstStrikeNeverCommits(GameTestHelper helper, int band) {
        Altar a = altar(helper, band, new ItemStack(Items.STICK), new ItemStack(Items.FLINT));
        InfusionAltars.onSeen(new InfusionPayloads.Seen(a.bowl(), 0), a.player());
        use(helper, a.player(), a.bowl(), wand());
        helper.assertFalse(bowl(helper, a.rel()).ringing(), "the first strike reads only");
        use(helper, a.player(), a.bowl(), wand());
        helper.assertFalse(bowl(helper, a.rel()).ringing(), "no seen: a strike again, no commit");
        helper.succeed();
    }

    /** A stabiliser broken between the strikes: STALE, the revision bumps, nothing starts. */
    private static void staleAfterChange(GameTestHelper helper, int band) {
        Altar a = altar(helper, band, new ItemStack(Items.STICK), new ItemStack(Items.FLINT));
        BlockPos s = a.rel().offset(3, 0, 1);
        helper.setBlock(s.below(), Blocks.STONE);
        helper.setBlock(s, Blocks.AMETHYST_CLUSTER);
        List<Component> lines = lines(a.player());
        use(helper, a.player(), a.bowl(), wand());
        int before = revision(a.player());
        seen(a.player(), a.bowl());
        helper.setBlock(s, Blocks.AIR);
        use(helper, a.player(), a.bowl(), wand());
        helper.assertFalse(bowl(helper, a.rel()).ringing(), "stale: not started");
        helper.assertTrue(revision(a.player()) != before, "revision bumped");
        helper.assertTrue(says(lines, "message.vftv.cost_changed"), "cost changed said");
        seen(a.player(), a.bowl());
        use(helper, a.player(), a.bowl(), wand());
        helper.assertTrue(
                bowl(helper, a.rel()).ringing(), "a retry with the fresh readout commits");
        helper.succeed();
    }

    /** Seen for another position or a wrong revision is ignored; more than 4 a tick are dropped. */
    private static void seenBadInput(GameTestHelper helper, int band) {
        Altar a = altar(helper, band, new ItemStack(Items.STICK), new ItemStack(Items.FLINT));
        use(helper, a.player(), a.bowl(), wand());
        int rev = revision(a.player());
        InfusionAltars.onSeen(new InfusionPayloads.Seen(a.bowl().above(), rev), a.player());
        InfusionAltars.onSeen(new InfusionPayloads.Seen(a.bowl(), rev + 1), a.player());
        InfusionAltars.onSeen(new InfusionPayloads.Seen(a.bowl(), rev - 1), a.player());
        InfusionAltars.onSeen(new InfusionPayloads.Seen(a.bowl(), Integer.MAX_VALUE), a.player());
        eq(
                helper,
                Objects.requireNonNull(InfusionAltars.armState(a.player()))[2],
                Integer.MIN_VALUE,
                "nothing recorded");
        InfusionAltars.onSeen(new InfusionPayloads.Seen(a.bowl(), rev), a.player());
        eq(
                helper,
                Objects.requireNonNull(InfusionAltars.armState(a.player()))[2],
                Integer.MIN_VALUE,
                "a fifth in one tick is dropped");
        ServerPlayer other = ResearchTestSupport.player(helper);
        InfusionAltars.onSeen(new InfusionPayloads.Seen(a.bowl(), rev), other);
        helper.assertTrue(InfusionAltars.armState(other) == null, "no arm, nothing");
        helper.succeed();
    }

    /** A whole run: drawn from a jar in the box, the offering absorbed, the output made. */
    private static void runCompletes(GameTestHelper helper, int band) {
        Altar a = altar(helper, band, new ItemStack(Items.STICK), new ItemStack(Items.FLINT));
        JarBlockEntity jar = jar(helper, a.rel().offset(1, 0, 1), AspectKeys.AER, 10);
        start(helper, a);
        helper.succeedWhen(
                () -> {
                    BowlBlockEntity bowl = bowl(helper, a.rel());
                    helper.assertFalse(bowl.ringing(), "still ringing");
                    helper.assertTrue(bowl.getTheItem().is(Items.BLAZE_ROD), "the output");
                    helper.assertTrue(
                            pedestal(helper, a.rel().offset(2, 0, 0)).getTheItem().isEmpty(),
                            "absorbed");
                    helper.assertTrue(
                            pedestal(helper, a.rel().offset(2, 0, 0)).claim() == null,
                            "claim cleared");
                    eq(helper, jar.contents().amount(), 8, "two drawn");
                    helper.assertTrue(
                            events(a.bowl()).stream()
                                    .anyMatch(e -> e instanceof InfusionPayload.Completed),
                            "COMPLETED");
                });
    }

    /** An unlearned striker reads NO_RECIPE. */
    private static void unlearned(GameTestHelper helper, int band) {
        Altar a = altar(helper, band, new ItemStack(Items.STICK), new ItemStack(Items.FLINT));
        ServerPlayer stranger = ResearchTestSupport.player(helper);
        stranger.setPos(a.player().position());
        eq(
                helper,
                InfusionAltars.readout(helper.getLevel(), a.bowl(), stranger).status(),
                InfusionStatus.NO_RECIPE,
                "no recipe");
        bowl(helper, a.rel()).setTheItem(ItemStack.EMPTY);
        eq(
                helper,
                InfusionAltars.readout(helper.getLevel(), a.bowl(), a.player()).status(),
                InfusionStatus.EMPTY,
                "empty");
        helper.succeed();
    }

    private static Holder<Enchantment> ench(GameTestHelper helper, ResourceKey<Enchantment> key) {
        return helper.getLevel()
                .registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(key);
    }

    /** A partly enchanted sword raised to the cap in one run, at the summed price and R. */
    private static void enchantToCap(GameTestHelper helper, int band) {
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        Holder<Enchantment> sharp = ench(helper, Enchantments.SHARPNESS);
        EnchantmentHelper.updateEnchantments(sword, m -> m.set(sharp, 2));
        Altar a = altar(helper, band, sword, new ItemStack(Items.QUARTZ));
        jar(helper, a.rel().offset(1, 0, 1), AspectKeys.TELUM, 100);
        InfusionReadout r = InfusionAltars.readout(helper.getLevel(), a.bowl(), a.player());
        eq(helper, r.status(), InfusionStatus.READY, "ready");
        var plan = Objects.requireNonNull(r.ready());
        eq(helper, plan.costs().getFirst().cost(), 12, "3 + 4 + 5 levels at 1 each");
        eq(helper, plan.instability(), 10 + 5 * (2 + 5 - 2 - 1), "R with the enchanting term");
        start(helper, a);
        helper.succeedWhen(
                () -> {
                    ItemStack out = bowl(helper, a.rel()).getTheItem();
                    helper.assertFalse(bowl(helper, a.rel()).ringing(), "finished");
                    eq(
                            helper,
                            EnchantmentHelper.getEnchantmentsForCrafting(out).getLevel(sharp),
                            5,
                            "raised to the cap");
                });
    }

    /** At the cap, a conflict and a book each refuse with their reason. */
    private static void enchantRefusals(GameTestHelper helper, int band) {
        Holder<Enchantment> sharp = ench(helper, Enchantments.SHARPNESS);
        ItemStack capped = new ItemStack(Items.IRON_SWORD);
        EnchantmentHelper.updateEnchantments(capped, m -> m.set(sharp, 5));
        Altar a = altar(helper, band, capped, new ItemStack(Items.QUARTZ));
        InfusionReadout r = InfusionAltars.readout(helper.getLevel(), a.bowl(), a.player());
        eq(
                helper,
                Objects.requireNonNull(r.reason()),
                InfusionStatus.EnchantRefusal.AT_MAX,
                "at max");
        ItemStack smite = new ItemStack(Items.IRON_SWORD);
        EnchantmentHelper.updateEnchantments(
                smite, m -> m.set(ench(helper, Enchantments.SMITE), 1));
        bowl(helper, a.rel()).setTheItem(smite);
        eq(
                helper,
                Objects.requireNonNull(
                        InfusionAltars.readout(helper.getLevel(), a.bowl(), a.player()).reason()),
                InfusionStatus.EnchantRefusal.CONFLICT,
                "conflict");
        bowl(helper, a.rel()).setTheItem(new ItemStack(Items.IRON_PICKAXE));
        eq(
                helper,
                InfusionAltars.readout(helper.getLevel(), a.bowl(), a.player()).status(),
                InfusionStatus.NO_RECIPE,
                "a pickaxe isn't a sharp weapon: no candidate");
        helper.succeed();
    }

    /** A transmuted output keeps the centre's components. */
    private static void transmute(GameTestHelper helper, int band) {
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.set(DataComponents.CUSTOM_NAME, Component.literal("Keeper"));
        Altar a = altar(helper, band, sword, new ItemStack(Items.GOLD_INGOT));
        InfusionReadout r = InfusionAltars.readout(helper.getLevel(), a.bowl(), a.player());
        eq(helper, r.status(), InfusionStatus.READY, "ready");
        ItemStack out = Objects.requireNonNull(r.ready()).output();
        helper.assertTrue(out.is(Items.GOLDEN_SWORD), "golden");
        eq(
                helper,
                Objects.requireNonNull(out.get(DataComponents.CUSTOM_NAME)).getString(),
                "Keeper",
                "kept the name");
        helper.succeed();
    }

    /** Two bowls sharing a pedestal: the second reads PROMISED and can't start. */
    private static void claimsRefuse(GameTestHelper helper, int band) {
        Altar a = altar(helper, band, new ItemStack(Items.STICK), new ItemStack(Items.FLINT));
        start(helper, a);
        BlockPos second = a.rel().offset(4, 0, 0); // the shared pedestal at +2 lies in both boxes
        helper.setBlock(second, InfusionContent.bowl());
        bowl(helper, second).setTheItem(new ItemStack(Items.STICK));
        BlockPos abs = helper.absolutePos(second);
        eq(
                helper,
                InfusionAltars.readout(helper.getLevel(), abs, a.player()).status(),
                InfusionStatus.PROMISED,
                "promised");
        PedestalBlockEntity shared = pedestal(helper, a.rel().offset(2, 0, 0));
        eq(
                helper,
                useEmpty(helper, a.player(), helper.absolutePos(a.rel().offset(2, 0, 0))),
                InteractionResult.CONSUME,
                "a claimed offering can't be taken");
        helper.assertFalse(shared.getTheItem().isEmpty(), "still there");
        helper.succeed();
    }

    /**
     * The item capability: claimed taking locked, restoration only, a ringing bowl locked, aborts
     * exact.
     */
    private static void handlerRules(GameTestHelper helper, int band) {
        Altar a = altar(helper, band, new ItemStack(Items.STICK), new ItemStack(Items.FLINT));
        ServerLevel level = helper.getLevel();
        BlockPos pRel = a.rel().offset(2, 0, 0);
        BlockPos pAbs = helper.absolutePos(pRel);
        ResourceHandler<ItemResource> ph =
                Objects.requireNonNull(level.getCapability(Capabilities.Item.BLOCK, pAbs, null));
        try (Transaction tx = Transaction.openRoot()) {
            eq(helper, ph.extract(ItemResource.of(Items.FLINT), 1, tx), 1, "unclaimed: taken");
            helper.assertTrue(pedestal(helper, pRel).busy(), "busy inside the transaction");
        } // aborted
        helper.assertTrue(
                pedestal(helper, pRel).getTheItem().is(Items.FLINT),
                "the abort left the slot exact");
        helper.assertFalse(pedestal(helper, pRel).busy(), "busy ends at the root's close");
        start(helper, a);
        try (Transaction tx = Transaction.openRoot()) {
            eq(helper, ph.extract(ItemResource.of(Items.FLINT), 1, tx), 0, "claimed: locked");
        }
        pedestal(helper, pRel).setTheItem(ItemStack.EMPTY); // jolted, say
        try (Transaction tx = Transaction.openRoot()) {
            eq(helper, ph.insert(ItemResource.of(Items.STICK), 1, tx), 0, "a wrong item refused");
            eq(
                    helper,
                    ph.insert(ItemResource.of(Items.FLINT), 1, tx),
                    1,
                    "the promised stack restores");
            tx.commit();
        }
        helper.assertTrue(pedestal(helper, pRel).getTheItem().is(Items.FLINT), "restored");
        ResourceHandler<ItemResource> bh =
                Objects.requireNonNull(
                        level.getCapability(Capabilities.Item.BLOCK, a.bowl(), null));
        try (Transaction tx = Transaction.openRoot()) {
            eq(
                    helper,
                    bh.extract(ItemResource.of(Items.STICK), 1, tx),
                    0,
                    "a ringing bowl is locked");
            eq(
                    helper,
                    bh.getCapacityAsLong(0, ItemResource.of(Items.STICK)),
                    0L,
                    "no room in a ringing bowl");
        }
        helper.succeed();
    }

    /** The hopper's container path agrees with the handler. */
    private static void containerPath(GameTestHelper helper, int band) {
        Altar a = altar(helper, band, new ItemStack(Items.STICK), new ItemStack(Items.FLINT));
        PedestalBlockEntity p = pedestal(helper, a.rel().offset(2, 0, 0));
        BowlBlockEntity b = bowl(helper, a.rel());
        helper.assertTrue(p.canTakeItem(p, 0, p.getTheItem()), "unclaimed: a hopper takes");
        helper.assertFalse(
                b.canPlaceItem(0, new ItemStack(Items.STICK)), "an occupied bowl refuses");
        start(helper, a);
        helper.assertFalse(p.canTakeItem(p, 0, p.getTheItem()), "claimed: locked");
        helper.assertFalse(b.canTakeItem(b, 0, b.getTheItem()), "ringing: locked");
        eq(helper, p.getMaxStackSize(), 1, "one item");
        helper.succeed();
    }

    /**
     * Arm, confirm: the run is abandoned, the centre stays, SPILLED(ABANDONED) and ABANDONED post.
     */
    private static void abandonFlow(GameTestHelper helper, int band) {
        Altar a = altar(helper, band, new ItemStack(Items.STICK), new ItemStack(Items.FLINT));
        start(helper, a);
        List<Component> lines = lines(a.player());
        ServerPlayer stranger = ResearchTestSupport.player(helper);
        stranger.setPos(a.player().position());
        eq(
                helper,
                useEmpty(helper, stranger, a.bowl()),
                InteractionResult.CONSUME,
                "unlearned refused");
        helper.assertTrue(says(lines(stranger), "message.vftv.infusion.not_yours"), "not yours");
        eq(helper, useEmpty(helper, a.player(), a.bowl()), InteractionResult.SUCCESS, "armed");
        helper.assertTrue(bowl(helper, a.rel()).ringing(), "still ringing while armed");
        helper.assertTrue(
                says(lines, "message.vftv.infusion.abandon_confirm"), "the price is shown");
        eq(helper, useEmpty(helper, a.player(), a.bowl()), InteractionResult.SUCCESS, "confirmed");
        helper.assertFalse(bowl(helper, a.rel()).ringing(), "abandoned");
        helper.assertTrue(bowl(helper, a.rel()).getTheItem().is(Items.STICK), "the centre stays");
        List<InfusionPayload> e = events(a.bowl());
        helper.assertTrue(
                e.stream()
                        .anyMatch(
                                p ->
                                        p instanceof InfusionPayload.Spilled s
                                                && s.cause() == SpillCause.ABANDONED),
                "spilled");
        helper.assertTrue(
                e.stream()
                        .anyMatch(
                                p ->
                                        p instanceof InfusionPayload.Abandoned ab
                                                && Objects.equals(a.player(), ab.player())),
                "abandoned by the starter");
        helper.assertTrue(
                pedestal(helper, a.rel().offset(2, 0, 0)).getTheItem().is(Items.FLINT),
                "the offering stays");
        helper.succeed();
    }

    /** Breaking a ringing bowl spills REMOVED once and drops the centre. */
    private static void removalSpills(GameTestHelper helper, int band) {
        Altar a = altar(helper, band, new ItemStack(Items.STICK), new ItemStack(Items.FLINT));
        start(helper, a);
        helper.getLevel().destroyBlock(a.bowl(), false);
        List<InfusionPayload> e = events(a.bowl());
        eq(
                helper,
                e.stream()
                        .filter(
                                p ->
                                        p instanceof InfusionPayload.Spilled s
                                                && s.cause() == SpillCause.REMOVED)
                        .count(),
                1L,
                "one spill");
        helper.assertTrue(
                e.stream()
                        .anyMatch(
                                p ->
                                        p instanceof InfusionPayload.Abandoned ab
                                                && ab.cause()
                                                        == com.andrewbrogoff.vftv.api.infusion
                                                                .AbandonCause.REMOVED),
                "abandoned: removed");
        helper.assertTrue(
                !helper.getLevel()
                        .getEntitiesOfClass(
                                net.minecraft.world.entity.item.ItemEntity.class,
                                new net.minecraft.world.phys.AABB(a.bowl()).inflate(1),
                                i -> i.getItem().is(Items.STICK))
                        .isEmpty(),
                "the centre dropped");
        helper.succeed();
    }

    /** A ringing bowl and its claimed pedestal survive a save and load unchanged. */
    private static void persistence(GameTestHelper helper, int band) {
        Altar a = altar(helper, band, new ItemStack(Items.STICK), new ItemStack(Items.FLINT));
        start(helper, a);
        BowlBlockEntity bowl = bowl(helper, a.rel());
        CompoundTag tag = bowl.saveWithFullMetadata(helper.getLevel().registryAccess());
        BlockEntity loaded =
                BlockEntity.loadStatic(
                        a.bowl(), bowl.getBlockState(), tag, helper.getLevel().registryAccess());
        helper.assertTrue(
                loaded instanceof BowlBlockEntity b
                        && b.run() != null
                        && b.run().seq() == 1
                        && b.id() == bowl.id()
                        && !b.unreadable(),
                "the run reloads");
        eq(
                helper,
                Objects.requireNonNull(loaded)
                        .saveWithFullMetadata(helper.getLevel().registryAccess()),
                tag,
                "byte-identical");
        PedestalBlockEntity p = pedestal(helper, a.rel().offset(2, 0, 0));
        CompoundTag ptag = p.saveWithFullMetadata(helper.getLevel().registryAccess());
        BlockEntity pl =
                BlockEntity.loadStatic(
                        p.getBlockPos(),
                        p.getBlockState(),
                        ptag,
                        helper.getLevel().registryAccess());
        helper.assertTrue(
                pl instanceof PedestalBlockEntity q && Objects.equals(q.claim(), p.claim()),
                "the claim reloads");
        helper.succeed();
    }

    /** A future format is kept byte-identical and refuses every hand use. */
    private static void unreadable(GameTestHelper helper, int band) {
        Altar a = altar(helper, band, new ItemStack(Items.STICK));
        BowlBlockEntity bowl = bowl(helper, a.rel());
        bowl.setChanged();
        CompoundTag tag = bowl.saveWithFullMetadata(helper.getLevel().registryAccess());
        tag.getCompound("infusion").orElseThrow().put("format", IntTag.valueOf(2));
        BlockEntity loaded =
                BlockEntity.loadStatic(
                        a.bowl(), bowl.getBlockState(), tag, helper.getLevel().registryAccess());
        BowlBlockEntity u = (BowlBlockEntity) Objects.requireNonNull(loaded);
        helper.assertTrue(u.unreadable(), "unreadable");
        eq(
                helper,
                u.saveWithFullMetadata(helper.getLevel().registryAccess()).getCompound("infusion"),
                tag.getCompound("infusion"),
                "kept");
        helper.getLevel().setBlockEntity(u);
        List<Component> lines = lines(a.player());
        eq(helper, use(helper, a.player(), a.bowl(), wand()), InteractionResult.CONSUME, "refused");
        helper.assertTrue(says(lines, "message.vftv.unreadable"), "unreadable said");
        eq(
                helper,
                InfusionAltars.readout(helper.getLevel(), a.bowl(), a.player()).status(),
                InfusionStatus.UNREADABLE,
                "UNREADABLE");
        helper.succeed();
    }

    /** The recipe codec rejects bad bodies; the stream codec round-trips both forms. */
    private static void recipeCodec(GameTestHelper helper) {
        var ops = helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
        String good =
                "{'research': 'vftv:infusion', 'centre': 'minecraft:stick', 'components': ['minecraft:flint'], 'essentia': {'vftv:aer': 2}, 'instability': 0, ";
        for (String bad :
                List.of(
                        "'result': {'enchantment': 'minecraft:sharpness', 'max_level': 5, 'transmute': true}}",
                        "'result': {'item': {'id': 'minecraft:stick'}, 'enchantment': 'minecraft:sharpness', 'max_level': 1}}",
                        "'result': {'enchantment': 'minecraft:sharpness'}}",
                        "'result': {'item': {'id': 'minecraft:stick'}, 'max_level': 1}}")) {
            JsonObject json =
                    JsonParser.parseString((good + bad).replace('\'', '"')).getAsJsonObject();
            helper.assertTrue(
                    InfusionRecipe.MAP_CODEC.codec().parse(ops, json).isError(),
                    "rejected: " + bad);
        }
        JsonObject dear =
                JsonParser.parseString(
                                "{'research': 'vftv:infusion', 'centre': 'minecraft:stick', 'components': ['minecraft:flint'], 'essentia': {'vftv:aer': 67}, 'instability': 0, 'result': {'enchantment': 'minecraft:sharpness', 'max_level': 5}}"
                                        .replace('\'', '"'))
                        .getAsJsonObject();
        helper.assertTrue(
                InfusionRecipe.MAP_CODEC.codec().parse(ops, dear).isError(),
                "67 x 15 is over 1000");
        JsonObject unknown =
                JsonParser.parseString(
                                "{'research': 'vftv:nope', 'centre': 'minecraft:stick', 'components': ['minecraft:flint'], 'essentia': {'vftv:aer': 1}, 'instability': 0, 'result': {'item': {'id': 'minecraft:stick'}}}"
                                        .replace('\'', '"'))
                        .getAsJsonObject();
        helper.assertTrue(
                InfusionRecipe.MAP_CODEC.codec().parse(ops, unknown).isError(), "unknown research");
        for (String id : List.of("infusion_simple", "infusion_enchant", "infusion_transmute")) {
            RecipeHolder<InfusionRecipe> holder =
                    Objects.requireNonNull(
                            InfusionRecipes.server()
                                    .get(Identifier.fromNamespaceAndPath("vftv_gametest", id)));
            var buf =
                    new net.minecraft.network.RegistryFriendlyByteBuf(
                            io.netty.buffer.Unpooled.buffer(),
                            helper.getLevel().registryAccess(),
                            net.neoforged.neoforge.network.connection.ConnectionType.NEOFORGE);
            InfusionRecipe.STREAM_CODEC.encode(buf, holder.value());
            InfusionRecipe back = InfusionRecipe.STREAM_CODEC.decode(buf);
            eq(helper, back.essentia(), holder.value().essentia(), id + " cost");
            eq(helper, back.result().getClass(), holder.value().result().getClass(), id + " form");
            helper.assertFalse(back.display().isEmpty(), id + " display");
        }
        helper.succeed();
    }

    /** The shown altar, placed from its content file: L 0, I 0, P at least 1. */
    private static void shownAltar(GameTestHelper helper, int band) {
        BlockPos rel = rel(band);
        java.nio.file.Path project =
                Objects.requireNonNull(
                        com.andrewbrogoff.vftv.core.common.model.PendingAudio.findProject(
                                java.nio.file.Path.of("").toAbsolutePath()));
        JsonObject file;
        try {
            file =
                    JsonParser.parseString(
                                    java.nio.file.Files.readString(
                                            project.resolve(
                                                    "src/generated/resources/assets/vftv/multiblock/infusion_altar.json")))
                            .getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        var b = file.getAsJsonArray("bowl");
        BlockPos origin =
                rel.offset(-b.get(0).getAsInt(), -b.get(1).getAsInt(), -b.get(2).getAsInt());
        for (var e : file.getAsJsonArray("blocks")) {
            var a = e.getAsJsonArray();
            BlockPos at =
                    origin.offset(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt());
            helper.setBlock(at.below(), Blocks.STONE);
            helper.setBlock(
                    at, BuiltInRegistries.BLOCK.getValue(Identifier.parse(a.get(3).getAsString())));
        }
        ServerPlayer player = ResearchTestSupport.player(helper);
        int[] lip = InfusionAltars.symmetry(helper.getLevel(), bowl(helper, rel), player);
        eq(helper, lip[0], 0, "L");
        eq(helper, lip[1], 0, "I");
        helper.assertTrue(lip[2] >= 1, "P at least 1");
        helper.succeed();
    }

    /** A jar outside the box is never drawn: the run waits on essentia. */
    private static void drawsInsideBox(GameTestHelper helper, int band) {
        Altar a = altar(helper, band, new ItemStack(Items.STICK), new ItemStack(Items.FLINT));
        JarBlockEntity far = jar(helper, a.rel().offset(5, 0, 0), AspectKeys.AER, 10);
        start(helper, a);
        helper.runAfterDelay(
                25,
                () -> {
                    eq(helper, far.contents().amount(), 10, "nothing drawn from outside the box");
                    helper.assertTrue(bowl(helper, a.rel()).ringing(), "waiting, unharmed");
                    InfusionReadout r =
                            InfusionAltars.readout(helper.getLevel(), a.bowl(), a.player());
                    eq(
                            helper,
                            Objects.requireNonNull(r.run()).stall(),
                            InfusionReadout.Stall.ESSENTIA,
                            "waiting on essentia");
                    eq(helper, r.run().upsets(), 0, "stalls never roll");
                    helper.succeed();
                });
    }

    /** The preview without a context shows no figures; with this recipe's readout it shows them. */
    private static void preview(GameTestHelper helper, int band) {
        Altar a = altar(helper, band, new ItemStack(Items.STICK), new ItemStack(Items.FLINT));
        RecipeHolder<InfusionRecipe> holder =
                Objects.requireNonNull(
                        InfusionRecipes.server()
                                .get(
                                        Identifier.fromNamespaceAndPath(
                                                "vftv_gametest", "infusion_simple")));
        InfusionPreview none = InfusionPreview.of(a.player(), holder, null);
        eq(helper, none.availability(), InfusionPreview.Availability.NO_ALTAR, "no altar");
        Component stab =
                Objects.requireNonNull(
                        none.fact(holder.value(), "stability", List.of(), List.of()));
        helper.assertTrue(
                stab.getContents() instanceof TranslatableContents t
                        && t.getKey().equals("infusion.vftv.status.no_altar"),
                "absent, never a number");
        InfusionReadout r = InfusionAltars.readout(helper.getLevel(), a.bowl(), a.player());
        InfusionPreview with =
                InfusionPreview.of(
                        a.player(),
                        holder,
                        new InfusionPreview.Context(
                                InfusionPayloads.Kind.ARMED,
                                helper.getLevel().dimension(),
                                a.bowl(),
                                0,
                                r));
        eq(
                helper,
                with.availability(),
                InfusionPreview.Availability.SHORT_NOW,
                "short now: no vessel");
        eq(
                helper,
                Objects.requireNonNull(with.fact(holder.value(), "stability", List.of(), List.of()))
                        .getString(),
                "94%",
                "stability");
        eq(
                helper,
                Objects.requireNonNull(with.fact(holder.value(), "chance", List.of(), List.of()))
                        .getString(),
                "0.9%",
                "chance");
        helper.assertTrue(with.checkedAt() != null, "the context is named");
        helper.assertTrue(
                with.fact(holder.value(), "nonsense", List.of(), List.of()) == null,
                "unknown field");
        helper.succeed();
    }

    /** Twenty strikes in one tick: at most 16 assessed, the rest queued and serviced next tick. */
    private static void budget(GameTestHelper helper, int band) {
        Altar a = altar(helper, band, new ItemStack(Items.STICK), new ItemStack(Items.FLINT));
        List<ServerPlayer> players = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            ServerPlayer p = onlinePlayer(helper);
            p.setPos(a.player().position());
            ResearchTestSupport.learn(p, INFUSION);
            players.add(p);
        }
        for (ServerPlayer p : players) {
            use(helper, p, a.bowl(), wand());
        }
        int[] stats = InfusionAltars.queueStats(helper.getLevel());
        helper.assertTrue(stats[4] <= 16, "at most 16 assessments this tick");
        helper.assertTrue(stats[3] >= 4, "the rest wait");
        helper.runAfterDelay(
                2,
                () -> {
                    eq(
                            helper,
                            InfusionAltars.queueStats(helper.getLevel())[3],
                            0,
                            "every pending one serviced");
                    for (ServerPlayer p : players) {
                        helper.assertTrue(
                                InfusionAltars.armState(p) != null, "every striker has an arm");
                    }
                    helper.succeed();
                });
    }

    /** The tuning file parses with its rules; a bad one is rejected. */
    private static void tuningRules(GameTestHelper helper) {
        helper.assertTrue(
                InfusionAltars.parse("{\"upset.max\": 64, \"upset.spill\": 200}").isError(),
                "the product bound");
        helper.assertTrue(InfusionAltars.parse("{}").isSuccess(), "defaults");
        eq(
                helper,
                InfusionAltars.rules().generation(),
                com.andrewbrogoff.vftv.core.infusion.InfusionRules.DEFAULTS.generation(),
                "the shipped tuning");
        helper.succeed();
    }
}
