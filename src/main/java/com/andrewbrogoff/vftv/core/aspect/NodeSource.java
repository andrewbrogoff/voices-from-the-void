package com.andrewbrogoff.vftv.core.aspect;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import java.util.List;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The nodes as the fork reads them, owned here and registered by the vis feature at init (build
 * plan, flipped arrows), so {@code core.aspect} never imports {@code core.vis}. Until then {@link
 * #NONE} answers: no node anywhere, and no missing starter to report.
 */
public interface NodeSource {
    /** No nodes. */
    NodeSource NONE =
            new NodeSource() {
                @Override
                public @Nullable Readout readout(ServerLevel level, BlockPos pos) {
                    return null;
                }

                @Override
                public @Nullable Nearest nearest(ServerLevel level, Vec3 point, int radius) {
                    return null;
                }

                @Override
                public boolean starterMissing(MinecraftServer server) {
                    return false;
                }
            };

    /**
     * The node at a position, settled (wands spec, {@code NodeService.readout}): loaded nodes only.
     *
     * @return the readout, or null when no node is there
     */
    @Nullable Readout readout(ServerLevel level, BlockPos pos);

    /**
     * The nearest node within {@code radius} (inclusive) of {@code point} in the level's dimension,
     * by 3-D distance to its centre, ties to the lowest id (wands spec, "The index"). Reads memory
     * only: it loads and generates no chunk.
     *
     * @return the node, or null when none is in range
     */
    @Nullable Nearest nearest(ServerLevel level, Vec3 point, int radius);

    /**
     * Whether the world has no starter node ({@code starter} absent or {@code REMOVED}) while the
     * starter isn't switched off, so an empty sounding names the missing Wellspring.
     */
    boolean starterMissing(MinecraftServer server);

    /**
     * A node's readout.
     *
     * @param id the node's stable id, which survives moves
     * @param type the localised type name ("Unknown" for an inert node)
     * @param clarity the localised clarity (brightness)
     * @param aspects each aspect's current and max, in ascending aspect id
     * @param rewardDivisor the node type's own reward divisor, or empty for the tuning default
     */
    record Readout(
            long id,
            Component type,
            Component clarity,
            List<NodeAspect> aspects,
            OptionalInt rewardDivisor) {
        /** Copies the list. */
        public Readout {
            aspects = List.copyOf(aspects);
        }
    }

    /**
     * One aspect of a node.
     *
     * @param aspect the aspect
     * @param current its current amount
     * @param max its capacity
     */
    record NodeAspect(ResourceKey<Aspect> aspect, int current, int max) {}

    /**
     * The nearest node: server-side only; its position never reaches a client.
     *
     * @param id its stable id
     * @param centre its centre
     */
    record Nearest(long id, Vec3 centre) {}
}
