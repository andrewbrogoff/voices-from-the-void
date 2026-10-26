package com.andrewbrogoff.vftv.core.aspect.model;

import com.andrewbrogoff.vftv.api.aspect.ScanOutcome;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * One player's knowledge (aspects spec, "Knowledge" and "Aspect points"): the known aspects, the
 * scan ledger with each partial scan's snapshot, and the point pool. Immutable: every change
 * returns a new value carrying the {@link Delta} from the one it was made from, which the owner
 * sync sends instead of a full resend.
 *
 * @param known the aspects the player knows
 * @param scans the ledger, by scan key text ({@code item:<id>}, {@code block:<id>}, …)
 * @param pool the points of each aspect; only positive amounts are held
 * @param delta what changed from the value this one was made from; null for a baseline. Not part of
 *     equality
 */
public record Knowledge(
        Set<String> known,
        Map<String, Scan> scans,
        Map<String, Integer> pool,
        @Nullable Delta delta) {
    /** Nothing known, nothing scanned, no points. */
    public static final Knowledge EMPTY = new Knowledge(Set.of(), Map.of(), Map.of(), null);

    /** Copies the collections and checks the pool. */
    public Knowledge {
        known = Set.copyOf(known);
        scans = Map.copyOf(scans);
        pool = Map.copyOf(pool);
        for (Map.Entry<String, Integer> e : pool.entrySet()) {
            if (e.getValue() < 1) {
                throw new IllegalArgumentException("pool amount of " + e.getKey() + " below 1");
            }
        }
    }

    /**
     * One ledger entry: complete, or partly scanned with the values at its first strike and the
     * aspects already paid.
     *
     * @param complete whether every aspect has been paid
     * @param snapshot a partial scan's values at the first strike; empty when complete
     * @param paid a partial scan's aspects already paid; empty when complete
     */
    public record Scan(boolean complete, Map<String, Integer> snapshot, Set<String> paid) {
        /** A completed entry. */
        public static final Scan COMPLETE = new Scan(true, Map.of(), Set.of());

        /** Copies and checks the invariants. */
        public Scan {
            snapshot = Map.copyOf(snapshot);
            paid = Set.copyOf(paid);
            if (complete && (!snapshot.isEmpty() || !paid.isEmpty())) {
                throw new IllegalArgumentException("a complete scan keeps no snapshot");
            }
            if (!complete
                    && (snapshot.isEmpty()
                            || !snapshot.keySet().containsAll(paid)
                            || paid.size() == snapshot.size())) {
                throw new IllegalArgumentException("a partial scan pays part of its snapshot");
            }
            for (Map.Entry<String, Integer> e : snapshot.entrySet()) {
                if (e.getValue() < 1) {
                    throw new IllegalArgumentException("snapshot amount below 1: " + e.getKey());
                }
            }
        }
    }

    /**
     * What one change did, for the owner's delta sync.
     *
     * @param known aspects newly known
     * @param scans ledger entries written
     * @param pool pool amounts that changed, at their new value (0 when spent out)
     */
    public record Delta(Set<String> known, Map<String, Scan> scans, Map<String, Integer> pool) {
        /** Copies the collections. */
        public Delta {
            known = Set.copyOf(known);
            scans = Map.copyOf(scans);
            pool = Map.copyOf(pool);
        }
    }

    /** What the rules need about the aspect set, from the registry and the tuning. */
    public interface Rules {
        /** An aspect's prerequisites: its parents, or a hidden primal's taught parents. */
        List<String> prerequisites(String aspect);

        /** An aspect's place in dependency order: its tier with Vacuos taught. */
        int order(String aspect);

        /** The discovery bonus of an aspect. */
        int bonus(String aspect);
    }

    /**
     * An aspect discovered, and the bonus actually awarded.
     *
     * @param aspect the aspect
     * @param bonus the points of it awarded
     */
    public record Discovery(String aspect, int bonus) {}

    /**
     * What a strike did.
     *
     * @param next the knowledge after the strike
     * @param outcome how the ledger entry changed
     * @param complete whether the key is now fully scanned
     * @param points the scan's points paid per aspect (discovery bonuses not included)
     * @param discovered the aspects discovered, in dependency order
     * @param values the values the strike read: the snapshot of a partial scan
     */
    public record Strike(
            Knowledge next,
            ScanOutcome outcome,
            boolean complete,
            SortedMap<String, Integer> points,
            List<Discovery> discovered,
            SortedMap<String, Integer> values) {}

    /**
     * Strikes a thing (aspects spec, "Knowledge"). Aspects whose prerequisites are known are
     * discovered on the spot, to a fixed point in dependency order; every known aspect not yet paid
     * pays its amount from the snapshot. A completed key pays nothing more.
     *
     * @param key the scan key text
     * @param values what the thing is made of now: non-empty; read only for a key with no entry
     * @param rules the aspect set's rules
     */
    public Strike strike(String key, Map<String, Integer> values, Rules rules) {
        Scan entry = scans.get(key);
        if (entry != null && entry.complete()) {
            return new Strike(
                    this, ScanOutcome.REPEAT, true, new TreeMap<>(), List.of(), new TreeMap<>());
        }
        SortedMap<String, Integer> snapshot =
                new TreeMap<>(entry == null ? values : entry.snapshot());
        if (snapshot.isEmpty()) {
            throw new IllegalArgumentException("nothing to learn is not a strike");
        }
        Set<String> nowKnown = new HashSet<>(known);
        List<Discovery> discovered = discover(snapshot.keySet(), nowKnown, rules);
        Set<String> paid = entry == null ? new HashSet<>() : new HashSet<>(entry.paid());
        SortedMap<String, Integer> points = new TreeMap<>();
        snapshot.forEach(
                (aspect, amount) -> {
                    if (nowKnown.contains(aspect) && paid.add(aspect)) {
                        points.put(aspect, amount);
                    }
                });
        boolean complete = paid.size() == snapshot.size();
        ScanOutcome outcome =
                entry == null
                        ? ScanOutcome.FIRST
                        : complete ? ScanOutcome.COMPLETED : ScanOutcome.PARTIAL;
        Scan written = complete ? Scan.COMPLETE : new Scan(false, snapshot, paid);
        Map<String, Integer> gains = new HashMap<>(points);
        discovered.forEach(d -> gains.merge(d.aspect(), d.bonus(), Integer::sum));
        Knowledge next = change(discoveredIds(discovered), key, written, gains);
        return new Strike(next, outcome, complete, points, discovered, snapshot);
    }

    /**
     * Discovers one aspect outright, as a research reward does, paying its bonus.
     *
     * @return the knowledge after it, or null if the aspect is already known
     */
    public @Nullable Knowledge discover(String aspect, int bonus) {
        if (known.contains(aspect)) {
            return null;
        }
        return change(Set.of(aspect), null, null, bonus > 0 ? Map.of(aspect, bonus) : Map.of());
    }

    /**
     * What a research reward did.
     *
     * @param next the knowledge after it
     * @param discovered the aspects it discovered, in dependency order
     */
    public record Rewarded(Knowledge next, List<Discovery> discovered) {}

    /**
     * Pays a research reward (research spec, "Rewards"): each named aspect the player doesn't know
     * is first discovered together with its missing prerequisites (a hidden primal's taught ones),
     * lowest order first, then by id, each paying its discovery bonus; then every named amount is
     * credited.
     *
     * @param points the reward's points per aspect, each above 0
     * @param rules the aspect set's rules
     */
    public Rewarded reward(Map<String, Integer> points, Rules rules) {
        Set<String> missing = new HashSet<>();
        List<String> queue = new ArrayList<>(points.keySet());
        while (!queue.isEmpty()) {
            String aspect = queue.removeLast();
            if (!known.contains(aspect) && missing.add(aspect)) {
                queue.addAll(rules.prerequisites(aspect));
            }
        }
        Set<String> nowKnown = new HashSet<>(known);
        List<Discovery> discovered = discover(missing, nowKnown, rules);
        Map<String, Integer> gains = new HashMap<>(points);
        discovered.forEach(d -> gains.merge(d.aspect(), d.bonus(), Integer::sum));
        return new Rewarded(change(discoveredIds(discovered), null, null, gains), discovered);
    }

    /**
     * Credits points of an aspect already known, as a Voice insight does; never discovers.
     *
     * @return the knowledge after it, or null if the aspect is unknown or {@code points} is below 1
     */
    public @Nullable Knowledge credit(String aspect, int points) {
        if (!known.contains(aspect) || points < 1) {
            return null;
        }
        return change(Set.of(), null, null, Map.of(aspect, points));
    }

    /**
     * Spends points (research spec, "Paying for placements": a Learn or a purchase), all or
     * nothing; an amount spent to 0 leaves the pool.
     *
     * @param points the amount of each aspect to take, each at least 0
     * @return the knowledge after it, or null if any balance is short
     */
    public @Nullable Knowledge debit(Map<String, Integer> points) {
        Map<String, Integer> p = new HashMap<>(pool);
        Map<String, Integer> changed = new HashMap<>();
        for (Map.Entry<String, Integer> e : points.entrySet()) {
            int left = p.getOrDefault(e.getKey(), 0) - e.getValue();
            if (e.getValue() < 0 || left < 0) {
                return null;
            }
            if (e.getValue() > 0) {
                changed.put(e.getKey(), left);
                if (left == 0) {
                    p.remove(e.getKey());
                } else {
                    p.put(e.getKey(), left);
                }
            }
        }
        return new Knowledge(known, scans, p, new Delta(Set.of(), Map.of(), changed));
    }

    /** Applies a delta the server sent, as the client does; an amount of 0 leaves the pool. */
    public Knowledge apply(Delta change) {
        Set<String> k = new HashSet<>(known);
        k.addAll(change.known());
        Map<String, Scan> s = new HashMap<>(scans);
        s.putAll(change.scans());
        Map<String, Integer> p = new HashMap<>(pool);
        change.pool()
                .forEach(
                        (aspect, n) -> {
                            if (n > 0) {
                                p.put(aspect, n);
                            } else {
                                p.remove(aspect);
                            }
                        });
        return new Knowledge(k, s, p, change);
    }

    /** This value with no delta, as a baseline. */
    public Knowledge baseline() {
        return delta == null ? this : new Knowledge(known, scans, pool, null);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        return o instanceof Knowledge other
                && known.equals(other.known)
                && scans.equals(other.scans)
                && pool.equals(other.pool);
    }

    @Override
    public int hashCode() {
        return Objects.hash(known, scans, pool);
    }

    private Knowledge change(
            Set<String> learned,
            @Nullable String key,
            @Nullable Scan entry,
            Map<String, Integer> gains) {
        // ponytail: each change copies the ledger, O(keys) (tens of microseconds at 2,000 keys,
        // once per strike); a persistent map if a heavier ledger ever shows in the server share.
        Set<String> k = learned.isEmpty() ? known : union(known, learned);
        Map<String, Scan> s = scans;
        if (key != null && entry != null) {
            s = new HashMap<>(scans);
            s.put(key, entry);
        }
        Map<String, Integer> p = pool;
        Map<String, Integer> changed = new HashMap<>();
        if (!gains.isEmpty()) {
            p = new HashMap<>(pool);
            for (Map.Entry<String, Integer> gain : gains.entrySet()) {
                if (gain.getValue() > 0) {
                    int total = saturatedAdd(p.getOrDefault(gain.getKey(), 0), gain.getValue());
                    p.put(gain.getKey(), total);
                    changed.put(gain.getKey(), total);
                }
            }
        }
        Delta d =
                new Delta(
                        learned,
                        key != null && entry != null ? Map.of(key, entry) : Map.of(),
                        changed);
        return new Knowledge(k, s, p, d);
    }

    /** The fixed point: lowest order first, ties by id, repeated until nothing changes. */
    private static List<Discovery> discover(Set<String> aspects, Set<String> known, Rules rules) {
        List<String> order = new ArrayList<>(aspects);
        order.sort(Comparator.comparingInt(rules::order).thenComparing(Comparator.naturalOrder()));
        List<Discovery> found = new ArrayList<>();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String aspect : order) {
                if (!known.contains(aspect) && known.containsAll(rules.prerequisites(aspect))) {
                    known.add(aspect);
                    found.add(new Discovery(aspect, Math.max(0, rules.bonus(aspect))));
                    changed = true;
                }
            }
        }
        return found;
    }

    private static Set<String> discoveredIds(List<Discovery> discovered) {
        Set<String> ids = new TreeSet<>();
        discovered.forEach(d -> ids.add(d.aspect()));
        return ids;
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        Set<String> u = new HashSet<>(a);
        u.addAll(b);
        return u;
    }

    private static int saturatedAdd(int a, int b) {
        long sum = (long) a + b;
        return sum > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sum;
    }
}
