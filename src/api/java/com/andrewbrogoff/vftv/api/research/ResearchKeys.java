package com.andrewbrogoff.vftv.api.research;

import com.andrewbrogoff.vftv.api.Stable;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * The research feature's ids: the {@code vftv:research} and {@code vftv:research_category}
 * registries, the required built-in entries and categories, the {@code vftv:page} component id and
 * the {@code vftv:research} attachment id. Stable: no id here is removed or renamed within a major.
 * See the research spec, "Registry format".
 */
@Stable
public final class ResearchKeys {
    private ResearchKeys() {}

    /** The synced datapack registry {@code vftv:research} of the research entries. */
    public static final ResourceKey<Registry<Research>> REGISTRY =
            ResourceKey.createRegistryKey(id("research"));

    /** The synced datapack registry {@code vftv:research_category} of the book's categories. */
    public static final ResourceKey<Registry<ResearchCategory>> CATEGORY_REGISTRY =
            ResourceKey.createRegistryKey(id("research_category"));

    /** {@code vftv:resonance}: the Stage 1 opener. */
    public static final ResourceKey<Research> RESONANCE =
            ResourceKey.create(REGISTRY, id("resonance"));

    /** {@code vftv:distillation}: the Stage 2 opener. */
    public static final ResourceKey<Research> DISTILLATION =
            ResourceKey.create(REGISTRY, id("distillation"));

    /** {@code vftv:ruins}: the Stage 3 opener. */
    public static final ResourceKey<Research> RUINS = ResourceKey.create(REGISTRY, id("ruins"));

    /** {@code vftv:choirs_road}: the Stage 4 opener. */
    public static final ResourceKey<Research> CHOIRS_ROAD =
            ResourceKey.create(REGISTRY, id("choirs_road"));

    /** {@code vftv:commonplace_book}: the entry for the Commonplace Book itself. */
    public static final ResourceKey<Research> COMMONPLACE_BOOK =
            ResourceKey.create(REGISTRY, id("commonplace_book"));

    /**
     * {@code vftv:silence_beneath}: the Stage 3 listen entry that is Vacuos's {@code revealed_by}.
     */
    public static final ResourceKey<Research> SILENCE_BENEATH =
            ResourceKey.create(REGISTRY, id("silence_beneath"));

    /**
     * {@code vftv:choir/beneath_the_notes}: the Choir page entry that reveals {@code
     * vftv:silence_beneath}.
     */
    public static final ResourceKey<Research> BENEATH_THE_NOTES =
            ResourceKey.create(REGISTRY, id("choir/beneath_the_notes"));

    /** {@code vftv:resonance}: the Stage 1 category. */
    public static final ResourceKey<ResearchCategory> CATEGORY_RESONANCE =
            ResourceKey.create(CATEGORY_REGISTRY, id("resonance"));

    /** {@code vftv:distillation}: the Stage 2 category. */
    public static final ResourceKey<ResearchCategory> CATEGORY_DISTILLATION =
            ResourceKey.create(CATEGORY_REGISTRY, id("distillation"));

    /** {@code vftv:ruins}: the Stage 3 category. */
    public static final ResourceKey<ResearchCategory> CATEGORY_RUINS =
            ResourceKey.create(CATEGORY_REGISTRY, id("ruins"));

    /** {@code vftv:choirs_road}: the Stage 4 category. */
    public static final ResourceKey<ResearchCategory> CATEGORY_CHOIRS_ROAD =
            ResourceKey.create(CATEGORY_REGISTRY, id("choirs_road"));

    /**
     * {@code vftv:choir_pages}: the category of the Choir pages, whose entries keep their own
     * Stage.
     */
    public static final ResourceKey<ResearchCategory> CATEGORY_CHOIR_PAGES =
            ResourceKey.create(CATEGORY_REGISTRY, id("choir_pages"));

    /**
     * The id of the {@code vftv:page} data component of a Choir page item, holding its entry id
     * (diagnostics only; direct use is unsupported).
     */
    public static final Identifier PAGE_COMPONENT = id("page");

    /**
     * The id of the {@code vftv:research} player attachment (diagnostics only; direct use is
     * unsupported).
     */
    public static final Identifier RESEARCH_ATTACHMENT = id("research");

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("vftv", path);
    }
}
