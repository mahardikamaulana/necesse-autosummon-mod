package autosummon;

import necesse.engine.modLoader.ModSettings;
import necesse.engine.save.SaveData;
import necesse.engine.save.LoadData;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Settings class for the auto summon mod that persists the enabled/disabled state
 * and per-weapon summon limits.
 */
public class AutoSummonModSettings extends ModSettings {
    
    // Default values
    private boolean isEnabled = true;
    
    // Global custom summon limit for staffs (0 = use staff/player defaults)
    private int globalCustomSummonLimit = 0;
    
    // Per-weapon custom summon limits (mapped by item string ID -> limit; 0 = Auto/Default)
    private final Map<String, Integer> weaponSummonLimits = new HashMap<>();
    
    public AutoSummonModSettings() {
        super();
    }
    
    @Override
    public void addSaveData(SaveData saveData) {
        saveData.addBoolean("autosummon_enabled", isEnabled);
        saveData.addInt("global_custom_summon_limit", globalCustomSummonLimit);
        
        SaveData weaponLimitsData = new SaveData("WEAPON_LIMITS");
        for (Map.Entry<String, Integer> entry : weaponSummonLimits.entrySet()) {
            if (entry.getValue() != null && entry.getValue() > 0) {
                SaveData limitData = new SaveData("LIMIT");
                limitData.addSafeString("id", entry.getKey());
                limitData.addInt("limit", entry.getValue());
                weaponLimitsData.addSaveData(limitData);
            }
        }
        saveData.addSaveData(weaponLimitsData);
    }
    
    @Override
    public void applyLoadData(LoadData loadData) {
        isEnabled = loadData.getBoolean("autosummon_enabled", true);
        globalCustomSummonLimit = loadData.getInt("global_custom_summon_limit", 0);
        
        weaponSummonLimits.clear();
        for (LoadData weaponData : loadData.getLoadDataByName("WEAPON_LIMITS")) {
            for (LoadData limitData : weaponData.getLoadDataByName("LIMIT")) {
                String id = limitData.getSafeString("id", null);
                int limit = limitData.getInt("limit", 0);
                if (id != null && limit > 0) {
                    weaponSummonLimits.put(id, limit);
                }
            }
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
     * @return Custom limit if configured, or 0 for default
     */
    public int getWeaponSummonLimit(String itemStringID) {
        if (itemStringID == null) return 0;
        return weaponSummonLimits.getOrDefault(itemStringID, 0);
    }
    
    /**
     * Set the configured summon limit for a specific weapon
     * @param itemStringID The item string ID
     * @param limit Limit (> 0 to cap, 0 to reset to default)
     */
    public void setWeaponSummonLimit(String itemStringID, int limit) {
        if (itemStringID == null) return;
        if (limit <= 0) {
            weaponSummonLimits.remove(itemStringID);
        } else {
            weaponSummonLimits.put(itemStringID, limit);
        }
    }
    
    /**
     * Resets custom limits for all slot-consuming weapons (drawMaxSummons == true)
     * back to Auto (0). Leaves dedicated follower staves intact.
     */
    public void resetSlotWeaponLimits(necesse.entity.mobs.PlayerMob player) {
        if (player == null || player.getInv() == null || player.getInv().main == null) {
            return;
        }
        for (int i = 0; i < player.getInv().main.getSize(); i++) {
            necesse.inventory.InventoryItem item = player.getInv().main.getItem(i);
            if (item != null) {
                autosummon.compat.SummonWeaponAdapter adapter = autosummon.compat.SummonCompatRegistry.getAdapter(item);
                if (adapter != null && adapter.isGlobalSlotWeapon(item, player)) {
                    weaponSummonLimits.remove(adapter.getItemStringID());
                }
            }
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
