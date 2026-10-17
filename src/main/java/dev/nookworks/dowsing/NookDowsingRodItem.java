package dev.nookworks.dowsing;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import dev.nookworks.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * The Nook Dowsing Rod: the way to the cinnabar the mercury chain starts from, which is found only in the sulfur caves
 * (#nookworks:has_cinnabar - vanilla's sulfur caves; a pack adds another mod's biomes there). Used, it searches out to
 * 6,400 blocks for the nearest ({@link DowsingSearch}, a little a tick), then points there as a lodestone compass
 * points - the same needle, by the same component, so it spins in another dimension - and says how far and how deep.
 * Each find wears it a little; copper mends it. Standing in the caves already, it says so and wears nothing.
 * <p>
 * The search takes a while, and the rod may be moved meanwhile - to the other hand, into a chest - which makes a new
 * stack of it: so the rod is marked with its search ({@link #SEARCH}), and the find goes to the rod carrying that mark,
 * wherever in its player's inventory it is by then. A rod no longer with them is told nothing (its player still is).
 * <p>
 * Unsaid anywhere but the changelog (the user, 2026-10-08: a hidden feature): in the Nether, where there is no cinnabar,
 * it finds the nearest Nether fortress, and in the End the nearest End city - #nookworks:dowsing/nether and
 * #nookworks:dowsing/end, as /locate structure finds them, the same few milliseconds a tick.
 */
public class NookDowsingRodItem extends Item {
    public static final TagKey<Biome> HAS_CINNABAR = TagKey.create(Registries.BIOME, Nookworks.id("has_cinnabar"));
    public static final TagKey<Structure> NETHER_FIND = TagKey.create(Registries.STRUCTURE, Nookworks.id("dowsing/nether"));
    public static final TagKey<Structure> END_FIND = TagKey.create(Registries.STRUCTURE, Nookworks.id("dowsing/end"));
    /** In a rod's custom data while its search runs: which search it is. */
    private static final String SEARCH = "nookworks_dowsing_search";
    /** Closer than this across the ground, what was found is said to be straight below (or above). */
    private static final int STRAIGHT = 16;

    public NookDowsingRodItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer seeker)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack rod = player.getItemInHand(hand);
        BlockPos at = player.blockPosition();
        String what = sought(server);
        TagKey<Structure> structures = what == null ? null : server.dimension() == Level.NETHER ? NETHER_FIND : END_FIND;
        if (structures == null ? server.getBiome(at).is(HAS_CINNABAR) : server.structureManager().getStructureWithPieceAt(at, structures).isValid()) {
            player.sendOverlayMessage(what == null ? Component.translatable("message.nookworks.nook_dowsing_rod.here")
                    : Component.translatable("message.nookworks.nook_dowsing_rod.inside", name(what)));
            server.playSound(null, at, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.6F, 1.4F);
            return InteractionResult.SUCCESS;
        }
        if (DowsingSearch.running(seeker.getUUID())) {
            // still at the last search: said so, and that search's rod and answer left as they are (marked anew, the rod
            // would not be found when it ends, and would get no needle)
            player.sendOverlayMessage(Component.translatable("message.nookworks.nook_dowsing_rod.still"));
            return InteractionResult.FAIL;
        }
        // said first: a dimension with none answers at once, and its answer must not be covered by this
        player.sendOverlayMessage(Component.translatable("message.nookworks.nook_dowsing_rod.searching"));
        long search = mark(rod, server.getRandom().nextLong());
        boolean[] answered = {false};
        // theirs by who they are, not by the player object: one who dies and comes back meanwhile is a new one
        Consumer<DowsingSearch.Result> done = result -> {
            answered[0] = true;
            found(server, seeker, search, at, result, what);
        };
        boolean started = structures == null
                ? DowsingSearch.start(server, at, biome -> biome.is(HAS_CINNABAR), DowsingSearch.RADIUS, seeker.getUUID(), done)
                : DowsingSearch.startStructures(server, at, structures, DowsingSearch.RADIUS, seeker.getUUID(), done);
        if (!started) {
            unmark(rod);
            return InteractionResult.FAIL; // the last search of theirs still running
        }
        if (!answered[0]) { // the chime of a search begun; a world with none has answered already
            server.playSound(null, at, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6F, 0.8F);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * What the rod is after in {@code level}: null for cinnabar, "fortress" in the Nether, "end_city" in the End. Public
     * for the tests.
     */
    public static @Nullable String sought(ServerLevel level) {
        return level.dimension() == Level.NETHER ? "fortress" : level.dimension() == Level.END ? "end_city" : null;
    }

    private static Component name(String what) {
        return Component.translatable("message.nookworks.nook_dowsing_rod." + what);
    }

    /** Marks {@code rod} as the one making search {@code search} (never 0), and returns the mark. Public for the tests. */
    public static long mark(ItemStack rod, long search) {
        long id = search == 0 ? 1 : search;
        CustomData.update(DataComponents.CUSTOM_DATA, rod, tag -> tag.putLong(SEARCH, id));
        return id;
    }

    /** Whether {@code rod} still carries the mark of a search. Public for the tests. */
    public static boolean marked(ItemStack rod) {
        return rod.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).contains(SEARCH);
    }

    private static void unmark(ItemStack rod) {
        CustomData.update(DataComponents.CUSTOM_DATA, rod, tag -> tag.remove(SEARCH));
    }

    /** The rod carrying search {@code search}'s mark, wherever in {@code player}'s inventory it went; empty if none. */
    private static ItemStack rodOf(ServerPlayer player, long search) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (isRodOf(stack, search)) {
                return stack;
            }
        }
        ItemStack carried = player.containerMenu.getCarried(); // held on the cursor in a screen
        return isRodOf(carried, search) ? carried : ItemStack.EMPTY;
    }

    private static boolean isRodOf(ItemStack stack, long search) {
        return stack.is(ModItems.NOOK_DOWSING_ROD.get())
                && stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getLongOr(SEARCH, 0L) == search;
    }

    /**
     * The search's end: the needle of the rod that made it set (or cleared) and worn, and the seeker told - the seeker as
     * they are now, found by who they are: one who died and came back while it ran is a new player, the rod picked up
     * again in their hands, not the old one's emptied inventory. Public for the tests, which hand it a result rather than
     * wait for a world that has sulfur caves.
     */
    public static void found(ServerLevel searched, ServerPlayer asked, long search, BlockPos from, DowsingSearch.Result result) {
        found(searched, asked, search, from, result, null);
    }

    /** The same, for a search after {@code what} ({@link #sought}): null for cinnabar. */
    public static void found(ServerLevel searched, ServerPlayer asked, long search, BlockPos from, DowsingSearch.Result result,
            @Nullable String what) {
        ServerPlayer seeker = searched.getServer().getPlayerList().getPlayer(asked.getUUID());
        if (seeker == null || seeker.hasDisconnected()) {
            return; // gone: the mark left on the rod does no harm, and goes with the next search
        }
        ItemStack rod = rodOf(seeker, search);
        if (!rod.isEmpty()) {
            unmark(rod);
        }
        if (result.outcome() != DowsingSearch.Outcome.FOUND || result.at() == null) {
            if (!rod.isEmpty()) {
                rod.remove(DataComponents.LODESTONE_TRACKER);
            }
            String radius = String.format(Locale.ROOT, "%,d", DowsingSearch.RADIUS);
            seeker.sendOverlayMessage(what != null
                    ? Component.translatable("message.nookworks.nook_dowsing_rod.none_of", name(what), radius)
                    : result.outcome() == DowsingSearch.Outcome.NOT_HERE
                    ? Component.translatable("message.nookworks.nook_dowsing_rod.not_here")
                    : Component.translatable("message.nookworks.nook_dowsing_rod.none", radius));
            return;
        }
        BlockPos at = result.at();
        double dx = at.getX() - from.getX();
        double dz = at.getZ() - from.getZ();
        double across = Math.sqrt(dx * dx + dz * dz);
        if (what != null) {
            // a structure's place is where it starts, at no height of its own: how far, and no depth
            long distance = Math.max(10, Math.round(across / 10.0) * 10);
            seeker.sendOverlayMessage(Component.translatable("message.nookworks.nook_dowsing_rod.found_of", name(what),
                    String.format(Locale.ROOT, "%,d", distance)));
        } else if (across < STRAIGHT) {
            seeker.sendOverlayMessage(Component.translatable(at.getY() < from.getY()
                    ? "message.nookworks.nook_dowsing_rod.below" : "message.nookworks.nook_dowsing_rod.above", at.getY()));
        } else {
            long distance = Math.round(across / 10.0) * 10; // across the ground, in tens
            seeker.sendOverlayMessage(Component.translatable("message.nookworks.nook_dowsing_rod.found",
                    String.format(Locale.ROOT, "%,d", distance), at.getY()));
        }
        ServerLevel here = seeker.level();
        here.playSound(null, seeker.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8F, 0.9F);
        if (rod.isEmpty()) {
            return; // no longer with them: told, but there is no needle to set
        }
        rod.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(Optional.of(GlobalPos.of(searched.dimension(), at)), false));
        // worn as a tool is: broken in a hand, it breaks as a tool does, with its sound and its bits
        if (rod == seeker.getMainHandItem()) {
            rod.hurtAndBreak(1, seeker, InteractionHand.MAIN_HAND);
        } else if (rod == seeker.getOffhandItem()) {
            rod.hurtAndBreak(1, seeker, InteractionHand.OFF_HAND);
        } else {
            rod.hurtAndBreak(1, here, seeker, item -> here.playSound(null, seeker.blockPosition(), SoundEvents.ITEM_BREAK.value(),
                    SoundSource.PLAYERS, 0.8F, 0.8F + here.getRandom().nextFloat() * 0.4F));
        }
    }
}
