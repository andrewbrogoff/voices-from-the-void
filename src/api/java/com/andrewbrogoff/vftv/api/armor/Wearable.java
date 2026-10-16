package com.andrewbrogoff.vftv.api.armor;

import com.andrewbrogoff.vftv.api.Stable;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A {@code vftv:wearable} data-map value: the attribute numbers an item gives where it counts.
 * Constructible by addons. The map holds raw entries; which take effect (the slot must match the
 * item) is vftv's own validated view. See the armor spec, "Wearables".
 *
 * @param slot where the item counts
 * @param armor {@code minecraft:armor}, 0 to {@link ArmorLimits#WEARABLE_MAX_ARMOR}
 * @param toughness {@code minecraft:armor_toughness}, 0.0 to 10.0, at most 1 decimal place
 * @param knockbackResistance {@code minecraft:knockback_resistance}, 0.00 to 1.00, at most 2
 *     decimal places
 * @param visDiscount {@code vftv:vis_discount}, 0.00 to 0.90, at most 2 decimal places
 * @param baffle {@code vftv:baffle}, 0 to {@link ArmorLimits#WEARABLE_MAX_BAFFLE}
 */
@Stable
public record Wearable(
        WearableSlot slot,
        int armor,
        double toughness,
        double knockbackResistance,
        double visDiscount,
        int baffle) {

    /**
     * The data-map codec: {@code slot} required, every number optional with default 0. It rejects
     * an unknown field, a value out of range or with more decimal places, and an entry whose every
     * number is 0.
     */
    public static final Codec<Wearable> CODEC =
            strict(
                    RecordCodecBuilder.create(
                            i ->
                                    i.group(
                                                    WearableSlot.CODEC
                                                            .fieldOf("slot")
                                                            .forGetter(Wearable::slot),
                                                    exactInt(0, ArmorLimits.WEARABLE_MAX_ARMOR)
                                                            .optionalFieldOf("armor", 0)
                                                            .forGetter(Wearable::armor),
                                                    Codec.DOUBLE
                                                            .optionalFieldOf("toughness", 0.0)
                                                            .forGetter(Wearable::toughness),
                                                    Codec.DOUBLE
                                                            .optionalFieldOf(
                                                                    "knockback_resistance", 0.0)
                                                            .forGetter(
                                                                    Wearable::knockbackResistance),
                                                    Codec.DOUBLE
                                                            .optionalFieldOf("vis_discount", 0.0)
                                                            .forGetter(Wearable::visDiscount),
                                                    exactInt(0, ArmorLimits.WEARABLE_MAX_BAFFLE)
                                                            .optionalFieldOf("baffle", 0)
                                                            .forGetter(Wearable::baffle))
                                            .apply(i, Wearable::new)));

    /**
     * Checks every range the data map allows.
     *
     * @throws IllegalArgumentException if a number is out of range or has more decimal places than
     *     allowed, or every number is 0
     */
    public Wearable {
        Objects.requireNonNull(slot, "slot");
        whole("armor", armor, ArmorLimits.WEARABLE_MAX_ARMOR);
        decimal("toughness", toughness, 10.0, 1);
        decimal("knockback_resistance", knockbackResistance, 1.0, 2);
        decimal("vis_discount", visDiscount, 0.9, 2);
        whole("baffle", baffle, ArmorLimits.WEARABLE_MAX_BAFFLE);
        if (armor == 0
                && toughness == 0
                && knockbackResistance == 0
                && visDiscount == 0
                && baffle == 0) {
            throw new IllegalArgumentException("a wearable needs at least one non-zero number");
        }
    }

    private static void whole(String field, int value, int max) {
        if (value < 0 || value > max) {
            throw new IllegalArgumentException(field + " must be 0 to " + max + ": " + value);
        }
    }

    private static void decimal(String field, double value, double max, int places) {
        if (!(value >= 0 && value <= max)
                || BigDecimal.valueOf(value).stripTrailingZeros().scale() > places) {
            throw new IllegalArgumentException(
                    field
                            + " must be 0 to "
                            + max
                            + " with at most "
                            + places
                            + " decimal places: "
                            + value);
        }
    }

    /** An int codec that rejects a fraction or overflow instead of truncating it. */
    private static Codec<Integer> exactInt(int min, int max) {
        return Codec.of(
                Codec.INT,
                Codec.DOUBLE.comapFlatMap(
                        d ->
                                d == Math.rint(d) && d >= min && d <= max
                                        ? DataResult.success((int) (double) d)
                                        : DataResult.error(
                                                () ->
                                                        "Not an integer "
                                                                + min
                                                                + "-"
                                                                + max
                                                                + ": "
                                                                + d),
                        Integer::doubleValue));
    }

    /** Encodes with {@code fields}; decodes with it after refusing unknown fields. */
    private static Codec<Wearable> strict(Codec<Wearable> fields) {
        Decoder<Wearable> decoder = Codec.PASSTHROUGH.flatMap(input -> decode(fields, input));
        return Codec.of(fields, decoder);
    }

    private static <T> DataResult<Wearable> decode(Codec<Wearable> fields, Dynamic<T> input) {
        List<String> known =
                List.of(
                        "slot",
                        "armor",
                        "toughness",
                        "knockback_resistance",
                        "vis_discount",
                        "baffle");
        for (Dynamic<T> key : input.getMapValues().result().orElse(Map.of()).keySet()) {
            String name = key.asString("");
            if (!known.contains(name)) {
                return DataResult.error(() -> "unknown wearable field: " + name);
            }
        }
        try {
            return fields.parse(input);
        } catch (IllegalArgumentException e) {
            return DataResult.error(e::getMessage);
        }
    }
}
