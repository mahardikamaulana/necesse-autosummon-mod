package autosummon.api;

import java.lang.reflect.Method;

/**
 * Single-file, zero-dependency helper bridge for Necesse mod creators.
 * Mod creators can either reference this class directly or copy its source code into their own project.
 */
public final class AutoSummonBridge {

    private static final String API_CLASS_NAME = "autosummon.api.AutoSummonAPI";
    private static volatile Boolean isAvailable = null;

    private static Method registerStaffMethod;
    private static Method registerStaffWithCostMethod;
    private static Method registerFollowerMethod;
    private static Method registerSentryMethod;
    private static Method ignoreMobMethod;

    private AutoSummonBridge() {}

    /**
     * Check if Auto Summon is currently installed and active.
     */
    public static boolean isAvailable() {
        if (isAvailable == null) {
            synchronized (AutoSummonBridge.class) {
                if (isAvailable == null) {
                    try {
                        Class<?> apiClass = Class.forName(API_CLASS_NAME);
                        registerStaffMethod = apiClass.getMethod("registerStaff", String.class, String[].class);
                        registerStaffWithCostMethod = apiClass.getMethod("registerStaff", String.class, float.class, String[].class);
                        registerFollowerMethod = apiClass.getMethod("registerFollower", String.class, int.class, String[].class);
                        registerSentryMethod = apiClass.getMethod("registerSentry", String.class, int.class, String[].class);
                        ignoreMobMethod = apiClass.getMethod("ignoreMob", String[].class);
                        isAvailable = true;
                    } catch (Throwable ignored) {
                        isAvailable = false;
                    }
                }
            }
        }
        return isAvailable;
    }

    /**
     * Register a standard summon staff that consumes 1.0 global summon slot.
     *
     * @param itemID    Your item string ID (e.g. "mycustomstaff")
     * @param minionIDs One or more mob string IDs that this weapon spawns
     */
    public static void registerStaff(String itemID, String... minionIDs) {
        if (!isAvailable()) return;
        try {
            registerStaffMethod.invoke(null, itemID, minionIDs);
        } catch (Throwable ignored) {}
    }

    /**
     * Register a standard summon staff with custom summon slot cost.
     *
     * @param itemID    Your item string ID
     * @param slotCost  Global summon slots consumed per summon (e.g. 1.0f, 2.0f, or 0.5f)
     * @param minionIDs One or more mob string IDs that this weapon spawns
     */
    public static void registerStaff(String itemID, float slotCost, String... minionIDs) {
        if (!isAvailable()) return;
        try {
            registerStaffWithCostMethod.invoke(null, itemID, slotCost, minionIDs);
        } catch (Throwable ignored) {}
    }

    /**
     * Register an independent follower weapon (e.g. spellbook, slime pet) with its own isolated cap.
     * (Does NOT consume the player's global summon slots).
     *
     * @param itemID     Your item string ID
     * @param maxAllowed Maximum active followers allowed
     * @param minionIDs  One or more mob string IDs that this weapon spawns
     */
    public static void registerFollower(String itemID, int maxAllowed, String... minionIDs) {
        if (!isAvailable()) return;
        try {
            registerFollowerMethod.invoke(null, itemID, maxAllowed, minionIDs);
        } catch (Throwable ignored) {}
    }

    /**
     * Register a stationary sentry, turret, or totem weapon with its own isolated cap.
     * (Does NOT consume the player's global summon slots).
     *
     * @param itemID     Your item string ID
     * @param maxAllowed Maximum active sentries allowed
     * @param minionIDs  One or more mob string IDs that this sentry spawns
     */
    public static void registerSentry(String itemID, int maxAllowed, String... minionIDs) {
        if (!isAvailable()) return;
        try {
            registerSentryMethod.invoke(null, itemID, maxAllowed, minionIDs);
        } catch (Throwable ignored) {}
    }

    /**
     * Register cosmetic companions, pets, or mounts so that Auto Summon never counts
     * them towards summon caps or removes them during despawn passes.
     *
     * @param mobIDs One or more mob string IDs to ignore
     */
    public static void ignoreMob(String... mobIDs) {
        if (!isAvailable()) return;
        try {
            ignoreMobMethod.invoke(null, (Object) mobIDs);
        } catch (Throwable ignored) {}
    }
}
