package com.andrewbrogoff.vftv.core.aspect;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;

/** The aspect GUI sprites (aspects spec, "Icons"), in the GUI atlas under {@code aspect/}. */
public final class AspectIcons {
    /** The unheard icon: an open ink ring broken at the top. */
    public static final Identifier UNHEARD =
            Identifier.fromNamespaceAndPath("vftv", "aspect/unheard");

    private AspectIcons() {}

    /** An aspect's sprite, its omitted icon resolved from the holder's own key. */
    public static Identifier of(Holder<Aspect> aspect) {
        if (aspect.value() instanceof AspectEntry entry) {
            aspect.unwrapKey().ifPresent(entry::bind);
        }
        return aspect.value().icon();
    }
}
