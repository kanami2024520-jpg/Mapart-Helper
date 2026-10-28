package rh.pixelarthelper.conversion;

/** Dense block grid ready to be serialized as a schematic. */
public class PixelArtResult {

    public final int width;
    public final int height;
    public final int length;
    public final int[] blockIds;
    public final int[] metas;

    public PixelArtResult(int width, int height, int length, int[] blockIds, int[] metas) {
        this.width = width;
        this.height = height;
        this.length = length;
        this.blockIds = blockIds;
        this.metas = metas;
    }

    public int index(int x, int y, int z) {
        return x + (y * length + z) * width;
    }
}
