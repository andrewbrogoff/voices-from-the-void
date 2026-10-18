package com.andrewbrogoff.vftv.gametest;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.api.essentia.EssentiaKeys;
import com.andrewbrogoff.vftv.api.essentia.EssentiaLimits;
import com.andrewbrogoff.vftv.api.essentia.EssentiaPayload;
import com.andrewbrogoff.vftv.api.essentia.EssentiaSuction;
import com.andrewbrogoff.vftv.api.essentia.SpillCause;
import com.andrewbrogoff.vftv.api.neoforge.essentia.EssentiaCapabilities;
import com.andrewbrogoff.vftv.api.neoforge.essentia.EssentiaEvent;
import com.andrewbrogoff.vftv.api.neoforge.essentia.EssentiaResource;
import com.andrewbrogoff.vftv.core.common.RawRecordCodec.Retained;
import com.andrewbrogoff.vftv.core.crucible.CrucibleBlock;
import com.andrewbrogoff.vftv.core.crucible.CrucibleBlockEntity;
import com.andrewbrogoff.vftv.core.crucible.CrucibleContent;
import com.andrewbrogoff.vftv.core.essentia.AlembicBlock;
import com.andrewbrogoff.vftv.core.essentia.EssentiaContent;
import com.andrewbrogoff.vftv.core.essentia.EssentiaLines;
import com.andrewbrogoff.vftv.core.essentia.EssentiaRecords.JarStack;
import com.andrewbrogoff.vftv.core.essentia.EssentiaRules;
import com.andrewbrogoff.vftv.core.essentia.EssentiaService;
import com.andrewbrogoff.vftv.core.essentia.EssentiaTubeBlock;
import com.andrewbrogoff.vftv.core.essentia.EssentiaTubeBlock.Face;
import com.andrewbrogoff.vftv.core.essentia.JarBlockEntity;
import com.andrewbrogoff.vftv.core.essentia.LabelledBlockEntity;
import com.andrewbrogoff.vftv.core.essentia.Labels;
import com.andrewbrogoff.vftv.core.essentia.TubeValveBlock;
import com.andrewbrogoff.vftv.core.essentia.model.EssentiaMath.Contents;
import com.andrewbrogoff.vftv.core.text.StatusLines;
import com.andrewbrogoff.vftv.core.text.model.SharedMessages;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * The essentia feature's GameTests. Name each test {@code essentia_<what>}, and list its id {@code
 * vftv:essentia_<what>} in {@code src/gameTest/expected/essentia.txt}.
 *
 * <p>Fixture: this mod's data adds {@code vftv:essentia_jar} to {@code #vftv:alembic_bases}, the
 * spec's pack-extended base, so the Alembic is testable before the crucible exists.
 */
final class EssentiaGameTests {
    private static final Map<Long, List<EssentiaPayload.Spilled>> SPILLS =
            new ConcurrentHashMap<>();
    private static final Map<UUID, List<Component>> LINES = new ConcurrentHashMap<>();
    private static volatile boolean throwOnSpill;

    private EssentiaGameTests() {}

    /**
     * Adds the feature's tests by name; {@code modBus} is for any test-only registration they need.
     */
    static void register(Map<String, Consumer<GameTestHelper>> tests, IEventBus modBus) {
        tests.put("essentia_tuning_and_tags", EssentiaGameTests::tuningAndTags);
        tests.put("essentia_handler_rules", EssentiaGameTests::handlerRules);
        tests.put("essentia_handler_transactions", EssentiaGameTests::handlerTransactions);
        tests.put("essentia_label_rows", EssentiaGameTests::labelRows);
        tests.put("essentia_shears_and_copy", EssentiaGameTests::shearsAndCopy);
        tests.put("essentia_jar_break_keeps_contents", EssentiaGameTests::jarBreakKeepsContents);
        tests.put("essentia_jar_replaced_spills", EssentiaGameTests::jarReplacedSpills);
        tests.put("essentia_removal_mid_transaction", EssentiaGameTests::removalMidTransaction);
        tests.put("essentia_reserved_extracts_nothing", EssentiaGameTests::reservedExtractsNothing);
        tests.put(
                "essentia_item_destroyed_spills_once", EssentiaGameTests::itemDestroyedSpillsOnce);
        tests.put("essentia_line_moves", EssentiaGameTests::lineMoves);
        tests.put("essentia_gate_blocks", EssentiaGameTests::gateBlocks);
        tests.put("essentia_valve", EssentiaGameTests::valve);
        tests.put("essentia_faces_and_glyph_face", EssentiaGameTests::facesAndGlyphFace);
        tests.put("essentia_admission_and_overgrown", EssentiaGameTests::admissionAndOvergrown);
        tests.put("essentia_alembic_pours", EssentiaGameTests::alembicPours);
        tests.put(
                "essentia_persistence_and_unreadable", EssentiaGameTests::persistenceAndUnreadable);
        tests.put("essentia_status_pages", EssentiaGameTests::statusPages);
        tests.put("essentia_crucible_cross", EssentiaGameTests::crucibleCross);
        NeoForge.EVENT_BUS.addListener(
                EssentiaEvent.class,
                e -> {
                    if (e.payload() instanceof EssentiaPayload.Spilled s) {
                        SPILLS.computeIfAbsent(s.pos().asLong(), k -> new ArrayList<>()).add(s);
                        if (throwOnSpill) {
                            throw new IllegalStateException("a throwing spill listener");
                        }
                    }
                });
        StatusLines.swapSender(
                (player, line) ->
                        LINES.computeIfAbsent(player.getUUID(), k -> new ArrayList<>())
                                .add(line.text()));
    }

    // --- fixtures -----------------------------------------------------------------------------

    private static ServerPlayer player(GameTestHelper helper, GameType mode) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(mode);
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        new ServerGamePacketListenerImpl(
                server,
                connection,
                player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false));
        Vec3 at = helper.absoluteVec(new Vec3(0.5, 1, -1.5));
        player.snapTo(at.x, at.y, at.z, 0, 0);
        return player;
    }

    private static @Nullable String lastKey(ServerPlayer player) {
        List<Component> lines = LINES.getOrDefault(player.getUUID(), List.of());
        return lines.isEmpty()
                ? null
                : lines.getLast().getContents() instanceof TranslatableContents t
                        ? t.getKey()
                        : null;
    }

    private static List<EssentiaPayload.Spilled> spills(GameTestHelper helper, BlockPos rel) {
        return SPILLS.getOrDefault(helper.absolutePos(rel).asLong(), List.of());
    }

    private static Holder<Aspect> holder(GameTestHelper helper, ResourceKey<Aspect> key) {
        return helper.getLevel()
                .registryAccess()
                .lookupOrThrow(AspectKeys.REGISTRY)
                .getOrThrow(key);
    }

    private static JarBlockEntity jar(
            GameTestHelper helper,
            BlockPos rel,
            @Nullable ResourceKey<Aspect> aspect,
            int amount,
            @Nullable ResourceKey<Aspect> label) {
        helper.setBlock(rel, EssentiaContent.jar());
        JarBlockEntity jar =
                Objects.requireNonNull(helper.getBlockEntity(rel, JarBlockEntity.class));
        jar.setLive(aspect == null ? Contents.empty() : new Contents<>(aspect, amount));
        if (label != null) {
            jar.setLabel(label);
        }
        return jar;
    }

    /** Places a block with its faces computed from its neighbours, as a player's placement does. */
    private static void place(GameTestHelper helper, BlockPos rel, BlockState state) {
        BlockPos abs = helper.absolutePos(rel);
        helper.getLevel()
                .setBlock(
                        abs,
                        Block.updateFromNeighbourShapes(state, helper.getLevel(), abs),
                        Block.UPDATE_ALL);
    }

    private static void tube(GameTestHelper helper, BlockPos rel) {
        place(helper, rel, EssentiaContent.tube().defaultBlockState());
    }

    // NullAway can't match the capability's @Nullable Direction context type argument.
    @SuppressWarnings("NullAway")
    private static @Nullable EssentiaSuction suction(GameTestHelper helper, BlockPos rel) {
        return helper.getLevel()
                .getCapability(EssentiaCapabilities.SUCTION, helper.absolutePos(rel), null);
    }

    // NullAway can't match the capability's @Nullable Direction context type argument.
    @SuppressWarnings("NullAway")
    private static ResourceHandler<EssentiaResource> handler(GameTestHelper helper, BlockPos rel) {
        return Objects.requireNonNull(
                helper.getLevel()
                        .getCapability(EssentiaCapabilities.HANDLER, helper.absolutePos(rel), null),
                "the jar's handler");
    }

    private static InteractionResult use(
            GameTestHelper helper, ServerPlayer player, BlockPos rel, ItemStack held) {
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        BlockPos pos = helper.absolutePos(rel);
        return player.gameMode.useItemOn(
                player,
                helper.getLevel(),
                held,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false));
    }

    private static void eq(
            GameTestHelper helper,
            @Nullable Object actual,
            @Nullable Object expected,
            String what) {
        helper.assertTrue(
                Objects.equals(actual, expected), what + ": " + actual + ", not " + expected);
    }

    private static List<ItemEntity> items(GameTestHelper helper) {
        return helper.getLevel()
                .getEntitiesOfClass(
                        ItemEntity.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(8));
    }

    private static Contents<ResourceKey<Aspect>> contents(GameTestHelper helper, BlockPos rel) {
        return Objects.requireNonNull(helper.getBlockEntity(rel, JarBlockEntity.class)).contents();
    }

    // --- tests --------------------------------------------------------------------------------

    /** The tuning in force is the shipped defaults; the tags hold the jar. */
    private static void tuningAndTags(GameTestHelper helper) {
        eq(helper, EssentiaService.rules(), EssentiaRules.DEFAULTS, "the shipped tuning");
        helper.assertTrue(
                EssentiaService.parse("{\"suction.jar\": 9}").isError(), "jar rank above labelled");
        EssentiaRules before = EssentiaService.rules();
        EssentiaService.applyTuning(EssentiaService.parse("{\"tube.rate\": 0}"), true);
        eq(helper, EssentiaService.rules(), before, "a /reload keeps the previous");
        helper.assertTrue(
                EssentiaContent.jar().defaultBlockState().is(EssentiaKeys.ESSENTIA_ENDPOINTS),
                "jars are endpoints");
        helper.assertTrue(
                EssentiaContent.jar().defaultBlockState().is(EssentiaKeys.ALEMBIC_BASES),
                "the fixture's base");
        helper.succeed();
    }

    /** Every handler method row, argument checks first, and the unavailable states. */
    private static void handlerRules(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 1);
        jar(helper, at, null, 0, AspectKeys.AER);
        ResourceHandler<EssentiaResource> h = handler(helper, at);
        EssentiaResource aer = new EssentiaResource(holder(helper, AspectKeys.AER));
        EssentiaResource terra = new EssentiaResource(holder(helper, AspectKeys.TERRA));
        eq(helper, h.size(), 1, "one slot");
        eq(helper, h.getCapacityAsLong(0, aer), 100L, "the capacity for its label");
        eq(helper, h.getCapacityAsLong(0, terra), 0L, "a label locks it");
        eq(helper, h.getCapacityAsLong(1, aer), 0L, "index 1");
        helper.assertTrue(
                h.isValid(0, aer) && !h.isValid(0, terra) && !h.isValid(1, aer), "isValid");
        try (Transaction tx = Transaction.openRoot()) {
            for (Consumer<Transaction> bad :
                    List.<Consumer<Transaction>>of(
                            t -> h.insert(EssentiaResource.EMPTY, 1, t),
                            t -> h.insert(0, aer, -1, t),
                            t -> h.extract(EssentiaResource.EMPTY, 1, t),
                            t -> h.extract(5, aer, -1, t))) {
                boolean threw = false;
                try {
                    bad.accept(tx);
                } catch (IllegalArgumentException e) {
                    threw = true;
                }
                helper.assertTrue(threw, "arguments are checked first");
            }
        }
        try (Transaction tx = Transaction.openRoot()) {
            eq(helper, h.insert(1, aer, 5, tx), 0, "index 1 inserts nothing");
            eq(helper, h.insert(terra, 5, tx), 0, "a mismatch inserts nothing");
            eq(helper, h.insert(aer, 150, tx), 100, "up to capacity");
            eq(helper, h.extract(terra, 5, tx), 0, "only the held aspect");
            eq(helper, h.extract(aer, 30, tx), 30, "extract");
            tx.commit();
        }
        eq(helper, h.getAmountAsLong(0), 70L, "committed");
        eq(helper, h.getResource(0), aer, "Aer held");
        helper.succeed();
    }

    /**
     * A child commit undone by a root abort; busy until the root closes; one snapshot per depth.
     */
    private static void handlerTransactions(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 1);
        JarBlockEntity jar = jar(helper, at, AspectKeys.AER, 50, null);
        ResourceHandler<EssentiaResource> h = handler(helper, at);
        EssentiaResource aer = new EssentiaResource(holder(helper, AspectKeys.AER));
        try (Transaction root = Transaction.openRoot()) {
            try (Transaction child = Transaction.open(root)) {
                h.extract(aer, 20, child);
                h.extract(aer, 5, child);
                child.commit();
            }
            helper.assertTrue(jar.busy(), "busy while the root is open");
            eq(helper, jar.contents().amount(), 25, "the child's view");
        }
        helper.assertTrue(!jar.busy(), "released on abort");
        eq(helper, jar.contents().amount(), 50, "the root abort restored it exactly");
        helper.succeed();
    }

    /** The label table's rows, consumption and refusals. */
    private static void labelRows(GameTestHelper helper) {
        ServerPlayer player = player(helper, GameType.SURVIVAL);
        BlockPos full = new BlockPos(1, 1, 1);
        BlockPos empty = new BlockPos(2, 1, 1);
        JarBlockEntity aerJar = jar(helper, full, AspectKeys.AER, 10, null);
        JarBlockEntity emptyJar = jar(helper, empty, null, 0, null);

        ItemStack blank = new ItemStack(EssentiaContent.label(), 3);
        eq(helper, use(helper, player, empty, blank), InteractionResult.CONSUME, "blank on empty");
        eq(helper, lastKey(player), Labels.NOTHING_TO_NAME, "nothing to name it by");
        eq(helper, use(helper, player, full, blank), InteractionResult.SUCCESS, "blank on Aer");
        eq(helper, aerJar.label(), AspectKeys.AER, "labelled Aer");
        eq(helper, blank.getCount(), 2, "one label consumed");
        ItemStack aerLabel = Labels.labelStack(AspectKeys.AER);
        eq(
                helper,
                use(helper, player, full, aerLabel),
                InteractionResult.CONSUME,
                "identical: no-op");
        eq(helper, aerLabel.getCount(), 1, "nothing consumed");
        ItemStack terraLabel = Labels.labelStack(AspectKeys.TERRA);
        use(helper, player, full, terraLabel);
        eq(helper, lastKey(player), Labels.CUT_FIRST, "cut it off first");
        use(helper, player, empty, terraLabel.copy());
        eq(helper, emptyJar.label(), AspectKeys.TERRA, "an empty jar locks to Terra");
        BlockPos other = new BlockPos(3, 1, 1);
        jar(helper, other, AspectKeys.IGNIS, 4, null);
        use(helper, player, other, Labels.labelStack(AspectKeys.AER));
        eq(helper, lastKey(player), Labels.MISMATCH, "that isn't what's inside");
        ItemStack ghost =
                Labels.labelStack(
                        ResourceKey.create(
                                AspectKeys.REGISTRY,
                                net.minecraft.resources.Identifier.fromNamespaceAndPath(
                                        "vftv", "ghost")));
        use(helper, player, other, ghost);
        eq(helper, lastKey(player), Labels.LABEL_UNREADABLE, "a missing aspect's label");
        // Busy: a jar touched by an open root refuses.
        BlockPos busy = new BlockPos(4, 1, 1);
        jar(helper, busy, AspectKeys.AER, 10, null);
        try (Transaction tx = Transaction.openRoot()) {
            handler(helper, busy)
                    .extract(new EssentiaResource(holder(helper, AspectKeys.AER)), 1, tx);
            use(helper, player, busy, new ItemStack(EssentiaContent.label()));
            eq(helper, lastKey(player), SharedMessages.BUSY, "not now");
        }
        // Creative: nothing consumed.
        ServerPlayer creative = player(helper, GameType.CREATIVE);
        BlockPos c = new BlockPos(1, 1, 2);
        jar(helper, c, AspectKeys.AQUA, 3, null);
        ItemStack one = new ItemStack(EssentiaContent.label());
        use(helper, creative, c, one);
        eq(helper, one.getCount(), 1, "infinite materials keep the label");
        helper.succeed();
    }

    /** Shears cut a label off and hand it over; sneaking with a blank label copies a jar's. */
    private static void shearsAndCopy(GameTestHelper helper) {
        ServerPlayer player = player(helper, GameType.SURVIVAL);
        BlockPos at = new BlockPos(1, 1, 1);
        JarBlockEntity jar = jar(helper, at, null, 0, AspectKeys.TERRA);
        ItemStack shears = new ItemStack(Items.SHEARS);
        eq(helper, use(helper, player, at, shears), InteractionResult.SUCCESS, "cut");
        helper.assertTrue(jar.label() == null, "the label is gone");
        eq(helper, shears.getDamageValue(), 1, "one durability");
        helper.assertTrue(
                player.getInventory()
                        .contains(
                                s ->
                                        s.is(EssentiaContent.label())
                                                && AspectKeys.TERRA.equals(
                                                        s.get(EssentiaContent.labelComponent()))),
                "the label is handed over");
        // Unlabelled: the shears try the empty hand, which the jar passes.
        eq(helper, use(helper, player, at, shears), InteractionResult.PASS, "nothing to cut");
        eq(helper, shears.getDamageValue(), 1, "no wear for nothing");
        // Copy: sneaking, blank label on a jar holding Aer.
        BlockPos full = new BlockPos(2, 1, 1);
        jar(helper, full, AspectKeys.AER, 5, null);
        player.setShiftKeyDown(true);
        ItemStack blank = new ItemStack(EssentiaContent.label(), 2);
        use(helper, player, full, blank);
        eq(helper, blank.getCount(), 1, "one blank used");
        helper.assertTrue(
                player.getInventory()
                        .contains(
                                s ->
                                        AspectKeys.AER.equals(
                                                s.get(EssentiaContent.labelComponent()))),
                "an Aer label copied");
        helper.succeed();
    }

    /** A survival break drops the jar with its contents; a creative break of a full jar too. */
    private static void jarBreakKeepsContents(GameTestHelper helper) {
        ServerPlayer player = player(helper, GameType.SURVIVAL);
        BlockPos at = new BlockPos(1, 1, 1);
        jar(helper, at, AspectKeys.AER, 40, AspectKeys.AER);
        player.gameMode.destroyBlock(helper.absolutePos(at));
        helper.assertBlockNotPresent(EssentiaContent.jar(), at);
        List<ItemEntity> drops = items(helper);
        ItemEntity drop =
                drops.stream()
                        .filter(e -> e.getItem().is(EssentiaContent.jarItem()))
                        .findFirst()
                        .orElse(null);
        helper.assertTrue(drop != null, "the jar dropped");
        if (drop == null) {
            return;
        }
        Retained<JarStack> stack = drop.getItem().get(EssentiaContent.contents());
        helper.assertTrue(
                stack instanceof Retained.Read<JarStack> r
                        && r.value().amount() == 40
                        && r.value().aspect().equals(AspectKeys.AER),
                "with Aer 40");
        eq(
                helper,
                drop.getItem().get(EssentiaContent.labelComponent()),
                AspectKeys.AER,
                "and its label");
        helper.assertTrue(spills(helper, at).isEmpty(), "nothing spilled");
        // Placing the item back restores the jar.
        BlockPos again = new BlockPos(2, 1, 1);
        helper.setBlock(again, EssentiaContent.jar());
        JarBlockEntity placed =
                Objects.requireNonNull(helper.getBlockEntity(again, JarBlockEntity.class));
        placed.applyComponents(
                drop.getItem().getComponents(),
                net.minecraft.core.component.DataComponentPatch.EMPTY);
        eq(
                helper,
                placed.contents(),
                new Contents<>(AspectKeys.AER, 40),
                "placed with its contents");
        drop.discard();
        // Creative break of a full jar spawns the filled item.
        ServerPlayer creative = player(helper, GameType.CREATIVE);
        BlockPos c = new BlockPos(3, 1, 1);
        jar(helper, c, AspectKeys.TERRA, 7, null);
        creative.gameMode.destroyBlock(helper.absolutePos(c));
        helper.assertTrue(
                items(helper).stream()
                        .anyMatch(
                                e ->
                                        e.getItem().get(EssentiaContent.contents())
                                                        instanceof Retained.Read<JarStack> r
                                                && r.value().amount() == 7),
                "creative keeps it too");
        helper.assertTrue(spills(helper, c).isEmpty(), "and spills nothing");
        helper.succeed();
    }

    /** A replacement with no drop spills everything as REMOVED; flag 256 loses it silently. */
    private static void jarReplacedSpills(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 1);
        jar(helper, at, AspectKeys.IGNIS, 33, null);
        helper.setBlock(at, Blocks.STONE);
        List<EssentiaPayload.Spilled> s = spills(helper, at);
        eq(helper, s.size(), 1, "one spill");
        eq(helper, s.getFirst().cause(), SpillCause.REMOVED, "REMOVED");
        eq(helper, s.getFirst().aspects(), Map.of(AspectKeys.IGNIS, 33), "the whole contents");
        BlockPos quiet = new BlockPos(2, 1, 1);
        jar(helper, quiet, AspectKeys.IGNIS, 33, null);
        helper.getLevel()
                .setBlock(
                        helper.absolutePos(quiet),
                        Blocks.STONE.defaultBlockState(),
                        Block.UPDATE_ALL | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS);
        helper.assertTrue(spills(helper, quiet).isEmpty(), "flag 256: no event");
        // A throwing listener: the spill stands and is not retried.
        throwOnSpill = true;
        try {
            BlockPos loud = new BlockPos(3, 1, 1);
            jar(helper, loud, AspectKeys.AER, 2, null);
            helper.setBlock(loud, Blocks.STONE);
            eq(helper, spills(helper, loud).size(), 1, "posted once despite the throw");
        } finally {
            throwOnSpill = false;
        }
        helper.succeed();
    }

    /**
     * Removal while transaction-busy waits for the root's close; it spills exactly the settled
     * contents minus the reserved carry (the carry vectors (a) on abort, and (b) on commit).
     */
    private static void removalMidTransaction(GameTestHelper helper) {
        EssentiaResource aer = new EssentiaResource(holder(helper, AspectKeys.AER));
        EssentiaResource ignis = new EssentiaResource(holder(helper, AspectKeys.IGNIS));
        BlockPos a = new BlockPos(1, 1, 1);
        JarBlockEntity jarA = jar(helper, a, AspectKeys.AER, 100, null);
        ResourceHandler<EssentiaResource> ha = handler(helper, a);
        try (Transaction root = Transaction.openRoot()) {
            try (Transaction n1 = Transaction.open(root)) {
                ha.extract(aer, 50, n1);
                n1.commit();
            }
            try (Transaction n2 = Transaction.open(root)) {
                ha.insert(aer, 30, n2);
                jarA.reserveForDrop();
                helper.getLevel().destroyBlock(helper.absolutePos(a), false);
                helper.assertTrue(spills(helper, a).isEmpty(), "settlement waits for the root");
            }
            // Root aborts here: settles Aer 100, carried Aer 50, spills Aer 50.
        }
        eq(
                helper,
                spills(helper, a).getFirst().aspects(),
                Map.of(AspectKeys.AER, 50),
                "(a) abort spills the excess");
        BlockPos b = new BlockPos(2, 1, 1);
        JarBlockEntity jarB = jar(helper, b, AspectKeys.AER, 100, null);
        ResourceHandler<EssentiaResource> hb = handler(helper, b);
        try (Transaction root = Transaction.openRoot()) {
            hb.extract(aer, 100, root);
            hb.insert(ignis, 100, root);
            jarB.reserveForDrop();
            helper.getLevel().destroyBlock(helper.absolutePos(b), false);
            root.commit();
        }
        eq(
                helper,
                spills(helper, b).getFirst().aspects(),
                Map.of(AspectKeys.IGNIS, 100),
                "(b) commit spills Ignis 100");
        helper.succeed();
    }

    /**
     * After the reservation, a drop callback's extract gets 0; a surviving reservation releases.
     */
    private static void reservedExtractsNothing(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 1);
        JarBlockEntity jar = jar(helper, at, AspectKeys.AER, 10, null);
        ResourceHandler<EssentiaResource> h = handler(helper, at);
        jar.reserveForDrop();
        try (Transaction tx = Transaction.openRoot()) {
            eq(
                    helper,
                    h.extract(new EssentiaResource(holder(helper, AspectKeys.AER)), 10, tx),
                    0,
                    "unavailable once reserved");
        }
        helper.runAfterDelay(
                2,
                () -> {
                    helper.assertTrue(
                            jar.available(), "a cancelled break releases the reservation");
                    helper.succeed();
                });
    }

    /** A destroyed stack of filled jars spills count × contents, once. */
    private static void itemDestroyedSpillsOnce(GameTestHelper helper) {
        ItemStack stack = new ItemStack(EssentiaContent.jarItem(), 3);
        stack.set(
                EssentiaContent.contents(),
                new Retained.Read<>(new JarStack(1, AspectKeys.AQUA, 25)));
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 1, 1.5));
        ItemEntity entity = new ItemEntity(helper.getLevel(), at.x, at.y, at.z, stack);
        helper.getLevel().addFreshEntity(entity);
        ServerLevel level = helper.getLevel();
        entity.hurtServer(level, level.damageSources().lava(), 100);
        entity.getItem().onDestroyed(entity, level.damageSources().lava());
        List<EssentiaPayload.Spilled> s =
                SPILLS.getOrDefault(entity.blockPosition().asLong(), List.of());
        eq(helper, s.size(), 1, "spilled once, even when destroyed again");
        eq(helper, s.getFirst().cause(), SpillCause.DESTROYED, "DESTROYED");
        eq(helper, s.getFirst().aspects(), Map.of(AspectKeys.AQUA, 75), "3 × 25");
        eq(
                helper,
                EssentiaLimits.MAX_ESSENTIA_SPILL,
                64 * EssentiaLimits.MAX_JAR_AMOUNT,
                "the bound");
        helper.succeed();
    }

    /** An unlabelled jar feeds a labelled one through a tube, 8 a pulse, and the line sleeps. */
    private static void lineMoves(GameTestHelper helper) {
        jar(helper, new BlockPos(0, 1, 1), AspectKeys.AER, 20, null);
        jar(helper, new BlockPos(3, 1, 1), null, 0, AspectKeys.AER);
        tube(helper, new BlockPos(1, 1, 1));
        tube(helper, new BlockPos(2, 1, 1));
        helper.succeedWhen(
                () -> {
                    eq(
                            helper,
                            contents(helper, new BlockPos(3, 1, 1)),
                            new Contents<>(AspectKeys.AER, 20),
                            "all 20 moved");
                    eq(
                            helper,
                            contents(helper, new BlockPos(0, 1, 1)),
                            Contents.<ResourceKey<Aspect>>empty(),
                            "source empty");
                    EssentiaLines.View v =
                            EssentiaService.lines(helper.getLevel())
                                    .of(helper.absolutePos(new BlockPos(1, 1, 1)));
                    eq(helper, v.members(), 2, "two members");
                    eq(helper, v.endpoints(), 2, "two vessels");
                    eq(helper, v.lastAspect(), AspectKeys.AER, "the last move");
                });
    }

    /** A gate labelled Aer passes Aer and holds back Ignis. */
    private static void gateBlocks(GameTestHelper helper) {
        jar(helper, new BlockPos(0, 1, 1), AspectKeys.IGNIS, 10, null);
        jar(helper, new BlockPos(3, 1, 1), null, 0, AspectKeys.IGNIS);
        tube(helper, new BlockPos(1, 1, 1));
        tube(helper, new BlockPos(2, 1, 1));
        LabelledBlockEntity gate =
                Objects.requireNonNull(
                        helper.getBlockEntity(new BlockPos(2, 1, 1), LabelledBlockEntity.class));
        gate.setLabel(AspectKeys.AER);
        helper.runAfterDelay(
                40,
                () -> {
                    eq(
                            helper,
                            contents(helper, new BlockPos(3, 1, 1)),
                            Contents.<ResourceKey<Aspect>>empty(),
                            "Ignis never crosses an Aer gate");
                    gate.setLabel(null);
                    helper.succeedWhen(
                            () ->
                                    eq(
                                            helper,
                                            contents(helper, new BlockPos(3, 1, 1)).amount(),
                                            10,
                                            "an unlabelled tube passes it"));
                });
    }

    /** A closed valve is a wall; opening by hand joins; redstone holds it shut. */
    private static void valve(GameTestHelper helper) {
        jar(helper, new BlockPos(0, 1, 1), AspectKeys.TERRA, 16, null);
        jar(helper, new BlockPos(3, 1, 1), null, 0, AspectKeys.TERRA);
        place(
                helper,
                new BlockPos(2, 1, 1),
                EssentiaContent.valve()
                        .defaultBlockState()
                        .setValue(TubeValveBlock.AXIS, Direction.Axis.X)
                        .setValue(TubeValveBlock.OPEN, false));
        tube(helper, new BlockPos(1, 1, 1));
        helper.runAfterDelay(
                30,
                () -> {
                    eq(
                            helper,
                            contents(helper, new BlockPos(3, 1, 1)).amount(),
                            0,
                            "closed: nothing passes");
                    helper.setBlock(new BlockPos(2, 2, 1), Blocks.REDSTONE_BLOCK);
                    BlockState valve = helper.getBlockState(new BlockPos(2, 1, 1));
                    helper.assertTrue(
                            valve.getValue(TubeValveBlock.POWERED), "powered by redstone");
                    helper.setBlock(
                            new BlockPos(2, 1, 1), valve.setValue(TubeValveBlock.OPEN, true));
                    helper.runAfterDelay(
                            30,
                            () -> {
                                eq(
                                        helper,
                                        contents(helper, new BlockPos(3, 1, 1)).amount(),
                                        0,
                                        "powered overrides open");
                                helper.setBlock(new BlockPos(2, 2, 1), Blocks.AIR);
                                helper.succeedWhen(
                                        () ->
                                                eq(
                                                        helper,
                                                        contents(helper, new BlockPos(3, 1, 1))
                                                                .amount(),
                                                        16,
                                                        "open and unpowered passes"));
                            });
                });
    }

    /** Toggling by hit point; the endpoint tag; a labelled tube's sixth connection is refused. */
    private static void facesAndGlyphFace(GameTestHelper helper) {
        BlockPos center = new BlockPos(2, 2, 2);
        tube(helper, center);
        LabelledBlockEntity be =
                Objects.requireNonNull(helper.getBlockEntity(center, LabelledBlockEntity.class));
        be.setLabel(AspectKeys.AER);
        Direction[] all = Direction.values();
        for (Direction d : all) {
            jar(helper, center.relative(d), null, 0, null);
        }
        // Neighbour updates refresh the faces; the labelled tube keeps one blocked.
        BlockState s = helper.getBlockState(center);
        int blocked = 0;
        int connected = 0;
        for (Direction d : all) {
            Face f = s.getValue(EssentiaTubeBlock.face(d));
            blocked += f == Face.BLOCKED ? 1 : 0;
            connected += f == Face.CONNECTED ? 1 : 0;
        }
        eq(helper, connected, 5, "five connected");
        eq(
                helper,
                s.getValue(EssentiaTubeBlock.face(Direction.DOWN)),
                Face.BLOCKED,
                "the first in Direction order blocked");
        // An unlabelled tube toggles a face by hand.
        BlockPos plain = new BlockPos(5, 1, 1);
        jar(helper, new BlockPos(6, 1, 1), null, 0, null);
        tube(helper, plain);
        eq(
                helper,
                helper.getBlockState(plain).getValue(EssentiaTubeBlock.face(Direction.EAST)),
                Face.CONNECTED,
                "connects to the endpoint tag");
        ServerPlayer player = player(helper, GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        BlockPos abs = helper.absolutePos(plain);
        Vec3 armHit = Vec3.atLowerCornerOf(abs).add(15.5 / 16, 8 / 16.0, 8 / 16.0);
        player.gameMode.useItemOn(
                player,
                helper.getLevel(),
                ItemStack.EMPTY,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(armHit, Direction.UP, abs, false));
        eq(
                helper,
                helper.getBlockState(plain).getValue(EssentiaTubeBlock.face(Direction.EAST)),
                Face.BLOCKED,
                "the arm's face toggled");
        eq(helper, blocked, 1, "one glyph face");
        helper.succeed();
    }

    /**
     * A 256-member line refuses the 257th tube; an overgrown line built by commands stays inert and
     * recovers when cut.
     */
    private static void admissionAndOvergrown(GameTestHelper helper) {
        int y0 = 20;
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                for (int y = 0; y < 4; y++) {
                    tube(helper, new BlockPos(x, y0 + y, z));
                }
            }
        }
        helper.runAfterDelay(
                3,
                () -> {
                    EssentiaLines lines = EssentiaService.lines(helper.getLevel());
                    EssentiaLines.View v = lines.of(helper.absolutePos(new BlockPos(0, y0, 0)));
                    eq(helper, v.members(), 256, "256 members");
                    eq(helper, v.state(), EssentiaLines.State.READY, "within the cap");
                    ServerPlayer player = player(helper, GameType.SURVIVAL);
                    BlockPos extra = helper.absolutePos(new BlockPos(8, y0, 0));
                    ItemStack tubes = new ItemStack(EssentiaContent.tubeItem());
                    player.setItemInHand(InteractionHand.MAIN_HAND, tubes);
                    InteractionResult r =
                            player.gameMode.useItemOn(
                                    player,
                                    helper.getLevel(),
                                    tubes,
                                    InteractionHand.MAIN_HAND,
                                    new BlockHitResult(
                                            Vec3.atCenterOf(extra.west()).add(0.5, 0, 0),
                                            Direction.EAST,
                                            extra.west(),
                                            false));
                    helper.assertTrue(
                            helper.getLevel().getBlockState(extra).isAir(),
                            "placement refused: " + r);
                    eq(helper, lastKey(player), TubeValveBlock.LINE_TOO_LONG, "too long a line");
                    tube(helper, new BlockPos(8, y0, 0));
                    helper.runAfterDelay(
                            3,
                            () -> {
                                eq(
                                        helper,
                                        lines.of(helper.absolutePos(new BlockPos(0, y0, 0)))
                                                .state(),
                                        EssentiaLines.State.OVERGROWN,
                                        "a command builds an overgrown line");
                                eq(
                                        helper,
                                        lines.of(helper.absolutePos(new BlockPos(0, y0, 0)))
                                                .members(),
                                        257,
                                        "counted exactly");
                                helper.setBlock(new BlockPos(8, y0, 0), Blocks.AIR);
                                helper.runAfterDelay(
                                        3,
                                        () -> {
                                            eq(
                                                    helper,
                                                    lines.of(
                                                                    helper.absolutePos(
                                                                            new BlockPos(0, y0, 0)))
                                                            .state(),
                                                    EssentiaLines.State.READY,
                                                    "recovers when cut");
                                            helper.succeed();
                                        });
                            });
                });
    }

    /** An Alembic on a jar base pours uphill into a labelled jar, never between equal ranks. */
    private static void alembicPours(GameTestHelper helper) {
        jar(helper, new BlockPos(1, 2, 1), AspectKeys.AER, 12, null);
        jar(helper, new BlockPos(2, 1, 1), null, 0, AspectKeys.AER);
        helper.setBlock(
                new BlockPos(2, 2, 1),
                EssentiaContent.alembic()
                        .defaultBlockState()
                        .setValue(AlembicBlock.FACING, Direction.WEST));
        jar(helper, new BlockPos(1, 2, 3), AspectKeys.TERRA, 12, null);
        jar(helper, new BlockPos(2, 1, 3), null, 0, null);
        helper.setBlock(
                new BlockPos(2, 2, 3),
                EssentiaContent.alembic()
                        .defaultBlockState()
                        .setValue(AlembicBlock.FACING, Direction.WEST));
        helper.runAfterDelay(
                90,
                () -> {
                    helper.assertTrue(
                            contents(helper, new BlockPos(2, 1, 1)).amount() >= 8,
                            "poured 4 a pour into the labelled jar");
                    eq(
                            helper,
                            contents(helper, new BlockPos(2, 1, 3)).amount(),
                            0,
                            "rank 4 never pours into rank 4");
                    helper.setBlock(new BlockPos(1, 2, 1), Blocks.STONE);
                    helper.assertBlockNotPresent(EssentiaContent.alembic(), new BlockPos(2, 2, 1));
                    helper.succeed();
                });
    }

    /** A jar survives a save and reload; an unreadable record round-trips raw and refuses use. */
    private static void persistenceAndUnreadable(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 1);
        JarBlockEntity jar = jar(helper, at, AspectKeys.AER, 42, AspectKeys.AER);
        CompoundTag saved = jar.saveWithFullMetadata(helper.getLevel().registryAccess());
        BlockPos other = new BlockPos(2, 1, 1);
        helper.setBlock(other, EssentiaContent.jar());
        JarBlockEntity copy =
                Objects.requireNonNull(helper.getBlockEntity(other, JarBlockEntity.class));
        copy.loadWithComponents(
                TagValueInput.create(
                        ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved));
        eq(helper, copy.contents(), new Contents<>(AspectKeys.AER, 42), "reloaded contents");
        eq(helper, copy.label(), AspectKeys.AER, "reloaded label");
        CompoundTag future = saved.copy();
        future.getCompoundOrEmpty("vftv").putInt("format", 9);
        future.getCompoundOrEmpty("vftv").putString("from_the_future", "kept");
        copy.loadWithComponents(
                TagValueInput.create(
                        ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), future));
        helper.assertTrue(!copy.readable(), "a future format is unreadable");
        eq(
                helper,
                copy.saveWithFullMetadata(helper.getLevel().registryAccess()).get("vftv"),
                future.get("vftv"),
                "written back unchanged");
        ServerPlayer player = player(helper, GameType.SURVIVAL);
        use(helper, player, other, new ItemStack(EssentiaContent.label()));
        eq(helper, lastKey(player), SharedMessages.UNREADABLE, "refused");
        helper.assertTrue(
                Objects.requireNonNull(suction(helper, other)).suction(AspectKeys.AER) == 0,
                "no suction");
        helper.succeed();
    }

    /**
     * The T10 × T11 cross-test: a cold crucible's extract-only handler feeds an Alembic hung on its
     * side and a tube on its side; a tube on its top is a deaf arm (only {@code UP} is withheld);
     * nothing is created or lost.
     */
    private static void crucibleCross(GameTestHelper helper) {
        BlockPos crucible = new BlockPos(1, 2, 1);
        helper.setBlock(
                crucible,
                CrucibleContent.crucible().defaultBlockState().setValue(CrucibleBlock.WATER, true));
        CrucibleBlockEntity pool =
                Objects.requireNonNull(helper.getBlockEntity(crucible, CrucibleBlockEntity.class));
        pool.pool().restore(Map.of("vftv:aer", 12, "vftv:terra", 9));
        EssentiaSuction none = suction(helper, crucible);
        helper.assertTrue(
                none == null
                        || (none.suction(AspectKeys.AER) == 0 && none.hold(AspectKeys.AER) == 0),
                "the crucible pulls nothing and holds at 0");
        // The Alembic on the crucible's east side pours Aer into a labelled jar below it.
        jar(helper, new BlockPos(2, 1, 1), null, 0, AspectKeys.AER);
        helper.setBlock(
                new BlockPos(2, 2, 1),
                EssentiaContent.alembic()
                        .defaultBlockState()
                        .setValue(AlembicBlock.FACING, Direction.WEST));
        // A tube on the crucible's north side leads to an unlabelled jar; one on top is deaf.
        jar(helper, new BlockPos(1, 2, -1), null, 0, null);
        tube(helper, new BlockPos(1, 2, 0));
        tube(helper, new BlockPos(1, 3, 1));
        helper.succeedWhen(
                () -> {
                    Contents<ResourceKey<Aspect>> poured = contents(helper, new BlockPos(2, 1, 1));
                    Contents<ResourceKey<Aspect>> piped = contents(helper, new BlockPos(1, 2, -1));
                    eq(helper, poured.aspect(), AspectKeys.AER, "the Alembic pours Aer");
                    helper.assertTrue(piped.amount() > 0, "the tube draws from the side");
                    int left = pool.pool().amount("vftv:aer") + pool.pool().amount("vftv:terra");
                    eq(helper, left + poured.amount() + piped.amount(), 21, "conserved");
                    eq(
                            helper,
                            EssentiaService.lines(helper.getLevel())
                                    .of(helper.absolutePos(new BlockPos(1, 3, 1)))
                                    .endpoints(),
                            1,
                            "the top tube touches the crucible");
                    eq(
                            helper,
                            EssentiaService.lines(helper.getLevel())
                                    .of(helper.absolutePos(new BlockPos(1, 3, 1)))
                                    .deafArms(),
                            1,
                            "UP is withheld: a deaf arm");
                    eq(
                            helper,
                            contents(helper, new BlockPos(2, 1, 1)).amount() <= 12,
                            true,
                            "never more than the pool held");
                });
    }

    /** Sneaking with both hands empty pages through a jar's status. */
    private static void statusPages(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 1, 1);
        jar(helper, at, AspectKeys.AER, 5, null);
        ServerPlayer player = player(helper, GameType.SURVIVAL);
        player.setShiftKeyDown(true);
        use(helper, player, at, ItemStack.EMPTY);
        eq(helper, lastKey(player), "status.vftv.essentia_jar.contents", "page 0");
        use(helper, player, at, ItemStack.EMPTY);
        eq(helper, lastKey(player), "status.vftv.essentia_jar.ranks", "page 1");
        use(helper, player, at, ItemStack.EMPTY);
        eq(helper, lastKey(player), "status.vftv.essentia_jar.contents", "wraps");
        helper.succeed();
    }
}
