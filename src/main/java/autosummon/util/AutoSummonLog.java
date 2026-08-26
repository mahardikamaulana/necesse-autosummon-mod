package autosummon.util;

import necesse.engine.GameLog;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Centralized logging utility for the Auto Summon mod.
 * Integrates with Necesse's GameLog system and provides rate-limited error
 * logging to prevent console flooding during game tick or render loops.
 */
public class AutoSummonLog {

    private static final String PREFIX = "[Auto Summon] ";
    private static final ConcurrentHashMap<String, ThrottleEntry> throttleMap = new ConcurrentHashMap<>();

    private static class ThrottleEntry {
        long lastTime;
        final AtomicInteger suppressedCount = new AtomicInteger(0);

        ThrottleEntry(long lastTime) {
            this.lastTime = lastTime;
        }
    }

    /**
     * Log informational message
     */
    public static void info(String message) {
        try {
            if (GameLog.debug != null) {
                GameLog.debug.println(PREFIX + message);
            } else {
                System.out.println(PREFIX + message);
            }
        } catch (Throwable t) {
            System.out.println(PREFIX + message);
        }
    }

    /**
     * Log warning message
     */
    public static void warn(String message) {
        try {
            if (GameLog.warn != null) {
                GameLog.warn.println(PREFIX + message);
            } else {
                System.err.println(PREFIX + "WARN: " + message);
            }
        } catch (Throwable t) {
            System.err.println(PREFIX + "WARN: " + message);
        }
    }

    /**
     * Log warning with throwable
     */
    public static void warn(String message, Throwable throwable) {
        warn(message);
        if (throwable != null) {
            try {
                if (GameLog.warn != null) {
                    throwable.printStackTrace(GameLog.warn);
                } else {
                    throwable.printStackTrace();
                }
            } catch (Throwable ignored) {
                throwable.printStackTrace();
            }
        }
    }

    /**
     * Log error message
     */
    public static void error(String message) {
        try {
            if (GameLog.err != null) {
                GameLog.err.println(PREFIX + "ERROR: " + message);
            } else {
                System.err.println(PREFIX + "ERROR: " + message);
            }
        } catch (Throwable t) {
            System.err.println(PREFIX + "ERROR: " + message);
        }
    }

    /**
     * Log error message with exception stack trace
     */
    public static void error(String message, Throwable throwable) {
        error(message);
        if (throwable != null) {
            try {
                if (GameLog.err != null) {
                    throwable.printStackTrace(GameLog.err);
                } else {
                    throwable.printStackTrace();
                }
            } catch (Throwable ignored) {
                throwable.printStackTrace();
            }
        }
    }

    /**
     * Log debug message
     */
    public static void debug(String message) {
        try {
            if (GameLog.debug != null) {
                GameLog.debug.println(PREFIX + "[DEBUG] " + message);
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Log an error message with rate limiting.
     * Prevents console flooding if the error is repeatedly triggered in high-frequency loops (e.g. ticks/renders).
     *
     * @param key Unique identifier for the error category/site
     * @param message Message to output
     * @param throwable Exception to output, if any
     * @param intervalMs Minimum time in ms between repeated logs
     */
    public static void logThrottledError(String key, String message, Throwable throwable, long intervalMs) {
        long now = System.currentTimeMillis();
        ThrottleEntry entry = throttleMap.compute(key, (k, existing) -> {
            if (existing == null) {
                return new ThrottleEntry(now);
            }
            if (now - existing.lastTime >= intervalMs) {
                int suppressed = existing.suppressedCount.getAndSet(0);
                if (suppressed > 0) {
                    error("[" + key + "] (" + suppressed + " duplicate events suppressed in last " + (now - existing.lastTime) + "ms)");
                }
                existing.lastTime = now;
            } else {
                existing.suppressedCount.incrementAndGet();
            }
            return existing;
        });

        if (entry != null && entry.lastTime == now) {
            error(message, throwable);
        }
    }

    /**
     * Log a warning with rate limiting.
     */
    public static void logThrottledWarn(String key, String message, long intervalMs) {
        long now = System.currentTimeMillis();
        ThrottleEntry entry = throttleMap.compute(key, (k, existing) -> {
            if (existing == null) {
                return new ThrottleEntry(now);
            }
            if (now - existing.lastTime >= intervalMs) {
                int suppressed = existing.suppressedCount.getAndSet(0);
                if (suppressed > 0) {
                    warn("[" + key + "] (" + suppressed + " duplicate warnings suppressed)");
                }
                existing.lastTime = now;
            } else {
                existing.suppressedCount.incrementAndGet();
            }
            return existing;
        });

        if (entry != null && entry.lastTime == now) {
            warn(message);
        }
    }
}
