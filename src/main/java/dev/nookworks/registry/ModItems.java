package dev.nookworks.registry;

import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import dev.nookworks.item.WoolShearsItem;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import dev.nookworks.Nookworks;
import dev.nookworks.block.StorageBlock;
import dev.nookworks.dowsing.NookDowsingRodItem;
import dev.nookworks.experience.ExperienceFluids;
import dev.nookworks.fluid.CopperTankItem;
import dev.nookworks.item.BuildingWandItem;
import dev.nookworks.item.CrateItem;
import dev.nookworks.item.CreativeOnlyBlockItem;
import dev.nookworks.item.DiceItem;
import dev.nookworks.item.FireproofCoatingItem;
import dev.nookworks.item.Hammers;
import dev.nookworks.item.HorizontalItemWarpReceiverItem;
import dev.nookworks.item.MagnetItem;
import dev.nookworks.item.VelariumGear;
import dev.nookworks.item.WrenchItem;
import dev.nookworks.label.LabelItem;
import dev.nookworks.mercury.MercuryBucketItem;
import dev.nookworks.mercury.MercuryFluids;
import dev.nookworks.mercury.MercuryTankItem;
import dev.nookworks.mobcapture.MobBottleItem;
import dev.nookworks.pearl.LodePearlItem;
import dev.nookworks.photo.CameraItem;
import dev.nookworks.poster.PosterItem;
import dev.nookworks.rope.ThinRopeItem;
import dev.nookworks.sorter.LegacySorterItem;
import dev.nookworks.sorter.SorterGear;
import dev.nookworks.sorter.SorterGearItem;
import dev.nookworks.spawner.SpawnerLinkReceiverItem;
import dev.nookworks.storage.StorageItem;
import dev.nookworks.storage.StorageTier;
import dev.nookworks.storage.StorageUpgradeItem;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Nookworks.MODID);

    public static final DeferredItem<BlockItem> ROPE = ITEMS.registerSimpleBlockItem(ModBlocks.ROPE);
    public static final DeferredItem<ThinRopeItem> THIN_ROPE = ITEMS.registerItem("thin_rope",
            p -> new ThinRopeItem(ModBlocks.THIN_ROPE.get(), p), p -> p.useBlockDescriptionPrefix());

    public static final DeferredItem<BlockItem> OIL_LAMP = ITEMS.registerSimpleBlockItem(ModBlocks.OIL_LAMP);
    public static final DeferredItem<BlockItem> BOOKEND = ITEMS.registerSimpleBlockItem(ModBlocks.BOOKEND);
    public static final DeferredItem<BlockItem> UNLIT_LANTERN = ITEMS.registerSimpleBlockItem(ModBlocks.UNLIT_LANTERN);
    public static final DeferredItem<BlockItem> UNLIT_SOUL_LANTERN = ITEMS.registerSimpleBlockItem(ModBlocks.UNLIT_SOUL_LANTERN);
    /** The unlit copper lanterns' items, by the lit one's id, in vanilla's order (four weathering, then four waxed). */
    public static final Map<String, DeferredItem<BlockItem>> UNLIT_COPPER_LANTERNS = unlitCopperLanterns();

    private static Map<String, DeferredItem<BlockItem>> unlitCopperLanterns() {
        Map<String, DeferredItem<BlockItem>> items = new LinkedHashMap<>();
        ModBlocks.UNLIT_COPPER_LANTERNS.forEach((lit, block) -> items.put(lit, ITEMS.registerSimpleBlockItem(block)));
        return items;
    }
    public static final DeferredItem<StandingAndWallBlockItem> SCONCE = ITEMS.registerItem("sconce",
            p -> new StandingAndWallBlockItem(ModBlocks.SCONCE.get(), ModBlocks.WALL_SCONCE.get(), Direction.DOWN, p),
            p -> p.useBlockDescriptionPrefix());

    public static final DeferredItem<BlockItem> IRON_WORKBENCH = ITEMS.registerSimpleBlockItem(ModBlocks.IRON_WORKBENCH);
    public static final DeferredItem<BlockItem> BRASS_WORKBENCH = ITEMS.registerSimpleBlockItem(ModBlocks.BRASS_WORKBENCH);
    public static final DeferredItem<CrateItem> CRATE = ITEMS.registerItem("crate",
            p -> new CrateItem(ModBlocks.CRATE.get(), p),
            p -> p.useBlockDescriptionPrefix().stacksTo(1));

    public static final DeferredItem<BlockItem> ELEVATOR = ITEMS.registerSimpleBlockItem(ModBlocks.ELEVATOR);

    public static final DeferredItem<BlockItem> ITEM_WARP_SENDER = ITEMS.registerSimpleBlockItem(ModBlocks.ITEM_WARP_SENDER);
    public static final DeferredItem<BlockItem> ITEM_WARP_RECEIVER = ITEMS.registerSimpleBlockItem(ModBlocks.ITEM_WARP_RECEIVER);
    /** The Horizontal Item Warps (ids {@code horizontal_item_warp_sender} / {@code _receiver}). */
    public static final DeferredItem<BlockItem> HORIZONTAL_ITEM_WARP_SENDER = ITEMS.registerSimpleBlockItem(ModBlocks.HORIZONTAL_ITEM_WARP_SENDER);
    public static final DeferredItem<HorizontalItemWarpReceiverItem> HORIZONTAL_ITEM_WARP_RECEIVER = ITEMS.registerItem("horizontal_item_warp_receiver",
            p -> new HorizontalItemWarpReceiverItem(ModBlocks.HORIZONTAL_ITEM_WARP_RECEIVER.get(), p), p -> p.useBlockDescriptionPrefix());

    public static final DeferredItem<BlockItem> SIFTER = ITEMS.registerSimpleBlockItem(ModBlocks.SIFTER);

    public static final DeferredItem<FireproofCoatingItem> FIREPROOF_COATING = ITEMS.registerItem("fireproof_coating",
            FireproofCoatingItem::new, p -> p.durability(64));

    public static final DeferredItem<BlockItem> GENERATOR = ITEMS.registerSimpleBlockItem(ModBlocks.GENERATOR);
    public static final DeferredItem<BlockItem> DIESEL_GENERATOR = ITEMS.registerSimpleBlockItem(ModBlocks.DIESEL_GENERATOR);
    /** Creative mode's: its name in purple, as vanilla's command block's; only a creative player sets it down. */
    public static final DeferredItem<CreativeOnlyBlockItem> CREATIVE_GENERATOR = ITEMS.registerItem("creative_generator",
            p -> new CreativeOnlyBlockItem(ModBlocks.CREATIVE_GENERATOR.get(), p), p -> p.useBlockDescriptionPrefix().rarity(Rarity.EPIC));
    public static final DeferredItem<BlockItem> CRUSHER = ITEMS.registerSimpleBlockItem(ModBlocks.CRUSHER);
    public static final DeferredItem<BlockItem> STONE_MAKER = ITEMS.registerSimpleBlockItem(ModBlocks.STONE_MAKER);
    public static final DeferredItem<BlockItem> ELECTRIC_FURNACE = ITEMS.registerSimpleBlockItem(ModBlocks.ELECTRIC_FURNACE);
    public static final DeferredItem<BlockItem> ENERGY_CABLE = ITEMS.registerSimpleBlockItem(ModBlocks.ENERGY_CABLE);

    public static final DeferredItem<BlockItem> ITEM_SORTER = ITEMS.registerSimpleBlockItem(ModBlocks.ITEM_SORTER);
    public static final DeferredItem<BlockItem> PRECISION_SORTER = ITEMS.registerSimpleBlockItem(ModBlocks.PRECISION_SORTER);
    // 0.2.0's, as they may still be in chests: set down, each becomes an Item Sorter with its gear.
    public static final DeferredItem<LegacySorterItem> GOLD_SORTER = ITEMS.registerItem("gold_sorter",
            p -> new LegacySorterItem(ModBlocks.GOLD_SORTER.get(), p), p -> p.useBlockDescriptionPrefix());
    public static final DeferredItem<LegacySorterItem> DIAMOND_SORTER = ITEMS.registerItem("diamond_sorter",
            p -> new LegacySorterItem(ModBlocks.DIAMOND_SORTER.get(), p), p -> p.useBlockDescriptionPrefix());
    public static final DeferredItem<LegacySorterItem> NETHERITE_SORTER = ITEMS.registerItem("netherite_sorter",
            p -> new LegacySorterItem(ModBlocks.NETHERITE_SORTER.get(), p), p -> p.useBlockDescriptionPrefix().fireResistant());
    /** Sorter Gears: fitted into either sorter's gear slot, it works faster. */
    public static final DeferredItem<SorterGearItem> GOLD_SORTER_GEAR = sorterGear("gold_sorter_gear",
            SorterGear.GOLD, false);
    public static final DeferredItem<SorterGearItem> DIAMOND_SORTER_GEAR = sorterGear("diamond_sorter_gear",
            SorterGear.DIAMOND, false);
    public static final DeferredItem<SorterGearItem> NETHERITE_SORTER_GEAR = sorterGear("netherite_sorter_gear",
            SorterGear.NETHERITE, true);
    public static final DeferredItem<SorterGearItem> VELARIUM_SORTER_GEAR = sorterGear("velarium_sorter_gear",
            SorterGear.VELARIUM, true);

    public static final DeferredItem<StorageItem> IRON_STORAGE = storage("iron_storage_barrel", ModBlocks.IRON_STORAGE);
    public static final DeferredItem<StorageItem> GOLD_STORAGE = storage("gold_storage_barrel", ModBlocks.GOLD_STORAGE);
    public static final DeferredItem<StorageItem> DIAMOND_STORAGE = storage("diamond_storage_barrel", ModBlocks.DIAMOND_STORAGE);
    public static final DeferredItem<StorageItem> NETHERITE_STORAGE = storage("netherite_storage_barrel", ModBlocks.NETHERITE_STORAGE);
    public static final DeferredItem<StorageItem> VELARIUM_STORAGE = storage("velarium_storage_barrel", ModBlocks.VELARIUM_STORAGE);
    public static final DeferredItem<StorageUpgradeItem> IRON_STORAGE_UPGRADE =
            storageUpgrade("iron_storage_upgrade", StorageTier.IRON, ModBlocks.IRON_STORAGE, false);
    public static final DeferredItem<StorageUpgradeItem> GOLD_STORAGE_UPGRADE =
            storageUpgrade("gold_storage_upgrade", StorageTier.GOLD, ModBlocks.GOLD_STORAGE, false);
    public static final DeferredItem<StorageUpgradeItem> DIAMOND_STORAGE_UPGRADE =
            storageUpgrade("diamond_storage_upgrade", StorageTier.DIAMOND, ModBlocks.DIAMOND_STORAGE, false);
    public static final DeferredItem<StorageUpgradeItem> NETHERITE_STORAGE_UPGRADE =
            storageUpgrade("netherite_storage_upgrade", StorageTier.NETHERITE, ModBlocks.NETHERITE_STORAGE, true);
    public static final DeferredItem<StorageUpgradeItem> VELARIUM_STORAGE_UPGRADE =
            storageUpgrade("velarium_storage_upgrade", StorageTier.VELARIUM, ModBlocks.VELARIUM_STORAGE, true);

    public static final DeferredItem<BlockItem> SPAWNER_LINK_SENDER = ITEMS.registerSimpleBlockItem(ModBlocks.SPAWNER_LINK_SENDER);
    public static final DeferredItem<SpawnerLinkReceiverItem> SPAWNER_LINK_RECEIVER = ITEMS.registerItem("spawner_link_receiver",
            p -> new SpawnerLinkReceiverItem(ModBlocks.SPAWNER_LINK_RECEIVER.get(), p), p -> p.useBlockDescriptionPrefix());
    public static final DeferredItem<BlockItem> SPAWNER_CONTROLLER = ITEMS.registerSimpleBlockItem(ModBlocks.SPAWNER_CONTROLLER);

    // ---- Mercury ----
    public static final DeferredItem<MercuryBucketItem> MERCURY_BUCKET = ITEMS.registerItem("mercury_bucket",
            p -> new MercuryBucketItem(MercuryFluids.SOURCE.get(), p),
            p -> p.craftRemainder(Items.BUCKET).stacksTo(1));
    public static final DeferredItem<BlockItem> MERCURY_TANK = ITEMS.registerItem("mercury_tank",
            p -> new MercuryTankItem(ModBlocks.MERCURY_TANK.get(), p), p -> p.useBlockDescriptionPrefix().stacksTo(1));
    public static final DeferredItem<BlockItem> MERCURY_PIPE = ITEMS.registerSimpleBlockItem(ModBlocks.MERCURY_PIPE);
    public static final DeferredItem<BlockItem> RETORT = ITEMS.registerSimpleBlockItem(ModBlocks.RETORT);
    public static final DeferredItem<BlockItem> STIRRING_TANK = ITEMS.registerSimpleBlockItem(ModBlocks.STIRRING_TANK);
    public static final DeferredItem<BlockItem> ELECTROLYTIC_CELL = ITEMS.registerSimpleBlockItem(ModBlocks.ELECTROLYTIC_CELL);
    public static final DeferredItem<BlockItem> DISTILLER = ITEMS.registerSimpleBlockItem(ModBlocks.DISTILLER);
    public static final DeferredItem<BlockItem> DISTILLATION_COLUMN = ITEMS.registerSimpleBlockItem(ModBlocks.DISTILLATION_COLUMN);
    public static final DeferredItem<BlockItem> CONDENSER = ITEMS.registerSimpleBlockItem(ModBlocks.CONDENSER);
    public static final DeferredItem<Item> SULFUR_DUST = ITEMS.registerSimpleItem("sulfur_dust");
    public static final DeferredItem<Item> IRON_AMALGAM = ITEMS.registerSimpleItem("iron_amalgam");
    public static final DeferredItem<Item> GOLD_AMALGAM = ITEMS.registerSimpleItem("gold_amalgam");
    public static final DeferredItem<Item> COPPER_AMALGAM = ITEMS.registerSimpleItem("copper_amalgam");
    /** Mixed Amalgam (id {@code mixed_amalgam}): the traces of several metals at once. */
    public static final DeferredItem<Item> TRACE_AMALGAM = ITEMS.registerSimpleItem("mixed_amalgam");
    public static final DeferredItem<BlockItem> VELARIUM_BLOCK = ITEMS.registerSimpleBlockItem(ModBlocks.VELARIUM_BLOCK, p -> p.fireResistant());
    public static final DeferredItem<Item> VELARIUM_INGOT = ITEMS.registerSimpleItem("velarium_ingot", p -> p.fireResistant());
    public static final DeferredItem<Item> VELARIUM_NUGGET = ITEMS.registerSimpleItem("velarium_nugget", p -> p.fireResistant());
    /** A Velarium Ingot out of the fire, one at a time: too hot to hold, quenched in oil within half a minute (dev.nookworks.quench). */
    public static final DeferredItem<dev.nookworks.quench.RedHotIngotItem> RED_HOT_VELARIUM_INGOT = ITEMS.registerItem("red_hot_velarium_ingot",
            dev.nookworks.quench.RedHotIngotItem::new, p -> p.fireResistant().stacksTo(1));
    /** Quenched in oil: what Velarium tools and armour are made and mended of. */
    public static final DeferredItem<Item> TEMPERED_VELARIUM_INGOT = ITEMS.registerSimpleItem("tempered_velarium_ingot", p -> p.fireResistant());
    /** A breeze rod sheathed in brass: the handle of Velarium tools, a wooden stick being no match for their heads. */
    public static final DeferredItem<Item> REINFORCED_ROD = ITEMS.registerSimpleItem("reinforced_rod");

    // ---- Machine parts: the chassis, and brass (copper alloyed with zinc in the Retort) ----
    public static final DeferredItem<BlockItem> MACHINE_CHASSIS = ITEMS.registerSimpleBlockItem(ModBlocks.MACHINE_CHASSIS);
    public static final DeferredItem<Item> RAW_ZINC = ITEMS.registerSimpleItem("raw_zinc");
    public static final DeferredItem<Item> ZINC_INGOT = ITEMS.registerSimpleItem("zinc_ingot");
    public static final DeferredItem<Item> BRASS_INGOT = ITEMS.registerSimpleItem("brass_ingot");
    /** Fitted into the Sifter: slower, but it keeps the small things. See SifterBlockEntity. */
    public static final DeferredItem<Item> BRASS_MESH = ITEMS.registerSimpleItem("brass_mesh", p -> p.stacksTo(1));
    /** Points the way to the sulfur caves, where cinnabar is: see {@link NookDowsingRodItem}. */
    public static final DeferredItem<NookDowsingRodItem> NOOK_DOWSING_ROD = ITEMS.registerItem("nook_dowsing_rod",
            NookDowsingRodItem::new, p -> p.durability(64).repairable(Items.COPPER_INGOT).useCooldown(3.0F));
    /** Takes photos to print as posters: see {@link CameraItem}. */
    public static final DeferredItem<CameraItem> CAMERA = ITEMS.registerItem("camera", CameraItem::new, p -> p.stacksTo(1));
    /** Shears a sheep and leaves its lead alone, now and then a wool more; cuts a vine so it stops spreading: see {@link WoolShearsItem}. */
    public static final DeferredItem<WoolShearsItem> WOOL_SHEARS = ITEMS.registerItem("wool_shears", WoolShearsItem::new,
            p -> p.durability(320).repairable(TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "ingots/brass"))).enchantable(10));
    /** An ender pearl bound to a lodestone: thrown, it takes you there. Cools down as an ender pearl does. */
    public static final DeferredItem<LodePearlItem> LODE_PEARL = ITEMS.registerItem("lode_pearl",
            LodePearlItem::new, p -> p.stacksTo(16).useCooldown(1.0F));

    // ---- Chlor-alkali and pulp (the retort boils brine, the tank and distiller run the mercury cell) ----
    public static final DeferredItem<Item> CRUDE_SALT = ITEMS.registerSimpleItem("crude_salt");
    public static final DeferredItem<Item> SALT = ITEMS.registerSimpleItem("salt");
    public static final DeferredItem<Item> CAUSTIC_SODA = ITEMS.registerSimpleItem("caustic_soda");
    public static final DeferredItem<Item> WOOD_CHIPS = ITEMS.registerSimpleItem("wood_chips");
    public static final DeferredItem<Item> WOOD_PULP = ITEMS.registerSimpleItem("wood_pulp");
    public static final DeferredItem<Item> FINE_PAPER = ITEMS.registerSimpleItem("fine_paper");

    public static final DeferredItem<BlockItem> PRINTING_PRESS = ITEMS.registerSimpleBlockItem(ModBlocks.PRINTING_PRESS);

    // ---- Liquid Experience ----
    public static final DeferredItem<BucketItem> EXPERIENCE_BUCKET = ITEMS.registerItem("experience_bucket",
            p -> new BucketItem(ExperienceFluids.SOURCE.get(), p),
            p -> p.craftRemainder(Items.BUCKET).stacksTo(1));
    public static final DeferredItem<BlockItem> EXPERIENCE_TANK = ITEMS.registerSimpleBlockItem(ModBlocks.EXPERIENCE_TANK);
    /** 250 mB of Liquid Experience; the Enchanting Table makes a Bottle o' Enchanting of it (EnchantmentMenuMixin). */
    public static final DeferredItem<Item> LIQUID_EXPERIENCE_BOTTLE = ITEMS.registerSimpleItem("liquid_experience_bottle",
            p -> p.craftRemainder(Items.GLASS_BOTTLE).stacksTo(16));
    public static final DeferredItem<BlockItem> EXPERIENCE_PIPE = ITEMS.registerSimpleBlockItem(ModBlocks.EXPERIENCE_PIPE);
    public static final DeferredItem<BlockItem> DEEPSLATE_GRAVEL = ITEMS.registerSimpleBlockItem(ModBlocks.DEEPSLATE_GRAVEL);
    public static final DeferredItem<BlockItem> NETHER_GRIT = ITEMS.registerSimpleBlockItem(ModBlocks.NETHER_GRIT);
    public static final DeferredItem<BlockItem> END_GRAVEL = ITEMS.registerSimpleBlockItem(ModBlocks.END_GRAVEL);
    public static final DeferredItem<BlockItem> STONY_DIRT = ITEMS.registerSimpleBlockItem(ModBlocks.STONY_DIRT);
    public static final DeferredItem<BlockItem> PEBBLES = ITEMS.registerSimpleBlockItem(ModBlocks.PEBBLES);
    public static final DeferredItem<BlockItem> WATER_PUMP = ITEMS.registerSimpleBlockItem(ModBlocks.WATER_PUMP);
    public static final DeferredItem<BlockItem> COPPER_PIPE = ITEMS.registerSimpleBlockItem(ModBlocks.COPPER_PIPE);
    public static final DeferredItem<BlockItem> SIGHT_GLASS = ITEMS.registerSimpleBlockItem(ModBlocks.SIGHT_GLASS);
    public static final DeferredItem<BlockItem> VALVE = ITEMS.registerSimpleBlockItem(ModBlocks.VALVE);
    public static final DeferredItem<BlockItem> COPPER_TANK = ITEMS.registerItem("copper_tank",
            p -> new CopperTankItem(ModBlocks.COPPER_TANK.get(), p), p -> p.useBlockDescriptionPrefix());
    public static final DeferredItem<BlockItem> ABSORPTION_PAD = ITEMS.registerSimpleBlockItem(ModBlocks.ABSORPTION_PAD);

    public static final DeferredItem<BlockItem> REPAIR_BOX = ITEMS.registerSimpleBlockItem(ModBlocks.REPAIR_BOX);
    public static final DeferredItem<BlockItem> TRASH_CAN = ITEMS.registerSimpleBlockItem(ModBlocks.TRASH_CAN);
    public static final DeferredItem<BlockItem> DARK_MATTER = ITEMS.registerSimpleBlockItem(ModBlocks.DARK_MATTER);
    /** Cooked food cooked again: what Dark Matter is made of. */
    public static final DeferredItem<Item> CHARRED_LUMP = ITEMS.registerSimpleItem("charred_lump");

    public static final DeferredItem<WrenchItem> WRENCH = ITEMS.registerItem("wrench",
            WrenchItem::new, p -> p.durability(512));
    public static final DeferredItem<BuildingWandItem> BUILDING_WAND = ITEMS.registerItem("building_wand",
            BuildingWandItem::new, p -> p.durability(1024));

    public static final DeferredItem<PosterItem> POSTER = ITEMS.registerItem("poster",
            PosterItem::new, p -> p.stacksTo(16));
    public static final DeferredItem<LabelItem> LABEL = ITEMS.registerItem("label", LabelItem::new);

    // ---- Velarium gear (crafted from ingots like iron gear, no smithing template) ----
    public static final DeferredItem<Item> VELARIUM_SWORD = ITEMS.registerItem("velarium_sword", Item::new,
            p -> p.sword(VelariumGear.TOOL, 3.0F, -2.4F).fireResistant());
    public static final DeferredItem<Item> VELARIUM_PICKAXE = ITEMS.registerItem("velarium_pickaxe", Item::new,
            p -> p.pickaxe(VelariumGear.TOOL, 1.0F, -2.8F).fireResistant());
    public static final DeferredItem<Item> VELARIUM_AXE = ITEMS.registerItem("velarium_axe", Item::new,
            p -> p.axe(VelariumGear.TOOL, 5.0F, -3.0F).fireResistant());
    public static final DeferredItem<Item> VELARIUM_SHOVEL = ITEMS.registerItem("velarium_shovel", Item::new,
            p -> p.shovel(VelariumGear.TOOL, 1.5F, -3.0F).fireResistant());
    public static final DeferredItem<Item> VELARIUM_HOE = ITEMS.registerItem("velarium_hoe", Item::new,
            p -> p.hoe(VelariumGear.TOOL, -4.0F, 0.0F).fireResistant());
    public static final DeferredItem<Item> VELARIUM_HAMMER = ITEMS.registerItem("velarium_hammer", Item::new,
            p -> Hammers.properties(p, VelariumGear.TOOL).fireResistant());
    public static final DeferredItem<Item> VELARIUM_HELMET = armor("velarium_helmet", ArmorType.HELMET);
    public static final DeferredItem<Item> VELARIUM_CHESTPLATE = armor("velarium_chestplate", ArmorType.CHESTPLATE);
    public static final DeferredItem<Item> VELARIUM_LEGGINGS = armor("velarium_leggings", ArmorType.LEGGINGS);
    public static final DeferredItem<Item> VELARIUM_BOOTS = armor("velarium_boots", ArmorType.BOOTS);

    private static DeferredItem<Item> armor(String name, ArmorType type) {
        return ITEMS.registerItem(name, Item::new, p -> p.humanoidArmor(VelariumGear.ARMOR, type).fireResistant());
    }

    /** Nourished Soil (id {@code nourished_soil}). */
    public static final DeferredItem<BlockItem> FERTILE_SOIL = ITEMS.registerSimpleBlockItem(ModBlocks.FERTILE_SOIL);
    public static final DeferredItem<BlockItem> LASER = ITEMS.registerSimpleBlockItem(ModBlocks.LASER);
    public static final DeferredItem<BlockItem> PLANTER = ITEMS.registerSimpleBlockItem(ModBlocks.PLANTER);
    public static final DeferredItem<BlockItem> MAGNET_CHEST = ITEMS.registerSimpleBlockItem(ModBlocks.MAGNET_CHEST);
    public static final DeferredItem<BlockItem> HUB_STONE = ITEMS.registerSimpleBlockItem(ModBlocks.HUB_STONE);

    public static final DeferredItem<MagnetItem> MAGNET = ITEMS.registerItem("magnet", p -> new MagnetItem(8, p), p -> p.stacksTo(1));
    public static final DeferredItem<MagnetItem> ADVANCED_MAGNET = ITEMS.registerItem("advanced_magnet", p -> new MagnetItem(16, p),
            p -> p.stacksTo(1));

    public static final DeferredItem<MobBottleItem> MOB_BOTTLE = ITEMS.registerItem("mob_bottle", MobBottleItem::new, p -> p.stacksTo(16));
    public static final DeferredItem<BlockItem> SPAWN_EGG_BREWER = ITEMS.registerSimpleBlockItem(ModBlocks.SPAWN_EGG_BREWER);

    private static DeferredItem<StorageItem> storage(String name, Supplier<? extends Block> block) {
        return ITEMS.registerItem(name, p -> new StorageItem(block.get(), p), p -> p.useBlockDescriptionPrefix().stacksTo(1).fireResistant());
    }

    private static DeferredItem<StorageUpgradeItem> storageUpgrade(String name, StorageTier tier,
            Supplier<? extends StorageBlock> block, boolean fireproof) {
        return ITEMS.registerItem(name, p -> new StorageUpgradeItem(p, tier, block),
                p -> fireproof ? p.fireResistant() : p);
    }

    public static final DeferredItem<Item> STONE_HAMMER = hammer("stone_hammer", ToolMaterial.STONE);
    public static final DeferredItem<Item> COPPER_HAMMER = hammer("copper_hammer", ToolMaterial.COPPER);
    public static final DeferredItem<Item> IRON_HAMMER = hammer("iron_hammer", ToolMaterial.IRON);
    public static final DeferredItem<Item> GOLDEN_HAMMER = hammer("golden_hammer", ToolMaterial.GOLD);
    public static final DeferredItem<Item> DIAMOND_HAMMER = hammer("diamond_hammer", ToolMaterial.DIAMOND);
    public static final DeferredItem<Item> NETHERITE_HAMMER = ITEMS.registerItem("netherite_hammer", Item::new,
            p -> Hammers.properties(p, ToolMaterial.NETHERITE).fireResistant());

    public static final DeferredItem<BlockItem> NESTED_TIMER = ITEMS.registerSimpleBlockItem(ModBlocks.NESTED_TIMER);
    public static final DeferredItem<DiceItem> DICE = ITEMS.registerItem("dice",
            p -> new DiceItem(ModBlocks.DICE.get(), p), p -> p.useBlockDescriptionPrefix().stacksTo(16));


    private static DeferredItem<Item> hammer(String name, ToolMaterial material) {
        return ITEMS.registerItem(name, Item::new, p -> Hammers.properties(p, material));
    }

    private static DeferredItem<SorterGearItem> sorterGear(String name, SorterGear gear, boolean fireproof) {
        return ITEMS.registerItem(name, p -> new SorterGearItem(gear, p), p -> fireproof ? p.stacksTo(16).fireResistant() : p.stacksTo(16));
    }

    private ModItems() {}
}
