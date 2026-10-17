package com.andrewbrogoff.vftv.core.text.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andrewbrogoff.vftv.core.text.model.CardLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Narration attempts (accessibility, "HUD narration"), with a recording sink in place of the
 * platform narrator: each act is attempted exactly once when its result arrives, and never on a
 * passive refresh. The helper never reads the narrator mode, so an act attempts the same whatever
 * the mode; vanilla decides emission.
 */
class NarrationTest {
    private final List<Component> said = new ArrayList<>();
    private Consumer<Component> previous = c -> {};

    @BeforeEach
    void record() {
        previous = Narration.swapSink(said::add);
    }

    @AfterEach
    void restore() {
        Narration.swapSink(previous);
    }

    @Test
    void aStatusLineIsNarratedOnceInFull() {
        Component text = Component.translatable("status.vftv.test", Component.literal("Aer"));
        StatusLineHud.arrive(text, 1.0);
        assertEquals(List.of(text), said);
        for (int i = 0; i < 80; i++) {
            StatusLineHud.tick();
        }
        assertEquals(1, said.size(), "counting down and expiring narrate nothing");
        assertFalse(StatusLineHud.showing());
        assertEquals(text, StatusLineHud.latest(), "kept for the details view after it fades");

        Component next = Component.literal("next");
        StatusLineHud.arrive(next, 2.0);
        StatusLineHud.arrive(next, 0.5);
        assertEquals(List.of(text, next, next), said, "every arrival, replacements included");
        StatusLineHud.clear();
        assertEquals(null, StatusLineHud.latest(), "a level change forgets it");
    }

    @Test
    void theLifetimeFollowsTheNotificationTime() {
        StatusLineHud.arrive(Component.literal("x"), 2.0);
        for (int i = 0; i < 119; i++) {
            StatusLineHud.tick();
        }
        assertTrue(StatusLineHud.showing(), "120 ticks at 2.0");
        StatusLineHud.tick();
        assertFalse(StatusLineHud.showing());
    }

    private static boolean reversed(String range) {
        int dash = range.indexOf('-');
        return Integer.parseInt(range.substring(0, dash))
                >= Integer.parseInt(range.substring(dash + 1));
    }

    @Test
    void aSheetTurnNarratesTheNewSheetsBodyOnce() {
        List<String> narrated = new ArrayList<>();
        CrosshairCard card =
                new CrosshairCard(
                        new CrosshairCard.Body() {
                            @Override
                            public void draw(
                                    GuiGraphicsExtractor graphics,
                                    int fromRow,
                                    int toRow,
                                    int columns,
                                    int x,
                                    int y) {}

                            @Override
                            public Component narrate(int fromRow, int toRow) {
                                narrated.add(fromRow + "-" + toRow);
                                return Component.literal("rows " + fromRow + "-" + toRow);
                            }
                        });
        card.content(
                "target",
                List.of(Component.literal("head")),
                40,
                42,
                List.of(Component.literal("more")),
                List.of());
        // 40 cells in 6 columns under the band's maximum: 7 rows and the more line, 4 per sheet.
        CardLayout.Card tall = new CardLayout.Card(1, 50, 40, 42, 1, 100, 0, 0);
        card.install(CardLayout.compute(320, 240, 83, tall, sheets -> 100));
        assertTrue(said.isEmpty(), "a relayout is passive");

        assertFalse(card.turn(-1), "already at the first sheet");
        assertTrue(card.turn(1));
        assertEquals(List.of("rows 4-7 more"), said.stream().map(Component::getString).toList());
        assertFalse(card.turn(1), "already at the last sheet");
        assertTrue(card.turn(-1));
        assertEquals("rows 0-4", said.get(1).getString());
        assertEquals(List.of("4-7", "0-4"), narrated, "one attempt per turn");

        assertTrue(card.turn(1));
        assertEquals(1, card.sheet());

        // A tail that spills onto its own sheets: those sheets narrate only their own lines.
        narrated.clear();
        said.clear();
        List<Component> tail = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tail.add(Component.literal("t" + i));
        }
        card.content("long tail", List.of(), 40, 42, tail, List.of());
        assertFalse(card.turn(1), "the old content's layout neither turns nor narrates");
        assertTrue(said.isEmpty());
        CardLayout longTail =
                CardLayout.compute(
                        320, 240, 83, new CardLayout.Card(1, 50, 40, 42, 10, 100, 0, 0), s -> 100);
        card.install(longTail);
        while (card.turn(1)) {
            // through every sheet
        }
        assertTrue(longTail.sheetStart(longTail.sheets() - 1) > longTail.rows());
        assertTrue(narrated.stream().noneMatch(NarrationTest::reversed), "no empty grid range");
        String last = said.get(said.size() - 1).getString();
        assertFalse(
                last.contains("t0"), "a later tail sheet doesn't repeat earlier lines: " + last);
        assertTrue(last.endsWith("t9"), last);
        card.content("other", List.of(), 40, 42, List.of(), List.of());
        assertEquals(0, card.sheet(), "a new target starts on the first sheet");
    }
}
