package com.andrewbrogoff.vftv.neoforge.common;

import com.andrewbrogoff.vftv.core.common.DataSlot;
import com.andrewbrogoff.vftv.core.common.DataSlots;
import com.andrewbrogoff.vftv.core.common.Registrar;
import com.mojang.serialization.Codec;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.neoforged.neoforge.attachment.AttachmentSyncHandler;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jspecify.annotations.Nullable;

/**
 * The persistent-state seam on NeoForge: each slot is an {@link AttachmentType}, saved under the
 * field {@code data}. {@code setData} marks the holder dirty and syncs it.
 */
public final class NeoForgeDataSlots implements DataSlots {
    private final Registrar registrar;

    /** Slots registered through {@code registrar}. */
    public NeoForgeDataSlots(Registrar registrar) {
        this.registrar = registrar;
    }

    @Override
    public <T> DataSlot<Player, T> player(
            String name,
            Codec<T> codec,
            Supplier<T> defaultValue,
            @Nullable StreamCodec<? super RegistryFriendlyByteBuf, T> sync) {
        AttachmentType.Builder<T> builder =
                AttachmentType.builder(defaultValue).serialize(codec.fieldOf("data")).copyOnDeath();
        if (sync != null) {
            builder.sync((holder, to) -> holder.equals(to), sync);
        }
        return new Slot<>(
                registrar.register(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, name, builder::build));
    }

    /**
     * NeoForge's initial sync (join, and every player that starts tracking the holder) never asks
     * {@code sendToPlayer}, so it would hand the record to watchers. This slot writes nothing then,
     * and instead sends the owner a baseline through the filtered update path when they log in,
     * respawn or change dimension.
     */
    @Override
    public <T> DataSlot<Player, T> playerWithDeltas(
            String name, Codec<T> codec, Supplier<T> defaultValue, DeltaSync<T> sync) {
        OwnerSync<T> handler = new OwnerSync<>(sync);
        AttachmentType.Builder<T> builder =
                AttachmentType.builder(defaultValue)
                        .serialize(codec.fieldOf("data"))
                        .copyOnDeath()
                        .sync(handler);
        Supplier<AttachmentType<?>> type =
                registrar.register(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, name, builder::build);
        Consumer<PlayerEvent> baseline =
                event -> {
                    if (event.getEntity() instanceof ServerPlayer player) {
                        handler.baseline = true;
                        try {
                            player.syncData(type);
                        } finally {
                            handler.baseline = false;
                        }
                    }
                };
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, baseline::accept);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerRespawnEvent.class, baseline::accept);
        NeoForge.EVENT_BUS.addListener(
                PlayerEvent.PlayerChangedDimensionEvent.class, baseline::accept);
        return new Slot<>(type);
    }

    /** The owner-only handler: no initial sync, a forced baseline on demand, else the delta. */
    private static final class OwnerSync<T> implements AttachmentSyncHandler<T> {
        private final DeltaSync<T> sync;
        private boolean baseline; // server thread only

        OwnerSync(DeltaSync<T> sync) {
            this.sync = sync;
        }

        @Override
        public boolean sendToPlayer(IAttachmentHolder holder, ServerPlayer to) {
            return holder.equals(to);
        }

        @Override
        public void write(RegistryFriendlyByteBuf buf, T attachment, boolean initialSync) {
            if (!initialSync) {
                sync.write(buf, attachment, baseline);
            }
        }

        @Override
        public T read(IAttachmentHolder holder, RegistryFriendlyByteBuf buf, @Nullable T previous) {
            return sync.read(buf, previous);
        }
    }

    @Override
    public <T> DataSlot<ChunkAccess, T> chunk(
            String name,
            Codec<T> codec,
            Supplier<T> defaultValue,
            @Nullable StreamCodec<? super RegistryFriendlyByteBuf, T> sync) {
        AttachmentType.Builder<T> builder =
                AttachmentType.builder(defaultValue).serialize(codec.fieldOf("data"));
        if (sync != null) {
            builder.sync(sync);
        }
        return new Slot<>(
                registrar.register(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, name, builder::build));
    }

    private record Slot<H extends IAttachmentHolder, T>(Supplier<AttachmentType<?>> registered)
            implements DataSlot<H, T> {
        @Override
        public T get(H holder) {
            return holder.getData(type());
        }

        @Override
        public void set(H holder, T value) {
            holder.setData(type(), value);
        }

        private AttachmentType<T> type() {
            // Registered from an AttachmentType<T> builder above.
            @SuppressWarnings("unchecked")
            AttachmentType<T> type = (AttachmentType<T>) registered.get();
            return type;
        }
    }
}
