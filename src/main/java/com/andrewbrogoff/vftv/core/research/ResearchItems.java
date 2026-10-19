package com.andrewbrogoff.vftv.core.research;

import com.andrewbrogoff.vftv.api.research.Research;
import com.andrewbrogoff.vftv.api.research.ResearchKeys;
import com.andrewbrogoff.vftv.core.common.Registrar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;

/**
 * The research feature's items, component and sounds (research spec, "The item", "Choir pages" and
 * "Book UI keys"): the Commonplace Book, a viewer of its holder's own research; the Choir page,
 * whose {@code vftv:page} component names its entry; and the book's sound events.
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

    /**
     * The book's sound events, {@code vftv:book.<name>} (research spec, "Book UI keys"), each with
     * its subtitle {@code subtitles.vftv.book.<name>}. Their audio is pending.
     */
    public static final List<String> SOUNDS =
            List.of(
                    "page_turn",
                    "place",
                    "link",
                    "solve",
                    "learn",
                    "stage_1",
                    "stage_2",
                    "stage_3",
                    "stage_4",
                    "tip_in",
                    "listen");

    private static final Map<String, Supplier<SoundEvent>> SOUND_EVENTS = new HashMap<>();

    private ResearchItems() {}

    static void register(Registrar registrar) {
        for (String name : SOUNDS) {
            Identifier id = Identifier.fromNamespaceAndPath("vftv", "book." + name);
            SOUND_EVENTS.put(
                    name,
                    registrar.register(
                            Registries.SOUND_EVENT,
                            id.getPath(),
                            () -> SoundEvent.createVariableRangeEvent(id)));
        }
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
                        () -> new ChoirPageItem(properties(CHOIR_PAGE_NAME)));
    }

    /** The Commonplace Book. */
    public static Item commonplaceBook() {
        return commonplaceBook.get();
    }

    /** The book sound {@code vftv:book.<name>}, one of {@link #SOUNDS}. */
    public static SoundEvent sound(String name) {
        Supplier<SoundEvent> sound = SOUND_EVENTS.get(name);
        if (sound == null) {
            throw new IllegalArgumentException("no book sound " + name);
        }
        return sound.get();
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
