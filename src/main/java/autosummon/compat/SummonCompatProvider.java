package autosummon.compat;

import necesse.inventory.InventoryItem;

/**
 * Service provider interface for mod compatibility integrations.
 */
public interface SummonCompatProvider {

    /**
     * The unique mod identifier this provider integrates with (e.g., "vanilla", "gagadoliano.summonerexpansion")
     */
    String getModID();

    /**
     * Check if the target mod is currently loaded and active in the game runtime
     */
    boolean isModLoaded();

    /**
     * Initialize any caches, registries, or mappings for this provider
     */
    void init();

    /**
     * Determine if this provider handles the specified inventory item
     */
    boolean canHandle(InventoryItem item);

    /**
     * Create or return a cached SummonWeaponAdapter for the given item
     */
    SummonWeaponAdapter createAdapter(InventoryItem item);
}
