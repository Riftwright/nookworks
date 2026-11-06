package dev.nookworks.client.gui;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import dev.nookworks.feature.AddonImpact;
import dev.nookworks.ifworld.NookOfTheDeadWarning;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.ScrollableLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Asked before a change of parts is saved that takes more with it than the parts switched off (the user, 2026-11-01:
 * 「「これをオフにしたら、これとコレが作れなくなるよ」って警告を出したうえで、オフにすること自体はできる」): every item nothing reaches
 * any more, each with its picture and name, under a line saying so - and, with Nook of the Dead installed and the If's
 * mirror among them, what that costs - and other mods' recipes it stops, counted by mod ({@link AddonImpact}: with the
 * Motor off most of nookTechnology goes; the user, 2026-11-03). Switch off anyway, or go back to the list.
 */
public final class PartsLossScreen extends Screen {
    private static final int WIDTH = 310;
    private static final int ROW_HEIGHT = 18;
    private static final int SCROLL_MIN_HEIGHT = 80;

    private final Screen parent;
    private final List<ItemStack> lost;
    private final boolean notd;
    private final Map<String, Integer> addons;
    private final Runnable confirmed;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    private ScrollableLayout scroll;
    private LinearLayout content;

    /**
     * Over {@code parent}: {@code lost}, by id, would be had no more, and {@code addons} that many of each mod's recipes
     * stopped; {@code confirmed} saves the change.
     */
    public PartsLossScreen(Screen parent, List<String> lost, Map<String, Integer> addons, Runnable confirmed) {
        super(Component.translatable("nookworks.world_features.loss.title"));
        this.parent = parent;
        this.lost = lost.stream()
                .map(id -> new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(id))))
                .filter(stack -> !stack.isEmpty())
                .sorted(Comparator.comparing(stack -> stack.getHoverName().getString()))
                .toList();
        this.notd = NookOfTheDeadWarning.present() && lost.contains(NookOfTheDeadWarning.MIRROR);
        this.addons = addons;
        this.confirmed = confirmed;
    }

    @Override
    protected void init() {
        this.layout.addTitleHeader(this.title, this.font);
        LinearLayout content = this.layout.addToContents(LinearLayout.vertical().spacing(6));
        this.content = content;
        if (!this.addons.isEmpty()) {
            content.addChild(new MultiLineTextWidget(Component.translatable("nookworks.world_features.loss.addons"), this.font)
                    .setMaxWidth(WIDTH));
            this.addons.forEach((mod, count) -> content.addChild(new MultiLineTextWidget(
                    Component.translatable("nookworks.world_features.loss.addon", AddonImpact.name(mod), count).withStyle(ChatFormatting.GOLD),
                    this.font).setMaxWidth(WIDTH)));
        }
        if (!this.lost.isEmpty()) {
            content.addChild(new MultiLineTextWidget(Component.translatable("nookworks.world_features.loss.message", this.lost.size()), this.font)
                    .setMaxWidth(WIDTH));
        }
        if (this.notd) {
            content.addChild(new MultiLineTextWidget(Component.translatable("nookworks.world_features.loss.notd").withStyle(ChatFormatting.GOLD),
                    this.font).setMaxWidth(WIDTH));
        }
        LinearLayout rows = LinearLayout.vertical();
        for (ItemStack stack : this.lost) {
            rows.addChild(new Row(stack));
        }
        rows.arrangeElements();
        this.scroll = new ScrollableLayout(this.minecraft, rows, SCROLL_MIN_HEIGHT);
        this.scroll.setMinWidth(WIDTH);
        content.addChild(this.scroll);
        LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
        footer.addChild(Button.builder(Component.translatable("nookworks.world_features.loss.confirm"), b -> this.confirmed.run())
                .width(150).build());
        footer.addChild(Button.builder(Component.translatable("nookworks.world_features.loss.back"), b -> this.onClose())
                .width(150).build());
        this.layout.visitWidgets(this::addRenderableWidget);
        this.repositionElements();
    }

    @Override
    protected void repositionElements() {
        if (this.scroll == null) {
            return;
        }
        // the list as tall as the room between the title and the buttons leaves, as the parts list's is
        this.scroll.setMaxHeight(SCROLL_MIN_HEIGHT);
        this.layout.arrangeElements();
        int over = this.content.getHeight() - this.scroll.getHeight();
        int room = this.height - this.layout.getHeaderHeight() - this.layout.getFooterHeight() - over - 8;
        this.scroll.setMaxHeight(Math.max(40, room));
        this.layout.arrangeElements();
    }

    /** Back to the parts list, the change not saved. */
    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }

    /** One item that would be lost: its picture and its name. */
    private final class Row extends AbstractWidget {
        private final ItemStack stack;

        Row(ItemStack stack) {
            super(0, 0, WIDTH - 12, ROW_HEIGHT, stack.getHoverName());
            this.stack = stack;
            this.active = false;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
            g.fakeItem(this.stack, this.getX(), this.getY() + 1);
            g.text(PartsLossScreen.this.font, this.getMessage(), this.getX() + 20, this.getY() + 5, 0xFFFFFFFF, false);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, this.getMessage());
        }
    }
}
