package autosummon.compat;

import autosummon.api.AutoSummonAPI;
import autosummon.api.AutoSummonableWeapon;
import autosummon.api.SummonWeaponSpec;
import autosummon.compat.SummonWeaponAdapter.SummonCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AutoSummonAPI, SummonWeaponSpec, and AutoSummonableWeapon.
 */
public class AutoSummonAPITest {

    @BeforeEach
    public void setUp() {
        SummonCompatRegistry.resetForTesting();
        SummonCompatRegistry.init();
    }

    @Test
    @DisplayName("AutoSummonAPI should report available")
    public void testIsAvailable() {
        assertTrue(AutoSummonAPI.isAvailable());
    }

    @Test
    @DisplayName("registerStaff should register spec and generate valid adapter")
    public void testRegisterStaff() {
        AutoSummonAPI.registerStaff("customstaff", 2.0f, "customminion1", "customminion2");

        SummonWeaponSpec spec = SummonWeaponSpec.builder("customstaff")
                .asGlobalSlot(2.0f)
                .withMinions("customminion1", "customminion2")
                .build();

        SummonWeaponAdapter adapter = spec.toAdapter();
        assertNotNull(adapter);
        assertEquals("customstaff", adapter.getItemStringID());
        assertEquals(SummonCategory.GLOBAL_SLOT, adapter.getCategory(null, null));
        assertEquals(2.0f, adapter.getSummonSpaceCost(null, null));
        assertTrue(adapter.isGlobalSlotWeapon(null, null));
        assertFalse(adapter.isSecondarySummon());
        assertFalse(adapter.isSupportWeapon());
    }

    @Test
    @DisplayName("registerFollower should register follower with isolated cap")
    public void testRegisterFollower() {
        AutoSummonAPI.registerFollower("bookofbees", 3, "beeminion");

        SummonWeaponSpec spec = SummonWeaponSpec.builder("bookofbees")
                .asFollower(3)
                .withMinions("beeminion")
                .build();

        SummonWeaponAdapter adapter = spec.toAdapter();
        assertNotNull(adapter);
        assertEquals(SummonCategory.INDEPENDENT_FOLLOWER, adapter.getCategory(null, null));
        assertFalse(adapter.isGlobalSlotWeapon(null, null));
        assertTrue(adapter.isSecondarySummon());
        assertEquals(3, adapter.getMaxAllowed(null, null, 10));
    }

    @Test
    @DisplayName("registerSentry should register sentry with isolated cap")
    public void testRegisterSentry() {
        AutoSummonAPI.registerSentry("crystalturret", 1, "crystalturretmob");

        SummonWeaponSpec spec = SummonWeaponSpec.builder("crystalturret")
                .asSentry(1)
                .withMinions("crystalturretmob")
                .build();

        SummonWeaponAdapter adapter = spec.toAdapter();
        assertNotNull(adapter);
        assertEquals(SummonCategory.SENTRY_SUPPORT, adapter.getCategory(null, null));
        assertFalse(adapter.isGlobalSlotWeapon(null, null));
        assertTrue(adapter.isSupportWeapon());
        assertEquals(1, adapter.getMaxAllowed(null, null, 10));
    }

    @Test
    @DisplayName("ignoreMob should register custom mob in MobClassifier")
    public void testIgnoreMob() {
        assertFalse(MobClassifier.isTrinketOrPassiveFollower(null));
        AutoSummonAPI.ignoreMob("customcosmeticpet");
        // Once registered, computeMetadata should treat it as ignored
        MobClassifier.MobMetadata meta = MobClassifier.getMetadata(null);
        assertNotNull(meta);
    }

    @Test
    @DisplayName("SummonWeaponSpec builder should validate arguments")
    public void testSpecBuilderValidation() {
        assertThrows(IllegalArgumentException.class, () -> SummonWeaponSpec.builder(null));
        assertThrows(IllegalArgumentException.class, () -> SummonWeaponSpec.builder("   "));

        SummonWeaponSpec spec = SummonWeaponSpec.builder("valid_item")
                .displayName("Valid Display Name")
                .withMinions("m1", "m2")
                .slotCost(1.5f)
                .build();

        assertEquals("valid_item", spec.getItemID());
        assertEquals("Valid Display Name", spec.getDisplayName());
        assertEquals(2, spec.getMinionIDs().size());
        assertEquals(1.5f, spec.getSlotCost());
    }

    @Test
    @DisplayName("AutoSummonableWeapon default interface methods should work")
    public void testAutoSummonableWeaponDefaults() {
        AutoSummonableWeapon weapon = new AutoSummonableWeapon() {
            @Override
            public Collection<String> getSummonMinionIDs(necesse.inventory.InventoryItem item) {
                return Arrays.asList("testmob1", "testmob2");
            }
        };

        assertEquals(SummonCategory.GLOBAL_SLOT, weapon.getSummonCategory(null, null));
        assertEquals(1.0f, weapon.getSummonSpaceCost(null, null));
        assertEquals(5, weapon.getSummonMaxAllowed(null, null, 5));
        assertNull(weapon.canAutoSummon(null, null));
        assertFalse(weapon.isAutoSummonDisabled(null));
        assertEquals(2, weapon.getSummonMinionIDs(null).size());
    }
}
