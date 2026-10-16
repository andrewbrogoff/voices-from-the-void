package com.andrewbrogoff.jartest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The shipping-jar checks (quality gates, "Shipping artifact"; API spec, "Tests"): the mod jar
 * holds the API classes and every resource and nothing dev-only; the api jars hold only the API and
 * its licence; every manifest names the versions.
 */
class ShippingJarTest {
    private static final Path PROJECT = Path.of(System.getProperty("vftv.projectDir"));
    private static final String API_PREFIX = "com/andrewbrogoff/vftv/api/";

    private static Path jar(String name) {
        return Path.of(System.getProperty("vftv.jar." + name));
    }

    private static Set<String> entries(Path jar) throws IOException {
        try (JarFile file = new JarFile(jar.toFile())) {
            Set<String> names = new TreeSet<>();
            file.stream().forEach(e -> names.add(e.getName()));
            return names;
        }
    }

    private static Set<String> files(Path dir, String suffix) throws IOException {
        if (!Files.isDirectory(dir)) {
            return Set.of();
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            Set<String> out = new TreeSet<>();
            walk.filter(Files::isRegularFile)
                    .map(p -> dir.relativize(p).toString().replace('\\', '/'))
                    .filter(p -> p.endsWith(suffix) && !p.startsWith(".cache/"))
                    .forEach(out::add);
            return out;
        }
    }

    private static void assertManifest(Path jar) throws IOException {
        try (JarFile file = new JarFile(jar.toFile())) {
            Attributes attributes = file.getManifest().getMainAttributes();
            assertTrue(attributes.getValue("Vftv-Version") != null, jar + " Vftv-Version");
            assertTrue(
                    attributes.getValue("Vftv-NeoForge-Version") != null,
                    jar + " Vftv-NeoForge-Version");
        }
    }

    private static void assertApiOnly(Path jar, String extension) throws IOException {
        Set<String> stray = new TreeSet<>();
        for (String name : entries(jar)) {
            boolean allowed =
                    name.equals("META-INF/MANIFEST.MF")
                            || name.endsWith("/")
                            || name.equals("LICENSE-API")
                            || (name.startsWith(API_PREFIX) && name.endsWith(extension));
            if (!allowed) {
                stray.add(name);
            }
        }
        assertEquals(Set.of(), stray, jar.getFileName() + " holds only the API");
        assertTrue(
                entries(jar).contains("LICENSE-API"), jar.getFileName() + " carries LICENSE-API");
    }

    @Test
    void theApiJarsHoldOnlyTheApiAndTheirLicence() throws IOException {
        assertApiOnly(jar("api"), ".class");
        assertApiOnly(jar("apiSources"), ".java");
        assertTrue(entries(jar("api")).contains(API_PREFIX + "Stable.class"));
        assertTrue(entries(jar("apiSources")).contains(API_PREFIX + "Stable.java"));
    }

    @Test
    void theModJarHoldsEveryApiClassAndEveryResource() throws IOException {
        Set<String> mod = entries(jar("mod"));
        Set<String> missing = new TreeSet<>();
        for (String apiClass : entries(jar("api"))) {
            if (apiClass.endsWith(".class") && !mod.contains(apiClass)) {
                missing.add(apiClass);
            }
        }
        for (String root : List.of("src/main/resources", "src/generated/resources")) {
            for (String resource : files(PROJECT.resolve(root), "")) {
                if (!mod.contains(resource)) {
                    missing.add(resource);
                }
            }
        }
        assertEquals(Set.of(), missing, "missing from the mod jar");
    }

    @Test
    void theModJarHoldsNothingDevOnly() throws IOException {
        Set<String> devOnly = new TreeSet<>();
        for (String name : entries(jar("mod"))) {
            boolean ours = name.startsWith("com/andrewbrogoff/");
            boolean shipped =
                    name.startsWith("com/andrewbrogoff/vftv/core/")
                            || name.startsWith("com/andrewbrogoff/vftv/neoforge/")
                            || name.startsWith("com/andrewbrogoff/vftv/datagen/")
                            || name.startsWith(API_PREFIX)
                            || name.equals("com/andrewbrogoff/vftv/api/")
                            || name.equals("com/andrewbrogoff/vftv/")
                            || name.equals("com/andrewbrogoff/");
            boolean nested = name.startsWith("META-INF/jarjar/") || name.endsWith(".jar");
            boolean classOutsideOurs = name.endsWith(".class") && !ours;
            if ((ours && !shipped) || nested || classOutsideOurs || name.contains("harness")) {
                devOnly.add(name);
            }
        }
        assertEquals(Set.of(), devOnly, "test, harness or dependency content in the mod jar");
    }

    @Test
    void everyManifestNamesTheVersions() throws IOException {
        for (String name : List.of("mod", "api", "apiSources")) {
            assertManifest(jar(name));
        }
    }
}
