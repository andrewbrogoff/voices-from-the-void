package com.andrewbrogoff.vftv.core.crucible.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andrewbrogoff.vftv.core.common.model.Hashes;
import com.andrewbrogoff.vftv.core.common.model.Salts;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.SortedMap;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/** The crucible spec's vector tables, replayed against the model tier. */
class CrucibleModelTest {
    private static final String AER = "vftv:aer";
    private static final String AQUA = "vftv:aqua";
    private static final String IGNIS = "vftv:ignis";
    private static final String TERRA = "vftv:terra";
    private static final String SONUS = "vftv:sonus";
    private static final String PRAE = "vftv:praecantatio";

    private static long asLong(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFFL);
    }

    @Test
    void phaseVectors() {
        long[][] rows = {
            {0, 64, 0, 0x0000000000000040L, 0xE0F53741245D77A5L, 2, 37},
            {1, 64, 0, 0x0000004000000040L, 0x52AD4BCB856565D5L, 1, 21},
            {0, 64, 1, 0x0000000000001040L, 0x088EFF2AA6E4BACBL, 0, 35},
            {-37, 70, 512, 0xFFFFF6C000200046L, 0x28DE383757949BE2L, 4, 34},
            {29999999, -64, -29999999, 0x7270DFE363C81FC0L, 0x235FAB678DA90CF7L, 3, 23},
        };
        for (long[] r : rows) {
            long pos = asLong((int) r[0], (int) r[1], (int) r[2]);
            assertEquals(r[3], pos);
            assertEquals(r[4], Hashes.fold(Salts.word(Salts.CRUC), pos));
            assertEquals(r[5], CrucibleMath.phase(pos, 5));
            assertEquals(r[6], CrucibleMath.phase(pos, 40));
            assertEquals(0, CrucibleMath.phase(pos, 1));
        }
    }

    @Test
    void drainVector() {
        CandidateSet set = new CandidateSet();
        int[] offers = {
            40, 7, 7, 93, 12, 5, 61, 18, 33, 2, 77, 21, 9, 50, 14, 88, 3, 70, 1, 40, 64, 6
        };
        for (int id : offers) {
            set.offer(100, id);
        }
        assertArrayEquals(
                new int[] {1, 2, 3, 5, 6, 7, 9, 12, 14, 18, 21, 33, 40, 50, 61, 64},
                set.drain(100));
        assertEquals(0, set.drain(100).length, "drained empties it");
    }

    @Test
    void drainIsTheLowestSixteenInAnyOrderAndANewTickEmpties() {
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            ids.add(i * 7 % 1000);
        }
        Random random = new Random(7);
        for (int round = 0; round < 20; round++) {
            Collections.shuffle(ids, random);
            CandidateSet set = new CandidateSet();
            set.offer(1, -5); // an older tick's offer is forgotten
            ids.forEach(id -> set.offer(2, id));
            int[] expected = ids.stream().sorted().limit(16).mapToInt(i -> i).toArray();
            assertArrayEquals(expected, set.drain(2));
        }
        CandidateSet set = new CandidateSet();
        set.offer(3, 9);
        assertEquals(0, set.drain(4).length, "offered in another tick: treated as empty");
    }

    private static Map<String, Integer> m(Object... kv) {
        Map<String, Integer> map = new TreeMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put((String) kv[i], (Integer) kv[i + 1]);
        }
        return map;
    }

    private static Pool distinct(int n) {
        Map<String, Integer> map = new TreeMap<>();
        for (int i = 0; i < n; i++) {
            map.put("vftv:x" + i, 1);
        }
        return new Pool(map);
    }

    @Test
    void fitVectors() {
        assertEquals(5, new Pool(m(TERRA, 190)).fit(m(TERRA, 2), 64, 200));
        assertEquals(64, new Pool().fit(m(TERRA, 2), 64, 200));
        assertEquals(0, new Pool(m(AER, 150)).fit(m(IGNIS, 3, TERRA, 60), 10, 200));
        assertEquals(0, new Pool(m(AER, 250)).fit(m(AER, 1), 3, 200), "capacity since lowered");
        assertEquals(0, distinct(63).fit(m("vftv:new1", 1, "vftv:new2", 1), 1, 200), "65 ids");
        assertEquals(1, distinct(63).fit(m("vftv:new1", 1, "vftv:x0", 1), 1, 200), "64 ids");
        assertEquals(
                0,
                new Pool().fit(m(AER, Integer.MAX_VALUE, TERRA, Integer.MAX_VALUE), 64, 10_000),
                "t overflows an int");
        assertEquals(64, new Pool().fit(m(AER, 3), 64, 10_000));
        assertEquals(0, new Pool().fit(m(), 5, 200), "{} dissolves nothing");
    }

    @Test
    void poolOperations() {
        Pool pool = new Pool();
        int rev = pool.revision();
        pool.credit(m(TERRA, 2), 3);
        assertEquals(6, pool.amount(TERRA));
        assertTrue(pool.revision() != rev);
        assertTrue(pool.affordable(m(TERRA, 6)));
        assertEquals(m(TERRA, 1, AER, 2), pool.shortfall(m(TERRA, 7, AER, 2)));
        assertThrows(IllegalStateException.class, () -> pool.debit(m(TERRA, 7)));
        pool.debit(m(TERRA, 6));
        assertTrue(pool.isEmpty(), "zero amounts are never kept");
        pool.restore(m(AER, 4));
        assertEquals(3, pool.extract(AER, 3));
        assertEquals(1, pool.extract(AER, 9));
        assertEquals(0, pool.extract(AER, 9));
        pool.restore(m(AER, 1, IGNIS, 2));
        assertEquals(3, pool.total());
        assertEquals(2, pool.size());
        assertEquals(m(AER, 1, IGNIS, 2), pool.snapshot());
        assertThrows(UnsupportedOperationException.class, () -> pool.snapshot().put(AER, 1));
        pool.clear();
        assertTrue(pool.isEmpty());
        assertTrue(pool.toString().startsWith("Pool"));
    }

    @Test
    void ejectionVectors() {
        assertVelocity(
                0.106066, 0.106066, CrucibleMath.ejection(10, -5, new double[] {13.25, -1.75}));
        assertVelocity(0, 0, CrucibleMath.ejection(10, -5, new double[] {10.5, -4.5}));
        assertVelocity(0, 0, CrucibleMath.ejection(10, -5, new double[] {10.50005, -4.5}));
        assertVelocity(-0.149656, 0.010158, CrucibleMath.ejection(10, -5, new double[] {-100, 3}));
        assertVelocity(0, 0, CrucibleMath.ejection(10, -5, null));
    }

    private static void assertVelocity(double x, double z, double[] v) {
        assertEquals(x, v[0], 1e-6);
        assertEquals(0.3, v[1], 1e-12);
        assertEquals(z, v[2], 1e-6);
    }

    @Test
    void tintVectors() {
        List<String> registry = List.of(AER, AQUA, IGNIS, TERRA);
        assertEquals(AER, CrucibleMath.dominant(registry, m(TERRA, 5, AER, 5, IGNIS, 3), AQUA));
        assertEquals(IGNIS, CrucibleMath.dominant(registry, m("vftv:gone", 50, IGNIS, 3), AQUA));
        assertEquals(AQUA, CrucibleMath.dominant(registry, m("vftv:gone", 50), AQUA));
        assertEquals(AQUA, CrucibleMath.dominant(registry, m(), AQUA));
    }

    @Test
    void cellVector() {
        SortedMap<String, Integer> pool = new TreeMap<>();
        for (int i = 0; i < 14; i++) {
            pool.put(String.format(java.util.Locale.ROOT, "vftv:a%02d", i), 14 - i);
        }
        pool.put("vftv:z", 3);
        List<String> cells = CrucibleMath.cells(pool, 12);
        assertEquals(12, cells.size());
        assertEquals("vftv:a00", cells.getFirst());
        assertEquals("vftv:a11", cells.getLast(), "z ties a11 and loses on id");
        assertEquals(3, pool.size() - cells.size(), "the more line");
        assertEquals(
                List.of(AER, IGNIS), CrucibleMath.cells(new TreeMap<>(m(IGNIS, 1, AER, 2)), 12));
    }

    private static Decision.Candidate cand(String id, Map<String, Integer> cost) {
        return new Decision.Candidate(id, new TreeMap<>(cost));
    }

    /**
     * The selection table: a copper ingot against a_brass (x), b_bell (y); c_glass never matches.
     */
    private static Decision select(
            List<String> learned, Map<String, Integer> pool, boolean mark, boolean prot) {
        List<Decision.Candidate> all =
                List.of(
                        cand("vftv:a_brass", m(PRAE, 4, SONUS, 2)),
                        cand("vftv:b_bell", m(SONUS, 10)));
        List<String> research = List.of("x", "y");
        List<Decision.Candidate> candidates = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            if (learned.contains(research.get(i))) {
                candidates.add(all.get(i));
            }
        }
        return Decision.decide(prot, mark, candidates, new Pool(pool));
    }

    private static String id(Decision d) {
        return java.util.Objects.requireNonNull(d.recipe()).id();
    }

    @Test
    void selectionVectors() {
        Decision d = select(List.of("x", "y"), m(PRAE, 4, SONUS, 12), false, false);
        assertEquals(Decision.Kind.CRAFT, d.kind());
        assertEquals("vftv:a_brass", id(d));
        d = select(List.of("x", "y"), m(SONUS, 12), false, false);
        assertEquals("vftv:b_bell", id(d));
        d = select(List.of("x", "y"), m(SONUS, 1), false, false);
        assertEquals(Decision.Kind.SHORT, d.kind());
        assertEquals("vftv:a_brass", id(d));
        assertEquals(m(PRAE, 4, SONUS, 1), d.shortfall());
        d = select(List.of("y"), m(SONUS, 9), false, false);
        assertEquals(m(SONUS, 1), d.shortfall());
        assertEquals("vftv:b_bell", id(d));
        assertEquals(Decision.Kind.DISSOLVE, select(List.of(), m(SONUS, 99), false, false).kind());
        assertEquals(
                Decision.Kind.DISSOLVE,
                select(List.of("x", "y"), m(PRAE, 9, SONUS, 99), true, false).kind());
        d = select(List.of("x"), m(), true, true);
        assertEquals(Decision.Kind.PROTECTED, d.kind());
        assertNull(d.recipe());
    }

    @Test
    void tuning() {
        assertEquals(CrucibleTuning.DEFAULTS, CrucibleTuning.parse("{}"));
        assertEquals(
                new CrucibleTuning(300, 7),
                CrucibleTuning.parse("{\"pool.capacity\": 300, \"step.interval\": 7}"));
        assertEquals(
                CrucibleTuning.DEFAULTS,
                CrucibleTuning.parse(CrucibleTuning.DEFAULTS.toJson().toString()));
        for (String bad :
                List.of(
                        "[]",
                        "{",
                        "{\"x\": 1}",
                        "{\"pool.capacity\": 15}",
                        "{\"pool.capacity\": 10001}",
                        "{\"pool.capacity\": 20.5}",
                        "{\"step.interval\": 0}",
                        "{\"step.interval\": 41}",
                        "{\"step.interval\": \"5\"}")) {
            assertThrows(IllegalArgumentException.class, () -> CrucibleTuning.parse(bad), bad);
        }
        assertThrows(IllegalArgumentException.class, () -> new CrucibleTuning(200, 0));
        assertSame(CrucibleTuning.DEFAULTS, CrucibleTuning.DEFAULTS);
    }
}
