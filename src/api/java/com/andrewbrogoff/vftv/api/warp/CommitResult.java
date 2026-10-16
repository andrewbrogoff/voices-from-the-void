package com.andrewbrogoff.vftv.api.warp;

import com.andrewbrogoff.vftv.api.Internal;
import java.util.OptionalLong;
import org.jspecify.annotations.Nullable;

/**
 * The result of vftv's warp commit or apply. Internal: public only for cross-feature wiring. See
 * the warp spec, "The commit" and "Commit and apply".
 *
 * @param status how the commit ended
 * @param preview the actual deltas for {@link Status#OK}, the current preview for {@link
 *     Status#STALE}, absent otherwise
 * @param revision the record's new revision for {@link Status#OK}, empty otherwise
 */
@Internal
public record CommitResult(Status status, @Nullable WarpPreview preview, OptionalLong revision) {

    /** Constructed by vftv only. */
    @Internal
    public CommitResult {}

    /** How a commit ended. Grows in minor releases: a switch over it needs a default arm. */
    public enum Status {
        /** Written: the deltas were applied. */
        OK,
        /** The preview no longer matches the expected one; nothing changed. */
        STALE,
        /** The player's warp guard is held; nothing changed. */
        BUSY,
        /** The cost is not allowed for its source; nothing changed. */
        INVALID,
        /** The warp record is unreadable; nothing changed. */
        UNREADABLE
    }
}
