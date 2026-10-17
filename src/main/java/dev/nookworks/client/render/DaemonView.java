package dev.nookworks.client.render;

import dev.nookworks.travel.DaemonDestinations;
import dev.nookworks.travel.DaemonDestinations.Look;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * The world beyond a Dock-a-Daemon Door, as its doorway and porthole show it: a picture {@link #WIDTH} by {@link #HEIGHT}
 * of whole pixels - the door's own pixels, one to a sixteenth of a block - worked out afresh each frame, so it moves as
 * pixel art moves, a pixel at a time.
 * <p>
 * It is a place with depth, not a picture on the door: each pixel is what the viewer's eye ({@link Eye}) sees through
 * it. The ground runs off level with the doorway's foot to a horizon at the eye's height, a path leading away from the
 * threshold; hills (or cliffs, or the End's pillars) stand far off, the sky beyond them. So stepping sideways shows more
 * of one side, near things slide past far ones, and the sky and stars stay put, as through a real opening. What moves
 * in it moves in it: clouds drifting, a lava sea churning, embers rising, fireflies hovering among the trees, stars
 * twinkling. The arithmetic is plain 32-bit hashing, so the same pixel is the same colour on every machine.
 */
public final class DaemonView {
    public static final int WIDTH = 16;
    public static final int HEIGHT = 32;
    /** The scene's ground lies level with the foot of the doorway. */
    private static final float FLOOR = HEIGHT;
    /** How close the eye is taken to be at the nearest: in the doorway itself the perspective would run wild. */
    private static final float NEAREST = 4;
    /** How far behind the door its far hills stand, in its pixels: 25 blocks. */
    private static final float HILLS = 400;
    /**
     * How high the sky's colour runs from the horizon's to the zenith's, as a slope (about 7 degrees): steeper than a real
     * sky's, so the strip of it over a doorway seen from a few blocks off is blue and not all horizon.
     */
    private static final float SKY_SPAN = 0.12F;
    private static final float PATH_HALF_WIDTH = 5;
    private static final int CLOUD = 0xFFFFFF;
    private static final int EMBER = 0xFFC460;
    private static final int STAR = 0xE6DCFF;
    private static final int FIREFLY = 0xD6EC78;
    private static final int LAVA = 0xD8561A;
    private static final int LAVA_HOT = 0xFFA236;
    private static final int LAVA_CRUST = 0x7E2410;
    private static final int NETHER_BRICK = 0x2C1418;
    private static final int NETHER_BRICK_LIT = 0x4A2026;
    /** How far off the Nether's fortress bridge stands, and how high its deck is over the lava, in door pixels. */
    private static final float FORTRESS = 300;
    private static final float DECK = 56;
    private static final int OBSIDIAN = 0x1A1026;
    private static final int CRYSTAL = 0xE58CFF;
    private static final int PURPUR = 0xA77BA7;
    private static final int PURPUR_LIT = 0xC8A2C8;
    private static final int BARK = 0x2A1E14;
    private static final int FOREST_PATH = 0x2E2A1E;
    private static final int DIRT_PATH = 0x94784A;
    private static final int DIRT_PATH_LIT = 0xAE9260;
    private static final int[] FLOWERS = {0xE8443A, 0xF2D64A, 0xF4F0E8, 0xB070E0};

    private DaemonView() {}

    /**
     * Where the viewer's eye is, against the face the view is drawn on, in that face's pixels: {@code across} from its
     * left edge (as one looks at it) and {@code down} from its top, and {@code away} in front of it.
     */
    public record Eye(float across, float down, float away) {
        /** Straight in front, three blocks off, the horizon a little above the middle: a view not seen from the world. */
        public static final Eye AHEAD = new Eye(WIDTH / 2.0F, 13, 48);
    }

    /** The colour of the pixel at column {@code x}, row {@code y} (from the top) at {@code time} ticks, seen from straight ahead. */
    public static int pixel(Look look, int x, int y, float time) {
        return pixel(look, x, y, time, Eye.AHEAD);
    }

    /** The colour (0xAARRGGBB, opaque) of the pixel at column {@code x}, row {@code y}, as seen from {@code eye}. */
    public static int pixel(Look look, int x, int y, float time, Eye eye) {
        float away = Math.max(NEAREST, eye.away());
        // the ray through the pixel's middle: how far across and down it goes for each pixel it goes back
        float du = (x + 0.5F - eye.across()) / away;
        float dv = (y + 0.5F - eye.down()) / away;
        // where it comes down to the floor, counted back from the door, and how far across it is there; never, if it rises
        float ground = dv > 0 && eye.down() < FLOOR ? (FLOOR - eye.down()) / dv - away : Float.POSITIVE_INFINITY;
        float gx = ground < Float.POSITIVE_INFINITY ? eye.across() + du * (away + ground) : 0;
        int colour = switch (look.kind()) {
            case SKY -> look.ground() == DaemonDestinations.NONE ? cloudSea(look, eye, away, du, dv, time)
                    : meadow(look, eye, away, du, dv, ground, gx, time);
            case EMBERS -> nether(look, eye, away, x, y, du, dv, ground, gx, time);
            case STARS -> end(look, eye, away, x, y, dv, ground, gx, time);
            case FIREFLIES -> forest(look, eye, away, x, y, dv, ground, gx, time);
        };
        return 0xFF000000 | colour;
    }

    // ---- the places ----

    /** A meadow under a sky with clouds: a path winding off between grass and flowers, blue hills beyond. */
    private static int meadow(Look look, Eye eye, float away, float du, float dv, float ground, float gx, float time) {
        int seed = look.seed();
        if (ground < HILLS) {
            float fromPath = Math.abs(gx - winding(ground, seed));
            int colour;
            if (fromPath < PATH_HALF_WIDTH) {
                colour = fromPath > PATH_HALF_WIDTH - 1.2F ? mix(DIRT_PATH, look.ground(), 0.4F)
                        : fbm(gx / 6, ground / 6, seed + 30) > 0.62F ? DIRT_PATH_LIT : DIRT_PATH;
            } else {
                float n = fbm(gx / 34, ground / 34, seed + 20);
                colour = n > 0.6F ? look.groundLit() : n < 0.36F ? mix(look.ground(), 0x000000, 0.18F) : look.ground();
                if (ground < 90 && fromPath > PATH_HALF_WIDTH + 2) {
                    int fx = Mth.floor(gx / 5);
                    int fz = Mth.floor(ground / 5);
                    if (hash(fx, fz, seed + 40) > 0.94F) {
                        colour = FLOWERS[(int) (hash(fx, fz, seed + 41) * FLOWERS.length) % FLOWERS.length];
                    }
                }
            }
            return haze(colour, look.bottom(), ground, 900);
        }
        int hill = hills(look, eye, away, du, dv, 30, 30, mix(look.ground(), look.top(), 0.35F), mix(look.groundLit(), look.top(), 0.3F));
        return hill >= 0 ? hill : sky(look, du, dv, time);
    }

    /** A floating place, or one not known: its sky above, and below the horizon a sea of cloud in its own colours. */
    private static int cloudSea(Look look, Eye eye, float away, float du, float dv, float time) {
        float below = FLOOR + 96; // six blocks down
        if (dv > 0 && eye.down() < below) {
            float depth = (below - eye.down()) / dv - away;
            float sx = eye.across() + du * (away + depth);
            float c = fbm(sx / 50 + time * 0.0015F, depth / 50, look.seed() + 50);
            int gap = mix(look.top(), look.bottom(), 0.35F);
            int colour = c > 0.58F ? mix(look.bottom(), CLOUD, 0.8F) : c > 0.5F ? mix(gap, CLOUD, 0.45F) : gap;
            return haze(colour, mix(look.top(), look.bottom(), 0.7F), depth, 1400);
        }
        return sky(look, du, dv, time);
    }

    /**
     * The Nether: a netherrack shore under the door, a churning lava sea, a fortress bridge striding across it far off,
     * netherrack cliffs beyond lit from below, the haze thick, embers rising.
     */
    private static int nether(Look look, Eye eye, float away, int x, int y, float du, float dv, float ground, float gx, float time) {
        int seed = look.seed();
        if (riser(eye, away, x, y, time, seed, 28, 300, 0.25F, 0.35F, 220)) {
            return EMBER;
        }
        if (ground < FORTRESS) {
            int colour;
            if (ground < 8 + 18 * fbm(gx / 30, 0.5F, seed + 60)) {
                colour = fbm(gx / 6, ground / 6, seed + 30) > 0.55F ? look.groundLit() : look.ground(); // the shore
            } else {
                float c = fbm(gx / 26 + time * 0.0016F, ground / 26 - time * 0.0011F, seed + 20);
                colour = c > 0.66F ? LAVA_HOT : c < 0.34F ? LAVA_CRUST : LAVA;
            }
            return haze(colour, look.bottom(), ground, 420);
        }
        // the fortress bridge: a deck on piers, every 64 pixels across
        float t = away + FORTRESS;
        float bx = eye.across() + du * t;
        float by = eye.down() + dv * t;
        float pixel = t / away; // how much of the bridge one pixel covers
        if (by >= FLOOR - DECK && by < FLOOR - DECK + 12 || by >= FLOOR - DECK && Mth.positiveModulo(bx, 64) < 14) {
            int brick = by < FLOOR - DECK + pixel ? NETHER_BRICK_LIT : NETHER_BRICK;
            return haze(by > FLOOR - 10 ? mix(brick, LAVA, 0.5F) : brick, look.bottom(), FORTRESS, 420);
        }
        if (ground < HILLS) {
            float c = fbm(gx / 26 + time * 0.0016F, ground / 26 - time * 0.0011F, seed + 20);
            return haze(c > 0.66F ? LAVA_HOT : c < 0.34F ? LAVA_CRUST : LAVA, look.bottom(), ground, 420);
        }
        int cliff = hills(look, eye, away, du, dv, 24, 60, mix(look.ground(), look.bottom(), 0.45F), mix(look.groundLit(), LAVA, 0.6F));
        if (cliff >= 0) {
            return cliff;
        }
        // the haze above, churning slowly
        float up = Math.clamp(-dv / SKY_SPAN, 0.0F, 1.0F);
        int base = mix(look.bottom(), look.top(), smooth(up));
        float c = fbm(du * 3 + time * 0.0005F, -dv * 8 + time * 0.004F, seed);
        return mix(base, look.bottom(), c > 0.64F ? 0.35F : c > 0.56F ? 0.15F : 0.0F);
    }

    /**
     * The End: a purpur walk across the pale island to its edge, the void beyond full of stars, below the horizon as
     * above it, and obsidian pillars standing out of it, each with its crystal.
     */
    private static int end(Look look, Eye eye, float away, int x, int y, float dv, float ground, float gx, float time) {
        int seed = look.seed();
        float edge = ground < 400 ? 240 + 90 * fbm(gx / 70, 0.5F, seed + 60) : 0;
        if (ground < edge) {
            float fromPath = Math.abs(gx - WIDTH / 2.0F);
            int colour;
            if (fromPath < PATH_HALF_WIDTH) {
                colour = fromPath > PATH_HALF_WIDTH - 1.2F || Mth.floor(ground / 8) % 2 == 0 ? PURPUR_LIT : PURPUR;
            } else {
                float n = fbm(gx / 20, ground / 20, seed + 20);
                colour = n > 0.62F ? look.groundLit() : n < 0.36F ? mix(look.ground(), 0x000000, 0.12F) : look.ground();
            }
            return ground > edge - 6 ? mix(colour, 0x000000, 0.35F) : colour; // the lip, shaded
        }
        // the pillars, out in the void
        float nearest = Float.POSITIVE_INFINITY;
        boolean crystal = false;
        for (int i = 0; i < 6; i++) {
            float depth = 420 + hash(i, 20, seed) * 520;
            float across = WIDTH / 2.0F + (hash(i, 21, seed) - 0.5F) * 900;
            float half = 18 + hash(i, 22, seed) * 14;
            float height = 120 + hash(i, 23, seed) * 180;
            float k = away / (away + depth);
            float left = eye.across() + (across - half - eye.across()) * k;
            float right = eye.across() + (across + half - eye.across()) * k;
            float top = eye.down() + (FLOOR - height - eye.down()) * k;
            if (x + 0.5F >= left && x + 0.5F < right && y + 0.5F >= top && depth < nearest) {
                nearest = depth;
                crystal = y + 0.5F < top + 1 && Mth.sin(time * 0.05F + i) > 0;
            }
        }
        if (nearest < Float.POSITIVE_INFINITY) {
            return crystal ? CRYSTAL : OBSIDIAN;
        }
        float tilt = Math.clamp(Math.abs(dv) / SKY_SPAN, 0.0F, 1.0F);
        return stars(mix(look.bottom(), look.top(), smooth(tilt)), eye, away, x, y, time, seed, 0.06F, 0.9F);
    }

    /** The Twilight Forest: a dark path through the trunks, fireflies hovering among them, a few stars through the canopy. */
    private static int forest(Look look, Eye eye, float away, int x, int y, float dv, float ground, float gx, float time) {
        int seed = look.seed();
        if (riser(eye, away, x, y, time, seed, 16, 260, 0.0F, 0.0F, 40)) {
            return FIREFLY;
        }
        // the nearest trunk in front of whatever else the pixel sees
        float trunk = Float.POSITIVE_INFINITY;
        for (int i = 0; i < 18; i++) {
            float depth = 24 + hash(i, 30, seed) * 420;
            float across = winding(depth, seed) + (hash(i, 31, seed) < 0.5F ? -1 : 1) * (16 + hash(i, 32, seed) * 160);
            float half = 5 + hash(i, 33, seed) * 4;
            float k = away / (away + depth);
            float left = eye.across() + (across - half - eye.across()) * k;
            float right = eye.across() + (across + half - eye.across()) * k;
            if (x + 0.5F >= left && x + 0.5F < right && depth < ground && depth < trunk) {
                trunk = depth;
            }
        }
        if (trunk < Float.POSITIVE_INFINITY) {
            return haze(BARK, look.bottom(), trunk, 520);
        }
        if (ground < 600) {
            float fromPath = Math.abs(gx - winding(ground, seed));
            float n = fbm(gx / 20, ground / 20, seed + 20);
            int colour = fromPath < PATH_HALF_WIDTH - 1 ? FOREST_PATH : n > 0.6F ? look.groundLit() : look.ground();
            return haze(colour, look.bottom(), ground, 520);
        }
        float up = Math.clamp(-dv / SKY_SPAN, 0.0F, 1.0F);
        return stars(mix(look.bottom(), look.top(), smooth(up)), eye, away, x, y, time, seed, 0.09F, 0.95F);
    }

    // ---- what they are made of ----

    /** The sky, by the direction looked in (it is far off, so it stays put as one moves): its colours, and clouds drifting. */
    private static int sky(Look look, float du, float dv, float time) {
        float up = Math.clamp(-dv / SKY_SPAN, 0.0F, 1.0F);
        int base = mix(look.bottom(), look.top(), smooth(up));
        float c = fbm(du * 4 + time * 0.0012F, -dv * 14, look.seed()) - 0.1F * up; // thinner overhead
        int light = mix(look.bottom(), CLOUD, 0.55F);
        return c > 0.64F ? mix(base, light, 0.8F) : c > 0.56F ? mix(base, light, 0.4F) : base;
    }

    /**
     * Hills along the horizon, {@link #HILLS} behind the door, {@code low} to {@code low + rise} pixels high, or -1
     * where the ray passes over them: {@code colour}, and {@code crest} along the top.
     */
    private static int hills(Look look, Eye eye, float away, float du, float dv, float low, float rise, int colour, int crest) {
        float t = away + HILLS;
        float top = FLOOR - low - rise * fbm((eye.across() + du * t) / 90, 0.5F, look.seed() + 100);
        float at = eye.down() + dv * t;
        if (at < top) {
            return -1;
        }
        return at < top + t / away ? crest : colour; // the crest: the first row of pixels down from the top
    }

    /**
     * Points of light rising through the place (embers, {@code speed} plus up to {@code spread} pixels a tick through
     * {@code height}) or, at no speed, hovering in it and blinking (fireflies, up to {@code height} high): {@code count}
     * of them, up to {@code deep} behind the door, spread wider the farther back. Whether one is at pixel {@code x}, {@code y}.
     */
    private static boolean riser(Eye eye, float away, int x, int y, float time, int seed, int count, float deep, float speed,
            float spread, float height) {
        for (int i = 0; i < count; i++) {
            float depth = 16 + hash(i, 10, seed) * deep;
            float across = WIDTH / 2.0F + (hash(i, 11, seed) - 0.5F) * (16 + depth * 1.2F) + 6 * Mth.sin(time * 0.02F + i);
            float lift;
            if (speed > 0) {
                lift = (time * (speed + spread * hash(i, 12, seed)) + hash(i, 13, seed) * height) % height;
            } else if (Mth.sin(time * 0.08F + i * 2.3F) > -0.2F) {
                lift = 8 + hash(i, 13, seed) * height + 3 * Mth.sin(time * 0.03F + i * 1.7F);
            } else {
                continue; // a firefly between flashes
            }
            float k = away / (away + depth);
            if (Mth.floor(eye.across() + (across - eye.across()) * k) == x && Mth.floor(eye.down() + (FLOOR - lift - eye.down()) * k) == y) {
                return true;
            }
        }
        return false;
    }

    /**
     * Stars, fixed in direction as stars are: each at a point of its own in a grid of directions {@code cell} across
     * (a cell in {@code 1 - chance} has one), lighting the pixel its direction falls in; they twinkle.
     */
    private static int stars(int base, Eye eye, float away, int x, int y, float time, int seed, float cell, float chance) {
        float du = (x + 0.5F - eye.across()) / away;
        float dv = (y + 0.5F - eye.down()) / away;
        int cx = Mth.floor(du / cell);
        int cy = Mth.floor(dv / cell);
        float s = hash(cx, cy, seed);
        if (s <= chance) {
            return base;
        }
        // where in its cell the star is, and so which pixel shows it
        float su = (cx + hash(cx, cy, seed + 1)) * cell;
        float sv = (cy + hash(cx, cy, seed + 2)) * cell;
        if (Mth.floor(eye.across() + su * away) != x || Mth.floor(eye.down() + sv * away) != y) {
            return base;
        }
        return mix(base, STAR, 0.5F + 0.5F * Mth.sin(time * 0.1F + s * 40));
    }

    /** Where a winding path is across, {@code depth} behind the door: straight out of the doorway, then bending. */
    private static float winding(float depth, int seed) {
        return WIDTH / 2.0F + 14 * Mth.sin(depth / 90 + seed) * smooth(Math.min(1.0F, depth / 120));
    }

    /** {@code colour} seen through {@code depth} of the place's air: taken towards the horizon's colour, nearly wholly by {@code clear}. */
    private static int haze(int colour, int horizon, float depth, float clear) {
        return mix(colour, horizon, 0.8F * smooth(Math.clamp(depth / clear, 0.0F, 1.0F)));
    }

    // ---- arithmetic ----

    private static float hash(int x, int y, int seed) {
        int h = x * 374761393 + y * 668265263 + seed * 0x85EBCA77;
        h = (h ^ h >>> 13) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFF) / 65535.0F;
    }

    private static float smooth(float t) {
        return t * t * (3 - 2 * t);
    }

    private static float noise(float x, float y, int seed) {
        int ix = Mth.floor(x);
        int iy = Mth.floor(y);
        float fx = smooth(x - ix);
        float fy = smooth(y - iy);
        float a = hash(ix, iy, seed);
        float b = hash(ix + 1, iy, seed);
        float c = hash(ix, iy + 1, seed);
        float d = hash(ix + 1, iy + 1, seed);
        return (a + (b - a) * fx) * (1 - fy) + (c + (d - c) * fx) * fy;
    }

    static float fbm(float x, float y, int seed) {
        return noise(x, y, seed) * 0.65F + noise(x * 2.1F, y * 2.1F, seed + 7) * 0.35F;
    }

    private static int mix(int a, int b, float t) {
        return ARGB.srgbLerp(t, a, b) & 0xFFFFFF;
    }
}
