package com.andrewbrogoff.vftv.neoforge.essentia;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.api.neoforge.essentia.EssentiaResource;
import com.andrewbrogoff.vftv.core.essentia.JarBlockEntity;
import com.andrewbrogoff.vftv.core.essentia.model.EssentiaMath;
import com.andrewbrogoff.vftv.core.essentia.model.EssentiaMath.Contents;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The jar's one explicit handler adapter (essentia spec, "Contents and the handler"): one slot,
 * arguments checked first, and our own {@link SnapshotJournal}, so the jar can read its busy state
 * and live snapshots. One instance per jar block entity.
 */
final class JarHandler implements ResourceHandler<EssentiaResource>, JarBlockEntity.Journal {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** A snapshot with identity, so equal contents at two depths stay two snapshots. */
    private static final class Snap {
        final Contents<ResourceKey<Aspect>> contents;

        Snap(Contents<ResourceKey<Aspect>> contents) {
            this.contents = contents;
        }
    }

    private final JarBlockEntity jar;
    private final List<Snap> live = new ArrayList<>();
    private final SnapshotJournal<Snap> journal =
            new SnapshotJournal<>() {
                @Override
                protected Snap createSnapshot() {
                    Snap s = new Snap(jar.contents());
                    live.add(s);
                    return s;
                }

                @Override
                protected void revertToSnapshot(Snap snapshot) {
                    jar.setLive(snapshot.contents);
                }

                @Override
                protected void releaseSnapshot(Snap snapshot) {
                    live.removeIf(s -> s == snapshot);
                    if (live.isEmpty()) {
                        jar.onRootClosed();
                    }
                }

                @Override
                protected void onRootCommit(Snap original) {
                    try {
                        jar.publish(original.contents);
                    } catch (RuntimeException e) {
                        // Publication is never part of correctness: the snapshot is still released.
                        LOGGER.error(
                                "An essentia jar's publication failed at {}", jar.getBlockPos(), e);
                    }
                }
            };

    JarHandler(JarBlockEntity jar) {
        this.jar = jar;
    }

    @Override
    public boolean busy() {
        return !live.isEmpty();
    }

    @Override
    public List<Contents<ResourceKey<Aspect>>> snapshots() {
        return live.stream().map(s -> s.contents).toList();
    }

    private Optional<Holder.Reference<Aspect>> holder(@Nullable ResourceKey<Aspect> key) {
        Level level = jar.getLevel();
        if (key == null || level == null) {
            return Optional.empty();
        }
        return level.registryAccess().lookupOrThrow(AspectKeys.REGISTRY).get(key);
    }

    private static @Nullable ResourceKey<Aspect> key(EssentiaResource resource) {
        Holder<Aspect> a = resource.aspect();
        return a == null ? null : a.unwrapKey().orElse(null);
    }

    private boolean accepts(EssentiaResource resource) {
        ResourceKey<Aspect> x = key(resource);
        return x != null && EssentiaMath.accepts(jar.label(), jar.held(), x);
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public EssentiaResource getResource(int index) {
        if (index != 0 || !jar.available()) {
            return EssentiaResource.EMPTY;
        }
        return holder(jar.held())
                .<EssentiaResource>map(EssentiaResource::new)
                .orElse(EssentiaResource.EMPTY);
    }

    @Override
    public long getAmountAsLong(int index) {
        return getResource(index).isEmpty() ? 0 : jar.contents().amount();
    }

    @Override
    public long getCapacityAsLong(int index, EssentiaResource resource) {
        return index == 0 && jar.available() && accepts(resource) ? JarBlockEntity.capacity() : 0;
    }

    @Override
    public boolean isValid(int index, EssentiaResource resource) {
        return index == 0 && accepts(resource);
    }

    @Override
    public int insert(
            int index, EssentiaResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        return index == 0 ? insertChecked(resource, amount, transaction) : 0;
    }

    @Override
    public int insert(EssentiaResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        return insertChecked(resource, amount, transaction);
    }

    private int insertChecked(
            EssentiaResource resource, int amount, TransactionContext transaction) {
        ResourceKey<Aspect> x = key(resource);
        if (x == null || !jar.available() || !accepts(resource)) {
            return 0;
        }
        int held = jar.contents().amount();
        int k = Math.min(amount, Math.max(0, JarBlockEntity.capacity() - held));
        if (k > 0) {
            journal.updateSnapshots(transaction);
            jar.setLive(new Contents<>(x, held + k));
        }
        return k;
    }

    @Override
    public int extract(
            int index, EssentiaResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        return index == 0 ? extractChecked(resource, amount, transaction) : 0;
    }

    @Override
    public int extract(EssentiaResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        return extractChecked(resource, amount, transaction);
    }

    private int extractChecked(
            EssentiaResource resource, int amount, TransactionContext transaction) {
        ResourceKey<Aspect> x = key(resource);
        Contents<ResourceKey<Aspect>> c = jar.contents();
        if (x == null || !jar.available() || !x.equals(c.aspect())) {
            return 0;
        }
        int k = Math.min(amount, c.amount());
        if (k > 0) {
            journal.updateSnapshots(transaction);
            int left = c.amount() - k;
            jar.setLive(left == 0 ? Contents.empty() : new Contents<>(x, left));
        }
        return k;
    }
}
