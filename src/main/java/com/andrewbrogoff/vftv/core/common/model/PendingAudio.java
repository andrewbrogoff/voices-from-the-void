package com.andrewbrogoff.vftv.core.common.model;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * Sound events whose audio has not landed yet (no audio files until the audio ticket). NeoForge's
 * datagen refuses a {@code sounds.json} entry with no files, so such an event is registered with
 * its subtitle key but gets no entry; its id is listed instead, one per line, in {@code
 * content/audio/pending/<owner>.txt}. The subtitle, resource-reference and client resource checks
 * skip a listed id, and fail one that has a {@code sounds.json} entry, so a list shrinks as each
 * owner's audio lands.
 */
public final class PendingAudio {
    /** The lists' directory, relative to the project. */
    public static final String DIR = "content/audio/pending";

    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    private PendingAudio() {}

    /**
     * Every listed id: blank lines and {@code #} comments skipped.
     *
     * @throws IllegalArgumentException naming a malformed or duplicate id
     */
    public static Set<String> read(Path project) {
        Path dir = project.resolve(DIR);
        Set<String> ids = new TreeSet<>();
        if (!Files.isDirectory(dir)) {
            return ids;
        }
        List<Path> files;
        try (Stream<Path> list = Files.list(dir)) {
            files = list.filter(p -> p.toString().endsWith(".txt")).sorted().toList();
            for (Path file : files) {
                for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    String line = raw.strip();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    if (!ID.matcher(line).matches()) {
                        throw new IllegalArgumentException(file.getFileName() + ": bad id " + line);
                    }
                    if (!ids.add(line)) {
                        throw new IllegalArgumentException(
                                file.getFileName() + ": " + line + " is listed twice");
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return ids;
    }

    /** The nearest directory at or above {@code from} that holds the lists, or null. */
    public static @Nullable Path findProject(Path from) {
        for (Path p = from.toAbsolutePath(); p != null; p = p.getParent()) {
            if (Files.isDirectory(p.resolve(DIR))) {
                return p;
            }
        }
        return null;
    }
}
