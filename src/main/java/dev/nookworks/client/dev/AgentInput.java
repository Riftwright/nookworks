package dev.nookworks.client.dev;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.lwjgl.sdl.SDLEvents;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.sdl.SDL_Event;
import org.lwjgl.sdl.SDL_EventFilter;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;

import dev.nookworks.Nookworks;
import dev.nookworks.rope.HungItemEntity;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Screenshot;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * DEVELOPMENT ONLY. Lets an automated agent drive the game while its window sits in the background.
 * <p>
 * Why: Minecraft ignores mouse movement, drags, cursor capture and held keys unless its window has OS focus
 * ({@code Window.focused}), so input posted from outside (Win32 PostMessage) cannot test in-world clicks,
 * GUI drags or movement. This bridge instead feeds input into Minecraft's own input handlers and keeps the
 * window's focus flag set, so everything goes through the real game code paths.
 * <p>
 * Active only when the JVM runs with {@code -Dnookworks.agentInput=true}, which only the {@code agentClient}
 * Gradle run sets. It is inert in normal play and in the released mod.
 * <p>
 * The window is hidden for its whole life when the run also sets {@code -Dnookworks.agentHidden=true} (the
 * {@code agentClient} run's default; {@code -PagentVisible} leaves it out): no window, no taskbar button, nothing to
 * take the user's focus or mouse or to be minimised by mistake. The game draws as before - {@code shot} reads its own
 * frame - and {@code window show} brings the window up when someone wants to watch or film it.
 * <p>
 * Commands are appended to {@code <game dir>/agent_input.txt}, one per line, one line per client tick. Each one
 * run is written to {@code agent_output.txt} ({@code ok <command>}, or {@code failed <command>: <why>}), so a
 * script can wait for its commands to be done; {@code agent_ready.txt} appears once a world is joined, and goes
 * when it is left. Both are started afresh with each game.
 * <pre>
 *   move X Y            cursor to window pixel X,Y
 *   down B [MODS]       mouse button B (0 left, 1 right) pressed; MODS: 1 shift, 2 ctrl, 4 alt
 *   up B [MODS]         mouse button released
 *   scroll DY           wheel (positive = up)
 *   keydown KEY [MODS]  GLFW key code (32 space, 340 left shift, 69 E, 256 escape)
 *   keyup KEY [MODS]
 *   look X Y Z          turn to look at a point from the eyes (tp ... facing aims from the feet)
 *   shot NAME           save the frame as drawn to screenshots/NAME.png (fine with the window hidden)
 *   use                 in-world right click (does not capture the cursor)
 *   attack              in-world left click
 *   mine N              hold the attack button for N ticks, to break a block
 *   usehold N           hold the use button down for N ticks, as a held right click (the spyglass, a camera raised)
 *   click use|attack    a click of the use or attack button as the game counts them (the key's own click, which a
 *                       raised camera takes as its shutter), without a mouse
 *   wait N              skip N ticks
 *   state               log focus, mouse grab, open screen, jump/sneak key state, frames a second, window hidden
 *   cmd COMMAND...      run a command as the player (no leading slash), without typing it into the chat
 *   close               close the open screen
 *   reload              reload resource packs (F3+T), after {@code processResources} has copied new textures
 *   bigicon ID PX [green]  one item alone, PX window pixels wide, on a key colour (for tools/make_logo.py)
 *   useon X Y Z FACE    use the held item on that block's face (FACE: up, down, north...), as a click there does
 *   hangon FX FY FZ TX TY TZ T ITEM  hang ITEM on the rope strung from post F to post T, T of the way (singleplayer);
 *                       refused, as a click is, where something is in its way
 *   campath TICKS x y z yaw pitch x y z yaw pitch [ease] [go]  move the camera (eye) smoothly from one place to
 *                       another; with go, the commands after it run while it moves (a die rolled on camera)
 *   turn TICKS yaw pitch [ease] [go]    turn the player's view smoothly from where it looks now (the short way round)
 *   turn TICKS at X Y Z [ease] [go]     the same, to look at a point (from the eyes, as look does)
 *   hud on|off          show or hide the HUD outright (F1 only flips it, and a flip lost along the way leaves every
 *                       shot after it the wrong way round)
 *   fov DEGREES         the field of view, as the slider sets it (30-110)
 *   player on|off       on: a person plays in this window - its focus and the mouse behave as in a normal game
 *                       (the cursor captured on a click, looking round with the mouse); off (the default) frees them
 *   lid X Y Z open|shut open or shut a chest's lid (a vanilla chest, a Repair Box) on this client only, as when someone
 *                       has it open, with no screen in front of it
 *   window show|hide    show the hidden window (without taking the focus: to watch, or to film it with a window
 *                       capture), or hide it again
 * </pre>
 */
@EventBusSubscriber(modid = Nookworks.MODID, value = Dist.CLIENT)
public final class AgentInput {
    private static final boolean ENABLED = Boolean.getBoolean("nookworks.agentInput");
    /** Whether the window starts hidden (the agentClient run without -PagentVisible). */
    private static final boolean START_HIDDEN = ENABLED && Boolean.getBoolean("nookworks.agentHidden");
    /** Whether the window is to be kept hidden now: from the start when hidden, until {@code window show}. */
    private static volatile boolean keepHidden = START_HIDDEN;
    /** The SDL event watch that hides the window the moment it is first shown; held so it is not freed. */
    private static @Nullable SDL_EventFilter hideOnShow;
    private static long readOffset = -1;
    /** Whether the output and ready files of an earlier game have been cleared yet. */
    private static boolean started;
    /** Whether {@code agent_ready.txt} says a world is joined. */
    private static boolean toldReady;
    private static int waitTicks;
    /** Ticks left of a held attack, for breaking a block. */
    private static int mineTicks;
    /** Ticks left of a held use (usehold). */
    private static int useTicks;
    /**
     * A camera move under way (campath): eye position and yaw, pitch at its start and end, the ticks it takes, the ticks
     * done, and whether it eases in and out. Moved on the client after each tick, so the frames between ticks are drawn
     * on the way from one place to the next - smooth at any frame rate, for recording (a /tp is a jump every tick).
     * The look is set again every frame ({@link #onCameraAngles}): the game draws the local player's view as it stands,
     * not eased between ticks as its place is, so a look set once a tick turned in steps (the user saw it, 2026-10-08).
     */
    private static double @Nullable [] camFrom;
    private static double[] camTo = new double[5];
    private static int camTicks;
    private static int camTick;
    private static boolean camEase;
    /** A turn of the view under way (turn): yaw and pitch at its start and end, ticks it takes and done, easing. */
    private static float @Nullable [] turnFrom;
    private static float[] turnTo = new float[2];
    private static int turnTicks;
    private static int turnTick;
    private static boolean turnEase;
    /** Whether a person is playing in the window (player on): the window's own focus and mouse capture are let be. */
    private static boolean playerHands;
    /** Whether the lines over the hotbar - the action bar and the held item's name - are kept off the screen
     *  (actionbar off): a video's captions go there. */
    private static boolean actionbarHidden;

    /**
     * Hides the window as the game first shows it. The game makes its window hidden and shows it once it is set up
     * ({@code EarlyWindowHandoff}), all inside the {@code Minecraft} constructor, with no event for a mod in between;
     * so an SDL event watch, set while the mods are built (before the window exists), hides it from inside that very
     * show, before anything is drawn. The run also asks SDL not to activate a window it shows
     * ({@code SDL_WINDOW_ACTIVATE_WHEN_SHOWN=0}), and turns NeoForge's loading-screen window off (its fml.toml), which
     * would otherwise stand on the screen from the start. Should the watch miss, {@link #onFrame} hides it a moment later.
     */
    @SubscribeEvent
    static void onConstruct(FMLConstructModEvent event) {
        if (!START_HIDDEN) {
            return;
        }
        hideOnShow = SDL_EventFilter.create((userdata, eventAddress) -> {
            SDL_Event shown = SDL_Event.create(eventAddress);
            if (keepHidden && shown.type() == SDLEvents.SDL_EVENT_WINDOW_SHOWN) {
                long window = SDLVideo.SDL_GetWindowFromID(shown.window().windowID());
                if (window != 0L) {
                    SDLVideo.SDL_HideWindow(window);
                    Nookworks.LOGGER.info("[agent-input] window hidden as it was shown");
                }
            }
            return true;
        });
        if (!SDLEvents.SDL_AddEventWatch(hideOnShow, 0L)) {
            Nookworks.LOGGER.warn("[agent-input] cannot watch the window being shown; it is hidden on the first frame");
        }
    }

    /** Keeps the window as asked ({@code window show|hide}): hidden unless shown on purpose. */
    @SubscribeEvent
    static void onFrame(RenderFrameEvent.Pre event) {
        if (keepHidden) {
            long window = Minecraft.getInstance().getWindow().handle();
            if ((SDLVideo.SDL_GetWindowFlags(window) & SDLVideo.SDL_WINDOW_HIDDEN) == 0) {
                SDLVideo.SDL_HideWindow(window);
                Nookworks.LOGGER.info("[agent-input] window hidden on a frame");
            }
        }
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Pre event) {
        if (!ENABLED) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Path dir = mc.gameDirectory.toPath();
        if (!started) {
            started = true;
            write(dir.resolve("agent_output.txt"), "", false);
            delete(dir.resolve("agent_ready.txt"));
        }
        boolean inWorld = mc.player != null && mc.level != null;
        if (inWorld != toldReady) {
            toldReady = inWorld;
            if (inWorld) {
                write(dir.resolve("agent_ready.txt"), "ready " + ProcessHandle.current().pid() + "\n", false);
            } else {
                delete(dir.resolve("agent_ready.txt"));
            }
        }
        if (!playerHands) {
            forceFocused(mc.getWindow());
            keepCursorFree(mc);
        }
        if (useTicks > 0 && --useTicks == 0) {
            mc.options.keyUse.setDown(false);
        }
        if (mineTicks > 0) {
            mineTicks--;
            invoke(Minecraft.class, mc, "continueAttack", new Class<?>[] {boolean.class}, true);
        }
        if (waitTicks > 0) {
            waitTicks--;
            return;
        }
        String line = nextLine(dir.resolve("agent_input.txt"));
        if (line != null && !line.isBlank()) {
            try {
                run(mc, line.trim().split("\\s+"));
                Nookworks.LOGGER.info("[agent-input] {}", line.trim());
                write(dir.resolve("agent_output.txt"), "ok " + line.trim() + "\n", true);
            } catch (RuntimeException e) {
                Nookworks.LOGGER.warn("[agent-input] failed: {} ({})", line.trim(), e.toString());
                write(dir.resolve("agent_output.txt"), "failed " + line.trim() + ": " + e + "\n", true);
            }
        }
    }

    private static void run(Minecraft mc, String[] a) {
        long handle = mc.getWindow().handle();
        int mods = a.length > 2 && a[2].matches("[0-9]+") ? Integer.parseInt(a[2]) : 0; // "cmd tp @s ..." has words there
        switch (a[0]) {
            case "move" -> {
                double x = Double.parseDouble(a[1]);
                double y = Double.parseDouble(a[2]);
                invoke(MouseHandler.class, mc.mouseHandler, "onMove", // 26.3 (SDL) is told the motion beside the place
                        new Class<?>[] {long.class, double.class, double.class, double.class, double.class},
                        handle, x, y, x - mc.mouseHandler.xpos(), y - mc.mouseHandler.ypos());
            }
            case "down", "up" -> invoke(MouseHandler.class, mc.mouseHandler, "onButton",
                    new Class<?>[] {long.class, MouseButtonInfo.class, int.class},
                    handle, new MouseButtonInfo(sdlButton(Integer.parseInt(a[1])), sdlMods(mods)), a[0].equals("down") ? 1 : 0);
            case "scroll" -> invoke(MouseHandler.class, mc.mouseHandler, "onScroll",
                    new Class<?>[] {long.class, double.class, double.class}, handle, 0.0, Double.parseDouble(a[1]));
            case "keydown", "keyup" -> invoke(KeyboardHandler.class, mc.keyboardHandler, "keyPress",
                    new Class<?>[] {long.class, int.class, KeyEvent.class},
                    handle, a[0].equals("keydown") ? 1 : 0, new KeyEvent(sdlKey(Integer.parseInt(a[1])), 0, sdlMods(mods)));
            // In-world clicking, without going near MouseHandler: posting a real button press there would
            // capture the cursor. These are the same calls the game makes for the attack and use keys.
            // characters typed into whatever has focus, as the keyboard sends them: type <text>
            case "type" -> {
                for (int c : String.join(" ", Arrays.copyOfRange(a, 1, a.length)).codePoints().toArray()) {
                    invoke(KeyboardHandler.class, mc.keyboardHandler, "charTyped",
                            new Class<?>[] {long.class, CharacterEvent.class}, handle,
                            new CharacterEvent(c));
                }
            }
            case "look" -> mc.player.lookAt(EntityAnchorArgument.Anchor.EYES,
                    new Vec3(Double.parseDouble(a[1]), Double.parseDouble(a[2]), Double.parseDouble(a[3])));
            // the last frame drawn, read from the game's own framebuffer rather than the window
            case "shot" -> Screenshot.grab(mc.gameDirectory, a[1] + ".png", mc.gameRenderer.mainRenderTarget(), 1, message -> {});
            case "use" -> invoke(Minecraft.class, mc, "startUseItem", new Class<?>[] {});
            case "attack" -> invoke(Minecraft.class, mc, "startAttack", new Class<?>[] {});
            case "mine" -> mineTicks = Integer.parseInt(a[1]);
            case "usehold" -> {
                useTicks = Integer.parseInt(a[1]);
                mc.options.keyUse.setDown(true);
                KeyMapping.click(mc.options.keyUse.getKey()); // the game starts using the item, as for a real press
            }
            case "click" -> KeyMapping.click((a[1].equals("use") ? mc.options.keyUse : mc.options.keyAttack).getKey());
            case "wait" -> waitTicks = Integer.parseInt(a[1]);
            case "state" -> Nookworks.LOGGER.info("[agent-input] state: focused={} grabbed={} screen={} jumpDown={} shiftDown={} onGround={} fps={} windowHidden={}",
                    mc.getWindow().isFocused(), mc.mouseHandler.isMouseGrabbed(), mc.gui.screen(),
                    mc.options.keyJump.isDown(), mc.options.keyShift.isDown(), mc.player != null && mc.player.onGround(),
                    mc.getFps(), (SDLVideo.SDL_GetWindowFlags(handle) & SDLVideo.SDL_WINDOW_HIDDEN) != 0);
            // the field of view for the shots that follow (degrees, 30-110), as the slider sets it: fov <degrees>
            case "fov" -> mc.options.fov().set(Integer.parseInt(a[1]));
            case "player" -> playerHands = a[1].equals("on");
            // the window shown (SDL does not activate it in a hidden run: no focus taken) or hidden again
            case "window" -> {
                keepHidden = switch (a[1]) {
                    case "show" -> false;
                    case "hide" -> true;
                    default -> throw new IllegalArgumentException("window show or window hide");
                };
                if (keepHidden) {
                    SDLVideo.SDL_HideWindow(handle);
                } else {
                    SDLVideo.SDL_ShowWindow(handle);
                }
            }
            // the lines over the hotbar (a machine's progress as it is worked, the name of an item taken in hand) kept off
            // the screen: actionbar off|on
            case "actionbar" -> actionbarHidden = a[1].equals("off");
            case "hud" -> {
                boolean hide = a[1].equals("off");
                if (mc.gui.hud.isHidden() != hide) {
                    mc.gui.hud.toggle();
                }
            }
            case "cmd" -> mc.player.connection.sendCommand(String.join(" ", Arrays.copyOfRange(a, 1, a.length)));
            case "close" -> {
                if (mc.player != null) {
                    mc.player.closeContainer();
                }
                mc.gui.setScreen(null);
            }
            // resource packs again, as F3+T: textures rebuilt by processResources show without a restart
            case "reload" -> mc.reloadResourcePacks();
            // every item of the creative tab on a flat colour, for the guide site's icons (IconSheetScreen)
            // iconsheet [list] <page> [green]
            case "iconsheet" -> {
                boolean list = a[1].equals("list");
                int page = Integer.parseInt(a[list ? 2 : 1]);
                mc.gui.setScreen(new IconSheetScreen(page, list, a[a.length - 1].equals("green")));
            }
            // the held item used on a block's face, as a click there does, with no aiming: useon X Y Z FACE
            // (the player within reach of it; for building scenes: posters, ropes, things hung, pipes that join)
            // the lid's block event, as the server sends it when a player opens or closes the chest, but here alone
            case "lid" -> {
                var box = mc.level.getBlockEntity(new BlockPos(Integer.parseInt(a[1]), Integer.parseInt(a[2]), Integer.parseInt(a[3])));
                if (box == null || !box.triggerEvent(1, a[4].equals("open") ? 1 : 0)) {
                    throw new IllegalArgumentException("no lid there");
                }
            }
            case "useon" -> {
                BlockPos at = new BlockPos(Integer.parseInt(a[1]), Integer.parseInt(a[2]), Integer.parseInt(a[3]));
                Direction face = Direction.byName(a[4]);
                Vec3 hit = Vec3.atCenterOf(at).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, at, false));
            }
            // something hung on the rope strung from post FROM to post TO, T of the way along, as a click on the rope
            // there hangs it (on the integrated server, without aiming): hangon FX FY FZ TX TY TZ T ITEM, where ITEM
            // "hand" is a copy of the held stack, components and all (a banner's patterns), faced as by the player
            case "hangon" -> {
                BlockPos from = new BlockPos(Integer.parseInt(a[1]), Integer.parseInt(a[2]), Integer.parseInt(a[3]));
                BlockPos to = new BlockPos(Integer.parseInt(a[4]), Integer.parseInt(a[5]), Integer.parseInt(a[6]));
                double along = Double.parseDouble(a[7]);
                ItemStack stack = a[8].equals("hand") ? mc.player.getMainHandItem().copyWithCount(1)
                        : new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(a[8])));
                Vec3 seenFrom = mc.player.position();
                var server = mc.getSingleplayerServer();
                var dimension = mc.player.level().dimension();
                // refused where a player's click would be - something in its way under the rope - so a scene set up
                // by commands never shows what the game would not allow (a drying line's fish in the grass, 2026-10-07)
                String misfit = server.submit(() -> {
                    var level = server.getLevel(dimension);
                    HungItemEntity hung = HungItemEntity.onSpan(level, from, to, along, stack, seenFrom);
                    String why = hung.misfit(level);
                    if (why == null) {
                        level.addFreshEntity(hung);
                    }
                    return why;
                }).join();
                if (misfit != null) {
                    throw new IllegalStateException("hangon refused: " + misfit);
                }
            }
            // a smooth camera move, for recording: campath TICKS x y z yaw pitch x y z yaw pitch [ease] [go], the eye's
            // place and look at the start and the end; the queue waits until it is done, unless "go"
            case "campath" -> {
                camTicks = Math.max(1, Integer.parseInt(a[1]));
                camFrom = new double[5];
                for (int i = 0; i < 5; i++) {
                    camFrom[i] = Double.parseDouble(a[2 + i]);
                    camTo[i] = Double.parseDouble(a[7 + i]);
                }
                List<String> flags = Arrays.asList(a).subList(Math.min(a.length, 12), a.length);
                camEase = flags.contains("ease");
                camTick = 0;
                placeCamera(mc, 0.0, true);
                waitTicks = flags.contains("go") ? 0 : camTicks + 1;
            }
            // the view turned smoothly, for recording a look round in first person: turn TICKS yaw pitch [ease] [go],
            // or turn TICKS at X Y Z [ease] [go] to end looking at a point; the queue waits until it is done, unless "go"
            case "turn" -> {
                turnTicks = Math.max(1, Integer.parseInt(a[1]));
                turnFrom = new float[] {mc.player.getYRot(), mc.player.getXRot()};
                int rest;
                if (a[2].equals("at")) {
                    Vec3 eye = mc.player.getEyePosition();
                    double dx = Double.parseDouble(a[3]) - eye.x;
                    double dy = Double.parseDouble(a[4]) - eye.y;
                    double dz = Double.parseDouble(a[5]) - eye.z;
                    turnTo[0] = (float) Math.toDegrees(Math.atan2(-dx, dz));
                    turnTo[1] = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
                    rest = 6;
                } else {
                    turnTo[0] = Float.parseFloat(a[2]);
                    turnTo[1] = Float.parseFloat(a[3]);
                    rest = 4;
                }
                // the short way round, from wherever the yaw has wound to
                turnTo[0] = turnFrom[0] + Mth.wrapDegrees(turnTo[0] - turnFrom[0]);
                List<String> flags = Arrays.asList(a).subList(Math.min(a.length, rest), a.length);
                turnEase = flags.contains("ease");
                turnTick = 0;
                waitTicks = flags.contains("go") ? 0 : turnTicks + 1;
            }
            // one item alone, big, for a project logo: bigicon <item id> <window px> [green]
            case "bigicon" -> mc.gui.setScreen(IconSheetScreen.single(
                    new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(a[1]))), Integer.parseInt(a[2]),
                    a[a.length - 1].equals("green")));
            default -> throw new IllegalArgumentException("unknown command " + a[0]);
        }
    }

    @SubscribeEvent
    static void onGuiLayer(RenderGuiLayerEvent.Pre event) {
        if (ENABLED && actionbarHidden && (event.getName().equals(VanillaGuiLayers.OVERLAY_MESSAGE)
                || event.getName().equals(VanillaGuiLayers.SELECTED_ITEM_NAME))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onClientTickPost(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!ENABLED || mc.player == null) {
            return;
        }
        if (camFrom != null) {
            camTick++;
            // kept one tick past the end, so the frames drawn on the way to the last place still turn with it
            if (camTick > camTicks) {
                camFrom = null;
            } else {
                double t = camTick / (double) camTicks;
                placeCamera(mc, camEase ? eased(t) : t, false);
            }
        }
        if (turnFrom != null) {
            turnTick++;
            if (turnTick >= turnTicks) {
                aim(mc.player, turnTo[0], turnTo[1]);
                turnFrom = null;
            }
        }
    }

    /**
     * The look for this very frame, between ticks, given to the camera as it is placed (the camera is set before
     * {@code RenderFrameEvent.Pre}, so a look set there showed a frame late, and a camera move's look of the new tick
     * slipped through on the frame after each tick): a camera move's at the same point of its way as the place being
     * drawn (eased from the last tick's place to this one's, so a tick behind), a turn's at the time it is now.
     */
    @SubscribeEvent
    static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (!ENABLED || mc.player == null || event.getCamera().entity() != mc.player) {
            return;
        }
        double partial = event.getPartialTick();
        float yaw;
        float pitch;
        if (camFrom != null) {
            double t = Mth.clamp((camTick - 1 + partial) / camTicks, 0.0, 1.0);
            double k = camEase ? eased(t) : t;
            yaw = (float) (camFrom[3] + Mth.wrapDegrees(camTo[3] - camFrom[3]) * k);
            pitch = (float) (camFrom[4] + (camTo[4] - camFrom[4]) * k);
        } else if (turnFrom != null) {
            double t = Mth.clamp((turnTick + partial) / turnTicks, 0.0, 1.0);
            double k = turnEase ? eased(t) : t;
            yaw = (float) (turnFrom[0] + (turnTo[0] - turnFrom[0]) * k);
            pitch = (float) (turnFrom[1] + (turnTo[1] - turnFrom[1]) * k);
        } else {
            return;
        }
        aim(mc.player, yaw, pitch);
        event.setYaw(yaw);
        event.setPitch(pitch);
    }

    private static double eased(double t) {
        return t * t * (3 - 2 * t);
    }

    private static void aim(LocalPlayer player, float yaw, float pitch) {
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.setYHeadRot(yaw);
    }

    /** The player (the camera) at {@code t} of the way along the camera move; {@code jump}: no drawing in between. */
    private static void placeCamera(Minecraft mc, double t, boolean jump) {
        var player = mc.player;
        double x = camFrom[0] + (camTo[0] - camFrom[0]) * t;
        double y = camFrom[1] + (camTo[1] - camFrom[1]) * t - player.getEyeHeight();
        double z = camFrom[2] + (camTo[2] - camFrom[2]) * t;
        double turn = Mth.wrapDegrees(camTo[3] - camFrom[3]);
        float yaw = (float) (camFrom[3] + turn * t);
        float pitch = (float) (camFrom[4] + (camTo[4] - camFrom[4]) * t);
        player.setDeltaMovement(Vec3.ZERO);
        player.setPos(x, y, z);
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.setYHeadRot(yaw);
        if (jump) {
            player.setOldPosAndRot();
        }
    }

    private static void write(Path file, String text, boolean append) {
        try {
            if (append) {
                Files.writeString(file, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } else {
                Files.writeString(file, text, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            Nookworks.LOGGER.warn("[agent-input] cannot write {}: {}", file, e.toString());
        }
    }

    private static void delete(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            Nookworks.LOGGER.warn("[agent-input] cannot delete {}: {}", file, e.toString());
        }
    }

    /** Reads the next unread line. Commands already in the file when the game started are skipped. */
    private static String nextLine(Path file) {
        if (!Files.exists(file)) {
            readOffset = 0; // a file created later is read from its start
            return null;
        }
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            if (readOffset < 0 || readOffset > raf.length()) {
                readOffset = raf.length(); // skip commands left over from an earlier session
                return null;
            }
            raf.seek(readOffset);
            StringBuilder sb = new StringBuilder();
            int b;
            while ((b = raf.read()) != -1 && b != '\n') {
                sb.append((char) b);
            }
            if (b == -1) {
                return null; // partial line: wait until it is complete
            }
            readOffset = raf.getFilePointer();
            return new String(sb.toString().getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Hands the OS cursor back, every tick.
     * <p>
     * Minecraft captures the cursor (GLFW {@code CURSOR_DISABLED}) the first time a mouse button is pressed
     * with no screen open, and {@link #forceFocused} makes that succeed even though the window is really in
     * the background - so driving the game used to take the mouse away from whoever is using the computer.
     * Nothing here needs the capture: aiming is done with {@code /tp ... facing}, and in-world clicks go
     * through {@code use}/{@code attack}, which call Minecraft's own click handling directly.
     */
    private static void keepCursorFree(Minecraft mc) {
        if (mc.mouseHandler.isMouseGrabbed()) {
            mc.mouseHandler.releaseMouse();
        }
    }

    /*
     * The commands take GLFW's numbers for buttons, keys and modifiers, as the tools and notes written for them give
     * them. From 26.3 LWJGL ships SDL in place of GLFW and the game reads SDL's (keys are scancodes), so they are
     * turned into those here.
     */
    private static int sdlButton(int glfw) {
        return switch (glfw) {
            case 0 -> InputConstants.MOUSE_BUTTON_LEFT;
            case 1 -> InputConstants.MOUSE_BUTTON_RIGHT;
            case 2 -> InputConstants.MOUSE_BUTTON_MIDDLE;
            default -> glfw + 1; // the side buttons: GLFW counts from 0, SDL from 1
        };
    }

    private static int sdlMods(int glfw) {
        return ((glfw & 1) != 0 ? InputConstants.MOD_SHIFT : 0) | ((glfw & 2) != 0 ? InputConstants.MOD_CONTROL : 0)
                | ((glfw & 4) != 0 ? InputConstants.MOD_ALT : 0);
    }

    private static int sdlKey(int glfw) {
        if (glfw >= 'A' && glfw <= 'Z') {
            return InputConstants.KEY_A + (glfw - 'A');
        }
        if (glfw >= '1' && glfw <= '9') {
            return InputConstants.KEY_1 + (glfw - '1');
        }
        if (glfw >= 290 && glfw <= 301) { // F1 to F12
            return InputConstants.KEY_F1 + (glfw - 290);
        }
        return switch (glfw) {
            case ' ' -> InputConstants.KEY_SPACE;
            case '0' -> InputConstants.KEY_0;
            case 256 -> InputConstants.KEY_ESCAPE;
            case 257 -> InputConstants.KEY_RETURN;
            case 258 -> InputConstants.KEY_TAB;
            case 259 -> InputConstants.KEY_BACKSPACE;
            case 260 -> InputConstants.KEY_INSERT;
            case 261 -> InputConstants.KEY_DELETE;
            case 262 -> InputConstants.KEY_RIGHT;
            case 263 -> InputConstants.KEY_LEFT;
            case 264 -> InputConstants.KEY_DOWN;
            case 265 -> InputConstants.KEY_UP;
            case 340 -> InputConstants.KEY_LSHIFT;
            case 341 -> InputConstants.KEY_LCONTROL;
            case 342 -> InputConstants.KEY_LALT;
            case 344 -> InputConstants.KEY_RSHIFT;
            case 345 -> InputConstants.KEY_RCONTROL;
            case 346 -> InputConstants.KEY_RALT;
            default -> throw new IllegalArgumentException("no SDL key for GLFW key " + glfw);
        };
    }

    private static void forceFocused(Window window) {
        try {
            Field focused = Window.class.getDeclaredField("focused");
            focused.setAccessible(true);
            focused.setBoolean(window, true);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("agent input: cannot set window focus", e);
        }
    }

    private static void invoke(Class<?> owner, Object target, String name, Class<?>[] types, Object... args) {
        try {
            Method m = owner.getDeclaredMethod(name, types);
            m.setAccessible(true);
            m.invoke(target, args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("agent input: cannot call " + name, e);
        }
    }

    private AgentInput() {}
}
