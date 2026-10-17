package com.andrewbrogoff.vftv.core.research;

import com.andrewbrogoff.vftv.api.research.Research;
import com.andrewbrogoff.vftv.api.research.ResearchKeys;
import com.andrewbrogoff.vftv.core.common.Registrar;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

/**
 * The research feature's items and component (research spec, "The item" and "Choir pages"): the
 * Commonplace Book, a viewer of its holder's own research, and the Choir page, whose {@code
 * vftv:page} component names its entry.
 */
public final class ResearchItems {
    /** {@code vftv:commonplace_book}, stacking to 1 and bound to no one. */
    public static final String COMMONPLACE_BOOK_NAME = "commonplace_book";

    /** {@code vftv:choir_page}. */
    public static final String CHOIR_PAGE_NAME = "choir_page";

    private static Supplier<Item> commonplaceBook =
            () -> {
                throw new IllegalStateException("ResearchFeature.init has not run");
            };
    private static Supplier<Item> choirPage = commonplaceBook;
    private static Supplier<DataComponentType<?>> page =
            () -> {
                throw new IllegalStateException("ResearchFeature.init has not run");
            };

    private ResearchItems() {}

    static void register(Registrar registrar) {
        page =
                registrar.register(
                        Registries.DATA_COMPONENT_TYPE,
                        ResearchKeys.PAGE_COMPONENT.getPath(),
                        () ->
                                DataComponentType.<ResourceKey<Research>>builder()
                                        .persistent(ResourceKey.codec(ResearchKeys.REGISTRY))
                                        .networkSynchronized(
                                                ResourceKey.streamCodec(ResearchKeys.REGISTRY))
                                        .build());
        commonplaceBook =
                registrar.register(
                        Registries.ITEM,
                        COMMONPLACE_BOOK_NAME,
                        () -> new Item(properties(COMMONPLACE_BOOK_NAME).stacksTo(1)));
        choirPage =
                registrar.register(
                        Registries.ITEM,
                        CHOIR_PAGE_NAME,
                        () -> new Item(properties(CHOIR_PAGE_NAME)));
    }

    /** The Commonplace Book. */
    public static Item commonplaceBook() {
        return commonplaceBook.get();
    }

    /** The Choir page. */
    public static Item choirPage() {
        return choirPage.get();
    }

    /** The {@code vftv:page} component: the Choir page's entry id. */
    // Registered above from a DataComponentType<ResourceKey<Research>> builder.
    @SuppressWarnings("unchecked")
    public static DataComponentType<ResourceKey<Research>> page() {
        return (DataComponentType<ResourceKey<Research>>) page.get();
    }

    private static Item.Properties properties(String name) {
        return new Item.Properties()
                .setId(
                        ResourceKey.create(
                                Registries.ITEM, Identifier.fromNamespaceAndPath("vftv", name)));
    }
}
