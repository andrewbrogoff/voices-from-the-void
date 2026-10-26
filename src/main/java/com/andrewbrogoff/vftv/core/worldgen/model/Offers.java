package com.andrewbrogoff.vftv.core.worldgen.model;

import com.andrewbrogoff.vftv.api.worldgen.WorldgenLimits;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * The Reliquary's offer function (worldgen spec, "Using it"): the first matching row decides. Pure:
 * the caller counts attempts, takes the guard and reads the records, and this decides the outcome
 * and the pages handed out.
 */
public final class Offers {
    private Offers() {}

    /** A row's outcome, in row order. */
    public enum Outcome {
        /** Row 0: the player's Reliquary guard is held. */
        BUSY,
        /** Row 1: the per-player attempt cap is reached this tick. */
        ATTEMPT_CAP,
        /** Row 2: the block's record or the player's claims are unreadable. */
        UNREADABLE,
        /** Row 3: the block's site names no kind. */
        SILENT,
        /** Row 4: claimed here, nothing to reissue. */
        DONE,
        /** Row 5: claimed here, lost pages reissued. */
        REISSUED,
        /** Row 6: no claim here, and nothing new to give; nothing recorded. */
        ALL_HELD,
        /** Row 7: no claim here, and no room for another claim. */
        FULL,
        /** Row 8: a new claim, recorded and handed out. */
        GIVEN;

        /** The fixtures' and logs' name: the constant in lower case. */
        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * A decision.
     *
     * @param outcome the matching row
     * @param pages the pages to hand out (rows 5 and 8; empty otherwise)
     */
    public record Decision(Outcome outcome, List<String> pages) {
        /** Copies the pages. */
        public Decision {
            pages = List.copyOf(pages);
        }
    }

    /**
     * A site kind.
     *
     * @param pages the pages it gives, in order
     * @param perClaim how many one first claim takes
     */
    public record Site(List<String> pages, int perClaim) {
        /** Copies the pages. */
        public Site {
            pages = List.copyOf(pages);
        }
    }

    /** What the decision reads of the registry and the player. */
    public interface State {
        /** Whether a page's entry exists in the research registry. */
        boolean exists(String page);

        /** Whether the player has latched or learned the page. */
        boolean held(String page);

        /** Whether the player has claimed the page at any site. */
        boolean claimedAnywhere(String page);
    }

    /**
     * Decides one use.
     *
     * @param busy the player's Reliquary guard is held
     * @param capped the attempt cap was reached before this attempt
     * @param unreadable the block's record or the player's claims are unreadable
     * @param site the block's site kind, or null when it names none
     * @param claimHere the pages claimed at this position, or null for no claim
     * @param claims the player's claim count
     * @param state the registry and the player
     */
    public static Decision decide(
            boolean busy,
            boolean capped,
            boolean unreadable,
            @Nullable Site site,
            @Nullable List<String> claimHere,
            int claims,
            State state) {
        if (busy) {
            return new Decision(Outcome.BUSY, List.of());
        }
        if (capped) {
            return new Decision(Outcome.ATTEMPT_CAP, List.of());
        }
        if (unreadable) {
            return new Decision(Outcome.UNREADABLE, List.of());
        }
        if (site == null) {
            return new Decision(Outcome.SILENT, List.of());
        }
        if (claimHere != null) {
            List<String> r = new ArrayList<>();
            for (String page : claimHere) {
                if (state.exists(page) && !state.held(page)) {
                    r.add(page);
                }
            }
            return new Decision(r.isEmpty() ? Outcome.DONE : Outcome.REISSUED, r);
        }
        List<String> b = new ArrayList<>();
        for (String page : site.pages()) {
            if (b.size() == site.perClaim()) {
                break;
            }
            if (state.exists(page) && !state.held(page) && !state.claimedAnywhere(page)) {
                b.add(page);
            }
        }
        if (b.isEmpty()) {
            return new Decision(Outcome.ALL_HELD, List.of());
        }
        if (claims >= WorldgenLimits.MAX_CLAIM_SITES) {
            return new Decision(Outcome.FULL, List.of());
        }
        return new Decision(Outcome.GIVEN, b);
    }
}
