package dev.nookworks.port.test;

import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestAssertPosException;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The GameTest helper as the mod's tests know it from Minecraft 26.3, on top of 1.21.1's. The tests only change their
 * import: each test is handed one of these (NookworksGameTests wraps the helper 1.21.1 makes for it), with the methods
 * 26.3 added or reshaped - assertions that take a Component, a block entity fetched by its class, a mock server player,
 * a block set facing a way - behaving as 26.3's do.
 * <p>
 * Where a test's positions are counted from differs, and matters most: in 1.21.1 the test's corner is its structure
 * block, which stands one below the structure (the arena's stone floor is relative y=1 there); in 26.3 it is the
 * structure's own corner (the floor is y=0, and every test of ours sets its blocks from y=1). Every position this helper
 * turns, and every one 1.21.1's own methods turn (they all go through {@link #absolutePos} and {@link #absoluteVec}),
 * is counted from the structure's corner, as in 26.3.
 */
public class GameTestHelper extends net.minecraft.gametest.framework.GameTestHelper {
    public GameTestHelper(GameTestInfo testInfo) {
        super(testInfo);
    }

    // ---- where the test stands ----

    /** The structure's own corner, which 26.3 counts a test's positions from (1.21.1: one below, at its structure block). */
    private BlockPos origin() {
        return this.testInfo.getStructureBlockPos().offset(this.testInfo.getStructureBlockEntity().getStructurePos());
    }

    /** The turn that undoes {@code rotation} (1.21.1's Rotation has no getInverse). */
    private static Rotation inverse(Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90 -> Rotation.COUNTERCLOCKWISE_90;
            case COUNTERCLOCKWISE_90 -> Rotation.CLOCKWISE_90;
            default -> rotation;
        };
    }

    @Override
    public BlockPos absolutePos(BlockPos relativePos) {
        BlockPos origin = this.origin();
        return StructureTemplate.transform(origin.offset(relativePos), Mirror.NONE, this.testInfo.getRotation(), origin);
    }

    @Override
    public BlockPos relativePos(BlockPos absolutePos) {
        BlockPos origin = this.origin();
        return StructureTemplate.transform(absolutePos, Mirror.NONE, inverse(this.testInfo.getRotation()), origin).subtract(origin);
    }

    @Override
    public Vec3 absoluteVec(Vec3 relativeVec) {
        BlockPos origin = this.origin();
        return StructureTemplate.transform(Vec3.atLowerCornerOf(origin).add(relativeVec), Mirror.NONE, this.testInfo.getRotation(), origin);
    }

    @Override
    public Vec3 relativeVec(Vec3 absoluteVec) {
        BlockPos origin = this.origin();
        return StructureTemplate.transform(absoluteVec, Mirror.NONE, inverse(this.testInfo.getRotation()), origin)
                .subtract(Vec3.atLowerCornerOf(origin));
    }

    public AABB absoluteAABB(AABB relativeAABB) {
        return new AABB(this.absoluteVec(relativeAABB.getMinPosition()), this.absoluteVec(relativeAABB.getMaxPosition()));
    }

    public AABB relativeAABB(AABB absoluteAABB) {
        return new AABB(this.relativeVec(absoluteAABB.getMinPosition()), this.relativeVec(absoluteAABB.getMaxPosition()));
    }

    /** The structure's room counted from its corner, turned with the test (public in 26.3, private in 1.21.1). */
    public AABB getRelativeBounds() {
        AABB absolute = this.testInfo.getStructureBounds();
        return switch (this.testInfo.getRotation()) {
            case COUNTERCLOCKWISE_90, CLOCKWISE_90 -> new AABB(0.0, 0.0, 0.0, absolute.getZsize(), absolute.getYsize(), absolute.getXsize());
            default -> new AABB(0.0, 0.0, 0.0, absolute.getXsize(), absolute.getYsize(), absolute.getZsize());
        };
    }

    public Direction getTestDirection() {
        return this.testInfo.getRotation().rotate(Direction.SOUTH);
    }

    public Direction getAbsoluteDirection(Direction direction) {
        return this.getTestRotation().rotate(direction);
    }

    // ---- failing ----

    public GameTestAssertException assertionException(Component description) {
        return new GameTestAssertException(description.getString());
    }

    /** 26.3 translates {@code descriptionId}; 1.21.1 lacks its test.error texts, so an untranslated one reads as given. */
    public GameTestAssertException assertionException(String descriptionId, Object... arguments) {
        return this.assertionException(Component.translatableWithFallback(descriptionId, descriptionId, arguments));
    }

    public GameTestAssertPosException assertionException(BlockPos pos, Component description) {
        return new GameTestAssertPosException(description.getString(), this.absolutePos(pos), pos, this.getTick());
    }

    public GameTestAssertPosException assertionException(BlockPos pos, String descriptionId, Object... arguments) {
        return this.assertionException(pos, Component.translatableWithFallback(descriptionId, descriptionId, arguments));
    }

    public void fail(Component message) {
        throw this.assertionException(message);
    }

    public void fail(Component message, BlockPos pos) {
        throw this.assertionException(pos, message);
    }

    public void fail(Component message, Entity entity) {
        throw this.assertionException(this.relativePos(entity.blockPosition()), message);
    }

    public void assertTrue(boolean condition, Component errorMessage) {
        if (!condition) {
            throw this.assertionException(errorMessage);
        }
    }

    public void assertFalse(boolean condition, Component errorMessage) {
        this.assertTrue(!condition, errorMessage);
    }

    public <N> void assertValueEqual(N value, N expected, Component valueName) {
        if (!value.equals(expected)) {
            throw this.assertionException(Component.literal("Expected " + valueName.getString() + " to be " + expected + ", but was " + value));
        }
    }

    // ---- blocks ----

    /** The block entity at {@code pos}, which must be a {@code type}: a test fails on a missing or different one. */
    public <T extends BlockEntity> T getBlockEntity(BlockPos pos, Class<T> type) {
        BlockEntity blockEntity = this.getLevel().getBlockEntity(this.absolutePos(pos));
        if (blockEntity == null) {
            throw this.assertionException(pos, Component.literal("Missing block entity"));
        } else if (type.isInstance(blockEntity)) {
            return type.cast(blockEntity);
        } else {
            throw this.assertionException(pos, Component.literal("Wrong block entity: " + BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType())));
        }
    }

    public void setBlock(BlockPos blockPos, Block block, Direction direction) {
        this.setBlock(blockPos, block.defaultBlockState(), direction);
    }

    /** Sets {@code blockState} turned to {@code direction}, if it turns (as 26.3: either facing property it has). */
    public void setBlock(BlockPos blockPos, BlockState blockState, Direction direction) {
        BlockState state = blockState;
        if (blockState.hasProperty(HorizontalDirectionalBlock.FACING)) {
            state = blockState.setValue(HorizontalDirectionalBlock.FACING, direction);
        }
        if (blockState.hasProperty(BlockStateProperties.FACING)) {
            state = blockState.setValue(BlockStateProperties.FACING, direction);
        }
        this.getLevel().setBlockAndUpdate(this.absolutePos(blockPos), state);
    }

    public void assertBlockState(BlockPos pos, BlockState expected) {
        BlockState state = this.getBlockState(pos);
        if (!state.equals(expected)) {
            throw this.assertionException(pos, Component.literal("Expected " + expected + ", got " + state));
        }
    }

    public void assertBlockState(BlockPos pos, Predicate<BlockState> predicate, Function<BlockState, Component> errorMessage) {
        BlockState state = this.getBlockState(pos);
        if (!predicate.test(state)) {
            throw this.assertionException(pos, errorMessage.apply(state));
        }
    }

    public <T extends Comparable<T>> void assertBlockProperty(BlockPos pos, Property<T> property, Predicate<T> predicate, Component errorMessage) {
        this.assertBlockState(pos, state -> state.hasProperty(property) && predicate.test(state.getValue(property)), state -> errorMessage);
    }

    // ---- entities and players ----

    /**
     * As 26.3 spawns: kept from despawning, faced the test's way, set down with whatever rides it. Every other spawn of
     * 1.21.1's helper (at a block, at x y z, with no free will) comes here.
     */
    @Override
    public <E extends Entity> E spawn(EntityType<E> entityType, Vec3 pos) {
        ServerLevel level = this.getLevel();
        E entity = entityType.create(level);
        if (entity == null) {
            throw this.assertionException(BlockPos.containing(pos), Component.literal("Failed to create entity " + BuiltInRegistries.ENTITY_TYPE.getKey(entityType)));
        }
        if (entity instanceof Mob mob) {
            mob.setPersistenceRequired();
        }
        Vec3 absolute = this.absoluteVec(pos);
        float yRot = entity.rotate(this.getTestRotation());
        entity.moveTo(absolute.x, absolute.y, absolute.z, yRot, entity.getXRot());
        entity.setYBodyRot(yRot);
        entity.setYHeadRot(yRot);
        level.addFreshEntityWithPassengers(entity);
        return entity;
    }

    /**
     * A server player in {@code gameType}, not joined to the server (26.3's mock server player). 26.3 sets its game mode
     * and lets the server, not a client, move it; 1.21.1 asks isSpectator / isCreative, and moves a vehicle a player
     * steers where isLocalPlayer says - the same as 1.21.1's own mock player answers.
     */
    public Player makeMockServerPlayer(GameType gameType) {
        ServerPlayer player = new ServerPlayer(this.getLevel().getServer(), this.getLevel(), new GameProfile(UUID.randomUUID(), "test-mock-player"),
                ClientInformation.createDefault()) {
            @Override
            public boolean isSpectator() {
                return gameType == GameType.SPECTATOR;
            }

            @Override
            public boolean isCreative() {
                return gameType.isCreative();
            }

            @Override
            public boolean isLocalPlayer() {
                return true;
            }
        };
        gameType.updatePlayerAbilities(player.getAbilities());
        // and its game mode, as 26.3's mock has it: 1.21.1's ServerPlayerGameMode.destroyBlock asks it, so a creative
        // mock breaking a block dropped it as a survival player would (the add-on's NEEDS 8). setGameMode would send a
        // packet the mock has no connection for; the game mode's own setter sends nothing
        try {
            java.lang.reflect.Method set = net.minecraft.server.level.ServerPlayerGameMode.class.getDeclaredMethod("setGameModeForPlayer",
                    GameType.class, GameType.class);
            set.setAccessible(true);
            set.invoke(player.gameMode, gameType, null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("ServerPlayerGameMode.setGameModeForPlayer not found", e);
        }
        return player;
    }
}
