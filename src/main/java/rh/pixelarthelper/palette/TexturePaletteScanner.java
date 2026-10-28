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

        AtlasSampler atlas = null;
        try {
            atlas = AtlasSampler.capture();
        } catch (Throwable t) {
            PixelArtHelper.LOG.warn("Could not read the block atlas; falling back to sprite frame data", t);
        }

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
                IIcon fallback = null;
                int face = 3;
                for (int candidateFace : FACE_ORDER) {
                    try {
                        IIcon candidate = block.getIcon(candidateFace, meta);
                        if (candidate == null) continue;
                        if (fallback == null) fallback = candidate;
                        if (candidate instanceof TextureAtlasSprite) {
                            icon = candidate;
                            face = candidateFace;
                            break;
                        }
                    } catch (Throwable ignored) {
                        // Some GTNH blocks only support specific metadata/faces.
                    }
                }
                if (icon == null) icon = fallback;
                if (icon == null) {
                    cache.skippedNoIcon++;
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

                AtlasSampler.Stats stats = atlas == null ? null : atlas.stats(icon);
                if (stats == null && icon instanceof TextureAtlasSprite) {
                    stats = statsFromFrames((TextureAtlasSprite) icon);
                    if (stats == null) cache.skippedNoFrames++;
                } else if (stats == null) {
                    cache.skippedNotSprite++;
                }
                if (stats == null) {
                    cache.variantsSkipped++;
                    continue;
                }
                if (stats.alphaCoverage <= 0.0) {
                    cache.skippedTransparent++;
                    cache.variantsSkipped++;
                    continue;
                }

                int r = applyTint((int) Math.round(stats.avgR), tint, 16);
                int g = applyTint((int) Math.round(stats.avgG), tint, 8);
                int b = applyTint((int) Math.round(stats.avgB), tint, 0);
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
                entry.variance = stats.variance;
                entry.alphaCoverage = stats.alphaCoverage;
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
            "Texture palette scan: {} blocks, {} variants, {} unique, {} skipped (noIcon={}, notSprite={}, noFrames={}, short={}, transparent={})",
            cache.blocksScanned,
            cache.variantsSeen,
            cache.entries.size(),
            cache.variantsSkipped,
            cache.skippedNoIcon,
            cache.skippedNotSprite,
            cache.skippedNoFrames,
            cache.skippedShortPixels,
            cache.skippedTransparent);
        return cache;
    }

    private static AtlasSampler.Stats statsFromFrames(TextureAtlasSprite sprite) {
        int[][] frames;
        int[] pixels;
        try {
            frames = sprite.getFrameTextureData(0);
            if (frames == null || frames.length == 0 || frames[0] == null) return null;
            pixels = frames[0];
        } catch (Throwable t) {
            return null;
        }

        int width = Math.max(1, sprite.getIconWidth());
        int height = Math.max(1, sprite.getIconHeight());
        if (pixels.length < width * height) return null;

        double sumAlpha = 0.0;
        long sumR = 0, sumG = 0, sumB = 0;
        int opaquePixels = 0;
        for (int i = 0; i < width * height; i++) {
            int pixel = pixels[i];
            int alpha = (pixel >>> 24) & 0xFF;
            if (alpha < 8) continue;
            sumAlpha += alpha;
            sumR += (long) ((pixel >> 16) & 0xFF) * alpha;
            sumG += (long) ((pixel >> 8) & 0xFF) * alpha;
            sumB += (long) (pixel & 0xFF) * alpha;
            opaquePixels++;
        }
        if (sumAlpha <= 0.0) return null;

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

        AtlasSampler.Stats stats = new AtlasSampler.Stats();
        stats.avgR = avgR;
        stats.avgG = avgG;
        stats.avgB = avgB;
        stats.variance = Math.sqrt(varianceSum / (3.0 * sumAlpha));
        stats.alphaCoverage = (double) opaquePixels / (double) (width * height);
        return stats;
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
