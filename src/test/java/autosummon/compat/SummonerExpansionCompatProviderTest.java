package autosummon.compat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SummonerExpansionCompatProvider.
 */
public class SummonerExpansionCompatProviderTest {

    private SummonerExpansionCompatProvider provider;

    @BeforeEach
    public void setUp() {
        provider = new SummonerExpansionCompatProvider();
    }

    @Test
    @DisplayName("Summoner Expansion provider should have correct mod ID")
    public void testModID() {
        assertEquals("gagadoliano.summonerexpansion", provider.getModID());
    }

    @Test
    @DisplayName("Null items and non-matching items should return false for canHandle")
    public void testNullSafety() {
        assertFalse(provider.canHandle(null));
        assertNull(provider.createAdapter(null));
    }
}
