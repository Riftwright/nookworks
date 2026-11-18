package dev.nookworks.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FireBlock;

/** Lets our wooden blocks burn the way vanilla's do, through the same table vanilla fills in {@code bootStrap}. */
@Mixin(FireBlock.class)
public interface FireBlockAccessor {
    @Invoker("setFlammable")
    void nookworks$setFlammable(Block block, int igniteOdds, int burnOdds);
}
