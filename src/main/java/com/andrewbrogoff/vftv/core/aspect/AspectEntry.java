package com.andrewbrogoff.vftv.core.aspect;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.api.aspect.HiddenPrimal;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ExtraCodecs;
import org.jspecify.annotations.Nullable;

/**
 * An entry of {@code vftv:aspect} (aspects spec, "Registry format"): {@code color}, {@code
 * parents}, optional {@code icon}, {@code hidden_primal} and {@code discovery_bonus}. The codec
 * checks each entry alone; the graph rules run over the whole registry when a world opens.
 *
 * <p>An element does not know its own id, so an omitted icon resolves to {@code <ns>:aspect/<path>}
 * when the registry is {@link #bind bound}, on each side once its tags load.
 */
public final class AspectEntry implements Aspect {
    /** The largest {@code discovery_bonus}. */
    public static final int MAX_BONUS = 1000;

    private static final Codec<ResourceKey<Aspect>> KEY = ResourceKey.codec(AspectKeys.REGISTRY);

    private static final Codec<HiddenPrimal> HIDDEN =
            RecordCodecBuilder.create(
                    i ->
                            i.group(
                                            KEY.listOf(2, 2)
                                                    .fieldOf("taught_parents")
                                                    .forGetter(HiddenPrimal::taughtParents),
                                            Identifier.CODEC
                                                    .fieldOf("revealed_by")
                                                    .forGetter(HiddenPrimal::revealedBy))
                                    .apply(i, HiddenPrimal::new));

    /** The entry codec, used on disk and on the network. */
    public static final Codec<AspectEntry> CODEC =
            RecordCodecBuilder.<AspectEntry>create(
                            i ->
                                    i.group(
                                                    ExtraCodecs.intRange(0, 0xFFFFFF)
                                                            .fieldOf("color")
                                                            .forGetter(AspectEntry::color),
                                                    KEY.listOf(0, 2)
                                                            .fieldOf("parents")
                                                            .forGetter(AspectEntry::parents),
                                                    Identifier.CODEC
                                                            .optionalFieldOf("icon")
                                                            .forGetter(e -> e.icon),
                                                    HIDDEN.optionalFieldOf("hidden_primal")
                                                            .forGetter(
                                                                    e ->
                                                                            Optional.ofNullable(
                                                                                    e.hiddenPrimal)),
                                                    ExtraCodecs.intRange(0, MAX_BONUS)
                                                            .optionalFieldOf("discovery_bonus")
                                                            .forGetter(
                                                                    e ->
                                                                            e.discoveryBonus
                                                                                            .isPresent()
                                                                                    ? Optional.of(
                                                                                            e
                                                                                                    .discoveryBonus
                                                                                                    .getAsInt())
                                                                                    : Optional
                                                                                            .empty()))
                                            .apply(i, AspectEntry::new))
                    .validate(AspectEntry::check);

    /**
     * The registry's element codec: an {@link AspectEntry}, up-cast, with the down-cast checked.
     */
    public static final Codec<Aspect> ELEMENT_CODEC =
            CODEC.flatComapMap(
                    e -> e,
                    a ->
                            a instanceof AspectEntry e
                                    ? DataResult.success(e)
                                    : DataResult.error(() -> "not a vftv aspect entry: " + a));

    private final int color;
    private final List<ResourceKey<Aspect>> parents;
    private final Optional<Identifier> icon;
    private final @Nullable HiddenPrimal hiddenPrimal;
    private final OptionalInt discoveryBonus;
    private volatile @Nullable Identifier resolvedIcon;

    /** An entry as the codec reads it. */
    public AspectEntry(
            int color,
            List<ResourceKey<Aspect>> parents,
            Optional<Identifier> icon,
            Optional<HiddenPrimal> hiddenPrimal,
            Optional<Integer> discoveryBonus) {
        this.color = color;
        this.parents = List.copyOf(parents);
        this.icon = icon;
        this.hiddenPrimal = hiddenPrimal.orElse(null);
        this.discoveryBonus = discoveryBonus.map(OptionalInt::of).orElseGet(OptionalInt::empty);
        this.resolvedIcon = icon.orElse(null);
    }

    /** A primal or compound with no optional fields, as the built-in table holds them. */
    public static AspectEntry of(int color, List<ResourceKey<Aspect>> parents) {
        return new AspectEntry(
                color, parents, Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static DataResult<AspectEntry> check(AspectEntry e) {
        if (e.parents.size() == 1) {
            return DataResult.error(() -> "an aspect has 0 or 2 parents, not 1");
        }
        if (e.parents.size() == 2 && e.parents.get(0).equals(e.parents.get(1))) {
            return DataResult.error(() -> "an aspect's two parents must differ");
        }
        HiddenPrimal hidden = e.hiddenPrimal;
        if (hidden != null) {
            if (!e.parents.isEmpty()) {
                return DataResult.error(() -> "a hidden primal has no parents");
            }
            if (hidden.taughtParents().get(0).equals(hidden.taughtParents().get(1))) {
                return DataResult.error(() -> "a hidden primal's taught parents must differ");
            }
        }
        return DataResult.success(e);
    }

    /** Resolves an omitted icon to {@code <ns>:aspect/<path>} of the entry's own key. */
    public void bind(ResourceKey<Aspect> key) {
        if (icon.isEmpty()) {
            resolvedIcon = key.identifier().withPrefix("aspect/");
        }
    }

    @Override
    public int color() {
        return color;
    }

    @Override
    public List<ResourceKey<Aspect>> parents() {
        return parents;
    }

    /** The icon; before the registry is bound, an omitted one reads as the unheard icon. */
    @Override
    public Identifier icon() {
        Identifier resolved = resolvedIcon;
        return resolved != null ? resolved : AspectIcons.UNHEARD;
    }

    @Override
    public @Nullable HiddenPrimal hiddenPrimal() {
        return hiddenPrimal;
    }

    @Override
    public OptionalInt discoveryBonus() {
        return discoveryBonus;
    }

    /** What discovery needs known first: the parents, or a hidden primal's taught parents. */
    public List<ResourceKey<Aspect>> prerequisites() {
        HiddenPrimal hidden = hiddenPrimal;
        return hidden != null ? hidden.taughtParents() : parents;
    }

    @Override
    public String toString() {
        return "AspectEntry[color=" + Integer.toHexString(color) + ", parents=" + parents + "]";
    }
}
