package dev.nookworks.registry;

import dev.nookworks.item.WoolShearsItem;
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
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import dev.nookworks.port.core.Props;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Nookworks.MODID);

    public static final DeferredItem<BlockItem> ROPE = ITEMS.registerSimpleBlockItem(ModBlocks.ROPE);
    public static final DeferredItem<ThinRopeItem> THIN_ROPE = ITEMS.registerItem("thin_rope",
            p -> new ThinRopeItem(ModBlocks.THIN_ROPE.get(), p), Props.item(p -> p));

    public static final DeferredItem<BlockItem> OIL_LAMP = ITEMS.registerSimpleBlockItem(ModBlocks.OIL_LAMP);
    public static final DeferredItem<BlockItem> BOOKEND = ITEMS.registerSimpleBlockItem(ModBlocks.BOOKEND);
    public static final DeferredItem<net.minecraft.world.item.DoubleHighBlockItem> STANDING_MIRROR = ITEMS.registerItem("standing_mirror",
            p -> new net.minecraft.world.item.DoubleHighBlockItem(ModBlocks.STANDING_MIRROR.get(), p), Props.item(p -> p));
    public static final DeferredItem<BlockItem> UNLIT_LANTERN = ITEMS.registerSimpleBlockItem(ModBlocks.UNLIT_LANTERN);
    public static final DeferredItem<BlockItem> UNLIT_SOUL_LANTERN = ITEMS.registerSimpleBlockItem(ModBlocks.UNLIT_SOUL_LANTERN);
    // PORT: 26.3's unlit copper lanterns' items (UNLIT_COPPER_LANTERNS) are left out: 1.21.1 has no copper lanterns
    public static final DeferredItem<StandingAndWallBlockItem> SCONCE = ITEMS.registerItem("sconce",
            p -> new StandingAndWallBlockItem(ModBlocks.SCONCE.get(), ModBlocks.WALL_SCONCE.get(), p, Direction.DOWN),
            Props.item(p -> p));

    public static final DeferredItem<BlockItem> IRON_WORKBENCH = ITEMS.registerSimpleBlockItem(ModBlocks.IRON_WORKBENCH);
    public static final DeferredItem<BlockItem> BRASS_WORKBENCH = ITEMS.registerSimpleBlockItem(ModBlocks.BRASS_WORKBENCH);
    public static final DeferredItem<CrateItem> CRATE = ITEMS.registerItem("crate",
            p -> new CrateItem(ModBlocks.CRATE.get(), p),
            Props.item(p -> p.stacksTo(1)));

    public static final DeferredItem<BlockItem> ELEVATOR = ITEMS.registerSimpleBlockItem(ModBlocks.ELEVATOR);

    public static final DeferredItem<BlockItem> ITEM_WARP_SENDER = ITEMS.registerSimpleBlockItem(ModBlocks.ITEM_WARP_SENDER);
    public static final DeferredItem<BlockItem> ITEM_WARP_RECEIVER = ITEMS.registerSimpleBlockItem(ModBlocks.ITEM_WARP_RECEIVER);
    /** The Horizontal Item Warps (ids {@code horizontal_item_warp_sender} / {@code _receiver}). */
    public static final DeferredItem<BlockItem> HORIZONTAL_ITEM_WARP_SENDER = ITEMS.registerSimpleBlockItem(ModBlocks.HORIZONTAL_ITEM_WARP_SENDER);
    public static final DeferredItem<HorizontalItemWarpReceiverItem> HORIZONTAL_ITEM_WARP_RECEIVER = ITEMS.registerItem("horizontal_item_warp_receiver",
            p -> new HorizontalItemWarpReceiverItem(ModBlocks.HORIZONTAL_ITEM_WARP_RECEIVER.get(), p), Props.item(p -> p));

    public static final DeferredItem<BlockItem> SIFTER = ITEMS.registerSimpleBlockItem(ModBlocks.SIFTER);

    public static final DeferredItem<FireproofCoatingItem> FIREPROOF_COATING = ITEMS.registerItem("fireproof_coating",
            FireproofCoatingItem::new, Props.item(p -> p.durability(64)));

    public static final DeferredItem<BlockItem> GENERATOR = ITEMS.registerSimpleBlockItem(ModBlocks.GENERATOR);
    public static final DeferredItem<BlockItem> DIESEL_GENERATOR = ITEMS.registerSimpleBlockItem(ModBlocks.DIESEL_GENERATOR);
    /** Creative mode's: its name in purple, as vanilla's command block's; only a creative player sets it down. */
    public static final DeferredItem<CreativeOnlyBlockItem> CREATIVE_GENERATOR = ITEMS.registerItem("creative_generator",
            p -> new CreativeOnlyBlockItem(ModBlocks.CREATIVE_GENERATOR.get(), p), Props.item(p -> p.rarity(Rarity.EPIC)));
    public static final DeferredItem<BlockItem> CRUSHER = ITEMS.registerSimpleBlockItem(ModBlocks.CRUSHER);
    public static final DeferredItem<BlockItem> STONE_MAKER = ITEMS.registerSimpleBlockItem(ModBlocks.STONE_MAKER);
    public static final DeferredItem<BlockItem> ELECTRIC_FURNACE = ITEMS.registerSimpleBlockItem(ModBlocks.ELECTRIC_FURNACE);
    public static final DeferredItem<BlockItem> ENERGY_CABLE = ITEMS.registerSimpleBlockItem(ModBlocks.ENERGY_CABLE);

    public static final DeferredItem<BlockItem> ITEM_SORTER = ITEMS.registerSimpleBlockItem(ModBlocks.ITEM_SORTER);
    public static final DeferredItem<BlockItem> PRECISION_SORTER = ITEMS.registerSimpleBlockItem(ModBlocks.PRECISION_SORTER);
    // 0.2.0's, as they may still be in chests: set down, each becomes an Item Sorter with its gear.
    public static final DeferredItem<LegacySorterItem> GOLD_SORTER = ITEMS.registerItem("gold_sorter",
            p -> new LegacySorterItem(ModBlocks.GOLD_SORTER.get(), p), Props.item(p -> p));
    public static final DeferredItem<LegacySorterItem> DIAMOND_SORTER = ITEMS.registerItem("diamond_sorter",
            p -> new LegacySorterItem(ModBlocks.DIAMOND_SORTER.get(), p), Props.item(p -> p));
    public static final DeferredItem<LegacySorterItem> NETHERITE_SORTER = ITEMS.registerItem("netherite_sorter",
            p -> new LegacySorterItem(ModBlocks.NETHERITE_SORTER.get(), p), Props.item(p -> p.fireResistant()));
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
            p -> new SpawnerLinkReceiverItem(ModBlocks.SPAWNER_LINK_RECEIVER.get(), p), Props.item(p -> p));
    public static final DeferredItem<BlockItem> SPAWNER_CONTROLLER = ITEMS.registerSimpleBlockItem(ModBlocks.SPAWNER_CONTROLLER);

    // ---- Mercury ----
    public static final DeferredItem<MercuryBucketItem> MERCURY_BUCKET = ITEMS.registerItem("mercury_bucket",
            p -> new MercuryBucketItem(MercuryFluids.SOURCE.get(), p),
            Props.item(p -> p.craftRemainder(Items.BUCKET).stacksTo(1)));
    public static final DeferredItem<BlockItem> MERCURY_TANK = ITEMS.registerItem("mercury_tank",
            p -> new MercuryTankItem(ModBlocks.MERCURY_TANK.get(), p), Props.item(p -> p.stacksTo(1)));
    public static final DeferredItem<BlockItem> MERCURY_PIPE = ITEMS.registerSimpleBlockItem(ModBlocks.MERCURY_PIPE);
    public static final DeferredItem<BlockItem> RETORT = ITEMS.registerSimpleBlockItem(ModBlocks.RETORT);
    public static final DeferredItem<BlockItem> STIRRING_TANK = ITEMS.registerSimpleBlockItem(ModBlocks.STIRRING_TANK);
    public static final DeferredItem<BlockItem> MACHINE_BELL = ITEMS.registerSimpleBlockItem(ModBlocks.MACHINE_BELL);
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
    public static final DeferredItem<BlockItem> VELARIUM_BLOCK = ITEMS.registerSimpleBlockItem(ModBlocks.VELARIUM_BLOCK, Props.item(p -> p.fireResistant()));
    public static final DeferredItem<Item> VELARIUM_INGOT = ITEMS.registerSimpleItem("velarium_ingot", Props.item(p -> p.fireResistant()));
    public static final DeferredItem<Item> VELARIUM_NUGGET = ITEMS.registerSimpleItem("velarium_nugget", Props.item(p -> p.fireResistant()));
    /** A copper nugget: 26.3 has it in vanilla, 1.21.1 does not, so it is ours (#c:nuggets/copper), nine to an ingot. */
    public static final DeferredItem<Item> COPPER_NUGGET = ITEMS.registerSimpleItem("copper_nugget");
    /** A Velarium Ingot out of the fire, one at a time: too hot to hold, quenched in oil within half a minute (dev.nookworks.quench). */
    public static final DeferredItem<dev.nookworks.quench.RedHotIngotItem> RED_HOT_VELARIUM_INGOT = ITEMS.registerItem("red_hot_velarium_ingot",
            dev.nookworks.quench.RedHotIngotItem::new, Props.item(p -> p.fireResistant().stacksTo(1)));
    /** Quenched in oil: what Velarium tools and armour are made and mended of. */
    public static final DeferredItem<Item> TEMPERED_VELARIUM_INGOT = ITEMS.registerSimpleItem("tempered_velarium_ingot", Props.item(p -> p.fireResistant()));
    /** A breeze rod sheathed in brass: the handle of Velarium tools, a wooden stick being no match for their heads. */
    public static final DeferredItem<Item> REINFORCED_ROD = ITEMS.registerSimpleItem("reinforced_rod");

    // ---- Machine parts: the chassis, and brass (copper alloyed with zinc in the Retort) ----
    public static final DeferredItem<BlockItem> MACHINE_CHASSIS = ITEMS.registerSimpleBlockItem(ModBlocks.MACHINE_CHASSIS);
    public static final DeferredItem<Item> RAW_ZINC = ITEMS.registerSimpleItem("raw_zinc");
    public static final DeferredItem<Item> ZINC_INGOT = ITEMS.registerSimpleItem("zinc_ingot");
    public static final DeferredItem<Item> BRASS_INGOT = ITEMS.registerSimpleItem("brass_ingot");
    /** Fitted into the Sifter: slower, but it keeps the small things. See SifterBlockEntity. */
    public static final DeferredItem<Item> BRASS_MESH = ITEMS.registerSimpleItem("brass_mesh", Props.item(p -> p.stacksTo(1)));
    /** Points the way to the sulfur caves, where cinnabar is: see {@link NookDowsingRodItem}. */
    // 1.21.1 has no repairable / useCooldown properties: mending with a copper ingot and the 3 s cooldown are the item's
    public static final DeferredItem<NookDowsingRodItem> NOOK_DOWSING_ROD = ITEMS.registerItem("nook_dowsing_rod",
            NookDowsingRodItem::new, Props.item(p -> p.durability(64)));
    /** Takes photos to print as posters: see {@link CameraItem}. */
    public static final DeferredItem<CameraItem> CAMERA = ITEMS.registerItem("camera", CameraItem::new, Props.item(p -> p.stacksTo(1)));
    /** Shears a sheep and leaves its lead alone, now and then a wool more; cuts a vine so it stops spreading: see {@link WoolShearsItem}. */
    // 1.21.1 has no repairable / enchantable properties: mending with #c:ingots/brass and enchantability 10 are the item's
    public static final DeferredItem<WoolShearsItem> WOOL_SHEARS = ITEMS.registerItem("wool_shears", WoolShearsItem::new,
            Props.item(p -> p.durability(320)));
    /** An ender pearl bound to a lodestone: thrown, it takes you there. Cools down as an ender pearl does. */
    // 1.21.1 has no useCooldown property: the 1 s cooldown is the item's
    public static final DeferredItem<LodePearlItem> LODE_PEARL = ITEMS.registerItem("lode_pearl",
            LodePearlItem::new, Props.item(p -> p.stacksTo(16)));

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
            Props.item(p -> p.craftRemainder(Items.BUCKET).stacksTo(1)));
    public static final DeferredItem<BlockItem> EXPERIENCE_TANK = ITEMS.registerSimpleBlockItem(ModBlocks.EXPERIENCE_TANK);
    /** 250 mB of Liquid Experience; the Enchanting Table makes a Bottle o' Enchanting of it (EnchantmentMenuMixin). */
    public static final DeferredItem<Item> LIQUID_EXPERIENCE_BOTTLE = ITEMS.registerSimpleItem("liquid_experience_bottle",
            Props.item(p -> p.craftRemainder(Items.GLASS_BOTTLE).stacksTo(16)));
    public static final DeferredItem<BlockItem> EXPERIENCE_PIPE = ITEMS.registerSimpleBlockItem(ModBlocks.EXPERIENCE_PIPE);
    public static final DeferredItem<BlockItem> DEEPSLATE_GRAVEL = ITEMS.registerSimpleBlockItem(ModBlocks.DEEPSLATE_GRAVEL);
    public static final DeferredItem<BlockItem> NETHER_GRIT = ITEMS.registerSimpleBlockItem(ModBlocks.NETHER_GRIT);
    public static final DeferredItem<BlockItem> END_GRAVEL = ITEMS.registerSimpleBlockItem(ModBlocks.END_GRAVEL);
    public static final DeferredItem<BlockItem> BASALT_GRAVEL = ITEMS.registerSimpleBlockItem(ModBlocks.BASALT_GRAVEL);
    /** Cinnabar: vanilla's in 26.3, ours in 1.21.1 (see ModBlocks.CINNABAR). */
    public static final DeferredItem<BlockItem> CINNABAR = ITEMS.registerSimpleBlockItem(ModBlocks.CINNABAR);
    public static final DeferredItem<BlockItem> STONY_DIRT = ITEMS.registerSimpleBlockItem(ModBlocks.STONY_DIRT);
    public static final DeferredItem<BlockItem> PEBBLES = ITEMS.registerSimpleBlockItem(ModBlocks.PEBBLES);
    public static final DeferredItem<BlockItem> WATER_PUMP = ITEMS.registerSimpleBlockItem(ModBlocks.WATER_PUMP);
    public static final DeferredItem<BlockItem> COPPER_PIPE = ITEMS.registerSimpleBlockItem(ModBlocks.COPPER_PIPE);
    public static final DeferredItem<BlockItem> SIGHT_GLASS = ITEMS.registerSimpleBlockItem(ModBlocks.SIGHT_GLASS);
    public static final DeferredItem<BlockItem> VALVE = ITEMS.registerSimpleBlockItem(ModBlocks.VALVE);
    public static final DeferredItem<BlockItem> COPPER_TANK = ITEMS.registerItem("copper_tank",
            p -> new CopperTankItem(ModBlocks.COPPER_TANK.get(), p), Props.item(p -> p));
    public static final DeferredItem<BlockItem> ABSORPTION_PAD = ITEMS.registerSimpleBlockItem(ModBlocks.ABSORPTION_PAD);

    public static final DeferredItem<BlockItem> REPAIR_BOX = ITEMS.registerSimpleBlockItem(ModBlocks.REPAIR_BOX);
    public static final DeferredItem<BlockItem> TRASH_CAN = ITEMS.registerSimpleBlockItem(ModBlocks.TRASH_CAN);
    public static final DeferredItem<BlockItem> DARK_MATTER = ITEMS.registerSimpleBlockItem(ModBlocks.DARK_MATTER);
    public static final DeferredItem<BlockItem> WARP_ANCHOR = ITEMS.registerSimpleBlockItem(ModBlocks.WARP_ANCHOR);
    public static final DeferredItem<BlockItem> WINE_GLASS = ITEMS.registerSimpleBlockItem(ModBlocks.WINE_GLASS);
    public static final DeferredItem<BlockItem> MUG = ITEMS.registerSimpleBlockItem(ModBlocks.MUG);
    /** Cooked food cooked again: what Dark Matter is made of. */
    public static final DeferredItem<Item> CHARRED_LUMP = ITEMS.registerSimpleItem("charred_lump");

    public static final DeferredItem<WrenchItem> WRENCH = ITEMS.registerItem("wrench",
            WrenchItem::new, Props.item(p -> p.durability(512)));
    public static final DeferredItem<BuildingWandItem> BUILDING_WAND = ITEMS.registerItem("building_wand",
            BuildingWandItem::new, Props.item(p -> p.durability(1024)));
    /**
     * The Building Wand with modes - Replace and the three Box modes beside Spread (the user, 2026-11-07: the modes were
     * too strong on the plain wand). Made from a Building Wand and velarium, keeping what the wand carried. Eight times
     * the wand's wear: one use of it can put down up to 4,096 blocks (a box) where the wand puts 32, so it lasts two of
     * the biggest boxes - at 4,096 a new one would stop a block short of its first.
     */
    public static final DeferredItem<BuildingWandItem> REINFORCED_BUILDING_WAND = ITEMS.registerItem("reinforced_building_wand",
            p -> new BuildingWandItem(p, true), Props.item(p -> p.durability(8192).fireResistant()));

    public static final DeferredItem<PosterItem> POSTER = ITEMS.registerItem("poster",
            PosterItem::new, Props.item(p -> p.stacksTo(16)));
    public static final DeferredItem<LabelItem> LABEL = ITEMS.registerItem("label", LabelItem::new);
    /** Item frames of dark oak and of brass; either also frames a poster: see {@link dev.nookworks.poster.PictureFrames}. */
    public static final DeferredItem<dev.nookworks.poster.NookItemFrameItem> DARK_OAK_ITEM_FRAME = ITEMS.registerItem("dark_oak_item_frame",
            p -> new dev.nookworks.poster.NookItemFrameItem(ModEntities.DARK_OAK_ITEM_FRAME, p));
    public static final DeferredItem<dev.nookworks.poster.NookItemFrameItem> DARK_OAK_GLOW_ITEM_FRAME = ITEMS.registerItem(
            "dark_oak_glow_item_frame", p -> new dev.nookworks.poster.NookItemFrameItem(ModEntities.DARK_OAK_GLOW_ITEM_FRAME, p));
    public static final DeferredItem<dev.nookworks.poster.NookItemFrameItem> BRASS_ITEM_FRAME = ITEMS.registerItem("brass_item_frame",
            p -> new dev.nookworks.poster.NookItemFrameItem(ModEntities.BRASS_ITEM_FRAME, p));
    public static final DeferredItem<dev.nookworks.poster.NookItemFrameItem> BRASS_GLOW_ITEM_FRAME = ITEMS.registerItem(
            "brass_glow_item_frame", p -> new dev.nookworks.poster.NookItemFrameItem(ModEntities.BRASS_GLOW_ITEM_FRAME, p));

    // ---- Velarium gear (crafted from ingots like iron gear, no smithing template) ----
    // 1.21.1: each tool is vanilla's item class over the Velarium tier (26.3: a plain item given the tool's
    // properties), with the same damage and speed baselines; durability, repair and enchantability come from the tier
    public static final DeferredItem<Item> VELARIUM_SWORD = ITEMS.registerItem("velarium_sword",
            p -> new SwordItem(VelariumGear.TOOL, p),
            Props.item(p -> p.attributes(SwordItem.createAttributes(VelariumGear.TOOL, 3.0F, -2.4F)).fireResistant()));
    public static final DeferredItem<Item> VELARIUM_PICKAXE = ITEMS.registerItem("velarium_pickaxe",
            p -> new PickaxeItem(VelariumGear.TOOL, p),
            Props.item(p -> p.attributes(PickaxeItem.createAttributes(VelariumGear.TOOL, 1.0F, -2.8F)).fireResistant()));
    public static final DeferredItem<Item> VELARIUM_AXE = ITEMS.registerItem("velarium_axe",
            p -> new AxeItem(VelariumGear.TOOL, p),
            Props.item(p -> p.attributes(AxeItem.createAttributes(VelariumGear.TOOL, 5.0F, -3.0F)).fireResistant()));
    public static final DeferredItem<Item> VELARIUM_SHOVEL = ITEMS.registerItem("velarium_shovel",
            p -> new ShovelItem(VelariumGear.TOOL, p),
            Props.item(p -> p.attributes(ShovelItem.createAttributes(VelariumGear.TOOL, 1.5F, -3.0F)).fireResistant()));
    public static final DeferredItem<Item> VELARIUM_HOE = ITEMS.registerItem("velarium_hoe",
            p -> new HoeItem(VelariumGear.TOOL, p),
            Props.item(p -> p.attributes(HoeItem.createAttributes(VelariumGear.TOOL, -4.0F, 0.0F)).fireResistant()));
    public static final DeferredItem<Item> VELARIUM_HAMMER = ITEMS.registerItem("velarium_hammer",
            p -> Hammers.item(p, VelariumGear.TOOL), Props.item(p -> p.fireResistant()));
    public static final DeferredItem<Item> VELARIUM_HELMET = armor("velarium_helmet", ArmorItem.Type.HELMET);
    public static final DeferredItem<Item> VELARIUM_CHESTPLATE = armor("velarium_chestplate", ArmorItem.Type.CHESTPLATE);
    public static final DeferredItem<Item> VELARIUM_LEGGINGS = armor("velarium_leggings", ArmorItem.Type.LEGGINGS);
    public static final DeferredItem<Item> VELARIUM_BOOTS = armor("velarium_boots", ArmorItem.Type.BOOTS);

    // 1.21.1: the durability goes on the properties (26.3's humanoidArmor took it from the material)
    private static DeferredItem<Item> armor(String name, ArmorItem.Type type) {
        return ITEMS.registerItem(name, p -> new ArmorItem(VelariumGear.ARMOR, type, p),
                Props.item(p -> p.durability(type.getDurability(VelariumGear.ARMOR_DURABILITY)).fireResistant()));
    }

    /** Nourished Soil (id {@code nourished_soil}). */
    public static final DeferredItem<BlockItem> FERTILE_SOIL = ITEMS.registerSimpleBlockItem(ModBlocks.FERTILE_SOIL);
    public static final DeferredItem<BlockItem> LASER = ITEMS.registerSimpleBlockItem(ModBlocks.LASER);
    public static final DeferredItem<BlockItem> PLANTER = ITEMS.registerSimpleBlockItem(ModBlocks.PLANTER);
    public static final DeferredItem<BlockItem> MAGNET_CHEST = ITEMS.registerSimpleBlockItem(ModBlocks.MAGNET_CHEST);
    public static final DeferredItem<BlockItem> HUB_STONE = ITEMS.registerSimpleBlockItem(ModBlocks.HUB_STONE);

    public static final DeferredItem<MagnetItem> MAGNET = ITEMS.registerItem("magnet", p -> new MagnetItem(8, p), Props.item(p -> p.stacksTo(1)));
    public static final DeferredItem<MagnetItem> ADVANCED_MAGNET = ITEMS.registerItem("advanced_magnet", p -> new MagnetItem(16, p),
            Props.item(p -> p.stacksTo(1)));

    public static final DeferredItem<MobBottleItem> MOB_BOTTLE = ITEMS.registerItem("mob_bottle", MobBottleItem::new, Props.item(p -> p.stacksTo(16)));
    public static final DeferredItem<BlockItem> SPAWN_EGG_BREWER = ITEMS.registerSimpleBlockItem(ModBlocks.SPAWN_EGG_BREWER);

    private static DeferredItem<StorageItem> storage(String name, Supplier<? extends Block> block) {
        return ITEMS.registerItem(name, p -> new StorageItem(block.get(), p), Props.item(p -> p.stacksTo(1).fireResistant()));
    }

    private static DeferredItem<StorageUpgradeItem> storageUpgrade(String name, StorageTier tier,
            Supplier<? extends StorageBlock> block, boolean fireproof) {
        return ITEMS.registerItem(name, p -> new StorageUpgradeItem(p, tier, block),
                Props.item(p -> fireproof ? p.fireResistant() : p));
    }

    public static final DeferredItem<Item> STONE_HAMMER = hammer("stone_hammer", Tiers.STONE);
    public static final DeferredItem<Item> COPPER_HAMMER = hammer("copper_hammer", Hammers.COPPER); // 1.21.1 has no copper tier
    public static final DeferredItem<Item> IRON_HAMMER = hammer("iron_hammer", Tiers.IRON);
    public static final DeferredItem<Item> GOLDEN_HAMMER = hammer("golden_hammer", Tiers.GOLD);
    public static final DeferredItem<Item> DIAMOND_HAMMER = hammer("diamond_hammer", Tiers.DIAMOND);
    public static final DeferredItem<Item> NETHERITE_HAMMER = ITEMS.registerItem("netherite_hammer",
            p -> Hammers.item(p, Tiers.NETHERITE), Props.item(p -> p.fireResistant()));

    public static final DeferredItem<BlockItem> NESTED_TIMER = ITEMS.registerSimpleBlockItem(ModBlocks.NESTED_TIMER);
    public static final DeferredItem<DiceItem> DICE = ITEMS.registerItem("dice",
            p -> new DiceItem(ModBlocks.DICE.get(), p), Props.item(p -> p.stacksTo(16)));


    private static DeferredItem<Item> hammer(String name, Tier material) {
        return ITEMS.registerItem(name, p -> Hammers.item(p, material), Props.item(p -> p));
    }

    private static DeferredItem<SorterGearItem> sorterGear(String name, SorterGear gear, boolean fireproof) {
        return ITEMS.registerItem(name, p -> new SorterGearItem(gear, p), Props.item(p -> fireproof ? p.stacksTo(16).fireResistant() : p.stacksTo(16)));
    }

    private ModItems() {}
}
