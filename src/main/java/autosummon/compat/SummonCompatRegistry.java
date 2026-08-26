package autosummon.compat;

import autosummon.api.SummonWeaponSpec;
import autosummon.util.AutoSummonLog;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.PlayerMob;
import necesse.entity.mobs.buffs.BuffModifiers;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.toolItem.summonToolItem.SummonToolItem;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Central registry and dispatcher for summon weapon compatibility providers,
 * declarative API specs, custom adapters, and data-driven JSON configurations.
 * Fully thread-safe, zero-allocation caching, and protected against faulty third-party providers.
 */
public class SummonCompatRegistry {

    private static final List<SummonCompatProvider> providers = new CopyOnWriteArrayList<>();
    private static final Map<String, SummonWeaponAdapter> adapterCache = new ConcurrentHashMap<>();
    private static final Map<String, SummonWeaponAdapter> minionToAdapterCache = new ConcurrentHashMap<>();
    private static final Map<String, SummonWeaponAdapter> dynamicAdapters = new ConcurrentHashMap<>();
    private static final UniversalSummonCompatProvider universalProvider = new UniversalSummonCompatProvider();
    private static final VanillaSummonCompatProvider vanillaFallbackProvider = new VanillaSummonCompatProvider();
    private static volatile boolean initialized = false;

    /**
     * Initialize all registered compatibility providers and auto-discover JSON configurations.
     */
    public static synchronized void init() {
        if (initialized) return;

        try {
            providers.clear();
            adapterCache.clear();
            minionToAdapterCache.clear();

            // 1. Re-register dynamic adapters
            for (Map.Entry<String, SummonWeaponAdapter> entry : dynamicAdapters.entrySet()) {
                adapterCache.put(entry.getKey(), entry.getValue());
            }

            // 2. Specialized mod providers (ordered by priority)
            registerProvider(new SummonerExpansionCompatProvider());

            // 3. Smart universal auto-discovery provider
            registerProvider(universalProvider);

            // 4. Vanilla fallback provider
            registerProvider(vanillaFallbackProvider);

            // Initialize all providers
            for (SummonCompatProvider provider : providers) {
                if (provider == null) continue;
                try {
                    if (provider.isModLoaded()) {
                        provider.init();
                    }
                } catch (Throwable t) {
                    AutoSummonLog.error("Failed to initialize provider for mod: " + provider.getModID(), t);
                }
            }

            // 5. Discover data-driven JSON configs from loaded mod JAR resources
            try {
                AutoSummonJsonLoader.loadAllConfigs();
            } catch (Throwable t) {
                AutoSummonLog.error("Error running AutoSummonJsonLoader during init", t);
            }

            initialized = true;
            AutoSummonLog.info("Summon compatibility registry initialized with " + providers.size() + " providers and " + dynamicAdapters.size() + " dynamic adapters.");
        } catch (Throwable t) {
            AutoSummonLog.error("Critical error during SummonCompatRegistry.init()", t);
        }
    }

    /**
     * Register a new custom compatibility provider
     */
    public static synchronized void registerProvider(SummonCompatProvider provider) {
        if (provider != null && !providers.contains(provider)) {
            providers.add(provider);
            adapterCache.clear();
            minionToAdapterCache.clear();
            // Keep dynamic adapters in cache
            for (Map.Entry<String, SummonWeaponAdapter> entry : dynamicAdapters.entrySet()) {
                adapterCache.put(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Register a weapon specification from the declarative AutoSummonAPI
     */
    public static synchronized void registerSpec(SummonWeaponSpec spec) {
        if (spec == null || spec.getItemID() == null) return;
        SummonWeaponAdapter adapter = spec.toAdapter();
        registerCustomAdapter(spec.getItemID(), adapter);

        // Pre-register all declared minion IDs for instant O(1) matching
        for (String minionID : spec.getMinionIDs()) {
            registerMinionMapping(minionID, adapter);
        }
    }

    /**
     * Register a custom adapter directly for a specific item ID
     */
    public static synchronized void registerCustomAdapter(String itemID, SummonWeaponAdapter adapter) {
        if (itemID == null || adapter == null) return;
        String cleanID = itemID.trim().toLowerCase();
        dynamicAdapters.put(cleanID, adapter);
        adapterCache.put(cleanID, adapter);
        adapterCache.put(itemID.trim(), adapter);
    }

    /**
     * Quick-register a standard summon staff
     */
    public static void registerStaff(String itemID, String... minionIDs) {
        if (itemID == null) return;
        registerSpec(SummonWeaponSpec.builder(itemID).asGlobalSlot(1.0f).withMinions(minionIDs).build());
    }

    /**
     * Quick-register an independent follower weapon
     */
    public static void registerFollower(String itemID, int maxAllowed, String... minionIDs) {
        if (itemID == null) return;
        registerSpec(SummonWeaponSpec.builder(itemID).asFollower(maxAllowed).withMinions(minionIDs).build());
    }

    /**
     * Quick-register a sentry / support tool
     */
    public static void registerSentry(String itemID, int maxAllowed, String... minionIDs) {
        if (itemID == null) return;
        registerSpec(SummonWeaponSpec.builder(itemID).asSentry(maxAllowed).withMinions(minionIDs).build());
    }

    /**
     * Register a custom mob ID to be ignored from summon capacity accounting and dismissal
     */
    public static void registerIgnoredMob(String mobStringID) {
        MobClassifier.registerIgnoredMob(mobStringID);
    }

    /**
     * Register a provider by its fully-qualified class name dynamically via reflection.
     * Allows third-party mods to register providers without compile-time hard dependencies.
     */
    public static synchronized boolean registerProviderClass(String className) {
        if (className == null || className.trim().isEmpty()) return false;
        try {
            Class<?> clazz = Class.forName(className);
            if (SummonCompatProvider.class.isAssignableFrom(clazz)) {
                SummonCompatProvider provider = (SummonCompatProvider) clazz.getDeclaredConstructor().newInstance();
                registerProvider(provider);
                AutoSummonLog.info("Dynamically registered provider class: " + className);
                return true;
            } else {
                AutoSummonLog.warn("Class '" + className + "' does not implement SummonCompatProvider.");
            }
        } catch (ClassNotFoundException e) {
            AutoSummonLog.debug("Provider class '" + className + "' not present on classpath (optional mod not loaded).");
        } catch (Throwable t) {
            AutoSummonLog.error("Failed to instantiate provider class: " + className, t);
        }
        return false;
    }

    /**
     * Get unmodifiable list of registered providers
     */
    public static List<SummonCompatProvider> getProviders() {
        return java.util.Collections.unmodifiableList(providers);
    }

    /**
     * Reset registry state (primarily for automated test isolation)
     */
    public static synchronized void resetForTesting() {
        providers.clear();
        adapterCache.clear();
        minionToAdapterCache.clear();
        dynamicAdapters.clear();
        MobClassifier.resetForTesting();
        AutoSummonJsonLoader.resetForTesting();
        initialized = false;
    }

    /**
     * Check if an inventory item is a valid summon weapon supported by any provider or spec
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

        try {
            String itemID = null;
            try {
                itemID = item.item.getStringID();
            } catch (Throwable ignored) {}

            if (itemID != null) {
                // 1. Direct dynamic adapter match
                SummonWeaponAdapter dynamic = dynamicAdapters.get(itemID.toLowerCase());
                if (dynamic != null) {
                    return dynamic;
                }

                // 2. Cache lookup
                SummonWeaponAdapter cached = adapterCache.get(itemID);
                if (cached != null) {
                    return cached;
                }
                cached = adapterCache.get(itemID.toLowerCase());
                if (cached != null) {
                    return cached;
                }
            }

            // 3. Check registered providers in priority order
            for (SummonCompatProvider provider : providers) {
                if (provider == null) continue;
                try {
                    if (provider.isModLoaded() && provider.canHandle(item)) {
                        SummonWeaponAdapter adapter = provider.createAdapter(item);
                        if (adapter != null) {
                            if (itemID != null) {
                                adapterCache.put(itemID, adapter);
                                adapterCache.put(itemID.toLowerCase(), adapter);
                            }
                            return adapter;
                        }
                    }
                } catch (Throwable t) {
                    AutoSummonLog.logThrottledError("adapter_resolve_" + itemID,
                            "Error resolving adapter for item '" + itemID + "' with provider '" + provider.getModID() + "'", t, 5000);
                }
            }
        } catch (Throwable t) {
            AutoSummonLog.logThrottledError("get_adapter_general", "Unexpected error in SummonCompatRegistry.getAdapter", t, 5000);
        }

        return null;
    }

    /**
     * Returns the name/ID of the provider that handles the given item (used for debugging & diagnostics)
     */
    public static String getProviderNameForWeapon(InventoryItem item) {
        if (item == null || item.item == null) return "None";
        String itemID = item.item.getStringID();
        if (itemID != null && dynamicAdapters.containsKey(itemID.toLowerCase())) {
            return "AutoSummonAPI / JSON";
        }
        for (SummonCompatProvider provider : providers) {
            if (provider != null && provider.isModLoaded() && provider.canHandle(item)) {
                return provider.getModID();
            }
        }
        return "Unknown";
    }

    /**
     * Checks if a given mob is a passive/trinket companion or pet/mount that should never
     * be counted towards weapon summon capacity or removed during weapon dismissal passes.
     */
    public static boolean isTrinketOrPassiveFollower(Mob mob) {
        return MobClassifier.isTrinketOrPassiveFollower(mob);
    }

    /**
     * Checks if a given mob entity is a worm body segment (which shouldn't be counted towards limits)
     */
    public static boolean isWormBody(Mob mob) {
        return MobClassifier.isWormBody(mob);
    }

    /**
     * Direct zero-allocation check if mob entity is ignored from summon accounting
     */
    public static boolean isIgnoredFollower(Mob mob) {
        return MobClassifier.isIgnoredFollower(mob);
    }

    /**
     * Pre-register or map a minion string ID to its spawner weapon adapter for instant O(1) matching
     */
    public static void registerMinionMapping(String minionStringID, SummonWeaponAdapter adapter) {
        if (minionStringID != null && adapter != null) {
            minionToAdapterCache.put(minionStringID.toLowerCase().trim(), adapter);
        }
    }

    /**
     * Finds the matching weapon adapter from the player's inventory that spawned a given mob.
     * Uses an inverted minion-to-weapon cache for instant O(1) resolution, falling back to
     * inventory scanning only once on first minion encounter.
     */
    public static SummonWeaponAdapter findWeaponForMob(PlayerMob player, Mob mob) {
        if (mob == null || MobClassifier.isIgnoredFollower(mob)) {
            return null;
        }

        String mobStringID = mob.getStringID();
        if (mobStringID != null) {
            SummonWeaponAdapter cached = minionToAdapterCache.get(mobStringID);
            if (cached != null) {
                return cached;
            }
            String lower = mobStringID.toLowerCase();
            cached = minionToAdapterCache.get(lower);
            if (cached != null) {
                return cached;
            }
        }

        if (player == null || player.getInv() == null || player.getInv().main == null) {
            return null;
        }

        try {
            for (int i = 0; i < player.getInv().main.getSize(); i++) {
                InventoryItem item = player.getInv().main.getItem(i);
                if (item != null && item.item != null) {
                    SummonWeaponAdapter adapter = getAdapter(item);
                    if (adapter != null) {
                        try {
                            if (adapter.isMatchingMinion(mob, item)) {
                                if (mobStringID != null) {
                                    minionToAdapterCache.put(mobStringID, adapter);
                                    minionToAdapterCache.put(mobStringID.toLowerCase(), adapter);
                                }
                                return adapter;
                            }
                        } catch (Throwable t) {
                            AutoSummonLog.logThrottledError("findWeaponForMob_matching", "Error in adapter.isMatchingMinion", t, 5000);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            AutoSummonLog.logThrottledError("findWeaponForMob", "Error finding weapon for mob in inventory", t, 5000);
        }

        return null;
    }

    /**
     * Resolves the effective global summon slot capacity for a player.
     * Incorporates base summon modifiers, BuffManager active modifiers, and any staff-level
     * getMaxSummons overrides from active global slot weapons.
     */
    public static int getPlayerMaxSummonSlots(PlayerMob player) {
        return getPlayerMaxSummonSlots(player, null);
    }

    /**
     * Resolves the effective global summon slot capacity for a player, inspecting candidate weapons for staff-level overrides.
     */
    public static int getPlayerMaxSummonSlots(PlayerMob player, Iterable<InventoryItem> itemsToCheck) {
        int base = 1;
        try {
            if (BuffModifiers.MAX_SUMMONS != null && BuffModifiers.MAX_SUMMONS.defaultBuffManagerValue != null) {
                base = BuffModifiers.MAX_SUMMONS.defaultBuffManagerValue;
            }
        } catch (Throwable ignored) {}

        int slots = base;
        if (player != null && player.buffManager != null) {
            try {
                slots = player.buffManager.getModifier(BuffModifiers.MAX_SUMMONS);
            } catch (Throwable ignored) {}
        }

        if (itemsToCheck != null) {
            for (InventoryItem item : itemsToCheck) {
                if (item != null && item.item != null) {
                    SummonWeaponAdapter adapter = getAdapter(item);
                    if (adapter != null && adapter.isGlobalSlotWeapon(item, player)) {
                        if (item.item instanceof SummonToolItem) {
                            try {
                                int staffMax = ((SummonToolItem) item.item).getMaxSummons(item, player);
                                if (staffMax > slots) {
                                    slots = staffMax;
                                }
                            } catch (Throwable ignored) {}
                        }
                    }
                }
            }
        }

        return Math.max(0, slots);
    }
}
