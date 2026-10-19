package com.andrewbrogoff.vftv.core.arcane;

import com.andrewbrogoff.vftv.api.vis.VisContext;
import com.andrewbrogoff.vftv.api.vis.VisKeys;
import com.andrewbrogoff.vftv.api.vis.VisKind;
import com.andrewbrogoff.vftv.core.common.model.CheckedCommit;
import com.andrewbrogoff.vftv.core.research.ResearchService;
import com.andrewbrogoff.vftv.core.research.model.ResearchRecord;
import com.andrewbrogoff.vftv.core.vis.VisContent;
import com.andrewbrogoff.vftv.core.vis.VisRulesService;
import com.andrewbrogoff.vftv.core.vis.VisService;
import com.andrewbrogoff.vftv.core.warp.WarpDials;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import org.jspecify.annotations.Nullable;

/**
 * The Worktable's menu (arcane spec, "The menu"): transient and per player, never a recipe-book
 * menu. It owns its grid, wand and output containers; the block holds nothing.
 *
 * <p>Data slots: 0 the last refusal code, 1 and 2 the preview revision's low and high 16 bits. The
 * revision is the menu's surface revision (conventions, "The revision"): it bumps on any change to
 * a menu slot, the player's research revision, the recipe snapshot or the vis rules, and every bump
 * clears the refusal. While a spend applies or restores slot changes the menu only marks itself
 * dirty, and the spend's apply boundary bumps once.
 */
public final class WorktableMenu extends AbstractContainerMenu {
    /** The wand slot. */
    public static final int WAND = 0;

    /** The first grid slot; the grid is 1-9, row-major. */
    public static final int GRID = 1;

    /** The output slot. */
    public static final int OUTPUT = 10;

    /** The first inventory slot (11-37). */
    public static final int INVENTORY = 11;

    /** The first hotbar slot (38-46). */
    public static final int HOTBAR = 38;

    /** One past the last slot. */
    public static final int END = 47;

    /** The empty wand slot's outline sprite. */
    public static final Identifier WAND_SPRITE =
            Identifier.fromNamespaceAndPath("vftv", "container/slot/wand");

    private static final VisContext ORDINARY =
            new VisContext(ArcaneRecipe.ID, VisKind.ORDINARY, null);
    private static final VisContext VOID = new VisContext(ArcaneRecipe.ID, VisKind.VOID, null);

    private final ContainerLevelAccess access;
    private final Player player;
    private final boolean client;
    private final Grid grid = new Grid();
    private final Box wand = new Box(1);
    private final Box output = new Box(99);

    private int revision;
    private int refusal;
    private int ownRevision;
    private boolean suppressed;
    private boolean dirty;
    private boolean acknowledged;
    private int clientVersion;

    // The change check's last-seen inputs (server only).
    private long seenResearch = Long.MIN_VALUE;
    private @Nullable Object seenSnapshot;
    private @Nullable Object seenRules;
    private ItemStack seenWand = ItemStack.EMPTY;
    private double seenDiscount = Double.NaN;
    private double seenOrdinary = Double.NaN;
    private double seenVoid = Double.NaN;

    /** The client's menu, built from the open-screen packet. */
    public WorktableMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, ContainerLevelAccess.NULL);
    }

    /** The server's menu for the Worktable that {@code access} names. */
    public WorktableMenu(int containerId, Inventory inventory, ContainerLevelAccess access) {
        super(ArcaneContent.menu(), containerId);
        this.access = access;
        this.player = inventory.player;
        this.client = player.level().isClientSide();
        addSlot(new WandSlot(wand)).setBackground(WAND_SPRITE);
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                addSlot(new Slot(grid, c + 3 * r, 30 + 18 * c, 17 + 18 * r));
            }
        }
        addSlot(new OutputSlot(output));
        addStandardInventorySlots(inventory, 8, 136);
        addDataSlots(new Data());
        if (!client) {
            changeCheck();
        }
    }

    // --- containers ---------------------------------------------------------------------------

    /** The 3x3 grid. */
    public Container grid() {
        return grid;
    }

    /** The grid as {@code CraftingInput.ofPositioned} builds it, trimmed to its occupied area. */
    public CraftingInput input() {
        return grid.asCraftInput();
    }

    /** The wand slot's container. */
    public Container wandSlot() {
        return wand;
    }

    /** The output slot's container. */
    public Container output() {
        return output;
    }

    /** The player whose menu this is. */
    public Player player() {
        return player;
    }

    /** The Worktable's position, or null for the client's menu. */
    public @Nullable BlockPos pos() {
        return access.evaluate((level, pos) -> pos).orElse(null);
    }

    // --- revision and refusal -----------------------------------------------------------------

    /** The preview revision: the server's own, or what the client last received. */
    public int revision() {
        return revision;
    }

    /** The last refusal's code, 0 for none. */
    public int refusal() {
        return refusal;
    }

    /** The revision recorded at the last commit's apply boundary. */
    int ownRevision() {
        return ownRevision;
    }

    /**
     * The client's shown refusal: the refusal while it is not yet acknowledged, else null. A shown
     * refusal is feedback only; it never disables a locally ready preview.
     */
    public @Nullable WorktableStatus shownRefusal() {
        return acknowledged || refusal == 0 ? null : WorktableStatus.of(refusal);
    }

    /** The client acknowledges the shown refusal at once when the player activates the preview. */
    public void acknowledge() {
        acknowledged = true;
    }

    /** Counts the client's slot changes, so its preview recomputes only on a change. */
    public int clientVersion() {
        return clientVersion;
    }

    /** Clears the refusal, as the start of every activation does. */
    void clearRefusal() {
        refusal = 0;
    }

    /** Sets the refusal and resends every slot and data value with it. */
    void refuse(WorktableStatus status) {
        refusal = status.code();
        sendAllDataToRemote();
    }

    /**
     * The O(1) change check: a new research revision, recipe snapshot or vis rules, or a wand
     * changed in place, bumps the revision; a changed price input clears the refusal without a
     * bump. Run every tick while open and at the start of every commit.
     */
    void changeCheck() {
        ResearchRecord record = ResearchService.record(player);
        long research = record == null ? -1 : record.revision();
        Object snapshot = ArcaneRecipes.server();
        Object rules = VisRulesService.tuning();
        @SuppressWarnings("ReferenceEquality") // instances are the identity of a load
        boolean replaced = snapshot != seenSnapshot || rules != seenRules;
        if (research != seenResearch || replaced || !ItemStack.matches(wand.getItem(0), seenWand)) {
            seenResearch = research;
            seenSnapshot = snapshot;
            seenRules = rules;
            bump();
        }
        double discount = discount(player);
        double ordinary = WarpDials.visFactor(player, ORDINARY);
        double voidFactor = WarpDials.visFactor(player, VOID);
        if (Double.compare(discount, seenDiscount) != 0
                || Double.compare(ordinary, seenOrdinary) != 0
                || Double.compare(voidFactor, seenVoid) != 0) {
            seenDiscount = discount;
            seenOrdinary = ordinary;
            seenVoid = voidFactor;
            refusal = 0;
        }
    }

    private static double discount(Player player) {
        Optional<Holder.Reference<Attribute>> attribute =
                BuiltInRegistries.ATTRIBUTE.get(VisKeys.VIS_DISCOUNT);
        return attribute.isPresent() && player.getAttributes().hasAttribute(attribute.get())
                ? player.getAttributeValue(attribute.get())
                : 0;
    }

    private void bump() {
        revision = CheckedCommit.bump(revision);
        refusal = 0;
        seenWand = wand.getItem(0).copy();
    }

    /** Begins a commit's suppression: slot changes only mark the menu dirty. */
    void suppress() {
        suppressed = true;
        dirty = false;
    }

    /**
     * The spend's apply boundary: ends suppression and bumps once if anything changed; on a commit
     * records the revision as the handler's own and clears the refusal.
     */
    VisService.Boundary boundary() {
        return this::endSuppression;
    }

    /** Ends suppression, if still on (a refused spend never calls its boundary). */
    void endSuppression(boolean committed) {
        if (!suppressed) {
            return;
        }
        suppressed = false;
        if (dirty || committed) {
            bump();
        }
        if (committed) {
            ownRevision = revision;
            refusal = 0;
        }
    }

    private void onSlotChange() {
        if (client) {
            clientVersion++;
        } else if (suppressed) {
            dirty = true;
        } else {
            bump();
        }
    }

    @Override
    public void slotsChanged(Container container) {
        onSlotChange();
        if (!suppressed) {
            super.slotsChanged(container);
        }
    }

    @Override
    public void broadcastChanges() {
        if (suppressed) {
            return;
        }
        if (!client) {
            changeCheck();
        }
        super.broadcastChanges();
    }

    @Override
    public void setData(int id, int value) {
        super.setData(id, value);
        if (id == 0) {
            acknowledged = false; // any received value ends the client's acknowledgement
        }
        clientVersion++;
    }

    // --- vanilla menu behaviour ---------------------------------------------------------------

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ArcaneContent.worktable());
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player instanceof ServerPlayer) {
            clearContainer(player, grid);
            clearContainer(player, wand);
            clearContainer(player, output);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        boolean moved;
        if (index == OUTPUT) {
            moved = moveItemStackTo(stack, INVENTORY, END, true);
        } else if (index < OUTPUT) {
            moved = moveItemStackTo(stack, INVENTORY, END, false);
        } else {
            boolean wandItem = stack.is(VisContent.wand());
            moved =
                    (wandItem
                                    && !slots.get(WAND).hasItem()
                                    && moveItemStackTo(stack, WAND, WAND + 1, false))
                            || (!wandItem && moveItemStackTo(stack, GRID, OUTPUT, false))
                            || (index < HOTBAR
                                    ? moveItemStackTo(stack, HOTBAR, END, false)
                                    : moveItemStackTo(stack, INVENTORY, HOTBAR, false));
        }
        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == before.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return before;
    }

    // --- parts --------------------------------------------------------------------------------

    /** The grid: every change, in-place merges included, reaches the menu at once. */
    private final class Grid extends TransientCraftingContainer {
        Grid() {
            super(WorktableMenu.this, 3, 3);
        }

        @Override
        public void setChanged() {
            slotsChanged(this);
        }
    }

    /** A one-slot container whose every change reaches the menu at once. */
    private final class Box extends SimpleContainer {
        private final int max;

        Box(int max) {
            super(1);
            this.max = max;
        }

        @Override
        public int getMaxStackSize() {
            return max;
        }

        @Override
        public void setChanged() {
            slotsChanged(this);
        }
    }

    /** Slot 0: only a wand, one at a time. Never charges it. */
    private static final class WandSlot extends Slot {
        WandSlot(Container container) {
            super(container, 0, 8, 35);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.is(VisContent.wand());
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    /** Slot 10: committed output, taken freely; nothing can insert into it. */
    private static final class OutputSlot extends Slot {
        OutputSlot(Container container) {
            super(container, 0, 134, 35);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }

    /** Data slots 0-2: the refusal, then the revision's low and high 16 bits. */
    private final class Data implements ContainerData {
        @Override
        public int get(int id) {
            return switch (id) {
                case 0 -> refusal;
                case 1 -> revision & 0xFFFF;
                default -> revision >>> 16;
            };
        }

        @Override
        public void set(int id, int value) {
            int bits = value & 0xFFFF;
            switch (id) {
                case 0 -> refusal = bits;
                case 1 -> revision = (revision & 0xFFFF0000) | bits;
                default -> revision = (revision & 0xFFFF) | (bits << 16);
            }
        }

        @Override
        public int getCount() {
            return 3;
        }
    }
}
