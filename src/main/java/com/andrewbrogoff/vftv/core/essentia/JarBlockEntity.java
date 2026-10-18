package com.andrewbrogoff.vftv.core.essentia;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.essentia.EssentiaSuction;
import com.andrewbrogoff.vftv.api.essentia.SpillCause;
import com.andrewbrogoff.vftv.core.common.RawRecordCodec.Retained;
import com.andrewbrogoff.vftv.core.essentia.EssentiaRecords.JarRecord;
import com.andrewbrogoff.vftv.core.essentia.EssentiaRecords.JarStack;
import com.andrewbrogoff.vftv.core.essentia.model.EssentiaMath;
import com.andrewbrogoff.vftv.core.essentia.model.EssentiaMath.Contents;
import com.andrewbrogoff.vftv.core.essentia.model.EssentiaTuning;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The Essentia Jar's block entity (essentia spec, "The Essentia Jar"): its record, its routing
 * ranks, the drop reservation and removal settlement, and the root-commit publication. The NeoForge
 * handler adapter and its journal read and write the live contents through {@link #setLive}.
 */
public final class JarBlockEntity extends BlockEntity implements EssentiaSuction, Labels.Target {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String RECORD = "vftv";
    private static final String ITEM_LABEL = "vftv_label";
    private static final String UNREADABLE = "unreadable";

    /** The handler adapter's journal, as the jar needs it: busy state and live snapshots. */
    public interface Journal {
        /** Whether a transfer root that touched this jar is still open. */
        boolean busy();

        /** Every live snapshot, at every depth. */
        List<Contents<ResourceKey<Aspect>>> snapshots();
    }

    private Retained<JarRecord> record = new Retained.Read<>(JarRecord.EMPTY);
    private @Nullable ResourceKey<Aspect> itemLabel;
    private @Nullable Journal journal;
    private @Nullable Contents<ResourceKey<Aspect>> reserved;
    private boolean pendingSettle;
    private boolean clientUnreadable;
    private boolean warned;
    private boolean settled;
    private long lastFill = -40;

    /** A jar at {@code pos}. */
    public JarBlockEntity(BlockPos pos, BlockState state) {
        super(EssentiaContent.jarType(), pos, state);
    }

    /** This jar's journal, created once by the adapter's factory. */
    public <J extends Journal> Journal journal(Supplier<J> factory) {
        Journal j = journal;
        if (j == null) {
            j = factory.get();
            journal = j;
        }
        return j;
    }

    /** Whether the record reads. */
    @Override
    public boolean readable() {
        return record instanceof Retained.Read<JarRecord> && !clientUnreadable;
    }

    /** Whether a transfer root touching this jar is open. */
    @Override
    public boolean busy() {
        Journal j = journal;
        return j != null && j.busy();
    }

    /** The live contents; empty when unreadable. */
    public Contents<ResourceKey<Aspect>> contents() {
        JarRecord r = EssentiaRecords.read(record);
        return r == null ? Contents.empty() : r.contents();
    }

    @Override
    public @Nullable ResourceKey<Aspect> label() {
        JarRecord r = EssentiaRecords.read(record);
        return r == null ? null : r.labelOrNull();
    }

    @Override
    public @Nullable ResourceKey<Aspect> held() {
        return contents().aspect();
    }

    @Override
    public boolean isJar() {
        return true;
    }

    @Override
    public void setLabel(@Nullable ResourceKey<Aspect> label) {
        record = new Retained.Read<>(JarRecord.of(contents(), label));
        changed();
    }

    /**
     * Whether the handler is available: in place, readable and not reserved for a drop (essentia
     * spec, "Contents and the handler").
     */
    public boolean available() {
        return !isRemoved()
                && readable()
                && reserved == null
                && !settled
                && level != null
                && level.getBlockEntity(worldPosition) == this;
    }

    /** The capacity in force. */
    public static int capacity() {
        return EssentiaService.rules().tuning().jarCapacity();
    }

    /** Sets the live contents inside a transaction, or on a journal revert; no side effects. */
    public void setLive(Contents<ResourceKey<Aspect>> contents) {
        record = new Retained.Read<>(JarRecord.of(contents, label()));
    }

    @Override
    public int suction(ResourceKey<Aspect> aspect) {
        if (!available() || !EssentiaService.labelAdmits(label())) {
            return 0;
        }
        EssentiaTuning t = EssentiaService.rules().tuning();
        return EssentiaMath.jarSuction(
                label(),
                contents(),
                t.jarCapacity(),
                aspect,
                t.suctionJar(),
                t.suctionJarLabelled());
    }

    @Override
    public int hold(ResourceKey<Aspect> aspect) {
        if (!available()) {
            return 0;
        }
        EssentiaTuning t = EssentiaService.rules().tuning();
        return EssentiaMath.jarHold(
                label(), contents(), aspect, t.suctionJar(), t.suctionJarLabelled());
    }

    // --- removal ----------------------------------------------------------------------------

    /**
     * Reserves the carry before a drop (essentia spec, "Removal and drops"); idempotent. From then
     * on the handler is unavailable, and the drop copies the carry, never the live amount.
     */
    public void reserveForDrop() {
        if (reserved != null || !readable()) {
            return;
        }
        Journal j = journal;
        reserved = EssentiaMath.carry(contents(), j == null ? List.of() : j.snapshots());
        if (level instanceof ServerLevel server) {
            EssentiaService.lines(server).releaseAtTickEnd(this);
        }
    }

    /** Releases a reservation that survived its tick: the jar is still in place. */
    void releaseReservation() {
        if (!isRemoved() && level != null && level.getBlockEntity(worldPosition) == this) {
            reserved = null;
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (busy()) {
            pendingSettle = true;
        } else {
            settle();
        }
    }

    /** The journal's root closed, on commit or abort: runs any pending settlement. */
    public void onRootClosed() {
        if (pendingSettle) {
            pendingSettle = false;
            settle();
        }
    }

    private void settle() {
        Contents<ResourceKey<Aspect>> carry = reserved == null ? Contents.empty() : reserved;
        reserved = null;
        // Claimed before the post: a listener can't extract what is being spilled.
        settled = true;
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        if (!readable()) {
            if (carry.isEmpty()) {
                LOGGER.warn(
                        "An unreadable essentia jar at {} was removed: {}", worldPosition, raw());
            }
            return;
        }
        Contents<ResourceKey<Aspect>> spill = EssentiaMath.spill(contents(), carry);
        ResourceKey<Aspect> spilled = spill.aspect();
        if (spilled != null) {
            EssentiaService.spill(
                    server, worldPosition, spilled, spill.amount(), SpillCause.REMOVED);
        }
    }

    /**
     * The root-commit publication: marks the jar changed and dirty, wakes lines touching it, and
     * plays the fill sound if the amount rose. Skipped for a removed jar.
     */
    public void publish(Contents<ResourceKey<Aspect>> before) {
        if (isRemoved() || !(level instanceof ServerLevel server)) {
            return;
        }
        changed();
        EssentiaService.lines(server).wakeAt(worldPosition);
        Contents<ResourceKey<Aspect>> now = contents();
        boolean rose =
                now.amount() > before.amount()
                        && (before.isEmpty()
                                || java.util.Objects.equals(before.aspect(), now.aspect()));
        long time = server.getGameTime();
        if (rose && time - lastFill >= 40) {
            lastFill = time;
            server.playSound(
                    null, worldPosition, EssentiaContent.jarFill(), SoundSource.BLOCKS, 1.0f, 1.0f);
        }
    }

    private void changed() {
        setChanged();
        if (level instanceof ServerLevel server) {
            EssentiaService.markDirty(server, this);
        }
    }

    // --- persistence and components -----------------------------------------------------------

    private Tag raw() {
        return record instanceof Retained.Unreadable<JarRecord> u ? u.raw() : new CompoundTag();
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        clientUnreadable = input.getBooleanOr(UNREADABLE, false);
        record =
                input.read(RECORD, JarRecord.RETAINED).orElse(new Retained.Read<>(JarRecord.EMPTY));
        itemLabel = input.read(ITEM_LABEL, EssentiaRecords.ASPECT).orElse(null);
        // A separate item label is kept only for a jar placed from an unreadable item: the raw
        // item contents beside it stay unreadable even where they would parse as a jar record.
        if (itemLabel != null && record instanceof Retained.Read<JarRecord> r) {
            record =
                    new Retained.Unreadable<>(
                            JarRecord.BOUNDED.encodeStart(NbtOps.INSTANCE, r.value()).getOrThrow(),
                            "placed from an unreadable item");
        }
        warnIfUnreadable();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store(RECORD, JarRecord.RETAINED, record);
        output.storeNullable(ITEM_LABEL, EssentiaRecords.ASPECT, itemLabel);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (readable()) {
            tag.store(RECORD, JarRecord.RETAINED, record);
        } else {
            tag.putBoolean(UNREADABLE, true);
        }
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        boolean wasReadable = readable();
        Retained<JarStack> stack = components.get(EssentiaContent.contents());
        ResourceKey<Aspect> label = components.get(EssentiaContent.labelComponent());
        if (stack instanceof Retained.Unreadable<JarStack> u) {
            record = new Retained.Unreadable<>(u.raw(), u.reason());
            itemLabel = label;
        } else {
            JarStack s = stack instanceof Retained.Read<JarStack> r ? r.value() : null;
            JarRecord candidate =
                    JarRecord.of(
                            s == null ? Contents.empty() : new Contents<>(s.aspect(), s.amount()),
                            label);
            String error = candidate.check().error().map(e -> e.message()).orElse(null);
            if (error == null) {
                record = new Retained.Read<>(candidate);
                itemLabel = null;
            } else {
                Tag raw =
                        JarStack.RETAINED
                                .encodeStart(
                                        NbtOps.INSTANCE, java.util.Objects.requireNonNull(stack))
                                .result()
                                .orElse(new CompoundTag());
                record = new Retained.Unreadable<>(raw, error);
                itemLabel = label;
            }
        }
        warnIfUnreadable();
        if (wasReadable != readable() && level instanceof ServerLevel server) {
            EssentiaService.seam().invalidateCapabilities(server, worldPosition);
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        if (record instanceof Retained.Unreadable<JarRecord> u) {
            components.set(
                    EssentiaContent.contents(), new Retained.Unreadable<>(u.raw(), u.reason()));
            if (itemLabel != null) {
                components.set(EssentiaContent.labelComponent(), itemLabel);
            }
            return;
        }
        Contents<ResourceKey<Aspect>> c = reserved != null ? reserved : contents();
        ResourceKey<Aspect> aspect = c.aspect();
        if (aspect != null) {
            components.set(
                    EssentiaContent.contents(),
                    new Retained.Read<>(new JarStack(EssentiaRecords.FORMAT, aspect, c.amount())));
        }
        ResourceKey<Aspect> label = label();
        if (label != null) {
            components.set(EssentiaContent.labelComponent(), label);
        }
    }

    private void warnIfUnreadable() {
        if (!warned && record instanceof Retained.Unreadable<JarRecord> u) {
            warned = true;
            LOGGER.warn(
                    "Unreadable essentia jar at {} (format {}): {}",
                    worldPosition,
                    u.raw() instanceof CompoundTag t ? t.get("format") : "?",
                    u.reason());
        }
    }
}
