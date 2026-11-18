package dev.nookworks.port.core;

import dev.nookworks.Nookworks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.ShearsDispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.IShearable;

/**
 * A dispenser firing shears at any shearable entity, as 26.3's does: 1.21.1's shears only living ones, so a tied load
 * (an entity of ours, not alive) would not be untied by one. Vanilla's behaviour is kept and asked first; when it
 * shears nothing, an {@link IShearable} entity in front that is not alive is sheared as 26.3 shears it, and the shears
 * are worn as vanilla wears them. Left alone if another mod has given shears a behaviour of its own.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class ShearsDispensing {
    private ShearsDispensing() {}

    @SubscribeEvent
    static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            if (DispenserBlock.DISPENSER_REGISTRY.get(Items.SHEARS) != null
                    && DispenserBlock.DISPENSER_REGISTRY.get(Items.SHEARS).getClass() == ShearsDispenseItemBehavior.class) {
                DispenserBlock.registerBehavior(Items.SHEARS, new AnyEntity());
            }
        });
    }

    private static final class AnyEntity extends ShearsDispenseItemBehavior {
        @Override
        protected ItemStack execute(BlockSource source, ItemStack stack) {
            ItemStack out = super.execute(source, stack);
            ServerLevel level = source.level();
            if (this.isSuccess()) {
                return out;
            }
            BlockPos pos = source.pos().relative(source.state().getValue(DispenserBlock.FACING));
            for (Entity entity : level.getEntitiesOfClass(Entity.class, new AABB(pos), EntitySelector.NO_SPECTATORS)) {
                if (!(entity instanceof LivingEntity) && entity instanceof IShearable shearable && shearable.isShearable(null, stack, level, pos)) {
                    shearable.onSheared(null, stack, level, pos).forEach(drop -> shearable.spawnShearedDrop(level, pos, drop));
                    level.gameEvent(null, GameEvent.SHEAR, pos);
                    this.setSuccess(true);
                    stack.hurtAndBreak(1, level, null, item -> {});
                    break;
                }
            }
            return out;
        }
    }
}
