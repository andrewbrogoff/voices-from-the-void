package com.andrewbrogoff.vftv.core.vis.client;

import com.andrewbrogoff.vftv.core.vis.NodeBlockEntity;
import com.andrewbrogoff.vftv.core.vis.Tapping;
import com.andrewbrogoff.vftv.core.vis.VisContent;
import com.andrewbrogoff.vftv.core.vis.VisRulesService;
import com.andrewbrogoff.vftv.core.vis.model.NodeClientNumbers;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The nodes' client ambience (wands spec, "Rendering and sound"): ambient glints at most once per
 * node per {@code glints.every_ticks} within {@code glints.range}, none at the minimal particle
 * setting; the hum, bounded by {@code hum.max_playing}; and the tapping player's two glints per
 * pulse. Client thread only.
 */
public final class NodeAmbience {
    /** How long a hum counts as playing, in ticks: the 3-second sound with its tail. */
    private static final int HUM_TICKS = 60;

    private static final Map<BlockPos, Long> NEXT_HUM = new HashMap<>();
    private static final Map<BlockPos, Long> PLAYING = new HashMap<>();
    private static volatile NodeClientNumbers numbers = NodeClientNumbers.DEFAULTS;

    private NodeAmbience() {}

    /** The numbers in force: the resource file's, or the defaults. */
    public static NodeClientNumbers numbers() {
        return numbers;
    }

    /** Takes reloaded numbers. */
    public static void installNumbers(NodeClientNumbers next) {
        numbers = next;
    }

    /** Forgets every hum, at disconnect. */
    public static void clear() {
        NEXT_HUM.clear();
        PLAYING.clear();
    }

    /** One client tick of one node. */
    public static void tick(Level level, BlockPos pos, NodeBlockEntity node) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || node.state() == null) {
            return;
        }
        NodeClientNumbers n = numbers;
        long now = level.getGameTime();
        Vec3 centre = Vec3.atCenterOf(pos);
        double distance = player.position().distanceTo(centre);
        if (distance <= n.glintRange()
                && Minecraft.getInstance().options.particles().get() != ParticleStatus.MINIMAL
                && Math.floorMod(now + pos.asLong(), (long) n.glintEvery()) == 0) {
            var r = level.getRandom();
            level.addParticle(
                    VisContent.glint(),
                    centre.x,
                    centre.y,
                    centre.z,
                    (r.nextDouble() - 0.5) * 0.04,
                    r.nextDouble() * 0.03,
                    (r.nextDouble() - 0.5) * 0.04);
        }
        hum(level, pos, now, distance <= n.humRange(), n);
    }

    private static void hum(
            Level level, BlockPos pos, long now, boolean inRange, NodeClientNumbers n) {
        if (!inRange) {
            NEXT_HUM.remove(pos);
            return;
        }
        Long next = NEXT_HUM.get(pos);
        int min = n.humInterval();
        if (next == null) {
            NEXT_HUM.put(pos.immutable(), now + min + level.getRandom().nextInt(min));
            return;
        }
        if (now < next) {
            return;
        }
        for (Iterator<Long> it = PLAYING.values().iterator(); it.hasNext(); ) {
            if (it.next() <= now) {
                it.remove();
            }
        }
        if (PLAYING.size() < n.humMaxPlaying()) {
            level.playLocalSound(pos, VisContent.sound(4), SoundSource.AMBIENT, 1, 1, false);
            PLAYING.put(pos.immutable(), now + HUM_TICKS);
        }
        NEXT_HUM.put(pos.immutable(), now + min + level.getRandom().nextInt(min));
    }

    /** The tapping player's glints: two per aligned pulse, from the node toward the hand. */
    public static void useTick(Level level, LivingEntity entity, ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (!(entity instanceof LocalPlayer)
                || level.getGameTime() % VisRulesService.tuning(level).tapInterval() != 0
                || !(mc.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK
                || !(level.getBlockEntity(hit.getBlockPos()) instanceof NodeBlockEntity)) {
            return;
        }
        Vec3 from = Vec3.atCenterOf(hit.getBlockPos());
        Vec3 toward = entity.getEyePosition().subtract(0, 0.4, 0).subtract(from).scale(0.08);
        for (int i = 0; i < Tapping.GLINTS_PER_PULSE; i++) {
            level.addParticle(
                    VisContent.glint(), from.x, from.y, from.z, toward.x, toward.y, toward.z);
        }
    }
}
