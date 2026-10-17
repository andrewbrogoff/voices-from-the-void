package com.andrewbrogoff.vftv.neoforge.common.client;

import com.andrewbrogoff.vftv.core.Vftv;
import com.andrewbrogoff.vftv.core.common.DataReloads;
import com.andrewbrogoff.vftv.core.common.TickBudget;
import com.andrewbrogoff.vftv.core.common.model.PendingAudio;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.resources.Identifier;
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
        offer("dump_client", "vftv:audio", InspectionDumps::audio);
    }

    /**
     * {@code vftv:audio} (client thread): the client resource check for our sound events with the
     * pending ones ({@link PendingAudio}, found above the game directory) skipped. {@code missing}:
     * unlisted events with no loaded sound event; {@code subtitles}: unlisted ones whose subtitle
     * doesn't resolve; {@code pendingPresent}: listed ones that do have an entry.
     */
    private static String audio(String args) {
        Path project = PendingAudio.findProject(Path.of(""));
        Set<String> pending = project == null ? Set.of() : PendingAudio.read(project);
        JsonArray missing = new JsonArray();
        JsonArray subtitles = new JsonArray();
        JsonArray pendingPresent = new JsonArray();
        for (Identifier id : BuiltInRegistries.SOUND_EVENT.keySet()) {
            if (!id.getNamespace().equals(Vftv.MOD_ID)) {
                continue;
            }
            WeighedSoundEvents loaded = Minecraft.getInstance().getSoundManager().getSoundEvent(id);
            if (pending.contains(id.toString())) {
                if (loaded != null) {
                    pendingPresent.add(id.toString());
                }
            } else if (loaded == null) {
                missing.add(id.toString());
            } else if (loaded.getSubtitle() == null
                    || !ComponentUtils.isTranslationResolvable(loaded.getSubtitle())) {
                subtitles.add(id.toString());
            }
        }
        // The harness's own resource check counts every vftv WARN; vanilla warns once per pending
        // event at load ("Missing sound for event"), so this re-reads the log without those.
        JsonArray log = new JsonArray();
        Path latest = Path.of("logs", "latest.log");
        if (Files.exists(latest)) {
            try {
                for (String line : Files.readAllLines(latest, StandardCharsets.UTF_8)) {
                    boolean warning = line.contains("/WARN]") || line.contains("/ERROR]");
                    boolean pendingSound =
                            line.contains("Missing sound for event: ")
                                    && pending.contains(
                                            line.substring(line.indexOf("event: ") + 7).strip());
                    if (warning && line.contains(Vftv.MOD_ID + ":") && !pendingSound) {
                        log.add(line);
                    }
                }
            } catch (IOException e) {
                log.add("unreadable log: " + e);
            }
        }
        JsonObject data = new JsonObject();
        data.add("log", log);
        data.addProperty("logRead", Files.exists(latest));
        data.addProperty("listsFound", project != null);
        data.addProperty("pending", pending.size());
        data.add("missing", missing);
        data.add("subtitles", subtitles);
        data.add("pendingPresent", pendingPresent);
        return data.toString();
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
