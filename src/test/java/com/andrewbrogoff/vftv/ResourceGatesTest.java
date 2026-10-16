package com.andrewbrogoff.vftv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andrewbrogoff.vftv.core.common.model.StrictJson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The static gates over hand-written resources (quality gates, "Static gates"): the access
 * transformer rule, the interface-injection ban and the hand-written JSON rule, each with a
 * self-test proving it rejects what it claims to.
 */
class ResourceGatesTest {
    private static final Path PROJECT = Path.of(System.getProperty("vftv.projectDir", "."));
    private static final Path MAIN_RESOURCES = PROJECT.resolve("src/main/resources");
    private static final Pattern WHY = Pattern.compile(".*\\S\\s+#\\s*\\S.*");
    private static final List<String> HAND_WRITTEN_JSON_DIRS =
            List.of("assets/vftv/post_effect/", "assets/vftv/shaders/");

    /** The active lines of an access transformer that lack a trailing {@code # why}. */
    static List<String> linesWithoutWhy(List<String> lines) {
        List<String> bad = new ArrayList<>();
        for (String line : lines) {
            String t = line.strip();
            if (!t.isEmpty() && !t.startsWith("#") && !WHY.matcher(t).matches()) {
                bad.add(line);
            }
        }
        return bad;
    }

    /** Whether a resource path is an interface-injection data file. */
    static boolean isInterfaceInjection(String path) {
        return path.endsWith("injected-interfaces.json") || path.matches("(.*/)?interfaces\\.json");
    }

    /** Whether hand-written JSON may live at this resource path: only where no provider exists. */
    static boolean handWrittenJsonAllowed(String path) {
        return HAND_WRITTEN_JSON_DIRS.stream().anyMatch(path::startsWith);
    }

    private static List<String> mainResources() throws IOException {
        try (Stream<Path> walk = Files.walk(MAIN_RESOURCES)) {
            return walk.filter(Files::isRegularFile)
                    .map(p -> MAIN_RESOURCES.relativize(p).toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        }
    }

    @Test
    void everyAccessTransformerLineSaysWhy() throws IOException {
        Path cfg = MAIN_RESOURCES.resolve("META-INF/accesstransformer.cfg");
        if (Files.exists(cfg)) {
            assertEquals(
                    List.of(), linesWithoutWhy(Files.readAllLines(cfg, StandardCharsets.UTF_8)));
        }
    }

    @Test
    void theAccessTransformerRuleRejectsALineWithoutWhy() {
        assertEquals(
                List.of(
                        "public net.minecraft.client.renderer.GameRenderer resourcePool",
                        "public net.minecraft.A c #"),
                linesWithoutWhy(
                        List.of(
                                "# a comment",
                                "",
                                "public net.minecraft.client.renderer.GameRenderer resourcePool",
                                "public net.minecraft.A b # reuse vanilla's pool",
                                "public net.minecraft.A c #")));
    }

    @Test
    void noInterfaceInjectionDataExists() throws IOException {
        assertEquals(
                List.of(),
                mainResources().stream().filter(ResourceGatesTest::isInterfaceInjection).toList());
        String build = Files.readString(PROJECT.resolve("build.gradle"), StandardCharsets.UTF_8);
        assertFalse(
                build.contains("interfaceInjectionData"),
                "build.gradle configures interface injection");
    }

    @Test
    void theInterfaceInjectionRuleRecognisesTheFiles() {
        assertTrue(isInterfaceInjection("META-INF/injected-interfaces.json"));
        assertTrue(isInterfaceInjection("interfaces.json"));
        assertFalse(isInterfaceInjection("assets/vftv/lang/en_us.json"));
    }

    @Test
    void everyHandWrittenJsonFileIsAllowedAndParses() throws IOException {
        for (String path : mainResources()) {
            if (path.endsWith(".json") || path.endsWith(".mcmeta")) {
                assertTrue(
                        handWrittenJsonAllowed(path),
                        path + " is hand-written where a provider exists");
                StrictJson.parse(
                        Files.readString(MAIN_RESOURCES.resolve(path), StandardCharsets.UTF_8));
            }
        }
    }

    @Test
    void theHandWrittenJsonRuleRejectsProviderOutput() {
        assertTrue(handWrittenJsonAllowed("assets/vftv/post_effect/warp.json"));
        assertTrue(handWrittenJsonAllowed("assets/vftv/shaders/core/drain.json"));
        assertFalse(handWrittenJsonAllowed("assets/vftv/lang/en_us.json"));
        assertFalse(handWrittenJsonAllowed("data/vftv/recipe/jar.json"));
    }
}
