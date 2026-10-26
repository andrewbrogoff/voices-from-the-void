package com.andrewbrogoff.vftv.core.warp;

import com.andrewbrogoff.vftv.api.warp.Bargain;
import com.andrewbrogoff.vftv.api.warp.VoiceEvent;
import com.andrewbrogoff.vftv.api.warp.WarpKeys;
import com.andrewbrogoff.vftv.core.common.Registrar;
import com.andrewbrogoff.vftv.core.warp.model.VoiceContent;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;

/**
 * The Voices' registered content (warp spec): the Stilling Draught, the apparition's entity type,
 * the four effect sounds, and the {@code vftv:voice_event} and {@code vftv:bargain} datapack
 * registries, both server-only.
 */
public final class WarpContent {
    /** {@code vftv:stilling_draught}. */
    public static final ResourceKey<Item> STILLING_DRAUGHT_KEY =
            ResourceKey.create(Registries.ITEM, id("stilling_draught"));

    /** {@code vftv:apparition}. */
    public static final ResourceKey<EntityType<?>> APPARITION_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, id("apparition"));

    /** {@code vftv:apparition.vanish}. */
    public static final Identifier APPARITION_VANISH = id("apparition.vanish");

    /** {@code vftv:voice.insight}, an effect sound (not a Voice line). */
    public static final Identifier VOICE_INSIGHT = id("voice.insight");

    /** {@code vftv:bargain.accept}. */
    public static final Identifier BARGAIN_ACCEPT = id("bargain.accept");

    /** {@code vftv:stilling.drink_end}. */
    public static final Identifier STILLING_DRINK_END = id("stilling.drink_end");

    /** Every sound event, in the spec's order. */
    public static final List<Identifier> SOUNDS =
            List.of(APPARITION_VANISH, VOICE_INSIGHT, BARGAIN_ACCEPT, STILLING_DRINK_END);

    private static Supplier<Item> stillingDraught = WarpContent::missing;
    private static Supplier<EntityType<Apparition>> apparition = WarpContent::missing;
    private static List<Supplier<SoundEvent>> sounds = List.of();

    private WarpContent() {}

    /** One {@code vftv:voice_event} entry, with the JSON it was read from. */
    public record VoiceEventEntry(VoiceContent.Event event, JsonObject json)
            implements VoiceEvent {}

    /** One {@code vftv:bargain} entry, with the JSON it was read from. */
    public record BargainEntry(VoiceContent.Bargain bargain, JsonObject json) implements Bargain {}

    /** The entry codec of {@code vftv:voice_event}: the warp spec's fields and rules. */
    public static final Codec<VoiceEvent> VOICE_EVENT_CODEC =
            json(
                    o -> {
                        VoiceContent.Event e = VoiceContent.event(o);
                        e.effects().forEach(f -> effect(f.effect()));
                        return new VoiceEventEntry(e, o);
                    },
                    v -> ((VoiceEventEntry) v).json());

    /** The entry codec of {@code vftv:bargain}. */
    public static final Codec<Bargain> BARGAIN_CODEC =
            json(
                    o -> {
                        VoiceContent.Bargain b = VoiceContent.bargain(o);
                        effect(b.effect());
                        return new BargainEntry(b, o);
                    },
                    b -> ((BargainEntry) b).json());

    /** The Stilling Draught. */
    public static Item stillingDraught() {
        return stillingDraught.get();
    }

    /** The apparition's entity type. */
    public static EntityType<Apparition> apparition() {
        return apparition.get();
    }

    /** The sound event registered as {@code id}, one of {@link #SOUNDS}. */
    public static SoundEvent sound(Identifier id) {
        return sounds.get(SOUNDS.indexOf(id)).get();
    }

    static void register(Registrar registrar) {
        registrar.dataPackRegistry(WarpKeys.VOICE_EVENT_REGISTRY, VOICE_EVENT_CODEC, null);
        registrar.dataPackRegistry(WarpKeys.BARGAIN_REGISTRY, BARGAIN_CODEC, null);
        stillingDraught =
                registrar.register(
                        Registries.ITEM,
                        "stilling_draught",
                        () ->
                                new StillingDraught(
                                        new Item.Properties()
                                                .stacksTo(16)
                                                .setId(STILLING_DRAUGHT_KEY)));
        Supplier<EntityType<?>> type =
                registrar.register(
                        Registries.ENTITY_TYPE,
                        "apparition",
                        () ->
                                EntityType.Builder.<Apparition>of(Apparition::new, MobCategory.MISC)
                                        .sized(0.5F, 1.875F)
                                        .noSummon()
                                        .noSave()
                                        .build(APPARITION_KEY));
        // Registered from the typed EntityType just above.
        @SuppressWarnings("unchecked")
        Supplier<EntityType<Apparition>> typed = () -> (EntityType<Apparition>) type.get();
        apparition = typed;
        sounds =
                SOUNDS.stream()
                        .map(
                                s ->
                                        registrar.register(
                                                Registries.SOUND_EVENT,
                                                s.getPath(),
                                                () -> SoundEvent.createVariableRangeEvent(s)))
                        .toList();
    }

    /** A codec that reads an entry's JSON with {@code read}, which throws for a bad one. */
    private static <T> Codec<T> json(Function<JsonObject, T> read, Function<T, JsonObject> write) {
        return Codec.PASSTHROUGH.comapFlatMap(
                dynamic -> {
                    JsonElement json = dynamic.convert(JsonOps.INSTANCE).getValue();
                    if (!json.isJsonObject()) {
                        return DataResult.error(() -> "not a JSON object");
                    }
                    try {
                        return DataResult.success(read.apply(json.getAsJsonObject()));
                    } catch (IllegalArgumentException e) {
                        return DataResult.error(e::getMessage);
                    }
                },
                value -> new Dynamic<>(JsonOps.INSTANCE, write.apply(value)));
    }

    /** Requires a registered mob effect. */
    private static void effect(String id) {
        Identifier parsed = Identifier.tryParse(id);
        if (parsed == null || !BuiltInRegistries.MOB_EFFECT.containsKey(parsed)) {
            throw new IllegalArgumentException("unknown mob effect " + id);
        }
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("vftv", path);
    }

    @SuppressWarnings("TypeParameterUnusedInFormals") // it only throws, so no cast is hidden
    private static <T> T missing() {
        throw new IllegalStateException("WarpFeature.init has not run");
    }
}
