package com.andrewbrogoff.vftv.gametest;

import com.andrewbrogoff.vftv.api.neoforge.warp.WarpChangedEvent;
import com.andrewbrogoff.vftv.api.warp.CommitResult;
import com.andrewbrogoff.vftv.api.warp.VoiceEvent;
import com.andrewbrogoff.vftv.api.warp.WarpCost;
import com.andrewbrogoff.vftv.api.warp.WarpKeys;
import com.andrewbrogoff.vftv.api.warp.WarpPreview;
import com.andrewbrogoff.vftv.api.warp.WarpSource;
import com.andrewbrogoff.vftv.api.warp.WarpSourceKind;
import com.andrewbrogoff.vftv.core.aspect.KnowledgeService;
import com.andrewbrogoff.vftv.core.aspect.model.Knowledge;
import com.andrewbrogoff.vftv.core.common.model.StrictJson;
import com.andrewbrogoff.vftv.core.warp.StillingDraught;
import com.andrewbrogoff.vftv.core.warp.VoiceChecks;
import com.andrewbrogoff.vftv.core.warp.VoicePayloads;
import com.andrewbrogoff.vftv.core.warp.WarpAttachment;
import com.andrewbrogoff.vftv.core.warp.WarpConfig;
import com.andrewbrogoff.vftv.core.warp.WarpContent;
import com.andrewbrogoff.vftv.core.warp.WarpRecord;
import com.andrewbrogoff.vftv.core.warp.WarpRulesService;
import com.andrewbrogoff.vftv.core.warp.WarpService;
import com.andrewbrogoff.vftv.core.warp.model.VoiceContent;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.JsonOps;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

/**
 * The Voices' GameTests (warp spec, "Tests"): the registries, the check and its suppression, the
 * switches, every event type's server part, Bargains, the Listen rite, stilling, danger and its
 * callbacks, Silencing's {@code VoiceClear}, privacy, persistence and {@code warp fire}. Named
 * {@code warp_<what>}, listed in {@code src/gameTest/expected/warp.txt}.
 *
 * <p>Every test runs in one call and restores any global it changes before returning, so tests in
 * the same batch never see each other's switches. Mock players have random UUIDs, so their
 * per-player hooks never cross.
 */
final class VoicesGameTests {
    private static final UUID PROBE = new UUID(0x0011223344556677L, 0x8899AABBCCDDEEFFL);

    /** Seven known aspects, as the spec's vectors assume. */
    private static final Set<String> KNOWN =
            Set.of(
                    "vftv:aer",
                    "vftv:aqua",
                    "vftv:ignis",
                    "vftv:ordo",
                    "vftv:perditio",
                    "vftv:terra",
                    "vftv:lux");

    private static final Map<ServerPlayer, List<CustomPacketPayload>> CUES =
            new ConcurrentHashMap<>();
    private static final Map<UUID, Consumer<MobEffectEvent.Added>> ADDED =
            new ConcurrentHashMap<>();
    private static final Map<UUID, Consumer<MobEffectEvent.Applicable>> APPLICABLE =
            new ConcurrentHashMap<>();
    private static final Map<UUID, Consumer<WarpChangedEvent>> CHANGED = new ConcurrentHashMap<>();
    private static final Set<BlockPos> PLACES = ConcurrentHashMap.newKeySet();

    private VoicesGameTests() {}

    static void register(Map<String, Consumer<GameTestHelper>> tests) {
        VoiceChecks.swapSender(
                (player, payload) ->
                        CUES.computeIfAbsent(player, k -> new ArrayList<>()).add(payload));
        WarpService.registerPlaceQuery((level, pos) -> PLACES.contains(pos.immutable()));
        NeoForge.EVENT_BUS.addListener(
                MobEffectEvent.Added.class,
                e -> {
                    Consumer<MobEffectEvent.Added> hook = ADDED.get(e.getEntity().getUUID());
                    if (hook != null) {
                        hook.accept(e);
                    }
                });
        NeoForge.EVENT_BUS.addListener(
                MobEffectEvent.Applicable.class,
                e -> {
                    Consumer<MobEffectEvent.Applicable> hook =
                            APPLICABLE.get(e.getEntity().getUUID());
                    if (hook != null) {
                        hook.accept(e);
                    }
                });
        NeoForge.EVENT_BUS.addListener(
                WarpChangedEvent.class,
                e -> {
                    Consumer<WarpChangedEvent> hook = CHANGED.get(e.payload().player().getUUID());
                    if (hook != null) {
                        hook.accept(e);
                    }
                });
        tests.put("warp_voice_registries", VoicesGameTests::registries);
        tests.put("warp_check_draws", VoicesGameTests::checkDraws);
        tests.put("warp_check_suppression", VoicesGameTests::suppression);
        tests.put("warp_voice_switches", VoicesGameTests::switches);
        tests.put("warp_insight", VoicesGameTests::insight);
        tests.put("warp_bargain_accept_refuse", VoicesGameTests::bargainAcceptRefuse);
        tests.put("warp_bargain_callbacks", VoicesGameTests::bargainCallbacks);
        tests.put("warp_listen_rite", VoicesGameTests::listenRite);
        tests.put("warp_listen_rite_refusals", VoicesGameTests::listenRiteRefusals);
        tests.put("warp_stilling", VoicesGameTests::stilling);
        tests.put("warp_danger", VoicesGameTests::danger);
        tests.put("warp_danger_callbacks", VoicesGameTests::dangerCallbacks);
        tests.put("warp_silence_clears", VoicesGameTests::silenceClears);
        tests.put("warp_cue_privacy", VoicesGameTests::privacy);
        tests.put("warp_voices_persistence", VoicesGameTests::persistence);
        tests.put("warp_fire_command", VoicesGameTests::fireCommand);
    }

    // ---- Registries ---------------------------------------------------------------------------

    /** The shipped events and bargains load; each bad entry is refused by the codec. */
    private static void registries(GameTestHelper helper) {
        Registry<VoiceEvent> events =
                helper.getLevel().registryAccess().lookupOrThrow(WarpKeys.VOICE_EVENT_REGISTRY);
        StrictJson.parse(VoiceContent.SHIPPED_EVENTS)
                .getAsJsonObject()
                .asMap()
                .forEach(
                        (id, json) -> {
                            VoiceEvent e = events.getValue(Identifier.parse(id));
                            helper.assertTrue(
                                    e instanceof WarpContent.VoiceEventEntry entry
                                            && entry.event()
                                                    .equals(
                                                            VoiceContent.event(
                                                                    json.getAsJsonObject())),
                                    id + " loaded as shipped");
                        });
        helper.assertValueEqual(
                helper.getLevel().registryAccess().lookupOrThrow(WarpKeys.BARGAIN_REGISTRY).size(),
                2,
                "both bargains");
        for (String bad :
                List.of(
                        "{\"type\": \"vftv:spawn\", \"weight\": 1}",
                        "{\"type\": \"vftv:phantom_block\", \"weight\": 1}",
                        "{\"type\": \"vftv:status\", \"weight\": 1, \"effects\": [{\"effect\":"
                                + " \"minecraft:slowness\", \"amplifier\": 0, \"duration\": 100}]}",
                        "{\"type\": \"vftv:status\", \"weight\": 1, \"context\": true, \"effects\":"
                                + " [{\"effect\": \"vftv:nothing\", \"amplifier\": 0, \"duration\":"
                                + " 100}]}",
                        "{\"type\": \"vftv:whisper\", \"weight\": 0}",
                        "{\"type\": \"vftv:whisper\", \"weight\": 1, \"min_band\": 3,"
                                + " \"max_band\": 1}")) {
            helper.assertTrue(
                    WarpContent.VOICE_EVENT_CODEC
                            .parse(JsonOps.INSTANCE, StrictJson.parse(bad))
                            .isError(),
                    "refused: " + bad);
        }
        for (String bad :
                List.of(
                        "{\"type\": \"vftv:effect\", \"weight\": 1, \"cost\": {\"permanent\": 1},"
                                + " \"effect\": \"minecraft:haste\", \"amplifier\": 0, \"duration\":"
                                + " 200}",
                        "{\"type\": \"vftv:points\", \"weight\": 1, \"cost\": {\"temporary\": 1},"
                                + " \"effect\": \"minecraft:haste\", \"amplifier\": 0, \"duration\":"
                                + " 200}",
                        "{\"type\": \"vftv:effect\", \"weight\": 1, \"cost\": {\"temporary\": 1},"
                                + " \"effect\": \"vftv:nothing\", \"amplifier\": 0, \"duration\":"
                                + " 200}")) {
            helper.assertTrue(
                    WarpContent.BARGAIN_CODEC
                            .parse(JsonOps.INSTANCE, StrictJson.parse(bad))
                            .isError(),
                    "refused: " + bad);
        }
        helper.succeed();
    }

    // ---- The check ----------------------------------------------------------------------------

    /**
     * The first vector: {@code T} 3, {@code L} 4, {@code P} 2, counter 90 for the spec's UUID;
     * check 0 is quiet, check 4 fires {@code vftv:footsteps} with cue {@code 0DF0AA695B97CC14} and
     * leaves the counter at 60.
     */
    private static void checkDraws(GameTestHelper helper) {
        ServerPlayer quiet = state(helper, PROBE, 3, 4, 2, 90, 0);
        helper.assertValueEqual(
                VoiceChecks.check(quiet, false), VoiceChecks.Outcome.QUIET, "check 0 rolls 188");
        WarpRecord q = record(helper, quiet);
        helper.assertValueEqual(q.checks(), 1L, "checks counts a quiet check");
        helper.assertValueEqual(q.counter(), 90, "a quiet check leaves the counter");
        helper.assertTrue(cues(quiet).isEmpty(), "no cue");

        ServerPlayer fired = state(helper, PROBE, 3, 4, 2, 90, 4);
        helper.assertValueEqual(
                VoiceChecks.check(fired, false), VoiceChecks.Outcome.FIRED, "check 4 rolls 26");
        WarpRecord f = record(helper, fired);
        helper.assertValueEqual(f.checks(), 5L, "checks");
        helper.assertValueEqual(f.counter(), 60, "the counter falls by 30");
        List<CustomPacketPayload> sent = cues(fired);
        helper.assertTrue(
                sent.size() == 1
                        && sent.getFirst() instanceof VoicePayloads.VoiceCue cue
                        && cue.event().equals("vftv:footsteps")
                        && cue.kind().equals(VoiceContent.SOUND)
                        && cue.band() == 1
                        && cue.seed() == 0x0DF0AA695B97CC14L,
                "the footsteps cue: " + sent);
        helper.succeed();
    }

    /** Each suppression row skips the check, changing nothing; the last VoiceQuiet wins. */
    private static void suppression(GameTestHelper helper) {
        ServerPlayer p = state(helper, PROBE, 3, 4, 2, 90, 4);
        WarpRecord before = record(helper, p);
        WarpService.quiet(p, true);
        skipped(helper, p, before, "quiet");
        WarpService.quiet(p, true);
        WarpService.quiet(p, false);
        p.openMenu(
                new SimpleMenuProvider(
                        (id, inventory, player) -> ChestMenu.threeRows(id, inventory),
                        Component.empty()));
        skipped(helper, p, before, "a menu open");
        p.closeContainer();
        WarpService.onArrive(p);
        WarpRecord arrived = record(helper, p);
        skipped(helper, p, arrived, "grace after arrival");
        WarpService.onLogout(p);

        ServerPlayer spectator = state(helper, PROBE, 3, 4, 2, 90, 4, GameType.SPECTATOR);
        skipped(helper, spectator, record(helper, spectator), "spectating");
        ServerPlayer dead = state(helper, PROBE, 3, 4, 2, 90, 4);
        dead.setHealth(0F);
        skipped(helper, dead, record(helper, dead), "dead");
        ServerPlayer none = player(helper, GameType.SURVIVAL);
        skipped(helper, none, record(helper, none), "total 0");
        ServerPlayer silenced = state(helper, PROBE, 3, 4, 2, 90, 4);
        WarpService.silence(silenced, new WarpPreview(-3, -4, 0, false));
        skipped(helper, silenced, record(helper, silenced), "Silenced");

        ServerPlayer flood = state(helper, PROBE, 3, 4, 2, 90, 4);
        for (int i = 0; i < 1000; i++) {
            WarpService.quiet(flood, i % 2 == 0);
        }
        helper.assertValueEqual(
                VoiceChecks.check(flood, false),
                VoiceChecks.Outcome.FIRED,
                "after a flood ending false, the last value wins");
        helper.succeed();
    }

    /** Every server switch: {@code warp.voices.enabled} and the seven per-type switches. */
    private static void switches(GameTestHelper helper) {
        Set<String> wasOff = VoiceChecks.typesOff();
        try {
            WarpRulesService.installConfig(new WarpConfig(false, true, true, true));
            ServerPlayer off = state(helper, PROBE, 3, 4, 2, 90, 4);
            skipped(helper, off, record(helper, off), "voices off");
            WarpRulesService.installConfig(WarpConfig.DEFAULT);
            helper.assertValueEqual(
                    VoiceChecks.check(off, false), VoiceChecks.Outcome.FIRED, "voices back on");
            for (String type : VoiceContent.TYPES) {
                // Band 3, in context, an aspect known: every type eligible but the one switched
                // off.
                ServerPlayer p = state(helper, UUID.randomUUID(), 0, 30, 0, 0, 0);
                know(p, "vftv:aer");
                place(helper, p);
                Set<String> others = new java.util.HashSet<>(VoiceContent.TYPES);
                others.remove(type);
                VoiceChecks.installTypesOff(others);
                helper.assertTrue(
                        VoiceChecks.check(p, true) != VoiceChecks.Outcome.NONE,
                        type + " alone is eligible");
                VoiceChecks.installTypesOff(Set.copyOf(VoiceContent.TYPES));
                WarpRecord before = record(helper, p);
                helper.assertValueEqual(
                        VoiceChecks.check(p, true), VoiceChecks.Outcome.NONE, type + " off");
                helper.assertValueEqual(
                        record(helper, p).counter(), before.counter(), "the counter untouched");
            }
        } finally {
            VoiceChecks.installTypesOff(wasOff);
            WarpRulesService.installConfig(WarpConfig.DEFAULT);
        }
        helper.succeed();
    }

    /** An insight credits points to a known aspect only; with none known it never fires. */
    private static void insight(GameTestHelper helper) {
        ServerPlayer p = state(helper, UUID.randomUUID(), 0, 3, 0, 0, 0);
        know(p, "vftv:aer");
        helper.assertValueEqual(fire(p, "vftv:insight"), VoiceChecks.Outcome.FIRED, "fired");
        Knowledge k = Objects.requireNonNull(KnowledgeService.get(p));
        helper.assertValueEqual(k.pool().getOrDefault("vftv:aer", 0), 3, "three points of Aer");
        helper.assertValueEqual(k.known(), Set.of("vftv:aer"), "never a discovery");
        helper.assertTrue(
                cues(p).getLast() instanceof VoicePayloads.VoiceCue c
                        && c.aspect().equals("vftv:aer")
                        && c.points() == 3,
                "the cue names the aspect and points");
        ServerPlayer none = state(helper, UUID.randomUUID(), 0, 3, 0, 0, 0);
        KnowledgeService.set(none, Knowledge.EMPTY);
        helper.assertValueEqual(
                fire(none, "vftv:insight"), VoiceChecks.Outcome.FAILED, "nothing known");
        helper.succeed();
    }

    // ---- Bargains -----------------------------------------------------------------------------

    /**
     * An offer from a fired bargain event; a stale echo; acceptance (effect, warp, the offer gone,
     * one event); a stale seq keeps the newer offer; refusal; an expired offer on accept and on
     * refuse; a clipped cost; death deletes the offer.
     */
    private static void bargainAcceptRefuse(GameTestHelper helper) {
        ServerPlayer p = state(helper, UUID.randomUUID(), 0, 6, 0, 0, 0);
        helper.assertValueEqual(fire(p, "vftv:offer"), VoiceChecks.Outcome.FIRED, "offer fired");
        WarpAttachment.Offer offer = Objects.requireNonNull(WarpService.offer(p), "an offer");
        helper.assertValueEqual(offer.seq(), 1L, "seq starts at 1");
        WarpPreview preview = Objects.requireNonNull(WarpService.offerPreview(p));
        helper.assertValueEqual(
                WarpService.acceptOffer(p, offer.seq(), new WarpPreview(9, 9, 0, false)),
                WarpService.Answer.STALE,
                "a stale echo");
        List<WarpChangedEvent> events = changes(p);
        WarpRecord before = record(helper, p);
        helper.assertValueEqual(
                WarpService.acceptOffer(p, offer.seq(), preview),
                WarpService.Answer.OK,
                "accepted");
        helper.assertTrue(
                p.hasEffect(MobEffects.NIGHT_VISION) || p.hasEffect(MobEffects.HASTE), "the power");
        WarpRecord after = record(helper, p);
        helper.assertValueEqual(
                after.temporary() + after.lasting(),
                before.temporary() + before.lasting() + preview.dT() + preview.dL(),
                "the price");
        helper.assertTrue(WarpService.offer(p) == null, "the offer is gone");
        helper.assertValueEqual(events.size(), 1, "one event");
        helper.assertValueEqual(
                events.getFirst().payload().source().kind(), WarpSourceKind.BARGAIN, "BARGAIN");
        CHANGED.remove(p.getUUID());

        fire(p, "vftv:offer");
        WarpAttachment.Offer second = Objects.requireNonNull(WarpService.offer(p));
        helper.assertValueEqual(
                WarpService.refuseOffer(p, offer.seq()), WarpService.Answer.GONE, "a stale seq");
        helper.assertTrue(second.equals(WarpService.offer(p)), "the newer offer kept");
        WarpRecord kept = record(helper, p);
        helper.assertValueEqual(
                WarpService.refuseOffer(p, second.seq()), WarpService.Answer.OK, "refused");
        helper.assertValueEqual(record(helper, p), kept, "refusing costs nothing");
        helper.assertTrue(WarpService.offer(p) == null, "deleted");

        long g = helper.getLevel().getServer().overworld().getGameTime();
        WarpService.putOffer(p, expired(9, g));
        WarpRecord unchanged = record(helper, p);
        helper.assertValueEqual(
                WarpService.acceptOffer(p, 9, new WarpPreview(3, 0, 0, false)),
                WarpService.Answer.GONE,
                "an expired offer on accept");
        helper.assertTrue(WarpService.get(p).offer() == null, "deleted on accept");
        WarpService.putOffer(p, expired(10, g));
        helper.assertValueEqual(
                WarpService.refuseOffer(p, 10),
                WarpService.Answer.GONE,
                "an expired offer on refuse");
        helper.assertTrue(WarpService.get(p).offer() == null, "deleted on refuse");
        helper.assertValueEqual(record(helper, p), unchanged, "nothing else changed");

        ServerPlayer full = state(helper, UUID.randomUUID(), 999, 6, 0, 0, 0);
        WarpService.putOffer(full, live(3, g));
        helper.assertValueEqual(
                WarpService.acceptOffer(full, 3, new WarpPreview(1, 0, 0, true)),
                WarpService.Answer.FULL,
                "a clipped cost");
        helper.assertTrue(WarpService.offer(full) != null, "the offer stays");
        WarpService.onDeath(full);
        helper.assertTrue(WarpService.get(full).offer() == null, "death deletes the offer");
        helper.succeed();
    }

    /**
     * An applicability callback that refuses (nothing charged, the offer kept); one that changes
     * its answer between calls (one addEffect: asked once); one that throws; an Added listener that
     * commits warp gets BUSY and our charge stands.
     */
    private static void bargainCallbacks(GameTestHelper helper) {
        long g = helper.getLevel().getServer().overworld().getGameTime();
        ServerPlayer p = state(helper, UUID.randomUUID(), 0, 6, 0, 0, 0);
        WarpPreview price = new WarpPreview(3, 0, 0, false);
        try {
            WarpService.putOffer(p, live(1, g));
            APPLICABLE.put(
                    p.getUUID(), e -> e.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY));
            WarpRecord before = record(helper, p);
            helper.assertValueEqual(
                    WarpService.acceptOffer(p, 1, price), WarpService.Answer.CANNOT, "refused");
            helper.assertValueEqual(record(helper, p), before, "nothing charged");
            helper.assertTrue(WarpService.offer(p) != null, "the offer kept");

            int[] asked = {0};
            APPLICABLE.put(
                    p.getUUID(),
                    e ->
                            e.setResult(
                                    asked[0]++ % 2 == 0
                                            ? MobEffectEvent.Applicable.Result.DO_NOT_APPLY
                                            : MobEffectEvent.Applicable.Result.APPLY));
            helper.assertValueEqual(
                    WarpService.acceptOffer(p, 1, price),
                    WarpService.Answer.CANNOT,
                    "the first answer counts");
            helper.assertValueEqual(asked[0], 1, "asked once");

            APPLICABLE.put(
                    p.getUUID(),
                    e -> {
                        throw new IllegalStateException("a test callback throws");
                    });
            helper.assertValueEqual(
                    WarpService.acceptOffer(p, 1, price), WarpService.Answer.CANNOT, "a throw");
            APPLICABLE.remove(p.getUUID());

            List<CommitResult> inner = new ArrayList<>();
            ADDED.put(
                    p.getUUID(),
                    e ->
                            inner.add(
                                    WarpService.apply(
                                            p,
                                            new WarpCost(5, 0, 0),
                                            new WarpSource(WarpSourceKind.COMMAND, null))));
            helper.assertValueEqual(
                    WarpService.acceptOffer(p, 1, price), WarpService.Answer.OK, "accepted");
            helper.assertTrue(
                    inner.size() == 1 && inner.getFirst().status() == CommitResult.Status.BUSY,
                    "the listener's commit is BUSY: " + inner);
            helper.assertValueEqual(record(helper, p).temporary(), 3, "our charge stands");
        } finally {
            APPLICABLE.remove(p.getUUID());
            ADDED.remove(p.getUUID());
        }
        helper.succeed();
    }

    // ---- The Listen rite ----------------------------------------------------------------------

    /**
     * Stilled to nothing at a Void-touched place: the rite commits lasting 2, the counter +20,
     * {@code last_listen}, one {@code LISTEN} event; the forced check waits while a screen is open
     * and runs on the tick {@code VoiceQuiet(false)} arrives, settling FIRED. Then it is cooling;
     * logout or death before the forced check clears it and keeps the charge.
     */
    private static void listenRite(GameTestHelper helper) {
        ServerPlayer p = player(helper, GameType.SURVIVAL);
        place(helper, p);
        List<WarpChangedEvent> events = changes(p);
        WarpPreview cost = Objects.requireNonNull(WarpService.ritePreview(p));
        helper.assertValueEqual(cost, new WarpPreview(0, 2, 0, false), "lasting 2, unclipped");
        try {
            helper.assertValueEqual(WarpService.listen(p, cost), WarpService.Answer.OK, "listened");
        } finally {
            CHANGED.remove(p.getUUID());
        }
        WarpRecord r = record(helper, p);
        long g = helper.getLevel().getServer().overworld().getGameTime();
        helper.assertValueEqual(r.lasting(), 2, "lasting 2");
        helper.assertValueEqual(r.counter(), 20, "the counter +20");
        helper.assertValueEqual(r.lastListen(), g, "last_listen");
        helper.assertTrue(
                events.size() == 1
                        && events.getFirst().payload().source().kind() == WarpSourceKind.LISTEN,
                "one LISTEN event");
        helper.assertTrue(WarpService.listenDue(p), "the forced check is due");
        WarpService.quiet(p, true);
        WarpService.onPlayerTick(p);
        helper.assertTrue(WarpService.listenDue(p), "not while a screen is open");
        helper.assertValueEqual(record(helper, p).checks(), 0L, "no check yet");
        WarpService.quiet(p, false);
        WarpService.onPlayerTick(p);
        helper.assertTrue(!WarpService.listenDue(p), "cleared");
        helper.assertTrue(record(helper, p).checks() >= 1L, "the forced check ran");
        helper.assertTrue(!cues(p).isEmpty(), "it fired, whatever the roll");
        helper.assertValueEqual(
                WarpService.listen(p, cost), WarpService.Answer.COOLDOWN, "now cooling");
        helper.assertTrue(WarpService.listenCooldownSeconds(p) > 0, "seconds left");

        ServerPlayer died = player(helper, GameType.SURVIVAL);
        place(helper, died);
        WarpService.listen(died, cost);
        WarpService.onDeath(died);
        helper.assertTrue(!WarpService.listenDue(died), "death clears it");
        helper.assertValueEqual(record(helper, died).lasting(), 2, "the charge kept");
        helper.succeed();
    }

    /** Each refusal in order, each changing nothing. */
    private static void listenRiteRefusals(GameTestHelper helper) {
        WarpPreview cost = new WarpPreview(0, 2, 0, false);
        ServerPlayer p = player(helper, GameType.SURVIVAL);
        place(helper, p);
        List<WarpService.Answer> inner = new ArrayList<>();
        CHANGED.put(p.getUUID(), e -> inner.add(WarpService.listen(p, cost)));
        WarpService.apply(p, new WarpCost(1, 0, 0), new WarpSource(WarpSourceKind.COMMAND, null));
        CHANGED.remove(p.getUUID());
        helper.assertValueEqual(inner, List.of(WarpService.Answer.BUSY), "BUSY under the guard");

        ServerPlayer silenced = player(helper, GameType.SURVIVAL);
        place(helper, silenced);
        WarpService.silence(silenced, new WarpPreview(0, 0, 0, false));
        refused(helper, silenced, cost, WarpService.Answer.CLOSED, "Silenced");
        try {
            WarpRulesService.installConfig(new WarpConfig(false, true, true, true));
            refused(helper, p, cost, WarpService.Answer.CLOSED, "voices off");
        } finally {
            WarpRulesService.installConfig(WarpConfig.DEFAULT);
        }
        ServerPlayer ground = player(helper, GameType.SURVIVAL);
        ground.snapTo(Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 5, 1))));
        refused(helper, ground, cost, WarpService.Answer.PLACE, "ordinary ground");
        ServerPlayer full = state(helper, UUID.randomUUID(), 0, 999, 0, 0, 0);
        place(helper, full);
        refused(helper, full, new WarpPreview(0, 1, 0, true), WarpService.Answer.FULL, "clipped");
        refused(
                helper,
                p,
                new WarpPreview(0, 3, 0, false),
                WarpService.Answer.STALE,
                "a stale echo");
        helper.assertValueEqual(
                WarpService.riteRefusal(ground), WarpService.Answer.PLACE, "quoted");
        helper.succeed();
    }

    // ---- Stilling -----------------------------------------------------------------------------

    /**
     * Exact removal; a partial; refused at 0 at the start and when stilled to 0 during the drink.
     */
    private static void stilling(GameTestHelper helper) {
        ServerPlayer p = state(helper, UUID.randomUUID(), 0, 6, 0, 0, 0);
        p.getAbilities().instabuild = true;
        ItemStack infinite = new ItemStack(WarpContent.stillingDraught(), 1);
        helper.assertValueEqual(
                infinite.finishUsingItem(helper.getLevel(), p).getCount(),
                1,
                "infinite materials consume no draught");
        helper.assertValueEqual(record(helper, p).lasting(), 2, "but it still stills");
        WarpService.apply(p, new WarpCost(0, 4, 0), new WarpSource(WarpSourceKind.COMMAND, null));
        p.getAbilities().instabuild = false;
        ItemStack draught = new ItemStack(WarpContent.stillingDraught(), 2);
        p.setItemInHand(InteractionHand.MAIN_HAND, draught);
        ItemStack left = draught.finishUsingItem(helper.getLevel(), p);
        helper.assertValueEqual(record(helper, p).lasting(), 2, "stilled 4");
        helper.assertValueEqual(left.getCount(), 1, "one consumed");
        helper.assertTrue(
                p.getInventory().contains(new ItemStack(Items.GLASS_BOTTLE)), "the bottle");
        left.finishUsingItem(helper.getLevel(), p);
        helper.assertValueEqual(record(helper, p).lasting(), 0, "2 left gives a partial");

        ItemStack refusedStack = new ItemStack(WarpContent.stillingDraught(), 1);
        p.setItemInHand(InteractionHand.MAIN_HAND, refusedStack);
        helper.assertValueEqual(
                refusedStack.use(helper.getLevel(), p, InteractionHand.MAIN_HAND),
                InteractionResult.FAIL,
                "refused at the start with nothing to still");
        helper.assertValueEqual(
                refusedStack.finishUsingItem(helper.getLevel(), p).getCount(),
                1,
                "stilled to 0 during the drink: nothing consumed");

        ServerPlayer busy = state(helper, UUID.randomUUID(), 0, 6, 0, 0, 0);
        ItemStack held = new ItemStack(WarpContent.stillingDraught(), 1);
        List<Integer> counts = new ArrayList<>();
        CHANGED.put(
                busy.getUUID(),
                e -> counts.add(held.finishUsingItem(helper.getLevel(), busy).getCount()));
        WarpService.apply(
                busy, new WarpCost(1, 0, 0), new WarpSource(WarpSourceKind.COMMAND, null));
        CHANGED.remove(busy.getUUID());
        helper.assertValueEqual(counts, List.of(1), "BUSY consumes nothing");
        helper.assertValueEqual(record(helper, busy).lasting(), 6, "BUSY stills nothing");
        helper.assertTrue(StillingDraught.DRINK_TICKS == held.getUseDuration(busy), "32 ticks");
        helper.succeed();
    }

    // ---- Danger -------------------------------------------------------------------------------

    /**
     * Status only in context; {@code pressing_dark} applies Slowness and no Darkness; every effect
     * refused settles FAILED with the counter unchanged.
     */
    private static void danger(GameTestHelper helper) {
        Set<String> wasOff = VoiceChecks.typesOff();
        Set<String> notStatus = new java.util.HashSet<>(VoiceContent.TYPES);
        notStatus.remove(VoiceContent.STATUS);
        ServerPlayer p = state(helper, UUID.randomUUID(), 0, 20, 0, 1000, 0);
        try {
            VoiceChecks.installTypesOff(notStatus);
            helper.assertValueEqual(
                    VoiceChecks.check(p, true), VoiceChecks.Outcome.NONE, "out of context");
            place(helper, p);
            helper.assertValueEqual(
                    VoiceChecks.check(p, true), VoiceChecks.Outcome.FIRED, "in context");
            helper.assertTrue(p.hasEffect(MobEffects.SLOWNESS), "Slowness");
            helper.assertTrue(!p.hasEffect(MobEffects.DARKNESS), "no Darkness");
            p.removeAllEffects();
            APPLICABLE.put(
                    p.getUUID(), e -> e.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY));
            WarpRecord before = record(helper, p);
            helper.assertValueEqual(
                    VoiceChecks.check(p, true), VoiceChecks.Outcome.FAILED, "every effect refused");
            helper.assertValueEqual(record(helper, p).counter(), before.counter(), "counter kept");
        } finally {
            VoiceChecks.installTypesOff(wasOff);
            APPLICABLE.remove(p.getUUID());
        }
        helper.succeed();
    }

    /**
     * The test pack's two-effect status event: a first effect's callback that moves the Listener
     * out of context, opens a menu, sets {@code quiet} or silences them stops the second; a
     * callback that silences after one success still lowers the counter but sends no cue.
     */
    private static void dangerCallbacks(GameTestHelper helper) {
        List<Consumer<ServerPlayer>> callbacks =
                List.of(
                        p -> p.snapTo(Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 5, 2)))),
                        p ->
                                p.openMenu(
                                        new SimpleMenuProvider(
                                                (id, inventory, player) ->
                                                        ChestMenu.threeRows(id, inventory),
                                                Component.empty())),
                        p -> WarpService.quiet(p, true),
                        p -> {
                            WarpRecord r = Objects.requireNonNull(WarpService.get(p).readable());
                            WarpService.silence(
                                    p, new WarpPreview(-r.temporary(), -r.lasting(), 0, false));
                        });
        for (int i = 0; i < callbacks.size(); i++) {
            ServerPlayer p = state(helper, UUID.randomUUID(), 0, 30, 0, 1000, 0);
            place(helper, p);
            Consumer<ServerPlayer> callback = callbacks.get(i);
            ADDED.put(
                    p.getUUID(),
                    e -> {
                        if (e.getEffectInstance()
                                .getEffect()
                                .unwrapKey()
                                .equals(MobEffects.WEAKNESS.unwrapKey())) {
                            callback.accept(p);
                        }
                    });
            try {
                helper.assertValueEqual(
                        fire(p, "vftv:test_danger"), VoiceChecks.Outcome.FIRED, "case " + i);
                helper.assertTrue(p.hasEffect(MobEffects.WEAKNESS), "the first effect, case " + i);
                helper.assertTrue(
                        !p.hasEffect(MobEffects.MINING_FATIGUE), "no second effect, case " + i);
                helper.assertTrue(
                        cues(p).stream().noneMatch(c -> c instanceof VoicePayloads.VoiceCue),
                        "no cue, case " + i);
                helper.assertTrue(
                        record(helper, p).counter() < 1000, "the counter fell, case " + i);
            } finally {
                ADDED.remove(p.getUUID());
                p.closeContainer();
            }
        }
        helper.succeed();
    }

    // ---- Silencing, privacy, persistence, commands -------------------------------------------

    /** Silencing sends VoiceClear, deletes the offer and the due rite check, and stops checks. */
    private static void silenceClears(GameTestHelper helper) {
        ServerPlayer p = player(helper, GameType.SURVIVAL);
        place(helper, p);
        WarpService.listen(p, new WarpPreview(0, 2, 0, false));
        long g = helper.getLevel().getServer().overworld().getGameTime();
        WarpService.putOffer(p, live(1, g));
        WarpService.silence(p, new WarpPreview(0, -2, 0, false));
        helper.assertTrue(cues(p).getLast() instanceof VoicePayloads.VoiceClear, "VoiceClear");
        helper.assertTrue(WarpService.get(p).offer() == null, "the offer deleted");
        helper.assertTrue(!WarpService.listenDue(p), "the due check deleted");
        helper.assertValueEqual(
                VoiceChecks.check(p, true), VoiceChecks.Outcome.SKIPPED, "no checks");
        helper.succeed();
    }

    /** Two players: a cue reaches only its owner. */
    private static void privacy(GameTestHelper helper) {
        ServerPlayer a = state(helper, UUID.randomUUID(), 0, 3, 0, 0, 0);
        ServerPlayer b = state(helper, UUID.randomUUID(), 0, 3, 0, 0, 0);
        fire(a, "vftv:whisper");
        helper.assertValueEqual(cues(a).size(), 1, "the owner's cue");
        helper.assertTrue(cues(b).isEmpty(), "nothing for the other");
        helper.succeed();
    }

    /**
     * {@code last_listen} and {@code checks} survive save and load and the death copy; offers
     * don't.
     */
    private static void persistence(GameTestHelper helper) {
        ServerPlayer p = player(helper, GameType.SURVIVAL);
        place(helper, p);
        WarpService.listen(p, new WarpPreview(0, 2, 0, false));
        WarpService.putOffer(p, live(1, helper.getLevel().getServer().overworld().getGameTime()));
        fire(p, "vftv:whisper");
        WarpRecord saved = record(helper, p);
        ServerPlayer reloaded = player(helper, GameType.SURVIVAL);
        reloaded.load(
                TagValueInput.create(ProblemReporter.DISCARDING, p.registryAccess(), save(p)));
        helper.assertValueEqual(record(helper, reloaded), saved, "save and load");
        helper.assertTrue(WarpService.get(reloaded).offer() == null, "no offer after a reload");
        place(helper, reloaded);
        helper.assertValueEqual(
                WarpService.riteRefusal(reloaded), WarpService.Answer.COOLDOWN, "still cooling");
        ServerPlayer respawned = player(helper, GameType.SURVIVAL);
        respawned.restoreFrom(p, false);
        helper.assertValueEqual(record(helper, respawned), saved, "the death copy");
        helper.assertTrue(WarpService.get(respawned).offer() == null, "no offer after death");
        helper.succeed();
    }

    /** {@code /vftv warp fire}: an event fires past roll and eligibility; suppression holds. */
    private static void fireCommand(GameTestHelper helper) {
        ServerPlayer p = state(helper, UUID.randomUUID(), 0, 3, 0, 0, 0);
        CommandSourceStack source =
                helper.getLevel()
                        .getServer()
                        .createCommandSourceStack()
                        .withEntity(p)
                        .withPermission(LevelBasedPermissionSet.GAMEMASTER)
                        .withSuppressedOutput();
        helper.assertValueEqual(run(helper, source, "vftv warp fire @s vftv:whisper"), 1, "fired");
        helper.assertTrue(
                cues(p).getLast() instanceof VoicePayloads.VoiceCue c
                        && c.event().equals("vftv:whisper"),
                "the whisper cue");
        helper.assertValueEqual(record(helper, p).checks(), 1L, "settled as a check");
        WarpService.quiet(p, true);
        helper.assertValueEqual(
                run(helper, source, "vftv warp fire @s vftv:whisper"), 0, "suppressed");
        boolean unknown = false;
        try {
            helper.getLevel()
                    .getServer()
                    .getCommands()
                    .getDispatcher()
                    .execute("vftv warp fire @s vftv:nothing", source);
        } catch (CommandSyntaxException e) {
            unknown = true;
        }
        helper.assertTrue(unknown, "an unknown event is an error");
        helper.succeed();
    }

    // ---- Helpers ------------------------------------------------------------------------------

    private static VoiceChecks.Outcome fire(ServerPlayer p, String event) {
        return Objects.requireNonNull(VoiceChecks.fire(p, Identifier.parse(event)), event);
    }

    private static void skipped(
            GameTestHelper helper, ServerPlayer p, WarpRecord before, String what) {
        helper.assertValueEqual(VoiceChecks.check(p, false), VoiceChecks.Outcome.SKIPPED, what);
        helper.assertValueEqual(record(helper, p), before, what + ": nothing changed");
    }

    private static void refused(
            GameTestHelper helper,
            ServerPlayer p,
            WarpPreview echo,
            WarpService.Answer answer,
            String what) {
        WarpAttachment before = WarpService.get(p);
        helper.assertValueEqual(WarpService.listen(p, echo), answer, what);
        helper.assertTrue(WarpService.get(p).equals(before), what + ": nothing changed");
    }

    private static List<CustomPacketPayload> cues(ServerPlayer p) {
        return List.copyOf(CUES.getOrDefault(p, List.of()));
    }

    private static List<WarpChangedEvent> changes(ServerPlayer p) {
        List<WarpChangedEvent> events = new ArrayList<>();
        CHANGED.put(p.getUUID(), events::add);
        return events;
    }

    /** Moves the player to a unique spot the test place query answers true for. */
    static void place(GameTestHelper helper, ServerPlayer p) {
        BlockPos pos =
                helper.absolutePos(new BlockPos(0, 2, 0))
                        .offset(PLACES.size() % 7, 0, PLACES.size() / 7);
        p.snapTo(Vec3.atCenterOf(pos));
        PLACES.add(p.blockPosition());
    }

    private static void know(ServerPlayer p, String aspect) {
        know(p, Set.of(aspect));
    }

    private static void know(ServerPlayer p, Set<String> aspects) {
        KnowledgeService.set(p, new Knowledge(aspects, Map.of(), Map.of(), null));
    }

    static WarpAttachment.Offer live(long seq, long g) {
        return new WarpAttachment.Offer(
                seq,
                Identifier.parse("vftv:keen_eyes"),
                Identifier.parse("minecraft:night_vision"),
                0,
                6000,
                3,
                0,
                g + 12_000);
    }

    static WarpAttachment.Offer expired(long seq, long g) {
        return new WarpAttachment.Offer(
                seq,
                Identifier.parse("vftv:keen_eyes"),
                Identifier.parse("minecraft:night_vision"),
                0,
                6000,
                3,
                0,
                g);
    }

    private static ServerPlayer player(GameTestHelper helper, GameType type) {
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(type);
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        new ServerGamePacketListenerImpl(
                helper.getLevel().getServer(),
                connection,
                player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false));
        return player;
    }

    private static ServerPlayer state(
            GameTestHelper helper, UUID uuid, int t, int l, int p, int counter, long checks) {
        return state(helper, uuid, t, l, p, counter, checks, GameType.SURVIVAL);
    }

    /**
     * A survival mock player whose record holds exactly these values, loaded from a saved tag, so
     * the draw index and counter can be set; {@code uuid} replaces its UUID for the draws.
     */
    private static ServerPlayer state(
            GameTestHelper helper,
            UUID uuid,
            int t,
            int l,
            int p,
            int counter,
            long checks,
            GameType type) {
        ServerPlayer source = player(helper, GameType.SURVIVAL);
        WarpService.apply(
                source, new WarpCost(1, 0, 0), new WarpSource(WarpSourceKind.COMMAND, null));
        CompoundTag saved = save(source);
        CompoundTag data =
                saved.getCompoundOrEmpty("neoforge:attachments")
                        .getCompoundOrEmpty("vftv:warp")
                        .getCompoundOrEmpty("data");
        data.putInt("temporary", t);
        data.putInt("lasting", l);
        data.putInt("permanent", p);
        data.putInt("counter", counter);
        data.putLong("checks", checks);
        ServerPlayer player = player(helper, type);
        player.load(
                TagValueInput.create(ProblemReporter.DISCARDING, source.registryAccess(), saved));
        player.setUUID(uuid);
        WarpService.onLogout(player); // a fresh session: no transient state from another test
        player.snapTo(Vec3.atCenterOf(helper.absolutePos(new BlockPos(3, 5, 3))));
        know(player, KNOWN);
        return player;
    }

    private static WarpRecord record(GameTestHelper helper, ServerPlayer player) {
        WarpRecord r = WarpService.get(player).readable();
        if (r == null) {
            throw helper.assertionException(Component.literal("the warp record is unreadable"));
        }
        return r;
    }

    private static CompoundTag save(ServerPlayer player) {
        TagValueOutput out =
                TagValueOutput.createWithContext(
                        ProblemReporter.DISCARDING, player.registryAccess());
        player.saveWithoutId(out);
        return out.buildResult();
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
}
