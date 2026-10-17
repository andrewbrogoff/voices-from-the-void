package com.andrewbrogoff.vftv.datagen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andrewbrogoff.vftv.core.text.model.LangEntry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResearchContentTest {
    private static final Path CONTENT =
            Path.of(System.getProperty("vftv.projectDir", ".")).resolve("content");

    @Test
    void theShippedContentDecodes() {
        ResearchContent.Content content = ResearchContent.read(CONTENT);
        assertEquals(5, content.categories().size());
        assertTrue(content.entries().containsKey("vftv:choir/beneath_the_notes"));
        assertEquals(
                "skeleton_research.json",
                Objects.requireNonNull(content.entries().get("vftv:resonance")).langFile());
        assertEquals("categories.json", content.categoryLang().get("vftv:resonance"));
    }

    @Test
    void langRowsFollowTheKindAndThePages() {
        List<LangEntry> rows = ResearchContent.lang(ResearchContent.read(CONTENT));
        assertTrue(owns(rows, "research.vftv.taking_notes.hint"));
        assertTrue(owns(rows, "research.vftv.taking_notes.page.0"));
        assertTrue(owns(rows, "research.vftv.taking_notes.margin.3"));
        assertTrue(!owns(rows, "research.vftv.taking_notes.page.1"), "an action page has no prose");
        assertTrue(!owns(rows, "research.vftv.taking_notes.margin.4"));
        assertTrue(!owns(rows, "research.vftv.commonplace_book.hint"), "a free entry has no hint");
        assertTrue(owns(rows, "research.vftv.choir.beneath_the_notes.page.0"));
        assertTrue(owns(rows, "category.vftv.choir_pages"));
    }

    @Test
    void namesAndFiles() {
        assertEquals(
                "vftv.choir.beneath_the_notes",
                ResearchContent.dotted("vftv:choir/beneath_the_notes"));
        assertEquals("skeleton_crucible.json", ResearchContent.langFile("skeleton/crucible.json"));
        assertEquals("stage1.json", ResearchContent.langFile("stage1.json"));
    }

    @Test
    void rejectsUnknownFieldsAndDuplicates(@TempDir Path dir) throws IOException {
        Path research = Files.createDirectories(dir.resolve("research/skeleton"));
        String entry =
                "{\"stage\": 1, \"category\": \"vftv:resonance\", \"position\": [0, 0],"
                        + " \"icon\": {\"item\": \"a:b\"}, \"kind\": \"free\","
                        + " \"pages\": [{\"type\": \"vftv:text\"}]%s}";
        Files.writeString(
                research.resolve("a.json"),
                "{\"schema\": \"vftv:research_entries/1\", \"entries\": {\"vftv:x\": "
                        + entry.formatted(", \"colour\": 1")
                        + "}}");
        IllegalStateException bad =
                assertThrows(IllegalStateException.class, () -> ResearchContent.read(dir));
        assertTrue(String.valueOf(bad.getMessage()).contains("unknown fields [colour]"));
        Files.writeString(
                research.resolve("a.json"),
                "{\"schema\": \"vftv:research_entries/1\", \"entries\": {\"vftv:x\": "
                        + entry.formatted("")
                        + "}}");
        Files.copy(research.resolve("a.json"), research.resolve("b.json"));
        IllegalStateException twice =
                assertThrows(IllegalStateException.class, () -> ResearchContent.read(dir));
        assertTrue(String.valueOf(twice.getMessage()).contains("duplicate entry vftv:x"));
    }

    private static boolean owns(List<LangEntry> rows, String key) {
        return rows.stream().anyMatch(r -> r.owns(key));
    }
}
