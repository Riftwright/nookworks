package dev.nookworks.client.render;

import dev.nookworks.Nookworks;
import dev.nookworks.registry.ModBlockEntities;
import dev.nookworks.registry.ModEntities;
import dev.nookworks.registry.ModRope;
import dev.nookworks.registry.ModTravel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.BoatModel;
import dev.nookworks.port.render.BoatRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;

/** Every entity and block entity renderer of the mod, their model layers and the standalone models they draw. */
@EventBusSubscriber(modid = Nookworks.MODID, value = Dist.CLIENT)
public final class ClientRenderers {
    /** Dawnwood's boats: vanilla's boat models, with the textures at textures/entity/boat/ and chest_boat/dawnwood.png. */
    private static final ModelLayerLocation DAWNWOOD_BOAT =
            new ModelLayerLocation(Nookworks.id("boat/dawnwood"), "main");
    private static final ModelLayerLocation DAWNWOOD_CHEST_BOAT =
            new ModelLayerLocation(Nookworks.id("chest_boat/dawnwood"), "main");

    @SubscribeEvent
    static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        // PORT: 1.21.1 calls the boat's layer createBodyModel, and the chest boat's is a model class of its own
        event.registerLayerDefinition(DAWNWOOD_BOAT, BoatModel::createBodyModel);
        event.registerLayerDefinition(DAWNWOOD_CHEST_BOAT, net.minecraft.client.model.ChestBoatModel::createBodyModel);
        event.registerLayerDefinition(RepairBoxModel.LAYERS.single(), RepairBoxModel::single);
        event.registerLayerDefinition(RepairBoxModel.LAYERS.left(), RepairBoxModel::left);
        event.registerLayerDefinition(RepairBoxModel.LAYERS.right(), RepairBoxModel::right);
    }

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.POSTER.get(), PosterRenderer::new);
        // vanilla's own, with a frame round a framed painting
        event.registerEntityRenderer(net.minecraft.world.entity.EntityType.PAINTING, FramedPaintingRenderer::new);
        event.registerEntityRenderer(ModEntities.DARK_OAK_ITEM_FRAME.get(), NookItemFrameRenderer::new);
        event.registerEntityRenderer(ModEntities.DARK_OAK_GLOW_ITEM_FRAME.get(), NookItemFrameRenderer::new);
        event.registerEntityRenderer(ModEntities.BRASS_ITEM_FRAME.get(), NookItemFrameRenderer::new);
        event.registerEntityRenderer(ModEntities.BRASS_GLOW_ITEM_FRAME.get(), NookItemFrameRenderer::new);
        event.registerEntityRenderer(ModEntities.SEAT.get(), SeatRenderer::new);
        event.registerEntityRenderer(ModEntities.LABEL.get(), LabelRenderer::new);
        event.registerEntityRenderer(ModEntities.LODE_PEARL.get(), ThrownItemRenderer::new);
        event.registerEntityRenderer(ModEntities.DAWNWOOD_BOAT.get(), c -> new BoatRenderer(c, DAWNWOOD_BOAT));
        event.registerEntityRenderer(ModEntities.DAWNWOOD_CHEST_BOAT.get(), c -> new BoatRenderer(c, DAWNWOOD_CHEST_BOAT));
        event.registerBlockEntityRenderer(ModBlockEntities.LASER.get(), LaserBeamRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.REPAIR_BOX.get(), RepairBoxRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.MOTOR.get(), MotorRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.DRIVE_WHEEL.get(), DriveWheelRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.GRILL.get(), GrillRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.IRON_POT.get(), IronPotRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.KITCHEN_TAP.get(), KitchenTapRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.NUKADOKO.get(), NukadokoRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.OIL_PRESS.get(), c -> new CrankRenderer<>(c, CrankRenderer.OIL_PRESS));
        event.registerBlockEntityRenderer(ModBlockEntities.RICE_POLISHER.get(), c -> new CrankRenderer<>(c, CrankRenderer.RICE_POLISHER));
        event.registerBlockEntityRenderer(ModBlockEntities.HAND_QUERN.get(), c -> new CrankRenderer<>(c, CrankRenderer.HAND_QUERN));
        event.registerBlockEntityRenderer(ModBlockEntities.VENT_FAN.get(), VentFanRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.TRAY.get(), TrayRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.DICE.get(), DiceRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.WARP_ANCHOR.get(), WarpAnchorRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.PLATE.get(), PlateRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.PLACED_BOWLS.get(), PlacedBowlsRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.PLACED_BOOKS.get(), PlacedBooksRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.BOOKEND.get(), BookendRenderer::new);
        event.registerEntityRenderer(ModRope.ROPE_KNOT.get(), RopeKnotRenderer::new);
        event.registerEntityRenderer(ModRope.HUNG_ITEM.get(), HungItemRenderer::new);
        event.registerEntityRenderer(ModRope.HUNG_BANNER.get(), HungBannerRenderer::new);
        event.registerEntityRenderer(ModRope.TIED_LOAD.get(), TiedLoadRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.NOODLE_MAKER.get(), c -> new CrankRenderer<>(c, CrankRenderer.NOODLE_MAKER));
        event.registerBlockEntityRenderer(ModTravel.DAEMON_DOOR_ENTITY.get(), DaemonDoorRenderer::new);
        event.registerBlockEntityRenderer(ModTravel.IF_MIRROR_ENTITY.get(), IfMirrorRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.STANDING_MIRROR.get(), StandingMirrorRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.COPPER_PIPE.get(), c -> new PipePullRenderer<>(c, PipePullRenderer.COPPER));
        event.registerBlockEntityRenderer(ModBlockEntities.EXPERIENCE_PIPE.get(), c -> new PipePullRenderer<>(c, PipePullRenderer.EXPERIENCE));
    }

    @SubscribeEvent
    static void registerModels(dev.nookworks.port.render.ModelEvent.RegisterStandalone event) {
        CrankRenderer.registerModels(event);
        MotorRenderer.registerModels(event);
        DriveWheelRenderer.registerModels(event);
        VentFanRenderer.registerModels(event);
        TableLots.registerModels(event);
        DiceRenderer.registerModels(event);
        WarpAnchorRenderer.registerModels(event);
        DishModels.registerModels(event);
        IronPotRenderer.registerModels(event);
        PlacedBooksRenderer.registerModels(event);
        HungItemRenderer.registerModels(event);
        PipePullRenderer.registerModels(event);
        NookItemFrameRenderer.registerModels(event);
    }

    private ClientRenderers() {}
}
