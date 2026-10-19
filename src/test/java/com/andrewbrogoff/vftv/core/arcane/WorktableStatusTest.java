package com.andrewbrogoff.vftv.core.arcane;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.andrewbrogoff.vftv.api.TestItems;
import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.api.vis.VisQuote;
import com.andrewbrogoff.vftv.core.text.model.SharedMessages;
import com.andrewbrogoff.vftv.core.vis.VisLang;
import com.andrewbrogoff.vftv.core.vis.VisService;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The status codes, the status line's keys and arguments, and the spend result mapping. */
class WorktableStatusTest {
    @BeforeAll
    static void bind() {
        TestItems.bindComponents();
    }

    /** The illustrative Bell quote: base 8/6/4, effective 10/8/5 at warp 1200 spilling 5. */
    private static VisQuote quote(int warp, int spill, boolean exceeds) {
        return new VisQuote(
                new TreeMap<>(Map.of(AspectKeys.AER, 8, AspectKeys.ORDO, 6, AspectKeys.TERRA, 4)),
                new TreeMap<>(Map.of(AspectKeys.AER, 10, AspectKeys.ORDO, 8, AspectKeys.TERRA, 5)),
                warp,
                spill,
                1000,
                0,
                warp,
                false,
                false,
                exceeds,
                false,
                true,
                List.of(AspectKeys.ORDO));
    }

    private static Worktables.Evaluation evaluation(
            WorktableStatus status, boolean empty, @Nullable VisQuote quote) {
        return new Worktables.Evaluation(
                status, empty, null, new ItemStack(Items.BELL), quote, null, null);
    }

    private static String key(@Nullable Component c) {
        return c != null && c.getContents() instanceof TranslatableContents t ? t.getKey() : "";
    }

    private static List<Object> args(@Nullable Component c) {
        return c != null && c.getContents() instanceof TranslatableContents t
                ? Arrays.asList(t.getArgs()).subList(1, t.getArgs().length)
                : List.of();
    }

    @Test
    void codesAreTheWireValues() {
        for (WorktableStatus s : WorktableStatus.values()) {
            assertEquals(s, WorktableStatus.of(s.code()));
        }
        assertEquals(9, WorktableStatus.ERROR.code());
        assertNull(WorktableStatus.of(10));
        assertNull(WorktableStatus.of(-1));
    }

    @Test
    void readyAgainstReadyTaxed() {
        Component plain =
                evaluation(WorktableStatus.READY, false, quote(1000, 0, false))
                        .line(WorktableStatus.READY);
        assertEquals("status.vftv.worktable.ready", key(plain));
        Component warp1001 =
                evaluation(WorktableStatus.READY, false, quote(1001, 0, false))
                        .line(WorktableStatus.READY);
        assertEquals(ArcaneLang.READY_TAXED, key(warp1001));
        Component voidSpill =
                evaluation(WorktableStatus.READY, false, quote(1000, 1, false))
                        .line(WorktableStatus.READY);
        assertEquals(List.of("1.00", "1"), args(voidSpill));
        Component bell10 =
                evaluation(WorktableStatus.READY, false, quote(1200, 5, false))
                        .line(WorktableStatus.READY);
        assertEquals(List.of("1.20", "5"), args(bell10), "Warp ×1.20, spills 5 flux.");
        Component bell25 =
                evaluation(WorktableStatus.READY, false, quote(1500, 9, false))
                        .line(WorktableStatus.READY);
        assertEquals(List.of("1.50", "9"), args(bell25));
    }

    @Test
    void lineKeysAndTheEmptyGrid() {
        assertNull(
                evaluation(WorktableStatus.NO_RECIPE, true, null).line(WorktableStatus.NO_RECIPE),
                "an empty grid shows no status line");
        assertEquals(
                "status.vftv.worktable.no_wand",
                key(evaluation(WorktableStatus.NO_WAND, true, null).line(WorktableStatus.NO_WAND)));
        assertEquals(
                VisLang.SPEND_INERT,
                key(
                        evaluation(WorktableStatus.INERT_WAND, true, null)
                                .line(WorktableStatus.INERT_WAND)));
        assertEquals(
                SharedMessages.COST_CHANGED,
                key(evaluation(WorktableStatus.NO_RECIPE, true, null).line(WorktableStatus.STALE)),
                "a shown refusal names itself even on an empty grid");
        assertEquals(
                VisLang.SPEND_SHORT,
                key(
                        evaluation(WorktableStatus.SHORT, false, quote(1000, 0, false))
                                .line(WorktableStatus.SHORT)));
    }

    @Test
    void spendResultMapping() {
        assertEquals(WorktableStatus.INERT_WAND, map(VisService.Outcome.INERT, null));
        assertEquals(WorktableStatus.NO_RECIPE, map(VisService.Outcome.UNLEARNED, null));
        assertEquals(WorktableStatus.STALE, map(VisService.Outcome.STALE, null));
        assertEquals(WorktableStatus.SHORT, map(VisService.Outcome.SHORT, quote(1000, 0, false)));
        assertEquals(
                WorktableStatus.EXCEEDS_CAPACITY,
                map(VisService.Outcome.SHORT, quote(1000, 0, true)));
        assertThrows(IllegalArgumentException.class, () -> map(VisService.Outcome.SPENT, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> map(VisService.Outcome.COMMITTED_NOTIFY_FAILED, null));
    }

    private static WorktableStatus map(VisService.Outcome outcome, @Nullable VisQuote quote) {
        return Worktables.refusal(new VisService.SpendResult(outcome, null, quote));
    }
}
