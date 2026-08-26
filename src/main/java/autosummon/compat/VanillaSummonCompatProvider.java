package autosummon.compat;

import autosummon.util.AutoSummonLog;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.PlayerMob;
import necesse.entity.mobs.summon.FollowingWormMobHead;
import necesse.entity.mobs.summon.summonFollowingMob.SummonedFollowingMob;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.toolItem.summonToolItem.SummonToolItem;

import java.util.*;

/**
 * Standard compatibility provider for vanilla Necesse summon weapons.
 * Includes multi-variant mob matching for weapons like Brain on a Stick with zero-allocation optimizations.
 */
public class VanillaSummonCompatProvider implements SummonCompatProvider {

    // Vanilla multi-mob mappings: item string ID -> Set of spawned minion mob string IDs
    private static final Map<String, Set<String>> VANILLA_MULTI_MOBS = new HashMap<>();

    static {
        // Brain on a Stick alternately summons baby zombies and baby zombie archers
        VANILLA_MULTI_MOBS.put("brainonastick", new HashSet<>(Arrays.asList("babyzombie", "babyzombiearcher")));
    }

    @Override
    public String getModID() {
        return "vanilla";
    }

    @Override
    public boolean isModLoaded() {
        return true;
    }

    @Override
    public void init() {
        // Vanilla is always available
    }

    @Override
    public boolean canHandle(InventoryItem item) {
        return item != null && item.item instanceof SummonToolItem;
    }

    @Override
    public SummonWeaponAdapter createAdapter(InventoryItem item) {
        if (item == null || !(item.item instanceof SummonToolItem)) {
            return null;
        }
        return new VanillaSummonAdapter((SummonToolItem) item.item);
    }

    public static class VanillaSummonAdapter implements SummonWeaponAdapter {
        protected final SummonToolItem staff;
        protected final String stringID;
        protected final String staffMobLower;
        protected final Set<String> multiMobs;

        public VanillaSummonAdapter(SummonToolItem staff) {
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
            this.multiMobs = VANILLA_MULTI_MOBS.getOrDefault(this.stringID, Collections.emptySet());
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
                if (item != null) {
                    return item.getItemDisplayName();
                }
            } catch (Throwable ignored) {}
            return staff != null ? staff.getStringID() : "Unknown Staff";
        }

        @Override
        public boolean isGlobalSlotWeapon(InventoryItem item, PlayerMob player) {
            if (staff == null) return false;
            try {
                // Only weapons that draw max summons AND use standard "summonedmob" group consume global slots
                boolean isStandardSummonMob = staff.summonType == null || staff.summonType.equalsIgnoreCase("summonedmob");
                return staff.drawMaxSummons && isStandardSummonMob;
            } catch (Throwable t) {
                AutoSummonLog.logThrottledError("vanilla_isGlobalSlotWeapon", "Error evaluating isGlobalSlotWeapon for " + stringID, t, 5000);
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
                AutoSummonLog.logThrottledError("vanilla_spaceCost_" + stringID, "Error getting space cost for " + stringID, t, 5000);
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
                AutoSummonLog.logThrottledError("vanilla_maxAllowed_" + stringID, "Error calculating max allowed for " + stringID, t, 5000);
                return 1;
            }
        }

        @Override
        public boolean isMatchingMinion(Mob mob, InventoryItem item) {
            if (mob == null || staff == null) return false;
            if (MobClassifier.isIgnoredFollower(mob)) return false;

            try {
                String mobStringID = mob.getStringID();
                if (mobStringID == null) return false;

                // 1. Direct string comparison
                String staffMobID = staff.mobStringID;
                if (staffMobID != null) {
                    if (mobStringID.equals(staffMobID) || mobStringID.equalsIgnoreCase(staffMobID)) {
                        return true;
                    }
                }

                String mobLower = mobStringID.toLowerCase();

                // 2. Multi-mob variant dictionary (e.g. brainonastick -> babyzombie, babyzombiearcher)
                if (!multiMobs.isEmpty() && multiMobs.contains(mobLower)) {
                    return true;
                }

                // 3. Prefix matching for variants (e.g. babyzombiearcher starts with babyzombie)
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
                AutoSummonLog.logThrottledError("vanilla_isMatchingMinion", "Error matching minion for " + stringID, t, 5000);
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
                AutoSummonLog.logThrottledError("vanilla_canAttack_" + stringID, "Error checking canAttack for " + stringID, t, 5000);
                return "Attack check failed";
            }
        }

        @Override
        public boolean isSecondarySummon() {
            return !isGlobalSlotWeapon(null, null);
        }

        @Override
        public boolean isSupportWeapon() {
            return false;
        }

        @Override
        public SummonCategory getCategory(InventoryItem item, PlayerMob player) {
            return isGlobalSlotWeapon(item, player) ? SummonCategory.GLOBAL_SLOT : SummonCategory.INDEPENDENT_FOLLOWER;
        }

        @Override
        public String getWeaponTypeDisplay(InventoryItem item, PlayerMob player, int maxAllowed) {
            try {
                if (isGlobalSlotWeapon(item, player)) {
                    float spaceCost = getSummonSpaceCost(item, player);
                    String costDisplay = (spaceCost == (int) spaceCost) ? String.valueOf((int) spaceCost) : String.format("%.1f", spaceCost);
                    return costDisplay + " slot(s)/summon (Max: " + maxAllowed + ")";
                } else {
                    return "Follower (Cap: " + maxAllowed + ")";
                }
            } catch (Throwable t) {
                return "Summon (Cap: " + maxAllowed + ")";
            }
        }
    }
}
