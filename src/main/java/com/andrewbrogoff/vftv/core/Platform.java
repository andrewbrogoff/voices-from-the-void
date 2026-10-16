package com.andrewbrogoff.vftv.core;

import com.andrewbrogoff.vftv.core.common.DataSlots;
import com.andrewbrogoff.vftv.core.common.Network;
import com.andrewbrogoff.vftv.core.common.Registrar;

/**
 * Every platform implementation {@code core} needs, built once by the platform's entrypoint and
 * passed to {@link Vftv#init} (ADR 0002: explicit wiring, no service discovery).
 *
 * @param registrar static and datapack registration
 * @param network payload registration and the server's sender
 * @param dataSlots player and chunk state
 */
public record Platform(Registrar registrar, Network network, DataSlots dataSlots) {}
