package rh.pixelarthelper.palette;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.util.IIcon;

import cpw.mods.fml.common.registry.GameData;
import rh.pixelarthelper.PixelArtHelper;
import rh.pixelarthelper.util.ColorUtil;

/**
 * Scans every registered block/metadata variant and records the average colour of a
 * visible face texture. This is deliberately a client-side development tool: the icon
 * data only exists after the texture atlas has been stitched.
 */
public final class TexturePaletteScanner {

    /** Prefer the south side; fall back to the other sides/top/bottom for odd blocks. */
    private static final int[] FACE_ORDER = { 3, 2, 4, 5, 1, 0 };

    private TexturePaletteScanner() {}

    public static PaletteCache scan() {
        PaletteCache cache = new PaletteCache();
        cache.generatedAt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX").format(new Date());

        Map<String, PaletteEntry> unique = new LinkedHashMap<>();
        Iterator<Block> iterator = GameData.getBlockRegistry()
            .iterator();

        while (iterator.hasNext()) {
            Block block = iterator.next();
            if (block == null || block == Blocks.air) continue;

            cache.blocksScanned++;
            String blockName = String.valueOf(Block.blockRegistry.getNameForObject(block));
            String modId = blockName.indexOf(':') > 0 ? blockName.substring(0, blockName.indexOf(':')) : "minecraft";
            int blockId = Block.getIdFromBlock(block);
            int renderType = block.getRenderType();
            boolean fullCube = block.renderAsNormalBlock() && block.isOpaqueCube();
            boolean hasItem = Item.getItemFromBlock(block) != null;
            boolean hasTileEntity = block instanceof ITileEntityProvider;

            for (int meta = 0; meta < 16; meta++) {
                IIcon icon = null;
                int face = 3;
                for (int candidateFace : FACE_ORDER) {
                    try {
                        IIcon candidate = block.getIcon(candidateFace, meta);
                        if (candidate != null) {
                            icon = candidate;
                            face = candidateFace;
                            break;
                        }
                    } catch (Throwable ignored) {
                        // Some GTNH blocks only support specific metadata/faces.
                    }
                }
                if (!(icon instanceof TextureAtlasSprite)) {
                    cache.variantsSkipped++;
                    continue;
                }

                int tint = renderColor(block, meta);
                String iconName = String.valueOf(icon.getIconName());
                String key = iconName + '|' + tint + '|' + face;
                PaletteEntry duplicate = unique.get(key);
                if (duplicate != null) {
                    duplicate.duplicates++;
                    continue;
                }

                TextureAtlasSprite sprite = (TextureAtlasSprite) icon;
                int[][] frames;
                int[] pixels;
                try {
                    frames = sprite.getFrameTextureData(0);
                    if (frames == null || frames.length == 0 || frames[0] == null) {
                        cache.variantsSkipped++;
                        continue;
                    }
                    pixels = frames[0];
                } catch (Throwable t) {
                    cache.variantsSkipped++;
                    continue;
                }

                int width = Math.max(1, sprite.getIconWidth());
                int height = Math.max(1, sprite.getIconHeight());
                if (pixels.length < width * height) {
                    cache.variantsSkipped++;
                    continue;
                }

                double sumAlpha = 0.0;
                long sumR = 0, sumG = 0, sumB = 0;
                int opaquePixels = 0;
                for (int i = 0; i < width * height; i++) {
                    int pixel = pixels[i];
                    int alpha = (pixel >>> 24) & 0xFF;
                    if (alpha < 8) continue;
                    int r = (pixel >> 16) & 0xFF;
                    int g = (pixel >> 8) & 0xFF;
                    int b = pixel & 0xFF;
                    sumAlpha += alpha;
                    sumR += (long) r * alpha;
                    sumG += (long) g * alpha;
                    sumB += (long) b * alpha;
                    opaquePixels++;
                }
                if (sumAlpha <= 0.0) {
                    cache.variantsSkipped++;
                    continue;
                }

                double avgR = sumR / sumAlpha;
                double avgG = sumG / sumAlpha;
                double avgB = sumB / sumAlpha;

                double varianceSum = 0.0;
                for (int i = 0; i < width * height; i++) {
                    int pixel = pixels[i];
                    int alpha = (pixel >>> 24) & 0xFF;
                    if (alpha < 8) continue;
                    double dr = ((pixel >> 16) & 0xFF) - avgR;
                    double dg = ((pixel >> 8) & 0xFF) - avgG;
                    double db = (pixel & 0xFF) - avgB;
                    varianceSum += (dr * dr + dg * dg + db * db) * alpha;
                }

                int r = applyTint((int) Math.round(avgR), tint, 16);
                int g = applyTint((int) Math.round(avgG), tint, 8);
                int b = applyTint((int) Math.round(avgB), tint, 0);
                int argb = ColorUtil.argb(r, g, b);

                PaletteEntry entry = new PaletteEntry();
                entry.blockId = blockId;
                entry.blockName = blockName;
                entry.modId = modId;
                entry.meta = meta;
                entry.face = face;
                entry.iconName = iconName;
                entry.tint = tint;
                entry.argb = argb;
                entry.lab = ColorUtil.srgbToLab(argb);
                entry.variance = Math.sqrt(varianceSum / (3.0 * sumAlpha));
                entry.alphaCoverage = (double) opaquePixels / (double) (width * height);
                entry.fullCube = fullCube;
                entry.hasItem = hasItem;
                entry.hasTileEntity = hasTileEntity;
                entry.renderType = renderType;
                unique.put(key, entry);
                cache.variantsSeen++;
            }
        }

        cache.entries = new ArrayList<>(unique.values());
        PixelArtHelper.LOG.info(
            "Texture palette scan: {} blocks, {} variants, {} unique, {} skipped",
            cache.blocksScanned,
            cache.variantsSeen,
            cache.entries.size(),
            cache.variantsSkipped);
        return cache;
    }

    private static int renderColor(Block block, int meta) {
        try {
            int color = block.getRenderColor(meta);
            return color == 0 ? 0xFFFFFF : color;
        } catch (Throwable t) {
            return 0xFFFFFF;
        }
    }

    private static int applyTint(int channel, int tint, int shift) {
        int factor = (tint >> shift) & 0xFF;
        return Math.min(255, Math.max(0, channel * factor / 255));
    }
}
