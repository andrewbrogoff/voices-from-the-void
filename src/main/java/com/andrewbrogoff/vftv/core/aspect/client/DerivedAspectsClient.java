package com.andrewbrogoff.vftv.core.aspect.client;

import com.andrewbrogoff.vftv.api.aspect.AspectValues;
import com.andrewbrogoff.vftv.core.aspect.AspectLookup;
import com.andrewbrogoff.vftv.core.aspect.AspectSet;
import com.andrewbrogoff.vftv.core.aspect.DerivedAspectsSync;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/**
 * The client's copy of the {@code DerivedAspects} table: slices gather until the one flagged last,
 * which swaps the whole copy into the lookup; logout clears it. Client thread only.
 *
 * <p>A client hosting the integrated server shares the lookup's static table with it, so it keeps
 * the server's own table: installing a slice queued before a {@code /reload} would overwrite the
 * table that reload just built, and the server would then send the stale one.
 */
public final class DerivedAspectsClient {
    private static Map<Item, AspectValues> pending = new LinkedHashMap<>();
    private static int arrivals;

    private DerivedAspectsClient() {}

    /** A slice arrived. */
    public static void arrive(DerivedAspectsSync payload) {
        arrivals++;
        payload.entries().forEach(e -> pending.put(e.item(), e.values()));
        if (payload.last()) {
            if (!hostsServer()) {
                AspectLookup.installDerived(pending);
            }
            pending = new LinkedHashMap<>();
        }
    }

    /**
     * The harness dump {@code vftv:derived}: the copy's size, how many slices arrived, and the
     * derived values of the {@code item} argument (empty when it has none).
     */
    public static String dump(String args) {
        JsonObject data = new JsonObject();
        data.addProperty("size", AspectLookup.derived().size());
        data.addProperty("arrivals", arrivals);
        JsonObject value = new JsonObject();
        JsonElement item =
                args.isBlank() ? null : JsonParser.parseString(args).getAsJsonObject().get("item");
        Identifier id = item == null ? null : Identifier.tryParse(item.getAsString());
        if (id != null) {
            BuiltInRegistries.ITEM
                    .getOptional(id)
                    .map(AspectLookup.derived()::get)
                    .ifPresent(
                            v ->
                                    v.aspects()
                                            .forEach(
                                                    (k, n) ->
                                                            value.addProperty(
                                                                    AspectSet.text(k), n)));
        }
        data.add("value", value);
        return data.toString();
    }

    private static boolean hostsServer() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.hasSingleplayerServer();
    }

    /** Logout: the copy and any partial slices are dropped. */
    public static void clear() {
        pending = new LinkedHashMap<>();
        AspectLookup.installDerived(Map.of());
    }
}
