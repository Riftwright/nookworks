package dev.nookworks.client.dev.bot;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.nookworks.dev.bot.path.Goal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Things in the hands and the bags: crafting, smelting, eating, armour, holding, throwing, chests. */
final class ItemTasks {
    private ItemTasks() {}

    /** A cell beside the player to set a block down in: air, with a floor, not where it stands. */
    static @Nullable BlockPos freeSpot(Minecraft mc) {
        BlockPos feet = mc.player.blockPosition();
        for (int r = 1; r <= 3; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        BlockPos p = feet.offset(dx, dy, dz);
                        BlockState s = mc.level.getBlockState(p);
                        BlockState below = mc.level.getBlockState(p.below());
                        if (s.canBeReplaced() && s.getFluidState().isEmpty() && below.isFaceSturdy(mc.level, p.below(), net.minecraft.core.Direction.UP)
                                && !p.equals(feet) && !p.equals(feet.above())) {
                            return p;
                        }
                    }
                }
            }
        }
        return null;
    }

    /** Turns to a block and right-clicks it with an empty hand, not sneaking (a door, a chest, a table, a bed). True
     *  while it should be waited on; false once it has tried too long. */
    static boolean useBlock(Minecraft mc, Controls c, BlockPos pos, int tries) {
        LocalPlayer player = mc.player;
        Acts.Aim aim = Acts.aimAt(player, pos);
        c.lookAt(player, aim.point());
        c.sneak = false;
        if (Controls.looking(player, aim.point(), 8.0F) && tries % 10 == 5) {
            Inv.select(mc, Inv.emptyHandSlot(player));
            Acts.useOn(mc, pos, aim.face(), aim.point());
        }
        if (tries > 80) {
            Bot.fail("it would not open: " + pos.toShortString());
            return false;
        }
        return true;
    }

    static void closeScreen(Minecraft mc) {
        if (mc.gui.screen() instanceof AbstractContainerScreen<?>) {
            mc.player.closeContainer();
        }
    }

    // ---- smelting

    static int burnTicks(ItemStack stack) {
        if (stack.is(Items.COAL) || stack.is(Items.CHARCOAL)) {
            return 1600;
        }
        if (stack.is(Items.COAL_BLOCK)) {
            return 16000;
        }
        if (stack.is(Items.LAVA_BUCKET)) {
            return 20000;
        }
        if (stack.is(Items.BLAZE_ROD)) {
            return 2400;
        }
        if (stack.is(ItemTags.LOGS) || stack.is(ItemTags.PLANKS)) {
            return 300;
        }
        if (stack.is(Items.STICK)) {
            return 100;
        }
        return stack.has(DataComponents.COOKING_FUEL) ? 200 : 0;
    }

    /**
     * Smelts {@code count} of an input in the nearest furnace, smoker or blast furnace (one set down if it has one): the
     * input and enough fuel put in by hand, the screen kept open until the output is taken.
     */
    static final class Smelt implements Task, Bot.ScreenTask {
        private final Item input;
        private final int count;
        private final @Nullable Item fuel;
        private @Nullable BlockPos furnace;
        private @Nullable Goal goal;
        private @Nullable Task sub;
        private int opened;
        private int phase;
        private int waited;
        private int taken;

        Smelt(Item input, int count, @Nullable Item fuel) {
            this.input = input;
            this.count = count;
            this.fuel = fuel;
        }

        @Override
        public String describe() {
            return "smelt " + this.count + " " + Bot.name(this.input) + " (" + this.taken + " out)";
        }

        @Override
        public Result tick(Minecraft mc, Controls c) {
            LocalPlayer player = mc.player;
            if (this.sub != null) {
                Result r = this.sub.tick(mc, c);
                if (r == Result.RUNNING) {
                    return r;
                }
                this.sub.cancel(mc);
                this.sub = null;
                if (r == Result.FAILED) {
                    return r;
                }
            }
            if (!(player.containerMenu instanceof AbstractFurnaceMenu menu)) {
                if (this.furnace == null || !isFurnace(mc.level.getBlockState(this.furnace))) {
                    this.furnace = Scan.nearest(mc, ItemTasks::isFurnace, 24, java.util.Set.of());
                    this.goal = null;
                    if (this.furnace == null) {
                        if (Inv.find(player, s -> s.is(Items.FURNACE)) >= 0 || player.getAbilities().instabuild) {
                            BlockPos spot = freeSpot(mc);
                            if (spot == null) {
                                Bot.fail("nowhere to set a furnace down");
                                return Result.FAILED;
                            }
                            this.sub = new WorkTasks.Build(List.of(new WorkTasks.Target(spot, Blocks.FURNACE.defaultBlockState())), "set a furnace down");
                            return Result.RUNNING;
                        }
                        this.sub = new Crafting.Craft(Items.FURNACE, 1);
                        return Result.RUNNING;
                    }
                }
                if (this.goal == null || !Bot.walker.isFor(this.goal)) {
                    this.goal = new WorkTasks.ReachGoal(this.furnace);
                    Bot.walker.go(this.goal);
                }
                if (Bot.walker.state() != Walker.State.DONE) {
                    Bot.walker.tick(mc, c);
                    if (Bot.walker.state() == Walker.State.FAILED) {
                        Bot.fail("cannot get to the furnace");
                        return Result.FAILED;
                    }
                    return Result.RUNNING;
                }
                return useBlock(mc, c, this.furnace, ++this.opened) ? Result.RUNNING : Result.FAILED;
            }
            Inventory inv = player.getInventory();
            switch (this.phase) {
                case 0 -> { // the input
                    int want = this.count - menu.getSlot(AbstractFurnaceMenu.INGREDIENT_SLOT).getItem().getCount();
                    int from = Inv.find(player, s -> s.is(this.input));
                    if (from < 0 && menu.getSlot(AbstractFurnaceMenu.INGREDIENT_SLOT).getItem().isEmpty()) {
                        Bot.fail("no " + Bot.name(this.input) + " to smelt");
                        return Result.FAILED;
                    }
                    if (from >= 0 && want > 0) {
                        int slot = Inv.menuSlot(menu, inv, from);
                        moveSome(mc, menu, slot, AbstractFurnaceMenu.INGREDIENT_SLOT, want);
                    }
                    this.phase = 1;
                }
                case 1 -> { // the fuel: enough for the lot
                    ItemStack in = menu.getSlot(AbstractFurnaceMenu.FUEL_SLOT).getItem();
                    int need = (int) Math.ceil(this.count * 200.0 / Math.max(1, burnTicks(in.isEmpty() ? ItemStack.EMPTY : in))) - in.getCount();
                    if (in.isEmpty() || need > 0 && !menu.isLit()) {
                        int from = this.fuel != null ? Inv.find(player, s -> s.is(this.fuel)) : bestFuel(player);
                        if (from < 0 && in.isEmpty() && !menu.isLit()) {
                            Bot.fail("no fuel");
                            return Result.FAILED;
                        }
                        if (from >= 0) {
                            ItemStack f = inv.getItem(from);
                            int n = (int) Math.ceil(this.count * 200.0 / burnTicks(f));
                            moveSome(mc, menu, Inv.menuSlot(menu, inv, from), AbstractFurnaceMenu.FUEL_SLOT, n);
                        }
                    }
                    this.phase = 2;
                }
                default -> { // waiting, taking what comes out
                    ItemStack out = menu.getSlot(AbstractFurnaceMenu.RESULT_SLOT).getItem();
                    if (!out.isEmpty() && (this.waited % 40 == 0 || menu.getSlot(AbstractFurnaceMenu.INGREDIENT_SLOT).getItem().isEmpty())) {
                        this.taken += out.getCount();
                        Inv.click(mc, menu, AbstractFurnaceMenu.RESULT_SLOT, 0, ContainerInput.QUICK_MOVE);
                    }
                    if (this.taken >= this.count || menu.getSlot(AbstractFurnaceMenu.INGREDIENT_SLOT).getItem().isEmpty()
                            && out.isEmpty() && !menu.isLit() && this.waited > 20) {
                        closeScreen(mc);
                        return Result.DONE;
                    }
                    if (++this.waited > 200 * this.count + 400) {
                        closeScreen(mc);
                        Bot.fail("the furnace is too slow");
                        return Result.FAILED;
                    }
                    c.lookAt(player, Vec3.atCenterOf(this.furnace));
                }
            }
            return Result.RUNNING;
        }

        @Override
        public void cancel(Minecraft mc) {
            closeScreen(mc);
            Bot.walker.stop(mc);
        }
    }

    static boolean isFurnace(BlockState s) {
        return s.is(Blocks.FURNACE) || s.is(Blocks.SMOKER) || s.is(Blocks.BLAST_FURNACE);
    }

    static int bestFuel(LocalPlayer player) {
        Inventory inv = player.getInventory();
        int best = -1;
        int bestTicks = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            // logs are kept for planks when there is anything else
            int t = burnTicks(s) * (s.is(ItemTags.LOGS) ? 1 : 2);
            if (t > bestTicks && !s.is(Items.LAVA_BUCKET)) {
                bestTicks = t;
                best = i;
            }
        }
        return best;
    }

    /** Moves {@code n} items from one menu slot to another: picked up, put down one at a time or whole, the rest back. */
    static void moveSome(Minecraft mc, AbstractContainerMenu menu, int from, int to, int n) {
        ItemStack stack = menu.getSlot(from).getItem();
        if (stack.isEmpty() || n <= 0) {
            return;
        }
        Inv.click(mc, menu, from, 0, ContainerInput.PICKUP);
        if (n >= stack.getCount()) {
            Inv.click(mc, menu, to, 0, ContainerInput.PICKUP);
        } else {
            for (int i = 0; i < n; i++) {
                Inv.click(mc, menu, to, 1, ContainerInput.PICKUP);
            }
        }
        if (!menu.getCarried().isEmpty()) {
            Inv.click(mc, menu, from, 0, ContainerInput.PICKUP);
        }
    }

    // ---- eating, armour, holding, throwing

    /** Eats the best food for the hunger it has: the key held until it is eaten. */
    static final class Eat implements Task {
        /** Hurt: a golden apple may be eaten, hungry or not. */
        private final boolean hurt;
        private int ticks;
        private int before = -1;

        Eat(boolean hurt) {
            this.hurt = hurt;
        }

        @Override
        public String describe() {
            return "eat";
        }

        @Override
        public Result tick(Minecraft mc, Controls c) {
            LocalPlayer player = mc.player;
            if (this.before < 0) {
                int food = this.hurt ? Inv.healingFood(player) : Inv.bestFood(player, player.getFoodData().getFoodLevel() <= 4);
                if (food < 0) {
                    Bot.fail(this.hurt && !player.getFoodData().needsFood() ? "not hungry, and no golden apple" : "nothing to eat");
                    return Result.FAILED;
                }
                if (!player.getFoodData().needsFood() && !player.getAbilities().instabuild
                        && !Inv.alwaysEdible(player.getInventory().getItem(food))) {
                    return Result.DONE;
                }
                Inv.hold(mc, food);
                this.before = player.getMainHandItem().getCount();
                return Result.RUNNING;
            }
            c.stopMoving();
            // up at the sky first: the use key held down on a chest in view opened it (and a job with a screen open
            // waits for good), on a door opened that
            c.face(player.getYRot(), -75.0F);
            if (player.getXRot() > -60.0F && this.ticks == 0) {
                return Result.RUNNING;
            }
            if (this.ticks == 1) {
                Acts.useItem(mc);
            }
            mc.options.keyUse.setDown(true);
            if (++this.ticks > 50 || this.ticks > 3 && !player.isUsingItem()) {
                mc.options.keyUse.setDown(false);
                if (player.getMainHandItem().getCount() < this.before || player.getMainHandItem().isEmpty()) {
                    return Result.DONE;
                }
                Bot.fail("did not eat (" + Bot.name(player.getMainHandItem().getItem()) + " in hand)");
                return Result.FAILED;
            }
            return Result.RUNNING;
        }

        @Override
        public void cancel(Minecraft mc) {
            mc.options.keyUse.setDown(false);
        }
    }

    /** Puts on the best armour it has, slot by slot (and a shield in the off hand), as clicks in the inventory. */
    static final class Equip implements Task {
        private int wait;

        @Override
        public String describe() {
            return "put on armour";
        }

        @Override
        public Result tick(Minecraft mc, Controls c) {
            if (this.wait-- > 0) {
                return Result.RUNNING;
            }
            LocalPlayer player = mc.player;
            Inventory inv = player.getInventory();
            InventoryMenu menu = player.inventoryMenu;
            for (int i = 0; i < 36; i++) {
                ItemStack stack = inv.getItem(i);
                EquipmentSlot slot = Inv.wornIn(stack);
                if (slot == null) {
                    continue;
                }
                ItemStack worn = player.getItemBySlot(slot);
                boolean better = slot == EquipmentSlot.OFFHAND ? worn.isEmpty() && stack.is(Items.SHIELD)
                        : slot.isArmor() && Inv.armor(stack) > Inv.armor(worn);
                if (!better) {
                    continue;
                }
                int from = Inv.menuSlot(menu, inv, i);
                int to = slot == EquipmentSlot.OFFHAND ? InventoryMenu.SHIELD_SLOT : 8 - slot.getIndex();
                Inv.click(mc, menu, from, 0, ContainerInput.PICKUP);
                Inv.click(mc, menu, to, 0, ContainerInput.PICKUP);
                if (!menu.getCarried().isEmpty()) {
                    Inv.click(mc, menu, from, 0, ContainerInput.PICKUP);
                }
                this.wait = 2;
                return Result.RUNNING;
            }
            return Result.DONE;
        }
    }

    static final class Hold implements Task {
        private final Item item;

        Hold(Item item) {
            this.item = item;
        }

        @Override
        public String describe() {
            return "hold " + Bot.name(this.item);
        }

        @Override
        public Result tick(Minecraft mc, Controls c) {
            if (Inv.holdItem(mc, s -> s.is(this.item), new ItemStack(this.item))) {
                return Result.DONE;
            }
            Bot.fail("no " + Bot.name(this.item));
            return Result.FAILED;
        }
    }

    /** Throws items out of the inventory: a number of them, or all. */
    static final class Drop implements Task {
        private final Item item;
        private final String how;
        private int thrown;

        Drop(Item item, String how) {
            this.item = item;
            this.how = how;
        }

        @Override
        public String describe() {
            return "throw " + this.how + " " + Bot.name(this.item);
        }

        @Override
        public Result tick(Minecraft mc, Controls c) {
            LocalPlayer player = mc.player;
            Inventory inv = player.getInventory();
            int want = this.how.equals("all") ? Integer.MAX_VALUE : Integer.parseInt(this.how);
            int index = Inv.find(player, s -> s.is(this.item));
            if (index < 0 || this.thrown >= want) {
                return this.thrown > 0 ? Result.DONE : fail();
            }
            InventoryMenu menu = player.inventoryMenu;
            int slot = Inv.menuSlot(menu, inv, index);
            ItemStack stack = inv.getItem(index);
            if (want - this.thrown >= stack.getCount()) {
                this.thrown += stack.getCount();
                Inv.click(mc, menu, slot, 1, ContainerInput.THROW);
            } else {
                this.thrown++;
                Inv.click(mc, menu, slot, 0, ContainerInput.THROW);
            }
            return Result.RUNNING;
        }

        private Result fail() {
            Bot.fail("no " + Bot.name(this.item));
            return Result.FAILED;
        }
    }

    /**
     * Hands items to a player: walks up to them, turns to face them, and throws the items their way (all it carries but
     * its tools, weapons and armour; or one kind, or N of it). The player picks them up as they land.
     */
    static final class Give implements Task {
        private final String who;
        private final @Nullable Item item;
        private final int count;
        private @Nullable Goal goal;
        private int thrown;
        private int ticks;

        Give(String who, @Nullable Item item, int count) {
            this.who = who;
            this.item = item;
            this.count = count;
        }

        @Override
        public String describe() {
            return "give " + (this.item == null ? "things" : Bot.name(this.item)) + " to " + this.who;
        }

        private boolean wanted(ItemStack s) {
            if (s.isEmpty()) {
                return false;
            }
            if (this.item != null) {
                return s.is(this.item);
            }
            return !s.has(DataComponents.TOOL) && !s.has(DataComponents.WEAPON) && Inv.wornIn(s) == null;
        }

        @Override
        public Result tick(Minecraft mc, Controls c) {
            LocalPlayer me = mc.player;
            var them = MoveTasks.player(mc, this.who);
            if (them == null || them.isDeadOrDying()) {
                if (++this.ticks > 600) {
                    Bot.fail(this.who + " is not here");
                    return Result.FAILED;
                }
                return Result.RUNNING;
            }
            if (me.distanceTo(them) > 3.0) {
                if (this.goal == null || !Bot.walker.isFor(this.goal) || Bot.walker.state() == Walker.State.DONE
                        || Bot.walker.state() == Walker.State.FAILED) {
                    this.goal = Goal.near(them.blockPosition(), 2.0);
                    Bot.walker.go(this.goal);
                }
                Bot.walker.tick(mc, c);
                return Result.RUNNING;
            }
            Bot.walker.stop(mc);
            c.lookAt(me, them.getEyePosition().add(0, -0.6, 0));
            if (!Controls.looking(me, them.getEyePosition().add(0, -0.6, 0), 10.0F) || ++this.ticks % 3 != 0) {
                return Result.RUNNING;
            }
            Inventory inv = me.getInventory();
            int index = Inv.find(me, this::wanted);
            if (index < 0 || this.thrown >= this.count) {
                return this.thrown > 0 ? Result.DONE : this.none();
            }
            InventoryMenu menu = me.inventoryMenu;
            int slot = Inv.menuSlot(menu, inv, index);
            ItemStack stack = inv.getItem(index);
            if (this.count - this.thrown >= stack.getCount()) {
                this.thrown += stack.getCount();
                Inv.click(mc, menu, slot, 1, ContainerInput.THROW);
            } else {
                this.thrown++;
                Inv.click(mc, menu, slot, 0, ContainerInput.THROW);
            }
            return Result.RUNNING;
        }

        private Result none() {
            Bot.fail("nothing to give");
            return Result.FAILED;
        }

        @Override
        public void cancel(Minecraft mc) {
            Bot.walker.stop(mc);
        }
    }

    // ---- chests

    /** Opens the container at a place (walking there first); then {@link #work} until it says it is done. */
    abstract static class AtContainer implements Task, Bot.ScreenTask {
        final BlockPos pos;
        private @Nullable Goal goal;
        private int opened;
        private int worked;

        AtContainer(BlockPos pos) {
            this.pos = pos;
        }

        @Override
        public Result tick(Minecraft mc, Controls c) {
            LocalPlayer player = mc.player;
            if (player.containerMenu == player.inventoryMenu) {
                if (this.goal == null || !Bot.walker.isFor(this.goal)) {
                    this.goal = new WorkTasks.ReachGoal(this.pos);
                    Bot.walker.go(this.goal);
                }
                if (Bot.walker.state() != Walker.State.DONE) {
                    Bot.walker.tick(mc, c);
                    if (Bot.walker.state() == Walker.State.FAILED) {
                        Bot.fail("cannot get to " + this.pos.toShortString());
                        return Result.FAILED;
                    }
                    return Result.RUNNING;
                }
                return useBlock(mc, c, this.pos, ++this.opened) ? Result.RUNNING : Result.FAILED;
            }
            if (this.worked++ % 2 == 1) {
                return Result.RUNNING; // a tick between clicks, for the server
            }
            if (this.work(mc, player.containerMenu)) {
                closeScreen(mc);
                return Result.DONE;
            }
            return Result.RUNNING;
        }

        /** One click's worth of work; true when done. */
        abstract boolean work(Minecraft mc, AbstractContainerMenu menu);

        @Override
        public void cancel(Minecraft mc) {
            closeScreen(mc);
            Bot.walker.stop(mc);
        }
    }

    static final class Deposit extends AtContainer {
        private final String which;

        Deposit(BlockPos pos, String which) {
            super(pos);
            this.which = which;
        }

        @Override
        public String describe() {
            return "put " + this.which + " into " + this.pos.toShortString();
        }

        /** Whether menu slot {@code at} holds the first stack of its kind among the player's own slots. */
        private static boolean firstStack(AbstractContainerMenu menu, Inventory inv, int at, ItemStack s) {
            for (int j = 0; j < at; j++) {
                Slot other = menu.slots.get(j);
                if (other.container == inv && ItemStack.isSameItem(other.getItem(), s)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        boolean work(Minecraft mc, AbstractContainerMenu menu) {
            Inventory inv = mc.player.getInventory();
            boolean loot = this.which.equals("loot");
            Item only = this.which.equals("all") || loot ? null : Bot.item(this.which);
            for (int i = 0; i < menu.slots.size(); i++) {
                Slot slot = menu.slots.get(i);
                ItemStack s = slot.getItem();
                // what it works with stays: "all" keeps the hotbar's tools, weapons and food; "loot" (the chores' full
                // bag) keeps them wherever they are, and armour, torches, buckets, what tools are made of, and the
                // first stack of each kind of block it builds pillars and bridges of
                boolean gear = s.has(DataComponents.TOOL) || s.has(DataComponents.WEAPON) || s.has(DataComponents.FOOD);
                boolean hotbarTool = slot.container == inv && only == null && (loot
                        ? gear || s.has(DataComponents.EQUIPPABLE) || s.is(Items.TORCH) || s.getItem() instanceof net.minecraft.world.item.BucketItem
                                || s.is(net.minecraft.tags.ItemTags.LOGS) || s.is(net.minecraft.tags.ItemTags.PLANKS) || s.is(Items.STICK)
                                || s.is(Items.IRON_INGOT) || s.is(Items.COAL) || s.is(Items.CHARCOAL) || s.is(Items.STRING)
                                || Inv.throwaway(s) && firstStack(menu, inv, i, s)
                        : slot.getContainerSlot() < 9 && gear);
                if (slot.container == inv && !s.isEmpty() && !hotbarTool && (only == null || s.is(only))) {
                    Inv.click(mc, menu, i, 0, ContainerInput.QUICK_MOVE);
                    if (!slot.getItem().isEmpty() && slot.getItem().getCount() == s.getCount()) {
                        return true; // the container is full
                    }
                    return false;
                }
            }
            return true;
        }
    }

    static final class Withdraw extends AtContainer {
        private final Item item;
        private final int count;
        private int taken;

        Withdraw(BlockPos pos, Item item, int count) {
            super(pos);
            this.item = item;
            this.count = count;
        }

        @Override
        public String describe() {
            return "take " + this.taken + "/" + this.count + " " + Bot.name(this.item) + " from " + this.pos.toShortString();
        }

        @Override
        boolean work(Minecraft mc, AbstractContainerMenu menu) {
            Inventory inv = mc.player.getInventory();
            if (this.taken >= this.count) {
                return true;
            }
            for (int i = 0; i < menu.slots.size(); i++) {
                Slot slot = menu.slots.get(i);
                if (slot.container != inv && slot.getItem().is(this.item)) {
                    int n = slot.getItem().getCount();
                    if (n <= this.count - this.taken) {
                        Inv.click(mc, menu, i, 0, ContainerInput.QUICK_MOVE);
                        this.taken += n;
                    } else {
                        // just some: picked up, the rest put back one at a time
                        Inv.click(mc, menu, i, 0, ContainerInput.PICKUP);
                        int back = n - (this.count - this.taken);
                        for (int k = 0; k < back; k++) {
                            Inv.click(mc, menu, i, 1, ContainerInput.PICKUP);
                        }
                        int free = Inv.menuSlot(menu, inv, inv.getFreeSlot());
                        if (free >= 0) {
                            Inv.click(mc, menu, free, 0, ContainerInput.PICKUP);
                        }
                        this.taken = this.count;
                    }
                    return false;
                }
            }
            return true;
        }
    }
}
