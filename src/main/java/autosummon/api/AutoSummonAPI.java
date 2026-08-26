package autosummon.api;

import autosummon.compat.SummonCompatRegistry;
import autosummon.compat.SummonWeaponAdapter;
import autosummon.util.AutoSummonLog;
import necesse.inventory.InventoryItem;

import java.util.Collection;

/**
 * Public, high-level API for third-party mod creators to seamlessly integrate
 * their summon weapons, followers, and sentries with Auto Summon in 1–3 lines of code.
 */
public final class AutoSummonAPI {

    private AutoSummonAPI() {}

    /**
     * Check if Auto Summon is currently available and loaded in the runtime.
     */
    public static boolean isAvailable() {
        return true;
    }

    /**
     * Quick-register a standard summon staff that consumes global summon slots.
     *
     * @param itemID    The unique string ID of the item (e.g. "skeletonstaff")
     * @param minionIDs One or more mob string IDs that this weapon spawns
     */
    public static void registerStaff(String itemID, String... minionIDs) {
        registerStaff(itemID, 1.0f, minionIDs);
    }

    /**
     * Quick-register a standard summon staff with custom summon slot cost.
     *
     * @param itemID    The unique string ID of the item
     * @param slotCost  The number of global summon slots consumed per summon (e.g. 1.0f or 2.0f)
     * @param minionIDs One or more mob string IDs that this weapon spawns
     */
    public static void registerStaff(String itemID, float slotCost, String... minionIDs) {
        if (itemID == null || itemID.trim().isEmpty()) return;
        SummonWeaponSpec spec = SummonWeaponSpec.builder(itemID)
                .asGlobalSlot(slotCost)
                .withMinions(minionIDs)
                .build();
        registerWeapon(spec);
    }

    /**
     * Quick-register an independent follower weapon (e.g. book/pet/slime) with its own isolated active cap.
     * (Does NOT consume the player's global summon slots).
     *
     * @param itemID     The unique string ID of the item
     * @param maxAllowed Maximum active followers permitted at one time
     * @param minionIDs  One or more mob string IDs that this weapon spawns
     */
    public static void registerFollower(String itemID, int maxAllowed, String... minionIDs) {
        if (itemID == null || itemID.trim().isEmpty()) return;
        SummonWeaponSpec spec = SummonWeaponSpec.builder(itemID)
                .asFollower(maxAllowed)
                .withMinions(minionIDs)
                .build();
        registerWeapon(spec);
    }

    /**
     * Quick-register a stationary sentry, turret, or totem weapon.
     * (Does NOT consume the player's global summon slots).
     *
     * @param itemID     The unique string ID of the item
     * @param maxAllowed Maximum active sentries permitted
     * @param minionIDs  One or more mob string IDs that this sentry spawns
     */
    public static void registerSentry(String itemID, int maxAllowed, String... minionIDs) {
        if (itemID == null || itemID.trim().isEmpty()) return;
        SummonWeaponSpec spec = SummonWeaponSpec.builder(itemID)
                .asSentry(maxAllowed)
                .withMinions(minionIDs)
                .build();
        registerWeapon(spec);
    }

    /**
     * Register a weapon using a builder or full specification.
     */
    public static void registerWeapon(SummonWeaponSpec spec) {
        if (spec == null) return;
        try {
            SummonCompatRegistry.registerSpec(spec);
            AutoSummonLog.debug("AutoSummonAPI: Registered weapon spec for '" + spec.getItemID() + "'");
        } catch (Throwable t) {
            AutoSummonLog.error("AutoSummonAPI: Failed to register spec for '" + spec.getItemID() + "'", t);
        }
    }

    /**
     * Register a weapon using a builder.
     */
    public static void registerWeapon(SummonWeaponSpec.Builder builder) {
        if (builder != null) {
            registerWeapon(builder.build());
        }
    }

    /**
     * Register a custom SummonWeaponAdapter directly.
     */
    public static void registerCustom(String itemID, SummonWeaponAdapter adapter) {
        if (itemID == null || adapter == null) return;
        try {
            SummonCompatRegistry.registerCustomAdapter(itemID, adapter);
        } catch (Throwable t) {
            AutoSummonLog.error("AutoSummonAPI: Failed to register custom adapter for '" + itemID + "'", t);
        }
    }

    /**
     * Register one or more mob string IDs as non-combat companions (pets, cosmetic followers, mounts)
     * so that Auto Summon never counts or despawns them.
     */
    public static void ignoreMob(String... mobIDs) {
        if (mobIDs == null) return;
        for (String id : mobIDs) {
            if (id != null && !id.trim().isEmpty()) {
                SummonCompatRegistry.registerIgnoredMob(id.trim());
            }
        }
    }

    /**
     * Register a collection of mob string IDs to be ignored by Auto Summon.
     */
    public static void ignoreMobs(Collection<String> mobIDs) {
        if (mobIDs == null) return;
        for (String id : mobIDs) {
            if (id != null && !id.trim().isEmpty()) {
                SummonCompatRegistry.registerIgnoredMob(id.trim());
            }
        }
    }

    /**
     * Check if a given inventory item is recognized as a summon weapon by Auto Summon.
     */
    public static boolean isSummonWeapon(InventoryItem item) {
        return SummonCompatRegistry.isSummonWeapon(item);
    }

    /**
     * Retrieve the resolved SummonWeaponAdapter for an item, or null if unsupported.
     */
    public static SummonWeaponAdapter getAdapter(InventoryItem item) {
        return SummonCompatRegistry.getAdapter(item);
    }
}
