package com.andrewbrogoff.vftv.core.text.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Locale;
import org.junit.jupiter.api.Test;

/** The frozen formats' vectors, from arcane "Number formats" and research's fact tables. */
class NumberFormatsTest {
    @Test
    void integers() {
        assertEquals("0", NumberFormats.integer(0));
        assertEquals("9999", NumberFormats.integer(9999));
        assertEquals("10000", NumberFormats.integer(10000));
        assertEquals("-3", NumberFormats.integer(-3));
    }

    @Test
    void factors() {
        assertEquals("0.85", NumberFormats.factor(850));
        assertEquals("0.956", NumberFormats.factor(956));
        assertEquals("1.00", NumberFormats.factor(1000));
        assertEquals("10.00", NumberFormats.factor(10000));
        assertEquals("0.00", NumberFormats.factor(0));
        assertEquals("0.005", NumberFormats.factor(5));
    }

    @Test
    void percentages() {
        assertEquals("30%", NumberFormats.percent(300));
        assertEquals("12.5%", NumberFormats.percent(125));
        assertEquals("1%", NumberFormats.percent(10));
        assertEquals("1.5%", NumberFormats.percent(15));
        assertEquals("100%", NumberFormats.percent(1000));
        assertEquals("0%", NumberFormats.percent(0));
        assertEquals("0.5%", NumberFormats.percent(5));
    }

    @Test
    void trimmedDecimals() {
        assertEquals("2.5", NumberFormats.trimmed(25, 1));
        assertEquals("2", NumberFormats.trimmed(20, 1));
        assertEquals("0.3", NumberFormats.trimmed(30, 2));
        assertEquals("0.26", NumberFormats.trimmed(26, 2));
        assertEquals("0.04", NumberFormats.trimmed(40, 3));
        assertEquals("0", NumberFormats.trimmed(0, 2));
        assertEquals("-0.5", NumberFormats.trimmed(-5, 1));
        assertEquals("7", NumberFormats.trimmed(7, 0));
        assertThrows(IllegalArgumentException.class, () -> NumberFormats.trimmed(1, -1));
    }

    @Test
    void seconds() {
        assertEquals("5", NumberFormats.seconds(100));
        assertEquals("1", NumberFormats.seconds(20));
        assertEquals("1.5", NumberFormats.seconds(30));
        assertEquals("0.05", NumberFormats.seconds(1));
        assertEquals("60", NumberFormats.seconds(1200));
    }

    @Test
    void noDefaultLocaleReachesThem() {
        Locale before = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-SA-u-nu-arab"));
            assertEquals("12.5%", NumberFormats.percent(125));
            Locale.setDefault(Locale.GERMANY);
            assertEquals("0.956", NumberFormats.factor(956));
        } finally {
            Locale.setDefault(before);
        }
    }
}
