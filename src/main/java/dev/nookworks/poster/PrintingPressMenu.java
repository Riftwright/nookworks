package dev.nookworks.poster;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.nookworks.label.LabelText;
import dev.nookworks.machine.RedstoneMode;
import dev.nookworks.menu.ShiftClick;
import dev.nookworks.photo.CameraItem;
import dev.nookworks.photo.Photo;
import dev.nookworks.photo.Photos;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModMenus;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Printing Press GUI: paper, one slot per process ink, the printed copy and a Print button, then a choice of posters or
 * labels - the posters as a scrolling grid of designs, labels as a sample slot with a preview of the label it makes.
 * The chosen design is synced as its index into the sorted design list rather than as an id; labels are the first.
 */
public class PrintingPressMenu extends AbstractContainerMenu {
    /** The letter labels' text, two letters a slot, from this slot on. */
    public static final int DATA_TEXT = 6;
    /** The chosen photo's number, in two slots, and its size's place in {@link Photo#SIZES}. */
    public static final int DATA_PHOTO = DATA_TEXT + LabelText.MAX / 2;
    public static final int DATA_COUNT = DATA_PHOTO + 3;
    public static final int BUTTON_PRINT = 0;
    /** Negative, so it can never be mistaken for a design (those are 1 and up). */
    public static final int BUTTON_REDSTONE = -1;
    /** Starts the letter labels' text over; the letters follow, one button each: {@link #letterButton}. */
    public static final int BUTTON_TEXT_CLEAR = -2;
    private static final int BUTTON_LETTER = -100;
    /** Prints the photo at the size {@link Photo#SIZES} has at this, less the index. */
    private static final int BUTTON_PHOTO_SIZE = -300;
    /** Chooses (or, chosen already, lets go of) a photo: this, less its number. */
    private static final int BUTTON_PHOTO = -1000;

    public static final int PAPER_X = 8;
    public static final int PAPER_Y = 20;
    public static final int INK_X = 30;
    public static final int INK_Y = 20;
    public static final int OUTPUT_X = 134;
    public static final int OUTPUT_Y = 20;
    /** In the labels page, under the Print button. */
    public static final int SAMPLE_X = 18;
    public static final int SAMPLE_Y = 96;
    /** The screen is this and 82 tall: 224, inside the 240 px a small window gives the GUI. */
    public static final int INVENTORY_Y = 142;

    private final Container container;
    private final ContainerData data;
    private final @Nullable PrintingPressBlockEntity press;
    private final List<Identifier> designs;
    /**
     * Server side: the letters as this player types them, spaces at the ends kept, while their screen sends them one by
     * one. Each player's own: with one buffer in the press, two players typing at once ran their letters together into
     * a label neither typed; now the last to type sets it, whole.
     */
    private final StringBuilder typing = new StringBuilder();
    /** Client side: the screen is showing the labels page, where the sample slot is. */
    public boolean labelPage;

    /** Client side. */
    public PrintingPressMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainer(PrintingPressBlockEntity.SLOTS), new SimpleContainerData(DATA_COUNT), null);
    }

    public PrintingPressMenu(int containerId, Inventory inventory, Container container, ContainerData data,
            @Nullable PrintingPressBlockEntity press) {
        super(ModMenus.PRINTING_PRESS.get(), containerId);
        this.container = container;
        this.data = data;
        this.press = press;
        if (press != null) {
            this.typing.append(press.labelText()); // what it says, for letters typed on after it
        }
        this.designs = PrintingPressBlockEntity.entries(inventory.player.level().registryAccess());

        this.addSlot(new FilterSlot(container, PrintingPressBlockEntity.PAPER, PAPER_X, PAPER_Y));
        for (int i = 0; i < PrintingPressBlockEntity.INK_SLOTS; i++) {
            this.addSlot(new FilterSlot(container, PrintingPressBlockEntity.FIRST_INK + i, INK_X + i * 18, INK_Y));
        }
        this.addSlot(new Slot(container, PrintingPressBlockEntity.OUTPUT, OUTPUT_X, OUTPUT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
        this.addSlot(new FilterSlot(container, PrintingPressBlockEntity.SAMPLE, SAMPLE_X, SAMPLE_Y) {
            @Override
            public int getMaxStackSize() {
                return 1; // one to print from is enough
            }

            /** Shown, and clickable, on the labels page only. */
            @Override
            public boolean isActive() {
                return PrintingPressMenu.this.press != null || PrintingPressMenu.this.labelPage;
            }
        });
        this.addStandardInventorySlots(inventory, 8, INVENTORY_Y);
        this.addDataSlots(data);
    }

    /** Labels, then every design the pack defines, in the order the GUI shows them. */
    public List<Identifier> designs() {
        return this.designs;
    }

    public Container container() {
        return this.container;
    }

    public @Nullable Identifier selected() {
        int index = this.data.get(0) - 1; // the entry's index, plus one; 0 is none
        return index >= 0 && index < this.designs.size() ? this.designs.get(index) : null;
    }

    /** Whether labels are what the press is set to print. */
    public boolean printingLabels() {
        return PrintingPressBlockEntity.LABEL.equals(this.selected());
    }

    /** 0-100. */
    public int progressPercent() {
        return this.data.get(1);
    }

    public int energy() {
        return this.data.get(2);
    }

    public boolean running() {
        return this.data.get(3) != 0;
    }

    public RedstoneMode redstoneMode() {
        return RedstoneMode.byId(this.data.get(4));
    }

    /** The button that adds the letter {@code c} to the letter labels' text. */
    public static int letterButton(char c) {
        return BUTTON_LETTER - LabelText.code(c);
    }

    /** What letter labels will say, as the press last sent it; "" for picture labels. Read from its own slots only. */
    public String labelText() {
        StringBuilder text = new StringBuilder();
        for (int i = DATA_TEXT; i < DATA_PHOTO; i++) { // the photo's slots after them are numbers, not letters (review r12)
            LabelText.unpack(this.data.get(i), text);
        }
        return text.toString();
    }

    /** The photo chosen to print, by number; 0 for none. */
    public int photo() {
        return this.data.get(DATA_PHOTO) | this.data.get(DATA_PHOTO + 1) << 15;
    }

    /** The size the chosen photo prints at. */
    public Photo.Size photoSize() {
        int index = this.data.get(DATA_PHOTO + 2);
        return index >= 0 && index < Photo.SIZES.size() ? Photo.SIZES.get(index) : Photo.SIZES.getFirst();
    }

    /** Whether a photo is what the press is set to print. */
    public boolean printingPhoto() {
        return PrintingPressBlockEntity.PHOTO.equals(this.selected());
    }

    /** The photos in the cameras {@code inventory} holds, newest first, each once: what the press offers to print. */
    public static List<Integer> photos(Inventory inventory) {
        List<Integer> photos = new ArrayList<>();
        for (ItemStack stack : inventory) {
            if (stack.is(ModItems.CAMERA.get())) {
                for (Integer number : CameraItem.photos(stack).reversed()) {
                    if (!photos.contains(number)) {
                        photos.add(number);
                    }
                }
            }
        }
        return photos;
    }

    /** The button that chooses photo {@code number}. */
    public static int photoButton(int number) {
        return BUTTON_PHOTO - number;
    }

    /** The button that prints the photo at {@code size}. */
    public static int sizeButton(Photo.Size size) {
        return BUTTON_PHOTO_SIZE - Photo.SIZES.indexOf(size);
    }

    /** Whether a job is waiting on the redstone mode. */
    public boolean heldByRedstone() {
        return this.data.get(5) != 0;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (this.press == null) {
            return false;
        }
        if (id == BUTTON_REDSTONE) {
            this.press.setRedstoneMode(this.press.redstoneMode().next());
            return true;
        }
        if (id <= BUTTON_PHOTO - 1) {
            int number = BUTTON_PHOTO - id;
            if (this.press.photo() == number && PrintingPressBlockEntity.PHOTO.equals(this.press.selected())) {
                this.press.select(null); // the photo chosen already: let go of it
                return true;
            }
            // only a photo the player carries: not one of anybody's by its number
            if (!(player instanceof ServerPlayer server) || !Photos.carries(server, number)) {
                return false;
            }
            this.press.selectPhoto(number);
            return true;
        }
        if (id <= BUTTON_PHOTO_SIZE && id > BUTTON_PHOTO_SIZE - Photo.SIZES.size()) {
            this.press.setPhotoSize(Photo.SIZES.get(BUTTON_PHOTO_SIZE - id));
            return true;
        }
        if (id == BUTTON_TEXT_CLEAR) {
            this.typing.setLength(0);
            this.press.setLabelText("");
            return true;
        }
        if (id <= BUTTON_LETTER - 1) {
            char c = LabelText.of(BUTTON_LETTER - id);
            if (c == 0) {
                return false;
            }
            // kept uncleaned: a space typed now may be followed by a letter
            if (LabelText.allowed(c) && this.typing.length() < LabelText.MAX) {
                this.typing.append(c);
            }
            this.press.setLabelText(this.typing.toString());
            return true;
        }
        if (id == BUTTON_PRINT) {
            // a photo is printed only by someone it may be shown to - its taker, someone carrying a camera holding it, or
            // anyone once it is printed: a press left set to another's photo would otherwise make it everyone's (review
            // r12 fix check). Stopping is anyone's.
            if (!this.press.running() && PrintingPressBlockEntity.PHOTO.equals(this.press.selected())
                    && !(player instanceof ServerPlayer server && Photos.mayShow(server, this.press.photo()))) {
                return false;
            }
            this.press.setRunning(!this.press.running());
            return true;
        }
        int index = id - 1;
        if (index < 0 || index >= this.designs.size()) {
            return false;
        }
        Identifier design = this.designs.get(index);
        if (PrintingPressBlockEntity.PHOTO.equals(design) && !design.equals(this.press.selected())) {
            return false; // a photo is chosen by its own button, which says which
        }
        // Clicking the design already chosen turns the press off again.
        this.press.select(design.equals(this.press.selected()) ? null : design);
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int machineSlots = PrintingPressBlockEntity.SLOTS;
        if (index < machineSlots) {
            boolean output = index == PrintingPressBlockEntity.OUTPUT;
            if (!this.moveItemStackTo(stack, machineSlots, machineSlots + ShiftClick.INVENTORY_SLOTS, output)) {
                return ItemStack.EMPTY;
            }
        } else if (PrintingPressBlockEntity.accepts(PrintingPressBlockEntity.PAPER, stack) || Posters.isInk(player.level(), stack)) {
            if (!this.moveItemStackTo(stack, 0, PrintingPressBlockEntity.OUTPUT, false)) {
                return ItemStack.EMPTY;
            }
        } else if (this.printingLabels()) {
            // anything else is a sample to print labels of, while labels are chosen
            if (!this.moveItemStackTo(stack, PrintingPressBlockEntity.SAMPLE, PrintingPressBlockEntity.SAMPLE + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!ShiftClick.acrossInventory(stack, index, machineSlots, this::moveItemStackTo)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
    }

    /** Paper and each process-ink slot only take their own item, on both sides of the GUI. */
    private static class FilterSlot extends Slot {
        private final int index;

        private FilterSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
            this.index = index;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return PrintingPressBlockEntity.accepts(this.index, stack);
        }
    }
}
