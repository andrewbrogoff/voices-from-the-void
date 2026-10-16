package com.andrewbrogoff.vftv.datagen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andrewbrogoff.vftv.core.common.model.Schema;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContentProviderTest {
    private static final Schema NOTES =
            Schema.of("vftv:notes/1", Schema.required("text", new Schema.Str(1, 20, null)));

    @TempDir Path dir;

    private void write(String path, String text) throws IOException {
        Path file = dir.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }

    @Test
    void readsValidEnvelopesInPathOrder() throws IOException {
        write("b.json", "{\"schema\": \"vftv:notes/1\", \"text\": \"b\"}");
        write("a/z.json", "{\"schema\": \"vftv:notes/1\", \"text\": \"z\"}");
        write("readme.txt", "not content");
        List<ContentProvider.Envelope> read = ContentProvider.read(dir, Map.of(NOTES.id(), NOTES));
        assertEquals(
                List.of("a/z.json", "b.json"),
                read.stream().map(ContentProvider.Envelope::path).toList());
        assertEquals(NOTES, read.get(0).schema());
    }

    @Test
    void rejectsEveryBadFileAtOnce() throws IOException {
        write("dup.json", "{\"schema\": \"vftv:notes/1\", \"text\": \"a\", \"text\": \"b\"}");
        write("unknown.json", "{\"schema\": \"vftv:other/1\"}");
        write("long.json", "{\"schema\": \"vftv:notes/1\", \"text\": \"" + "x".repeat(21) + "\"}");
        var e =
                assertThrows(
                        IllegalStateException.class,
                        () -> ContentProvider.read(dir, Map.of(NOTES.id(), NOTES)));
        String message = String.valueOf(e.getMessage());
        assertTrue(message.contains("dup.json: duplicate key $.text"), message);
        assertTrue(
                message.contains("unknown.json: $.schema: unknown schema vftv:other/1"), message);
        assertTrue(message.contains("long.json: $.text: length 21"), message);
    }

    @Test
    void aMissingDirectoryHoldsNoContent() {
        assertEquals(List.of(), ContentProvider.read(dir.resolve("absent"), Map.of()));
    }
}
