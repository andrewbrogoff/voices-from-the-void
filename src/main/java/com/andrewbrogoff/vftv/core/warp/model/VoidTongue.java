package com.andrewbrogoff.vftv.core.warp.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The Void-tongue phonology lint (lore, "Void-tongue phonology"; warp spec, "The lexicon"):
 * consonants only from s, sh, h, l, r, m, n, v, th, w, y; syllables (C)V(V)(C), closed only by s,
 * l, r, n or h; vowels a, e, i, o, u, doubled for length; stress on the first long vowel, else the
 * first syllable.
 */
public final class VoidTongue {
    private static final String VOWELS = "aeiou";
    private static final Set<String> CODAS = Set.of("s", "l", "r", "n", "h");
    private static final Set<String> SINGLE = Set.of("s", "h", "l", "r", "m", "n", "v", "w", "y");

    /** The punctuation a {@code void} segment's tokens may carry at either end. */
    private static final String PUNCTUATION = ".,;:!?…—'\"";

    private VoidTongue() {}

    /**
     * The 1-based stressed syllable a valid word's phonology gives.
     *
     * @throws IllegalArgumentException when the word breaks the phonology
     */
    public static int stress(String word) {
        List<String> nuclei = syllables(word);
        for (int i = 0; i < nuclei.size(); i++) {
            String n = nuclei.get(i);
            if (n.length() == 2 && n.charAt(0) == n.charAt(1)) {
                return i + 1;
            }
        }
        return 1;
    }

    /** Whether the word passes the lint. */
    public static boolean valid(String word) {
        try {
            syllables(word);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * The tokens of a {@code void} segment: split on spaces, each stripped of leading and trailing
     * punctuation. A token left empty is kept, so the caller can reject it.
     */
    public static List<String> tokens(String segment) {
        List<String> out = new ArrayList<>();
        for (String raw : segment.split(" ", -1)) {
            int from = 0;
            int to = raw.length();
            while (from < to && PUNCTUATION.indexOf(raw.charAt(from)) >= 0) {
                from++;
            }
            while (to > from && PUNCTUATION.indexOf(raw.charAt(to - 1)) >= 0) {
                to--;
            }
            out.add(raw.substring(from, to));
        }
        return out;
    }

    /**
     * The nuclei of a word's syllables, in order.
     *
     * @throws IllegalArgumentException naming the first break of the phonology
     */
    static List<String> syllables(String word) {
        if (word.isEmpty() || !word.chars().allMatch(c -> c >= 'a' && c <= 'z')) {
            throw new IllegalArgumentException(word + ": not lowercase a-z");
        }
        // Units: a vowel run (1 or 2 vowels) or one consonant (sh and th as one).
        List<String> units = new ArrayList<>();
        for (int i = 0; i < word.length(); ) {
            char c = word.charAt(i);
            if (VOWELS.indexOf(c) >= 0) {
                int j = i;
                while (j < word.length() && VOWELS.indexOf(word.charAt(j)) >= 0) {
                    j++;
                }
                if (j - i > 2) {
                    throw new IllegalArgumentException(word + ": three vowels in a row");
                }
                units.add(word.substring(i, j));
                i = j;
            } else if (word.startsWith("sh", i) || word.startsWith("th", i)) {
                units.add(word.substring(i, i + 2));
                i += 2;
            } else if (SINGLE.contains(String.valueOf(c))) {
                units.add(String.valueOf(c));
                i++;
            } else {
                throw new IllegalArgumentException(word + ": the consonant " + c);
            }
        }
        List<String> nuclei = new ArrayList<>();
        List<String> cluster = new ArrayList<>();
        for (String unit : units) {
            if (VOWELS.indexOf(unit.charAt(0)) >= 0) {
                boolean first = nuclei.isEmpty();
                if (first ? cluster.size() > 1 : !clusterOk(cluster)) {
                    throw new IllegalArgumentException(word + ": the cluster " + cluster);
                }
                nuclei.add(unit);
                cluster.clear();
            } else {
                cluster.add(unit);
            }
        }
        if (nuclei.isEmpty()) {
            throw new IllegalArgumentException(word + ": no vowel");
        }
        if (cluster.size() > 1 || (cluster.size() == 1 && !CODAS.contains(cluster.get(0)))) {
            throw new IllegalArgumentException(word + ": the ending " + cluster);
        }
        return nuclei;
    }

    /** Between two nuclei: nothing, one onset, or a coda then an onset. */
    private static boolean clusterOk(List<String> cluster) {
        return cluster.size() <= 1 || (cluster.size() == 2 && CODAS.contains(cluster.get(0)));
    }
}
