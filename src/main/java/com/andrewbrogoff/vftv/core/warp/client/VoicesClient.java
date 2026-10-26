package com.andrewbrogoff.vftv.core.warp.client;

import com.andrewbrogoff.vftv.api.warp.WarpLimits;
import com.andrewbrogoff.vftv.core.common.client.ClientNetwork;
import com.andrewbrogoff.vftv.core.common.model.StrictJson;
import com.andrewbrogoff.vftv.core.text.client.StatusLineHud;
import com.andrewbrogoff.vftv.core.text.client.TextFit;
import com.andrewbrogoff.vftv.core.text.model.CaptionBand;
import com.andrewbrogoff.vftv.core.warp.Apparition;
import com.andrewbrogoff.vftv.core.warp.VoicePayloads;
import com.andrewbrogoff.vftv.core.warp.WarpContent;
import com.andrewbrogoff.vftv.core.warp.model.ColourRamp;
import com.andrewbrogoff.vftv.core.warp.model.CueDraws;
import com.andrewbrogoff.vftv.core.warp.model.VoiceContent;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.EntityHitResult;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * What one Listener perceives (warp spec, "What a Listener perceives"), on the owner's client only:
 * the cue held while a screen is open, Voice lines and their text, phantom sounds, the colour drain
 * and apparitions; and the {@code VoiceQuiet} screen poll. Client thread only; never touches server
 * state.
 */
@SuppressWarnings(
        "ReferenceEquality") // players, levels and the apparition are compared as instances
public final class VoicesClient {
    /** The Voice text's wrap width. */
    public static final int TEXT_WIDTH = 240;

    /** An apparition vanishes with the player this close, in blocks. */
    public static final double TOUCH_RADIUS = 4.0;

    /** An apparition's lifetime in ticks. */
    public static final int APPARITION_TICKS = 400;

    /** The Voice text's extra ticks beyond its clip. */
    public static final int TEXT_EXTRA_TICKS = 20;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Identifier PHANTOMS = id("voices/phantoms.json");
    private static final Identifier LINES = id("voices/lines.json");

    private static @Nullable ClientNetwork network;
    private static WarpClientConfig config = WarpClientConfig.DEFAULT;

    /** One line of the generated {@code lines.json}: its pool, a Void segment, its clip ticks. */
    record LineInfo(String pool, boolean voids, int ticks) {}

    private static Map<String, List<String>> pools = Map.of();
    private static Map<String, LineInfo> lines = Map.of();
    private static List<Identifier> phantomSounds = List.of();
    private static int reloads;

    private static VoicePayloads.@Nullable VoiceCue held;
    private static int heldTicks;

    private static @Nullable Component text;
    private static boolean textVoid;
    private static int textTicks;
    private static int lineTicks;
    private static @Nullable SoundInstance lineSound;

    private static final ColourRamp DRAIN = new ColourRamp();
    private static float drainBefore;

    private static @Nullable Apparition apparition;
    private static int apparitionAge;

    private static @Nullable Boolean sentOpen;
    private static @Nullable LocalPlayer seenPlayer;
    private static @Nullable ClientLevel seenLevel;

    private VoicesClient() {}

    static void init(ClientNetwork seam) {
        network = seam;
    }

    /** Takes the client options; {@code voices.phantoms} off removes an apparition silently. */
    public static void installConfig(WarpClientConfig next) {
        config = next;
        StatusLineHud.voice(visibleLines());
    }

    /** The client options in force. */
    public static WarpClientConfig config() {
        return config;
    }

    // ---- Cues ---------------------------------------------------------------------------------

    /** {@code VoiceCue}: plays now, or is held (replacing any held one) while a screen is open. */
    public static void onCue(VoicePayloads.VoiceCue cue) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.screen() != null) {
            held = cue;
            heldTicks = 0;
            return;
        }
        play(minecraft, cue);
    }

    /** {@code VoiceClear}, a level change, a new player or logout: every presentation stops. */
    public static void clear() {
        held = null;
        text = null;
        textTicks = 0;
        lineTicks = 0;
        if (lineSound != null) {
            Minecraft.getInstance().getSoundManager().stop(lineSound);
            lineSound = null;
        }
        StatusLineHud.voice(0);
        DRAIN.clear();
        drainBefore = 0;
        removeApparition(false);
    }

    private static void play(Minecraft minecraft, VoicePayloads.VoiceCue cue) {
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null) {
            return;
        }
        Random random = new Random(cue.seed());
        switch (cue.kind()) {
            case VoiceContent.WHISPER -> line(minecraft, random, "band" + cue.band(), true);
            case VoiceContent.INSIGHT -> {
                line(minecraft, random, "insight", false);
                Identifier aspect = Identifier.tryParse(cue.aspect());
                if (aspect != null) {
                    StatusLineHud.arrive(
                            Component.translatable(
                                    "voice.vftv.insight_gain",
                                    Component.translatable(
                                            "aspect."
                                                    + aspect.getNamespace()
                                                    + "."
                                                    + aspect.getPath()),
                                    cue.points()),
                            minecraft.options.notificationDisplayTime().get());
                }
            }
            case VoiceContent.BARGAIN -> line(minecraft, random, "offer", false);
            case VoiceContent.SOUND -> {
                CueDraws.Sound s = CueDraws.sound(random, phantomSounds.size());
                if (s != null && config.phantomSounds()) {
                    double yaw = Math.toRadians(s.yaw());
                    SoundEvent sound =
                            SoundEvent.createVariableRangeEvent(phantomSounds.get(s.index()));
                    level.playLocalSound(
                            player.getX() - Math.sin(yaw) * s.dist(),
                            player.getY() + 1,
                            player.getZ() + Math.cos(yaw) * s.dist(),
                            sound,
                            SoundSource.AMBIENT,
                            1.0F,
                            1.0F,
                            false);
                }
            }
            case VoiceContent.COLOUR -> {
                if (config.colourShift()) {
                    DRAIN.start(cue.band());
                }
            }
            case VoiceContent.PHANTOM_MOB -> {
                if (config.phantoms()) {
                    spawnApparition(level, player, random);
                }
            }
            default -> {}
        }
    }

    /** A line from {@code pool}, unless one plays: its sound if it has audio, and its text. */
    private static void line(Minecraft minecraft, Random random, String pool, boolean whisper) {
        if (lineTicks > 0) {
            return; // one line at a time: a line cue arriving while one plays is dropped
        }
        CueDraws.Line drawn = CueDraws.line(random, pools.getOrDefault(pool, List.of()), whisper);
        LineInfo info = drawn == null ? null : lines.get(drawn.line());
        if (drawn == null || info == null) {
            return;
        }
        Identifier id = Identifier.parse(drawn.line());
        lineTicks = info.ticks();
        Identifier sound =
                Identifier.fromNamespaceAndPath(id.getNamespace(), "voice." + id.getPath());
        // A line without audio yet (the owner's recordings) shows its text alone.
        if (minecraft.getSoundManager().getSoundEvent(sound) != null) {
            double side = drawn.side() == 0 ? -1 : drawn.side() == 1 ? 1 : 0;
            lineSound =
                    new SimpleSoundInstance(
                            sound,
                            SoundSource.VOICE,
                            1.0F,
                            1.0F,
                            RandomSource.create(random.nextLong()),
                            false,
                            0,
                            SoundInstance.Attenuation.NONE,
                            side,
                            0,
                            0,
                            true);
            minecraft.getSoundManager().play(lineSound);
        }
        text = Component.translatable(VoiceContent.langKey(drawn.line()));
        textVoid = info.voids();
        textTicks =
                CaptionBand.hold(
                        info.ticks() + TEXT_EXTRA_TICKS,
                        minecraft.options.notificationDisplayTime().get());
        StatusLineHud.voice(visibleLines());
    }

    // ---- Ticks --------------------------------------------------------------------------------

    /** One client tick: the screen poll, a held cue, the line, the drain and the apparition. */
    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player != seenPlayer || level != seenLevel) {
            seenPlayer = player;
            seenLevel = level;
            clear();
            sentOpen = null; // a replaced player sees no new transition: reassert
        }
        if (player == null || level == null) {
            return;
        }
        boolean open = minecraft.gui.screen() != null;
        if (sentOpen == null || sentOpen != open) {
            ClientNetwork seam = network;
            if (seam != null && minecraft.getConnection() != null) {
                seam.toServer(new VoicePayloads.VoiceQuiet(open));
                sentOpen = open;
            }
        }
        VoicePayloads.VoiceCue cue = held;
        if (cue != null) {
            if (!open) {
                held = null;
                play(minecraft, cue);
            } else if (++heldTicks > WarpLimits.MAX_CUE_HOLD) {
                held = null;
            }
        }
        if (lineTicks > 0) {
            lineTicks--;
        }
        if (textTicks > 0 && --textTicks == 0) {
            text = null;
            StatusLineHud.voice(0);
        }
        drainBefore = DRAIN.intensity();
        DRAIN.tick(config.colourShift());
        Apparition a = apparition;
        if (a != null) {
            if (!config.phantoms() || a.isRemoved() || a.level() != level) {
                removeApparition(false);
            } else if (++apparitionAge >= APPARITION_TICKS
                    || a.distanceTo(player) <= TOUCH_RADIUS) {
                removeApparition(true);
            }
        }
    }

    // ---- The Voice text -----------------------------------------------------------------------

    /** The text showing, or null. */
    static @Nullable Component text() {
        return config.text() ? text : null;
    }

    /** Whether the showing line has a Void segment (amethyst, else hush). */
    static boolean textVoid() {
        return textVoid;
    }

    /** How many resource reloads have run: fonts may have changed. */
    static int reloads() {
        return reloads;
    }

    /** Ticks the text has left. */
    static int textTicks() {
        return textTicks;
    }

    /** The wrapped line count the caption band reserves: at most 4; 0 when nothing shows. */
    private static int visibleLines() {
        Component t = text();
        if (t == null) {
            return 0;
        }
        List<FormattedText> wrapped = TextFit.wrap(Minecraft.getInstance().font, t, TEXT_WIDTH);
        return Math.min(CaptionBand.VOICE_MAX_LINES, wrapped.size());
    }

    // ---- The colour drain ---------------------------------------------------------------------

    /** The drain's intensity this frame, interpolated between ticks, before the effect scale. */
    public static float drain(float partialTick) {
        return Mth.lerp(partialTick, drainBefore, DRAIN.intensity());
    }

    // ---- Apparitions --------------------------------------------------------------------------

    /**
     * Places an apparition (warp spec, "Phantoms"): up to 16 attempts, each a yaw and distance,
     * then a column scan for a standing cell; it faces the player. Any earlier one vanishes first.
     */
    private static void spawnApparition(ClientLevel level, LocalPlayer player, Random random) {
        removeApparition(false);
        int eye = Mth.floor(player.getEyeY());
        for (int i = 0; i < WarpLimits.MAX_APPARITION_ATTEMPTS; i++) {
            CueDraws.Attempt a = CueDraws.attempt(random);
            double yaw = Math.toRadians(a.yaw());
            int x = Mth.floor(player.getX() - Math.sin(yaw) * a.dist());
            int z = Mth.floor(player.getZ() + Math.cos(yaw) * a.dist());
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            int y =
                    CueDraws.column(
                            eye,
                            cy -> level.isLoaded(pos.set(x, cy, z)),
                            cy -> {
                                BlockState state = level.getBlockState(pos.set(x, cy, z));
                                return state.isSolidRender()
                                        && state.isCollisionShapeFullBlock(level, pos);
                            },
                            cy -> level.getBlockState(pos.set(x, cy, z)).isAir());
            if (y == Integer.MIN_VALUE) {
                continue;
            }
            Apparition apparitionEntity = new Apparition(WarpContent.apparition(), level);
            int id = -0x5650;
            while (level.getEntity(id) != null) {
                id--;
            }
            apparitionEntity.setId(id);
            double ax = x + 0.5;
            double az = z + 0.5;
            float facing =
                    (float) (Mth.atan2(player.getZ() - az, player.getX() - ax) * Mth.RAD_TO_DEG)
                            - 90F;
            apparitionEntity.snapTo(ax, y, az, facing, 0F);
            apparitionEntity.setYHeadRot(facing);
            level.addEntity(apparitionEntity);
            if (level.getEntity(id) == apparitionEntity) {
                apparition = apparitionEntity;
                apparitionAge = 0;
            }
            return;
        }
    }

    /**
     * An attack or use input whose crosshair hit is the apparition: it vanishes, and the input is
     * cancelled with no swing, so nothing is sent.
     *
     * @return whether the input must be cancelled
     */
    public static boolean onInput() {
        Apparition a = apparition;
        if (a != null
                && Minecraft.getInstance().hitResult instanceof EntityHitResult hit
                && hit.getEntity() == a) {
            removeApparition(true);
            return true;
        }
        return false;
    }

    /** The apparition the client shows, or null; for the harness dump. */
    static @Nullable Entity apparition() {
        return apparition;
    }

    private static void removeApparition(boolean sound) {
        Apparition a = apparition;
        apparition = null;
        if (a == null) {
            return;
        }
        if (a.level() instanceof ClientLevel level && !a.isRemoved()) {
            if (sound) {
                level.playLocalSound(
                        a.getX(),
                        a.getY(),
                        a.getZ(),
                        WarpContent.sound(WarpContent.APPARITION_VANISH),
                        SoundSource.AMBIENT,
                        1.0F,
                        1.0F,
                        false);
            }
            level.removeEntity(a.getId(), Entity.RemovalReason.DISCARDED);
        }
    }

    // ---- Resources ----------------------------------------------------------------------------

    /**
     * Reads {@code assets/vftv/voices/phantoms.json} and {@code lines.json}, the topmost valid file
     * of each winning; an invalid file is skipped with one logged error.
     */
    public static void onResources(ResourceManager manager) {
        List<Identifier> sounds = List.of();
        for (Resource r : top(manager, PHANTOMS)) {
            List<Identifier> read = phantoms(r);
            if (read != null) {
                sounds = read;
                break;
            }
        }
        phantomSounds = sounds;
        Map<String, LineInfo> readLines = Map.of();
        for (Resource r : top(manager, LINES)) {
            Map<String, LineInfo> read = lines(r);
            if (read != null) {
                readLines = read;
                break;
            }
        }
        lines = readLines;
        Map<String, LineInfo> read = readLines;
        pools =
                CueDraws.sorted(read.keySet()).stream()
                        .collect(
                                Collectors.groupingBy(
                                        id -> Objects.requireNonNull(read.get(id)).pool()));
        reloads++;
    }

    private static List<Resource> top(ResourceManager manager, Identifier id) {
        List<Resource> stack = new ArrayList<>(manager.getResourceStack(id));
        java.util.Collections.reverse(stack);
        return stack;
    }

    private static @Nullable List<Identifier> phantoms(Resource resource) {
        try {
            JsonObject json = parse(resource);
            JsonArray array = json.getAsJsonArray("sounds");
            if (json.size() != 1 || array == null || array.isEmpty() || array.size() > 32) {
                throw new IllegalArgumentException("not 1 to 32 sounds");
            }
            List<Identifier> out = new ArrayList<>();
            for (JsonElement e : array) {
                Identifier id = Identifier.parse(e.getAsString());
                if (!BuiltInRegistries.SOUND_EVENT.containsKey(id)) {
                    throw new IllegalArgumentException("unknown sound " + id);
                }
                out.add(id);
            }
            return List.copyOf(out);
        } catch (RuntimeException | IOException e) {
            LOGGER.error(
                    "Ignoring {} from {}: {}", PHANTOMS, resource.sourcePackId(), e.getMessage());
            return null;
        }
    }

    private static @Nullable Map<String, LineInfo> lines(Resource resource) {
        try {
            JsonObject all = parse(resource).getAsJsonObject("lines");
            Map<String, LineInfo> out = new HashMap<>();
            for (Map.Entry<String, JsonElement> e : all.entrySet()) {
                JsonObject o = e.getValue().getAsJsonObject();
                String pool = o.get("pool").getAsString();
                int ticks = o.get("ticks").getAsInt();
                if (!VoiceContent.POOLS.containsKey(pool) || ticks < 1 || ticks > 200) {
                    throw new IllegalArgumentException("bad line " + e.getKey());
                }
                Identifier.parse(e.getKey());
                out.put(e.getKey(), new LineInfo(pool, o.get("void").getAsBoolean(), ticks));
            }
            return Map.copyOf(out);
        } catch (RuntimeException | IOException e) {
            LOGGER.error("Ignoring {} from {}: {}", LINES, resource.sourcePackId(), e.getMessage());
            return null;
        }
    }

    private static JsonObject parse(Resource resource) throws IOException {
        try (java.io.InputStream in = resource.open()) {
            return StrictJson.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject();
        }
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("vftv", path);
    }

    /** A JSON view for the harness's {@code vftv:voices_client} dump. */
    public static String dump() {
        JsonObject json = new JsonObject();
        Component t = text();
        json.addProperty("text", t == null ? "" : t.getString());
        json.addProperty("textVoid", textVoid);
        json.addProperty("textTicks", textTicks);
        json.addProperty("lineTicks", lineTicks);
        json.addProperty("held", held != null);
        json.addProperty("drain", DRAIN.intensity());
        json.addProperty("apparition", apparition != null);
        Apparition a = apparition;
        if (a != null) {
            json.addProperty("apparitionX", a.getX());
            json.addProperty("apparitionY", a.getY());
            json.addProperty("apparitionZ", a.getZ());
        }
        json.addProperty("phantomSounds", phantomSounds.size());
        json.addProperty("lines", lines.size());
        json.addProperty("quietSent", sentOpen != null && sentOpen);
        return json.toString();
    }
}
