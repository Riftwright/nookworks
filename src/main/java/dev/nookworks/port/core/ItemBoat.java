package dev.nookworks.port.core;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 26.3's {@code new Boat(type, level, () -> item)}: a boat of a wood of one's own, with an entity type of its own,
 * that drops (and is picked as, and is named after) the item given. 1.21.1's boat takes its drop from its vanilla
 * {@code Boat.Type}, which would make ours an oak boat; the look is the renderer's ({@code port/render/BoatRenderer}).
 * <p>
 * 26.3's boats no longer break, nor hurt their riders, when they land from a height; 1.21.1's do (into the type's
 * planks and sticks, oak for ours). As in 26.3, a fall is forgotten here before 1.21.1's landing code reads it.
 */
public class ItemBoat extends Boat {
    private final Supplier<Item> dropItem;

    public ItemBoat(EntityType<? extends Boat> type, Level level, Supplier<Item> dropItem) {
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
