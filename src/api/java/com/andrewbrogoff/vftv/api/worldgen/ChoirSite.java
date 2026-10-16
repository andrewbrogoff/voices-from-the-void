package com.andrewbrogoff.vftv.api.worldgen;

import com.andrewbrogoff.vftv.api.Stable;

/**
 * An entry of the server-only datapack registry {@code vftv:choir_site} ({@link
 * WorldgenKeys#CHOIR_SITE}): a kind of Reliquary site and the Choir pages it gives. A marker in v1:
 * it gives id and holder access only; read an entry's fields through its JSON. See the worldgen
 * spec, "Site kinds".
 *
 * <p>Not for implementation: vftv implements this interface; an addon only refers to it.
 */
@Stable
public interface ChoirSite {}
