package com.andrewbrogoff.vftv.core.warp;

import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.api.warp.WarpKeys;
import com.andrewbrogoff.vftv.api.warp.WarpPreview;
import com.andrewbrogoff.vftv.api.warp.WarpRules;
import com.andrewbrogoff.vftv.api.warp.WarpSourceKind;
import com.andrewbrogoff.vftv.core.aspect.KnowledgeService;
import com.andrewbrogoff.vftv.core.aspect.model.Knowledge;
import com.andrewbrogoff.vftv.core.common.Network;
import com.andrewbrogoff.vftv.core.common.OperationGuard;
import com.andrewbrogoff.vftv.core.common.RawRecordCodec.Retained;
import com.andrewbrogoff.vftv.core.warp.model.VoiceContent;
import com.andrewbrogoff.vftv.core.warp.model.VoiceDraws;
import com.andrewbrogoff.vftv.core.warp.model.WarpMath;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The Voice check (warp spec, "Voice events"): the suppression rows, the seeded draws over the
 * eligible events, each type's server part, the outcome and its counter, and the owner's cue. Also
 * {@code /vftv warp fire}. Server thread only.
 *
 * <p>The draws, the record's {@code checks} and counter, and an offer are written under the warp
 * guard. A {@code vftv:status} event's {@code addEffect} calls run foreign callbacks, so they run
 * with the guard released, and the Listener's danger eligibility is read again before each call
 * after the first.
 */
public final class VoiceChecks {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> THROWN = new HashSet<>();

    private static @Nullable Network network;
    private static BiConsumer<ServerPlayer, CustomPacketPayload> sender =
            (player, payload) ->
                    Objects.requireNonNull(network, "WarpFeature.init").toPlayer(player, payload);
    private static Set<String> typesOff = Set.of();

    private VoiceChecks() {}

    /** How a check settled (warp spec, "Outcomes"). */
    public enum Outcome {
        /** A suppression row held: nothing drawn, nothing changed. */
        SKIPPED,
        /** The roll was at or above the chance. */
        QUIET,
        /** Nothing was eligible. */
        NONE,
        /** The server part did nothing. */
        FAILED,
        /** The event fired. */
        FIRED
    }

    static void init(Network seam) {
        network = seam;
    }

    /** Takes the per-type server switches that are off, as {@code vftv:voice_event_type} ids. */
    public static void installTypesOff(Set<String> off) {
        typesOff = Set.copyOf(off);
    }

    /** The per-type switches that are off. */
    public static Set<String> typesOff() {
        return typesOff;
    }

    /** Replaces where cues go and returns the previous; for GameTests. */
    public static BiConsumer<ServerPlayer, CustomPacketPayload> swapSender(
            BiConsumer<ServerPlayer, CustomPacketPayload> next) {
        BiConsumer<ServerPlayer, CustomPacketPayload> previous = sender;
        sender = next;
        return previous;
    }

    static void send(ServerPlayer player, CustomPacketPayload payload) {
        sender.accept(player, payload);
    }

    // ---- Suppression --------------------------------------------------------------------------

    /**
     * The first suppression row that holds for the player ("Suppression"), 1–8, or null when none
     * does.
     */
    public static @Nullable Integer suppressed(ServerPlayer player) {
        WarpRecord r = WarpService.get(player).readable();
        WarpService.Transient t = WarpService.transientOf(player);
        if (r == null) {
            return 1;
        }
        if (r.silenced()) {
            return 2;
        }
        if (!WarpRulesService.current().voicesEnabled()) {
            return 3;
        }
        if (r.total() == 0) {
            return 4;
        }
        if (!player.isAlive() || player.isSpectator()) {
            return 5;
        }
        if (WarpService.gameTime(player) < t.graceUntil) {
            return 6;
        }
        if (player.hasContainerOpen()) {
            return 7;
        }
        return t.quiet ? 8 : null;
    }

    /**
     * Danger eligibility ("Danger"): online, alive, in {@code level}, no suppression row, and in
     * context, all read afresh.
     */
    static boolean dangerEligible(ServerPlayer player, ServerLevel level) {
        return !player.hasDisconnected()
                && player.isAlive()
                && player.level().equals(level)
                && suppressed(player) == null
                && WarpService.inContext(player);
    }

    // ---- The check ----------------------------------------------------------------------------

    /**
     * Runs one check: the roll, then (unless quiet) the eligible events, the pick, the cue seed,
     * the type's parameter, its server part and the outcome. {@code forced} (the Listen rite's
     * check) draws the roll and ignores it.
     */
    public static Outcome check(ServerPlayer player, boolean forced) {
        return run(player, forced, null);
    }

    /**
     * {@code /vftv warp fire}: that event's server part and cue, skipping the roll and eligibility
     * but not the suppression rows, settled as a check.
     *
     * @return the outcome, or null when no such event exists
     */
    public static @Nullable Outcome fire(ServerPlayer player, Identifier event) {
        SortedMap<String, VoiceContent.Event> events = events(player);
        if (!events.containsKey(event.toString())) {
            return null;
        }
        return run(player, true, event.toString());
    }

    private static Outcome run(ServerPlayer player, boolean forced, @Nullable String only) {
        if (suppressed(player) != null) {
            return Outcome.SKIPPED;
        }
        WarpRecord r = Objects.requireNonNull(WarpService.get(player).readable());
        WarpRules rules = WarpRulesService.current();
        long g = WarpService.gameTime(player);
        int total = r.total();
        int band = WarpMath.band(total, rules);
        int pm = WarpMath.chancePm(total, r.permanent(), r.counter(), rules);
        WarpAttachment.Offer offer = WarpService.get(player).offer();
        boolean offerLive = offer != null && g < offer.expires();
        SortedMap<String, VoiceContent.Event> events = events(player);
        List<String> known = known(player);
        SortedMap<String, VoiceContent.Bargain> bargains = eligibleBargains(player, r, rules);
        int bargainWeight = 0;
        for (VoiceContent.Bargain b : bargains.values()) {
            bargainWeight += b.weight();
        }
        List<VoiceDraws.Candidate> eligible;
        if (only == null) {
            Set<String> disabled = new HashSet<>();
            events.forEach(
                    (id, e) -> {
                        if (typesOff.contains(e.type())) {
                            disabled.add(id);
                        }
                    });
            eligible =
                    VoiceDraws.eligible(
                            events,
                            disabled,
                            band,
                            () -> WarpService.inContext(player),
                            offerLive,
                            known.size(),
                            bargainWeight);
        } else {
            VoiceContent.Event e = Objects.requireNonNull(events.get(only));
            int bound =
                    switch (e.type()) {
                        case VoiceContent.BARGAIN -> offerLive ? 0 : bargainWeight;
                        case VoiceContent.INSIGHT -> known.size();
                        default -> -1;
                    };
            eligible =
                    bound == 0
                            ? List.of()
                            : List.of(
                                    new VoiceDraws.Candidate(only, e.weight(), Math.max(0, bound)));
        }
        VoiceDraws.Draw draw = VoiceDraws.draw(player.getUUID(), r.checks(), pm, forced, eligible);
        if (draw.result() != VoiceDraws.Result.PICKED) {
            settle(player, null, false);
            return only != null ? Outcome.FAILED : outcome(draw.result());
        }
        String id = eligible.get(draw.index()).id();
        VoiceContent.Event event = Objects.requireNonNull(events.get(id));
        String aspect = "";
        int points = 0;
        WarpAttachment.Offer created = null;
        boolean fired = true;
        boolean cue = true;
        switch (event.type()) {
            case VoiceContent.INSIGHT -> {
                aspect = known.get(draw.param());
                points = event.points();
                KnowledgeService.credit(
                        player,
                        ResourceKey.create(AspectKeys.REGISTRY, Identifier.parse(aspect)),
                        points);
                playToOwner(player, WarpContent.sound(WarpContent.VOICE_INSIGHT));
            }
            case VoiceContent.BARGAIN -> {
                List<String> ids = new ArrayList<>(bargains.keySet());
                int[] weights =
                        bargains.values().stream().mapToInt(VoiceContent.Bargain::weight).toArray();
                String bargainId = ids.get(VoiceDraws.weighted(draw.param(), weights));
                VoiceContent.Bargain b = Objects.requireNonNull(bargains.get(bargainId));
                created =
                        new WarpAttachment.Offer(
                                ++WarpService.transientOf(player).offerSeq,
                                Identifier.parse(bargainId),
                                Identifier.parse(b.effect()),
                                b.amplifier(),
                                b.duration(),
                                b.costTemporary(),
                                b.costLasting(),
                                g + rules.bargainTtl());
            }
            case VoiceContent.STATUS -> {
                ServerLevel level = player.level();
                fired = false;
                List<VoiceContent.Effect> effects = event.effects();
                for (int i = 0; i < effects.size(); i++) {
                    if (i > 0 && !dangerEligible(player, level)) {
                        break;
                    }
                    VoiceContent.Effect f = effects.get(i);
                    fired |=
                            addEffect(
                                    player,
                                    Identifier.parse(f.effect()),
                                    f.duration(),
                                    f.amplifier());
                }
                cue = dangerEligible(player, level);
            }
            default -> {}
        }
        settle(player, created, fired);
        if (fired && cue) {
            send(
                    player,
                    new VoicePayloads.VoiceCue(id, event.type(), band, draw.cue(), aspect, points));
        }
        return fired ? Outcome.FIRED : Outcome.FAILED;
    }

    /**
     * Writes the check under the guard: {@code checks} + 1, the counter lowered for a fired event,
     * and the offer (an expired one deleted, a new one stored). A held guard skips the write.
     */
    private static void settle(
            ServerPlayer player, WarpAttachment.@Nullable Offer offer, boolean fired) {
        try (OperationGuard.Scope scope = WarpService.GUARD.tryEnter(player.getUUID())) {
            if (scope == null) {
                return;
            }
            WarpRecord r = WarpService.get(player).readable();
            if (r == null) {
                return;
            }
            WarpRules rules = WarpRulesService.current();
            int counter = fired ? WarpMath.counterAfterEvent(r.counter(), rules) : r.counter();
            WarpRecord next =
                    new WarpRecord(
                            WarpRecord.FORMAT,
                            r.temporary(),
                            r.lasting(),
                            r.permanent(),
                            r.gear(),
                            counter,
                            r.checks() + 1,
                            r.milestonesPaid(),
                            r.lastListen(),
                            r.silenced(),
                            r.revision() + 1);
            // A new offer replaces none (a bargain needs no live one); otherwise the offer as it is
            // now, after any callback's Silencing or death, with an expired one deleted.
            WarpAttachment.Offer now = WarpService.get(player).offer();
            WarpAttachment.Offer kept =
                    offer != null
                            ? offer
                            : now != null && WarpService.gameTime(player) < now.expires()
                                    ? now
                                    : null;
            WarpService.write(
                    player,
                    r,
                    new WarpAttachment(new Retained.Read<>(next), kept),
                    WarpService.source(WarpSourceKind.COMMAND));
        }
    }

    private static Outcome outcome(VoiceDraws.Result result) {
        return result == VoiceDraws.Result.QUIET ? Outcome.QUIET : Outcome.NONE;
    }

    // ---- Inputs -------------------------------------------------------------------------------

    /** The {@code vftv:voice_event} entries by id, ascending. */
    static SortedMap<String, VoiceContent.Event> events(ServerPlayer player) {
        SortedMap<String, VoiceContent.Event> out = new TreeMap<>();
        Registry<com.andrewbrogoff.vftv.api.warp.VoiceEvent> registry =
                player.level().registryAccess().lookupOrThrow(WarpKeys.VOICE_EVENT_REGISTRY);
        registry.entrySet()
                .forEach(
                        e ->
                                out.put(
                                        e.getKey().identifier().toString(),
                                        ((WarpContent.VoiceEventEntry) e.getValue()).event()));
        return out;
    }

    /** The bargains whose cost previews unclipped for the player, by id, ascending. */
    static SortedMap<String, VoiceContent.Bargain> eligibleBargains(
            ServerPlayer player, WarpRecord r, WarpRules rules) {
        SortedMap<String, VoiceContent.Bargain> out = new TreeMap<>();
        for (Map.Entry<
                        ResourceKey<com.andrewbrogoff.vftv.api.warp.Bargain>,
                        com.andrewbrogoff.vftv.api.warp.Bargain>
                e :
                        player.level()
                                .registryAccess()
                                .lookupOrThrow(WarpKeys.BARGAIN_REGISTRY)
                                .entrySet()) {
            VoiceContent.Bargain b = ((WarpContent.BargainEntry) e.getValue()).bargain();
            Identifier id = e.getKey().identifier();
            WarpPreview p =
                    WarpMath.preview(
                            r.temporary(),
                            r.lasting(),
                            r.permanent(),
                            r.paid(),
                            b.costTemporary(),
                            b.costLasting(),
                            0,
                            WarpSourceKind.BARGAIN,
                            id.toString(),
                            rules);
            if (p != null && !p.clipped()) {
                out.put(id.toString(), b);
            }
        }
        return out;
    }

    /** The player's known aspects in ascending id order. */
    private static List<String> known(ServerPlayer player) {
        Knowledge k = KnowledgeService.get(player);
        return k == null ? List.of() : new ArrayList<>(new java.util.TreeSet<>(k.known()));
    }

    // ---- Effects and sounds -------------------------------------------------------------------

    /**
     * One vanilla {@code addEffect} call: its return value is the result; a throw is logged once
     * and counts as false.
     */
    static boolean addEffect(ServerPlayer player, Identifier effect, int duration, int amplifier) {
        Holder<MobEffect> holder = BuiltInRegistries.MOB_EFFECT.get(effect).orElse(null);
        if (holder == null) {
            return false;
        }
        try {
            return player.addEffect(new MobEffectInstance(holder, duration, amplifier));
        } catch (RuntimeException e) {
            if (THROWN.add(effect.toString())) {
                LOGGER.warn("An effect callback for {} threw; it counts as refused", effect, e);
            }
            return false;
        }
    }

    /** Plays an effect sound to the owner alone. */
    static void playToOwner(ServerPlayer player, SoundEvent sound) {
        player.connection.send(
                new ClientboundSoundPacket(
                        BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound),
                        SoundSource.PLAYERS,
                        player.getX(),
                        player.getY(),
                        player.getZ(),
                        1.0F,
                        1.0F,
                        player.getRandom().nextLong()));
    }
}
