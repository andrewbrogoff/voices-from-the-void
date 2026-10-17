package com.andrewbrogoff.vftv.core.vis;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The global node registry, the {@code vftv_nodes} SavedData in the overworld's storage, format 1
 * (wands spec, "Identity and reconciliation"). Mutable, on the server thread only; every change
 * goes through {@link NodeService}, which marks it dirty.
 */
public final class NodeLedger {
    /** The only format this build reads and writes. */
    public static final int FORMAT = 1;

    /** Where a node is. */
    public enum State implements StringRepresentable {
        /** In a block, at a dimension and position. */
        PLACED,
        /** In a Bell Jar, under a capture token. */
        CARRIED,
        /** Tombstoned. */
        REMOVED;

        static final Codec<State> CODEC = StringRepresentable.fromEnum(State::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /**
     * One id's entry.
     *
     * @param id the id, 1 or more
     * @param origin how it was born
     * @param state where it is
     * @param dimension its dimension while placed
     * @param pos its position while placed
     * @param token its capture token while carried
     */
    public record Entry(
            long id,
            NodeState.Origin origin,
            State state,
            Optional<ResourceKey<Level>> dimension,
            Optional<BlockPos> pos,
            long token) {
        static final Codec<Entry> CODEC =
                VisCodecs.strict(
                        RecordCodecBuilder.<Entry>create(
                                        i ->
                                                i.group(
                                                                Codec.LONG
                                                                        .fieldOf("id")
                                                                        .forGetter(Entry::id),
                                                                NodeState.Origin.CODEC
                                                                        .fieldOf("origin")
                                                                        .forGetter(Entry::origin),
                                                                State.CODEC
                                                                        .fieldOf("state")
                                                                        .forGetter(Entry::state),
                                                                ResourceKey.codec(
                                                                                Registries
                                                                                        .DIMENSION)
                                                                        .optionalFieldOf(
                                                                                "dimension")
                                                                        .forGetter(
                                                                                Entry::dimension),
                                                                BlockPos.CODEC
                                                                        .optionalFieldOf("pos")
                                                                        .forGetter(Entry::pos),
                                                                Codec.LONG
                                                                        .optionalFieldOf(
                                                                                "token", 0L)
                                                                        .forGetter(Entry::token))
                                                        .apply(i, Entry::new))
                                .validate(Entry::check),
                        Set.of("id", "origin", "state", "dimension", "pos", "token"));

        /** A placed entry. */
        public static Entry placed(
                long id, NodeState.Origin origin, ResourceKey<Level> dimension, BlockPos pos) {
            return new Entry(
                    id,
                    origin,
                    State.PLACED,
                    Optional.of(dimension),
                    Optional.of(pos.immutable()),
                    0);
        }

        /** This entry carried under {@code token}. */
        public Entry carried(long next) {
            return new Entry(id, origin, State.CARRIED, Optional.empty(), Optional.empty(), next);
        }

        /** This entry tombstoned. */
        public Entry removed() {
            return new Entry(id, origin, State.REMOVED, Optional.empty(), Optional.empty(), 0);
        }

        /** Whether this entry is placed at exactly this dimension and position. */
        public boolean placedAt(ResourceKey<Level> where, BlockPos at) {
            return state == State.PLACED
                    && dimension.filter(where::equals).isPresent()
                    && pos.filter(at::equals).isPresent();
        }

        private static DataResult<Entry> check(Entry e) {
            boolean placed = e.state == State.PLACED;
            if (e.id < 1 || e.token < 0) {
                return DataResult.error(() -> "id below 1 or negative token");
            }
            if (placed != e.dimension.isPresent() || placed != e.pos.isPresent()) {
                return DataResult.error(() -> "a placed entry needs a dimension and position");
            }
            return DataResult.success(e);
        }
    }

    /**
     * The starter's record.
     *
     * @param id its id
     * @param bootstrap the overworld respawn position at its placement
     */
    public record Starter(long id, BlockPos bootstrap) {
        static final Codec<Starter> CODEC =
                RecordCodecBuilder.create(
                        i ->
                                i.group(
                                                Codec.LONG.fieldOf("id").forGetter(Starter::id),
                                                BlockPos.CODEC
                                                        .fieldOf("bootstrap")
                                                        .forGetter(Starter::bootstrap))
                                        .apply(i, Starter::new));
    }

    /** The bounded codec. */
    public static final Codec<NodeLedger> BOUNDED =
            VisCodecs.strict(
                    RecordCodecBuilder.<NodeLedger>create(
                                    i ->
                                            i.group(
                                                            Codec.INT
                                                                    .fieldOf("format")
                                                                    .forGetter(l -> FORMAT),
                                                            Codec.LONG
                                                                    .fieldOf("next_id")
                                                                    .forGetter(l -> l.nextId),
                                                            Codec.LONG
                                                                    .fieldOf("next_token")
                                                                    .forGetter(l -> l.nextToken),
                                                            Starter.CODEC
                                                                    .optionalFieldOf("starter")
                                                                    .forGetter(
                                                                            l ->
                                                                                    Optional
                                                                                            .ofNullable(
                                                                                                    l.starter)),
                                                            Entry.CODEC
                                                                    .listOf()
                                                                    .fieldOf("nodes")
                                                                    .forGetter(
                                                                            l ->
                                                                                    List.copyOf(
                                                                                            l.nodes
                                                                                                    .values())))
                                                    .apply(i, NodeLedger::decoded))
                            .validate(NodeLedger::check),
                    Set.of("format", "next_id", "next_token", "starter", "nodes"));

    private long nextId = 1;
    private long nextToken = 1;
    private @Nullable Starter starter;
    private final TreeMap<Long, Entry> nodes = new TreeMap<>();

    /** An empty registry. */
    public NodeLedger() {}

    private static NodeLedger decoded(
            int format, long nextId, long nextToken, Optional<Starter> starter, List<Entry> nodes) {
        if (format != FORMAT) {
            throw new IllegalArgumentException("unknown format " + format);
        }
        NodeLedger l = new NodeLedger();
        l.starter = starter.orElse(null);
        for (Entry e : nodes) {
            if (l.nodes.put(e.id(), e) != null) {
                throw new IllegalArgumentException("node " + e.id() + " twice");
            }
        }
        // Loading repairs the counters past every id and token held.
        long maxId = l.nodes.isEmpty() ? 0 : l.nodes.lastKey();
        long maxToken = nodes.stream().mapToLong(Entry::token).max().orElse(0);
        l.nextId = Math.max(nextId, maxId + 1);
        l.nextToken = Math.max(nextToken, maxToken + 1);
        return l;
    }

    private static DataResult<NodeLedger> check(NodeLedger l) {
        return l.nextId < 1 || l.nextToken < 1
                ? DataResult.error(() -> "counters below 1")
                : DataResult.success(l);
    }

    /** The entry for an id, or null. */
    public @Nullable Entry get(long id) {
        return nodes.get(id);
    }

    /** Every entry, ascending id. */
    public Iterable<Entry> entries() {
        return nodes.values();
    }

    /** Stores an entry. */
    void put(Entry entry) {
        nodes.put(entry.id(), entry);
    }

    /** Takes the next id. */
    long mint() {
        return nextId++;
    }

    /** Takes the next capture token. */
    long token() {
        return nextToken++;
    }

    /** Raises {@code next_id} above {@code id}. */
    void advancePast(long id) {
        nextId = Math.max(nextId, id + 1);
    }

    /** Raises {@code next_token} above {@code token}. */
    void advanceTokenPast(long token) {
        nextToken = Math.max(nextToken, token + 1);
    }

    /** The next id that would be minted. */
    public long nextId() {
        return nextId;
    }

    /** The next capture token. */
    public long nextToken() {
        return nextToken;
    }

    /** The starter's record, or null. */
    public @Nullable Starter starter() {
        return starter;
    }

    void starter(Starter next) {
        starter = next;
    }

    /** Whether the starter is absent or its id is {@code REMOVED}. */
    public boolean starterMissing() {
        Starter s = starter;
        if (s == null) {
            return true;
        }
        Entry e = nodes.get(s.id());
        return e == null || e.state() == State.REMOVED;
    }

    /** Whether the starter's entry is {@code PLACED}. */
    public boolean starterPlaced() {
        Starter s = starter;
        Entry e = s == null ? null : nodes.get(s.id());
        return e != null && e.state() == State.PLACED;
    }
}
