package dev.nookworks.barter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import dev.nookworks.feature.Permission;
import dev.nookworks.machine.GuiTank;
import dev.nookworks.machine.MachineBlockEntity;
import dev.nookworks.machine.MachineIO;
import dev.nookworks.machine.MachineInfo;
import dev.nookworks.machine.MachineLayout;
import dev.nookworks.machine.MachineStatus;
import dev.nookworks.machine.MachineTuning;
import dev.nookworks.machine.OutputRetry;
import dev.nookworks.mobcapture.MobBottleItem;
import dev.nookworks.registry.ModBarter;
import dev.nookworks.registry.ModItems;
import dev.nookworks.ventilation.VentFanBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Barter Terminal: piglins kept out of the Nether, bartering. It makes the Nether's air on power and sends it along its
 * Ducts to a Ventilation Fan set in the wall of a room of the Nether's own ground ({@link BarterRoom}); the piglins in
 * the room live there as at home, full size, and do not turn into zombified piglins (see {@link PiglinShelter}).
 * <ul>
 * <li>A Mob Bottle with a piglin in it, put in its bottle slot, lets the piglin out into the room, and the bottle comes
 * back empty; an empty one takes a piglin out. A room takes {@link BarterRoom#PIGLINS_PER_BLOCK} piglins a floor
 * block.</li>
 * <li>Gold put in is handed to the piglins with a hand free, one ingot each, as a player hands it over; each admires it
 * as it would, and throws what it gives for it, which the terminal draws in through the floor's vents into its outputs
 * and passes into the container below.</li>
 * <li>The air lasts {@link #AIR_TICKS} after the power fails or the room is broken open: time to see to it before the
 * piglins start to turn.</li>
 * </ul>
 * Gold and bottles in from the top and sides, by hopper; everything out of the bottom.
 */
public class BarterTerminalBlockEntity extends MachineBlockEntity implements MachineInfo {
    public static final int FE_PER_TICK = 20;
    public static final int CAPACITY = 20_000;
    /** How long the air in a room lasts with nothing making more: five minutes. */
    public static final int AIR_TICKS = 6_000;
    /** How fast the air is made: a room filled in fifteen seconds. */
    private static final int AIR_MADE = AIR_TICKS / 300;
    /** How long a piglin looks at its gold, as vanilla has it (PiglinAi.admireGoldItem). */
    public static final int ADMIRE_TICKS = 119;
    /** The Nether's air leaking out of a room: crimson dust. */
    private static final DustParticleOptions LEAK = new DustParticleOptions(0xB8322C, 1.4F);

    public static final int GOLD = 0;
    public static final int BOTTLE = 1;
    public static final int FIRST_OUT = 2;
    public static final int OUTPUTS = 9;
    private static final int[] IN_SLOTS = {GOLD, BOTTLE};
    private static final int[] OUT_SLOTS = {2, 3, 4, 5, 6, 7, 8, 9, 10};

    /** How long a fan goes on turning after the air was last made (power coming in fits keeps it turning). */
    private static final int FAN_HOLD = 40;
    /** How far round the terminal the room it looks for can reach: ducts, the room, its walls. */
    private static final int REACH = BarterRoom.DUCT_LIMIT + BarterRoom.MAX_SIDE + 2;

    /** The air left in the room, in ticks. Saved: a world reloaded does not refill it. */
    private int air;
    /**
     * The room as last found (looked for again every second). Its bounds are saved: a room broken open keeps its
     * piglins while its air lasts, across a reload too.
     */
    private BarterRoom.Found room = BarterRoom.Found.fault(BarterRoom.Fault.NO_FAN, null, null);
    /** When the power last fell short: the air is made again only after a whole second paid for. */
    private long lastShort = Long.MIN_VALUE / 2;
    /** When the air was last made: the fan turns on a little after. */
    private long lastMade = Long.MIN_VALUE / 2;
    /**
     * Whether the owner (who set it down) may take from and put into the room, as last asked: when the room is found
     * anew, and every five minutes. Asking a claim mod posts a break and a place event, which a logging mod writes down:
     * not every second.
     */
    private boolean allowed;
    private @Nullable BoundingBox askedOf;
    private long askedAt;
    /** Set as its chunk unloads, so its removal then does not stop the fan (the block is still there). */
    private boolean unloading;
    /** The fan this terminal last set turning, to stop it again when it no longer blows. */
    private @Nullable BlockPos drivenFan;
    /** Shown on its screen: piglins in the room, and how many it takes. */
    private int piglins;
    private int admiring;
    /** Whether its own work moved anything in its slots this tick: a comparator reading it is told at the tick's end. */
    private boolean moved;
    /** When the outputs are next passed on below: a hopper's beat after a try that found no room. */
    private final OutputRetry outputRetry = new OutputRetry();

    public BarterTerminalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBarter.BARTER_TERMINAL_ENTITY.get(), pos, state, FIRST_OUT + OUTPUTS, CAPACITY, 200, 0);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BarterTerminalBlockEntity terminal) {
        if (level instanceof ServerLevel server) {
            terminal.tick(server, pos);
        }
    }

    public BarterRoom.Found room() {
        return this.room;
    }

    public int air() {
        return this.air;
    }

    /** Whether the room keeps its piglins now: whole, or not long broken, with air in it. */
    public boolean sheltering() {
        return this.air > 0 && this.room.inside() != null;
    }

    /**
     * Whether {@code piglin} is kept here: in the room while there is air in it. Touching the room is enough - one on
     * soul sand stands a little into the floor.
     */
    public boolean keeps(AbstractPiglin piglin) {
        return this.sheltering() && this.room.box().intersects(piglin.getBoundingBox());
    }

    private void tick(ServerLevel level, BlockPos pos) {
        long time = level.getGameTime();
        int air = this.air;
        MachineStatus was = this.status;
        if (time % 20 == Math.floorMod(pos.hashCode(), 20)
                // looked for only with all of it loaded: a duct's or a wall's chunk not loaded is not a fault, and is not
                // to be loaded for it
                && level.hasChunksAt(pos.getX() - REACH, pos.getZ() - REACH, pos.getX() + REACH, pos.getZ() + REACH)) {
            BarterRoom.Found found = BarterRoom.find(level, pos);
            // A room broken open keeps its old bounds while its air lasts, so its piglins are still known.
            this.room = found.whole() || this.room.inside() == null || this.air <= 0 ? found
                    : new BarterRoom.Found(found.fault(), found.at(), found.fan(), this.room.inside());
            if (!found.whole() && found.at() != null && (this.air > 0 || this.energyStored() > 0)) {
                leak(level, found.at());
            }
            BoundingBox inside = this.room.inside();
            if (inside != null && (!inside.equals(this.askedOf) || time - this.askedAt >= AIR_TICKS)) {
                this.allowed = this.mayWorkIn(level);
                this.askedOf = inside;
                this.askedAt = time;
            }
        }
        boolean whole = this.room.whole();
        // The room as saved, not looked at again since a load (its chunks not all loaded yet): no air made for it until it
        // is seen whole, but what it had is held on power, not run down - a room whose far side stayed unloaded after a
        // restart ran out of air while the terminal could not look, and lost its piglins. Off power it runs down as ever.
        boolean waiting = this.room.fault() == BarterRoom.Fault.UNSEEN && this.room.inside() != null;
        boolean powered = this.energyStored() >= MachineTuning.energy(FE_PER_TICK);
        if (!powered) {
            this.lastShort = time;
        }
        // Made only on power paid every tick for a second first - and that second is paid for too: a trickle of power
        // saved up keeps nothing up. (Drawn only once the second was up, a trickle of 1 FE/t saved twenty, passed the
        // second on its savings and bought a second's air with them, over and over.)
        boolean paying = (whole || waiting) && powered;
        boolean making = whole && paying && time - this.lastShort > 20;
        if (paying) {
            this.addEnergy(-MachineTuning.energy(FE_PER_TICK));
        }
        if (making) {
            this.air = Math.min(AIR_TICKS, this.air + AIR_MADE);
            this.lastMade = time;
        } else if (this.air > 0 && !(waiting && paying)) {
            this.air--;
            if (this.air == 0 && !whole) {
                this.room = BarterRoom.Found.fault(this.room.fault(), this.room.at(), this.room.fan()); // the old room forgotten
            }
        }
        this.driveFan(level, whole && time - this.lastMade < FAN_HOLD ? this.room.fan() : null);

        List<AbstractPiglin> inside = this.room.inside() == null ? new ArrayList<>()
                : new ArrayList<>(level.getEntitiesOfClass(AbstractPiglin.class, this.room.box(), AbstractPiglin::isAlive));
        this.piglins = inside.size();
        boolean counted = this.room.inside() != null && this.entitiesLoaded(level);
        if (time % 20 == 3) {
            for (AbstractPiglin piglin : inside) {
                if (this.air > 0) {
                    PiglinShelter.keep(piglin, pos);
                } else {
                    PiglinShelter.letGo(piglin, pos); // only those it kept: another terminal on the room may keep them
                }
            }
        }
        if (time % 4 == 0 && this.sheltering() && this.allowed) {
            this.gather(level);
        }
        boolean outFree = this.flush(level, pos);
        // a piglin can be taken out while the air lasts, the room broken or not: the way to save one from a broken room
        if (this.sheltering() && this.allowed && time % 10 == 0) {
            this.useBottle(level, inside, whole && counted);
        }

        this.admiring = 0;
        for (AbstractPiglin piglin : inside) {
            if (piglin.getBrain().hasMemoryValue(MemoryModuleType.ADMIRING_ITEM)) {
                this.admiring++;
            }
        }
        MachineStatus status = switch (this.room.fault()) {
            case NO_FAN -> MachineStatus.NO_FAN;
            case OPEN -> MachineStatus.ROOM_OPEN;
            case SIZE -> MachineStatus.ROOM_SIZE;
            case FLOOR -> MachineStatus.NETHER_FLOOR;
            case UNSEEN -> MachineStatus.UNLOADED;
            case NONE -> null;
        };
        if (status == null) {
            status = !powered ? MachineStatus.NO_POWER
                    : !this.allowed ? MachineStatus.ROOM_PROTECTED // nothing handed, nothing taken, on land not theirs
                    : !outFree && !this.items.get(BOTTLE).isEmpty() ? MachineStatus.OUTPUT_BLOCKED // a bottle waits for room
                    : this.piglins > this.room.capacity() ? MachineStatus.CROWDED
                    : inside.stream().noneMatch(BarterTerminalBlockEntity::barters) ? MachineStatus.NO_PIGLINS
                    : !outFree ? MachineStatus.OUTPUT_BLOCKED
                    : !this.redstoneAllows() ? MachineStatus.REDSTONE
                    : this.items.get(GOLD).isEmpty() && this.admiring == 0 ? MachineStatus.IDLE
                    : MachineStatus.RUNNING;
            if (status == MachineStatus.RUNNING && time % 5 == 0) {
                this.handGold(inside);
            }
        }
        this.status = status;
        // a whole update (neighbours, comparators) only when something shows or its slots changed; the air ticking down
        // is only saved
        if (status != was || this.moved) {
            this.moved = false;
            this.setChanged();
        } else if (this.air != air) {
            savePower(this);
        }
    }

    /**
     * Whether the piglins of the room are all loaded, to be counted: entities come in after their chunk, and a room
     * counted empty while they do would take another piglin over what it holds.
     */
    private boolean entitiesLoaded(ServerLevel level) {
        BoundingBox box = this.room.inside();
        for (int x = box.minX() >> 4; x <= box.maxX() >> 4; x++) {
            for (int z = box.minZ() >> 4; z <= box.maxZ() >> 4; z++) {
                if (!level.areEntitiesLoaded(ChunkPos.pack(x, z))) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Whether claims let the terminal's owner take from and put into its room (asked at the room's floor). */
    private boolean mayWorkIn(ServerLevel level) {
        BoundingBox box = this.room.inside();
        BlockPos floor = new BlockPos(box.getCenter().getX(), box.minY(), box.getCenter().getZ());
        Player actor = Permission.actor(level, this.owner);
        return Permission.mayBreak(level, floor, actor) && Permission.mayPlace(level, floor, Direction.UP, actor);
    }

    /** A piglin that barters: a grown one, not a brute. */
    private static boolean barters(AbstractPiglin piglin) {
        return piglin instanceof Piglin && piglin.isAdult();
    }

    /**
     * Gold to each piglin with a hand free, as a player hands it over (PiglinAi.mobInteract) - no more at once than there
     * are output slots free for what they give back.
     */
    private void handGold(List<AbstractPiglin> inside) {
        int free = 0;
        for (int slot : OUT_SLOTS) {
            if (this.items.get(slot).isEmpty()) {
                free++;
            }
        }
        for (AbstractPiglin piglin : inside) {
            ItemStack gold = this.items.get(GOLD);
            if (gold.isEmpty() || this.admiring >= free) {
                return;
            }
            if (piglin.isRemoved() || !barters(piglin) || !piglin.getOffhandItem().isEmpty()
                    || piglin.getBrain().hasMemoryValue(MemoryModuleType.ADMIRING_ITEM)
                    || piglin.getBrain().hasMemoryValue(MemoryModuleType.ADMIRING_DISABLED)) {
                continue;
            }
            piglin.setItemSlot(EquipmentSlot.OFFHAND, gold.split(1));
            this.moved = true;
            piglin.setGuaranteedDrop(EquipmentSlot.OFFHAND);
            piglin.getBrain().setMemoryWithExpiry(MemoryModuleType.ADMIRING_ITEM, true, ADMIRE_TICKS);
            piglin.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            piglin.getNavigation().stop();
            this.admiring++;
        }
    }

    /** What lies on the room's floor - what the piglins threw - drawn in through its vents. */
    private void gather(ServerLevel level) {
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, this.room.box(), ItemEntity::isAlive)) {
            ItemStack rest = this.addOutput(item.getItem().copy());
            if (rest.isEmpty()) {
                item.discard();
                this.moved = true;
            } else if (rest.getCount() != item.getItem().getCount()) {
                item.setItem(rest);
                this.moved = true;
            }
        }
    }

    /** Into the output slots; what does not fit comes back. */
    private ItemStack addOutput(ItemStack stack) {
        for (int slot : OUT_SLOTS) {
            ItemStack there = this.items.get(slot);
            if (there.isEmpty()) {
                this.items.set(slot, stack);
                return ItemStack.EMPTY;
            }
            if (ItemStack.isSameItemSameComponents(there, stack) && there.getCount() < there.getMaxStackSize()) {
                int move = Math.min(stack.getCount(), there.getMaxStackSize() - there.getCount());
                there.grow(move);
                stack.shrink(move);
                if (stack.isEmpty()) {
                    return ItemStack.EMPTY;
                }
            }
        }
        return stack;
    }

    private boolean roomInOutputs() {
        for (int slot : OUT_SLOTS) {
            if (this.items.get(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * The outputs into the container below - what piglins throw back dropped out with none, as a machine's output is,
     * but a bottled piglin never: it waits in its slot for the container or for a hand to take it, since dropped it
     * would be lost with the item in five minutes. With no room below at the last try, a hopper's beat later
     * ({@link OutputRetry}). Whether there is a free output slot after.
     */
    private boolean flush(ServerLevel level, BlockPos pos) {
        if (this.outputRetry.due(level)) {
            boolean waiting = false;
            boolean moved = false;
            for (int slot : OUT_SLOTS) {
                ItemStack stack = this.items.get(slot);
                if (!stack.isEmpty()) {
                    waiting = true;
                    ItemStack rest = MobBottleItem.captured(stack) != null ? MachineIO.intoBelow(level, pos, stack)
                            : MachineIO.deliverBelow(level, pos, stack);
                    if (rest.getCount() != stack.getCount()) {
                        this.items.set(slot, rest);
                        moved = true;
                        this.moved = true; // a comparator reading it is told
                    }
                }
            }
            this.outputRetry.tried(level, waiting && !moved);
        }
        return this.roomInOutputs();
    }

    /**
     * A piglin's bottle lets it out into the room - a whole one, with air already in it, so it is kept from the first
     * moment; an empty one takes one out, the room broken or not.
     */
    private void useBottle(ServerLevel level, List<AbstractPiglin> inside, boolean whole) {
        ItemStack bottle = this.items.get(BOTTLE);
        if (bottle.isEmpty() || !this.roomInOutputs() || this.room.inside() == null) {
            return;
        }
        EntityType<?> type = MobBottleItem.captured(bottle);
        if (type != null) {
            if (!whole || this.air <= 0 || this.piglins >= this.room.capacity()) {
                return;
            }
            BlockPos at = this.freestFloor(inside);
            var released = MobBottleItem.letOut(level, bottle, at);
            if (released != null) {
                if (released instanceof AbstractPiglin piglin && this.air > 0) {
                    PiglinShelter.keep(piglin, this.worldPosition); // not left to start turning before the next look
                }
                level.playSound(null, at, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0F, 0.8F);
                this.addOutput(MobBottleItem.emptied(bottle));
                this.items.set(BOTTLE, ItemStack.EMPTY);
                this.moved = true;
            }
            return;
        }
        // Only a kind a bottle lets out again: a pack may keep piglins out of bottles (a tag added after rooms were
        // filled), and one bottled then could never leave the bottle - lost, with all it carried.
        List<AbstractPiglin> takeable = inside.stream().filter(p -> MobBottleItem.bottleable(p.getType())).toList();
        if (!takeable.isEmpty()) {
            // the one with nothing in hand first: a piglin looking at its gold finishes with it
            AbstractPiglin taken = takeable.stream().filter(p -> p.getOffhandItem().isEmpty()).findFirst().orElse(takeable.get(0));
            PiglinShelter.letGo(taken);
            taken.setTimeInOverworld(0);
            ItemStack filled = MobBottleItem.filled(taken);
            level.sendParticles(ParticleTypes.POOF, taken.getX(), taken.getY() + 1, taken.getZ(), 12, 0.3, 0.3, 0.3, 0.02);
            level.playSound(null, taken.blockPosition(), SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 0.8F);
            taken.discard();
            inside.remove(taken); // handed no gold this tick
            bottle.shrink(1);
            this.addOutput(filled);
            this.moved = true;
        }
    }

    /** The middle of the floor block with the fewest piglins on it, where the next one is let out. */
    private BlockPos freestFloor(List<AbstractPiglin> inside) {
        BoundingBox box = this.room.inside();
        BlockPos best = new BlockPos(box.minX(), box.minY(), box.minZ());
        int fewest = Integer.MAX_VALUE;
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                int n = 0;
                for (AbstractPiglin piglin : inside) {
                    if (piglin.getBlockX() == x && piglin.getBlockZ() == z) {
                        n++;
                    }
                }
                if (n < fewest) {
                    fewest = n;
                    best = new BlockPos(x, box.minY(), z);
                }
            }
        }
        return best;
    }

    /**
     * The Nether's air seen leaking out of a gap in the room, or from where its fault is: a puff of crimson dust, big
     * enough to be found from across the room, and a few of a crimson forest's motes with it.
     */
    private static void leak(ServerLevel level, BlockPos at) {
        level.sendParticles(LEAK, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 6, 0.25, 0.25, 0.25, 0.0);
        level.sendParticles(ParticleTypes.CRIMSON_SPORE, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.0);
    }

    /** Sets {@code fan} turning (null: none), and the one it turned before still if it is another. */
    private void driveFan(ServerLevel level, @Nullable BlockPos fan) {
        if (this.drivenFan != null && !this.drivenFan.equals(fan) && level.isLoaded(this.drivenFan)) {
            VentFanBlock.drive(level, this.drivenFan, false);
        }
        if (fan != null && level.isLoaded(fan)) {
            VentFanBlock.drive(level, fan, true);
        }
        this.drivenFan = fan;
    }

    @Override
    public void onChunkUnloaded() {
        this.unloading = true;
        super.onChunkUnloaded();
    }

    /**
     * Taken away: its fan stops, and its piglins are let go at their next look (their terminal is gone). Not as its chunk
     * unloads, when the block stays - told by {@link #onChunkUnloaded}, as the chunk is not to be read then.
     */
    @Override
    public void setRemoved() {
        if (!this.unloading && this.level instanceof ServerLevel server && this.drivenFan != null && server.isLoaded(this.drivenFan)) {
            VentFanBlock.drive(server, this.drivenFan, false);
        }
        super.setRemoved();
    }

    // ---- what its screen shows ----

    @Override
    public MachineLayout layout() {
        return MachineLayout.BARTER_TERMINAL;
    }

    @Override
    public int guiEnergyRate() {
        return MachineTuning.energy(FE_PER_TICK);
    }

    /** Its arrow: how many of the piglins able to are bartering now. */
    @Override
    public int guiProgress() {
        return this.admiring;
    }

    /** Of the grown piglins in the room (who barter), not of all the room takes. */
    @Override
    public int guiProgressMax() {
        if (this.level == null || this.room.inside() == null) {
            return 1;
        }
        return Math.max(1, (int) this.level.getEntitiesOfClass(AbstractPiglin.class, this.room.box(), AbstractPiglin::isAlive).stream()
                .filter(BarterTerminalBlockEntity::barters).count());
    }

    /** The piglins in the room, and how many it takes, for its status line. */
    @Override
    public int guiExtra() {
        return this.piglins | Math.min(0xFFFF, this.room.capacity()) << 16;
    }

    @Override
    public List<GuiTank> guiTanks() {
        return List.of(new GuiTank(GuiTank.Kind.NETHER_AIR, this.air, AIR_TICKS));
    }

    @Override
    public Component status() {
        int seconds = this.air / 20;
        return Component.translatable("nookworks.machine.barter_terminal", this.energyText(), this.piglins, this.room.capacity(),
                String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60));
    }

    // ---- saving ----

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Air", this.air);
        if (this.drivenFan != null) {
            output.putLong("Fan", this.drivenFan.asLong());
        }
        BoundingBox inside = this.room.inside();
        if (inside != null) {
            output.store("Room", BoundingBox.CODEC, inside);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.air = Math.max(0, Math.min(AIR_TICKS, input.getIntOr("Air", 0)));
        // only near it: saved data from elsewhere (a creative player's made-up item) naming a far-off fan or a vast room
        // is not trusted - a fan driven, piglins sheltered, across the world
        this.drivenFan = input.getLong("Fan").map(BlockPos::of).filter(this::nearby).orElse(null);
        // the room as it was saved, until it is looked for again: its piglins kept from the first tick after a reload,
        // but nothing let into it, no air made for it, until it is seen whole (it may have been broken meanwhile)
        this.room = input.read("Room", BoundingBox.CODEC)
                .filter(inside -> inside.getXSpan() <= BarterRoom.MAX_SIDE && inside.getZSpan() <= BarterRoom.MAX_SIDE
                        && inside.getYSpan() <= BarterRoom.MAX_HEIGHT && this.nearby(inside.getCenter()))
                .map(inside -> new BarterRoom.Found(BarterRoom.Fault.UNSEEN, null, this.drivenFan, inside))
                .orElse(BarterRoom.Found.fault(BarterRoom.Fault.NO_FAN, null, null));
    }

    /** Within reach of a room the terminal could have found: its ducts' length, and a room's width, and some. */
    private boolean nearby(BlockPos pos) {
        return pos.closerThan(this.worldPosition, BarterRoom.DUCT_LIMIT + 2 * BarterRoom.MAX_SIDE + 4);
    }

    // ---- hoppers ----

    @Override
    public int[] getSlotsForFace(Direction side) {
        return side == Direction.DOWN ? OUT_SLOTS : IN_SLOTS;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == GOLD ? stack.isPiglinCurrency() : slot == BOTTLE && takesBottle(stack);
    }

    /**
     * A Mob Bottle, empty or with a piglin (or a brute) in it - of a kind a bottle lets out: one a pack's tag keeps out
     * of bottles could only be tried here again and again.
     */
    public static boolean takesBottle(ItemStack stack) {
        if (!stack.is(ModItems.MOB_BOTTLE.get())) {
            return false;
        }
        EntityType<?> type = MobBottleItem.captured(stack);
        return type == null || (type == EntityTypes.PIGLIN || type == EntityTypes.PIGLIN_BRUTE) && MobBottleItem.bottleable(type);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot >= FIRST_OUT && side == Direction.DOWN;
    }
}
