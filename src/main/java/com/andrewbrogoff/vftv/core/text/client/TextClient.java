package com.andrewbrogoff.vftv.core.text.client;

import com.andrewbrogoff.vftv.core.common.client.ClientNetwork;
import com.andrewbrogoff.vftv.core.text.StatusLine;
import java.util.Objects;
import net.minecraft.client.Minecraft;
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
                line ->
                        StatusLineHud.arrive(
                                line.text(),
                                Minecraft.getInstance().options.notificationDisplayTime().get()));
    }

    /** Once per client tick, after the game's own tick. */
    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!Objects.equals(minecraft.level, level)) {
            level = minecraft.level;
            StatusLineHud.clear();
        }
        ticks++;
        StatusLineHud.tick();
        TextKeys.tick(minecraft);
    }

    /** After every client resource reload, which a language change includes. */
    public static void onResourcesReloaded() {
        reloads++;
    }

    /** The client tick count, for "shown this tick" checks. */
    static int ticks() {
        return ticks;
    }

    /**
     * Changes whenever measured text widths may: a resource reload (packs, fonts, language) or the
     * font options. Every cached layout compares it.
     */
    static int fontKey() {
        Minecraft minecraft = Minecraft.getInstance();
        return reloads * 2 + (minecraft.options.forceUnicodeFont().get() ? 1 : 0);
    }
}
