package com.andrewbrogoff.vftv.gametest;

import com.andrewbrogoff.vftv.api.neoforge.vis.VisSpentEvent;
import com.andrewbrogoff.vftv.api.neoforge.warp.WarpChangedEvent;
import com.andrewbrogoff.vftv.api.vis.VisContext;
import com.andrewbrogoff.vftv.api.vis.VisKind;
import com.andrewbrogoff.vftv.api.vis.VisQuote;
import com.andrewbrogoff.vftv.api.vis.VisSpentPayload;
import com.andrewbrogoff.vftv.api.warp.CommitResult;
import com.andrewbrogoff.vftv.api.warp.WarpChangedPayload;
import com.andrewbrogoff.vftv.api.warp.WarpCost;
import com.andrewbrogoff.vftv.api.warp.WarpPreview;
import com.andrewbrogoff.vftv.api.warp.WarpRules;
import com.andrewbrogoff.vftv.api.warp.WarpSource;
import com.andrewbrogoff.vftv.api.warp.WarpSourceKind;
import com.andrewbrogoff.vftv.core.warp.WarpConfig;
import com.andrewbrogoff.vftv.core.warp.WarpDials;
import com.andrewbrogoff.vftv.core.warp.WarpRecord;
import com.andrewbrogoff.vftv.core.warp.WarpRulesService;
import com.andrewbrogoff.vftv.core.warp.WarpService;
import com.andrewbrogoff.vftv.core.warp.model.WarpTuning;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.DataResult;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentSync;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.payload.SyncAttachmentsPayload;
import net.neoforged.neoforge.network.registration.ChannelAttributes;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jspecify.annotations.Nullable;

/**
 * The warp feature's GameTests. Name each test {@code warp_<what>}, and list its id {@code
 * vftv:warp_<what>} in {@code src/gameTest/expected/warp.txt}.
 *
 * <p>Each test makes its own mock players, with an un-negotiated connection so the attachment's
 * owner sync is skipped as for any client without our channel. Tests that change the rules in force
 * restore them before their tick ends, so no other test sees the change.
 */
final class WarpGameTests {
    /** Per-player {@code WarpChangedEvent} hooks, so tests that run together never cross. */
    private static final Map<UUID, Consumer<WarpChangedPayload>> HOOKS = new ConcurrentHashMap<>();

    /** The level and position the test place query answers true for. */
    private static volatile @Nullable ServerLevel placeLevel;

    private static volatile @Nullable BlockPos placePos;

    private static final Identifier SILENCE_BENEATH = id("silence_beneath");
    private static final Identifier UNDER_THE_SILENCE = id("under_the_silence");

    private WarpGameTests() {}

    /**
     * Adds the feature's tests by name; {@code modBus} is for any test-only registration they need.
     */
    static void register(Map<String, Consumer<GameTestHelper>> tests, IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(
                WarpChangedEvent.class,
                event -> {
                    Consumer<WarpChangedPayload> hook =
                            HOOKS.get(event.payload().player().getUUID());
                    if (hook != null) {
                        hook.accept(event.payload());
                    }
                });
        WarpService.registerPlaceQuery(
                (level, pos) -> Objects.equals(level, placeLevel) && Objects.equals(pos, placePos));
        tests.put("warp_tuning_loaded", WarpGameTests::tuningLoaded);
        tests.put("warp_tuning_reload_rollback", WarpGameTests::tuningReloadRollback);
        tests.put("warp_commit_entry", WarpGameTests::commitEntry);
        tests.put("warp_commit_refusals", WarpGameTests::commitRefusals);
        tests.put("warp_commit_reentry", WarpGameTests::commitReentry);
        tests.put("warp_milestones", WarpGameTests::milestones);
        tests.put("warp_gear_sample", WarpGameTests::gearSample);
        tests.put("warp_void_work", WarpGameTests::voidWork);
        tests.put("warp_switches", WarpGameTests::switches);
        tests.put("warp_silence", WarpGameTests::silence);
        tests.put("warp_joining", WarpGameTests::joining);
        tests.put("warp_context", WarpGameTests::context);
        tests.put("warp_owner_only_sync", WarpGameTests::ownerOnlySync);
        tests.put("warp_persistence", WarpGameTests::persistence);
        tests.put("warp_commands", WarpGameTests::commands);
    }

    // ---- Tuning ------------------------------------------------------------------------------

    /** The datagen'd {@code warp.json} is loaded, holds every default and is in force. */
    private static void tuningLoaded(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Optional<Resource> file =
                server.getResourceManager()
                        .getResource(
                                Identifier.fromNamespaceAndPath(
                                        "vftv", WarpRulesService.TUNING_PATH));
        helper.assertTrue(file.isPresent(), "data/vftv/tuning/warp.json is loaded");
        DataResult<WarpRules> read = WarpRulesService.parse(text(file.orElseThrow()));
        same(helper, read.result().orElse(null), WarpTuning.DEFAULTS, "the shipped file");
        helper.assertValueEqual(WarpRulesService.current(), WarpTuning.DEFAULTS, "in force");
        helper.assertValueEqual(WarpRulesService.config(), WarpConfig.DEFAULT, "the switches");
        helper.succeed();
    }

    /** A {@code /reload} whose file breaks a milestone rule keeps the previous record. */
    private static void tuningReloadRollback(GameTestHelper helper) {
        WarpRules before = WarpRulesService.current();
        try {
            WarpRulesService.applyTuning(
                    WarpRulesService.parse(
                            "{\"permanent.milestones\": {\"vftv:under_the_silence\": 2}}"),
                    true);
            helper.assertValueEqual(WarpRulesService.current(), before, "Joining unlisted");
            WarpRulesService.applyTuning(WarpRulesService.parse("{\"permanent.cap\": 9}"), true);
            helper.assertValueEqual(WarpRulesService.current(), before, "over the cap");
            WarpRulesService.applyTuning(WarpRulesService.parse("{\"grace\": 0}"), true);
            helper.assertValueEqual(WarpRulesService.current().grace(), 0, "a valid one applies");
        } finally {
            WarpRulesService.applyTuning(DataResult.success(WarpTuning.DEFAULTS), true);
        }
        helper.succeed();
    }

    // ---- The commit --------------------------------------------------------------------------

    /**
     * {@code silence_beneath}'s listen applies lasting 4, raises the counter by 40 and bumps the
     * revision, posting one event whose snapshot shows the completed change.
     */
    private static void commitEntry(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        List<WarpChangedPayload> events = listen(player);
        try {
            CommitResult result =
                    WarpService.commit(
                            player,
                            new WarpCost(0, 4, 0),
                            entry(SILENCE_BENEATH),
                            new WarpPreview(0, 4, 0, false));
            helper.assertValueEqual(result.status(), CommitResult.Status.OK, "status");
            same(helper, result.preview(), new WarpPreview(0, 4, 0, false), "the actual deltas");
            WarpRecord r = record(helper, player);
            helper.assertValueEqual(r.lasting(), 4, "lasting");
            helper.assertValueEqual(r.counter(), 40, "counter");
            helper.assertValueEqual(result.revision().orElse(-1), r.revision(), "revision");
            helper.assertValueEqual(r.revision(), 1L, "the next revision");
            helper.assertValueEqual(events.size(), 1, "one event");
            WarpChangedPayload e = events.getFirst();
            helper.assertValueEqual(e.after().lasting(), 4, "after");
            helper.assertValueEqual(e.delta().total(), 4, "delta");
            helper.assertValueEqual(e.source(), entry(SILENCE_BENEATH), "source");
        } finally {
            HOOKS.remove(player.getUUID());
        }
        helper.succeed();
    }

    /**
     * A stale echo, an invalid milestone and an unreadable record each refuse, changing nothing.
     */
    private static void commitRefusals(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        List<WarpChangedPayload> events = listen(player);
        try {
            CommitResult stale =
                    WarpService.commit(
                            player,
                            new WarpCost(0, 4, 0),
                            entry(SILENCE_BENEATH),
                            new WarpPreview(0, 3, 0, false));
            helper.assertValueEqual(stale.status(), CommitResult.Status.STALE, "stale");
            same(helper, stale.preview(), new WarpPreview(0, 4, 0, false), "the current preview");
            CommitResult invalid =
                    WarpService.commit(
                            player,
                            new WarpCost(0, 0, 4),
                            new WarpSource(WarpSourceKind.MILESTONE, WarpService.JOINING),
                            new WarpPreview(0, 0, 4, false));
            helper.assertValueEqual(invalid.status(), CommitResult.Status.INVALID, "invalid");
            CommitResult negative =
                    WarpService.apply(
                            player,
                            new WarpCost(0, 0, -1),
                            new WarpSource(WarpSourceKind.STILLING, null));
            helper.assertValueEqual(negative.status(), CommitResult.Status.INVALID, "negative P");
            helper.assertValueEqual(record(helper, player), WarpRecord.EMPTY, "nothing changed");

            ServerPlayer unreadable = unreadable(helper);
            CommitResult refused =
                    WarpService.apply(
                            unreadable,
                            new WarpCost(1, 0, 0),
                            new WarpSource(WarpSourceKind.COMMAND, null));
            helper.assertValueEqual(refused.status(), CommitResult.Status.UNREADABLE, "unreadable");
            helper.assertTrue(WarpService.get(unreadable).readable() == null, "still unreadable");
            helper.assertValueEqual(WarpDials.amplifierWarp(unreadable), 0, "dials neutral");
            helper.assertValueEqual(events.size(), 0, "no event");
        } finally {
            HOOKS.remove(player.getUUID());
        }
        helper.succeed();
    }

    /**
     * A listener that commits gets {@code BUSY}; a throwing listener is logged and the change
     * stands.
     */
    private static void commitReentry(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        List<CommitResult> inner = new ArrayList<>();
        HOOKS.put(
                player.getUUID(),
                payload -> {
                    inner.add(
                            WarpService.apply(
                                    player,
                                    new WarpCost(1, 0, 0),
                                    new WarpSource(WarpSourceKind.COMMAND, null)));
                    helper.assertValueEqual(
                            record(helper, player).temporary(), 2, "the write is complete");
                    throw new IllegalStateException("a throwing listener, on purpose");
                });
        try {
            CommitResult result =
                    WarpService.apply(
                            player,
                            new WarpCost(2, 0, 0),
                            new WarpSource(WarpSourceKind.COMMAND, null));
            helper.assertValueEqual(result.status(), CommitResult.Status.OK, "the outer apply");
            helper.assertValueEqual(inner.size(), 1, "the listener ran");
            helper.assertValueEqual(inner.getFirst().status(), CommitResult.Status.BUSY, "busy");
            helper.assertValueEqual(record(helper, player).temporary(), 2, "the change stands");
        } finally {
            HOOKS.remove(player.getUUID());
        }
        helper.succeed();
    }

    /** A milestone pays once; a lowered cap keeps permanent warp and blocks gains. */
    private static void milestones(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        WarpSource source = entry(UNDER_THE_SILENCE);
        CommitResult first =
                WarpService.commit(
                        player, new WarpCost(0, 0, 2), source, new WarpPreview(0, 0, 2, false));
        helper.assertValueEqual(first.status(), CommitResult.Status.OK, "paid");
        WarpRecord r = record(helper, player);
        helper.assertValueEqual(r.permanent(), 2, "permanent");
        helper.assertTrue(r.milestonesPaid().contains(UNDER_THE_SILENCE), "recorded");
        CommitResult again =
                WarpService.commit(
                        player, new WarpCost(0, 0, 2), source, new WarpPreview(0, 0, 0, true));
        helper.assertValueEqual(again.status(), CommitResult.Status.OK, "a second listen");
        helper.assertValueEqual(record(helper, player).permanent(), 2, "pays 0");

        WarpService.apply(
                player, new WarpCost(0, 0, 7), new WarpSource(WarpSourceKind.COMMAND, null));
        try {
            WarpRulesService.applyTuning(
                    WarpRulesService.parse(
                            // The research milestones stay listed: the research registry's
                            // milestone rule refuses a tuning that drops them.
                            "{\"permanent.cap\": 6, \"permanent.milestones\":"
                                    + " {\"vftv:under_the_silence\": 2,"
                                    + " \"vftv:learning_the_tongue\": 3, \"vftv:joining\": 1}}"),
                    true);
            helper.assertValueEqual(record(helper, player).permanent(), 9, "kept above the cap");
            CommitResult blocked =
                    WarpService.apply(
                            player,
                            new WarpCost(0, 0, 1),
                            new WarpSource(WarpSourceKind.COMMAND, null));
            same(helper, blocked.preview(), new WarpPreview(0, 0, 0, true), "a gain previews 0");
            helper.assertValueEqual(record(helper, player).permanent(), 9, "still kept");
        } finally {
            WarpRulesService.applyTuning(DataResult.success(WarpTuning.DEFAULTS), true);
        }
        helper.succeed();
    }

    // ---- The schedule ------------------------------------------------------------------------

    /**
     * Carried gear posts a {@code GEAR} event within 20 ticks of the schedule, capped and summed
     * per stack; putting it down posts another; {@code warp.gear} off samples 0.
     */
    private static void gearSample(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        List<WarpChangedPayload> events = listen(player);
        player.getInventory().add(new ItemStack(Items.ECHO_SHARD, 5));
        player.getInventory().setItem(40, new ItemStack(Items.NETHER_STAR));
        helper.onEachTick(() -> WarpService.onPlayerTick(player));
        helper.runAfterDelay(
                21,
                () -> {
                    helper.assertValueEqual(
                            record(helper, player).gear(), 12, "2 + 10, at most 12");
                    helper.assertValueEqual(events.size(), 1, "one GEAR event");
                    helper.assertValueEqual(
                            events.getFirst().source().kind(), WarpSourceKind.GEAR, "its source");
                    player.getInventory().clearContent();
                    player.getInventory().add(new ItemStack(Items.ECHO_SHARD, 1));
                });
        helper.runAfterDelay(
                42,
                () -> {
                    helper.assertValueEqual(record(helper, player).gear(), 2, "a smaller sum");
                    WarpRulesService.installConfig(new WarpConfig(true, true, true, false));
                    try {
                        WarpService.sampleGear(player);
                        helper.assertValueEqual(record(helper, player).gear(), 0, "gear off");
                    } finally {
                        WarpRulesService.installConfig(WarpConfig.DEFAULT);
                    }
                    HOOKS.remove(player.getUUID());
                    helper.succeed();
                });
    }

    /**
     * Each {@code VOID} spend adds 5 to the counter, never warp; nothing for an ordinary spend, a
     * Silenced or unreadable record, or under a held guard.
     */
    private static void voidWork(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        spend(helper, player, VisKind.VOID);
        spend(helper, player, VisKind.ORDINARY);
        WarpRecord r = record(helper, player);
        helper.assertValueEqual(r.counter(), 5, "one VOID spend");
        helper.assertValueEqual(r.total(), 0, "never warp");

        HOOKS.put(player.getUUID(), payload -> spend(helper, player, VisKind.VOID));
        try {
            WarpService.apply(
                    player, new WarpCost(1, 0, 0), new WarpSource(WarpSourceKind.COMMAND, null));
        } finally {
            HOOKS.remove(player.getUUID());
        }
        helper.assertValueEqual(record(helper, player).counter(), 15, "the gain's 10, no 5");

        WarpService.silence(player, new WarpPreview(-1, 0, 0, false));
        spend(helper, player, VisKind.VOID);
        helper.assertValueEqual(record(helper, player).counter(), 0, "silenced: none");
        ServerPlayer unreadable = unreadable(helper);
        spend(helper, unreadable, VisKind.VOID);
        helper.assertTrue(WarpService.get(unreadable).readable() == null, "unreadable: untouched");
        helper.succeed();
    }

    // ---- Switches, Silencing, Joining, context -----------------------------------------------

    /** Each server switch, off and on, through the rules every dial reads. */
    private static void switches(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        WarpService.apply(
                player, new WarpCost(0, 24, 0), new WarpSource(WarpSourceKind.COMMAND, null));
        VisContext ordinary = new VisContext(id("test"), VisKind.ORDINARY, null);
        helper.assertValueEqual(WarpDials.visFactor(player, ordinary), 1.48, "taxed");
        helper.assertValueEqual(WarpDials.infusionPenalty(player, VisKind.ORDINARY), 12, "penalty");
        helper.assertValueEqual(WarpDials.infusionPenalty(player, VisKind.VOID), 0, "void");
        helper.assertValueEqual(WarpDials.amplifierWarp(player), 24, "amplifier");
        try {
            WarpRulesService.installConfig(new WarpConfig(false, false, false, false));
            WarpRules off = WarpRulesService.current();
            helper.assertFalse(off.voicesEnabled(), "warp.voices.enabled off reaches the rules");
            helper.assertValueEqual(WarpDials.visFactor(player, ordinary), 1.0, "tax off");
            helper.assertValueEqual(
                    WarpDials.infusionPenalty(player, VisKind.ORDINARY), 0, "tax off");
            helper.assertValueEqual(WarpDials.amplifierWarp(player), 0, "amplifier off");
        } finally {
            WarpRulesService.installConfig(WarpConfig.DEFAULT);
        }
        helper.assertTrue(WarpRulesService.current().voicesEnabled(), "back on");
        helper.assertValueEqual(WarpDials.amplifierWarp(player), 24, "amplifier back on");
        helper.succeed();
    }

    /**
     * Silencing writes everything before its one event and refuses a stale or repeated echo, a held
     * guard and an unreadable record, changing nothing.
     */
    private static void silence(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        WarpService.apply(
                player, new WarpCost(3, 4, 2), new WarpSource(WarpSourceKind.COMMAND, null));
        helper.assertValueEqual(
                WarpService.silence(player, new WarpPreview(-3, -3, 0, false)).status(),
                CommitResult.Status.STALE,
                "a stale echo");
        List<CommitResult> inner = new ArrayList<>();
        List<WarpChangedPayload> events = new ArrayList<>();
        HOOKS.put(
                player.getUUID(),
                payload -> {
                    events.add(payload);
                    helper.assertTrue(WarpService.isSilenced(player), "written before the event");
                    inner.add(WarpService.silence(player, new WarpPreview(0, 0, 0, false)));
                });
        try {
            CommitResult ok = WarpService.silence(player, new WarpPreview(-3, -4, 0, false));
            helper.assertValueEqual(ok.status(), CommitResult.Status.OK, "silenced");
        } finally {
            HOOKS.remove(player.getUUID());
        }
        helper.assertValueEqual(events.size(), 1, "one event");
        helper.assertValueEqual(events.getFirst().source().kind(), WarpSourceKind.SILENCING, "src");
        helper.assertValueEqual(inner.getFirst().status(), CommitResult.Status.BUSY, "busy");
        WarpRecord r = record(helper, player);
        helper.assertTrue(r.silenced() && WarpService.isSilenced(player), "the flag");
        helper.assertValueEqual(r.temporary() + r.lasting() + r.counter(), 0, "T, L, counter");
        helper.assertValueEqual(r.permanent(), 2, "permanent stays");
        helper.assertValueEqual(
                WarpService.silence(player, new WarpPreview(0, 0, 0, false)).status(),
                CommitResult.Status.STALE,
                "a repeated silencing");
        ServerPlayer unreadable = unreadable(helper);
        helper.assertValueEqual(
                WarpService.silence(unreadable, new WarpPreview(0, 0, 0, false)).status(),
                CommitResult.Status.UNREADABLE,
                "unreadable");
        helper.assertFalse(WarpService.isSilenced(unreadable), "isSilenced reads false");
        helper.succeed();
    }

    /** Joining through commit, once; the amplifier then carries the bonus, zeroed by the switch. */
    private static void joining(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        WarpSource joining = new WarpSource(WarpSourceKind.MILESTONE, WarpService.JOINING);
        CommitResult result =
                WarpService.commit(
                        player, new WarpCost(0, 0, 5), joining, new WarpPreview(0, 0, 5, false));
        helper.assertValueEqual(result.status(), CommitResult.Status.OK, "joined");
        helper.assertTrue(record(helper, player).joined(), "Joined");
        helper.assertValueEqual(WarpDials.amplifierWarp(player), 15, "5 + the bonus 10");
        same(
                helper,
                WarpService.preview(player, new WarpCost(0, 0, 5), joining).preview(),
                new WarpPreview(0, 0, 0, true),
                "at most once");
        try {
            WarpRulesService.installConfig(new WarpConfig(true, true, false, true));
            helper.assertValueEqual(WarpDials.amplifierWarp(player), 0, "the switch zeroes it");
        } finally {
            WarpRulesService.installConfig(WarpConfig.DEFAULT);
        }
        helper.succeed();
    }

    /**
     * {@code inVoidTouchedPlace} answers each registered query; recent Void work alone puts a
     * player in context but never makes a place Void-touched.
     */
    private static void context(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper);
        BlockPos pos = player.blockPosition();
        helper.assertFalse(WarpService.inVoidTouchedPlace(level, pos), "no query answers");
        helper.assertFalse(WarpService.inContext(player), "not in context");
        spend(helper, player, VisKind.VOID);
        helper.assertTrue(WarpService.inContext(player), "recent Void work");
        helper.assertFalse(WarpService.inVoidTouchedPlace(level, pos), "is not a place");
        ServerPlayer other = player(helper);
        placeLevel = level;
        placePos = other.blockPosition();
        try {
            helper.assertTrue(
                    WarpService.inVoidTouchedPlace(level, other.blockPosition()), "the query");
            helper.assertTrue(WarpService.inContext(other), "a Void-touched place");
        } finally {
            placeLevel = null;
            placePos = null;
        }
        helper.succeed();
    }

    // ---- Owner-only sync ---------------------------------------------------------------------

    /**
     * Player B, whose client negotiated attachment sync, is never sent A's record: not in what B is
     * sent on starting to track A (B's or A's join, A's respawn or dimension change), not by A's
     * baselines, not by A's update. A is sent the full record on each.
     */
    private static void ownerOnlySync(GameTestHelper helper) {
        ServerPlayer a = negotiated(helper);
        ServerPlayer b = negotiated(helper);
        WarpSource source = new WarpSource(WarpSourceKind.COMMAND, null);
        WarpService.apply(a, new WarpCost(1, 0, 0), source);
        drain(a);
        AttachmentType<?> warp =
                Objects.requireNonNull(
                        NeoForgeRegistries.ATTACHMENT_TYPES.getValue(
                                Identifier.fromNamespaceAndPath("vftv", "warp")));

        List<Packet<?>> pairing = new ArrayList<>();
        AttachmentSync.syncInitialEntityAttachments(a, b, pairing::add);
        helper.assertTrue(warpIn(pairing, warp).isEmpty(), "b's pairing with a carries no warp");

        AttachmentSync.syncInitialPlayerAttachments(a);
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(a));
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerRespawnEvent(a, false));
        NeoForge.EVENT_BUS.post(
                new PlayerEvent.PlayerChangedDimensionEvent(a, Level.OVERWORLD, Level.NETHER));
        WarpService.apply(a, new WarpCost(1, 0, 0), source);
        List<SyncAttachmentsPayload> toA = warpIn(drain(a), warp);
        helper.assertValueEqual(
                toA.size(), 4, "a's baselines on login, respawn, dimension, update");
        ServerPlayer copy = player(helper);
        SyncAttachmentsPayload last = toA.getLast();
        AttachmentSync.receiveSyncedDataAttachments(
                copy, a.registryAccess(), last.types(), last.syncPayload());
        helper.assertValueEqual(record(helper, copy), record(helper, a), "a's full record");
        helper.assertTrue(warpIn(drain(b), warp).isEmpty(), "b was sent none of a's warp");
        helper.succeed();
    }

    /** A mock player whose client negotiated NeoForge's attachment sync, but not our channel. */
    private static ServerPlayer negotiated(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        ChannelAttributes.getOrCreateAdHocChannels(player.connection.getConnection())
                .add(SyncAttachmentsPayload.TYPE.id());
        return player;
    }

    /** Takes every packet sent to the player's connection so far. */
    private static List<Packet<?>> drain(ServerPlayer player) {
        EmbeddedChannel channel = (EmbeddedChannel) player.connection.getConnection().channel();
        channel.flushOutbound();
        List<Packet<?>> sent = new ArrayList<>();
        for (Object o; (o = channel.readOutbound()) != null; ) {
            sent.add((Packet<?>) o);
        }
        return sent;
    }

    /** The attachment-sync payloads among {@code packets} that carry {@code type}. */
    private static List<SyncAttachmentsPayload> warpIn(
            List<Packet<?>> packets, AttachmentType<?> type) {
        return packets.stream()
                .filter(ClientboundCustomPayloadPacket.class::isInstance)
                .map(p -> ((ClientboundCustomPayloadPacket) p).payload())
                .filter(SyncAttachmentsPayload.class::isInstance)
                .map(SyncAttachmentsPayload.class::cast)
                .filter(s -> s.types().contains(type))
                .toList();
    }

    // ---- Persistence and commands ------------------------------------------------------------

    /** The record survives a save and load, and the death copy; its offer never does. */
    private static void persistence(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        WarpService.commit(
                player,
                new WarpCost(0, 0, 2),
                entry(UNDER_THE_SILENCE),
                new WarpPreview(0, 0, 2, false));
        WarpService.apply(
                player, new WarpCost(3, 4, 0), new WarpSource(WarpSourceKind.COMMAND, null));
        WarpRecord saved = record(helper, player);

        ServerPlayer reloaded = player(helper);
        reloaded.load(
                TagValueInput.create(
                        ProblemReporter.DISCARDING, player.registryAccess(), save(player)));
        helper.assertValueEqual(record(helper, reloaded), saved, "save and load");

        ServerPlayer respawned = player(helper);
        respawned.restoreFrom(player, false);
        helper.assertValueEqual(record(helper, respawned), saved, "the death copy");
        helper.succeed();
    }

    /** Every command: {@code get}, {@code set}, {@code add} and {@code clear}. */
    private static void commands(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        MinecraftServer server = helper.getLevel().getServer();
        CommandSourceStack source =
                server.createCommandSourceStack()
                        .withEntity(player)
                        .withPermission(LevelBasedPermissionSet.GAMEMASTER)
                        .withSuppressedOutput();
        helper.assertValueEqual(run(helper, source, "vftv warp add @s lasting 4"), 1, "add");
        helper.assertValueEqual(record(helper, player).lasting(), 4, "added");
        helper.assertValueEqual(record(helper, player).counter(), 40, "a gain raises the counter");
        run(helper, source, "vftv warp set @s temporary 5000");
        helper.assertValueEqual(record(helper, player).temporary(), 1000, "clamped to its bound");
        run(helper, source, "vftv warp set @s permanent 50");
        helper.assertValueEqual(record(helper, player).permanent(), 10, "a gain clamps to the cap");
        run(helper, source, "vftv warp add @s permanent -3");
        helper.assertValueEqual(record(helper, player).permanent(), 7, "a command may lower it");
        run(helper, source, "vftv warp set @s counter 10000");
        helper.assertValueEqual(record(helper, player).counter(), 10_000, "the counter");
        helper.assertValueEqual(run(helper, source, "vftv warp get @s"), 1, "get");
        WarpService.commit(
                player,
                new WarpCost(0, 0, 2),
                entry(UNDER_THE_SILENCE),
                new WarpPreview(0, 0, 2, false));
        run(helper, source, "vftv warp clear @s");
        WarpRecord r = record(helper, player);
        helper.assertValueEqual(r.total() + r.counter(), 0, "cleared");
        helper.assertTrue(r.milestonesPaid().contains(UNDER_THE_SILENCE), "milestones kept");
        ServerPlayer unreadable = unreadable(helper);
        helper.assertValueEqual(
                run(helper, source.withEntity(unreadable), "vftv warp get @s"), 0, "unreadable");
        helper.succeed();
    }

    // ---- Helpers -----------------------------------------------------------------------------

    private static void same(
            GameTestHelper helper, @Nullable Object actual, Object expected, String what) {
        helper.assertTrue(expected.equals(actual), what + ": " + actual + ", not " + expected);
    }

    private static int run(GameTestHelper helper, CommandSourceStack source, String command) {
        try {
            return helper.getLevel()
                    .getServer()
                    .getCommands()
                    .getDispatcher()
                    .execute(command, source);
        } catch (CommandSyntaxException e) {
            throw helper.assertionException(Component.literal(command + ": " + e.getMessage()));
        }
    }

    /** A mock player with a connection that never negotiated our channel. */
    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        new ServerGamePacketListenerImpl(
                helper.getLevel().getServer(),
                connection,
                player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false));
        return player;
    }

    /** A mock player whose warp record is from a future format. */
    private static ServerPlayer unreadable(GameTestHelper helper) {
        ServerPlayer source = player(helper);
        WarpService.apply(
                source, new WarpCost(1, 0, 0), new WarpSource(WarpSourceKind.COMMAND, null));
        CompoundTag saved = save(source);
        saved.getCompoundOrEmpty("neoforge:attachments")
                .getCompoundOrEmpty("vftv:warp")
                .getCompoundOrEmpty("data")
                .putInt("format", 99);
        ServerPlayer player = player(helper);
        player.load(
                TagValueInput.create(ProblemReporter.DISCARDING, source.registryAccess(), saved));
        return player;
    }

    private static WarpRecord record(GameTestHelper helper, ServerPlayer player) {
        WarpRecord r = WarpService.get(player).readable();
        if (r == null) {
            throw helper.assertionException(Component.literal("the warp record is unreadable"));
        }
        return r;
    }

    private static List<WarpChangedPayload> listen(ServerPlayer player) {
        List<WarpChangedPayload> events = new ArrayList<>();
        HOOKS.put(player.getUUID(), events::add);
        return events;
    }

    private static void spend(GameTestHelper helper, ServerPlayer player, VisKind kind) {
        NeoForge.EVENT_BUS.post(
                new VisSpentEvent(
                        new VisSpentPayload(
                                player,
                                helper.getLevel(),
                                player.blockPosition(),
                                new VisContext(id("test"), kind, null),
                                new VisQuote(
                                        new TreeMap<>(),
                                        new TreeMap<>(),
                                        1000,
                                        0,
                                        1000,
                                        0,
                                        1000,
                                        false,
                                        false,
                                        false,
                                        false,
                                        true,
                                        List.of()))));
    }

    private static WarpSource entry(Identifier id) {
        return new WarpSource(WarpSourceKind.ENTRY, id);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("vftv", path);
    }

    private static CompoundTag save(ServerPlayer player) {
        TagValueOutput out =
                TagValueOutput.createWithContext(
                        ProblemReporter.DISCARDING, player.registryAccess());
        player.saveWithoutId(out);
        return out.buildResult();
    }

    private static String text(Resource resource) {
        try (InputStream in = resource.open()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
