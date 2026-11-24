package dev.nookworks.dev.bot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.common.Tags;

/**
 * DEVELOPMENT ONLY: the agent bot's big dig ({@code bot excavate}), worked out here, out of the client package, so the
 * GameTests check it headlessly (BotHallTests). For the YouTube episode 「AIと一緒にサバイバル #2」 (the user, 2026-11-21):
 * a hall 41 wide and 41 high dug at about y=0, as far along as it goes, by NookAI alone through the night.
 * <p>
 * The box is dug from the top down, a layer three high at a time (the bottom one two or one high when the height is not a
 * multiple of three), each layer in lanes three wide running along the box's longer side, there and back; every step one
 * hammer blow at the middle of the 3x3 in front ({@link Stairs#next}, a tunnel's step), so the cells a blow takes are
 * always inside the box. From one lane to the next it turns with blows to the side, a layer ends with a stair three steps
 * down along the lane it ended in, and the corner the stair leaves is dug back before the next layer's lanes go on.
 * The first blow is struck from the entry, the cell outside the box before the first lane's first column, level with the
 * top layer's floor: where the way in (a stair, a tunnel) must end.
 * <p>
 * What the dig opens is looked round ({@link #toSeal}): water and lava beside or over it, a source left in it, and holes
 * in the box's walls, ceiling and floor (a cave, gravel fallen out of the ceiling) are filled with a block, so the hall
 * stays a closed, dry box that mobs do not walk into; and a torch goes on each layer's floor every twelve blocks each way
 * ({@link #torchAt}), so no floor it leaves is dark.
 */
public final class Hall {
    /** The box, corners in any order. */
    public record Box(int x1, int y1, int z1, int x2, int y2, int z2) {
        public static Box of(int ax, int ay, int az, int bx, int by, int bz) {
            return new Box(Math.min(ax, bx), Math.min(ay, by), Math.min(az, bz), Math.max(ax, bx), Math.max(ay, by), Math.max(az, bz));
        }

        public boolean contains(BlockPos p) {
            return p.getX() >= this.x1 && p.getX() <= this.x2 && p.getY() >= this.y1 && p.getY() <= this.y2 && p.getZ() >= this.z1
                    && p.getZ() <= this.z2;
        }

        public int sizeX() {
            return this.x2 - this.x1 + 1;
        }

        public int sizeY() {
            return this.y2 - this.y1 + 1;
        }

        public int sizeZ() {
            return this.z2 - this.z1 + 1;
        }

        public long volume() {
            return (long) this.sizeX() * this.sizeY() * this.sizeZ();
        }

        @Override
        public String toString() {
            return this.x1 + " " + this.y1 + " " + this.z1 + " " + this.x2 + " " + this.y2 + " " + this.z2;
        }
    }

    /** What a move is for: the first blow into the box, a lane's step, a turn to the next lane, the stair down to the
     *  next layer, the corner that stair left, dug back. */
    public enum Part { ENTRY, LANE, TURN, DOWN, BACK, IN, UP }

    /** One blow (and what it leaves, a block at a time): stood at {@code step.from()}, facing {@code step.dir()}.
     *  {@code segment}: the section and layer it belongs to, as {@code section * layers + layer} (a layer of a section
     *  is looked over when the dig moves on from it; the climb into a section counts as its top layer's). */
    public record Move(int index, Part part, int layer, int section, int segment, Stairs.Step step) {
        public BlockPos stand() {
            return this.step.from();
        }

        public List<BlockPos> open() {
            return this.step.open();
        }
    }

    /** A layer: feet at {@code bottom} (its floor the block under), up to {@code top}. */
    public record Layer(int bottom, int top) {}

    public final Box box;
    /** The way the first lane goes, from the entry end. */
    public final Direction forward;
    /** The way from the first lane to the next. */
    public final Direction side;
    /** Cells across (along {@link #side}) and along (along {@link #forward}). */
    public final int width;
    public final int length;
    /** The box's corner at the entry end, on the first lane's side. */
    private final BlockPos origin;
    public final List<Layer> layers;
    /** The lanes' middles, across. */
    public final int[] lanes;
    private final int[] torchAcross;
    private final int[] torchAlong;
    /** The sections along, each dug from its top to its floor before the next: first and last column. */
    public final List<int[]> sections;
    public final int sectionLength;
    public final List<Move> moves;

    public Hall(Box box, Direction forward, Direction side) {
        this(box, forward, side, 0);
    }

    /**
     * {@code section}: dug in sections that many along (0: the whole length at once), each from its top to its floor
     * before the next, so that whenever the dig is stopped what is done is the hall at its full height. Into each
     * section after the first it climbs a stair up across the section's first three columns, from the floor at the
     * first lane's end to the top layer at the last lane's middle (so the box must be at least as wide as it is high),
     * which the layers then dig away from the top down: the stair's foot, below the layer under way, is the way from the
     * floor up to it.
     */
    public Hall(Box box, Direction forward, Direction side, int section) {
        this(box, forward, side, section, false);
    }

    /** Whether each section's stair up comes out at the first lane's end (the side the dig started from) rather than the
     *  last's: the other end was under an aquifer, and NookAI drowned at its head (2026-11-22). */
    public final boolean climbBack;

    public Hall(Box box, Direction forward, Direction side, int section, boolean climbBack) {
        this.climbBack = climbBack;
        if (!forward.getAxis().isHorizontal() || !side.getAxis().isHorizontal() || forward.getAxis() == side.getAxis()) {
            throw new IllegalArgumentException("forward and side must be two horizontal directions at a right angle");
        }
        this.box = box;
        this.forward = forward;
        this.side = side;
        this.width = forward.getAxis() == Direction.Axis.X ? box.sizeZ() : box.sizeX();
        this.length = forward.getAxis() == Direction.Axis.X ? box.sizeX() : box.sizeZ();
        if (this.width < 3 || this.length < 4 || box.sizeY() < 3) {
            throw new IllegalArgumentException("a hall is at least 3 across, 4 along and 3 high: this box is " + box.sizeX() + " x "
                    + box.sizeY() + " x " + box.sizeZ());
        }
        int ox;
        int oz;
        if (forward.getAxis() == Direction.Axis.X) {
            ox = forward == Direction.EAST ? box.x1() : box.x2();
            oz = side == Direction.SOUTH ? box.z1() : box.z2();
        } else {
            oz = forward == Direction.SOUTH ? box.z1() : box.z2();
            ox = side == Direction.EAST ? box.x1() : box.x2();
        }
        this.origin = new BlockPos(ox, box.y1(), oz);
        this.layers = layers(box.y1(), box.y2());
        this.lanes = lanes(this.width);
        this.torchAcross = spots(this.width);
        this.sectionLength = section <= 0 || section >= this.length ? this.length : Math.max(4, section);
        this.sections = new ArrayList<>();
        for (int ja = 0; ja < this.length;) {
            int jb = Math.min(this.length - 1, ja + this.sectionLength - 1);
            if (this.length - 1 - jb < 4) {
                jb = this.length - 1; // a short end goes with the section before it
            }
            this.sections.add(new int[] {ja, jb});
            ja = jb + 1;
        }
        // torches along each section by itself (a short section between two of the whole length's places was left dark)
        List<Integer> along = new ArrayList<>();
        for (int[] sec : this.sections) {
            for (int j : spots(sec[1] - sec[0] + 1)) {
                along.add(sec[0] + j);
            }
        }
        this.torchAlong = along.stream().mapToInt(Integer::intValue).toArray();
        if (this.sections.size() > 1 && this.width < box.sizeY()) {
            throw new IllegalArgumentException("sections need a box at least as wide as it is high (the stair up into each): "
                    + this.width + " across, " + box.sizeY() + " high");
        }
        this.moves = this.makeMoves();
    }

    /**
     * A hall for the box, its lanes along the longer side (along z if square), dug from the end and the side nearer
     * {@code from} (where the bot is, or where its way in ends).
     */
    public static Hall plan(Box box, BlockPos from) {
        return plan(box, from, 0);
    }

    public static Hall plan(Box box, BlockPos from, int section) {
        boolean alongX = box.sizeX() > box.sizeZ();
        Direction forward;
        Direction side;
        double midX = (box.x1() + box.x2()) / 2.0;
        double midZ = (box.z1() + box.z2()) / 2.0;
        if (alongX) {
            forward = from.getX() <= midX ? Direction.EAST : Direction.WEST;
            side = from.getZ() <= midZ ? Direction.SOUTH : Direction.NORTH;
        } else {
            forward = from.getZ() <= midZ ? Direction.SOUTH : Direction.NORTH;
            side = from.getX() <= midX ? Direction.EAST : Direction.WEST;
        }
        return new Hall(box, forward, side, section);
    }

    /**
     * A hall straight ahead of {@code feet}, the way in: {@code width} across (its first lane centred on the feet, the
     * rest toward {@code side}), {@code height} high (the top layer's floor under the feet) and {@code length} along.
     */
    public static Hall ahead(BlockPos feet, Direction forward, Direction side, int width, int height, int length) {
        return ahead(feet, forward, side, width, height, length, 0);
    }

    /** The same, dug in sections ({@link #Hall(Box, Direction, Direction, int)}). */
    public static Hall ahead(BlockPos feet, Direction forward, Direction side, int width, int height, int length, int section) {
        int top = feet.getY() + 2;
        BlockPos a = feet.relative(side.getOpposite()).relative(forward).atY(top - height + 1);
        BlockPos b = feet.relative(side, width - 2).relative(forward, length).atY(top);
        Hall hall = new Hall(Box.of(a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ()), forward, side, section);
        if (!hall.entry().equals(feet)) {
            throw new IllegalStateException("the way in came out at " + hall.entry() + ", not " + feet);
        }
        return hall;
    }

    /** Layers from the top down: three high, the last what is left (two or one high). */
    static List<Layer> layers(int y1, int y2) {
        List<Layer> out = new ArrayList<>();
        int top = y2;
        while (top >= y1) {
            int bottom = Math.max(y1, top - 2);
            out.add(new Layer(bottom, top));
            top = bottom - 1;
        }
        return out;
    }

    /** Lane middles across a width: 1, 4, 7..., and the last one against the far side (overlapping the one before). */
    static int[] lanes(int width) {
        List<Integer> out = new ArrayList<>();
        int c = 1;
        while (c + 1 <= width - 1) {
            out.add(c);
            c += 3;
        }
        if (out.get(out.size() - 1) + 1 < width - 1) {
            out.add(width - 2);
        }
        return out.stream().mapToInt(Integer::intValue).toArray();
    }

    /** Torch places along a side: every 12, 3 in from the start, and one more if the far end is left over 6 from one. */
    static int[] spots(int n) {
        List<Integer> out = new ArrayList<>();
        int s = Math.min(3, (n - 1) / 2);
        while (s <= n - 1) {
            out.add(s);
            s += 12;
        }
        int last = out.get(out.size() - 1);
        if (n - 1 - last > 6) {
            out.add(n - 1 - 3);
        }
        return out.stream().mapToInt(Integer::intValue).toArray();
    }

    /** The cell {@code across} along {@link #side} and {@code along} along {@link #forward} from the origin corner. */
    public BlockPos cell(int across, int y, int along) {
        return new BlockPos(this.origin.getX() + this.side.getStepX() * across + this.forward.getStepX() * along, y,
                this.origin.getZ() + this.side.getStepZ() * across + this.forward.getStepZ() * along);
    }

    public int across(BlockPos p) {
        return (p.getX() - this.origin.getX()) * this.side.getStepX() + (p.getZ() - this.origin.getZ()) * this.side.getStepZ();
    }

    public int along(BlockPos p) {
        return (p.getX() - this.origin.getX()) * this.forward.getStepX() + (p.getZ() - this.origin.getZ()) * this.forward.getStepZ();
    }

    private Direction alongDir(int sign) {
        return sign > 0 ? this.forward : this.forward.getOpposite();
    }

    private Direction acrossDir(int sign) {
        return sign > 0 ? this.side : this.side.getOpposite();
    }

    /** Where the first blow is struck from: outside the box, before the first lane's first column, level with the top
     *  layer's floor. The way in ends here. */
    public BlockPos entry() {
        return this.cell(this.lanes[0], this.layers.get(0).bottom(), -1);
    }

    /** The entry's doorway: the 3x3 of the end wall the way in comes through - left open. */
    public boolean doorway(BlockPos p) {
        int a = this.across(p);
        int top = this.layers.get(0).bottom();
        return this.along(p) == -1 && a >= this.lanes[0] - 1 && a <= this.lanes[0] + 1 && p.getY() >= top && p.getY() <= top + 2;
    }

    /** Whether the walker may break this block while the hall is dug: in the box, or in the end wall the way in comes
     *  through (back in from its own stair beside it, after a death) - never the side walls, the ceiling or the floor. */
    public boolean mayDig(BlockPos p) {
        if (this.box.contains(p)) {
            return true;
        }
        int a = this.across(p);
        return this.along(p) == -1 && a >= 0 && a < this.width && p.getY() >= this.box.y1() && p.getY() <= this.box.y2();
    }

    private int section;
    private int segment;

    private void add(List<Move> out, Part part, int layer, Stairs.Kind kind, BlockPos stand, Direction dir) {
        Stairs.Step s = Stairs.next(kind, stand, dir);
        List<BlockPos> open = new ArrayList<>(9);
        for (BlockPos p : s.open()) {
            if (this.box.contains(p)) {
                open.add(p);
            }
        }
        s = new Stairs.Step(s.kind(), s.from(), s.dir(), List.copyOf(open), s.aim(), s.face(), s.floor(), s.to());
        out.add(new Move(out.size(), part, layer, this.section, this.segment, s));
    }

    /** A lane's steps: stood at {@code standAlong}, then at each column dug, to the end of the section. */
    private void lane(List<Move> out, Part first, int layer, int across, int feet, int standAlong, int dir, int ja, int jb) {
        Part part = first;
        for (int s = standAlong; s + dir >= ja && s + dir <= jb; s += dir) {
            this.add(out, part, layer, Stairs.Kind.TUNNEL, this.cell(across, feet, s), this.alongDir(dir));
            part = Part.LANE;
        }
    }

    private List<Move> makeMoves() {
        List<Move> out = new ArrayList<>();
        for (int s = 0; s < this.sections.size(); s++) {
            this.section = s;
            this.makeSection(out, s, this.sections.get(s)[0], this.sections.get(s)[1]);
        }
        return out;
    }

    private void makeSection(List<Move> out, int s, int ja, int jb) {
        int n = this.lanes.length;
        int lane;
        int laneDir;
        int sideSign;
        int feet = this.layers.get(0).bottom();
        this.segment = s * this.layers.size();
        if (s == 0) {
            lane = 0;
            laneDir = 1;
            sideSign = 1;
            this.lane(out, Part.ENTRY, 0, this.lanes[0], feet, ja - 1, 1, ja, jb);
        } else {
            // in from the last section's floor, two columns, at the foot of a stair up across this section's first
            // three, which comes out on the top layer at the last lane's middle
            int floor = this.box.y1();
            int last = this.layers.size() - 1;
            // up toward the last lane, or (climbBack) back toward the first: the end the stair comes out at
            int up = this.climbBack ? -1 : 1;
            int head = this.climbBack ? 0 : n - 1;
            int foot = this.lanes[head] - up * (feet - floor);
            this.add(out, Part.IN, last, Stairs.Kind.TUNNEL, this.cell(foot, floor, ja - 1), this.alongDir(1));
            this.add(out, Part.IN, last, Stairs.Kind.TUNNEL, this.cell(foot, floor, ja), this.alongDir(1));
            int f = floor;
            for (int i = foot; f < feet; i += up, f++) {
                this.add(out, Part.UP, this.layerAt(f + 1), Stairs.Kind.UP, this.cell(i, f, ja + 1), this.acrossDir(up));
            }
            lane = head;
            sideSign = -up;
            int c = this.lanes[lane];
            // the column behind the stair's head, then the last lane on
            this.add(out, Part.BACK, 0, Stairs.Kind.TUNNEL, this.cell(c, feet, ja + 1), this.alongDir(-1));
            laneDir = 1;
            this.lane(out, Part.LANE, 0, c, feet, ja, 1, ja, jb);
        }
        int end = laneDir > 0 ? jb : ja;
        for (int li = 0; li < this.layers.size(); li++) {
            Layer layer = this.layers.get(li);
            this.segment = s * this.layers.size() + li;
            if (li > 0) {
                // down a stair along the lane it ended in, back from the corner, to the new layer's floor
                int c = this.lanes[lane];
                int drop = feet - layer.bottom();
                int j = end;
                int f = feet;
                for (int k = 0; k < drop; k++) {
                    this.add(out, Part.DOWN, li, Stairs.Kind.STAIR, this.cell(c, f, j), this.alongDir(-laneDir));
                    j -= laneDir;
                    f--;
                }
                feet = layer.bottom();
                int foot = j;
                // the corner the stair left over its foot, dug back
                for (int st = foot; st != end; st += laneDir) {
                    this.add(out, Part.BACK, li, Stairs.Kind.TUNNEL, this.cell(c, feet, st), this.alongDir(laneDir));
                }
                laneDir = -laneDir;
                this.lane(out, Part.LANE, li, c, feet, foot, laneDir, ja, jb);
                end = laneDir > 0 ? jb : ja;
                sideSign = -sideSign;
            }
            while (lane + sideSign >= 0 && lane + sideSign < n) {
                int next = lane + sideSign;
                int from = this.lanes[lane];
                int to = this.lanes[next];
                int standAlong = end - laneDir;
                // a strip three along at the lane's end, dug sideways a column at a time, into the next lane
                for (int st = from + sideSign; st != to + sideSign; st += sideSign) {
                    this.add(out, Part.TURN, li, Stairs.Kind.TUNNEL, this.cell(st, feet, standAlong), this.acrossDir(sideSign));
                }
                laneDir = -laneDir;
                this.lane(out, Part.LANE, li, to, feet, end + 2 * laneDir, laneDir, ja, jb);
                end = laneDir > 0 ? jb : ja;
                lane = next;
            }
        }
    }

    /** The layer whose floor the feet stand on at this height (-1 if none). */
    public int layerAt(int feetY) {
        for (int i = 0; i < this.layers.size(); i++) {
            Layer l = this.layers.get(i);
            if (feetY >= l.bottom() && feetY <= l.top()) {
                return i;
            }
        }
        return -1;
    }

    /** The torch place among the cells this move opens, on its layer's floor: null if none. */
    public @Nullable BlockPos torchAt(Move m) {
        int feet = this.layers.get(m.layer()).bottom();
        for (BlockPos p : m.open()) {
            if (p.getY() == feet && this.torchSpot(p)) {
                return p;
            }
        }
        return null;
    }

    public boolean torchSpot(BlockPos p) {
        return contains(this.torchAcross, this.across(p)) && contains(this.torchAlong, this.along(p));
    }

    private static boolean contains(int[] a, int v) {
        for (int x : a) {
            if (x == v) {
                return true;
            }
        }
        return false;
    }

    /** Every cell of a section's layer, {@code segment} as in {@link Move#segment} (for the look round it when it is
     *  done: what fell in, what was left). */
    public List<BlockPos> layerCells(int segment) {
        Layer l = this.layers.get(segment % this.layers.size());
        int[] sec = this.sections.get(segment / this.layers.size());
        List<BlockPos> out = new ArrayList<>();
        for (int y = l.top(); y >= l.bottom(); y--) {
            for (int j = sec[0]; j <= sec[1]; j++) {
                for (int i = 0; i < this.width; i++) {
                    out.add(this.cell(i, y, j));
                }
            }
        }
        return out;
    }

    /** The torch places on a section's layer's floor ({@code segment} as in {@link Move#segment}). */
    public List<BlockPos> torchPlaces(int segment) {
        int feet = this.layers.get(segment % this.layers.size()).bottom();
        int[] sec = this.sections.get(segment / this.layers.size());
        List<BlockPos> out = new ArrayList<>();
        for (int j : this.torchAlong) {
            if (j < sec[0] || j > sec[1]) {
                continue;
            }
            for (int i : this.torchAcross) {
                out.add(this.cell(i, feet, j));
            }
        }
        return out;
    }

    /** What a cell to fill holds, for the counts: lava, water, or a hole (air, a plant). */
    public enum Seal { LAVA, WATER, HOLE }

    public static Seal sealKind(BlockGetter level, BlockPos p) {
        FluidState f = level.getFluidState(p);
        if (f.isEmpty()) {
            return Seal.HOLE;
        }
        return f.is(FluidTags.LAVA) ? Seal.LAVA : Seal.WATER;
    }

    /**
     * The cells to fill with a block round what was just opened, lava first, then water, then holes. A source in the
     * opened cells themselves (a pocket inside the box: filled, and dug again); round them, inside the box, a source
     * beside or above (one below cannot rise: the layer under it deals with it); outside the box - the walls, the
     * ceiling, the floor - any water or lava, and any hole (air, a plant: a cave, gravel fallen out of the ceiling), but
     * in the end wall the way in comes through (its doorway, a stair dug along it to the floor).
     */
    public List<BlockPos> toSeal(BlockGetter level, Collection<BlockPos> opened) {
        Set<BlockPos> inOpen = Set.copyOf(opened);
        Set<BlockPos> lava = new LinkedHashSet<>();
        Set<BlockPos> water = new LinkedHashSet<>();
        Set<BlockPos> holes = new LinkedHashSet<>();
        for (BlockPos o : opened) {
            FluidState here = level.getFluidState(o);
            if (here.isSource()) {
                (here.is(FluidTags.LAVA) ? lava : water).add(o);
            }
            for (Direction d : Direction.values()) {
                BlockPos n = o.relative(d);
                if (inOpen.contains(n)) {
                    continue;
                }
                FluidState f = level.getFluidState(n);
                boolean inside = this.box.contains(n);
                if (!f.isEmpty()) {
                    if (inside && (d == Direction.DOWN || !f.isSource())) {
                        continue; // below, it cannot rise; flowing in the hall, it drains once its source is shut
                    }
                    (f.is(FluidTags.LAVA) ? lava : water).add(n);
                } else if (!inside && this.along(n) != -1 && Stairs.passable(level, n, level.getBlockState(n))) {
                    holes.add(n);
                }
            }
        }
        List<BlockPos> out = new ArrayList<>(lava);
        out.addAll(water);
        out.addAll(holes);
        return out;
    }

    /** Whether water or lava is in a cell or next to it (the dry dig leaves such cells be). */
    public static boolean wetBy(BlockGetter level, BlockPos p) {
        if (!level.getFluidState(p).isEmpty()) {
            return true;
        }
        for (Direction d : Direction.values()) {
            if (!level.getFluidState(p.relative(d)).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** Whether water or lava is in or beside a step's cells (the dry dig passes such a step over). */
    public static boolean wet(BlockGetter level, Stairs.Step s) {
        for (BlockPos p : s.open()) {
            if (wetBy(level, p)) {
                return true;
            }
        }
        return false;
    }

    /** Whether lava is in or beside a step's cells (then it is dug a block at a time, each lava cell shut as it shows). */
    public static boolean lavaBy(BlockGetter level, Stairs.Step s) {
        for (BlockPos p : s.open()) {
            if (level.getFluidState(p).is(FluidTags.LAVA)) {
                return true;
            }
        }
        for (BlockPos p : Stairs.shell(s)) {
            if (level.getFluidState(p).is(FluidTags.LAVA)) {
                return true;
            }
        }
        return false;
    }

    /** What an ore is called in the counts: iron for iron ore and deepslate iron ore alike. */
    public static @Nullable String oreName(BlockState state) {
        if (!state.is(Tags.Blocks.ORES)) {
            return null;
        }
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return path.replace("deepslate_", "").replace("_ore", "").replace("nether_", "nether ");
    }
}
