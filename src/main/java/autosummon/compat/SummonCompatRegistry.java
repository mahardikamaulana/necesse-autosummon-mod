package autosummon.compat;

import necesse.entity.mobs.Mob;
import necesse.entity.mobs.PlayerMob;
import necesse.entity.mobs.WormMobBody;
import necesse.inventory.InventoryItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry and dispatcher for summon weapon compatibility providers.
 */
public class SummonCompatRegistry {

    private static final List<SummonCompatProvider> providers = new ArrayList<>();
    private static final Map<String, SummonWeaponAdapter> adapterCache = new ConcurrentHashMap<>();
    private static final VanillaSummonCompatProvider fallbackProvider = new VanillaSummonCompatProvider();
    private static boolean initialized = false;

    /**
     * Initialize all registered compatibility providers
     */
    public static synchronized void init() {
        if (initialized) return;

        providers.clear();
        adapterCache.clear();

        // 1. Specialized mod providers (ordered by priority)
        registerProvider(new SummonerExpansionCompatProvider());

        // 2. Vanilla fallback provider
        registerProvider(fallbackProvider);

        for (SummonCompatProvider provider : providers) {
            try {
                if (provider.isModLoaded()) {
                    provider.init();
                }
            } catch (Throwable t) {
                System.err.println("[Auto Summon] Failed to initialize provider for mod: " + provider.getModID());
                t.printStackTrace();
            }
        }

        initialized = true;
        System.out.println("[Auto Summon] Summon compatibility registry initialized with " + providers.size() + " providers.");
    }

    /**
     * Register a new custom compatibility provider
     */
    public static synchronized void registerProvider(SummonCompatProvider provider) {
        if (provider != null && !providers.contains(provider)) {
            providers.add(provider);
            adapterCache.clear();
        }
    }

    /**
     * Check if an inventory item is a valid summon weapon supported by any provider
     */
    public static boolean isSummonWeapon(InventoryItem item) {
        if (item == null || item.item == null) return false;
        return getAdapter(item) != null;
    }

    /**
     * Get or create a SummonWeaponAdapter for the given item
     */
    public static SummonWeaponAdapter getAdapter(InventoryItem item) {
        if (item == null || item.item == null) return null;
        if (!initialized) init();

        String itemID = item.item.getStringID();
        SummonWeaponAdapter cached = adapterCache.get(itemID);
        if (cached != null) {
            return cached;
        }

        for (SummonCompatProvider provider : providers) {
            try {
                if (provider.isModLoaded() && provider.canHandle(item)) {
                    SummonWeaponAdapter adapter = provider.createAdapter(item);
                    if (adapter != null) {
                        adapterCache.put(itemID, adapter);
                        return adapter;
                    }
                }
            } catch (Throwable t) {
                System.err.println("[Auto Summon] Error resolving adapter for " + itemID + " with provider " + provider.getModID());
            }
        }

        return null;
    }

    /**
     * Checks if a given mob entity is a worm body segment (which shouldn't be counted towards limits)
     */
    public static boolean isWormBody(Mob mob) {
        if (mob == null) return false;
        if (mob instanceof WormMobBody) return true;
        String stringID = mob.getStringID();
        if (stringID != null) {
            String lower = stringID.toLowerCase();
            if (lower.contains("bodyminion") || lower.equals("sandwormbodyminion") || lower.equals("fallendragonbodyminion")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds the matching weapon adapter from the player's inventory that spawned a given mob
     */
    public static SummonWeaponAdapter findWeaponForMob(PlayerMob player, Mob mob) {
        if (player == null || player.getInv() == null || player.getInv().main == null || mob == null) {
            return null;
        }

        for (int i = 0; i < player.getInv().main.getSize(); i++) {
            InventoryItem item = player.getInv().main.getItem(i);
            if (item != null) {
                SummonWeaponAdapter adapter = getAdapter(item);
                if (adapter != null && adapter.isMatchingMinion(mob, item)) {
                    return adapter;
                }
            }
        }

        return null;
    }
}
