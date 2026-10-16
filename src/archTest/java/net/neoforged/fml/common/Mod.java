package net.neoforged.fml.common;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** A name-only stub for the fixtures. */
@Retention(RetentionPolicy.RUNTIME)
public @interface Mod {
    /** The mod id. */
    String value();
}
