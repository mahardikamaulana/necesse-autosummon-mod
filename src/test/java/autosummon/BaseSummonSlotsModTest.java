package autosummon;

import autosummon.api.SummonWeaponSpec;
import autosummon.compat.*;
import autosummon.compat.SummonWeaponAdapter.SummonCategory;
import necesse.entity.mobs.buffs.BuffModifiers;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.toolItem.summonToolItem.SummonToolItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests verifying that when a mod modifies base summoning slots (or staves provide
 * custom getMaxSummons overrides), global summon slots and weapon limits update correctly.
 */
public class BaseSummonSlotsModTest {

    @org.junit.jupiter.api.BeforeAll
    public static void beforeAll() {
        try {
            necesse.engine.registries.GNDRegistry.instance.registerCore();
        } catch (Throwable ignored) {}
    }

    @BeforeEach
    public void setUp() {
        SummonCompatRegistry.resetForTesting();
        SummonCompatRegistry.init();
    }

    @AfterEach
    public void tearDown() {
        SummonCompatRegistry.resetForTesting();
    }

    @Test
    @DisplayName("SummonCompatRegistry.getPlayerMaxSummonSlots should return default base when player/buffManager is null")
    public void testDefaultBaseSlotsWithoutPlayer() {
        int slots = SummonCompatRegistry.getPlayerMaxSummonSlots(null);
        int expectedBase = (BuffModifiers.MAX_SUMMONS != null && BuffModifiers.MAX_SUMMONS.defaultBuffManagerValue != null)
                ? BuffModifiers.MAX_SUMMONS.defaultBuffManagerValue : 1;
        assertEquals(expectedBase, slots, "Global summon slots should match BuffModifiers default value");
    }

    @Test
    @DisplayName("Global slot adapters should respect staff.getMaxSummons when staff overrides base slot capacity")
    public void testStaffLevelMaxSummonsOverride() {
        // Create a custom SummonToolItem that simulates a mod providing 4 base slots
        SummonToolItem moddedStaff = new SummonToolItem("testminion", null, 1.0f, 100, null) {
            @Override
            public int getMaxSummons(InventoryItem item, necesse.entity.mobs.itemAttacker.ItemAttackerMob attackerMob) {
                return 4; // Mod gives 4 base slots
            }
        };

        InventoryItem invItem = new InventoryItem(moddedStaff);
        SummonWeaponAdapter adapter = new VanillaSummonCompatProvider.VanillaSummonAdapter(moddedStaff);

        // Inspect via SummonCompatRegistry with items list
        int playerSlots = SummonCompatRegistry.getPlayerMaxSummonSlots(null, Collections.singletonList(invItem));
        assertEquals(4, playerSlots, "getPlayerMaxSummonSlots should reflect staff's 4 slots");

        // When resolved capacity (4) is passed to adapter, maxAllowed should be 4
        int maxAllowed = adapter.getMaxAllowed(invItem, null, playerSlots);
        assertEquals(4, maxAllowed, "Adapter should use staff override of 4 slots");
    }

    @Test
    @DisplayName("Zero base summoning slots should result in 0 max allowed for slot weapons, while followers remain usable")
    public void testZeroBaseSummonSlots() {
        // 1. Standard global slot staff with 0 base slots
        SummonToolItem standardStaff = new SummonToolItem("testminion", null, 1.0f, 100, null);
        InventoryItem standardItem = new InventoryItem(standardStaff);
        SummonWeaponAdapter vanillaAdapter = new VanillaSummonCompatProvider.VanillaSummonAdapter(standardStaff);

        int maxAllowedStandard = vanillaAdapter.getMaxAllowed(standardItem, null, 0);
        assertEquals(0, maxAllowedStandard, "Global slot weapon should allow 0 summons when capacity is 0");

        // 2. Follower weapon with dedicated independent cap
        SummonWeaponSpec followerSpec = SummonWeaponSpec.builder("followerpet")
                .asFollower(2)
                .withMinions("petmob")
                .build();
        SummonWeaponAdapter followerAdapter = followerSpec.toAdapter();
        InventoryItem followerItem = new InventoryItem(standardStaff);

        int maxAllowedFollower = followerAdapter.getMaxAllowed(followerItem, null, 0);
        assertEquals(2, maxAllowedFollower, "Follower weapon should retain its independent cap even if base slots are 0");

        // 3. Sentry weapon with dedicated independent cap
        SummonWeaponSpec sentrySpec = SummonWeaponSpec.builder("testsentry")
                .asSentry(3)
                .withMinions("sentrymob")
                .build();
        SummonWeaponAdapter sentryAdapter = sentrySpec.toAdapter();

        int maxAllowedSentry = sentryAdapter.getMaxAllowed(followerItem, null, 0);
        assertEquals(3, maxAllowedSentry, "Sentry weapon should retain its independent cap even if base slots are 0");
    }

    @Test
    @DisplayName("Higher slot costs should divide the effective modded base slots correctly")
    public void testSlotCostDivisionWithModdedBaseSlots() {
        // Staff that takes 2.0 slots per summon, under a mod that provides 6 base slots
        SummonToolItem heavyStaff = new SummonToolItem("heavyminion", null, 2.0f, 100, null) {
            @Override
            public int getMaxSummons(InventoryItem item, necesse.entity.mobs.itemAttacker.ItemAttackerMob attackerMob) {
                return 6;
            }
        };

        InventoryItem item = new InventoryItem(heavyStaff);
        SummonWeaponAdapter adapter = new VanillaSummonCompatProvider.VanillaSummonAdapter(heavyStaff);

        int playerSlots = SummonCompatRegistry.getPlayerMaxSummonSlots(null, Collections.singletonList(item));
        assertEquals(6, playerSlots);

        int maxAllowed = adapter.getMaxAllowed(item, null, playerSlots);
        // 6 base slots / 2 slot cost = 3 summons
        assertEquals(3, maxAllowed, "6 base slots with 2.0 slot cost should allow 3 summons");
    }

    @Test
    @DisplayName("SummonerExpansion and Universal adapters should respect staff getMaxSummons overrides")
    public void testOtherProvidersStaffOverride() {
        SummonToolItem moddedStaff = new SummonToolItem("modminion", null, 1.0f, 100, null) {
            @Override
            public int getMaxSummons(InventoryItem item, necesse.entity.mobs.itemAttacker.ItemAttackerMob attackerMob) {
                return 5;
            }
        };
        InventoryItem item = new InventoryItem(moddedStaff);

        int playerSlots = SummonCompatRegistry.getPlayerMaxSummonSlots(null, Collections.singletonList(item));
        assertEquals(5, playerSlots);

        // UniversalSummonToolAdapter
        UniversalSummonCompatProvider.UniversalSummonToolAdapter universalAdapter =
                new UniversalSummonCompatProvider.UniversalSummonToolAdapter(moddedStaff);
        assertEquals(5, universalAdapter.getMaxAllowed(item, null, playerSlots));

        // SummonerExpansionWeaponAdapter
        SummonerExpansionCompatProvider.SummonerExpansionWeaponAdapter seAdapter =
                new SummonerExpansionCompatProvider.SummonerExpansionWeaponAdapter(moddedStaff);
        assertEquals(5, seAdapter.getMaxAllowed(item, null, playerSlots));

        // SimpleSummonWeaponAdapter
        SimpleSummonWeaponAdapter simpleAdapter = new SimpleSummonWeaponAdapter("simplestaff", SummonCategory.GLOBAL_SLOT, "minion") {};
        assertEquals(5, simpleAdapter.getMaxAllowed(item, null, playerSlots));

        // SummonWeaponSpec adapter
        SummonWeaponSpec spec = SummonWeaponSpec.builder("specstaff").asGlobalSlot(1.0f).build();
        assertEquals(5, spec.toAdapter().getMaxAllowed(item, null, playerSlots));
    }

    @Test
    @DisplayName("Hotbar scan with multiple staves should resolve to highest global slot capacity")
    public void testMultipleHotbarStavesResolution() {
        SummonToolItem staff1 = new SummonToolItem("staff1", null, 1.0f, 100, null) {
            @Override
            public int getMaxSummons(InventoryItem item, necesse.entity.mobs.itemAttacker.ItemAttackerMob attackerMob) {
                return 3;
            }
        };

        SummonToolItem staff2 = new SummonToolItem("staff2", null, 1.0f, 100, null) {
            @Override
            public int getMaxSummons(InventoryItem item, necesse.entity.mobs.itemAttacker.ItemAttackerMob attackerMob) {
                return 5;
            }
        };

        InventoryItem item1 = new InventoryItem(staff1);
        InventoryItem item2 = new InventoryItem(staff2);

        int resolvedSlots = SummonCompatRegistry.getPlayerMaxSummonSlots(null, Arrays.asList(item1, item2));
        assertEquals(5, resolvedSlots, "Should resolve to maximum capacity (5) among active hotbar staves");
    }

    @Test
    @DisplayName("Fractional slot costs should calculate correctly against modded base slots")
    public void testFractionalSlotCosts() {
        SummonToolItem fractionalStaffHalf = new SummonToolItem("halfminion", null, 0.5f, 100, null);
        SummonToolItem fractionalStaffOneAndHalf = new SummonToolItem("onehalfminion", null, 1.5f, 100, null);

        InventoryItem itemHalf = new InventoryItem(fractionalStaffHalf);
        InventoryItem itemOneHalf = new InventoryItem(fractionalStaffOneAndHalf);

        SummonWeaponAdapter adapterHalf = new VanillaSummonCompatProvider.VanillaSummonAdapter(fractionalStaffHalf);
        SummonWeaponAdapter adapterOneHalf = new VanillaSummonCompatProvider.VanillaSummonAdapter(fractionalStaffOneAndHalf);

        // With 5 base slots: 5 / 0.5 = 10 summons
        assertEquals(10, adapterHalf.getMaxAllowed(itemHalf, null, 5));

        // With 5 base slots: 5 / 1.5 = 3.33 -> 3 summons
        assertEquals(3, adapterOneHalf.getMaxAllowed(itemOneHalf, null, 5));
    }

    @Test
    @DisplayName("Mixed hotbar should differentiate global slots, follower caps, and sentry caps")
    public void testMixedHotbarWeaponCaps() {
        SummonToolItem globalStaff = new SummonToolItem("globalminion", null, 1.0f, 100, null);
        InventoryItem globalItem = new InventoryItem(globalStaff);
        SummonWeaponAdapter globalAdapter = new VanillaSummonCompatProvider.VanillaSummonAdapter(globalStaff);

        SummonWeaponSpec followerSpec = SummonWeaponSpec.builder("followerstaff")
                .asFollower(2)
                .withMinions("followermob")
                .build();
        SummonWeaponAdapter followerAdapter = followerSpec.toAdapter();

        SummonWeaponSpec sentrySpec = SummonWeaponSpec.builder("sentrystaff")
                .asSentry(4)
                .withMinions("sentrymob")
                .build();
        SummonWeaponAdapter sentryAdapter = sentrySpec.toAdapter();

        // 8 base slots
        int baseSlots = 8;
        assertEquals(8, globalAdapter.getMaxAllowed(globalItem, null, baseSlots));
        assertEquals(2, followerAdapter.getMaxAllowed(globalItem, null, baseSlots));
        assertEquals(4, sentryAdapter.getMaxAllowed(globalItem, null, baseSlots));

        // 0 base slots
        int zeroSlots = 0;
        assertEquals(0, globalAdapter.getMaxAllowed(globalItem, null, zeroSlots));
        assertEquals(2, followerAdapter.getMaxAllowed(globalItem, null, zeroSlots));
        assertEquals(4, sentryAdapter.getMaxAllowed(globalItem, null, zeroSlots));
    }

    @Test
    @DisplayName("canSummon should respect dynamic playerMaxSummons and return false when capacity is 0")
    public void testCanSummonGlobalSlots() {
        SummonToolItem staff = new SummonToolItem("minion", null, 1.0f, 100, null);
        InventoryItem item = new InventoryItem(staff);
        SummonWeaponAdapter adapter = new VanillaSummonCompatProvider.VanillaSummonAdapter(staff);

        // When playerMaxSummons is 0, global slot weapon cannot summon
        boolean canSummonZero = autosummon.patches.AutoSummonPatch.canSummon(null, adapter, item, 0);
        assertFalse(canSummonZero, "canSummon should return false when playerMaxSummons is 0");
    }
}
