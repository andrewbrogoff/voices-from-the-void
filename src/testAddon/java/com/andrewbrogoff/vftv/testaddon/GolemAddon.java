package com.andrewbrogoff.vftv.testaddon;

import com.andrewbrogoff.vftv.api.golem.GolemCarryKind;
import com.andrewbrogoff.vftv.api.golem.GolemCore;
import com.andrewbrogoff.vftv.api.golem.GolemJob;
import com.andrewbrogoff.vftv.api.golem.GolemKeys;
import com.andrewbrogoff.vftv.api.golem.GolemLimits;
import com.andrewbrogoff.vftv.api.golem.GolemPayload;
import com.andrewbrogoff.vftv.api.golem.GolemResource;
import com.andrewbrogoff.vftv.api.golem.GolemRole;
import com.andrewbrogoff.vftv.api.golem.GolemWorkPayload;
import com.andrewbrogoff.vftv.api.neoforge.golem.GolemEvent;
import com.andrewbrogoff.vftv.api.neoforge.golem.GolemWorkEvent;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The example addon's use of the golem API: its keys and limits, a listener per event, an addon
 * {@link GolemJob} and a core item bound to it through {@link GolemCore}.
 */
final class GolemAddon {
    /** Every Stable constant of the feature, referenced so a rename breaks this build. */
    static final List<Object> CONSTANTS =
            List.of(
                    GolemKeys.MATERIAL_REGISTRY,
                    GolemKeys.WOOD,
                    GolemKeys.TERRACOTTA,
                    GolemKeys.STONE,
                    GolemKeys.BRASS,
                    GolemKeys.ECHO,
                    GolemKeys.JOB_REGISTRY,
                    GolemKeys.CARRY,
                    GolemKeys.DECANT,
                    GolemKeys.GOLEM_BODY_COMPONENT,
                    GolemKeys.GOLEM_CORE_COMPONENT,
                    GolemKeys.GOLEM_STATE_COMPONENT,
                    GolemKeys.BATON_ATTUNEMENT_COMPONENT,
                    GolemLimits.MAX_MARKS,
                    GolemLimits.MAX_THINKS_PER_TICK,
                    GolemLimits.MAX_THINK_POPS_PER_TICK,
                    GolemLimits.MAX_THINK_WORK,
                    GolemLimits.MAX_PATH_SEARCHES_PER_TICK,
                    GolemLimits.MAX_SEARCH_POPS_PER_TICK,
                    GolemLimits.MAX_VISITED_NODES,
                    GolemLimits.MAX_HANDLER_SLOTS,
                    GolemLimits.MAX_COLLECT_ENTITIES,
                    GolemLimits.MAX_CARRY_ITEMS,
                    GolemLimits.MAX_CARRY_ESSENTIA,
                    GolemLimits.MAX_ID_CHARS,
                    GolemLimits.MAX_OWNER_NAME);

    /**
     * The key of the addon's job in {@code vftv:golem_job}. Not registered yet: vftv doesn't create
     * that registry until the golems ticket.
     */
    static final ResourceKey<GolemJob> SHUTTLE =
            ResourceKey.create(
                    GolemKeys.JOB_REGISTRY,
                    Identifier.fromNamespaceAndPath(VftvAddon.MOD_ID, "shuttle"));

    private static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(VftvAddon.MOD_ID);

    /** {@code vftv_addon:shuttle_core}: the core item bound to {@link #SHUTTLE}. */
    static final DeferredItem<ShuttleCoreItem> SHUTTLE_CORE =
            ITEMS.registerItem("shuttle_core", ShuttleCoreItem::new);

    private GolemAddon() {}

    /** Subscribes a listener per event and registers the feature's example content. */
    static void register(IEventBus modBus, IEventBus gameBus) {
        ITEMS.register(modBus);
        gameBus.addListener(GolemWorkEvent.class, e -> read(e.payload()));
        gameBus.addListener(GolemEvent.class, e -> read(e.payload()));
        VftvAddon.consume(job());
    }

    /**
     * The addon's job: an item shuttle that only takes and gives, through its Stable constructor.
     */
    static GolemJob job() {
        return new GolemJob(
                GolemCarryKind.ITEMS, List.of(GolemRole.TAKE, GolemRole.GIVE), GolemRole.TAKE);
    }

    /** Reads every accessor, as an addon would. */
    private static void read(GolemWorkPayload p) {
        VftvAddon.consume(p.golem(), p.owner(), p.level(), p.pos(), p.face(), p.role(), p.amount());
        read(p.resource());
    }

    /** Reads every accessor of every variant, as an addon would. */
    private static void read(GolemPayload p) {
        switch (p) {
            case GolemPayload.Placed v ->
                    VftvAddon.consume(v.golem(), v.owner(), v.level(), v.pos(), v.body(), v.core());
            case GolemPayload.PickedUp v ->
                    VftvAddon.consume(v.golem(), v.owner(), v.level(), v.pos());
            case GolemPayload.Moved v -> {
                VftvAddon.consume(
                        v.golem(), v.owner(), v.level(), v.pos(), v.face(), v.role(), v.amount());
                read(v.resource());
            }
            case GolemPayload.Spilled v ->
                    VftvAddon.consume(
                            v.golem(), v.level(), v.pos(), v.aspect(), v.amount(), v.cause());
            case GolemPayload.Died v ->
                    VftvAddon.consume(v.golem(), v.owner(), v.level(), v.pos(), v.drops());
        }
    }

    /** Reads every accessor of every variant, as an addon would. */
    private static void read(GolemResource r) {
        switch (r) {
            case GolemResource.Item i -> VftvAddon.consume(i.stack());
            case GolemResource.Essentia e -> VftvAddon.consume(e.aspect());
        }
    }

    /** The addon's core item: binds a golem to {@link #SHUTTLE}, fixed at registration. */
    static final class ShuttleCoreItem extends Item implements GolemCore {
        ShuttleCoreItem(Item.Properties properties) {
            super(properties);
        }

        @Override
        public ResourceKey<GolemJob> golemJob() {
            return SHUTTLE;
        }
    }
}
