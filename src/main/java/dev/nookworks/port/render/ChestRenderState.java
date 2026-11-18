package dev.nookworks.port.render;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.resources.model.Material;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.ChestType;

/** 26.3's {@code ChestRenderState}: which half, how far open, the way it faces, and the sprite it is drawn with. */
public class ChestRenderState extends BlockEntityRenderState {
    public ChestType type = ChestType.SINGLE;
    public float open;
    public Direction facing = Direction.SOUTH;
    public @Nullable Material customSprite;
}
