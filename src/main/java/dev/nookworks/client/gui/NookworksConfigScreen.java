package dev.nookworks.client.gui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import dev.nookworks.client.SettingsGroups;
import dev.nookworks.client.SettingsGroups.Entry;
import dev.nookworks.client.SettingsGroups.Group;
import dev.nookworks.client.SettingsGroups.Header;
import dev.nookworks.client.SettingsGroups.Opt;
import dev.nookworks.client.SettingsGroups.Scope;
import dev.nookworks.client.SettingsGroups.Unit;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.TranslatableEnum;

/**
 * Mods > Nookworks > Config > Settings: every setting of the mod's three configs on one screen of its own (the user,
 * 2026-11-22: 「設定項目は、確かにもう少し作り込めるよね。説明だけではなく、アイコンや写真なども場合によっては入れていいかも」), in
 * place of NeoForge's generic list. The settings stand in groups by what they are about - not by the file they are kept
 * in - each group under its item's picture on the left; a group's settings on the right, a row each with its own
 * picture where a thing of ours is what it is about; and under them, the one the pointer is on (or was last), with its
 * whole explanation, which file it is kept in (the world's, this computer's, read at the game's start) and, where one
 * says more than words, a picture.
 * <p>
 * The world's settings follow NeoForge's rules for a synced config: changed in a world of one's own, not while on a
 * server or with the world open to LAN - they are the server's then, shown but not changed. Out of a world (the title
 * screen) NeoForge has not loaded them at all: their defaults are shown, greyed, and nothing reads or writes them (it
 * threw and closed the game, the 1.8 review).
 * Changes are written as the screen closes; one read at start says so. NeoForge's list stays one button away.
 */
public final class NookworksConfigScreen extends Screen {
    private static final int PAD = 8;
    private static final int ROW = 22;
    private static final int CONTROL = 112;
    private static final int GROUP_ROW = 20;
    private static final int HEADING = 14;
    private static final int HEADING_TEXT = 0xFFFFD970;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int DIM = 0xFFA0A0A0;
    private static final int PANEL = 0x90000000;
    private static final int LINE = 0x40FFFFFF;

    private final ModContainer mod;
    private final Screen parent;
    private final List<Group> groups = SettingsGroups.groups();
    private int group;
    private int scroll;
    private @Nullable Opt shown;
    private final Set<ModConfigSpec> changed = new HashSet<>();
    private boolean startupChanged;
    /** The rows of the group on show: a heading (no widget) or a setting and the widget that changes it. */
    private final List<Line> lines = new ArrayList<>();

    public NookworksConfigScreen(ModContainer mod, Screen parent) {
        super(Component.translatable("nookworks.configuration.title"));
        this.mod = mod;
        this.parent = parent;
    }

    /** A row: where it starts in the list (before scrolling), how tall it is, what it shows and changes. */
    private record Line(Entry entry, @Nullable AbstractWidget widget, int top, int height) {}

    /** The heading each setting of the group on show stands under, to name it with the setting's own name. */
    private final java.util.Map<Opt, Header> headings = new java.util.IdentityHashMap<>();

    // ---- layout: the groups down the left; the rows on the right, the explanation under them; Done at the foot

    private int groupWidth() {
        return Math.max(96, Math.min(150, this.width * 3 / 10));
    }

    private int listLeft() {
        return PAD + this.groupWidth() + PAD;
    }

    private int listRight() {
        return this.width - PAD;
    }

    private int listTop() {
        return 30;
    }

    private int footerTop() {
        return this.height - 26;
    }

    /**
     * The explanation's room: a third of the screen, up to 100, and on the smallest (240 high, where the game's automatic
     * GUI size puts many screens) 64 - room for a picture 54 high beside four lines.
     */
    private int descHeight() {
        return Math.max(64, Math.min(100, (this.height - 60) / 3));
    }

    private int listBottom() {
        return this.footerTop() - 6 - this.descHeight() - 4;
    }

    private int contentHeight() {
        return this.lines.isEmpty() ? 0 : this.lines.getLast().top() + this.lines.getLast().height();
    }

    @Override
    protected void init() {
        this.clearWidgets();
        this.lines.clear();
        int y = this.listTop();
        int step = Math.min(GROUP_ROW, (this.footerTop() - 4 - this.listTop()) / this.groups.size());
        for (int i = 0; i < this.groups.size(); i++) {
            Group g = this.groups.get(i);
            int index = i;
            GroupButton button = new GroupButton(PAD, y, this.groupWidth(), step - 2, g, () -> {
                this.group = index;
                this.scroll = 0;
                this.shown = null;
                this.init();
            });
            button.setOverrideRenderHighlightedSprite(() -> this.group == index);
            this.addRenderableWidget(button);
            y += step;
        }
        int top = 0;
        this.headings.clear();
        Header heading = null;
        for (Entry entry : this.groups.get(this.group).entries()) {
            if (entry instanceof Header h) {
                heading = h;
            }
            if (entry instanceof Opt opt) {
                if (heading != null) {
                    this.headings.put(opt, heading);
                }
                AbstractWidget widget = this.control(opt, this.listRight() - CONTROL, 0, CONTROL, 20);
                widget.active = widget.active && this.editable(opt);
                this.lines.add(new Line(entry, widget, top, ROW));
                this.addRenderableWidget(widget);
                top += ROW;
            } else {
                top += top == 0 ? 0 : 4; // a little air over each heading but the first
                this.lines.add(new Line(entry, null, top, HEADING));
                top += HEADING;
            }
        }
        this.placeRows();
        this.addRenderableWidget(Button.builder(Component.translatable("nookworks.settings.all"),
                b -> this.minecraft.gui.setScreen(new ConfigurationScreen(this.mod, this))).bounds(PAD, this.footerTop(), this.groupWidth(), 20)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("nookworks.settings.all.tooltip"))).build());
        int doneWidth = Math.min(150, this.listRight() - this.listLeft());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose())
                .bounds(this.listLeft() + (this.listRight() - this.listLeft() - doneWidth) / 2, this.footerTop(), doneWidth, 20).build());
    }

    /** Sets each row's control at its place under the scroll, and hides those out of the list's room. */
    private void placeRows() {
        int max = Math.max(0, this.contentHeight() - (this.listBottom() - this.listTop()));
        this.scroll = Math.max(0, Math.min(this.scroll, max));
        for (Line line : this.lines) {
            if (line.widget() != null) {
                int y = this.listTop() + line.top() - this.scroll;
                line.widget().setY(y + 1);
                line.widget().visible = y >= this.listTop() - 1 && y + line.height() <= this.listBottom() + 1;
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= this.listLeft() && mouseY >= this.listTop() && mouseY <= this.listBottom()) {
            this.scroll -= (int) (scrollY * ROW);
            this.placeRows();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // ---- what may be changed

    /** The world's settings are the server's while on one, or while this world is open to LAN (NeoForge's rule). */
    private boolean editable(Opt opt) {
        return opt.spec().isLoaded() && (opt.scope() != Scope.WORLD || this.lockedWhy() == null);
    }

    /** What a setting is now: its default while its config is not loaded (the world's, out of a world). */
    private static Object now(Opt opt) {
        return opt.spec().isLoaded() ? opt.value().get() : opt.value().getDefault();
    }

    private @Nullable Component lockedWhy() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getCurrentServer() != null && !mc.hasSingleplayerServer()) {
            return Component.translatable("nookworks.settings.locked.online");
        }
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer().isPublished()) {
            return Component.translatable("nookworks.settings.locked.lan");
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> void set(Opt opt, T value) {
        ((ModConfigSpec.ConfigValue) opt.value()).set(value);
        this.changed.add(opt.spec());
        if (opt.scope() == Scope.STARTUP) {
            this.startupChanged = true;
        }
    }

    // ---- the controls

    @SuppressWarnings({"unchecked", "rawtypes"})
    private AbstractWidget control(Opt opt, int x, int y, int w, int h) {
        ModConfigSpec.ConfigValue<?> value = opt.value();
        Component name = Component.translatable(opt.key());
        if (value instanceof ModConfigSpec.BooleanValue bool) {
            return CycleButton.onOffBuilder((Boolean) now(opt)).displayOnlyValue()
                    .create(x, y, w, h, name, (b, v) -> this.set(opt, v));
        }
        if (value instanceof ModConfigSpec.EnumValue<?>) {
            Enum<?> now = (Enum<?>) now(opt);
            List<Enum<?>> choices = new ArrayList<>();
            for (Object c : now.getDeclaringClass().getEnumConstants()) {
                if (value.getSpec().test(c)) {
                    choices.add((Enum<?>) c);
                }
            }
            Function<Enum<?>, Component> label = e -> e instanceof TranslatableEnum t ? t.getTranslatedName()
                    : Component.literal(e.name().toLowerCase(Locale.ROOT));
            return CycleButton.<Enum<?>>builder(label, now).withValues(choices).displayOnlyValue()
                    .create(x, y, w, h, name, (b, v) -> this.set(opt, v));
        }
        if (value.getSpec().getRange() != null && (value instanceof ModConfigSpec.IntValue || value instanceof ModConfigSpec.DoubleValue)) {
            return new Steps(opt, x, y, w, h);
        }
        if (now(opt) instanceof List<?> list) {
            EditBox box = new EditBox(this.font, x, y, w, h, name);
            box.setMaxLength(1024);
            box.setValue(String.join(", ", list.stream().map(String::valueOf).toList()));
            box.moveCursorToStart(false); // the list read from its start, not scrolled to its end
            box.setHint(Component.translatable("nookworks.settings.list.hint").withStyle(ChatFormatting.DARK_GRAY));
            box.setResponder(text -> {
                List<String> ids = new ArrayList<>();
                for (String part : text.split(",")) {
                    String id = part.trim();
                    if (!id.isEmpty()) {
                        ids.add(id);
                    }
                }
                if (value.getSpec().test(ids)) {
                    this.set(opt, ids);
                    box.setTextColor(0xFFE0E0E0);
                } else {
                    box.setTextColor(0xFFFF6060);
                }
            });
            return box;
        }
        Button none = Button.builder(Component.literal(String.valueOf(now(opt))), b -> {}).bounds(x, y, w, h).build();
        none.active = false;
        return none;
    }

    /** A number, chosen from steps: round ones within its range, with its default and its value as they are. */
    private final class Steps extends AbstractSliderButton {
        private final Opt opt;
        private final List<Double> steps;
        private int index;

        Steps(Opt opt, int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(), 0);
            this.opt = opt;
            this.steps = SettingsGroups.steps(opt);
            double now = ((Number) now(opt)).doubleValue();
            this.index = nearest(this.steps, now);
            this.value = this.steps.size() < 2 ? 0 : this.index / (double) (this.steps.size() - 1);
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            this.setMessage(NookworksConfigScreen.format(this.opt, this.steps.get(this.index)));
        }

        @Override
        protected void applyValue() {
            int i = (int) Math.round(this.value * (this.steps.size() - 1));
            if (i != this.index) {
                this.index = i;
                double v = this.steps.get(i);
                if (this.opt.value() instanceof ModConfigSpec.IntValue) {
                    NookworksConfigScreen.this.set(this.opt, (int) Math.round(v));
                } else {
                    NookworksConfigScreen.this.set(this.opt, v);
                }
            }
        }
    }

    private static int nearest(List<Double> steps, double v) {
        int best = 0;
        for (int i = 1; i < steps.size(); i++) {
            if (Math.abs(steps.get(i) - v) < Math.abs(steps.get(best) - v)) {
                best = i;
            }
        }
        return best;
    }

    static Component format(Opt opt, double v) {
        boolean whole = opt.value() instanceof ModConfigSpec.IntValue;
        String n = whole ? String.format(Locale.ROOT, "%,d", Math.round(v))
                : v == Math.rint(v) ? String.format(Locale.ROOT, "%.1f", v) : String.format(Locale.ROOT, "%s", trim(v));
        return switch (opt.unit()) {
            case PLAIN -> Component.literal(n);
            case BLOCKS -> Component.translatable("nookworks.settings.unit.blocks", n);
            case TIMES -> Component.translatable("nookworks.settings.unit.times", n);
            case PERCENT -> Component.translatable("nookworks.settings.unit.percent", n);
            case CHANCE -> Component.translatable("nookworks.settings.unit.percent", String.valueOf(Math.round(v * 100)));
            case TICKS -> Component.translatable("nookworks.settings.unit.ticks", n, trim(v / 20.0));
            case STACKS -> Component.translatable("nookworks.settings.unit.stacks", n);
        };
    }

    private static String trim(double v) {
        String s = String.format(Locale.ROOT, "%.2f", v);
        s = s.contains(".") ? s.replaceAll("0+$", "").replaceAll("\\.$", "") : s;
        return s;
    }

    // ---- drawing

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        g.centeredText(this.font, this.title, this.width / 2, 10, TEXT);
        int left = this.listLeft();
        int right = this.listRight();
        int top = this.listTop();
        int bottom = this.listBottom();
        g.fill(left - 4, top - 4, right + 2, bottom + 2, PANEL);
        Opt hovered = null;
        g.enableScissor(left - 4, top - 2, right + 2, bottom + 2);
        for (Line line : this.lines) {
            int y = top + line.top() - this.scroll;
            if (y < top - 1 || y + line.height() > bottom + 1) {
                continue; // drawn whole or not at all: a label half under the edge, its control hidden, read oddly
            }
            if (line.entry() instanceof Header header) {
                int textX = left;
                if (header.icon() != null) {
                    g.fakeItem(stack(header.icon()), left, y - 2);
                    textX += 20;
                }
                g.text(this.font, Component.translatable(header.key()).withStyle(ChatFormatting.BOLD), textX, y + 3, HEADING_TEXT, false);
                g.horizontalLine(textX + this.font.width(Component.translatable(header.key()).withStyle(ChatFormatting.BOLD)) + 4, right - 2, y + 7, LINE);
                continue;
            }
            Opt opt = (Opt) line.entry();
            AbstractWidget w = line.widget();
            boolean over = w != null && w.visible && mouseX >= left - 4 && mouseX < right && mouseY >= Math.max(y, top) && mouseY < Math.min(y + ROW, bottom);
            if (over || w != null && w.isFocused()) {
                hovered = opt;
                g.fill(left - 4, y, right + 2, y + ROW, 0x30FFFFFF);
            }
            int textX = left + 20;
            if (opt.icon() != null) {
                g.fakeItem(stack(opt.icon()), left, y + 3);
            }
            int room = right - CONTROL - 6 - textX;
            List<FormattedCharSequence> label = this.font.split(Component.translatable(opt.key()), Math.max(20, room));
            int shownLines = Math.min(2, label.size());
            int ty = y + (ROW - shownLines * 9) / 2 + 1;
            for (int i = 0; i < shownLines; i++) {
                g.text(this.font, label.get(i), textX, ty + i * 9, w == null || w.active ? TEXT : DIM, false);
            }
        }
        g.disableScissor();
        if (this.contentHeight() > bottom - top) {
            // where in the list the view is: a thin bar down its right edge
            int track = bottom - top;
            int thumb = Math.max(12, track * track / this.contentHeight());
            int max = this.contentHeight() - track;
            int ty = top + (track - thumb) * this.scroll / Math.max(1, max);
            g.fill(right, top, right + 2, bottom, 0x40FFFFFF);
            g.fill(right, ty, right + 2, ty + thumb, 0xC0FFFFFF);
        }
        if (hovered != null) {
            this.shown = hovered;
        }
        super.extractRenderState(g, mouseX, mouseY, a);
        this.drawDescription(g, mouseX, mouseY);
    }

    private void drawDescription(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int x0 = this.listLeft() - 4;
        int x1 = this.listRight() + 2;
        int y0 = this.listBottom() + 6;
        int y1 = y0 + this.descHeight();
        g.fill(x0, y0, x1, y1, PANEL);
        g.horizontalLine(x0, x1 - 1, y0, LINE);
        int textX = x0 + 6;
        Opt opt = this.shown;
        textX = this.drawPicture(g, opt, x0, y0, textX);
        int width = x1 - 6 - textX;
        int y = y0 + 6;
        if (opt == null) {
            Group group = this.groups.get(this.group);
            g.text(this.font, Component.translatable("nookworks.settings.group." + group.id()).withStyle(ChatFormatting.BOLD), textX, y, TEXT, false);
            y += 12;
            List<FormattedCharSequence> lines = this.font.split(Component.translatable("nookworks.settings.group." + group.id() + ".desc"), width);
            for (int i = 0; i < Math.min((y1 - 4 - y) / 9, lines.size()); i++) {
                g.text(this.font, lines.get(i), textX, y + i * 9, DIM, false);
            }
            return;
        }
        MutableComponent where = Component.translatable("nookworks.settings.scope." + opt.scope().name().toLowerCase(Locale.ROOT))
                .withStyle(opt.scope() == Scope.STARTUP ? ChatFormatting.GOLD : opt.scope() == Scope.CLIENT ? ChatFormatting.AQUA : ChatFormatting.GREEN);
        Component locked = !opt.spec().isLoaded() ? Component.translatable("nookworks.settings.locked.no_world")
                : opt.scope() == Scope.WORLD ? this.lockedWhy() : null;
        MutableComponent scope = where.copy();
        if (locked != null) {
            where.append(Component.literal("  ")).append(locked.copy().withStyle(ChatFormatting.RED));
        }
        Header under = this.headings.get(opt);
        MutableComponent head = (under == null ? Component.translatable(opt.key())
                : Component.translatable(under.key()).append(" › ").append(Component.translatable(opt.key()))).withStyle(ChatFormatting.BOLD);
        if (this.font.width(head) > x1 - 6 - textX) {
            head = Component.translatable(opt.key()).withStyle(ChatFormatting.BOLD); // the heading dropped when both do not fit
        }
        g.text(this.font, head, textX, y, TEXT, false);
        // where it is kept, at the right of the name if it fits there, else under it
        int whereWidth = this.font.width(where);
        if (textX + this.font.width(head) + 8 + whereWidth <= x1 - 6) {
            g.text(this.font, where, x1 - 6 - whereWidth, y, TEXT, false);
            y += 12;
        } else if (textX + whereWidth <= x1 - 6 || locked == null) {
            y += 10;
            g.text(this.font, where, textX, y, TEXT, false);
            y += 11;
        } else { // beside a picture the why goes on a line of its own, not off the panel's edge
            y += 10;
            g.text(this.font, scope, textX, y, TEXT, false);
            y += 9;
            g.text(this.font, locked.copy().withStyle(ChatFormatting.RED), textX, y, TEXT, false);
            y += 11;
        }
        String tip = opt.key() + ".tooltip";
        Component body = net.minecraft.locale.Language.getInstance().has(tip) ? Component.translatable(tip) : Component.empty();
        List<FormattedCharSequence> lines = this.font.split(body, width);
        int room = Math.max(1, (y1 - 3 - y) / 9);
        boolean cut = lines.size() > room;
        for (int i = 0; i < Math.min(room, lines.size()); i++) {
            g.text(this.font, lines.get(i), textX, y + i * 9, DIM, false);
        }
        if (cut) {
            // more than the room holds on a small screen: marked, and the whole of it shown with the pointer over it
            g.text(this.font, "…", x1 - 6 - this.font.width("…"), y1 - 11, TEXT, false);
            if (mouseX >= x0 && mouseX < x1 && mouseY >= y0 && mouseY < y1) {
                g.setTooltipForNextFrame(this.font.split(body, Math.min(320, this.width - 40)), mouseX, mouseY);
            }
        }
    }

    /** Below this height a picture is too small to read: the explanation has the room instead. */
    private static final int PICTURE_MIN_H = 54;
    /** The size the pictures drawn on the spot are laid out at, scaled to the room they get. */
    private static final int LIVE_W = 192;
    private static final int LIVE_H = 108;

    /**
     * The setting's picture ({@link SettingsGroups#PICTURES}), at the left of the explanation, 16:9: a shot from the game
     * (textures/gui/settings/NAME.png) or, for what has words in it, drawn here by the game itself so its words are in
     * the player's language. Returns where the explanation's text starts.
     */
    private int drawPicture(GuiGraphicsExtractor g, @Nullable Opt opt, int x0, int y0, int textX) {
        String name = opt == null ? null : SettingsGroups.PICTURES.get(opt.key());
        int ph = this.descHeight() - 10;
        if (name == null || ph < PICTURE_MIN_H) {
            return textX;
        }
        int pw = ph * 16 / 9;
        int px = x0 + 5;
        int py = y0 + 5;
        if (name.startsWith("@")) {
            g.fill(px, py, px + pw, py + ph, 0xFF202226);
            g.enableScissor(px, py, px + pw, py + ph);
            g.pose().pushMatrix();
            g.pose().translate(px, py);
            g.pose().scale(pw / (float) LIVE_W, ph / (float) LIVE_H);
            switch (name) {
                case "@chest_sort" -> this.liveChestSort(g);
                case "@world_menu" -> this.liveWorldMenu(g);
                default -> { }
            }
            g.pose().popMatrix();
            g.disableScissor();
        } else {
            Identifier id = Nookworks.id("textures/gui/settings/" + name + ".png");
            if (Minecraft.getInstance().getResourceManager().getResource(id).isEmpty()) {
                return textX;
            }
            g.blit(id, px, py, px + pw, py + ph, 0.0F, 1.0F, 0.0F, 1.0F);
        }
        g.outline(px - 1, py - 1, pw + 2, ph + 2, 0x60FFFFFF);
        return px + pw + 8;
    }

    private static final Identifier BUTTON = Identifier.withDefaultNamespace("widget/button");
    private static final Identifier BUTTON_OFF = Identifier.withDefaultNamespace("widget/button_disabled");
    private static final Identifier ORDER_ICONS = Nookworks.id("textures/gui/sort_orders.png");
    private static final int POINT = 0xFFFFD24A;

    /** A vanilla button's face with its words, as the game draws one. */
    private void button(GuiGraphicsExtractor g, int x, int y, int w, int h, Component text, boolean active) {
        g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, active ? BUTTON : BUTTON_OFF, x, y, w, h);
        g.centeredText(this.font, text, x + w / 2, y + (h - 8) / 2, active ? TEXT : 0xFFA0A0A0);
    }

    /** A chest's screen, its top rows: the order, direction and Sort buttons at its top right, pointed out. */
    private void liveChestSort(GuiGraphicsExtractor g) {
        int x = 8;
        int y = 10;
        int w = 176;
        GuiDraw.panel(g, x, y, w, 120);
        g.text(this.font, Component.translatable("container.chest"), x + 8, y + 6, 0xFF404040, false);
        ItemStack[] things = !itemsReady() ? new ItemStack[0] : new ItemStack[] {new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.OAK_LOG, 12), new ItemStack(Items.COAL, 30),
                new ItemStack(Items.IRON_INGOT, 7), new ItemStack(Items.WHEAT_SEEDS, 20), new ItemStack(Items.COBBLESTONE, 33),
                new ItemStack(Items.BREAD, 5), new ItemStack(Items.OAK_LOG, 40), new ItemStack(Items.TORCH, 16)};
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 9; col++) {
                int sx = x + 7 + col * 18;
                int sy = y + 17 + row * 18;
                GuiDraw.slot(g, sx, sy);
                int i = row * 9 + col;
                if ((i * 7) % 5 < 2 && i / 2 < things.length) {
                    ItemStack stack = things[(i * 3) % things.length];
                    g.fakeItem(stack, sx + 1, sy + 1);
                    g.itemDecorations(this.font, stack, sx + 1, sy + 1);
                }
            }
        }
        Component sort = Component.translatable("gui.nookworks.storage.sort");
        int sw = this.font.width(sort) + 10;
        int sx = x + w - 7 - sw;
        int by = y + 3;
        this.button(g, sx, by, sw, 13, sort, true);
        int turnX = sx - SortDirectionButton.WIDTH - 2;
        g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, BUTTON, turnX, by, SortDirectionButton.WIDTH, 13);
        g.centeredText(this.font, "↓", turnX + SortDirectionButton.WIDTH / 2 + 1, by + 3, TEXT);
        int orderX = turnX - 13 - 2;
        g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, BUTTON, orderX, by, 13, 13);
        g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, ORDER_ICONS, orderX + 3, by + 3, 0, 0, 8, 8, 32, 8);
        // the three pointed out
        g.outline(orderX - 2, by - 2, sx + sw - orderX + 4, 17, POINT);
        g.outline(orderX - 3, by - 3, sx + sw - orderX + 6, 19, POINT);
    }

    /** The Create World screen's More tab: vanilla's buttons there, and Nookworks Parts under them, pointed out. */
    private void liveWorldMenu(GuiGraphicsExtractor g) {
        g.centeredText(this.font, Component.translatable("createWorld.tab.more.title").withStyle(ChatFormatting.UNDERLINE), LIVE_W / 2, 5, TEXT);
        int bw = 150;
        int bx = (LIVE_W - bw) / 2;
        this.button(g, bx, 17, bw, 20, Component.translatable("selectWorld.gameRules"), false);
        this.button(g, bx, 39, bw, 20, Component.translatable("selectWorld.experiments"), false);
        this.button(g, bx, 61, bw, 20, Component.translatable("selectWorld.dataPacks"), false);
        this.button(g, bx, 85, bw, 20, Component.translatable("nookworks.world_features.button"), true);
        g.outline(bx - 2, 83, bw + 4, 24, POINT);
        g.outline(bx - 3, 82, bw + 6, 26, POINT);
    }

    private static ItemStack stack(String id) {
        if (!itemsReady()) {
            return ItemStack.EMPTY;
        }
        return BuiltInRegistries.ITEM.getOptional(Identifier.parse(id)).map(ItemStack::new).orElse(new ItemStack(Items.BARRIER));
    }

    /**
     * Whether an item can be made at all: in 26.3 items have their components only once a world's registries are in,
     * so on the title screen the pictures are left out (making one threw and closed the game, the 1.8 review).
     */
    private static boolean itemsReady() {
        return Items.BARRIER.builtInRegistryHolder().areComponentsBound();
    }

    // ---- closing

    @Override
    public void onClose() {
        for (ModConfigSpec spec : this.changed) {
            if (spec.isLoaded()) {
                spec.save();
            }
        }
        this.changed.clear();
        if (this.startupChanged) {
            this.startupChanged = false;
            this.minecraft.gui.setScreen(new AlertScreen(() -> this.minecraft.gui.setScreen(this.parent),
                    Component.translatable("nookworks.settings.restart.title"), Component.translatable("nookworks.settings.restart.message")));
            return;
        }
        this.minecraft.gui.setScreen(this.parent);
    }

    /** A group on the left: its item's picture and its name, lit while it is the one on show. */
    private static final class GroupButton extends AbstractButton {
        private final ItemStack icon;
        private final Runnable press;

        GroupButton(int x, int y, int w, int h, Group group, Runnable press) {
            super(x, y, w, h, Component.translatable("nookworks.settings.group." + group.id()));
            this.icon = stack(group.icon());
            this.press = press;
        }

        @Override
        public void onPress(InputWithModifiers input) {
            this.press.run();
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
            this.extractDefaultSprite(g);
            g.fakeItem(this.icon, this.getX() + 2, this.getY() + (this.getHeight() - 16) / 2);
            var font = Minecraft.getInstance().font;
            Component text = this.getMessage();
            int room = this.getWidth() - 24;
            String plain = text.getString();
            if (font.width(plain) > room) {
                plain = font.plainSubstrByWidth(plain, room - font.width("…")) + "…";
            }
            g.text(font, plain, this.getX() + 21, this.getY() + (this.getHeight() - 8) / 2, 0xFFFFFFFF, true);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            this.defaultButtonNarrationText(output);
        }
    }
}
