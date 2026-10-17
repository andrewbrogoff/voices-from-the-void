package com.andrewbrogoff.vftv.core.essentia.client;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.core.aspect.AspectIcons;
import com.andrewbrogoff.vftv.core.aspect.AspectLang;
import com.andrewbrogoff.vftv.core.aspect.AspectSet;
import com.andrewbrogoff.vftv.core.aspect.KnowledgeService;
import com.andrewbrogoff.vftv.core.aspect.model.Knowledge;
import com.andrewbrogoff.vftv.core.common.model.StrictJson;
import com.andrewbrogoff.vftv.core.essentia.EssentiaRules;
import com.andrewbrogoff.vftv.core.essentia.EssentiaStatus;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The client's essentia state: the last {@link EssentiaRules} received (dropped at disconnect), the
 * mote numbers from {@code assets/vftv/essentia/client.json}, and the masked aspect names and
 * glyphs every client surface uses. Client thread only.
 */
public final class ClientEssentia {
    /** The resource-pack file of the mote numbers. */
    public static final Identifier CLIENT_FILE =
            Identifier.fromNamespaceAndPath("vftv", "essentia/client.json");

    /** The crucible's missing-aspect sprite, reused for a missing glyph. */
    public static final Identifier MISSING_SPRITE =
            Identifier.fromNamespaceAndPath("vftv", "hud/crucible_missing");

    private static final Logger LOGGER = LogUtils.getLogger();
    private static @Nullable EssentiaRules rules;
    private static double moteSpeed = 0.25;
    private static double moteSize = 0.25;

    private ClientEssentia() {}

    /** The last rules received, or null before the first and after disconnect. */
    public static @Nullable EssentiaRules rules() {
        return rules;
    }

    static void setRules(EssentiaRules received) {
        rules = received;
    }

    /** Drops the rules and motes, at disconnect. */
    public static void clear() {
        rules = null;
        FlowMotes.clear();
    }

    /** The jar capacity the renderers draw against: the received rules', or the shipped default. */
    public static int capacity() {
        EssentiaRules r = rules;
        return (r == null ? EssentiaRules.DEFAULTS : r).tuning().jarCapacity();
    }

    /** Blocks per tick a mote travels. */
    public static double moteSpeed() {
        return moteSpeed;
    }

    /** A mote's size in blocks. */
    public static double moteSize() {
        return moteSize;
    }

    /**
     * Re-reads the mote numbers on a resource reload: an invalid override is ignored with a logged
     * error, keeping the built-in values.
     */
    public static void reloadClientFile(ResourceManager manager) {
        double speed = 0.25;
        double size = 0.25;
        for (Resource resource : manager.getResourceStack(CLIENT_FILE)) {
            try (InputStream in = resource.open()) {
                JsonElement root =
                        StrictJson.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                JsonObject mote = root.getAsJsonObject().getAsJsonObject("mote");
                double s = mote.get("speed").getAsDouble();
                double z = mote.get("size").getAsDouble();
                if (!"vftv:essentia_client/1"
                                .equals(root.getAsJsonObject().get("schema").getAsString())
                        || s < 0.05
                        || s > 2.0
                        || z < 0.0625
                        || z > 1.0) {
                    throw new IllegalArgumentException("out of range");
                }
                speed = s;
                size = z;
            } catch (IOException | RuntimeException e) {
                LOGGER.error(
                        "Ignoring an invalid {} from {}: {}",
                        CLIENT_FILE,
                        resource.sourcePackId(),
                        e.toString());
            }
        }
        moteSpeed = speed;
        moteSize = size;
    }

    /**
     * The harness's {@code vftv:essentia} dump: whether rules arrived, the capacity, the mote
     * numbers and live motes, and with {@code args} "x y z" the client's view of a jar there.
     */
    public static String dump(String args) {
        JsonObject data = new JsonObject();
        data.addProperty("rules", rules != null);
        data.addProperty("capacity", capacity());
        data.addProperty("moteSpeed", moteSpeed);
        data.addProperty("moteSize", moteSize);
        data.addProperty("motes", FlowMotes.live());
        var level = Minecraft.getInstance().level;
        if (level != null) {
            try {
                var xyz = JsonParser.parseString(args).getAsJsonObject().getAsJsonArray("pos");
                var pos =
                        xyz == null
                                ? null
                                : new net.minecraft.core.BlockPos(
                                        xyz.get(0).getAsInt(),
                                        xyz.get(1).getAsInt(),
                                        xyz.get(2).getAsInt());
                if (pos != null
                        && level.getBlockEntity(pos)
                                instanceof
                                com.andrewbrogoff.vftv.core.essentia.JarBlockEntity jar) {
                    var c = jar.contents();
                    data.addProperty("readable", jar.readable());
                    data.addProperty(
                            "aspect", c.aspect() == null ? "" : c.aspect().identifier().toString());
                    data.addProperty("amount", c.amount());
                    data.addProperty(
                            "label",
                            jar.label() == null ? "" : jar.label().identifier().toString());
                }
            } catch (RuntimeException e) {
                data.addProperty("error", "pos is [x, y, z]");
            }
        }
        return data.toString();
    }

    /** The aspect's holder in the client's registries, if it has one. */
    static Optional<Holder.Reference<Aspect>> holder(ResourceKey<Aspect> key) {
        var level = Minecraft.getInstance().level;
        return level == null
                ? Optional.empty()
                : level.registryAccess().lookupOrThrow(AspectKeys.REGISTRY).get(key);
    }

    private static boolean knows(ResourceKey<Aspect> key) {
        Player player = Minecraft.getInstance().player;
        Knowledge k = player == null ? null : KnowledgeService.get(player);
        return k != null && k.known().contains(AspectSet.text(key));
    }

    /** An aspect's name, masked: unheard if the viewer hasn't discovered it, missing if gone. */
    public static Component name(ResourceKey<Aspect> key) {
        if (holder(key).isEmpty()) {
            return Component.translatable(EssentiaStatus.MISSING);
        }
        return knows(key) ? AspectLang.name(key) : Component.translatable(EssentiaStatus.UNHEARD);
    }

    /** An aspect's glyph sprite in the GUI atlas: its icon, unheard, or the missing sprite. */
    public static Identifier glyph(ResourceKey<Aspect> key) {
        Optional<Holder.Reference<Aspect>> h = holder(key);
        if (h.isEmpty()) {
            return MISSING_SPRITE;
        }
        return knows(key) ? AspectIcons.of(h.get()) : AspectIcons.UNHEARD;
    }

    /** An aspect's tint, opaque; a missing one takes Aqua's. */
    public static int color(ResourceKey<Aspect> key) {
        Optional<Holder.Reference<Aspect>> h = holder(key);
        if (h.isEmpty()) {
            h = holder(AspectKeys.AQUA);
        }
        return 0xFF000000 | h.map(a -> a.value().color()).orElse(0x4A78C4);
    }
}
