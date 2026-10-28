package rh.pixelarthelper.util;

/** Small sRGB/Lab helper used by the texture palette matcher. */
public final class ColorUtil {

    private ColorUtil() {}

    public static int argb(int r, int g, int b) {
        return 0xFF000000 | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    public static int red(int argb) {
        return (argb >> 16) & 0xFF;
    }

    public static int green(int argb) {
        return (argb >> 8) & 0xFF;
    }

    public static int blue(int argb) {
        return argb & 0xFF;
    }

    /** sRGB (D65) to CIELAB. */
    public static double[] srgbToLab(int argb) {
        double r = linearize(red(argb) / 255.0);
        double g = linearize(green(argb) / 255.0);
        double b = linearize(blue(argb) / 255.0);

        double x = (r * 0.4124 + g * 0.3576 + b * 0.1805) / 0.95047;
        double y = (r * 0.2126 + g * 0.7152 + b * 0.0722) / 1.00000;
        double z = (r * 0.0193 + g * 0.1192 + b * 0.9505) / 1.08883;

        double fx = pivotXyz(x);
        double fy = pivotXyz(y);
        double fz = pivotXyz(z);

        return new double[] { 116.0 * fy - 16.0, 500.0 * (fx - fy), 200.0 * (fy - fz) };
    }

    public static double distanceSquared(double[] a, double[] b) {
        double dl = a[0] - b[0];
        double da = a[1] - b[1];
        double db = a[2] - b[2];
        return dl * dl + da * da + db * db;
    }

    private static double linearize(double c) {
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static double pivotXyz(double t) {
        return t > 0.008856 ? Math.cbrt(t) : (7.787 * t + 16.0 / 116.0);
    }

    private static int clamp(int value) {
        return value < 0 ? 0 : value > 255 ? 255 : value;
    }
}
