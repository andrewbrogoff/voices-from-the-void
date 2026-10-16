package com.andrewbrogoff.vftv.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a declaration as Experimental: it carries no promise and may change or vanish in any
 * release, patches included.
 *
 * <p>This is the default tier of every {@code api.<feature>} and {@code api.neoforge.<feature>}
 * package, declared on its {@code package-info}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({
    ElementType.TYPE,
    ElementType.METHOD,
    ElementType.FIELD,
    ElementType.CONSTRUCTOR,
    ElementType.PACKAGE
})
public @interface Experimental {}
