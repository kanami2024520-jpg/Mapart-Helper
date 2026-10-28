package rh.pixelarthelper.palette;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import net.minecraft.client.Minecraft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public final class PaletteStorage {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .create();

    private PaletteStorage() {}

    public static File directory() {
        File base = Minecraft.getMinecraft() == null ? new File(".") : Minecraft.getMinecraft().mcDataDir;
        return new File(base, "config/pixelarthelper");
    }

    public static File file() {
        return new File(directory(), "block_colors.json");
    }

    public static File save(PaletteCache cache) throws IOException {
        File dir = directory();
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Could not create " + dir);
        }
        File target = file();
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(target), StandardCharsets.UTF_8)) {
            GSON.toJson(cache, writer);
        }
        return target;
    }

    public static PaletteCache load() throws IOException {
        File target = file();
        if (!target.isFile()) return null;
        try (Reader reader = new InputStreamReader(new FileInputStream(target), StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, PaletteCache.class);
        }
    }
}
