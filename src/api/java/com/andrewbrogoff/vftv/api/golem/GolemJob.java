package com.andrewbrogoff.vftv.api.golem;

import com.andrewbrogoff.vftv.api.Stable;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * A golem job type: a declaration with no behaviour, registered in the code registry {@code
 * vftv:golem_job} ({@link GolemKeys#JOB_REGISTRY}) and bound to a core item through {@link
 * GolemCore}. Addons construct and register their own. See the golems spec, "Cores and jobs", and
 * the API spec, "Golem jobs".
 *
 * @param carry what the job carries
 * @param roles the roles a mark may take, in the Baton's cycling order: non-empty and distinct; an
 *     {@link GolemCarryKind#ESSENTIA} job may use only {@link GolemRole#TAKE} and {@link
 *     GolemRole#GIVE}
 * @param defaultRole the role of a newly added mark, one of {@code roles}
 */
@Stable
public record GolemJob(GolemCarryKind carry, List<GolemRole> roles, GolemRole defaultRole) {
    /**
     * The codec of the object {@code {"carry": ..., "roles": [...], "default_role": ...}}, with the
     * constructor's checks reported as errors.
     */
    public static final Codec<GolemJob> CODEC =
            RecordCodecBuilder.<DataResult<GolemJob>>create(
                            i ->
                                    i.group(
                                                    GolemCarryKind.CODEC
                                                            .fieldOf("carry")
                                                            .forGetter(j -> j.getOrThrow().carry()),
                                                    GolemRole.CODEC
                                                            .listOf()
                                                            .fieldOf("roles")
                                                            .forGetter(j -> j.getOrThrow().roles()),
                                                    GolemRole.CODEC
                                                            .fieldOf("default_role")
                                                            .forGetter(
                                                                    j ->
                                                                            j.getOrThrow()
                                                                                    .defaultRole()))
                                            .apply(i, GolemJob::checked))
                    .<GolemJob>flatXmap(r -> r, j -> DataResult.success(DataResult.success(j)));

    /**
     * Creates a job type, copying {@code roles}.
     *
     * @throws IllegalArgumentException if {@code roles} is empty or has a duplicate, if {@code
     *     defaultRole} isn't one of them, or if an {@code ESSENTIA} job lists a role other than
     *     {@code TAKE} and {@code GIVE}
     */
    public GolemJob {
        roles = List.copyOf(roles);
        String problem = problem(carry, roles, defaultRole);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
    }

    private static DataResult<GolemJob> checked(
            GolemCarryKind carry, List<GolemRole> roles, GolemRole defaultRole) {
        String problem = problem(carry, roles, defaultRole);
        if (problem != null) {
            return DataResult.error(() -> problem);
        }
        return DataResult.success(new GolemJob(carry, roles, defaultRole));
    }

    private static @Nullable String problem(
            GolemCarryKind carry, List<GolemRole> roles, GolemRole defaultRole) {
        if (roles.isEmpty()) {
            return "a golem job needs at least one role";
        }
        if (Set.copyOf(roles).size() != roles.size()) {
            return "a golem job's roles must be distinct: " + roles;
        }
        if (carry == GolemCarryKind.ESSENTIA
                && !Set.of(GolemRole.TAKE, GolemRole.GIVE).containsAll(roles)) {
            return "an essentia golem job may use only take and give: " + roles;
        }
        if (!roles.contains(defaultRole)) {
            return "a golem job's default role " + defaultRole + " is not one of " + roles;
        }
        return null;
    }
}
