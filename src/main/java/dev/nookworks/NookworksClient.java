package dev.nookworks;

import dev.nookworks.client.CameraView;
import dev.nookworks.client.PhotoTextures;
import dev.nookworks.client.PotLines;
import dev.nookworks.client.RecipeChoiceClient;
import dev.nookworks.client.Squint;
import dev.nookworks.client.gui.TradingCounterScreen;
import net.minecraft.client.Minecraft;
import dev.nookworks.client.gui.LodestoneNameScreen;
import dev.nookworks.pearl.LodestoneNames;
import dev.nookworks.client.LiquidLooks;
import dev.nookworks.client.NookworksClientConfig;
import dev.nookworks.client.gui.CrateScreen;
import dev.nookworks.client.gui.DestinationPanelScreen;
import dev.nookworks.client.gui.MachineScreen;
import dev.nookworks.client.gui.NestedTimerScreen;
import dev.nookworks.client.gui.PrintingPressScreen;
import dev.nookworks.client.gui.RepairBoxScreen;
import dev.nookworks.client.gui.SorterScreen;
import dev.nookworks.client.gui.SpawnEggBrewerScreen;
import dev.nookworks.client.gui.SpawnerControllerScreen;
import dev.nookworks.client.gui.StorageScreen;
import dev.nookworks.client.render.LabelIconRenderer;
import dev.nookworks.client.render.PhotoIconRenderer;
import dev.nookworks.client.render.RedHotLook;
import dev.nookworks.client.render.RepairBoxItemRenderer;
import dev.nookworks.darkmatter.DarkMatter;
import dev.nookworks.experience.ExperienceFluids;
import dev.nookworks.machine.LootSummaries;
import dev.nookworks.mercury.MercuryFluids;
import dev.nookworks.oil.OilFluids;
import dev.nookworks.registry.ModMenus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterConditionalItemModelPropertyEvent;
import net.neoforged.neoforge.client.event.RegisterSpecialModelRendererEvent;
import net.neoforged.neoforge.client.fluid.FluidTintSource;
import dev.nookworks.client.gui.NookworksSettingsScreen;
import dev.nookworks.client.gui.WorldFeaturesScreen;
import dev.nookworks.feature.WorldPartsEditing;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = Nookworks.MODID, dist = Dist.CLIENT)
public class NookworksClient {
    public NookworksClient(ModContainer container, IEventBus modEventBus) {
        modEventBus.addListener(NookworksClient::registerScreens);
        modEventBus.addListener(NookworksClient::registerFluidModels);
        modEventBus.addListener(LiquidLooks::register);
        RecipeChoiceClient.init(); // the server's word on what a crafting grid could make reaches it
        modEventBus.addListener(Squint::registerKeys);
        modEventBus.addListener(Squint::registerRenderState);
        // the Camera: its viewfinder, the photos the server sends, and a photo poster's item showing its picture
        modEventBus.addListener(CameraView::registerLayer);
        PhotoTextures.init();
        PotLines.init(); // an Iron Pot's contents on the action bar, fitted to the screen
        RedHotLook.register(modEventBus); // a red-hot ingot cooling in a chest, and its heat left
        modEventBus.addListener((RegisterSpecialModelRendererEvent event) -> event.register(
                PhotoIconRenderer.TYPE, PhotoIconRenderer.Unbaked.MAP_CODEC));
        modEventBus.addListener((RegisterConditionalItemModelPropertyEvent event) -> event.register(
                PhotoIconRenderer.TYPE, PhotoIconRenderer.IsPhoto.MAP_CODEC));
        // a label's item shows what it prints (items/label.json lays it over the plain card)
        modEventBus.addListener((RegisterSpecialModelRendererEvent event) -> event.register(
                LabelIconRenderer.TYPE, LabelIconRenderer.Unbaked.MAP_CODEC));
        // the Repair Box's item, with the handle its lid has in the world (vanilla's chest item model has none)
        modEventBus.addListener((RegisterSpecialModelRendererEvent event) -> event.register(
                RepairBoxItemRenderer.TYPE, RepairBoxItemRenderer.Unbaked.MAP_CODEC));
        container.registerConfig(ModConfig.Type.CLIENT, NookworksClientConfig.SPEC);
        // the loot tables a server sent are its own: the next one, or a world of one's own, sends its
        NeoForge.EVENT_BUS.addListener(
                (ClientPlayerNetworkEvent.LoggingOut event) -> LootSummaries.forget());
        // Dark Matter's disc coming or going: the sections it reaches, and their neighbours' smooth light, are drawn again.
        DarkMatter.redraw = (level, centre) -> {
            if (level instanceof ClientLevel client) {
                int r = DarkMatter.RADIUS + 1;
                client.setSectionRangeDirty((centre.getX() - r) >> 4, (centre.getY() - DarkMatter.BELOW - 1) >> 4,
                        (centre.getZ() - r) >> 4, (centre.getX() + r) >> 4, (centre.getY() + DarkMatter.ABOVE + 1) >> 4,
                        (centre.getZ() + r) >> 4);
            }
        };
        // lodestones' names: the box to rename one, and another world's names forgotten on leaving
        LodestoneNames.editor = pos -> Minecraft.getInstance().gui.setScreen(new LodestoneNameScreen(pos));
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> LodestoneNames.clientForget());
        // Mods > Nookworks > Config: the configs (labels from nookworks.configuration.* lang keys) and, in a world, its parts
        container.registerExtensionPoint(IConfigScreenFactory.class, NookworksSettingsScreen::new);
        WorldPartsEditing.shown = view -> {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.gui.setScreen(WorldFeaturesScreen.playing(minecraft.gui.screen(), view,
                    chosen -> ClientPacketDistributor.sendToServer(new NookworksNetwork.SetWorldParts(chosen))));
        };
    }

    /** Our liquids are drawn with their own still/flowing textures (no tint); what they look like from inside is {@link dev.nookworks.client.LiquidLooks}. */
    private static void registerFluidModels(RegisterFluidModelsEvent event) {
        event.register(new FluidModel.Unbaked(
                new Material(Nookworks.id("block/mercury_still")),
                new Material(Nookworks.id("block/mercury_flow")),
                null, (FluidTintSource) null),
                MercuryFluids.SOURCE.get(), MercuryFluids.FLOWING.get());
        event.register(new FluidModel.Unbaked(
                new Material(Nookworks.id("block/liquid_experience_still")),
                new Material(Nookworks.id("block/liquid_experience_flow")),
                null, (FluidTintSource) null),
                ExperienceFluids.SOURCE.get(), ExperienceFluids.FLOWING.get());
        event.register(new FluidModel.Unbaked(
                new Material(Nookworks.id("block/vegetable_oil_still")),
                new Material(Nookworks.id("block/vegetable_oil_flow")),
                null, (FluidTintSource) null),
                OilFluids.SOURCE.get(), OilFluids.FLOWING.get());
        event.register(new FluidModel.Unbaked(
                new Material(Nookworks.id("block/biodiesel_still")),
                new Material(Nookworks.id("block/biodiesel_flow")),
                null, (FluidTintSource) null),
                OilFluids.BIODIESEL.get(), OilFluids.FLOWING_BIODIESEL.get());
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.SORTER.get(), SorterScreen::new);
        event.register(ModMenus.STORAGE.get(), StorageScreen::new);
        event.register(ModMenus.SPAWNER_CONTROLLER.get(), SpawnerControllerScreen::new);
        event.register(ModMenus.SPAWN_EGG_BREWER.get(), SpawnEggBrewerScreen::new);
        event.register(ModMenus.PRINTING_PRESS.get(), PrintingPressScreen::new);
        event.register(ModMenus.REPAIR_BOX.get(), RepairBoxScreen::new);
        event.register(ModMenus.MACHINE.get(), MachineScreen::new);
        event.register(ModMenus.CRATE.get(), CrateScreen::new);
        event.register(ModMenus.NESTED_TIMER.get(), NestedTimerScreen::new);
        event.register(ModMenus.DESTINATION_PANEL.get(), DestinationPanelScreen::new);
        event.register(ModMenus.TRADING_COUNTER.get(), TradingCounterScreen::new);
    }
}
