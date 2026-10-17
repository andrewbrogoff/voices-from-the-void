package com.andrewbrogoff.vftv.core.aspect;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.aspect.AspectValues;
import com.andrewbrogoff.vftv.api.aspect.ScanKey;
import com.andrewbrogoff.vftv.api.aspect.ScanTargetKind;
import com.andrewbrogoff.vftv.core.aspect.model.Knowledge;
import com.andrewbrogoff.vftv.core.aspect.model.ScanningTuning;
import com.andrewbrogoff.vftv.core.aspect.model.Sounding;
import com.andrewbrogoff.vftv.core.common.Network;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Every strike path of the Tuning Fork, on the server (aspects spec, "Striking", "What a strike
 * reveals", "Sounding for nodes"). The platform's thin listeners and the item call these; each
 * consumes the click whatever happens. A strike during the cooldown does nothing.
 *
 * <p>A ringing strike starts the cooldown and broadcasts the strike sound; the readout is private.
 * Silence (a target the server does not have) rings nothing, starts no cooldown and shows no card.
 */
public final class Strikes {
    /** The particles' colour: {@code teal2}, the resonance. */
    private static final int TEAL = 0x82c9bc;

    private static volatile NodeSource nodes = NodeSource.NONE;
    private static @Nullable Network network;
    private static BiConsumer<ServerPlayer, ScanResult> sender =
            (player, result) ->
                    Objects.requireNonNull(network, "AspectFeature.init").toPlayer(player, result);

    private Strikes() {}

    static void init(Network seam) {
        network = seam;
        seam.clientbound(ScanResult.TYPE, ScanResult.STREAM_CODEC);
    }

    /** Registers the vis feature's nodes; called once from its init. */
    public static void registerNodeSource(NodeSource source) {
        nodes = source;
    }

    /** The nodes the fork reads. */
    public static NodeSource nodes() {
        return nodes;
    }

    /**
     * Replaces the result sender and returns the previous one: GameTests record what a mock player,
     * which has no connection, would be sent.
     */
    public static BiConsumer<ServerPlayer, ScanResult> swapSender(
            BiConsumer<ServerPlayer, ScanResult> replacement) {
        BiConsumer<ServerPlayer, ScanResult> previous = sender;
        sender = replacement;
        return previous;
    }

    /** Whether a stack is the Tuning Fork. */
    public static boolean isFork(ItemStack stack) {
        return stack.is(AspectContent.tuningFork());
    }

    /**
     * A right-click on a block with the fork. A dropped item along the look ray reads as itself; a
     * position the server holds as air is silence (a phantom); a node reads as the node.
     */
    public static void strikeBlock(ServerPlayer player, ItemStack fork, BlockPos pos, Vec3 hit) {
        if (cooling(player, fork)) {
            return;
        }
        ItemEntity drop = dropAlong(player, hit);
        if (drop != null) {
            strikeEntity(player, fork, drop);
            return;
        }
        ServerLevel level = player.level();
        if (!level.isLoaded(pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return;
        }
        NodeSource.Readout node = nodes.readout(level, pos);
        if (node != null) {
            strikeNode(player, fork, node);
            return;
        }
        ring(player, fork);
        scan(
                player,
                AspectLookup.key(state),
                ScanTargetKind.BLOCK,
                AspectLookup.block(state),
                state.getBlock().getName());
    }

    /** A right-click on an entity with the fork; a dropped item reads as the item it carries. */
    public static void strikeEntity(ServerPlayer player, ItemStack fork, Entity target) {
        if (cooling(player, fork)) {
            return;
        }
        if (target.isRemoved() || !target.level().equals(player.level())) {
            return;
        }
        ring(player, fork);
        Component name =
                target instanceof ItemEntity drop
                        ? drop.getItem().getHoverName()
                        : target.getName();
        scan(
                player,
                AspectLookup.key(target),
                ScanTargetKind.ENTITY,
                AspectLookup.entity(target),
                name);
    }

    /**
     * A right-click with the fork on an inventory stack (aspects spec, "Item in an inventory"): a
     * {@code vftv:strike} recipe converts one item first; anything else is scanned. The caller has
     * checked the menu, the slot and the carried fork.
     *
     * @param target the struck stack's place
     * @param modifiable whether the place may be changed (a conversion needs it)
     */
    public static void strikeStack(
            ServerPlayer player, ItemStack fork, SlotAccess target, boolean modifiable) {
        if (cooling(player, fork)) {
            return;
        }
        ItemStack stack = target.get();
        if (stack.isEmpty()) {
            return;
        }
        ring(player, fork);
        RecipeHolder<StrikeRecipe> recipe = recipe(player.level(), stack);
        if (recipe != null && modifiable) {
            if (hasWriting(stack)) {
                send(
                        player,
                        ScanResult.plain(
                                ScanResult.State.INK,
                                stack.getHoverName(),
                                ScanningService.tuning().readoutHoldTicks()));
                return;
            }
            convert(player, target, recipe.value().assemble(new SingleRecipeInput(stack)));
            return;
        }
        scan(
                player,
                AspectLookup.key(stack.getItem()),
                ScanTargetKind.ITEM,
                AspectLookup.item(stack.getItem()),
                stack.getHoverName());
    }

    /**
     * Sounding: the fork used with nothing targeted. The answer sounds along the bearing, at the
     * answer offset, never at the node; only a direction and a band reach the client.
     */
    public static void sound(ServerPlayer player, ItemStack fork) {
        if (cooling(player, fork)) {
            return;
        }
        ring(player, fork);
        ScanningTuning t = ScanningService.tuning();
        ServerLevel level = player.level();
        Vec3 eye = player.getEyePosition();
        NodeSource.Nearest nearest = nodes.nearest(level, eye, t.soundingRadius());
        Sounding.Band band =
                nearest == null
                        ? null
                        : Sounding.band(
                                eye.distanceTo(nearest.centre()),
                                t.soundingNear(),
                                t.soundingMid(),
                                t.soundingRadius());
        if (nearest == null || band == null) {
            ScanResult.State state =
                    nodes.starterMissing(level.getServer())
                            ? ScanResult.State.NO_WELLSPRING
                            : ScanResult.State.NO_ANSWER;
            send(player, ScanResult.plain(state, Component.empty(), t.readoutHoldTicks()));
            return;
        }
        Vec3 offset = nearest.centre().subtract(eye);
        Vec3 along = offset.normalize();
        Vec3 at = eye.add(along.scale(t.answerOffset()));
        level.playSound(
                null, at.x, at.y, at.z, AspectContent.answerSound(), SoundSource.PLAYERS, 1, 1);
        level.sendParticles(
                player,
                new DustParticleOptions(TEAL, 1.0f),
                false,
                false,
                eye.x + along.x,
                eye.y + along.y,
                eye.z + along.z,
                t.particles(),
                along.x * 0.5,
                along.y * 0.5,
                along.z * 0.5,
                0.0);
        ScanResult.Bearing bearing =
                new ScanResult.Bearing(
                        Sounding.direction(offset.x, offset.z),
                        Sounding.vertical(offset.x, offset.y, offset.z),
                        band);
        send(
                player,
                new ScanResult(
                        ScanResult.State.ANSWER,
                        Component.empty(),
                        List.of(),
                        List.of(),
                        new TreeMap<>(),
                        bearing,
                        t.readoutHoldTicks()));
    }

    private static void strikeNode(ServerPlayer player, ItemStack fork, NodeSource.Readout node) {
        ring(player, fork);
        ScanningTuning t = ScanningService.tuning();
        int divisor = node.rewardDivisor().orElse(t.nodeRewardDivisor());
        SortedMap<ResourceKey<Aspect>, Integer> reward = new TreeMap<>();
        Map<ResourceKey<Aspect>, NodeSource.NodeAspect> byAspect = new TreeMap<>();
        for (NodeSource.NodeAspect a : node.aspects()) {
            reward.put(a.aspect(), Math.max(1, Math.ceilDiv(a.max(), Math.max(1, divisor))));
            byAspect.put(a.aspect(), a);
        }
        KnowledgeService.Struck struck =
                reward.isEmpty()
                        ? null
                        : KnowledgeService.strike(
                                player, new ScanKey.Node(node.id()), ScanTargetKind.NODE, reward);
        Knowledge known = struck != null ? struck.after() : KnowledgeService.get(player);
        if (known == null) {
            return;
        }
        List<ScanResult.Entry> entries = new ArrayList<>();
        AspectSet set = AspectSet.of(player.registryAccess());
        byAspect.forEach(
                (aspect, a) -> entries.add(entry(set, known, aspect, a.current(), a.max())));
        if (struck != null && !struck.strike().discovered().isEmpty()) {
            chime(player);
        }
        send(
                player,
                new ScanResult(
                        ScanResult.State.SCANNED,
                        node.type(),
                        List.of(node.type(), node.clarity()),
                        entries,
                        gained(struck),
                        null,
                        t.readoutHoldTicks()));
    }

    private static void scan(
            ServerPlayer player,
            ScanKey key,
            ScanTargetKind kind,
            @Nullable AspectValues values,
            Component name) {
        int hold = ScanningService.tuning().readoutHoldTicks();
        if (values == null || values.aspects().isEmpty()) {
            send(player, ScanResult.plain(ScanResult.State.NOTHING, name, hold));
            return;
        }
        KnowledgeService.Struck struck =
                KnowledgeService.strike(player, key, kind, values.aspects());
        if (struck == null) {
            return;
        }
        Map<String, Integer> shown =
                struck.strike().values().isEmpty()
                        ? textMap(values.aspects())
                        : struck.strike().values();
        AspectSet set = AspectSet.of(player.registryAccess());
        List<ScanResult.Entry> entries = new ArrayList<>();
        shown.forEach(
                (aspect, amount) ->
                        entries.add(entry(set, struck.after(), AspectSet.key(aspect), amount, 0)));
        if (!struck.strike().discovered().isEmpty()) {
            chime(player);
        }
        send(
                player,
                new ScanResult(
                        ScanResult.State.SCANNED,
                        name,
                        List.of(),
                        entries,
                        gained(struck),
                        null,
                        hold));
    }

    private static ScanResult.Entry entry(
            AspectSet set, Knowledge known, ResourceKey<Aspect> aspect, int amount, int max) {
        if (known.known().contains(AspectSet.text(aspect))) {
            return new ScanResult.Entry(Optional.of(aspect), amount, max, List.of());
        }
        List<Optional<ResourceKey<Aspect>>> hint = new ArrayList<>();
        for (ResourceKey<Aspect> prerequisite : set.prerequisites(aspect)) {
            hint.add(
                    known.known().contains(AspectSet.text(prerequisite))
                            ? Optional.of(prerequisite)
                            : Optional.empty());
        }
        return new ScanResult.Entry(Optional.empty(), amount, max, hint);
    }

    private static SortedMap<ResourceKey<Aspect>, Integer> gained(
            KnowledgeService.@Nullable Struck struck) {
        SortedMap<ResourceKey<Aspect>, Integer> out = new TreeMap<>();
        if (struck != null) {
            struck.gained().forEach((a, n) -> out.put(AspectSet.key(a), n));
        }
        return out;
    }

    private static Map<String, Integer> textMap(Map<ResourceKey<Aspect>, Integer> values) {
        Map<String, Integer> out = new TreeMap<>();
        values.forEach((a, n) -> out.put(AspectSet.text(a), n));
        return out;
    }

    private static boolean cooling(ServerPlayer player, ItemStack fork) {
        return player.getCooldowns().isOnCooldown(fork);
    }

    private static void ring(ServerPlayer player, ItemStack fork) {
        player.getCooldowns().addCooldown(fork, ScanningService.tuning().strikeCooldownTicks());
        play(player, AspectContent.strikeSound());
    }

    private static void chime(ServerPlayer player) {
        play(player, AspectContent.discoverSound());
    }

    private static void play(ServerPlayer player, SoundEvent sound) {
        player.level()
                .playSound(
                        null,
                        player.getX(),
                        player.getEyeY(),
                        player.getZ(),
                        sound,
                        SoundSource.PLAYERS,
                        1,
                        1);
    }

    private static void send(ServerPlayer player, ScanResult result) {
        sender.accept(player, result);
    }

    /** The nearest dropped item between the eye and the block hit, within reach. */
    private static @Nullable ItemEntity dropAlong(ServerPlayer player, Vec3 hit) {
        Vec3 eye = player.getEyePosition();
        AABB box = new AABB(eye, hit).inflate(1);
        EntityHitResult found =
                ProjectileUtil.getEntityHitResult(
                        player,
                        eye,
                        hit,
                        box,
                        e -> e instanceof ItemEntity,
                        eye.distanceToSqr(hit));
        return found != null && found.getEntity() instanceof ItemEntity drop ? drop : null;
    }

    private static @Nullable RecipeHolder<StrikeRecipe> recipe(ServerLevel level, ItemStack stack) {
        return level.recipeAccess()
                .getRecipeFor(AspectContent.strikeType(), new SingleRecipeInput(stack), level)
                .orElse(null);
    }

    /** Whether a stack carries a Book and Quill's writing: any page with text on it. */
    static boolean hasWriting(ItemStack stack) {
        WritableBookContent content = stack.get(DataComponents.WRITABLE_BOOK_CONTENT);
        return content != null && content.getPages(false).anyMatch(page -> !page.isBlank());
    }

    /**
     * Converts one item: a single item's place takes the result; from a stack, the rest stays and
     * the result goes to the inventory, or drops at the player's feet if it is full.
     */
    private static void convert(ServerPlayer player, SlotAccess target, ItemStack result) {
        ItemStack stack = target.get();
        if (stack.getCount() == 1) {
            target.set(result);
            return;
        }
        ItemStack rest = stack.copy();
        rest.shrink(1);
        target.set(rest);
        if (!player.getInventory().add(result)) {
            player.drop(result, false);
        }
    }
}
