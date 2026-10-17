package com.andrewbrogoff.vftv.core.text.model;

/**
 * The frozen number formats (accessibility, "Numbers and plurals"; arcane, "Number formats";
 * research, "Prose and fact references"): ASCII digits, {@code .} as the decimal point, {@code %}
 * as the percentage suffix and no grouping, in every language. Pure integer arithmetic, so no
 * default locale can reach them.
 */
public final class NumberFormats {
    private NumberFormats() {}

    /** A plain integer: costs, pools, spill, counts and ticks. */
    public static String integer(long n) {
        return Long.toString(n);
    }

    /**
     * The arcane factor format: per mille {@code p} as {@code p / 1000} with 2 decimals, or 3 when
     * the third is non-zero (850 → "0.85", 956 → "0.956", 1000 → "1.00", 10000 → "10.00").
     */
    public static String factor(long perMille) {
        return perMille % 10 == 0 ? fixed(perMille / 10, 2) : fixed(perMille, 3);
    }

    /**
     * The arcane percentage format: per mille {@code d} as {@code d / 10} with 0 decimals, or 1
     * when needed, plus "%" (300 → "30%", 125 → "12.5%").
     */
    public static String percent(long perMille) {
        return trimmed(perMille, 1) + "%";
    }

    /**
     * A decimal with up to {@code places} places, trailing zeros and a bare point trimmed: {@code
     * value / 10^places} (tenths 25 → "2.5", 20 → "2"; thousandths 40 → "0.04").
     */
    public static String trimmed(long value, int places) {
        String s = fixed(value, places);
        if (places == 0) {
            return s;
        }
        int end = s.length();
        while (s.charAt(end - 1) == '0') {
            end--;
        }
        if (s.charAt(end - 1) == '.') {
            end--;
        }
        return s.substring(0, end);
    }

    /**
     * Ticks as seconds, {@code ticks / 20} with up to 2 places, trimmed (100 → "5", 30 → "1.5").
     */
    public static String seconds(long ticks) {
        return trimmed(ticks * 5, 2);
    }

    /** {@code value / 10^places} with exactly {@code places} decimals. */
    static String fixed(long value, int places) {
        if (places < 0 || places > 18) {
            throw new IllegalArgumentException("places " + places);
        }
        if (places == 0) {
            return Long.toString(value);
        }
        String sign = value < 0 ? "-" : "";
        String digits = Long.toString(value).substring(sign.length());
        if (digits.length() <= places) {
            digits = "0".repeat(places + 1 - digits.length()) + digits;
        }
        int point = digits.length() - places;
        return sign + digits.substring(0, point) + "." + digits.substring(point);
    }
}
