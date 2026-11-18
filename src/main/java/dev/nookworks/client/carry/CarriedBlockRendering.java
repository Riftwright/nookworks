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
import dev.nookworks.port.render.SubmitNodeCollector;
import dev.nookworks.port.render.BlockModelRenderState;
import dev.nookworks.port.render.MovingBlockRenderState;
import dev.nookworks.port.render.BlockStateModelPart;
import dev.nookworks.port.render.BlockDisplayContext;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import dev.nookworks.port.render.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import dev.nookworks.port.util.LightCoordsUtil;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

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
        submit(look, pose, collector, light, partialTick, camera, null);
    }

    /**
     * As above, with {@code at} the world cell of the first part, where one is known: a plain model is then drawn as a
     * falling block is, tinted by the biome there (a grass block's green, leaves) and lit there, where without it a
     * grass block came out in the tint's no-world green (the user, 2026-11-12: 「草ブロック縛ったら、なんか色変わるし」).
     */
    public static void submit(CarriedLook look, PoseStack pose, SubmitNodeCollector collector, int light, float partialTick,
            CameraRenderState camera, @Nullable BlockPos at) {
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
            // 1.21.1: the block entity's renderer as 1.21.1 has it, drawn at once (26.3 fills its render state and submits it)
            var renderer = entity == null ? null : dispatcher.getRenderer(entity);
            pose.pushPose();
            pose.translate(part.offset().getX(), part.offset().getY(), part.offset().getZ());
            // a plain model is meshed in the world beside its renderer; a special one (a chest's) is the renderer's own
            // drawing, which the world leaves to the renderer
            // (1.21.1: a block whose renderer draws it has an animated render shape, not MODEL - the chest; 26.3 tells it by
            // its block model not being a plain wrapper)
            boolean plain = renderer == null || state.getRenderShape() == RenderShape.MODEL;
            if (plain && at != null && state.getRenderShape() == RenderShape.MODEL) {
                BlockPos cell = at.offset(part.offset());
                MovingBlockRenderState moving = new MovingBlockRenderState();
                moving.randomSeedPos = cell;
                moving.blockPos = cell;
                moving.blockState = state;
                moving.biome = level.getBiome(cell); // (the shading and light are the client world's, as 1.21.1 draws a falling block)
                collector.submitMovingBlock(pose, moving, 0);
            } else if (plain) {
                BlockModelRenderState model = new BlockModelRenderState();
                dev.nookworks.port.render.BlockModelResolver.get().update(model, state, CONTEXT);
                model.submitMultiLayer(pose, collector, LightCoordsUtil.max(light, model.blockLightCoords), OverlayTexture.NO_OVERLAY, 0);
            }
            if (renderer != null) {
                try {
                    renderer.render(entity, partialTick, pose, collector.buffers(), light, OverlayTexture.NO_OVERLAY);
                } catch (RuntimeException e) {
                    Nookworks.LOGGER.warn("Could not draw a carried {}; drawing its block alone", state, e);
                    entities.set(i, Optional.empty());
                }
            }
            pose.popPose();
        }
    }

    /**
     * A block's cracks over each part with a model, {@code progress} 0-9 as on a block being dug (none below 0): a Tied
     * Load being hit. The pose as for {@link #submit}.
     */
    public static void submitCracks(CarriedLook look, PoseStack pose, SubmitNodeCollector collector, int progress) {
        if (progress < 0 || look.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        for (CarriedLook.LookPart part : look.parts()) {
            BlockState state = part.state();
            if (state.getRenderShape() != RenderShape.MODEL) {
                continue;
            }
            // 1.21.1: the block's one baked model as its part (26.3 collects the parts of its block state model)
            BlockStateModelPart model = new BlockStateModelPart(mc.getBlockRenderer().getBlockModel(state), state);
            pose.pushPose();
            pose.translate(part.offset().getX(), part.offset().getY(), part.offset().getZ());
            collector.submitBreakingBlockModel(pose, List.of(model), Math.min(9, progress), model.translucent());
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
                entity.handleUpdateTag(part.updateTag(), level.registryAccess()); // 1.21.1 reads the tag itself
            }
            return entity;
        } catch (RuntimeException e) {
            Nookworks.LOGGER.debug("No client block entity for a carried {}: {}", part.state(), e.toString());
            return null;
        }
    }
}
