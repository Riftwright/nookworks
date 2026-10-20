package dev.nookworks.compat.jade;

import java.util.ArrayList;
import java.util.List;

import dev.nookworks.Nookworks;
import dev.nookworks.client.CameraView;
import dev.nookworks.machine.MachineInfo;
import dev.nookworks.machine.MachineLayout;
import dev.nookworks.machine.MachineStatus;
import dev.nookworks.machine.Readout;
import dev.nookworks.mercury.MercuryMachineBlockEntity;
import dev.nookworks.pearl.LodestoneNames;
import dev.nookworks.recycling.RecyclerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade, when installed: look at one of our blocks and it says what it is doing without being opened - a machine's
 * status and how far along it is, what is in an Iron Pot or a Nukadoko and how long it has to go, what lies on a Grill,
 * a press's oil and turns; a lodestone's name. Energy, fluid tanks and inventories Jade already reads through NeoForge's capabilities; these
 * are the things only we know. Loaded by Jade only: nothing here is touched without it.
 */
@WailaPlugin(Nookworks.MODID)
public final class NookworksJadePlugin implements IWailaPlugin {
    static final Identifier READOUT = JadeConfigs.READOUT; // every switch goes in JadeConfigs.ALL, to be named
    private static final String LINES = "NookworksLines";

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(Server.INSTANCE, BlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(Client.INSTANCE, Block.class);
        // nothing of Jade's in the viewfinder, nor in the photo taken through it (review r12: its panel was in the prints)
        registration.addRayTraceCallback((hit, accessor, original) -> CameraView.looking() ? null : accessor);
        // the Recycler's intake is the body's: looked at, it reads as the body below, where its work and status are
        registration.addRayTraceCallback((hit, accessor, original) -> {
            if (accessor instanceof BlockAccessor block && block.getBlockState().getBlock() instanceof RecyclerBlock
                    && block.getBlockState().getValue(RecyclerBlock.HALF) == DoubleBlockHalf.UPPER) {
                BlockPos below = block.getPosition().below();
                BlockState body = block.getLevel().getBlockState(below);
                if (body.getBlock() instanceof RecyclerBlock) {
                    return registration.blockAccessor().from(block).blockState(body)
                            .blockEntity(() -> block.getLevel().getBlockEntity(below))
                            .hit(block.getHitResult().withPosition(below)).build();
                }
            }
            return accessor;
        });
        // the Creative Generator's endless power is no bar (r15: "9.22EFE / 9.22EFE"); its own line says it
        registration.addTooltipCollectedCallback((box, accessor) -> {
            if (accessor instanceof snownee.jade.api.BlockAccessor block && block.getBlockEntity() instanceof dev.nookworks.machine.CreativeGeneratorBlockEntity) {
                box.getTooltip().remove(snownee.jade.api.JadeIds.UNIVERSAL_ENERGY_STORAGE);
            }
        });
    }

    /** On the server: the lines, as text components. */
    enum Server implements IServerDataProvider<BlockAccessor> {
        INSTANCE;

        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            List<Component> lines = new ArrayList<>();
            BlockEntity entity = accessor.getBlockEntity();
            if (entity instanceof MachineInfo machine) {
                lines.add(status(machine)); // its screen's words: power, tanks and slots Jade shows already
                if (entity instanceof MercuryMachineBlockEntity mercury) {
                    lines.add(mercury.mercuryLine()); // mercury is no fluid capability, so Jade cannot read it itself
                }
            } else if (entity instanceof Readout readout) {
                readout.jade(lines::add);
            }
            if (lines.isEmpty()) {
                return;
            }
            var ops = accessor.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
            ListTag list = new ListTag();
            for (Component line : lines) {
                ComponentSerialization.CODEC.encodeStart(ops, line).result().ifPresent(list::add);
            }
            data.put(LINES, list);
        }

        @Override
        public boolean shouldRequestData(BlockAccessor accessor) {
            BlockEntity entity = accessor.getBlockEntity();
            return entity instanceof Readout || entity instanceof MachineInfo;
        }

        @Override
        public Identifier getUid() {
            return READOUT;
        }
    }

    /** A machine's status in its screen's words and colour, with how far along the work is. */
    static Component status(MachineInfo machine) {
        MachineStatus status = machine.guiStatus();
        int max = machine.guiProgressMax();
        Component line = status == MachineStatus.RUNNING && max > 0 && machine.layout().gauge() == MachineLayout.Gauge.ARROW
                ? Component.translatable("gui.nookworks.machine.running_percent", machine.guiProgress() * 100 / max)
                // an incomplete tower says what it lacks, as its screen's tooltip does
                : status == MachineStatus.INCOMPLETE ? Component.translatable("nookworks.machine.distiller.incomplete")
                : Component.translatable(status.translationKey());
        return line.copy().withColor(switch (status.tone()) {
            case GOOD -> 0x7EDC7E;
            case QUIET -> 0xA3A9B2;
            case WARN -> 0xF0C050;
            case BAD -> 0xFF6A5A;
        });
    }

    /** On the client: the lines the server sent, under the block's name. */
    enum Client implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (accessor.getBlockState().is(Blocks.LODESTONE)) {
                // its name, which the client already has (a Lode Pearl's tooltip names it too): no word from the server
                LodestoneNames.of(GlobalPos.of(accessor.getLevel().dimension(), accessor.getPosition()))
                        .ifPresent(name -> tooltip.add(Component.translatable("tooltip.nookworks.lodestone_named", name)));
            }
            if (!accessor.getServerData().contains(LINES)) {
                return;
            }
            var ops = accessor.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
            for (Tag tag : accessor.getServerData().getListOrEmpty(LINES)) {
                ComponentSerialization.CODEC.parse(ops, tag).result().ifPresent(tooltip::add);
            }
        }

        @Override
        public Identifier getUid() {
            return READOUT;
        }
    }
}
