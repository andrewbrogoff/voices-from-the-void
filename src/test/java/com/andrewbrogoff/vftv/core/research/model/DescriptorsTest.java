package com.andrewbrogoff.vftv.core.research.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class DescriptorsTest {
    @Test
    void pageTypesAndTheirProse() {
        List<Page> pages =
                List.of(
                        new Page.Text(),
                        new Page.Recipe("vftv:crucible", false),
                        new Page.Aspects("item:minecraft:torch"),
                        new Page.Structure("vftv:infusion_altar"),
                        new Page.Image("vftv:research/ruins"),
                        new Page.Action(),
                        new Page.Choir());
        assertEquals(
                List.of(
                        "vftv:text",
                        "vftv:recipe",
                        "vftv:aspects",
                        "vftv:structure",
                        "vftv:image",
                        "vftv:action",
                        "vftv:choir"),
                pages.stream().map(Page::type).toList());
        assertEquals(
                List.of(
                        Page.Prose.REQUIRED,
                        Page.Prose.OPTIONAL,
                        Page.Prose.OPTIONAL,
                        Page.Prose.OPTIONAL,
                        Page.Prose.OPTIONAL,
                        Page.Prose.NONE,
                        Page.Prose.REQUIRED),
                pages.stream().map(Page::prose).toList());
    }

    @Test
    void triggerTypes() {
        assertEquals(
                List.of(
                        "vftv:advancement",
                        "vftv:scanned",
                        "vftv:discovered",
                        "vftv:choir_page",
                        "vftv:warp_at_least"),
                List.of(
                                new Trigger.Advancement("vftv:find/resonance"),
                                new Trigger.Scanned(null, null, true),
                                new Trigger.Discovered("vftv:aer"),
                                new Trigger.ChoirPage("vftv:choir/beneath_the_notes"),
                                new Trigger.WarpAtLeast(Trigger.WarpKind.TOTAL, 1))
                        .stream()
                        .map(Trigger::type)
                        .toList());
        assertThrows(IllegalArgumentException.class, () -> new Trigger.Scanned(null, null, false));
        assertThrows(
                IllegalArgumentException.class, () -> new Trigger.Scanned("item:a:b", null, true));
    }
}
