package fixture.vftv.neoforge.mixin;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** The one-line justification every mixin carries. */
@Retention(RetentionPolicy.CLASS)
public @interface WhyMixin {
    /** Why no event, API or access transformer would do. */
    String value();
}
