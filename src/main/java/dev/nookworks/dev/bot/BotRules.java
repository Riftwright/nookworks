package dev.nookworks.dev.bot;

import java.util.List;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * DEVELOPMENT ONLY: the agent bot's (dev.nookworks.client.dev.bot.Bot) judgements that need no game to be made - kept
 * here, out of the client package, so the GameTests check them headlessly (BotRulesTests).
 */
public final class BotRules {
    private BotRules() {}

    /** Three hearts: below this it stops fighting (it fought a skeleton down to 1.5 health with apples in its bag, and
     *  died, beside the user on 2026-10-21). */
    public static final float LOW_HEALTH = 6.0F;
    /** How near a mob must be for it to back off rather than stop to eat. */
    public static final double BACK_OFF_FROM = 10.0;
    /** Once eating, how near a mob must come to stop it (nearer than {@link #BACK_OFF_FROM}: a mob hovering at the edge
     *  turned it from eating to running and back every few ticks). */
    public static final double STOP_EATING_FROM = 5.0;

    public enum Survive {
        /** Healthy enough: fight, work, as it was. */
        CARRY_ON,
        /** Hurt, a mob near: get away from it first. */
        BACK_OFF,
        /** Hurt, nothing near: eat to heal. */
        EAT,
        /** Hurt, cornered (backing off just failed): stand and fight, nothing better to do. */
        FIGHT
    }

    /**
     * What to do about its health. {@code threat}: the distance to the nearest hostile mob (infinite: none);
     * {@code canEat}: there is food it can eat now (hungry enough, or a golden apple); {@code cornered}: backing off just
     * failed; {@code doing}: what it is at now (BACK_OFF running, EAT eating, else CARRY_ON) - kept to until done unless
     * a mob comes close, so it does not flicker between the two.
     */
    public static Survive survive(float health, double threat, boolean canEat, boolean cornered, Survive doing) {
        if (health > LOW_HEALTH) {
            return Survive.CARRY_ON;
        }
        if (doing == Survive.BACK_OFF) {
            return Survive.BACK_OFF;
        }
        if (threat < (doing == Survive.EAT ? STOP_EATING_FROM : BACK_OFF_FROM)) {
            return cornered ? Survive.FIGHT : Survive.BACK_OFF;
        }
        return canEat || doing == Survive.EAT ? Survive.EAT : Survive.CARRY_ON;
    }

    /** A weapon worth stepping in with: a wooden or gold sword (4) and up; bare hands are 1. */
    public static final double WEAPON = 4.0;

    /**
     * Whether it may step in to fight (defending the companion, a mob close by, an archer): with a weapon, or, bare
     * handed, only well armoured and near full health. Bare handed beside the user after a respawn, it stepped in
     * against zombies and spiders and died three times running (2026-10-21).
     */
    public static boolean mayFight(double weaponDamage, float health, double armor) {
        if (health <= LOW_HEALTH) {
            return false;
        }
        return weaponDamage >= WEAPON || health >= 16.0F && armor >= 10.0;
    }

    /** Where it goes, bare handed, from a mob close by. */
    public enum Retreat {
        /** Into the home it was given (a room: the hut). */
        HOME,
        /** To the companion's side. */
        COMPANION,
        /** Away, over dry land. */
        FLEE,
        /** Underground: up to the surface over it (away, in a cave, was deeper in: it died twice so, 2026-10-22). */
        SURFACE,
        /** Stay: it is home, or beside the companion, already - it fights only what reaches it. */
        HOLD
    }

    /** How near home (the companion) counts as there; how far it goes back to them rather than running off. */
    public static final double AT_HOME = 4.0;
    public static final double HOME_REACH = 64.0;
    public static final double COMPANION_REACH = 32.0;

    /**
     * Where to retreat to, by the distances to home and to the companion (infinite: none). Running off away from each
     * mob in turn took it, leg by leg, out of the hut's door and into the sea, where it drowned (2026-10-21).
     */
    public static Retreat retreat(double fromHome, double fromCompanion) {
        return retreat(fromHome, fromCompanion, false);
    }

    /** The same, {@code underground}: never away along a cave - up to the surface (home, or the companion, if near). */
    public static Retreat retreat(double fromHome, double fromCompanion, boolean underground) {
        if (fromHome <= AT_HOME || fromCompanion <= 3.0) {
            return Retreat.HOLD;
        }
        if (fromHome <= HOME_REACH) {
            return Retreat.HOME;
        }
        if (fromCompanion <= COMPANION_REACH) {
            return Retreat.COMPANION;
        }
        return underground ? Retreat.SURFACE : Retreat.FLEE;
    }

    /** Below the surface: under ground at least six blocks thick over the feet ({@code top}: the column's top), or well
     *  below sea level (a ravine's floor is open to the sky, and as deep as any cave). */
    public static boolean underground(int feetY, int top, int seaLevel) {
        return feetY < top - 6 || feetY < seaLevel - 8;
    }

    /** How far apart it sets torches in the dark. */
    public static final int TORCH_EVERY = 8;

    /**
     * Whether to set a torch at its feet: underground, in the dark (block light 7 or less), no torch within
     * {@link #TORCH_EVERY} of the last it set, and it has one. Lit, a cave's mobs cannot spawn about it.
     */
    public static boolean wantsTorch(boolean underground, int blockLight, double fromLastTorch, boolean hasTorch) {
        return underground && hasTorch && blockLight <= 7 && fromLastTorch >= TORCH_EVERY;
    }

    /** Night, when the dark's mobs are about (day time, ticks of 24000): from a little after sunset to dawn. */
    public static boolean night(long dayTime) {
        long t = Math.floorMod(dayTime, 24000L);
        return t >= 12500 && t < 23500;
    }

    /**
     * Whether a retreat into home ends the jobs it broke off rather than putting them aside: at night, or badly hurt -
     * taken up again, they walk it straight back out to what it ran from. At night, starving and bare handed, it went
     * home, shut the door and went out again to explore, three times in ten seconds, until a zombie killed it in the
     * doorway (2026-10-22).
     */
    public static boolean retreatEndsJobs(Retreat retreat, long dayTime, float health) {
        return retreat == Retreat.HOME && (night(dayTime) || health <= LOW_HEALTH);
    }

    /** Air (of 300) below which it stops what it does and swims up. */
    public static final int LOW_AIR = 120;

    public static boolean needsAir(int air, boolean underWater) {
        return underWater && air < LOW_AIR;
    }

    /** Whether feet in this cell are on dry land: neither the cell nor the one under it is water (a place to run to;
     *  a run's end in the sea was the next run's start). */
    public static boolean dry(BlockGetter level, int x, int y, int z) {
        return level.getFluidState(new BlockPos(x, y, z)).isEmpty() && level.getFluidState(new BlockPos(x, y - 1, z)).isEmpty();
    }

    /**
     * Whether a block can be got at only from under water: every open face of it (a neighbour it could be mined from)
     * is water. A lake bed's stone is; a shore's stone, or stone deep in the rock, is not. It drowned mining stone off a
     * lake bed (2026-10-21).
     */
    public static boolean onlyUnderWater(BlockGetter level, BlockPos pos) {
        boolean open = false;
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
            BlockPos n = pos.relative(d);
            BlockState s = level.getBlockState(n);
            if (!level.getFluidState(n).isEmpty()) {
                open = true;
            } else if (s.canBeReplaced() || s.getCollisionShape(level, n).isEmpty()) {
                return false; // a face open to the air
            }
        }
        return open;
    }

    /**
     * Whether a block holds up a build: the block over it (or the one over that) is solid and not of the land - planks,
     * bricks, a chest - not stone, dirt, a tree or a plant. Mining stone by the hut, the walker dug the dirt under its
     * plank floor (2026-10-22); the ground under someone's floor or wall is left.
     */
    public static boolean underBuild(BlockGetter level, BlockPos pos, Predicate<BlockState> natural) {
        for (int up = 1; up <= 2; up++) {
            BlockPos p = pos.above(up);
            BlockState s = level.getBlockState(p);
            if (s.isAir() || !s.getFluidState().isEmpty()) {
                return false;
            }
            boolean land = natural.test(s) || s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES) || s.is(net.minecraft.tags.BlockTags.REPLACEABLE)
                    || s.getCollisionShape(level, p).isEmpty() || s.is(net.minecraft.world.level.block.Blocks.BEDROCK)
                    || s.is(net.neoforged.neoforge.common.Tags.Blocks.ORES);
            if (!land) {
                return true;
            }
        }
        return false;
    }

    /** Whether feet in this cell stand in a doorway (a door in the cell or the one above): no place to idle in, half in
     *  and half out (it stood in the hut's doorway at night as the companion, 2026-10-21). */
    public static boolean doorway(BlockGetter level, int x, int y, int z) {
        return level.getBlockState(new BlockPos(x, y, z)).getBlock() instanceof net.minecraft.world.level.block.DoorBlock
                || level.getBlockState(new BlockPos(x, y + 1, z)).getBlock() instanceof net.minecraft.world.level.block.DoorBlock;
    }

    /** From when the sun sets to when the night's mobs come (day time, ticks of 24000). */
    public static final long DUSK = 12000;
    public static final long NIGHT = 13000;
    /** How far from home a job at dusk is broken off for the way back. */
    public static final double FAR_FROM_HOME = 32.0;

    /** Whether, at this time of day and this far from home, the job under way should be broken off for the way back
     *  (it died cutting leaves in the forest at dusk, 2026-10-21). Once a day: the caller keeps count. */
    public static boolean duskCall(long dayTime, double fromHome) {
        long t = Math.floorMod(dayTime, 24000L);
        return t >= DUSK && t < NIGHT + 1000 && fromHome > FAR_FROM_HOME;
    }

    /** How far above the ground a goto may aim before it is taken for a mistake (three numbers read as X Y Z when
     *  X Z R was meant: {@code goto -22 252 4} sent it toward y=252). */
    public static final int HIGH_ABOVE_GROUND = 24;

    /**
     * Why a goto's height looks wrong, or null if it does not. {@code ground}: the top of the column there
     * ({@link Integer#MIN_VALUE}: not known, the chunk is not loaded).
     */
    public static @Nullable String gotoHeightProblem(int x, int y, int z, int minY, int maxY, int ground) {
        String hint = " (for any height there: goto " + x + " " + z + ", or goto " + x + " ~ " + z + " R)";
        if (y < minY || y > maxY) {
            return "y=" + y + " is outside the world (" + minY + " to " + maxY + ")" + hint;
        }
        if (ground != Integer.MIN_VALUE && y > ground + HIGH_ABOVE_GROUND) {
            return "y=" + y + " is " + (y - ground) + " blocks above the ground there (y=" + ground + ")" + hint;
        }
        return null;
    }

    /**
     * Whether feet in this cell are out on the surface, for {@code goto X Z} (any height): no ground (stone, dirt -
     * {@code ground}, leaves and logs aside) over it up to {@code top}, the column's highest block, and not under
     * water. A cave floor is not (it took one 40 blocks down, 2026-10-21, and fell to 0.4 health there); a house's
     * floor is (planks over it), and so are a tree's shade and a water surface.
     */
    public static boolean onSurface(BlockGetter level, int x, int y, int z, int top, Predicate<BlockState> ground) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, y + 2, z);
        if (!level.getFluidState(p).isEmpty()) {
            return false;
        }
        for (int h = y + 2; h <= top; h++) {
            p.setY(h);
            BlockState s = level.getBlockState(p);
            if (!s.is(BlockTags.LEAVES) && !s.is(BlockTags.LOGS) && ground.test(s)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Of the commands waiting in the input file (oldest first), the index of the last {@code bot now ...} or
     * {@code bot stop}, -1 if none: those before it are dropped unrun (they were what "now" means to replace), even
     * while the input waits for the bot ({@code botwait}).
     */
    public static int jumpTo(List<String> waiting) {
        for (int i = waiting.size() - 1; i >= 0; i--) {
            String[] w = waiting.get(i).trim().split("\\s+");
            if (w.length >= 2 && w[0].equals("bot") && (w[1].equals("now") || w[1].equals("stop"))) {
                return i;
            }
        }
        return -1;
    }

    // ---- chores: the daily round while nobody tells it what to do (NookAI alone for a day, 2026-10-22)

    public enum Chore { NONE, DEPOSIT, PICKAXE, SWORD, AXE, TORCHES, FOOD, GUARD, FARM, STOCK }

    /** Torches it keeps by it for the dark underground; fewer, and it makes more if it can. */
    public static final int FEW_TORCHES = 4;

    /** Bag slots left free that count as a full bag. */
    public static final int FULL_BAG = 2;

    /**
     * The chore most worth doing now, between jobs: a full bag emptied into the store first (nothing more can be picked
     * up, nor made: what is made has nowhere to go - what tools are made of stays in the bag), then a missing pickaxe,
     * sword, axe, then food when hungry with none in the bag. {@code tried}: chores it tried lately and that failed (or had nothing to do them
     * with), left alone for a while.
     */
    public static Chore chore(int freeSlots, boolean storeKnown, boolean pickaxe, boolean sword, boolean axe, int food, boolean hasFood,
            java.util.Set<Chore> tried) {
        return chore(freeSlots, storeKnown, pickaxe, sword, axe, FEW_TORCHES, false, food, hasFood, tried);
    }

    /** The same, with torches: fewer than {@link #FEW_TORCHES} and the coal and wood for more - made after the tools. */
    public static Chore chore(int freeSlots, boolean storeKnown, boolean pickaxe, boolean sword, boolean axe, int torches,
            boolean canMakeTorches, int food, boolean hasFood, java.util.Set<Chore> tried) {
        if (freeSlots <= FULL_BAG && storeKnown && !tried.contains(Chore.DEPOSIT)) {
            return Chore.DEPOSIT;
        }
        if (!pickaxe && !tried.contains(Chore.PICKAXE)) {
            return Chore.PICKAXE;
        }
        if (!sword && !tried.contains(Chore.SWORD)) {
            return Chore.SWORD;
        }
        if (!axe && !tried.contains(Chore.AXE)) {
            return Chore.AXE;
        }
        if (torches < FEW_TORCHES && canMakeTorches && !tried.contains(Chore.TORCHES)) {
            return Chore.TORCHES;
        }
        if (food <= 12 && !hasFood && !tried.contains(Chore.FOOD)) {
            return Chore.FOOD;
        }
        return Chore.NONE;
    }

    /** Of iron, stone and wood, the best a tool can be made of with what is in the bag ({@code need} of the head's
     *  material), or null: "iron", "stone", "wooden". */
    public static @Nullable String toolTier(int iron, int stone, int planksOrMore, int need) {
        if (iron >= need) {
            return "iron";
        }
        if (stone >= need) {
            return "stone";
        }
        return planksOrMore >= need + 1 ? "wooden" : null; // a plank more for the sticks
    }

    // ---- farming (the user's first wish for NookAI's days alone, 2026-10-22: a farm for food)

    /** Ground a hoe makes farmland of - grass or dirt, not a path (someone's) - with room above: air, or a plant a hand
     *  clears (short grass). */
    public static boolean tillable(BlockGetter level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (!s.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK) && !s.is(net.minecraft.world.level.block.Blocks.DIRT)) {
            return false;
        }
        BlockState above = level.getBlockState(pos.above());
        return above.isAir() || above.canBeReplaced() && above.getFluidState().isEmpty();
    }

    /** Whether farmland here stays wet: water within 4 across, level with it or one above (the game's own reach). */
    public static boolean hydrated(BlockGetter level, BlockPos pos) {
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-4, 0, -4), pos.offset(4, 1, 4))) {
            if (level.getFluidState(p).is(net.minecraft.tags.FluidTags.WATER)) {
                return true;
            }
        }
        return false;
    }

    /** Ground to till within {@code r} of a spot (a level either way), nearest first; only where the farmland would stay
     *  wet unless {@code dryToo}. */
    public static List<BlockPos> tillTargets(BlockGetter level, BlockPos center, int r, boolean dryToo) {
        List<BlockPos> out = new java.util.ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-r, -1, -r), center.offset(r, 1, r))) {
            if (p.distSqr(center) <= (r + 0.5) * (r + 0.5) + 1 && tillable(level, p) && (dryToo || hydrated(level, p))) {
                out.add(p.immutable());
            }
        }
        out.sort(java.util.Comparator.comparingDouble(p -> p.distSqr(center)));
        return out;
    }

    /** Farmland with nothing growing on it yet, within {@code r} of a spot (a level either way), nearest first. */
    public static List<BlockPos> plantTargets(BlockGetter level, BlockPos center, int r) {
        List<BlockPos> out = new java.util.ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-r, -1, -r), center.offset(r, 1, r))) {
            if (level.getBlockState(p).getBlock() instanceof net.minecraft.world.level.block.FarmlandBlock && level.getBlockState(p.above()).isAir()) {
                out.add(p.immutable());
            }
        }
        out.sort(java.util.Comparator.comparingDouble(p -> p.distSqr(center)));
        return out;
    }

    /** A crop ready to pick: a vanilla-style crop at its last age, or any crop (#crops) with an age at its top - the
     *  lower half of a tall one (corn). */
    public static boolean ripe(BlockState s) {
        if (s.getBlock() instanceof net.minecraft.world.level.block.CropBlock crop) {
            return crop.isMaxAge(s);
        }
        if (!s.is(BlockTags.CROPS)) {
            return false;
        }
        if (s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)
                && s.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)
                        != net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER) {
            return false;
        }
        for (var p : s.getProperties()) {
            if (p.getName().equals("age") && p instanceof net.minecraft.world.level.block.state.properties.IntegerProperty age) {
                return s.getValue(age) == java.util.Collections.max(age.getPossibleValues());
            }
        }
        return false;
    }

    /** Ripe crops within {@code r} of a spot (two levels either way), nearest first. */
    public static List<BlockPos> ripeCrops(BlockGetter level, BlockPos center, int r) {
        List<BlockPos> out = new java.util.ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-r, -2, -r), center.offset(r, 2, r))) {
            if (ripe(level.getBlockState(p))) {
                out.add(p.immutable());
            }
        }
        out.sort(java.util.Comparator.comparingDouble(p -> p.distSqr(center)));
        return out;
    }

    // ---- fishing

    /** Where to fish from: a dry cell to stand in (solid ground under it, room for the body) with open water - a source
     *  with air over it - two to five blocks out, no higher than the feet; and that water. */
    public record FishingSpot(BlockPos stand, BlockPos water) {}

    public static @Nullable FishingSpot fishingSpot(BlockGetter level, BlockPos near, int r) {
        FishingSpot best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos w : BlockPos.betweenClosed(near.offset(-r, -4, -r), near.offset(r, 2, r))) {
            var fluid = level.getFluidState(w);
            if (!fluid.is(net.minecraft.tags.FluidTags.WATER) || !fluid.isSource() || !level.getBlockState(w.above()).isAir()) {
                continue;
            }
            for (BlockPos s : BlockPos.betweenClosed(w.offset(-5, 0, -5), w.offset(5, 3, 5))) {
                double across = Math.sqrt(Math.pow(s.getX() - w.getX(), 2) + Math.pow(s.getZ() - w.getZ(), 2));
                if (across < 2.0 || across > 5.0 || s.getY() < w.getY()) {
                    continue;
                }
                double d = s.distSqr(near);
                if (d >= bestD || !standable(level, s)) {
                    continue;
                }
                bestD = d;
                best = new FishingSpot(s.immutable(), w.immutable());
            }
        }
        return best;
    }

    /** Feet can stand here: solid ground under, two free dry cells. */
    public static boolean standable(BlockGetter level, BlockPos s) {
        BlockState under = level.getBlockState(s.below());
        return !under.getCollisionShape(level, s.below()).isEmpty() && under.getFluidState().isEmpty()
                && level.getBlockState(s).getCollisionShape(level, s).isEmpty() && level.getFluidState(s).isEmpty()
                && level.getBlockState(s.above()).getCollisionShape(level, s.above()).isEmpty() && level.getFluidState(s.above()).isEmpty();
    }

    // ---- exploring

    /** The n-th corner (from 1) of a square spiral round the start, {@code step} blocks a side unit: east 1, south 1,
     *  west 2, north 2, east 3... - every place within the spiral passed near once. {dx, dz} from the start. */
    public static int[] spiralCorner(int n, int step) {
        int x = 0;
        int z = 0;
        int[][] dirs = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
        for (int i = 0; i < n; i++) {
            int len = i / 2 + 1;
            x += dirs[i % 4][0] * len * step;
            z += dirs[i % 4][1] * len * step;
        }
        return new int[] {x, z};
    }

    /** What a block says of the place, for an explorer's notes: a village's bell, bed or workstation; a lava pool;
     *  null for nothing of note. */
    public static @Nullable String landmark(BlockState s) {
        if (s.is(net.minecraft.world.level.block.Blocks.BELL)) {
            return "bell";
        }
        if (s.is(BlockTags.BEDS)) {
            return "bed";
        }
        if (s.getFluidState().is(net.minecraft.tags.FluidTags.LAVA) && s.getFluidState().isSource()) {
            return "lava";
        }
        var b = s.getBlock();
        if (b == net.minecraft.world.level.block.Blocks.COMPOSTER || b == net.minecraft.world.level.block.Blocks.LECTERN
                || b == net.minecraft.world.level.block.Blocks.SMITHING_TABLE || b == net.minecraft.world.level.block.Blocks.FLETCHING_TABLE
                || b == net.minecraft.world.level.block.Blocks.CARTOGRAPHY_TABLE || b == net.minecraft.world.level.block.Blocks.BLAST_FURNACE
                || b == net.minecraft.world.level.block.Blocks.SMOKER || b == net.minecraft.world.level.block.Blocks.LOOM
                || b == net.minecraft.world.level.block.Blocks.GRINDSTONE || b == net.minecraft.world.level.block.Blocks.STONECUTTER
                || b == net.minecraft.world.level.block.Blocks.BREWING_STAND) {
            return "workstation";
        }
        return null;
    }

    // ---- home: the house it keeps and spends its nights in (the 10-hour day alone, 2026-10-22: about twenty deaths, most
    // of them at night by the hut - dark all round, a wall a creeper had blown open, no bed, the door left open, and it
    // went out of the door to fight)

    /**
     * A house's plan ({@code bot home X Y Z FILE OX OY OZ}): the blocks it should have, where, and the box round them. The
     * room is the box less its outer walls, floor and roof.
     */
    public record Plan(java.util.Map<BlockPos, BlockState> blocks, net.minecraft.world.level.levelgen.structure.BoundingBox box) {
        public static Plan of(java.util.Map<BlockPos, BlockState> blocks) {
            net.minecraft.world.level.levelgen.structure.BoundingBox box = null;
            for (BlockPos p : blocks.keySet()) {
                box = box == null ? new net.minecraft.world.level.levelgen.structure.BoundingBox(p) : box.encapsulate(p);
            }
            if (box == null) {
                throw new IllegalArgumentException("an empty plan");
            }
            return new Plan(java.util.Map.copyOf(blocks), box);
        }

        /** Feet in this cell are in the room: off the outer walls, above the floor, under the roof. */
        public boolean inside(BlockPos feet) {
            return feet.getX() > this.box.minX() && feet.getX() < this.box.maxX() && feet.getZ() > this.box.minZ()
                    && feet.getZ() < this.box.maxZ() && feet.getY() > this.box.minY() && feet.getY() < this.box.maxY();
        }
    }

    /** A doorway in a plan's outer wall: its lower cell, and the way out. */
    public record Doorway(BlockPos pos, net.minecraft.core.Direction out) {}

    /**
     * The doorway a plan leaves in its outer wall - two cells over the floor with no block planned, walled on both sides
     * along the wall - or null.
     */
    public static @Nullable Doorway doorway(Plan plan) {
        var b = plan.box();
        int y = b.minY() + 1;
        for (int x = b.minX(); x <= b.maxX(); x++) {
            for (int z = b.minZ(); z <= b.maxZ(); z++) {
                boolean west = x == b.minX();
                boolean east = x == b.maxX();
                boolean north = z == b.minZ();
                boolean south = z == b.maxZ();
                if ((west || east) == (north || south)) {
                    continue; // inside, or a corner
                }
                BlockPos p = new BlockPos(x, y, z);
                if (plan.blocks().containsKey(p) || plan.blocks().containsKey(p.above())) {
                    continue;
                }
                net.minecraft.core.Direction along = west || east ? net.minecraft.core.Direction.SOUTH : net.minecraft.core.Direction.EAST;
                if (plan.blocks().containsKey(p.relative(along)) && plan.blocks().containsKey(p.relative(along.getOpposite()))
                        && plan.blocks().containsKey(p.below())) {
                    return new Doorway(p, west ? net.minecraft.core.Direction.WEST : east ? net.minecraft.core.Direction.EAST
                            : north ? net.minecraft.core.Direction.NORTH : net.minecraft.core.Direction.SOUTH);
                }
            }
        }
        return null;
    }

    /** Whether a house with this many of its blocks missing is mended: not when most are (not built there yet, or the
     *  world round it not loaded yet - just after joining, a whole hut read as missing). */
    public static boolean mendable(int missing, int total) {
        return missing * 2 < total;
    }

    /**
     * Whether a plan's block is missing and is to be set down again: the cell holds air, a plant, water - something a
     * block is set into - and not the block itself. Anything else there (a chest someone put in the wall, the block
     * turned another way) is someone's doing, and left. The upper half of a door and a bed's head come with the lower
     * half and the foot.
     */
    public static boolean needsRepair(BlockState want, BlockState have) {
        if (want.isAir() || have.getBlock() == want.getBlock()) {
            return false;
        }
        if (want.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)
                && want.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)
                        == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER) {
            return false;
        }
        if (want.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.BED_PART)
                && want.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.BED_PART)
                        == net.minecraft.world.level.block.state.properties.BedPart.HEAD) {
            return false;
        }
        return have.isAir() || have.canBeReplaced() || !have.getFluidState().isEmpty();
    }

    /** How bright (block light) the ground round home is kept: mobs spawn only in the dark (block light 0), and a torch
     *  every few blocks keeps the whole yard well above that. */
    public static final int YARD_LIGHT = 8;
    /** How far round home it keeps lit. */
    public static final int YARD = 10;
    /** A torch's light, falling by one a block. */
    public static final int TORCH_LIGHT = 14;

    /**
     * Where to set torches so that every ground cell given has at least {@code want} block light: the darkest first,
     * each torch counted as lighting the cells round it (14, less one a block - walls aside: a cell left dark behind one
     * is found dark again next time, and lit then). At most {@code max} torches. {@code light}: the ground cells (the
     * feet cell over a block a mob could stand on) and their block light now.
     */
    public static List<BlockPos> torchSpots(java.util.Map<BlockPos, Integer> light, int want, int max) {
        java.util.Map<BlockPos, Integer> lit = new java.util.HashMap<>(light);
        List<BlockPos> cells = new java.util.ArrayList<>(light.keySet());
        cells.sort(java.util.Comparator.<BlockPos>comparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getY));
        List<BlockPos> out = new java.util.ArrayList<>();
        while (out.size() < max) {
            BlockPos darkest = null;
            for (BlockPos c : cells) {
                if (lit.get(c) < want && (darkest == null || lit.get(c) < lit.get(darkest))) {
                    darkest = c;
                }
            }
            if (darkest == null) {
                break;
            }
            out.add(darkest);
            for (BlockPos c : cells) {
                lit.put(c, Math.max(lit.get(c), TORCH_LIGHT - c.distManhattan(darkest)));
            }
        }
        return out;
    }

    /** A bed's place in a room: its foot cell and the way to its head. */
    public record BedSpot(BlockPos foot, net.minecraft.core.Direction facing) {}

    /**
     * Where a bed goes in a room: a foot cell and its head beside it, both free floor ({@code free}: feet cells with a
     * floor under them and room above), the head against a wall if it can be, never in front of a door ({@code doors}:
     * their lower cells). Null if the room has no room for one.
     */
    public static @Nullable BedSpot bedSpot(java.util.Set<BlockPos> free, java.util.Set<BlockPos> doors) {
        BedSpot best = null;
        int bestScore = Integer.MIN_VALUE;
        List<BlockPos> cells = new java.util.ArrayList<>(free);
        cells.sort(java.util.Comparator.<BlockPos>comparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getY));
        for (BlockPos f : cells) {
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                BlockPos h = f.relative(d);
                if (!free.contains(h)) {
                    continue;
                }
                boolean byDoor = false;
                for (BlockPos door : doors) {
                    if (door.distManhattan(f) <= 1 || door.distManhattan(h) <= 1) {
                        byDoor = true;
                    }
                }
                if (byDoor) {
                    continue;
                }
                int score = (free.contains(h.relative(d)) ? 0 : 2) // the head against a wall
                        + (free.contains(f.relative(d.getClockWise())) && free.contains(f.relative(d.getCounterClockWise())) ? 0 : 1); // along one
                if (score > bestScore) {
                    bestScore = score;
                    best = new BedSpot(f, d);
                }
            }
        }
        return best;
    }

    /**
     * Whether, at night with a home, it stays in: no job takes it out of the door until morning, and a fight is fought
     * from inside; out of doors, it goes in. {@code nightOut}: told it may go out ({@code bot set nightout on}).
     */
    public static boolean keepIn(long dayTime, boolean homeSet, boolean nightOut) {
        return homeSet && !nightOut && night(dayTime);
    }

    /** Whether a mob may be struck from where it stands, staying in: the mob is in the room too, or within reach (a
     *  zombie at the window, at the door). Others are left - going after them was going out. */
    public static boolean strikeFromInside(boolean mobInside, double distance, double reach) {
        return mobInside || distance <= reach;
    }

    // ---- food: kept in stock, not looked for when starving (the same day: no animals within about 100 blocks of the
    // hut, and it went hungry all day)

    /** Cooked food (bread, cooked meat and fish) kept in the bag, unless told otherwise ({@code bot set food N}). */
    public static final int FOOD_STOCK = 6;
    /** Adults of a kind kept in the pen: more are eaten when food runs short. */
    public static final int PEN_KEEP = 2;
    /** No more bred once this many are in the pen (babies too). */
    public static final int PEN_FULL = 8;
    /** Ticks between two breedings (an animal that has bred will not again for five minutes). */
    public static final int BREED_EVERY = 6000;

    /**
     * What is known for a food decision. {@code penAdults}/{@code penBabies}: in the pen; {@code breedFood}: what breeds
     * them is in the bag; {@code tempt}: what leads the kind to fetch is; {@code animalsNear}: two or more of a kind worth
     * eating within 48; {@code animalsKnown}: a place an explore saw some; {@code longDay}: time enough before dusk for a
     * long walk; {@code tried}: steps that failed lately, left a while.
     */
    public record Larder(int cooked, int want, int raw, int wheat, boolean fuel, boolean penSet, boolean penBuilt, boolean penMaterials,
            int penAdults, int penBabies, boolean breedFood, boolean bredLately, boolean tempt, boolean animalsNear,
            boolean animalsKnown, boolean longDay, boolean fishingWater, java.util.Set<FoodStep> tried) {}

    public enum FoodStep { NONE, COOK, BREAD, CULL, BREED, BUILD_PEN, FETCH, HUNT, FISH }

    /**
     * The next step to keep food in stock, nearest at hand first: raw food cooked, wheat baked, an animal more than the
     * pen keeps eaten, the pen's pair bred; the pen built if it is not yet, and two animals led into it from a place seen;
     * animals hunted; fish caught. NONE: stocked, or nothing to do it with.
     */
    public static FoodStep foodStep(Larder l) {
        if (l.cooked() >= l.want()) {
            return FoodStep.NONE;
        }
        Predicate<FoodStep> ok = s -> !l.tried().contains(s);
        if (l.raw() > 0 && l.fuel() && ok.test(FoodStep.COOK)) {
            return FoodStep.COOK;
        }
        if (l.wheat() >= 3 && ok.test(FoodStep.BREAD)) {
            return FoodStep.BREAD;
        }
        if (l.penAdults() > PEN_KEEP && ok.test(FoodStep.CULL)) {
            return FoodStep.CULL;
        }
        if (penBreeds(l.penAdults(), l.penBabies(), l.breedFood(), l.bredLately()) && ok.test(FoodStep.BREED)) {
            return FoodStep.BREED;
        }
        if (l.penSet() && !l.penBuilt() && l.penMaterials() && ok.test(FoodStep.BUILD_PEN)) {
            return FoodStep.BUILD_PEN;
        }
        if (l.penSet() && l.penBuilt() && l.penAdults() + l.penBabies() < PEN_KEEP && l.tempt() && l.longDay()
                && (l.animalsNear() || l.animalsKnown()) && ok.test(FoodStep.FETCH)) {
            return FoodStep.FETCH;
        }
        if ((l.animalsNear() || l.animalsKnown() && l.longDay()) && ok.test(FoodStep.HUNT)) {
            return FoodStep.HUNT;
        }
        if (l.fishingWater() && ok.test(FoodStep.FISH)) {
            return FoodStep.FISH;
        }
        return FoodStep.NONE;
    }

    /** Whether to breed the pen's animals: two adults or more, room in the pen, their food, not bred lately. */
    public static boolean penBreeds(int adults, int babies, boolean food, boolean bredLately) {
        return adults >= 2 && adults + babies < PEN_FULL && food && !bredLately;
    }

    /** Morning, with time enough for a long walk out and back before dusk (day time, ticks of 24000) - from when the sun
     *  is up enough to have burnt the night's skeletons (one left from the night shot it on its way out at dawn,
     *  2026-10-22). */
    public static boolean longDay(long dayTime) {
        long t = Math.floorMod(dayTime, 24000L);
        return t >= 1000 && t < 8000;
    }

    /** Whether to set out on a long walk for food: the morning, and not badly hurt (three hearts or less, one arrow on
     *  the way is the end: at home it is safe, hungry or not). */
    public static boolean farTrip(long dayTime, float health) {
        return longDay(dayTime) && health > LOW_HEALTH;
    }

    /** A pen: a fence round a 3x3 yard, its gate on the side toward home. */
    public record Pen(BlockPos centre, List<BlockPos> fence, BlockPos gate, net.minecraft.core.Direction gateSide) {
        /** Inside the fence. */
        public boolean inside(net.minecraft.core.Vec3i p) {
            return Math.abs(p.getX() - this.centre.getX()) <= 1 && Math.abs(p.getZ() - this.centre.getZ()) <= 1
                    && Math.abs(p.getY() - this.centre.getY()) <= 2;
        }

        /** The cells just outside the fence (corners aside), to stand at to feed or strike over it. */
        public List<BlockPos> ring() {
            List<BlockPos> out = new java.util.ArrayList<>();
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == 3 && !(Math.abs(dx) == 3 && Math.abs(dz) == 3)) {
                        out.add(this.centre.offset(dx, 0, dz));
                    }
                }
            }
            return out;
        }
    }

    /** The pen round {@code centre} (the yard's middle, at the feet's level), its gate on the side facing {@code home}. */
    public static Pen pen(BlockPos centre, BlockPos home) {
        int dx = home.getX() - centre.getX();
        int dz = home.getZ() - centre.getZ();
        net.minecraft.core.Direction side = Math.abs(dx) >= Math.abs(dz) ? (dx >= 0 ? net.minecraft.core.Direction.EAST : net.minecraft.core.Direction.WEST)
                : (dz >= 0 ? net.minecraft.core.Direction.SOUTH : net.minecraft.core.Direction.NORTH);
        BlockPos gate = centre.relative(side, 2);
        List<BlockPos> fence = new java.util.ArrayList<>();
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                BlockPos p = centre.offset(x, 0, z);
                if (Math.max(Math.abs(x), Math.abs(z)) == 2 && !p.equals(gate)) {
                    fence.add(p);
                }
            }
        }
        return new Pen(centre, fence, gate, side);
    }

    /** Places an explore saw animals of a kind ({@code explore_notes.txt}: "... 3 cow at -196 73 432; 4 sheep at ..."),
     *  those with two or more first, then nearest to {@code from} first. */
    public static List<BlockPos> animalPlaces(List<String> notes, String kind, BlockPos from) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("(\\d+) " + java.util.regex.Pattern.quote(kind) + " at (-?\\d+) (-?\\d+) (-?\\d+)");
        java.util.Map<BlockPos, Integer> seen = new java.util.LinkedHashMap<>();
        for (String line : notes) {
            var m = p.matcher(line);
            while (m.find()) {
                seen.merge(new BlockPos(Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4))),
                        Integer.parseInt(m.group(1)), Math::max);
            }
        }
        List<BlockPos> out = new java.util.ArrayList<>(seen.keySet());
        out.sort(java.util.Comparator.<BlockPos>comparingInt(b -> seen.get(b) >= 2 ? 0 : 1).thenComparingDouble(b -> b.distSqr(from)));
        return out;
    }

    // ---- the companion out at night (the user's own character, left standing outside while they were away, was killed
    // by zombies, 2026-10-22)

    /** Ticks without moving that count as away from the keyboard. */
    public static final int AWAY = 1200;
    /** Ticks between two warnings while it lasts; a mob close by is said sooner, though no more often than a third of this. */
    public static final int WARN_EVERY = 3600;

    public enum Warn { NONE, OUT_AT_NIGHT, MOB_CLOSE }

    /**
     * Whether to tell the companion, by chat, that they are out at night: night, out of doors, still for a minute; a
     * hostile mob within 8 of them makes it urgent. {@code sinceWarned}: ticks since the last warning.
     */
    public static Warn warnCompanion(long dayTime, boolean outdoors, int stillTicks, boolean mobClose, long sinceWarned) {
        if (!night(dayTime) || !outdoors || stillTicks < AWAY) {
            return Warn.NONE;
        }
        if (mobClose && sinceWarned >= WARN_EVERY / 3) {
            return Warn.MOB_CLOSE;
        }
        return sinceWarned >= WARN_EVERY ? Warn.OUT_AT_NIGHT : Warn.NONE;
    }

    /** The daily round with the home's upkeep and food: as {@link #chore}, then the home lit and mended ({@code guard}),
     *  the field and the pen tended ({@code farm}), food stocked ({@code stock}). */
    public static Chore chore(int freeSlots, boolean storeKnown, boolean pickaxe, boolean sword, boolean axe, int torches,
            boolean canMakeTorches, int food, boolean hasFood, boolean guard, boolean farm, boolean stock, java.util.Set<Chore> tried) {
        Chore first = chore(freeSlots, storeKnown, pickaxe, sword, axe, torches, canMakeTorches, food, hasFood, tried);
        if (first != Chore.NONE) {
            return first;
        }
        if (guard && !tried.contains(Chore.GUARD)) {
            return Chore.GUARD;
        }
        if (farm && !tried.contains(Chore.FARM)) {
            return Chore.FARM;
        }
        if (stock && !tried.contains(Chore.STOCK)) {
            return Chore.STOCK;
        }
        return Chore.NONE;
    }
}
