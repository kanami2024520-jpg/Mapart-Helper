package rh.pixelarthelper.palette;

import java.util.ArrayList;
import java.util.List;

/** Serialized result of a full texture-palette scan. */
public class PaletteCache {

    public int formatVersion = 1;
    public String minecraft = "1.7.10";
    public String generatedAt = "";
    public int blocksScanned;
    public int variantsSeen;
    public int variantsSkipped;
    public List<PaletteEntry> entries = new ArrayList<>();
}
