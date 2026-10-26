package com.andrewbrogoff.vftv.core.worldgen.model;

import com.andrewbrogoff.vftv.api.worldgen.WorldgenLimits;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The {@code vftv:choir_site} registry's load rules (worldgen spec, "Site kinds"), run after the
 * research registry: {@link #check} refuses the world; {@link #builtIn} holds the extra rules for
 * the built-in {@code vftv:choir_ruin}, which the tests assert.
 */
public final class SiteRules {
    private SiteRules() {}

    /** The required built-in site kind. */
    public static final String CHOIR_RUIN = "vftv:choir_ruin";

    /** The Choir pages' category. */
    public static final String CHOIR_PAGES = "vftv:choir_pages";

    /** The page every ruin gives first. */
    public static final String BENEATH_THE_NOTES = "vftv:choir/beneath_the_notes";

    /**
     * What the rules read of one research entry.
     *
     * @param category its category id
     * @param stage its Stage
     * @param kind its kind, as the entry JSON names it
     */
    public record Entry(String category, int stage, String kind) {}

    /**
     * Every problem that refuses the world, each naming its site and entry.
     *
     * @param sites every site kind
     * @param entries every research entry
     */
    public static List<String> check(Map<String, Offers.Site> sites, Map<String, Entry> entries) {
        List<String> problems = new ArrayList<>();
        if (!sites.containsKey(CHOIR_RUIN)) {
            problems.add("vftv:choir_site " + CHOIR_RUIN + ": required, missing");
        }
        new TreeMap<>(sites)
                .forEach(
                        (id, site) -> {
                            String at = "vftv:choir_site " + id + ": ";
                            if (site.pages().isEmpty()
                                    || site.pages().size() > WorldgenLimits.MAX_PAGES_PER_SITE) {
                                problems.add(at + "pages: " + site.pages().size() + ", not 1–64");
                            }
                            if (site.perClaim() < 1
                                    || site.perClaim() > WorldgenLimits.MAX_PAGES_PER_CLAIM) {
                                problems.add(
                                        at + "pages_per_claim: " + site.perClaim() + ", not 1–8");
                            }
                            Set<String> seen = new HashSet<>();
                            for (String page : site.pages()) {
                                Entry e = entries.get(page);
                                if (!seen.add(page)) {
                                    problems.add(at + page + " is listed twice");
                                }
                                if (e == null
                                        || !e.category().equals(CHOIR_PAGES)
                                        || !e.kind().equals("find")) {
                                    problems.add(
                                            at + page + " is not a find entry in " + CHOIR_PAGES);
                                }
                            }
                        });
        return problems;
    }

    /** The built-in {@code vftv:choir_ruin}'s extra rules: Stage 3 only, the first page, all. */
    public static List<String> builtIn(Offers.Site ruin, Map<String, Entry> entries) {
        List<String> problems = new ArrayList<>();
        String at = "vftv:choir_site " + CHOIR_RUIN + ": ";
        for (String page : ruin.pages()) {
            Entry e = entries.get(page);
            if (e != null && e.stage() != 3) {
                problems.add(at + page + " is outside Stage 3");
            }
        }
        if (ruin.pages().isEmpty() || !ruin.pages().getFirst().equals(BENEATH_THE_NOTES)) {
            problems.add(at + BENEATH_THE_NOTES + " must be first");
        }
        new TreeMap<>(entries)
                .forEach(
                        (id, e) -> {
                            if (e.category().equals(CHOIR_PAGES)
                                    && e.stage() == 3
                                    && !ruin.pages().contains(id)) {
                                problems.add(at + "the Stage 3 Choir page " + id + " is missing");
                            }
                        });
        return problems;
    }
}
