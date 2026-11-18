package dev.nookworks.item;

import java.util.List;
import java.util.Map;

import dev.nookworks.Nookworks;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.SimpleTier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Velarium gear: the one tier above netherite. Velarium only comes out of the mercury chain, so the gear is
 * crafted straight from ingots like iron gear is - no smithing template, no base item to upgrade.
 */
public final class VelariumGear {
    /** Ingots that repair Velarium tools. */
    public static final TagKey<Item> TOOL_MATERIALS = TagKey.create(Registries.ITEM, Nookworks.id("velarium_tool_materials"));
    /** Ingots that repair Velarium armour. */
    public static final TagKey<Item> ARMOR_MATERIALS = TagKey.create(Registries.ITEM, Nookworks.id("repairs_velarium_armor"));

    /** Netherite is 2031 / 9.0 / 4.0 / 15; Velarium beats it on every line. */
    public static final Tier TOOL = new SimpleTier(
            BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 3400, 11.0F, 5.5F, 18, () -> Ingredient.of(TOOL_MATERIALS));

    /**
     * 26.3's armour material carries its durability (50, times each piece's own: 550 / 800 / 750 / 650) and names its
     * worn look by an equipment asset (assets/nookworks/equipment/velarium.json); 1.21.1's is a registered material
     * without the durability, which goes on each item's properties, and names its two layer textures,
     * textures/models/armor/velarium_layer_1.png and _layer_2.png (the same pictures).
     */
    public static final int ARMOR_DURABILITY = 50;
    public static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, Nookworks.MODID);

    /** Netherite is 37 / 3,6,8,3,19 / 15 / 3.0F / 0.1F. */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> ARMOR = MATERIALS.register("velarium", () -> new ArmorMaterial(
            Map.of(ArmorItem.Type.BOOTS, 4, ArmorItem.Type.LEGGINGS, 7, ArmorItem.Type.CHESTPLATE, 9, ArmorItem.Type.HELMET, 4,
                    ArmorItem.Type.BODY, 20),
            18, SoundEvents.ARMOR_EQUIP_NETHERITE, () -> Ingredient.of(ARMOR_MATERIALS),
            List.of(new ArmorMaterial.Layer(Nookworks.id("velarium"))), 4.0F, 0.15F));

    private VelariumGear() {}
}
