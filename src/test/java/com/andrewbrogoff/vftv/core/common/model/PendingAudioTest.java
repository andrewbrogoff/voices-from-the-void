package com.andrewbrogoff.vftv.core.common.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PendingAudioTest {
    @Test
    void readsEveryListSkippingCommentsAndFindsTheProject(@TempDir Path project)
            throws IOException {
        assertEquals(Set.of(), PendingAudio.read(project), "no lists, nothing pending");
        assertNull(PendingAudio.findProject(project));
        Path dir = Files.createDirectories(project.resolve(PendingAudio.DIR));
        Files.writeString(dir.resolve("a.txt"), "# a comment\nvftv:a.one\n\n  vftv:a.two  \n");
        Files.writeString(dir.resolve("b.txt"), "vftv:b\n");
        Files.writeString(dir.resolve("notes.md"), "vftv:ignored\n");
        assertEquals(Set.of("vftv:a.one", "vftv:a.two", "vftv:b"), PendingAudio.read(project));
        Path deep = Files.createDirectories(project.resolve("harness-results/run/game"));
        assertEquals(project.toAbsolutePath(), PendingAudio.findProject(deep));
    }

    @Test
    void rejectsABadOrRepeatedId(@TempDir Path project) throws IOException {
        Path dir = Files.createDirectories(project.resolve(PendingAudio.DIR));
        Files.writeString(dir.resolve("a.txt"), "vftv:x\n", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("b.txt"), "vftv:x\n", StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> PendingAudio.read(project));
        Files.writeString(dir.resolve("b.txt"), "Not An Id\n", StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> PendingAudio.read(project));
    }

    @Test
    void theShippedListsParse() {
        Path project = Path.of(System.getProperty("vftv.projectDir", "."));
        assertEquals(true, PendingAudio.read(project).contains("vftv:fork.strike"));
    }
}
