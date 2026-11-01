package autosummon;

import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AutoSummonModSettings state management and serialization.
 */
public class AutoSummonModSettingsTest {

    private AutoSummonModSettings settings;

    @BeforeEach
    public void setUp() {
        settings = new AutoSummonModSettings();
    }

    @Test
    @DisplayName("Default settings should have auto-summon and join enabled with 0 global limit")
    public void testDefaultSettings() {
        assertTrue(settings.isEnabled(), "Mod should be enabled by default");
        assertTrue(settings.isAutoSummonOnJoin(), "Auto summon on join should be true by default");
        assertEquals(0, settings.getGlobalCustomSummonLimit(), "Global limit should default to 0");
        assertFalse(settings.hasGlobalCustomLimit(), "hasGlobalCustomLimit should be false when limit is 0");
        assertTrue(settings.getAllWeaponLimits().isEmpty(), "Weapon limits should initially be empty");
    }

    @Test
    @DisplayName("Setting and toggling enable state should work properly")
    public void testToggleEnable() {
        settings.setEnabled(false);
        assertFalse(settings.isEnabled());
        settings.setEnabled(true);
        assertTrue(settings.isEnabled());
    }

    @Test
    @DisplayName("Global toggle should flip the shared enabled state both ways")
    public void testGlobalToggle() {
        boolean initial = AutoSummonConfig.isEnabled();
        try {
            AutoSummonConfig.toggle();
            assertEquals(!initial, AutoSummonConfig.isEnabled());
            AutoSummonConfig.toggle();
            assertEquals(initial, AutoSummonConfig.isEnabled());
        } finally {
            AutoSummonConfig.getSettings().setEnabled(initial);
        }
    }

    @Test
    @DisplayName("Per-weapon summon limits should store, retrieve, case-insensitively match, and reset correctly")
    public void testWeaponSummonLimits() {
        // Auto (0) by default
        assertEquals(0, settings.getWeaponSummonLimit("skeletonstaff"));
        assertFalse(settings.isWeaponDisabled("skeletonstaff"));

        // Set custom limit
        settings.setWeaponSummonLimit("SkeletonStaff", 3);
        assertEquals(3, settings.getWeaponSummonLimit("skeletonstaff"), "Lookup should be case-insensitive");
        assertEquals(3, settings.getWeaponSummonLimit("SKELETONSTAFF"));
        assertFalse(settings.isWeaponDisabled("skeletonstaff"));

        // Disable weapon (-1)
        settings.setWeaponDisabled("skeletonstaff", true);
        assertEquals(-1, settings.getWeaponSummonLimit("skeletonstaff"));
        assertTrue(settings.isWeaponDisabled("skeletonstaff"));

        // Re-enable weapon
        settings.setWeaponDisabled("skeletonstaff", false);
        assertEquals(0, settings.getWeaponSummonLimit("skeletonstaff"));
        assertFalse(settings.isWeaponDisabled("skeletonstaff"));

        // Reset to auto via limit 0
        settings.setWeaponSummonLimit("skeletonstaff", 5);
        settings.setWeaponSummonLimit("skeletonstaff", 0);
        assertEquals(0, settings.getWeaponSummonLimit("skeletonstaff"));
    }

    @Test
    @DisplayName("Global custom summon limit should clamp negative values to 0")
    public void testGlobalCustomSummonLimitClamping() {
        settings.setGlobalCustomSummonLimit(5);
        assertEquals(5, settings.getGlobalCustomSummonLimit());
        assertTrue(settings.hasGlobalCustomLimit());

        settings.setGlobalCustomSummonLimit(-10);
        assertEquals(0, settings.getGlobalCustomSummonLimit());
        assertFalse(settings.hasGlobalCustomLimit());
    }

    @Test
    @DisplayName("Save and Load round-trip should preserve all configuration values")
    public void testSaveAndLoadRoundTrip() {
        settings.setEnabled(false);
        settings.setAutoSummonOnJoin(false);
        settings.setGlobalCustomSummonLimit(4);
        settings.setWeaponSummonLimit("skeletonstaff", 2);
        settings.setWeaponSummonLimit("redspiderstaff", -1);

        SaveData saveData = new SaveData("TEST_SETTINGS");
        settings.addSaveData(saveData);

        // Create fresh settings instance and apply loaded data
        AutoSummonModSettings loadedSettings = new AutoSummonModSettings();
        LoadData loadData = saveData.toLoadData();
        loadedSettings.applyLoadData(loadData);

        assertFalse(loadedSettings.isEnabled());
        assertFalse(loadedSettings.isAutoSummonOnJoin());
        assertEquals(4, loadedSettings.getGlobalCustomSummonLimit());
        assertEquals(2, loadedSettings.getWeaponSummonLimit("skeletonstaff"));
        assertEquals(-1, loadedSettings.getWeaponSummonLimit("redspiderstaff"));
        assertTrue(loadedSettings.isWeaponDisabled("redspiderstaff"));
    }

    @Test
    @DisplayName("Clear all weapon limits should reset all entries")
    public void testClearAllWeaponLimits() {
        settings.setWeaponSummonLimit("staff1", 2);
        settings.setWeaponSummonLimit("staff2", -1);
        settings.setWeaponSummonLimit("staff3", 5);

        assertEquals(3, settings.getAllWeaponLimits().size());
        settings.clearAllWeaponLimits();
        assertTrue(settings.getAllWeaponLimits().isEmpty());
    }
}
