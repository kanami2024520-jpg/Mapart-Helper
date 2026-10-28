package rh.pixelarthelper.command;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import net.minecraft.block.Block;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.init.Blocks;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import cpw.mods.fml.common.registry.GameData;
import rh.pixelarthelper.PixelArtHelper;
import rh.pixelarthelper.conversion.ImageLoader;
import rh.pixelarthelper.conversion.PixelArtConverter;
import rh.pixelarthelper.conversion.PixelArtResult;
import rh.pixelarthelper.palette.PaletteCache;
import rh.pixelarthelper.palette.PaletteStorage;
import rh.pixelarthelper.palette.TexturePaletteScanner;
import rh.pixelarthelper.schematic.SchematicWriter;

public class PixelArtCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "pixelart";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/pixelart <palette <dump|info>|export <image> <output> [width] [height] [backing|none]>";
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length >= 1 && "palette".equalsIgnoreCase(args[0])) {
            String action = args.length >= 2 ? args[1].toLowerCase() : "dump";
            if ("dump".equals(action) || "regenerate".equals(action)) {
                dumpPalette(sender);
                return;
            }
            if ("info".equals(action)) {
                printInfo(sender);
                return;
            }
        }
        if (args.length >= 3 && "export".equalsIgnoreCase(args[0])) {
            export(sender, args);
            return;
        }
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + getCommandUsage(sender)));
    }

    private void export(ICommandSender sender, String[] args) {
        try {
            File imageFile = new File(args[1]);
            if (!imageFile.isAbsolute()) imageFile = new File(PaletteStorage.directory(), args[1]);
            BufferedImage image = ImageLoader.load(imageFile);

            int width = args.length >= 4 ? Integer.parseInt(args[3]) : 128;
            int height = args.length >= 5 ? Integer.parseInt(args[4]) : 0;

            Block backing = Blocks.stone;
            if (args.length >= 6) {
                if ("none".equalsIgnoreCase(args[5])) {
                    backing = null;
                } else {
                    Block requested = GameData.getBlockRegistry()
                        .getObject(args[5]);
                    if (requested == null || requested == Blocks.air) {
                        sender.addChatMessage(
                            new ChatComponentText(EnumChatFormatting.RED + "[PixelArt] Unknown backing block: " + args[5]));
                        return;
                    }
                    backing = requested;
                }
            }

            PaletteCache palette = PaletteStorage.load();
            if (palette == null || palette.entries.isEmpty()) {
                sender.addChatMessage(
                    new ChatComponentText(EnumChatFormatting.YELLOW + "[PixelArt] No palette cache; scanning textures..."));
                palette = TexturePaletteScanner.scan();
                PaletteStorage.save(palette);
            }

            PixelArtResult result = PixelArtConverter.convert(image, palette, width, height, backing != null, backing);

            File output = new File(args[2]);
            if (!output.isAbsolute()) {
                output = new File(new File(PaletteStorage.directory(), "exports"), args[2]);
            }
            File saved = SchematicWriter.write(result, output);

            sender.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.GREEN + "[PixelArt] Wrote "
                        + result.width
                        + "x"
                        + result.height
                        + "x"
                        + result.length
                        + " to "
                        + saved.getAbsolutePath()));
        } catch (Throwable t) {
            PixelArtHelper.LOG.error("Export failed", t);
            sender.addChatMessage(
                new ChatComponentText(EnumChatFormatting.RED + "[PixelArt] Export failed: " + t.getMessage()));
        }
    }

    private void dumpPalette(ICommandSender sender) {
        sender.addChatMessage(
            new ChatComponentText(EnumChatFormatting.YELLOW + "[PixelArt] Scanning block textures..."));
        try {
            PaletteCache cache = TexturePaletteScanner.scan();
            File file = PaletteStorage.save(cache);
            sender.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.GREEN + "[PixelArt] Wrote "
                        + cache.entries.size()
                        + " entries to "
                        + file.getAbsolutePath()));
        } catch (Throwable t) {
            PixelArtHelper.LOG.error("Palette scan failed", t);
            sender.addChatMessage(
                new ChatComponentText(EnumChatFormatting.RED + "[PixelArt] Scan failed: " + t.getMessage()));
        }
    }

    private void printInfo(ICommandSender sender) {
        try {
            PaletteCache cache = PaletteStorage.load();
            if (cache == null) {
                sender.addChatMessage(
                    new ChatComponentText(
                        EnumChatFormatting.RED + "[PixelArt] No palette cache yet; run /pixelart palette dump"));
                return;
            }
            sender.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.GREEN + "[PixelArt] "
                        + cache.entries.size()
                        + " entries, generated "
                        + cache.generatedAt
                        + ", scanned "
                        + cache.blocksScanned
                        + " blocks"));
            sender.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.GRAY + "[PixelArt] Cache: " + PaletteStorage.file()
                        .getAbsolutePath()));
        } catch (IOException e) {
            sender.addChatMessage(
                new ChatComponentText(EnumChatFormatting.RED + "[PixelArt] Could not read cache: " + e.getMessage()));
        }
    }
}
