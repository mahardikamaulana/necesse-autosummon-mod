package autosummon.compat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for UniversalSummonCompatProvider.
 */
public class UniversalSummonCompatProviderTest {

    private UniversalSummonCompatProvider provider;

    @BeforeEach
    public void setUp() {
        provider = new UniversalSummonCompatProvider();
        provider.init();
    }

    @Test
    @DisplayName("Universal provider should always report loaded with id 'universal'")
    public void testProviderProperties() {
        assertEquals("universal", provider.getModID());
        assertTrue(provider.isModLoaded());
        assertDoesNotThrow(() -> provider.init());
    }

    @Test
    @DisplayName("canHandle and createAdapter should return false/null for null items")
    public void testNullSafety() {
        assertFalse(provider.canHandle(null));
        assertNull(provider.createAdapter(null));
    }
}
