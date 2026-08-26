package autosummon.compat;

import autosummon.util.AutoSummonLog;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.WormMobBody;
import necesse.entity.mobs.summon.summonFollowingMob.petFollowingMob.PetFollowingMob;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-performance, zero-allocation classifier for mob entities.
 * Caches metadata (worm bodies, trinket followers, pets, mounts) in O(1)
 * thread-safe lookup tables, eliminating repeated toLowerCase() and string allocations on every tick.
 */
public final class MobClassifier {

    public static final class MobMetadata {
        public final String stringID;
        public final boolean isWormBody;
        public final boolean isTrinketOrPassive;
        public final boolean isIgnored; // isWormBody || isTrinketOrPassive

        public MobMetadata(String stringID, boolean isWormBody, boolean isTrinketOrPassive) {
            this.stringID = stringID;
            this.isWormBody = isWormBody;
            this.isTrinketOrPassive = isTrinketOrPassive;
            this.isIgnored = isWormBody || isTrinketOrPassive;
        }
    }

    private static final Set<String> KNOWN_TRINKET_FOLLOWERS = new HashSet<>(Arrays.asList(
        "willowisp",
        "teapotminion",
        "beetminion",
        "carrotminion",
        "onionminion",
        "potatominion",
        "pumpkinminion",
        "jellyfishminion",
        "livingarmorstandminion",
        "dragonicflamesminion",
        "willowisplantern",
        "minerslantern",
        "calmminerslantern",
        "magicteapot",
        "giantbeet",
        "giantcarrot",
        "giantonion",
        "giantpotato",
        "giantpumpkin",
        "jellyfishbowl",
        "livingarmorstand"
    ));

    private static final Set<String> KNOWN_WORM_BODIES = new HashSet<>(Arrays.asList(
        "sandwormbodyminion",
        "fallendragonbodyminion"
    ));

    private static final Set<String> dynamicIgnoredMobs = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final ConcurrentHashMap<String, MobMetadata> metadataCache = new ConcurrentHashMap<>(128);
    private static final MobMetadata DEFAULT_VALID = new MobMetadata("", false, false);
    private static final MobMetadata DEFAULT_WORM = new MobMetadata("", true, false);
    private static final MobMetadata DEFAULT_TRINKET = new MobMetadata("", false, true);

    static {
        // Pre-warm known trinket and worm IDs
        for (String id : KNOWN_TRINKET_FOLLOWERS) {
            metadataCache.put(id, new MobMetadata(id, false, true));
        }
        for (String id : KNOWN_WORM_BODIES) {
            metadataCache.put(id, new MobMetadata(id, true, false));
        }
    }

    /**
     * Register a custom mob ID to be ignored by Auto Summon capacity and despawning
     */
    public static void registerIgnoredMob(String mobStringID) {
        if (mobStringID == null || mobStringID.trim().isEmpty()) return;
        String clean = mobStringID.trim().toLowerCase();
        dynamicIgnoredMobs.add(clean);
        metadataCache.put(clean, new MobMetadata(clean, false, true));
        metadataCache.put(mobStringID.trim(), new MobMetadata(mobStringID.trim(), false, true));
    }

    private MobClassifier() {}

    /**
     * Get or compute the classification metadata for a mob entity in O(1)
     */
    public static MobMetadata getMetadata(Mob mob) {
        if (mob == null) return DEFAULT_VALID;

        if (mob instanceof WormMobBody) {
            return DEFAULT_WORM;
        }
        if (mob.isMounted() || mob instanceof PetFollowingMob) {
            return DEFAULT_TRINKET;
        }

        String stringID = mob.getStringID();
        if (stringID == null || stringID.isEmpty()) {
            return DEFAULT_VALID;
        }

        MobMetadata cached = metadataCache.get(stringID);
        if (cached != null) {
            return cached;
        }

        return computeMetadata(mob, stringID);
    }

    private static MobMetadata computeMetadata(Mob mob, String stringID) {
        try {
            String lower = stringID.toLowerCase();

            boolean worm = KNOWN_WORM_BODIES.contains(lower) || lower.contains("bodyminion");
            boolean trinket = false;

            if (!worm) {
                if (KNOWN_TRINKET_FOLLOWERS.contains(lower)) {
                    trinket = true;
                } else if (lower.contains("pet") || lower.contains("mount") || lower.contains("wisp")
                        || lower.contains("teapot") || lower.contains("lantern")) {
                    trinket = true;
                } else {
                    Class<?> mobClass = mob.getClass();
                    if (mobClass != null) {
                        String className = mobClass.getName().toLowerCase();
                        if (className.contains("pet") || className.contains("trinket")
                                || className.contains("wisp") || className.contains("teapot")) {
                            trinket = true;
                        }
                    }
                }
            }

            MobMetadata meta = new MobMetadata(stringID, worm, trinket);
            metadataCache.put(stringID, meta);
            if (!stringID.equals(lower)) {
                metadataCache.put(lower, meta);
            }
            return meta;
        } catch (Throwable t) {
            AutoSummonLog.logThrottledError("mob_classify_err", "Error computing mob metadata for " + stringID, t, 5000);
            return DEFAULT_VALID;
        }
    }

    /**
     * Fast O(1) check if mob is a worm body segment
     */
    public static boolean isWormBody(Mob mob) {
        if (mob == null) return false;
        if (mob instanceof WormMobBody) return true;
        return getMetadata(mob).isWormBody;
    }

    /**
     * Fast O(1) check if mob is a trinket companion, passive pet, or mount
     */
    public static boolean isTrinketOrPassiveFollower(Mob mob) {
        if (mob == null) return false;
        if (mob.isMounted() || mob instanceof PetFollowingMob) return true;
        return getMetadata(mob).isTrinketOrPassive;
    }

    /**
     * Fast O(1) combined check if mob should be ignored during summon capacity accounting
     */
    public static boolean isIgnoredFollower(Mob mob) {
        if (mob == null) return true;
        if (mob.isMounted() || mob instanceof PetFollowingMob || mob instanceof WormMobBody) return true;
        return getMetadata(mob).isIgnored;
    }

    /**
     * Clear dynamic cache entries (retaining pre-warmed and dynamically registered sets)
     */
    public static void clearCache() {
        metadataCache.clear();
        for (String id : KNOWN_TRINKET_FOLLOWERS) {
            metadataCache.put(id, new MobMetadata(id, false, true));
        }
        for (String id : KNOWN_WORM_BODIES) {
            metadataCache.put(id, new MobMetadata(id, true, false));
        }
        for (String id : dynamicIgnoredMobs) {
            metadataCache.put(id, new MobMetadata(id, false, true));
        }
    }

    /**
     * Completely reset classifier state for tests
     */
    public static void resetForTesting() {
        dynamicIgnoredMobs.clear();
        clearCache();
    }
}
