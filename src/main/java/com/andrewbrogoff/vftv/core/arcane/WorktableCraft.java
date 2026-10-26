package com.andrewbrogoff.vftv.core.arcane;

import com.andrewbrogoff.vftv.api.arcane.ArcaneLimits;
import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.api.vis.VisQuote;
import io.netty.handler.codec.DecoderException;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * {@code WorktableCraft(containerId, revision, recipeId, echo, batch)} (arcane spec, "The craft
 * payload"): the one C2S payload that crafts, sent by the preview widget. Every size is read and
 * checked before anything is allocated; out-of-range or malformed input is rejected at decode.
 *
 * @param containerId the open Worktable menu's id
 * @param revision the 32-bit preview revision the client holds
 * @param recipeId the previewed recipe
 * @param echo the previewed quote's cost
 * @param batch whether to repeat the same recipe up to {@value ArcaneLimits#MAX_BATCH} times
 */
public record WorktableCraft(
        int containerId, int revision, Identifier recipeId, Echo echo, boolean batch)
        implements CustomPacketPayload {
    /** {@code vftv:worktable_craft}. */
    public static final Type<WorktableCraft> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("vftv", "worktable_craft"));

    /** The bounded codec. */
    public static final StreamCodec<RegistryFriendlyByteBuf, WorktableCraft> STREAM_CODEC =
            StreamCodec.of(WorktableCraft::write, WorktableCraft::read);

    /**
     * The previewed quote's cost fields, which echo equality compares.
     *
     * @param base the base cost, 1-8 entries of 1-1000
     * @param effective the effective cost, the same keys, each 1-10,000
     * @param m the multiplier, per mille, 0-10,000
     * @param spill the spill, 0-1,000,000
     * @param discountCapped the quote's flag
     * @param clamped the quote's flag
     * @param exceedsCapacity the quote's flag
     */
    public record Echo(
            SortedMap<ResourceKey<Aspect>, Integer> base,
            SortedMap<ResourceKey<Aspect>, Integer> effective,
            int m,
            int spill,
            boolean discountCapped,
            boolean clamped,
            boolean exceedsCapacity) {
        /** Copies the maps. */
        public Echo {
            base = new TreeMap<>(base);
            effective = new TreeMap<>(effective);
        }

        /** The echo of a quote. */
        public static Echo of(VisQuote quote) {
            return new Echo(
                    new TreeMap<>(quote.base()),
                    new TreeMap<>(quote.effective()),
                    quote.m(),
                    quote.spill(),
                    quote.discountCapped(),
                    quote.clamped(),
                    quote.exceedsCapacity());
        }

        /**
         * A quote carrying exactly the echoed cost fields, for the spend's echo equality; its
         * factors and the other flags are not compared.
         */
        public VisQuote quote() {
            return new VisQuote(
                    base,
                    effective,
                    m,
                    spill,
                    0,
                    0,
                    0,
                    discountCapped,
                    clamped,
                    exceedsCapacity,
                    false,
                    true,
                    List.of());
        }
    }

    @Override
    public Type<WorktableCraft> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buf, WorktableCraft p) {
        buf.writeVarInt(p.containerId);
        buf.writeInt(p.revision);
        buf.writeUtf(p.recipeId.toString(), ArcaneLimits.MAX_ID_CHARS);
        writeCost(buf, p.echo.base);
        writeCost(buf, p.echo.effective);
        buf.writeVarInt(p.echo.m);
        buf.writeVarInt(p.echo.spill);
        buf.writeBoolean(p.echo.discountCapped);
        buf.writeBoolean(p.echo.clamped);
        buf.writeBoolean(p.echo.exceedsCapacity);
        buf.writeBoolean(p.batch);
    }

    private static void writeCost(
            RegistryFriendlyByteBuf buf, Map<ResourceKey<Aspect>, Integer> m) {
        buf.writeVarInt(m.size());
        m.forEach(
                (aspect, n) -> {
                    buf.writeUtf(aspect.identifier().toString(), ArcaneLimits.MAX_ID_CHARS);
                    buf.writeVarInt(n);
                });
    }

    /** Reads a payload: the effective cost must have exactly the base's keys. */
    private static WorktableCraft read(RegistryFriendlyByteBuf buf) {
        int containerId = buf.readVarInt();
        if (containerId < 0) {
            throw new DecoderException("container id " + containerId);
        }
        int revision = buf.readInt();
        Identifier recipe = id(buf);
        SortedMap<ResourceKey<Aspect>, Integer> base =
                readCost(buf, ArcaneLimits.MIN_BASE_COST, ArcaneLimits.MAX_BASE_COST, "base");
        SortedMap<ResourceKey<Aspect>, Integer> effective =
                readCost(buf, 1, ArcaneLimits.MAX_ECHO_EFFECTIVE, "effective");
        if (!effective.keySet().equals(base.keySet())) {
            throw new DecoderException("the effective cost's aspects differ from the base's");
        }
        int m = bounded(buf.readVarInt(), 0, ArcaneLimits.MAX_ECHO_MULTIPLIER, "multiplier");
        int spill = bounded(buf.readVarInt(), 0, ArcaneLimits.MAX_ECHO_SPILL, "spill");
        Echo echo =
                new Echo(
                        base,
                        effective,
                        m,
                        spill,
                        buf.readBoolean(),
                        buf.readBoolean(),
                        buf.readBoolean());
        return new WorktableCraft(containerId, revision, recipe, echo, buf.readBoolean());
    }

    private static SortedMap<ResourceKey<Aspect>, Integer> readCost(
            RegistryFriendlyByteBuf buf, int min, int max, String what) {
        int entries =
                bounded(buf.readVarInt(), 1, ArcaneLimits.MAX_COST_ENTRIES, what + " entries");
        SortedMap<ResourceKey<Aspect>, Integer> cost = new TreeMap<>();
        for (int i = 0; i < entries; i++) {
            ResourceKey<Aspect> aspect = ResourceKey.create(AspectKeys.REGISTRY, id(buf));
            if (cost.put(aspect, bounded(buf.readVarInt(), min, max, what + " cost")) != null) {
                throw new DecoderException("duplicate " + what + " aspect " + aspect.identifier());
            }
        }
        return cost;
    }

    private static Identifier id(RegistryFriendlyByteBuf buf) {
        String text = buf.readUtf(ArcaneLimits.MAX_ID_CHARS);
        Identifier id = Identifier.tryParse(text);
        if (id == null) {
            throw new DecoderException("not an id: " + text);
        }
        return id;
    }

    private static int bounded(int value, int min, int max, String what) {
        if (value < min || value > max) {
            throw new DecoderException(what + " " + value + " outside " + min + ".." + max);
        }
        return value;
    }
}
