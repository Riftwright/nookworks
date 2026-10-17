package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.nookworks.Nookworks;
import dev.nookworks.block.MachineBlock;
import dev.nookworks.block.MotorBlock;
import dev.nookworks.machine.MachineBlockEntity;
import dev.nookworks.registry.ModSounds;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * The mod's sounds: each defined, subtitled in every language, playing a vanilla sound that is there; and the machines'
 * working sounds heard while they work, not while they stand.
 */
final class SoundTests {
    static void register() {
        NookworksGameTests.test("sounds_are_whole", SoundTests::soundsAreWhole);
        NookworksGameTests.test("machines_sound_only_while_they_work", SoundTests::machinesSoundWhileTheyWork);
    }

    /**
     * Every sound event of ours is in sounds.json, and nothing else is; its subtitle is written in every language (a
     * missing one shows its key in the subtitles); a vanilla event it plays exists (a wrong name plays nothing at all).
     */
    private static void soundsAreWhole(GameTestHelper helper) {
        JsonObject sounds = read("/assets/" + Nookworks.MODID + "/sounds.json");
        List<Set<String>> languages = DataTests.ALL_LANGUAGES.stream().map(DataTests::lang).toList();
        var registered = ModSounds.SOUND_EVENTS.getEntries();
        for (var holder : registered) {
            String name = holder.getId().getPath();
            check(helper, sounds.has(name), name + " is in sounds.json");
            JsonObject entry = sounds.getAsJsonObject(name);
            String subtitle = entry.has("subtitle") ? entry.get("subtitle").getAsString() : "";
            check(helper, !subtitle.isEmpty() && languages.stream().allMatch(lang -> lang.contains(subtitle)),
                    name + " has a subtitle every language writes: " + subtitle);
            for (JsonElement element : entry.getAsJsonArray("sounds")) {
                JsonObject sound = element.getAsJsonObject();
                if (sound.has("type") && sound.get("type").getAsString().equals("event")) {
                    Identifier event = Identifier.parse(sound.get("name").getAsString());
                    check(helper, BuiltInRegistries.SOUND_EVENT.containsKey(event), name + " plays " + event + ", which is there");
                } else {
                    // a file of ours: there (a missing one plays nothing), and mono - a stereo sound is not placed where
                    // it plays but heard everywhere at full volume
                    Identifier file = Identifier.parse(sound.get("name").getAsString());
                    int channels = oggChannels("/assets/" + file.getNamespace() + "/sounds/" + file.getPath() + ".ogg");
                    check(helper, channels == 1, name + "'s file " + file + " is there and mono, has " + channels + " channels");
                }
            }
        }
        check(helper, sounds.size() == registered.size(), "sounds.json defines nothing that is not registered: "
                + sounds.size() + " entries, " + registered.size() + " sounds");
        helper.succeed();
    }

    /** Every machine that shows it is working (its lamp, its fire) has a sound for it, and none while it stands. */
    private static void machinesSoundWhileTheyWork(GameTestHelper helper) {
        int checked = 0;
        for (var block : BuiltInRegistries.BLOCK) {
            if (!(block instanceof MachineBlock machine) || !BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals(Nookworks.MODID)) {
                continue;
            }
            BlockState state = block.defaultBlockState();
            BooleanProperty working = block instanceof MotorBlock ? MotorBlock.POWERED // its coil glows while it runs
                    : state.hasProperty(MachineBlockEntity.RUNNING) ? MachineBlockEntity.RUNNING
                    : state.hasProperty(BlockStateProperties.LIT) ? BlockStateProperties.LIT : null;
            if (working == null) {
                continue; // nothing shows it working: it says nothing either
            }
            check(helper, machine.workSound(state.setValue(working, false)) == null, block + " is quiet while it stands");
            check(helper, machine.workSound(state.setValue(working, true)) != null, block + " sounds while it works");
            checked++;
        }
        check(helper, checked >= 11, "the machines with a lamp or a fire, " + checked + " of them");
        helper.succeed();
    }

    /** The channels an Ogg Vorbis file has, from its identification header; -1 for no such file. */
    private static int oggChannels(String path) {
        try (var in = Nookworks.class.getResourceAsStream(path)) {
            if (in == null) {
                return -1;
            }
            byte[] head = in.readNBytes(128);
            for (int i = 0; i + 11 < head.length; i++) {
                if (head[i] == 1 && head[i + 1] == 'v' && head[i + 2] == 'o' && head[i + 3] == 'r' && head[i + 4] == 'b'
                        && head[i + 5] == 'i' && head[i + 6] == 's') {
                    return head[i + 11]; // after the type, "vorbis" and four bytes of version
                }
            }
            return 0;
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + path, e);
        }
    }

    private static JsonObject read(String path) {
        try (var in = Nookworks.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("no " + path);
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + path, e);
        }
    }

    private SoundTests() {}
}
