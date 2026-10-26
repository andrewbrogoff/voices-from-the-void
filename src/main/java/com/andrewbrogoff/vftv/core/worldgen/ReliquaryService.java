package com.andrewbrogoff.vftv.core.worldgen;

import com.andrewbrogoff.vftv.api.research.Research;
import com.andrewbrogoff.vftv.api.research.ResearchKeys;
import com.andrewbrogoff.vftv.api.worldgen.WorldgenLimits;
import com.andrewbrogoff.vftv.core.common.DataSlot;
import com.andrewbrogoff.vftv.core.common.OperationGuard;
import com.andrewbrogoff.vftv.core.common.RawRecordCodec.Retained;
import com.andrewbrogoff.vftv.core.research.ResearchItems;
import com.andrewbrogoff.vftv.core.research.ResearchService;
import com.andrewbrogoff.vftv.core.text.StatusLines;
import com.andrewbrogoff.vftv.core.text.model.SharedMessages;
import com.andrewbrogoff.vftv.core.worldgen.model.ChoirClaims;
import com.andrewbrogoff.vftv.core.worldgen.model.Offers;
import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * {@code ReliquaryService.use(player, pos)} (worldgen spec, "Using it" and "Handing out"): the
 * first matching row decides, under the player's Reliquary guard. A new claim is written before any
 * page is handed out; a hand-out that fails or throws loses only that copy, which the next use
 * reissues. Server thread only.
 */
public final class ReliquaryService {
    /** The status-line keys, {@code reliquary.vftv.<row>}. */
    public static final String PREFIX = "reliquary.vftv.";

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final OperationGuard GUARD = new OperationGuard("reliquary");
    private static final Map<UUID, long[]> ATTEMPTS = new HashMap<>();
    private static final Map<UUID, Long> BUSY_SAID = new HashMap<>();
    private static final Map<UUID, Set<String>> CLAIMED = new HashMap<>();
    private static @Nullable DataSlot<Player, Retained<ChoirClaims>> slot;
    private static boolean warnedFull;

    private ReliquaryService() {}

    static void install(DataSlot<Player, Retained<ChoirClaims>> claims) {
        slot = claims;
    }

    /** The {@code vftv:choir_claims} slot. */
    public static DataSlot<Player, Retained<ChoirClaims>> claims() {
        DataSlot<Player, Retained<ChoirClaims>> s = slot;
        if (s == null) {
            throw new IllegalStateException("WorldgenFeature.init has not run");
        }
        return s;
    }

    /** One use of the Reliquary at {@code pos} by {@code player}; returns the row's outcome. */
    public static Offers.Outcome use(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.level();
        UUID uuid = player.getUUID();
        long now = level.getServer().getTickCount();
        if (GUARD.isHeld(uuid)) {
            Long said = BUSY_SAID.put(uuid, now);
            if (said == null || said != now) {
                StatusLines.send(player, Component.translatable(SharedMessages.BUSY));
            }
            return Offers.Outcome.BUSY;
        }
        long[] attempts = ATTEMPTS.computeIfAbsent(uuid, k -> new long[] {Long.MIN_VALUE, 0});
        if (attempts[0] != now) {
            attempts[0] = now;
            attempts[1] = 0;
        }
        if (attempts[1] >= WorldgenLimits.MAX_USES_PER_PLAYER_PER_TICK) {
            return Offers.Outcome.ATTEMPT_CAP;
        }
        attempts[1]++;
        try (var _ = GUARD.tryEnter(uuid)) {
            return decideAndHandOut(player, level, pos);
        }
    }

    private static Offers.Outcome decideAndHandOut(
            ServerPlayer player, ServerLevel level, BlockPos pos) {
        Retained<WorldgenCodecs.Reliquary> record =
                level.getBlockEntity(pos) instanceof ReliquaryBlockEntity be
                        ? be.initialized()
                        : null;
        ChoirClaims read =
                claims().get(player) instanceof Retained.Read<ChoirClaims> c ? c.value() : null;
        WorldgenCodecs.Reliquary reliquary =
                record instanceof Retained.Read<WorldgenCodecs.Reliquary> r ? r.value() : null;
        boolean unreadable = read == null || reliquary == null;
        Offers.Site site = null;
        if (reliquary != null && read != null) {
            site = WorldgenService.site(level.registryAccess(), reliquary.site());
            if (site == null
                    && level.getBlockEntity(pos) instanceof ReliquaryBlockEntity be
                    && be.warnSilentOnce()) {
                LOGGER.warn(
                        "Reliquary at {} names the site {}, which no vftv:choir_site defines",
                        pos,
                        reliquary.site().identifier());
            }
        }
        String dim = level.dimension().identifier().toString();
        ChoirClaims current = read;
        Map<String, ?> entries = ResearchService.data(level.registryAccess()).entries();
        Offers.Decision decision =
                Offers.decide(
                        false,
                        false,
                        unreadable,
                        site,
                        current == null ? null : current.at(dim, pos.asLong()),
                        current == null ? 0 : current.sites().size(),
                        new Offers.State() {
                            @Override
                            public boolean exists(String page) {
                                return entries.containsKey(page);
                            }

                            @Override
                            public boolean held(String page) {
                                return ResearchService.isLatchedOrLearned(player, key(page));
                            }

                            @Override
                            public boolean claimedAnywhere(String page) {
                                return claimed(player, current).contains(page);
                            }
                        });
        switch (decision.outcome()) {
            case GIVEN -> {
                ChoirClaims next =
                        java.util.Objects.requireNonNull(current)
                                .with(dim, pos.asLong(), decision.pages());
                claims().set(player, new Retained.Read<>(next));
                claimed(player, current).addAll(decision.pages());
                handOut(player, decision.pages());
            }
            case REISSUED -> handOut(player, decision.pages());
            case FULL -> {
                if (!warnedFull) {
                    warnedFull = true;
                    LOGGER.warn(
                            "A player reached {} Reliquary claims", WorldgenLimits.MAX_CLAIM_SITES);
                }
            }
            default -> {}
        }
        boolean give =
                decision.outcome() == Offers.Outcome.GIVEN
                        || decision.outcome() == Offers.Outcome.REISSUED;
        level.playSound(
                null,
                pos,
                WorldgenContent.sound(
                        give ? WorldgenContent.RELIQUARY_GIVE : WorldgenContent.RELIQUARY_EMPTY),
                SoundSource.BLOCKS,
                give ? 1.0F : 0.6F,
                1.0F);
        StatusLines.send(
                player,
                Component.translatable(
                        decision.outcome() == Offers.Outcome.UNREADABLE
                                ? SharedMessages.UNREADABLE
                                : PREFIX + decision.outcome().id()));
        return decision.outcome();
    }

    /** Each page into the inventory, the remainder dropped; a failure loses only that copy. */
    private static void handOut(ServerPlayer player, List<String> pages) {
        boolean logged = false;
        for (String page : pages) {
            try {
                ItemStack stack = new ItemStack(ResearchItems.choirPage());
                stack.set(ResearchItems.page(), key(page));
                player.getInventory().add(stack);
                if (!stack.isEmpty()) {
                    player.drop(stack, false);
                }
            } catch (RuntimeException e) {
                if (!logged) {
                    logged = true;
                    LOGGER.error("A callback threw while the Reliquary handed out {}", page, e);
                }
            }
        }
    }

    /** The player's "claimed anywhere" set, built once from the record and kept in step. */
    private static Set<String> claimed(Player player, @Nullable ChoirClaims claims) {
        return CLAIMED.computeIfAbsent(
                player.getUUID(),
                k -> claims == null ? new java.util.HashSet<>() : claims.claimedAnywhere());
    }

    private static ResourceKey<Research> key(String page) {
        return ResourceKey.create(ResearchKeys.REGISTRY, Identifier.parse(page));
    }

    /** Builds the player's claimed set at login. */
    public static void login(ServerPlayer player) {
        forget(player);
        switch (claims().get(player)) {
            case Retained.Read<ChoirClaims> r -> claimed(player, r.value());
            case Retained.Unreadable<ChoirClaims> u ->
                    LOGGER.warn(
                            "{}'s vftv:choir_claims record is unreadable ({}); Reliquaries refuse"
                                    + " them until it reads",
                            player.getGameProfile().name(),
                            u.reason());
        }
    }

    /** Drops the player's in-memory state: at logout, or after a direct change to their claims. */
    public static void forget(Player player) {
        UUID uuid = player.getUUID();
        CLAIMED.remove(uuid);
        ATTEMPTS.remove(uuid);
        BUSY_SAID.remove(uuid);
    }

    /** Drops every in-memory state when the server stops. */
    public static void clear() {
        CLAIMED.clear();
        ATTEMPTS.clear();
        BUSY_SAID.clear();
        warnedFull = false;
    }
}
