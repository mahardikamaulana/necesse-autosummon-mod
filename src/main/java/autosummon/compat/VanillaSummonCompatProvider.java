package autosummon.compat;

import necesse.entity.mobs.Mob;
import necesse.entity.mobs.PlayerMob;
import necesse.entity.mobs.WormMobBody;
import necesse.entity.mobs.summon.FollowingWormMobHead;
import necesse.entity.mobs.summon.summonFollowingMob.SummonedFollowingMob;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.toolItem.summonToolItem.SummonToolItem;

import java.util.*;

/**
 * Standard compatibility provider for vanilla Necesse summon weapons.
 * Includes multi-variant mob matching for weapons like Brain on a Stick.
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
        protected final Set<String> multiMobs;

        public VanillaSummonAdapter(SummonToolItem staff) {
            this.staff = staff;
            this.stringID = staff != null ? staff.getStringID().toLowerCase() : "";
            this.multiMobs = VANILLA_MULTI_MOBS.getOrDefault(this.stringID, Collections.emptySet());
        }

        public SummonToolItem getStaff() {
            return staff;
        }

        @Override
        public String getItemStringID() {
            return staff.getStringID();
        }

        @Override
        public String getDisplayName(InventoryItem item) {
            return item != null ? item.getItemDisplayName() : staff.getStringID();
        }

        @Override
        public boolean isGlobalSlotWeapon(InventoryItem item, PlayerMob player) {
            if (staff == null) return false;
            // Only weapons that draw max summons AND use standard "summonedmob" group consume global slots
            boolean isStandardSummonMob = staff.summonType == null || staff.summonType.equalsIgnoreCase("summonedmob");
            return staff.drawMaxSummons && isStandardSummonMob;
        }

        @Override
        public float getSummonSpaceCost(InventoryItem item, PlayerMob player) {
            try {
                return staff.getSummonSpaceTaken(item, player);
            } catch (Exception e) {
                return 1.0f;
            }
        }

        @Override
        public int getMaxAllowed(InventoryItem item, PlayerMob player, int playerMaxSummons) {
            if (isGlobalSlotWeapon(item, player)) {
                float cost = getSummonSpaceCost(item, player);
                if (cost <= 0) cost = 1.0f;
                return Math.max(1, (int) Math.floor(playerMaxSummons / cost));
            } else {
                try {
                    return Math.max(1, staff.getMaxSummons(item, player));
                } catch (Throwable t) {
                    return 1;
                }
            }
        }

        @Override
        public boolean isMatchingMinion(Mob mob, InventoryItem item) {
            if (mob == null || staff == null) return false;
            if (mob instanceof WormMobBody) return false;

            try {
                String mobStringID = mob.getStringID();
                if (mobStringID == null) return false;
                String mobLower = mobStringID.toLowerCase();

                // 1. Check multi-mob variant dictionary (e.g. brainonastick -> babyzombie, babyzombiearcher)
                if (!multiMobs.isEmpty() && multiMobs.contains(mobLower)) {
                    return true;
                }

                // 2. Direct string ID match
                String staffMobStringID = staff.mobStringID;
                if (staffMobStringID != null) {
                    String staffLower = staffMobStringID.toLowerCase();
                    if (mobLower.equals(staffLower)) {
                        return true;
                    }
                    // Prefix matching for variants (e.g. babyzombiearcher starts with babyzombie)
                    if (mobLower.startsWith(staffLower) && !staffLower.isEmpty()) {
                        return true;
                    }
                }

                // 3. Summon type match
                if (staff.summonType != null && staff.summonType.equalsIgnoreCase(mobStringID)) {
                    return true;
                }

                // 4. Inventory item back-reference checks
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
            } catch (Exception ignored) {}

            return false;
        }

        @Override
        public String canAttack(PlayerMob player, InventoryItem item) {
            if (player == null || player.getLevel() == null || item == null) {
                return "Invalid state";
            }
            return staff.canAttack(player.getLevel(), (int) player.getX(), (int) player.getY(), player, item);
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
            if (isGlobalSlotWeapon(item, player)) {
                float spaceCost = getSummonSpaceCost(item, player);
                String costDisplay = (spaceCost == (int) spaceCost) ? String.valueOf((int) spaceCost) : String.format("%.1f", spaceCost);
                return costDisplay + " slot(s)/summon (Max: " + maxAllowed + ")";
            } else {
                return "Follower (Cap: " + maxAllowed + ")";
            }
        }
    }
}
