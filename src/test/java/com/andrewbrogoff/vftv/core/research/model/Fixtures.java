package com.andrewbrogoff.vftv.core.research.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/** A small valid research world and an entry builder, for the model tests. */
final class Fixtures {
    private Fixtures() {}

    /**
     * A slice of the built-in graph: the six exposed primals, hidden Vacuos (revealed by {@code
     * vftv:silence_beneath}) and some compounds.
     */
    static AspectGraph aspects() {
        return new AspectGraph(taught(), Map.of("vftv:vacuos", "vftv:silence_beneath"));
    }

    static Map<String, List<String>> taught() {
        Map<String, List<String>> taught = new TreeMap<>();
        for (String primal : List.of("aer", "terra", "ignis", "aqua", "ordo", "perditio")) {
            taught.put("vftv:" + primal, List.of());
        }
        taught.put("vftv:vacuos", List.of("vftv:aer", "vftv:perditio"));
        taught.put("vftv:lux", List.of("vftv:aer", "vftv:ignis"));
        taught.put("vftv:motus", List.of("vftv:aer", "vftv:ordo"));
        taught.put("vftv:sonus", List.of("vftv:aer", "vftv:motus"));
        taught.put("vftv:silentium", List.of("vftv:sonus", "vftv:vacuos"));
        taught.put("vftv:alienis", List.of("vftv:vacuos", "vftv:aqua"));
        return taught;
    }

    static Map<String, CategoryEntry> categories() {
        Map<String, CategoryEntry> out = new TreeMap<>();
        ResearchEntry.Icon icon = new ResearchEntry.Icon(false, "vftv:commonplace_book");
        out.put("vftv:resonance", new CategoryEntry(1, icon, 0));
        out.put("vftv:distillation", new CategoryEntry(2, icon, 1));
        out.put("vftv:ruins", new CategoryEntry(3, icon, 2));
        out.put("vftv:choirs_road", new CategoryEntry(4, icon, 3));
        out.put("vftv:choir_pages", new CategoryEntry(null, icon, 4));
        return out;
    }

    /** Every Stage opened once, the required keys, and the Vacuos listen with its warp ancestry. */
    static Map<String, B> world() {
        Map<String, B> w = new LinkedHashMap<>();
        w.put(
                "vftv:resonance",
                b(1, "vftv:resonance", 0, 0, EntryKind.FIND).opens(1, "vftv:find/resonance"));
        w.put(
                "vftv:commonplace_book",
                b(1, "vftv:resonance", 0, 1, EntryKind.FREE).parents("vftv:resonance"));
        w.put(
                "vftv:taking_notes",
                b(1, "vftv:resonance", 0, 2, EntryKind.NOTE)
                        .parents("vftv:commonplace_book")
                        .anchors("vftv:aer", "vftv:ignis"));
        w.put(
                "vftv:distillation",
                b(2, "vftv:distillation", 0, 0, EntryKind.FIND)
                        .opens(2, "vftv:find/distillation")
                        .parents("vftv:resonance")
                        .hint("vftv:commonplace_book"));
        w.put(
                "vftv:ruins",
                b(3, "vftv:ruins", 0, 0, EntryKind.FIND)
                        .opens(3, "vftv:find/ruins")
                        .parents("vftv:distillation")
                        .hint("vftv:distillation"));
        B page = b(3, "vftv:choir_pages", 0, 0, EntryKind.FIND);
        page.triggers.add(new Trigger.ChoirPage("vftv:choir/beneath_the_notes"));
        page.pages = new ArrayList<>(List.of(new Page.Choir()));
        w.put("vftv:choir/beneath_the_notes", page);
        B heard = b(3, "vftv:ruins", 1, 0, EntryKind.LISTEN).parents("vftv:ruins");
        heard.warp = new ResearchEntry.Warp(3, 0, 0);
        w.put("vftv:being_heard", heard);
        B silence =
                b(3, "vftv:ruins", 1, 1, EntryKind.LISTEN)
                        .parents("vftv:ruins", "vftv:being_heard")
                        .hint("vftv:choir/beneath_the_notes");
        silence.secret = true;
        silence.reveal.add(new Trigger.ChoirPage("vftv:choir/beneath_the_notes"));
        silence.warp = new ResearchEntry.Warp(0, 4, 0);
        w.put("vftv:silence_beneath", silence);
        w.put(
                "vftv:choirs_road",
                b(4, "vftv:choirs_road", 0, 0, EntryKind.FIND)
                        .opens(4, "vftv:find/choirs_road")
                        .parents("vftv:ruins", "vftv:silence_beneath")
                        .hint("vftv:choir/beneath_the_notes"));
        return w;
    }

    static Map<String, ResearchEntry> build(Map<String, B> world) {
        Map<String, ResearchEntry> out = new TreeMap<>();
        world.forEach((id, b) -> out.put(id, b.build()));
        return out;
    }

    static EntryRules.Report validate(Map<String, B> world) {
        return EntryRules.validate(
                build(world),
                categories(),
                aspects(),
                java.util.Set.of(
                        "vftv:resonance",
                        "vftv:distillation",
                        "vftv:ruins",
                        "vftv:choirs_road",
                        "vftv:commonplace_book",
                        "vftv:silence_beneath",
                        "vftv:choir/beneath_the_notes"),
                categories().keySet());
    }

    /** An entry with the pages its kind needs and nothing else. */
    static B b(int stage, String category, int x, int y, EntryKind kind) {
        B b = new B();
        b.stage = stage;
        b.category = category;
        b.x = x;
        b.y = y;
        b.kind = kind;
        b.pages =
                kind.committed()
                        ? new ArrayList<>(List.of(new Page.Text(), new Page.Action()))
                        : new ArrayList<>(List.of(new Page.Text()));
        return b;
    }

    /** A mutable entry. */
    static final class B {
        int stage;
        String category = "";
        int x;
        int y;
        EntryKind kind = EntryKind.FREE;
        List<String> parents = new ArrayList<>();
        List<String> hidden = new ArrayList<>();
        List<String> anchors = new ArrayList<>();
        Map<String, Integer> skip = new TreeMap<>();
        List<Trigger> triggers = new ArrayList<>();
        boolean secret;
        List<Trigger> reveal = new ArrayList<>();
        @Nullable String hintedBy;
        int opensStage;
        ResearchEntry.Warp warp = ResearchEntry.Warp.NONE;
        Map<String, Integer> rewards = new TreeMap<>();
        List<Page> pages = new ArrayList<>();

        B parents(String... ids) {
            parents.addAll(List.of(ids));
            return this;
        }

        B anchors(String... ids) {
            anchors.addAll(List.of(ids));
            return this;
        }

        B hint(String id) {
            hintedBy = id;
            return this;
        }

        B opens(int stage, String advancement) {
            opensStage = stage;
            triggers.add(new Trigger.Advancement(advancement));
            return this;
        }

        ResearchEntry build() {
            return new ResearchEntry(
                    stage,
                    category,
                    x,
                    y,
                    new ResearchEntry.Icon(false, "vftv:commonplace_book"),
                    kind,
                    parents,
                    hidden,
                    anchors,
                    skip,
                    triggers,
                    secret,
                    reveal,
                    hintedBy,
                    opensStage,
                    warp,
                    rewards,
                    pages);
        }
    }
}
