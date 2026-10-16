package com.andrewbrogoff.vftv.api.essentia;

import com.andrewbrogoff.vftv.api.Stable;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * The essentia feature's ids: the block tags {@code #vftv:essentia_endpoints} and {@code
 * #vftv:alembic_bases}, the capability ids {@code vftv:essentia} and {@code vftv:essentia_suction},
 * and the item component ids {@code vftv:jar_contents} and {@code vftv:label}. Stable: no id here
 * is removed or renamed within a major.
 */
@Stable
public final class EssentiaKeys {
    private EssentiaKeys() {}

    /**
     * The block tag {@code #vftv:essentia_endpoints}: tube faces connect to these blocks. Packs and
     * addons extend it so tubes reach their blocks.
     */
    public static final TagKey<Block> ESSENTIA_ENDPOINTS =
            TagKey.create(Registries.BLOCK, id("essentia_endpoints"));

    /** The block tag {@code #vftv:alembic_bases}: an Alembic may hang on these blocks. */
    public static final TagKey<Block> ALEMBIC_BASES =
            TagKey.create(Registries.BLOCK, id("alembic_bases"));

    /** The id {@code vftv:essentia} of the essentia handler block capability. */
    public static final Identifier HANDLER_CAPABILITY = id("essentia");

    /** The id {@code vftv:essentia_suction} of the suction block capability. */
    public static final Identifier SUCTION_CAPABILITY = id("essentia_suction");

    /**
     * The id of the {@code vftv:jar_contents} item component (diagnostics only; direct use is
     * unsupported).
     */
    public static final Identifier JAR_CONTENTS_COMPONENT = id("jar_contents");

    /**
     * The id of the {@code vftv:label} item component (diagnostics only; direct use is
     * unsupported).
     */
    public static final Identifier LABEL_COMPONENT = id("label");

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("vftv", path);
    }
}
