package dev.nookworks.cooking;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.logging.LogUtils;

import dev.nookworks.Nookworks;
import dev.nookworks.NookworksNetwork;
import dev.nookworks.block.Clicks;
import dev.nookworks.oil.OilFluids;
import dev.nookworks.registry.ModBlockEntities;
import dev.nookworks.registry.ModCooking;
import dev.nookworks.registry.ModOil;
import dev.nookworks.ventilation.Ventilation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Prediction;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Iron Pot: a deep black iron pot for the fire. Set it over heat (on a Grill over a lit campfire, fire, magma, lava, a
 * lit furnace, a running Generator), pour in water or vegetable oil - bottles, a bucket, or a Copper Pipe - and put in
 * what a {@link PotRecipe} asks for: in water it boils (rice, soup), in oil it fries and stir-fries.
 * <p>
 * On a Grill over a campfire the pot sits down on the grate, and takes the grill's place: one block, {@link #ON_GRILL},
 * drawn as the grill with the pot on it, so what you see is what you click and what you break. Broken, the pot comes
 * away and the grill stays; take the campfire away and the grill stands up with the pot on top of it.
 * <ul>
 * <li>Use an ingredient on it: in it goes, one at a time. Water or oil: in it goes. An empty bottle or bucket: out it
 * comes again.</li>
 * <li>Use a bowl (or a glass bottle, for what goes in one) on it when the dish is ready: a serving.</li>
 * <li>Use it with an empty hand: a dish taken by hand, all of it, or one served into the bowls the ingredients came in;
 * otherwise it says what is in it. Sneak: the last ingredient comes back out.</li>
 * </ul>
 */
public class IronPotBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** In a grill's place over a campfire: the block is the grill and the pot on its grate. */
    public static final BooleanProperty ON_GRILL = BooleanProperty.create("on_grill");
    public static final BooleanProperty COOKING = BooleanProperty.create("cooking");
    /** What the model shows standing in the pot. */
    public static final EnumProperty<Contents> LIQUID = EnumProperty.create("liquid", Contents.class);
    /** What heats a pot from below. A block with a "lit" state only while lit. */
    public static final TagKey<Block> HEAT = TagKey.create(Registries.BLOCK, Nookworks.id("heats_pots"));
    /**
     * What passes heat up from a fire under it to a pot or a grill on top (a hopper): the hopper that takes the dishes out
     * from underneath cannot be under the fire as well, so the fire goes under the hopper.
     */
    public static final TagKey<Block> CONDUCTS_HEAT = TagKey.create(Registries.BLOCK, Nookworks.id("conducts_heat"));
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 7, 14);
    /** The grate a pixel thick, and the pot standing on it. */
    private static final VoxelShape ON_GRILL_SHAPE = Shapes.or(Block.box(0, 0, 0, 16, 1, 16), Block.box(2, 1, 2, 14, 8, 14));

    public enum Contents implements StringRepresentable {
        EMPTY("empty"),
        WATER("water"),
        OIL("oil");

        private final String name;

        Contents(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }

    public IronPotBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ON_GRILL, false)
                .setValue(COOKING, false).setValue(LIQUID, Contents.EMPTY));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ON_GRILL, COOKING, LIQUID);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** A grill astride a campfire with nothing on its grate: a pot may take its place. */
    static boolean emptyGrillOverFire(BlockGetter level, BlockPos pos) {
        return GrillBlock.overFire(level.getBlockState(pos))
                && level.getBlockEntity(pos) instanceof GrillBlockEntity grill && grill.items().stream().allMatch(ItemStack::isEmpty);
    }

    /**
     * Whether the pot at {@code pos} has heat under it: on a grill over a lit campfire, or on a block in #heats_pots (a
     * block with a "lit" state only while lit). A campfire itself is not in the tag - a pot needs the grill to sit on.
     * Or on a block in #conducts_heat (a hopper) over a lit fire of either kind: the heat comes up through it.
     */
    public static boolean heated(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        BlockState below = level.getBlockState(pos.below());
        if (state.getBlock() instanceof IronPotBlock && state.getValue(ON_GRILL)) {
            return CampfireBlock.isLitCampfire(below); // a mod's campfire with no "lit" state reads as unlit
        }
        if (below.is(CONDUCTS_HEAT)) {
            return heatsThrough(level.getBlockState(pos.below(2)));
        }
        return below.is(HEAT) && lit(below);
    }

    /** Whether {@code source}, under a block that conducts heat, sends heat up through it: a lit campfire or anything that heats a pot. */
    public static boolean heatsThrough(BlockState source) {
        return (source.is(HEAT) || source.is(BlockTags.CAMPFIRES)) && lit(source);
    }

    private static boolean lit(BlockState state) {
        return !state.hasProperty(BlockStateProperties.LIT) || state.getValue(BlockStateProperties.LIT);
    }

    /** The pot's state for {@code facing}, set down on a grill over a fire. */
    public BlockState onGrill(Direction facing) {
        return this.defaultBlockState().setValue(FACING, facing).setValue(ON_GRILL, true);
    }

    /** Turned and mirrored with the build it stands in (a structure block's, a building mod's), as vanilla's facing blocks are. */
    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos below = context.getClickedPos().below();
        if (GrillBlock.overFire(context.getLevel().getBlockState(below)) && !emptyGrillOverFire(context.getLevel(), below)) {
            return null; // food on the grate: clear it first (the pot would hide it, and float over it)
        }
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /** Set down in the block over a grill astride a fire (from the side, say), it drops onto the grate. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!state.getValue(ON_GRILL) && GrillBlock.overFire(level.getBlockState(pos.below()))) {
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
            Direction side, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if (side == Direction.DOWN) {
            boolean campfire = neighbourState.is(BlockTags.CAMPFIRES);
            if (state.getValue(ON_GRILL) ? !campfire : GrillBlock.overFire(neighbourState)) {
                ticks.scheduleTick(pos, this, 1); // off the grill, or onto it: moved on the next tick
            }
        }
        return state;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(ON_GRILL) && !level.getBlockState(pos.below()).is(BlockTags.CAMPFIRES)) {
            this.standUp(level, pos, state);
        } else if (!state.getValue(ON_GRILL) && GrillBlock.overFire(level.getBlockState(pos.below()))) {
            this.sitDown(level, pos, state);
        }
    }

    /**
     * The campfire under the pot on its grill went: the grill stands back up on its own legs, grate on top, and the pot
     * goes up onto it with everything in it. With no room above, the pot comes off as an item and its contents spill.
     */
    private void standUp(ServerLevel level, BlockPos pos, BlockState state) {
        BlockState grill = ModCooking.GRILL.get().defaultBlockState();
        BlockPos above = pos.above();
        BlockState up = level.getBlockState(above);
        // the top of the world counts as no room: the air over it takes no block, and the pot set there was lost
        if (level.isOutsideBuildHeight(above) || !up.canBeReplaced() || !up.getFluidState().isEmpty()) {
            Block.popResource(level, pos, new ItemStack(this));
            level.setBlock(pos, grill, Block.UPDATE_ALL); // the pot's contents drop as it goes
            return;
        }
        CompoundTag saved = this.lift(level, pos);
        level.setBlock(pos, grill, Block.UPDATE_ALL);
        level.setBlock(above, state.setValue(ON_GRILL, false).setValue(COOKING, false), Block.UPDATE_ALL);
        this.settle(level, above, saved);
    }

    /** A grill under the pot went down over a fire: the pot sits down on its grate, in its place. */
    private void sitDown(ServerLevel level, BlockPos pos, BlockState state) {
        BlockPos below = pos.below();
        if (level.getBlockEntity(below) instanceof GrillBlockEntity grill) {
            grill.dropContents(level, below); // anything left on the grate comes off: the pot takes the whole of it
            grill.items().clear();
        }
        CompoundTag saved = this.lift(level, pos);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(below, this.onGrill(state.getValue(FACING)).setValue(LIQUID, state.getValue(LIQUID)), Block.UPDATE_ALL);
        this.settle(level, below, saved);
    }

    /** Saves the pot at {@code pos} for a move, telling it not to spill when its block goes. */
    private CompoundTag lift(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof IronPotBlockEntity pot) {
            pot.moving();
            return pot.saveWithoutMetadata(level.registryAccess());
        }
        return new CompoundTag();
    }

    private void settle(ServerLevel level, BlockPos pos, CompoundTag saved) {
        if (level.getBlockEntity(pos) instanceof IronPotBlockEntity pot) {
            try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(pot.problemPath(), LogUtils.getLogger())) {
                pot.loadWithComponents(TagValueInput.create(reporter, level.registryAccess(), saved));
            }
            pot.setChanged();
            level.sendBlockUpdated(pos, pot.getBlockState(), pot.getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Broken on its grill, only the pot comes away: the grill stays over the fire. */
    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, ItemStack tool, boolean willHarvest,
            FluidState fluid) {
        if (!state.getValue(ON_GRILL)) {
            return super.onDestroyedByPlayer(state, level, pos, player, tool, willHarvest, fluid);
        }
        return level.setBlock(pos, ModCooking.GRILL.get().defaultBlockState().setValue(GrillBlock.OVER_FIRE, true),
                level.isClientSide() ? Block.UPDATE_ALL_IMMEDIATE : Block.UPDATE_ALL);
    }

    /** The pot's own drops, without the grill that stayed (an explosion takes both, through the loot table). */
    @Override
    public void playerDestroy(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state,
            @Nullable BlockEntity blockEntity, ItemStack tool) {
        super.playerDestroy(level, player, pos, state.setValue(ON_GRILL, false), blockEntity, tool);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(ON_GRILL) ? ON_GRILL_SHAPE : SHAPE;
    }

    /** What pouring {@code stack} in would add, or null for something that is not water or oil. */
    private static PotRecipe.@Nullable Liquid pours(ItemStack stack) {
        if (stack.is(Items.WATER_BUCKET) || stack.is(Items.POTION)
                && stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(Potions.WATER)) {
            return PotRecipe.Liquid.WATER;
        }
        return stack.is(ModOil.VEGETABLE_OIL_BUCKET.get()) || stack.is(ModOil.VEGETABLE_OIL_BOTTLE.get()) ? PotRecipe.Liquid.OIL : null;
    }

    /** What an empty bottle or bucket comes back full of, taking {@code liquid} out of a pot; empty if nothing. */
    private static ItemStack fill(ItemStack empty, PotRecipe.@Nullable Liquid liquid) {
        if (liquid == null) {
            return ItemStack.EMPTY;
        }
        if (empty.is(Items.BUCKET)) {
            return new ItemStack(liquid == PotRecipe.Liquid.WATER ? Items.WATER_BUCKET : ModOil.VEGETABLE_OIL_BUCKET.get());
        }
        if (empty.is(Items.GLASS_BOTTLE)) {
            return liquid == PotRecipe.Liquid.WATER ? PotionContents.createItemStack(Items.POTION, Potions.WATER)
                    : new ItemStack(ModOil.VEGETABLE_OIL_BOTTLE.get());
        }
        return ItemStack.EMPTY;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof IronPotBlockEntity pot)) {
            return InteractionResult.PASS;
        }
        if (!pot.dish().isEmpty() && pot.container() != null && stack.is(pot.container())) {
            if (!level.isClientSide()) {
                Item container = pot.container();
                player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, pot.serve()));
                if (hand == InteractionHand.MAIN_HAND && !player.getMainHandItem().is(container)) {
                    // the last bowl in hand became the dish: off the hand, as a Tray gives back, or the next click put a
                    // Bowl of Rice back into the pot as an ingredient (round two of r21)
                    TrayBlock.keepHandEmpty(player.getInventory());
                }
                level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.6F, 0.8F);
            }
            return InteractionResult.SUCCESS;
        }
        PotRecipe.Liquid pour = pours(stack);
        if (pour != null) {
            boolean bucket = stack.is(Items.WATER_BUCKET) || stack.is(ModOil.VEGETABLE_OIL_BUCKET.get());
            if (!level.isClientSide()) {
                int mB = bucket ? IronPotBlockEntity.CAPACITY : OilFluids.BOTTLE;
                IronPotBlockEntity.Refusal refusal = pot.refusesLiquid(pour, mB);
                if (refusal != null) {
                    player.sendOverlayMessage(Component.translatable(switch (refusal) {
                        case DISH_WAITING -> "nookworks.iron_pot.take_dish_first";
                        case OTHER_LIQUID -> "nookworks.iron_pot.no_mixing";
                        case FULL -> "nookworks.iron_pot.no_room";
                    }).withStyle(ChatFormatting.RED));
                    return Clicks.REFUSED;
                }
                pot.addLiquid(pour, mB);
                ItemStack empty = new ItemStack(bucket ? Items.BUCKET : Items.GLASS_BOTTLE);
                player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, empty));
                level.playSound(null, pos, bucket ? SoundEvents.BUCKET_EMPTY : SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 0.8F,
                        pour == PotRecipe.Liquid.OIL ? 0.8F : 1.0F);
            }
            return InteractionResult.SUCCESS;
        }
        ItemStack filled = fill(stack, pot.liquid());
        if (!filled.isEmpty()) {
            int mB = stack.is(Items.BUCKET) ? IronPotBlockEntity.CAPACITY : OilFluids.BOTTLE;
            if (!level.isClientSide()) {
                if (pot.dish().isEmpty() && pot.amount() < OilFluids.BOTTLE && pot.percent() < 0) {
                    // under a bottle's worth left (an egg boiled out of a bucket's water, oil left from frying): nothing
                    // took it out and the other liquid was turned down, so the pot could not be emptied - tipped out
                    // (round two of r21); not while it cooks, which a bottle carried past would have spoilt
                    pot.drainLiquid(pot.amount());
                    player.sendOverlayMessage(Component.translatable("nookworks.iron_pot.tipped_out"));
                    level.playSound(null, pos, stack.is(Items.BUCKET) ? SoundEvents.BUCKET_EMPTY : SoundEvents.BOTTLE_EMPTY,
                            SoundSource.BLOCKS, 0.5F, 1.2F);
                    return InteractionResult.SUCCESS;
                }
                if (!pot.drainLiquid(mB)) {
                    player.sendOverlayMessage(Component.translatable(pot.dish().isEmpty() ? "nookworks.iron_pot.too_little"
                            : "nookworks.iron_pot.take_dish_first").withStyle(ChatFormatting.RED));
                    return Clicks.REFUSED;
                }
                player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, filled));
                level.playSound(null, pos, stack.is(Items.BUCKET) ? SoundEvents.BUCKET_FILL : SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.8F, 1.0F);
            }
            return InteractionResult.SUCCESS;
        }
        if (!stack.isEmpty() && PotRecipe.isIngredient(level, stack)) {
            if (!level.isClientSide()) {
                if (!pot.addIngredient(stack)) {
                    player.sendOverlayMessage(Component.translatable(pot.dish().isEmpty() ? "nookworks.iron_pot.full"
                            : "nookworks.iron_pot.take_dish_first").withStyle(ChatFormatting.RED));
                    return Clicks.REFUSED;
                }
                boolean frying = pot.liquid() == PotRecipe.Liquid.OIL;
                stack.consume(1, player); // a bowl it came in stays with it in the pot until the cooking frees it
                level.playSound(null, pos, frying ? SoundEvents.GENERIC_EXTINGUISH_FIRE : SoundEvents.ITEM_FRAME_ADD_ITEM,
                        SoundSource.BLOCKS, frying ? 0.3F : 0.6F, frying ? 1.8F : 1.2F);
            }
            return InteractionResult.SUCCESS;
        }
        if (stack.getItem() instanceof net.minecraft.world.item.BucketItem filledBucket
                && filledBucket.getContent() != net.minecraft.world.level.material.Fluids.EMPTY
                || stack.is(Items.POWDER_SNOW_BUCKET)) {
            // any other bucket went on to be poured out over the pot: lava on a pot over a fire, and the kitchen burned
            // (the kitchen play-test, 2026-10-19)
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("nookworks.iron_pot.only_water_or_oil").withStyle(ChatFormatting.RED));
            }
            return Clicks.REFUSED;
        }
        if (stack.has(DataComponents.FOOD)) {
            // a food no pot recipe takes (a Boiled Egg into the oil) went on to be eaten, or did nothing at all, with no
            // word why (the kitchen play-test, item 8); other things in hand still act as on any block (one is set down)
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("nookworks.iron_pot.not_an_ingredient").withStyle(ChatFormatting.RED));
            }
            return Clicks.REFUSED;
        }
        return stack.isEmpty() ? InteractionResult.TRY_WITH_EMPTY_HAND : InteractionResult.PASS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS; // a dish is taken by hand; anything else in hand is used as on any block
        }
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof IronPotBlockEntity pot) {
            List<ItemStack> out = new ArrayList<>();
            if (player.isSecondaryUseActive()) {
                out.add(pot.takeIngredient());
            } else if (!pot.dish().isEmpty() && pot.container() == null) {
                out.add(pot.takeDish());
                out.addAll(pot.takeFreed());
            } else if (!pot.dish().isEmpty()) {
                out.addAll(pot.serveIntoFreed()); // into the bowls the rice came in
            } else {
                out.addAll(pot.takeFreed());
            }
            out.removeIf(ItemStack::isEmpty);
            for (ItemStack stack : out) {
                // in stacks a hand can hold, as when the pot is broken: eight soap (one to a stack) that found no room
                // were thrown as one item, and a hopper that caught it kept one and lost the rest
                while (!stack.isEmpty()) {
                    ItemStack piece = stack.split(Math.max(1, stack.getMaxStackSize()));
                    if (!player.getInventory().add(piece)) {
                        player.drop(piece, false, Prediction.PREDICTED);
                    }
                }
            }
            if (!out.isEmpty()) {
                TrayBlock.keepHandEmpty(player.getInventory()); // as a Tray gives back: the hand stays free for the next click
            }
            if (out.isEmpty()) {
                NookworksNetwork.PotContents contents = pot.shown();
                if (contents != null && player instanceof ServerPlayer to && to.connection.hasChannel(NookworksNetwork.PotContents.TYPE)) {
                    PacketDistributor.sendToPlayer(to, contents); // fitted to the screen there
                } else {
                    Component why = pot.stuck();
                    player.sendOverlayMessage(why == null ? pot.describe() : pot.describe().copy().append("  ").append(why));
                }
            } else {
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.6F, 1.0F);
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** Steam off boiling water; spitting oil and a thin haze off frying. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(COOKING)) {
            return;
        }
        double top = pos.getY() + (state.getValue(ON_GRILL) ? 7.5 / 16 : 6.5 / 16);
        boolean oil = state.getValue(LIQUID) == Contents.OIL;
        if (random.nextInt(oil ? 2 : 3) == 0) {
            Ventilation.smoke(level, pos, oil ? ParticleTypes.SMOKE : ParticleTypes.WHITE_SMOKE,
                    pos.getX() + 0.5 + random.nextGaussian() * 0.12, top, pos.getZ() + 0.5 + random.nextGaussian() * 0.12, 0.0, oil ? 0.02 : 0.03, 0.0);
        }
        if (oil && random.nextInt(6) == 0) {
            level.playLocalSound(pos, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS, 0.5F, 1.8F, false);
        } else if (!oil && random.nextInt(40) == 0) {
            level.playLocalSound(pos, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, SoundSource.BLOCKS, 0.4F, 0.8F, false);
        }
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new IronPotBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, ModBlockEntities.IRON_POT.get(), IronPotBlockEntity::serverTick);
    }
}
