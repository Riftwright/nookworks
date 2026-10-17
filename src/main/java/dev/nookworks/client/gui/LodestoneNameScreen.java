package dev.nookworks.client.gui;

import dev.nookworks.NookworksNetwork;
import dev.nookworks.pearl.LodestoneNames;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.CommonComponents;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The box a lodestone is renamed in ({@link LodestoneNames}), opened by an empty-handed click on it: its name as it is,
 * all of it chosen, so typing replaces it; Done (or Enter) sends it, Cancel (or Escape) leaves it. An empty box gives it
 * back a name of its own.
 */
public final class LodestoneNameScreen extends Screen {
    private final GlobalPos lodestone;
    private EditBox name;

    public LodestoneNameScreen(GlobalPos lodestone) {
        super(Component.translatable("gui.nookworks.lodestone_name.title"));
        this.lodestone = lodestone;
    }

    @Override
    protected void init() {
        int x = this.width / 2 - 100;
        int y = this.height / 2 - 20;
        EditBox before = this.name; // (built again when the window or the GUI scale changes: what was typed stays)
        this.name = new EditBox(this.font, x, y, 200, 20, this.title);
        this.name.setMaxLength(LodestoneNames.MAX_LENGTH);
        this.name.setValue(before != null ? before.getValue() : LodestoneNames.nameText(this.lodestone));
        this.name.moveCursorToEnd(false);
        if (before == null) {
            this.name.setHighlightPos(0); // all of it chosen: typing replaces it, an arrow key keeps it to edit
        }
        this.name.setHint(Component.translatable("gui.nookworks.lodestone_name.hint"));
        this.addRenderableWidget(this.name);
        this.setInitialFocus(this.name);
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.done()).bounds(x, y + 30, 98, 20).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> this.onClose()).bounds(x + 102, y + 30, 98, 20).build());
    }

    private void done() {
        ClientPacketDistributor.sendToServer(new NookworksNetwork.RenameLodestone(this.lodestone.pos(), this.name.getValue()));
        this.onClose();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isConfirmation() && this.name.isFocused()) {
            this.done();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        graphics.centeredText(this.font, this.title, this.width / 2, this.height / 2 - 40, 0xFFFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
