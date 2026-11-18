package dev.nookworks.item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import dev.nookworks.feature.Permission;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The Building Wands' work on the server (the user, 2026-11-07): what one use puts down, remembered so that it can be
 * taken back once (either wand); and, the Reinforced Building Wand's alone, replacing a surface with the block in the
 * other hand and filling a box a few hundred blocks a tick, so a big one does not stall the server.
 * <p>
 * Every block is asked about as a hand placing it would be ({@link BuildingWandItem#place}), and its material taken
 * only once it stands. The wand wears one point a block, and stops with one point left rather than break in the middle
 * of a wall. Only the last use is remembered, and only until the player leaves: taking it back puts each square as it
 * was if what the wand put there still stands, hands the materials back, and mends the wand by as much.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class WandWork {
    /** Blocks a box puts down a tick. */
    public static final int PER_TICK = 256;

    /**
     * One square the wand changed: the block it put there as it stood once set, what stood there before, the material it
     * took, what it gave.
     */
    record Done(BlockPos pos, BlockState placed, BlockState before, ItemStack material, List<ItemStack> given) {}

    /**
     * The last use of a player's wand, as taken back: the wand it was (the very stack) and the wear it really took, which
     * Unbreaking may have made less than a point a block (r1 review of the trial, 2026-11-07: undo mended more than was
     * worn, and whichever wand was in hand).
     */
    static final class Last {
        final ResourceKey<Level> dimension;
        final List<Done> done = new ArrayList<>();
        @Nullable ItemStack wand;
        int wear;

        Last(ResourceKey<Level> dimension) {
            this.dimension = dimension;
        }
    }

    /** A box being filled. */
    static final class Job {
        final Player player;
        final ServerLevel level;
        final ItemStack wand;
        final BlockState block;
        final List<BlockPos> cells;
        final Last last;
        int next;
        int placed;

        Job(Player player, ServerLevel level, ItemStack wand, BlockState block, List<BlockPos> cells, Last last) {
            this.player = player;
            this.level = level;
            this.wand = wand;
            this.block = block;
            this.cells = cells;
            this.last = last;
        }
    }

    private static final Map<UUID, Last> LAST = new HashMap<>();
    private static final Map<UUID, Job> JOBS = new HashMap<>();

    /** How it stopped, or that it went on. */
    enum Step { PLACED, SKIPPED, NO_BLOCKS }

    private WandWork() {}

    /** How many more blocks {@code wand} may put down: all but its last point, or any number when it does not wear. */
    static int budget(Player player, ItemStack wand) {
        if (player.hasInfiniteMaterials() || !wand.isDamageableItem()) {
            return Integer.MAX_VALUE;
        }
        return Math.max(0, wand.getMaxDamage() - wand.getDamageValue() - 1);
    }

    static void wear(ServerLevel level, Player player, ItemStack wand, int blocks, Last last) {
        if (blocks > 0) {
            int before = wand.getDamageValue();
            wand.hurtAndBreak(blocks, level, player, item -> {});
            last.wand = wand;
            last.wear += Math.max(0, wand.getDamageValue() - before);
        }
    }

    /** A new use, which forgets the last one (and stops a box still being filled). */
    static Last begin(Player player, ServerLevel level) {
        finishJob(player.getUUID());
        Last last = new Last(level.dimension());
        LAST.put(player.getUUID(), last);
        return last;
    }

    /**
     * Puts one block of {@code template} at {@code pos}, from the player's inventory, if the square is free and a hand
     * could put it there. Records it in {@code last}.
     */
    static Step placeOne(ServerLevel level, Player player, BlockPos pos, Direction face, BlockState template, ItemStack wand,
            Last last) {
        BlockState before = level.getBlockState(pos);
        if (!level.isLoaded(pos) || !before.canBeReplaced()) {
            return Step.SKIPPED;
        }
        int cost = BuildingWandItem.cost(template);
        int slot = BuildingWandItem.find(player, template);
        boolean free = player.hasInfiniteMaterials();
        if (!free && (slot < 0 || BuildingWandItem.count(player, template) < cost)) {
            return Step.NO_BLOCKS;
        }
        ItemStack from = slot < 0 ? ItemStack.EMPTY : BuildingWandItem.stacks(player).get(slot);
        BlockState block = slot < 0 ? template : BuildingWandItem.placing(template, from);
        ItemStack material = free ? ItemStack.EMPTY : from.copyWithCount(cost);
        if (!BuildingWandItem.place(level, player, pos, face, block, wand)) {
            return Step.SKIPPED;
        }
        if (!free) {
            BuildingWandItem.take(player, template, slot, cost);
        }
        last.done.add(new Done(pos, level.getBlockState(pos), before, material, List.of()));
        return Step.PLACED;
    }

    /** The surface mode, the wand as it always was: {@code targets} from {@link BuildingWandItem#plan}. */
    static void spread(ServerLevel level, Player player, BlockPos clicked, Direction face, BlockState template,
            List<BlockPos> targets, ItemStack wand) {
        Last last = begin(player, level);
        int budget = budget(player, wand);
        int placed = 0;
        Step stop = null;
        for (BlockPos target : targets) {
            if (placed >= budget) {
                stop = null;
                break;
            }
            Step step = placeOne(level, player, target, face, template, wand, last);
            if (step == Step.NO_BLOCKS) {
                stop = step;
                break;
            }
            if (step == Step.PLACED) {
                placed++;
            }
        }
        wear(level, player, wand, placed, last);
        if (placed > 0) {
            level.playSound(null, clicked, template.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
        } else {
            level.playSound(null, clicked, SoundEvents.DISPENSER_FAIL, SoundSource.BLOCKS, 0.6F, 1.0F);
        }
        if (placed >= budget && budget < targets.size()) {
            say(player, "worn", placed);
        } else if (stop == Step.NO_BLOCKS && placed < targets.size()) {
            say(player, "out_of_blocks", placed);
        }
    }

    /**
     * Why Replace will not turn the block at {@code clicked} into the one in the player's other hand - the key of the
     * message that says so - or null when it will. Asked by the server before it replaces and by the client's outline,
     * so the two agree.
     * <p>
     * Since the user's 2026-11-07 decision (the trial's Replace was a free fast hammer: 32 blocks dug at a click, no
     * digging time, and torches or carpets put in their place would have tunnelled through rock) three limits: what goes
     * in must be an ordinary full block, solid all through ({@link #fullBlock}); ores are left alone; and so is anything
     * harder than the deepslate stones ({@link #hardest}) - obsidian, ancient debris, the metal blocks - and anything
     * that cannot be broken.
     */
    public static @Nullable String replaceRefusal(Level level, Player player, BlockPos clicked, BlockState old) {
        ItemStack other = player.getOffhandItem();
        if (!(other.getItem() instanceof BlockItem item) || !BuildingWandItem.canCopy(item.getBlock().defaultBlockState())) {
            return "need_block";
        }
        if (item.getBlock() == old.getBlock()) {
            return "same_block";
        }
        if (!fullBlock(level, clicked, BuildingWandItem.oriented(old, item.getBlock().defaultBlockState()))) {
            return "not_full_block";
        }
        if (!BuildingWandItem.canCopy(old)) {
            return "cannot_replace";
        }
        if (old.is(Tags.Blocks.ORES)) { // (1.21.1 has no #minecraft:ores; #c:ores holds every vanilla ore)
            return "ore";
        }
        float hardness = old.getDestroySpeed(level, clicked);
        if (hardness < 0 || hardness > hardest()) {
            return "too_hard";
        }
        return tool(player, old) == null ? "no_tool" : null;
    }

    /**
     * The hardest block Replace takes out: the deepslate stones' - cobbled, polished, bricks and tiles (3.5; deepslate
     * itself is 3.0) - read from vanilla's own cobbled deepslate, so a player's deepslate walls can be replaced but
     * obsidian (50), ancient debris (30) or a block of iron (5) cannot.
     */
    public static float hardest() {
        return Blocks.COBBLED_DEEPSLATE.defaultBlockState().getDestroySpeed(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }

    /**
     * Whether {@code state} is an ordinary full block: one whose collision shape fills the whole square, so a surface
     * replaced with it is as solid as before - no torch, carpet, slab, stair, flower, pane or fence opens a way through.
     */
    public static boolean fullBlock(Level level, BlockPos pos, BlockState state) {
        return !state.isAir() && state.isCollisionShapeFullBlock(level, pos);
    }

    /**
     * The replace mode: the flat surface of blocks like the one clicked ({@link #surface}) becomes the block in the other
     * hand, turned as each was. What comes out of each is what the player's own tool for it would dig (no tool, what a
     * hand digs); a block that needs a tool the player does not carry is not touched, nor anything
     * {@link #replaceRefusal} turns down.
     */
    static void replace(ServerLevel level, Player player, BlockPos clicked, Direction face, ItemStack wand) {
        BlockState old = level.getBlockState(clicked);
        String refusal = replaceRefusal(level, player, clicked, old);
        if (refusal != null) {
            fail(level, player, clicked, refusal);
            return;
        }
        ItemStack tool = tool(player, old);
        BlockItem item = (BlockItem) player.getOffhandItem().getItem();
        BlockState template = BuildingWandItem.oriented(old, item.getBlock().defaultBlockState());
        List<BlockPos> squares = surface(level, clicked, face, old);
        Last last = begin(player, level);
        int budget = budget(player, wand);
        int replaced = 0;
        boolean ranOut = false;
        boolean free = player.hasInfiniteMaterials();
        int cost = BuildingWandItem.cost(template);
        for (BlockPos pos : squares) {
            if (replaced >= budget) {
                break;
            }
            if (!Permission.mayBreak(level, pos, player)) {
                continue;
            }
            int slot = BuildingWandItem.find(player, template);
            if (!free && (slot < 0 || BuildingWandItem.count(player, template) < cost)) {
                ranOut = true;
                break;
            }
            ItemStack from = slot < 0 ? ItemStack.EMPTY : BuildingWandItem.stacks(player).get(slot);
            BlockState block = slot < 0 ? template : BuildingWandItem.placing(template, from);
            ItemStack material = free ? ItemStack.EMPTY : from.copyWithCount(cost);
            List<ItemStack> drops = free ? List.of() : drops(level, pos, old, player, tool);
            if (!BuildingWandItem.place(level, player, pos, face, block, wand)) {
                continue;
            }
            if (!free) {
                BuildingWandItem.take(player, template, slot, cost);
            }
            List<ItemStack> given = new ArrayList<>();
            for (ItemStack drop : drops) {
                given.add(drop.copy());
                give(player, drop);
            }
            last.done.add(new Done(pos, level.getBlockState(pos), old, material, given));
            replaced++;
            if (!tool.isEmpty() && tool.isDamageableItem()) {
                tool.hurtAndBreak(1, level, player, broken -> {}); // dug, as a hand digging it would wear it
                if (tool.isEmpty()) {
                    break;
                }
            }
        }
        wear(level, player, wand, replaced, last);
        if (replaced > 0) {
            level.playSound(null, clicked, template.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
            say(player, ranOut ? "out_of_blocks" : replaced >= budget && budget < squares.size() ? "worn" : "replaced", replaced);
        } else {
            fail(level, player, clicked, ranOut ? "no_blocks" : null);
        }
    }

    /**
     * The squares of the flat surface of blocks the same as {@code template} that {@code clicked} is part of, across
     * the clicked face, as far as {@link BuildingWandItem#plan} walks: the blocks themselves, not the space in front.
     */
    public static List<BlockPos> surface(Level level, BlockPos clicked, Direction face, BlockState template) {
        List<BlockPos> found = new ArrayList<>();
        List<BlockPos> queue = new ArrayList<>(List.of(clicked));
        java.util.Set<BlockPos> seen = new java.util.HashSet<>(queue);
        for (int i = 0; i < queue.size() && i < BuildingWandItem.MAX_VISITED && found.size() < BuildingWandItem.MAX_BLOCKS; i++) {
            BlockPos current = queue.get(i);
            if (level.getBlockState(current) != template) {
                continue;
            }
            found.add(current);
            for (Direction step : Direction.values()) {
                BlockPos next = current.relative(step);
                if (step.getAxis() != face.getAxis() && BuildingWandItem.chessboard(next, clicked) <= BuildingWandItem.MAX_REACH
                        && level.isLoaded(next) && seen.add(next) && level.getBlockState(next) == template) {
                    queue.add(next);
                }
            }
        }
        return found;
    }

    /**
     * What digging {@code state} at {@code pos} with {@code tool} gives, rolled from the square and the world rather than
     * at random: replacing a block, taking it back and replacing it again gives the same, so Fortune or a sapling cannot be
     * rolled again that way (r1 review of the trial). No experience comes of it, as none would of an ore put back.
     */
    static List<ItemStack> drops(ServerLevel level, BlockPos pos, BlockState state, Player player, ItemStack tool) {
        var key = state.getBlock().getLootTable();
        if (key == net.minecraft.world.level.storage.loot.BuiltInLootTables.EMPTY) { // 1.21.1: no Optional, the empty table
            return List.of();
        }
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .withParameter(LootContextParams.TOOL, tool)
                .withOptionalParameter(LootContextParams.THIS_ENTITY, player)
                .withParameter(LootContextParams.BLOCK_STATE, state)
                .create(LootContextParamSets.BLOCK);
        long seed = level.getSeed() ^ Mth.getSeed(pos) ^ (long) BuiltInRegistries.BLOCK.getKey(state.getBlock()).hashCode() << 32;
        return level.getServer().reloadableRegistries().getLootTable(key).getRandomItems(params, seed);
    }

    /**
     * The tool the player would dig {@code state} with: the first they carry that is right for it; none (a hand) when it
     * needs none and they carry none; null when it needs one they do not carry.
     */
    static @Nullable ItemStack tool(Player player, BlockState state) {
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && stack.getDestroySpeed(state) > 1.0F && stack.isCorrectToolForDrops(state)) {
                return stack;
            }
        }
        return state.requiresCorrectToolForDrops() ? null : ItemStack.EMPTY;
    }

    /** Starts filling the box from {@code a} to {@code b} as {@code mode} fills it, a share a tick. */
    static void startBox(ServerLevel level, Player player, ItemStack wand, BlockPos a, BlockPos b, WandMode mode, BlockState block) {
        if (!WandBox.fits(a, b)) {
            return; // never listed: a box's cells are listed whole before they are placed a few a tick
        }
        Last last = begin(player, level);
        JOBS.put(player.getUUID(), new Job(player, level, wand, block, WandBox.cells(a, b, mode), last));
        level.playSound(null, a, block.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    /** Whether a box of this player's is still being filled. */
    public static boolean busy(Player player) {
        return JOBS.containsKey(player.getUUID());
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        for (Iterator<Job> it = JOBS.values().iterator(); it.hasNext();) {
            Job job = it.next();
            String end = work(job);
            if (end != null) {
                it.remove();
                say(job.player, end, job.placed);
                if (job.placed > 0) {
                    job.level.playSound(null, job.player.blockPosition(), job.block.getSoundType().getPlaceSound(),
                            SoundSource.BLOCKS, 1.0F, 1.0F);
                }
            }
        }
    }

    /** One tick of a box; the message it ends with, or null while it goes on. */
    private static @Nullable String work(Job job) {
        Player player = job.player;
        if (player.isRemoved() || player.level() != job.level || !holds(player, job.wand)) {
            return "stopped";
        }
        int budget = budget(player, job.wand);
        int placed = 0;
        String end = null;
        while (placed < PER_TICK && job.next < job.cells.size()) {
            if (placed >= budget) {
                end = "worn";
                break;
            }
            Step step = placeOne(job.level, player, job.cells.get(job.next), Direction.UP, job.block, job.wand, job.last);
            if (step == Step.NO_BLOCKS) {
                end = "out_of_blocks";
                break;
            }
            job.next++;
            if (step == Step.PLACED) {
                placed++;
            }
        }
        wear(job.level, player, job.wand, placed, job.last);
        job.placed += placed;
        if (end == null && job.next >= job.cells.size()) {
            end = "done";
        }
        return end;
    }

    private static boolean holds(Player player, ItemStack wand) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack == wand) {
                return true;
            }
        }
        return player.getOffhandItem() == wand;
    }

    private static void finishJob(UUID player) {
        Job job = JOBS.remove(player);
        if (job != null && job.placed > 0) {
            say(job.player, "stopped", job.placed);
        }
    }

    /**
     * Takes the last use back: every square whose block the wand put there still stands goes back to what it was, its
     * material back to the player (and, for a replaced block, what it gave taken back - only while the player still has
     * it). The wand mends by as many points.
     */
    static void undo(ServerLevel level, Player player, ItemStack wand) {
        finishJob(player.getUUID());
        Last last = LAST.get(player.getUUID());
        if (last == null || last.done.isEmpty() || last.dimension != level.dimension()) {
            fail(level, player, player.blockPosition(), "nothing_to_undo");
            return; // kept: taken back from the world it was made in
        }
        LAST.remove(player.getUUID());
        int undone = 0;
        for (int i = last.done.size() - 1; i >= 0; i--) {
            Done done = last.done.get(i);
            if (!level.isLoaded(done.pos()) || !unchanged(level, done.pos(), done.placed())
                    || !Permission.mayBreak(level, done.pos(), player)) {
                continue;
            }
            if (!done.given().isEmpty() && !takeBack(player, done.given())) {
                continue;
            }
            level.setBlock(done.pos(), done.before(), Block.UPDATE_ALL);
            if (!done.material().isEmpty()) {
                give(player, done.material().copy());
            }
            undone++;
        }
        // the wear really taken, in the share of the squares taken back, mended on the wand that took it
        if (undone > 0 && last.wand != null && last.wear > 0 && holds(player, last.wand)) {
            int mend = (int) ((long) last.wear * undone / last.done.size());
            last.wand.setDamageValue(Math.max(0, last.wand.getDamageValue() - mend));
        }
        if (undone > 0) {
            level.playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6F, 0.8F);
            say(player, "undone", undone);
        } else {
            fail(level, player, player.blockPosition(), "nothing_to_undo");
        }
    }

    /**
     * Whether the square still holds what the wand put there, as it was put: the same state, or one its neighbours have
     * only reshaped (a fence joined since). A cake bitten, a slab broken in half, a candle added - anything else - is not
     * the wand's to take back (r1 review: a bitten cake came back whole).
     */
    private static boolean unchanged(ServerLevel level, BlockPos pos, BlockState placed) {
        BlockState now = level.getBlockState(pos);
        return now == placed || (now.is(placed.getBlock()) && Block.updateFromNeighbourShapes(placed, level, pos) == now);
    }

    /** Takes {@code stacks} out of the player's inventory, all or none. */
    private static boolean takeBack(Player player, List<ItemStack> stacks) {
        var items = player.getInventory().items;
        for (ItemStack wanted : stacks) {
            int have = 0;
            for (ItemStack stack : items) {
                if (ItemStack.isSameItemSameComponents(stack, wanted)) {
                    have += stack.getCount();
                }
            }
            int needed = stacks.stream().filter(s -> ItemStack.isSameItemSameComponents(s, wanted)).mapToInt(ItemStack::getCount).sum();
            if (have < needed) {
                return false;
            }
        }
        for (ItemStack wanted : stacks) {
            int left = wanted.getCount();
            for (ItemStack stack : items) {
                if (left > 0 && ItemStack.isSameItemSameComponents(stack, wanted)) {
                    int take = Math.min(left, stack.getCount());
                    stack.shrink(take);
                    left -= take;
                }
            }
        }
        return true;
    }

    private static void give(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack) && !stack.isEmpty()) {
            player.drop(stack, false);
        }
    }

    static void say(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable("message.nookworks.wand." + key, args), true);
    }

    private static void fail(ServerLevel level, Player player, BlockPos at, @Nullable String key) {
        level.playSound(null, at, SoundEvents.DISPENSER_FAIL, SoundSource.BLOCKS, 0.6F, 1.0F);
        if (key != null) {
            say(player, key);
        }
    }

    @SubscribeEvent
    static void onLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        LAST.remove(id);
        JOBS.remove(id);
    }
}
