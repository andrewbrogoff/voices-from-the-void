package com.andrewbrogoff.vftv.core.vis.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * One dimension's relay topology (wands spec, "Topology"): members by key (their position's {@code
 * BlockPos.asLong()}), linked iff their centres are within the range, in connected components of at
 * most {@link #MAX_MEMBERS}. A spatial hash with cell side {@code range} finds neighbours. A member
 * that a range rebuild could not admit is <em>unlinked</em>: kept, but in no component. Mutable, on
 * one thread.
 */
public final class RelayNetwork {
    /** The most members in one component. */
    public static final int MAX_MEMBERS = 32;

    /** A member's kind. */
    public enum Kind {
        /** A Node Tap: a source. */
        TAP,
        /** A Vis Relay: pass-through. */
        RELAY,
        /** A Wand Cradle: a sink. */
        CRADLE
    }

    /** One member: its key, centre cell coordinates, kind and component (-1 while unlinked). */
    private static final class Member {
        final long key;
        final int x;
        final int y;
        final int z;
        Kind kind;
        int component = -1;

        Member(long key, int x, int y, int z, Kind kind) {
            this.key = key;
            this.x = x;
            this.y = y;
            this.z = z;
            this.kind = kind;
        }
    }

    private double range;
    private final Map<Long, Member> members = new HashMap<>();
    private final Map<Long, List<Member>> cells = new HashMap<>();
    private final Map<Integer, SortedSet<Long>> components = new HashMap<>();
    // The unlinked members, so a split's flood never pulls one in uncounted.
    private final TreeSet<Long> unlinkedSet = new TreeSet<>();
    private int nextComponent;

    /** An empty network linking at {@code range}. */
    public RelayNetwork(double range) {
        this.range = range;
    }

    /** The range it links at. */
    public double range() {
        return range;
    }

    // --- reads -------------------------------------------------------------------------------

    /** The member's kind, or null when there is none at {@code key}. */
    public @Nullable Kind kind(long key) {
        Member m = members.get(key);
        return m == null ? null : m.kind;
    }

    /** Whether a member at {@code key} is unlinked. */
    public boolean unlinked(long key) {
        Member m = members.get(key);
        return m != null && m.component < 0;
    }

    /** The member's component id, or -1 when it is absent or unlinked. */
    public int component(long key) {
        Member m = members.get(key);
        return m == null ? -1 : m.component;
    }

    /** A component's members, ascending key; empty for an unknown id. */
    public SortedSet<Long> members(int component) {
        SortedSet<Long> keys = components.get(component);
        return keys == null ? new TreeSet<>() : java.util.Collections.unmodifiableSortedSet(keys);
    }

    /** A component's key: its lowest member key. */
    public long componentKey(int component) {
        return comp(component).first();
    }

    /** Every member key, ascending. */
    public SortedSet<Long> keys() {
        return new TreeSet<>(members.keySet());
    }

    // --- admission ----------------------------------------------------------------------------

    /**
     * The admission size of a new member at ({@code x}, {@code y}, {@code z}): 1 plus the sizes of
     * the distinct components its linked neighbours belong to. It is admitted iff this is at most
     * {@link #MAX_MEMBERS}. The member at {@code self}, if any, is not counted.
     */
    public int admissionSize(long self, int x, int y, int z) {
        List<Integer> seen = new ArrayList<>(4);
        int size = 1;
        for (Member n : neighbours(self, x, y, z)) {
            if (n.component >= 0 && !seen.contains(n.component)) {
                seen.add(n.component);
                size += comp(n.component).size();
            }
        }
        return size;
    }

    /** Whether a member at ({@code x}, {@code y}, {@code z}) would link to any linked member. */
    public boolean linksAny(long self, int x, int y, int z) {
        for (Member n : neighbours(self, x, y, z)) {
            if (n.component >= 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Admits a member if its admission size is at most {@link #MAX_MEMBERS}, merging the components
     * it links; otherwise changes nothing.
     *
     * @return whether it was admitted
     * @throws IllegalArgumentException if a member is already at {@code key}
     */
    public boolean admit(long key, int x, int y, int z, Kind kind) {
        if (members.containsKey(key)) {
            throw new IllegalArgumentException("a member is already at " + key);
        }
        if (admissionSize(key, x, y, z) > MAX_MEMBERS) {
            return false;
        }
        Member m = new Member(key, x, y, z, kind);
        members.put(key, m);
        hash(m);
        link(m);
        return true;
    }

    /** Joins an unlinked member to the components it links (its admission already checked). */
    private void link(Member m) {
        List<Integer> joined = new ArrayList<>(4);
        for (Member n : neighbours(m.key, m.x, m.y, m.z)) {
            if (n.component >= 0 && !joined.contains(n.component)) {
                joined.add(n.component);
            }
        }
        SortedSet<Long> into;
        int id;
        if (joined.isEmpty()) {
            id = nextComponent++;
            into = new TreeSet<>();
            components.put(id, into);
        } else {
            // Merge into the largest, so each member moves O(log n) times over many merges.
            id = joined.get(0);
            for (int c : joined) {
                if (comp(c).size() > comp(id).size()) {
                    id = c;
                }
            }
            into = comp(id);
            for (int c : joined) {
                if (c != id) {
                    for (long k : comp(c)) {
                        member(k).component = id;
                        into.add(k);
                    }
                    components.remove(c);
                }
            }
        }
        into.add(m.key);
        m.component = id;
    }

    /** Changes a member's kind; nothing else changes. */
    public void setKind(long key, Kind kind) {
        Member m = members.get(key);
        if (m != null) {
            m.kind = kind;
        }
    }

    /**
     * Removes a member: its component is rebuilt by BFS over its remaining members (it may split),
     * then every unlinked member is offered admission again, in ascending key.
     *
     * @return whether a member was there
     */
    public boolean remove(long key) {
        Member m = members.remove(key);
        if (m == null) {
            return false;
        }
        List<Member> in = cells.get(cell(m.x, m.y, m.z));
        if (in != null) {
            in.remove(m);
        }
        unlinkedSet.remove(key);
        if (m.component >= 0) {
            SortedSet<Long> rest = comp(m.component);
            components.remove(m.component);
            rest.remove(key);
            for (long k : rest) {
                member(k).component = -1;
            }
            for (long k : rest) {
                Member start = member(k);
                if (start.component < 0) {
                    flood(start);
                }
            }
            readmit();
        }
        return true;
    }

    /**
     * Gives a component to every linked-to-be member reachable from {@code start} among {@code
     * -1}s.
     */
    private void flood(Member start) {
        int id = nextComponent++;
        TreeSet<Long> keys = new TreeSet<>();
        components.put(id, keys);
        ArrayDeque<Member> queue = new ArrayDeque<>();
        start.component = id;
        keys.add(start.key);
        queue.add(start);
        while (!queue.isEmpty()) {
            Member at = queue.poll();
            for (Member n : neighbours(at.key, at.x, at.y, at.z)) {
                // Only the split members (-1 and not unlinked) join: unlinked ones are readmitted.
                if (n.component < 0 && !unlinkedSet.contains(n.key)) {
                    n.component = id;
                    keys.add(n.key);
                    queue.add(n);
                }
            }
        }
    }

    private void readmit() {
        for (long k : List.copyOf(unlinkedSet)) {
            Member u = member(k);
            if (admissionSize(k, u.x, u.y, u.z) <= MAX_MEMBERS) {
                unlinkedSet.remove(k);
                link(u);
            }
        }
    }

    /**
     * Rebuilds every component at a (possibly new) range: members are admitted in ascending key,
     * each by the admission rule, and the rest are unlinked. Nothing is refused.
     *
     * @return for each connected group (ignoring the cap) that exceeds {@link #MAX_MEMBERS}, its
     *     first unlinked member's key, ascending
     */
    public List<Long> rebuild(double newRange) {
        List<Member> all = new ArrayList<>(members.values());
        all.sort((a, b) -> Long.compare(a.key, b.key));
        range = newRange;
        members.clear();
        cells.clear();
        components.clear();
        unlinkedSet.clear();
        for (Member m : all) {
            m.component = -1;
            members.put(m.key, m);
            hash(m);
        }
        // The uncapped groups, for the log: each oversized group is reported once.
        Map<Long, Integer> group = new HashMap<>();
        int groups = 0;
        for (Member m : all) {
            if (group.containsKey(m.key)) {
                continue;
            }
            int g = groups++;
            ArrayDeque<Member> queue = new ArrayDeque<>();
            group.put(m.key, g);
            queue.add(m);
            while (!queue.isEmpty()) {
                Member at = queue.poll();
                for (Member n : neighbours(at.key, at.x, at.y, at.z)) {
                    if (group.putIfAbsent(n.key, g) == null) {
                        queue.add(n);
                    }
                }
            }
        }
        Map<Integer, Long> firstUnlinked = new TreeMap<>();
        for (Member m : all) {
            if (admissionSize(m.key, m.x, m.y, m.z) <= MAX_MEMBERS) {
                link(m);
            } else {
                unlinkedSet.add(m.key);
                firstUnlinked.putIfAbsent(group.get(m.key), m.key);
            }
        }
        List<Long> out = new ArrayList<>(firstUnlinked.values());
        out.sort(null);
        return out;
    }

    /**
     * Adds a persisted member without linking it; once every member is loaded, {@link #rebuild}
     * links them.
     */
    public void load(long key, int x, int y, int z, Kind kind) {
        Member m = new Member(key, x, y, z, kind);
        if (members.put(key, m) == null) {
            hash(m);
        }
    }

    private SortedSet<Long> comp(int id) {
        return java.util.Objects.requireNonNull(components.get(id), "component");
    }

    private Member member(long key) {
        return java.util.Objects.requireNonNull(members.get(key), "member");
    }

    // --- the spatial hash ---------------------------------------------------------------------

    private long cell(int x, int y, int z) {
        long cx = (long) Math.floor(x / range);
        long cy = (long) Math.floor(y / range);
        long cz = (long) Math.floor(z / range);
        return (cx & 0x1FFFFF) << 42 | (cy & 0x1FFFFF) << 21 | (cz & 0x1FFFFF);
    }

    private void hash(Member m) {
        cells.computeIfAbsent(cell(m.x, m.y, m.z), c -> new ArrayList<>()).add(m);
    }

    /** The members (other than {@code self}) within the range, from the 27 cells around. */
    private List<Member> neighbours(long self, int x, int y, int z) {
        List<Member> out = new ArrayList<>();
        long cx = (long) Math.floor(x / range);
        long cy = (long) Math.floor(y / range);
        long cz = (long) Math.floor(z / range);
        double r2 = range * range;
        for (long dx = -1; dx <= 1; dx++) {
            for (long dy = -1; dy <= 1; dy++) {
                for (long dz = -1; dz <= 1; dz++) {
                    long key =
                            ((cx + dx) & 0x1FFFFF) << 42
                                    | ((cy + dy) & 0x1FFFFF) << 21
                                    | ((cz + dz) & 0x1FFFFF);
                    List<Member> in = cells.get(key);
                    if (in == null) {
                        continue;
                    }
                    for (Member n : in) {
                        long ddx = (long) n.x - x;
                        long ddy = (long) n.y - y;
                        long ddz = (long) n.z - z;
                        if (n.key != self && ddx * ddx + ddy * ddy + ddz * ddz <= r2) {
                            out.add(n);
                        }
                    }
                }
            }
        }
        return out;
    }
}
