package com.andrewbrogoff.vftv.api.road;

import com.andrewbrogoff.vftv.api.Stable;

/**
 * An entry of the synced datapack registry {@code vftv:chorister} ({@link EldritchKeys#CHORISTER}):
 * one Step's Chorister instance, with its health, moves and cycle. The registry holds exactly
 * {@link EldritchKeys#WUUREN}, {@link EldritchKeys#SUHLAA} and {@link EldritchKeys#NIHTHAA}; a pack
 * may override their values but can't add or remove one. A marker in v1: it gives id and holder
 * access only; read an entry's fields through its JSON. See the Choristers spec, "The registry".
 *
 * <p>Not for implementation: vftv implements this interface; an addon only refers to it.
 */
@Stable
public interface Chorister {}
