package com.andrewbrogoff.vftv.datagen;

import net.minecraft.data.tags.TagAppender;
import net.minecraft.tags.TagKey;

/** The tag builder a feature's {@link FeatureData#tags} writes into, for any registry. */
@FunctionalInterface
public interface Tags {
    /** The builder of {@code key}, shared by every feature that adds to it. */
    <T> TagAppender<T> tag(TagKey<T> key);
}
