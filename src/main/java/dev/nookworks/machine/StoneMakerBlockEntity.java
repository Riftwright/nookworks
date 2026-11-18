package dev.nookworks.machine;

import com.mojang.serialization.Codec;
import dev.nookworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import dev.nookworks.port.storage.ValueInput;
import dev.nookworks.port.storage.ValueOutput;
import dev.nookworks.port.transfer.Capabilities;
import dev.nookworks.port.transfer.ItemUtil;
import org.jspecify.annotations.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import dev.nookworks.port.storage.ValueIO;

/**
 * Stone Maker: a lava bucket in its right-hand slot, and a water bucket or blue ice in its left, make stone as vanilla's
 * flows do where they meet - cobblestone, or stone if its Settings tab says so, from water; basalt from blue ice, with
 * soul soil in its Extra Slots tab, as vanilla's basalt forms over soul soil beside blue ice (the user, 2026-11-14:
 * 「ソウルソイル！忘れてた」, 「触媒的な感じだろうから、アドオンタブに入れたらいい」 - the tab renamed Extra Slots the same
 * day). The buckets, the ice and the soul soil are kept, as the lava and water of a cobblestone generator are (the user, 2026-11-14: 「GUIに溶岩
 * バケツと水バケツを入れる枠を作って、そこに入れると丸石/石が作れる。水バケツの代わりに[青]氷を入れると、玄武岩が作れる」;
 * 「石と丸石は、右側の設定タブから切り替える」; no obsidian - 「どんだけ強いドリルを内蔵しているんですか」). One every
 * {@link #TICKS_PER_ITEM} ticks at {@link #FE_PER_TICK} FE/t, stone at {@link #FE_PER_TICK_STONE} (the furnace's work
 * done too). What it makes goes into the container below; without one it collects inside (up to a stack).
 * <p>
 * Before 1.7 it made cobblestone from nothing, the two buckets in its recipe: one saved before then is given them in its
 * slots as it loads, so it goes on as it did.
 */
public class StoneMakerBlockEntity extends MachineBlockEntity implements MachineInfo {
    public static final int FE_PER_TICK = 20;
    /** Stone, as cobblestone smelted: a half more. */
    public static final int FE_PER_TICK_STONE = 30;
    public static final int TICKS_PER_ITEM = 20;
    public static final int CAPACITY = 10_000;
    /**
     * What it makes (kept at slot 0, where 1.6 kept its cobblestone), what cools the lava, the lava; and the soul soil,
     * in the screen's Extra Slots tab (MachineMenu), for basalt.
     */
    public static final int OUTPUT = 0;
    public static final int COOLER = 1;
    public static final int LAVA = 2;
    public static final int SOUL_SOIL = 3;
    private static final int[] SLOTS = {OUTPUT, COOLER, LAVA, SOUL_SOIL};
    /** Its Settings tab's choice, saved always: a save without it is one from before the slots (see the class note). */
    private static final String MAKES_STONE_KEY = "makes_stone";

    private int progress;
    /** Stone rather than cobblestone, from water (blue ice makes basalt whatever this says). */
    private boolean makesStone;
    /** When what it holds is next passed on below: a hopper's beat after a try that found no room. */
    private final OutputRetry outputRetry = new OutputRetry();

    public StoneMakerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STONE_MAKER.get(), pos, state, 4, CAPACITY, 200, 0);
    }

    /**
     * What lava and {@code cooler} make: basalt with blue ice and soul soil, stone or cobblestone with water; null without
     * what it takes.
     */
    public static @Nullable Item product(ItemStack cooler, ItemStack lava, ItemStack soulSoil, boolean stone) {
        if (!lava.is(Items.LAVA_BUCKET)) {
            return null;
        }
        if (cooler.is(Items.BLUE_ICE)) {
            return soulSoil.is(Items.SOUL_SOIL) ? Items.BASALT : null;
        }
        return cooler.is(Items.WATER_BUCKET) ? stone ? Items.STONE : Items.COBBLESTONE : null;
    }

    /** What goes in the left-hand slot: a water bucket, or blue ice for basalt. */
    public static boolean cools(ItemStack stack) {
        return stack.is(Items.WATER_BUCKET) || stack.is(Items.BLUE_ICE);
    }

    public @Nullable Item product() {
        return product(this.items.get(COOLER), this.items.get(LAVA), this.items.get(SOUL_SOIL), this.makesStone);
    }

    public boolean makesStone() {
        return this.makesStone;
    }

    public void setMakesStone(boolean stone) {
        this.makesStone = stone;
        this.setChanged();
    }

    /** Its Settings tab's button: cobblestone and stone in turn. */
    @Override
    public void nextOption() {
        this.setMakesStone(!this.makesStone);
    }

    private static int energyFor(@Nullable Item product) {
        return product == Items.STONE ? FE_PER_TICK_STONE : FE_PER_TICK;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, StoneMakerBlockEntity maker) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        maker.flush(server, pos);
        Item product = maker.product();
        if (product == null) {
            maker.status = MachineStatus.IDLE; // the lava, or what cools it, not in: the status line says which go in
            savePower(maker);
            return;
        }
        ItemStack held = maker.items.get(OUTPUT);
        if (!held.isEmpty() && (!held.is(product) || held.getCount() >= held.getMaxStackSize())) {
            maker.status = MachineStatus.OUTPUT_BLOCKED; // full, or holding what it made before it was switched
            return;
        }
        if (!maker.redstoneAllows()) {
            maker.status = MachineStatus.REDSTONE;
            return;
        }
        int energy = energyFor(product);
        if (maker.energyStored() < maker.workCost(energy)) {
            maker.status = MachineStatus.NO_POWER;
            return;
        }
        maker.status = MachineStatus.RUNNING;
        maker.payWork(energy);
        if (++maker.progress >= MachineTuning.time(TICKS_PER_ITEM)) {
            maker.progress = 0;
            if (held.isEmpty()) {
                maker.items.set(OUTPUT, new ItemStack(product));
            } else {
                held.grow(1);
            }
            maker.flush(server, pos);
            maker.setChanged();
        } else {
            savePower(maker); // only its progress to keep: no comparator change to tell the neighbours of every tick
        }
    }

    /** Into the container below, if there is one - with no room at the last try, a hopper's beat later ({@link OutputRetry}). */
    private void flush(ServerLevel level, BlockPos pos) {
        ItemStack held = this.items.get(OUTPUT);
        if (held.isEmpty() || !this.outputRetry.due(level)) {
            return;
        }
        boolean moved = false;
        var handler = level.getCapability(Capabilities.Item.BLOCK, pos.below(), Direction.UP);
        if (handler != null) {
            ItemStack rest = ItemUtil.insertItemReturnRemaining(handler, held.copy(), false, null);
            if (rest.getCount() != held.getCount()) { // a full chest below: nothing moved, nothing to tell anyone
                this.items.set(OUTPUT, rest);
                this.setChanged();
                moved = true;
            }
        }
        this.outputRetry.tried(level, !moved);
    }

    @Override
    public MachineLayout layout() {
        return MachineLayout.STONE_MAKER;
    }

    @Override
    public int guiEnergyRate() {
        return MachineTuning.workEnergy(energyFor(this.product()));
    }

    @Override
    public int guiProgress() {
        return this.progress;
    }

    @Override
    public int guiProgressMax() {
        return MachineTuning.time(TICKS_PER_ITEM);
    }

    /** Its choice for the Settings tab's button: 1 for stone, 0 for cobblestone. */
    @Override
    public int guiExtra() {
        return this.makesStone ? 1 : 0;
    }

    /** Soul soil is the Extra Slots tab's: a shift-click opens it and puts it there (MenuTabs). */
    @Override
    public int shiftClickSlot(ItemStack stack) {
        return cools(stack) ? COOLER : stack.is(Items.LAVA_BUCKET) ? LAVA : -1;
    }

    @Override
    public Component status() {
        return Component.translatable("nookworks.machine.stone_maker.status", this.energyText(), this.items.get(OUTPUT).getCount());
    }

    @Override
    protected void saveAdditional(CompoundTag portTag, HolderLookup.Provider portLookup) {
        ValueOutput output = ValueIO.output(this, portTag, portLookup); // PORT: 1.21.1 hands a tag
        super.saveAdditional(portTag, portLookup);
        output.putInt("Progress", this.progress);
        output.putBoolean(MAKES_STONE_KEY, this.makesStone);
    }

    @Override
    protected void loadAdditional(CompoundTag portTag, HolderLookup.Provider portLookup) {
        ValueInput input = ValueIO.input(this, portTag, portLookup); // PORT: 1.21.1 hands a tag
        super.loadAdditional(portTag, portLookup);
        this.progress = input.getIntOr("Progress", 0);
        var stone = input.read(MAKES_STONE_KEY, Codec.BOOL);
        this.makesStone = stone.orElse(false);
        if (stone.isEmpty()) {
            // made before its slots, of the two buckets it now takes in them: they are given back to it, so it goes on
            if (this.items.get(COOLER).isEmpty()) {
                this.items.set(COOLER, new ItemStack(Items.WATER_BUCKET));
            }
            if (this.items.get(LAVA).isEmpty()) {
                this.items.set(LAVA, new ItemStack(Items.LAVA_BUCKET));
            }
        }
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return SLOTS;
    }

    /** A water bucket or blue ice on the left, a lava bucket on the right, one each: a hopper keeps one there, no more. */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return switch (slot) {
            case COOLER -> cools(stack) && this.items.get(COOLER).isEmpty();
            case LAVA -> stack.is(Items.LAVA_BUCKET) && this.items.get(LAVA).isEmpty();
            case SOUL_SOIL -> stack.is(Items.SOUL_SOIL) && this.items.get(SOUL_SOIL).isEmpty();
            default -> false;
        };
    }

    /** Only what it made leaves by a hopper or pipe; the buckets, the ice and the soul soil stay until taken by hand. */
    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == OUTPUT;
    }
}
