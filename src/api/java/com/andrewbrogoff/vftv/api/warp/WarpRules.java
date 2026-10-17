package com.andrewbrogoff.vftv.api.warp;

import com.andrewbrogoff.vftv.api.Internal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The warp tuning in force, with the four warp server switches the client needs: a fixed, immutable
 * snapshot that every player receives, so client and server compute the same dials. Each value is
 * within the range the warp spec's "Tuning and limits" gives for its key.
 *
 * <p>Internal: public only for cross-feature wiring. See the warp spec, "Sync and state".
 *
 * @param format the record's format, 1
 * @param bands {@code bands}: the three strictly ascending band thresholds on total warp
 * @param temporaryDecayInterval {@code temporary.decay_interval}: ticks per temporary point faded
 * @param taxPerWarpPm {@code tax.per_warp_pm}: the ordinary vis surcharge per warp, per mille
 * @param taxMaxPm {@code tax.max_pm}: the largest ordinary vis surcharge, per mille
 * @param infusionPerWarpPm {@code infusion.per_warp_pm}: stability lost per 1000 warp
 * @param infusionMaxPenalty {@code infusion.max_penalty}: the largest stability penalty
 * @param checkInterval {@code check.interval}: ticks between a player's Voice checks
 * @param counterPerGain {@code counter.per_gain}: counter added per warp point gained
 * @param counterPerEvent {@code counter.per_event}: counter removed per fired event
 * @param counterMax {@code counter.max}: the largest counter read
 * @param counterPerVoidWork {@code counter.per_void_work}: counter added per Void spend
 * @param chanceBasePm {@code chance.base_pm}: the chance with warp, per mille
 * @param chancePerPermanentPm {@code chance.per_permanent_pm}: per permanent warp
 * @param chancePerCounterPm {@code chance.per_counter_pm}: per counter point
 * @param chanceMaxPm {@code chance.max_pm}: the largest chance
 * @param grace {@code grace}: ticks without checks after a login, respawn or dimension change
 * @param contextWindow {@code context.window}: ticks a Void spend keeps a player in context
 * @param contextDimensions {@code context.dimensions}: the Void-touched level ids, at most 16
 * @param joiningAmplifierBonus {@code joining.amplifier_bonus}: warp a Joined amplifier adds
 * @param gearMax {@code gear.max}: the largest gear sample
 * @param bargainTtl {@code bargain.ttl}: ticks an offer stands
 * @param listenCostTemporary {@code listen.cost.temporary}: the Listen rite's temporary price
 * @param listenCostLasting {@code listen.cost.lasting}: the Listen rite's lasting price
 * @param listenCooldown {@code listen.cooldown}: ticks between a player's rites
 * @param stillAmount {@code still.amount}: lasting warp one Stilling Draught removes
 * @param permanentCap {@code permanent.cap}: the most permanent warp a gain reaches
 * @param permanentMilestones {@code permanent.milestones}: each milestone id with its permanent
 *     amount, sorted by id
 * @param voicesEnabled the server switch {@code warp.voices.enabled}
 * @param tax the server switch {@code warp.tax}
 * @param amplifier the server switch {@code warp.amplifier}
 * @param gear the server switch {@code warp.gear}
 */
@Internal
public record WarpRules(
        int format,
        List<Integer> bands,
        int temporaryDecayInterval,
        int taxPerWarpPm,
        int taxMaxPm,
        int infusionPerWarpPm,
        int infusionMaxPenalty,
        int checkInterval,
        int counterPerGain,
        int counterPerEvent,
        int counterMax,
        int counterPerVoidWork,
        int chanceBasePm,
        int chancePerPermanentPm,
        int chancePerCounterPm,
        int chanceMaxPm,
        int grace,
        int contextWindow,
        List<String> contextDimensions,
        int joiningAmplifierBonus,
        int gearMax,
        int bargainTtl,
        int listenCostTemporary,
        int listenCostLasting,
        int listenCooldown,
        int stillAmount,
        int permanentCap,
        Map<String, Integer> permanentMilestones,
        boolean voicesEnabled,
        boolean tax,
        boolean amplifier,
        boolean gear) {

    /** Constructed by vftv only; copies the collections, so the record is immutable. */
    @Internal
    public WarpRules {
        bands = List.copyOf(bands);
        contextDimensions = List.copyOf(contextDimensions);
        permanentMilestones = Collections.unmodifiableMap(new TreeMap<>(permanentMilestones));
    }
}
