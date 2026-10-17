package dev.nookworks.ventilation;

import dev.nookworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A Ventilation Fan's place in its level, so smoke can find the fans near it without searching the blocks round it
 * ({@link Ventilation}); and, on the client, how far round its blades have turned.
 */
public class VentFanBlockEntity extends BlockEntity {
    /** Degrees a tick the blades turn at full speed. */
    public static final float FULL_SPEED = 24.0F;

    private float angle;
    private float speed;
    private float lastTime = -1;

    public VentFanBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.VENT_FAN.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level != null) {
            Ventilation.fanLoaded(this.level, this.worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        if (this.level != null) {
            Ventilation.fanUnloaded(this.level, this.worldPosition);
        }
        super.setRemoved();
    }

    /** On the client: the blades' angle now, their speed easing up as the fan starts and down as it stops. */
    public float bladeAngle(float time, boolean on) {
        if (this.lastTime < 0) {
            this.lastTime = time;
            this.speed = on ? FULL_SPEED : 0;
        }
        float dt = Math.max(0, Math.min(5, time - this.lastTime));
        this.lastTime = time;
        float target = on ? FULL_SPEED : 0;
        this.speed += (target - this.speed) * Math.min(1, dt * 0.06F);
        this.angle = (this.angle + this.speed * dt) % 360;
        return this.angle;
    }
}
