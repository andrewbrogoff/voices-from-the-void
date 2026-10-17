package com.andrewbrogoff.vftv.core.research.client;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.crafting.RecipeType;
import org.jspecify.annotations.Nullable;

/**
 * The authoritative cost preview of one recipe type, as recipe pages and {@code
 * {fact:recipe:<ns>:<path>:<field>}} spans read it (research spec, "Page descriptors" and "Prose
 * and fact references"). Each recipe type's owner registers its own at client init, so research
 * never imports it; the {@code recipe} source dispatches here by the synced recipe's type.
 */
public interface RecipePreviewSource {
    /** The fields its preview returns: at least output, inputs and every decision cost. */
    Set<String> fields();

    /**
     * A field's value for the reader now, in the preview's own context (a reference wand, the
     * targeted crucible or Singing Bowl), or null when it can't be resolved ({@code [?]}, or the
     * context's absent text).
     */
    @Nullable Component fact(Player reader, Identifier recipe, String field);

    /** The registry by recipe type: written during client mod construction, read after. */
    final class Registry {
        private static final Map<RecipeType<?>, RecipePreviewSource> SOURCES = new HashMap<>();

        private Registry() {}

        /** Registers {@code source} for {@code type}, once. */
        public static synchronized void register(RecipeType<?> type, RecipePreviewSource source) {
            if (SOURCES.putIfAbsent(type, source) != null) {
                throw new IllegalStateException("a preview for " + type + " is already registered");
            }
        }

        /** The preview registered for {@code type}, or null. */
        public static synchronized @Nullable RecipePreviewSource get(RecipeType<?> type) {
            return SOURCES.get(type);
        }
    }
}
