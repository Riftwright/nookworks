package dev.nookworks.item;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import dev.nookworks.registry.ModItems;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.OptionalDispenseItemBehavior;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Fine Shears (上質なはさみ; the id stays wool_shears): spring shears with brass handles, made for wool. Vanilla's shears cut a sheep's lead before its wool
 * (since 1.21.6), so a sheep kept on a lead had to be let loose to be shorn; these shear it and leave the lead as it is,
 * held by a player or tied to a post. They give a little more, too: each shearing rolls once for a wool more, at one in
 * {@link #BONUS_ODDS}, and Fortune rolls again for each level. A dispenser holding them shears a sheep in front of it,
 * leads left alone. Used by hand on a vine they cut it, and it stops spreading ({@link dev.nookworks.feature.TrimmedVines};
 * theirs alone since 2026-10-06, the dispenser leaving vines be). By hand they trim the tip of a cave vine, a weeping or
 * twisting vine or kelp, as vanilla's shears do, and it grows no further (the user, 2026-10-06). They cut nothing else:
 * no leads, no leaves, no other mob.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public class WoolShearsItem extends Item {
    /** Each roll for a wool more comes up one time in this many. */
    public static final int BONUS_ODDS = 3;

    public WoolShearsItem(Properties properties) {
        super(properties);
    }

    /** Trimming a growing plant's tip, as vanilla's shears do - and nothing else of theirs (no leaves, no cobwebs). */
    @Override
    public boolean canPerformAction(ItemInstance stack, ItemAbility ability) {
        return ability == ItemAbilities.SHEARS_TRIM;
    }

    /** A cave vine's, a weeping or twisting vine's or kelp's tip clicked: trimmed, so it grows no further, as vanilla's shears do. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState trimmed = level.getBlockState(pos).getToolModifiedState(context, ItemAbilities.SHEARS_TRIM, false);
        if (trimmed == null) {
            return super.useOn(context);
        }
        Player player = context.getPlayer();
        if (player instanceof ServerPlayer server) {
            CriteriaTriggers.ITEM_USED_ON_BLOCK.trigger(server, pos, context.getItemInHand());
        }
        level.setBlockAndUpdate(pos, trimmed);
        level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, trimmed));
        if (player != null) {
            context.getItemInHand().hurtAndBreak(1, player, context.getHand().asEquipmentSlot());
        }
        return InteractionResult.SUCCESS;
    }

    /** Fortune goes on them, by anvil and at the enchanting table, besides what any tool with durability takes. */
    @Override
    public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
        return enchantment.is(Enchantments.FORTUNE) || super.supportsEnchantment(stack, enchantment);
    }

    @Override
    public boolean isPrimaryItemFor(ItemStack stack, Holder<Enchantment> enchantment) {
        return enchantment.is(Enchantments.FORTUNE) || super.isPrimaryItemFor(stack, enchantment);
    }

    /** How many wool more a shearing gives with {@code fortune}: one roll, and one more a level, each at one in {@link #BONUS_ODDS}. */
    public static int bonus(RandomSource random, int fortune) {
        int more = 0;
        for (int roll = 0; roll <= fortune; roll++) {
            if (random.nextInt(BONUS_ODDS) == 0) {
                more++;
            }
        }
        return more;
    }

    /**
     * Shears {@code sheep} with {@code shears} as vanilla does - its own wool, by its shearing loot table - and the wool
     * more on top, dropped as vanilla drops wool. Whatever it is leashed by is left alone. False if it has no wool to
     * give (shorn already, or a lamb). The shears are not worn here: whoever uses them does that.
     */
    public static boolean shear(ServerLevel level, Sheep sheep, @Nullable Player player, ItemStack shears) {
        BlockPos pos = sheep.blockPosition();
        if (!sheep.isShearable(player, shears, level, pos)) {
            return false;
        }
        List<ItemStack> drops = new ArrayList<>(sheep.onSheared(player, shears, level, pos));
        if (!drops.isEmpty()) {
            var fortune = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FORTUNE);
            int more = bonus(sheep.getRandom(), EnchantmentHelper.getItemEnchantmentLevel(fortune, shears));
            for (int i = 0; i < more; i++) {
                drops.add(drops.getFirst().copyWithCount(1));
            }
        }
        for (ItemStack drop : drops) {
            sheep.spawnShearedDrop(level, pos, drop);
        }
        sheep.gameEvent(GameEvent.SHEAR, player);
        return true;
    }

    /**
     * A click on a sheep with them, before the game's own handling of the click: that would cut a lead the sheep is tied
     * by, or untie it from the player leading it, before its wool was looked at. A sheep with no wool is left to the game.
     */
    // low, as the Mob Bottle's capture is: the shearing is done in here, so a claim mod listening at the usual priority
    // is heard first
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOW)
    static void onInteract(PlayerInteractEvent.EntityInteract event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof WoolShearsItem) || !(event.getTarget() instanceof Sheep sheep)) {
            return;
        }
        Player player = event.getEntity();
        if (!sheep.isShearable(player, stack, event.getLevel(), sheep.blockPosition())) {
            return;
        }
        event.setCanceled(true);
        if (event.getLevel() instanceof ServerLevel level) {
            shear(level, sheep, player, stack);
            stack.hurtAndBreak(1, player, event.getHand());
            event.setCancellationResult(InteractionResult.SUCCESS_SERVER);
        } else {
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    @SubscribeEvent
    static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> DispenserBlock.registerBehavior(ModItems.WOOL_SHEARS.get(), new Dispense()));
    }

    /** From a dispenser: the first sheep with wool in the block in front of it, shorn, its lead left on. */
    static final class Dispense extends OptionalDispenseItemBehavior {
        @Override
        protected ItemStack execute(BlockSource source, ItemStack stack) {
            ServerLevel level = source.level();
            Direction facing = source.state().getValue(DispenserBlock.FACING);
            this.setSuccess(false);
            for (Sheep sheep : level.getEntitiesOfClass(Sheep.class, new AABB(source.pos().relative(facing)))) {
                if (shear(level, sheep, null, stack)) {
                    stack.hurtAndBreak(1, level, null, item -> {});
                    this.setSuccess(true);
                    break;
                }
            }
            return stack;
        }
    }
}
