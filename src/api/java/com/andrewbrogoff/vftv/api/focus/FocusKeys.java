package com.andrewbrogoff.vftv.api.focus;

import com.andrewbrogoff.vftv.api.Stable;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * The focus feature's ids: the three synced datapack registries, their 15 required built-in entries
 * (8 effects, 4 media, 3 modifiers), the block tag {@code #vftv:focus_immune} and the {@code
 * vftv:focus} component id. Packs may override the built-ins' values and add entries; they cannot
 * delete these. Stable: no id here is removed or renamed within a major. See the foci spec,
 * "Composition" and "The roster".
 */
@Stable
public final class FocusKeys {
    private FocusKeys() {}

    /** The synced datapack registry {@code vftv:focus_effect} of the focus effects. */
    public static final ResourceKey<Registry<FocusEffect>> EFFECT_REGISTRY =
            ResourceKey.createRegistryKey(id("focus_effect"));

    /** The synced datapack registry {@code vftv:focus_medium} of the focus media. */
    public static final ResourceKey<Registry<FocusMedium>> MEDIUM_REGISTRY =
            ResourceKey.createRegistryKey(id("focus_medium"));

    /** The synced datapack registry {@code vftv:focus_modifier} of the focus modifiers. */
    public static final ResourceKey<Registry<FocusModifier>> MODIFIER_REGISTRY =
            ResourceKey.createRegistryKey(id("focus_modifier"));

    /** {@code vftv:kindle}: an ordinary entity effect that burns and sets alight. */
    public static final ResourceKey<FocusEffect> KINDLE = effect("kindle");

    /** {@code vftv:gust}: an ordinary entity effect that pushes away. */
    public static final ResourceKey<FocusEffect> GUST = effect("gust");

    /** {@code vftv:rime}: an ordinary entity effect that chills and slows. */
    public static final ResourceKey<FocusEffect> RIME = effect("rime");

    /** {@code vftv:delve}: an ordinary block effect that mines. */
    public static final ResourceKey<FocusEffect> DELVE = effect("delve");

    /** {@code vftv:mend}: an ordinary entity effect that heals; not harmful. */
    public static final ResourceKey<FocusEffect> MEND = effect("mend");

    /** {@code vftv:exchange}: an ordinary block effect that exchanges blocks. */
    public static final ResourceKey<FocusEffect> EXCHANGE = effect("exchange");

    /** {@code vftv:unmaking}: a Void entity effect that harms through armour. */
    public static final ResourceKey<FocusEffect> UNMAKING = effect("unmaking");

    /** {@code vftv:undertow}: a Void entity effect that pulls toward the caster. */
    public static final ResourceKey<FocusEffect> UNDERTOW = effect("undertow");

    /** {@code vftv:bolt}: the medium of a launched projectile. */
    public static final ResourceKey<FocusMedium> BOLT = medium("bolt");

    /** {@code vftv:touch}: the medium of a short ray to one target. */
    public static final ResourceKey<FocusMedium> TOUCH = medium("touch");

    /** {@code vftv:burst}: the medium of an area around a point. */
    public static final ResourceKey<FocusMedium> BURST = medium("burst");

    /** {@code vftv:beam}: the held medium that works in pulses. */
    public static final ResourceKey<FocusMedium> BEAM = medium("beam");

    /** {@code vftv:force}: the modifier that raises an effect's magnitude per level. */
    public static final ResourceKey<FocusModifier> FORCE = modifier("force");

    /** {@code vftv:reach}: the modifier that lengthens a Touch or Beam entity ray per level. */
    public static final ResourceKey<FocusModifier> REACH = modifier("reach");

    /** {@code vftv:breadth}: the modifier that widens a Burst per level. */
    public static final ResourceKey<FocusModifier> BREADTH = modifier("breadth");

    /**
     * The block tag {@code #vftv:focus_immune}: blocks no block effect touches (empty by default).
     */
    public static final TagKey<Block> FOCUS_IMMUNE =
            TagKey.create(Registries.BLOCK, id("focus_immune"));

    /**
     * The id of the {@code vftv:focus} item data component (diagnostics only; direct use is
     * unsupported).
     */
    public static final Identifier FOCUS_COMPONENT = id("focus");

    private static ResourceKey<FocusEffect> effect(String path) {
        return ResourceKey.create(EFFECT_REGISTRY, id(path));
    }

    private static ResourceKey<FocusMedium> medium(String path) {
        return ResourceKey.create(MEDIUM_REGISTRY, id(path));
    }

    private static ResourceKey<FocusModifier> modifier(String path) {
        return ResourceKey.create(MODIFIER_REGISTRY, id(path));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("vftv", path);
    }
}
