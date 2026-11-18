package dev.nookworks.fluid;

import dev.nookworks.oil.OilFluids;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import dev.nookworks.port.transfer.FluidResource;

/**
 * What a window on the copper line shows standing in it - a Sight Glass, a Copper Tank: nothing, water, vegetable
 * oil, biodiesel, methanol, or (another mod's fluid of the same kind) something else, drawn in a plain grey.
 */
public enum LineContent implements StringRepresentable {
    EMPTY("empty"),
    WATER("water"),
    OIL("oil"),
    BIODIESEL("biodiesel"),
    /** Since 1.5 (a value added: old worlds' blocks keep theirs). */
    METHANOL("methanol"),
    OTHER("other");

    public static final EnumProperty<LineContent> PROPERTY = EnumProperty.create("content", LineContent.class);

    private final String name;

    LineContent(String name) {
        this.name = name;
    }

    public static LineContent of(FluidResource resource, int amount) {
        if (amount <= 0 || resource.isEmpty()) {
            return EMPTY;
        }
        return of(resource.getFluid());
    }

    public static LineContent of(Fluid fluid) {
        if (fluid.isSame(Fluids.WATER)) {
            return WATER;
        }
        if (fluid.isSame(OilFluids.SOURCE.get())) {
            return OIL;
        }
        if (fluid.isSame(OilFluids.BIODIESEL.get())) {
            return BIODIESEL;
        }
        if (fluid.isSame(OilFluids.METHANOL.get())) {
            return METHANOL;
        }
        return fluid == Fluids.EMPTY ? EMPTY : OTHER;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }
}
