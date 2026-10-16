package com.andrewbrogoff.vftv.core.text.client;

import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * The narration helper (accessibility, "HUD narration"). Owners call {@link #say} once when a
 * deliberate act's readout arrives, whatever the narrator mode; vanilla's {@code
 * GameNarrator.saySystemQueued} alone decides from the player's mode whether anything is spoken.
 * Passive HUDs are never narrated.
 */
public final class Narration {
    private static Consumer<Component> sink =
            text -> Minecraft.getInstance().getNarrator().saySystemQueued(text);

    private Narration() {}

    /** Attempts to narrate {@code text} once. */
    public static void say(Component text) {
        sink.accept(text);
    }

    /**
     * Replaces the sink and returns the previous one: unit tests record what each act attempts in
     * place of the platform narrator.
     */
    public static Consumer<Component> swapSink(Consumer<Component> replacement) {
        Consumer<Component> previous = sink;
        sink = replacement;
        return previous;
    }
}
