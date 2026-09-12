package autosummon.compat;

import autosummon.AutoSummonModSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CustomSettingsLibBridge.
 * Verifies that the mod runs smoothly without throwing NoClassDefFoundError or ClassNotFoundException
 * when CustomSettingsLib is not installed.
 */
public class CustomSettingsLibBridgeTest {

    @BeforeEach
    public void setUp() {
        CustomSettingsLibBridge.resetCache();
    }

    @Test
    @DisplayName("isAvailable should return false gracefully when CustomSettingsLib is missing without crashing")
    public void testIsAvailableWhenMissing() {
        // CustomSettingsLib is not on standard test classpath
        boolean available = CustomSettingsLibBridge.isAvailable();
        assertFalse(available, "CustomSettingsLib should report false when not present");
    }

    @Test
    @DisplayName("tryRegisterSettings should return false safely with null settings or when library is absent")
    public void testTryRegisterSettingsWhenMissing() {
        assertFalse(CustomSettingsLibBridge.tryRegisterSettings(null));

        AutoSummonModSettings settings = new AutoSummonModSettings();
        boolean registered = CustomSettingsLibBridge.tryRegisterSettings(settings);
        assertFalse(registered, "Registration should return false gracefully when library is not present");
    }

    @Test
    @DisplayName("Multiple calls to isAvailable should use cache and never throw exceptions")
    public void testCachingAndResilience() {
        for (int i = 0; i < 10; i++) {
            assertDoesNotThrow(CustomSettingsLibBridge::isAvailable);
            assertFalse(CustomSettingsLibBridge.isAvailable());
        }
    }
}
