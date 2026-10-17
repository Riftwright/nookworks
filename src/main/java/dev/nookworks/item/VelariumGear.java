package dev.nookworks.item;

import java.util.Map;

import dev.nookworks.Nookworks;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

/**
 * Velarium gear: the one tier above netherite. Velarium only comes out of the mercury chain, so the gear is
 * crafted straight from ingots like iron gear is - no smithing template, no base item to upgrade.
 */
public final class VelariumGear {
    /** Ingots that repair Velarium tools. */
    public static final TagKey<Item> TOOL_MATERIALS = TagKey.create(Registries.ITEM, Nookworks.id("velarium_tool_materials"));
    /** Ingots that repair Velarium armour. */
    public static final TagKey<Item> ARMOR_MATERIALS = TagKey.create(Registries.ITEM, Nookworks.id("repairs_velarium_armor"));
    /** Points at assets/nookworks/equipment/velarium.json, which names the worn-armour textures. */
    public static final ResourceKey<EquipmentAsset> ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, Nookworks.id("velarium"));

    /** Netherite is 2031 / 9.0 / 4.0 / 15; Velarium beats it on every line. */
    public static final ToolMaterial TOOL = new ToolMaterial(
            BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 3400, 11.0F, 5.5F, 18, TOOL_MATERIALS);

    /** Netherite is 37 / 3,6,8,3,19 / 15 / 3.0F / 0.1F. */
    public static final ArmorMaterial ARMOR = new ArmorMaterial(50,
            Map.of(ArmorType.BOOTS, 4, ArmorType.LEGGINGS, 7, ArmorType.CHESTPLATE, 9, ArmorType.HELMET, 4, ArmorType.BODY, 20),
            18, SoundEvents.ARMOR_EQUIP_NETHERITE, 4.0F, 0.15F, ARMOR_MATERIALS, ASSET);

    private VelariumGear() {}
}
