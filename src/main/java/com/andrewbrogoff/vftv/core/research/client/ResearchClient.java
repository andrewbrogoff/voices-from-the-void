package com.andrewbrogoff.vftv.core.research.client;

import com.andrewbrogoff.vftv.api.research.ResearchKeys;
import com.andrewbrogoff.vftv.core.research.ResearchService;
import com.andrewbrogoff.vftv.core.research.model.ResearchRecord;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Collection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;

/** The research feature's client state as the E2E harness reads it. Client thread only. */
public final class ResearchClient {
    private ResearchClient() {}

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
