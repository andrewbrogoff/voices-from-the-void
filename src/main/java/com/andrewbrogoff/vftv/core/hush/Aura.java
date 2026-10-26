package com.andrewbrogoff.vftv.core.hush;

import com.andrewbrogoff.vftv.core.common.RawRecordCodec;
import com.andrewbrogoff.vftv.core.common.RawRecordCodec.Retained;
import com.mojang.serialization.Codec;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

/**
 * One chunk's {@code vftv:aura} (Hush spec, "The record"): created once by the first retaining
 * deposit and mutated in place after that, so a write never re-syncs by itself. An unreadable aura
 * keeps its raw tag and is never a candidate, never cleared and never written.
 */
public final class Aura {
    /** The attachment's codec: the raw tag is kept for an unreadable record. */
    public static final Codec<Aura> CODEC =
            RawRecordCodec.wrap(HushRecords.AURA).xmap(Aura::of, Aura::retained);

    private @Nullable Tag raw;
    private String reason = "";

    /** Pooled flux, 0 to {@code FLUX_FORMAT_MAX}. */
    int flux;

    /** The seep's {@code asLong()}, meaningful iff {@link #hasSeep}. */
    long seep;

    boolean hasSeep;

    @Nullable UUID spiller;

    /** Attempts made: the stream index, a wrapping word. */
    long steps;

    /** Blocks converted: the Hollow turn, a wrapping word. */
    long taken;

    /** An empty readable aura, as the attachment's default and a repair. */
    public Aura() {}

    private static Aura of(Retained<HushRecords.AuraData> retained) {
        Aura a = new Aura();
        switch (retained) {
            case Retained.Read<HushRecords.AuraData> r -> {
                HushRecords.AuraData d = r.value();
                a.flux = d.flux();
                a.hasSeep = d.seep().isPresent();
                a.seep = d.seep().orElse(0L);
                a.spiller = d.spiller().orElse(null);
                a.steps = d.steps();
                a.taken = d.taken();
            }
            case Retained.Unreadable<HushRecords.AuraData> u -> {
                a.raw = u.raw();
                a.reason = u.reason();
            }
        }
        return a;
    }

    private Retained<HushRecords.AuraData> retained() {
        Tag r = raw;
        return r != null ? new Retained.Unreadable<>(r, reason) : new Retained.Read<>(data());
    }

    private HushRecords.AuraData data() {
        return new HushRecords.AuraData(
                HushRecords.FORMAT,
                flux,
                hasSeep ? Optional.of(seep) : Optional.empty(),
                Optional.ofNullable(spiller),
                steps,
                taken);
    }

    /** Whether the record decoded. */
    public boolean readable() {
        return raw == null;
    }

    /** Why it is unreadable, or empty. */
    public String reason() {
        return reason;
    }

    /** The raw tag of an unreadable record, or null. */
    public @Nullable Tag raw() {
        return raw;
    }

    /** Pooled flux; 0 while unreadable. */
    public int flux() {
        return raw == null ? 0 : flux;
    }

    /** The seep, or null. */
    public @Nullable BlockPos seep() {
        return raw == null && hasSeep ? BlockPos.of(seep) : null;
    }

    /** The last known spiller, or null. */
    public @Nullable UUID spiller() {
        return spiller;
    }

    /** Attempts made. */
    public long steps() {
        return steps;
    }

    /** Blocks converted. */
    public long taken() {
        return taken;
    }

    /**
     * Applies the invariant decoding can't check: a seep outside its chunk makes the record
     * unreadable, keeping the tag as decoded.
     *
     * @return whether the record became unreadable
     */
    boolean checkSeep(ChunkPos chunk) {
        if (raw == null && hasSeep && !ChunkPos.containing(BlockPos.of(seep)).equals(chunk)) {
            raw = HushRecords.AURA.encodeStart(NbtOps.INSTANCE, data()).getOrThrow();
            reason = "seep " + BlockPos.of(seep).toShortString() + " outside chunk " + chunk;
            return true;
        }
        return false;
    }

    /** Replaces an unreadable record with an empty readable one (the repair command). */
    void repair() {
        raw = null;
        reason = "";
        flux = 0;
        hasSeep = false;
        seep = 0;
        spiller = null;
        steps = 0;
        taken = 0;
    }
}
