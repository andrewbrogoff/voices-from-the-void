package com.andrewbrogoff.vftv.core.vis;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.vis.WandCap;
import com.andrewbrogoff.vftv.api.vis.WandRod;
import com.andrewbrogoff.vftv.core.common.RawRecordCodec.Retained;
import com.andrewbrogoff.vftv.core.vis.model.Pools;
import com.mojang.logging.LogUtils;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

/**
 * What a wand stack is, read against the loaded parts (wands spec, "Vis storage", "Inert wands"):
 * unfinished, missing a part, holding an unreadable {@code vftv:vis}, or ready. Only a ready wand
 * taps, is charged or spends; nothing writes an inert wand's {@code vftv:vis}.
 */
public sealed interface WandState {
    /** Whether the wand is inert. */
    default boolean inert() {
        return !(this instanceof Ready);
    }

    /** A rod or cap component is missing (e.g. from {@code /give}). */
    record Unfinished() implements WandState {}

    /**
     * A component names a key the registry doesn't hold.
     *
     * @param rod whether the missing part is the rod (else the cap)
     * @param id the id it names
     */
    record MissingPart(boolean rod, Identifier id) implements WandState {}

    /**
     * {@code vftv:vis} is unreadable.
     *
     * @param reason why
     */
    record Unreadable(String reason) implements WandState {}

    /**
     * A finished wand whose parts are present.
     *
     * @param rodKey the rod's key
     * @param rod the rod
     * @param capKey the cap's key
     * @param cap the cap
     * @param stored the stored pools, unclamped
     */
    record Ready(
            ResourceKey<WandRod> rodKey,
            WandRodEntry rod,
            ResourceKey<WandCap> capKey,
            WandCapEntry cap,
            VisPools stored)
            implements WandState {
        /** One pool as read: clamped to the capacity. */
        public int pool(ResourceKey<Aspect> aspect) {
            return Pools.read(stored.pools(), aspect.identifier().toString(), rod.capacity());
        }

        /** Every vis aspect's pool as read, by id text. */
        public SortedMap<String, Integer> pools(WandParts parts) {
            SortedMap<String, Integer> out = new TreeMap<>();
            parts.visAspects().forEach(a -> out.put(a.identifier().toString(), pool(a)));
            return out;
        }
    }

    /** Reads a wand stack. */
    static WandState of(ItemStack stack, WandParts parts) {
        ResourceKey<WandRod> rodKey = stack.get(VisContent.rod());
        ResourceKey<WandCap> capKey = stack.get(VisContent.cap());
        if (rodKey == null || capKey == null) {
            return new Unfinished();
        }
        WandRodEntry rod = parts.rods().get(rodKey);
        if (rod == null) {
            return new MissingPart(true, rodKey.identifier());
        }
        WandCapEntry cap = parts.caps().get(capKey);
        if (cap == null) {
            return new MissingPart(false, capKey.identifier());
        }
        Retained<VisPools> vis = stack.get(VisContent.vis());
        return switch (vis) {
            case null -> new Ready(rodKey, rod, capKey, cap, VisPools.EMPTY);
            case Retained.Read<VisPools> r -> new Ready(rodKey, rod, capKey, cap, r.value());
            case Retained.Unreadable<VisPools> u -> new Unreadable(u.reason());
        };
    }

    /**
     * Writes a ready wand's pools in place: the changed ones, the rest clamped, ignored entries
     * carried through. The stack object stays the same.
     */
    static void write(ItemStack stack, Ready wand, Map<String, Integer> changed, WandParts parts) {
        Pools.Written written =
                Pools.write(
                        wand.stored().pools(),
                        parts.visAspectIds(),
                        changed,
                        wand.rod().capacity());
        if (!written.dropped().isEmpty()) {
            Holder.LOGGER.warn(
                    "A wand's vis held more than 16 entries; dropped {}", written.dropped());
        }
        stack.set(VisContent.vis(), new Retained.Read<>(new VisPools(written.pools())));
    }

    /** The logger, in a holder because interfaces have no private static fields. */
    final class Holder {
        static final Logger LOGGER = LogUtils.getLogger();

        private Holder() {}
    }
}
