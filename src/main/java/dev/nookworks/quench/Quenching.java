package dev.nookworks.quench;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import dev.nookworks.Nookworks;
import dev.nookworks.oil.OilFluids;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModDataComponents;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModOil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.core.cauldron.CauldronInteractions;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.minecraft.util.TriState;
import net.neoforged.neoforge.event.RegisterCauldronInteractionEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.fluids.RegisterCauldronFluidContentEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Tempering Velarium (the user, 2026-10-12: "焼き入れがいいな。ただし、鍋でやるのは無理がありるから、大釜とか使わせたい";
 * "赤熱したベラリウムは熱くて手では持てない。ので、ホッパーやディスペンサーで大釜に入れる必要がある。しかも熱いうちに入れなければ
 * いけないので、時間制限がある"). A Velarium Ingot heated in a furnace, blast furnace or Electric Furnace comes out red-hot,
 * one at a time; it burns a hand that takes it and is dropped, and cannot be picked up while it glows. Fed into a
 * cauldron of vegetable oil within {@link #HOT_TICKS} - by a hopper, a dropper, a dispenser, or falling in - it is
 * quenched into a Tempered Velarium Ingot, a level of the oil spent. Cooled first, it is a plain ingot again: heat it
 * again. What comes out of the oil goes into a container under the cauldron, or onto its rim.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class Quenching {
    /** A hand burnt on a red-hot ingot: fire (fire resistance stops it), through any armour, with a death message of its own. */
    public static final ResourceKey<DamageType> BURN = ResourceKey.create(Registries.DAMAGE_TYPE, Nookworks.id("red_hot_ingot"));

    /** How long a red-hot ingot stays hot enough to temper: half a minute, time for a hopper line, not for a walk. */
    public static final int HOT_TICKS = 600;

    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, Nookworks.MODID);
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<HeatingRecipe>> HEATING =
            SERIALIZERS.register("heating", () -> new RecipeSerializer<>(HeatingRecipe.MAP_CODEC, HeatingRecipe.STREAM_CODEC));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<BlastHeatingRecipe>> BLAST_HEATING =
            SERIALIZERS.register("blast_heating", () -> new RecipeSerializer<>(BlastHeatingRecipe.MAP_CODEC, BlastHeatingRecipe.STREAM_CODEC));

    /** What a bucket, a hopper and the rest do with a cauldron of oil (registered under nookworks:vegetable_oil). */
    public static final CauldronInteraction.Dispatcher OIL = new CauldronInteraction.Dispatcher();

    /** The time the ingot came out of the fire, from the server's clock; with none, a creative copy, it counts as hot. */
    public static ItemStack stamp(ItemStack stack, long now) {
        stack.set(ModDataComponents.HEATED_AT.get(), now);
        return stack;
    }

    /**
     * Whether {@code stack}, whatever it is, carries a heating stamp still within {@link #HOT_TICKS}: an add-on's red-hot
     * ingot heated by our recipes as ours is (nookTechnology's steel). The fire turns it away while it is hot, as it
     * turns ours away (r21, 2026-11-06: a loop through a furnace kept a red-hot steel ingot hot for ever).
     */
    public static boolean stampedHot(ItemStack stack, long now) {
        Long heated = stack.get(ModDataComponents.HEATED_AT.get());
        return heated != null && now - heated < HOT_TICKS;
    }

    public static boolean isHot(ItemStack stack, long now) {
        if (!stack.is(ModItems.RED_HOT_VELARIUM_INGOT.get())) {
            return false;
        }
        Long heated = stack.get(ModDataComponents.HEATED_AT.get());
        return heated == null || now - heated < HOT_TICKS;
    }

    /** Ticks a red-hot ingot has left before it is too cool to temper; 0 once it is. */
    public static long heatLeft(ItemStack stack, long now) {
        Long heated = stack.get(ModDataComponents.HEATED_AT.get());
        return heated == null ? HOT_TICKS : Math.max(0, HOT_TICKS - (now - heated));
    }

    /** What a red-hot ingot is once it has cooled in the air. */
    public static ItemStack cooled(ItemStack stack) {
        return new ItemStack(ModItems.VELARIUM_INGOT.get(), stack.getCount());
    }

    /** A cauldron of oil's item handler: takes red-hot ingots one at a time and nothing else. */
    public static ResourceHandler<ItemResource> handler(Level level, BlockPos pos) {
        return BATHS.computeIfAbsent(level, l -> new HashMap<>()).computeIfAbsent(pos.immutable(), p -> new Bath(level, p));
    }

    /**
     * One bath per cauldron, so a transaction reaching it from two faces (a pipe network's) sees what the other has
     * spent (r14 re-check: two handlers each spent the last level). Forgotten when the cauldron goes, with its level.
     */
    private static final Map<Level, Map<BlockPos, Bath>> BATHS = java.util.Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * A bath holds its level, so its level's entry would never go from the weak map: every bath is forgotten with its
     * level, and all of them when the server stops (r15: a world closed stayed in memory, one more with each opened).
     */
    @SubscribeEvent
    static void onLevelUnload(LevelEvent.Unload event) {
        BATHS.remove(event.getLevel());
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        BATHS.clear();
    }

    static void forget(Level level, BlockPos pos) {
        Map<BlockPos, Bath> baths = BATHS.get(level);
        if (baths != null) {
            baths.remove(pos);
        }
    }

    /**
     * One slot, always empty, that takes a red-hot ingot. The quench is worked out in the transaction - the oil it
     * spends and where the tempered ingot goes (the container below takes it in the same transaction, or a full one
     * refuses the ingot) - and done on commit: the oil level set, the hiss and the steam, and the ingot set on the
     * rim when there is no container below. A simulated insert (a pipe trying first) changes nothing. The oil is read
     * from the block each time, less what this transaction has spent: a handler another mod caches outlives any number
     * of quenches, fillings and drainings by others (r14: a cached one put back oil they had spent).
     */
    private static final class Bath implements ResourceHandler<ItemResource> {
        private final Level level;
        private final BlockPos pos;
        private record Pending(int oilUsed, int quenched, List<ItemStack> onRim) {}
        private Pending pending;
        private final SnapshotJournal<Pending> journal = new SnapshotJournal<>() {
            @Override
            protected Pending createSnapshot() {
                return Bath.this.pending;
            }

            @Override
            protected void revertToSnapshot(Pending snapshot) {
                Bath.this.pending = snapshot;
            }

            @Override
            protected void onRootCommit(Pending original) {
                Bath.this.commit(original);
            }
        };

        Bath(Level level, BlockPos pos) {
            this.level = level;
            this.pos = pos;
            this.pending = new Pending(0, 0, List.of());
        }

        /** The oil in the cauldron now, less what this transaction has spent so far. */
        private int oil() {
            BlockState state = this.level.getBlockState(this.pos);
            return (state.is(ModBlocks.VEGETABLE_OIL_CAULDRON.get()) ? state.getValue(LayeredCauldronBlock.LEVEL) : 0) - this.pending.oilUsed();
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public ItemResource getResource(int index) {
            return ItemResource.EMPTY;
        }

        @Override
        public long getAmountAsLong(int index) {
            return 0;
        }

        /** Room for one, asked of nothing in particular (a hopper asks so before it tries) as of a red-hot ingot. */
        @Override
        public long getCapacityAsLong(int index, ItemResource resource) {
            return this.oil() > 0 && (resource.isEmpty() || this.isValid(index, resource)) ? 1 : 0;
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            return resource.is(ModItems.RED_HOT_VELARIUM_INGOT.get()) && this.oil() > 0;
        }

        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
            if (amount <= 0 || !this.isValid(index, resource)) {
                return 0;
            }
            ItemStack ingot = resource.toStack(1);
            boolean hot = isHot(ingot, this.level.getGameTime());
            ItemStack out = hot ? new ItemStack(ModItems.TEMPERED_VELARIUM_INGOT.get()) : cooled(ingot);
            ResourceHandler<ItemResource> below = this.level.getCapability(Capabilities.Item.BLOCK, this.pos.below(), Direction.UP);
            List<ItemStack> onRim = this.pending.onRim();
            if (below != null) {
                if (below.insert(ItemResource.of(out), 1, transaction) < 1) {
                    return 0; // the container below is full: the ingot waits where it is
                }
            } else {
                onRim = new ArrayList<>(onRim);
                onRim.add(out);
            }
            this.journal.updateSnapshots(transaction);
            this.pending = new Pending(this.pending.oilUsed() + (hot ? 1 : 0), this.pending.quenched() + (hot ? 1 : 0), onRim);
            return 1;
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
            return 0;
        }

        private void commit(Pending before) {
            Pending now = this.pending;
            if (this.level instanceof ServerLevel server) {
                int spent = now.oilUsed() - before.oilUsed();
                BlockState current = server.getBlockState(this.pos);
                if (spent > 0 && current.is(ModBlocks.VEGETABLE_OIL_CAULDRON.get())) {
                    int left = current.getValue(LayeredCauldronBlock.LEVEL) - spent;
                    BlockState state = left <= 0 ? Blocks.CAULDRON.defaultBlockState()
                            : current.setValue(LayeredCauldronBlock.LEVEL, left);
                    server.setBlockAndUpdate(this.pos, state);
                    server.gameEvent(GameEvent.BLOCK_CHANGE, this.pos, GameEvent.Context.of(state));
                }
                if (now.quenched() > before.quenched()) {
                    server.playSound(null, this.pos, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.8F, 1.4F);
                    server.sendParticles(ParticleTypes.LARGE_SMOKE, this.pos.getX() + 0.5, this.pos.getY() + 1.0, this.pos.getZ() + 0.5,
                            8, 0.2, 0.1, 0.2, 0.02);
                }
                for (ItemStack out : now.onRim()) {
                    ItemEntity entity = new ItemEntity(server, this.pos.getX() + 0.5, this.pos.getY() + 1.05, this.pos.getZ() + 0.5, out, 0, 0.15, 0);
                    entity.setDefaultPickUpDelay();
                    server.addFreshEntity(entity);
                }
            }
            this.pending = new Pending(0, 0, List.of());
        }
    }

    /** Puts one red-hot ingot of {@code stack} into the oil at {@code pos}; true, and the stack one smaller, if it went. */
    public static boolean dip(Level level, BlockPos pos, ItemStack stack) {
        if (!level.getBlockState(pos).is(ModBlocks.VEGETABLE_OIL_CAULDRON.get()) || stack.isEmpty()) {
            return false;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            if (handler(level, pos).insert(ItemResource.of(stack), 1, transaction) == 1) {
                transaction.commit();
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }

    // ---- registration ----

    @EventBusSubscriber(modid = Nookworks.MODID)
    static final class Registration {
        @SubscribeEvent
        static void dispatchers(RegisterCauldronInteractionEvent.Dispatcher event) {
            event.register(Nookworks.id("vegetable_oil"), OIL);
        }

        @SubscribeEvent
        static void interactions(RegisterCauldronInteractionEvent.Interaction event) {
            // a bucket of oil fills any cauldron, as a bucket of water or lava does; an empty bucket takes a full one back
            event.registerToAll(ModOil.VEGETABLE_OIL_BUCKET.get(), (state, level, pos, player, hand, held) -> CauldronInteractions.emptyBucket(
                    level, pos, player, hand, held,
                    ModBlocks.VEGETABLE_OIL_CAULDRON.get().defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3), SoundEvents.BUCKET_EMPTY));
            CauldronInteractions.addDefaultInteractions(OIL);
            OIL.put(Items.BUCKET, (state, level, pos, player, hand, held) -> CauldronInteractions.fillBucket(state, level, pos, player, hand,
                    held, new ItemStack(ModOil.VEGETABLE_OIL_BUCKET.get()), s -> s.getValue(LayeredCauldronBlock.LEVEL) == 3, SoundEvents.BUCKET_FILL));
            // in creative a red-hot ingot can be held: dipped by hand
            OIL.put(ModItems.RED_HOT_VELARIUM_INGOT.get(), (state, level, pos, player, hand, held) ->
                    level.isClientSide() || dip(level, pos, held) ? InteractionResult.SUCCESS : InteractionResult.TRY_WITH_EMPTY_HAND);
        }

        /** Pipes and tanks fill and drain it as they would a cauldron of water: a bucket's worth, three levels. */
        @SubscribeEvent
        static void fluid(RegisterCauldronFluidContentEvent event) {
            event.register(ModBlocks.VEGETABLE_OIL_CAULDRON.get(), OilFluids.SOURCE.get(), 1000, LayeredCauldronBlock.LEVEL);
        }

        @SubscribeEvent
        static void capabilities(RegisterCapabilitiesEvent event) {
            event.registerBlock(Capabilities.Item.BLOCK, (level, pos, state, be, side) -> handler(level, pos),
                    ModBlocks.VEGETABLE_OIL_CAULDRON.get());
        }

        @SubscribeEvent
        static void setup(FMLCommonSetupEvent event) {
            event.enqueueWork(() -> DispenserBlock.registerBehavior(ModItems.RED_HOT_VELARIUM_INGOT.get(), new Dispense()));
        }

        private Registration() {}
    }

    /** A dispenser facing a cauldron of oil dips the ingot in, and waits while it cannot; facing anything else, throws it. */
    static final class Dispense extends DefaultDispenseItemBehavior {
        @Override
        protected ItemStack execute(BlockSource source, ItemStack stack) {
            BlockPos front = source.pos().relative(source.state().getValue(DispenserBlock.FACING));
            if (source.level().getBlockState(front).is(ModBlocks.VEGETABLE_OIL_CAULDRON.get())) {
                dip(source.level(), front, stack);
                return stack;
            }
            return super.execute(source, stack);
        }
    }

    /** A red-hot ingot on the ground is not picked up while it glows (survival; creative players take what they like). */
    @SubscribeEvent
    static void pickup(ItemEntityPickupEvent.Pre event) {
        ItemStack stack = event.getItemEntity().getItem();
        if (isHot(stack, event.getItemEntity().level().getGameTime()) && !event.getPlayer().isCreative()) {
            event.setCanPickup(TriState.FALSE);
        }
    }

    /** The block a red-hot ingot dropped into it falls in: quenched there as if a hopper had fed it. */
    static void fallenIn(Level level, BlockPos pos, ItemEntity entity) {
        ItemStack stack = entity.getItem();
        if (!level.isClientSide() && stack.is(ModItems.RED_HOT_VELARIUM_INGOT.get()) && dip(level, pos, stack) && stack.isEmpty()) {
            entity.discard();
        }
    }

    private Quenching() {}
}
