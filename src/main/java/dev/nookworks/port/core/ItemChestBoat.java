package dev.nookworks.port.core;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.ChestBoat;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** 26.3's {@code new ChestBoat(type, level, () -> item)}: as {@link ItemBoat}, with a chest. */
public class ItemChestBoat extends ChestBoat {
    private final Supplier<Item> dropItem;

    public ItemChestBoat(EntityType<? extends Boat> type, Level level, Supplier<Item> dropItem) {
        super(type, level);
        this.dropItem = dropItem;
    }

    @Override
    public Item getDropItem() {
        return this.dropItem.get();
    }

    @Override
    protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {
        if (onGround) {
            this.resetFallDistance(); // 26.3: landing breaks nothing
        }
        super.checkFallDamage(ya, onGround, onState, pos);
    }
}
