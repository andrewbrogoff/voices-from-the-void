package com.andrewbrogoff.vftv.core.research.client;

import com.andrewbrogoff.vftv.api.research.ResearchKeys;
import com.andrewbrogoff.vftv.core.research.ResearchFeedback;
import com.andrewbrogoff.vftv.core.research.ResearchService;
import com.andrewbrogoff.vftv.core.research.model.ResearchRecord;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import org.jspecify.annotations.Nullable;

/**
 * The research feature's client state: the open book's latest quotes and action results, as the
 * book reads them, and the harness's dump. Client thread only.
 */
public final class ResearchClient {
    private static ResearchFeedback.@Nullable Quotes quotes;
    private static final Map<Integer, ResearchFeedback.Result> RESULTS = new LinkedHashMap<>();

    /** The results kept, so a book polling for its pending request finds it. */
    private static final int KEPT_RESULTS = 32;

    private ResearchClient() {}

    /** The latest quotes, or null before the book has opened. */
    public static ResearchFeedback.@Nullable Quotes quotes() {
        return quotes;
    }

    /** The result of a request, or null while it is pending. */
    public static ResearchFeedback.@Nullable Result result(int requestId) {
        return RESULTS.get(requestId);
    }

    static void onQuotes(ResearchFeedback.Quotes next) {
        quotes = next;
    }

    static void onResult(ResearchFeedback.Result result) {
        RESULTS.put(result.requestId(), result);
        if (RESULTS.size() > KEPT_RESULTS) {
            RESULTS.remove(RESULTS.keySet().iterator().next());
        }
    }

    /** The connection closed: nothing of it stays. */
    public static void clear() {
        quotes = null;
        RESULTS.clear();
    }

    /**
     * The {@code vftv:research} dump: how many entries and categories the client's synced
     * registries hold, and the owner's synced record (Stage, learned count and ids, revealed ids,
     * revision, and Vacuos's links in their view), or {@code readable: false}.
     */
    public static String dump(String args) {
        JsonObject data = new JsonObject();
        ClientLevel level = Minecraft.getInstance().level;
        LocalPlayer player = Minecraft.getInstance().player;
        data.addProperty("inWorld", level != null && player != null);
        if (level != null && player != null) {
            data.addProperty(
                    "entries",
                    level.registryAccess()
                            .lookupOrThrow(ResearchKeys.REGISTRY)
                            .listElements()
                            .count());
            data.addProperty(
                    "categories",
                    level.registryAccess()
                            .lookupOrThrow(ResearchKeys.CATEGORY_REGISTRY)
                            .listElements()
                            .count());
            ResearchRecord record = ResearchService.record(player);
            data.addProperty("readable", record != null);
            if (record != null) {
                data.addProperty("stage", record.stage());
                data.addProperty("learned", record.learned().size());
                data.addProperty("revision", record.revision());
                data.add("learnedIds", ids(record.learned()));
                data.add("revealedIds", ids(record.revealed()));
                var view = ResearchService.Data.of(level.registryAccess()).view(record);
                data.addProperty("trueView", view.revealed("vftv:vacuos"));
                data.add("vacuosLinks", ids(view.links("vftv:vacuos")));
            }
        }
        return data.toString();
    }

    private static JsonArray ids(Collection<String> ids) {
        JsonArray out = new JsonArray();
        ids.forEach(out::add);
        return out;
    }
}
