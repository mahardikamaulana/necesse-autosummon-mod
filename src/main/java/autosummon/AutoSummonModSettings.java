package autosummon;

import autosummon.util.AutoSummonLog;
import necesse.engine.modLoader.ModSettings;
import necesse.engine.save.SaveData;
import necesse.engine.save.LoadData;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Settings class for the auto summon mod that persists the enabled/disabled state
 * and per-weapon summon limits with full thread safety and resilient validation.
 */
public class AutoSummonModSettings extends ModSettings {
    
    // Maximum manual summon limit selectable in GUI
    public static final int MAX_MANUAL_LIMIT = 50;

    // Default values
    private volatile boolean isEnabled = true;
    private volatile boolean autoSummonOnJoin = true;
    
    // Global custom summon limit for staffs (0 = use staff/player defaults)
    private volatile int globalCustomSummonLimit = 0;
    
    // Per-weapon custom summon limits (mapped by item string ID -> limit; 0 = Auto/Default, -1 = Off/Disabled, >0 = Custom Cap)
    private final ConcurrentHashMap<String, Integer> weaponSummonLimits = new ConcurrentHashMap<>();
    
    public AutoSummonModSettings() {
        super();
    }
    
    @Override
    public void addSaveData(SaveData saveData) {
        if (saveData == null) return;
        try {
            saveData.addBoolean("autosummon_enabled", isEnabled);
            saveData.addBoolean("autosummon_on_join", autoSummonOnJoin);
            saveData.addInt("global_custom_summon_limit", globalCustomSummonLimit);
            
            SaveData weaponLimitsData = new SaveData("WEAPON_LIMITS");
            for (Map.Entry<String, Integer> entry : weaponSummonLimits.entrySet()) {
                String key = entry.getKey();
                Integer val = entry.getValue();
                if (key != null && !key.trim().isEmpty() && val != null && (val > 0 || val == -1)) {
                    SaveData limitData = new SaveData("LIMIT");
                    limitData.addSafeString("id", key.trim().toLowerCase());
                    limitData.addInt("limit", val);
                    weaponLimitsData.addSaveData(limitData);
                }
            }
            saveData.addSaveData(weaponLimitsData);
        } catch (Throwable t) {
            AutoSummonLog.error("Failed to serialize AutoSummonModSettings save data", t);
        }
    }
    
    @Override
    public void applyLoadData(LoadData loadData) {
        if (loadData == null) return;
        try {
            isEnabled = loadData.getBoolean("autosummon_enabled", true);
            autoSummonOnJoin = loadData.getBoolean("autosummon_on_join", true);
            int loadedGlobalLimit = loadData.getInt("global_custom_summon_limit", 0);
            globalCustomSummonLimit = Math.max(0, loadedGlobalLimit);
            
            weaponSummonLimits.clear();
            for (LoadData weaponData : loadData.getLoadDataByName("WEAPON_LIMITS")) {
                if (weaponData == null) continue;
                for (LoadData limitData : weaponData.getLoadDataByName("LIMIT")) {
                    if (limitData == null) continue;
                    try {
                        String id = limitData.getSafeString("id", null);
                        int limit = limitData.getInt("limit", 0);
                        if (id != null && !id.trim().isEmpty() && (limit > 0 || limit == -1)) {
                            weaponSummonLimits.put(id.trim().toLowerCase(), limit);
                        }
                    } catch (Throwable t) {
                        AutoSummonLog.warn("Failed to load individual weapon limit entry: " + t.getMessage());
                    }
                }
            }
        } catch (Throwable t) {
            AutoSummonLog.error("Failed to apply AutoSummonModSettings load data", t);
        }
    }
    
    /**
     * Check if auto summon is enabled
     */
    public boolean isEnabled() {
        return isEnabled;
    }
    
    /**
     * Set whether auto summon is enabled
     */
    public void setEnabled(boolean enabled) {
        this.isEnabled = enabled;
    }

    /**
     * Check if auto summon should be active on world join
     */
    public boolean isAutoSummonOnJoin() {
        return autoSummonOnJoin;
    }

    /**
     * Set whether auto summon should be active on world join
     */
    public void setAutoSummonOnJoin(boolean autoSummonOnJoin) {
        this.autoSummonOnJoin = autoSummonOnJoin;
    }
    
    /**
     * Set global custom summon limit
     */
    public void setGlobalCustomSummonLimit(int limit) {
        this.globalCustomSummonLimit = Math.max(0, limit);
    }
    
    /**
     * Get global custom summon limit (0 = default dynamic limit)
     */
    public int getGlobalCustomSummonLimit() {
        return globalCustomSummonLimit;
    }
    
    /**
     * Check if global custom limit is active
     */
    public boolean hasGlobalCustomLimit() {
        return globalCustomSummonLimit > 0;
    }
    
    /**
     * Get the configured summon limit for a specific weapon string ID
     * @param itemStringID The item string ID (e.g., "skeletonstaff")
     * @return Custom limit if configured (> 0), -1 if disabled/off, or 0 for auto
     */
    public int getWeaponSummonLimit(String itemStringID) {
        if (itemStringID == null) return 0;
        Integer limit = weaponSummonLimits.get(itemStringID.toLowerCase());
        return limit != null ? limit : 0;
    }

    /**
     * Check if a specific weapon has been disabled/off in configuration
     */
    public boolean isWeaponDisabled(String itemStringID) {
        return getWeaponSummonLimit(itemStringID) == -1;
    }

    /**
     * Enable or disable a specific weapon
     */
    public void setWeaponDisabled(String itemStringID, boolean disabled) {
        setWeaponSummonLimit(itemStringID, disabled ? -1 : 0);
    }
    
    /**
     * Set the configured summon limit for a specific weapon
     * @param itemStringID The item string ID
     * @param limit Limit (> 0 to cap, -1 to disable/off, 0 to reset to Auto)
     */
    public void setWeaponSummonLimit(String itemStringID, int limit) {
        if (itemStringID == null || itemStringID.trim().isEmpty()) return;
        String normalizedId = itemStringID.trim().toLowerCase();
        if (limit == 0) {
            weaponSummonLimits.remove(normalizedId);
        } else if (limit == -1 || limit > 0) {
            weaponSummonLimits.put(normalizedId, limit);
        }
    }
    
    /**
     * Resets custom limits for all slot-consuming weapons (drawMaxSummons == true)
     * back to Auto (0). Leaves disabled (-1) and dedicated follower staves intact.
     */
    public void resetSlotWeaponLimits(necesse.entity.mobs.PlayerMob player) {
        if (player == null || player.getInv() == null || player.getInv().main == null) {
            return;
        }
        try {
            for (int i = 0; i < player.getInv().main.getSize(); i++) {
                necesse.inventory.InventoryItem item = player.getInv().main.getItem(i);
                if (item != null && item.item != null) {
                    autosummon.compat.SummonWeaponAdapter adapter = autosummon.compat.SummonCompatRegistry.getAdapter(item);
                    if (adapter != null && adapter.isGlobalSlotWeapon(item, player)) {
                        String id = adapter.getItemStringID();
                        if (id != null) {
                            Integer limit = weaponSummonLimits.get(id.toLowerCase());
                            if (limit != null && limit > 0) {
                                weaponSummonLimits.remove(id.toLowerCase());
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            AutoSummonLog.error("Error during resetSlotWeaponLimits", t);
        }
    }
    
    /**
     * Clear all per-weapon summon limits
     */
    public void clearAllWeaponLimits() {
        weaponSummonLimits.clear();
    }
    
    /**
     * Get unmodifiable map of configured weapon limits
     */
    public Map<String, Integer> getAllWeaponLimits() {
        return Collections.unmodifiableMap(weaponSummonLimits);
    }
}
