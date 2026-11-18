package dev.nookworks.port.render;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;

import dev.nookworks.port.core.ModBusEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.IModBusEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Where the port's stand-in events come from: each posted, on every mod's bus or on the game's as 26.3 posts it, from
 * the 1.21.1 event that comes at the same point, and what the port does itself there (models loaded on their own, item
 * properties and colours, the items drawn by code). An add-on built on this port listens to them as it would to
 * NeoForge's own on 26.3.
 */
@EventBusSubscriber(modid = PortIds.MODID, value = Dist.CLIENT)
public final class PortClientEvents {
    private PortClientEvents() {}

    /** A stand-in mod-bus event, posted to every mod's bus as 26.3 posts its own ({@link ModBusEvents}). */
    private static <E extends Event & IModBusEvent> void postOnModBus(E event) {
        ModBusEvents.post(event);
    }

    // ---- the mod's bus

    @SubscribeEvent
    static void registerModels(ModelEvent.RegisterAdditional event) {
        dev.nookworks.port.render.ModelEvent.REQUESTED.clear();
        postOnModBus(new dev.nookworks.port.render.ModelEvent.RegisterStandalone(event));
        SpecialItems.registerModels(event);
    }

    @SubscribeEvent
    static void modelsBaked(ModelEvent.BakingCompleted event) {
        dev.nookworks.port.render.ModelEvent.baked(event.getModels());
        SpecialItems.reset();
    }

    @SubscribeEvent
    static void blockColours(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Block event) {
        postOnModBus(new RegisterColorHandlersEvent.BlockTintSources(event));
    }

    @SubscribeEvent
    static void itemColours(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Item event) {
        postOnModBus(new RegisterColorHandlersEvent.ItemTintSources());
        ItemModelProperties.registerTints(event);
    }

    @SubscribeEvent
    static void clientExtensions(RegisterClientExtensionsEvent event) {
        postOnModBus(new RegisterFluidModelsEvent());
        SpecialItems.registerExtensions(event);
    }

    /**
     * Client setup runs each mod's listeners on worker threads side by side; the two events go out from the main thread
     * (26.3 posts them there, as the game starts), in the work queued here, before the item properties read them.
     */
    @SubscribeEvent
    static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            postOnModBus(new RegisterSpecialModelRendererEvent());
            postOnModBus(new RegisterConditionalItemModelPropertyEvent());
            ItemModelProperties.registerProperties();
        });
    }

    /** 26.3's wall banner, bar and flag, which 1.21.1 has no layers for. */
    @SubscribeEvent
    static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(BannerModel.WALL_LAYER, BannerModel::createWallLayer);
        event.registerLayerDefinition(BannerFlagModel.WALL_LAYER, BannerFlagModel::createWallLayer);
    }

    // ---- the game's bus

    /** Buffers of our own for what is added to the world, drawn as soon as it is all added. */
    private static final MultiBufferSource.BufferSource CUSTOM = MultiBufferSource.immediate(new ByteBufferBuilder(1 << 16));

    /** 26.3's custom geometry: after the particles, with the world's own depth, from the camera. */
    @SubscribeEvent
    static void customGeometry(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        PoseStack pose = new PoseStack();
        NeoForge.EVENT_BUS.post(new SubmitCustomGeometryEvent(LevelRenderState.current(), new SubmitNodeCollector(CUSTOM), pose));
        CUSTOM.endBatch();
    }

    /** 26.3's outline event, from 1.21.1's: its outline left out if the event is cancelled or a renderer of its own says so. */
    @SubscribeEvent
    static void outline(RenderHighlightEvent.Block event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        BlockHitResult hit = event.getTarget();
        var state = minecraft.level.getBlockState(hit.getBlockPos());
        LevelRenderState level = LevelRenderState.current();
        ExtractBlockOutlineRenderStateEvent extract = new ExtractBlockOutlineRenderStateEvent(minecraft.level, hit.getBlockPos(), state, hit,
                event.getCamera(), level);
        NeoForge.EVENT_BUS.post(extract);
        if (extract.isCanceled()) {
            event.setCanceled(true);
            return;
        }
        BlockOutlineRenderState outline = new BlockOutlineRenderState(hit.getBlockPos(), state, true, false);
        SubmitNodeCollector collector = new SubmitNodeCollector(event.getMultiBufferSource());
        for (CustomBlockOutlineRenderer renderer : extract.customRenderers()) {
            if (renderer.render(outline, collector, event.getPoseStack(), level)) {
                event.setCanceled(true);
            }
        }
    }
}
