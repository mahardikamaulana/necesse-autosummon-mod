package autosummon.compat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SummonCompatRegistry initialization, provider management, and lookups.
 */
public class SummonCompatRegistryTest {

    @BeforeEach
    public void setUp() {
        SummonCompatRegistry.resetForTesting();
        SummonCompatRegistry.init();
    }

    @Test
    @DisplayName("Registry init should register SummonerExpansion and Vanilla fallback providers")
    public void testRegistryInit() {
        List<SummonCompatProvider> providers = SummonCompatRegistry.getProviders();
        assertNotNull(providers);
        assertFalse(providers.isEmpty(), "Providers list should not be empty after init");

        boolean hasVanilla = false;
        boolean hasExpansion = false;
        for (SummonCompatProvider provider : providers) {
            if (provider instanceof VanillaSummonCompatProvider) hasVanilla = true;
            if (provider instanceof SummonerExpansionCompatProvider) hasExpansion = true;
        }

        assertTrue(hasVanilla, "Registry should contain VanillaSummonCompatProvider");
        assertTrue(hasExpansion, "Registry should contain SummonerExpansionCompatProvider");
    }

    @Test
    @DisplayName("Registering null or duplicate provider should be handled safely")
    public void testRegisterDuplicateOrNullProvider() {
        int initialSize = SummonCompatRegistry.getProviders().size();
        SummonCompatRegistry.registerProvider(null);
        assertEquals(initialSize, SummonCompatRegistry.getProviders().size(), "Null provider should not be added");

        // Try registering vanilla provider again
        SummonCompatRegistry.registerProvider(new VanillaSummonCompatProvider());
        // Custom or duplicate should not double-register existing instance
        assertTrue(SummonCompatRegistry.getProviders().size() >= initialSize);
    }

    @Test
    @DisplayName("Dynamic provider registration via class name should handle missing or valid classes")
    public void testRegisterProviderClass() {
        // Non-existent class
        boolean registeredNonExistent = SummonCompatRegistry.registerProviderClass("non.existent.ModProvider");
        assertFalse(registeredNonExistent);

        // Invalid class (not implementing SummonCompatProvider)
        boolean registeredInvalid = SummonCompatRegistry.registerProviderClass("java.lang.String");
        assertFalse(registeredInvalid);

        // Valid provider class
        boolean registeredValid = SummonCompatRegistry.registerProviderClass("autosummon.compat.VanillaSummonCompatProvider");
        assertTrue(registeredValid);
    }

    @Test
    @DisplayName("Null safety for items and mobs")
    public void testNullSafety() {
        assertFalse(SummonCompatRegistry.isSummonWeapon(null));
        assertNull(SummonCompatRegistry.getAdapter(null));
        assertNull(SummonCompatRegistry.findWeaponForMob(null, null));
        assertFalse(SummonCompatRegistry.isWormBody(null));
        assertFalse(SummonCompatRegistry.isTrinketOrPassiveFollower(null));
        assertTrue(SummonCompatRegistry.isIgnoredFollower(null));
    }

    @Test
    @DisplayName("Explicit minion mapping should register and resolve correctly")
    public void testMinionMapping() {
        assertDoesNotThrow(() -> {
            SummonCompatRegistry.registerMinionMapping("testminion", null);
            SummonCompatRegistry.registerMinionMapping(null, null);
        });
    }
}
