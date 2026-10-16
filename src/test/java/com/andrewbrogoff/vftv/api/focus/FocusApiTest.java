package com.andrewbrogoff.vftv.api.focus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.andrewbrogoff.vftv.api.vis.VisKind;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;

/** The focus API's payload rules (API spec, "Immutability and access"). */
// No server exists in a unit test, so live handles (players, levels) are passed as null.
@SuppressWarnings("NullAway")
class FocusApiTest {
    @Test
    void focusPayloadCopiesItsModifiers() {
        List<ResourceKey<FocusModifier>> modifiers = new ArrayList<>(List.of(FocusKeys.FORCE));
        FocusPayload payload =
                new FocusPayload(
                        FocusEventKind.CAST,
                        null,
                        2,
                        modifiers,
                        FocusKeys.RIME,
                        FocusKeys.BURST,
                        VisKind.ORDINARY,
                        FocusGesture.ENTITY,
                        5);

        modifiers.add(FocusKeys.BREADTH);

        assertEquals(List.of(FocusKeys.FORCE), payload.modifiers());
        assertThrows(
                UnsupportedOperationException.class,
                () -> payload.modifiers().add(FocusKeys.REACH));
        assertThrows(UnsupportedOperationException.class, () -> payload.modifiers().clear());
    }

    @Test
    void focusAffectEntityPayloadHoldsItsValues() {
        FocusAffectEntityPayload payload =
                new FocusAffectEntityPayload(null, null, FocusKeys.MEND, false);

        assertEquals(FocusKeys.MEND, payload.effect());
        assertFalse(payload.harmful());
    }
}
