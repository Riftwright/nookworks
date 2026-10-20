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
import dev.nookworks.storage.StorageSpillsCondition;
import dev.nookworks.travel.TravelLog;
import net.minecraft.resources.Identifier;
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
        modContainer.registerConfig(ModConfig.Type.SYNCED, NookworksConfig.SPEC, "nookworks-server.toml");
        modContainer.registerConfig(ModConfig.Type.STARTUP, NookworksStartupConfig.SPEC);
        ModWoods.init();
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
        ModConditions.CONDITIONS.register(modEventBus);
        PickOneFunction.FUNCTIONS.register(modEventBus);
        VillageMap.FUNCTIONS.register(modEventBus);
        StorageSpillsCondition.CONDITIONS.register(modEventBus);
        FertileSoilBlock.PROVIDERS.register(modEventBus);
        PotRecipe.TYPES.register(modEventBus);
        PotRecipe.SERIALIZERS.register(modEventBus);
        LeftoverRecipe.SERIALIZERS.register(modEventBus);
        dev.nookworks.quench.Quenching.SERIALIZERS.register(modEventBus);
        PotRecipe.BOOK_CATEGORIES.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        ModDataComponents.COMPONENTS.register(modEventBus);
        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModRope.register(modEventBus);
        MercuryFluids.register(modEventBus);
        ExperienceFluids.register(modEventBus);
        OilFluids.register(modEventBus);
        registerGameTests(modEventBus);
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

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
