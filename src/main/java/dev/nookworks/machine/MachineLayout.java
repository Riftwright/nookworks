package dev.nookworks.machine;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

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
    // What it makes in the middle, the water bucket or blue ice on its left and the lava bucket on its right, an arrow
    // from each to the middle (the user's drawing, 2026-11-14); slot 0 stays the output, as 1.6 saved it.
    STONE_MAKER(Gauge.ARROW, 52, 36, 84, 56, List.of(out(80, 36), in(32, 36), in(128, 36)), List.of()),
    LASER(Gauge.BEAM, 30, 42, -1, -1, List.of(), List.of()),
    PLANTER(Gauge.NONE, 0, 0, -1, -1, grid(62, 20, 3, 3, false), List.of()),
    // Input, the nine results, then the mesh fitted under the input (slot 10: SifterBlockEntity.MESH).
    SIFTER(Gauge.ARROW, 54, 36, -1, -1, concat(concat(List.of(in(30, 27)), grid(86, 20, 3, 3, true)),
            List.of(fitting(30, 49))), List.of()),
    // Three input slots, all alike, in a column (since 1.9: a charge slot over two reagent slots read as a main input
    // and lesser ones, the user, 2026-11-25), the byproduct and the remainder (the fill box, bottle in and filled out,
    // went in 1.5: a bottle is filled in the Liquid tab now); experience, water, the liquid it gives off (wood vinegar,
    // since 1.5) and mercury tanks - four, a pixel closer than other machines' and the slots a little further left, as
    // the Stirring Tank's.
    RETORT(Gauge.ARROW, 44, 36, 84, 56, List.of(in(22, 18), in(22, 36), in(22, 54), out(70, 36), out(88, 36)),
            List.of(tank(106, Flow.OUT), tank(122, Flow.IN), tank(138, Flow.OUT), tank(154, Flow.OUT))),
    // The charge (ore, methanol for biodiesel, or dirt for mud), the caustic soda for biodiesel, then the output; the
    // water for mud, the biodiesel it makes (out of the top), the oil and the methanol it takes and the mercury. The
    // slots stand further left than the other machines', and its five tanks a pixel closer, to leave room for them all.
    STIRRING_TANK(Gauge.ARROW, 43, 37, 74, 57, List.of(in(23, 27), in(23, 49), out(70, 37)),
            List.of(tank(90, Flow.IN), tank(106, Flow.UP), tank(122, Flow.IN), tank(138, Flow.IN), tank(154, Flow.IN))),
    // The charge, four outputs; the methanol it pipes off and the wood vinegar piped in (both since 1.5), and the
    // mercury it gives back - its slots a little further left for the third tank.
    DISTILLER(Gauge.ARROW, 58, 37, 104, 66, List.of(in(34, 37), out(86, 28), out(104, 28), out(86, 46), out(104, 46)),
            List.of(tank(122, Flow.OUT), tank(138, Flow.IN), tank(154, Flow.OUT))),
    // What it smelts and what that becomes, and the experience it keeps (its fill box went in 1.5, to the Liquid tab).
    ELECTRIC_FURNACE(Gauge.ARROW, 72, 36, 108, 56, List.of(in(48, 36), out(104, 36)),
            List.of(tank(152, Flow.OUT))),
    // What it works on, then the result and its extra; the water it uses and the mercury pool of its mercury cell.
    ELECTROLYTIC_CELL(Gauge.ARROW, 58, 36, 96, 56, List.of(in(34, 36), out(88, 36), out(106, 36)),
            List.of(tank(135, Flow.IN), tank(152, Flow.IN))),
    // Gold, and a piglin's Mob Bottle under it; the nine slots what the piglins throw is drawn into (and bottles come
    // back to); the Nether's air it keeps the room filled with, which goes through no pipe.
    BARTER_TERMINAL(Gauge.ARROW, 54, 36, -1, -1, concat(List.of(in(30, 27), in(30, 49)), grid(86, 20, 3, 3, true)),
            List.of(tank(152, Flow.NONE))),
    // Nine of an item in, what they press into out.
    // four inputs in a square (slot 0 and 2-4; slot 1, the output, where it was)
    COMPRESSOR(Gauge.ARROW, 72, 36, 108, 56, List.of(in(30, 27), out(104, 36), in(48, 27), in(30, 45), in(48, 45)), List.of()),
    // The junk in; scrap and gold nuggets out, side by side.
    RECYCLER(Gauge.ARROW, 64, 36, 110, 56, List.of(in(40, 36), out(96, 36), out(114, 36)), List.of());

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
    /** The bay drawn closer to make room for an add-on's tanks ({@link #addTanks}); null as it stands. */
    private volatile @Nullable Crowded crowded;

    /**
     * The bay of a machine an add-on has given tanks of its own: where its slots, arrow and mark under the output go
     * instead, the arrow pointing down when it does, and where the add-on's tanks stand after the machine's own.
     */
    public record Crowded(int gaugeX, int gaugeY, boolean arrowDown, int belowX, int belowY, List<Spot> spots,
            List<TankSpot> tanks, List<TankSpot> addOnTanks) {}

    /**
     * An add-on's tanks on this machine's screen, in the same row as its own and after them, drawn as they are
     * (MachineScreen#addGauges on the client). Called once as the add-on is constructed, on client and server alike:
     * the menu's slots stand where the crowded bay puts them, on both sides. The Stirring Tank alone has room for any,
     * two at most (nookTechnology's creosote oil and liquid experience, which stood in its Extra Slots tab: the user,
     * 2026-11-25, 「なんで攪拌槽の経験値とクレオソートだけ追加スロットに入ってるの？変だと思わなかった？」).
     */
    public static void addTanks(MachineLayout layout, List<Flow> flows) {
        // after another add-on's, if one gave some (a second call took the first's place)
        List<Flow> all = new ArrayList<>();
        Crowded before = layout.crowded;
        if (before != null) {
            before.addOnTanks().forEach(tank -> all.add(tank.flow()));
        }
        all.addAll(flows);
        if (layout != STIRRING_TANK || flows.isEmpty() || all.size() > 2) {
            throw new IllegalArgumentException("no room for " + flows.size() + " more tanks on " + layout);
        }
        layout.crowded = crowdedStirringTank(all);
    }

    /**
     * The Stirring Tank with an add-on's tanks: its charge and caustic soda side by side at the top, the arrow down to
     * the output under them, and seven tanks in the row, the add-on's last.
     */
    public static Crowded crowdedStirringTank(List<Flow> flows) {
        List<TankSpot> own = new ArrayList<>();
        Flow[] ownFlows = {Flow.IN, Flow.UP, Flow.IN, Flow.IN, Flow.IN};
        for (int i = 0; i < ownFlows.length; i++) {
            own.add(tank(58 + i * 16, ownFlows[i]));
        }
        List<TankSpot> added = new ArrayList<>();
        for (int i = 0; i < flows.size(); i++) {
            added.add(tank(58 + (ownFlows.length + i) * 16, flows.get(i)));
        }
        return new Crowded(30, 38, true, 50, 63, List.of(in(21, 20), in(39, 20), out(30, 57)), List.copyOf(own),
                List.copyOf(added));
    }

    /** The add-on's tanks after the machine's own ({@link #addTanks}); none for most. */
    public List<TankSpot> addOnTanks() {
        Crowded c = this.crowded;
        return c == null ? List.of() : c.addOnTanks();
    }

    /** Whether the arrow points down, from inputs above to the output below (a crowded bay). */
    public boolean arrowDown() {
        Crowded c = this.crowded;
        return c != null && c.arrowDown();
    }

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
        Crowded c = this.crowded;
        return c == null ? this.gaugeX : c.gaugeX();
    }

    public int gaugeY() {
        Crowded c = this.crowded;
        return c == null ? this.gaugeY : c.gaugeY();
    }

    /** The tanks on the screen, in the order the machine's {@link MachineInfo#guiTanks()} fills them. */
    public List<TankSpot> tanks() {
        Crowded c = this.crowded;
        return c == null ? this.tanks : c.tanks();
    }

    /** Where the "out through the bottom" mark goes, or -1 for a machine whose outputs have none. */
    public int belowX() {
        Crowded c = this.crowded;
        return c == null ? this.belowX : c.belowX();
    }

    public int belowY() {
        Crowded c = this.crowded;
        return c == null ? this.belowY : c.belowY();
    }

    public List<Spot> spots() {
        Crowded c = this.crowded;
        return c == null ? this.spots : c.spots();
    }

    public int slotCount() {
        return this.spots.size();
    }

    /** Whether this is the screen of a machine that makes power rather than uses it. */
    public boolean generates() {
        return this == GENERATOR || this == DIESEL_GENERATOR;
    }

    /**
     * Two arrows meeting at the output, the second the first mirrored about the screen's middle: what comes from the
     * left and from the right meets there (the Stone Maker's lava and water).
     */
    public boolean meets() {
        return this == STONE_MAKER;
    }

    /**
     * Slots of the machine's own in its Extra Slots tab, after the ones its bay shows (the Stone Maker's soul soil): the
     * client's stand-in container has room for them (MachineMenu).
     */
    public int extraSlots() {
        return this == STONE_MAKER ? 1 : 0;
    }

    /**
     * The most the machine's {@code slot} holds, however it comes - a shift-click, another mod's pipe, an Item Warp: the
     * Stone Maker keeps one water bucket or blue ice, one lava bucket and one soul soil, as a hopper's one at a time leaves
     * there (64 blue ice went in at a shift-click, and sat there unused: 1.7 review). A container's own most otherwise.
     */
    public int slotLimit(int slot) {
        return this == STONE_MAKER && slot != StoneMakerBlockEntity.OUTPUT ? 1 : net.minecraft.world.item.Item.ABSOLUTE_MAX_STACK_SIZE;
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
