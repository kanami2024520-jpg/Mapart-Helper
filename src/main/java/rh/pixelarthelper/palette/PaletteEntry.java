package rh.pixelarthelper.palette;

/** One visible block variant in the texture palette. */
public class PaletteEntry {

    public int blockId;
    public String blockName;
    public String modId;
    public int meta;
    public int face;
    public String iconName;
    public int tint;
    public int argb;
    public double[] lab;
    public double variance;
    public double alphaCoverage;
    public boolean fullCube;
    public boolean hasItem;
    public boolean hasTileEntity;
    public int renderType;
    public int duplicates;

    public PaletteEntry() {}
}
