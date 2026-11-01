package dev.nookworks.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

/**
 * A checkbox alone, drawn with vanilla's checkbox sprites, that can also be set from outside ({@link #set}) - the
 * parts screen's All on and All off - which vanilla's Checkbox cannot. Its label is a widget of its own beside it.
 */
public final class PartCheckbox extends AbstractButton {
    public interface Changed {
        void changed(boolean selected);
    }

    private static final Identifier SELECTED_HIGHLIGHTED = Identifier.withDefaultNamespace("widget/checkbox_selected_highlighted");
    private static final Identifier SELECTED = Identifier.withDefaultNamespace("widget/checkbox_selected");
    private static final Identifier HIGHLIGHTED = Identifier.withDefaultNamespace("widget/checkbox_highlighted");
    private static final Identifier BOX = Identifier.withDefaultNamespace("widget/checkbox");
    /** Vanilla's checkbox: the font's line and 8 px. */
    public static final int SIZE = 17;
    private boolean selected;
    private final Changed changed;

    public PartCheckbox(Component name, boolean selected, Changed changed) {
        super(0, 0, SIZE, SIZE, name);
        this.selected = selected;
        this.changed = changed;
    }

    public boolean selected() {
        return this.selected;
    }

    /** Ticks or clears it without telling anyone: the screen set it so itself. */
    public void set(boolean selected) {
        this.selected = selected;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        this.selected = !this.selected;
        this.changed.changed(this.selected);
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, this.createNarrationMessage());
        if (this.active) {
            output.add(NarratedElementType.USAGE, Component.translatable(this.isFocused()
                    ? this.selected ? "narration.checkbox.usage.focused.uncheck" : "narration.checkbox.usage.focused.check"
                    : this.selected ? "narration.checkbox.usage.hovered.uncheck" : "narration.checkbox.usage.hovered.check"));
        }
    }

    @Override
    public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        boolean lit = this.isFocused() || this.isHovered();
        Identifier sprite = this.selected ? lit ? SELECTED_HIGHLIGHTED : SELECTED : lit ? HIGHLIGHTED : BOX;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, this.getX(), this.getY(), SIZE, SIZE,
                ARGB.white(this.active ? this.alpha : this.alpha * 0.5F));
    }
}
