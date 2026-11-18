package dev.nookworks;

import dev.nookworks.block.FertileSoilBlock;
import dev.nookworks.feature.Squinting;
import dev.nookworks.registry.ModTrading;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import dev.nookworks.cooking.PotRecipe;
import dev.nookworks.experience.ExperienceFluids;
import dev.nookworks.feature.Fireproofing;
import dev.nookworks.feature.FoodPoisoning;
import dev.nookworks.feature.Lubrication;
import dev.nookworks.feature.PickOneFunction;
import dev.nookworks.feature.PlacedLogs;
import dev.nookworks.feature.TrimmedVines;
import dev.nookworks.feature.VillageMap;
import dev.nookworks.feature.WoodVinegar;
import dev.nookworks.mercury.MercuryFluids;
import dev.nookworks.oil.LeftoverRecipe;
import dev.nookworks.oil.OilFluids;
import dev.nookworks.registry.GlazedPots;
import dev.nookworks.registry.ModBlockEntities;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModConditions;
import dev.nookworks.registry.ModCooking;
import dev.nookworks.registry.ModCreativeTabs;
import dev.nookworks.registry.ModCrops;
import dev.nookworks.registry.ModDataComponents;
import dev.nookworks.registry.ModEntities;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModKitchen;
import dev.nookworks.registry.ModMenus;
import dev.nookworks.registry.ModOil;
import dev.nookworks.registry.ModRecycling;
import dev.nookworks.registry.ModRope;
import dev.nookworks.registry.ModBarter;
import dev.nookworks.registry.ModSounds;
import dev.nookworks.registry.ModTravel;
import dev.nookworks.registry.ModVentilation;
import dev.nookworks.registry.ModWoods;
import dev.nookworks.registry.RenamedIds;
import dev.nookworks.wood.TurnedPlanks;
import dev.nookworks.storage.StorageSpillsCondition;
import dev.nookworks.travel.TravelLog;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(Nookworks.MODID)
public class Nookworks {
    public static final String MODID = "nookworks";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Nookworks(IEventBus modEventBus, ModContainer modContainer) {
        // NeoForge 26.3 calls the server's settings "synced" (config/<mod>-synced.toml by default, a world's own copy in
        // its syncedconfig/ folder): the file keeps its name, so settings already made carry over
        // PORT: 1.21.1's NeoForge has the server's settings as SERVER, kept per world only (<world>/serverconfig/, made
        // from defaultconfigs/ when there is a copy there): no config/ file that every world falls back on
        modContainer.registerConfig(ModConfig.Type.SERVER, NookworksConfig.SPEC, "nookworks-server.toml");
        modContainer.registerConfig(ModConfig.Type.STARTUP, NookworksStartupConfig.SPEC);
        ModWoods.init();
        TurnedPlanks.init();
        GlazedPots.init();
        ModCrops.init();
        ModCooking.init();
        ModKitchen.init();
        ModVentilation.init();
        ModRope.init();
        ModOil.init();
        ModRecycling.init();
        ModTravel.init();
        ModBarter.init();
        ModTrading.init();
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModSounds.SOUND_EVENTS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITY_TYPES.register(modEventBus);
        RenamedIds.register();
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        Fireproofing.ATTACHMENT_TYPES.register(modEventBus);
        Lubrication.ATTACHMENT_TYPES.register(modEventBus);
        FoodPoisoning.ATTACHMENT_TYPES.register(modEventBus);
        PlacedLogs.ATTACHMENT_TYPES.register(modEventBus);
        WoodVinegar.ATTACHMENT_TYPES.register(modEventBus);
        TrimmedVines.ATTACHMENT_TYPES.register(modEventBus);
        TravelLog.ATTACHMENT_TYPES.register(modEventBus);
        Squinting.ATTACHMENT_TYPES.register(modEventBus);
        dev.nookworks.feature.JoinOverrides.ATTACHMENT_TYPES.register(modEventBus);
        dev.nookworks.feature.DrawRights.ATTACHMENT_TYPES.register(modEventBus);
        dev.nookworks.poster.PaintingFrames.ATTACHMENT_TYPES.register(modEventBus);
        ModConditions.CONDITIONS.register(modEventBus);
        PickOneFunction.FUNCTIONS.register(modEventBus);
        VillageMap.FUNCTIONS.register(modEventBus);
        StorageSpillsCondition.CONDITIONS.register(modEventBus);
        FertileSoilBlock.PROVIDERS.register(modEventBus);
        PotRecipe.TYPES.register(modEventBus);
        PotRecipe.SERIALIZERS.register(modEventBus);
        LeftoverRecipe.SERIALIZERS.register(modEventBus);
        dev.nookworks.quench.Quenching.SERIALIZERS.register(modEventBus);
        // (26.3's recipe book category for the pot: 1.21.1 has no registry of them)
        dev.nookworks.registry.PortRecipeSerializers.SERIALIZERS.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        ModDataComponents.COMPONENTS.register(modEventBus);
        ModEntities.ENTITY_TYPES.register(modEventBus);
        dev.nookworks.item.VelariumGear.MATERIALS.register(modEventBus); // 1.21.1: armour materials are registered
        ModRope.register(modEventBus);
        MercuryFluids.register(modEventBus);
        ExperienceFluids.register(modEventBus);
        OilFluids.register(modEventBus);
        registerCreate(modEventBus, modContainer);
        registerGameTests(modEventBus);
    }

    /**
     * With Create installed, dev.nookworks.compat.create (its shafts turning our machines); without it nothing - the
     * package is reached by name only, so no class of Create's is ever loaded from a game that lacks it.
     */
    private static void registerCreate(IEventBus modEventBus, ModContainer modContainer) {
        if (!net.neoforged.fml.ModList.get().isLoaded("create")) {
            return;
        }
        try {
            Class.forName("dev.nookworks.compat.create.CreateCompat").getMethod("register", IEventBus.class, ModContainer.class)
                    .invoke(null, modEventBus, modContainer);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("could not set up the Create part", e);
        }
    }

    /**
     * The GameTests are part of the dev build only: the published jar leaves their package out (build.gradle), so
     * they are looked up by name instead of linked, and a jar without them simply has none.
     */
    private static void registerGameTests(IEventBus modEventBus) {
        try {
            Class.forName("dev.nookworks.gametest.NookworksGameTests").getMethod("register", IEventBus.class).invoke(null, modEventBus);
        } catch (ClassNotFoundException notShipped) {
            // the published jar
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("could not register the GameTests", e);
        }
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
