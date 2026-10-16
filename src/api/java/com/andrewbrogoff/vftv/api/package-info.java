/**
 * The root of the Voices From The Void API: the three stability-tier annotations.
 *
 * <p>Every other API package is {@code api.<feature>} or {@code api.neoforge.<feature>}, and
 * defaults to {@link com.andrewbrogoff.vftv.api.Experimental}. This package and its three
 * annotations are Stable. Classes outside {@code com.andrewbrogoff.vftv.api} are internal.
 */
@Stable
@NullMarked
package com.andrewbrogoff.vftv.api;

import org.jspecify.annotations.NullMarked;
