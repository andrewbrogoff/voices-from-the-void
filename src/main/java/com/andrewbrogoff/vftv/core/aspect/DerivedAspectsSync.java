package com.andrewbrogoff.vftv.core.aspect;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.api.aspect.AspectLimits;
import com.andrewbrogoff.vftv.api.aspect.AspectValues;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

/**
 * S2C: one slice of the {@code DerivedAspects} table (aspects spec, "Sync and state"), sent to
 * every player on join and after every {@code /reload}. Entries come in ascending item id, at most
 * {@value #MAX} per payload; the client replaces its copy when the slice flagged last arrives.
 *
 * @param entries the slice's entries
 * @param last whether this slice ends the table
 */
public record DerivedAspectsSync(List<Entry> entries, boolean last) implements CustomPacketPayload {
    /** The most entries one payload carries. */
    public static final int MAX = 4096;

    /** The payload's type, {@code vftv:derived_aspects}. */
    public static final Type<DerivedAspectsSync> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("vftv", "derived_aspects"));

    /**
     * One item's derived values.
     *
     * @param item the item
     * @param values its values, at most {@link AspectLimits#MAX_ASPECTS_PER_ENTRY} aspects
     */
    public record Entry(Item item, AspectValues values) {}

    private static final StreamCodec<RegistryFriendlyByteBuf, Item> ITEM =
            ByteBufCodecs.registry(Registries.ITEM);
    private static final StreamCodec<ByteBuf, ResourceKey<Aspect>> ASPECT =
            ResourceKey.streamCodec(AspectKeys.REGISTRY);

    /** The wire form; a count or amount out of bounds is rejected at decode. */
    public static final StreamCodec<RegistryFriendlyByteBuf, DerivedAspectsSync> STREAM_CODEC =
            StreamCodec.of(DerivedAspectsSync::write, DerivedAspectsSync::read);

    /** Checks the bound and copies the entries. */
    public DerivedAspectsSync {
        if (entries.size() > MAX) {
            throw new IllegalArgumentException(
                    "At most " + MAX + " entries, got " + entries.size());
        }
        entries = List.copyOf(entries);
    }

    @Override
    public Type<DerivedAspectsSync> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buf, DerivedAspectsSync payload) {
        buf.writeVarInt(payload.entries.size());
        for (Entry e : payload.entries) {
            ITEM.encode(buf, e.item());
            buf.writeVarInt(e.values().aspects().size());
            for (Map.Entry<ResourceKey<Aspect>, Integer> a : e.values().aspects().entrySet()) {
                ASPECT.encode(buf, a.getKey());
                buf.writeVarInt(a.getValue());
            }
        }
        buf.writeBoolean(payload.last);
    }

    private static DerivedAspectsSync read(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX) {
            throw new DecoderException("derived_aspects: " + count + " entries, at most " + MAX);
        }
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Item item = ITEM.decode(buf);
            int size = buf.readVarInt();
            if (size < 0 || size > AspectLimits.MAX_ASPECTS_PER_ENTRY) {
                throw new DecoderException(
                        "derived_aspects: "
                                + size
                                + " aspects, at most "
                                + AspectLimits.MAX_ASPECTS_PER_ENTRY);
            }
            TreeMap<ResourceKey<Aspect>, Integer> aspects = new TreeMap<>();
            for (int j = 0; j < size; j++) {
                ResourceKey<Aspect> aspect = ASPECT.decode(buf);
                int amount = buf.readVarInt();
                if (amount < 1 || aspects.put(aspect, amount) != null) {
                    throw new DecoderException("derived_aspects: a bad or repeated amount");
                }
            }
            entries.add(new Entry(item, new AspectValues(aspects)));
        }
        return new DerivedAspectsSync(entries, buf.readBoolean());
    }
}
