package rh.pixelarthelper.palette;

import java.nio.IntBuffer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.IIcon;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/**
 * Reads the stitched block atlas once and samples icon regions from it.
 *
 * <p>
 * GTNH's texture pipeline (Angelica/Celeritas) does not keep frame pixel arrays for every
 * static sprite, so {@code getFrameTextureData} is not reliable after resource loading.
 * The atlas itself is always present in GL and works for both vanilla and CTM/wrapped icons.
 *
 * <p>
 * 1.7.10 uploads TextureUtil ARGB ints with BGRA/UNSIGNED_INT_8_8_8_8_REV; reading the same
 * format gives ARGB ints without channel swizzling and without a row flip.
 */
public final class AtlasSampler {

    private final int width;
    private final int height;
    private final int[] pixels;

    private AtlasSampler(int width, int height, int[] pixels) {
        this.width = width;
        this.height = height;
        this.pixels = pixels;
    }

    public static AtlasSampler capture() {
        TextureMap map = Minecraft.getMinecraft()
            .getTextureMapBlocks();
        int textureId = map.getGlTextureId();
        int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
            int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            if (width <= 0 || height <= 0) throw new IllegalStateException("Empty block atlas");

            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 4);
            GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, 0);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, 0);
            IntBuffer buffer = BufferUtils.createIntBuffer(width * height);
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, buffer);
            int error = GL11.glGetError();
            if (error != GL11.GL_NO_ERROR) {
                throw new IllegalStateException("glGetTexImage failed: 0x" + Integer.toHexString(error));
            }
            int[] pixels = new int[width * height];
            buffer.rewind();
            buffer.get(pixels);
            return new AtlasSampler(width, height, pixels);
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous);
        }
    }

    public Stats stats(IIcon icon) {
        int x;
        int y;
        int w;
        int h;
        if (icon instanceof TextureAtlasSprite) {
            TextureAtlasSprite sprite = (TextureAtlasSprite) icon;
            x = sprite.getOriginX();
            y = sprite.getOriginY();
            w = sprite.getIconWidth();
            h = sprite.getIconHeight();
        } else {
            x = (int) Math.floor(icon.getMinU() * width);
            y = (int) Math.floor(icon.getMinV() * height);
            w = (int) Math.ceil((icon.getMaxU() - icon.getMinU()) * width);
            h = (int) Math.ceil((icon.getMaxV() - icon.getMinV()) * height);
        }

        if (w <= 0 || h <= 0) return null;
        int x0 = Math.max(0, x);
        int y0 = Math.max(0, y);
        int x1 = Math.min(width, x + w);
        int y1 = Math.min(height, y + h);
        if (x0 >= x1 || y0 >= y1) return null;

        double sumAlpha = 0.0;
        long sumR = 0, sumG = 0, sumB = 0;
        int opaque = 0;
        for (int py = y0; py < y1; py++) {
            for (int px = x0; px < x1; px++) {
                int pixel = pixels[py * width + px];
                int alpha = (pixel >>> 24) & 0xFF;
                if (alpha < 8) continue;
                sumAlpha += alpha;
                sumR += (long) ((pixel >> 16) & 0xFF) * alpha;
                sumG += (long) ((pixel >> 8) & 0xFF) * alpha;
                sumB += (long) (pixel & 0xFF) * alpha;
                opaque++;
            }
        }
        if (sumAlpha <= 0.0) return null;

        double avgR = sumR / sumAlpha;
        double avgG = sumG / sumAlpha;
        double avgB = sumB / sumAlpha;
        double variance = 0.0;
        for (int py = y0; py < y1; py++) {
            for (int px = x0; px < x1; px++) {
                int pixel = pixels[py * width + px];
                int alpha = (pixel >>> 24) & 0xFF;
                if (alpha < 8) continue;
                double dr = ((pixel >> 16) & 0xFF) - avgR;
                double dg = ((pixel >> 8) & 0xFF) - avgG;
                double db = (pixel & 0xFF) - avgB;
                variance += (dr * dr + dg * dg + db * db) * alpha;
            }
        }

        Stats stats = new Stats();
        stats.avgR = avgR;
        stats.avgG = avgG;
        stats.avgB = avgB;
        stats.variance = Math.sqrt(variance / (3.0 * sumAlpha));
        stats.alphaCoverage = (double) opaque / (double) ((x1 - x0) * (y1 - y0));
        return stats;
    }

    public static final class Stats {

        public double avgR;
        public double avgG;
        public double avgB;
        public double variance;
        public double alphaCoverage;
    }
}
