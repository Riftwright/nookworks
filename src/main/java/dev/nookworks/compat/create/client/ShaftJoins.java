package dev.nookworks.compat.create.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.nookworks.Nookworks;
import dev.nookworks.client.render.ForeignJoins;
import dev.nookworks.port.render.BlockStateModelPart;
import dev.nookworks.port.render.SimpleUnbakedStandaloneModel;
import dev.nookworks.port.render.StandaloneModelKey;
import dev.nookworks.port.render.SubmitNodeCollector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Client only: where a Create shaft runs straight into our machine or a Drive Wheel's axle. Create's shaft is 4 px
 * thick, our machines' axles 3 px and the wheel's 2 px, so the join is drawn on our side, inside our block, turning with
 * the shaft: a flange coupling - a bolted round flange wider than the shaft, a boss, then our slim axle (the owner's
 * pick of four looks, 2026-11-15). The models are tools/port1211/gen_create_joins.py's, under models/compat/create,
 * loaded only with Create.
 */
public final class ShaftJoins implements ForeignJoins.Look {
    /** The parts the join is drawn for: the machines driven from a side, the quern from above and below, the wheel, the Motor. */
    public static final List<String> PARTS = List.of("oil_press", "rice_polisher", "noodle_maker", "hand_quern_up", "hand_quern_down", "drive_wheel",
            "motor");

    private static final Map<String, StandaloneModelKey<BlockStateModelPart>> KEYS = new HashMap<>();
    private static final int[] NO_TINTS = new int[0];

    static StandaloneModelKey<BlockStateModelPart> key(String part) {
        return KEYS.computeIfAbsent(part, p -> new StandaloneModelKey<>(() -> Nookworks.MODID + ":compat_create_join_" + p));
    }

    static void registerModels(dev.nookworks.port.render.ModelEvent.RegisterStandalone event) {
        for (String part : PARTS) {
            event.register(key(part), SimpleUnbakedStandaloneModel.simpleModelWrapper(Nookworks.id("compat/create/join_" + part)));
        }
    }

    @Override
    public boolean draw(String part, PoseStack pose, SubmitNodeCollector collector, int light) {
        var models = dev.nookworks.port.render.PortModelManager.of(Minecraft.getInstance().getModelManager());
        BlockStateModelPart model = models.getStandaloneModel(key(part));
        if (model == null) {
            return false;
        }
        collector.submitMultiLayerBlockModel(pose, List.of(model), false, NO_TINTS, light, OverlayTexture.NO_OVERLAY, 0);
        // from below the quern's still plate stays, the join drawn under it; elsewhere the join is the whole axle there
        return !part.endsWith("_down");
    }
}
