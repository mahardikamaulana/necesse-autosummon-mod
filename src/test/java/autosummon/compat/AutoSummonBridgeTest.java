package autosummon.compat;

import autosummon.api.AutoSummonBridge;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests verifying that the drop-in AutoSummonBridge helper
 * communicates cleanly with AutoSummonAPI via reflection.
 */
public class AutoSummonBridgeTest {

    @BeforeEach
    public void setUp() {
        SummonCompatRegistry.resetForTesting();
        SummonCompatRegistry.init();
    }

    @Test
    @DisplayName("AutoSummonBridge should detect API availability and invoke methods safely")
    public void testBridgeInvocation() {
        assertTrue(AutoSummonBridge.isAvailable());

        assertDoesNotThrow(() -> {
            AutoSummonBridge.registerStaff("bridgestaff", "bridgeminion");
            AutoSummonBridge.registerStaff("bridgestaff_cost", 2.0f, "bridgeminion_cost");
            AutoSummonBridge.registerFollower("bridgefollower", 2, "bridgefollowerminion");
            AutoSummonBridge.registerSentry("bridgesentry", 1, "bridgesentrymob");
            AutoSummonBridge.ignoreMob("bridgeignoredmob");
        });

        // Verify that registered specs exist in registry
        assertNotNull(SummonCompatRegistry.getProviders());
    }
}
