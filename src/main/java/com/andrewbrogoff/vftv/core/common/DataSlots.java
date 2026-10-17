package com.andrewbrogoff.vftv.core.common;

import com.mojang.serialization.Codec;
import java.util.function.Supplier;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.jspecify.annotations.Nullable;

/**
 * The persistent-state seam (architecture, "Seams"): creates player and chunk slots, backed by the
 * platform's attachments. Level-wide state is a {@link SavedDataSlot}, which is vanilla.
 *
 * <p>Create slots during mod construction. Wrap a record's codec in {@link RawRecordCodec#wrap} to
 * keep unreadable records raw.
 */
public interface DataSlots {
    /**
     * A player slot, kept through death and synced to the owning player only when {@code sync} is
     * non-null.
     */
    <T> DataSlot<Player, T> player(
            String name,
            Codec<T> codec,
            Supplier<T> defaultValue,
            @Nullable StreamCodec<? super RegistryFriendlyByteBuf, T> sync);

    /**
     * A player slot, kept through death and synced to the owning player only as a full baseline on
     * join and then as deltas (architecture, "State and sync": large or growing collections).
     */
    <T> DataSlot<Player, T> playerWithDeltas(
            String name, Codec<T> codec, Supplier<T> defaultValue, DeltaSync<T> sync);

    /**
     * How a growing player slot reaches its owner: each value carries what changed since the one it
     * replaced, so a write needs no previous value.
     */
    interface DeltaSync<T> {
        /**
         * Writes {@code value}: in full when {@code baseline}, else only its change. Writing
         * nothing sends nothing.
         */
        void write(RegistryFriendlyByteBuf buf, T value, boolean baseline);

        /** Reads on the client: a baseline, or a change applied to {@code previous}. */
        T read(RegistryFriendlyByteBuf buf, @Nullable T previous);
    }

    /** A chunk slot, synced to every player tracking the chunk when {@code sync} is non-null. */
    <T> DataSlot<ChunkAccess, T> chunk(
            String name,
            Codec<T> codec,
            Supplier<T> defaultValue,
            @Nullable StreamCodec<? super RegistryFriendlyByteBuf, T> sync);
}
