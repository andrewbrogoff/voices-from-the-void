package com.andrewbrogoff.vftv.datagen;

import com.andrewbrogoff.vftv.core.common.model.Schema;
import com.andrewbrogoff.vftv.core.research.ResearchCodecs;
import com.andrewbrogoff.vftv.core.research.model.CategoryEntry;
import com.andrewbrogoff.vftv.core.research.model.EntryKind;
import com.andrewbrogoff.vftv.core.research.model.ResearchEntry;
import com.andrewbrogoff.vftv.core.text.model.LangEntry;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import net.minecraft.resources.Identifier;

/**
 * The research feature's authored content (research spec, "Authored content"): {@code
 * content/research/categories.json}, the Stage files and the system tickets' skeleton files, read
 * through the content framework and decoded with the registry codecs, so the generated JSON is
 * exactly what the registries load. Each entry's prose belongs in the lang file paired with its
 * source: {@code stage1.json} with the lang file {@code stage1.json}, {@code
 * skeleton/crucible.json} with {@code skeleton_crucible.json}, {@code categories.json} with {@code
 * categories.json}.
 */
final class ResearchContent {
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    /** {@code vftv:research_categories/1}: a {@code categories} object of categories by id. */
    static final Schema CATEGORIES =
            Schema.of(
                    "vftv:research_categories/1",
                    Schema.required(
                            "categories",
                            new Schema.MapOf(
                                    new Schema.Str(3, 256, ID),
                                    new Schema.Checked(
                                            json -> decodeProblems(ResearchCodecs.CATEGORY, json)),
                                    1,
                                    256)));

    /** {@code vftv:research_entries/1}: an {@code entries} object of entries by id. */
    static final Schema ENTRIES =
            Schema.of(
                    "vftv:research_entries/1",
                    Schema.required(
                            "entries",
                            new Schema.MapOf(
                                    new Schema.Str(3, 256, ID),
                                    new Schema.Checked(
                                            json -> decodeProblems(ResearchCodecs.ENTRY, json)),
                                    1,
                                    4096)));

    /**
     * One entry and the lang file its prose belongs in.
     *
     * @param entry the decoded entry
     * @param langFile the paired lang file name
     */
    record Sourced(ResearchEntry entry, String langFile) {}

    /**
     * Everything read.
     *
     * @param categories every category by id
     * @param categoryLang each category's paired lang file
     * @param entries every entry by id
     */
    record Content(
            Map<String, CategoryEntry> categories,
            Map<String, String> categoryLang,
            Map<String, Sourced> entries) {
        /** The entries alone. */
        Map<String, ResearchEntry> plainEntries() {
            Map<String, ResearchEntry> out = new TreeMap<>();
            entries.forEach((id, s) -> out.put(id, s.entry()));
            return out;
        }
    }

    private ResearchContent() {}

    /** Reads {@code content/research}, rejecting every problem at once. */
    static Content read(Path contentDir) {
        List<ContentProvider.Envelope> envelopes =
                ContentProvider.read(
                        contentDir.resolve("research"),
                        Map.of(CATEGORIES.id(), CATEGORIES, ENTRIES.id(), ENTRIES));
        Map<String, CategoryEntry> categories = new TreeMap<>();
        Map<String, String> categoryLang = new TreeMap<>();
        Map<String, Sourced> entries = new TreeMap<>();
        List<String> problems = new ArrayList<>();
        for (ContentProvider.Envelope envelope : envelopes) {
            String lang = langFile(envelope.path());
            if (envelope.schema().id().equals(CATEGORIES.id())) {
                members(envelope, "categories")
                        .forEach(
                                (id, json) -> {
                                    if (categories.putIfAbsent(
                                                    id, decode(ResearchCodecs.CATEGORY, json))
                                            != null) {
                                        problems.add(
                                                "research/"
                                                        + envelope.path()
                                                        + ": duplicate category "
                                                        + id);
                                    }
                                    categoryLang.put(id, lang);
                                });
            } else {
                members(envelope, "entries")
                        .forEach(
                                (id, json) -> {
                                    if (entries.putIfAbsent(
                                                    id,
                                                    new Sourced(
                                                            decode(ResearchCodecs.ENTRY, json),
                                                            lang))
                                            != null) {
                                        problems.add(
                                                "research/"
                                                        + envelope.path()
                                                        + ": duplicate entry "
                                                        + id);
                                    }
                                });
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Rejected content:\n" + String.join("\n", problems));
        }
        return new Content(categories, categoryLang, entries);
    }

    /**
     * The lang rows the content declares (research spec, "Prose and fact references"): each entry's
     * title, its hint unless {@code free}, the prose of each {@code text} and {@code choir} page,
     * all required; optional captions on the other prose-bearing pages and the margins 1-3; and
     * each category's name.
     */
    static List<LangEntry> lang(Content content) {
        List<LangEntry> rows = new ArrayList<>();
        content.categoryLang()
                .forEach(
                        (id, file) ->
                                rows.add(
                                        LangEntry.authored(
                                                "category." + dotted(id),
                                                0,
                                                file,
                                                LangEntry.Kind.TEMPLATE)));
        content.entries()
                .forEach(
                        (id, sourced) -> {
                            ResearchEntry e = sourced.entry();
                            String file = sourced.langFile();
                            String base = "research." + dotted(id);
                            rows.add(
                                    LangEntry.authored(
                                            base + ".title", 0, file, LangEntry.Kind.PROSE));
                            if (e.kind() != EntryKind.FREE) {
                                rows.add(
                                        LangEntry.authored(
                                                base + ".hint", 0, file, LangEntry.Kind.PROSE));
                            }
                            List<String> captions = new ArrayList<>(List.of("margin\\.[123]"));
                            for (int i = 0; i < e.pages().size(); i++) {
                                switch (e.pages().get(i).prose()) {
                                    case REQUIRED ->
                                            rows.add(
                                                    LangEntry.authored(
                                                            base + ".page." + i,
                                                            0,
                                                            file,
                                                            LangEntry.Kind.PROSE));
                                    case OPTIONAL -> captions.add("page\\." + i);
                                    case NONE -> {}
                                }
                            }
                            rows.add(
                                    LangEntry.pattern(
                                            Pattern.quote(base + ".")
                                                    + "("
                                                    + String.join("|", captions)
                                                    + ")",
                                            0,
                                            file,
                                            LangEntry.Kind.PROSE));
                        });
        return rows;
    }

    /** {@code ns:a/b} as it appears in a lang key: {@code ns.a.b}. */
    static String dotted(String id) {
        Identifier parsed = Identifier.parse(id);
        return parsed.getNamespace() + "." + parsed.getPath().replace('/', '.');
    }

    /** The lang file paired with a content file under {@code content/research}. */
    static String langFile(String path) {
        return path.startsWith("skeleton/")
                ? "skeleton_" + path.substring("skeleton/".length())
                : path;
    }

    private static Map<String, JsonElement> members(
            ContentProvider.Envelope envelope, String field) {
        return envelope.json().getAsJsonObject().getAsJsonObject(field).asMap();
    }

    private static <T> T decode(Codec<T> codec, JsonElement json) {
        return codec.parse(JsonOps.INSTANCE, json).getOrThrow(IllegalStateException::new);
    }

    private static <T> List<String> decodeProblems(Codec<T> codec, JsonElement json) {
        return codec.parse(JsonOps.INSTANCE, json)
                .error()
                .map(e -> List.of(e.message()))
                .orElse(List.of());
    }
}
