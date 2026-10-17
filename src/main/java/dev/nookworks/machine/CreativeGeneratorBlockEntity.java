package dev.nookworks.machine;

import java.util.function.Consumer;

import dev.nookworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Creative Generator: power without end, for creative mode. Each tick it offers as much as everything it reaches -
 * beside it, and along Energy Cables - will take, shared out as a Generator's power is ({@link MachineIO#pushEnergy});
 * another mod's cable may draw on it as much as it likes. It burns nothing and holds nothing: there is no fuel, slot or
 * screen, and its item carries no power. A redstone signal stops it, as it stops any machine by default (with no screen,
 * there is no other mode to set). Only a player in creative mode places one (see
 * {@link dev.nookworks.item.CreativeOnlyBlockItem}); in survival it cannot be broken, and it drops nothing.
 */
public class CreativeGeneratorBlockEntity extends BlockEntity implements Readout {
    /** What it offers a tick: as much as there is to give, every receiver taking only what it takes in a tick. */
    public static final int FE_PER_TICK = Integer.MAX_VALUE;

    private final EnergyHandler energy = new Endless();

    public CreativeGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CREATIVE_GENERATOR.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CreativeGeneratorBlockEntity gen) {
        if (level instanceof ServerLevel server && gen.running()) {
            MachineIO.pushEnergy(server, pos, gen.energy, FE_PER_TICK);
        }
    }

    /** Running unless a redstone signal stops it. */
    public boolean running() {
        return this.level != null && RedstoneMode.DEFAULT.allows(this.level.hasNeighborSignal(this.worldPosition));
    }

    /** Its power, for cables and other mods: drawn on without end while it runs, nothing taken in. */
    public EnergyHandler energy() {
        return this.energy;
    }

    @Override
    public void readout(Consumer<Component> lines) {
        lines.accept(this.status());
    }

    /** What it says to a click, and to Jade. */
    public Component status() {
        return Component.translatable(this.running() ? "nookworks.machine.creative_generator.running"
                : MachineStatus.REDSTONE.translationKey());
    }

    /**
     * NeoForge's endless store ({@code InfiniteEnergyHandler}) that goes dry while a signal stops the generator. It keeps
     * nothing, so a transaction has nothing to put back.
     */
    private final class Endless implements EnergyHandler {
        @Override
        public long getAmountAsLong() {
            return CreativeGeneratorBlockEntity.this.running() ? Long.MAX_VALUE : 0;
        }

        @Override
        public long getCapacityAsLong() {
            return Long.MAX_VALUE;
        }

        @Override
        public int insert(int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonNegative(amount);
            return 0;
        }

        @Override
        public int extract(int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonNegative(amount);
            return CreativeGeneratorBlockEntity.this.running() ? amount : 0;
        }
    }
}
