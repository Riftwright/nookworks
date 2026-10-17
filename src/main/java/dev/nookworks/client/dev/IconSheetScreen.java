package dev.nookworks.client.dev;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import dev.nookworks.Nookworks;
import dev.nookworks.registry.ModCreativeTabs;
import dev.nookworks.registry.ModDataComponents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;

/**
 * Development only (agentClient; this package stays out of the jar): every item of the mod's creative tab drawn 64 px
 * wide on a flat key colour, a grid of 16 x 9 cells of 80 px a page, for tools/make_guide.py to cut into icons for the
 * guide site. Each open writes {@code run-agent/icon_sheet.txt}: one line per cell of the page, its item's id and the
 * components that tell it from its neighbours (a poster's design, a label's picture), so no cell is guessed.
 */
public final class IconSheetScreen extends Screen {
    public static final int CELL = 80;
    public static final int COLUMNS = 16;
    public static final int ROWS = 9;
    /**
     * The flat colour behind the icons: magenta, or green for a second sheet - from the two, make_guide.py works out
     * each pixel's own colour and opacity, so glass and bottles come out see-through rather than tinted.
     */
    public static final int KEY = 0xFFFF00FF;
    public static final int KEY_GREEN = 0xFF00FF00;
    private final int key;

    private final int page;
    /** Draw the items listed in run-agent/icon_list.txt (one id a line) instead of the creative tab's. */
    private final boolean fromList;
    private final List<ItemStack> items = new ArrayList<>();
    /** One item alone, this many window pixels wide at the top left, for a project logo; 0 for a sheet. */
    private final int single;

    public IconSheetScreen(int page, boolean fromList, boolean green) {
        this(page, fromList, green, 0);
    }

    private IconSheetScreen(int page, boolean fromList, boolean green, int single) {
        super(Component.literal("icon sheet"));
        this.page = page;
        this.fromList = fromList;
        this.key = green ? KEY_GREEN : KEY;
        this.single = single;
    }

    /** {@code stack} alone, {@code size} window pixels wide, on the key colour (tools/make_logo.py). */
    public static IconSheetScreen single(ItemStack stack, int size, boolean green) {
        IconSheetScreen screen = new IconSheetScreen(0, false, green, size);
        screen.items.add(stack);
        return screen;
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || this.single > 0) {
            return;
        }
        CreativeModeTabs.tryRebuildTabContents(mc.level.enabledFeatures(), true, mc.level.registryAccess());
        List<ItemStack> all = new ArrayList<>();
        if (this.fromList) {
            try {
                for (String line : Files.readAllLines(Path.of("icon_list.txt"), StandardCharsets.UTF_8)) {
                    var id = Identifier.tryParse(line.trim());
                    if (id != null) {
                        BuiltInRegistries.ITEM.get(id).ifPresent(item -> all.add(new ItemStack(item)));
                    }
                }
            } catch (IOException e) {
                Nookworks.LOGGER.warn("[agent-input] could not read icon_list.txt", e);
            }
        } else {
            ModCreativeTabs.all().forEach(tab -> all.addAll(tab.get().getDisplayItems()));
        }
        int from = this.page * COLUMNS * ROWS;
        this.items.clear();
        this.items.addAll(all.subList(Math.min(from, all.size()), Math.min(from + COLUMNS * ROWS, all.size())));
        StringBuilder out = new StringBuilder("page ").append(this.page).append(" of ").append((all.size() + COLUMNS * ROWS - 1) / (COLUMNS * ROWS))
                .append(" total ").append(all.size()).append('\n');
        for (ItemStack stack : this.items) {
            String extra = "";
            var poster = stack.get(ModDataComponents.POSTER.get());
            if (poster != null) {
                extra = " poster=" + poster;
            }
            var label = stack.get(ModDataComponents.LABEL.get());
            if (label != null) {
                extra = " label=" + label.getRegisteredName();
            }
            out.append(stack.typeHolder().getRegisteredName()).append(extra).append('\n');
        }
        try {
            Files.writeString(Path.of("icon_sheet.txt"), out.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Nookworks.LOGGER.warn("[agent-input] could not write icon_sheet.txt", e);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.fill(0, 0, this.width, this.height, this.key);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        this.extractBackground(graphics, mouseX, mouseY, a);
        // Cells are measured in window pixels: the GUI scale is undone, so the sheet is the same at any setting.
        float gui = (float) Minecraft.getInstance().getWindow().getGuiScale();
        if (this.single > 0) {
            graphics.pose().pushMatrix();
            graphics.pose().scale(this.single / 16.0F / gui, this.single / 16.0F / gui);
            graphics.fakeItem(this.items.get(0), 0, 0);
            graphics.pose().popMatrix();
            return;
        }
        float scale = 4.0F / gui;
        for (int i = 0; i < this.items.size(); i++) {
            float x = (i % COLUMNS) * CELL / gui + 8 / gui;
            float y = (i / COLUMNS) * CELL / gui + 8 / gui;
            graphics.pose().pushMatrix();
            graphics.pose().translate(x, y);
            graphics.pose().scale(scale, scale);
            graphics.fakeItem(this.items.get(i), 0, 0);
            graphics.pose().popMatrix();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
