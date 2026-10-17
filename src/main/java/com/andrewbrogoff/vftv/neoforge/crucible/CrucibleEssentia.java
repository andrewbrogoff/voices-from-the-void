package com.andrewbrogoff.vftv.neoforge.crucible;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.api.neoforge.essentia.EssentiaResource;
import com.andrewbrogoff.vftv.core.crucible.CrucibleBlockEntity;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.WeakHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * The crucible's extract-only essentia handler (crucible spec, "Essentia out"): one slot per aspect
 * of {@code vftv:aspect} in ascending id, reading the pool; nothing can be inserted. A snapshot
 * journal over the pool reports the crucible transaction-busy from its first snapshot until the
 * root transaction closes, either way.
 */
final class CrucibleEssentia implements ResourceHandler<EssentiaResource> {
    private static final Map<CrucibleBlockEntity, CrucibleEssentia> HANDLERS = new WeakHashMap<>();
    private static @Nullable Slots slots;

    private final CrucibleBlockEntity crucible;
    private final Journal journal = new Journal();

    private CrucibleEssentia(CrucibleBlockEntity crucible) {
        this.crucible = crucible;
    }

    /** The handler of one block entity, bound to that instance for its life. */
    static CrucibleEssentia of(CrucibleBlockEntity crucible) {
        return HANDLERS.computeIfAbsent(crucible, CrucibleEssentia::new);
    }

    /** The aspect slots of a registry, sorted by id. */
    private record Slots(
            HolderLookup.@Nullable RegistryLookup<Aspect> registry,
            List<Holder.Reference<Aspect>> aspects,
            Map<ResourceKey<Aspect>, Integer> index) {}

    private Slots slots() {
        Level level = crucible.getLevel();
        if (level == null) {
            return new Slots(null, List.of(), Map.of());
        }
        HolderLookup.RegistryLookup<Aspect> registry =
                level.registryAccess().lookupOrThrow(AspectKeys.REGISTRY);
        Slots s = slots;
        if (s == null || !registry.equals(s.registry())) {
            List<Holder.Reference<Aspect>> sorted =
                    registry.listElements()
                            .sorted(Comparator.comparing(h -> h.key().identifier().toString()))
                            .toList();
            Map<ResourceKey<Aspect>, Integer> index = new HashMap<>();
            for (int i = 0; i < sorted.size(); i++) {
                index.put(sorted.get(i).key(), i);
            }
            s = new Slots(registry, sorted, Map.copyOf(index));
            slots = s;
        }
        return s;
    }

    @Override
    public int size() {
        return slots().aspects().size();
    }

    @Override
    public EssentiaResource getResource(int index) {
        List<Holder.Reference<Aspect>> aspects = slots().aspects();
        return index < 0 || index >= aspects.size()
                ? EssentiaResource.EMPTY
                : new EssentiaResource(aspects.get(index));
    }

    @Override
    public long getAmountAsLong(int index) {
        List<Holder.Reference<Aspect>> aspects = slots().aspects();
        if (index < 0 || index >= aspects.size() || crucible.unavailable()) {
            return 0;
        }
        return crucible.pool().amount(aspects.get(index).key().identifier().toString());
    }

    @Override
    public long getCapacityAsLong(int index, EssentiaResource resource) {
        return 0;
    }

    @Override
    public boolean isValid(int index, EssentiaResource resource) {
        return false;
    }

    @Override
    public int insert(int index, EssentiaResource resource, int amount, TransactionContext tx) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        return 0;
    }

    @Override
    public int insert(EssentiaResource resource, int amount, TransactionContext tx) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        return 0;
    }

    @Override
    public int extract(int index, EssentiaResource resource, int amount, TransactionContext tx) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        List<Holder.Reference<Aspect>> aspects = slots().aspects();
        if (amount == 0
                || index < 0
                || index >= aspects.size()
                || crucible.unavailable()
                || !resource.equals(new EssentiaResource(aspects.get(index)))) {
            return 0;
        }
        String id = aspects.get(index).key().identifier().toString();
        int n = Math.min(amount, crucible.pool().amount(id));
        if (n > 0) {
            journal.updateSnapshots(tx);
            crucible.pool().extract(id, n);
        }
        return n;
    }

    @Override
    public int extract(EssentiaResource resource, int amount, TransactionContext tx) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        Holder<Aspect> aspect = resource.aspect();
        Integer index =
                aspect == null
                        ? null
                        : aspect.unwrapKey().map(k -> slots().index().get(k)).orElse(null);
        return index == null ? 0 : extract(index, resource, amount, tx);
    }

    /** Snapshots the pool once per touched depth; tracks busy until the root closes. */
    private final class Journal extends SnapshotJournal<SortedMap<String, Integer>> {
        private int live;

        @Override
        protected SortedMap<String, Integer> createSnapshot() {
            if (live++ == 0) {
                crucible.setBusy(true);
            }
            return crucible.pool().snapshot();
        }

        @Override
        protected void revertToSnapshot(SortedMap<String, Integer> snapshot) {
            crucible.pool().restore(snapshot);
        }

        @Override
        protected void releaseSnapshot(SortedMap<String, Integer> snapshot) {
            if (--live == 0) {
                crucible.setBusy(false);
            }
        }

        @Override
        protected void onRootCommit(SortedMap<String, Integer> originalState) {
            if (!crucible.isRemoved()) {
                crucible.markDirty();
            }
        }
    }
}
