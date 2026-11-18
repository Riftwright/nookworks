package dev.nookworks.gametest;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.nookworks.port.test.GameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Other mods' blocks for the tests that try their pipes, cables and buses on ours: found by registry id and set the way
 * their own wrench or screen sets them, by reflection, so Nookworks has no compile-time link to any of them (and the
 * tests are registered only when the mod is loaded). A name that is not found fails the test, saying which: a later
 * version of the other mod that renamed it shows here, not as a silent pass.
 */
final class Foreign {
    private Foreign() {}

    static boolean loaded(String mod) {
        return ModList.get().isLoaded(mod);
    }

    static Block block(String id) {
        Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
        if (block == Blocks.AIR) {
            throw new IllegalStateException("no block " + id);
        }
        return block;
    }

    static Item item(String id) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
        if (item == net.minecraft.world.item.Items.AIR) {
            throw new IllegalStateException("no item " + id);
        }
        return item;
    }

    static BlockEntity be(GameTestHelper helper, BlockPos pos) {
        BlockEntity be = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (be == null) {
            throw new IllegalStateException("no block entity at " + pos + " (" + helper.getBlockState(pos) + ")");
        }
        return be;
    }

    static Player fakePlayer(GameTestHelper helper) {
        return FakePlayerFactory.getMinecraft(helper.getLevel());
    }

    /** Recomputes the shape of a pipe block set by setBlock (which skips getStateForPlacement) from its neighbours. */
    static void reshape(GameTestHelper helper, BlockPos pos) {
        BlockPos at = helper.absolutePos(pos);
        BlockState state = helper.getLevel().getBlockState(at);
        BlockState shaped = Block.updateFromNeighbourShapes(state, helper.getLevel(), at);
        if (shaped != state) {
            helper.getLevel().setBlock(at, shaped, 3);
        }
    }

    // ---- reflection ----

    static Class<?> type(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("no class " + name, e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static Object constant(String enumClass, String name) {
        return Enum.valueOf((Class) type(enumClass), name);
    }

    static @Nullable Object staticField(String className, String field) {
        try {
            return type(className).getField(field).get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("no field " + className + "." + field, e);
        }
    }

    /** Calls the public method of that name whose parameters take these arguments (null fits any object parameter). */
    static @Nullable Object call(Object target, String name, Object... args) {
        return invoke(target.getClass(), target, name, args);
    }

    static @Nullable Object callStatic(String className, String name, Object... args) {
        return invoke(type(className), null, name, args);
    }

    private static @Nullable Object invoke(Class<?> owner, @Nullable Object target, String name, Object... args) {
        List<Method> seen = new ArrayList<>();
        for (Class<?> c = owner; c != null; c = c.getSuperclass()) {
            for (Method method : c.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == args.length && fits(method, args)) {
                    seen.add(method);
                }
            }
        }
        for (Class<?> c : owner.getInterfaces()) {
            for (Method method : c.getMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == args.length && fits(method, args)) {
                    seen.add(method);
                }
            }
        }
        if (seen.isEmpty()) {
            throw new IllegalStateException("no method " + owner.getName() + "." + name + " for " + args.length + " arguments");
        }
        Method method = seen.get(0);
        try {
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (ReflectiveOperationException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new IllegalStateException(owner.getName() + "." + name + " threw " + cause, cause);
        }
    }

    private static boolean fits(Method method, Object[] args) {
        Class<?>[] types = method.getParameterTypes();
        for (int i = 0; i < args.length; i++) {
            if (args[i] == null) {
                if (types[i].isPrimitive()) {
                    return false;
                }
                continue;
            }
            Class<?> want = box(types[i]);
            if (!want.isInstance(args[i])) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> box(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        return type == int.class ? Integer.class : type == long.class ? Long.class : type == boolean.class ? Boolean.class
                : type == double.class ? Double.class : type == float.class ? Float.class : type == byte.class ? Byte.class
                : type == short.class ? Short.class : Character.class;
    }

    // ---- NeoForge 21.1's capabilities, as another mod sees a block ----

    static @Nullable IItemHandler items(GameTestHelper helper, BlockPos pos, @Nullable Direction side) {
        return helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(pos), side);
    }

    static @Nullable IFluidHandler fluids(GameTestHelper helper, BlockPos pos, @Nullable Direction side) {
        return helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(pos), side);
    }

    static @Nullable IEnergyStorage energy(GameTestHelper helper, BlockPos pos, @Nullable Direction side) {
        return helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(pos), side);
    }

    /** Fills a block through its 21.1 fluid handler, from no side or else the first side that takes it. */
    static int fill(GameTestHelper helper, BlockPos pos, FluidStack stack) {
        IFluidHandler handler = fluids(helper, pos, null);
        if (handler != null && handler.fill(stack, IFluidHandler.FluidAction.SIMULATE) == stack.getAmount()) {
            return handler.fill(stack.copy(), IFluidHandler.FluidAction.EXECUTE);
        }
        for (Direction side : Direction.values()) {
            handler = fluids(helper, pos, side);
            if (handler != null && handler.fill(stack, IFluidHandler.FluidAction.SIMULATE) > 0) {
                return handler.fill(stack.copy(), IFluidHandler.FluidAction.EXECUTE);
            }
        }
        return 0;
    }

    /** A block's 21.1 item handler from one side, slot by slot, as another mod's pipe reads it: for a failing test's message. */
    static String describe(GameTestHelper helper, BlockPos pos, @Nullable Direction side, ItemStack probe) {
        IItemHandler handler = items(helper, pos, side);
        if (handler == null) {
            return "no item handler from " + side;
        }
        StringBuilder out = new StringBuilder("from " + side + ":");
        for (int i = 0; i < handler.getSlots(); i++) {
            out.append(" [").append(i).append(' ').append(handler.getStackInSlot(i)).append(" limit ").append(handler.getSlotLimit(i))
                    .append(" valid ").append(handler.isItemValid(i, probe)).append(" takes ")
                    .append(probe.getCount() - handler.insertItem(i, probe.copy(), true).getCount()).append(']');
        }
        return out.toString();
    }

    /** How many of {@code item} a block holds, as another mod reads it (every slot of its 21.1 item handler). */
    static long count(GameTestHelper helper, BlockPos pos, Item item) {
        IItemHandler handler = items(helper, pos, null);
        long total = 0;
        if (handler != null) {
            for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack stack = handler.getStackInSlot(i);
                if (stack.is(item)) {
                    total += stack.getCount();
                }
            }
        }
        return total;
    }

    /** How much fluid a block holds (every tank of its 21.1 fluid handler, from no side). */
    static int fluid(GameTestHelper helper, BlockPos pos, @Nullable Direction side) {
        IFluidHandler handler = fluids(helper, pos, side);
        int total = 0;
        if (handler != null) {
            for (int i = 0; i < handler.getTanks(); i++) {
                total += handler.getFluidInTank(i).getAmount();
            }
        }
        return total;
    }

    static FluidStack fluidIn(GameTestHelper helper, BlockPos pos, @Nullable Direction side) {
        IFluidHandler handler = fluids(helper, pos, side);
        if (handler != null) {
            for (int i = 0; i < handler.getTanks(); i++) {
                if (!handler.getFluidInTank(i).isEmpty()) {
                    return handler.getFluidInTank(i);
                }
            }
        }
        return FluidStack.EMPTY;
    }
}
