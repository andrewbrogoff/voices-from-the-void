package com.andrewbrogoff.vftv.core.aspect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.aspect.HiddenPrimal;
import com.andrewbrogoff.vftv.core.aspect.model.AspectGraph;
import com.andrewbrogoff.vftv.core.text.model.LangEntry;
import com.andrewbrogoff.vftv.core.text.model.Palette;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;

/** The shipped aspect table: its graph, colours, icons and names (aspects spec, "Tests"). */
class BuiltinAspectsTest {
    private static final Path PROJECT = Path.of(System.getProperty("vftv.projectDir", "."));
    private static final Path ICONS =
            PROJECT.resolve("src/main/resources/assets/vftv/textures/gui/sprites/aspect");

    private static Map<String, AspectGraph.Node> nodes() {
        Map<String, AspectGraph.Node> nodes = new HashMap<>();
        BuiltinAspects.ALL.forEach(
                (key, entry) -> {
                    HiddenPrimal hidden = entry.hiddenPrimal();
                    nodes.put(
                            AspectSet.text(key),
                            new AspectGraph.Node(
                                    entry.parents().stream().map(AspectSet::text).toList(),
                                    hidden == null
                                            ? List.of()
                                            : hidden.taughtParents().stream()
                                                    .map(AspectSet::text)
                                                    .toList()));
                });
        return nodes;
    }

    @Test
    void theShippedTablePassesEveryRuleOnBothViews() {
        assertEquals(37, BuiltinAspects.ALL.size());
        List<AspectGraph.Problem> problems =
                AspectGraph.validate(
                        nodes(),
                        BuiltinAspects.ALL.keySet().stream().map(AspectSet::text).toList());
        assertEquals(List.of(), problems, "built-in soft rules are test failures");
    }

    @Test
    void theTiersAreTheSpecs() {
        Map<String, Integer> truth = AspectGraph.tiers(nodes(), false);
        Map<String, Integer> taught = AspectGraph.tiers(nodes(), true);
        assertEquals(0, truth.get("vftv:vacuos"));
        assertEquals(1, taught.get("vftv:vacuos"));
        assertEquals(1, truth.get("vftv:tenebrae"));
        assertEquals(2, taught.get("vftv:tenebrae"));
        assertEquals(3, truth.get("vftv:alienis"));
        assertEquals(3, taught.get("vftv:alienis"));
        assertEquals(3, truth.get("vftv:machina"));
    }

    @Test
    void everyBuiltInColourIsAPaletteMember() {
        Set<Integer> palette = Set.copyOf(Palette.COLOURS.values());
        BuiltinAspects.ALL.forEach(
                (key, entry) ->
                        assertTrue(
                                palette.contains(entry.color()),
                                key.identifier() + " is off the palette"));
    }

    @Test
    void theEntryCodecRoundTripsAndChecksEachEntry() {
        for (Map.Entry<ResourceKey<Aspect>, AspectEntry> e : BuiltinAspects.ALL.entrySet()) {
            JsonElement json =
                    AspectEntry.CODEC.encodeStart(JsonOps.INSTANCE, e.getValue()).getOrThrow();
            AspectEntry back = AspectEntry.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
            assertEquals(e.getValue().parents(), back.parents());
            assertEquals(e.getValue().color(), back.color());
            assertEquals(e.getValue().hiddenPrimal(), back.hiddenPrimal());
        }
        for (String bad :
                List.of(
                        "{\"color\": 1, \"parents\": [\"vftv:aer\"]}",
                        "{\"color\": 1, \"parents\": [\"vftv:aer\", \"vftv:aer\"]}",
                        "{\"color\": 16777216, \"parents\": []}",
                        "{\"color\": 1, \"parents\": [\"a:b\", \"c:d\", \"e:f\"]}",
                        "{\"color\": 1, \"parents\": [\"a:b\", \"c:d\"], \"hidden_primal\":"
                                + " {\"taught_parents\": [\"a:b\", \"c:d\"], \"revealed_by\":"
                                + " \"vftv:x\"}}",
                        "{\"color\": 1, \"parents\": [], \"hidden_primal\": {\"taught_parents\":"
                                + " [\"a:b\", \"a:b\"], \"revealed_by\": \"vftv:x\"}}",
                        "{\"color\": 1, \"parents\": [], \"discovery_bonus\": -1}",
                        "{\"parents\": []}")) {
            assertTrue(
                    AspectEntry.CODEC
                            .parse(JsonOps.INSTANCE, JsonParser.parseString(bad))
                            .isError(),
                    bad);
        }
        AspectEntry withIcon =
                AspectEntry.CODEC
                        .parse(
                                JsonOps.INSTANCE,
                                JsonParser.parseString(
                                        "{\"color\": 1, \"parents\": [], \"icon\": \"x:y\","
                                                + " \"discovery_bonus\": 7}"))
                        .getOrThrow();
        assertEquals("x:y", withIcon.icon().toString());
        assertEquals(7, withIcon.discoveryBonus().getAsInt());
        AspectEntry plain = AspectEntry.of(1, List.of());
        assertEquals(AspectIcons.UNHEARD, plain.icon(), "unbound reads as unheard");
        plain.bind(com.andrewbrogoff.vftv.api.aspect.AspectKeys.AER);
        assertEquals("vftv:aspect/aer", plain.icon().toString());
        assertTrue(AspectEntry.ELEMENT_CODEC.encodeStart(JsonOps.INSTANCE, plain).isSuccess());
    }

    @Test
    void everyIconDeclaresItsIdentityColourPixels() throws IOException {
        JsonObject declared =
                JsonParser.parseString(
                                Files.readString(
                                        PROJECT.resolve("tools/art/aspect_identity.json"),
                                        StandardCharsets.UTF_8))
                        .getAsJsonObject();
        assertTrue(Files.exists(ICONS.resolve("unheard.png")));
        for (Map.Entry<ResourceKey<Aspect>, AspectEntry> e : BuiltinAspects.ALL.entrySet()) {
            String id = AspectSet.text(e.getKey());
            JsonObject icon = declared.getAsJsonObject(id);
            int colour = Integer.parseInt(icon.get("colour").getAsString().substring(1), 16);
            assertEquals(e.getValue().color(), colour, id + ": declared colour is the registry's");
            BufferedImage png =
                    ImageIO.read(
                            ICONS.resolve(e.getKey().identifier().getPath() + ".png").toFile());
            assertEquals(16, png.getWidth());
            assertTrue(icon.getAsJsonArray("pixels").size() >= 4, id + " has identity pixels");
            for (JsonElement p : icon.getAsJsonArray("pixels")) {
                int x = p.getAsJsonArray().get(0).getAsInt();
                int y = p.getAsJsonArray().get(1).getAsInt();
                assertEquals(colour, png.getRGB(x, y) & 0xffffff, id + " at " + x + "," + y);
            }
        }
    }

    @Test
    void everyAspectHasItsNameAndGloss() {
        Set<String> keys =
                AspectLang.TABLE.stream().map(LangEntry::key).collect(Collectors.toSet());
        BuiltinAspects.ALL
                .keySet()
                .forEach(
                        k -> {
                            String base = "aspect.vftv." + k.identifier().getPath();
                            assertTrue(keys.contains(base), base);
                            assertTrue(keys.contains(base + ".gloss"), base + ".gloss");
                        });
        assertEquals(37 * 2 + 31, AspectLang.TABLE.size());
    }
}
