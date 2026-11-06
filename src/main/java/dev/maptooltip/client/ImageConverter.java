package dev.maptooltip.client;

import net.minecraft.block.MapColor;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns any picture into 128x128 map colour bytes.
 *
 * Pipeline:
 *   1. centre-crop to a square
 *   2. halve repeatedly with smooth filtering (avoids the mushy look of one big downscale)
 *   3. final bicubic scale to 128x128
 *   4. error-diffusion dithering onto the real map palette, matching colours in CIELAB so the
 *      nearest match looks right to the eye
 *
 * The result is still 128x128 map pixels, so it keeps the blocky map-art look, while the
 * dithering keeps gradients smooth instead of banded.
 */
public final class ImageConverter {
    public static final int SIZE = 128;

    /** How strongly quantisation error is spread to neighbouring pixels (0 = none, 1 = full). */
    private static final float DITHER_STRENGTH = 0.8f;

    /** ARGB colour for every possible map byte. Index 0 (and any transparent slot) is 0. */
    private static final int[] ARGB_BY_BYTE = new int[256];

    /** Usable palette entries (map byte values that actually draw something). */
    private static final byte[] PAL_BYTE;
    private static final float[] PAL_L, PAL_A, PAL_B;

    static {
        List<Integer> usable = new ArrayList<>();
        for (int b = 4; b < 256; b++) { // ids 0 (NONE) are transparent
            int c = MapColor.getRenderColor((byte) b);
            int rgb = c & 0xFFFFFF;
            if (rgb == 0 && ((c >>> 24) & 0xFF) == 0) continue;
            ARGB_BY_BYTE[b] = 0xFF000000 | rgb;
            usable.add(b);
        }

        PAL_BYTE = new byte[usable.size()];
        PAL_L = new float[usable.size()];
        PAL_A = new float[usable.size()];
        PAL_B = new float[usable.size()];
        for (int i = 0; i < usable.size(); i++) {
            int b = usable.get(i);
            int argb = ARGB_BY_BYTE[b];
            PAL_BYTE[i] = (byte) b;
            float[] lab = toLab((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF);
            PAL_L[i] = lab[0];
            PAL_A[i] = lab[1];
            PAL_B[i] = lab[2];
        }
    }

    private ImageConverter() {}

    /** ARGB colour for a stored map byte, or 0 for transparent. */
    public static int argbFor(byte mapByte) {
        return ARGB_BY_BYTE[mapByte & 0xFF];
    }

    /** Reads an image file and returns 16384 map colour bytes. Throws with a readable message on failure. */
    public static byte[] convert(Path file) throws IOException {
        BufferedImage src = ImageIO.read(file.toFile());
        if (src == null) {
            throw new IOException("Unsupported or unreadable image (use PNG, JPG, GIF or BMP)");
        }
        return convert(src);
    }

    /** Converts an image that is already loaded, such as one pasted from the clipboard. */
    public static byte[] convert(BufferedImage src) {
        // Normalise to ARGB so transparency and odd colour models survive.
        BufferedImage argb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D base = argb.createGraphics();
        base.drawImage(src, 0, 0, null);
        base.dispose();

        // 1. centre-crop to a square
        int side = Math.min(argb.getWidth(), argb.getHeight());
        int sx = (argb.getWidth() - side) / 2;
        int sy = (argb.getHeight() - side) / 2;
        BufferedImage square = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
        Graphics2D c = square.createGraphics();
        c.drawImage(argb, 0, 0, side, side, sx, sy, sx + side, sy + side, null);
        c.dispose();

        // 2. halve repeatedly with smooth filtering while the image is much bigger than the target
        BufferedImage current = square;
        while (current.getWidth() > SIZE * 2) {
            int half = current.getWidth() / 2;
            BufferedImage next = new BufferedImage(half, half, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = next.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(current, 0, 0, half, half, null);
            g.dispose();
            current = next;
        }

        // 3. final scale to 128x128
        BufferedImage scaled = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(current, 0, 0, SIZE, SIZE, null);
        g.dispose();

        // 4. palette matching with dithering
        return quantize(scaled);
    }

    /** Floyd-Steinberg dithering onto the map palette, matching in CIELAB. */
    private static byte[] quantize(BufferedImage img) {
        int n = SIZE * SIZE;
        float[] r = new float[n];
        float[] g = new float[n];
        float[] b = new float[n];
        boolean[] clear = new boolean[n];

        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int i = y * SIZE + x;
                int argb = img.getRGB(x, y);
                if (((argb >>> 24) & 0xFF) < 128) {
                    clear[i] = true;
                } else {
                    r[i] = (argb >> 16) & 0xFF;
                    g[i] = (argb >> 8) & 0xFF;
                    b[i] = argb & 0xFF;
                }
            }
        }

        byte[] out = new byte[n];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int i = y * SIZE + x;
                if (clear[i]) {
                    out[i] = 0;
                    continue;
                }
                float cr = clamp(r[i]), cg = clamp(g[i]), cb = clamp(b[i]);
                int best = nearestLab(cr, cg, cb);
                out[i] = PAL_BYTE[best];

                // error uses the RGB of the chosen palette colour, scaled for a softer map look
                int argb = ARGB_BY_BYTE[PAL_BYTE[best] & 0xFF];
                float er = (cr - ((argb >> 16) & 0xFF)) * DITHER_STRENGTH;
                float eg = (cg - ((argb >> 8) & 0xFF)) * DITHER_STRENGTH;
                float eb = (cb - (argb & 0xFF)) * DITHER_STRENGTH;
                diffuse(r, g, b, clear, x + 1, y, er, eg, eb, 7f / 16f);
                diffuse(r, g, b, clear, x - 1, y + 1, er, eg, eb, 3f / 16f);
                diffuse(r, g, b, clear, x, y + 1, er, eg, eb, 5f / 16f);
                diffuse(r, g, b, clear, x + 1, y + 1, er, eg, eb, 1f / 16f);
            }
        }
        return out;
    }

    private static void diffuse(float[] r, float[] g, float[] b, boolean[] clear,
                                int x, int y, float er, float eg, float eb, float factor) {
        if (x < 0 || y < 0 || x >= SIZE || y >= SIZE) return;
        int j = y * SIZE + x;
        if (clear[j]) return;
        r[j] += er * factor;
        g[j] += eg * factor;
        b[j] += eb * factor;
    }

    /** Nearest palette entry by CIELAB distance, which matches how the eye judges colour difference. */
    private static int nearestLab(float r, float g, float b) {
        float[] lab = toLab(r, g, b);
        int best = 0;
        float bestDist = Float.MAX_VALUE;
        for (int i = 0; i < PAL_BYTE.length; i++) {
            float dl = lab[0] - PAL_L[i];
            float da = lab[1] - PAL_A[i];
            float db = lab[2] - PAL_B[i];
            float d = dl * dl + da * da + db * db;
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        return best;
    }

    /** sRGB (0-255) to CIELAB (D65). */
    private static float[] toLab(float r, float g, float b) {
        double rl = linearize(r / 255.0), gl = linearize(g / 255.0), bl = linearize(b / 255.0);
        double x = (0.4124 * rl + 0.3576 * gl + 0.1805 * bl) / 0.95047;
        double y = (0.2126 * rl + 0.7152 * gl + 0.0722 * bl);
        double z = (0.0193 * rl + 0.1192 * gl + 0.9505 * bl) / 1.08883;
        double fx = labF(x), fy = labF(y), fz = labF(z);
        return new float[]{
                (float) (116 * fy - 16),
                (float) (500 * (fx - fy)),
                (float) (200 * (fy - fz))
        };
    }

    private static double linearize(double c) {
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static double labF(double t) {
        return t > 0.008856 ? Math.cbrt(t) : 7.787 * t + 16.0 / 116.0;
    }

    private static float clamp(float v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }
}
