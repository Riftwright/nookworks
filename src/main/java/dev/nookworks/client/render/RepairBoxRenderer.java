package dev.nookworks.client.render;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import dev.nookworks.Nookworks;
import dev.nookworks.block.RepairBoxBlock;
import dev.nookworks.experience.RepairBoxBlockEntity;
import net.minecraft.client.model.object.chest.ChestModel;
import net.minecraft.client.renderer.MultiblockChestResources;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.ChestRenderer;
import net.minecraft.client.renderer.blockentity.state.ChestRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the Repair Box: its own model, hollow ({@link RepairBoxModel}), in a texture of its own
 * ({@code textures/entity/chest/repair_box*.png}, stitched into the chest atlas because that atlas takes every
 * {@code entity/chest} folder) - and, with the lid up, what is in its well: the gear lying on the floor, and over it
 * the liquid experience, its surface as high as the box (or the pair) is full, see-through and lit of itself, so it
 * glows in a dark workshop (the user, 2026-10-10).
 */
public class RepairBoxRenderer implements BlockEntityRenderer<RepairBoxBlockEntity, RepairBoxRenderer.State> {
    public static final MultiblockChestResources<SpriteId> TEXTURES = new MultiblockChestResources<>(
            Sheets.CHEST_MAPPER.apply(Nookworks.id("repair_box")),
            Sheets.CHEST_MAPPER.apply(Nookworks.id("repair_box_left")),
            Sheets.CHEST_MAPPER.apply(Nookworks.id("repair_box_right")));
    /** The liquid's surface: the still liquid experience's own texture, moving as it does in the world and in tanks. */
    private static final SpriteId POOL = Sheets.BLOCKS_MAPPER.apply(Nookworks.id("liquid_experience_still"));
    /** See-through enough that the gear under it shows, greened. */
    private static final int POOL_COLOUR = 0x99FFFFFF;
    /** How high the gear lies: just off the well's floor, in pixels. */
    private static final float GEAR = RepairBoxModel.FLOOR + 0.4F;

    public static final class State extends ChestRenderState {
        /** How full the box, or the pair, is: 0 to 1. */
        float fill;
        final List<ItemStackRenderState> items = new ArrayList<>();
        final List<Integer> slots = new ArrayList<>();
    }

    private final SpriteGetter sprites;
    private final MultiblockChestResources<ChestModel> models;
    private final ItemModelResolver itemModels;

    public RepairBoxRenderer(BlockEntityRendererProvider.Context context) {
        this.sprites = context.sprites();
        this.models = RepairBoxModel.LAYERS.map(layer -> new ChestModel(context.bakeLayer(layer)));
        this.itemModels = context.itemModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(RepairBoxBlockEntity box, State state, float partialTicks, Vec3 camera,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(box, state, partialTicks, camera, breakProgress);
        BlockState block = box.getBlockState();
        state.type = block.hasProperty(RepairBoxBlock.TYPE) ? block.getValue(RepairBoxBlock.TYPE) : ChestType.SINGLE;
        state.facing = block.hasProperty(RepairBoxBlock.FACING) ? block.getValue(RepairBoxBlock.FACING) : Direction.SOUTH;
        float open = box.getOpenNess(partialTicks);
        int steps = box.shownStep();
        int most = RepairBoxBlockEntity.SHOWN_STEPS;
        // Both halves of a pair open together, whichever one the screen was opened on, and fill as one well.
        if (box.getLevel() != null && state.type != ChestType.SINGLE
                && box.getLevel().getBlockEntity(box.getBlockPos().relative(RepairBoxBlock.connectedDirection(block)))
                        instanceof RepairBoxBlockEntity other) {
            open = Math.max(open, other.getOpenNess(partialTicks));
            steps += other.shownStep();
            most += RepairBoxBlockEntity.SHOWN_STEPS;
        }
        state.open = open;
        state.fill = steps / (float) most;
        state.customSprite = TEXTURES.select(state.type);
        state.items.clear();
        state.slots.clear();
        if (open > 0.0F) { // shut, the well is not seen
            List<ItemStack> gear = box.shownItems();
            int seed = (int) box.getBlockPos().asLong();
            for (int slot = 0; slot < gear.size(); slot++) {
                if (!gear.get(slot).isEmpty()) {
                    ItemStackRenderState item = new ItemStackRenderState();
                    this.itemModels.updateForTopItem(item, gear.get(slot), ItemDisplayContext.FIXED, box.getLevel(), null, seed + slot);
                    state.items.add(item);
                    state.slots.add(slot);
                }
            }
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.mulPose(ChestRenderer.modelTransformation(state.facing));
        float open = 1.0F - state.open;
        open = 1.0F - open * open * open;
        ChestModel model = this.models.select(state.type);
        collector.submitModel(model, open, poseStack, state.lightCoords, OverlayTexture.NO_OVERLAY, -1,
                state.customSprite, this.sprites, 0);
        if (state.breakProgress != null) { // as ChestRenderer does
            collector.order(1).submitCrumblingOverlay(model, open, poseStack, state.customSprite.renderType(model.renderType()),
                    state.lightCoords, OverlayTexture.NO_OVERLAY, -1, state.breakProgress);
        }
        if (state.open > 0.0F) {
            // the well's floor, x from to (in pixels), as RepairBoxModel's walls leave it: a pair's halves to their seam
            float x0 = state.type == ChestType.LEFT ? 0 : 2;
            float x1 = state.type == ChestType.RIGHT ? 16 : 14;
            this.gear(state, poseStack, collector, x0, x1);
            if (state.fill > 0) {
                this.pool(state, poseStack, collector, x0, x1);
            }
        }
        poseStack.popPose();
    }

    /**
     * The gear, lying flat on the well's floor, three to a row, two rows, each turned a little its own way, as things
     * put down in a box lie - not set out in a grid.
     */
    private void gear(State state, PoseStack pose, SubmitNodeCollector collector, float x0, float x1) {
        for (int i = 0; i < state.items.size(); i++) {
            int slot = state.slots.get(i);
            float x = x0 + (x1 - x0) * (slot % 3 * 2 + 1) / 6;
            float z = slot < 3 ? 5.0F : 10.5F;
            pose.pushPose();
            pose.translate(x / 16, (GEAR + i * 0.05F) / 16, z / 16);
            pose.rotate(Axis.YP.rotationDegrees(slot * 73.0F + 17.0F));
            pose.rotate(Axis.XP.rotationDegrees(90.0F + (slot % 2 == 0 ? 4.0F : -3.0F))); // not quite flat: on what is under it
            pose.scale(0.42F, 0.42F, 0.42F);
            state.items.get(i).submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
    }

    /**
     * The liquid experience's surface over the well's floor: as high as the box is full, up to a pixel under the rim,
     * its texture laid by the block's own pixels so a pair's halves run on into each other, and lit of itself.
     */
    private void pool(State state, PoseStack pose, SubmitNodeCollector collector, float x0, float x1) {
        TextureAtlasSprite sprite = this.sprites.get(POOL);
        float y = (RepairBoxModel.FLOOR + 0.6F + state.fill * (RepairBoxModel.RIM - RepairBoxModel.FLOOR - 1.6F)) / 16;
        float z0 = 2, z1 = 14;
        collector.submitCustomGeometry(pose, RenderTypes.entityTranslucent(sprite.atlasLocation()), (p, buffer) -> {
            vertex(p, buffer, sprite, x0, y, z1);
            vertex(p, buffer, sprite, x1, y, z1);
            vertex(p, buffer, sprite, x1, y, z0);
            vertex(p, buffer, sprite, x0, y, z0);
        });
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite, float x, float y, float z) {
        buffer.addVertex(pose, x / 16, y, z / 16).setColor(POOL_COLOUR).setUv(sprite.getU(x / 16), sprite.getV(z / 16))
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0, 1, 0);
    }

    @Override
    public AABB getRenderBoundingBox(RepairBoxBlockEntity box) {
        BlockPos pos = box.getBlockPos();
        return AABB.encapsulatingFullBlocks(pos.offset(-1, 0, -1), pos.offset(1, 1, 1));
    }
}
