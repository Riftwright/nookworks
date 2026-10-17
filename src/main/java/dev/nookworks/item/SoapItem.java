package dev.nookworks.item;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import dev.nookworks.feature.Fireproofing;
import dev.nookworks.feature.Lubrication;
import dev.nookworks.feature.Permission;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ColorCollection;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * Soap: vegetable oil boiled with caustic soda. Scrub a dyed block with it - wool, carpet, a bed, stained glass or a pane
 * of it, terracotta - and the dye washes out, back to white wool, plain glass, plain terracotta. On anything else it
 * washes off what was put on: the oil from a hinge (see {@link Lubrication}) and a fireproof coating (see
 * {@link Fireproofing}). A bar lasts a while.
 */
public class SoapItem extends Item {
    /** Each dyed block and what it washes back to. */
    private static final Map<Block, Block> UNDYED = new HashMap<>();

    public SoapItem(Properties properties) {
        super(properties);
    }

    /**
     * Washing something in the crafting grid - a Lode Pearl back to a plain ender pearl - takes one scrub, as a block
     * does, and hands the bar back; the last scrub uses it up.
     */
    @Override
    public @Nullable ItemStackTemplate getCraftingRemainder(ItemInstance instance) {
        if (!(instance instanceof ItemStack stack)) {
            return super.getCraftingRemainder(instance);
        }
        ItemStack worn = stack.copyWithCount(1);
        worn.setDamageValue(worn.getDamageValue() + 1);
        return worn.getDamageValue() >= worn.getMaxDamage() ? null : ItemStackTemplate.fromNonEmptyStack(worn);
    }

    /** The block {@code block} washes back to, or null when it has no dye to lose. */
    public static @Nullable Block washed(Block block) {
        if (UNDYED.isEmpty()) {
            washTo(Blocks.WOOL, Blocks.WOOL.pick(DyeColor.WHITE));
            washTo(Blocks.CARPET, Blocks.CARPET.pick(DyeColor.WHITE));
            washTo(Blocks.BED, Blocks.BED.pick(DyeColor.WHITE));
            washTo(Blocks.STAINED_GLASS, Blocks.GLASS);
            washTo(Blocks.STAINED_GLASS_PANE, Blocks.GLASS_PANE);
            washTo(Blocks.DYED_TERRACOTTA, Blocks.TERRACOTTA);
        }
        return UNDYED.get(block);
    }

    private static void washTo(ColorCollection<Block> dyed, Block plain) {
        dyed.forEach(block -> {
            if (block != plain) {
                UNDYED.put(block, plain);
            }
        });
    }

    /** Before the block's own right-click, or a bed would be slept in and a door opened instead of washed. */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Player player = context.getPlayer();
        Block plain = washed(state.getBlock());
        List<BlockPos> parts = parts(pos, state);
        boolean coated = parts.stream().anyMatch(p -> Lubrication.isLubricated(level, p) || Fireproofing.isCoated(level, p));
        if (plain == null && !coated) {
            return InteractionResult.PASS;
        }
        if (player != null && (!level.mayInteract(player, pos) || !player.mayUseItemAt(pos, context.getClickedFace(), context.getItemInHand()))) {
            return InteractionResult.PASS; // someone else's claim, spawn protection, adventure mode
        }
        if (state.hasProperty(BedBlock.OCCUPIED) && state.getValue(BedBlock.OCCUPIED)) {
            if (player != null && !level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("block.minecraft.bed.occupied"));
            }
            return InteractionResult.FAIL; // not while someone is asleep in it
        }
        if (level instanceof ServerLevel server) {
            if (player != null) {
                for (BlockPos part : parts) {
                    // the other half of a bed or door, which a claim's edge may run between, asked as the clicked one was
                    if (!part.equals(pos) && !Permission.mayPlace(server, part, Direction.UP, player)) {
                        return InteractionResult.FAIL;
                    }
                }
            }
            if (plain != null) {
                // Both halves of a bed at once, and without telling the neighbours in between: a bed half next to a
                // half of another colour would take itself for broken and drop.
                for (BlockPos part : parts) {
                    BlockState old = server.getBlockState(part);
                    server.setBlock(part, plain.withPropertiesOf(old), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
                for (BlockPos part : parts) {
                    server.updateNeighborsAt(part, plain);
                }
            } else {
                for (BlockPos part : parts) {
                    Lubrication.clean(server, part);
                    Fireproofing.uncoat(server, part);
                }
            }
            server.sendParticles(ParticleTypes.BUBBLE_POP, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, 8, 0.3, 0.2, 0.3, 0.02);
            server.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.PLAYERS, 0.5F, 1.6F);
            server.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
            if (player != null) {
                context.getItemInHand().hurtAndBreak(1, player, context.getHand());
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** The squares one scrub covers: both halves of a bed or a door, one square of anything else. */
    private static List<BlockPos> parts(BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof BedBlock) {
            return List.of(pos, pos.relative(BedBlock.getConnectedDirection(state)));
        }
        if (state.getBlock() instanceof DoorBlock) {
            return List.of(pos, state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below());
        }
        return List.of(pos);
    }
}
