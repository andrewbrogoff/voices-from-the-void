package com.andrewbrogoff.vftv.core.infusion;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.api.infusion.InfusionCost;
import com.andrewbrogoff.vftv.api.infusion.InfusionLimits;
import com.andrewbrogoff.vftv.api.infusion.InfusionPlan;
import com.andrewbrogoff.vftv.api.vis.VisKind;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * What the server read at an altar (infusion spec, "The readout"): the status and its arguments,
 * the bowl's place, and for {@code READY} the committed-to-be assessment (the API's {@link
 * InfusionPlan}, whose costs carry "held now"), for {@code RINGING} the run's frozen figures and
 * live counters. Immutable; the HUD, the commit's recheck and the preview read it.
 *
 * @param status the status
 * @param reason the enchanting refusal's reason, for {@code ENCHANT_REFUSED}
 * @param args the status key's arguments
 * @param dimension the bowl's dimension
 * @param pos the bowl's position
 * @param ready the assessment, for {@code READY}
 * @param run the run's figures, for {@code RINGING}
 * @param changed whether this answers a {@code STALE} refusal (the HUD's {@code .changed} line)
 */
public record InfusionReadout(
        InfusionStatus status,
        InfusionStatus.@Nullable EnchantRefusal reason,
        List<Component> args,
        ResourceKey<Level> dimension,
        BlockPos pos,
        @Nullable InfusionPlan ready,
        @Nullable Run run,
        boolean changed) {
    /** Most status arguments. */
    private static final int MAX_ARGS = 3;

    /** Most units of one cost cell over a whole run: 8 entries of 1000 at worst. */
    private static final int MAX_CELL = 8000;

    /** Why a run isn't progressing, if it isn't. */
    public enum Stall {
        /** Progressing. */
        NONE,
        /** Nothing drawn: the vessels are short. */
        ESSENTIA,
        /** A component's pedestal isn't holding the promised stack. */
        COMPONENT,
        /** A box chunk is unloaded or not block-ticking. */
        PAUSED
    }

    /**
     * A run readout's figures.
     *
     * @param recipe the run's recipe
     * @param output the frozen output
     * @param stability the frozen {@code S}
     * @param chance the frozen {@code p}
     * @param drawn units drawn in all
     * @param owed units owed in all
     * @param costs each cost aspect with its frozen effective cost ({@code heldNow} 0)
     * @param cells each cost aspect with its units still owed ({@code heldNow} 0)
     * @param absorbed components absorbed
     * @param components components in all
     * @param upsets upsets so far
     * @param upsetMax the frozen {@code upset.max}
     * @param stall why it waits, if it does
     * @param missing the stalled component's pedestal, for {@code COMPONENT}
     * @param upsetFlux the upset flux bound under the run's frozen rules
     * @param abandonFlux {@code essentiaFlux(drawn, ABANDONED, rules)} under the current rules
     * @param abandonArmed whether an abandon is armed
     */
    public record Run(
            ResourceKey<Recipe<?>> recipe,
            ItemStack output,
            int stability,
            int chance,
            int drawn,
            int owed,
            List<InfusionCost> costs,
            List<InfusionCost> cells,
            int absorbed,
            int components,
            int upsets,
            int upsetMax,
            Stall stall,
            @Nullable BlockPos missing,
            long upsetFlux,
            int abandonFlux,
            boolean abandonArmed) {
        /** Copies. */
        public Run {
            output = output.copy();
            costs = List.copyOf(costs);
            cells = List.copyOf(cells);
        }
    }

    /** Copies the arguments. */
    public InfusionReadout {
        args = List.copyOf(args);
        pos = pos.immutable();
    }

    /** This readout answering a {@code STALE} refusal. */
    public InfusionReadout asChanged() {
        return new InfusionReadout(status, reason, args, dimension, pos, ready, run, true);
    }

    /** The status's line-1 key. */
    public String key() {
        return status.key(reason);
    }

    /** The recipe this readout is for: the ready assessment's or the run's; null otherwise. */
    public @Nullable ResourceKey<Recipe<?>> recipe() {
        InfusionPlan r = ready;
        Run n = run;
        return r != null ? r.recipe() : n != null ? n.recipe() : null;
    }

    /**
     * The bounded wire form: at most 3 arguments, at most 8 cost cells with costs 1-8,000 and
     * held-now 0-99,999 (VarInts), ids at most 256 characters, outputs through vanilla's item
     * stream codec.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, InfusionReadout> STREAM_CODEC =
            StreamCodec.of(InfusionReadout::write, InfusionReadout::read);

    private static void write(RegistryFriendlyByteBuf buf, InfusionReadout r) {
        buf.writeEnum(r.status);
        buf.writeBoolean(r.reason != null);
        if (r.reason != null) {
            buf.writeEnum(r.reason);
        }
        buf.writeVarInt(r.args.size());
        r.args.forEach(a -> ComponentSerialization.STREAM_CODEC.encode(buf, a));
        buf.writeResourceKey(r.dimension);
        buf.writeBlockPos(r.pos);
        buf.writeBoolean(r.changed);
        InfusionPlan plan = r.ready;
        buf.writeBoolean(plan != null);
        if (plan != null) {
            writeId(buf, plan.recipe().identifier());
            buf.writeBoolean(plan.kind() == VisKind.VOID);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, plan.output());
            for (int v :
                    new int[] {
                        plan.instability(),
                        plan.warpPenalty(),
                        plan.lonePedestals(),
                        plan.loneItems(),
                        plan.stabiliserPairs(),
                        plan.stabiliserBonus(),
                        plan.bonusCap(),
                        plan.stability(),
                        plan.upsetChance(),
                        plan.nominal(),
                        plan.worst(),
                        plan.ceiling(),
                        plan.cleanFloor(),
                        plan.cleanFullSupply(),
                        plan.upsetMax(),
                        plan.upsetSpill()
                    }) {
                buf.writeVarInt(v);
            }
            buf.writeVarLong(plan.upsetFluxBound());
            writeCells(buf, plan.costs());
            buf.writeBoolean(plan.stabilisersCapped());
            buf.writeBoolean(plan.clamped());
        }
        Run run = r.run;
        buf.writeBoolean(run != null);
        if (run != null) {
            writeId(buf, run.recipe().identifier());
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, run.output());
            for (int v :
                    new int[] {
                        run.stability(),
                        run.chance(),
                        run.drawn(),
                        run.owed(),
                        run.absorbed(),
                        run.components(),
                        run.upsets(),
                        run.upsetMax(),
                        run.abandonFlux()
                    }) {
                buf.writeVarInt(v);
            }
            writeCells(buf, run.costs());
            writeCells(buf, run.cells());
            buf.writeEnum(run.stall());
            buf.writeBoolean(run.missing() != null);
            if (run.missing() != null) {
                buf.writeBlockPos(run.missing());
            }
            buf.writeVarLong(run.upsetFlux());
            buf.writeBoolean(run.abandonArmed());
        }
    }

    private static InfusionReadout read(RegistryFriendlyByteBuf buf) {
        InfusionStatus status = buf.readEnum(InfusionStatus.class);
        InfusionStatus.EnchantRefusal reason =
                buf.readBoolean() ? buf.readEnum(InfusionStatus.EnchantRefusal.class) : null;
        int n = bounded(buf.readVarInt(), 0, MAX_ARGS, "arguments");
        List<Component> args = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            args.add(ComponentSerialization.STREAM_CODEC.decode(buf));
        }
        ResourceKey<Level> dimension = buf.readResourceKey(Registries.DIMENSION);
        BlockPos pos = buf.readBlockPos();
        boolean changed = buf.readBoolean();
        InfusionPlan plan = null;
        if (buf.readBoolean()) {
            ResourceKey<Recipe<?>> recipe = ResourceKey.create(Registries.RECIPE, readId(buf));
            VisKind kind = buf.readBoolean() ? VisKind.VOID : VisKind.ORDINARY;
            ItemStack output = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
            int[] v = new int[16];
            for (int i = 0; i < v.length; i++) {
                v[i] = bounded(buf.readVarInt(), 0, InfusionLimits.MAX_ROLLS, "figure");
            }
            long flux = buf.readVarLong();
            if (flux < 0 || flux > 64_000_000L) {
                throw new DecoderException("upset flux " + flux);
            }
            List<InfusionCost> cells = readCells(buf);
            plan =
                    new InfusionPlan(
                            recipe,
                            kind,
                            output,
                            v[0],
                            v[1],
                            v[2],
                            v[3],
                            v[4],
                            v[5],
                            v[6],
                            v[7],
                            v[8],
                            v[9],
                            v[10],
                            v[11],
                            v[12],
                            v[13],
                            v[14],
                            v[15],
                            flux,
                            cells,
                            buf.readBoolean(),
                            buf.readBoolean());
        }
        Run run = null;
        if (buf.readBoolean()) {
            ResourceKey<Recipe<?>> recipe = ResourceKey.create(Registries.RECIPE, readId(buf));
            ItemStack output = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
            int[] v = new int[9];
            for (int i = 0; i < v.length; i++) {
                v[i] = bounded(buf.readVarInt(), 0, 1_000_000, "figure");
            }
            List<InfusionCost> costs = readCells(buf);
            List<InfusionCost> cells = readCells(buf);
            Stall stall = buf.readEnum(Stall.class);
            BlockPos missing = buf.readBoolean() ? buf.readBlockPos() : null;
            long flux = buf.readVarLong();
            run =
                    new Run(
                            recipe,
                            output,
                            v[0],
                            v[1],
                            v[2],
                            v[3],
                            costs,
                            cells,
                            v[4],
                            v[5],
                            v[6],
                            v[7],
                            stall,
                            missing,
                            flux,
                            v[8],
                            buf.readBoolean());
        }
        return new InfusionReadout(status, reason, args, dimension, pos, plan, run, changed);
    }

    private static void writeCells(RegistryFriendlyByteBuf buf, List<InfusionCost> cells) {
        buf.writeVarInt(cells.size());
        for (InfusionCost c : cells) {
            writeId(buf, c.aspect().identifier());
            buf.writeVarInt(c.cost());
            buf.writeVarInt(c.heldNow());
        }
    }

    private static List<InfusionCost> readCells(RegistryFriendlyByteBuf buf) {
        int n = bounded(buf.readVarInt(), 0, InfusionLimits.MAX_COST_ENTRIES, "cost cells");
        List<InfusionCost> cells = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            ResourceKey<Aspect> aspect = ResourceKey.create(AspectKeys.REGISTRY, readId(buf));
            int cost = bounded(buf.readVarInt(), 0, MAX_CELL, "cost");
            int held = bounded(buf.readVarInt(), 0, InfusionLimits.HELD_NOW_MAX, "held now");
            cells.add(new InfusionCost(aspect, cost, held));
        }
        return cells;
    }

    private static void writeId(RegistryFriendlyByteBuf buf, Identifier id) {
        buf.writeUtf(id.toString(), InfusionLimits.MAX_ID_CHARS);
    }

    private static Identifier readId(RegistryFriendlyByteBuf buf) {
        String text = buf.readUtf(InfusionLimits.MAX_ID_CHARS);
        Identifier id = Identifier.tryParse(text);
        if (id == null) {
            throw new DecoderException("not an id: " + text);
        }
        return id;
    }

    private static int bounded(int value, int min, int max, String what) {
        if (value < min || value > max) {
            throw new DecoderException(what + " " + value + " outside " + min + ".." + max);
        }
        return value;
    }
}
