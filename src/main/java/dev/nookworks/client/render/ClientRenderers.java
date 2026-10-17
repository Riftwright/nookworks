package dev.nookworks.client.render;

import dev.nookworks.Nookworks;
import dev.nookworks.registry.ModBlockEntities;
import dev.nookworks.registry.ModEntities;
import dev.nookworks.registry.ModRope;
import dev.nookworks.registry.ModTravel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.object.boat.BoatModel;
import net.minecraft.client.renderer.entity.BoatRenderer;
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
        event.registerLayerDefinition(DAWNWOOD_BOAT, BoatModel::createBoatModel);
        event.registerLayerDefinition(DAWNWOOD_CHEST_BOAT, BoatModel::createChestBoatModel);
        event.registerLayerDefinition(RepairBoxModel.LAYERS.single(), RepairBoxModel::single);
        event.registerLayerDefinition(RepairBoxModel.LAYERS.left(), RepairBoxModel::left);
        event.registerLayerDefinition(RepairBoxModel.LAYERS.right(), RepairBoxModel::right);
    }

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.POSTER.get(), PosterRenderer::new);
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
        event.registerBlockEntityRenderer(ModBlockEntities.NUKADOKO.get(), NukadokoRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.OIL_PRESS.get(), c -> new CrankRenderer<>(c, CrankRenderer.OIL_PRESS));
        event.registerBlockEntityRenderer(ModBlockEntities.RICE_POLISHER.get(), c -> new CrankRenderer<>(c, CrankRenderer.RICE_POLISHER));
        event.registerBlockEntityRenderer(ModBlockEntities.HAND_QUERN.get(), c -> new CrankRenderer<>(c, CrankRenderer.HAND_QUERN));
        event.registerBlockEntityRenderer(ModBlockEntities.VENT_FAN.get(), VentFanRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.TRAY.get(), TrayRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.DICE.get(), DiceRenderer::new);
        event.registerEntityRenderer(ModRope.ROPE_KNOT.get(), RopeKnotRenderer::new);
        event.registerEntityRenderer(ModRope.HUNG_ITEM.get(), HungItemRenderer::new);
        event.registerEntityRenderer(ModRope.HUNG_BANNER.get(), HungBannerRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.NOODLE_MAKER.get(), c -> new CrankRenderer<>(c, CrankRenderer.NOODLE_MAKER));
        event.registerBlockEntityRenderer(ModTravel.DAEMON_DOOR_ENTITY.get(), DaemonDoorRenderer::new);
        event.registerBlockEntityRenderer(ModTravel.IF_MIRROR_ENTITY.get(), IfMirrorRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.COPPER_PIPE.get(), c -> new PipePullRenderer<>(c, PipePullRenderer.COPPER));
        event.registerBlockEntityRenderer(ModBlockEntities.EXPERIENCE_PIPE.get(), c -> new PipePullRenderer<>(c, PipePullRenderer.EXPERIENCE));
    }

    @SubscribeEvent
    static void registerModels(ModelEvent.RegisterStandalone event) {
        CrankRenderer.registerModels(event);
        MotorRenderer.registerModels(event);
        DriveWheelRenderer.registerModels(event);
        VentFanRenderer.registerModels(event);
        TrayRenderer.registerModels(event);
        DiceRenderer.registerModels(event);
        HungItemRenderer.registerModels(event);
        PipePullRenderer.registerModels(event);
    }

    private ClientRenderers() {}
}
