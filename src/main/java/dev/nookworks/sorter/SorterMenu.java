package dev.nookworks.sorter;

import java.util.BitSet;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.nookworks.block.SorterBlock;
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
 * A Precision Sorter also has its tag picker, which takes the place of the filter on its screen while it is open: a
 * ghost sample slot, the sample's mod and its tags ({@link SorterTags#choices}) to tick, and Register, which puts a mod
 * entry or a tag entry in the filter for each one ticked - or Keep Out, which puts them in the Keep Out row. The sample
 * and the ticks belong to this screen alone and are never saved. The client keeps its own copy of the ticks in step by
 * applying the same button presses it sends.
 * <p>
 * Under it all, the Keep Out row: ghost slots as the filter's, for what the sorter is never to take in.
 */
public class SorterMenu extends AbstractContainerMenu {
    public static final int BUTTON_SWAP = 0;
    /** Button ids for the Precision Sorter's switches: {@link SorterSettings#BLACKLIST} and {@link SorterSettings#MATCH_COMPONENTS}. */
    public static final int BUTTON_FLAG = 1;
    /** The tag picker: opened, closed (the sample and ticks let go), and its ticks put in the filter. */
    public static final int BUTTON_PICKER_OPEN = 10;
    public static final int BUTTON_PICKER_CLOSE = 11;
    public static final int BUTTON_REGISTER_TAGS = 12;
    /** The sample's mod ticked or unticked (the line above its tags). */
    public static final int BUTTON_CHOOSE_MOD = 13;
    /**
     * The picker's ticks put in the Keep Out row instead of the filter (the user, 2026-10-10: "精密ソーターの禁止項目なんだけど、
     * タグ単位で禁止項目を追加できるようにして").
     */
    public static final int BUTTON_REGISTER_DENY = 14;
    /** A tag ticked or unticked: this plus its place in the sample's list. */
    public static final int BUTTON_CHOOSE = 100;
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
    public static final int DENY_X = 26;
    private static final int INVENTORY = DENY_SLOT + SorterBlockEntity.DENY_SLOTS;

    private final Container filter;
    private final Container deny;
    private final Container sample = new SimpleContainer(1);
    private final BlockPos pos;
    private final DataSlot swapped;
    private final SorterTier tier;
    private final @Nullable SorterSettings settings;
    private final DataSlot[] flags = new DataSlot[SorterSettings.COUNT];
    /** Whether the tag picker is open, which of the sample's tags are ticked, and whether its mod is. */
    private boolean picking;
    private final BitSet chosen = new BitSet();
    private boolean modChosen;

    /** Client side. The tier comes over with the menu, because the GUI's shape depends on it. */
    public static SorterMenu client(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        SorterTier tier = data.readEnum(SorterTier.class);
        BlockPos pos = data.readBlockPos();
        return new SorterMenu(containerId, inventory, new SimpleContainer(SorterBlockEntity.FILTER_SLOTS), new SimpleContainer(1),
                new SimpleContainer(SorterBlockEntity.DENY_SLOTS), pos, tier, null);
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

    public SorterMenu(int containerId, Inventory inventory, Container filter, Container gear, Container deny, BlockPos pos,
            SorterTier tier, @Nullable SorterSettings settings) {
        super(ModMenus.SORTER.get(), containerId);
        this.filter = filter;
        this.deny = deny;
        this.pos = pos;
        this.tier = tier;
        this.settings = settings;
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
                    return used && !SorterMenu.this.picking;
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
                return !SorterMenu.this.picking;
            }
        });
        this.addSlot(new Slot(this.sample, 0, SAMPLE_X + 1, SAMPLE_Y + 1) {
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
                return SorterMenu.this.picking;
            }
        });
        for (int i = 0; i < SorterBlockEntity.DENY_SLOTS; i++) {
            this.addSlot(new Slot(deny, i, DENY_X + i * 18 + 1, denyY(tier) + 1) {
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
                    return !SorterMenu.this.picking;
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
    }

    /** The player inventory, under the Keep Out row (lower when there is a second row of filter slots). */
    public static int playerY(SorterTier tier) {
        return tier.filterSlots > COLUMNS ? 146 : 108;
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

    public boolean picking() {
        return this.picking;
    }

    public ItemStack sample() {
        return this.sample.getItem(0);
    }

    /** The sample's tags, in the order the picker lists them (the same on both sides: the tags are synced). */
    public List<TagKey<Item>> choices() {
        return SorterTags.choices(this.sample());
    }

    public boolean chosen(int index) {
        return this.chosen.get(index);
    }

    /** The sample's mod, offered above its tags (by its namespace), or null with no sample. */
    public @Nullable String sampleMod() {
        return this.sample().isEmpty() ? null : SorterTags.namespace(this.sample());
    }

    public boolean modChosen() {
        return this.modChosen;
    }

    public boolean anyChosen() {
        return !this.chosen.isEmpty() || this.modChosen;
    }

    /** Filter slots still free. */
    public int freeSlots() {
        return free(this.filter, this.tier.filterSlots);
    }

    /** Keep Out slots still free. */
    public int freeDenySlots() {
        return free(this.deny, SorterBlockEntity.DENY_SLOTS);
    }

    private static int free(Container container, int size) {
        int free = 0;
        for (int i = 0; i < size; i++) {
            free += container.getItem(i).isEmpty() ? 1 : 0;
        }
        return free;
    }

    /** Whether a tag entry for {@code tag} is in the filter already. */
    public boolean hasEntryFor(TagKey<Item> tag) {
        return holdsTag(this.filter, this.tier.filterSlots, tag);
    }

    /** Whether a mod entry for the mod {@code namespace} is in the filter already. */
    public boolean hasModEntryFor(String namespace) {
        return holdsMod(this.filter, this.tier.filterSlots, namespace);
    }

    /** Whether a tag entry for {@code tag} is in the Keep Out row already. */
    public boolean hasDenyEntryFor(TagKey<Item> tag) {
        return holdsTag(this.deny, SorterBlockEntity.DENY_SLOTS, tag);
    }

    /** Whether a mod entry for the mod {@code namespace} is in the Keep Out row already. */
    public boolean hasDenyModEntryFor(String namespace) {
        return holdsMod(this.deny, SorterBlockEntity.DENY_SLOTS, namespace);
    }

    private static boolean holdsTag(Container container, int size, TagKey<Item> tag) {
        for (int i = 0; i < size; i++) {
            if (tag.equals(SorterTags.of(container.getItem(i)))) {
                return true;
            }
        }
        return false;
    }

    private static boolean holdsMod(Container container, int size, String namespace) {
        for (int i = 0; i < size; i++) {
            if (namespace.equals(SorterTags.modOf(container.getItem(i)))) {
                return true;
            }
        }
        return false;
    }

    private void setSample(ItemStack stack) {
        this.sample.setItem(0, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
        this.chosen.clear(); // a new sample has a list of its own
        this.modChosen = false;
    }

    /**
     * The picker's buttons, as the client applies them to its own copy as it sends them to the server. Returns whether
     * the id is the picker's.
     */
    public boolean pickerButton(Player player, int id) {
        if (!this.tier.configurable) {
            return false;
        }
        if (id == BUTTON_PICKER_OPEN || id == BUTTON_PICKER_CLOSE) {
            this.picking = id == BUTTON_PICKER_OPEN;
            this.setSample(ItemStack.EMPTY);
            return true;
        }
        if (id >= BUTTON_CHOOSE) {
            int index = id - BUTTON_CHOOSE;
            if (index < this.choices().size()) {
                this.chosen.flip(index);
            }
            return true;
        }
        if (id == BUTTON_CHOOSE_MOD) {
            this.modChosen = !this.modChosen && !this.sample().isEmpty();
            return true;
        }
        if (id == BUTTON_REGISTER_TAGS || id == BUTTON_REGISTER_DENY) {
            this.registerChosen(player, id == BUTTON_REGISTER_DENY);
            this.picking = false;
            this.setSample(ItemStack.EMPTY);
            return true;
        }
        return false;
    }

    /**
     * What was ticked, each in the first free slot of the filter, or of the Keep Out row for {@code deny}: the mod entry
     * if its mod was ticked, then a tag entry for each tag ticked, in the list's order. One already there is skipped.
     */
    private void registerChosen(Player player, boolean deny) {
        ItemStack sample = this.sample();
        String mod = this.sampleMod();
        Container target = deny ? this.deny : this.filter;
        int size = deny ? SorterBlockEntity.DENY_SLOTS : this.tier.filterSlots;
        if (this.modChosen && mod != null && !holdsMod(target, size, mod) && !put(player, target, size, SorterTags.modEntry(sample), deny)) {
            return;
        }
        List<TagKey<Item>> choices = this.choices();
        for (int index = this.chosen.nextSetBit(0); index >= 0 && index < choices.size(); index = this.chosen.nextSetBit(index + 1)) {
            TagKey<Item> tag = choices.get(index);
            if (!holdsTag(target, size, tag) && !put(player, target, size, SorterTags.entry(sample, tag), deny)) {
                return;
            }
        }
    }

    /** Puts {@code entry} in the first free slot of {@code target}; with none free, says so and returns false. */
    private static boolean put(Player player, Container target, int size, ItemStack entry, boolean deny) {
        for (int i = 0; i < size; i++) {
            if (target.getItem(i).isEmpty()) {
                target.setItem(i, entry);
                return true;
            }
        }
        if (!player.level().isClientSide()) {
            player.sendOverlayMessage(Component.translatable(deny ? "message.nookworks.sorter.deny_full" : "message.nookworks.sorter.filter_full"));
        }
        return false;
    }

    @Override
    public void clicked(int slotIndex, int button, ContainerInput input, Player player) {
        if (slotIndex >= 0 && slotIndex < this.tier.filterSlots) {
            if (input != ContainerInput.PICKUP || this.picking) {
                return; // shift, number keys, Q, double-click: none of them register or clear a filter
            }
            ItemStack carried = this.getCarried();
            this.filter.setItem(slotIndex, carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1));
            return;
        }
        if (slotIndex == SAMPLE_SLOT) {
            if (input == ContainerInput.PICKUP && this.picking) {
                this.setSample(this.getCarried()); // a copy, as a filter entry is: the item stays in hand
            }
            return;
        }
        if (slotIndex >= DENY_SLOT && slotIndex < INVENTORY) {
            // the Keep Out row, as the filter: an item clicked in registers a copy, an empty hand clears
            if (input == ContainerInput.PICKUP && !this.picking) {
                ItemStack carried = this.getCarried();
                this.deny.setItem(slotIndex - DENY_SLOT, carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1));
            }
            return;
        }
        super.clicked(slotIndex, button, input, player);
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
        if (this.picking) {
            this.setSample(stack);
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
        if (this.pickerButton(player, id)) {
            return true;
        }
        if (id == BUTTON_FLAG + SorterSettings.BLACKLIST || id == BUTTON_FLAG + SorterSettings.MATCH_COMPONENTS) {
            if (this.settings == null || !this.tier.configurable) {
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
