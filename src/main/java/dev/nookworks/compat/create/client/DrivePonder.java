package dev.nookworks.compat.create.client;

import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import com.simibubi.create.infrastructure.ponder.AllCreatePonderTags;

import dev.nookworks.Nookworks;
import dev.nookworks.registry.ModOil;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/**
 * Ponder scenes (W over the item, as on Create's own blocks) for a Create shaft turning our machines straight: a shaft
 * into an Oil Press, a shaft into a Drive Wheel's axle, and a Motor turning Create's shafts. Their schematics are assets/nookworks/ponder/create_drive/*.nbt
 * (tools/port1211/gen_create.py); their texts are in our language files, nookworks.ponder.*. The machines and the wheel
 * are listed among Create's kinetic appliances.
 */
public final class DrivePonder implements PonderPlugin {
    @Override
    public String getModId() {
        return Nookworks.MODID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(ModOil.OIL_PRESS.getId(), id("rice_polisher"), id("hand_quern"), id("noodle_maker"))
                .addStoryBoard("create_drive/machine", DrivePonder::machine, AllCreatePonderTags.KINETIC_APPLIANCES);
        helper.forComponents(ModOil.DRIVE_WHEEL.getId())
                .addStoryBoard("create_drive/wheel", DrivePonder::wheel, AllCreatePonderTags.KINETIC_APPLIANCES);
        helper.forComponents(ModOil.MOTOR.getId())
                .addStoryBoard("create_drive/motor", DrivePonder::motor, AllCreatePonderTags.KINETIC_SOURCES);
    }

    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        helper.addToTag(AllCreatePonderTags.KINETIC_APPLIANCES).add(ModOil.OIL_PRESS.getId()).add(id("rice_polisher"))
                .add(id("hand_quern")).add(id("noodle_maker")).add(ModOil.DRIVE_WHEEL.getId());
        helper.addToTag(AllCreatePonderTags.KINETIC_SOURCES).add(ModOil.MOTOR.getId());
    }

    private static ResourceLocation id(String path) {
        return Nookworks.id(path);
    }

    /** Create's shaft run straight up to an Oil Press: the press turns with it. */
    private static void machine(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("create_drive", "Turning Nookworks' machines with Create's shafts");
        scene.configureBasePlate(0, 0, 5);
        scene.world().showSection(util.select().layer(0), Direction.UP);
        BlockPos press = util.grid().at(1, 1, 2);
        BlockPos near = util.grid().at(2, 1, 2);
        Selection shafts = util.select().fromTo(2, 1, 2, 4, 1, 2);
        scene.idle(10);
        scene.world().showSection(util.select().position(press), Direction.DOWN);
        scene.idle(15);
        scene.world().showSection(shafts, Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(70).attachKeyFrame().placeNearTarget().pointAt(util.vector().blockSurface(press, Direction.EAST))
                .text("A Create shaft set straight against a Nookworks machine joins its axle - nothing goes in between");
        scene.idle(80);
        scene.world().setKineticSpeed(shafts, 32.0F);
        scene.effects().rotationSpeedIndicator(near);
        scene.overlay().showText(80).attachKeyFrame().placeNearTarget().pointAt(util.vector().topOf(press))
                .text("The shaft turns the machine: a turn of the handle for every quarter round of the shaft");
        scene.idle(90);
        scene.overlay().showText(80).placeNearTarget().colored(PonderPalette.GREEN).pointAt(util.vector().topOf(near))
                .text("Each machine weighs 2 su per RPM on the shaft, half a Millstone: a Hand Crank turns four");
        scene.idle(90);
        scene.world().setKineticSpeed(shafts, 64.0F);
        scene.effects().rotationSpeedIndicator(near);
        scene.overlay().showText(60).placeNearTarget().pointAt(util.vector().topOf(press))
                .text("The faster the shaft, the faster the machine works");
        scene.idle(70);
        scene.overlay().showText(70).placeNearTarget().pointAt(util.vector().topOf(press))
                .text("A Hand Quern is turned from above or below, the others from their sides");
        scene.idle(80);
        scene.markAsFinished();
    }

    /** A powered Motor with Create's shafts run straight from its axle: they turn, and what they turn. */
    private static void motor(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("create_motor", "Turning Create's shafts with a Motor");
        scene.configureBasePlate(0, 0, 5);
        scene.world().showSection(util.select().layer(0), Direction.UP);
        BlockPos motor = util.grid().at(1, 1, 2);
        BlockPos near = util.grid().at(2, 1, 2);
        Selection shafts = util.select().fromTo(2, 1, 2, 4, 1, 2);
        scene.idle(10);
        scene.world().showSection(util.select().position(motor), Direction.DOWN);
        scene.idle(15);
        scene.world().showSection(shafts, Direction.DOWN);
        scene.idle(10);
        scene.world().setKineticSpeed(shafts, 30.0F);
        scene.effects().rotationSpeedIndicator(near);
        scene.overlay().showText(80).attachKeyFrame().placeNearTarget().pointAt(util.vector().blockSurface(motor, Direction.EAST))
                .text("A Create shaft set straight against a powered Motor's axle turns with it - nothing goes in between");
        scene.idle(90);
        scene.overlay().showText(80).placeNearTarget().colored(PonderPalette.GREEN).pointAt(util.vector().topOf(near))
                .text("It turns at 30 RPM and gives 480 su: the eight machines' worth a Motor turns");
        scene.idle(90);
        scene.overlay().showText(80).attachKeyFrame().placeNearTarget().pointAt(util.vector().topOf(motor))
                .text("The Motor pays in FE for the stress drawn: 20 FE/t for every 60 su, as for a machine of its own");
        scene.idle(90);
        scene.overlay().showText(80).placeNearTarget().colored(PonderPalette.GREEN).pointAt(util.vector().topOf(near))
                .text("Not only a shaft: a Cogwheel, a Gearbox, an Encased Shaft, a Mechanical Belt, a Millstone - whatever of Create's has a shaft end turns where it stands");
        scene.idle(90);
        scene.overlay().showText(90).placeNearTarget().pointAt(util.vector().topOf(motor))
                .text("A shaft at an axle end of a Drive Wheel the Motor turns is turned too; each machine of ours on the line takes an eighth of the 480 su");
        scene.idle(100);
        scene.markAsFinished();
    }

    /** Create's shaft into a Drive Wheel's axle: the wheel turns, and what its axle and belts reach. */
    private static void wheel(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("create_drive_wheel", "Driving a belt line with Create's shafts");
        scene.configureBasePlate(0, 0, 5);
        scene.world().showSection(util.select().layer(0), Direction.UP);
        BlockPos press = util.grid().at(1, 1, 2);
        BlockPos wheel = util.grid().at(2, 1, 2);
        Selection shafts = util.select().fromTo(3, 1, 2, 4, 1, 2);
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(1, 1, 2, 2, 1, 2), Direction.DOWN);
        scene.idle(15);
        scene.world().showSection(shafts, Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(70).attachKeyFrame().placeNearTarget().pointAt(util.vector().blockSurface(wheel, Direction.EAST))
                .text("A Create shaft on a Drive Wheel's axle turns the wheel");
        scene.idle(80);
        scene.world().setKineticSpeed(shafts, 32.0F);
        scene.effects().rotationSpeedIndicator(util.grid().at(3, 1, 2));
        scene.overlay().showText(90).attachKeyFrame().placeNearTarget().pointAt(util.vector().topOf(press))
                .text("The machine on its axle turns with it, and through its belts up to eight machines, as with a Motor");
        scene.idle(100);
        scene.overlay().showText(80).placeNearTarget().colored(PonderPalette.GREEN).pointAt(util.vector().topOf(wheel))
                .text("Every machine the line turns weighs on the shaft");
        scene.idle(90);
        scene.overlay().showText(90).placeNearTarget().pointAt(util.vector().blockSurface(wheel, Direction.EAST))
                .text("With Create's shafts at both ends of its axle line, the wheel passes their turning on: one Create network through it");
        scene.idle(100);
        scene.markAsFinished();
    }
}
