package com.andrewbrogoff.vftv.core.aspect;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.aspect.AspectValues;
import com.andrewbrogoff.vftv.core.aspect.model.Derivation;
import com.andrewbrogoff.vftv.core.aspect.model.ScanningTuning;
import com.andrewbrogoff.vftv.core.common.Network;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The server table {@code DerivedAspects} (aspects spec, "Derived values at load"; compatibility,
 * "The derivation at load"): item to values, derived from the live recipes for items with neither a
 * {@code vftv:aspects} entry nor a default, outside the {@code minecraft} and {@code vftv}
 * namespaces. It is rebuilt whole at every data load and replaces the previous table; the lookup
 * reads it after the defaults. Off ({@code aspects.derive_modded}) or over a limit, it is empty.
 */
public final class DerivedAspects {
    /** The namespaces the table and the export never derive over: vanilla's and ours. */
    public static final Predicate<String> MODDED =
            id -> !id.startsWith("minecraft:") && !id.startsWith("vftv:");

    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile AspectConfig config = AspectConfig.DEFAULT;
    private static @Nullable Network network;
    private static BiConsumer<ServerPlayer, DerivedAspectsSync> sender =
            (player, payload) ->
                    Objects.requireNonNull(network, "AspectFeature.init").toPlayer(player, payload);

    private DerivedAspects() {}

    static void init(Network seam) {
        network = seam;
        seam.clientbound(DerivedAspectsSync.TYPE, DerivedAspectsSync.STREAM_CODEC);
    }

    /** The switch in force. */
    public static AspectConfig config() {
        return config;
    }

    /** Swaps the switch; the next data load applies it. */
    public static void installConfig(AspectConfig value) {
        config = value;
    }

    /**
     * The derivation's inputs from the live server data.
     *
     * @param resolved every item's authored or default value, by item id
     * @param capture the adapted recipes
     * @param tuning the loaded {@code derivation.*} tuning
     */
    public record Inputs(
            SortedMap<String, SortedMap<String, Integer>> resolved,
            DerivationRecipes.Capture capture,
            Derivation.Tuning tuning) {
        /** Whether the inputs exceed the derivation's bounds. */
        public boolean overLimit() {
            return Derivation.overLimit(capture.recipes().size(), edges());
        }

        /** The expanded alternative edges. */
        public long edges() {
            return Derivation.edges(capture.recipes());
        }

        /** Derives over the modded candidates. */
        public Derivation.Result derive() {
            return Derivation.derive(resolved, MODDED, capture.recipes(), tuning);
        }
    }

    /** Reads the inputs: the recipes given, the loaded data maps and the loaded tuning. */
    public static Inputs inputs(
            Collection<RecipeHolder<?>> recipes, HolderLookup.Provider registries) {
        SortedMap<String, SortedMap<String, Integer>> resolved = new TreeMap<>();
        for (Item item : BuiltInRegistries.ITEM) {
            AspectValues value = AspectLookup.authored(item);
            if (value != null) {
                resolved.put(DerivationRecipes.id(item), text(value));
            }
        }
        ScanningTuning t = ScanningService.tuning();
        return new Inputs(
                resolved,
                DerivationRecipes.capture(recipes, registries),
                new Derivation.Tuning(t.derivationFactor(), t.smeltBonus()));
    }

    /**
     * Rebuilds the table at a data load (the first and every {@code /reload}) and installs it into
     * the lookup. Runs on the server thread.
     */
    public static void rebuild(
            Collection<RecipeHolder<?>> recipes, HolderLookup.Provider registries) {
        try {
            build(recipes, registries);
        } catch (RuntimeException e) {
            // Nothing refuses the world: modded items fall back to the defaults.
            LOGGER.error("Could not derive modded aspect values; the table is empty", e);
            AspectLookup.installDerived(Map.of());
        }
    }

    /**
     * At server start: the first data load ran before the server config loaded, so an operator's
     * {@code false} empties the table now.
     */
    public static void serverStarted() {
        if (!config.deriveModded()) {
            clear();
        }
    }

    private static void build(
            Collection<RecipeHolder<?>> recipes, HolderLookup.Provider registries) {
        if (!config.deriveModded()) {
            AspectLookup.installDerived(Map.of());
            return;
        }
        Inputs inputs = inputs(recipes, registries);
        if (inputs.overLimit()) {
            LOGGER.warn(
                    "Not deriving modded aspect values: {} recipes and {} ingredient links, where"
                            + " the limits are {} and {}",
                    inputs.capture().recipes().size(),
                    inputs.edges(),
                    Derivation.MAX_RECIPES,
                    Derivation.MAX_EDGES);
            AspectLookup.installDerived(Map.of());
            return;
        }
        Map<Item, AspectValues> table = table(inputs.derive().values());
        AspectLookup.installDerived(table);
        LOGGER.debug("Derived aspect values for {} modded items", table.size());
    }

    /** Empties the table, as when the switch is off. */
    public static void clear() {
        AspectLookup.installDerived(Map.of());
    }

    /** The derivation's values as the lookup's table; ids the registry lacks are dropped. */
    public static Map<Item, AspectValues> table(
            SortedMap<String, SortedMap<String, Integer>> values) {
        Map<Item, AspectValues> table = new LinkedHashMap<>();
        values.forEach(
                (id, value) ->
                        BuiltInRegistries.ITEM
                                .getOptional(Identifier.parse(id))
                                .ifPresent(item -> table.put(item, values(value))));
        return table;
    }

    /**
     * Sends the whole table to a player, in payloads of at most {@value DerivedAspectsSync#MAX}.
     */
    public static void sendTo(ServerPlayer player) {
        payloads(AspectLookup.derived()).forEach(p -> sender.accept(player, p));
    }

    /**
     * Replaces the payload sender and returns the previous one: GameTests record what a mock
     * player, which has no connection, would be sent.
     */
    public static BiConsumer<ServerPlayer, DerivedAspectsSync> swapSender(
            BiConsumer<ServerPlayer, DerivedAspectsSync> replacement) {
        BiConsumer<ServerPlayer, DerivedAspectsSync> previous = sender;
        sender = replacement;
        return previous;
    }

    /** The table as payloads in ascending item id, the last one flagged; one empty for none. */
    public static List<DerivedAspectsSync> payloads(Map<Item, AspectValues> table) {
        List<DerivedAspectsSync.Entry> entries = new ArrayList<>();
        table.forEach((item, value) -> entries.add(new DerivedAspectsSync.Entry(item, value)));
        entries.sort(Comparator.comparing(e -> DerivationRecipes.id(e.item())));
        return split(entries);
    }

    /** Ordered entries in slices of at most {@value DerivedAspectsSync#MAX}, the last flagged. */
    static List<DerivedAspectsSync> split(List<DerivedAspectsSync.Entry> entries) {
        List<DerivedAspectsSync> out = new ArrayList<>();
        for (int from = 0; from < entries.size() || out.isEmpty(); from += DerivedAspectsSync.MAX) {
            int to = Math.min(entries.size(), from + DerivedAspectsSync.MAX);
            out.add(
                    new DerivedAspectsSync(
                            List.copyOf(entries.subList(from, to)), to == entries.size()));
        }
        return out;
    }

    /** A value's aspects by id text. */
    public static SortedMap<String, Integer> text(AspectValues value) {
        SortedMap<String, Integer> out = new TreeMap<>();
        value.aspects().forEach((k, n) -> out.put(AspectSet.text(k), n));
        return out;
    }

    /** A model value as the data-map value. */
    public static AspectValues values(SortedMap<String, Integer> value) {
        TreeMap<ResourceKey<Aspect>, Integer> out = new TreeMap<>();
        value.forEach((id, n) -> out.put(AspectSet.key(id), n));
        return new AspectValues(out);
    }
}
