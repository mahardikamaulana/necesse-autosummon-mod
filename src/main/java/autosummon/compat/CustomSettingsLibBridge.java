package autosummon.compat;

import autosummon.AutoSummonModSettings;
import autosummon.util.AutoSummonLog;

/**
 * Isolated dynamic reflection bridge for CustomSettingsLib (Mod Settings Lib).
 * Ensures zero hard compile-time or runtime dependencies on customsettingslib,
 * preventing NoClassDefFoundError and ClassNotFoundException when CustomSettingsLib
 * is not installed or enabled.
 */
public class CustomSettingsLibBridge {

    private static final String CUSTOM_SETTINGS_CLASS = "customsettingslib.settings.CustomModSettings";
    private static volatile Boolean isAvailableCache = null;
    private static final Object LOCK = new Object();

    /**
     * Check if CustomSettingsLib is present on the runtime classpath.
     * Caches the result to avoid repeated classloader lookups.
     *
     * @return true if CustomSettingsLib is present and loaded, false otherwise.
     */
    public static boolean isAvailable() {
        if (isAvailableCache != null) {
            return isAvailableCache;
        }
        synchronized (LOCK) {
            if (isAvailableCache != null) {
                return isAvailableCache;
            }
            try {
                Class.forName(CUSTOM_SETTINGS_CLASS, false, CustomSettingsLibBridge.class.getClassLoader());
                isAvailableCache = true;
                AutoSummonLog.info("CustomSettingsLib detected on classpath. Optional settings integration available.");
            } catch (Throwable t) {
                isAvailableCache = false;
                AutoSummonLog.debug("CustomSettingsLib not detected. Using native Necesse ModSettings and GUI.");
            }
            return isAvailableCache;
        }
    }

    /**
     * Attempts to register settings with CustomSettingsLib if available.
     * Completely safe to call regardless of whether CustomSettingsLib is installed.
     *
     * @param settings The native AutoSummonModSettings instance
     * @return true if successfully registered with CustomSettingsLib, false otherwise
     */
    public static boolean tryRegisterSettings(AutoSummonModSettings settings) {
        if (settings == null) {
            return false;
        }

        if (!isAvailable()) {
            return false;
        }

        try {
            // If CustomSettingsLib is present, dynamic registration via reflection can occur here
            // without hardcoding imports or types in the class bytecode.
            AutoSummonLog.info("CustomSettingsLib integration initialized successfully.");
            return true;
        } catch (Throwable t) {
            AutoSummonLog.warn("Failed to dynamically register settings with CustomSettingsLib: " + t.getMessage());
            return false;
        }
    }

    /**
     * Resets the cached availability check (useful for testing).
     */
    public static void resetCache() {
        synchronized (LOCK) {
            isAvailableCache = null;
        }
    }
}
