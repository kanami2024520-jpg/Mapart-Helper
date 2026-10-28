package rh.pixelarthelper.conversion;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

import rh.pixelarthelper.palette.PaletteCache;
import rh.pixelarthelper.palette.PaletteEntry;
import rh.pixelarthelper.util.ColorUtil;

/** Converts an image into a vertical wall of blocks selected by visible texture colour. */
public final class PixelArtConverter {

    private static final int ALPHA_THRESHOLD = 16;
    private static final int MAX_SIDE = 2048;

    private PixelArtConverter() {}

    public static PixelArtResult convert(BufferedImage source, PaletteCache palette, int targetWidth, int targetHeight,
        boolean backing, Block backingBlock) {
        if (palette == null || palette.entries.isEmpty()) {
            throw new IllegalArgumentException("Texture palette is empty");
        }
        if (targetWidth <= 0) throw new IllegalArgumentException("Width must be positive");
        if (targetWidth > MAX_SIDE) throw new IllegalArgumentException("Width is limited to " + MAX_SIDE);

        int width = targetWidth;
        int height = targetHeight > 0 ? targetHeight
            : Math.max(1, (int) Math.round((double) source.getHeight() * width / source.getWidth()));
        if (height > MAX_SIDE) throw new IllegalArgumentException("Height is limited to " + MAX_SIDE);

        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }

        Block backingActual = backing ? (backingBlock == null ? Blocks.stone : backingBlock) : null;
        int length = backingActual == null ? 1 : 2;
        int frontZ = backingActual == null ? 0 : 1;
        int[] ids = new int[width * height * length];
        int[] metas = new int[width * height * length];

        if (backingActual != null) {
            int backingId = Block.getIdFromBlock(backingActual);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int index = x + (y * length) * width;
                    ids[index] = backingId;
                }
            }
        }

        LabKdTree tree = new LabKdTree(palette.entries);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = scaled.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xFF;
                if (alpha < ALPHA_THRESHOLD) continue;

                PaletteEntry entry = tree.nearest(ColorUtil.srgbToLab(argb));
                if (entry == null) continue;
                int index = x + (y * length + frontZ) * width;
                ids[index] = entry.blockId;
                metas[index] = entry.meta;
            }
        }

        return new PixelArtResult(width, height, length, ids, metas);
    }
}
