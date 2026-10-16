/**
 * The Hush feature's NeoForge events: {@code HushSpreadEvent} before each Hush mutation and {@code
 * FluxEvent} after each settled deposit or clear. See the Hush spec.
 *
 * <p>Experimental by default: a declaration is Stable only where it carries {@link
 * com.andrewbrogoff.vftv.api.Stable}.
 */
@Experimental
@NullMarked
package com.andrewbrogoff.vftv.api.neoforge.hush;

import com.andrewbrogoff.vftv.api.Experimental;
import org.jspecify.annotations.NullMarked;
