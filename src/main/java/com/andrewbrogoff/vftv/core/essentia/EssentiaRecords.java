package com.andrewbrogoff.vftv.core.essentia;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.api.essentia.EssentiaLimits;
import com.andrewbrogoff.vftv.core.common.RawRecordCodec;
import com.andrewbrogoff.vftv.core.common.RawRecordCodec.Retained;
import com.andrewbrogoff.vftv.core.essentia.model.EssentiaMath.Contents;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

/**
 * The essentia records and their bounded codecs (essentia spec, "Sync and state"): the jar's block
 * entity record, the {@code vftv:jar_contents} item component, the tube's and Alembic's label
 * record and the {@code vftv:label} component. Decoding checks format bounds only, never
 * registries, so a missing aspect id decodes and is inert.
 */
public final class EssentiaRecords {
    /** Every record's format. */
    public static final int FORMAT = 1;

    /** An aspect id of at most {@link EssentiaLimits#MAX_ID_CHARS} characters, as a key. */
    public static final Codec<ResourceKey<Aspect>> ASPECT =
            Identifier.CODEC
                    .validate(
                            id ->
                                    id.toString().length() <= EssentiaLimits.MAX_ID_CHARS
                                            ? DataResult.success(id)
                                            : DataResult.error(() -> "id over 256 characters"))
                    .xmap(
                            id -> ResourceKey.create(AspectKeys.REGISTRY, id),
                            ResourceKey::identifier);

    /** The aspect id on the wire, bounded the same way. */
    public static final StreamCodec<ByteBuf, ResourceKey<Aspect>> ASPECT_STREAM =
            ByteBufCodecs.stringUtf8(EssentiaLimits.MAX_ID_CHARS)
                    .map(
                            s -> ResourceKey.create(AspectKeys.REGISTRY, Identifier.parse(s)),
                            k -> k.identifier().toString());

    private EssentiaRecords() {}

    /**
     * A jar's block-entity record: aspect and amount both present or both absent, amount 1–10,000,
     * and a label that, with an aspect, equals it.
     */
    public record JarRecord(
            int format,
            Optional<ResourceKey<Aspect>> aspect,
            Optional<Integer> amount,
            Optional<ResourceKey<Aspect>> label) {
        /** The empty, unlabelled jar. */
        public static final JarRecord EMPTY =
                new JarRecord(FORMAT, Optional.empty(), Optional.empty(), Optional.empty());

        /** The bounded codec: format bounds and invariants. */
        public static final Codec<JarRecord> BOUNDED =
                RecordCodecBuilder.<JarRecord>create(
                                i ->
                                        i.group(
                                                        Codec.INT
                                                                .fieldOf("format")
                                                                .forGetter(JarRecord::format),
                                                        ASPECT.optionalFieldOf("aspect")
                                                                .forGetter(JarRecord::aspect),
                                                        Codec.INT
                                                                .optionalFieldOf("amount")
                                                                .forGetter(JarRecord::amount),
                                                        ASPECT.optionalFieldOf("label")
                                                                .forGetter(JarRecord::label))
                                                .apply(i, JarRecord::new))
                        .validate(JarRecord::check);

        /** The persisted form, keeping an unreadable record raw. */
        public static final Codec<Retained<JarRecord>> RETAINED = RawRecordCodec.wrap(BOUNDED);

        /** A record from contents and a label, unchecked. */
        public static JarRecord of(
                Contents<ResourceKey<Aspect>> contents, @Nullable ResourceKey<Aspect> label) {
            return new JarRecord(
                    FORMAT,
                    Optional.ofNullable(contents.aspect()),
                    contents.isEmpty() ? Optional.empty() : Optional.of(contents.amount()),
                    Optional.ofNullable(label));
        }

        /** The contents this record holds. */
        public Contents<ResourceKey<Aspect>> contents() {
            return aspect.isPresent() && amount.isPresent()
                    ? new Contents<>(aspect.get(), amount.get())
                    : Contents.empty();
        }

        /** The label, or null. */
        public @Nullable ResourceKey<Aspect> labelOrNull() {
            return label.orElse(null);
        }

        /** Why this record is out of bounds, or success. */
        public DataResult<JarRecord> check() {
            if (format != FORMAT) {
                return DataResult.error(() -> "unknown format " + format);
            }
            if (aspect.isPresent() != amount.isPresent()) {
                return DataResult.error(() -> "aspect and amount not both present");
            }
            if (amount.isPresent()
                    && (amount.get() < 1 || amount.get() > EssentiaLimits.MAX_JAR_AMOUNT)) {
                return DataResult.error(() -> "amount " + amount.get() + " outside 1..10000");
            }
            if (aspect.isPresent() && label.isPresent() && !aspect.equals(label)) {
                return DataResult.error(() -> "label " + label.get() + " differs from the aspect");
            }
            return DataResult.success(this);
        }
    }

    /** The {@code vftv:jar_contents} item component: a filled jar's aspect and amount. */
    public record JarStack(int format, ResourceKey<Aspect> aspect, int amount) {
        /** The bounded codec. */
        public static final Codec<JarStack> BOUNDED =
                RecordCodecBuilder.<JarStack>create(
                                i ->
                                        i.group(
                                                        Codec.INT
                                                                .fieldOf("format")
                                                                .forGetter(JarStack::format),
                                                        ASPECT.fieldOf("aspect")
                                                                .forGetter(JarStack::aspect),
                                                        Codec.INT
                                                                .fieldOf("amount")
                                                                .forGetter(JarStack::amount))
                                                .apply(i, JarStack::new))
                        .validate(
                                s ->
                                        s.format() != FORMAT
                                                ? DataResult.error(
                                                        () -> "unknown format " + s.format())
                                                : s.amount() < 1
                                                                || s.amount()
                                                                        > EssentiaLimits
                                                                                .MAX_JAR_AMOUNT
                                                        ? DataResult.error(
                                                                () ->
                                                                        "amount "
                                                                                + s.amount()
                                                                                + " outside"
                                                                                + " 1..10000")
                                                        : DataResult.success(s));

        /** The component's codec, keeping an unreadable value raw. */
        public static final Codec<Retained<JarStack>> RETAINED = RawRecordCodec.wrap(BOUNDED);

        /** The component on the wire: whole, as vanilla sends components. */
        public static final StreamCodec<ByteBuf, Retained<JarStack>> STREAM =
                ByteBufCodecs.fromCodec(RETAINED);
    }

    /** A tube's or Alembic's record: the optional label. */
    public record LabelRecord(int format, Optional<ResourceKey<Aspect>> label) {
        /** The unlabelled record. */
        public static final LabelRecord EMPTY = new LabelRecord(FORMAT, Optional.empty());

        /** The bounded codec. */
        public static final Codec<LabelRecord> BOUNDED =
                RecordCodecBuilder.<LabelRecord>create(
                                i ->
                                        i.group(
                                                        Codec.INT
                                                                .fieldOf("format")
                                                                .forGetter(LabelRecord::format),
                                                        ASPECT.optionalFieldOf("label")
                                                                .forGetter(LabelRecord::label))
                                                .apply(i, LabelRecord::new))
                        .validate(
                                r ->
                                        r.format() == FORMAT
                                                ? DataResult.success(r)
                                                : DataResult.error(
                                                        () -> "unknown format " + r.format()));

        /** The persisted form, keeping an unreadable record raw. */
        public static final Codec<Retained<LabelRecord>> RETAINED = RawRecordCodec.wrap(BOUNDED);
    }

    /** The readable value of a retained record, or null when unreadable. */
    public static <T> @Nullable T read(Retained<T> retained) {
        return retained instanceof Retained.Read<T> r ? r.value() : null;
    }
}
