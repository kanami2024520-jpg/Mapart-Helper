package rh.pixelarthelper.command;

import java.io.File;
import java.io.IOException;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import rh.pixelarthelper.PixelArtHelper;
import rh.pixelarthelper.palette.PaletteCache;
import rh.pixelarthelper.palette.PaletteStorage;
import rh.pixelarthelper.palette.TexturePaletteScanner;

public class PixelArtCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "pixelart";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/pixelart palette <dump|info>";
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
        sender.addChatMessage(new ChatComponentText(EnumChatFormatting.YELLOW + getCommandUsage(sender)));
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
