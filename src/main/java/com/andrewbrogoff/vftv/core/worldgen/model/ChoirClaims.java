package com.andrewbrogoff.vftv.core.worldgen.model;

import com.andrewbrogoff.vftv.api.worldgen.WorldgenLimits;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * A player's {@code vftv:choir_claims} record (worldgen spec, "Claims"): every Reliquary position
 * they have claimed at, with the pages each claim recorded. The constructor checks the syntactic
 * bounds only; a {@code dim} naming no loaded dimension or a {@code pos} outside the world is kept
 * as it is and simply never matches. There is no eviction.
 *
 * @param sites sorted by ({@code dim}, signed {@code pos}), no two sharing both
 */
public record ChoirClaims(List<Site> sites) {
    /** The only format this build reads and writes. */
    public static final int FORMAT = 1;

    /** The default: no claims. */
    public static final ChoirClaims EMPTY = new ChoirClaims(List.of());

    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final int MAX_ID = 256;
    private static final Comparator<Site> ORDER =
            Comparator.comparing(Site::dim).thenComparingLong(Site::pos);

    /**
     * One claim.
     *
     * @param dim the Reliquary's dimension id
     * @param pos its {@code BlockPos.asLong()}
     * @param pages the pages the claim recorded: 1 to 8 distinct ids, kept even when unknown
     */
    public record Site(String dim, long pos, List<String> pages) {
        /** Copies the pages and checks the site's bounds. */
        public Site {
            pages = List.copyOf(pages);
            checkId(dim);
            if (pages.isEmpty() || pages.size() > WorldgenLimits.MAX_PAGES_PER_CLAIM) {
                throw new IllegalArgumentException(pages.size() + " pages in one claim");
            }
            if (new HashSet<>(pages).size() != pages.size()) {
                throw new IllegalArgumentException("a page claimed twice in one claim");
            }
            pages.forEach(ChoirClaims::checkId);
        }
    }

    /** Copies the sites and checks every bound. */
    public ChoirClaims {
        sites = List.copyOf(sites);
        if (sites.size() > WorldgenLimits.MAX_CLAIM_SITES) {
            throw new IllegalArgumentException(sites.size() + " sites");
        }
        for (int i = 1; i < sites.size(); i++) {
            if (ORDER.compare(sites.get(i - 1), sites.get(i)) >= 0) {
                throw new IllegalArgumentException("sites not sorted by (dim, pos), or repeated");
            }
        }
    }

    private static void checkId(String id) {
        if (id.length() > MAX_ID || !ID.matcher(id).matches()) {
            throw new IllegalArgumentException("not an id: " + id);
        }
    }

    /** The pages claimed at a position, or null when there is no claim there. */
    public @Nullable List<String> at(String dim, long pos) {
        int i = indexOf(dim, pos);
        return i >= 0 ? sites.get(i).pages() : null;
    }

    /** This record with a new claim; the position must hold none. */
    public ChoirClaims with(String dim, long pos, List<String> pages) {
        int i = indexOf(dim, pos);
        if (i >= 0) {
            throw new IllegalArgumentException("already claimed at " + dim + " " + pos);
        }
        List<Site> next = new ArrayList<>(sites.size() + 1);
        next.addAll(sites);
        next.add(-i - 1, new Site(dim, pos, pages));
        return new ChoirClaims(next);
    }

    /** The "claimed anywhere" set: the union of every claim's pages, O(sites × 8). */
    public Set<String> claimedAnywhere() {
        Set<String> all = new HashSet<>();
        sites.forEach(s -> all.addAll(s.pages()));
        return all;
    }

    private int indexOf(String dim, long pos) {
        int lo = 0;
        int hi = sites.size() - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            Site s = sites.get(mid);
            int c = s.dim().compareTo(dim);
            if (c == 0) {
                c = Long.compare(s.pos(), pos);
            }
            if (c < 0) {
                lo = mid + 1;
            } else if (c > 0) {
                hi = mid - 1;
            } else {
                return mid;
            }
        }
        return -lo - 1;
    }
}
