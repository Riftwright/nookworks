package dev.nookworks.mercury;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import dev.nookworks.fluid.Bottles;
import dev.nookworks.fluid.LiquidOutlet;
import dev.nookworks.fluid.Tanks;
import dev.nookworks.machine.GuiTank;
import dev.nookworks.machine.MachineInfo;
import dev.nookworks.machine.MachineLayout;
import dev.nookworks.machine.MachineStatus;
import dev.nookworks.machine.MachineTuning;
import dev.nookworks.oil.OilFluids;
import dev.nookworks.registry.ModBlockEntities;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModOil;
import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.CombinedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import org.jspecify.annotations.Nullable;

/**
 * Vacuum Distiller: the base of a four-block tower (this block, two Distillation Column sections, a Condenser
 * on top). Under vacuum the mercury boils off the amalgam at a low temperature, rises through the column and is
 * condensed for reuse; the purified metal stays behind. Only runs while the tower is complete.
 * <p>
 * Wood vinegar distils to methanol, a liquid since 1.5 (the user, 2026-11-07: 「メタノールは液体として扱いたい」): it goes
 * into its methanol tank ({@link #METHANOL_TANK} mB), which pours out into a Copper Pipe whose line leads to a taker, a
 * Copper Tank, another mod's tank or machine beside or under it, as the Oil Press's does. Nothing is bottled: a run
 * whose methanol has no room waits ({@link MachineStatus#TANK_FULL}), a datapack entry with methanol as its result too;
 * a bottle or bucket in its screen's Liquid tab draws it.
 * <p>
 * Wood vinegar comes in as a liquid since 1.5 (the user, 2026-11-10: 「木酢液も液体として作って欲しい」; and their rule
 * for both mods, 2026-11-08: 「入ってくる瓶と汲みだす瓶を一緒にしない。瓶やバケツで投入されたものも、液体として機械にいれる」):
 * piped into its wood vinegar tank ({@link #WOOD_VINEGAR_TANK} mB), or poured there from a bottle or bucket put in its
 * charge slot, the emptied bottle or bucket going out below as its products do - never filled again with methanol. Each
 * bottle's worth of the tank distils to methanol into its methanol tank, which pours out as above; with that tank too
 * full for it, it waits ({@link MachineStatus#TANK_FULL}), as there is no bottle to put it in.
 * <p>
 * The play-test after 1.5 (the user, 2026-11-12): what is distilled leaves at the top, and wood vinegar is mostly water.
 * Its methanol and its mercury go out at the Condenser, into pipes on its four sides or on top only - Copper Pipes for
 * the methanol, Mercury Pipes for the mercury, nothing set against it - as nookTechnology's Tar Still sends its benzene
 * (「メタノールを取り出すパイプは上の方…につないで…水銀も然り」, 「冷却器の横に機械を置くのは…パイプで運ぶ設計にしましょう」); the
 * Distiller itself sends out neither. A run of the tank's wood vinegar takes {@link #WOOD_VINEGAR_RUN} mB and gives 250 of
 * methanol and the rest as water (「1：1変換されるのもちょっと不自然…残りはどこに行った？」), the Retort's log making as
 * much more wood vinegar, so a log's methanol is what it was. The water goes out of the Distiller's own sides and
 * bottom - never back up the line its wood vinegar comes in by (「蒸留器からレトルト炉へ水を送ろうとして、機械が止まることは
 * ありませんか？」) - and with nowhere to go it is let off as steam: the still never stops for it.
 * <p>
 * Which sides are the wood vinegar's (the user, 2026-11-13: kept for ten minutes, a side went back to taking water and the
 * Retort's line had it back; 「配置を変えた時に…パイプを壊さずに使いまわそうとしたら」 - so by where the line goes, not for
 * good): a side is shut to the water while its line - our pipes followed, else the block against it - still reaches a
 * tank or machine a wood vinegar line reached when wood vinegar last came in (a tank between the Retort and the
 * Distiller: filled with water while empty, it took no wood vinegar after, and the Retort stopped). Moved so it reaches
 * none of those, the side takes water again; another mod's pipe against it is that side's line until another block takes
 * its place. A Sealed Retort on a line of its own has the water (the user, 2026-11-14: 「木酢液に全く関係ない別のレトルト炉に
 * 別のパイプで蒸留器から直接水を送るのは、できた方がいい」; the one that feeds the still need not: 「出来なくてよい」), but
 * only once the still knows where its wood vinegar comes from - by a line, or poured in by hand: till then that Retort may
 * be its own, its line not yet known (a still from before 1.7, or one whose line was moved) - and only while that Retort
 * makes no wood vinegar (no log in it, none in its tank) and has half its water tank free: water standing in its pipes
 * would bar its wood vinegar's way, and one not boiling it off (unpowered, at a charge) would fill and leave it standing.
 * A shut side gives no water to what draws from it either (another mod's pipe that pulls). ({@link #lookAtSides} says
 * how it is looked at.)
 */
public class DistillerBlockEntity extends MercuryMachineBlockEntity implements MachineInfo {
    public static final int FE_PER_TICK = 60;
    public static final int TICKS = 120;
    public static final int COLUMN_SECTIONS = 2;
    private static final int OUTPUTS = 4;
    /** Four bottles' worth: only what is on its way out by pipe waits here. */
    public static final int METHANOL_TANK = 1_000;
    /** Sixteen bottles' worth of wood vinegar, piped or poured in, waiting to be distilled. */
    public static final int WOOD_VINEGAR_TANK = 4_000;
    /** The wood vinegar one run of the tank takes: a quarter of it methanol, the rest water. */
    public static final int WOOD_VINEGAR_RUN = 1_000;
    /** The water a run leaves, on its way out by pipe: past this it is let off as steam. */
    public static final int WATER_TANK = 1_000;
    /** How many tanks and machines a side's wood vinegar line is remembered by, at most. */
    private static final int FEEDS_KEPT = 32;
    /**
     * How many looks in a row (seconds) a side's line may miss what it fed from before that is forgotten: a pipe of the
     * line broken and set again (a Sight Glass for a pipe) is not a line moved.
     */
    private static final int MISSES_KEPT = 30;
    /** The blocks each side's wood vinegar line reached when wood vinegar last came in by it (saved). */
    private static final Codec<Map<Direction, List<BlockPos>>> FEEDS_CODEC = Codec.unboundedMap(Direction.CODEC, BlockPos.CODEC.listOf());
    /** How far along a pipe line from the Condenser it looks for the Distiller below. */
    private static final int LINE_LOOK = 512;

    /** Methanol on its way out: filled by its own work only, drawn by pipes (and by a bottle or bucket at its gauge). */
    private final FluidStacksResourceHandler methanol = Tanks.tank(METHANOL_TANK, fluid -> fluid == OilFluids.METHANOL.get(),
            false, true, this::setChanged);
    /** Wood vinegar to distil: filled by pipes and poured from what is put in the charge slot, never drawn by a pipe. */
    private final FluidStacksResourceHandler woodVinegar = Tanks.tank(WOOD_VINEGAR_TANK,
            fluid -> fluid == OilFluids.WOOD_VINEGAR.get(), true, false, this::setChanged);
    /** The water a run leaves: drawn by pipes, never filled. */
    private final FluidStacksResourceHandler water = Tanks.tank(WATER_TANK, fluid -> fluid == net.minecraft.world.level.material.Fluids.WATER,
            false, true, this::setChanged);
    /** What pipes and other mods see of the Distiller itself: its water to draw, its wood vinegar to fill. */
    private final ResourceHandler<FluidResource> fluids = new CombinedResourceHandler<>(this.water, this.woodVinegar);
    /**
     * The same, side by side, each noting wood vinegar put in through it (that side is where it comes in), and none of
     * the water drawn through a side shut to it ({@link #waterShut}).
     */
    private final java.util.List<ResourceHandler<FluidResource>> bySide = java.util.Arrays.stream(Direction.values())
            .map(side -> Tanks.noting(this.waterGuarded(side), transaction -> this.cameIn(side, transaction))).toList();
    /** Wood vinegar has come in by each side since the last look: its line is noted then. */
    private final boolean[] cameNow = new boolean[6];
    /** {@link #cameNow} kept with the transaction that set it: a pipe asking what would go in leaves it as it was. */
    private final SnapshotJournal<boolean[]> cameJournal = new SnapshotJournal<>() {
        @Override
        protected boolean[] createSnapshot() {
            return DistillerBlockEntity.this.cameNow.clone();
        }

        @Override
        protected void revertToSnapshot(boolean[] snapshot) {
            System.arraycopy(snapshot, 0, DistillerBlockEntity.this.cameNow, 0, snapshot.length);
        }
    };
    /** What each side's wood vinegar line reached when it last came in by it (saved). */
    private final Map<Direction, List<BlockPos>> feeds = new EnumMap<>(Direction.class);
    /** The block against each side at the last look, to see another take its place. */
    private final @Nullable Block[] against = new Block[6];
    /**
     * The block against each side at the last look, air too: what draws the water by a side has it only if it stood there
     * then (a pulling pipe set where one was broken, before the next look, drew it into a line not looked at).
     */
    private final @Nullable Block[] lookedAt = new Block[6];
    /** Looks in a row each side's line, walked whole, reached none of what it fed from: forgotten at {@link #MISSES_KEPT}. */
    private final int[] missed = new int[6];
    /** Where the Distiller stood when its feeds were saved: carried off since, they are looked for both there and moved with it. */
    private @Nullable BlockPos feedsSavedAt;
    /**
     * Wood vinegar has been put in by no side's line - poured from its charge slot, at its gauge or clicked in from a
     * bucket - since a line was last noted: the still knows where its wood vinegar comes from, and a line to a Sealed
     * Retort may have its water (saved).
     */
    private boolean pouredByHand;
    /** The wood vinegar held at the end of the last tick, to see it rise by hand; -1 till a first tick (not saved). */
    private int woodVinegarSeen = -1;
    /** Which sides the water may not leave by, looked at once a second. */
    private final boolean[] waterShut = {true, true, true, true, true, true};
    /** Which open sides' lines had something to take the water, at the same look. */
    private final boolean[] waterLine = new boolean[6];
    private long shutAskedAt = Long.MIN_VALUE / 2;
    /** The last look found nothing to give and walked no line: the first water to come is looked for at once. */
    private boolean stale;
    /** Where the methanol goes out: the Condenser's sides and top, into pipes only. */
    private final LiquidOutlet outlet = new LiquidOutlet(true, true);
    /** Where the water goes out: the Distiller's sides and bottom, not up into the column. */
    private final LiquidOutlet waterOutlet = new LiquidOutlet(false);
    /** Whether a pipe on each side of the Condenser runs to the Distiller as well, asked once a second. */
    private final boolean[] lineToBase = new boolean[6];
    private long lineAskedAt = Long.MIN_VALUE / 2;
    /** Whether the tower stood whole at the last look: when that changes, pipes by the Condenser look again. */
    private @Nullable Boolean wasFormed;
    /** Whether the work in hand is on the tank's wood vinegar; not saved, taken up afresh after a load. */
    private boolean workingFromTank;

    public DistillerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DISTILLER.get(), pos, state, OUTPUTS, 20_000);
    }

    @Override
    public Role mercuryRole() {
        return Role.PRODUCER;
    }

    /** Whether the column sections and the condenser are stacked on top. */
    public static boolean isFormed(LevelReader level, BlockPos base) {
        for (int i = 1; i <= COLUMN_SECTIONS; i++) {
            if (!level.getBlockState(base.above(i)).is(ModBlocks.DISTILLATION_COLUMN.get())) {
                return false;
            }
        }
        return level.getBlockState(base.above(COLUMN_SECTIONS + 1)).is(ModBlocks.CONDENSER.get());
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DistillerBlockEntity distiller) {
        if (level instanceof ServerLevel server) {
            distiller.noteHandPoured();
            distiller.tick(server, pos);
            distiller.woodVinegarSeen = distiller.woodVinegarStored();
        }
    }

    /**
     * Wood vinegar more than at the end of the last tick, no side marked as having had some come in by it: it came by hand
     * (a bucket at its gauge or clicked on it) or by something with no side. With a side marked, a rise is taken for that
     * line's - a pour by hand meanwhile is not missed for long, the side noted at the next look.
     */
    private void noteHandPoured() {
        int held = this.woodVinegarStored();
        if (this.pouredByHand || this.woodVinegarSeen < 0 || held <= this.woodVinegarSeen) {
            return;
        }
        for (boolean came : this.cameNow) {
            if (came) {
                return;
            }
        }
        this.pouredByHand = true;
        this.setChanged();
    }

    private void tick(ServerLevel level, BlockPos pos) {
        boolean formed = isFormed(level, pos);
        if (this.wasFormed == null || this.wasFormed != formed) {
            this.wasFormed = formed;
            condenserChanged(level, pos);
        }
        if (formed && this.mercury.amount() > 0) {
            MercuryNetwork.push(level, condenser(pos), this, PIPE_RATE, true);
        }
        if (formed && this.methanolStored() > 0) {
            this.pushMethanol(level, pos);
        }
        this.lookAtSides(level, pos);
        if (this.waterStored() > 0) {
            this.waterOutlet.push(level, pos, this, this.water, side -> !this.waterShut[side.ordinal()] && this.waterLine[side.ordinal()]);
        }
        this.pourIn();
        ItemStack charge = this.items.get(INPUT);
        boolean pouring = pours(charge);
        MercuryRecipes.Distillation recipe = pouring ? null : MercuryRecipes.distillation(charge);
        // with nothing else in the charge slot (or a bottle there waiting for room in the tank), the tank's wood vinegar
        boolean fromTank = recipe == null && (charge.isEmpty() || pouring) && this.woodVinegarStored() >= WOOD_VINEGAR_RUN
                && (recipe = MercuryRecipes.distillation(new ItemStack(ModOil.WOOD_VINEGAR.get()))) != null;
        if (recipe != null && fromTank != this.workingFromTank) {
            this.progress = 0; // the work done belongs to what it was done on
            this.workingFromTank = fromTank;
        }
        boolean flushed = this.flush(level, pos);
        MachineStatus blocked = !isFormed(level, pos) ? MachineStatus.INCOMPLETE
                : !flushed ? this.outputWaits()
                : recipe == null ? MachineStatus.IDLE
                : this.mercury.space() < recipe.mercury() ? MachineStatus.MERCURY_FULL
                : METHANOL_TANK - this.methanolStored() < methanolMade(recipe) ? MachineStatus.TANK_FULL : null;
        if (blocked != null) {
            // Nothing to work on - even while it waits for room - and what it had done went with what was in it;
            // stopped for room or its tower with the work still in it, it keeps what it had done.
            if (blocked == MachineStatus.IDLE || recipe == null) {
                this.progress = 0;
            }
            this.status = blocked;
            return;
        }
        if (!this.redstoneAllows()) {
            this.status = MachineStatus.REDSTONE;
            return;
        }
        if (this.energyStored() < this.workCost(FE_PER_TICK)) {
            this.status = MachineStatus.NO_POWER;
            return;
        }
        this.status = MachineStatus.RUNNING;
        this.payWork(FE_PER_TICK);
        if (++this.progress >= MachineTuning.time(TICKS)) {
            this.progress = 0;
            if (fromTank) {
                Tanks.set(this.woodVinegar, OilFluids.WOOD_VINEGAR.get(), this.woodVinegarStored() - WOOD_VINEGAR_RUN);
                this.leaveWater(level, pos, WOOD_VINEGAR_RUN - methanolMade(recipe));
            } else {
                this.items.get(INPUT).shrink(1);
            }
            this.mercury.fill(recipe.mercury(), false);
            boolean methanolResult = methanolMade(recipe) > 0;
            for (ItemStack made : products(level, pos, recipe)) {
                if (methanolResult && made.is(ModOil.METHANOL.get())) {
                    // the methanol goes into its tank as the liquid it is, whatever it was distilled from (a datapack's
                    // entry too): nothing is bottled by the machine (the user's rule, 2026-11-08); the room for it was
                    // asked before the run (TANK_FULL)
                    int room = METHANOL_TANK - this.methanolStored();
                    Tanks.set(this.methanol, OilFluids.METHANOL.get(),
                            this.methanolStored() + Math.min(room, made.getCount() * OilFluids.BOTTLE));
                    continue;
                }
                if (!this.addOutput(made)) {
                    Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, made);
                }
            }
            if (this.methanolStored() > 0) {
                this.pushMethanol(level, pos);
            }
            this.flush(level, pos);
        }
        this.setChanged();
    }

    static List<ItemStack> products(ServerLevel level, BlockPos pos, MercuryRecipes.Distillation recipe) {
        List<ItemStack> products = new ArrayList<>(2);
        if (recipe.result().isPresent()) {
            int count = recipe.count() + (level.getRandom().nextFloat() < recipe.bonusChance() ? recipe.bonus() : 0);
            // a stack at most, whatever a datapack asks: a bigger one is not saved, and is lost with the world closed
            products.add(new ItemStack(recipe.result().get(), Math.min(count, new ItemStack(recipe.result().get()).getMaxStackSize())));
        } else if (recipe.lootTable().isPresent()) {
            LootTable table = level.getServer().reloadableRegistries().getLootTable(recipe.lootTable().get());
            LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                    .create(LootContextParamSets.CHEST);
            products.addAll(table.getRandomItems(params));
        }
        // none while velarium is switched off (features.json)
        if (level.getRandom().nextFloat() < recipe.velariumChance() && dev.nookworks.feature.Features.enabled(ModItems.VELARIUM_NUGGET.get())) {
            products.add(new ItemStack(ModItems.VELARIUM_NUGGET.get()));
        }
        return products;
    }

    /**
     * Pours its methanol into the pipes on the Condenser's sides and top, not across a claim's edge (LiquidOutlet), nor
     * into a line that also runs to the Distiller below (its wood vinegar's or its water's: the methanol would plug it).
     */
    private void pushMethanol(ServerLevel level, BlockPos pos) {
        BlockPos top = condenser(pos);
        long now = level.getGameTime();
        if (now - this.lineAskedAt >= 20 || now < this.lineAskedAt) {
            this.lineAskedAt = now;
            for (Direction side : Direction.values()) {
                this.lineToBase[side.ordinal()] = side != Direction.DOWN
                        && level.getBlockEntity(top.relative(side)) instanceof dev.nookworks.fluid.FluidPipeBlockEntity pipe
                        && pipe.lineReaches(level, pos, LINE_LOOK);
            }
        }
        this.outlet.push(level, top, this, this.methanol, side -> side != Direction.DOWN && !this.lineToBase[side.ordinal()]);
    }

    /**
     * The water a run leaves into its water tank; what does not fit is let off as steam from the Condenser, a puff to see,
     * so a still with nowhere for its water still runs.
     */
    private void leaveWater(ServerLevel level, BlockPos pos, int amount) {
        if (amount <= 0) {
            return;
        }
        int room = WATER_TANK - this.waterStored();
        Tanks.set(this.water, net.minecraft.world.level.material.Fluids.WATER, this.waterStored() + Math.min(room, amount));
        if (amount > room) {
            BlockPos top = condenser(pos);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, top.getX() + 0.5, top.getY() + 1.1, top.getZ() + 0.5,
                    6, 0.2, 0.1, 0.2, 0.02);
        }
    }

    /**
     * Wood vinegar put in through {@code side}: its line is noted at the next look ({@link #lookAtSides}) - once the
     * putting in is done: a pipe asking whether the Distiller would take some (every tick an empty Copper Pipe beside it
     * asks so of everything it may carry, and takes it back) leaves no mark, or every pipe against it marked its side and
     * no water ever left by pipe (2026-11-13 review; 1.6.0 had it so).
     */
    private void cameIn(Direction side, TransactionContext transaction) {
        int i = side.ordinal();
        if (!this.cameNow[i]) {
            this.cameJournal.updateSnapshots(transaction);
            this.cameNow[i] = true;
        }
    }

    /**
     * Once a second, while it has water to give or wood vinegar has come in (and staggered by where it stands): which
     * sides the water may leave by. Each side's line is walked once - our pipes followed through shut valves, else the
     * block against it; a line touching several sides, once for all. A side is shut while its line reaches something a
     * wood vinegar line reached when wood vinegar last came in (any side's: a water line run round into the tank the wood
     * vinegar comes from is that tank's line), or a Sealed Retort that may not have it ({@link #mayWater}); the top
     * always, the column standing there (wood vinegar come in by it is not noted); one not walked whole
     * (a chunk not loaded along it) kept shut. The water goes only where this same look found something to take it - so
     * a line newly joined to a Retort or a feed is never poured into on the outlet's own word before this look saw where
     * it leads (2026-11-13 review). What a side's line reached is noted at the first look after wood vinegar comes in by it,
     * kept as offsets from the Distiller (a still carried off with its tank keeps it), and forgotten once its line,
     * walked whole, reaches none of it, or another block than our pipe takes the place of the one against it.
     */
    private void lookAtSides(ServerLevel level, BlockPos pos) {
        long now = level.getGameTime();
        boolean came = false;
        for (boolean c : this.cameNow) {
            came |= c;
        }
        boolean wanted = came || this.waterStored() > 0;
        boolean first = this.shutAskedAt == Long.MIN_VALUE / 2;
        boolean due = now - this.shutAskedAt >= 20 || now < this.shutAskedAt;
        if (!first && !due && !(this.stale && wanted)) {
            return;
        }
        // staggered: stills loaded together do not all walk their lines in one tick
        this.shutAskedAt = first ? now - Math.floorMod(pos.asLong(), 20) : now;
        if (!wanted && !first) {
            // nothing to give and nothing new: not walked, and nothing poured on an old look - the first water to come
            // is looked for at once
            this.stale = true;
            java.util.Arrays.fill(this.waterLine, false);
            return;
        }
        this.stale = false;
        BlockPos was = this.feedsSavedAt;
        if (was != null) {
            // carried off since its feeds were saved: each looked for where it was and moved with the still
            this.feedsSavedAt = null;
            if (!was.equals(pos)) {
                BlockPos step = pos.subtract(was);
                this.feeds.replaceAll((side, at) -> java.util.stream.Stream.concat(at.stream(), at.stream().map(p -> p.offset(step)))
                        .distinct().limit(FEEDS_KEPT * 2L).toList());
            }
        }
        dev.nookworks.fluid.FluidPipeBlockEntity.Line[] lines = new dev.nookworks.fluid.FluidPipeBlockEntity.Line[6];
        java.util.Map<BlockPos, dev.nookworks.fluid.FluidPipeBlockEntity.Line> walked = new java.util.HashMap<>();
        for (Direction side : Direction.values()) {
            int i = side.ordinal();
            BlockPos next = pos.relative(side);
            if (side == Direction.UP || !level.isLoaded(next)) {
                this.lookedAt[i] = null;
                continue;
            }
            Block block = level.getBlockState(next).getBlock();
            this.lookedAt[i] = block;
            if (level.getBlockEntity(next) instanceof dev.nookworks.fluid.FluidPipeBlockEntity pipe) {
                lines[i] = walked.get(next);
                if (lines[i] == null) {
                    lines[i] = pipe.line(level, pos, LINE_LOOK);
                    for (BlockPos p : lines[i].pipes()) {
                        walked.put(p, lines[i]);
                    }
                }
            } else {
                if (this.against[i] != null && this.against[i] != block && !level.getBlockState(next).isAir()
                        && this.feeds.remove(side) != null) {
                    this.setChanged(); // another block against it (not our pipe; air is a gap, not a block): its line is new
                }
                boolean holds = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Fluid.BLOCK, next, side.getOpposite()) != null;
                lines[i] = new dev.nookworks.fluid.FluidPipeBlockEntity.Line(java.util.Set.of(),
                        holds ? java.util.Map.of(next.immutable(), java.util.Set.of(side.getOpposite())) : java.util.Map.of(), true);
            }
            if (!level.getBlockState(next).isAir()) {
                this.against[i] = block;
            }
        }
        // the top's: the column's place, never a line to note (a tank set on a Distiller with no column poured down)
        this.cameNow[Direction.UP.ordinal()] = false;
        for (Direction side : Direction.values()) {
            int i = side.ordinal();
            if (this.cameNow[i] && lines[i] != null && lines[i].whole()) {
                // what can give wood vinegar: not another Distiller on a shared manifold (its wood vinegar is never drawn;
                // noted, a bank of stills shut each other's water lines and let all their water off as steam)
                this.cameNow[i] = false; // (not walked whole, it is noted at a later look)
                this.feeds.put(side, lines[i].ends().keySet().stream()
                        .filter(end -> !(level.getBlockEntity(end) instanceof DistillerBlockEntity)).limit(FEEDS_KEPT).toList());
                this.missed[i] = 0;
                // known by its line now: that line moved and forgotten, it knows of nothing again till the next comes in,
                // a bottle poured in long ago not making it sure of a Retort the moved line may lead to
                this.pouredByHand = false;
                this.setChanged();
            }
            List<BlockPos> fed = this.feeds.get(side);
            if (fed != null && lines[i] != null && lines[i].whole()) {
                if (fed.stream().anyMatch(lines[i].ends()::containsKey)) {
                    this.missed[i] = 0;
                } else if (++this.missed[i] >= MISSES_KEPT) {
                    this.feeds.remove(side); // moved: its line has reached none of what it fed from for half a minute
                    this.missed[i] = 0;
                    this.setChanged();
                }
            }
        }
        java.util.Set<BlockPos> fedFrom = new java.util.HashSet<>();
        this.feeds.values().forEach(fedFrom::addAll);
        boolean unsure = this.feeds.isEmpty() && !this.pouredByHand;
        FluidResource waterResource = FluidResource.of(net.minecraft.world.level.material.Fluids.WATER);
        for (Direction side : Direction.values()) {
            int i = side.ordinal();
            var line = lines[i];
            if (line == null || !line.whole()) {
                this.waterShut[i] = true;
                this.waterLine[i] = false;
                continue;
            }
            // shut while it still knows the side as a wood vinegar line (missing what it fed from only a while: a pipe of it
            // being changed), or its line reaches what any side's wood vinegar came from, or a Sealed Retort that may not
            // have it; a Retort on a line of its own, at no wood vinegar, has it
            this.waterShut[i] = this.feeds.containsKey(side) || line.ends().keySet().stream().anyMatch(end -> fedFrom.contains(end)
                    || level.getBlockEntity(end) instanceof RetortBlockEntity retort && (unsure || !mayWater(retort)));
            this.waterLine[i] = !this.waterShut[i] && line.ends().entrySet().stream().anyMatch(end -> end.getValue().stream().anyMatch(face -> {
                var handler = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Fluid.BLOCK, end.getKey(), face);
                return handler != null && Tanks.wouldTake(handler, waterResource, WATER_TANK);
            }));
        }
    }

    /**
     * Whether a Sealed Retort not known as where the wood vinegar comes from may have the water: making no liquid (no log
     * in it, no wood vinegar in its tank - a Retort piped in to feed the still, its logs come after the water, had its
     * pipes stand full of water and its wood vinegar nowhere to go), and with half its water tank free (one not boiling it
     * off - unpowered, at a charge - would fill, the water left standing in its pipes; 2026-11-14 review).
     */
    private static boolean mayWater(RetortBlockEntity retort) {
        return !retort.givesOffLiquid() && retort.waterStored() < RetortBlockEntity.WATER_TANK / 2;
    }

    /**
     * Whether what draws from {@code side} may have the water: the side open at the last look, and the same block against
     * it still (one set there since - a pipe that pulls, its line to a feed - waits for the next look).
     */
    private boolean givesWaterBy(Direction side) {
        int i = side.ordinal();
        Level level = this.level;
        BlockPos next = this.worldPosition.relative(side);
        return !this.waterShut[i] && level != null && this.lookedAt[i] != null && level.isLoaded(next)
                && level.getBlockState(next).getBlock() == this.lookedAt[i];
    }

    /** {@link #fluids}, giving no water through {@code side} while that side is shut to it. */
    private ResourceHandler<FluidResource> waterGuarded(Direction side) {
        ResourceHandler<FluidResource> all = this.fluids;
        return new ResourceHandler<>() {
            @Override
            public int size() {
                return all.size();
            }

            @Override
            public FluidResource getResource(int index) {
                return all.getResource(index);
            }

            @Override
            public long getAmountAsLong(int index) {
                return all.getAmountAsLong(index);
            }

            @Override
            public long getCapacityAsLong(int index, FluidResource resource) {
                return all.getCapacityAsLong(index, resource);
            }

            @Override
            public boolean isValid(int index, FluidResource resource) {
                return all.isValid(index, resource);
            }

            @Override
            public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
                return all.insert(index, resource, amount, transaction);
            }

            @Override
            public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
                return DistillerBlockEntity.this.givesWaterBy(side) ? all.extract(index, resource, amount, transaction) : 0;
            }
        };
    }

    /** The Condenser on top of a whole tower whose Distiller is at {@code base}. */
    public static BlockPos condenser(BlockPos base) {
        return base.above(COLUMN_SECTIONS + 1);
    }

    /** Whether {@code pos} holds the Condenser of a whole tower of ours, a Distiller under it. */
    public static boolean isTowerCondenser(LevelReader level, BlockPos pos) {
        BlockPos base = pos.below(COLUMN_SECTIONS + 1);
        return level.getBlockState(pos).is(ModBlocks.CONDENSER.get()) && level.getBlockState(base).is(ModBlocks.DISTILLER.get())
                && isFormed(level, base);
    }

    /**
     * What a pipe sees on {@code side} of a Condenser: the methanol of the whole tower's Distiller under it, drawn only - or
     * nothing, for any other Condenser (nookTechnology's Tar Still answers for its own) and on its underside, the column.
     */
    public static @Nullable ResourceHandler<FluidResource> condenserTank(Level level, BlockPos pos, @Nullable Direction side) {
        if (side == Direction.DOWN || !isTowerCondenser(level, pos)) {
            return null;
        }
        return level.getBlockEntity(pos.below(COLUMN_SECTIONS + 1)) instanceof DistillerBlockEntity distiller ? distiller.methanol : null;
    }

    /** The tower stood up or came apart: the Condenser's capability asked again, and the pipes by it look again. */
    private static void condenserChanged(Level level, BlockPos base) {
        BlockPos top = condenser(base);
        level.invalidateCapabilities(top);
        BlockState state = level.getBlockState(top);
        if (state.is(ModBlocks.CONDENSER.get())) {
            state.updateNeighbourShapes(level, top, net.minecraft.world.level.block.Block.UPDATE_ALL);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (this.level != null && !this.level.isClientSide()) {
            this.level.invalidateCapabilities(condenser(this.worldPosition));
        }
    }

    /** The methanol one run of {@code recipe} makes, in mB (its bonus counted in, so there is always room for it). */
    private static int methanolMade(MercuryRecipes.Distillation recipe) {
        return recipe.result().filter(result -> result.value() == ModOil.METHANOL.get()).isPresent()
                ? (recipe.count() + (recipe.bonusChance() > 0 ? recipe.bonus() : 0)) * OilFluids.BOTTLE : 0;
    }

    /**
     * Whether {@code stack} is wood vinegar the Distiller pours into its tank rather than distilling as it stands: a
     * Bottle of Wood Vinegar, a Wood Vinegar Bucket.
     */
    public static boolean pours(ItemStack stack) {
        return pouredAmount(stack) > 0;
    }

    private static int pouredAmount(ItemStack stack) {
        if (stack.getItem() instanceof BucketItem bucket && bucket.content == OilFluids.WOOD_VINEGAR.get()) {
            return 1_000;
        }
        return Bottles.fluidOf(stack) == OilFluids.WOOD_VINEGAR.get() ? OilFluids.BOTTLE : 0;
    }

    /** One bottle or bucket from the charge slot into the wood vinegar tank, the empty container into the outputs. */
    private void pourIn() {
        ItemStack charge = this.items.get(INPUT);
        int amount = pouredAmount(charge);
        if (amount <= 0 || WOOD_VINEGAR_TANK - this.woodVinegarStored() < amount) {
            return;
        }
        ItemStack empty = new ItemStack(charge.getItem() instanceof BucketItem ? Items.BUCKET : Items.GLASS_BOTTLE);
        if (!this.addOutput(empty)) {
            return; // no room for the empty one: it waits, as a product would
        }
        charge.shrink(1);
        Tanks.set(this.woodVinegar, OilFluids.WOOD_VINEGAR.get(), this.woodVinegarStored() + amount);
        this.pouredByHand = true;
        this.setChanged();
    }

    public int woodVinegarStored() {
        return this.woodVinegar.getAmountAsInt(0);
    }

    public int methanolStored() {
        return this.methanol.getAmountAsInt(0);
    }

    public int waterStored() {
        return this.water.getAmountAsInt(0);
    }

    /** Its tanks as pipes and other mods see the Distiller itself: its water drawn, never filled; its wood vinegar filled, never drawn. */
    public ResourceHandler<FluidResource> fluidHandler() {
        return this.fluids;
    }

    /** As {@link #fluidHandler()}, through {@code side}: what puts wood vinegar in there marks it where it comes in. */
    public ResourceHandler<FluidResource> fluidHandler(@Nullable Direction side) {
        return side == null ? this.fluids : this.bySide.get(side.ordinal());
    }

    /** The water tank alone: drawn from, never filled. */
    public FluidStacksResourceHandler waterTank() {
        return this.water;
    }

    /** The methanol tank alone: drawn from, never filled. */
    public FluidStacksResourceHandler methanolTank() {
        return this.methanol;
    }

    /** The wood vinegar tank alone. */
    public FluidStacksResourceHandler woodVinegarTank() {
        return this.woodVinegar;
    }

    /** The methanol it pipes off and the wood vinegar it distils (the mercury it gives back has a gauge of its own kind). */
    @Override
    public @Nullable ResourceHandler<FluidResource> gaugeTank(int index) {
        return switch (index) {
            case 0 -> this.methanol;
            case 1 -> this.woodVinegar;
            default -> null;
        };
    }

    @Override
    public List<GuiTank> guiTanks() {
        return List.of(new GuiTank(GuiTank.Kind.METHANOL, this.methanolStored(), METHANOL_TANK),
                new GuiTank(GuiTank.Kind.WOOD_VINEGAR, this.woodVinegarStored(), WOOD_VINEGAR_TANK), this.mercuryTank());
    }

    /** Its methanol, wood vinegar and water go with it, as its mercury does. */
    @Override
    protected void heldFluids(Consumer<FluidStack> out) {
        super.heldFluids(out);
        out.accept(Tanks.held(this.methanol));
        out.accept(Tanks.held(this.woodVinegar));
        out.accept(Tanks.held(this.water));
    }

    @Override
    protected void restoreFluid(FluidStack fluid) {
        if (!Tanks.restore(this.methanol, fluid) && !Tanks.restore(this.woodVinegar, fluid) && !Tanks.restore(this.water, fluid)) {
            super.restoreFluid(fluid);
        }
    }

    /**
     * "methanol" and "wood_vinegar" only since 1.5, "water" since the play-test after it, "wood_vinegar_feeds" and
     * "wood_vinegar_poured" since 1.7 (1.6's "wood_vinegar_inlets", times, is no longer read: a pipe's asking marked them
     * too): a Distiller saved before has none of them, and its tanks start empty, no side known for its wood vinegar until
     * some comes in - and till then no water to a Sealed Retort, which may be the one that feeds it.
     */
    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        this.methanol.serialize(output.child("methanol"));
        this.woodVinegar.serialize(output.child("wood_vinegar"));
        this.water.serialize(output.child("water"));
        if (!this.feeds.isEmpty()) {
            output.store("wood_vinegar_feeds", FEEDS_CODEC, Map.copyOf(this.feeds));
            output.store("wood_vinegar_feeds_at", BlockPos.CODEC, this.worldPosition);
        }
        if (this.pouredByHand) {
            output.putBoolean("wood_vinegar_poured", true);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child("methanol").ifPresent(this.methanol::deserialize);
        input.child("wood_vinegar").ifPresent(this.woodVinegar::deserialize);
        input.child("water").ifPresent(this.water::deserialize);
        this.feeds.clear();
        input.read("wood_vinegar_feeds", FEEDS_CODEC).ifPresent(this.feeds::putAll);
        this.feedsSavedAt = input.read("wood_vinegar_feeds_at", BlockPos.CODEC).orElse(null);
        this.pouredByHand = input.getBooleanOr("wood_vinegar_poured", false);
        this.woodVinegarSeen = -1; // read in afresh: no rise by hand to see till a tick has passed
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return takes(slot, stack);
    }

    /** What slot {@code slot} takes: the machine's rule, and its screen's on the client (MachineMenu). */
    public static boolean takes(int slot, ItemStack stack) {
        return slot == INPUT && (MercuryRecipes.distillation(stack) != null || pours(stack));
    }

    @Override
    public MachineLayout layout() {
        return MachineLayout.DISTILLER;
    }

    @Override
    public int guiEnergyRate() {
        return MachineTuning.workEnergy(FE_PER_TICK);
    }

    @Override
    public int guiProgressMax() {
        return MachineTuning.time(TICKS);
    }

    /**
     * 1 while it works the tank's wood vinegar, a liquid into liquids: the screen's arrow, which stands for an item worked,
     * stays still, the run's progress said in the status line (the user, 2026-11-12: 「液体を液体に変えているだけなのに、
     * アイテムの横の矢印のアニメーションが出るのは不自然」).
     */
    @Override
    public int guiExtra() {
        return this.workingFromTank && this.status == MachineStatus.RUNNING ? 1 : 0;
    }

    @Override
    public Component status() {
        if (this.level != null && !isFormed(this.level, this.worldPosition)) {
            return Component.translatable("nookworks.machine.distiller.incomplete");
        }
        return Component.translatable("nookworks.machine.mercury_status", this.energyText(), this.mercuryText(), MachineTuning.percent(this.progress, MachineTuning.time(TICKS)));
    }
}
