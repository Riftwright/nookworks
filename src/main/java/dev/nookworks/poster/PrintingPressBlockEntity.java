package dev.nookworks.poster;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import dev.nookworks.label.LabelItem;
import dev.nookworks.label.LabelText;
import dev.nookworks.machine.MachineBlockEntity;
import dev.nookworks.machine.MachineStatus;
import dev.nookworks.machine.MachineTuning;
import dev.nookworks.photo.Photo;
import dev.nookworks.photo.PhotoPrints;
import dev.nookworks.photo.Photos;
import dev.nookworks.registry.ModBlockEntities;
import dev.nookworks.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Printing Press: load Fine Paper and the four process inks, pick a design, then press Print for one copy.
 * <p>
 * One press, one copy: the job ends when the copy comes out, and a job that finds the paper or an ink gone is
 * dropped rather than left waiting, so the press never starts up again by itself when a hopper tops it up.
 * <p>
 * This exists so posters do not need a hand recipe each. The press has one slot per process colour
 * (cyan, magenta, yellow, black); a design that names something else cannot be printed here.
 * <p>
 * It also prints labels ({@link dev.nookworks.label.LabelItem}): a sample goes in the slot of its own, and the first
 * entry in the list of designs, {@link #LABEL}, prints {@link #LABELS_PER_COPY} labels with its picture from one sheet
 * of Fine Paper and a drop of each ink. The sample stays; a label as the sample prints what that label shows. With
 * letters typed in ({@link #labelText()}), it prints letter labels instead, and the sample is not needed: they are
 * printed in black alone, so they take a drop of black ink and no other.
 * <p>
 * And it prints photos ({@link Photos}): the second entry, {@link #PHOTO}, with a photo chosen from a camera the player
 * carries and the size to print it at, prints it as a poster, as a design would be - a sheet and the four inks.
 */
public class PrintingPressBlockEntity extends MachineBlockEntity implements MenuProvider {
    public static final int PAPER = 0;
    public static final int FIRST_INK = 1;
    public static final int INK_SLOTS = PosterVariant.PROCESS_INKS.size();
    public static final int OUTPUT = FIRST_INK + INK_SLOTS;
    /** What labels are printed of. After the output, so the slots saved before it existed keep their places. */
    public static final int SAMPLE = OUTPUT + 1;
    public static final int SLOTS = SAMPLE + 1;

    /** The entry for printing labels, first in the list the press shows; not a poster design. */
    public static final Identifier LABEL = Nookworks.id("label");
    public static final int LABELS_PER_COPY = 8;
    /** The entry for printing a photo, second in the list; which photo, and at what size, the press keeps beside it. */
    public static final Identifier PHOTO = Nookworks.id("photo");

    public static final int CAPACITY = 10_000;
    public static final int FE_PER_TICK = 20;
    public static final int TICKS = 60;

    private static final int[] INPUT_SLOTS;
    private static final int[] OUTPUT_SLOTS = {OUTPUT};

    static {
        INPUT_SLOTS = new int[1 + INK_SLOTS];
        for (int i = 0; i < INPUT_SLOTS.length; i++) {
            INPUT_SLOTS[i] = i;
        }
    }

    private @Nullable Identifier selected;
    /** What letter labels say; "" to print pictures of the sample. */
    private String labelText = "";
    /** The photo chosen to print, by number; 0 for none. */
    private int photo;
    private Photo.Size photoSize = Photo.SIZES.getFirst();
    private int progress;
    private boolean running;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> PrintingPressBlockEntity.this.selectedIndex() + 1; // 0 for none: a screen not yet sent a value reads none
                case 1 -> PrintingPressBlockEntity.this.progress * 100 / MachineTuning.time(TICKS);
                case 2 -> PrintingPressBlockEntity.this.energyStored();
                case 3 -> PrintingPressBlockEntity.this.running ? 1 : 0;
                case 4 -> PrintingPressBlockEntity.this.redstone.ordinal();
                case 5 -> PrintingPressBlockEntity.this.status == MachineStatus.REDSTONE ? 1 : 0;
                // a value goes over the wire as a short: the photo's number in two halves of 15 bits
                case PrintingPressMenu.DATA_PHOTO -> PrintingPressBlockEntity.this.photo & 0x7FFF;
                case PrintingPressMenu.DATA_PHOTO + 1 -> PrintingPressBlockEntity.this.photo >>> 15 & 0x7FFF;
                case PrintingPressMenu.DATA_PHOTO + 2 -> Photo.SIZES.indexOf(PrintingPressBlockEntity.this.photoSize);
                default -> LabelText.pack(PrintingPressBlockEntity.this.labelText, (index - PrintingPressMenu.DATA_TEXT) * 2);
            };
        }

        @Override
        public void set(int index, int value) {
            // Read-only: the design and the Print button are menu buttons, not writes to this.
        }

        @Override
        public int getCount() {
            return PrintingPressMenu.DATA_COUNT;
        }
    };

    /** The chosen design's place in the sorted list the GUI shows, or -1 when the press is idle. */
    private int selectedIndex() {
        return this.selected == null || this.level == null ? -1 : entries(this.level.registryAccess()).indexOf(this.selected);
    }

    /** What the press can print, in the order its GUI shows them: labels, a photo, then every poster design. */
    public static List<Identifier> entries(HolderLookup.Provider registries) {
        List<Identifier> entries = new ArrayList<>();
        entries.add(LABEL);
        entries.add(PHOTO);
        entries.addAll(Posters.ids(registries));
        return entries;
    }

    public PrintingPressBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PRINTING_PRESS.get(), pos, state, SLOTS, CAPACITY, 200, 0);
    }

    public @Nullable Identifier selected() {
        return this.selected;
    }

    public void select(@Nullable Identifier design) {
        if (!Objects.equals(this.selected, design)) {
            this.selected = design;
            if (!PHOTO.equals(design)) {
                this.photo = 0;
            }
            this.progress = 0;
            this.running = false;
            this.setChanged();
        }
    }

    /** Sets the press to print photo {@code number} (one the player has in a camera: the menu sees to that). */
    public void selectPhoto(int number) {
        this.select(PHOTO);
        if (this.photo != number) {
            this.photo = number;
            this.progress = 0;
            this.running = false;
            this.setChanged();
        }
    }

    public int photo() {
        return this.photo;
    }

    public Photo.Size photoSize() {
        return this.photoSize;
    }

    public void setPhotoSize(Photo.Size size) {
        if (!size.equals(this.photoSize)) {
            this.photoSize = size;
            this.progress = 0;
            this.running = false;
            this.setChanged();
        }
    }

    /** The poster id a copy carries: the design, or for a photo its print at the chosen size; null with nothing chosen. */
    public @Nullable Identifier copyId() {
        if (PHOTO.equals(this.selected)) {
            return this.photo > 0 ? PhotoPrints.id(this.photo, this.photoSize) : null;
        }
        return this.selected;
    }

    public boolean running() {
        return this.running;
    }

    /** The lamp is lit while a page is printing - not while a job waits for power. */
    @Override
    protected boolean lampOn() {
        return this.running && this.status == MachineStatus.RUNNING;
    }

    public String labelText() {
        return this.labelText;
    }

    /** Sets what letter labels say (cleaned: see {@link dev.nookworks.label.LabelText#clean}); "" for picture labels. */
    public void setLabelText(String text) {
        String clean = LabelText.clean(text);
        if (!clean.equals(this.labelText)) {
            this.labelText = clean;
            this.setChanged();
        }
    }

    /** Starts or cancels the job: one copy of the chosen design. Starting with no design chosen does nothing. */
    public void setRunning(boolean running) {
        boolean next = running && this.selected != null;
        if (this.running != next) {
            this.running = next;
            if (!this.running) {
                this.progress = 0;
            }
            this.setChanged();
        }
    }

    public int progress() {
        return this.progress;
    }

    /** The dedicated slot for this process ink, or -1 when the press has no home for it. */
    public static int slotFor(Item ink) {
        int index = PosterVariant.PROCESS_INKS.indexOf(ink);
        return index < 0 ? -1 : FIRST_INK + index;
    }

    /** What an empty slot should show as a ghost, or empty when the slot is the output. */
    public static ItemStack ghost(int slot) {
        if (slot == PAPER) {
            return new ItemStack(ModItems.FINE_PAPER.get());
        }
        int ink = slot - FIRST_INK; // no ghost in the sample slot: it takes any item, and a blank label is the one it refuses
        return ink >= 0 && ink < INK_SLOTS ? new ItemStack(PosterVariant.PROCESS_INKS.get(ink)) : ItemStack.EMPTY;
    }

    public static boolean accepts(int slot, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (slot == PAPER) {
            return stack.is(ModItems.FINE_PAPER.get());
        }
        if (slot == SAMPLE) {
            return !LabelItem.of(stack).isEmpty();
        }
        int ink = slot - FIRST_INK;
        return ink >= 0 && ink < INK_SLOTS && stack.is(PosterVariant.PROCESS_INKS.get(ink));
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, PrintingPressBlockEntity press) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        boolean labels = LABEL.equals(press.selected);
        // a photo let go of, or taken out by an operator, since it was chosen prints nothing
        // nor, with photos turned off, a blank sheet for paper and ink
        boolean photoGone = PHOTO.equals(press.selected) && (!Photos.allowed() || Photos.get(server.getServer(), press.photo) == null);
        PosterVariant design = labels || photoGone ? null : Posters.get(server, press.copyId());
        if (!press.running || (labels ? !press.canPrintLabels() : design == null || !press.canPrint(design))) {
            press.status = MachineStatus.IDLE;
            // A job short of paper or ink is dropped, not kept waiting: topped up later, the press would start up by
            // itself.
            press.setRunning(false);
            return;
        }
        if (!press.redstoneAllows()) {
            press.status = MachineStatus.REDSTONE;
            return;
        }
        if (press.energyStored() < press.workCost(FE_PER_TICK)) {
            press.status = MachineStatus.NO_POWER; // and its lamp dark, as any machine's without power
            return;
        }
        press.status = MachineStatus.RUNNING;
        press.payWork(FE_PER_TICK);
        if (++press.progress >= MachineTuning.time(TICKS)) {
            if (labels) {
                press.printLabels();
            } else {
                press.print(design);
            }
            press.setRunning(false); // one copy per press of the button
        }
        press.setChanged();
    }

    /** Whether the paper, the inks and the room for the copy are all there. */
    public boolean canPrint(PosterVariant design) {
        if (this.items.get(PAPER).getCount() < PosterVariant.PAPER_PER_COPY) {
            return false;
        }
        ItemStack out = this.items.get(OUTPUT);
        Identifier copy = this.copyId();
        if (!out.isEmpty() && (copy == null || !copy.equals(Posters.idOf(out)) || out.getCount() >= out.getMaxStackSize())) {
            return false;
        }
        return missingInk(this, design) == null;
    }

    /**
     * The labels a print would give: {@link #LABELS_PER_COPY} saying {@code text}, or with none, of the sample's picture;
     * empty with neither.
     */
    public static ItemStack labelsOf(Container slots, String text) {
        ItemStack labels = text.isEmpty() ? LabelItem.of(slots.getItem(SAMPLE)) : LabelItem.ofText(text);
        return labels.isEmpty() ? labels : labels.copyWithCount(LABELS_PER_COPY);
    }

    /** Whether the output has room for a print of {@code copy}: empty, or the same thing with room enough. */
    public static boolean roomFor(Container slots, ItemStack copy) {
        ItemStack out = slots.getItem(OUTPUT);
        return out.isEmpty() || ItemStack.isSameItemSameComponents(out, copy) && out.getCount() + copy.getCount() <= out.getMaxStackSize();
    }

    /**
     * The inks a print of labels takes: black alone for letters - typed, or copied from a lettered label put in as the
     * sample - and all four for a picture.
     */
    public static List<Item> labelInks(Container slots, String text) {
        String letters = text.isEmpty() ? LabelItem.text(labelsOf(slots, text)) : text;
        return letters.isEmpty() ? PosterVariant.PROCESS_INKS : List.of(Items.DYE.pick(DyeColor.BLACK));
    }

    /** Whether the paper, the inks, a sample and the room for the labels are all there. */
    public boolean canPrintLabels() {
        ItemStack labels = labelsOf(this, this.labelText);
        return !labels.isEmpty() && this.items.get(PAPER).getCount() >= PosterVariant.PAPER_PER_COPY && roomFor(this, labels)
                && missing(this, labelInks(this, this.labelText)).isEmpty();
    }

    private void printLabels() {
        ItemStack labels = labelsOf(this, this.labelText);
        this.items.get(PAPER).shrink(PosterVariant.PAPER_PER_COPY);
        for (Item ink : labelInks(this, this.labelText)) {
            this.consume(ink);
        }
        ItemStack out = this.items.get(OUTPUT);
        if (out.isEmpty()) {
            this.items.set(OUTPUT, labels);
        } else {
            out.grow(labels.getCount());
        }
    }

    /**
     * The first ink {@code inks} has run out of, or null when it holds them all. Static and container-based so
     * the GUI can grey out designs client-side from the slots it already sees, with no extra syncing.
     */
    public static @Nullable Item missingInk(Container inks, PosterVariant design) {
        List<Item> missing = missing(inks, design);
        return missing.isEmpty() ? null : missing.get(0);
    }

    /** Every ink {@code inks} lacks for a copy of {@code design}, in the order of the ink slots. */
    public static List<Item> missing(Container inks, PosterVariant design) {
        return missing(inks, design.inkCost());
    }

    /** Every ink {@code inks} lacks to pay {@code cost}, one of each entry, in the order of the ink slots. */
    public static List<Item> missing(Container inks, List<Item> cost) {
        List<Item> missing = new ArrayList<>();
        Map<Item, Integer> needed = new LinkedHashMap<>();
        for (Item ink : cost) {
            needed.merge(ink, 1, Integer::sum);
        }
        for (Map.Entry<Item, Integer> entry : needed.entrySet()) {
            int slot = slotFor(entry.getKey());
            int have = 0;
            if (slot >= 0) {
                ItemStack stack = inks.getItem(slot);
                if (stack.is(entry.getKey())) {
                    have = stack.getCount();
                }
            }
            if (have < entry.getValue()) {
                missing.add(entry.getKey());
            }
        }
        missing.sort(Comparator.comparingInt(PrintingPressBlockEntity::slotFor));
        return missing;
    }

    private void print(PosterVariant design) {
        this.items.get(PAPER).shrink(PosterVariant.PAPER_PER_COPY);
        for (Item ink : design.inkCost()) {
            this.consume(ink);
        }
        ItemStack out = this.items.get(OUTPUT);
        if (out.isEmpty()) {
            this.items.set(OUTPUT, Posters.item(this.copyId()));
        } else {
            out.grow(1);
        }
        if (PHOTO.equals(this.selected) && this.level instanceof ServerLevel server) {
            Photos.printed(server.getServer(), this.photo); // kept now, when the camera lets it go
        }
    }

    private void consume(Item ink) {
        int slot = slotFor(ink);
        if (slot >= 0) {
            this.items.get(slot).shrink(1);
        }
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot != SAMPLE && accepts(slot, stack); // the sample is set by hand, not fed by a hopper
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return side == Direction.DOWN ? OUTPUT_SLOTS : INPUT_SLOTS;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == OUTPUT;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.nookworks.printing_press");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new PrintingPressMenu(containerId, inventory, this, this.data, this);
    }

    /** How far a print is, or, with none under way, what it is waiting for (as its screen and lamp say). */
    @Override
    public Component status() {
        if (this.running && this.status == MachineStatus.RUNNING) {
            return Component.translatable("nookworks.machine.printing_press", this.energyText(), this.progress * 100 / MachineTuning.time(TICKS));
        }
        return Component.translatable("nookworks.machine.printing_press.waiting", this.energyText(),
                Component.translatable(this.status.translationKey()));
    }

    /** To Jade, how far a print is or what it waits for: its power Jade shows itself. */
    @Override
    public void jade(java.util.function.Consumer<Component> lines) {
        lines.accept(this.running && this.status == MachineStatus.RUNNING
                ? Component.translatable("gui.nookworks.machine.running_percent", this.progress * 100 / MachineTuning.time(TICKS))
                : Component.translatable(this.status.translationKey()));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Progress", this.progress);
        output.putBoolean("Running", this.running);
        if (this.selected != null) {
            output.putString("Design", this.selected.toString());
        }
        if (!this.labelText.isEmpty()) {
            output.putString("LabelText", this.labelText);
        }
        if (this.photo > 0) {
            output.putInt("Photo", this.photo);
            output.putString("PhotoSize", this.photoSize.toString());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.progress = input.getIntOr("Progress", 0);
        this.running = input.getBooleanOr("Running", false);
        this.selected = Identifier.tryParse(input.getStringOr("Design", ""));
        this.labelText = LabelText.clean(input.getStringOr("LabelText", ""));
        this.photo = Math.max(0, input.getIntOr("Photo", 0));
        String[] size = input.getStringOr("PhotoSize", "").split("x");
        Photo.Size kept = null;
        try {
            kept = size.length == 2 ? Photo.size(Integer.parseInt(size[0]), Integer.parseInt(size[1])) : null;
        } catch (NumberFormatException ignored) {
            // an odd size reads as the whole photo
        }
        this.photoSize = kept != null ? kept : Photo.SIZES.getFirst();
    }
}
