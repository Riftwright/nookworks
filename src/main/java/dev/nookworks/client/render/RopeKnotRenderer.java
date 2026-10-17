package dev.nookworks.client.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.nookworks.rope.ChainSway;
import dev.nookworks.rope.HookBlock;
import dev.nookworks.rope.RopeKnotEntity;
import dev.nookworks.rope.RopeSpans;
import dev.nookworks.rope.SpanMaterial;
import dev.nookworks.wood.SupportBlock;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.RodBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a knot of Thin Rope round its fence post (or, smaller, round the foot of a hook's upright: {@link HookBlock#eye};
 * round the middle of a wall bracket's brace, as slanted as it is: {@link SupportBlock#brace}; round a rod, along the
 * way it points: {@link RopeSpans#rod}; round the middle bar of iron or copper bars), and from it each span it tied off,
 * hanging slack to the far post ({@link RopeSpans#at}); and while a player is paying rope out from it, the rope to
 * their hand. Thin Chain is drawn in links ({@link ChainDrawing}), wound round the post in a ring of them where it is
 * tied (under the rope's knot, where rope is tied there too), and swinging while it swings ({@link ChainSway}).
 */
public class RopeKnotRenderer extends EntityRenderer<RopeKnotEntity, RopeKnotRenderer.State> {
    /** The knot: a turn of cord round the post, a little wider than it; on a hook, a turn round the iron. */
    private static final float KNOT_HALF = 2.6F / 16;
    private static final float KNOT_HEIGHT = 1.5F / 16;
    private static final float HOOK_KNOT_HALF = (float) HookBlock.KNOT_HALF / 16;
    private static final float HOOK_KNOT_HEIGHT = (float) HookBlock.KNOT_HEIGHT / 16;
    /** Round a bracket's brace: a turn as wide as the fence post's, the timber being 4 pixels across and 3 through. */
    private static final float BRACE_KNOT_ACROSS = (float) (SupportBlock.BRACE_WIDTH / 2 + 0.6) / 16;
    private static final float BRACE_KNOT_THROUGH = (float) (SupportBlock.BRACE_THICKNESS / 2 + 0.6) / 16;
    /** Round a rod or a bar, 2 pixels across: as much wider than it as the fence post's knot is than the post. */
    private static final float THIN_KNOT_HALF = 1.6F / 16;
    /**
     * A chain wound round: eight links round a fence post, a link's width out from its middle, snug to its faces; four
     * round a rod, a bar or a hook's iron.
     */
    private static final double WRAP_POST = 2.75 / 16;
    private static final double WRAP_THIN = 1.7 / 16;
    private static final double WRAP_HOOK = 1.4 / 16;

    /** What a knot is wound round, which sets its size and the way it lies. */
    enum Round {
        FENCE_POST, HOOK, BRACE, ROD, BARS
    }

    public record Strand(Vec3[] points, int[] lights) {}

    /** A span as drawn: a rope's cord, or a chain's links along the same line. */
    private record Line(Strand strand, @Nullable List<ChainDrawing.Link> links) {}

    /** A knot's spans as last drawn: from where to where, which are chain, when their light was looked up, the lines. */
    private record Drawn(Vec3 origin, Vec3 from, List<Vec3> ends, List<BlockPos> chains, long lit, List<Line> lines) {}

    /** How often a span's light along it is looked up again, in ticks: its shape is kept for as long as its ends stay. */
    private static final int RELIGHT = 20;
    private final Map<RopeKnotEntity, Drawn> drawn = new WeakHashMap<>();

    public static final class State extends EntityRenderState {
        final List<Strand> strands = new ArrayList<>();
        final List<List<ChainDrawing.Link>> chains = new ArrayList<>();
        Round round = Round.FENCE_POST;
        /** On a bracket's brace, the way its beam points, which the brace rises towards; on a rod, the way it points. */
        Direction facing = Direction.UP;
        /** What is wound round the post: a rope's knot, a ring of chain, or both (the chain under the knot). */
        boolean ropeKnot = true;
        boolean chainWrap;
    }

    public RopeKnotRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(RopeKnotEntity knot, State state, float partialTicks) {
        super.extractRenderState(knot, state, partialTicks);
        state.strands.clear();
        state.chains.clear();
        Vec3 origin = knot.position();
        Vec3 from = RopeSpans.tie(knot.level(), knot.getPos());
        BlockState post = knot.level().getBlockState(knot.getPos());
        state.facing = Direction.UP;
        if (post.getBlock() instanceof HookBlock) {
            state.round = Round.HOOK;
        } else if (SupportBlock.braced(post)) {
            state.round = Round.BRACE;
            state.facing = post.getValue(SupportBlock.FACING);
        } else if (post.getBlock() instanceof RodBlock) {
            state.round = Round.ROD;
            state.facing = post.getValue(RodBlock.FACING);
        } else {
            state.round = post.is(BlockTags.BARS) ? Round.BARS : Round.FENCE_POST;
        }
        state.chainWrap = knot.wound(SpanMaterial.CHAIN);
        state.ropeKnot = knot.wound(SpanMaterial.ROPE) || !state.chainWrap;
        // Every frame for every knot, the points of each span and two light lookups a point add up with a festival's
        // worth of rope strung: the spans are worked out again only when an end moves, their light once a second.
        List<BlockPos> spans = knot.spans();
        List<BlockPos> chains = knot.chains();
        List<Vec3> ends = new ArrayList<>();
        for (BlockPos to : spans) {
            ends.add(RopeSpans.tie(knot.level(), to));
        }
        long now = knot.level().getGameTime();
        Drawn last = this.drawn.get(knot);
        if (last == null || !last.origin().equals(origin) || !last.from().equals(from) || !last.ends().equals(ends)
                || !last.chains().equals(chains) || now - last.lit() >= RELIGHT || now < last.lit()) {
            List<Line> lines = new ArrayList<>();
            for (int i = 0; i < spans.size(); i++) {
                Strand strand = strand(knot, origin, from, ends.get(i));
                lines.add(new Line(strand, chains.contains(spans.get(i)) ? ChainDrawing.along(strand.points(), strand.lights()) : null));
            }
            last = new Drawn(origin, from, ends, chains, now, lines);
            this.drawn.put(knot, last);
        }
        for (int i = 0; i < last.lines().size(); i++) {
            Line line = last.lines().get(i);
            if (line.links() == null) {
                state.strands.add(line.strand());
                continue;
            }
            float swing = knot.swing(spans.get(i), partialTicks);
            state.chains.add(swing == 0.0F ? line.links() : swung(line.strand(), origin, from, ends.get(i), swing));
        }
        Entity holder = knot.holder();
        if (holder != null) {
            Strand held = strand(knot, origin, from, holder.getRopeHoldPosition(partialTicks));
            if (knot.heldMaterial() == SpanMaterial.CHAIN) {
                state.chains.add(ChainDrawing.along(held.points(), held.lights()));
            } else {
                state.strands.add(held);
            }
        }
    }

    private static Strand strand(RopeKnotEntity knot, Vec3 origin, Vec3 a, Vec3 b) {
        int steps = RopeDrawing.steps(a.distanceTo(b));
        Vec3[] points = new Vec3[steps + 1];
        int[] lights = new int[steps + 1];
        for (int i = 0; i <= steps; i++) {
            Vec3 point = RopeSpans.at(a, b, i / (double) steps);
            points[i] = point.subtract(origin);
            lights[i] = RopeDrawing.light(knot.level(), point);
        }
        return new Strand(points, lights);
    }

    /** A chain's links swung {@code angle} round, along the points the strand was drawn at, lit as it was. */
    private static List<ChainDrawing.Link> swung(Strand strand, Vec3 origin, Vec3 a, Vec3 b, float angle) {
        int steps = strand.points().length - 1;
        Vec3[] points = new Vec3[steps + 1];
        for (int i = 0; i <= steps; i++) {
            points[i] = ChainSway.swung(a, b, i / (double) steps, angle).subtract(origin);
        }
        return ChainDrawing.along(points, strand.lights());
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        int light = state.lightCoords;
        if (state.ropeKnot || !state.strands.isEmpty()) {
            collector.submitCustomGeometry(pose, RenderTypes.entityCutout(RopeDrawing.TEXTURE), (p, buffer) -> {
                if (state.ropeKnot) {
                    switch (state.round) {
                        case HOOK -> RopeDrawing.knot(p, buffer, HOOK_KNOT_HALF, HOOK_KNOT_HEIGHT, light);
                        case BRACE -> RopeDrawing.knot(brace(p, state.facing), buffer, BRACE_KNOT_ACROSS, BRACE_KNOT_THROUGH, KNOT_HEIGHT / 2, light);
                        case ROD -> RopeDrawing.knot(rod(p, state.facing), buffer, THIN_KNOT_HALF, KNOT_HEIGHT, light);
                        case BARS -> RopeDrawing.knot(p, buffer, THIN_KNOT_HALF, KNOT_HEIGHT, light);
                        case FENCE_POST -> RopeDrawing.knot(p, buffer, KNOT_HALF, KNOT_HEIGHT, light);
                    }
                }
                for (Strand strand : state.strands) {
                    RopeDrawing.cord(p, buffer, strand.points(), strand.lights());
                }
            });
        }
        if (state.chainWrap || !state.chains.isEmpty()) {
            collector.submitCustomGeometry(pose, RenderTypes.entityCutout(ChainDrawing.TEXTURE), (p, buffer) -> {
                if (state.chainWrap) {
                    // under the rope's knot where rope is tied there too, the two wound one over the other
                    float under = state.ropeKnot ? -KNOT_HEIGHT : 0;
                    switch (state.round) {
                        case HOOK -> ChainDrawing.draw(lowered(p, under), buffer, ChainDrawing.wrap(WRAP_HOOK, WRAP_HOOK, 4, light));
                        case BRACE -> {
                            // the brace runs along the turned pose's z: the ring goes round it, in x and y
                            PoseStack.Pose round = brace(p, state.facing);
                            round.translate(0, 0, under);
                            round.rotate(Axis.XP.rotationDegrees(90));
                            ChainDrawing.draw(round, buffer, ChainDrawing.wrap(BRACE_KNOT_ACROSS + 0.15 / 16, BRACE_KNOT_THROUGH + 0.15 / 16, 8, light));
                        }
                        case ROD -> ChainDrawing.draw(lowered(rod(p, state.facing), under), buffer, ChainDrawing.wrap(WRAP_THIN, WRAP_THIN, 4, light));
                        case BARS -> ChainDrawing.draw(lowered(p, under), buffer, ChainDrawing.wrap(WRAP_THIN, WRAP_THIN, 4, light));
                        case FENCE_POST -> ChainDrawing.draw(lowered(p, under), buffer, ChainDrawing.wrap(WRAP_POST, WRAP_POST, 8, light));
                    }
                }
                for (List<ChainDrawing.Link> links : state.chains) {
                    ChainDrawing.draw(p, buffer, links);
                }
            });
        }
        super.submit(state, pose, collector, camera);
    }

    /** Turned as the bracket's model is (its brace laid along z, then tipped about x), for what is wound round it. */
    private static PoseStack.Pose brace(PoseStack.Pose pose, Direction facing) {
        PoseStack.Pose turned = pose.copy();
        turned.rotate(Axis.YP.rotationDegrees(180 - facing.toYRot()));
        turned.rotate(Axis.XP.rotationDegrees(SupportBlock.BRACE_ANGLE));
        return turned;
    }

    /** A rod on a wall lies along the way it points: turned so, for what is wound round it. */
    private static PoseStack.Pose rod(PoseStack.Pose pose, Direction facing) {
        PoseStack.Pose turned = pose.copy();
        turned.rotate(facing.getRotation());
        return turned;
    }

    /** {@code pose} moved {@code by} along its y axis (none: itself). */
    private static PoseStack.Pose lowered(PoseStack.Pose pose, float by) {
        if (by == 0) {
            return pose;
        }
        PoseStack.Pose moved = pose.copy();
        moved.translate(0, by, 0);
        return moved;
    }

    /** Seen whenever any of its rope is: the spans reach well past the knot, and a chain swings out a little. */
    @Override
    protected AABB getBoundingBoxForCulling(RopeKnotEntity knot, float partialTicks) {
        AABB box = knot.getBoundingBox();
        for (BlockPos to : knot.spans()) {
            AABB span = RopeSpans.bounds(knot.level(), knot.getPos(), to);
            box = box.minmax(knot.material(to) == SpanMaterial.CHAIN ? span.inflate(span.getYsize() * 0.5, 0, span.getYsize() * 0.5) : span);
        }
        Entity holder = knot.holder();
        return holder == null ? box : box.minmax(holder.getBoundingBox());
    }
}
