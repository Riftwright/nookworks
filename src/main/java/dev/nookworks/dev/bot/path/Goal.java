package dev.nookworks.dev.bot.path;

import java.util.Collection;
import java.util.List;

import net.minecraft.core.BlockPos;

/** Where a path is to end: a test of a cell (where the feet are), and a guess of the cost from a cell to it. */
public interface Goal {
    boolean reached(int x, int y, int z);

    /** A guess of the cost from the cell to the goal, in ticks; it should not run far over the truth. */
    double estimate(int x, int y, int z);

    /** The cost of a block's travel, for estimates: a sprint along the level. */
    double PER_BLOCK = 4.6;

    /** Feet in this very cell. */
    static Goal at(BlockPos pos) {
        return near(pos, 0.0);
    }

    /** Feet within {@code radius} of the cell (a straight line, cell centre to cell centre). */
    static Goal near(BlockPos pos, double radius) {
        double r2 = radius * radius + 1e-6;
        return new Goal() {
            @Override
            public boolean reached(int x, int y, int z) {
                double dx = x - pos.getX();
                double dy = y - pos.getY();
                double dz = z - pos.getZ();
                return dx * dx + dy * dy + dz * dz <= r2;
            }

            @Override
            public double estimate(int x, int y, int z) {
                return Math.max(0.0, distance(x - pos.getX(), y - pos.getY(), z - pos.getZ()) - radius) * PER_BLOCK;
            }

            @Override
            public String toString() {
                return "near " + pos.toShortString() + " within " + radius;
            }
        };
    }

    /** Feet within {@code radius} of the cell, in a cell {@code ok} allows. */
    static Goal near(BlockPos pos, double radius, Cells ok) {
        Goal near = near(pos, radius);
        return new Goal() {
            @Override
            public boolean reached(int x, int y, int z) {
                return near.reached(x, y, z) && ok.test(x, y, z);
            }

            @Override
            public double estimate(int x, int y, int z) {
                return near.estimate(x, y, z);
            }

            @Override
            public String toString() {
                return near + " (some cells left out)";
            }
        };
    }

    /** Any height, this column. */
    static Goal column(int gx, int gz) {
        return new Goal() {
            @Override
            public boolean reached(int x, int y, int z) {
                return x == gx && z == gz;
            }

            @Override
            public double estimate(int x, int y, int z) {
                return distance(x - gx, 0, z - gz) * PER_BLOCK;
            }

            @Override
            public String toString() {
                return "column " + gx + " " + gz;
            }
        };
    }

    /** Whether a cell will do (open to the sky, say). */
    interface Cells {
        boolean test(int x, int y, int z);
    }

    /** Any height, within {@code radius} of the column (across, cell centre to cell centre). */
    static Goal columnNear(int gx, int gz, double radius) {
        return columnNear(gx, gz, radius, (x, y, z) -> true);
    }

    /** Any height within {@code radius} of the column, in cells that {@code open} allows (above ground, not in a cave). */
    static Goal columnNear(int gx, int gz, double radius, Cells open) {
        double r2 = radius * radius + 1e-6;
        return new Goal() {
            @Override
            public boolean reached(int x, int y, int z) {
                double dx = x - gx;
                double dz = z - gz;
                return dx * dx + dz * dz <= r2 && open.test(x, y, z);
            }

            @Override
            public double estimate(int x, int y, int z) {
                return Math.max(0.0, distance(x - gx, 0, z - gz) - radius) * PER_BLOCK;
            }

            @Override
            public String toString() {
                return "column " + gx + " " + gz + " within " + radius;
            }
        };
    }

    /** Any of these cells (the places from which a block can be reached, say). */
    static Goal any(Collection<BlockPos> cells) {
        List<BlockPos> list = List.copyOf(cells);
        java.util.Set<Long> keys = new java.util.HashSet<>();
        for (BlockPos p : list) {
            keys.add(p.asLong());
        }
        return new Goal() {
            @Override
            public boolean reached(int x, int y, int z) {
                return keys.contains(BlockPos.asLong(x, y, z));
            }

            @Override
            public double estimate(int x, int y, int z) {
                double best = Double.MAX_VALUE;
                for (BlockPos p : list) {
                    best = Math.min(best, distance(x - p.getX(), y - p.getY(), z - p.getZ()));
                }
                return best * PER_BLOCK;
            }

            @Override
            public String toString() {
                return "any of " + list.size() + " cells";
            }
        };
    }

    /** At least {@code distance} blocks (on the level) from a point: running away. */
    static Goal awayFrom(double fx, double fz, double distance) {
        return awayFrom(fx, fz, distance, (x, y, z) -> true);
    }

    /** At least {@code distance} across from a point, in a cell {@code ok} allows (dry land). */
    static Goal awayFrom(double fx, double fz, double distance, Cells ok) {
        double d2 = distance * distance;
        return new Goal() {
            @Override
            public boolean reached(int x, int y, int z) {
                double dx = x + 0.5 - fx;
                double dz = z + 0.5 - fz;
                return dx * dx + dz * dz >= d2 && ok.test(x, y, z);
            }

            @Override
            public double estimate(int x, int y, int z) {
                double dx = x + 0.5 - fx;
                double dz = z + 0.5 - fz;
                return Math.max(0.0, distance - Math.sqrt(dx * dx + dz * dz)) * PER_BLOCK;
            }

            @Override
            public String toString() {
                return "away from " + (int) fx + " " + (int) fz;
            }
        };
    }

    /** Straight-line distance, with height counted a little dearer (climbing costs more than walking). */
    static double distance(double dx, double dy, double dz) {
        return Math.sqrt(dx * dx + dz * dz + dy * dy * 1.2);
    }
}
