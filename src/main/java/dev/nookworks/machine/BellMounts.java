package dev.nookworks.machine;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;

import dev.nookworks.Nookworks;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;

/**
 * Where a Machine Bell meets each block (the user, 2026-11-12: 「奥まっている部分だけでなく、高さが低めの機械や形が特殊な
 * 機械にもちゃんと美しく張り付くように」). A machine's side is a panel set in from its frame, and some are lower or shaped;
 * tools/textures/gen_machine_bell.py reads every model of ours as its blockstate turns it and measures, for each face, how
 * far to move the bell up or down the face ({@code up}, -3 to 3) and how deep the black iron pad behind its plate must
 * reach to meet the surface ({@code inset}). A block not listed is flat, as a wall.
 * <p>
 * Data: {@code data/<namespace>/bell_mounts/*.json}, each {@code {"<block id>": {"<its facing, or none>": {"<face the
 * bell is on>": [inset, up]}}}} - so an add-on lists its own machines (the script takes another mod's folder:
 * {@code --mod <resources> <namespace>}). Server side: the bell's state carries the mount to clients.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class BellMounts extends SimpleJsonResourceReloadListener<Map<String, Map<String, Map<String, List<Integer>>>>> {
    /** How the bell sits on one face: the pad's depth and how far it is moved up (down below 0), px. */
    public record Mount(int inset, int up) {
        public static final Mount FLAT = new Mount(0, 0);
    }

    /** A file: block id, then its facing (or none), then the face the bell is on, to [inset, up]. */
    private static final Codec<Map<String, Map<String, Map<String, List<Integer>>>>> FILE = Codec.unboundedMap(Codec.STRING,
            Codec.unboundedMap(Codec.STRING, Codec.unboundedMap(Codec.STRING, Codec.INT.listOf())));
    private static volatile Map<Block, Map<String, Map<Direction, Mount>>> table = Map.of();

    private BellMounts() {
        super(FILE, FileToIdConverter.json("bell_mounts"));
    }

    @SubscribeEvent
    static void listen(AddServerReloadListenersEvent event) {
        event.addListener(Nookworks.id("bell_mounts"), new BellMounts());
    }

    /**
     * How a bell sits on {@code face} of a block in {@code support}: by its facing where it has one (the table is by its
     * model as turned), or as listed for none; flat for a block not listed.
     */
    public static Mount of(BlockState support, Direction face) {
        Map<String, Map<Direction, Mount>> byFacing = table.get(support.getBlock());
        if (byFacing == null) {
            return Mount.FLAT;
        }
        Map<Direction, Mount> faces = null;
        for (Property<?> property : support.getProperties()) {
            if (property.getName().equals("facing")) {
                faces = byFacing.get(String.valueOf(support.getValue(property)).toLowerCase(java.util.Locale.ROOT));
            }
        }
        if (faces == null) {
            faces = byFacing.get("none");
        }
        return faces == null ? Mount.FLAT : faces.getOrDefault(face, Mount.FLAT);
    }

    @Override
    protected void apply(Map<Identifier, Map<String, Map<String, Map<String, List<Integer>>>>> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<Block, Map<String, Map<Direction, Mount>>> read = new HashMap<>();
        files.forEach((file, blocks) -> blocks.forEach((id, byFacing) -> {
            Identifier blockId = Identifier.tryParse(id);
            Block block = blockId == null ? null : BuiltInRegistries.BLOCK.getOptional(blockId).orElse(null);
            if (block == null) {
                return; // an add-on's block, that add-on not here
            }
            Map<String, Map<Direction, Mount>> facings = read.computeIfAbsent(block, b -> new HashMap<>());
            byFacing.forEach((facing, faces) -> {
                Map<Direction, Mount> mounts = facings.computeIfAbsent(facing, f -> new HashMap<>());
                faces.forEach((faceName, numbers) -> {
                    Direction face = Direction.byName(faceName);
                    if (face != null && numbers.size() == 2) {
                        mounts.put(face, new Mount(Math.clamp(numbers.get(0), 0, 7), Math.clamp(numbers.get(1), -3, 3)));
                    }
                });
            });
        }));
        table = read;
    }
}
