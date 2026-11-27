package dev.nookworks.sorter;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.nookworks.block.SorterBlock;
import dev.nookworks.inventory.GhostSlots;
import dev.nookworks.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Sorter GUI: nine "ghost" filter slots (clicking with an item registers a copy, clicking with an empty hand
 * clears), the gear slot - a real one, for a Sorter Gear - and a button that swaps the outlets. Nothing is ever moved
 * into or out of the filter.
 * <p>
 * A Precision Sorter also has its tag picker ({@link TagPicker}, the one add-ons' screens share), which takes the place
 * of the filter on its screen while it is open: a ghost sample slot, the sample's mod and its tags to tick, and
 * Register, which puts a mod entry or a tag entry in the filter for each one ticked - or Keep Out, which puts them in
 * the Keep Out row. The sample and the ticks belong to this screen alone and are never saved. The client keeps its own
 * copy of the ticks in step by applying the same button presses it sends.
 * <p>
 * Under it all, the Keep Out row: ghost slots as the filter's, for what the sorter is never to take in.
 * <p>
 * An item dragged from JEI's list onto a filter slot or the Keep Out row registers it there, and onto the picker's sample
 * slot makes it the sample, as a click with it in hand would ({@link GhostSlots}).
 */
public class SorterMenu extends AbstractContainerMenu implements GhostSlots, dev.nookworks.menu.tab.TabbedMenu {
    public static final int BUTTON_SWAP = 0;
    /** The redstone mode, in the Settings tab: clear of the flags', the tabs' 20-39 and the picker's. */
    public static final int BUTTON_REDSTONE = 10;
    /**
     * Button ids for the Precision Sorter's switches - {@link SorterSettings#BLACKLIST} and
     * {@link SorterSettings#MATCH_COMPONENTS} - and either sorter's Keep Out row turned round
     * ({@link SorterSettings#KEEP_OUT_OTHERS}): this plus the flag.
     */
    public static final int BUTTON_FLAG = 1;
    /** The tag picker's buttons, from here on ({@link TagPicker}): clear of the flags', and of the tabs' 20-39. */
    public static final int PICKER = 200;
    /** The tag picker: opened, closed (the sample and ticks let go), and its ticks put in the filter. */
    public static final int BUTTON_PICKER_OPEN = PICKER + TagPicker.OPEN;
    public static final int BUTTON_PICKER_CLOSE = PICKER + TagPicker.CLOSE;
    public static final int BUTTON_REGISTER_TAGS = PICKER + TagPicker.REGISTER;
    /** The sample's mod ticked or unticked (the line above its tags). */
    public static final int BUTTON_CHOOSE_MOD = PICKER + TagPicker.MOD;
    /**
     * The picker's ticks put in the Keep Out row instead of the filter (the user, 2026-10-10: "精密ソーターの禁止項目なんだけど、
     * タグ単位で禁止項目を追加できるようにして").
     */
    public static final int BUTTON_REGISTER_DENY = PICKER + TagPicker.REGISTER + 1;
    /** A tag ticked or unticked: this plus its place in the sample's list. */
    public static final int BUTTON_CHOOSE = PICKER + TagPicker.TICKS;
    public static final int FILTER_X = 8;
    public static final int FILTER_Y = 20;
    public static final int COLUMNS = 9;
    /** The gear slot's index, after the filter slots. */
    public static final int GEAR_SLOT = SorterBlockEntity.FILTER_SLOTS;
    public static final int GEAR_X = 110;
    /** The tag picker's sample slot, after the gear; the Keep Out row follows it, then the player's inventory. */
    public static final int SAMPLE_SLOT = GEAR_SLOT + 1;
    public static final int SAMPLE_X = 8;
    public static final int SAMPLE_Y = 22;
    /** The Keep Out row's first slot, and where the row sits: right of its mark, ending where the filter's rows end. */
    public static final int DENY_SLOT = SAMPLE_SLOT + 1;
    /** On the filter's columns 1-8, its mark in column 0 (r21: a pixel right of them, into the bay's rim at the end). */
    public static final int DENY_X = FILTER_X + 18;
    /** Where what the sorter holds is shown: the right end of the inventory's caption row. */
    public static final int HELD_X = 152;
    /**
     * What the sorter holds - its input's slots, waiting there while an outlet is full or blocked - after the Keep Out row: the
     * player's inventory stays the last 36 slots, as a menu's is. Shown, not clicked: the screen draws it
     * ({@code SorterScreen}).
     */
    public static final int HELD_SLOT = DENY_SLOT + SorterBlockEntity.DENY_SLOTS;
    private static final int INVENTORY = HELD_SLOT + SorterBlockEntity.INPUT_SLOTS;

    private final Container filter;
    private final Container deny;
    private final BlockPos pos;
    private final DataSlot swapped;
    private final SorterTier tier;
    private final @Nullable SorterSettings settings;
    private final DataSlot[] flags = new DataSlot[SorterSettings.COUNT];
    /** The redstone mode, as the sorter has it (synced). */
    private final DataSlot redstone;
    /** The tabs on the screen's right edge, as a machine's: Settings, its redstone mode. */
    private final dev.nookworks.menu.tab.MenuTabs tabs = new dev.nookworks.menu.tab.MenuTabs(176);
    /** The tag picker: the filter its first destination, the Keep Out row its second. */
    private final TagPicker picker;

    /** Client side. The tier comes over with the menu, because the GUI's shape depends on it. */
    public static SorterMenu client(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        SorterTier tier = data.readEnum(SorterTier.class);
        BlockPos pos = data.readBlockPos();
        return new SorterMenu(containerId, inventory, new SimpleContainer(SorterBlockEntity.FILTER_SLOTS), new SimpleContainer(1),
                new SimpleContainer(SorterBlockEntity.DENY_SLOTS), new SimpleContainer(SorterBlockEntity.INPUT_SLOTS), pos, tier, null);
    }

    /** Where the gear slot's top sits: beside the swap button, under the filter rows. */
    public static int gearY(SorterTier tier) {
        return FILTER_Y + (tier.filterSlots > COLUMNS ? 36 : 18) + 6;
    }

    /** Where the Keep Out row's top sits: under the labels and the swap button, and a Precision Sorter's switches. */
    public static int denyY(SorterTier tier) {
        return FILTER_Y + (tier.filterSlots > COLUMNS ? 36 : 18) + (tier.configurable ? 54 : 30);
    }

    /** With an empty Keep Out row, as a menu made before it was. */
    public SorterMenu(int containerId, Inventory inventory, Container filter, Container gear, BlockPos pos, SorterTier tier,
            @Nullable SorterSettings settings) {
        this(containerId, inventory, filter, gear, new SimpleContainer(SorterBlockEntity.DENY_SLOTS), pos, tier, settings);
    }

    /** With nothing held shown, as a menu made before it was. */
    public SorterMenu(int containerId, Inventory inventory, Container filter, Container gear, Container deny, BlockPos pos,
            SorterTier tier, @Nullable SorterSettings settings) {
        this(containerId, inventory, filter, gear, deny, new SimpleContainer(SorterBlockEntity.INPUT_SLOTS), pos, tier, settings);
    }

    /** {@code held}: what the sorter holds, its input's slots (the sorter itself, on the server). */
    public SorterMenu(int containerId, Inventory inventory, Container filter, Container gear, Container deny, Container held,
            BlockPos pos, SorterTier tier, @Nullable SorterSettings settings) {
        super(ModMenus.SORTER.get(), containerId);
        this.filter = filter;
        this.deny = deny;
        this.pos = pos;
        this.tier = tier;
        this.settings = settings;
        this.picker = new TagPicker(PICKER, List.of(
                new TagPicker.Destination(filter, tier.filterSlots, () -> Component.translatable("message.nookworks.sorter.filter_full")),
                new TagPicker.Destination(deny, SorterBlockEntity.DENY_SLOTS, () -> Component.translatable(
                        this.flag(SorterSettings.KEEP_OUT_OTHERS) ? "message.nookworks.sorter.allow_full" : "message.nookworks.sorter.deny_full"))));
        for (int i = 0; i < SorterBlockEntity.FILTER_SLOTS; i++) {
            boolean used = i < tier.filterSlots;
            this.addSlot(new Slot(filter, i, FILTER_X + (i % COLUMNS) * 18, FILTER_Y + (i / COLUMNS) * 18) {
                @Override
                public boolean mayPickup(Player player) {
                    return false;
                }

                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }

                // An Item Sorter's second row is not there: it would take the hover and the clicks from the labels
                // and the gear slot drawn where it lies. Nor is the filter while the tag picker covers it.
                @Override
                public boolean isActive() {
                    return used && !SorterMenu.this.picker.open();
                }
            });
        }
        this.addSlot(new Slot(gear, 0, GEAR_X + 1, gearY(tier) + 1) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof SorterGearItem;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }

            @Override
            public boolean isActive() {
                return !SorterMenu.this.picker.open();
            }
        });
        this.addSlot(this.picker.sampleSlot(SAMPLE_X + 1, SAMPLE_Y + 1));
        for (int i = 0; i < SorterBlockEntity.DENY_SLOTS; i++) {
            this.addSlot(new Slot(deny, i, DENY_X + i * 18, denyY(tier) + 1) {
                @Override
                public boolean mayPickup(Player player) {
                    return false;
                }

                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }

                @Override
                public boolean isActive() {
                    return !SorterMenu.this.picker.open();
                }
            });
        }
        for (int i = 0; i < SorterBlockEntity.INPUT_SLOTS; i++) {
            this.addSlot(new Slot(held, i, HELD_X, playerY(tier) - 17) {
                @Override
                public boolean mayPickup(Player player) {
                    return false;
                }

                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }

                @Override
                public boolean isActive() {
                    return false; // drawn by the screen, over the inventory's caption; never clicked
                }
            });
        }
        this.addStandardInventorySlots(inventory, 8, playerY(tier));
        for (int i = 0; i < SorterSettings.COUNT; i++) {
            int flag = i;
            this.flags[i] = this.addDataSlot(new DataSlot() {
                private int clientValue;

                @Override
                public int get() {
                    // Only the server has the settings; the client answers with what was last synced to it.
                    return settings == null ? this.clientValue : settings.get(flag) ? 1 : 0;
                }

                @Override
                public void set(int value) {
                    this.clientValue = value;
                }
            });
        }
        Player player = inventory.player;
        this.swapped = this.addDataSlot(new DataSlot() {
            private int clientValue;

            @Override
            public int get() {
                BlockState state = player.level().getBlockState(pos);
                return player.level().isClientSide() || !state.hasProperty(SorterBlock.SWAPPED)
                        ? this.clientValue : state.getValue(SorterBlock.SWAPPED) ? 1 : 0;
            }

            @Override
            public void set(int value) {
                this.clientValue = value;
            }
        });
        this.redstone = this.addDataSlot(new DataSlot() {
            private int clientValue = dev.nookworks.machine.RedstoneMode.DEFAULT.ordinal();

            @Override
            public int get() {
                return !player.level().isClientSide() && player.level().getBlockEntity(pos) instanceof SorterBlockEntity sorter
                        ? sorter.redstoneMode().ordinal() : this.clientValue;
            }

            @Override
            public void set(int value) {
                this.clientValue = value;
            }
        });
        this.tabs.enable(dev.nookworks.menu.tab.MachineTab.SETTINGS);
    }

    @Override
    public dev.nookworks.menu.tab.MenuTabs tabs() {
        return this.tabs;
    }

    /** How the sorter answers redstone, as last synced. */
    public dev.nookworks.machine.RedstoneMode redstoneMode() {
        return dev.nookworks.machine.RedstoneMode.byId(this.redstone.get());
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.tabs.removed(player);
    }

    /**
     * The player inventory, under the Keep Out row (lower when there is a second row of filter slots), with 18 px over
     * it for its caption and what the sorter holds beside it (a Precision Sorter's 14 grew by 4 for it).
     */
    public static int playerY(SorterTier tier) {
        return tier.filterSlots > COLUMNS ? 150 : 108;
    }

    /** What the sorter holds, as last sent. */
    /** What the sorter holds, in its input's order, the empty slots left out. */
    public List<ItemStack> held() {
        List<ItemStack> held = new ArrayList<>();
        for (int i = 0; i < SorterBlockEntity.INPUT_SLOTS; i++) {
            ItemStack stack = this.slots.get(HELD_SLOT + i).getItem();
            if (!stack.isEmpty()) {
                held.add(stack);
            }
        }
        return held;
    }

    public SorterTier tier() {
        return this.tier;
    }

    public boolean flag(int index) {
        return this.flags[index].get() == 1;
    }

    public boolean isSwapped() {
        return this.swapped.get() == 1;
    }

    // ---- the tag picker ----

    /** The Precision Sorter's tag picker; on an Item Sorter's menu it is there, but no button reaches it. */
    public TagPicker picker() {
        return this.picker;
    }

    public boolean picking() {
        return this.picker.open();
    }

    public ItemStack sample() {
        return this.picker.sample();
    }

    /** The sample's tags, in the order the picker lists them (the same on both sides: the tags are synced). */
    public List<TagKey<Item>> choices() {
        return this.picker.choices();
    }

    public boolean chosen(int index) {
        return this.picker.chosen(index);
    }

    /** The sample's mod, offered above its tags (by its namespace), or null with no sample. */
    public @Nullable String sampleMod() {
        return this.picker.sampleMod();
    }

    public boolean modChosen() {
        return this.picker.modChosen();
    }

    public boolean anyChosen() {
        return this.picker.anyChosen();
    }

    /** Filter slots still free. */
    public int freeSlots() {
        return this.picker.free(0);
    }

    /** Keep Out slots still free. */
    public int freeDenySlots() {
        return this.picker.free(1);
    }

    /** Whether a tag entry for {@code tag} is in the filter already. */
    public boolean hasEntryFor(TagKey<Item> tag) {
        return this.picker.holds(0, tag, null);
    }

    /** Whether a mod entry for the mod {@code namespace} is in the filter already. */
    public boolean hasModEntryFor(String namespace) {
        return this.picker.holds(0, null, namespace);
    }

    /** Whether a tag entry for {@code tag} is in the Keep Out row already. */
    public boolean hasDenyEntryFor(TagKey<Item> tag) {
        return this.picker.holds(1, tag, null);
    }

    /** Whether a mod entry for the mod {@code namespace} is in the Keep Out row already. */
    public boolean hasDenyModEntryFor(String namespace) {
        return this.picker.holds(1, null, namespace);
    }

    /**
     * The picker's buttons, as the client applies them to its own copy as it sends them to the server. Returns whether
     * the id is the picker's: only a Precision Sorter's is pressed.
     */
    public boolean pickerButton(Player player, int id) {
        return this.tier.configurable && this.picker.button(player, id);
    }

    @Override
    public void clicked(int slotIndex, int button, ContainerInput input, Player player) {
        if (slotIndex >= 0 && slotIndex < this.tier.filterSlots) {
            if (input != ContainerInput.PICKUP || this.picker.open()) {
                return; // shift, number keys, Q, double-click: none of them register or clear a filter
            }
            ItemStack carried = this.getCarried();
            this.filter.setItem(slotIndex, carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1));
            return;
        }
        if (this.picker.clicked(this, slotIndex, input)) {
            return; // the sample: a copy, as a filter entry is, the item staying in hand
        }
        if (slotIndex >= HELD_SLOT && slotIndex < INVENTORY) {
            return; // shown, not taken: what the sorter holds stays with it
        }
        if (slotIndex >= DENY_SLOT && slotIndex < INVENTORY) {
            // the Keep Out row, as the filter: an item clicked in registers a copy, an empty hand clears
            if (input == ContainerInput.PICKUP && !this.picker.open()) {
                ItemStack carried = this.getCarried();
                this.deny.setItem(slotIndex - DENY_SLOT, carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1));
            }
            return;
        }
        super.clicked(slotIndex, button, input, player);
    }

    /**
     * Where an item dragged from JEI's list may land just now: a filter slot the sorter has, or the Keep Out row, while
     * the tag picker is shut; the sample slot while it is open (the user, 2026-11-10: 「3もつけていいです。」).
     */
    @Override
    public boolean takesGhost(int slotIndex) {
        if (this.picker.open()) {
            return this.picker.takesGhost(slotIndex);
        }
        return slotIndex >= 0 && slotIndex < this.tier.filterSlots || slotIndex >= DENY_SLOT && slotIndex < HELD_SLOT;
    }

    /** An item dropped there from JEI: registered as a click with it would (a copy of one), or made the sample. */
    @Override
    public boolean setGhost(int slotIndex, ItemStack stack) {
        if (stack.isEmpty() || !this.takesGhost(slotIndex)) {
            return false;
        }
        if (this.picker.open()) {
            return this.picker.ghost(slotIndex, stack);
        }
        if (slotIndex < this.tier.filterSlots) {
            this.filter.setItem(slotIndex, stack.copyWithCount(1));
        } else {
            this.deny.setItem(slotIndex - DENY_SLOT, stack.copyWithCount(1));
        }
        return true;
    }

    /**
     * Shift-click in the player inventory: with the tag picker open, the item becomes the sample. Otherwise a Sorter
     * Gear goes into the gear slot if it is free, and anything else is registered in the first free filter slot.
     * Shift-click on the gear takes it back.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < SorterBlockEntity.FILTER_SLOTS || index == SAMPLE_SLOT || index >= DENY_SLOT && index < INVENTORY) {
            return ItemStack.EMPTY;
        }
        Slot slot = this.slots.get(index);
        if (index == GEAR_SLOT) {
            ItemStack gear = slot.getItem();
            if (!gear.isEmpty() && this.moveItemStackTo(gear, INVENTORY, this.slots.size(), true)) {
                slot.setChanged();
            }
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (this.picker.quickMove(stack)) {
            return ItemStack.EMPTY;
        }
        if (stack.getItem() instanceof SorterGearItem && !this.slots.get(GEAR_SLOT).hasItem()) {
            this.slots.get(GEAR_SLOT).setByPlayer(stack.split(1));
            slot.setChanged();
            return ItemStack.EMPTY;
        }
        int usable = this.tier.filterSlots;
        // the screen reckons as the sorter does, from the switch it is sent, so what it shows at once is what stays
        boolean components = this.settings != null ? this.settings.get(SorterSettings.MATCH_COMPONENTS)
                : this.flag(SorterSettings.MATCH_COMPONENTS);
        for (int i = 0; i < usable; i++) {
            // "Already registered" means what the sorter itself would match: with component matching on, a
            // damaged or renamed sword is a different entry from a fresh one and may be added beside it. A tag or mod
            // entry is not the item itself: the item may still be registered beside the tag or mod it is in.
            ItemStack registered = this.filter.getItem(i);
            if (registered.isEmpty() || SorterTags.of(registered) != null || SorterTags.modOf(registered) != null) {
                continue;
            }
            if (components ? ItemStack.isSameItemSameComponents(stack, registered) : ItemStack.isSameItem(stack, registered)) {
                return ItemStack.EMPTY;
            }
        }
        for (int i = 0; i < usable; i++) {
            if (this.filter.getItem(i).isEmpty()) {
                this.filter.setItem(i, stack.copyWithCount(1));
                return ItemStack.EMPTY;
            }
        }
        if (!player.level().isClientSide()) {
            player.sendOverlayMessage(Component.translatable("message.nookworks.sorter.filter_full"));
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (this.tabs.clickMenuButton(player, id) || this.pickerButton(player, id)) {
            return true;
        }
        if (id == BUTTON_REDSTONE) {
            if (player.level().getBlockEntity(this.pos) instanceof SorterBlockEntity sorter && this.settings != null) {
                sorter.setRedstoneMode(sorter.redstoneMode().next());
            }
            return true;
        }
        boolean keepOutOthers = id == BUTTON_FLAG + SorterSettings.KEEP_OUT_OTHERS; // the Keep Out row's sign: either sorter
        if (id == BUTTON_FLAG + SorterSettings.BLACKLIST || id == BUTTON_FLAG + SorterSettings.MATCH_COMPONENTS || keepOutOthers) {
            if (this.settings == null || !this.tier.configurable && !keepOutOthers) {
                return false;
            }
            this.settings.toggle(id - BUTTON_FLAG);
            if (player.level().getBlockEntity(this.pos) instanceof SorterBlockEntity sorter) {
                sorter.setChanged();
            }
            return true;
        }
        if (id != BUTTON_SWAP) {
            return false;
        }
        BlockState state = player.level().getBlockState(this.pos);
        if (state.getBlock() instanceof SorterBlock) {
            player.level().setBlock(this.pos, state.cycle(SorterBlock.SWAPPED), 3);
        }
        return true;
    }

    /**
     * On the server, the sorter the screen was opened on, still there and in reach: one broken - or broken and set down
     * again in the same tick - closes the screen, so nothing goes into a sorter that is gone, and no button lands on
     * one. (A sorter at 0,0,0 was taken for the client's stand-in and never checked for reach.)
     */
    @Override
    public boolean stillValid(Player player) {
        if (this.settings != null) {
            return player.level().getBlockEntity(this.pos) instanceof SorterBlockEntity sorter && sorter.settings() == this.settings
                    && Container.stillValidBlockEntity(sorter, player);
        }
        return this.pos.equals(BlockPos.ZERO) || player.distanceToSqr(this.pos.getX() + 0.5, this.pos.getY() + 0.5, this.pos.getZ() + 0.5) <= 64.0;
    }
}
