package com.andrewbrogoff.vftv.api.warp;

import com.andrewbrogoff.vftv.api.Stable;

/**
 * An element of the server-only datapack registry {@code vftv:bargain}: one Bargain, power offered
 * for warp. A marker in v1: it gives id and holder access only, and an addon reads a bargain's
 * fields through its JSON. Accessors may arrive in a minor as {@code default} methods.
 *
 * <p>Not for implementation: only vftv implements this interface. See the warp spec, "Bargains".
 */
@Stable
public interface Bargain {}
