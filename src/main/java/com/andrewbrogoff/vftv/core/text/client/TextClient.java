package com.andrewbrogoff.vftv.core.text.client;

import com.andrewbrogoff.vftv.core.common.client.ClientNetwork;
import com.andrewbrogoff.vftv.core.text.StatusLine;
import com.andrewbrogoff.vftv.core.text.model.CaptionBand;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Objects;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The text feature's client half: the status line's handler, the client tick and the font
 * generation every cached text layout keys on. Platform listeners call these in one line each.
 */
public final class TextClient {
    private static @Nullable Object level;
    private static int ticks;
    private static int reloads;

    private TextClient() {}

    /** Registers the status line's handler; called once from {@code Vftv.initClient}. */
    public static void init(ClientNetwork network) {
        network.handler(
                StatusLine.TYPE,
                line -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    // A level change handled earlier in this packet batch clears before, not after.
                    syncLevel(minecraft);
                    StatusLineHud.arrive(
                            line.text(), minecraft.options.notificationDisplayTime().get());
                });
    }

    /** Once per client tick, after the game's own tick. */
    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        syncLevel(minecraft);
        ticks++;
        StatusLineHud.tick();
        TextKeys.tick(minecraft);
    }

    /** Forgets the status line at a level change or disconnect. */
    private static void syncLevel(Minecraft minecraft) {
        if (!Objects.equals(minecraft.level, level)) {
            level = minecraft.level;
            StatusLineHud.clear();
        }
    }

    /** After every client resource reload, which a language change includes. */
    public static void onResourcesReloaded() {
        reloads++;
    }

    /**
     * The E2E harness's {@code vftv:text} dump (client thread): the status line and the published
     * caption band. {@code {"arrive": "<text>"}} first delivers that text as a status line on the
     * client, as the payload handler would, for the scenario's render and key checks.
     */
    public static String dump(String args) {
        JsonObject request = JsonParser.parseString(args).getAsJsonObject();
        if (request.has("arrive")) {
            StatusLineHud.arrive(
                    Component.literal(request.get("arrive").getAsString()),
                    Minecraft.getInstance().options.notificationDisplayTime().get());
        }
        Component latest = StatusLineHud.latest();
        CaptionBand band = StatusLineHud.BAND;
        JsonObject data = new JsonObject();
        data.addProperty("statusShowing", StatusLineHud.showing());
        data.addProperty("latest", latest == null ? "" : latest.getString());
        data.addProperty("bandShowing", band.showing());
        data.addProperty("bandTop", band.bandTop());
        data.addProperty("bandBottom", band.bottom());
        data.addProperty("revision", band.revision());
        return data.toString();
    }

    /** The client tick count, for "shown this tick" checks. */
    static int ticks() {
        return ticks;
    }

    /**
     * Changes whenever measured text may: a resource reload (packs, fonts, language), the font
     * options, or a rebinding of the keys the text names. Every cached layout compares it.
     */
    static int fontKey() {
        Minecraft minecraft = Minecraft.getInstance();
        int key =
                reloads * 4
                        + (minecraft.options.forceUnicodeFont().get() ? 1 : 0)
                        + (minecraft.options.japaneseGlyphVariants().get() ? 2 : 0);
        // The hint and the sheet indicator name these keys: a rebinding relays them out.
        for (int i = 0; i < TextKeys.ALL.size(); i++) { // indexed: no iterator per frame
            KeyMapping mapping = TextKeys.ALL.get(i);
            key = 31 * key + mapping.saveString().hashCode();
        }
        return key;
    }
}
