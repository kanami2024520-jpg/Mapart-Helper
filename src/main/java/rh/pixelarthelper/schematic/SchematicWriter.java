package rh.pixelarthelper.schematic;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import rh.pixelarthelper.conversion.PixelArtResult;

/**
 * Writes a classic MCEdit/WorldEdit Alpha schematic. When a block id or metadata value
 * cannot be represented by the classic 12-bit/4-bit format the file is upgraded to
 * Schematica-Plus's extended .schemplus container instead.
 */
public final class SchematicWriter {

    private SchematicWriter() {}

    public static File write(PixelArtResult result, File requested) throws IOException {
        boolean plusRequested = requested.getName()
            .toLowerCase(Locale.ROOT)
            .endsWith(".schemplus");
        boolean extended = plusRequested;

        for (int i = 0; i < result.blockIds.length; i++) {
            int id = result.blockIds[i];
            int meta = result.metas[i];
            if (id < 0 || id > 65535) throw new IOException("Unsupported block id: " + id);
            if (meta < 0 || meta > 65535) throw new IOException("Unsupported metadata: " + meta);
            if (id > 4095 || meta > 15) extended = true;
        }

        File target = normalize(requested, extended);
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create " + parent);
        }

        int size = result.width * result.height * result.length;
        byte[] blocks = new byte[size];
        byte[] data = new byte[size];
        byte[] highIds = new byte[size];
        byte[] highMeta = new byte[size];
        boolean hasHighMeta = false;
        Map<String, Short> mapping = new LinkedHashMap<>();

        for (int y = 0; y < result.height; y++) {
            for (int z = 0; z < result.length; z++) {
                for (int x = 0; x < result.width; x++) {
                    int index = result.index(x, y, z);
                    int id = result.blockIds[index];
                    int meta = result.metas[index];
                    blocks[index] = (byte) (id & 0xFF);
                    highIds[index] = (byte) ((id >>> 8) & 0xFF);
                    data[index] = (byte) (meta & 0xFF);
                    highMeta[index] = (byte) ((meta >>> 8) & 0xFF);
                    hasHighMeta |= highMeta[index] != 0;

                    Block block = Block.getBlockById(id);
                    if (block != null) {
                        String name = Block.blockRegistry.getNameForObject(block);
                        if (name != null) mapping.putIfAbsent(name, (short) id);
                    }
                }
            }
        }

        NBTTagCompound root = new NBTTagCompound();
        root.setString("Materials", "Alpha");
        root.setShort("Width", (short) result.width);
        root.setShort("Height", (short) result.height);
        root.setShort("Length", (short) result.length);
        root.setByteArray("Blocks", blocks);
        root.setByteArray("Data", data);

        if (extended) {
            root.setString("SchematicaBlockIdEncoding", "unsigned16");
            root.setByteArray("AddBlocks", highIds);
            if (hasHighMeta) root.setByteArray("SchematicaPlusDataHigh", highMeta);
        } else {
            root.setString("SchematicaBlockIdEncoding", "nibble12hi");
            byte[] packed = new byte[(size + 1) / 2];
            for (int i = 0; i < size; i++) {
                int high = highIds[i] & 0x0F;
                if (high == 0) continue;
                int shift = ((i & 1) ^ 1) * 4;
                packed[i / 2] |= (byte) (high << shift);
            }
            root.setByteArray("AddBlocks", packed);
        }

        NBTTagCompound mappingTag = new NBTTagCompound();
        for (Map.Entry<String, Short> entry : mapping.entrySet()) {
            mappingTag.setShort(entry.getKey(), entry.getValue());
        }
        root.setTag("SchematicaMapping", mappingTag);
        root.setTag("TileEntities", new NBTTagList());
        root.setTag("Entities", new NBTTagList());
        root.setInteger("WEOriginX", 0);
        root.setInteger("WEOriginY", 0);
        root.setInteger("WEOriginZ", 0);
        root.setInteger("WEOffsetX", 0);
        root.setInteger("WEOffsetY", 0);
        root.setInteger("WEOffsetZ", 0);

        try (FileOutputStream output = new FileOutputStream(target)) {
            CompressedStreamTools.writeCompressed(root, output);
        }
        return target;
    }

    private static File normalize(File requested, boolean extended) {
        File absolute = requested.getAbsoluteFile();
        String name = absolute.getName();
        String lower = name.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".schematic") && !lower.endsWith(".schemplus")) {
            name = name + (extended ? ".schemplus" : ".schematic");
        } else if (extended && lower.endsWith(".schematic")) {
            name = name.substring(0, name.length() - ".schematic".length()) + ".schemplus";
        }
        return new File(absolute.getParentFile(), name);
    }
}
