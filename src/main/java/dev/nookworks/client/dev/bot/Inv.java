package dev.nookworks.client.dev.bot;

import java.util.function.Predicate;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;

/**
 * The walker's inventory, handled as a player handles it: an item taken into the hand by choosing its hotbar slot, or
 * swapped into the hotbar from the rest of the inventory with a number key (the game's own SWAP click), blocks counted
 * for building, the best tool for a block, the best food, armour and weapon.
 */
final class Inv {
    private Inv() {}

    static Inventory of(LocalPlayer player) {
        return player.getInventory();
    }

    /** The inventory index (0-8 hotbar, 9-35 the rest) of the first stack that fits, hotbar and the held one first;
     *  -1 for none. */
    static int find(LocalPlayer player, Predicate<ItemStack> wanted) {
        Inventory inv = player.getInventory();
        int held = inv.getSelectedSlot();
        if (wanted.test(inv.getItem(held))) {
            return held;
        }
        for (int i = 0; i < 36; i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && wanted.test(stack)) {
                return i;
            }
        }
        return -1;
    }

    static int count(LocalPlayer player, Predicate<ItemStack> wanted) {
        Inventory inv = player.getInventory();
        int n = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && wanted.test(stack)) {
                n += stack.getCount();
            }
        }
        if (wanted.test(player.getOffhandItem())) {
            n += player.getOffhandItem().getCount();
        }
        return n;
    }

    /** The menu slot of the open container that shows inventory index {@code index}; -1 if it does not show it. */
    static int menuSlot(AbstractContainerMenu menu, Inventory inv, int index) {
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (slot.container == inv && slot.getContainerSlot() == index) {
                return i;
            }
        }
        return -1;
    }

    static void click(Minecraft mc, AbstractContainerMenu menu, int slot, int button, ContainerInput input) {
        mc.gameMode.handleContainerInput(menu.containerId, slot, button, input, mc.player);
    }

    /**
     * Takes inventory index {@code index} into the hand: chooses it on the hotbar, or swaps it onto the hotbar first (into
     * an empty slot, or the last one) as a number key over it does. False if it could not.
     */
    static boolean hold(Minecraft mc, int index) {
        LocalPlayer player = mc.player;
        Inventory inv = player.getInventory();
        if (index < 0) {
            return false;
        }
        if (Inventory.isHotbarSlot(index)) {
            select(mc, index);
            return true;
        }
        int target = -1;
        for (int i = 0; i < 9; i++) {
            if (inv.getItem(i).isEmpty()) {
                target = i;
                break;
            }
        }
        if (target < 0) {
            target = spareHotbarSlot(player);
        }
        AbstractContainerMenu menu = player.containerMenu;
        int slot = menuSlot(menu, inv, index);
        if (slot < 0) {
            return false;
        }
        click(mc, menu, slot, target, ContainerInput.SWAP);
        select(mc, target);
        return true;
    }

    /** The hotbar slot it gives up most easily: not a tool, not food, not its building blocks. */
    private static int spareHotbarSlot(LocalPlayer player) {
        Inventory inv = player.getInventory();
        for (int i = 8; i >= 0; i--) {
            ItemStack stack = inv.getItem(i);
            if (!stack.has(DataComponents.TOOL) && !stack.has(DataComponents.FOOD) && !stack.has(DataComponents.WEAPON)) {
                return i;
            }
        }
        return 8;
    }

    static void select(Minecraft mc, int hotbar) {
        LocalPlayer player = mc.player;
        if (player.getInventory().getSelectedSlot() != hotbar) {
            player.getInventory().setSelectedSlot(hotbar);
        }
    }

    /** Puts the item in the hand, from the creative inventory if the player is in creative and has none. */
    static boolean holdItem(Minecraft mc, Predicate<ItemStack> wanted, ItemStack creativeCopy) {
        int index = find(mc.player, wanted);
        if (index >= 0) {
            return hold(mc, index);
        }
        if (mc.player.getAbilities().instabuild && !creativeCopy.isEmpty()) {
            int slot = spareHotbarSlot(mc.player);
            for (int i = 0; i < 9; i++) {
                if (mc.player.getInventory().getItem(i).isEmpty()) {
                    slot = i;
                    break;
                }
            }
            ItemStack stack = creativeCopy.copyWithCount(creativeCopy.getMaxStackSize());
            mc.player.getInventory().setItem(slot, stack);
            mc.gameMode.handleCreativeModeItemAdd(stack, 36 + slot);
            select(mc, slot);
            return true;
        }
        return false;
    }

    // ---- what is what

    /** Blocks it builds bridges and pillars with: plain stuff dug up on the way, never anything someone made. */
    static boolean throwaway(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem block)) {
            return false;
        }
        BlockState state = block.getBlock().defaultBlockState();
        if (state.getBlock() instanceof FallingBlock) {
            return false;
        }
        return state.is(BlockTags.DIRT) && !state.is(net.minecraft.world.level.block.Blocks.MOSS_BLOCK)
                || state.is(Tags.Blocks.COBBLESTONES) || state.is(Tags.Blocks.NETHERRACKS)
                || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.BASE_STONE_NETHER)
                || state.is(Tags.Blocks.END_STONES);
    }

    static int throwawayCount(LocalPlayer player) {
        return player.getAbilities().instabuild ? 999 : count(player, Inv::throwaway);
    }

    /** Ticks it takes to break the block with the best tool in the inventory (the hand if none is better). */
    static double breakTicks(LocalPlayer player, BlockState state, BlockPos pos) {
        if (player.getAbilities().instabuild) {
            return 1.0;
        }
        float hardness = state.getDestroySpeed(player.level(), pos);
        if (hardness < 0) {
            return Double.POSITIVE_INFINITY;
        }
        if (hardness == 0) {
            return 1.0;
        }
        double best = Double.POSITIVE_INFINITY;
        Inventory inv = player.getInventory();
        for (int i = -1; i < 36; i++) {
            ItemStack stack = i < 0 ? ItemStack.EMPTY : inv.getItem(i);
            if (i >= 0 && (stack.isEmpty() || wornOut(stack))) {
                continue; // (one it will not use: counted, it planned stone at a pickaxe's pace and broke it by hand)
            }
            float speed = stack.isEmpty() ? 1.0F : stack.getDestroySpeed(state);
            boolean harvest = !state.requiresCorrectToolForDrops() || !stack.isEmpty() && stack.isCorrectToolForDrops(state);
            double perTick = speed / hardness / (harvest ? 30.0 : 100.0);
            best = Math.min(best, Math.ceil(1.0 / perTick));
        }
        return best;
    }

    /** The inventory index of the best tool for the block (or -1: the bare hand is as good). Only tools that harvest it
     *  when it needs one. */
    /** A tool down to its last uses: kept, not used (it would break in the hand). */
    static boolean wornOut(ItemStack stack) {
        return stack.isDamageableItem() && stack.getMaxDamage() - stack.getDamageValue() <= 2;
    }

    static int bestTool(LocalPlayer player, BlockState state) {
        Inventory inv = player.getInventory();
        int best = -1;
        float bestSpeed = 1.0F;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            float speed = stack.getDestroySpeed(state);
            boolean harvest = !state.requiresCorrectToolForDrops() || stack.isCorrectToolForDrops(state);
            if (harvest && !wornOut(stack) && speed > bestSpeed) {
                bestSpeed = speed;
                best = i;
            }
        }
        if (best < 0 && state.requiresCorrectToolForDrops()) {
            return -2; // nothing harvests it: breaking gives nothing
        }
        return best;
    }

    /** Whether the hand is empty of anything a block would be used with (for breaking by hand). */
    static int emptyHandSlot(LocalPlayer player) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty() || !stack.isDamageableItem() && !(stack.getItem() instanceof BlockItem)) {
                return i;
            }
        }
        return inv.getSelectedSlot();
    }

    static int nutrition(ItemStack stack) {
        FoodProperties food = stack.get(DataComponents.FOOD);
        return food == null ? 0 : food.nutrition();
    }

    /** Food that does harm (rotten flesh, a spider eye, a pufferfish...), eaten only when starving. */
    static boolean harmful(ItemStack stack) {
        return stack.is(Items.ROTTEN_FLESH) || stack.is(Items.SPIDER_EYE) || stack.is(Items.POISONOUS_POTATO)
                || stack.is(Items.PUFFERFISH) || stack.is(Items.CHICKEN) || stack.is(Items.SUSPICIOUS_STEW);
    }

    /** Food that may be eaten hungry or not (a golden apple). */
    static boolean alwaysEdible(ItemStack stack) {
        FoodProperties food = stack.get(DataComponents.FOOD);
        return food != null && food.canAlwaysEat();
    }

    /** Hurt: the food to heal with, -1 for none - the best food when hungry, else a golden apple (one that can always be
     *  eaten; the plain one before the enchanted). */
    static int healingFood(LocalPlayer player) {
        if (player.getFoodData().needsFood()) {
            int food = bestFood(player, false);
            if (food >= 0) {
                return food;
            }
        }
        int apple = find(player, s -> s.is(Items.GOLDEN_APPLE));
        return apple >= 0 ? apple : find(player, s -> s.is(Items.ENCHANTED_GOLDEN_APPLE));
    }

    /** The inventory index of the best food to eat now, -1 for none. */
    static int bestFood(LocalPlayer player, boolean starving) {
        Inventory inv = player.getInventory();
        int need = 20 - player.getFoodData().getFoodLevel();
        int best = -1;
        double bestScore = -Double.MAX_VALUE;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = inv.getItem(i);
            int n = nutrition(stack);
            if (n <= 0 || harmful(stack) && !starving || stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE)) {
                continue;
            }
            // as close to what is missing as possible, without much waste
            double score = Math.min(n, need) - Math.max(0, n - need) * 0.3;
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    static double armor(ItemStack stack) {
        ItemAttributeModifiers mods = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        double total = 0;
        for (ItemAttributeModifiers.Entry e : mods.modifiers()) {
            if (e.attribute().is(Attributes.ARMOR) || e.attribute().is(Attributes.ARMOR_TOUGHNESS)) {
                total += e.modifier().amount();
            }
        }
        return total;
    }

    static double damage(ItemStack stack) {
        ItemAttributeModifiers mods = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        double total = 1.0;
        for (ItemAttributeModifiers.Entry e : mods.modifiers()) {
            if (e.attribute().is(Attributes.ATTACK_DAMAGE)) {
                total += e.modifier().amount();
            }
        }
        return total;
    }

    /** The slot of the body armour or the like goes in, or null if it is not worn. */
    static EquipmentSlot wornIn(ItemStack stack) {
        Equippable eq = stack.get(DataComponents.EQUIPPABLE);
        return eq == null ? null : eq.slot();
    }

    /** The inventory index of the best weapon (sword, axe...), -1 for the hand. */
    static int bestWeapon(LocalPlayer player) {
        Inventory inv = player.getInventory();
        int best = -1;
        double bestDamage = 1.0;
        for (int i = 0; i < 36; i++) {
            double d = damage(inv.getItem(i));
            if (d > bestDamage + 1e-6) {
                bestDamage = d;
                best = i;
            }
        }
        return best;
    }
}
