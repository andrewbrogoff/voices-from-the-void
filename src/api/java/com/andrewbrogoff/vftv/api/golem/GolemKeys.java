package com.andrewbrogoff.vftv.api.golem;

import com.andrewbrogoff.vftv.api.Stable;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * The golem feature's ids: the synced datapack registry {@code vftv:golem_material} and its five
 * built-in materials, the code registry {@code vftv:golem_job} and its two built-in jobs, and the
 * component ids {@code vftv:golem_body}, {@code vftv:golem_core}, {@code vftv:golem_state} and
 * {@code vftv:baton_attunement}. Stable: no id here is removed or renamed within a major. See the
 * golems spec, "Materials", "Cores and jobs" and "Sync and state".
 */
@Stable
public final class GolemKeys {
    private GolemKeys() {}

    /** The synced datapack registry {@code vftv:golem_material} of the golem body materials. */
    public static final ResourceKey<Registry<GolemMaterial>> MATERIAL_REGISTRY =
            ResourceKey.createRegistryKey(id("golem_material"));

    /** {@code vftv:wood}: the fast, fragile material. */
    public static final ResourceKey<GolemMaterial> WOOD = material("wood");

    /** {@code vftv:terracotta}: a fire-immune material. */
    public static final ResourceKey<GolemMaterial> TERRACOTTA = material("terracotta");

    /** {@code vftv:stone}: the slow, sturdy material. */
    public static final ResourceKey<GolemMaterial> STONE = material("stone");

    /** {@code vftv:brass}: the material that carries the most of Stage 2. */
    public static final ResourceKey<GolemMaterial> BRASS = material("brass");

    /** {@code vftv:echo}: the Stage 3 upgrade material. */
    public static final ResourceKey<GolemMaterial> ECHO = material("echo");

    /**
     * The code registry {@code vftv:golem_job} of the golem job types, the one code registry addons
     * may extend: register a {@link GolemJob} through NeoForge's {@code DeferredRegister}.
     */
    public static final ResourceKey<Registry<GolemJob>> JOB_REGISTRY =
            ResourceKey.createRegistryKey(id("golem_job"));

    /** {@code vftv:carry}: the item job of {@code vftv:carrying_core}, all five roles. */
    public static final ResourceKey<GolemJob> CARRY = ResourceKey.create(JOB_REGISTRY, id("carry"));

    /** {@code vftv:decant}: the essentia job of {@code vftv:decanting_core}, TAKE and GIVE. */
    public static final ResourceKey<GolemJob> DECANT =
            ResourceKey.create(JOB_REGISTRY, id("decant"));

    /**
     * The id of the {@code vftv:golem_body} item data component (diagnostics only; direct use is
     * unsupported).
     */
    public static final Identifier GOLEM_BODY_COMPONENT = id("golem_body");

    /**
     * The id of the {@code vftv:golem_core} item data component (diagnostics only; direct use is
     * unsupported).
     */
    public static final Identifier GOLEM_CORE_COMPONENT = id("golem_core");

    /**
     * The id of the {@code vftv:golem_state} item data component (diagnostics only; direct use is
     * unsupported).
     */
    public static final Identifier GOLEM_STATE_COMPONENT = id("golem_state");

    /**
     * The id of the {@code vftv:baton_attunement} item data component (diagnostics only; direct use
     * is unsupported).
     */
    public static final Identifier BATON_ATTUNEMENT_COMPONENT = id("baton_attunement");

    private static ResourceKey<GolemMaterial> material(String path) {
        return ResourceKey.create(MATERIAL_REGISTRY, id(path));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("vftv", path);
    }
}
