package dev.nookworks.wood;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Planks with their boards turned to run up and down ({@link TurnedPlanks}): the planks in every way but the look - a
 * player never holds one. Picked with the middle button it gives the planks; broken, it drops them (its loot table).
 */
public class TurnedPlanksBlock extends Block {
    private final Supplier<? extends Block> plain;

    public TurnedPlanksBlock(Supplier<? extends Block> plain, Properties properties) {
        super(properties);
        this.plain = plain;
    }

    /** The planks this is, turned. */
    public Block plain() {
        return plain.get();
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(plain.get());
    }

    /** Named as the plain one (26.3: the properties' overrideDescription, which 1.21.1 lacks). */
    @Override
    public String getDescriptionId() {
        return plain.get().getDescriptionId();
    }
}
