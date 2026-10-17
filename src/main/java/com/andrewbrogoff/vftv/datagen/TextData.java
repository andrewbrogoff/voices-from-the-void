package com.andrewbrogoff.vftv.datagen;

import com.andrewbrogoff.vftv.core.text.model.AccessibilityKeys;
import com.andrewbrogoff.vftv.core.text.model.LangEntry;
import java.util.List;

/**
 * The text feature's data generation: the accessibility keys' lang table (the shared message keys
 * stay {@link LangProvider}'s). Listed once in {@link VftvData#FEATURES}.
 */
final class TextData implements FeatureData {
    @Override
    public List<LangEntry> lang() {
        return AccessibilityKeys.TABLE;
    }
}
