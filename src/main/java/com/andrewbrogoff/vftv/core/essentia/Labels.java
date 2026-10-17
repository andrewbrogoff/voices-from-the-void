package com.andrewbrogoff.vftv.core.essentia;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.core.text.StatusLines;
import com.andrewbrogoff.vftv.core.text.model.SharedMessages;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Applying, copying and cutting labels (essentia spec, "Labels"): the first-match table every jar,
 * tube and Alembic runs from {@code useItemOn}. The client computes the same result without side
 * effects; only the server changes anything.
 */
public final class Labels {
    /** "I can't read this label." */
    public static final String LABEL_UNREADABLE = "message.vftv.essentia.label_unreadable";

    /** "It's already labelled. I'd have to cut that off first." */
    public static final String CUT_FIRST = "message.vftv.essentia.cut_label_first";

    /** "There's nothing to name it by." */
    public static final String NOTHING_TO_NAME = "message.vftv.essentia.nothing_to_name";

    /** "That isn't what's inside." */
    public static final String MISMATCH = "message.vftv.essentia.label_mismatch";

    /** A block entity that carries a label. */
    public interface Target {
        /** Whether its record reads. */
        boolean readable();

        /** Whether it is transaction-busy (jars only). */
        boolean busy();

        /** Its label, or null. */
        @Nullable ResourceKey<Aspect> label();

        /** What a jar holds, or null. */
        @Nullable ResourceKey<Aspect> held();

        /** Whether it is a jar. */
        boolean isJar();

        /** Replaces the label (server only). */
        void setLabel(@Nullable ResourceKey<Aspect> label);
    }

    /** {@code #c:tools/shear}, NeoForge's {@code Tags.Items.TOOLS_SHEAR}. */
    public static final TagKey<Item> SHEARS =
            TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "tools/shear"));

    private Labels() {}

    /** A label stack naming {@code aspect}, or blank for null. */
    public static ItemStack labelStack(@Nullable ResourceKey<Aspect> aspect) {
        ItemStack stack = new ItemStack(EssentiaContent.label());
        if (aspect != null) {
            stack.set(EssentiaContent.labelComponent(), aspect);
        }
        return stack;
    }

    /** The table, for a held stack on a target. */
    public static InteractionResult use(
            ItemStack held,
            Target target,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand) {
        boolean label = held.is(EssentiaContent.label());
        boolean shears = held.is(SHEARS);
        if (!label && !shears) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!target.readable()) {
            return refuse(player, SharedMessages.UNREADABLE);
        }
        if (target.isJar() && target.busy()) {
            return refuse(player, SharedMessages.BUSY);
        }
        ResourceKey<Aspect> current = target.label();
        if (shears) {
            if (current == null) {
                return InteractionResult.TRY_WITH_EMPTY_HAND;
            }
            if (player instanceof ServerPlayer sp) {
                target.setLabel(null);
                sp.getInventory().placeItemBackInInventory(labelStack(current));
                held.hurtAndBreak(
                        1,
                        sp,
                        hand == InteractionHand.MAIN_HAND
                                ? EquipmentSlot.MAINHAND
                                : EquipmentSlot.OFFHAND);
                level.playSound(
                        null, pos, EssentiaContent.labelCut(), SoundSource.BLOCKS, 1.0f, 1.0f);
            }
            return InteractionResult.SUCCESS;
        }
        ResourceKey<Aspect> x = held.get(EssentiaContent.labelComponent());
        if (x != null && !EssentiaService.labelAdmits(x, level)) {
            return refuse(player, LABEL_UNREADABLE);
        }
        if (current != null) {
            return x != null && x.equals(current)
                    ? InteractionResult.CONSUME
                    : refuse(player, CUT_FIRST);
        }
        ResourceKey<Aspect> inside = target.held();
        ResourceKey<Aspect> apply;
        if (x == null) {
            if (!target.isJar() || inside == null) {
                return refuse(player, NOTHING_TO_NAME);
            }
            apply = inside;
        } else {
            if (target.isJar() && inside != null && !inside.equals(x)) {
                return refuse(player, MISMATCH);
            }
            apply = x;
        }
        if (player instanceof ServerPlayer) {
            target.setLabel(apply);
            if (!player.hasInfiniteMaterials()) {
                held.shrink(1);
            }
            level.playSound(
                    null, pos, EssentiaContent.labelApply(), SoundSource.BLOCKS, 1.0f, 1.0f);
        }
        return InteractionResult.SUCCESS;
    }

    static InteractionResult refuse(Player player, String key) {
        if (player instanceof ServerPlayer sp) {
            StatusLines.send(sp, Component.translatable(key));
        }
        return InteractionResult.CONSUME;
    }
}
