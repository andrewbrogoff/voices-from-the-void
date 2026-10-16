package com.andrewbrogoff.vftv.api.golem;

import com.andrewbrogoff.vftv.api.Stable;
import net.minecraft.resources.ResourceKey;

/**
 * The binding of a golem core item to its job: an addon's core {@code Item} implements this, as
 * vftv's own core items do. A golem reads its job from the core item named by its {@code
 * vftv:golem_core} component. See the golems spec, "Cores and jobs", and the API spec, "Golem
 * jobs".
 *
 * <p>The implementer's contract: implement it on an {@code Item} subclass only; bind each core item
 * to exactly one job, fixed at registration, so {@link #golemJob()} returns the same key for the
 * item's whole lifetime, whatever the stack or the world. The key should name a job registered in
 * {@code vftv:golem_job}; a key that names none (or a core whose mod is removed) leaves a golem
 * fitted with it inert, not broken.
 */
@Stable
public interface GolemCore {
    /**
     * The job this core item binds a golem to.
     *
     * @return the job's key in {@code vftv:golem_job}, the same on every call
     */
    ResourceKey<GolemJob> golemJob();
}
