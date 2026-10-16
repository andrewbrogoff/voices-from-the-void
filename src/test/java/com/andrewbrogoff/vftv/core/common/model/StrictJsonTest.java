package com.andrewbrogoff.vftv.core.common.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

class StrictJsonTest {
    @Test
    void parsesEveryValueKind() {
        JsonObject o =
                StrictJson.parse(
                                "{\"s\": \"x\", \"n\": 1.50, \"b\": true, \"z\": null, \"a\": [1, {\"k\": []}]}")
                        .getAsJsonObject();
        assertEquals("x", o.get("s").getAsString());
        assertEquals(1.5, o.get("n").getAsDouble());
        assertTrue(o.get("b").getAsBoolean());
        assertTrue(o.get("z").isJsonNull());
        assertEquals(2, o.getAsJsonArray("a").size());
    }

    @Test
    void rejectsADuplicateKeyAndNamesItsPath() {
        var e =
                assertThrows(
                        StrictJson.MalformedJsonException.class,
                        () -> StrictJson.parse("{\"a\": {\"k\": 1, \"k\": 2}}"));
        String message = String.valueOf(e.getMessage());
        assertTrue(message.contains("$.a.k"), message);
    }

    @Test
    void rejectsLenientJson() {
        for (String bad :
                new String[] {
                    "",
                    "{'a': 1}",
                    "{\"a\": 1,}",
                    "{\"a\": 1} {}",
                    "// c\n{}",
                    "{\"a\": NaN}",
                    "[1 2]"
                }) {
            assertThrows(StrictJson.MalformedJsonException.class, () -> StrictJson.parse(bad), bad);
        }
    }

    @Test
    void keepsNumbersExact() {
        JsonElement big = StrictJson.parse("12345678901234567890");
        assertEquals("12345678901234567890", big.getAsBigDecimal().toPlainString());
    }
}
