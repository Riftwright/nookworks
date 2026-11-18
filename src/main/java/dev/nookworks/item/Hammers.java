package dev.nookworks.item;

import dev.nookworks.Nookworks;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.SimpleTier;

/**
 * Hammer balance. A hammer is a pickaxe-class tool that mines 3x3 (see feature/HammerMining), so it trades
 * speed for area: it digs at {@link #SPEED_FACTOR} of the matching pickaxe's speed and has
 * {@link #DURABILITY_FACTOR} times its durability, because every swing can cost up to nine points.
 * <p>
 * 1.21.1: a tool is an item class over a {@code Tier} (26.3: any item, given the tool's properties), so a hammer is a
 * {@link DiggerItem} of the pickaxe's blocks - durability, speed, drops, enchantability and repair from its tier.
 */
public final class Hammers {
    public static final TagKey<Item> TAG = TagKey.create(Registries.ITEM, Nookworks.id("hammers"));

    static final float SPEED_FACTOR = 0.4F;
    static final int DURABILITY_FACTOR = 4;
    /** Heavy, slow swing: more damage than a pickaxe, attacks like an axe. */
    public static final float ATTACK_DAMAGE = 4.0F;
    public static final float ATTACK_SPEED = -3.3F;

    /**
     * 26.3's copper tool material, which 1.21.1 lacks (it has no copper tools), for the Copper Hammer: 190 uses, speed
     * 5.0, +1.0 damage, enchantability 13, mending with copper ingots (26.3's copper tool materials), and stone's
     * reach (26.3's incorrect_for_copper_tool holds what incorrect_for_stone_tool does).
     */
    public static final Tier COPPER = new SimpleTier(BlockTags.INCORRECT_FOR_STONE_TOOL, 190, 5.0F, 1.0F, 13,
            () -> Ingredient.of(Items.COPPER_INGOT));

    public static Tier material(Tier base) {
        return new SimpleTier(base.getIncorrectBlocksForDrops(), base.getUses() * DURABILITY_FACTOR,
                base.getSpeed() * SPEED_FACTOR, base.getAttackDamageBonus(), base.getEnchantmentValue(), base::getRepairIngredient);
    }

    public static Item.Properties properties(Item.Properties p, Tier base) {
        return p.attributes(DiggerItem.createAttributes(material(base), ATTACK_DAMAGE, ATTACK_SPEED));
    }

    /** A hammer of {@code base}'s kind: 26.3's {@code new Item(properties(p, base))}. */
    public static Item item(Item.Properties p, Tier base) {
        return new DiggerItem(material(base), BlockTags.MINEABLE_WITH_PICKAXE, properties(p, base));
    }

    private Hammers() {}
}
