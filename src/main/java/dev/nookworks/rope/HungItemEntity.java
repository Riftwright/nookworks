package dev.nookworks.rope;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import dev.nookworks.block.RopeBlock;
import dev.nookworks.decor.Lanterns;
import dev.nookworks.feature.StructureTurns;
import dev.nookworks.registry.ModRope;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.common.ItemAbilities;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Something hung up by a bit of string: from a rope strung between posts, at some point along it, or from the end of
 * a Thin Rope let down from a ceiling. What hangs is in {@link #HANGS} - bottles, fish, kelp, a bunch of flowers or
 * herbs, an ear of corn, berries, a lantern, a banner - and a Filament Bulb. A bulb, glow berries and a lit lantern
 * light up where they hang ({@link #glow()}); sweet berries are drawn as a sprig of the bush, as on a {@link HookBlock}
 * ({@link #SPRIGS}), glow berries as vanilla's cave vine ({@link #GLOW_VINE}), and a lantern as the lantern
 * block itself, hanging ({@link #LANTERNS}). A
 * banner, near a block across and two high, is a hung thing of its own kind ({@link HungBannerEntity}).
 * <p>
 * Out in the sun, off the rain, it dries as its {@link Drying} data says: raw fish into Dried Fish, kelp into dried kelp.
 * An empty hand takes it down (sneaking, it puts a lit lantern out; flint and steel lights it again); a hit knocks it
 * off. It falls when what it hangs from goes.
 */
public class HungItemEntity extends Entity {
    /** What may be hung up besides a Filament Bulb. */
    public static final TagKey<Item> HANGS = TagKey.create(Registries.ITEM, Nookworks.id("hangs_from_rope"));
    /**
     * What hangs whole and solid, as an item frame shows it, from a cord at its top: mobs' heads, as big as one set on
     * a block and facing whoever hung it, and the dragon egg by its tip (the user, 2026-10-11). The dragon's head, a
     * block across, is not among them.
     */
    public static final TagKey<Item> SOLID = TagKey.create(Registries.ITEM, Nookworks.id("hangs_solid"));
    /** A solid thing hung up takes a cube half a block across, as a head set on a block does. */
    public static final double SOLID_SIZE = 0.5;
    /** What a solid thing keeps clear along the rope beyond its own reach. */
    private static final double SOLID_GAP = 0.05;
    /** How far along a rope a solid thing keeps each way, its half and a little. */
    public static final double SOLID_ROOM = SOLID_SIZE / 2 + SOLID_GAP;
    /**
     * What hangs as the block it is, hooked on the rope by its own loop as a lantern hangs under a chain: the lanterns,
     * lit or put out (the user, 2026-10-16: a lantern hung as a block, not as its picture). A block item put in the tag
     * is drawn as its block, in its hanging state where it has one.
     */
    public static final TagKey<Item> LANTERNS = TagKey.create(Registries.ITEM, Nookworks.id("hangs_as_lantern"));
    /**
     * A lantern is drawn full size, its loop's top on the rope's top: vanilla's hanging lantern is six pixels across and,
     * loop and all, fifteen tall, so its foot is 14.5 pixels under the rope.
     */
    public static final double LANTERN_HALF = 3.0 / 16;
    public static final double LANTERN_DROP = 14.5 / 16;
    /**
     * How far along a rope a lantern keeps each way: 0.3, so two side by side hang 0.6 apart, a little under four pixels
     * between them, and one keeps half a block from a bottle, a bulb or a post. At a head's room (its half and 0.05) a
     * row of lanterns all but touched, a wall of lanterns rather than lanterns along a line (seen in the client, 2026-10-16).
     */
    public static final double LANTERN_ROOM = 0.3;
    /**
     * Glow berries hang as vanilla's cave vine, the end piece of the vine, a block tall and as the block is drawn, rather
     * than a sprig of our drawing (the user, 2026-10-16: the sprig's size beside the lanterns and the world's own vines).
     * Hung, it is the vine as glow berries set under a ceiling make it: no berries yet (the user, the same day: "天井に貼り付ける
     * ときと同じ挙動でいいよ"). Its strands are on the block's two crossed planes, a little under a block across either way.
     */
    public static final BlockState GLOW_VINE = Blocks.CAVE_VINES.defaultBlockState().setValue(CaveVines.BERRIES, false);
    /** The vine's strands reach 0.4 each way from its middle, and from just under the rope's top a block down. */
    public static final double VINE_HALF = 0.4;
    public static final double VINE_DROP = 15.5 / 16;
    /** How far along a rope the vine keeps each way: its reach, so two vines hang strand to strand, 0.8 apart. */
    public static final double VINE_ROOM = 0.4;
    /**
     * A hung vine grows down as vanilla's cave vine does (the user, 2026-10-16: "伸びるようにしていいよ。ハサミで留められるし。"):
     * a block at a time, on what stands for a random tick of its tip, at vanilla's chance of 0.1, each new piece bearing
     * berries at vanilla's 0.11 and its age one on, up to vanilla's 25 - or as many blocks as this, the berries hung
     * and four more below them, so a line across a garden hangs a fringe and not a wall.
     */
    public static final int VINE_MAX = 5;
    public static final double VINE_GROW_CHANCE = 0.1;
    public static final float VINE_BERRY_CHANCE = 0.11F;
    /** The vine's length (the low three bits) and which grown pieces bear berries (bit 3 + i for piece i). */
    private static final EntityDataAccessor<Integer> DATA_VINE = SynchedEntityData.defineId(HungItemEntity.class, EntityDataSerializers.INT);
    /** A Filament Bulb switched off where it hangs (the user, 2026-10-16): its coil dark, no light. */
    private static final EntityDataAccessor<Boolean> DATA_OFF = SynchedEntityData.defineId(HungItemEntity.class, EntityDataSerializers.BOOLEAN);
    /** Ticks until a bulb switched on lights the air, a second as a filament warms, as one hung does; 0 when none. */
    private int warming;
    /** A piglin's head reaches further along the rope: ten pixels wide and its ears out to 8.5 pixels each side. */
    public static final double PIGLIN_HALF = 8.5 / 16;
    private static final EntityDataAccessor<ItemStack> DATA_ITEM = SynchedEntityData.defineId(HungItemEntity.class, EntityDataSerializers.ITEM_STACK);
    static final EntityDataAccessor<Float> DATA_YAW = SynchedEntityData.defineId(HungItemEntity.class, EntityDataSerializers.FLOAT);
    /** Whether it is tied on a rope strung between posts (else on a rope's end): the client draws the tie to suit. */
    private static final EntityDataAccessor<Boolean> DATA_ON_SPAN = SynchedEntityData.defineId(HungItemEntity.class, EntityDataSerializers.BOOLEAN);
    /** The posts of the span it hangs on, for the client to know which chain it swings with (its place comes as is). */
    private static final EntityDataAccessor<Optional<BlockPos>> DATA_SPAN_FROM = SynchedEntityData.defineId(HungItemEntity.class,
            EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<Optional<BlockPos>> DATA_SPAN_TO = SynchedEntityData.defineId(HungItemEntity.class,
            EntityDataSerializers.OPTIONAL_BLOCK_POS);
    /** The string from the rope to the top of what hangs. */
    public static final double STRING = 2.5 / 16;
    /** How far along a rope a thing keeps each way: with the next one's, {@link RopeHanging#SPACING}. */
    public static final double ROOM = RopeHanging.SPACING / 2;
    /** Said when something is in the way below the rope. */
    static final String NO_ROOM = "message.nookworks.rope.no_room";
    /** A bulb's light where it hangs. */
    public static final int BULB_LIGHT = 13;
    /**
     * What a hung thing takes up, as it is drawn (HungItemRenderer): a thing on a string is a picture 0.45 of a block
     * across, flat along the rope, below its string; a Filament Bulb its glass and cap, five pixels across and fourteen
     * down from the rope, with no string. The entity's own size is only a square a little smaller than either.
     */
    private static final double DRAWN = 0.45;
    /**
     * A sprig of berries is drawn bigger, near its size on a Hook, its berries as big as the bush's (at 0.45 they were
     * specks: the user, 2026-10-11); it keeps more room along the rope to match.
     */
    public static final double SPRIG_DRAWN = 0.7;
    private static final double SPRIG_ROOM = SPRIG_DRAWN / 2 - 0.05;
    private static final double FLAT = 1.0 / 16;
    private static final double BULB_HALF = 2.5 / 16;
    private static final double BULB_DROP = 14.0 / 16;
    /** Glow berries', as the cave vine's that bears them, and as on a Hook. */
    public static final int GLOW_BERRY_LIGHT = HookBlock.GLOW_LIGHT;
    /**
     * Sweet berries hung up are drawn as a sprig, as they hang on a Hook - a bunch of the bush (glow berries on a Hook too,
     * strands of the cave vine, but on a rope they are the vine itself, {@link #GLOW_VINE}):
     * the client item each is drawn with (assets/nookworks/items), there being no item of that name.
     */
    public static final Map<Item, Identifier> SPRIGS = Map.of(Items.SWEET_BERRIES, Nookworks.id("hung_sweet_berries"));

    /** Hung on a span: the post it was tied off from, the post it runs to, and how far along. */
    private @Nullable BlockPos from;
    private @Nullable BlockPos to;
    private double along;
    /** Or hung from the bottom end of a Thin Rope: that segment. */
    private @Nullable BlockPos ropeEnd;
    private int dried;
    /**
     * Where a bulb set its light block, to take away when it goes: that one only, never a light block a map maker set
     * there, or another bulb's.
     */
    private @Nullable BlockPos litAt;
    /** Saved before bulbs kept where they set their light: the light block at its place, if one of a bulb's, is its. */
    private boolean adoptsLight;
    /** The bolt striking it now, which spares what it knocks down ({@link StruckLoose}); null otherwise. */
    private @Nullable UUID struckBy;
    /** A solid thing on a span turned round to face the other side of the rope, the side it was hung from. */
    private boolean facesBack;
    /**
     * Loaded from a save that kept its places as steps from where it hangs: those steps, for a structure setting it down
     * to turn and mirror ({@link #rotate}). Kept as read, not worked out again from the places, as it may hang on a
     * block's very edge, where a step worked back from a turned place would round to the block beside it. Null for a
     * thing hung here, or saved before there were steps.
     */
    private @Nullable Steps steps;
    /** How a structure setting it down has turned it, for the mirror it tells next ({@link StructureTurns#afterTurn}). */
    private Rotation turned = Rotation.NONE;
    /**
     * A glow-berry vine's age, as vanilla's cave vine tip has one: from 0 to 24 as it is hung, as vanilla sets a vine
     * down, one on with each piece grown; at 25, cut by shears or grown out, it grows no more.
     */
    private int vineAge;
    /** The light blocks its grown pieces' berries set (the berries hung light the block at its place, {@link #litAt}). */
    private final Set<BlockPos> vineLit = new HashSet<>();

    /** From where it hangs to the middle of each block it keeps: the rope's end, the span's two posts, its light. */
    private record Steps(@Nullable Vec3 rope, @Nullable Vec3 from, @Nullable Vec3 to, @Nullable Vec3 lit) {
        Steps change(UnaryOperator<Vec3> change) {
            return new Steps(this.rope == null ? null : change.apply(this.rope), this.from == null ? null : change.apply(this.from),
                    this.to == null ? null : change.apply(this.to), this.lit == null ? null : change.apply(this.lit));
        }
    }

    public HungItemEntity(EntityType<? extends HungItemEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        // no block is set down where it hangs, as none is where a minecart stands (the user, 2026-10-07)
        this.blocksBuilding = true;
        this.vineAge = this.random.nextInt(GrowingPlantHeadBlock.MAX_AGE);
    }

    /** Whether {@code stack} can be hung up. */
    public static boolean hangs(ItemStack stack) {
        return stack.is(HANGS) || stack.is(ModRope.FILAMENT_BULB_ITEM.get());
    }

    /** Whether {@code stack} hangs as a banner, by its bar ({@link HungBannerEntity}), rather than by a string. */
    public static boolean isBanner(ItemStack stack) {
        return stack.getItem() instanceof BannerItem;
    }

    /** Whether {@code stack} hangs whole and solid, a head facing one way ({@link #SOLID}). */
    public static boolean isSolid(ItemStack stack) {
        return stack.is(SOLID);
    }

    /** How far along a rope {@code stack} keeps each way from its middle, hung up. */
    public static double room(ItemStack stack) {
        return isBanner(stack) ? HungBannerEntity.ROOM : isSolid(stack) ? solidHalf(stack) + SOLID_GAP
                : isLantern(stack) ? LANTERN_ROOM : isVine(stack) ? VINE_ROOM : SPRIGS.containsKey(stack.getItem()) ? SPRIG_ROOM : ROOM;
    }

    /** How far along the rope it keeps each way from its middle. */
    public double room() {
        return room(this.item());
    }

    /** How big its picture is drawn, a block across: a sprig of berries bigger than the rest. */
    public static double drawn(ItemStack stack) {
        return SPRIGS.containsKey(stack.getItem()) ? SPRIG_DRAWN : DRAWN;
    }

    public boolean isSolid() {
        return isSolid(this.item());
    }

    /** Whether {@code stack} hangs as a lantern, the block itself ({@link #LANTERNS}). */
    public static boolean isLantern(ItemStack stack) {
        return lanternState(stack) != null;
    }

    public boolean isLantern() {
        return isLantern(this.item());
    }

    /** Whether {@code stack} hangs as a cave vine ({@link #GLOW_VINE}): glow berries. */
    public static boolean isVine(ItemStack stack) {
        return stack.is(Items.GLOW_BERRIES);
    }

    /** The block {@code stack} is drawn as, hung up - a lantern, or the berried vine for glow berries - or null. */
    public static @Nullable BlockState drawnBlock(ItemStack stack) {
        BlockState lantern = lanternState(stack);
        return lantern != null ? lantern : isVine(stack) ? GLOW_VINE : null;
    }

    /**
     * The block {@code stack} hangs as, if it hangs as a lantern: its block as the item would set it down (any block
     * state the stack carries), hanging. Null for anything else.
     */
    public static @Nullable BlockState lanternState(ItemStack stack) {
        if (!stack.is(LANTERNS) || !(stack.getItem() instanceof BlockItem item)) {
            return null;
        }
        BlockState state = stack.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY)
                .apply(item.getBlock().defaultBlockState());
        return state.trySetValue(BlockStateProperties.HANGING, true);
    }

    /** How far a solid thing reaches each way along the rope from its middle: a piglin's head its ears, the rest half a cube. */
    public static double solidHalf(ItemStack stack) {
        return stack.is(Items.PIGLIN_HEAD) ? PIGLIN_HALF : SOLID_SIZE / 2;
    }

    /** A hung thing of the kind {@code stack} hangs as, not yet put anywhere. */
    private static HungItemEntity of(Level level, ItemStack stack) {
        return isBanner(stack) ? new HungBannerEntity(ModRope.HUNG_BANNER.get(), level) : new HungItemEntity(ModRope.HUNG_ITEM.get(), level);
    }

    public static HungItemEntity onSpan(Level level, BlockPos from, BlockPos to, double along, ItemStack stack) {
        return onSpan(level, from, to, along, stack, null);
    }

    /** The same, hung by someone at {@code seenFrom}: a banner shows them its face. */
    public static HungItemEntity onSpan(Level level, BlockPos from, BlockPos to, double along, ItemStack stack, @Nullable Vec3 seenFrom) {
        HungItemEntity hung = of(level, stack);
        hung.from = from.immutable();
        hung.to = to.immutable();
        hung.along = along;
        if (hung instanceof HungBannerEntity banner && seenFrom != null) {
            banner.faceTowards(level, seenFrom);
        } else if (isSolid(stack) && seenFrom != null) {
            // its face looks out along (sin, -cos) of its yaw, the other way to a banner's: turned round to whoever hung it
            double yaw = Math.toRadians(spanYaw(level, from, to));
            Vec3 point = RopeSpans.at(level, from, to, along);
            hung.facesBack = (seenFrom.x - point.x) * Math.sin(yaw) - (seenFrom.z - point.z) * Math.cos(yaw) < 0;
        }
        hung.entityData.set(DATA_YAW, hung.spanYaw(level));
        hung.entityData.set(DATA_ON_SPAN, true);
        hung.entityData.set(DATA_SPAN_FROM, Optional.of(hung.from));
        hung.entityData.set(DATA_SPAN_TO, Optional.of(hung.to));
        hung.setItem(stack.copyWithCount(1));
        hung.place();
        return hung;
    }

    /** Side on to the rope from {@code from} to {@code to}, so a fish shows its side to whoever walks along it. */
    static float spanYaw(Level level, BlockPos from, BlockPos to) {
        Vec3 a = RopeSpans.tie(level, from);
        Vec3 b = RopeSpans.tie(level, to);
        return (float) Math.toDegrees(Math.atan2(b.z - a.z, b.x - a.x));
    }

    /** Which way it faces on its span, as the rope runs now: a head turned round if it faces the other side. */
    protected float spanYaw(Level level) {
        return this.from != null && this.to != null ? spanYaw(level, this.from, this.to) + (this.facesBack ? 180.0F : 0.0F)
                : this.yaw();
    }

    public static HungItemEntity underRope(Level level, BlockPos ropeEnd, ItemStack stack, float yaw) {
        HungItemEntity hung = of(level, stack);
        hung.ropeEnd = ropeEnd.immutable();
        hung.entityData.set(DATA_YAW, yaw);
        hung.setItem(stack.copyWithCount(1));
        hung.place();
        return hung;
    }

    /**
     * Which way {@code stack} faces hung from a rope's end by a player facing {@code playerYaw}: towards them - a
     * banner its face, turned in sixteenths of a turn as a standing banner is set.
     */
    public static float facing(ItemStack stack, float playerYaw) {
        // a head looks back at them: its face is out along (sin, -cos) of its yaw, against the way they look
        return isBanner(stack) ? HungBannerEntity.facing(playerYaw) : playerYaw;
    }

    /**
     * Why it cannot hang where it was put - something in its way under the rope - as the message's key, or null when it
     * can. On a span its box must be clear of blocks; under a rope's end, the block it hangs in must be free.
     */
    public @Nullable String misfit(Level level) {
        if (this.ropeEnd != null) {
            return level.getBlockState(this.ropeEnd.below()).canBeReplaced() ? null : NO_ROOM;
        }
        return level.noBlockCollision(null, this.getBoundingBox()) ? null : NO_ROOM;
    }

    /**
     * What it takes up where it hangs, as it is drawn: what must be clear of blocks for it to hang there, what a block
     * set down must keep clear of, and what is clicked to take it down. Worked out from the point it hangs from, so
     * where it is drawn does not move.
     */
    @Override
    protected AABB makeBoundingBox(Vec3 position) {
        Vec3 hang = position.add(0, this.getBbHeight() + STRING, 0);
        if (this.isBulb()) {
            // from just under the rope, where its flex ends, to the foot of its glass
            return new AABB(hang.x - BULB_HALF, hang.y - BULB_DROP, hang.z - BULB_HALF,
                    hang.x + BULB_HALF, hang.y - 1.0 / 16, hang.z + BULB_HALF);
        }
        if (this.isLantern() || isVine(this.item())) {
            // the lantern from its loop's top on the rope to its foot, or the vine a block down from the rope, as wide as
            // it is either way it is turned
            boolean lantern = this.isLantern();
            double yaw = Math.toRadians(this.yaw());
            double half = (lantern ? LANTERN_HALF : VINE_HALF) * (Math.abs(Math.cos(yaw)) + Math.abs(Math.sin(yaw)));
            double drop = lantern ? LANTERN_DROP : VINE_DROP + this.vineLength() - 1;
            return new AABB(hang.x - half, hang.y - drop, hang.z - half, hang.x + half, hang.y - 1.0 / 16, hang.z + half);
        }
        if (this.isSolid()) {
            // a head or the egg, half a block deep, as wide along the rope as it reaches, turned as the rope runs, from the
            // cord's end down
            double yaw = Math.toRadians(this.yaw());
            double cos = Math.abs(Math.cos(yaw));
            double sin = Math.abs(Math.sin(yaw));
            double along = solidHalf(this.item());
            double across = SOLID_SIZE / 2;
            double x = cos * along + sin * across;
            double z = sin * along + cos * across;
            double top = hang.y - STRING;
            return new AABB(hang.x - x, top - SOLID_SIZE, hang.z - z, hang.x + x, top, hang.z + z);
        }
        // a picture along the rope (or, from a rope's end, turned as it faces), hanging from its string
        double yaw = Math.toRadians(this.yaw());
        double cos = Math.abs(Math.cos(yaw));
        double sin = Math.abs(Math.sin(yaw));
        double drawn = drawn(this.item());
        double half = drawn / 2;
        double x = cos * half + sin * FLAT;
        double z = sin * half + cos * FLAT;
        double top = hang.y - STRING;
        return new AABB(hang.x - x, top - drawn, hang.z - z, hang.x + x, top, hang.z + z);
    }

    /** Its box goes with what it is and which way it faces, on the client too as they come in. */
    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (DATA_ITEM.equals(accessor) || DATA_YAW.equals(accessor) || DATA_VINE.equals(accessor)) {
            this.setBoundingBox(this.makeBoundingBox());
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        entityData.define(DATA_ITEM, ItemStack.EMPTY);
        entityData.define(DATA_YAW, 0.0F);
        entityData.define(DATA_ON_SPAN, false);
        entityData.define(DATA_SPAN_FROM, Optional.empty());
        entityData.define(DATA_SPAN_TO, Optional.empty());
        entityData.define(DATA_VINE, 1);
        entityData.define(DATA_OFF, false);
    }

    /** The posts of the span it hangs on, on either side: the client's only way to know which line it is on. */
    public @Nullable BlockPos spanFromSeen() {
        return this.entityData.get(DATA_SPAN_FROM).orElse(null);
    }

    public @Nullable BlockPos spanToSeen() {
        return this.entityData.get(DATA_SPAN_TO).orElse(null);
    }

    /** Tied on a rope strung between posts, rather than on the end of one let down. */
    public boolean onSpan() {
        return this.entityData.get(DATA_ON_SPAN);
    }

    public ItemStack item() {
        return this.entityData.get(DATA_ITEM);
    }

    private void setItem(ItemStack stack) {
        this.entityData.set(DATA_ITEM, stack);
    }

    /** Which way it faces, in degrees about the vertical. */
    public float yaw() {
        return this.entityData.get(DATA_YAW);
    }

    public boolean isBulb() {
        return this.item().is(ModRope.FILAMENT_BULB_ITEM.get());
    }

    /** A bulb switched off where it hangs. */
    public boolean switchedOff() {
        return this.entityData.get(DATA_OFF);
    }

    /** The light it gives where it hangs: a bulb's, glow berries', a lantern's as its block gives it (none put out), or none. */
    public int glow() {
        if (this.isBulb()) {
            return this.switchedOff() ? 0 : BULB_LIGHT;
        }
        if (this.item().is(Items.GLOW_BERRIES)) {
            return this.vineBerries(0) ? GLOW_BERRY_LIGHT : 0; // a vine's top piece, lit while it bears, as vanilla's
        }
        BlockState lantern = lanternState(this.item());
        return lantern == null ? 0 : lantern.getLightEmission();
    }

    /** Where its string is tied: the point on the rope, or the bottom of the rope's end. */
    public Vec3 hangPoint() {
        if (this.ropeEnd != null) {
            return new Vec3(this.ropeEnd.getX() + 0.5, this.ropeEnd.getY(), this.ropeEnd.getZ() + 0.5);
        }
        return this.from != null && this.to != null ? RopeSpans.at(this.level(), this.from, this.to, this.along)
                : this.position().add(0, this.getBbHeight() + STRING, 0);
    }

    /** Where it hangs below its point: its top on the end of the string. */
    protected Vec3 restingAt() {
        Vec3 point = this.hangPoint();
        return new Vec3(point.x, point.y - STRING - this.getBbHeight(), point.z);
    }

    /** Hangs it where it rests below its point. */
    protected void place() {
        this.setPos(this.restingAt());
    }

    /** The bottom end of the Thin Rope it hangs from, or null on a span. */
    protected @Nullable BlockPos ropeEnd() {
        return this.ropeEnd;
    }

    public @Nullable BlockPos spanFrom() {
        return this.from;
    }

    public @Nullable BlockPos spanTo() {
        return this.to;
    }

    public double along() {
        return this.along;
    }

    @Override
    public void tick() {
        // Not Entity's own round of water, lava, fire, portals and powder snow: a thing tied to a rope stays where it is
        // put, as an item frame does, and with hundreds of them strung up those rounds would add up for nothing.
        this.checkBelowWorld();
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        if (this.warming > 0 && --this.warming == 0 && this.glow() > 0) {
            this.light(level); // switched on a second ago: the filament has warmed
        }
        if (this.tickCount % 20 == 0) {
            if (!this.stillHangs(level)) {
                this.fall(level);
                return;
            }
            this.followSpan(level);
            this.dry(level, 20, this.inTheSun(level));
            if (this.glow() > 0 && this.warming == 0) {
                this.light(level);
            }
            if (isVine(this.item())) {
                // the twenty ticks since it last looked stand for as many chances of a random tick of its tip, at the
                // world's rate (random_tick_speed blocks of a 16-cube section a tick), as vanilla's vine is ticked
                int speed = level.getGameRules().get(GameRules.RANDOM_TICK_SPEED);
                if (this.random.nextDouble() < 20 * speed / 4096.0 * VINE_GROW_CHANCE) {
                    this.growVine(level, this.random);
                }
                this.lightVine(level);
            }
        } else if (this.tickCount == 1 && this.glow() > 0 && !this.isBulb()) {
            // glow berries are alight as they are hung, as on the vine, and a lantern's flame burns already; a bulb comes
            // on a second later, as a filament warms up (the user, 2026-10-11: the bulb's wait suits it, the berries' did not)
            this.light(level);
        }
    }

    /** Whether what it hangs from is still there. A rope let down further takes it down to its new end. */
    private boolean stillHangs(ServerLevel level) {
        if (this.ropeEnd != null) {
            if (!(level.getBlockState(this.ropeEnd).getBlock() instanceof RopeBlock rope)) {
                return !level.isLoaded(this.ropeEnd);
            }
            BlockPos bottom = rope.bottomOf(level, this.ropeEnd);
            if (!bottom.equals(this.ropeEnd)) {
                HungItemEntity moved = underRope(level, bottom, this.item(), this.yaw());
                if (moved.misfit(level) != null) {
                    return false; // let down onto the floor: no room under its new end, it comes off (as a broken rope's do)
                }
                moved.dried = this.dried; // the same fish, as dry as it was
                moved.takeVine(level, this);
                level.addFreshEntity(moved);
                this.discard();
            }
            return true;
        }
        if (this.from == null || this.to == null || !level.isLoaded(this.from)) {
            return this.from != null;
        }
        if (!level.areEntitiesLoaded(ChunkPos.pack(this.from))) {
            return true; // the post's chunk is in, but its knot is still on its way: entities load apart from blocks, later
        }
        RopeKnotEntity knot = RopeKnotEntity.at(level, this.from);
        return knot != null && knot.ties(this.to);
    }

    /**
     * A post turned (a Wrench on a rod, a hook, a bracket) moves where its rope is tied, and the rope with it: what hangs
     * on it goes along, side on to the rope as it runs now, rather than hanging on where the rope was (r7 review).
     */
    private void followSpan(ServerLevel level) {
        if (this.from == null || this.to == null || !level.isLoaded(this.from) || !level.isLoaded(this.to)) {
            return; // never load a chunk to look at a post
        }
        if (this.position().distanceToSqr(this.restingAt()) <= 1.0E-6) {
            return;
        }
        BlockPos was = this.blockPosition();
        this.entityData.set(DATA_YAW, this.spanYaw(level));
        this.place();
        this.needsSync = true; // sent to whoever sees it: nothing else ever moves a thing hung up
        if (this.litAt != null && !this.blockPosition().equals(was)) {
            this.putOutLight(level); // and lit again where it hangs now, this same tick
        }
    }

    /** Out in daylight under the open sky, and not rained on. */
    private boolean inTheSun(ServerLevel level) {
        BlockPos at = this.blockPosition();
        return level.isBrightOutside() && level.canSeeSky(at) && !level.isRainingAt(at);
    }

    /** {@code ticks} more of hanging, in the sun or not: what dries, dries. Public for tests. */
    public void dry(ServerLevel level, int ticks, boolean inTheSun) {
        Optional<Drying> drying = Optional.ofNullable(this.item().typeHolder().getData(Drying.MAP));
        if (drying.isEmpty() || !inTheSun) {
            return;
        }
        this.dried += ticks;
        if (this.dried >= drying.get().ticks()) {
            this.dried = 0;
            this.setItem(new ItemStack(drying.get().result()));
        }
    }

    /**
     * A bulb, or glow berries, light the air where they hang, with an unseen light block of ours ({@link HungLightBlock})
     * - and light it again once it is gone: a block set there and taken away, water poured over it and drained.
     */
    private void light(ServerLevel level) {
        if (this.litAt != null) {
            BlockState lit = level.getBlockState(this.litAt);
            if (lit.is(Blocks.LIGHT)) { // set before the light was ours: swapped for ours, at its brightness
                level.setBlock(this.litAt, ModRope.HUNG_LIGHT.get().at(lit.getValue(LightBlock.LEVEL)), Block.UPDATE_ALL);
            } else if (!lit.is(ModRope.HUNG_LIGHT.get())) {
                this.litAt = null; // put out by something else: not ours to take away any more, nor to keep
            }
        }
        BlockPos at = this.blockPosition();
        BlockState there = level.getBlockState(at);
        if (this.adoptsLight) {
            this.adoptsLight = false;
            if ((there.is(Blocks.LIGHT) || there.is(ModRope.HUNG_LIGHT.get())) && there.getValue(LightBlock.LEVEL) == this.glow()) {
                this.litAt = at;
                if (there.is(Blocks.LIGHT)) {
                    level.setBlock(at, ModRope.HUNG_LIGHT.get().at(this.glow()), Block.UPDATE_ALL);
                }
            }
        }
        if (this.litAt == null && there.isAir()) {
            level.setBlock(at, ModRope.HUNG_LIGHT.get().at(this.glow()), Block.UPDATE_ALL);
            this.litAt = at;
        }
        // two lit things in one block, the light another put there: as bright as the brightest of them, whichever came
        // first (a soul lantern's 10 kept a lantern hung beside it at 10; the r18 review); the one whose light it is
        // keeps it at the brightest, up or down, as the others come and go
        BlockPos shared = this.litAt != null ? this.litAt : there.is(ModRope.HUNG_LIGHT.get()) ? at : null;
        if (shared != null) {
            BlockState light = level.getBlockState(shared);
            int brightest = this.litAt != null ? brightestAt(level, shared) : Math.max(this.glow(), light.getValue(LightBlock.LEVEL));
            if (light.is(ModRope.HUNG_LIGHT.get()) && brightest > 0 && light.getValue(LightBlock.LEVEL) != brightest) {
                level.setBlock(shared, light.setValue(LightBlock.LEVEL, brightest), Block.UPDATE_ALL);
            }
        }
    }

    /** The brightest of the lit things hanging in the block at {@code pos}: what its light block shines at. */
    private static int brightestAt(Level level, BlockPos pos) {
        int brightest = 0;
        for (HungItemEntity hung : level.getEntitiesOfClass(HungItemEntity.class, hangingOver(new AABB(pos)), h -> !h.isRemoved())) {
            if (hung.blockPosition().equals(pos)) {
                brightest = Math.max(brightest, hung.glow());
            }
            if (hung.vineLights(pos)) {
                brightest = Math.max(brightest, GLOW_BERRY_LIGHT);
            }
        }
        return brightest;
    }

    /**
     * Where to look for what hangs in or over the block at {@code pos}: up to a vine's length above it. A vine stands at
     * its top and reaches down; the game files an entity by where it stands and looks for one only a little below the
     * box it is asked about, never above - a grown piece across a 16-block section's edge from the vine's top was not
     * found, and its light went out every few seconds (the review of the 10-16 commits).
     */
    private static AABB hangingOver(AABB box) {
        return box.expandTowards(0, VINE_MAX + 1, 0);
    }

    /** Whether a thing hanging in the block at {@code pos} gives light there: what keeps a {@link HungLightBlock} lit. */
    public static boolean lightsAt(Level level, BlockPos pos) {
        return !level.getEntitiesOfClass(HungItemEntity.class, hangingOver(new AABB(pos)), hung -> !hung.isRemoved() && (hung.glow() > 0
                && hung.blockPosition().equals(pos) || hung.vineLights(pos))).isEmpty();
    }

    /**
     * Takes its light away - or, with another light hanging in the same block, leaves it to that one, at that one's
     * brightness.
     */
    private void putOutLight(Level level) {
        BlockPos at = this.litAt;
        this.litAt = null;
        if (at == null || !level.getBlockState(at).is(ModRope.HUNG_LIGHT.get()) && !level.getBlockState(at).is(Blocks.LIGHT)) {
            return;
        }
        level.getEntitiesOfClass(HungItemEntity.class, new AABB(at),
                        o -> o != this && !o.isRemoved() && o.glow() > 0 && o.litAt == null && o.blockPosition().equals(at))
                .stream().findFirst()
                .ifPresentOrElse(heir -> {
                    heir.litAt = at;
                    BlockState light = level.getBlockState(at);
                    if (light.getValue(LightBlock.LEVEL) != heir.glow()) {
                        level.setBlock(at, light.setValue(LightBlock.LEVEL, heir.glow()), Block.UPDATE_ALL);
                    }
                }, () -> level.removeBlock(at, false));
    }

    /** Where its light block is, or null. Public for tests. */
    public @Nullable BlockPos litAt() {
        return this.litAt;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (reason.shouldDestroy() && this.glow() > 0 && !this.level().isClientSide()) {
            this.putOutLight(this.level());
        }
        if (reason.shouldDestroy() && !this.level().isClientSide()) {
            for (BlockPos at : this.vineLit) {
                this.unlightVine(this.level(), at);
            }
            this.vineLit.clear();
        }
        super.remove(reason);
    }

    /** Down it comes, and drops - or, with entity drops off, is gone, as an item frame's item is. */
    private void fall(ServerLevel level) {
        if (level.getGameRules().get(GameRules.ENTITY_DROPS)) {
            StruckLoose.fellFrom(this.spawnAtLocation(level, this.drops()), this.struckBy);
        }
        this.playSound(SoundEvents.ITEM_FRAME_REMOVE_ITEM, 0.6F, 1.2F);
        this.discard();
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        InteractionResult lantern = this.putOutOrLight(player, hand, player.getItemInHand(hand));
        if (lantern != null) {
            return lantern;
        }
        InteractionResult bulb = this.switchBulb(player, hand, player.getItemInHand(hand));
        if (bulb != null) {
            return bulb;
        }
        InteractionResult vine = this.tendVine(player, hand, player.getItemInHand(hand), location);
        if (vine != null) {
            return vine;
        }
        if (!player.getItemInHand(hand).isEmpty() || hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (!player.mayBuild() || !this.level().mayInteract(player, this.blockPosition())) {
            return InteractionResult.FAIL; // taking it down is breaking it, which a hit asks the same of (skipAttackInteraction)
        }
        if (this.level() instanceof ServerLevel level) {
            ItemStack stack = this.drops();
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false, Prediction.PREDICTED);
            }
            this.playSound(SoundEvents.ITEM_FRAME_REMOVE_ITEM, 0.6F, 1.3F);
            this.discard();
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * A hung lantern put out or lit where it hangs, as the block is ({@link Lanterns}; the user, 2026-10-16): sneak and
     * use it with an empty hand and a lit one goes out into its unlit one, weathering and wax kept; flint and steel (worn
     * a use) or a fire charge (spent) lights an unlit one again. Its light goes or comes with it. Only by a player who
     * may take it down, as the block is only by one who may build. Null when it is neither.
     */
    private @Nullable InteractionResult putOutOrLight(Player player, InteractionHand hand, ItemStack held) {
        if (player.isSpectator() || !(this.item().getItem() instanceof BlockItem item) || !this.isLantern()) {
            return null;
        }
        Block unlit = Lanterns.unlit(item.getBlock());
        Block lit = Lanterns.lit(item.getBlock());
        boolean putOut = unlit != null && hand == InteractionHand.MAIN_HAND && held.isEmpty() && player.isSecondaryUseActive();
        boolean light = lit != null && (held.is(Items.FLINT_AND_STEEL) || held.is(Items.FIRE_CHARGE));
        if (!putOut && !light) {
            return null;
        }
        if (!player.mayBuild() || !this.level().mayInteract(player, this.blockPosition())) {
            return InteractionResult.FAIL;
        }
        if (this.level() instanceof ServerLevel level) {
            if (putOut) {
                this.lanternTo(level, unlit, false, SoundEvents.CANDLE_EXTINGUISH);
            } else if (held.is(Items.FLINT_AND_STEEL)) {
                this.lanternTo(level, lit, true, SoundEvents.FLINTANDSTEEL_USE);
                held.hurtAndBreak(1, player, hand);
            } else {
                this.lanternTo(level, lit, true, SoundEvents.FIRECHARGE_USE);
                held.consume(1, player);
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** The hung lantern becomes {@code to}, lit or put out, with {@code sound}; its light comes at once or goes. */
    private void lanternTo(ServerLevel level, Block to, boolean lit, SoundEvent sound) {
        this.setItem(this.item().transmuteCopy(to, 1));
        Vec3 at = this.getBoundingBox().getCenter();
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
        if (lit) {
            this.light(level); // alight at once, as a lantern hung lit is
        } else {
            level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 4, 0.1, 0.1, 0.1, 0.01);
            this.putOutLight(level);
        }
    }

    /** The hung bulb switched on or off, with the copper bulb's click: off, its light goes; on, it comes a second later. */
    private void bulbTo(ServerLevel level, boolean on) {
        this.entityData.set(DATA_OFF, !on);
        Vec3 at = this.getBoundingBox().getCenter();
        level.playSound(null, at.x, at.y, at.z, FilamentBulbBlock.click(on), SoundSource.BLOCKS, 1.0F, 1.0F);
        if (on) {
            this.warming = 20;
        } else {
            this.warming = 0;
            this.putOutLight(level);
        }
    }

    /**
     * Switched by a redstone signal at a post of its span ({@link RopeSignals}): a bulb on or off, a lantern lit or put
     * out (an unlit lantern of ours lit too), as by hand. Whether it changed; anything else hung never does.
     */
    public boolean switchLight(ServerLevel level, boolean on) {
        if (this.isBulb()) {
            if (this.switchedOff() != on) {
                return false; // already as asked
            }
            this.bulbTo(level, on);
            return true;
        }
        if (!this.isLantern() || !(this.item().getItem() instanceof BlockItem item)) {
            return false;
        }
        Block to = on ? Lanterns.lit(item.getBlock()) : Lanterns.unlit(item.getBlock());
        if (to == null) {
            return false;
        }
        this.lanternTo(level, to, on, on ? SoundEvents.FLINTANDSTEEL_USE : SoundEvents.CANDLE_EXTINGUISH);
        return true;
    }

    /** Whether it is a light a post's signal switches: a Filament Bulb or a lantern. */
    public boolean switchable() {
        return this.isBulb() || this.isLantern();
    }

    /**
     * A hung bulb switched off or on where it hangs, as a lantern is put out (the user, 2026-10-16: "フィラメント電球も右クリックで
     * オンオフできたらいいんじゃない？"): sneak and use it with an empty hand, with the copper bulb's click. Off, its light goes at
     * once; on, it comes a second later, as a filament warms. An empty hand not sneaking takes it down as before. Only
     * by a player who may take it down. Null when it is not that.
     */
    private @Nullable InteractionResult switchBulb(Player player, InteractionHand hand, ItemStack held) {
        if (!this.isBulb() || player.isSpectator() || hand != InteractionHand.MAIN_HAND || !held.isEmpty() || !player.isSecondaryUseActive()) {
            return null;
        }
        if (!player.mayBuild() || !this.level().mayInteract(player, this.blockPosition())) {
            return InteractionResult.FAIL;
        }
        if (this.level() instanceof ServerLevel level) {
            this.bulbTo(level, this.switchedOff());
        }
        return InteractionResult.SUCCESS;
    }

    /** How many blocks long it hangs: 1 for anything but a glow-berry vine grown on. */
    public int vineLength() {
        return Math.max(1, this.entityData.get(DATA_VINE) & 7);
    }

    /** Whether piece {@code piece} of the vine bears berries. */
    public boolean vineBerries(int piece) {
        return (this.entityData.get(DATA_VINE) >> 3 & 1 << piece) != 0;
    }

    /** Its age as vanilla's vine tip has one; 25 grows no more. Public for tests. */
    public int vineAge() {
        return this.vineAge;
    }

    public void setVineAge(int age) {
        this.vineAge = Mth.clamp(age, 0, GrowingPlantHeadBlock.MAX_AGE);
    }

    private void setVine(int length, int berries) {
        int pieces = Mth.clamp(length, 1, VINE_MAX);
        this.entityData.set(DATA_VINE, pieces | (berries & (1 << pieces) - 1) << 3);
    }

    private int berriesMask() {
        return this.entityData.get(DATA_VINE) >> 3;
    }

    /** The top of the vine, where its strands meet the rope. */
    private double vineTop() {
        return this.hangPoint().y + 0.5 / 16;
    }

    /** What piece {@code piece} of the vine takes up, a block tall, as wide as the vine is turned. */
    private AABB vinePiece(int piece) {
        Vec3 hang = this.hangPoint();
        double yaw = Math.toRadians(this.yaw());
        double half = VINE_HALF * (Math.abs(Math.cos(yaw)) + Math.abs(Math.sin(yaw)));
        double top = this.vineTop() - piece;
        return new AABB(hang.x - half, top - 1 + 1.0E-4, hang.z - half, hang.x + half, top - 1.0E-4, hang.z + half);
    }

    /** The block a grown piece's berries light: the one its middle is in. Public for tests. */
    public BlockPos vineLightAt(int piece) {
        Vec3 hang = this.hangPoint();
        return BlockPos.containing(hang.x, this.vineTop() - piece - 0.5, hang.z);
    }

    /** Whether a grown piece of this vine bearing berries lights {@code pos}. */
    private boolean vineLights(BlockPos pos) {
        if (!isVine(this.item())) {
            return false;
        }
        for (int piece = 1; piece < this.vineLength(); piece++) {
            if (this.vineBerries(piece) && this.vineLightAt(piece).equals(pos)) {
                return true;
            }
        }
        return false;
    }

    /** Whether piece {@code piece} has room: nothing but air (or a hung thing's light) there, and no other hung thing. */
    private boolean roomFor(Level level, int piece) {
        return this.roomFor(level, piece, null);
    }

    /** As {@link #roomFor(Level, int)}, {@code leaving} not counted: the one this takes the place of, still in the level. */
    private boolean roomFor(Level level, int piece, @Nullable HungItemEntity leaving) {
        AABB box = this.vinePiece(piece);
        if (level.isOutsideBuildHeight(Mth.floor(box.minY))) {
            return false;
        }
        for (BlockPos at : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            BlockState there = level.getBlockState(at);
            if (!there.isAir() && !there.is(ModRope.HUNG_LIGHT.get())) {
                return false;
            }
        }
        return level.getEntitiesOfClass(HungItemEntity.class, hangingOver(box),
                o -> o != this && o != leaving && !o.isRemoved() && o.getBoundingBox().intersects(box)).isEmpty();
    }

    /**
     * A random tick's growth, as vanilla's cave vine tip grows: a block further down where there is room, while it is
     * younger than 25 and shorter than {@link #VINE_MAX}, bearing berries at vanilla's chance. Public for tests.
     */
    public boolean growVine(ServerLevel level, RandomSource random) {
        int length = this.vineLength();
        if (!isVine(this.item()) || this.vineAge >= GrowingPlantHeadBlock.MAX_AGE || length >= VINE_MAX || !this.roomFor(level, length)) {
            return false;
        }
        boolean berries = random.nextFloat() < VINE_BERRY_CHANCE;
        this.vineAge++;
        this.setVine(length + 1, this.berriesMask() | (berries ? 1 << length : 0));
        this.needsSync = true;
        this.lightVine(level);
        level.gameEvent(GameEvent.BLOCK_PLACE, this.vineLightAt(length), GameEvent.Context.of(this));
        return true;
    }

    /**
     * Lights the blocks of its grown pieces that bear berries, at the vine's 14, and takes the light from those that no
     * longer do - its own, left where another lit thing still lights them. Light blocks of ours already there (its own,
     * from before a save or a chunk's unloading) are taken up as its own.
     */
    private void lightVine(ServerLevel level) {
        Set<BlockPos> wanted = new HashSet<>();
        for (int piece = 1; piece < this.vineLength(); piece++) {
            if (this.vineBerries(piece)) {
                wanted.add(this.vineLightAt(piece).immutable());
            }
        }
        for (BlockPos at : Set.copyOf(this.vineLit)) {
            if (!wanted.contains(at)) {
                this.vineLit.remove(at);
                this.unlightVine(level, at);
            }
        }
        for (BlockPos at : wanted) {
            BlockState there = level.getBlockState(at);
            if (there.is(ModRope.HUNG_LIGHT.get())) {
                // as bright as the brightest lighting it, down again when a brighter one beside the berries goes
                int brightest = Math.max(GLOW_BERRY_LIGHT, brightestAt(level, at));
                if (there.getValue(LightBlock.LEVEL) != brightest) {
                    level.setBlock(at, there.setValue(LightBlock.LEVEL, brightest), Block.UPDATE_ALL);
                }
                this.vineLit.add(at);
            } else if (there.isAir()) {
                level.setBlock(at, ModRope.HUNG_LIGHT.get().at(GLOW_BERRY_LIGHT), Block.UPDATE_ALL);
                this.vineLit.add(at);
            }
        }
    }

    /** Takes a grown piece's light away, unless another lit thing hangs there. */
    private void unlightVine(Level level, BlockPos at) {
        if (!level.getBlockState(at).is(ModRope.HUNG_LIGHT.get()) || at.equals(this.litAt)) {
            return;
        }
        boolean other = !level.getEntitiesOfClass(HungItemEntity.class, hangingOver(new AABB(at)), o -> o != this && !o.isRemoved()
                && (o.glow() > 0 && o.blockPosition().equals(at) || o.vineLights(at))).isEmpty();
        if (!other) {
            level.removeBlock(at, false);
        }
    }

    /** Gives piece {@code piece} berries, or takes them, as bone meal and picking do (no item spent). Public for tests. */
    public void setVineBerries(int piece, boolean berries) {
        this.setVine(this.vineLength(), berries ? this.berriesMask() | 1 << piece : this.berriesMask() & ~(1 << piece));
    }

    /** Lights the grown pieces now (as they are hung, or loaded). Public for tests. */
    public void lightVineNow(ServerLevel level) {
        if (isVine(this.item())) {
            this.lightVine(level);
        }
    }

    /**
     * Moved down with its rope's end: the vine as it was, as far as there is room below - a piece with no room left
     * behind, its berries dropped.
     */
    private void takeVine(ServerLevel level, HungItemEntity from) {
        if (!isVine(from.item())) {
            return;
        }
        this.vineAge = from.vineAge;
        this.setVine(from.vineLength(), from.berriesMask());
        int keep = 1;
        // the vine it was is still hanging there until this one is in: its pieces are no hindrance (a grown vine was cut
        // back to one piece every time its rope was let down a block - the review of the 10-16 commits)
        while (keep < this.vineLength() && this.roomFor(level, keep, from)) {
            keep++;
        }
        for (int piece = keep; piece < this.vineLength(); piece++) {
            if (this.vineBerries(piece)) {
                Block.popResource(level, this.vineLightAt(piece), new ItemStack(Items.GLOW_BERRIES));
            }
        }
        this.setVine(keep, this.berriesMask());
    }

    /**
     * What it gives back taken down or knocked off: what was hung, as every hung thing gives it back, and a vine's berries
     * on top, one for each piece bearing - the harvest it grew, as vanilla's vine gives its berries.
     */
    private ItemStack drops() {
        int berries = 0;
        if (isVine(this.item())) {
            for (int piece = 0; piece < this.vineLength(); piece++) {
                berries += this.vineBerries(piece) ? 1 : 0;
            }
        }
        return this.item().copyWithCount(1 + berries);
    }

    /**
     * A glow-berry vine tended as vanilla's is, piece by piece where it is clicked, the top one as any: an empty hand
     * picks a piece's berries (vanilla's loot table and sound); bone meal gives a bare piece berries; shears on the tip
     * stop its growing for good (vanilla's age 25), with vanilla's sound, worn a use. Only by a player who may take it
     * down. An empty hand on a bare piece takes the vine down. Null when it is none of these.
     */
    private @Nullable InteractionResult tendVine(Player player, InteractionHand hand, ItemStack held, Vec3 location) {
        if (!isVine(this.item()) || player.isSpectator()) {
            return null;
        }
        int length = this.vineLength();
        int piece = Mth.clamp(Mth.floor(this.vineTop() - (this.getY() + location.y)), 0, length - 1);
        boolean pick = held.isEmpty() && hand == InteractionHand.MAIN_HAND && this.vineBerries(piece);
        boolean feed = held.is(Items.BONE_MEAL) && !this.vineBerries(piece);
        boolean cut = held.canPerformAction(ItemAbilities.SHEARS_TRIM) && piece == length - 1
                && this.vineAge < GrowingPlantHeadBlock.MAX_AGE;
        if (!pick && !feed && !cut) {
            return null;
        }
        if (!player.mayBuild() || !this.level().mayInteract(player, this.blockPosition())) {
            return InteractionResult.FAIL;
        }
        if (this.level() instanceof ServerLevel level) {
            BlockPos at = this.vineLightAt(piece);
            if (pick) {
                BlockState state = (piece == length - 1 ? Blocks.CAVE_VINES : Blocks.CAVE_VINES_PLANT).defaultBlockState()
                        .setValue(CaveVines.BERRIES, true);
                Block.dropFromBlockInteractLootTable(level, BuiltInLootTables.HARVEST_CAVE_VINE, at, state, null, null, player,
                        (server, stack) -> Block.popResource(server, at, stack));
                level.playSound(null, at, SoundEvents.CAVE_VINES_PICK_BERRIES, SoundSource.BLOCKS, 1.0F,
                        Mth.randomBetween(level.getRandom(), 0.8F, 1.2F));
                this.setVine(length, this.berriesMask() & ~(1 << piece));
                level.gameEvent(GameEvent.BLOCK_CHANGE, at, GameEvent.Context.of(player));
            } else if (feed) {
                this.setVine(length, this.berriesMask() | 1 << piece);
                level.levelEvent(LevelEvent.PARTICLES_AND_SOUND_PLANT_GROWTH, at, 15);
                held.consume(1, player);
            } else {
                this.vineAge = GrowingPlantHeadBlock.MAX_AGE;
                level.playSound(null, at, SoundEvents.GROWING_PLANT_CROP, SoundSource.BLOCKS, 1.0F, 1.0F);
                held.hurtAndBreak(1, player, hand);
                level.gameEvent(GameEvent.BLOCK_CHANGE, at, GameEvent.Context.of(player));
            }
            this.needsSync = true;
            if (this.glow() > 0) {
                this.light(level); // the top piece lights its place as it bears, and goes dark picked
            } else if (this.litAt != null) {
                this.putOutLight(level);
            }
            this.lightVine(level);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    /**
     * A hit takes it down only where a player may break things: not in adventure mode, as taking it down by hand is
     * not, and not in spawn protection.
     */
    @Override
    public boolean skipAttackInteraction(Entity source) {
        if (source instanceof Player player) {
            return !player.mayBuild() || !this.level().mayInteract(player, this.blockPosition())
                    || this.hurtOrSimulate(this.damageSources().playerAttack(player), 0.0F);
        }
        return false;
    }

    /**
     * Knocked off, it falls, as an item frame's item does - and is spared what spares an item frame: a mob's doing with
     * mob griefing off (a skeleton's arrow, a creeper), and a player who may not break things here.
     */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (this.isInvulnerableToBase(source) || this.isRemoved()) {
            return false;
        }
        if (!level.getGameRules().get(GameRules.MOB_GRIEFING)
                && source.getEntity() instanceof Mob) {
            return false;
        }
        if (source.getEntity() instanceof Player player && !player.mayBuild()) {
            return false;
        }
        if (source.getEntity() instanceof Player player && player.hasInfiniteMaterials()) {
            this.discard();
        } else {
            this.fall(level);
        }
        return true;
    }

    /** Struck by lightning, it is knocked down, as by a hit; what falls the bolt spares. */
    @Override
    public void thunderHit(ServerLevel level, LightningBolt bolt) {
        this.struckBy = bolt.getUUID();
        try {
            super.thunderHit(level, bolt);
        } finally {
            this.struckBy = null;
        }
    }

    /** An explosion that leaves item frames be leaves it be too. */
    @Override
    public boolean ignoreExplosion(Explosion explosion) {
        return !explosion.shouldAffectBlocklikeEntities() || super.ignoreExplosion(explosion);
    }

    /** Moved - by a piston: nothing else moves it - it is off its string, and falls, rather than hanging on in the air. */
    @Override
    public void move(MoverType type, Vec3 delta) {
        if (this.level() instanceof ServerLevel level && !this.isRemoved() && delta.lengthSqr() > 0.0) {
            this.fall(level);
        }
    }

    @Override
    public ItemStack getPickResult() {
        return this.item().copy();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 64 * 64;
    }

    /**
     * The places it keeps - the rope's end, the span's posts, its light - each written twice: as it is, as before (what an
     * older Nookworks reads), and as a step from where it hangs to that block's middle ("rope_offset", "from_offset",
     * "to_offset", "lit_offset"), which is what a structure copying it keeps right: a template sets the copy where the
     * copy goes, and leaves every other place in it as it was - a thing on a copied rope hung from the first build's
     * (r15 fix check 4, S3).
     */
    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.store("item", ItemStack.CODEC, this.item());
        output.putFloat("yaw", this.yaw());
        output.putInt("dried", this.dried);
        if (isVine(this.item())) {
            output.putInt("vine_length", this.vineLength());
            output.putInt("vine_berries", this.entityData.get(DATA_VINE) >> 3);
            output.putInt("vine_age", this.vineAge);
        }
        output.putBoolean("light_kept", true);
        if (this.switchedOff()) {
            output.putBoolean("off", true);
        }
        if (this.facesBack) {
            output.putBoolean("faces_back", true);
        }
        Vec3 here = this.position();
        if (this.litAt != null) {
            output.store("lit_at", BlockPos.CODEC, this.litAt);
            output.store("lit_offset", Vec3.CODEC, Vec3.atCenterOf(this.litAt).subtract(here));
        }
        if (this.ropeEnd != null) {
            output.store("rope", BlockPos.CODEC, this.ropeEnd);
            output.store("rope_offset", Vec3.CODEC, Vec3.atCenterOf(this.ropeEnd).subtract(here));
        } else if (this.from != null && this.to != null) {
            output.store("from", BlockPos.CODEC, this.from);
            output.store("to", BlockPos.CODEC, this.to);
            output.store("from_offset", Vec3.CODEC, Vec3.atCenterOf(this.from).subtract(here));
            output.store("to_offset", Vec3.CODEC, Vec3.atCenterOf(this.to).subtract(here));
            output.putDouble("along", this.along);
        }
    }

    /** Each place read from its step from where it hangs where one was written, else as it was written (an older save). */
    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.setItem(input.read("item", ItemStack.CODEC).orElse(ItemStack.EMPTY));
        this.entityData.set(DATA_YAW, input.getFloatOr("yaw", 0.0F));
        this.dried = input.getIntOr("dried", 0);
        int length = input.getIntOr("vine_length", 0);
        // hung before vines grew, glow berries were alight where they hung: they keep their berries, on the top piece
        this.setVine(length > 0 ? length : 1, length > 0 ? input.getIntOr("vine_berries", 0) : 1);
        int age = input.getIntOr("vine_age", -1); // hung before vines grew: an age as one hung now
        this.vineAge = age >= 0 ? Math.min(age, GrowingPlantHeadBlock.MAX_AGE) : this.random.nextInt(GrowingPlantHeadBlock.MAX_AGE);
        this.facesBack = input.getBooleanOr("faces_back", false);
        this.entityData.set(DATA_OFF, input.getBooleanOr("off", false));
        this.adoptsLight = !input.getBooleanOr("light_kept", false);
        Steps steps = new Steps(input.read("rope_offset", Vec3.CODEC).orElse(null), input.read("from_offset", Vec3.CODEC).orElse(null),
                input.read("to_offset", Vec3.CODEC).orElse(null), input.read("lit_offset", Vec3.CODEC).orElse(null));
        this.steps = steps.rope() != null || steps.from() != null || steps.to() != null || steps.lit() != null ? steps : null;
        this.litAt = this.place(input, "lit_at", steps.lit());
        this.ropeEnd = this.place(input, "rope", steps.rope());
        this.from = this.place(input, "from", steps.from());
        this.to = this.place(input, "to", steps.to());
        this.along = input.getDoubleOr("along", 0.5);
        this.entityData.set(DATA_ON_SPAN, this.ropeEnd == null && this.from != null);
        this.entityData.set(DATA_SPAN_FROM, Optional.ofNullable(this.from));
        this.entityData.set(DATA_SPAN_TO, Optional.ofNullable(this.to));
    }

    /** The block {@code step} from where it hangs, or with no step, the place written under {@code key}. */
    private @Nullable BlockPos place(ValueInput input, String key, @Nullable Vec3 step) {
        return step != null ? BlockPos.containing(this.position().add(step)) : input.read(key, BlockPos.CODEC).orElse(null);
    }

    /**
     * A structure setting it down turns it: its places turn with it about where it hangs, as the blocks they are were
     * turned, and it faces as turned (called on the world-making thread too, before it is in any level: nothing is read
     * but itself).
     */
    @Override
    public float rotate(Rotation rotation) {
        this.turned = rotation;
        if (rotation != Rotation.NONE) {
            this.turn(step -> StructureTurns.rotate(step, rotation), yaw -> StructureTurns.rotate(yaw, rotation));
        }
        return super.rotate(rotation);
    }

    /** And mirrors it, after turning it: on a span it faces the other side of the rope, which runs mirrored. */
    @Override
    public float mirror(Mirror mirror) {
        Mirror after = StructureTurns.afterTurn(mirror, this.turned);
        if (after != Mirror.NONE) {
            this.turn(step -> StructureTurns.mirror(step, after), yaw -> StructureTurns.mirror(yaw, after));
            if (this.from != null && this.to != null) {
                this.mirroredOnSpan();
            }
        }
        return super.mirror(mirror);
    }

    private void turn(UnaryOperator<Vec3> step, UnaryOperator<Float> yaw) {
        if (this.steps != null) {
            this.steps = this.steps.change(step);
            Vec3 here = this.position();
            this.ropeEnd = this.steps.rope() == null ? this.ropeEnd : BlockPos.containing(here.add(this.steps.rope()));
            this.from = this.steps.from() == null ? this.from : BlockPos.containing(here.add(this.steps.from()));
            this.to = this.steps.to() == null ? this.to : BlockPos.containing(here.add(this.steps.to()));
            this.litAt = this.steps.lit() == null ? this.litAt : BlockPos.containing(here.add(this.steps.lit()));
            this.entityData.set(DATA_SPAN_FROM, Optional.ofNullable(this.from));
            this.entityData.set(DATA_SPAN_TO, Optional.ofNullable(this.to));
        }
        this.entityData.set(DATA_YAW, Mth.wrapDegrees(yaw.apply(this.yaw())));
    }

    /**
     * Mirrored on its span: the rope runs the mirrored way, and the side it faces is worked out from that way, so it faces
     * the rope's other side - as it does, mirrored - and turns so again when the rope moves ({@link #spanYaw(Level)}).
     */
    protected void mirroredOnSpan() {
        this.facesBack = !this.facesBack;
    }
}
