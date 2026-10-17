package dev.nookworks.item;

import dev.nookworks.Nookworks;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;

/**
 * Hammer balance. A hammer is a pickaxe-class tool that mines 3x3 (see feature/HammerMining), so it trades
 * speed for area: it digs at {@link #SPEED_FACTOR} of the matching pickaxe's speed and has
 * {@link #DURABILITY_FACTOR} times its durability, because every swing can cost up to nine points.
 */
public final class Hammers {
    public static final TagKey<Item> TAG = TagKey.create(Registries.ITEM, Nookworks.id("hammers"));

    static final float SPEED_FACTOR = 0.4F;
    static final int DURABILITY_FACTOR = 4;
    /** Heavy, slow swing: more damage than a pickaxe, attacks like an axe. */
    public static final float ATTACK_DAMAGE = 4.0F;
    public static final float ATTACK_SPEED = -3.3F;

    public static ToolMaterial material(ToolMaterial base) {
        return new ToolMaterial(base.incorrectBlocksForDrops(), base.durability() * DURABILITY_FACTOR,
                base.speed() * SPEED_FACTOR, base.attackDamageBonus(), base.enchantmentValue(), base.repairItems());
    }

    public static Item.Properties properties(Item.Properties p, ToolMaterial base) {
        return p.tool(material(base), BlockTags.MINEABLE_WITH_PICKAXE, ATTACK_DAMAGE, ATTACK_SPEED, 0.0F);
    }

    private Hammers() {}
}
