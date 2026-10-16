package com.andrewbrogoff.vftv.neoforge.common.client;

import com.andrewbrogoff.vftv.core.common.DataReloads;
import com.andrewbrogoff.vftv.core.common.TickBudget;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Map;
import java.util.function.Function;
import net.neoforged.fml.InterModComms;

/**
 * State the E2E harness reads with {@code dump}, offered over FML inter-mod comms so the mod never
 * depends on the harness and the harness never on us. Without the harness loaded, nothing is sent.
 *
 * <ul>
 *   <li>{@code vftv:tick_budget} (server thread): the {@link TickBudget} summaries, in nanoseconds
 *       per tick, the total first; {@code {"reset": true}} forgets them after reading (the end of a
 *       warm-up).
 *   <li>{@code vftv:data_reloads} (client thread): how often the client rebuilt from changed data
 *       maps ({@link DataReloads}).
 * </ul>
 */
public final class InspectionDumps {
    private static final String TARGET = "mc_agent_harness";

    private InspectionDumps() {}

    /** Offers the dumps; called on {@code InterModEnqueueEvent}. */
    public static void send() {
        offer("dump_server", "vftv:tick_budget", InspectionDumps::tickBudget);
        offer(
                "dump_client",
                "vftv:data_reloads",
                args -> {
                    JsonObject data = new JsonObject();
                    data.addProperty("clientRebuilds", DataReloads.clientRebuilds());
                    return data.toString();
                });
    }

    private static void offer(String method, String kind, Function<String, String> dump) {
        InterModComms.sendTo(TARGET, method, () -> Map.entry(kind, dump));
    }

    private static String tickBudget(String args) {
        JsonObject data = new JsonObject();
        data.addProperty("enabled", TickBudget.ENABLED);
        JsonArray sections = new JsonArray();
        for (TickBudget.Summary summary : TickBudget.snapshot()) {
            JsonObject section = new JsonObject();
            section.addProperty("section", summary.section());
            section.addProperty("ticks", summary.ticks());
            section.addProperty("meanNanos", summary.mean());
            section.addProperty("p99Nanos", summary.p99());
            section.addProperty("maxNanos", summary.max());
            sections.add(section);
        }
        data.add("sections", sections);
        JsonObject request = JsonParser.parseString(args).getAsJsonObject();
        boolean reset = request.has("reset") && request.get("reset").getAsBoolean();
        if (reset) {
            TickBudget.reset();
        }
        data.addProperty("reset", reset);
        return data.toString();
    }
}
