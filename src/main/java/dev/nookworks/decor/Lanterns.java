package dev.nookworks.decor;

import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import com.google.common.base.Suppliers;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import dev.nookworks.Nookworks;
import dev.nookworks.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.TriState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Lanterns put out and lit again (the user, 2026-10-12: a lantern gone out, for builds, and for the If's ruins; the
 * copper ones too). Sneak and right-click a lantern, a soul lantern or any of the eight copper lanterns with an empty
 * hand and it goes out - standing or hanging, in water or not - into its unlit one, which gives no light; flint and
 * steel or a fire charge lights it again. The unlit one is vanilla's own lantern with sooty glass over its flame
 * (blockstates/unlit_*lantern.json), so the two match; a copper one keeps its weathering and its wax either way.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class Lanterns {
    private Lanterns() {}

    /** Each lantern and its unlit one. */
    private static final Supplier<BiMap<Block, Block>> UNLIT = Suppliers.memoize(() -> {
        BiMap<Block, Block> unlit = HashBiMap.create();
        unlit.put(Blocks.LANTERN, ModBlocks.UNLIT_LANTERN.get());
        unlit.put(Blocks.SOUL_LANTERN, ModBlocks.UNLIT_SOUL_LANTERN.get());
        ModBlocks.UNLIT_COPPER_LANTERNS.forEach((lit, out) -> unlit.put(BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(lit)), out.get()));
        return unlit;
    });

    /** The unlit one of {@code lit}, or null when it is not a lantern of ours to put out. Public for tests. */
    public static @Nullable Block unlit(Block lit) {
        return UNLIT.get().get(lit);
    }

    /** The lantern {@code unlit} is when lit, or null when it is not an unlit lantern. Public for tests. */
    public static @Nullable Block lit(Block unlit) {
        return UNLIT.get().inverse().get(unlit);
    }

    /** {@code to}'s default state with the hanging and waterlogged {@code from} had. */
    private static BlockState swap(BlockState from, Block to) {
        return to.defaultBlockState().setValue(LanternBlock.HANGING, from.getValue(LanternBlock.HANGING))
                .setValue(LanternBlock.WATERLOGGED, from.getValue(LanternBlock.WATERLOGGED));
    }

    /**
     * Putting a lantern out or lighting it changes the block, so neither is for a spectator or in adventure mode - as a
     * candle is put out only by a player who may build - and both are heard at low priority, after a claim mod (r15: a
     * spectator put lanterns out).
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        Player player = event.getEntity();
        ItemStack held = event.getItemStack();
        if (player.isSpectator() || !level.mayInteract(player, pos)) {
            return;
        }
        Block out = unlit(state.getBlock());
        if (out != null && event.getHand() == InteractionHand.MAIN_HAND && held.isEmpty() && player.isSecondaryUseActive()
                && player.mayBuild() && event.getUseBlock() != TriState.FALSE) {
            if (level instanceof ServerLevel server) {
                server.setBlock(pos, swap(state, out), Block.UPDATE_ALL);
                server.playSound(null, pos, SoundEvents.CANDLE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 1.0F);
                server.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.1, 0.1, 0.1, 0.01);
            }
            done(event);
            return;
        }
        Block in = lit(state.getBlock());
        if (in != null && (held.is(Items.FLINT_AND_STEEL) || held.is(Items.FIRE_CHARGE)) && event.getUseItem() != TriState.FALSE
                && event.getFace() != null && player.mayUseItemAt(pos.relative(event.getFace()), event.getFace(), held)) {
            if (level instanceof ServerLevel server) {
                server.setBlock(pos, swap(state, in), Block.UPDATE_ALL);
                if (held.is(Items.FLINT_AND_STEEL)) {
                    server.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
                    held.hurtAndBreak(1, player, event.getHand() == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
                } else {
                    server.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
                    held.consume(1, player);
                }
            }
            done(event);
        }
    }

    private static void done(PlayerInteractEvent.RightClickBlock event) {
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }
}
