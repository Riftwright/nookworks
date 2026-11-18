package dev.nookworks.client;

import dev.nookworks.Nookworks;
import dev.nookworks.block.HubStoneBlock;
import dev.nookworks.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import dev.nookworks.port.render.SimpleUnbakedStandaloneModel;
import dev.nookworks.port.render.StandaloneModelKey;

/**
 * The Hub Stone's running light, and the setting that stills it (the user, 2026-11-14, of the light: 「これ重いかな…
 * アニメーションの有無はmodの設定で変えられるといいかもね」).
 *
 * <p>The light itself is all in the pictures (tools/textures/gen_hub_stone.py): a powered Hub Stone's model is one of
 * eight copies of an animated face, chosen by its power - how far it stands from its source - each begun a frame
 * later than the last, so a pulse runs along the line. Nothing of that is here.
 *
 * <p>With {@link NookworksClientConfig#HUB_STONE_FLOW} off, every powered state is given the lit, still model instead
 * as the models are baked. A model in place cannot be told to change, so turning the setting reads the resources
 * again, as changing a resource pack does.
 */
@EventBusSubscriber(modid = Nookworks.MODID, value = Dist.CLIENT)
public final class HubStoneLight {
    /** The powered Hub Stone with its light at rest: no blockstate names it, so it is asked for here. */
    private static final StandaloneModelKey<BakedModel> STILL = new StandaloneModelKey<>(() -> Nookworks.MODID + ":hub_stone_still");
    /** PORT: the model file loaded for {@link #STILL}, as 1.21.1 keys it in the baked models. */
    private static final ModelResourceLocation STILL_MODEL = ModelResourceLocation.standalone(Nookworks.id("block/hub_stone_on"));
    /** What the models now in the game were baked for. */
    private static volatile boolean bakedRunning = true;

    private HubStoneLight() {}

    private static boolean runs() {
        return !NookworksClientConfig.SPEC.isLoaded() || NookworksClientConfig.HUB_STONE_FLOW.get();
    }

    @SubscribeEvent
    static void registerModels(dev.nookworks.port.render.ModelEvent.RegisterStandalone event) {
        event.register(STILL, SimpleUnbakedStandaloneModel.blockStateModel(Nookworks.id("block/hub_stone_on")));
    }

    @SubscribeEvent
    static void stillTheLight(ModelEvent.ModifyBakingResult event) {
        boolean runs = runs();
        bakedRunning = runs;
        if (runs) {
            return;
        }
        // PORT: 1.21.1 keeps every baked model, a block state's and one loaded on its own, in one map by location
        BakedModel still = event.getModels().get(STILL_MODEL);
        if (still == null) {
            return;
        }
        for (BlockState state : ModBlocks.HUB_STONE.get().getStateDefinition().getPossibleStates()) {
            if (state.getValue(HubStoneBlock.POWER) > 0) {
                event.getModels().put(BlockModelShaper.stateToModelLocation(state), still);
            }
        }
    }

    /** The setting changed, in the settings screen or in its file: the models are baked again for it. */
    @SubscribeEvent
    static void onConfig(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() != NookworksClientConfig.SPEC || runs() == bakedRunning) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (runs() != bakedRunning) {
                minecraft.reloadResourcePacks();
            }
        });
    }
}
