package dev.nookworks.sorter;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The tag and mod picker of a menu with ghost entries - the Precision Sorter's, and any add-on's that sorts as it does
 * (nookTechnology's Precision Sorting Tunnel, 2026-11-10: the user, 「そこがTechと食い違うと、大きくユーザー体験を損なう」 -
 * one picker, so the two never drift apart). A ghost sample slot, the sample's mod and its tags
 * ({@link SorterTags#choices}) to tick, and a Register button for each {@link Destination}, which puts a mod entry or a
 * tag entry there for each one ticked. The sample and the ticks belong to the open screen alone and are never saved;
 * the client keeps its copy in step by applying the presses it sends ({@code TagPickerView}).
 * <p>
 * The menu makes one, adds its {@link #sampleSlot}, hides its own slots while it is {@link #open}, and hands it its
 * clicks: {@link #clicked} from {@code clicked}, {@link #quickMove} from {@code quickMoveStack}, {@link #button} from
 * {@code clickMenuButton}. Its button ids are {@code first} on ({@link #ids}), clear of the menu's own.
 */
public final class TagPicker {
    /**
     * Its buttons' ids, after the {@code first} it is given: opened, closed, the mod's line ticked, Register into
     * destination {@code n} at {@code REGISTER + n}, and the tag at {@code i} in {@link #choices} ticked at
     * {@code TICKS + i}. The same as {@link #openButton} and the rest, for a menu's constants.
     */
    public static final int OPEN = 0;
    public static final int CLOSE = 1;
    public static final int MOD = 2;
    public static final int REGISTER = 3;
    public static final int TICKS = 16;
    /** The most destinations it takes. */
    public static final int MOST_DESTINATIONS = 8;

    /**
     * Where its picks can go: {@code size} slots of ghost {@code entries}, the first free one taking each pick, and what a
     * player is told over the hotbar when none is free (on the server; read when it is said, so it may follow a switch).
     */
    public record Destination(Container entries, int size, Supplier<Component> full) {}

    private final int first;
    private final List<Destination> destinations;
    private final SimpleContainer sample = new SimpleContainer(1);
    private @Nullable Slot sampleSlot;
    private boolean open;
    private final BitSet chosen = new BitSet();
    private boolean modChosen;

    /**
     * Button ids {@code first} to {@code first + 15} and from {@code first + 16} on are this picker's ({@link #ids}): an
     * add-on's menu gives a {@code first} past its own buttons and Nookworks' tabs (20-39).
     */
    public TagPicker(int first, List<Destination> destinations) {
        if (destinations.isEmpty() || destinations.size() > MOST_DESTINATIONS) {
            throw new IllegalArgumentException("a picker takes 1 to " + MOST_DESTINATIONS + " destinations: " + destinations.size());
        }
        this.first = first;
        this.destinations = List.copyOf(destinations);
    }

    /**
     * A button's place under the list ({@link #layout}): its row from the first, and {@link #WHOLE} the row's width,
     * {@link #LEFT} or {@link #RIGHT} half of it.
     */
    public record Place(int row, int column) {
        public static final int WHOLE = 0;
        public static final int LEFT = 1;
        public static final int RIGHT = 2;
    }

    /**
     * The one rule for the buttons under every picker's list ({@code TagPickerView} lays them out by it), so Back is
     * where a player looks for it on any screen: each destination's Register in the order given, two that fit half a
     * row ({@code fitsHalf}, by their label and sign) sharing one, left then right; one that does not, or one left
     * over, a row of its own; and last Back, always a row of its own. The Precision Sorter's: Register, Keep these out
     * (too wide for half), Back. nookTechnology's Precision Sorting Tunnel's: Register left and Register right in one
     * row, Back. Each destination's place, then Back's.
     */
    public static List<Place> layout(List<Boolean> fitsHalf) {
        List<Place> places = new ArrayList<>(fitsHalf.size() + 1);
        int row = 0;
        int waiting = -1; // one that fits half a row, waiting to see whether the next shares it
        for (int i = 0; i < fitsHalf.size(); i++) {
            if (fitsHalf.get(i)) {
                if (waiting < 0) {
                    waiting = i;
                    places.add(null);
                    continue;
                }
                places.set(waiting, new Place(row, Place.LEFT));
                places.add(new Place(row, Place.RIGHT));
                waiting = -1;
            } else {
                if (waiting >= 0) {
                    places.set(waiting, new Place(row++, Place.WHOLE));
                    waiting = -1;
                }
                places.add(new Place(row, Place.WHOLE));
            }
            row++;
        }
        if (waiting >= 0) {
            places.set(waiting, new Place(row++, Place.WHOLE));
        }
        places.add(new Place(row, Place.WHOLE));
        return places;
    }

    // ---- the menu's side ----

    /**
     * The ghost sample slot at ({@code x}, {@code y}), for the menu to add once ({@code addSlot}): shown only while the
     * picker is open, a copy of what is clicked into it, never taken out. The Precision Sorter has it at 8, 22, beside the
     * list ({@code TagPickerView} draws its frame and caption there).
     */
    public Slot sampleSlot(int x, int y) {
        if (this.sampleSlot != null) {
            throw new IllegalStateException("the sample slot is made once");
        }
        this.sampleSlot = new Slot(this.sample, 0, x, y) {
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
                return TagPicker.this.open;
            }
        };
        return this.sampleSlot;
    }

    /**
     * A click on the menu's slot {@code slotIndex}: whether it was the sample's, which takes it - a plain click while open
     * sets the sample to a copy of what is carried (the item stays in hand), or clears it with an empty hand.
     */
    public boolean clicked(AbstractContainerMenu menu, int slotIndex, ContainerInput input) {
        if (this.sampleSlot == null || slotIndex < 0 || slotIndex >= menu.slots.size() || menu.slots.get(slotIndex) != this.sampleSlot) {
            return false;
        }
        if (input == ContainerInput.PICKUP && this.open) {
            this.setSample(menu.getCarried());
        }
        return true;
    }

    /** The sample slot made by {@link #sampleSlot}, or null before. */
    public @Nullable Slot slot() {
        return this.sampleSlot;
    }

    /**
     * A shift-click on {@code stack} in the player's inventory: whether the picker took it, which it does while open -
     * the item becomes the sample, and nothing moves.
     */
    public boolean quickMove(ItemStack stack) {
        return this.offerSample(stack);
    }

    /**
     * {@code stack} offered as the sample from elsewhere - an item dragged from JEI's list onto the sample slot, through
     * the menu's own packet: whether it was taken, which it is only while open (a copy of one, the ticks cleared).
     */
    public boolean offerSample(ItemStack stack) {
        if (!this.open || stack.isEmpty()) {
            return false;
        }
        this.setSample(stack);
        return true;
    }

    /**
     * An item dropped from a recipe viewer onto slot {@code slotIndex} of the menu (its ghost slots' {@code setGhost}):
     * whether it was the sample slot and the picker took it as the sample, which it does only while open.
     */
    public boolean ghost(int slotIndex, ItemStack stack) {
        return this.takesGhost(slotIndex) && this.offerSample(stack);
    }

    /** Whether slot {@code slotIndex} of the menu is the sample slot, shown (the picker open): where a drop is taken. */
    public boolean takesGhost(int slotIndex) {
        return this.open && this.sampleSlot != null && slotIndex == this.sampleSlot.index;
    }

    /**
     * A button pressed: whether {@code id} is this picker's. Applied by the server from the packet and by the client as it
     * sends it ({@code TagPickerView}), so both keep the same ticks.
     */
    public boolean button(Player player, int id) {
        int at = id - this.first;
        if (at < 0) {
            return false;
        }
        if (at >= TICKS) {
            int index = at - TICKS;
            if (this.open && index < this.choices().size()) {
                this.chosen.flip(index);
            }
            return true;
        }
        if (at == OPEN || at == CLOSE) {
            this.open = at == OPEN;
            this.setSample(ItemStack.EMPTY);
            return true;
        }
        if (at == MOD) {
            this.modChosen = this.open && !this.modChosen && !this.sample().isEmpty();
            return true;
        }
        int destination = at - REGISTER;
        if (destination >= 0 && destination < this.destinations.size()) {
            if (this.open) {
                this.register(player, this.destinations.get(destination));
            }
            this.open = false;
            this.setSample(ItemStack.EMPTY);
            return true;
        }
        return at < TICKS; // one of its sixteen, not in use
    }

    /** Whether {@code id} is one of this picker's buttons. */
    public boolean ids(int id) {
        return id >= this.first;
    }

    /** The ids of its buttons, for the screen and for tests that press them as a player would. */
    public int openButton() {
        return this.first + OPEN;
    }

    public int closeButton() {
        return this.first + CLOSE;
    }

    /** The sample's mod's line ticked or unticked. */
    public int modButton() {
        return this.first + MOD;
    }

    /** Register into destination {@code destination}, in the order given. */
    public int registerButton(int destination) {
        return this.first + REGISTER + destination;
    }

    /** The tag at {@code index} in {@link #choices} ticked or unticked. */
    public int tickButton(int index) {
        return this.first + TICKS + index;
    }

    // ---- what it holds ----

    /** Whether it is open, in the place of the menu's own slots. */
    public boolean open() {
        return this.open;
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

    public List<Destination> destinations() {
        return this.destinations;
    }

    /** Slots still free in destination {@code destination}. */
    public int free(int destination) {
        Destination d = this.destinations.get(destination);
        int free = 0;
        for (int i = 0; i < d.size(); i++) {
            free += d.entries().getItem(i).isEmpty() ? 1 : 0;
        }
        return free;
    }

    /** Whether destination {@code destination} holds an entry for {@code tag}, or with {@code tag} null, for the mod {@code mod}. */
    public boolean holds(int destination, @Nullable TagKey<Item> tag, @Nullable String mod) {
        Destination d = this.destinations.get(destination);
        return tag != null ? holdsTag(d, tag) : mod != null && holdsMod(d, mod);
    }

    /** Whether any destination holds it already: its line is shown ticked and dim. */
    public boolean taken(@Nullable TagKey<Item> tag, @Nullable String mod) {
        for (int i = 0; i < this.destinations.size(); i++) {
            if (this.holds(i, tag, mod)) {
                return true;
            }
        }
        return false;
    }

    private static boolean holdsTag(Destination d, TagKey<Item> tag) {
        for (int i = 0; i < d.size(); i++) {
            if (tag.equals(SorterTags.of(d.entries().getItem(i)))) {
                return true;
            }
        }
        return false;
    }

    private static boolean holdsMod(Destination d, String namespace) {
        for (int i = 0; i < d.size(); i++) {
            if (namespace.equals(SorterTags.modOf(d.entries().getItem(i)))) {
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
     * What was ticked, each in the first free slot of {@code d}: the mod entry if its mod was ticked, then a tag entry for
     * each tag ticked, in the list's order. One already there is skipped; with no slot free, the player is told so and the
     * rest is left.
     */
    private void register(Player player, Destination d) {
        ItemStack sample = this.sample();
        String mod = this.sampleMod();
        if (this.modChosen && mod != null && !holdsMod(d, mod) && !put(player, d, SorterTags.modEntry(sample))) {
            return;
        }
        List<TagKey<Item>> choices = this.choices();
        for (int index = this.chosen.nextSetBit(0); index >= 0 && index < choices.size(); index = this.chosen.nextSetBit(index + 1)) {
            TagKey<Item> tag = choices.get(index);
            if (!holdsTag(d, tag) && !put(player, d, SorterTags.entry(sample, tag))) {
                return;
            }
        }
    }

    private static boolean put(Player player, Destination d, ItemStack entry) {
        for (int i = 0; i < d.size(); i++) {
            if (d.entries().getItem(i).isEmpty()) {
                d.entries().setItem(i, entry);
                return true;
            }
        }
        if (!player.level().isClientSide()) {
            player.sendOverlayMessage(d.full().get());
        }
        return false;
    }
}
