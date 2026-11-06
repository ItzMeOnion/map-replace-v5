package dev.maptooltip.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.font.TextRenderer;

/** Shared look for every Map Tooltip screen. */
final class Ui {
    static final int GOLD = 0xFFFFC857;
    static final int GOLD_DIM = 0x66FFC857;
    static final int TEXT = 0xFFF2EEE4;
    static final int TEXT_DIM = 0xFF9AA3B8;
    static final int GREEN = 0xFF6BE38C;
    static final int RED = 0xFFFF6B6B;
    static final int CARD = 0xCC131A2B;
    static final int CARD_HOVER = 0xCC1B2439;
    static final int PANEL = 0xE60A0E1A;
    static final int CHECK_A = 0xFF2A2F40;
    static final int CHECK_B = 0xFF3A4056;

    private Ui() {}

    static void background(DrawContext ctx, int width, int height) {
        ctx.fillGradient(0, 0, width, height, 0xF00A0E1A, 0xF01A0F2E);
        // soft vignette bands at top and bottom
        ctx.fillGradient(0, 0, width, 40, 0x66000000, 0x00000000);
        ctx.fillGradient(0, height - 40, width, height, 0x00000000, 0x66000000);
    }

    /** Rounded-looking panel: flat fill, gold hairline border, and a gold top stripe. */
    static void panel(DrawContext ctx, int x, int y, int w, int h) {
        ctx.fill(x - 1, y - 1, x + w + 1, y + h + 1, GOLD_DIM);
        ctx.fill(x, y, x + w, y + h, PANEL);
        ctx.fill(x, y, x + w, y + 2, GOLD);
    }

    static void card(DrawContext ctx, int x, int y, int w, int h, boolean accent) {
        ctx.fill(x, y, x + w, y + h, CARD);
        ctx.fill(x, y, x + 2, y + h, accent ? GREEN : 0xFF3A4260);
        ctx.fill(x, y + h - 1, x + w, y + h, 0x33FFFFFF);
    }

    /** Draws a checkerboard for transparent map pixels. */
    static void checker(DrawContext ctx, int x, int y, int w, int h, int cell) {
        for (int cy = 0; cy * cell < h; cy++) {
            for (int cx = 0; cx * cell < w; cx++) {
                int color = ((cx + cy) & 1) == 0 ? CHECK_A : CHECK_B;
                int x1 = x + cx * cell, y1 = y + cy * cell;
                ctx.fill(x1, y1, Math.min(x1 + cell, x + w), Math.min(y1 + cell, y + h), color);
            }
        }
    }

    /**
     * Draws a pixel mosaic of 128x128 map colours, sampled down to {@code cells} x {@code cells}.
     * Each cell is {@code cellPx} screen pixels.
     */
    static void mosaic(DrawContext ctx, byte[] colors, int x, int y, int cells, int cellPx) {
        int size = cells * cellPx;
        if (colors == null) {
            ctx.fill(x, y, x + size, y + size, 0xFF1C2233);
            return;
        }
        checker(ctx, x, y, size, size, Math.max(2, cellPx / 2));
        for (int cy = 0; cy < cells; cy++) {
            for (int cx = 0; cx < cells; cx++) {
                int sx = cx * ImageConverter.SIZE / cells + ImageConverter.SIZE / (cells * 2);
                int sy = cy * ImageConverter.SIZE / cells + ImageConverter.SIZE / (cells * 2);
                int argb = ImageConverter.argbFor(colors[sy * ImageConverter.SIZE + sx]);
                if (argb == 0) continue;
                int x1 = x + cx * cellPx, y1 = y + cy * cellPx;
                ctx.fill(x1, y1, x1 + cellPx, y1 + cellPx, argb);
            }
        }
    }

    /** Trims text with an ellipsis so it fits in {@code maxWidth}. */
    static String fit(TextRenderer font, String text, int maxWidth) {
        if (font.getWidth(text) <= maxWidth) return text;
        String ellipsis = "...";
        int len = text.length();
        while (len > 0 && font.getWidth(text.substring(0, len) + ellipsis) > maxWidth) len--;
        return text.substring(0, len) + ellipsis;
    }
}
