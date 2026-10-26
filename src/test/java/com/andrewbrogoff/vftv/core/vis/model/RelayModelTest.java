package com.andrewbrogoff.vftv.core.vis.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andrewbrogoff.vftv.core.common.model.Allocations;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * The relays' and cultivation's model (wands spec, "Topology", "Flow", "Cultivation"): components,
 * the 32 cap on create and merge, the early refusal, splits, range rebuilds that unlink and
 * readmit, same-tick order, the pulse vectors and conservation, and cultivation's arithmetic.
 */
class RelayModelTest {
    private static final RelayNetwork.Kind RELAY = RelayNetwork.Kind.RELAY;

    /** A member key for x along a line (the model takes any distinct longs). */
    private static long key(int x, int y, int z) {
        return ((long) x << 32) ^ ((long) y << 16) ^ z;
    }

    private static boolean add(RelayNetwork n, int x) {
        return n.admit(key(x, 0, 0), x, 0, 0, RELAY);
    }

    // --- topology -----------------------------------------------------------------------------

    @Test
    void linksWithinRangeInclusiveAndFormsComponents() {
        RelayNetwork n = new RelayNetwork(10.0);
        assertTrue(add(n, 0));
        assertTrue(add(n, 10)); // exactly the range links
        assertTrue(add(n, 21)); // 11 from the last: its own component
        assertEquals(n.component(key(0, 0, 0)), n.component(key(10, 0, 0)));
        assertTrue(n.component(key(21, 0, 0)) != n.component(key(0, 0, 0)));
        assertEquals(2, n.members(n.component(key(0, 0, 0))).size());
        assertEquals(key(0, 0, 0), n.componentKey(n.component(key(10, 0, 0))));
        assertTrue(n.linksAny(-1, 11, 0, 0));
        assertFalse(n.linksAny(-1, 40, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> add(n, 0));
    }

    @Test
    void theCapHoldsOnCreateAndMerge() {
        RelayNetwork n = new RelayNetwork(2.0);
        for (int i = 0; i < 32; i++) {
            assertTrue(add(n, i * 2), "member " + i);
        }
        assertEquals(33, n.admissionSize(-1, 64, 0, 0), "the early refusal counts 33");
        assertFalse(add(n, 64), "the 33rd is refused");
        assertFalse(n.kind(key(64, 0, 0)) != null, "a refusal changes nothing");
        // Two components of 16 each, then a bridge between them that would make 33.
        RelayNetwork m = new RelayNetwork(2.0);
        for (int i = 0; i < 16; i++) {
            add(m, i * 2);
            add(m, 34 + i * 2);
        }
        assertEquals(33, m.admissionSize(-1, 32, 0, 0));
        assertFalse(add(m, 32), "a merge to 33 is refused");
        m.remove(key(0, 0, 0));
        assertTrue(add(m, 32), "with room, the bridge merges both");
        assertEquals(32, m.members(m.component(key(32, 0, 0))).size());
    }

    @Test
    void removalSplitsAComponent() {
        RelayNetwork n = new RelayNetwork(2.0);
        for (int i = 0; i < 5; i++) {
            add(n, i * 2);
        }
        n.remove(key(4, 0, 0));
        assertTrue(n.component(key(0, 0, 0)) != n.component(key(8, 0, 0)));
        assertEquals(2, n.members(n.component(key(0, 0, 0))).size());
        assertEquals(2, n.members(n.component(key(6, 0, 0))).size());
        assertFalse(n.remove(key(4, 0, 0)), "removing twice finds nothing");
    }

    @Test
    void sameTickAdmissionsGoInPositionOrder() {
        RelayNetwork n = new RelayNetwork(2.0);
        for (int i = 0; i < 31; i++) {
            add(n, i * 2);
        }
        // Two placements that together would exceed 32: the lower key is admitted first.
        List<Long> admitted = new ArrayList<>();
        for (int x : new int[] {62, 64}) {
            if (add(n, x)) {
                admitted.add(key(x, 0, 0));
            }
        }
        assertEquals(List.of(key(62, 0, 0)), admitted);
    }

    @Test
    void aRangeRebuildUnlinksPastTheCapAndReadmitsLater() {
        // Two 20-member lines 4 apart: separate at range 2, one 40-member group at range 4,
        // where every leftover member touches the 32 admitted first.
        RelayNetwork n = new RelayNetwork(2.0);
        for (int i = 0; i < 20; i++) {
            add(n, i);
            n.admit(key(i, 4, 0), i, 4, 0, RELAY);
        }
        List<Long> first = n.rebuild(4.0);
        assertEquals(1, first.size(), "one oversized group, logged once");
        int unlinked = 0;
        Set<Long> linked = new TreeSet<>();
        for (long k : n.keys()) {
            if (n.unlinked(k)) {
                unlinked++;
            } else {
                linked.add(k);
            }
        }
        assertEquals(8, unlinked);
        assertEquals(32, linked.size());
        assertEquals(first.get(0), n.keys().stream().filter(n::unlinked).findFirst().orElseThrow());
        // The 32 lowest keys are the ones linked.
        assertEquals(new TreeSet<>(new ArrayList<>(n.keys()).subList(0, 32)), linked);
        // A removal nearby makes room: the lowest unlinked member is readmitted.
        long firstUnlinked = first.get(0);
        n.remove(linked.iterator().next());
        assertFalse(n.unlinked(firstUnlinked));
        // Back at range 2, both lines are whole again and nothing is unlinked.
        assertTrue(n.rebuild(2.0).isEmpty());
        assertTrue(n.keys().stream().noneMatch(n::unlinked));
    }

    @Test
    void loadThenRebuildRestoresComponents() {
        RelayNetwork n = new RelayNetwork(3.0);
        n.load(key(0, 0, 0), 0, 0, 0, RelayNetwork.Kind.TAP);
        n.load(key(3, 0, 0), 3, 0, 0, RelayNetwork.Kind.CRADLE);
        assertTrue(n.rebuild(3.0).isEmpty());
        assertEquals(n.component(key(0, 0, 0)), n.component(key(3, 0, 0)));
        n.setKind(key(3, 0, 0), RELAY);
        assertEquals(RELAY, n.kind(key(3, 0, 0)));
        assertEquals(2, n.keys().size());
    }

    // --- the pulse ----------------------------------------------------------------------------

    @Test
    void pulseVectorsFromTheFixtures() {
        JsonObject fixtures = VisFixturesTest.load("content/nodes/fixtures.json");
        for (JsonElement e : fixtures.getAsJsonArray("pulse")) {
            JsonObject row = e.getAsJsonObject();
            List<String> aspects = new ArrayList<>(new TreeSet<>(aspectIds(row)));
            int a = aspects.size();
            JsonArray src = row.getAsJsonArray("sources");
            JsonArray snk = row.getAsJsonArray("sinks");
            int[] sources = new int[src.size() * a];
            int[] room = new int[snk.size() * a];
            fill(src, aspects, sources);
            fill(snk, aspects, room);
            int before = sum(sources);
            List<List<Object>> moves = new ArrayList<>();
            int start =
                    RelayPulse.run(
                            VisFixturesTest.hex(row.get("game_time")),
                            row.get("period").getAsInt(),
                            row.get("rate").getAsInt(),
                            a,
                            sources,
                            src.size(),
                            room,
                            snk.size(),
                            (sink, aspect, source, amount) ->
                                    moves.add(List.of(sink, aspects.get(aspect), source, amount)));
            JsonObject expect = row.getAsJsonObject("expect");
            assertEquals(expect.get("start").getAsInt(), start, row.toString());
            List<List<Object>> want = new ArrayList<>();
            for (JsonElement m : expect.getAsJsonArray("moves")) {
                JsonArray mv = m.getAsJsonArray();
                want.add(
                        List.of(
                                mv.get(0).getAsInt(),
                                mv.get(1).getAsString(),
                                mv.get(2).getAsInt(),
                                mv.get(3).getAsInt()));
            }
            assertEquals(want, moves, row.toString());
            int moved = moves.stream().mapToInt(m -> (Integer) m.get(3)).sum();
            assertEquals(before - sum(sources), moved, "conservation: out of nodes = into wands");
        }
    }

    @Test
    void theRotatingStartReachesEverySink() {
        Set<Integer> starts = new TreeSet<>();
        for (long t = 0; t < 200; t += 20) {
            starts.add(RelayPulse.start(t, 20, 3));
        }
        assertEquals(Set.of(0, 1, 2), starts);
    }

    @Test
    void thePulseAllocatesNothing() {
        int[] sources = new int[31 * 8];
        int[] room = new int[31 * 8];
        RelayPulse.Moves none = (sink, aspect, source, amount) -> {};
        Allocations.assertNone(
                "1,000 full pulses",
                () -> {
                    for (int i = 0; i < 1000; i++) {
                        java.util.Arrays.fill(sources, 50);
                        java.util.Arrays.fill(room, 30);
                        RelayPulse.run(i * 20L, 20, 4, 8, sources, 31, room, 31, none);
                    }
                });
    }

    private static Set<String> aspectIds(JsonObject row) {
        Set<String> ids = new TreeSet<>();
        for (String list : List.of("sources", "sinks")) {
            row.getAsJsonArray(list).forEach(o -> ids.addAll(o.getAsJsonObject().keySet()));
        }
        return ids;
    }

    private static void fill(JsonArray rows, List<String> aspects, int[] into) {
        for (int i = 0; i < rows.size(); i++) {
            JsonObject o = rows.get(i).getAsJsonObject();
            for (int a = 0; a < aspects.size(); a++) {
                into[i * aspects.size() + a] =
                        o.has(aspects.get(a)) ? o.get(aspects.get(a)).getAsInt() : 0;
            }
        }
    }

    private static int sum(int[] values) {
        int s = 0;
        for (int v : values) {
            s += v;
        }
        return s;
    }

    // --- cultivation --------------------------------------------------------------------------

    @Test
    void cultivationBulkPartialAndCap() {
        // per_max 4, cap 250: a max of 40 has (250 - 40) * 4 = 840 units of room.
        assertEquals(840, Cultivate.visRoom(40, 0, 250, 4));
        assertEquals(837, Cultivate.visRoom(40, 3, 250, 4));
        assertEquals(new Cultivate.Vis(42, 1), Cultivate.insertVis(40, 3, 6, 250, 4));
        assertEquals(new Cultivate.Vis(250, 0), Cultivate.insertVis(40, 3, 837, 250, 4), "at cap");
        assertEquals(0, Cultivate.visRoom(250, 0, 250, 4));
        // A new aspect's slot: room from max 0.
        assertEquals(1000, Cultivate.visRoom(0, 0, 250, 4));
        assertEquals(new Cultivate.Vis(1, 0), Cultivate.insertVis(0, 0, 4, 250, 4));
    }

    @Test
    void cultivationCarriesAfterLoweredThresholds() {
        // A counter of 3 under a per_max lowered to 2 carries one step at the next settlement.
        assertEquals(new Cultivate.Vis(41, 1), Cultivate.insertVis(40, 3, 0, 250, 2));
        // A lowered cap never shrinks a max past it; it only stops accepting.
        assertEquals(new Cultivate.Vis(300, 0), Cultivate.insertVis(300, 2, 0, 250, 4));
        assertEquals(0, Cultivate.visRoom(300, 0, 250, 4));
        // Sonus: a cost lowered to 10 turns 25 into two steps.
        assertEquals(new Cultivate.Clarity(2, 0), Cultivate.insertSonus(0, 25, 0, 10));
        assertEquals(new Cultivate.Clarity(1, 5), Cultivate.insertSonus(0, 15, 0, 10));
    }

    @Test
    void sonusStepsClarityToClear() {
        assertEquals(96, Cultivate.sonusRoom(0, 0, 48));
        assertEquals(40, Cultivate.sonusRoom(1, 8, 48));
        assertEquals(0, Cultivate.sonusRoom(2, 0, 48));
        assertEquals(new Cultivate.Clarity(1, 2), Cultivate.insertSonus(0, 10, 40, 48));
        assertEquals(new Cultivate.Clarity(2, 0), Cultivate.insertSonus(1, 8, 40, 48));
        assertEquals(new Cultivate.Clarity(2, 0), Cultivate.insertSonus(2, 0, 5, 48));
    }
}
