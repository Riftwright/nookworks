package dev.nookworks.fluid;

import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import dev.nookworks.experience.Move;
import dev.nookworks.feature.DrawRights;
import dev.nookworks.mercury.MercuryBucketItem;
import dev.nookworks.mercury.MercuryHandler;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModOil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Buckets and bottles in a dispenser (the user, 2026-11-10: 「ディスペンサーに液体入りバケツ入れたら、レッドストーンで水みたい
 * に出したり戻したりできたら楽しいな」; and 「ディスペンサーに空の瓶とか入れてタンクに向けて置いておく→RS信号入れたら、瓶詰に
 * してディスペンサーのインベントリに戻す」):
 * <ul>
 * <li>a bucket of any of our liquids pours it out in front, as a water bucket does - a pool of oil, of mercury, of liquid
 * experience - and an empty bucket takes a pool of ours back up, as vanilla's takes water (vanilla's own pickup);</li>
 * <li>facing anything that holds liquid - a Copper Tank, a cauldron, a machine, another mod's tank - a filled bucket or
 * bottle pours into it, the empty one kept in the dispenser, and an empty bucket or glass bottle is filled from it, the
 * filled one put back into the dispenser (dropped in front when it is full). A bucket takes a bucket, a bottle a
 * quarter ({@link Bottles}); what will not go in or come out stays in the dispenser, which clicks as when empty;</li>
 * <li>the Mercury Bucket, which no fluid handler takes, pours into a Mercury Tank or machine in front and fills an
 * empty cauldron with mercury; an empty bucket draws a bucket of mercury from them.</li>
 * </ul>
 * Vanilla's water and lava buckets, empty bucket, glass bottle and Water Bottle keep their own behaviour wherever no
 * liquid holder stands in front. Nothing moves across a claim's edge, into or out of what stands there or poured out
 * there ({@link DrawRights#mayReach}: asked about as the player who set the dispenser down, or as nobody).
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class LiquidDispensing {
    public static final int BUCKET = 1_000;

    private LiquidDispensing() {}

    @SubscribeEvent
    static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(LiquidDispensing::register);
    }

    private static void register() {
        for (Item bucket : List.of(ModOil.VEGETABLE_OIL_BUCKET.get(), ModOil.BIODIESEL_BUCKET.get(), ModOil.METHANOL_BUCKET.get(),
                ModOil.WOOD_VINEGAR_BUCKET.get(), ModItems.EXPERIENCE_BUCKET.get(), ModItems.MERCURY_BUCKET.get())) {
            bucket(bucket);
        }
        // vanilla's, wrapped: into what holds liquid in front, as before elsewhere
        for (Item item : List.of(Items.WATER_BUCKET, Items.LAVA_BUCKET, Items.BUCKET, Items.GLASS_BOTTLE, Items.POTION)) {
            DispenserBlock.registerBehavior(item, new Behaviour(DispenserBlock.DISPENSER_REGISTRY.get(item)));
        }
        for (Item bottle : Bottles.all()) {
            bottle(bottle);
        }
    }

    /**
     * Gives {@code bucket} (a {@link BucketItem} of a liquid: ours, or an add-on's) the dispenser behaviour of ours: into
     * what holds liquid in front, else poured out there as a water bucket is. Unless it has a behaviour of its own.
     * Main thread, at setup.
     */
    public static void bucket(Item bucket) {
        if (!DispenserBlock.DISPENSER_REGISTRY.containsKey(bucket)) {
            DispenserBlock.registerBehavior(bucket, new Behaviour(null));
        }
    }

    /** As {@link #bucket}, for a bottle {@link Bottles} knows: into what holds liquid in front, else thrown out as any item. */
    static void bottle(Item bottle) {
        if (!DispenserBlock.DISPENSER_REGISTRY.containsKey(bottle)) {
            DispenserBlock.registerBehavior(bottle, new Behaviour(null));
        }
    }

    /** What happened: the stack the dispenser keeps, and whether it went (a click) or failed (the empty click). */
    private record Done(ItemStack stack, boolean ok) {}

    /** Ours over what was there before ({@code fallback}; vanilla's filled-bucket behaviour for our buckets). */
    private static final class Behaviour implements DispenseItemBehavior {
        private final @Nullable DispenseItemBehavior fallback;
        private final Helper helper = new Helper();

        Behaviour(@Nullable DispenseItemBehavior fallback) {
            this.fallback = fallback;
        }

        @Override
        public ItemStack dispense(BlockSource source, ItemStack stack) {
            Done done = this.helper.handle(source, stack);
            BlockPos front = source.pos().relative(source.state().getValue(DispenserBlock.FACING));
            if (done == null && this.fallback == null && stack.getItem() instanceof BucketItem bucket && bucket.content != Fluids.EMPTY
                    && source.level().isLoaded(front) && !DrawRights.mayReach(source.level(), source.pos(), source.blockEntity(), front, null)) {
                done = new Done(stack, false); // a bucket of ours poured out over a claim's edge: not
            }
            if (done == null) {
                return this.fallback != null ? this.fallback.dispense(source, stack) : this.helper.pourOut(source, stack);
            }
            source.level().levelEvent(done.ok() ? 1000 : 1001, source.pos(), 0);
            if (done.ok()) {
                source.level().levelEvent(2000, source.pos(), source.state().getValue(DispenserBlock.FACING).get3DDataValue());
            }
            return done.stack();
        }
    }

    /** The work, on vanilla's helpers (consumeWithRemainder puts the container back into the dispenser, or out in front). */
    private static final class Helper extends DefaultDispenseItemBehavior {
        /** Into or out of what holds liquid in front; null when nothing there does (the fallback's turn). */
        @Nullable Done handle(BlockSource source, ItemStack stack) {
            ServerLevel level = source.level();
            Direction facing = source.state().getValue(DispenserBlock.FACING);
            BlockPos front = source.pos().relative(facing);
            if (!level.isLoaded(front)) {
                return null;
            }
            BlockEntity there = level.getBlockEntity(front);
            if (stack.getItem() instanceof MercuryBucketItem || stack.is(Items.BUCKET) && holdsMercury(level, front, there)) {
                return this.mercury(source, stack, front, there);
            }
            ResourceHandler<FluidResource> handler = level.getCapability(Capabilities.Fluid.BLOCK, front, facing.getOpposite());
            if (handler == null) {
                return null;
            }
            boolean bucket = stack.getItem() instanceof BucketItem;
            Fluid poured = stack.getItem() instanceof BucketItem filled ? filled.content : Bottles.fluidOf(stack);
            if (poured != null && poured != Fluids.EMPTY) {
                if (!stack.is(Items.POTION) || poured == Fluids.WATER) {
                    return this.pour(source, stack, handler, front, there, poured, bucket ? BUCKET : Bottles.AMOUNT, bucket);
                }
                return null; // a potion of something else: vanilla's
            }
            if (stack.is(Items.BUCKET) || stack.is(Items.GLASS_BOTTLE)) {
                return this.draw(source, stack, handler, front, there, stack.is(Items.BUCKET));
            }
            return null;
        }

        private Done pour(BlockSource source, ItemStack stack, ResourceHandler<FluidResource> handler, BlockPos front,
                @Nullable BlockEntity there, Fluid fluid, int amount, boolean bucket) {
            ServerLevel level = source.level();
            if (!DrawRights.mayReach(level, source.pos(), source.blockEntity(), front, there)
                    || !Move.insertExactly(handler, FluidResource.of(fluid), amount)) {
                return new Done(stack, false);
            }
            var sound = bucket ? fluid.getFluidType().getSound(SoundActions.BUCKET_EMPTY) : SoundEvents.BOTTLE_EMPTY;
            if (sound != null) {
                level.playSound(null, front, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            level.gameEvent(null, GameEvent.FLUID_PLACE, front);
            return new Done(this.consumeWithRemainder(source, stack, new ItemStack(bucket ? Items.BUCKET : Items.GLASS_BOTTLE)), true);
        }

        private Done draw(BlockSource source, ItemStack stack, ResourceHandler<FluidResource> handler, BlockPos front,
                @Nullable BlockEntity there, boolean bucket) {
            ServerLevel level = source.level();
            if (!DrawRights.mayReach(level, source.pos(), source.blockEntity(), front, there)) {
                return new Done(stack, false);
            }
            int amount = bucket ? BUCKET : Bottles.AMOUNT;
            for (int i = 0; i < handler.size(); i++) {
                FluidResource resource = handler.getResource(i);
                if (resource.isEmpty() || handler.getAmountAsLong(i) < amount) {
                    continue;
                }
                Fluid fluid = resource.getFluid();
                ItemStack filled = bucket ? new ItemStack(fluid.getBucket()) : Bottles.filled(fluid);
                if (filled.isEmpty() || filled.is(Items.AIR) || !resource.isComponentsPatchEmpty()) {
                    continue;
                }
                try (Transaction tx = Transaction.openRoot()) {
                    if (handler.extract(resource, amount, tx) != amount) {
                        continue;
                    }
                    tx.commit();
                }
                var sound = bucket ? fluid.getFluidType().getSound(SoundActions.BUCKET_FILL) : SoundEvents.BOTTLE_FILL;
                if (sound != null) {
                    level.playSound(null, front, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
                }
                level.gameEvent(null, GameEvent.FLUID_PICKUP, front);
                return new Done(this.consumeWithRemainder(source, stack, filled), true);
            }
            return new Done(stack, false);
        }

        /**
         * Whether an empty bucket at {@code front} draws mercury: a mercury cauldron, or a Mercury Tank or machine with a
         * bucket of it to give (one with none - a Retort between charges - is left to its fluid handler, its wood vinegar).
         */
        private static boolean holdsMercury(ServerLevel level, BlockPos front, @Nullable BlockEntity there) {
            return level.getBlockState(front).is(ModBlocks.MERCURY_CAULDRON.get())
                    || there instanceof MercuryHandler tank && tank.drainMercury(BUCKET, true) == BUCKET;
        }

        /** The Mercury Bucket into a Mercury Tank or machine, or an empty cauldron; an empty bucket back out of them. */
        private @Nullable Done mercury(BlockSource source, ItemStack stack, BlockPos front, @Nullable BlockEntity there) {
            ServerLevel level = source.level();
            BlockState state = level.getBlockState(front);
            boolean full = stack.getItem() instanceof MercuryBucketItem;
            boolean held = there instanceof MercuryHandler || full && state.is(Blocks.CAULDRON) || !full && state.is(ModBlocks.MERCURY_CAULDRON.get());
            if (held && !DrawRights.mayReach(level, source.pos(), source.blockEntity(), front, there)) {
                return new Done(stack, false);
            }
            if (there instanceof MercuryHandler tank) {
                boolean moved = (full ? tank.fillMercury(BUCKET, true) == BUCKET && tank.fillMercury(BUCKET, false) == BUCKET
                        : tank.drainMercury(BUCKET, true) == BUCKET && tank.drainMercury(BUCKET, false) == BUCKET);
                if (!moved) {
                    return new Done(stack, false);
                }
                there.setChanged();
            } else if (full && state.is(Blocks.CAULDRON)) {
                level.setBlockAndUpdate(front, LiquidCauldrons.full(ModBlocks.MERCURY_CAULDRON.get()));
            } else if (!full && state.is(ModBlocks.MERCURY_CAULDRON.get())) {
                if (state.getValue(LayeredCauldronBlock.LEVEL) != 3) {
                    return new Done(stack, false);
                }
                level.setBlockAndUpdate(front, Blocks.CAULDRON.defaultBlockState());
            } else {
                return null; // in the open: poured out as a pool, as any bucket
            }
            level.playSound(null, front, full ? SoundEvents.BUCKET_EMPTY_LAVA : SoundEvents.BUCKET_FILL_LAVA, SoundSource.BLOCKS, 1.0F, 1.2F);
            level.gameEvent(null, full ? GameEvent.FLUID_PLACE : GameEvent.FLUID_PICKUP, front);
            return new Done(this.consumeWithRemainder(source, stack,
                    new ItemStack(full ? Items.BUCKET : ModItems.MERCURY_BUCKET.get())), true);
        }

        /** A bucket of ours with nothing in front that holds liquid: poured out there as a water bucket, else thrown out. */
        ItemStack pourOut(BlockSource source, ItemStack stack) {
            return this.dispense(source, stack);
        }

        @Override
        protected ItemStack execute(BlockSource source, ItemStack stack) {
            if (stack.getItem() instanceof BucketItem bucket && bucket.content != Fluids.EMPTY) {
                BlockPos front = source.pos().relative(source.state().getValue(DispenserBlock.FACING));
                if (bucket.emptyContents(null, source.level(), front, null, stack)) {
                    bucket.checkExtraContent(null, source.level(), stack, front);
                    return this.consumeWithRemainder(source, stack, new ItemStack(Items.BUCKET));
                }
            }
            return super.execute(source, stack); // thrown out, as any item
        }
    }
}
