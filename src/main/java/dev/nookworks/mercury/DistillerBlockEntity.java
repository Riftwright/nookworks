package dev.nookworks.mercury;

import java.util.ArrayList;
import java.util.List;

import dev.nookworks.machine.MachineInfo;
import dev.nookworks.machine.MachineLayout;
import dev.nookworks.machine.MachineStatus;
import dev.nookworks.machine.MachineTuning;
import dev.nookworks.registry.ModBlockEntities;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * Vacuum Distiller: the base of a four-block tower (this block, two Distillation Column sections, a Condenser
 * on top). Under vacuum the mercury boils off the amalgam at a low temperature, rises through the column and is
 * condensed for reuse; the purified metal stays behind. Only runs while the tower is complete.
 */
public class DistillerBlockEntity extends MercuryMachineBlockEntity implements MachineInfo {
    public static final int FE_PER_TICK = 60;
    public static final int TICKS = 120;
    public static final int COLUMN_SECTIONS = 2;
    private static final int OUTPUTS = 4;

    public DistillerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DISTILLER.get(), pos, state, OUTPUTS, 20_000);
    }

    @Override
    public Role mercuryRole() {
        return Role.PRODUCER;
    }

    /** Whether the column sections and the condenser are stacked on top. */
    public static boolean isFormed(LevelReader level, BlockPos base) {
        for (int i = 1; i <= COLUMN_SECTIONS; i++) {
            if (!level.getBlockState(base.above(i)).is(ModBlocks.DISTILLATION_COLUMN.get())) {
                return false;
            }
        }
        return level.getBlockState(base.above(COLUMN_SECTIONS + 1)).is(ModBlocks.CONDENSER.get());
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DistillerBlockEntity distiller) {
        if (level instanceof ServerLevel server) {
            distiller.tick(server, pos);
        }
    }

    private void tick(ServerLevel level, BlockPos pos) {
        if (this.mercury.amount() > 0) {
            MercuryNetwork.push(level, pos, this, PIPE_RATE);
        }
        MercuryRecipes.Distillation recipe = MercuryRecipes.distillation(this.items.get(INPUT));
        boolean flushed = this.flush(level, pos);
        MachineStatus blocked = !isFormed(level, pos) ? MachineStatus.INCOMPLETE
                : !flushed ? MachineStatus.OUTPUT_BLOCKED
                : recipe == null ? MachineStatus.IDLE
                : this.mercury.space() < recipe.mercury() ? MachineStatus.MERCURY_FULL : null;
        if (blocked != null) {
            // Nothing to work on - even while it waits for room - and what it had done went with what was in it;
            // stopped for room or its tower with the work still in it, it keeps what it had done.
            if (blocked == MachineStatus.IDLE || recipe == null) {
                this.progress = 0;
            }
            this.status = blocked;
            return;
        }
        if (!this.redstoneAllows()) {
            this.status = MachineStatus.REDSTONE;
            return;
        }
        if (this.energyStored() < this.workCost(FE_PER_TICK)) {
            this.status = MachineStatus.NO_POWER;
            return;
        }
        this.status = MachineStatus.RUNNING;
        this.payWork(FE_PER_TICK);
        if (++this.progress >= MachineTuning.time(TICKS)) {
            this.progress = 0;
            this.items.get(INPUT).shrink(1);
            this.mercury.fill(recipe.mercury(), false);
            for (ItemStack product : products(level, pos, recipe)) {
                if (!this.addOutput(product)) {
                    Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, product);
                }
            }
            this.flush(level, pos);
        }
        this.setChanged();
    }

    static List<ItemStack> products(ServerLevel level, BlockPos pos, MercuryRecipes.Distillation recipe) {
        List<ItemStack> products = new ArrayList<>(2);
        if (recipe.result().isPresent()) {
            int count = recipe.count() + (level.getRandom().nextFloat() < recipe.bonusChance() ? recipe.bonus() : 0);
            // a stack at most, whatever a datapack asks: a bigger one is not saved, and is lost with the world closed
            products.add(new ItemStack(recipe.result().get(), Math.min(count, new ItemStack(recipe.result().get()).getMaxStackSize())));
        } else if (recipe.lootTable().isPresent()) {
            LootTable table = level.getServer().reloadableRegistries().getLootTable(recipe.lootTable().get());
            LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                    .create(LootContextParamSets.CHEST);
            products.addAll(table.getRandomItems(params));
        }
        // none while velarium is switched off (features.json)
        if (level.getRandom().nextFloat() < recipe.velariumChance() && dev.nookworks.feature.Features.enabled(ModItems.VELARIUM_NUGGET.get())) {
            products.add(new ItemStack(ModItems.VELARIUM_NUGGET.get()));
        }
        return products;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == INPUT && MercuryRecipes.distillation(stack) != null;
    }

    @Override
    public MachineLayout layout() {
        return MachineLayout.DISTILLER;
    }

    @Override
    public int guiEnergyRate() {
        return MachineTuning.workEnergy(FE_PER_TICK);
    }

    @Override
    public int guiProgressMax() {
        return MachineTuning.time(TICKS);
    }

    @Override
    public Component status() {
        if (this.level != null && !isFormed(this.level, this.worldPosition)) {
            return Component.translatable("nookworks.machine.distiller.incomplete");
        }
        return Component.translatable("nookworks.machine.mercury_status", this.energyText(), this.mercuryText(), MachineTuning.percent(this.progress, MachineTuning.time(TICKS)));
    }
}
