package autosummon.compat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for VanillaSummonCompatProvider.
 */
public class VanillaSummonCompatProviderTest {

    private VanillaSummonCompatProvider provider;

    @BeforeEach
    public void setUp() {
        provider = new VanillaSummonCompatProvider();
    }

    @Test
    @DisplayName("Vanilla provider should always report loaded with id 'vanilla'")
    public void testProviderProperties() {
        assertEquals("vanilla", provider.getModID());
        assertTrue(provider.isModLoaded());
        assertDoesNotThrow(() -> provider.init());
    }

    @Test
    @DisplayName("canHandle and createAdapter should return false/null for null or non-SummonToolItem")
    public void testNullAndInvalidItems() {
        assertFalse(provider.canHandle(null));
        assertNull(provider.createAdapter(null));
    }
}
