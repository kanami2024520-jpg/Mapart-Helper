package rh.pixelarthelper.conversion;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

public final class ImageLoader {

    private ImageLoader() {}

    public static BufferedImage load(File file) throws IOException {
        BufferedImage image = ImageIO.read(file);
        if (image == null) {
            throw new IOException("Unsupported or unreadable image: " + file);
        }
        return image;
    }
}
