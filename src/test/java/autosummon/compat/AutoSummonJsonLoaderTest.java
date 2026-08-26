package autosummon.compat;

import autosummon.compat.AutoSummonJsonLoader.JsonLoadResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AutoSummonJsonLoader comprehensive error handling and JSON parsing.
 */
public class AutoSummonJsonLoaderTest {

    @BeforeEach
    public void setUp() {
        SummonCompatRegistry.resetForTesting();
        SummonCompatRegistry.init();
    }

    @Test
    @DisplayName("Valid JSON configuration with comments and relaxed syntax should parse cleanly")
    public void testParseValidJsonWithComments() {
        String json = "// Mod configuration\n" +
                "{\n" +
                "  /* Main mod ID */\n" +
                "  \"modId\": \"test.mod\",\n" +
                "  # Weapons list\n" +
                "  \"weapons\": [\n" +
                "    {\n" +
                "      'id': 'staff1',\n" + // Single quotes
                "      displayName: \"Staff One\",\n" + // Unquoted key
                "      \"minion\": \"minion1\",\n" +
                "      \"category\": \"GLOBAL_SLOT\",\n" +
                "      \"slotCost\": 2.0,\n" + // Trailing comma in object
                "    },\n" +
                "    {\n" +
                "      \"id\": \"book1\",\n" +
                "      \"displayName\": \"Book One\",\n" +
                "      \"minions\": [\"slime1\", \"slime2\"],\n" +
                "      \"type\": \"follower\",\n" +
                "      \"maxAllowed\": 3\n" +
                "    },\n" +
                "    {\n" +
                "      \"id\": \"turret1\",\n" +
                "      \"type\": \"sentry\",\n" +
                "      \"minion\": \"turretmob\",\n" +
                "      \"maxAllowed\": 1\n" +
                "    },\n" + // Trailing comma in array
                "  ],\n" +
                "  \"ignoredMobs\": [\"pet1\", \"mount1\"]\n" +
                "}";

        JsonLoadResult result = AutoSummonJsonLoader.parseAndRegisterJsonString(json, "test-source");
        assertTrue(result.isSuccess(), "Parse should succeed without errors");
        assertEquals(3, result.weaponsRegistered, "Should register 3 weapons");
        assertEquals(2, result.ignoredMobsRegistered, "Should register 2 ignored mobs");
        assertEquals("test.mod", result.modId);
    }

    @Test
    @DisplayName("UTF-8 BOM should be automatically stripped without errors")
    public void testBomHandling() {
        String json = "\uFEFF{\n" +
                "  \"modId\": \"bom.mod\",\n" +
                "  \"weapons\": [{ \"id\": \"bomstaff\", \"minion\": \"bommob\" }]\n" +
                "}";

        JsonLoadResult result = AutoSummonJsonLoader.parseAndRegisterJsonString(json, "bom-source");
        assertTrue(result.isSuccess());
        assertEquals(1, result.weaponsRegistered);
    }

    @Test
    @DisplayName("Top-level array format should be supported as a weapons list")
    public void testTopLevelArray() {
        String json = "[\n" +
                "  { \"id\": \"arraystaff1\", \"minion\": \"mob1\" },\n" +
                "  { \"id\": \"arraystaff2\", \"minion\": \"mob2\" }\n" +
                "]";

        JsonLoadResult result = AutoSummonJsonLoader.parseAndRegisterJsonString(json, "array-source");
        assertTrue(result.isSuccess());
        assertEquals(2, result.weaponsRegistered);
        assertTrue(result.warnings.stream().anyMatch(w -> w.contains("Top-level JSON is an array")));
    }

    @Test
    @DisplayName("Partial recovery: Invalid weapon entries should be skipped while valid ones register")
    public void testPartialWeaponRecovery() {
        String json = "{\n" +
                "  \"modId\": \"recovery.mod\",\n" +
                "  \"weapons\": [\n" +
                "    { \"id\": \"good_staff_1\", \"minion\": \"mob1\" },\n" +
                "    { \"displayName\": \"Missing ID Staff\", \"minion\": \"mob2\" },\n" + // Missing ID!
                "    { \"id\": \"   \", \"minion\": \"mob3\" },\n" + // Blank ID!
                "    { \"id\": \"good_staff_2\", \"minion\": \"mob4\" }\n" +
                "  ]\n" +
                "}";

        JsonLoadResult result = AutoSummonJsonLoader.parseAndRegisterJsonString(json, "recovery-source");
        assertTrue(result.isSuccess());
        assertEquals(2, result.weaponsRegistered, "Should register the 2 valid weapons");
        assertEquals(2, result.warnings.size(), "Should have 2 warnings for missing/blank IDs");
    }

    @Test
    @DisplayName("Invalid numeric values and unknown categories should fallback safely")
    public void testInvalidFieldFallbacks() {
        String json = "{\n" +
                "  \"weapons\": [\n" +
                "    {\n" +
                "      \"id\": \"test_fallback\",\n" +
                "      \"slotCost\": -5.0,\n" + // Negative slotCost
                "      \"maxAllowed\": \"not_a_number\",\n" + // String instead of int
                "      \"category\": \"UNKNOWN_CUSTOM_CATEGORY\"\n" + // Unknown category
                "    }\n" +
                "  ]\n" +
                "}";

        JsonLoadResult result = AutoSummonJsonLoader.parseAndRegisterJsonString(json, "fallback-source");
        assertTrue(result.isSuccess());
        assertEquals(1, result.weaponsRegistered);
        assertTrue(result.warnings.stream().anyMatch(w -> w.contains("non-positive slotCost")));
        assertTrue(result.warnings.stream().anyMatch(w -> w.contains("invalid integer value")));
        assertTrue(result.warnings.stream().anyMatch(w -> w.contains("Unrecognized category")));
    }

    @Test
    @DisplayName("Syntax errors should report accurate line and column information")
    public void testSyntaxErrorReporting() {
        String json = "{\n" +
                "  \"modId\": \"broken.mod\",\n" +
                "  \"weapons\": [ { \"id\": \"staff1\" \n" + // Missing closing brace and bracket!
                "";

        JsonLoadResult result = AutoSummonJsonLoader.parseAndRegisterJsonString(json, "syntax-source");
        assertFalse(result.isSuccess());
        assertFalse(result.errors.isEmpty());
        assertTrue(result.errors.get(0).contains("line"));
        assertTrue(result.errors.get(0).contains("column"));
    }

    @Test
    @DisplayName("Empty or null JSON inputs should return safe empty result")
    public void testEmptyJsonInputs() {
        JsonLoadResult r1 = AutoSummonJsonLoader.parseAndRegisterJsonString(null, "null-source");
        assertEquals(0, r1.weaponsRegistered);
        assertTrue(r1.warnings.get(0).contains("empty or blank"));

        JsonLoadResult r2 = AutoSummonJsonLoader.parseAndRegisterJsonString("   ", "blank-source");
        assertEquals(0, r2.weaponsRegistered);
        assertTrue(r2.warnings.get(0).contains("empty or blank"));
    }
}
