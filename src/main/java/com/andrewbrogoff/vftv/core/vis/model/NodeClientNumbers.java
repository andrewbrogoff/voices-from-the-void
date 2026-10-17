package com.andrewbrogoff.vftv.core.vis.model;

import com.andrewbrogoff.vftv.core.common.model.Schema;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;

/**
 * The node rendering and ambience numbers (wands spec, "Client numbers"): {@code
 * content/nodes/client.json}, written to {@code assets/vftv/nodes/client.json} and overridable by
 * resource packs. An invalid override is ignored with a logged error, and the built-in file is
 * used.
 *
 * @param baseSize the core's size at no vis
 * @param sizePerRoot the core's growth per root of the summed current
 * @param maxSize the core's largest size
 * @param frameTicks ticks per core frame: faint, steady, clear
 * @param scale the core's scale: faint, steady, clear
 * @param moteSize a mote's size
 * @param orbitRadius the motes' orbit radius
 * @param orbitTicks ticks per orbit
 * @param bob the motes' vertical bob
 * @param glintEvery ticks between a node's ambient glints
 * @param glintRange blocks within which glints spawn
 * @param glintLifetime a glint's lifetime in ticks
 * @param humInterval the hum's least interval in ticks
 * @param humRange blocks within which nodes hum
 * @param humMaxPlaying the most hums playing at once
 */
public record NodeClientNumbers(
        double baseSize,
        double sizePerRoot,
        double maxSize,
        List<Integer> frameTicks,
        List<Double> scale,
        double moteSize,
        double orbitRadius,
        int orbitTicks,
        double bob,
        int glintEvery,
        int glintRange,
        int glintLifetime,
        int humInterval,
        int humRange,
        int humMaxPlaying) {
    private static final Schema.Num SIZE = new Schema.Num(0.0, 2.0);
    private static final Schema.Int TICKS = new Schema.Int(1, 1200);

    private static Schema.Obj byClarity(Schema.Type type) {
        return new Schema.Obj(
                List.of(
                        Schema.required("faint", type),
                        Schema.required("steady", type),
                        Schema.required("clear", type)));
    }

    /** {@code vftv:node_client/1}: every field required, with the spec's ranges. */
    public static final Schema SCHEMA =
            Schema.of(
                    "vftv:node_client/1",
                    Schema.required(
                            "core",
                            new Schema.Obj(
                                    List.of(
                                            Schema.required("base_size", SIZE),
                                            Schema.required("size_per_root", SIZE),
                                            Schema.required("max_size", SIZE),
                                            Schema.required("frame_ticks", byClarity(TICKS)),
                                            Schema.required("scale", byClarity(SIZE))))),
                    Schema.required(
                            "motes",
                            new Schema.Obj(
                                    List.of(
                                            Schema.required("size", SIZE),
                                            Schema.required("orbit_radius", SIZE),
                                            Schema.required("orbit_ticks", TICKS),
                                            Schema.required("bob", SIZE)))),
                    Schema.required(
                            "glints",
                            new Schema.Obj(
                                    List.of(
                                            Schema.required("every_ticks", TICKS),
                                            Schema.required("range", new Schema.Int(0, 16)),
                                            Schema.required("lifetime_ticks", TICKS)))),
                    Schema.required(
                            "hum",
                            new Schema.Obj(
                                    List.of(
                                            Schema.required("min_interval_ticks", TICKS),
                                            Schema.required("range", new Schema.Int(0, 16)),
                                            Schema.required(
                                                    "max_playing", new Schema.Int(0, 8))))));

    /** The shipped values. */
    public static final NodeClientNumbers DEFAULTS =
            new NodeClientNumbers(
                    0.30,
                    0.05,
                    1.00,
                    List.of(6, 4, 2),
                    List.of(0.80, 0.90, 1.00),
                    0.25,
                    0.60,
                    80,
                    0.05,
                    10,
                    16,
                    20,
                    160,
                    8,
                    4);

    /** Copies the lists. */
    public NodeClientNumbers {
        frameTicks = List.copyOf(frameTicks);
        scale = List.copyOf(scale);
    }

    /**
     * Reads a checked envelope.
     *
     * @throws IllegalArgumentException naming every problem
     */
    public static NodeClientNumbers parse(JsonObject json) {
        List<String> problems = Schema.check(json, Map.of(SCHEMA.id(), SCHEMA));
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", problems));
        }
        JsonObject core = json.getAsJsonObject("core");
        JsonObject motes = json.getAsJsonObject("motes");
        JsonObject glints = json.getAsJsonObject("glints");
        JsonObject hum = json.getAsJsonObject("hum");
        JsonObject frames = core.getAsJsonObject("frame_ticks");
        JsonObject scale = core.getAsJsonObject("scale");
        return new NodeClientNumbers(
                core.get("base_size").getAsDouble(),
                core.get("size_per_root").getAsDouble(),
                core.get("max_size").getAsDouble(),
                List.of(
                        frames.get("faint").getAsInt(),
                        frames.get("steady").getAsInt(),
                        frames.get("clear").getAsInt()),
                List.of(
                        scale.get("faint").getAsDouble(),
                        scale.get("steady").getAsDouble(),
                        scale.get("clear").getAsDouble()),
                motes.get("size").getAsDouble(),
                motes.get("orbit_radius").getAsDouble(),
                motes.get("orbit_ticks").getAsInt(),
                motes.get("bob").getAsDouble(),
                glints.get("every_ticks").getAsInt(),
                glints.get("range").getAsInt(),
                glints.get("lifetime_ticks").getAsInt(),
                hum.get("min_interval_ticks").getAsInt(),
                hum.get("range").getAsInt(),
                hum.get("max_playing").getAsInt());
    }

    /** The core's size in blocks for a summed current and clarity (0 faint, 1 steady, 2 clear). */
    public double coreSize(int sumCurrent, int clarity) {
        return Math.min(maxSize, baseSize + sizePerRoot * Math.sqrt(sumCurrent))
                * scale.get(clarity);
    }

    /** The core's frame at a game time: {@code (gameTime / frame_ticks[clarity]) % 8}. */
    public int frame(long gameTime, int clarity) {
        return (int) Math.floorMod(gameTime / frameTicks.get(clarity), 8L);
    }
}
