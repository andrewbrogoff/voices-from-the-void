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
    /** Built on first use, after registration: source to form, and form to source. */
    private record Table(Map<Block, Block> toForm, Map<Block, Block> toSource) {}

    private static volatile @Nullable Table table;

    private HushedForms() {}

    private static Table table() {
        Table t = table;
        if (t == null) {
            Map<Block, Block> forms = new HashMap<>();
            Map<Block, Block> sources = new HashMap<>();
            for (HushedForm f : HushKeys.FORMS) {
                Block source = BuiltInRegistries.BLOCK.getValue(f.source());
                Block form = HushContent.form(f.form());
                forms.put(source, form);
                sources.put(form, source);
            }
            t = new Table(forms, sources);
            table = t;
        }
        return t;
    }

    private static Map<Block, Block> toForm() {
        return table().toForm();
    }

    private static Map<Block, Block> toSource() {
        return table().toSource();
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
