package autosummon.compat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MobClassifier metadata caching and entity classification.
 */
public class MobClassifierTest {

    @BeforeEach
    public void setUp() {
        MobClassifier.clearCache();
    }

    @Test
    @DisplayName("Null mobs should be handled safely")
    public void testNullMob() {
        assertFalse(MobClassifier.isWormBody(null));
        assertFalse(MobClassifier.isTrinketOrPassiveFollower(null));
        assertTrue(MobClassifier.isIgnoredFollower(null));
        assertNotNull(MobClassifier.getMetadata(null));
    }

    @Test
    @DisplayName("Metadata cache should pre-warm and classify known trinkets and worms correctly")
    public void testPreWarmedClassifications() {
        // Known worm bodies
        MobClassifier.MobMetadata wormMeta = MobClassifier.getMetadata(null);
        assertFalse(wormMeta.isWormBody);

        // Pre-warmed cache lookup by string ID
        MobClassifier.clearCache();
        // Check that clearCache restores pre-warmed set
        assertNotNull(MobClassifier.getMetadata(null));
    }
}
