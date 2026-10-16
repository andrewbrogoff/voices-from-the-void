package com.andrewbrogoff.vftv.api.aspect;

import com.andrewbrogoff.vftv.api.Stable;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * The aspect feature's ids: the {@code vftv:aspect} registry and its 37 built-in aspects, the
 * {@code vftv:aspects} and {@code vftv:aspect_defaults} data-map ids, and the {@code
 * vftv:knowledge} attachment id. Stable: no id here is removed or renamed within a major. See the
 * aspects spec, "The aspects" and "Registry format".
 */
@Stable
public final class AspectKeys {
    private AspectKeys() {}

    /** The synced datapack registry {@code vftv:aspect} of the aspects. */
    public static final ResourceKey<Registry<Aspect>> REGISTRY =
            ResourceKey.createRegistryKey(id("aspect"));

    /** {@code vftv:aer} (air, breath, wind): a primal. */
    public static final ResourceKey<Aspect> AER = aspect("aer");

    /** {@code vftv:terra} (earth, stone, soil): a primal. */
    public static final ResourceKey<Aspect> TERRA = aspect("terra");

    /** {@code vftv:ignis} (fire, heat): a primal. */
    public static final ResourceKey<Aspect> IGNIS = aspect("ignis");

    /** {@code vftv:aqua} (water, liquid): a primal. */
    public static final ResourceKey<Aspect> AQUA = aspect("aqua");

    /** {@code vftv:ordo} (order, law, pattern): a primal. */
    public static final ResourceKey<Aspect> ORDO = aspect("ordo");

    /** {@code vftv:perditio} (entropy, decay, undoing): a primal. */
    public static final ResourceKey<Aspect> PERDITIO = aspect("perditio");

    /** {@code vftv:vacuos} (void, emptiness): the hidden primal, taught as Aer and Perditio. */
    public static final ResourceKey<Aspect> VACUOS = aspect("vacuos");

    /** {@code vftv:lux} (light): a compound of Aer and Ignis. */
    public static final ResourceKey<Aspect> LUX = aspect("lux");

    /** {@code vftv:gelum} (cold, ice): a compound of Aqua and Ordo. */
    public static final ResourceKey<Aspect> GELUM = aspect("gelum");

    /** {@code vftv:motus} (motion): a compound of Aer and Aqua. */
    public static final ResourceKey<Aspect> MOTUS = aspect("motus");

    /** {@code vftv:potentia} (power, energy): a compound of Ignis and Ordo. */
    public static final ResourceKey<Aspect> POTENTIA = aspect("potentia");

    /** {@code vftv:victus} (life): a compound of Aqua and Terra. */
    public static final ResourceKey<Aspect> VICTUS = aspect("victus");

    /** {@code vftv:vitreus} (crystal, glass): a compound of Terra and Ordo. */
    public static final ResourceKey<Aspect> VITREUS = aspect("vitreus");

    /** {@code vftv:venenum} (poison): a compound of Aqua and Perditio. */
    public static final ResourceKey<Aspect> VENENUM = aspect("venenum");

    /** {@code vftv:permutatio} (change, exchange): a compound of Ordo and Perditio. */
    public static final ResourceKey<Aspect> PERMUTATIO = aspect("permutatio");

    /** {@code vftv:sonus} (sound, tone): a compound of Aer and Ordo. */
    public static final ResourceKey<Aspect> SONUS = aspect("sonus");

    /** {@code vftv:metallum} (metal): a compound of Terra and Ignis. */
    public static final ResourceKey<Aspect> METALLUM = aspect("metallum");

    /** {@code vftv:tenebrae} (darkness): a compound of Terra and Vacuos. */
    public static final ResourceKey<Aspect> TENEBRAE = aspect("tenebrae");

    /** {@code vftv:herba} (plants): a compound of Victus and Lux. */
    public static final ResourceKey<Aspect> HERBA = aspect("herba");

    /** {@code vftv:bestia} (beasts): a compound of Victus and Motus. */
    public static final ResourceKey<Aspect> BESTIA = aspect("bestia");

    /** {@code vftv:mortuus} (death): a compound of Victus and Perditio. */
    public static final ResourceKey<Aspect> MORTUUS = aspect("mortuus");

    /** {@code vftv:spiritus} (soul, spirit): a compound of Victus and Sonus. */
    public static final ResourceKey<Aspect> SPIRITUS = aspect("spiritus");

    /** {@code vftv:praecantatio} (magic, incantation): a compound of Potentia and Sonus. */
    public static final ResourceKey<Aspect> PRAECANTATIO = aspect("praecantatio");

    /** {@code vftv:fames} (hunger): a compound of Victus and Vacuos. */
    public static final ResourceKey<Aspect> FAMES = aspect("fames");

    /** {@code vftv:instrumentum} (tools): a compound of Metallum and Ordo. */
    public static final ResourceKey<Aspect> INSTRUMENTUM = aspect("instrumentum");

    /** {@code vftv:silentium} (silence): a compound of Sonus and Vacuos. */
    public static final ResourceKey<Aspect> SILENTIUM = aspect("silentium");

    /** {@code vftv:arbor} (wood, trees): a compound of Herba and Terra. */
    public static final ResourceKey<Aspect> ARBOR = aspect("arbor");

    /** {@code vftv:sano} (healing): a compound of Herba and Ordo. */
    public static final ResourceKey<Aspect> SANO = aspect("sano");

    /** {@code vftv:corpus} (flesh, body): a compound of Bestia and Terra. */
    public static final ResourceKey<Aspect> CORPUS = aspect("corpus");

    /** {@code vftv:exanimis} (undeath): a compound of Mortuus and Motus. */
    public static final ResourceKey<Aspect> EXANIMIS = aspect("exanimis");

    /** {@code vftv:cognitio} (mind, knowledge): a compound of Spiritus and Ordo. */
    public static final ResourceKey<Aspect> COGNITIO = aspect("cognitio");

    /** {@code vftv:humanus} (humankind): a compound of Spiritus and Bestia. */
    public static final ResourceKey<Aspect> HUMANUS = aspect("humanus");

    /** {@code vftv:telum} (weapons): a compound of Instrumentum and Perditio. */
    public static final ResourceKey<Aspect> TELUM = aspect("telum");

    /** {@code vftv:tutamen} (protection): a compound of Instrumentum and Vitreus. */
    public static final ResourceKey<Aspect> TUTAMEN = aspect("tutamen");

    /** {@code vftv:pannus} (cloth): a compound of Bestia and Instrumentum. */
    public static final ResourceKey<Aspect> PANNUS = aspect("pannus");

    /** {@code vftv:machina} (machines): a compound of Instrumentum and Potentia. */
    public static final ResourceKey<Aspect> MACHINA = aspect("machina");

    /** {@code vftv:alienis} (the Choir, the otherworldly): a compound of Spiritus and Vacuos. */
    public static final ResourceKey<Aspect> ALIENIS = aspect("alienis");

    /**
     * The id of the synced data map {@code vftv:aspects} on items, blocks and entity types: what a
     * thing is made of.
     */
    public static final Identifier ASPECTS = id("aspects");

    /**
     * The id of the synced item data map {@code vftv:aspect_defaults}, keyed by convention tags and
     * consulted only where {@code vftv:aspects} has no entry.
     */
    public static final Identifier ASPECT_DEFAULTS = id("aspect_defaults");

    /**
     * The id of the {@code vftv:knowledge} player attachment (diagnostics only; direct use is
     * unsupported).
     */
    public static final Identifier KNOWLEDGE_ATTACHMENT = id("knowledge");

    private static ResourceKey<Aspect> aspect(String path) {
        return ResourceKey.create(REGISTRY, id(path));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("vftv", path);
    }
}
