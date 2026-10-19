package com.andrewbrogoff.vftv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andrewbrogoff.vftv.core.common.model.StrictJson;
import com.andrewbrogoff.vftv.core.text.model.Palette;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * The localisation and accessibility static gates over committed resources (accessibility, "The
 * flash test", "Colour and contrast", "Subtitles"; quality gates, "Static gates"): the palette's
 * no-saturated-red assertion, the state-variant luma check, the text contrast table and subtitle
 * coverage, each with self-tests proving it rejects what it claims to.
 *
 * <p>Asset tickets add their rows to {@link #VARIANT_SETS} and {@link #REGIONS}.
 */
class AccessibilityGatesTest {
    private static final Path PROJECT = Path.of(System.getProperty("vftv.projectDir", "."));
    private static final Path ASSETS = PROJECT.resolve("src/main/resources/assets/vftv");
    private static final Path GENERATED = PROJECT.resolve("src/generated/resources/assets/vftv");

    /** WCAG AA for text. */
    private static final double AA = 4.5;

    /** A state-variant set: sprites under {@code assets/vftv/textures/}, compared pairwise. */
    record VariantSet(String owner, List<String> textures) {}

    /** Every state-variant set; the research, arcane, foci and golem asset tickets add theirs. */
    private static final List<VariantSet> VARIANT_SETS =
            List.of(
                    // crucible: a HUD cell's undiscovered and missing-id states (crucible, The
                    // HUD).
                    new VariantSet(
                            "crucible",
                            List.of(
                                    "gui/sprites/aspect/unheard",
                                    "gui/sprites/hud/crucible_missing")),
                    // essentia: a written label carries an ink mark; the open wheel's spokes are
                    // upright, the closed wheel's diagonal.
                    new VariantSet("essentia", List.of("item/label", "item/label_written")),
                    // arcane: the short cell's notch; the preview frames' ring, brightness and
                    // hatch.
                    new VariantSet(
                            "arcane",
                            List.of(
                                    "gui/sprites/worktable/cost_cell",
                                    "gui/sprites/worktable/cost_cell_short")),
                    new VariantSet(
                            "arcane",
                            List.of(
                                    "gui/sprites/worktable/preview_idle",
                                    "gui/sprites/worktable/preview_disabled",
                                    "gui/sprites/worktable/preview_hover",
                                    "gui/sprites/worktable/preview_ready")),
                    new VariantSet(
                            "essentia",
                            List.of(
                                    "block/tube_valve_wheel_open",
                                    "block/tube_valve_wheel_closed")));

    /**
     * A text region an owner declares: the sprite, the rectangle where its text draws, the text
     * colour and, for a uniform region, the one palette colour the region holds (null: textured,
     * checked against every pixel).
     */
    record Region(String texture, int x, int y, int w, int h, int text, @Nullable String uniform) {}

    /** Every text-bearing sprite; asset tickets add their rows. */
    private static final List<Region> REGIONS =
            List.of(
                    // aspect: the crosshair cards' nine-slice frame, its interior opaque ink1.
                    new Region("gui/sprites/hud/vis_frame", 4, 4, 8, 8, Palette.HUSH3, "ink1"),
                    // arcane: the cost number field in both cells, the preview's count corner,
                    // and the Worktable's title, status and inventory label strips.
                    new Region(
                            "gui/sprites/worktable/cost_cell",
                            18,
                            4,
                            21,
                            10,
                            Palette.INK1,
                            "brass5"),
                    new Region(
                            "gui/sprites/worktable/cost_cell_short",
                            18,
                            4,
                            21,
                            10,
                            Palette.INK1,
                            "brass5"),
                    new Region(
                            "gui/sprites/worktable/preview_ready",
                            12,
                            12,
                            11,
                            11,
                            0xffffff,
                            "ink1"),
                    new Region(
                            "gui/sprites/worktable/preview_hover",
                            12,
                            12,
                            11,
                            11,
                            0xffffff,
                            "ink1"),
                    new Region("gui/container/worktable", 7, 6, 160, 8, Palette.INK1, "brass4"),
                    new Region("gui/container/worktable", 8, 112, 160, 9, Palette.INK1, "brass5"),
                    new Region("gui/container/worktable", 7, 125, 160, 8, Palette.INK1, "brass4"));

    // --- the palette -----------------------------------------------------------------------

    /** The palette as {@code tools/art/lib.py} defines it: {@code "ramp": ["#hex", …]}. */
    static Map<String, Integer> libPalette(String lib) {
        Map<String, Integer> out = new LinkedHashMap<>();
        Matcher ramp = Pattern.compile("\"(\\w+)\":\\s*\\[([^\\]]*)\\]").matcher(lib);
        while (ramp.find()) {
            Matcher hex = Pattern.compile("#([0-9a-f]{6})").matcher(ramp.group(2));
            for (int i = 0; hex.find(); i++) {
                out.put(ramp.group(1) + i, Integer.parseInt(hex.group(1), 16));
            }
        }
        return out;
    }

    /** A saturated red: R / (R + G + B) ≥ 0.8. */
    static boolean saturatedRed(int rgb) {
        int r = rgb >> 16 & 0xff;
        int sum = r + (rgb >> 8 & 0xff) + (rgb & 0xff);
        return sum > 0 && r >= 0.8 * sum;
    }

    @Test
    void theJavaPaletteIsTheLockedPalette() throws IOException {
        String lib = Files.readString(PROJECT.resolve("tools/art/lib.py"), StandardCharsets.UTF_8);
        assertEquals(libPalette(lib), Palette.COLOURS);
        assertEquals(48, Palette.COLOURS.size(), "art style §2: 48 colours");
    }

    @Test
    void noPaletteColourIsASaturatedRed() {
        Palette.COLOURS.forEach(
                (name, rgb) -> assertFalse(saturatedRed(rgb), name + " is a saturated red"));
    }

    @Test
    void theRedAssertionCatchesARed() {
        assertTrue(saturatedRed(0xff0000));
        assertTrue(saturatedRed(0xff1010));
        assertFalse(saturatedRed(0xff2020), "R / (R + G + B) just under 0.8");
        assertFalse(saturatedRed(0xc8445c), "vital1");
        assertFalse(saturatedRed(0x000000));
    }

    // --- state variants --------------------------------------------------------------------

    /** Rec. 709 luma of a gamma-encoded colour, 0–1, over black for translucent pixels. */
    static double luma(int argb) {
        double a = (argb >>> 24) / 255.0;
        return a
                * (0.2126 * (argb >> 16 & 0xff)
                        + 0.7152 * (argb >> 8 & 0xff)
                        + 0.0722 * (argb & 0xff))
                / 255.0;
    }

    /** Two variants differ in luma by ≥ 0.15 on at least 8 pixels. */
    static boolean variantsDiffer(BufferedImage a, BufferedImage b) {
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
            return true;
        }
        int differing = 0;
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                if (Math.abs(luma(a.getRGB(x, y)) - luma(b.getRGB(x, y))) >= 0.15) {
                    differing++;
                }
            }
        }
        return differing >= 8;
    }

    private static BufferedImage texture(String path) throws IOException {
        BufferedImage image = ImageIO.read(ASSETS.resolve("textures/" + path + ".png").toFile());
        assertTrue(image != null, path + " is a readable PNG");
        return image;
    }

    @Test
    void everyStateVariantDiffersByLuma() throws IOException {
        for (VariantSet set : VARIANT_SETS) {
            for (int i = 0; i < set.textures().size(); i++) {
                for (int j = i + 1; j < set.textures().size(); j++) {
                    String a = set.textures().get(i);
                    String b = set.textures().get(j);
                    assertTrue(
                            variantsDiffer(texture(a), texture(b)),
                            set.owner() + ": " + a + " and " + b + " differ by hue alone");
                }
            }
        }
    }

    private static BufferedImage filled(int argb) {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                image.setRGB(x, y, argb);
            }
        }
        return image;
    }

    @Test
    void theVariantCheckFailsAHueOnlyPair() {
        // teal1 and a red of the same luma: a hue-only swap.
        BufferedImage teal = filled(0xff3f8a86);
        BufferedImage red = filled(0xff_ad_6d_6d);
        assertTrue(Math.abs(luma(0xff3f8a86) - luma(0xffad6d6d)) < 0.15, "the pair is hue-only");
        assertFalse(variantsDiffer(teal, red));

        BufferedImage outlined = filled(0xff3f8a86);
        for (int x = 0; x < 16; x++) {
            outlined.setRGB(x, 0, 0xffd2f2ea);
        }
        assertTrue(variantsDiffer(teal, outlined), "a 16-px light outline is a mark");
        for (int x = 8; x < 16; x++) {
            outlined.setRGB(x, 0, 0xff3f8a86);
        }
        for (int x = 1; x < 8; x++) {
            outlined.setRGB(x, 0, 0xff3f8a86);
        }
        assertFalse(variantsDiffer(teal, outlined), "one pixel is not");
    }

    // --- text contrast ---------------------------------------------------------------------

    /** WCAG relative luminance of an opaque RGB. */
    static double luminance(int rgb) {
        return 0.2126 * linear(rgb >> 16 & 0xff)
                + 0.7152 * linear(rgb >> 8 & 0xff)
                + 0.0722 * linear(rgb & 0xff);
    }

    private static double linear(int channel) {
        double c = channel / 255.0;
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    static double ratio(int a, int b) {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    /** {@code argb} composited over an opaque {@code base}, as the GUI blends. */
    static int over(int argb, int base) {
        int a = argb >>> 24;
        int out = 0;
        for (int shift = 0; shift <= 16; shift += 8) {
            int c = argb >> shift & 0xff;
            int d = base >> shift & 0xff;
            out |= Math.round((c * a + d * (255 - a)) / 255f) << shift;
        }
        return out;
    }

    /** The worst ratio of text on a possibly translucent background: over black and over white. */
    static double worst(int text, int backgroundArgb) {
        return Math.min(
                ratio(text, over(backgroundArgb, 0x000000)),
                ratio(text, over(backgroundArgb, 0xffffff)));
    }

    /** The region's problems: a uniform region holding another colour, or a pixel below AA. */
    static List<String> regionProblems(BufferedImage image, Region r) {
        List<String> problems = new ArrayList<>();
        Integer uniform = r.uniform() == null ? null : Palette.COLOURS.get(r.uniform());
        for (int y = r.y(); y < r.y() + r.h(); y++) {
            for (int x = r.x(); x < r.x() + r.w(); x++) {
                int pixel = image.getRGB(x, y);
                if (uniform != null && pixel != Palette.opaque(uniform)) {
                    problems.add(r.texture() + " (" + x + ", " + y + ") is not " + r.uniform());
                } else if (worst(r.text(), pixel) < AA) {
                    problems.add(r.texture() + " (" + x + ", " + y + ") is below 4.5:1");
                }
            }
        }
        return problems;
    }

    @Test
    void theContrastTableHolds() {
        // The spec's ratios, to its two decimals.
        double near = 0.011;
        int blackAt08 = 0xcc000000;
        assertEquals(6.48, worst(Palette.AMETHYST3, blackAt08), near, "Voice text, void");
        assertEquals(7.17, worst(Palette.HUSH3, blackAt08), near, "backed hush3");
        assertEquals(9.73, ratio(Palette.HUSH3, Palette.INK1), near, "HUD cards");
        assertEquals(12.17, ratio(Palette.INK1, Palette.BRASS5), near, "prose, panels");
        assertEquals(5.10, ratio(colour("amethyst1"), Palette.BRASS5), near, "foreign hand");
        assertEquals(2.73, ratio(colour("amethyst2"), Palette.BRASS5), near, "why amethyst1");
        assertEquals(13.31, ratio(Palette.INK1, colour("glass1")), near, "Choir paper");
        assertEquals(9.95, ratio(Palette.INK1, Palette.BRASS4), near, "label strips");
        assertEquals(17.14, ratio(0xffffff, Palette.INK1), near, "preview count");
    }

    @Test
    void everyDeclaredTextRegionMeetsAA() throws IOException {
        for (Region r : REGIONS) {
            assertEquals(List.of(), regionProblems(texture(r.texture()), r));
        }
    }

    @Test
    void theContrastCheckRejectsItsDefects() {
        int blackAt05 = 0x80000000;
        assertTrue(worst(Palette.AMETHYST3, blackAt05) < AA, "low opacity, over white");
        assertTrue(worst(Palette.HUSH3, blackAt05) < AA);

        BufferedImage panel = filled(Palette.opaque(Palette.BRASS5));
        panel.setRGB(9, 9, Palette.opaque(colour("brass1")));
        Region textured = new Region("panel", 0, 0, 16, 16, Palette.INK1, null);
        assertEquals(
                List.of("panel (9, 9) is below 4.5:1"),
                regionProblems(panel, textured),
                "a passing sample beside a failing pixel");
        Region uniform = new Region("panel", 0, 0, 16, 16, Palette.INK1, "brass5");
        assertEquals(
                List.of("panel (9, 9) is not brass5"),
                regionProblems(panel, uniform),
                "a uniform region holding a second colour");
        assertEquals(
                List.of(),
                regionProblems(filled(Palette.opaque(Palette.BRASS5)), uniform),
                "a true uniform region passes");
    }

    private static int colour(String name) {
        Integer rgb = Palette.COLOURS.get(name);
        assertTrue(rgb != null, name);
        return rgb == null ? 0 : rgb;
    }

    // --- subtitles -------------------------------------------------------------------------

    /**
     * Subtitle coverage: every sound event names {@code subtitles.vftv.<event>} and that key exists
     * in {@code en_us}, except the enumerated Voice line events, which have none.
     */
    static List<String> subtitleProblems(
            JsonObject sounds, Set<String> english, Set<String> voiceLineEvents) {
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : sounds.entrySet()) {
            String event = entry.getKey();
            JsonElement subtitle = entry.getValue().getAsJsonObject().get("subtitle");
            if (voiceLineEvents.contains(event)) {
                if (subtitle != null) {
                    problems.add(event + ": a Voice line event has no subtitle");
                }
                continue;
            }
            String expected = "subtitles.vftv." + event;
            if (subtitle == null || !subtitle.getAsString().equals(expected)) {
                problems.add(event + ": the subtitle must be " + expected);
            } else if (!english.contains(expected)) {
                problems.add(event + ": " + expected + " is not in en_us");
            }
        }
        return problems;
    }

    /** {@code voice.<path>} for each line id {@code vftv:<path>} of the Voice line script. */
    static Set<String> voiceLineEvents(@Nullable JsonObject script) {
        Set<String> events = new TreeSet<>();
        if (script != null && script.has("lines")) {
            for (String id : script.getAsJsonObject("lines").keySet()) {
                events.add("voice." + id.substring(id.indexOf(':') + 1));
            }
        }
        return events;
    }

    private static @Nullable JsonObject json(Path path) throws IOException {
        return Files.exists(path)
                ? StrictJson.parse(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject()
                : null;
    }

    @Test
    void everySoundEventHasItsSubtitle() throws IOException {
        JsonObject sounds = json(GENERATED.resolve("sounds.json"));
        JsonObject english = json(GENERATED.resolve("lang/en_us.json"));
        Set<String> keys = english == null ? Set.of() : english.keySet();
        JsonObject script = json(PROJECT.resolve("content/warp/voice_lines.json"));
        assertEquals(
                List.of(),
                subtitleProblems(
                        sounds == null ? new JsonObject() : sounds, keys, voiceLineEvents(script)));
    }

    @Test
    void theSubtitleCheckRejectsItsDefects() {
        JsonObject sounds =
                StrictJson.parse(
                                """
                                {"crucible.refuse": {"subtitle": "subtitles.vftv.crucible.refuse", "sounds": []},
                                 "jar.fill": {"sounds": []},
                                 "bell.toll": {"subtitle": "subtitles.vftv.bell.ring", "sounds": []},
                                 "fork.strike": {"subtitle": "subtitles.vftv.fork.strike", "sounds": []},
                                 "voice.band1.first": {"sounds": []},
                                 "voice.insight": {"sounds": []}}
                                """)
                        .getAsJsonObject();
        JsonObject script =
                StrictJson.parse("{\"lines\": {\"vftv:band1.first\": {}}}").getAsJsonObject();
        Set<String> english = Set.of("subtitles.vftv.crucible.refuse", "subtitles.vftv.bell.ring");
        assertEquals(
                List.of(
                        "jar.fill: the subtitle must be subtitles.vftv.jar.fill",
                        "bell.toll: the subtitle must be subtitles.vftv.bell.toll",
                        "fork.strike: subtitles.vftv.fork.strike is not in en_us",
                        "voice.insight: the subtitle must be subtitles.vftv.voice.insight"),
                subtitleProblems(sounds, english, voiceLineEvents(script)),
                "the Voice line is exempt; voice.insight is not a Voice line");
    }
}
