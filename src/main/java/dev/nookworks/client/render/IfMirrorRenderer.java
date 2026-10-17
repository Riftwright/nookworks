package dev.nookworks.client.render;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.nookworks.ifworld.IfMirrorBlock;
import dev.nookworks.ifworld.IfMirrorBlockEntity;
import dev.nookworks.ifworld.IfWorld;
import dev.nookworks.travel.DaemonDestinations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Draws on an If Mirror's glass, over its silver: the dice as they are thrown - two across and three down, in the order
 * thrown, each the Die's own ivory with its red one, the newest tumbling a moment before it settles - and, once all six
 * are, the world beyond: at home the If at its dawn, in the If the home it leads back to. The last die settles, the six
 * rest a moment, and the view comes up through them pixel by pixel.
 */
public class IfMirrorRenderer implements BlockEntityRenderer<IfMirrorBlockEntity, IfMirrorRenderer.State> {
    /** The glass in the two blocks' picture (as the model has it): columns and rows, first to last. */
    private static final int LEFT = 3;
    private static final int RIGHT = 12;
    private static final int TOP = 3;
    private static final int BOTTOM = 28;
    /** The glass's face, half a pixel in front of the block's middle; and a little in front of that. */
    private static final float OUT = 0.5F / 16 + 0.002F;
    /** Ticks the newest die tumbles before it settles; and from the last throw, when the view starts coming up, and is up. */
    private static final int TUMBLE = 12;
    private static final int REVEAL = 40;
    private static final int REVEALED = 64;
    /** The rows each pair of dice stands in, from the glass's top. */
    private static final int[] DIE_ROWS = {4, 11, 18};
    private static final int IVORY = 0xFFEEE6D4;
    private static final int RIM = 0xFFD6CCB8;
    private static final int RIM_DARK = 0xFFBDB29C;
    private static final int PIP = 0xFF1E1C20;
    private static final int RED = 0xFFC42828;
    /** Each face's pips on the die's inner three by three, as {x, y}. */
    private static final int[][][] PIPS = {
            {{1, 1}},
            {{2, 0}, {0, 2}},
            {{2, 0}, {1, 1}, {0, 2}},
            {{0, 0}, {2, 0}, {0, 2}, {2, 2}},
            {{0, 0}, {2, 0}, {1, 1}, {0, 2}, {2, 2}},
            {{0, 0}, {0, 1}, {0, 2}, {2, 0}, {2, 1}, {2, 2}}};

    public IfMirrorRenderer(BlockEntityRendererProvider.Context context) {
    }

    public static final class State extends BlockEntityRenderState {
        Direction facing = Direction.NORTH;
        List<Integer> faces = List.of();
        float sinceThrow;
        DaemonDestinations.@Nullable Look look;
        float time;
        /** The camera, from the lower half's corner. */
        Vec3 eye = Vec3.ZERO;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(IfMirrorBlockEntity mirror, State state, float partialTicks, Vec3 camera,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(mirror, state, partialTicks, camera, breakProgress);
        var block = mirror.getBlockState();
        state.facing = block.hasProperty(IfMirrorBlock.FACING) ? block.getValue(IfMirrorBlock.FACING) : Direction.NORTH;
        state.faces = mirror.faces();
        state.time = AnimationTime.cycle(mirror.getLevel(), partialTicks, AnimationTime.VIEW_CYCLE);
        state.sinceThrow = AnimationTime.since(mirror.getLevel(), partialTicks, mirror.thrownAt());
        state.look = state.faces.size() == IfWorld.THROWS ? mirror.looksOnto().map(key -> DaemonDestinations.look(key, Minecraft.getInstance().getConnection().registryAccess())).orElse(null) : null;
        state.eye = camera.subtract(Vec3.atLowerCornerOf(mirror.getBlockPos()));
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.faces.isEmpty()) {
            return; // the silver as it is
        }
        collector.submitCustomGeometry(pose, PixelFaces.TYPE, (p, buffer) -> {
            float revealed = state.look == null ? 0 : Math.clamp((state.sinceThrow - REVEAL) / (REVEALED - REVEAL), 0.0F, 1.0F);
            DaemonView.Eye eye = DaemonDoorRenderer.eye(state.eye, state.facing, OUT);
            for (int y = TOP; y <= BOTTOM; y++) {
                for (int x = LEFT; x <= RIGHT; x++) {
                    int colour = state.look != null && dissolve(x, y) < revealed ? DaemonView.pixel(state.look, x, y, state.time, eye)
                            : die(state, x - LEFT, y - TOP);
                    if (colour != 0) {
                        PixelFaces.pixel(p, buffer, state.facing, OUT, x, y, colour);
                    }
                }
            }
        });
    }

    /** The colour of a die at glass pixel {@code gx}, {@code gy} (0 where there is none, and the silver shows). */
    private static int die(State state, int gx, int gy) {
        int column = gx < 5 ? 0 : 1;
        int row = -1;
        for (int i = 0; i < DIE_ROWS.length; i++) {
            if (gy >= DIE_ROWS[i] && gy < DIE_ROWS[i] + 5) {
                row = i;
            }
        }
        int index = row * 2 + column;
        if (row < 0 || index >= state.faces.size()) {
            return 0;
        }
        int dx = gx - column * 5;
        int dy = gy - DIE_ROWS[row];
        boolean edgeX = dx == 0 || dx == 4;
        boolean edgeY = dy == 0 || dy == 4;
        if (edgeX && edgeY) {
            return 0; // its corners rounded
        }
        if (edgeX || edgeY) {
            return dx == 4 || dy == 4 ? RIM_DARK : RIM;
        }
        int face = state.faces.get(index);
        if (index == state.faces.size() - 1 && state.sinceThrow < TUMBLE) {
            face = 1 + (int) (hash((int) (state.sinceThrow / 3) + index * 7) * 6) % 6; // still tumbling
        }
        for (int[] pip : PIPS[face - 1]) {
            if (pip[0] == dx - 1 && pip[1] == dy - 1) {
                return face == 1 ? RED : PIP;
            }
        }
        return IVORY;
    }

    /** When each pixel of the view comes up, 0 to 1: scattered, so it comes up through the dice like a dawn. */
    private static float dissolve(int x, int y) {
        return hash(x * 31 + y * 1013);
    }

    private static float hash(int n) {
        int h = n * 0x9E3779B1;
        h ^= h >>> 15;
        h *= 0x85EBCA77;
        h ^= h >>> 13;
        return (h & 0xFFFF) / 65536.0F;
    }

    /** Both halves: the glass stands two blocks high. */
    @Override
    public AABB getRenderBoundingBox(IfMirrorBlockEntity mirror) {
        return new AABB(mirror.getBlockPos()).expandTowards(0, 1, 0);
    }
}
