package autosummon;

import necesse.engine.save.LoadData;
import necesse.engine.save.SaveData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests verifying manual weapon limit configuration, high ceiling bounds,
 * persistence across save/load, and resilience against summon slot fluctuations.
 */
public class ManualLimitAndSlotModTest {

    private AutoSummonModSettings settings;

    @BeforeEach
    public void setUp() {
        settings = new AutoSummonModSettings();
    }

    @Test
    @DisplayName("Manual limits can be set beyond standard base summon slot limits (up to MAX_MANUAL_LIMIT)")
    public void testManualLimitSetting() {
        // Default is Auto (0)
        assertEquals(0, settings.getWeaponSummonLimit("skeletonstaff"));

        // Set manual limit of 10
        settings.setWeaponSummonLimit("skeletonstaff", 10);
        assertEquals(10, settings.getWeaponSummonLimit("skeletonstaff"));

        // Set manual limit of 50 (MAX_MANUAL_LIMIT)
        settings.setWeaponSummonLimit("skeletonstaff", AutoSummonModSettings.MAX_MANUAL_LIMIT);
        assertEquals(50, settings.getWeaponSummonLimit("skeletonstaff"));

        // Set another weapon to Disabled (-1)
        settings.setWeaponSummonLimit("redspiderstaff", -1);
        assertEquals(-1, settings.getWeaponSummonLimit("redspiderstaff"));
        assertTrue(settings.isWeaponDisabled("redspiderstaff"));

        // Reset to Auto (0)
        settings.setWeaponSummonLimit("skeletonstaff", 0);
        assertEquals(0, settings.getWeaponSummonLimit("skeletonstaff"));
    }

    @Test
    @DisplayName("Manual limits persist across save and load round trips without being clamped to 1")
    public void testManualLimitSaveAndLoadPersistence() {
        settings.setWeaponSummonLimit("skeletonstaff", 8);
        settings.setWeaponSummonLimit("vampirewings", 15);
        settings.setWeaponSummonLimit("caveglowstaff", -1);
        settings.setGlobalCustomSummonLimit(12);

        SaveData saveData = new SaveData("TEST_SAVE");
        settings.addSaveData(saveData);

        AutoSummonModSettings loaded = new AutoSummonModSettings();
        LoadData loadData = saveData.toLoadData();
        loaded.applyLoadData(loadData);

        assertEquals(8, loaded.getWeaponSummonLimit("skeletonstaff"));
        assertEquals(15, loaded.getWeaponSummonLimit("vampirewings"));
        assertEquals(-1, loaded.getWeaponSummonLimit("caveglowstaff"));
        assertEquals(12, loaded.getGlobalCustomSummonLimit());
    }

    @Test
    @DisplayName("Multiple weapons can have independent manual limits, auto modes, and disabled states")
    public void testMultipleWeaponConfigurations() {
        settings.setWeaponSummonLimit("staff1", 3);
        settings.setWeaponSummonLimit("staff2", 0); // Auto
        settings.setWeaponSummonLimit("staff3", -1); // Off
        settings.setWeaponSummonLimit("staff4", 25); // High manual cap

        assertEquals(3, settings.getWeaponSummonLimit("staff1"));
        assertEquals(0, settings.getWeaponSummonLimit("staff2"));
        assertEquals(-1, settings.getWeaponSummonLimit("staff3"));
        assertEquals(25, settings.getWeaponSummonLimit("staff4"));

        assertEquals(3, settings.getAllWeaponLimits().size()); // Only non-zero entries are stored in map
    }
}
