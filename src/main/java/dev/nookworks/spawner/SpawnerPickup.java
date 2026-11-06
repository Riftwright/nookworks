package dev.nookworks.spawner;

import dev.nookworks.Nookworks;
import dev.nookworks.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

/**
 * A hammer with Silk Touch drops a mined spawner as an item that remembers its mob (a pickaxe still gets
 * nothing). No experience drops then, so placing and re-mining it cannot farm XP. The tools are
 * #nookworks:spawner_pickup_tools, which holds the hammers and which an add-on's tool can join without joining the
 * hammers' 3x3 (r21, 2026-11-06: nookTechnology's Heavy Hammer broke a spawner every hammer of ours picks up).
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class SpawnerPickup {
    @SubscribeEvent
    static void onDrops(BlockDropsEvent event) {
        if (!event.getState().is(Blocks.SPAWNER) || !(event.getBlockEntity() instanceof SpawnerBlockEntity spawner)
                || !canPickUp(event.getLevel(), event.getTool())) {
            return;
        }
        ItemStack stack = new ItemStack(Items.SPAWNER);
        EntityType<?> type = ControlledSpawner.of(spawner.getSpawner()).nookworks$entityType();
        if (type != null) {
            stack.set(ModDataComponents.SPAWNER_ENTITY.get(), type);
        }
        BlockPos pos = event.getPos();
        ServerLevel level = event.getLevel();
        ItemEntity drop = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
        drop.setDefaultPickUpDelay();
        event.getDrops().add(drop);
        event.setDroppedExperience(0);
    }

    public static final TagKey<Item> TOOLS = TagKey.create(Registries.ITEM, Nookworks.id("spawner_pickup_tools"));

    public static boolean canPickUp(ServerLevel level, ItemStack tool) {
        if (!tool.is(TOOLS)) {
            return false;
        }
        var silk = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH);
        return EnchantmentHelper.getItemEnchantmentLevel(silk, tool) > 0;
    }

    private SpawnerPickup() {}
}
