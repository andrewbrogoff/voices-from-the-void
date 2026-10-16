package com.andrewbrogoff.vftv.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a declaration as Stable: within one major version it keeps binary and source compatibility,
 * and is removed only after a deprecation window.
 *
 * <p>A declaration is Stable only when it carries this annotation, or inherits it from its
 * enclosing type or package, and is on the API spec's Stable allowlist. The effective tier of a
 * declaration is its own annotation, else its nearest enclosing type's, else its package's.
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
public @interface Stable {}
