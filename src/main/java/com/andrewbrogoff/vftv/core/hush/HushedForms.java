package com.andrewbrogoff.vftv.core.hush;

import com.andrewbrogoff.vftv.api.hush.HushKeys;
import com.andrewbrogoff.vftv.api.hush.HushedForm;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The fixed hushed-form table (Hush spec, "The forms"), bijective: converting sets {@code
 * form.withPropertiesOf(state)} and restoring sets {@code source.withPropertiesOf(state)}, so a
 * restored cell holds exactly the state that was hushed.
 */
public final class HushedForms {
    private static volatile @Nullable Map<Block, Block> toForm;
    private static volatile @Nullable Map<Block, Block> toSource;

    private HushedForms() {}

    private static Map<Block, Block> toForm() {
        Map<Block, Block> m = toForm;
        if (m == null) {
            build();
            m = toForm;
        }
        return java.util.Objects.requireNonNull(m);
    }

    private static Map<Block, Block> toSource() {
        Map<Block, Block> m = toSource;
        if (m == null) {
            build();
            m = toSource;
        }
        return java.util.Objects.requireNonNull(m);
    }

    private static synchronized void build() {
        Map<Block, Block> forms = new HashMap<>();
        Map<Block, Block> sources = new HashMap<>();
        for (HushedForm f : HushKeys.FORMS) {
            Block source = BuiltInRegistries.BLOCK.getValue(f.source());
            Block form = HushContent.form(f.form());
            forms.put(source, form);
            sources.put(form, source);
        }
        toSource = sources;
        toForm = forms;
    }

    /** Whether {@code state} is a source of the table. */
    public static boolean isSource(BlockState state) {
        return toForm().containsKey(state.getBlock());
    }

    /** Whether {@code state} is a hushed form. */
    public static boolean isForm(BlockState state) {
        return toSource().containsKey(state.getBlock());
    }

    /** The hushed state of a source state, or null for anything else. */
    public static @Nullable BlockState hushed(BlockState state) {
        Block form = toForm().get(state.getBlock());
        return form == null ? null : form.withPropertiesOf(state);
    }

    /** The source state of a hushed state, or null for anything else. */
    public static @Nullable BlockState restored(BlockState state) {
        Block source = toSource().get(state.getBlock());
        return source == null ? null : source.withPropertiesOf(state);
    }
}
