package com.andrewbrogoff.vftv.api.neoforge.essentia;

import com.andrewbrogoff.vftv.api.Stable;
import com.andrewbrogoff.vftv.api.aspect.Aspect;
import java.util.Objects;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.transfer.resource.Resource;
import org.jspecify.annotations.Nullable;

/**
 * One aspect of essentia as a NeoForge transfer {@link Resource}, moved through the {@link
 * EssentiaCapabilities#HANDLER} capability. Constructible by addons.
 *
 * <p>The empty value is {@link #EMPTY}, whose aspect is {@code null}; {@link #isEmpty()} is true
 * exactly then, and {@code new EssentiaResource(null)} equals it. A non-empty value needs a holder
 * bound to a key, as every {@code Holder.Reference} from the {@code vftv:aspect} registry is.
 * Equality and the hash code compare the aspect's {@link ResourceKey} only, never holder identity,
 * so holders for one key from different registry snapshots give equal resources.
 *
 * @param aspect the aspect, or {@code null} for the empty resource
 */
@Stable
public record EssentiaResource(@Nullable Holder<Aspect> aspect) implements Resource {
    /** The empty resource: no aspect. */
    public static final EssentiaResource EMPTY = new EssentiaResource(null);

    /**
     * Checks that a present aspect holder is bound to a key.
     *
     * @throws IllegalArgumentException if the holder has no key (an unbound direct holder)
     */
    public EssentiaResource {
        if (aspect != null && aspect.unwrapKey().isEmpty()) {
            throw new IllegalArgumentException("An essentia aspect holder must be bound to a key");
        }
    }

    /**
     * Whether this is the empty resource.
     *
     * @return true exactly when the aspect is {@code null}
     */
    @Override
    public boolean isEmpty() {
        return aspect == null;
    }

    /**
     * Compares the aspects' keys only.
     *
     * @param other the object to compare with
     * @return true if {@code other} is an essentia resource with the same aspect key, or both are
     *     empty
     */
    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof EssentiaResource that && Objects.equals(key(), that.key());
    }

    /**
     * Hashes the aspect's key only.
     *
     * @return the hash of the aspect's key, or 0 when empty
     */
    @Override
    public int hashCode() {
        return Objects.hashCode(key());
    }

    private @Nullable ResourceKey<Aspect> key() {
        return aspect == null ? null : aspect.unwrapKey().orElseThrow();
    }
}
