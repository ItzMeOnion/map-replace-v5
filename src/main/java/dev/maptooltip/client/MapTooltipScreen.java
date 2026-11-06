package dev.maptooltip.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.nio.file.Path;
import java.util.List;

/** The main Map Tooltip menu: every replacement, with toggle / edit / delete. */
public class MapTooltipScreen extends Screen {
    private static final int TOP = 10;
    private static final int TITLE_BAR = 52;
    private static final int FOOTER = 64;
    private static final int CARD_H = 66;
    private static final int GAP = 6;
    private static final int THUMB_CELLS = 14;
    private static final int THUMB_PX = 4;

    private final Screen parent;
    private int scroll = 0;
    private String pendingDelete = null;
    private String status = "";
    private int statusColor = Ui.TEXT_DIM;

    public MapTooltipScreen(Screen parent) {
        super(Text.literal("Map Tooltip"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        rebuild();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    /** Overrides the default blurred vanilla background so our own gradient is the only one drawn. */
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // intentionally empty; render() paints the background itself
    }

    // ---- layout helpers -------------------------------------------------------

    private int panelW() { return Math.min(width - 24, 480); }
    private int left() { return (width - panelW()) / 2; }
    private int right() { return left() + panelW(); }
    private int bottom() { return height - TOP; }
    private int listTop() { return TOP + TITLE_BAR; }
    private int listBottom() { return bottom() - FOOTER; }
    private int visibleRows() {
        return Math.max(1, (listBottom() - listTop() + GAP) / (CARD_H + GAP));
    }
    private int cardX() { return left() + 14; }
    private int cardW() { return panelW() - 28; }
    private int cardRightEdge() { return cardX() + cardW() - 10; }
    private int deleteX() { return cardRightEdge() - 44; }
    private int editX() { return deleteX() - 44; }
    private int toggleX() { return editX() - 62; }

    private void setStatus(String text, int color) {
        this.status = text;
        this.statusColor = color;
    }

    // ---- widgets --------------------------------------------------------------

    private void rebuild() {
        clearChildren();
        ReplacementManager mgr = ReplacementManager.get();
        List<Replacement> list = mgr.all();
        int rows = visibleRows();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, list.size() - rows)));

        addDrawableChild(ButtonWidget.builder(Text.literal("+ New"),
                        b -> client.setScreen(new AddReplacementScreen(this, null)))
                .dimensions(right() - 16 - 96, TOP + 12, 96, 20)
                .build());

        for (int i = scroll; i < Math.min(list.size(), scroll + rows); i++) {
            Replacement r = list.get(i);
            int y = listTop() + (i - scroll) * (CARD_H + GAP);
            int by = y + 23;
            String id = r.id();
            int mapId = r.mapId();
            boolean on = r.enabled();

            addDrawableChild(ButtonWidget.builder(
                            Text.literal(on ? "ON" : "OFF").formatted(on ? Formatting.GREEN : Formatting.RED),
                            b -> {
                                mgr.setEnabled(id, !on);
                                setStatus("Map #" + mapId + " " + (on ? "paused" : "active"),
                                        on ? Ui.TEXT_DIM : Ui.GREEN);
                                rebuild();
                            })
                    .dimensions(toggleX(), by, 58, 20)
                    .build());

            addDrawableChild(ButtonWidget.builder(Text.literal("Edit"),
                            b -> client.setScreen(new AddReplacementScreen(this, r)))
                    .dimensions(editX(), by, 40, 20)
                    .build());

            boolean armed = id.equals(pendingDelete);
            addDrawableChild(ButtonWidget.builder(
                            Text.literal(armed ? "Sure?" : "Del").formatted(armed ? Formatting.RED : Formatting.GRAY),
                            b -> {
                                if (armed) {
                                    mgr.remove(id);
                                    pendingDelete = null;
                                    setStatus("Removed replacement for map #" + mapId, Ui.RED);
                                } else {
                                    pendingDelete = id;
                                    setStatus("Click Sure? again to delete map #" + mapId, Ui.RED);
                                }
                                rebuild();
                            })
                    .dimensions(deleteX(), by, 44, 20)
                    .build());
        }

        boolean globalOn = mgr.isGlobalEnabled();
        addDrawableChild(ButtonWidget.builder(
                        Text.literal("Replacements: " + (globalOn ? "ON" : "OFF"))
                                .formatted(globalOn ? Formatting.GREEN : Formatting.RED),
                        b -> {
                            mgr.setGlobalEnabled(!globalOn);
                            setStatus(globalOn ? "All replacements paused" : "All replacements active",
                                    globalOn ? Ui.RED : Ui.GREEN);
                            rebuild();
                        })
                .dimensions(left() + 16, bottom() - 30, 170, 20)
                .build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(right() - 16 - 100, bottom() - 30, 100, 20)
                .build());
    }

    // ---- rendering ------------------------------------------------------------

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        Ui.background(ctx, width, height);

        int left = left(), right = right(), top = TOP, bottom = bottom(), pw = panelW();
        Ui.panel(ctx, left, top, pw, bottom - top);

        ReplacementManager mgr = ReplacementManager.get();
        List<Replacement> list = mgr.all();
        int enabledCount = (int) list.stream().filter(Replacement::enabled).count();

        ctx.drawTextWithShadow(textRenderer, Text.literal("MAP TOOLTIP").formatted(Formatting.BOLD),
                left + 16, top + 12, Ui.GOLD);
        String sub = list.isEmpty()
                ? "No replacements yet"
                : list.size() + " replacement" + (list.size() == 1 ? "" : "s") + "  ·  " + enabledCount + " active";
        ctx.drawTextWithShadow(textRenderer, sub, left + 16, top + 26, Ui.TEXT_DIM);
        ctx.fill(left + 12, top + TITLE_BAR - 8, right - 12, top + TITLE_BAR - 7, Ui.GOLD_DIM);

        if (list.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, "Nothing replaced yet", width / 2, listTop() + 40, Ui.TEXT);
            ctx.drawCenteredTextWithShadow(textRenderer, "Press + New to link a map ID to an image",
                    width / 2, listTop() + 56, Ui.TEXT_DIM);
        }

        int rows = visibleRows();
        for (int i = scroll; i < Math.min(list.size(), scroll + rows); i++) {
            Replacement r = list.get(i);
            int y = listTop() + (i - scroll) * (CARD_H + GAP);
            int x = cardX(), w = cardW();
            boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + CARD_H;

            Ui.card(ctx, x, y, w, CARD_H, r.enabled());
            if (hover) ctx.fill(x + 2, y, x + w, y + CARD_H, 0x12FFFFFF);

            byte[] colors = mgr.imageFor(r.id());
            Ui.mosaic(ctx, colors, x + 10, y + 5, THUMB_CELLS, THUMB_PX);

            int tx = x + 78;
            int maxW = toggleX() - 8 - tx;
            ctx.drawTextWithShadow(textRenderer, Ui.fit(textRenderer, r.name(), maxW), tx, y + 10, Ui.TEXT);
            ctx.drawTextWithShadow(textRenderer, "Map #" + r.mapId(), tx, y + 24, Ui.GOLD);
            ctx.drawTextWithShadow(textRenderer, Ui.fit(textRenderer, fileName(r.sourcePath()), maxW), tx, y + 38, Ui.TEXT_DIM);
            ctx.drawTextWithShadow(textRenderer, r.enabled() ? "Showing on your screen" : "Paused",
                    tx, y + 51, r.enabled() ? Ui.GREEN : Ui.TEXT_DIM);
        }

        if (list.size() > rows) {
            String hint = "Scroll for more  (" + (scroll + 1) + "-" + Math.min(list.size(), scroll + rows) + " of " + list.size() + ")";
            ctx.drawCenteredTextWithShadow(textRenderer, hint, width / 2, listBottom() + 2, Ui.TEXT_DIM);
        }

        ctx.drawCenteredTextWithShadow(textRenderer, status, width / 2, bottom - 56, statusColor);
        ctx.fill(left + 12, bottom - 40, right - 12, bottom - 39, Ui.GOLD_DIM);

        super.render(ctx, mouseX, mouseY, delta);
    }

    private static String fileName(String path) {
        if (path == null || path.isBlank()) return "(no file)";
        try {
            Path p = Path.of(path);
            return p.getFileName() != null ? p.getFileName().toString() : path;
        } catch (RuntimeException e) {
            return path;
        }
    }

    // ---- input ----------------------------------------------------------------

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int max = Math.max(0, ReplacementManager.get().all().size() - visibleRows());
        if (verticalAmount != 0 && max > 0) {
            scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(verticalAmount)));
            rebuild();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }
}
