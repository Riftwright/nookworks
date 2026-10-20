package dev.nookworks.client.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import dev.nookworks.feature.Features;
import dev.nookworks.feature.WorldFeatures;
import dev.nookworks.ifworld.NookOfTheDeadWarning;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.ScrollableLayout;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.layouts.SpacerElement;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Which parts of the mod a new world has ({@link WorldFeatures}), opened from the Create World screen's More tab: a
 * switch a part, with its name and a line on what it is, laid out as vanilla's Experiments screen lays out its packs. A
 * part the start-up config switches off is shown off and cannot be switched on (the modpack's say); one that goes with
 * another - the Barter Terminal with the Mob Bottle - is off while that is. Done with a change loads the world's data
 * packs again, as a change of data packs does, so its recipes and advancements are the chosen ones.
 */
public final class WorldFeaturesScreen extends Screen {
    /** The Create World screen, which loads its data packs again for a new choice ({@code CreateWorldScreenMixin}). */
    public interface Host {
        /** Loads the data packs again; {@code failed} if they could not be (the screen it returns to is seen to). */
        void nookworks$reloadDataPacks(Runnable failed);
    }

    private static final Component INFO = Component.translatable("nookworks.world_features.info");
    private static final int WIDTH = 310;
    private static final int GRID_WIDTH = 299; // room for the scroll bar, as vanilla's Experiments screen leaves
    private static final int SWITCH_WIDTH = 44;
    private static final int SCROLL_MIN_HEIGHT = 130;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    private final CreateWorldScreen parent;
    /** What the world had when the screen opened, and what is switched here (a locked part as it was). */
    private final Map<String, Boolean> before;
    private final Map<String, Boolean> chosen;
    private final List<Runnable> refresh = new ArrayList<>();
    private @Nullable ScrollableLayout scroll;

    public WorldFeaturesScreen(CreateWorldScreen parent) {
        super(Component.translatable("nookworks.world_features.title"));
        this.parent = parent;
        this.before = WorldFeatures.choosing();
        this.chosen = new LinkedHashMap<>(this.before);
    }

    @Override
    protected void init() {
        this.refresh.clear();
        this.layout.addTitleHeader(this.title, this.font);
        LinearLayout content = this.layout.addToContents(LinearLayout.vertical());
        content.addChild(new MultiLineTextWidget(INFO, this.font).setMaxWidth(WIDTH), settings -> settings.paddingBottom(10));
        GridLayout grid = new GridLayout().rowSpacing(4);
        grid.addChild(SpacerElement.width(GRID_WIDTH - SWITCH_WIDTH), 0, 0);
        grid.addChild(SpacerElement.width(SWITCH_WIDTH), 0, 1);
        int row = 1;
        for (String part : Features.PARTS.keySet()) {
            row = this.addSwitch(grid, row, part);
        }
        grid.arrangeElements();
        this.scroll = new ScrollableLayout(this.minecraft, grid, SCROLL_MIN_HEIGHT);
        this.scroll.setMinWidth(WIDTH);
        content.addChild(this.scroll);
        LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
        footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> this.done()).build());
        footer.addChild(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose()).build());
        this.layout.visitWidgets(this::addRenderableWidget);
        this.refresh.forEach(Runnable::run);
        this.repositionElements();
    }

    /** A part's name and switch on row {@code row}, the line on what it is under them; the row after it. */
    private int addSwitch(GridLayout grid, int row, String part) {
        Component name = Component.translatable("nookworks.configuration.features." + part);
        Component info = Component.translatable("nookworks.world_features." + part);
        StringWidget label = new StringWidget(name, this.font);
        grid.addChild(label, row, 0, grid.newCellSettings().align(0.0F, 0.5F));
        CycleButton<Boolean> button = CycleButton.onOffBuilder(this.shownOn(part))
                .displayOnlyValue()
                .withTooltip(value -> Tooltip.create(this.tooltip(part)))
                .withCustomNarration(b -> CommonComponents.joinForNarration(name, b.createDefaultNarrationMessage(), info))
                .create(0, 0, SWITCH_WIDTH, 20, name, (b, value) -> {
                    this.chosen.put(part, value);
                    this.refresh.forEach(Runnable::run); // a part that goes with this one follows it
                });
        grid.addChild(button, row, 1, grid.newCellSettings().alignHorizontallyRight());
        MultiLineTextWidget line = new MultiLineTextWidget(info.copy().withStyle(ChatFormatting.GRAY), this.font)
                .setMaxWidth(GRID_WIDTH - SWITCH_WIDTH).setMaxRows(2);
        grid.addChild(line, row + 1, 0, grid.newCellSettings().paddingTop(-4).paddingBottom(18 - line.getHeight()));
        this.refresh.add(() -> {
            boolean free = this.free(part);
            button.setValue(this.shownOn(part));
            button.active = free;
            label.setMessage(free ? name : name.copy().withStyle(ChatFormatting.GRAY));
        });
        return row + 2;
    }

    /** Whether part {@code part} may be switched here: the start-up config leaves it on, and the parts it needs are on. */
    private boolean free(String part) {
        return WorldFeatures.offered(part) && this.needsOn(part);
    }

    private boolean needsOn(String part) {
        return Features.NEEDS.getOrDefault(part, List.of()).stream().allMatch(need -> Features.enabled(need, this::on));
    }

    /** Whether part {@code part} would be on in the world, its own switch alone. */
    private boolean on(String part) {
        return WorldFeatures.offered(part) && this.chosen.getOrDefault(part, true);
    }

    private boolean shownOn(String part) {
        return this.on(part) && this.needsOn(part);
    }

    private Component tooltip(String part) {
        if (!WorldFeatures.offered(part)) {
            Component locked = Component.translatable("nookworks.world_features.locked");
            // the If locked off by the start-up config, with Nook of the Dead installed: say what it costs
            return part.equals(NookOfTheDeadWarning.PART) && NookOfTheDeadWarning.present()
                    ? locked.copy().append("\n").append(Component.translatable("message.nookworks.notd_if_off"))
                    : locked;
        }
        List<String> off = Features.NEEDS.getOrDefault(part, List.of()).stream()
                .filter(need -> !Features.enabled(need, this::on)).toList();
        if (!off.isEmpty()) {
            MutableComponent names = Component.translatable("nookworks.configuration.features." + off.getFirst());
            for (int i = 1; i < off.size(); i++) {
                names.append(Component.translatable("nookworks.list.separator"))
                        .append(Component.translatable("nookworks.configuration.features." + off.get(i)));
            }
            return Component.translatable("nookworks.world_features.needs", names);
        }
        return Component.translatable("nookworks.configuration.features." + part + ".tooltip");
    }

    @Override
    protected void repositionElements() {
        if (this.scroll == null) {
            return;
        }
        // as tall as the screen leaves room for, as vanilla's Experiments screen does
        this.scroll.setMaxHeight(SCROLL_MIN_HEIGHT);
        this.layout.arrangeElements();
        int room = this.height - this.layout.getFooterHeight() - this.scroll.getRectangle().bottom();
        this.scroll.setMaxHeight(this.scroll.getHeight() + room);
        this.layout.arrangeElements();
    }

    /**
     * With a change, the world's data packs load again with it; back on the Create World screen either way. With Nook of
     * the Dead installed, switching the If off is asked first: that add-on reaches its world through the If.
     */
    private void done() {
        if (this.chosen.equals(this.before)) {
            this.onClose();
            return;
        }
        if (NookOfTheDeadWarning.present() && !this.shownOn(NookOfTheDeadWarning.PART)
                && WorldFeatures.offered(NookOfTheDeadWarning.PART) && this.before.getOrDefault(NookOfTheDeadWarning.PART, true)) {
            this.minecraft.gui.setScreen(new ConfirmScreen(yes -> {
                if (yes) {
                    this.apply();
                } else {
                    this.minecraft.gui.setScreen(this);
                }
            }, Component.translatable("nookworks.world_features.notd.title"), Component.translatable("nookworks.world_features.notd.message")));
            return;
        }
        this.apply();
    }

    private void apply() {
        WorldFeatures.Choice was = WorldFeatures.current();
        WorldFeatures.choose(this.chosen);
        ((Host) this.parent).nookworks$reloadDataPacks(() -> WorldFeatures.restore(was));
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }
}
