package com.andrewbrogoff.vftv.api.warp;

import com.andrewbrogoff.vftv.api.Stable;

/**
 * An element of the datapack registry {@code vftv:voice_event}: one Voice event, with its type,
 * weight, bands and context rule. A marker in v1: it gives id and holder access only, and an addon
 * reads an event's fields through its JSON. Accessors may arrive in a minor as {@code default}
 * methods.
 *
 * <p>Not for implementation: only vftv implements this interface. See the warp spec, "Voice
 * events".
 */
@Stable
public interface VoiceEvent {}
