package com.andrewbrogoff.vftv.core.research;

import com.andrewbrogoff.vftv.core.research.model.FactSpan;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * One fact source of book prose, {@code {fact:<source>:...}} (research spec, "Prose and fact
 * references"): each feature research doesn't import registers its own at init, so research never
 * imports it. The span's arity is fixed by {@link FactSpan#ARITY}; the source checks its keys and
 * renders its live values.
 */
public interface FactSource {
    /**
     * Why these arguments are a malformed span for this source (a key or field outside its table),
     * or null when they are well formed. The arity is already checked.
     */
    @Nullable String malformed(List<String> args);

    /**
     * The value as the reader sees it now, in plain text with exact numbers, or null when it can't
     * be resolved yet ({@code [?]}: a Rules record not yet arrived, or data a pack removed).
     */
    @Nullable Component resolve(Player reader, List<String> args);

    /** The registry of fact sources by name: written during mod construction, read after. */
    final class Registry {
        private static final Map<String, FactSource> SOURCES = new TreeMap<>();

        private Registry() {}

        /**
         * Registers {@code source} under {@code name}, one of {@link FactSpan#ARITY}'s names, once.
         */
        public static synchronized void register(String name, FactSource source) {
            if (!FactSpan.ARITY.containsKey(name)) {
                throw new IllegalArgumentException("no fact source is named " + name);
            }
            if (SOURCES.putIfAbsent(name, source) != null) {
                throw new IllegalStateException("fact source " + name + " is already registered");
            }
        }

        /** The source registered under {@code name}, or null. */
        public static synchronized @Nullable FactSource get(String name) {
            return SOURCES.get(name);
        }
    }
}
