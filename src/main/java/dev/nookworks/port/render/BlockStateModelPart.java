package dev.nookworks.port.render;

import java.util.ArrayList;
import java.util.List;

import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * 26.3's {@code BlockStateModelPart}: the quads of a model, by the side they face (null: the ones that face no side). A
 * 1.21.1 baked model, and the block state it was baked for (null for a model loaded on its own), its quads read with the
 * model's own render types.
 * <p>
 * 26.3 knows each quad's layer (solid, cut out, see-through) from the quad itself; a 1.21.1 model has its render types
 * for the whole of it, from its JSON's {@code render_type} ({@link #translucent}).
 */
public final class BlockStateModelPart {
    private static final Direction[] SIDES = Direction.values();
    private final BakedModel model;
    private final @Nullable BlockState state;
    private @Nullable Boolean translucent;

    public BlockStateModelPart(BakedModel model) {
        this(model, null);
    }

    public BlockStateModelPart(BakedModel model, @Nullable BlockState state) {
        this.model = model;
        this.state = state;
    }

    public BakedModel model() {
        return this.model;
    }

    /** One of the model's quads, read as 26.3's are: a corner's place. */
    public record Quad(BakedQuad quad) {
        /** Corner {@code vertex} (0-3), in the model's block space. */
        public Vector3f position(int vertex) {
            int[] data = this.quad.getVertices();
            int stride = data.length / 4;
            int at = vertex * stride;
            return new Vector3f(Float.intBitsToFloat(data[at]), Float.intBitsToFloat(data[at + 1]), Float.intBitsToFloat(data[at + 2]));
        }

        public int tintIndex() {
            return this.quad.getTintIndex();
        }
    }

    public List<Quad> getQuads(@Nullable Direction side) {
        List<Quad> quads = new ArrayList<>();
        for (BakedQuad quad : this.rawQuads(side)) {
            quads.add(new Quad(quad));
        }
        return quads;
    }

    /** The 1.21.1 quads facing {@code side}, as the model gives them (the same seed every time, as 26.3's parts are fixed). */
    public List<BakedQuad> rawQuads(@Nullable Direction side) {
        RandomSource random = RandomSource.create(42L);
        return this.model.getQuads(this.state, side, random, ModelData.EMPTY, null);
    }

    /** Every quad, the sides' and the unculled ones. */
    public List<BakedQuad> allQuads() {
        List<BakedQuad> all = new ArrayList<>();
        for (Direction side : SIDES) {
            all.addAll(this.rawQuads(side));
        }
        all.addAll(this.rawQuads(null));
        return all;
    }

    /** Whether the model says it is drawn see-through ({@code "render_type": "minecraft:translucent"}). */
    public boolean translucent() {
        if (this.translucent == null) {
            BlockState asked = this.state != null ? this.state : Blocks.AIR.defaultBlockState();
            boolean found;
            try {
                found = this.model.getRenderTypes(asked, RandomSource.create(42L), ModelData.EMPTY).contains(RenderType.translucent());
            } catch (RuntimeException unreadable) {
                found = false;
            }
            this.translucent = found;
        }
        return this.translucent;
    }
}
