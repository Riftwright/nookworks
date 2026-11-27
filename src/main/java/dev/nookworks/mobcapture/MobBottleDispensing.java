package dev.nookworks.mobcapture;

import dev.nookworks.Nookworks;
import dev.nookworks.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.OptionalDispenseItemBehavior;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * A dispenser lets a filled Mob Bottle's mob out in front of it, as it lets out a bucket's fish or a spawn egg's mob, and
 * keeps the empty bottle (the user, 2026-11-24: 「どちらもそのようにして下さい」, after the audit found it threw the bottle out
 * whole). The same rules as {@link MobBottleItem#letOut}: what could not have gone in does not come out, nor a monster
 * on Peaceful - then it stays in the bottle, with the dispenser's failing click. An empty bottle is thrown out, as before.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class MobBottleDispensing {
    private MobBottleDispensing() {}

    @SubscribeEvent
    static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> DispenserBlock.registerBehavior(ModItems.MOB_BOTTLE.get(), new LetOut()));
    }

    static final class LetOut extends OptionalDispenseItemBehavior {
        @Override
        protected ItemStack execute(BlockSource source, ItemStack dispensed) {
            if (MobBottleItem.captured(dispensed) == null) {
                this.setSuccess(true);
                return super.execute(source, dispensed); // thrown out, as any item
            }
            BlockPos front = source.pos().relative(source.state().getValue(DispenserBlock.FACING));
            // standing on what floor the block in front has (a carpet, a slab), and only with room there: a mob let out
            // into a wall or the ground suffocated in it (r23 self-review) - then it stays in the bottle
            var level = source.level();
            var shape = level.getBlockState(front).getCollisionShape(level, front);
            double floor = shape.isEmpty() || shape.max(net.minecraft.core.Direction.Axis.Y) > 0.5 ? 0.0
                    : shape.max(net.minecraft.core.Direction.Axis.Y);
            var at = new net.minecraft.world.phys.Vec3(front.getX() + 0.5, front.getY() + floor, front.getZ() + 0.5);
            if (MobBottleItem.letOut(level, dispensed, at, false, true) == null) {
                this.setSuccess(false);
                return dispensed;
            }
            this.setSuccess(true);
            source.level().playSound(null, front, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0F, 0.8F);
            source.level().gameEvent(null, GameEvent.ENTITY_PLACE, front);
            return this.consumeWithRemainder(source, dispensed, MobBottleItem.emptied(dispensed));
        }
    }
}
