package com.andrewbrogoff.vftv.core.research;

import com.andrewbrogoff.vftv.api.research.ResearchLimits;
import com.andrewbrogoff.vftv.core.common.DataSlots.DeltaSync;
import com.andrewbrogoff.vftv.core.common.RawRecordCodec;
import com.andrewbrogoff.vftv.core.common.RawRecordCodec.Retained;
import com.andrewbrogoff.vftv.core.research.model.Note;
import com.andrewbrogoff.vftv.core.research.model.ResearchDelta;
import com.andrewbrogoff.vftv.core.research.model.ResearchRecord;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.nbt.EndTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.network.VarLong;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * The {@code vftv:research} attachment's value: the player's record, kept raw when unreadable, and
 * on the server the change that made it, which the owner's client receives as a delta after its
 * baseline (research spec, "Sync and state"). Only the record is persisted.
 *
 * @param record the record, readable or kept raw
 * @param change the change from the previous value, or null (a baseline, or nothing to send)
 */
public record ResearchAttachment(Retained<ResearchRecord> record, @Nullable ResearchDelta change) {
    /** A player who has heard nothing yet. */
    public static final ResearchAttachment EMPTY =
            new ResearchAttachment(new Retained.Read<>(ResearchRecord.EMPTY), null);

    private static final Codec<Note.Cell> CELL =
            RecordCodecBuilder.create(
                    i ->
                            i.group(
                                            Codec.INT.fieldOf("q").forGetter(Note.Cell::q),
                                            Codec.INT.fieldOf("r").forGetter(Note.Cell::r))
                                    .apply(i, Note.Cell::new));

    private static final Codec<Note.Placed> PLACED =
            RecordCodecBuilder.create(
                    i ->
                            i.group(
                                            Codec.INT.fieldOf("q").forGetter(p -> p.cell().q()),
                                            Codec.INT.fieldOf("r").forGetter(p -> p.cell().r()),
                                            ResearchCodecs.ID
                                                    .fieldOf("aspect")
                                                    .forGetter(Note.Placed::aspect))
                                    .apply(
                                            i,
                                            (q, r, a) -> new Note.Placed(new Note.Cell(q, r), a)));

    private static final Codec<Note.Snapshot> SNAPSHOT =
            RecordCodecBuilder.create(
                    i ->
                            i.group(
                                            Codec.INT
                                                    .fieldOf("min_distance")
                                                    .forGetter(Note.Snapshot::minDistance),
                                            Codec.INT
                                                    .fieldOf("silent_cells")
                                                    .forGetter(Note.Snapshot::silentCells),
                                            ResearchCodecs.enumCodec(
                                                            Note.PaletteHelp.values(),
                                                            Note.PaletteHelp::id)
                                                    .fieldOf("palette_help")
                                                    .forGetter(Note.Snapshot::paletteHelp),
                                            Codec.INT
                                                    .fieldOf("place_cost")
                                                    .forGetter(Note.Snapshot::placeCost),
                                            Codec.INT
                                                    .fieldOf("parent_cost")
                                                    .forGetter(Note.Snapshot::parentCost))
                                    .apply(i, Note.Snapshot::new));

    private static final Codec<Note> NOTE =
            RecordCodecBuilder.create(
                    i ->
                            i.group(
                                            ResearchCodecs.ID
                                                    .fieldOf("entry")
                                                    .forGetter(Note::entry),
                                            SNAPSHOT.fieldOf("snapshot").forGetter(Note::snapshot),
                                            Codec.INT.fieldOf("radius").forGetter(Note::radius),
                                            PLACED.listOf()
                                                    .fieldOf("anchors")
                                                    .forGetter(Note::anchors),
                                            CELL.listOf().fieldOf("silent").forGetter(Note::silent),
                                            PLACED.listOf()
                                                    .fieldOf("placements")
                                                    .forGetter(Note::placements),
                                            Codec.LONG
                                                    .fieldOf("revision")
                                                    .forGetter(Note::revision))
                                    .apply(i, Note::new));

    private static final Codec<NavigableSet<String>> IDS =
            ResearchCodecs.ID.listOf().xmap(TreeSet::new, ArrayList::new);

    /**
     * The bounded record codec: format bounds only, every field written, so raw retention sees any
     * key it doesn't know (conventions, "Raw-record retention").
     */
    public static final Codec<ResearchRecord> RECORD =
            RecordCodecBuilder.create(
                    i ->
                            i.group(
                                            Codec.INT
                                                    .fieldOf("format")
                                                    .forGetter(ResearchRecord::format),
                                            Codec.INT
                                                    .fieldOf("stage")
                                                    .forGetter(ResearchRecord::stage),
                                            IDS.fieldOf("learned")
                                                    .forGetter(ResearchRecord::learned),
                                            IDS.fieldOf("revealed")
                                                    .forGetter(ResearchRecord::revealed),
                                            IDS.fieldOf("latched")
                                                    .forGetter(ResearchRecord::latched),
                                            IDS.fieldOf("reveal_latched")
                                                    .forGetter(ResearchRecord::revealLatched),
                                            NOTE.listOf()
                                                    .<java.util.NavigableMap<String, Note>>xmap(
                                                            l ->
                                                                    new TreeMap<>(
                                                                            l.stream()
                                                                                    .collect(
                                                                                            Collectors
                                                                                                    .toMap(
                                                                                                            Note
                                                                                                                    ::entry,
                                                                                                            n ->
                                                                                                                    n))),
                                                            m -> List.copyOf(m.values()))
                                                    .fieldOf("notes")
                                                    .forGetter(ResearchRecord::notes),
                                            ResearchCodecs.ID
                                                    .listOf()
                                                    .fieldOf("bookmarks")
                                                    .forGetter(ResearchRecord::bookmarks),
                                            Codec.LONG
                                                    .fieldOf("revision")
                                                    .forGetter(ResearchRecord::revision),
                                            IDS.fieldOf("rewarded")
                                                    .forGetter(ResearchRecord::rewarded),
                                            Codec.unboundedMap(ResearchCodecs.ID, Codec.INT)
                                                    .<java.util.NavigableMap<String, Integer>>xmap(
                                                            TreeMap::new, m -> m)
                                                    .fieldOf("attempts")
                                                    .forGetter(ResearchRecord::attempts))
                                    .apply(i, ResearchRecord::new));

    /** The persisted form: the record, raw when unreadable; the change is never saved. */
    public static final Codec<ResearchAttachment> CODEC =
            RawRecordCodec.wrap(RECORD)
                    .xmap(r -> new ResearchAttachment(r, null), ResearchAttachment::record);

    /** The readable record, or null when it is kept raw. */
    public @Nullable ResearchRecord readable() {
        return record instanceof Retained.Read<ResearchRecord> r ? r.value() : null;
    }

    // --- Sync: a baseline, or a delta (research spec, "Bounded codecs"). ---

    private static final int UNREADABLE = 0;
    private static final int BASELINE = 1;
    private static final int DELTA = 2;

    private static final StreamCodec<ByteBuf, String> ID_STREAM =
            ByteBufCodecs.STRING_UTF8.map(ResearchAttachment::checkId, Function.identity());

    private static final StreamCodec<ByteBuf, List<String>> ID_LIST =
            ID_STREAM.apply(ByteBufCodecs.list(ResearchRecord.MAX_IDS));

    private static final StreamCodec<ByteBuf, List<String>> BOOKMARKS =
            ID_STREAM.apply(ByteBufCodecs.list(ResearchLimits.MAX_BOOKMARKS));

    private static final StreamCodec<ByteBuf, Note> NOTE_STREAM =
            StreamCodec.of(ResearchAttachment::writeNote, ResearchAttachment::readNote);

    private static final StreamCodec<ByteBuf, List<Note>> NOTES =
            NOTE_STREAM.apply(ByteBufCodecs.list(ResearchLimits.MAX_OPEN_NOTES));

    /** The owner-only sync: an unreadable flag, a baseline of the synced fields, or a delta. */
    public static final DeltaSync<ResearchAttachment> SYNC =
            new DeltaSync<>() {
                @Override
                public void write(
                        RegistryFriendlyByteBuf buf, ResearchAttachment value, boolean baseline) {
                    ResearchRecord record = value.readable();
                    ResearchDelta change = value.change();
                    if (record == null) {
                        buf.writeByte(UNREADABLE);
                    } else if (baseline || change == null) {
                        buf.writeByte(BASELINE);
                        writeBaseline(buf, record);
                    } else {
                        buf.writeByte(DELTA);
                        writeDelta(buf, change);
                    }
                }

                @Override
                public ResearchAttachment read(
                        RegistryFriendlyByteBuf buf, @Nullable ResearchAttachment previous) {
                    int kind = buf.readByte();
                    return switch (kind) {
                        case UNREADABLE -> unreadableOnServer();
                        case BASELINE ->
                                new ResearchAttachment(
                                        new Retained.Read<>(readBaseline(buf)), null);
                        case DELTA -> {
                            ResearchDelta delta = readDelta(buf);
                            ResearchRecord before = previous == null ? null : previous.readable();
                            yield new ResearchAttachment(
                                    new Retained.Read<>(
                                            delta.applyTo(
                                                    before == null
                                                            ? ResearchRecord.EMPTY
                                                            : before)),
                                    null);
                        }
                        default -> throw new DecoderException("unknown research sync kind " + kind);
                    };
                }
            };

    /** What the client holds for a record its server can't read: no fields, only the flag. */
    static ResearchAttachment unreadableOnServer() {
        return new ResearchAttachment(
                new Retained.Unreadable<>(EndTag.INSTANCE, "unreadable on the server"), null);
    }

    static void writeBaseline(ByteBuf buf, ResearchRecord r) {
        VarInt.write(buf, r.stage());
        VarLong.write(buf, r.revision());
        ID_LIST.encode(buf, List.copyOf(r.learned()));
        ID_LIST.encode(buf, List.copyOf(r.revealed()));
        ID_LIST.encode(buf, List.copyOf(r.latched()));
        ID_LIST.encode(buf, List.copyOf(r.revealLatched()));
        BOOKMARKS.encode(buf, r.bookmarks());
        NOTES.encode(buf, List.copyOf(r.notes().values()));
    }

    static ResearchRecord readBaseline(ByteBuf buf) {
        int stage = VarInt.read(buf);
        long revision = VarLong.read(buf);
        NavigableSet<String> learned = new TreeSet<>(ID_LIST.decode(buf));
        NavigableSet<String> revealed = new TreeSet<>(ID_LIST.decode(buf));
        NavigableSet<String> latched = new TreeSet<>(ID_LIST.decode(buf));
        NavigableSet<String> revealLatched = new TreeSet<>(ID_LIST.decode(buf));
        List<String> bookmarks = BOOKMARKS.decode(buf);
        List<Note> notes = NOTES.decode(buf);
        return checked(
                () ->
                        new ResearchRecord(
                                ResearchRecord.FORMAT,
                                stage,
                                learned,
                                revealed,
                                latched,
                                revealLatched,
                                new TreeMap<>(
                                        notes.stream()
                                                .collect(Collectors.toMap(Note::entry, n -> n))),
                                bookmarks,
                                revision,
                                new TreeSet<>(),
                                new TreeMap<>()));
    }

    static void writeDelta(ByteBuf buf, ResearchDelta d) {
        VarInt.write(buf, d.stage());
        VarLong.write(buf, d.revision());
        for (ResearchDelta.Ids ids :
                List.of(d.learned(), d.revealed(), d.latched(), d.revealLatched())) {
            ID_LIST.encode(buf, ids.added());
            ID_LIST.encode(buf, ids.removed());
        }
        BOOKMARKS.encode(buf, d.bookmarks());
        NOTES.encode(buf, d.notes());
        ID_STREAM.apply(ByteBufCodecs.list(ResearchLimits.MAX_OPEN_NOTES)).encode(buf, d.dropped());
    }

    static ResearchDelta readDelta(ByteBuf buf) {
        int stage = VarInt.read(buf);
        long revision = VarLong.read(buf);
        List<ResearchDelta.Ids> sets = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            sets.add(new ResearchDelta.Ids(ID_LIST.decode(buf), ID_LIST.decode(buf)));
        }
        List<String> bookmarks = BOOKMARKS.decode(buf);
        List<Note> notes = NOTES.decode(buf);
        List<String> dropped =
                ID_STREAM.apply(ByteBufCodecs.list(ResearchLimits.MAX_OPEN_NOTES)).decode(buf);
        if (stage < 0 || stage > 4 || revision < 0) {
            throw new DecoderException("research delta out of range");
        }
        return new ResearchDelta(
                stage,
                revision,
                sets.get(0),
                sets.get(1),
                sets.get(2),
                sets.get(3),
                bookmarks,
                notes,
                dropped);
    }

    private static void writeNote(ByteBuf buf, Note n) {
        ID_STREAM.encode(buf, n.entry());
        Note.Snapshot s = n.snapshot();
        VarInt.write(buf, s.minDistance());
        VarInt.write(buf, s.silentCells());
        buf.writeBoolean(s.paletteHelp() == Note.PaletteHelp.PARENTS);
        VarInt.write(buf, s.placeCost());
        VarInt.write(buf, s.parentCost());
        VarInt.write(buf, n.radius());
        writePlaced(buf, n.anchors());
        VarInt.write(buf, n.silent().size());
        n.silent().forEach(c -> writeCell(buf, c));
        writePlaced(buf, n.placements());
        VarLong.write(buf, n.revision());
    }

    private static Note readNote(ByteBuf buf) {
        String entry = ID_STREAM.decode(buf);
        int minDistance = VarInt.read(buf);
        int silentCells = VarInt.read(buf);
        Note.PaletteHelp help =
                buf.readBoolean() ? Note.PaletteHelp.PARENTS : Note.PaletteHelp.NEIGHBOURS;
        int placeCost = VarInt.read(buf);
        int parentCost = VarInt.read(buf);
        int radius = VarInt.read(buf);
        List<Note.Placed> anchors = readPlaced(buf, ResearchLimits.MAX_ANCHORS);
        int silentCount = count(buf, Note.MAX_SILENT);
        List<Note.Cell> silent = new ArrayList<>(silentCount);
        for (int i = 0; i < silentCount; i++) {
            silent.add(readCell(buf));
        }
        List<Note.Placed> placements = readPlaced(buf, 169);
        long revision = VarLong.read(buf);
        return checked(
                () ->
                        new Note(
                                entry,
                                new Note.Snapshot(
                                        minDistance, silentCells, help, placeCost, parentCost),
                                radius,
                                anchors,
                                silent,
                                placements,
                                revision));
    }

    private static void writePlaced(ByteBuf buf, List<Note.Placed> placed) {
        VarInt.write(buf, placed.size());
        for (Note.Placed p : placed) {
            writeCell(buf, p.cell());
            ID_STREAM.encode(buf, p.aspect());
        }
    }

    private static List<Note.Placed> readPlaced(ByteBuf buf, int max) {
        int n = count(buf, max);
        List<Note.Placed> placed = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Note.Cell cell = readCell(buf);
            placed.add(new Note.Placed(cell, ID_STREAM.decode(buf)));
        }
        return placed;
    }

    private static void writeCell(ByteBuf buf, Note.Cell c) {
        buf.writeByte(c.q());
        buf.writeByte(c.r());
    }

    private static Note.Cell readCell(ByteBuf buf) {
        return new Note.Cell(buf.readByte(), buf.readByte());
    }

    private static int count(ByteBuf buf, int max) {
        int n = VarInt.read(buf);
        if (n < 0 || n > max) {
            throw new DecoderException(n + " elements, over " + max);
        }
        return n;
    }

    private static String checkId(String id) {
        if (Identifier.tryParse(id) == null) {
            throw new DecoderException("not an id: " + id);
        }
        return id;
    }

    /** Builds a decoded value, turning a failed invariant into a decode error. */
    private static <T> T checked(java.util.function.Supplier<T> build) {
        try {
            return build.get();
        } catch (IllegalArgumentException e) {
            throw new DecoderException(e.getMessage(), e);
        }
    }
}
