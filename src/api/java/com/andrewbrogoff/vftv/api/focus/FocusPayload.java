package com.andrewbrogoff.vftv.api.focus;

import com.andrewbrogoff.vftv.api.Internal;
import com.andrewbrogoff.vftv.api.vis.VisKind;
import java.util.List;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * What a focus event reports: a committed cast, compose or rank-up. A blank focus (no effect and no
 * medium) can be ranked, so its effect, medium and kind are absent. See the foci spec, "Events".
 *
 * @param kind what happened
 * @param player the player who cast, composed or ranked; a live handle, valid only during dispatch
 * @param rank the focus's rank after the change, 0 to {@link FocusLimits#MAX_RANK}
 * @param modifiers the ids of the focus's modifiers, unmodifiable
 * @param effect the focus's effect, or {@code null} for a blank focus
 * @param medium the focus's medium, or {@code null} for a blank focus
 * @param visKind the focus's kind ({@code VOID} iff its effect is a Void effect), or {@code null}
 *     for a blank focus
 * @param gesture for {@code CAST}, the gesture that started it; {@code null} otherwise
 * @param attempted for {@code CAST}, the number of targets or positions resolved before the spend
 *     (0 for a Bolt, which is a launch); 0 otherwise
 */
public record FocusPayload(
        FocusEventKind kind,
        ServerPlayer player,
        int rank,
        List<ResourceKey<FocusModifier>> modifiers,
        @Nullable ResourceKey<FocusEffect> effect,
        @Nullable ResourceKey<FocusMedium> medium,
        @Nullable VisKind visKind,
        @Nullable FocusGesture gesture,
        int attempted) {
    /** Constructed by vftv only. */
    @Internal
    public FocusPayload {
        modifiers = List.copyOf(modifiers);
    }
}
