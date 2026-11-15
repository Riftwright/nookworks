package dev.nookworks.cooking;

import dev.nookworks.machine.MachineAlerts;
import dev.nookworks.machine.MachineStatus;
import dev.nookworks.machine.MachineTuning;
import dev.nookworks.registry.ModBlockEntities;
import dev.nookworks.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;

/**
 * The Electric Stove's power, and when its ring is hot ({@link ElectricStoveBlock}). It heats only while the pot or pan
 * on it has something to cook, and only that costs power ({@link #FE_PER_TICK}): a pot left on it with a dish waiting,
 * or with nothing in it, costs nothing. Out of power the ring goes dark and the cooking stops where it is, to go on
 * when the power is back - not until there is a second's worth, so a trickle does not flicker the ring.
 */
public class ElectricStoveBlockEntity extends BlockEntity implements MachineAlerts.Alerting {
    public static final int CAPACITY = 8_000;
    /**
     * A quarter of an Electric Furnace's: it heats one pot. (It was 20; the user, 2026-11-14: 消費電力はもう少し少なくても
     * いいと思います.)
     */
    public static final int FE_PER_TICK = 10;
    /** After running dry, the ring stays off until there is this many ticks' power. */
    private static final int RELIGHT_TICKS = 20;

    private final SimpleEnergyHandler energy = new SimpleEnergyHandler(CAPACITY, 500, 0) {
        @Override
        protected void onEnergyChanged(int previousAmount) {
            ElectricStoveBlockEntity.this.setChanged();
        }
    };
    private boolean ranDry;
    /** What it is doing, in a machine's words, for the stop that is heard and the Machine Bell (not saved: the next tick says). */
    private MachineStatus status = MachineStatus.IDLE;

    public ElectricStoveBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ELECTRIC_STOVE.get(), pos, state);
    }

    public SimpleEnergyHandler energy() {
        return this.energy;
    }

    /** Its power goes with its item when it is broken, as every powered block's does (DECISIONS; missed: 1.7 review). */
    @Override
    protected void collectImplicitComponents(net.minecraft.core.component.DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        dev.nookworks.machine.MachineBlockEntity.keepEnergy(this.energy, components);
    }

    @Override
    protected void applyImplicitComponents(net.minecraft.core.component.DataComponentGetter components) {
        super.applyImplicitComponents(components);
        dev.nookworks.machine.MachineBlockEntity.restoreEnergy(this.energy, components);
    }

    /** Heating, out of power under a pot that wants heat, or with nothing to do. */
    @Override
    public MachineStatus alertStatus() {
        return this.status;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        MachineAlerts.track(this);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ElectricStoveBlockEntity stove) {
        if (level instanceof ServerLevel) {
            stove.tick(level, pos, state);
        }
    }

    private void tick(Level level, BlockPos pos, BlockState state) {
        int cost = MachineTuning.energy(FE_PER_TICK);
        int held = (int) this.energy.getAmountAsLong();
        boolean hot = false;
        boolean wanted = this.wanted(level, pos);
        if (wanted) {
            if (this.ranDry && held >= cost * RELIGHT_TICKS) {
                this.ranDry = false;
            }
            if (!this.ranDry && held >= cost) {
                this.energy.set(held - cost);
                hot = true;
            } else {
                this.ranDry = true;
            }
        }
        this.status = hot ? MachineStatus.RUNNING : wanted ? MachineStatus.NO_POWER : MachineStatus.IDLE;
        if (state.getValue(ElectricStoveBlock.LIT) != hot) {
            level.setBlock(pos, state.setValue(ElectricStoveBlock.LIT, hot), Block.UPDATE_ALL);
            // all a stove with no fire in it says: its switch, as the ring comes on and as it goes off (a real one hums
            // no more than that - the pot on it is what is heard)
            level.playSound(null, pos, ModSounds.SWITCH_CLICK.get(), SoundSource.BLOCKS, 0.35F, hot ? 1.0F : 0.9F);
        }
    }

    /** Whether a pot or pan stands on it (or on a hopper on it: the heat goes up through one) with something to cook. */
    private boolean wanted(Level level, BlockPos pos) {
        BlockPos above = level.getBlockState(pos.above()).is(IronPotBlock.CONDUCTS_HEAT) ? pos.above(2) : pos.above();
        return level.getBlockEntity(above) instanceof IronPotBlockEntity pot && pot.wantsHeat();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        this.energy.serialize(output.child("energy"));
        output.putBoolean("ran_dry", this.ranDry);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child("energy").ifPresent(this.energy::deserialize);
        if (this.energy.getAmountAsLong() > CAPACITY) {
            this.energy.set(CAPACITY);
        }
        this.ranDry = input.getBooleanOr("ran_dry", false);
    }
}
