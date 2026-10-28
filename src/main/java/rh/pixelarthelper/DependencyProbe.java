package rh.pixelarthelper;

/**
 * Compile-time/runtime presence check for the GTNH libraries this mod builds on.
 *
 * <p>
 * This is intentionally tiny for the bootstrap milestone: it makes CI fail if the
 * dependency coordinates stop resolving, and makes a dev-client launch fail fast if the
 * libraries are missing.
 */
public final class DependencyProbe {

    private DependencyProbe() {}

    public static void verify() {
        Class<?> modularUi = com.cleanroommc.modularui.screen.ModularScreen.class;
        Class<?> gtnhLib = com.gtnewhorizon.gtnhlib.config.ConfigurationManager.class;
        PixelArtHelper.LOG.info("Dependencies present: {} / {}", modularUi.getName(), gtnhLib.getName());
    }
}
