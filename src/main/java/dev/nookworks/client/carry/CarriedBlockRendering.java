package dev.nookworks.client.carry;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import org.jspecify.annotations.Nullable;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.blaze3d.vertex.PoseStack;

import dev.nookworks.Nookworks;
import dev.nookworks.carry.CarriedLook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.block.model.BlockStateModelWrapper;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * Draws a carried block ({@link CarriedLook}) in any entity renderer, as the world draws it: each part's block model,
 * and its block entity's renderer from a client-side block entity loaded from the update tag - a chest by its renderer,
 * a pot with what is in it. The pose's origin is the first part's corner, as a block model's is (from an entity's
 * bottom middle, translate by -0.5, 0, -0.5 first); interpolation is the entity's own. The block entities are made once
 * a look and kept while it is (a new look, a new one).
 */
public final class CarriedBlockRendering {
    private static final BlockDisplayContext CONTEXT = BlockDisplayContext.create();
    /** Client block entities by look, weakly and by identity: a look is a value sent once and held by its entity. */
    private static final Cache<CarriedLook, List<Optional<BlockEntity>>> ENTITIES = CacheBuilder.newBuilder().weakKeys().maximumSize(1024).build();
    /** Where the client block entities are said to stand: out of the world, so nothing there is read as a neighbour. */
    private static final int NOWHERE = -30_000;

    private CarriedBlockRendering() {}

    public static void submit(CarriedLook look, PoseStack pose, SubmitNodeCollector collector, int light, float partialTick,
            CameraRenderState camera) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || look.isEmpty()) {
            return;
        }
        List<Optional<BlockEntity>> entities = entities(look, level);
        BlockEntityRenderDispatcher dispatcher = mc.getBlockEntityRenderDispatcher();
        for (int i = 0; i < look.parts().size(); i++) {
            CarriedLook.LookPart part = look.parts().get(i);
            BlockState state = part.state();
            BlockEntity entity = entities.get(i).orElse(null);
            BlockEntityRenderer<BlockEntity, BlockEntityRenderState> renderer = entity == null ? null : dispatcher.getRenderer(entity);
            pose.pushPose();
            pose.translate(part.offset().getX(), part.offset().getY(), part.offset().getZ());
            // a plain model is meshed in the world beside its renderer; a special one (a chest's) is the renderer's own
            // drawing, which the world leaves to the renderer
            if (renderer == null || mc.getModelManager().getBlockModelSet().get(state) instanceof BlockStateModelWrapper) {
                BlockModelRenderState model = new BlockModelRenderState();
                mc.getBlockModelResolver().update(model, state, CONTEXT);
                model.submitMultiLayer(pose, collector, LightCoordsUtil.max(light, model.blockLightCoords), OverlayTexture.NO_OVERLAY, 0);
            }
            if (renderer != null) {
                try {
                    BlockEntityRenderState drawn = renderer.createRenderState();
                    renderer.extractRenderState(entity, drawn, partialTick, camera.pos, null);
                    drawn.lightCoords = light;
                    dispatcher.submit(drawn, pose, collector, camera);
                } catch (RuntimeException e) {
                    Nookworks.LOGGER.warn("Could not draw a carried {}; drawing its block alone", state, e);
                    entities.set(i, Optional.empty());
                }
            }
            pose.popPose();
        }
    }

    private static List<Optional<BlockEntity>> entities(CarriedLook look, ClientLevel level) {
        try {
            return ENTITIES.get(look, () -> make(look, level));
        } catch (ExecutionException e) {
            return make(look, level);
        }
    }

    private static List<Optional<BlockEntity>> make(CarriedLook look, ClientLevel level) {
        List<Optional<BlockEntity>> made = new ArrayList<>(look.parts().size());
        for (CarriedLook.LookPart part : look.parts()) {
            made.add(Optional.ofNullable(make(part, level)));
        }
        return made;
    }

    private static @Nullable BlockEntity make(CarriedLook.LookPart part, ClientLevel level) {
        if (!(part.state().getBlock() instanceof EntityBlock block)) {
            return null;
        }
        try {
            BlockPos at = new BlockPos(part.offset().getX(), NOWHERE + part.offset().getY(), part.offset().getZ());
            BlockEntity entity = block.newBlockEntity(at, part.state());
            if (entity == null) {
                return null;
            }
            entity.setLevel(level);
            if (part.updateTag() != null) {
                entity.handleUpdateTag(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), part.updateTag()));
            }
            return entity;
        } catch (RuntimeException e) {
            Nookworks.LOGGER.debug("No client block entity for a carried {}: {}", part.state(), e.toString());
            return null;
        }
    }
}
