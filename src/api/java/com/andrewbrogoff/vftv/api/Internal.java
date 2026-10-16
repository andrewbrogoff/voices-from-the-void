package com.andrewbrogoff.vftv.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a declaration as Internal: public only for the mod's own wiring, and not for addon use. It
 * carries no promise.
 *
 * <p>On the constructor of a record or event the mod owns, it withdraws construction only: the
 * type's accessors, {@code equals} and record patterns keep the type's own tier.
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
public @interface Internal {}
