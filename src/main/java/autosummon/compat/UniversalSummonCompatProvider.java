package autosummon.compat;

import autosummon.api.AutoSummonableWeapon;
import autosummon.util.AutoSummonLog;
import necesse.engine.registries.DamageTypeRegistry;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.PlayerMob;
import necesse.entity.mobs.summon.FollowingWormMobHead;
import necesse.entity.mobs.summon.summonFollowingMob.SummonedFollowingMob;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.Item;
import necesse.inventory.item.toolItem.ToolItem;
import necesse.inventory.item.toolItem.summonToolItem.SummonToolItem;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Next-generation universal compatibility provider for Necesse.
 * Automatically identifies and adapts vanilla summon staves, third-party mod weapons extending
 * {@link SummonToolItem} or {@link ToolItem} with summon damage, and custom items implementing
 * {@link AutoSummonableWeapon} with zero external configuration required.
 */
public class UniversalSummonCompatProvider implements SummonCompatProvider {

    private static final Map<String, Set<String>> KNOWN_MULTI_MOBS = new HashMap<>();

    static {
        // Brain on a Stick alternately summons baby zombies and baby zombie archers
        KNOWN_MULTI_MOBS.put("brainonastick", new HashSet<>(Arrays.asList("babyzombie", "babyzombiearcher")));
    }

    private static final Set<String> EXCLUDED_ITEM_PATTERNS = new HashSet<>(Arrays.asList(
            "whip", "claw", "sword", "boomerang", "trinket", "lantern", "teapot", "armor"
    ));

    private final Map<String, Boolean> handleCache = new ConcurrentHashMap<>();

    @Override
    public String getModID() {
        return "universal";
    }

    @Override
    public boolean isModLoaded() {
        return true;
    }

    @Override
    public void init() {
        handleCache.clear();
    }

    @Override
    public boolean canHandle(InventoryItem item) {
        if (item == null || item.item == null) return false;

        String id = null;
        try {
            id = item.item.getStringID();
        } catch (Throwable ignored) {}

        if (id != null) {
            Boolean cached = handleCache.get(id);
            if (cached != null) return cached;
        }

        boolean result = evaluateCanHandle(item);
        if (id != null) {
            handleCache.put(id, result);
        }
        return result;
    }

    private boolean evaluateCanHandle(InventoryItem item) {
        Item itemObj = item.item;
        if (itemObj == null) return false;

        // 1. Direct interface implementation
        if (itemObj instanceof AutoSummonableWeapon) {
            return !((AutoSummonableWeapon) itemObj).isAutoSummonDisabled(item);
        }

        // 2. Standard Necesse SummonToolItem
        if (itemObj instanceof SummonToolItem) {
            return true;
        }

        // 3. Check for ToolItem with summon characteristics or summon damage type
        if (itemObj instanceof ToolItem) {
            String className = itemObj.getClass().getName().toLowerCase();
            for (String pattern : EXCLUDED_ITEM_PATTERNS) {
                if (className.contains(pattern)) {
                    return false;
                }
            }
            if (className.contains("summon") || className.contains("sentry") || className.contains("scepter")) {
                return true;
            }
            try {
                java.lang.reflect.Field field = ToolItem.class.getDeclaredField("damageType");
                field.setAccessible(true);
                Object dt = field.get(itemObj);
                if (dt != null && dt.equals(DamageTypeRegistry.SUMMON)) {
                    return true;
                }
            } catch (Throwable ignored) {}
        }

        return false;
    }

    @Override
    public SummonWeaponAdapter createAdapter(InventoryItem item) {
        if (item == null || item.item == null) return null;

        if (item.item instanceof AutoSummonableWeapon) {
            return new AutoSummonableItemAdapter((AutoSummonableWeapon) item.item, item.item);
        }

        if (item.item instanceof SummonToolItem) {
            return new UniversalSummonToolAdapter((SummonToolItem) item.item);
        }

        if (item.item instanceof ToolItem) {
            return new UniversalToolItemAdapter((ToolItem) item.item);
        }

        return null;
    }

    /**
     * Adapter for items implementing {@link AutoSummonableWeapon}
     */
    public static class AutoSummonableItemAdapter implements SummonWeaponAdapter {
        private final AutoSummonableWeapon summonable;
        private final Item itemObj;
        private final String stringID;

        public AutoSummonableItemAdapter(AutoSummonableWeapon summonable, Item itemObj) {
            this.summonable = summonable;
            this.itemObj = itemObj;
            this.stringID = itemObj != null && itemObj.getStringID() != null ? itemObj.getStringID() : "";
        }

        @Override
        public String getItemStringID() {
            return stringID;
        }

        @Override
        public String getDisplayName(InventoryItem item) {
            try {
                if (item != null) return item.getItemDisplayName();
            } catch (Throwable ignored) {}
            return stringID;
        }

        @Override
        public boolean isGlobalSlotWeapon(InventoryItem item, PlayerMob player) {
            return getCategory(item, player) == SummonCategory.GLOBAL_SLOT;
        }

        @Override
        public float getSummonSpaceCost(InventoryItem item, PlayerMob player) {
            try {
                return summonable.getSummonSpaceCost(item, player);
            } catch (Throwable t) {
                return 1.0f;
            }
        }

        @Override
        public int getMaxAllowed(InventoryItem item, PlayerMob player, int playerMaxSummons) {
            try {
                if (isGlobalSlotWeapon(item, player)) {
                    if (playerMaxSummons <= 0) return 0;
                    float cost = getSummonSpaceCost(item, player);
                    if (cost <= 0) cost = 1.0f;
                    return Math.max(1, (int) Math.floor(playerMaxSummons / cost));
                }
                return Math.max(1, summonable.getSummonMaxAllowed(item, player, playerMaxSummons));
            } catch (Throwable t) {
                return Math.max(0, playerMaxSummons);
            }
        }


        @Override
        public boolean isMatchingMinion(Mob mob, InventoryItem item) {
            if (mob == null || mob.removed() || mob.getHealth() <= 0) return false;
            if (SummonCompatRegistry.isIgnoredFollower(mob)) return false;
            try {
                return summonable.isMatchingSummonMinion(mob, item);
            } catch (Throwable t) {
                return false;
            }
        }

        @Override
        public String canAttack(PlayerMob player, InventoryItem item) {
            try {
                String custom = summonable.canAutoSummon(player, item);
                if (custom != null) return custom;
                if (itemObj instanceof ToolItem && player != null && player.getLevel() != null) {
                    return ((ToolItem) itemObj).canAttack(player.getLevel(), (int) player.getX(), (int) player.getY(), player, item);
                }
            } catch (Throwable t) {
                return "Attack check failed";
            }
            return null;
        }

        @Override
        public boolean isSecondarySummon() {
            return summonable.getSummonCategory(null, null) == SummonCategory.INDEPENDENT_FOLLOWER;
        }

        @Override
        public boolean isSupportWeapon() {
            return summonable.getSummonCategory(null, null) == SummonCategory.SENTRY_SUPPORT;
        }

        @Override
        public SummonCategory getCategory(InventoryItem item, PlayerMob player) {
            try {
                return summonable.getSummonCategory(item, player);
            } catch (Throwable t) {
                return SummonCategory.GLOBAL_SLOT;
            }
        }

        @Override
        public String getWeaponTypeDisplay(InventoryItem item, PlayerMob player, int maxAllowed) {
            try {
                if (isSecondarySummon()) {
                    return "Follower (Cap: " + maxAllowed + ")";
                } else if (isSupportWeapon()) {
                    return "Sentry (Cap: " + maxAllowed + ")";
                } else {
                    float cost = getSummonSpaceCost(item, player);
                    String costDisplay = (cost == (int) cost) ? String.valueOf((int) cost) : String.format("%.1f", cost);
                    return costDisplay + " slot(s)/summon (Max: " + maxAllowed + ")";
                }
            } catch (Throwable t) {
                return "Summon (Cap: " + maxAllowed + ")";
            }
        }
    }

    /**
     * Smart adapter for standard Necesse {@link SummonToolItem} instances
     */
    public static class UniversalSummonToolAdapter implements SummonWeaponAdapter {
        protected final SummonToolItem staff;
        protected final String stringID;
        protected final String staffMobLower;
        protected final Set<String> multiMobs;
        protected final boolean isFollower;
        protected final boolean isSentry;

        public UniversalSummonToolAdapter(SummonToolItem staff) {
            this.staff = staff;
            String id = "";
            try {
                if (staff != null && staff.getStringID() != null) {
                    id = staff.getStringID().toLowerCase();
                }
            } catch (Throwable ignored) {}
            this.stringID = id;

            String mob = "";
            try {
                if (staff != null && staff.mobStringID != null) {
                    mob = staff.mobStringID.toLowerCase();
                }
            } catch (Throwable ignored) {}
            this.staffMobLower = mob;
            this.multiMobs = KNOWN_MULTI_MOBS.getOrDefault(this.stringID, Collections.emptySet());

            boolean follower = false;
            boolean sentry = false;

            if (staff != null) {
                String staffClass = staff.getClass().getName().toLowerCase();
                if (staffClass.contains("sentry") || staffClass.contains("turret") || staffClass.contains("totem")
                        || stringID.contains("sentry") || stringID.contains("scepter")) {
                    sentry = true;
                } else if (!staff.drawMaxSummons || (staff.summonType != null && !staff.summonType.equalsIgnoreCase("summonedmob"))) {
                    follower = true;
                }
            }

            this.isFollower = follower;
            this.isSentry = sentry;
        }

        public SummonToolItem getStaff() {
            return staff;
        }

        @Override
        public String getItemStringID() {
            try {
                if (staff != null && staff.getStringID() != null) {
                    return staff.getStringID();
                }
            } catch (Throwable ignored) {}
            return stringID;
        }

        @Override
        public String getDisplayName(InventoryItem item) {
            try {
                if (item != null) return item.getItemDisplayName();
            } catch (Throwable ignored) {}
            return staff != null ? staff.getStringID() : "Unknown Staff";
        }

        @Override
        public boolean isGlobalSlotWeapon(InventoryItem item, PlayerMob player) {
            if (isFollower || isSentry || staff == null) return false;
            try {
                boolean isStandardSummonMob = staff.summonType == null || staff.summonType.equalsIgnoreCase("summonedmob");
                return staff.drawMaxSummons && isStandardSummonMob;
            } catch (Throwable t) {
                return true;
            }
        }

        @Override
        public float getSummonSpaceCost(InventoryItem item, PlayerMob player) {
            if (staff == null) return 1.0f;
            try {
                float cost = staff.getSummonSpaceTaken(item, player);
                if (Float.isNaN(cost) || Float.isInfinite(cost) || cost <= 0) {
                    return 1.0f;
                }
                return cost;
            } catch (Throwable t) {
                return 1.0f;
            }
        }

        @Override
        public int getMaxAllowed(InventoryItem item, PlayerMob player, int playerMaxSummons) {
            if (staff == null) return 1;
            try {
                if (isGlobalSlotWeapon(item, player)) {
                    if (playerMaxSummons <= 0) return 0;
                    float cost = getSummonSpaceCost(item, player);
                    if (cost <= 0) cost = 1.0f;
                    return Math.max(1, (int) Math.floor(playerMaxSummons / cost));
                } else {
                    return Math.max(1, staff.getMaxSummons(item, player));
                }
            } catch (Throwable t) {
                return 1;
            }
        }

        @Override
        public boolean isMatchingMinion(Mob mob, InventoryItem item) {
            if (mob == null || staff == null || mob.removed() || mob.getHealth() <= 0) return false;
            if (SummonCompatRegistry.isIgnoredFollower(mob)) return false;

            try {
                String mobStringID = mob.getStringID();
                if (mobStringID == null) return false;

                // 1. Direct mob string ID match
                if (staff.mobStringID != null && (mobStringID.equals(staff.mobStringID) || mobStringID.equalsIgnoreCase(staff.mobStringID))) {
                    return true;
                }

                String mobLower = mobStringID.toLowerCase();

                // 2. Known multi-mob variants
                if (!multiMobs.isEmpty() && multiMobs.contains(mobLower)) {
                    return true;
                }

                // 3. Prefix matching
                if (!staffMobLower.isEmpty() && mobLower.startsWith(staffMobLower)) {
                    return true;
                }

                // 4. Summon type match
                if (staff.summonType != null && staff.summonType.equalsIgnoreCase(mobStringID)) {
                    return true;
                }

                // 5. Inventory item back-reference checks
                if (mob instanceof SummonedFollowingMob) {
                    SummonedFollowingMob sfm = (SummonedFollowingMob) mob;
                    if (sfm.removeWhenNotInInventoryItem != null && sfm.removeWhenNotInInventoryItem.getStringID().equalsIgnoreCase(staff.getStringID())) {
                        return true;
                    }
                }

                if (mob instanceof FollowingWormMobHead) {
                    FollowingWormMobHead<?, ?> fwh = (FollowingWormMobHead<?, ?>) mob;
                    if (fwh.removeWhenNotInInventoryItem != null && fwh.removeWhenNotInInventoryItem.getStringID().equalsIgnoreCase(staff.getStringID())) {
                        return true;
                    }
                }
            } catch (Throwable t) {
                AutoSummonLog.logThrottledError("universal_isMatchingMinion", "Error matching minion for " + stringID, t, 5000);
            }

            return false;
        }

        @Override
        public String canAttack(PlayerMob player, InventoryItem item) {
            if (player == null || player.getLevel() == null || item == null || staff == null) {
                return "Invalid state";
            }
            try {
                return staff.canAttack(player.getLevel(), (int) player.getX(), (int) player.getY(), player, item);
            } catch (Throwable t) {
                return "Attack check failed";
            }
        }

        @Override
        public boolean isSecondarySummon() {
            return isFollower;
        }

        @Override
        public boolean isSupportWeapon() {
            return isSentry;
        }

        @Override
        public SummonCategory getCategory(InventoryItem item, PlayerMob player) {
            if (isSentry) return SummonCategory.SENTRY_SUPPORT;
            if (isFollower) return SummonCategory.INDEPENDENT_FOLLOWER;
            return SummonCategory.GLOBAL_SLOT;
        }

        @Override
        public String getWeaponTypeDisplay(InventoryItem item, PlayerMob player, int maxAllowed) {
            try {
                if (isSentry) {
                    return "Sentry (Cap: " + maxAllowed + ")";
                } else if (isFollower) {
                    return "Follower (Cap: " + maxAllowed + ")";
                } else {
                    float spaceCost = getSummonSpaceCost(item, player);
                    String costDisplay = (spaceCost == (int) spaceCost) ? String.valueOf((int) spaceCost) : String.format("%.1f", spaceCost);
                    return costDisplay + " slot(s)/summon (Max: " + maxAllowed + ")";
                }
            } catch (Throwable t) {
                return "Summon (Cap: " + maxAllowed + ")";
            }
        }
    }

    /**
     * Fallback adapter for custom {@link ToolItem} instances with summon damage
     */
    public static class UniversalToolItemAdapter implements SummonWeaponAdapter {
        protected final ToolItem tool;
        protected final String stringID;

        public UniversalToolItemAdapter(ToolItem tool) {
            this.tool = tool;
            String id = "";
            try {
                if (tool != null && tool.getStringID() != null) {
                    id = tool.getStringID().toLowerCase();
                }
            } catch (Throwable ignored) {}
            this.stringID = id;
        }

        @Override
        public String getItemStringID() {
            try {
                if (tool != null && tool.getStringID() != null) {
                    return tool.getStringID();
                }
            } catch (Throwable ignored) {}
            return stringID;
        }

        @Override
        public String getDisplayName(InventoryItem item) {
            try {
                if (item != null) return item.getItemDisplayName();
            } catch (Throwable ignored) {}
            return stringID;
        }

        @Override
        public boolean isGlobalSlotWeapon(InventoryItem item, PlayerMob player) {
            return true;
        }

        @Override
        public float getSummonSpaceCost(InventoryItem item, PlayerMob player) {
            return 1.0f;
        }

        @Override
        public int getMaxAllowed(InventoryItem item, PlayerMob player, int playerMaxSummons) {
            if (playerMaxSummons <= 0) return 0;
            return Math.max(1, playerMaxSummons);
        }

        @Override
        public boolean isMatchingMinion(Mob mob, InventoryItem item) {
            if (mob == null || tool == null || mob.removed() || mob.getHealth() <= 0) return false;
            if (SummonCompatRegistry.isIgnoredFollower(mob)) return false;

            String mobID = mob.getStringID();
            if (mobID == null) return false;

            if (mobID.toLowerCase().contains(stringID) || stringID.contains(mobID.toLowerCase())) {
                return true;
            }

            if (mob instanceof SummonedFollowingMob) {
                SummonedFollowingMob sfm = (SummonedFollowingMob) mob;
                if (sfm.removeWhenNotInInventoryItem != null && sfm.removeWhenNotInInventoryItem.getStringID().equalsIgnoreCase(tool.getStringID())) {
                    return true;
                }
            }

            return false;
        }

        @Override
        public String canAttack(PlayerMob player, InventoryItem item) {
            if (player == null || player.getLevel() == null || item == null || tool == null) {
                return "Invalid state";
            }
            try {
                return tool.canAttack(player.getLevel(), (int) player.getX(), (int) player.getY(), player, item);
            } catch (Throwable t) {
                return "Attack check failed";
            }
        }

        @Override
        public boolean isSecondarySummon() {
            return false;
        }

        @Override
        public boolean isSupportWeapon() {
            return false;
        }

        @Override
        public SummonCategory getCategory(InventoryItem item, PlayerMob player) {
            return SummonCategory.GLOBAL_SLOT;
        }

        @Override
        public String getWeaponTypeDisplay(InventoryItem item, PlayerMob player, int maxAllowed) {
            return "1 slot(s)/summon (Max: " + maxAllowed + ")";
        }
    }
}
