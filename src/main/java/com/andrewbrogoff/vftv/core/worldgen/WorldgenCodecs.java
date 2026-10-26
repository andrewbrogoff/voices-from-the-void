package com.andrewbrogoff.vftv.core.worldgen;

import com.andrewbrogoff.vftv.api.research.ResearchKeys;
import com.andrewbrogoff.vftv.api.worldgen.ChoirSite;
import com.andrewbrogoff.vftv.api.worldgen.WorldgenKeys;
import com.andrewbrogoff.vftv.api.worldgen.WorldgenLimits;
import com.andrewbrogoff.vftv.core.worldgen.model.ChoirClaims;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ExtraCodecs;

/** The worldgen feature's codecs: the claims record, the site kind and the Reliquary's record. */
public final class WorldgenCodecs {
    private WorldgenCodecs() {}

    /** The longest id a record keeps: 256 characters. */
    static final int MAX_ID = 256;

    private static final Codec<String> ID =
            Identifier.CODEC.comapFlatMap(
                    id ->
                            id.toString().length() <= MAX_ID
                                    ? DataResult.success(id.toString())
                                    : DataResult.error(() -> "id over " + MAX_ID + " characters"),
                    Identifier::parse);

    private static final Codec<ChoirClaims.Site> SITE =
            strict(
                    RecordCodecBuilder.create(
                            i ->
                                    i.group(
                                                    ID.fieldOf("dim")
                                                            .forGetter(ChoirClaims.Site::dim),
                                                    Codec.LONG
                                                            .fieldOf("pos")
                                                            .forGetter(ChoirClaims.Site::pos),
                                                    ID.listOf(1, WorldgenLimits.MAX_PAGES_PER_CLAIM)
                                                            .fieldOf("pages")
                                                            .forGetter(ChoirClaims.Site::pages))
                                            .apply(i, ChoirClaims.Site::new)),
                    Set.of("dim", "pos", "pages"));

    /**
     * The claims record's bounded codec, {@code {format: 1, sites: [...]}}: format bounds only, so
     * an unknown page or dimension is kept. Wrap it in {@code RawRecordCodec} to keep unreadable
     * records raw.
     */
    public static final Codec<ChoirClaims> CLAIMS =
            strict(
                    RecordCodecBuilder.<ChoirClaims>create(
                            i ->
                                    i.group(
                                                    Codec.INT
                                                            .fieldOf("format")
                                                            .forGetter(c -> ChoirClaims.FORMAT),
                                                    SITE.listOf(0, WorldgenLimits.MAX_CLAIM_SITES)
                                                            .fieldOf("sites")
                                                            .forGetter(ChoirClaims::sites))
                                            .apply(
                                                    i,
                                                    (format, sites) -> {
                                                        if (format != ChoirClaims.FORMAT) {
                                                            throw new IllegalArgumentException(
                                                                    "unknown format " + format);
                                                        }
                                                        return new ChoirClaims(sites);
                                                    })),
                    Set.of("format", "sites"));

    /**
     * A {@code vftv:choir_site} entry (worldgen spec, "Site kinds"): unknown fields refused; the
     * cross-registry rules run when the world opens.
     *
     * @param pages the pages it gives, in order, 1 to 64
     * @param pagesPerClaim how many a first claim takes, 1 to 8
     */
    public record SiteEntry(
            List<ResourceKey<com.andrewbrogoff.vftv.api.research.Research>> pages,
            int pagesPerClaim)
            implements ChoirSite {
        /** The entry's codec. */
        public static final Codec<SiteEntry> CODEC =
                strict(
                        RecordCodecBuilder.create(
                                i ->
                                        i.group(
                                                        ResourceKey.codec(ResearchKeys.REGISTRY)
                                                                .listOf(
                                                                        1,
                                                                        WorldgenLimits
                                                                                .MAX_PAGES_PER_SITE)
                                                                .fieldOf("pages")
                                                                .forGetter(SiteEntry::pages),
                                                        ExtraCodecs.intRange(
                                                                        1,
                                                                        WorldgenLimits
                                                                                .MAX_PAGES_PER_CLAIM)
                                                                .fieldOf("pages_per_claim")
                                                                .forGetter(
                                                                        SiteEntry::pagesPerClaim))
                                                .apply(i, SiteEntry::new)),
                        Set.of("pages", "pages_per_claim"));

        /** The registry's element codec, up-casting to {@link ChoirSite}. */
        public static final Codec<ChoirSite> ELEMENT_CODEC =
                CODEC.flatComapMap(
                        e -> e,
                        s ->
                                s instanceof SiteEntry e
                                        ? DataResult.success(e)
                                        : DataResult.error(() -> "not a vftv choir site: " + s));

        /** Copies the pages. */
        public SiteEntry {
            pages = List.copyOf(pages);
        }

        /** The page ids, in order. */
        public List<String> pageIds() {
            return pages.stream().map(k -> k.identifier().toString()).toList();
        }
    }

    /**
     * The Reliquary's owned record, {@code {format: 1, site}}.
     *
     * @param site its site kind's id, kept as written
     */
    public record Reliquary(ResourceKey<ChoirSite> site) {
        /** The only format this build reads and writes. */
        public static final int FORMAT = 1;

        /** A data-free placement's initial record. */
        public static final Reliquary BUILT_IN = new Reliquary(WorldgenKeys.CHOIR_RUIN_SITE);

        /** The bounded codec. */
        public static final Codec<Reliquary> CODEC =
                strict(
                        RecordCodecBuilder.create(
                                i ->
                                        i.group(
                                                        Codec.INT
                                                                .fieldOf("format")
                                                                .forGetter(r -> FORMAT),
                                                        ID.fieldOf("site")
                                                                .forGetter(
                                                                        r ->
                                                                                r.site()
                                                                                        .identifier()
                                                                                        .toString()))
                                                .apply(
                                                        i,
                                                        (format, site) -> {
                                                            if (format != FORMAT) {
                                                                throw new IllegalArgumentException(
                                                                        "unknown format " + format);
                                                            }
                                                            return new Reliquary(
                                                                    ResourceKey.create(
                                                                            WorldgenKeys.CHOIR_SITE,
                                                                            Identifier.parse(
                                                                                    site)));
                                                        })),
                        Set.of("format", "site"));
    }

    /** A codec that refuses an object with a key outside {@code keys}. */
    static <T> Codec<T> strict(Codec<T> codec, Set<String> keys) {
        return new Codec<>() {
            @Override
            public <O> DataResult<Pair<T, O>> decode(DynamicOps<O> ops, O input) {
                List<String> unknown = new ArrayList<>();
                ops.getMapValues(input)
                        .ifSuccess(
                                entries ->
                                        entries.forEach(
                                                e ->
                                                        ops.getStringValue(e.getFirst())
                                                                .ifSuccess(
                                                                        k -> {
                                                                            if (!keys.contains(k)) {
                                                                                unknown.add(k);
                                                                            }
                                                                        })));
                if (!unknown.isEmpty()) {
                    return DataResult.error(() -> "unknown field " + String.join(", ", unknown));
                }
                return codec.decode(ops, input);
            }

            @Override
            public <O> DataResult<O> encode(T input, DynamicOps<O> ops, O prefix) {
                return codec.encode(input, ops, prefix);
            }

            @Override
            public String toString() {
                return "Strict[" + codec + "]";
            }
        };
    }
}
