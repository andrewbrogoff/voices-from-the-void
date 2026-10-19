package com.andrewbrogoff.vftv.core.research.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * Every entry's triggers and reveals, indexed by type and key when a world opens (research spec,
 * "Triggers"), so a piece of evidence costs O(matching triggers). A trigger that fires is latched
 * whether or not its entry is visible yet: {@code latched} for a find's trigger, {@code
 * reveal_latched} for a secret's reveal.
 */
public final class TriggerIndex {
    private final Map<String, List<Hit>> buckets;

    /**
     * One indexed trigger.
     *
     * @param entry the entry it belongs to
     * @param reveal whether it is a reveal (else a find's trigger)
     * @param trigger the trigger
     */
    private record Hit(String entry, boolean reveal, Trigger trigger) {}

    /** Indexes every entry's triggers and reveals. */
    public TriggerIndex(Map<String, ResearchEntry> entries) {
        Map<String, List<Hit>> out = new HashMap<>();
        new TreeMap<>(entries)
                .forEach(
                        (id, e) -> {
                            e.triggers().forEach(t -> add(out, new Hit(id, false, t)));
                            e.reveal().forEach(t -> add(out, new Hit(id, true, t)));
                        });
        out.replaceAll((k, v) -> List.copyOf(v));
        buckets = Collections.unmodifiableMap(out);
    }

    private static void add(Map<String, List<Hit>> out, Hit hit) {
        String bucket =
                switch (hit.trigger()) {
                    case Trigger.Advancement a -> "a|" + a.advancement();
                    case Trigger.Scanned s when s.key() != null -> "k|" + s.key();
                    case Trigger.Scanned s when s.tag() != null ->
                            "t|" + s.tag().substring(0, Math.max(0, s.tag().indexOf(':')));
                    case Trigger.Scanned s -> "n|";
                    case Trigger.Discovered d -> "d|" + d.aspect();
                    case Trigger.ChoirPage p -> "p|" + p.page();
                    case Trigger.WarpAtLeast w -> "w|";
                };
        out.computeIfAbsent(bucket, k -> new ArrayList<>()).add(hit);
    }

    private List<String> bucketsOf(Evidence evidence) {
        return switch (evidence) {
            case Evidence.Advancement a -> List.of("a|" + a.id());
            case Evidence.Scanned s ->
                    s.node()
                            ? List.of("k|" + s.key(), "n|")
                            : List.of("k|" + s.key(), "t|" + s.kind());
            case Evidence.Discovered d -> List.of("d|" + d.aspect());
            case Evidence.ChoirPage p -> List.of("p|" + p.page());
            case Evidence.Warp w -> List.of("w|");
        };
    }

    /**
     * Latches every trigger {@code evidence} fires on.
     *
     * @return the record with the new latches, or {@code record} itself when nothing new fired
     */
    public ResearchRecord latch(ResearchRecord record, Evidence evidence) {
        List<Hit> hits = new ArrayList<>();
        for (String bucket : bucketsOf(evidence)) {
            for (Hit hit : buckets.getOrDefault(bucket, List.of())) {
                if (evidence.matches(hit.trigger())) {
                    hits.add(hit);
                }
            }
        }
        return apply(record, hits);
    }

    /**
     * The login catch-up (research spec, "Triggers"): latches every trigger whose condition holds
     * now. A {@code choir_page} trigger holds when its page entry is latched or learned; every
     * other type asks {@code holds} (the advancement is done, the ledger holds a matching key, the
     * aspect is known, the current warp meets it).
     */
    public ResearchRecord catchUp(ResearchRecord record, Predicate<Trigger> holds) {
        List<Hit> hits = new ArrayList<>();
        for (List<Hit> bucket : buckets.values()) {
            for (Hit hit : bucket) {
                boolean fires =
                        hit.trigger() instanceof Trigger.ChoirPage p
                                ? record.isLatchedOrLearned(p.page())
                                : holds.test(hit.trigger());
                if (fires) {
                    hits.add(hit);
                }
            }
        }
        return apply(record, hits);
    }

    private static ResearchRecord apply(ResearchRecord record, List<Hit> hits) {
        Set<String> latch = new TreeSet<>();
        Set<String> reveal = new TreeSet<>();
        for (Hit hit : hits) {
            if (hit.reveal()) {
                if (!record.revealLatched().contains(hit.entry())) {
                    reveal.add(hit.entry());
                }
            } else if (!record.latched().contains(hit.entry())) {
                latch.add(hit.entry());
            }
        }
        ResearchRecord out = record;
        if (!latch.isEmpty()) {
            out = out.latch(latch.toArray(String[]::new));
        }
        if (!reveal.isEmpty()) {
            out = out.latchReveal(reveal.toArray(String[]::new));
        }
        return out;
    }
}
