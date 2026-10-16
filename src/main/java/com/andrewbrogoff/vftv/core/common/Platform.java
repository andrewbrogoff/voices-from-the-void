package com.andrewbrogoff.vftv.core.common;

/**
 * Every platform implementation {@code core} needs, built once by the platform's entrypoint and
 * passed to {@code Vftv.init} (ADR 0002: explicit wiring, no service discovery). It lives in the
 * shared leaf so every feature's init, the leaf {@code core.flux} included, can take it.
 *
 * @param registrar static and datapack registration
 * @param network payload registration and the server's sender
 * @param dataSlots player and chunk state
 */
public record Platform(Registrar registrar, Network network, DataSlots dataSlots) {}
