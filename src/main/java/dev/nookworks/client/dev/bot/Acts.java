package dev.nookworks.client.dev.bot;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The walker's clicks, each made as the game makes a person's: breaking a block by holding the attack key on it (the
 * game's own {@code continueDestroyBlock}, with the arm swinging), a block used or set down with a right click on a face,
 * a mob struck. The view is turned to what is clicked first: what the walker does, it is seen doing.
 */
final class Acts {
    private Acts() {}

    /** The point of the block it sees best from the eyes, and the face that point is on. */
    record Aim(Vec3 point, Direction face) {}

    /** Where to aim at a block: the face the eyes see (a ray to its middle), else the face turned toward the eyes. */
    static Aim aimAt(LocalPlayer player, BlockPos pos) {
        Vec3 eye = player.getEyePosition();
        Vec3 center = Vec3.atCenterOf(pos);
        BlockHitResult hit = player.level().clip(new ClipContext(eye, center, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos)) {
            return new Aim(hit.getLocation(), hit.getDirection());
        }
        Direction face = Direction.getApproximateNearest(eye.x - center.x, eye.y - center.y, eye.z - center.z);
        return new Aim(center.add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5), face);
    }

    /** Whether the eyes reach the point (the player's block reach, a little short of it). */
    static boolean inReach(LocalPlayer player, Vec3 point) {
        double reach = player.blockInteractionRange() - 0.15;
        return player.getEyePosition().distanceToSqr(point) <= reach * reach;
    }

    /** A right click on the block's face at {@code hit}, with what is in the hand. */
    static InteractionResult useOn(Minecraft mc, BlockPos pos, Direction face, Vec3 hit) {
        InteractionResult result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, pos, false));
        if (result instanceof InteractionResult.Success success && success.swingSource() == InteractionResult.SwingSource.PREDICTED) {
            swing(mc, false);
        }
        return result;
    }

    /** A right click in the air (eat, drink, throw, a bucket aimed at a block through the look). */
    static InteractionResult useItem(Minecraft mc) {
        return mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
    }

    /** The arm swung as the game swings it for a click: the held item's own attack or use animation. */
    static void swing(Minecraft mc, boolean attack) {
        var held = mc.player.getMainHandItem();
        mc.player.swing(InteractionHand.MAIN_HAND, attack ? held.getAttackAnimation() : held.getInteractAnimation(), false);
    }

    static void attack(Minecraft mc, Entity target) {
        mc.gameMode.attack(mc.player, target);
        swing(mc, true);
    }

    /**
     * Breaks one block: the best tool taken, the view turned to it, the attack key held on it until it goes. Ticked once
     * a tick; done when the cell is clear (a gravel column falling into the gap is broken in turn, up to a point).
     */
    static final class Breaker {
        final BlockPos pos;
        private int ticks;
        private int limit;
        private int refills;
        private boolean toolChosen;
        private net.minecraft.world.item.@Nullable Item tool;
        private @Nullable String failure;
        private String what = "";
        /** The block itself is wanted (mining), not just the way cleared: a plant with nothing to bump into counts as
         *  gone only once broken (mining short grass for seeds broke nothing, 2026-10-22). */
        private final boolean whole;
        private net.minecraft.world.level.block.@Nullable Block was;

        Breaker(BlockPos pos) {
            this(pos, false);
        }

        Breaker(BlockPos pos, boolean whole) {
            this.pos = pos;
            this.whole = whole;
        }

        @Nullable String failure() {
            return this.failure;
        }

        /** True when the block is gone (or the job failed: see {@link #failure}). */
        boolean tick(Minecraft mc, Controls controls) {
            LocalPlayer player = mc.player;
            BlockState state = player.level().getBlockState(this.pos);
            if (gone(player, state)) {
                if (this.ticks > 0) {
                    mc.gameMode.stopDestroyBlock();
                    Bot.event("broke", this.what + " at " + this.pos.toShortString());
                }
                return true;
            }
            this.what = Bot.id(state.getBlock());
            if (this.toolChosen && this.tool != null && this.ticks > 5 && !player.getMainHandItem().is(this.tool)) {
                this.toolChosen = false; // the tool broke in the hand: another, and the time it takes worked out again
                this.ticks = 0;
            }
            if (!this.toolChosen) {
                this.toolChosen = true;
                int tool = Inv.bestTool(player, state);
                this.tool = tool >= 0 ? player.getInventory().getItem(tool).getItem() : null;
                if (tool >= 0) {
                    Inv.hold(mc, tool);
                } else {
                    Inv.select(mc, Inv.emptyHandSlot(player));
                }
                this.limit = (int) Math.min(2000, Inv.breakTicks(player, state, this.pos) * 2 + 60);
            }
            Aim aim = aimAt(player, this.pos);
            if (!inReach(player, aim.point())) {
                this.failure = "out of reach of " + this.pos.toShortString();
                return true;
            }
            controls.stopMoving();
            // a hammer in the hand (the best tool it has): held as for one block - by default, sneaking - or the 3x3
            // round the block goes too (a floor, a stair's step)
            boolean hammer = player.getMainHandItem().is(dev.nookworks.item.Hammers.TAG);
            boolean sneak = hammer && dev.nookworks.dev.bot.Stairs.sneakForOne(dev.nookworks.NookworksConfig.HAMMER_ACTIVATION.get());
            controls.sneak = sneak;
            controls.lookAt(player, aim.point());
            if (++this.ticks > this.limit) {
                this.failure = "could not break " + state.getBlock().getName().getString() + " at " + this.pos.toShortString();
                mc.gameMode.stopDestroyBlock();
                return true;
            }
            if (Controls.looking(player, aim.point(), 20.0F) && (!hammer || player.isShiftKeyDown() == sneak)) {
                mc.gameMode.continueDestroyBlock(this.pos, aim.face());
                swing(mc, true);
            }
            return false;
        }

        /** Whether the cell is clear for walking: nothing left of the block in the way. Gravel that fell in again counts. */
        boolean gone(LocalPlayer player, BlockState state) {
            if (this.whole) {
                if (this.was == null) {
                    this.was = state.getBlock();
                }
                if (state.isAir() || !state.is(this.was) && !(state.getBlock() instanceof net.minecraft.world.level.block.FallingBlock)) {
                    return true;
                }
                if (!state.getFluidState().isEmpty() && state.getCollisionShape(player.level(), this.pos).isEmpty() && state.canBeReplaced()) {
                    return true; // water came in where it was
                }
            }
            if (this.whole && !state.isAir() && state.getCollisionShape(player.level(), this.pos).isEmpty()) {
                return false; // a plant: broken, not walked through
            }
            if (state.isAir() || state.getCollisionShape(player.level(), this.pos).isEmpty() && !state.canBeReplaced() && state.getFluidState().isEmpty()) {
                return true;
            }
            if (state.canBeReplaced() && state.getCollisionShape(player.level(), this.pos).isEmpty()) {
                return true; // grass, snow layers: nothing in the way
            }
            if (this.ticks > 0 && this.refills < 8 && state.getBlock() instanceof net.minecraft.world.level.block.FallingBlock) {
                this.refills++;
                this.toolChosen = false; // a new block fell in: start over on it
                this.ticks = 0;
            }
            return false;
        }
    }
}
