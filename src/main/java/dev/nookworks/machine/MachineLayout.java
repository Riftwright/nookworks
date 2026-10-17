package dev.nookworks.machine;

import java.util.ArrayList;
import java.util.List;

/**
 * Where everything sits on a machine's screen. One entry per machine; the menu builds its slots from it and the
 * screen draws from it, so every machine screen shares one look and one set of parts:
 * <ul>
 * <li>an energy gauge down the left edge (when the machine takes power),</li>
 * <li>a gauge showing its work: an arrow from the inputs to the outputs, a flame for the generator, a beam for
 * the laser,</li>
 * <li>the machine's tanks on the right - mercury, water, oil, biodiesel, experience - each marked with what it holds
 * and which way it moves through the pipes,</li>
 * <li>a mark under the outputs where they leave into the container below.</li>
 * </ul>
 * Coordinates are a slot's item area, as {@code Slot.x/y} store them, in a {@link #WIDTH} x {@link #HEIGHT} screen
 * whose machine bay runs from y {@link #BAY_TOP} to {@link #BAY_BOTTOM}, with the status line under it.
 */
public enum MachineLayout {
    GENERATOR(Gauge.FLAME, 80, 26, -1, -1, List.of(in(80, 48)), List.of(tank(135, Flow.IN))),
    // The Generator's screen: the flame, the slot a bucket or bottle of biodiesel is poured from, the tank it fills.
    DIESEL_GENERATOR(Gauge.FLAME, 80, 26, -1, -1, List.of(in(80, 48)), List.of(tank(135, Flow.IN))),
    CRUSHER(Gauge.ARROW, 72, 36, 108, 56, List.of(in(48, 36), out(104, 36)), List.of()),
    STONE_MAKER(Gauge.ARROW, 60, 36, 92, 56, List.of(out(88, 36)), List.of()),
    LASER(Gauge.BEAM, 30, 42, -1, -1, List.of(), List.of()),
    PLANTER(Gauge.NONE, 0, 0, -1, -1, grid(62, 20, 3, 3, false), List.of()),
    // Input, the nine results, then the mesh fitted under the input (slot 10: SifterBlockEntity.MESH).
    SIFTER(Gauge.ARROW, 54, 36, -1, -1, concat(concat(List.of(in(30, 27)), grid(86, 20, 3, 3, true)),
            List.of(fitting(30, 49))), List.of()),
    // The charge and its two reagents, the byproduct and the remainder, the fill box (bottle in, filled out); experience,
    // water and mercury tanks.
    RETORT(Gauge.ARROW, 52, 27, 94, 47, List.of(in(30, 27), in(21, 51), in(39, 51), out(80, 27), out(98, 27), in(64, 55), out(82, 55)),
            List.of(tank(118, Flow.OUT), tank(135, Flow.IN), tank(152, Flow.OUT))),
    // The charge (ore, methanol for biodiesel, or dirt for mud), the caustic soda for biodiesel, then the output; the
    // water for mud, the biodiesel it makes (out of the top), the oil it takes and the mercury. The slots stand further
    // left than the other machines' to leave room for the fourth tank.
    STIRRING_TANK(Gauge.ARROW, 48, 37, 81, 57, List.of(in(26, 27), in(26, 49), out(77, 37)),
            List.of(tank(101, Flow.IN), tank(118, Flow.UP), tank(135, Flow.IN), tank(152, Flow.IN))),
    DISTILLER(Gauge.ARROW, 64, 37, 112, 66, List.of(in(40, 37), out(94, 28), out(112, 28), out(94, 46), out(112, 46)),
            List.of(tank(152, Flow.OUT))),
    // What it smelts and what that becomes, and the experience it keeps.
    ELECTRIC_FURNACE(Gauge.ARROW, 72, 36, 108, 56, List.of(in(48, 36), out(104, 36), in(130, 24), out(130, 50)),
            List.of(tank(152, Flow.OUT))),
    // What it works on, then the result and its extra; the water it uses and the mercury pool of its mercury cell.
    ELECTROLYTIC_CELL(Gauge.ARROW, 58, 36, 96, 56, List.of(in(34, 36), out(88, 36), out(106, 36)),
            List.of(tank(135, Flow.IN), tank(152, Flow.IN))),
    // Gold, and a piglin's Mob Bottle under it; the nine slots what the piglins throw is drawn into (and bottles come
    // back to); the Nether's air it keeps the room filled with, which goes through no pipe.
    BARTER_TERMINAL(Gauge.ARROW, 54, 36, -1, -1, concat(List.of(in(30, 27), in(30, 49)), grid(86, 20, 3, 3, true)),
            List.of(tank(152, Flow.NONE)));

    /** What shows the machine's work. */
    public enum Gauge {
        NONE, ARROW, FLAME, BEAM
    }

    /**
     * Which way a tank's contents move through the pipes: in from them, out into them, or out of the top only; or none,
     * for what no pipe carries (a Barter Terminal's air).
     */
    public enum Flow {
        IN, OUT, UP, NONE
    }

    /** Where a tank stands, and which way it flows. */
    public record TankSpot(int x, Flow flow) {}

    /**
     * One slot on the screen. An output is filled only by the machine; a fitting is a part the machine is fitted
     * with (the Sifter's mesh), framed in brass so it reads as part of the machine rather than as an input.
     */
    public record Spot(int x, int y, boolean output, boolean fitting) {}

    public static final int WIDTH = 176;
    public static final int HEIGHT = 186;
    public static final int INVENTORY_Y = 104;
    /** The machine bay: gauges and slots. Gauges carry their icon on top, from {@link #BAY_TOP}. */
    public static final int BAY_TOP = 16;
    public static final int BAY_BOTTOM = 76;
    /** The status line under the bay. */
    public static final int STATUS_Y = 79;

    private final Gauge gauge;
    private final int gaugeX;
    private final int gaugeY;
    private final int belowX;
    private final int belowY;
    private final List<Spot> spots;
    private final List<TankSpot> tanks;

    MachineLayout(Gauge gauge, int gaugeX, int gaugeY, int belowX, int belowY, List<Spot> spots, List<TankSpot> tanks) {
        this.gauge = gauge;
        this.gaugeX = gaugeX;
        this.gaugeY = gaugeY;
        this.belowX = belowX;
        this.belowY = belowY;
        this.spots = spots;
        this.tanks = tanks;
    }

    public Gauge gauge() {
        return this.gauge;
    }

    public int gaugeX() {
        return this.gaugeX;
    }

    public int gaugeY() {
        return this.gaugeY;
    }

    /** The tanks on the screen, in the order the machine's {@link MachineInfo#guiTanks()} fills them. */
    public List<TankSpot> tanks() {
        return this.tanks;
    }

    /** Where the "out through the bottom" mark goes, or -1 for a machine whose outputs have none. */
    public int belowX() {
        return this.belowX;
    }

    public int belowY() {
        return this.belowY;
    }

    public List<Spot> spots() {
        return this.spots;
    }

    public int slotCount() {
        return this.spots.size();
    }

    /** Whether this is the screen of a machine that makes power rather than uses it. */
    public boolean generates() {
        return this == GENERATOR || this == DIESEL_GENERATOR;
    }

    public static MachineLayout byId(int id) {
        return id >= 0 && id < values().length ? values()[id] : CRUSHER;
    }

    private static TankSpot tank(int x, Flow flow) {
        return new TankSpot(x, flow);
    }

    private static Spot in(int x, int y) {
        return new Spot(x, y, false, false);
    }

    private static Spot out(int x, int y) {
        return new Spot(x, y, true, false);
    }

    private static Spot fitting(int x, int y) {
        return new Spot(x, y, false, true);
    }

    private static List<Spot> grid(int x, int y, int columns, int rows, boolean output) {
        List<Spot> spots = new ArrayList<>();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < columns; c++) {
                spots.add(new Spot(x + c * 18, y + r * 18, output, false));
            }
        }
        return List.copyOf(spots);
    }

    private static List<Spot> concat(List<Spot> a, List<Spot> b) {
        List<Spot> all = new ArrayList<>(a);
        all.addAll(b);
        return List.copyOf(all);
    }
}
