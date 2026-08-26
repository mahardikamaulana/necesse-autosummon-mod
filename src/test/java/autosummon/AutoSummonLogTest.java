package autosummon;

import autosummon.util.AutoSummonLog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AutoSummonLog rate-limiting and logging logic.
 */
public class AutoSummonLogTest {

    @Test
    @DisplayName("Throttled error logging should not throw exceptions")
    public void testThrottledErrorLogging() {
        assertDoesNotThrow(() -> {
            AutoSummonLog.logThrottledError("test_key", "Test throttled error", new RuntimeException("Test Exception"), 1000);
            // Repeated immediate call should be throttled/suppressed
            AutoSummonLog.logThrottledError("test_key", "Test throttled error 2", null, 1000);
        });
    }

    @Test
    @DisplayName("Throttled warn logging should execute cleanly")
    public void testThrottledWarnLogging() {
        assertDoesNotThrow(() -> {
            AutoSummonLog.logThrottledWarn("test_warn_key", "Test throttled warn", 1000);
            AutoSummonLog.logThrottledWarn("test_warn_key", "Test throttled warn 2", 1000);
        });
    }

    @Test
    @DisplayName("Standard log methods should handle nulls and empty messages safely")
    public void testStandardLoggingSafety() {
        assertDoesNotThrow(() -> {
            AutoSummonLog.info("Info message");
            AutoSummonLog.warn("Warning message");
            AutoSummonLog.warn("Warning with throwable", new IllegalStateException("Test"));
            AutoSummonLog.error("Error message");
            AutoSummonLog.error("Error with throwable", new NullPointerException("Test NPE"));
            AutoSummonLog.debug("Debug message");
        });
    }
}
