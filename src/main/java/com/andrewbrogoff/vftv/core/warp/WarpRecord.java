package com.andrewbrogoff.vftv.core.warp;

import com.andrewbrogoff.vftv.api.warp.WarpLimits;
import com.andrewbrogoff.vftv.api.warp.WarpState;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.PrimitiveCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * The persisted fields of the {@code vftv:warp} player attachment (warp spec, "Kinds and the
 * record"). The constructor checks every format bound and nothing about the current tuning, so a
 * lowered tuning never makes a record unreadable; a value out of bounds throws, which the raw
 * record codec keeps as unreadable.
 *
 * @param format 1
 * @param temporary {@code T}, 0–1,000
 * @param lasting {@code L}, 0–1,000
 * @param permanent {@code P}, 0–100
 * @param gear {@code G}, the latest gear sample, 0–100
 * @param counter the Voice counter, 0–10,000
 * @param checks performed checks, the draw index, ≥ 0
 * @param milestonesPaid the named milestones already paid: at most 16 distinct ids of at most 256
 *     characters, kept sorted
 * @param lastListen the overworld game time of the last Listen rite, −1 if never
 * @param silenced set only by Silencing
 * @param revision monotonic: every change takes the next value
 */
public record WarpRecord(
        int format,
        int temporary,
        int lasting,
        int permanent,
        int gear,
        int counter,
        long checks,
        List<Identifier> milestonesPaid,
        long lastListen,
        boolean silenced,
        long revision) {
    /** The record's format. */
    public static final int FORMAT = 1;

    /** A new player's record. */
    public static final WarpRecord EMPTY =
            new WarpRecord(FORMAT, 0, 0, 0, 0, 0, 0, List.of(), -1, false, 0);

    /**
     * An int that never narrows: a fraction or a value outside {@code int} fails, so the raw record
     * codec keeps the tag instead of rewriting it. ({@code Codec.INT} would truncate.)
     */
    private static final Codec<Integer> EXACT_INT =
            new PrimitiveCodec<>() {
                @Override
                public <T> DataResult<Integer> read(DynamicOps<T> ops, T input) {
                    return exact(ops, input)
                            .flatMap(
                                    v ->
                                            v == (int) (long) v
                                                    ? DataResult.success((int) (long) v)
                                                    : DataResult.error(() -> "not an int: " + v));
                }

                @Override
                public <T> T write(DynamicOps<T> ops, Integer value) {
                    return ops.createInt(value);
                }
            };

    /** A long that never narrows: a fraction or a value outside {@code long} fails. */
    private static final Codec<Long> EXACT_LONG =
            new PrimitiveCodec<>() {
                @Override
                public <T> DataResult<Long> read(DynamicOps<T> ops, T input) {
                    return exact(ops, input);
                }

                @Override
                public <T> T write(DynamicOps<T> ops, Long value) {
                    return ops.createLong(value);
                }
            };

    /** A boolean stored as 0 or 1 (NBT's byte); any other value fails rather than coercing. */
    private static final Codec<Boolean> EXACT_BOOL =
            new PrimitiveCodec<>() {
                @Override
                public <T> DataResult<Boolean> read(DynamicOps<T> ops, T input) {
                    return exact(ops, input)
                            .flatMap(
                                    v ->
                                            v == 0 || v == 1
                                                    ? DataResult.success(v == 1)
                                                    : DataResult.error(() -> "not 0 or 1: " + v));
                }

                @Override
                public <T> T write(DynamicOps<T> ops, Boolean value) {
                    return ops.createBoolean(value);
                }
            };

    /** The bounded persisted form: every field required; the constructor checks the bounds. */
    public static final Codec<WarpRecord> CODEC =
            RecordCodecBuilder.create(
                    i ->
                            i.group(
                                            EXACT_INT
                                                    .fieldOf("format")
                                                    .forGetter(WarpRecord::format),
                                            EXACT_INT
                                                    .fieldOf("temporary")
                                                    .forGetter(WarpRecord::temporary),
                                            EXACT_INT
                                                    .fieldOf("lasting")
                                                    .forGetter(WarpRecord::lasting),
                                            EXACT_INT
                                                    .fieldOf("permanent")
                                                    .forGetter(WarpRecord::permanent),
                                            EXACT_INT.fieldOf("gear").forGetter(WarpRecord::gear),
                                            EXACT_INT
                                                    .fieldOf("counter")
                                                    .forGetter(WarpRecord::counter),
                                            EXACT_LONG
                                                    .fieldOf("checks")
                                                    .forGetter(WarpRecord::checks),
                                            Identifier.CODEC
                                                    .listOf()
                                                    .fieldOf("milestones_paid")
                                                    .forGetter(WarpRecord::milestonesPaid),
                                            EXACT_LONG
                                                    .fieldOf("last_listen")
                                                    .forGetter(WarpRecord::lastListen),
                                            EXACT_BOOL
                                                    .fieldOf("silenced")
                                                    .forGetter(WarpRecord::silenced),
                                            EXACT_LONG
                                                    .fieldOf("revision")
                                                    .forGetter(WarpRecord::revision))
                                    .apply(i, WarpRecord::new));

    /** The wire form, the same fields; the constructor checks the bounds at decode. */
    public static final StreamCodec<FriendlyByteBuf, WarpRecord> STREAM_CODEC =
            StreamCodec.of(WarpRecord::write, WarpRecord::read);

    /** Checks every format bound; sorts the milestones. */
    public WarpRecord {
        check(format == FORMAT, "format " + format);
        check(temporary >= 0 && temporary <= WarpLimits.T_MAX, "temporary " + temporary);
        check(lasting >= 0 && lasting <= WarpLimits.L_MAX, "lasting " + lasting);
        check(permanent >= 0 && permanent <= WarpLimits.P_FORMAT_MAX, "permanent " + permanent);
        check(gear >= 0 && gear <= WarpLimits.P_FORMAT_MAX, "gear " + gear);
        check(counter >= 0 && counter <= WarpLimits.COUNTER_FORMAT_MAX, "counter " + counter);
        check(checks >= 0, "checks " + checks);
        check(milestonesPaid.size() <= WarpLimits.MAX_MILESTONES, "too many milestones");
        check(new HashSet<>(milestonesPaid).size() == milestonesPaid.size(), "duplicate milestone");
        for (Identifier id : milestonesPaid) {
            check(id.toString().length() <= WarpLimits.MAX_ID_CHARS, "milestone id too long");
        }
        check(lastListen >= -1, "last_listen " + lastListen);
        check(revision >= 0, "revision " + revision);
        List<Identifier> sorted = new ArrayList<>(milestonesPaid);
        sorted.sort(null);
        milestonesPaid = List.copyOf(sorted);
    }

    /** {@code T + L + P + G}. */
    public int total() {
        return temporary + lasting + permanent + gear;
    }

    /** The record's warp as an API state. */
    public WarpState state() {
        return new WarpState(temporary, lasting, permanent, gear, total());
    }

    /** The paid milestones as {@code ns:path} strings, for the model. */
    public Set<String> paid() {
        Set<String> paid = new HashSet<>();
        milestonesPaid.forEach(id -> paid.add(id.toString()));
        return paid;
    }

    /** Whether {@code vftv:joining} is paid: the Joined ending. */
    public boolean joined() {
        return milestonesPaid.contains(WarpService.JOINING);
    }

    /** A JSON view for diagnostics (the harness dump): the player's name and the record. */
    public static JsonObject dump(String name, @Nullable WarpRecord r) {
        JsonObject json = new JsonObject();
        json.addProperty("name", name);
        json.addProperty("readable", r != null);
        if (r != null) {
            json.addProperty("temporary", r.temporary);
            json.addProperty("lasting", r.lasting);
            json.addProperty("permanent", r.permanent);
            json.addProperty("gear", r.gear);
            json.addProperty("total", r.total());
            json.addProperty("counter", r.counter);
            json.addProperty("silenced", r.silenced);
            json.addProperty("revision", r.revision);
            JsonArray paid = new JsonArray();
            r.milestonesPaid.forEach(id -> paid.add(id.toString()));
            json.add("milestonesPaid", paid);
        }
        return json;
    }

    /** A number as a long, only when that is exact: integer types, or an integral double. */
    private static <T> DataResult<Long> exact(DynamicOps<T> ops, T input) {
        return ops.getNumberValue(input)
                .flatMap(
                        n -> {
                            if (n instanceof Long
                                    || n instanceof Integer
                                    || n instanceof Short
                                    || n instanceof Byte) {
                                return DataResult.success(n.longValue());
                            }
                            double d = n.doubleValue();
                            return d == Math.rint(d) && Math.abs(d) <= (1L << 53)
                                    ? DataResult.success((long) d)
                                    : DataResult.error(() -> "not an integer: " + n);
                        });
    }

    private static void check(boolean ok, String what) {
        if (!ok) {
            throw new IllegalArgumentException("warp record: " + what);
        }
    }

    private static void write(FriendlyByteBuf buf, WarpRecord r) {
        buf.writeVarInt(r.format);
        buf.writeVarInt(r.temporary);
        buf.writeVarInt(r.lasting);
        buf.writeVarInt(r.permanent);
        buf.writeVarInt(r.gear);
        buf.writeVarInt(r.counter);
        buf.writeVarLong(r.checks);
        buf.writeVarInt(r.milestonesPaid.size());
        r.milestonesPaid.forEach(id -> ID.encode(buf, id));
        buf.writeVarLong(r.lastListen + 1);
        buf.writeBoolean(r.silenced);
        buf.writeVarLong(r.revision);
    }

    private static WarpRecord read(FriendlyByteBuf buf) {
        int format = buf.readVarInt();
        int temporary = buf.readVarInt();
        int lasting = buf.readVarInt();
        int permanent = buf.readVarInt();
        int gear = buf.readVarInt();
        int counter = buf.readVarInt();
        long checks = buf.readVarLong();
        int count = buf.readVarInt();
        check(count >= 0 && count <= WarpLimits.MAX_MILESTONES, "milestone count " + count);
        List<Identifier> paid = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            paid.add(ID.decode(buf));
        }
        return new WarpRecord(
                format,
                temporary,
                lasting,
                permanent,
                gear,
                counter,
                checks,
                paid,
                buf.readVarLong() - 1,
                buf.readBoolean(),
                buf.readVarLong());
    }

    /** An id of at most 256 characters on the wire. */
    static final StreamCodec<FriendlyByteBuf, Identifier> ID =
            ByteBufCodecs.stringUtf8(WarpLimits.MAX_ID_CHARS)
                    .map(Identifier::parse, Identifier::toString)
                    .cast();
}
