package dev.nookworks.client.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import dev.nookworks.feature.Features;
import dev.nookworks.feature.WorldFeatures;
import dev.nookworks.feature.PartGraph;
import dev.nookworks.feature.PartsChange;
import dev.nookworks.feature.WorldPartsEditing;
import dev.nookworks.ifworld.NookOfTheDeadWarning;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
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
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Which parts of the mod a world has ({@link WorldFeatures}): a checkbox a part, with its name and a line on what it
 * is, laid out as vanilla's Experiments screen lays out its packs, and All on / All off over them (the user, 2026-10-26).
 * The parts stand in groups, each under its heading with a box that switches all of it (the user, 2026-11-01).
 * A part the start-up config switches off is shown off and cannot be switched on (the modpack's say); one that goes
 * with another - the Barter Terminal with the Mob Bottle - is off while that is, and so is one none of whose items
 * anything reaches any more (the Printing Press and its posters, with the Electrolytic Cell off: {@link PartGraph}).
 * Done with a change that takes more than the parts switched off asks first, naming every item it takes
 * ({@link PartsLossScreen}; the user, 2026-11-01: 「「これをオフにしたら、これとコレが作れなくなるよ」って警告を出したうえで、オフに
 * すること自体はできる」).
 * <p>
 * Opened from the Create World screen's More tab, Done with a change loads the world's data packs again, as a change of
 * data packs does, so its recipes and advancements are the chosen ones. Opened in a world from the mod's settings
 * (the user, 2026-10-31), Done sends the choice to the server, which writes it into the world's file for the next time
 * the world opens; one who may not change it (not an operator, on a server) sees it without the checkboxes working.
 */
public final class WorldFeaturesScreen extends Screen {
    /** The Create World screen, which loads its data packs again for a new choice ({@code CreateWorldScreenMixin}). */
    public interface Host {
        /** Loads the data packs again; {@code failed} if they could not be (the screen it returns to is seen to). */
        void nookworks$reloadDataPacks(Runnable failed);
    }

    private static final Component INFO = Component.translatable("nookworks.world_features.info");
    private static final Component INFO_IN_GAME = Component.translatable("nookworks.world_features.info_in_game");
    private static final Component VIEW_ONLY = Component.translatable("nookworks.world_features.view_only");
    private static final Component PACK_SETS = Component.translatable("nookworks.settings.world_parts.off");
    private static final int WIDTH = 310;
    private static final int GRID_WIDTH = 299; // room for the scroll bar, as vanilla's Experiments screen leaves
    private static final int BOX_WIDTH = PartCheckbox.SIZE + 6;
    private static final int SCROLL_MIN_HEIGHT = 130;
    private static final int ALL_WIDTH = 100;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    private final Screen parent;
    /** What the world had when the screen opened, and what is switched here (a locked part as it was). */
    private final Map<String, Boolean> before;
    private final Map<String, Boolean> chosen;
    /** What Done does with a changed choice: loads the new world's packs, or sends it to the server. */
    private final Consumer<Map<String, Boolean>> apply;
    /** Opened in a world (not on the Create World screen); and whether the one looking may change it there. */
    private final boolean inGame;
    private final boolean editable;
    /** Which parts the start-up config lets a world have: this game's on the Create World screen, the server's in a world. */
    private final Predicate<String> offered;
    /** Whether that config lets worlds choose at all. */
    private final boolean menu;
    private final List<Runnable> refresh = new ArrayList<>();
    /** What nothing reaches any more with the parts as switched here, worked out again at each change. */
    private Set<String> lost = Set.of();
    /**
     * The parts left with nothing that can be had, whichever way their own box is: one switched off is asked as if it
     * were on, so a box that cannot take is never one to tick and see go blank (the re-check of a91bf6aa).
     */
    private Set<String> taken = Set.of();
    private @Nullable ScrollableLayout scroll;
    private @Nullable LinearLayout content;

    /** On the Create World screen {@code parent}, for the world being made. */
    public static WorldFeaturesScreen creating(Screen parent) {
        return new WorldFeaturesScreen(parent, WorldFeatures.choosing(), false, true, WorldFeatures::offered, true, chosen -> {
            WorldFeatures.Choice was = WorldFeatures.current();
            WorldFeatures.choose(chosen);
            ((Host) parent).nookworks$reloadDataPacks(() -> WorldFeatures.restore(was));
        });
    }

    /**
     * In a world, from the mod's settings: what its server shows, {@code view} - its file's choice, sent back by {@code
     * apply} on Done, and what the server's start-up config allows, whatever this game's own says (r19).
     */
    public static WorldFeaturesScreen playing(Screen parent, WorldPartsEditing.View view, Consumer<Map<String, Boolean>> apply) {
        return new WorldFeaturesScreen(parent, view.parts(), true, view.editable() && view.menu(),
                part -> view.offered().getOrDefault(part, true), view.menu(), chosen -> {
            apply.accept(chosen);
            Minecraft.getInstance().gui.setScreen(parent);
        });
    }

    private WorldFeaturesScreen(Screen parent, Map<String, Boolean> parts, boolean inGame, boolean editable,
            Predicate<String> offered, boolean menu, Consumer<Map<String, Boolean>> apply) {
        super(Component.translatable("nookworks.world_features.title"));
        this.parent = parent;
        this.before = new LinkedHashMap<>();
        Features.PARTS.keySet().forEach(part -> this.before.put(part, parts.getOrDefault(part, true)));
        this.chosen = new LinkedHashMap<>(this.before);
        this.inGame = inGame;
        this.editable = editable;
        this.offered = offered;
        this.menu = menu;
        this.apply = apply;
    }

    @Override
    protected void init() {
        this.refresh.clear();
        this.refresh.add(this::workOut);
        this.layout.addTitleHeader(this.title, this.font);
        LinearLayout content = this.layout.addToContents(LinearLayout.vertical());
        this.content = content;
        Component info = !this.inGame ? INFO : !this.menu ? PACK_SETS : this.editable ? INFO_IN_GAME : VIEW_ONLY;
        content.addChild(new MultiLineTextWidget(info, this.font).setMaxWidth(WIDTH), settings -> settings.paddingBottom(6));
        LinearLayout all = content.addChild(LinearLayout.horizontal().spacing(8), settings -> settings.paddingBottom(6));
        Button allOn = all.addChild(Button.builder(Component.translatable("nookworks.world_features.all_on"), b -> this.setAll(true))
                .width(ALL_WIDTH).build());
        Button allOff = all.addChild(Button.builder(Component.translatable("nookworks.world_features.all_off"), b -> this.setAll(false))
                .width(ALL_WIDTH).build());
        allOn.active = this.editable;
        allOff.active = this.editable;
        GridLayout grid = new GridLayout().rowSpacing(4);
        grid.addChild(SpacerElement.width(BOX_WIDTH), 0, 0);
        grid.addChild(SpacerElement.width(GRID_WIDTH - BOX_WIDTH), 0, 1);
        int row = 1;
        for (Map.Entry<String, List<String>> group : Features.GROUPS.entrySet()) {
            row = this.addGroup(grid, row, group.getKey(), group.getValue());
            for (String part : group.getValue()) {
                row = this.addSwitch(grid, row, part);
            }
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

    /** A group's heading on row {@code row}, with a box that switches every part of it that may be; the row after it. */
    private int addGroup(GridLayout grid, int row, String group, List<String> parts) {
        Component name = Component.translatable("nookworks.world_features.group." + group).withStyle(ChatFormatting.BOLD);
        PartCheckbox box = new PartCheckbox(name, this.allChosen(parts), value -> {
            parts.stream().filter(this::free).forEach(part -> this.chosen.put(part, value));
            this.refresh.forEach(Runnable::run);
        });
        int above = row == 1 ? 0 : 8;
        grid.addChild(box, row, 0, grid.newCellSettings().align(0.0F, 0.5F).paddingTop(above));
        grid.addChild(new StringWidget(name, this.font), row, 1, grid.newCellSettings().align(0.0F, 0.5F).paddingTop(above));
        this.refresh.add(() -> {
            box.set(this.allChosen(parts));
            box.active = this.editable && parts.stream().anyMatch(this::free);
            box.setTooltip(Tooltip.create(Component.translatable("nookworks.world_features.group.tooltip")));
        });
        return row + 1;
    }

    /** Whether every part of {@code parts} that may be switched is switched on here. */
    private boolean allChosen(List<String> parts) {
        return parts.stream().filter(this::free).allMatch(part -> this.chosen.getOrDefault(part, true));
    }

    /** What nothing reaches with the parts as switched, and which parts that leaves with nothing. */
    private void workOut() {
        List<String> kept = WorldFeatures.kept();
        this.lost = PartGraph.lost(this::on, kept);
        Set<String> taken = new java.util.HashSet<>();
        for (String part : Features.PARTS.keySet()) {
            if (this.on(part)) {
                if (PartsChange.taken(part, this::on, this.lost)) {
                    taken.add(part);
                }
            } else if (this.offered.test(part)) {
                Predicate<String> withIt = other -> other.equals(part) || this.on(other);
                if (PartsChange.taken(part, withIt, PartGraph.lost(withIt, kept))) {
                    taken.add(part);
                }
            }
        }
        this.taken = taken;
    }

    /** A part's checkbox and name on row {@code row}, the line on what it is under the name; the row after it. */
    private int addSwitch(GridLayout grid, int row, String part) {
        Component name = Component.translatable("nookworks.configuration.features." + part);
        Component info = Component.translatable("nookworks.world_features." + part);
        PartCheckbox box = new PartCheckbox(CommonComponents.joinForNarration(name, info), this.shownOn(part), value -> {
            this.chosen.put(part, value);
            this.refresh.forEach(Runnable::run); // a part that goes with this one follows it
        });
        grid.addChild(box, row, 0, grid.newCellSettings().align(0.0F, 0.5F));
        StringWidget label = new StringWidget(name, this.font);
        grid.addChild(label, row, 1, grid.newCellSettings().align(0.0F, 0.5F));
        MultiLineTextWidget line = new MultiLineTextWidget(info.copy().withStyle(ChatFormatting.GRAY), this.font)
                .setMaxWidth(GRID_WIDTH - BOX_WIDTH).setMaxRows(2);
        grid.addChild(line, row + 1, 1, grid.newCellSettings().paddingTop(-4).paddingBottom(18 - line.getHeight()));
        this.refresh.add(() -> {
            boolean free = this.free(part);
            box.set(this.shownOn(part));
            box.active = free && this.editable;
            box.setTooltip(Tooltip.create(this.tooltip(part)));
            label.setMessage(free ? name : name.copy().withStyle(ChatFormatting.GRAY));
        });
        return row + 2;
    }

    /** All on or All off: every part that may be switched here (one the start-up config switches off stays off). */
    private void setAll(boolean on) {
        Features.PARTS.keySet().stream().filter(this.offered).forEach(part -> this.chosen.put(part, on));
        this.refresh.forEach(Runnable::run);
    }

    /**
     * Whether part {@code part} may be switched here: the start-up config leaves it on, the parts it needs are on, and
     * something still reaches its items.
     */
    private boolean free(String part) {
        return this.offered.test(part) && this.needsOn(part) && !this.taken(part);
    }

    /** Whether nothing reaches any item of part {@code part} with the others as switched here. */
    private boolean taken(String part) {
        return this.taken.contains(part);
    }

    /** Whether part {@code part} was on when the screen opened, its own switch alone. */
    private boolean wasOn(String part) {
        return this.offered.test(part) && this.before.getOrDefault(part, true);
    }

    private boolean needsOn(String part) {
        return Features.NEEDS.getOrDefault(part, List.of()).stream().allMatch(need -> Features.enabled(need, this::on));
    }

    /** Whether part {@code part} would be on in the world, its own switch alone. */
    private boolean on(String part) {
        return this.offered.test(part) && this.chosen.getOrDefault(part, true);
    }

    private boolean shownOn(String part) {
        return this.on(part) && this.needsOn(part) && !this.taken(part);
    }

    private Component tooltip(String part) {
        if (!this.offered.test(part)) {
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
        if (this.taken(part)) {
            return Component.translatable("nookworks.world_features.taken");
        }
        return Component.translatable("nookworks.configuration.features." + part + ".tooltip");
    }

    @Override
    protected void repositionElements() {
        if (this.scroll == null) {
            return;
        }
        // as tall as the room between the title and the buttons leaves after the lines over the list, as vanilla's
        // Experiments screen does - worked out from that room, not from where the list ends: the contents are centred, so
        // growing the list by the room under it pushed the lines over it up into the title
        this.scroll.setMaxHeight(SCROLL_MIN_HEIGHT);
        this.layout.arrangeElements();
        int over = this.content == null ? 0 : this.content.getHeight() - this.scroll.getHeight();
        int room = this.height - this.layout.getHeaderHeight() - this.layout.getFooterHeight() - over - 8;
        this.scroll.setMaxHeight(Math.max(40, room));
        this.layout.arrangeElements();
    }

    /**
     * With a change, the world's data packs load again with it; back on the Create World screen either way. With Nook of
     * the Dead installed, switching the If off is asked first: that add-on reaches its world through the If. A change
     * that takes more with it than the parts switched off - items nothing reaches any more - shows them, by name, to be
     * confirmed or gone back from.
     */
    private void done() {
        if (this.chosen.equals(this.before)) {
            this.onClose();
            return;
        }
        List<String> lost = PartsChange.newlyLost(this::wasOn, this::on, WorldFeatures.kept());
        Runnable save = lost.isEmpty() ? this::apply : () -> this.minecraft.gui.setScreen(new PartsLossScreen(this, lost, this::apply));
        if (NookOfTheDeadWarning.present() && !this.on(NookOfTheDeadWarning.PART) && this.wasOn(NookOfTheDeadWarning.PART)) {
            this.minecraft.gui.setScreen(new ConfirmScreen(yes -> {
                if (yes) {
                    save.run();
                } else {
                    this.minecraft.gui.setScreen(this);
                }
            }, Component.translatable("nookworks.world_features.notd.title"), Component.translatable("nookworks.world_features.notd.message")));
            return;
        }
        save.run();
    }

    private void apply() {
        this.apply.accept(Map.copyOf(this.chosen));
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }
}
