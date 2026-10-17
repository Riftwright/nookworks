package dev.nookworks.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.nookworks.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A spawner picked up with a Silk Touch hammer carries only its mob type ({@code nookworks:spawner_entity}),
 * never raw block entity data, so placing it cannot smuggle in arbitrary spawner NBT the way an op-only
 * {@code block_entity_data} item could. And which Spawner Controller holds a spawner off - saved with the spawner (mixin
 * BaseSpawnerMixin) - stays in the save: it is not sent to the clients near it with the rest of the spawner, which
 * would tell a modified client where a far-off controller, and so someone's base, is.
 */
@Mixin(SpawnerBlockEntity.class)
abstract class SpawnerBlockEntityMixin extends BlockEntity {
    @Shadow
    public abstract BaseSpawner getSpawner();

    private SpawnerBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** Sent without the controller's place; only that it is held off, so the clients show it standing still. */
    @Inject(method = "getUpdateTag", at = @At("RETURN"))
    private void nookworks$keepTheHoldOut(HolderLookup.Provider registries, CallbackInfoReturnable<CompoundTag> cir) {
        CompoundTag tag = cir.getReturnValue();
        if (tag.contains("NookworksHeldOffBy")) {
            tag.remove("NookworksHeldOffBy");
            tag.putBoolean("NookworksOff", true);
        }
    }

    /**
     * Nor copied by a creative player's pick-block with its data (any creative player may, op or not): an item that
     * carried the hold would say where the controller is.
     */
    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        super.removeComponentsFromTag(output);
        output.discard("NookworksHeldOffBy");
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        EntityType<?> type = components.get(ModDataComponents.SPAWNER_ENTITY.get());
        if (type != null) {
            RandomSource random = this.level != null ? this.level.getRandom() : RandomSource.create();
            this.getSpawner().setEntityId(type, this.level, random, this.worldPosition);
        }
    }
}
