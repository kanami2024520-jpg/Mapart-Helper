package rh.pixelarthelper;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.Writer;

import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import rh.pixelarthelper.conversion.PixelArtConverter;
import rh.pixelarthelper.conversion.PixelArtResult;
import rh.pixelarthelper.palette.PaletteCache;
import rh.pixelarthelper.palette.PaletteStorage;
import rh.pixelarthelper.palette.TexturePaletteScanner;
import rh.pixelarthelper.schematic.SchematicWriter;

/**
 * Optional smoke test used while bringing the 1.7.10 port up.
 *
 * <p>
 * Create {@code config/pixelarthelper/selftest.flag} and start the client. Once the
 * block atlas exists the client scans the texture palette, writes a small schematic
 * and shuts down. This is intentionally dormant during normal play.
 */
public final class SelfTest {

    private static boolean done;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.getTextureMapBlocks() == null) return;
        if (minecraft.getTextureMapBlocks()
            .getGlTextureId() <= 0) return;

        File flag = new File(PaletteStorage.directory(), "selftest.flag");
        if (!flag.isFile()) return;
        done = true;

        PixelArtHelper.LOG.info("PIXELART_SELFTEST_START");
        try {
            PaletteCache palette = TexturePaletteScanner.scan();
            PaletteStorage.save(palette);

            BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 32; y++) {
                for (int x = 0; x < 32; x++) {
                    image.setRGB(x, y, 0xFF000000 | (x * 8 << 16) | (y * 8 << 8) | 0x80);
                }
            }

            PixelArtResult result = PixelArtConverter.convert(image, palette, 32, 32, true, Blocks.stone);
            File output = new File(new File(PaletteStorage.directory(), "exports"), "selftest.schematic");
            File saved = SchematicWriter.write(result, output);

            try (Writer writer = new FileWriter(new File(PaletteStorage.directory(), "selftest-result.txt"))) {
                writer.write("OK\nentries=" + palette.entries.size() + "\n");
                writer.write("schematic=" + saved.getAbsolutePath() + "\n");
                writer.write("size=" + result.width + "x" + result.height + "x" + result.length + "\n");
            }
            PixelArtHelper.LOG.info("PIXELART_SELFTEST_OK {}", saved.getAbsolutePath());
        } catch (Throwable t) {
            PixelArtHelper.LOG.error("PIXELART_SELFTEST_FAIL", t);
            try (Writer writer = new FileWriter(new File(PaletteStorage.directory(), "selftest-result.txt"))) {
                writer.write("FAIL\n" + t + "\n");
            } catch (Throwable ignored) {
                // Nothing else we can do.
            }
        } finally {
            minecraft.shutdown();
        }
    }
}
