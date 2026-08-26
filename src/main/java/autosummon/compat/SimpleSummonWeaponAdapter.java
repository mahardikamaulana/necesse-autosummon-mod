package autosummon.compat;

import autosummon.util.AutoSummonLog;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.toolItem.ToolItem;
import necesse.inventory.item.toolItem.summonToolItem.SummonToolItem;

import java.util.*;

/**
 * Convenient abstract base adapter providing sensible default implementations
 * for all {@link SummonWeaponAdapter} methods. Mod creators can extend this class
 * and only override the specific methods they need.
 */
public abstract class SimpleSummonWeaponAdapter implements SummonWeaponAdapter {

    protected final String itemStringID;
    protected final SummonCategory defaultCategory;
    protected final Set<String> minionIDs = new HashSet<>();
    protected float spaceCost = 1.0f;
    protected int defaultMaxAllowed = -1;

    public SimpleSummonWeaponAdapter(String itemStringID, SummonCategory category, String... minionIDs) {
        this.itemStringID = itemStringID != null ? itemStringID.toLowerCase().trim() : "";
        this.defaultCategory = category != null ? category : SummonCategory.GLOBAL_SLOT;
        if (minionIDs != null) {
            for (String id : minionIDs) {
                if (id != null && !id.trim().isEmpty()) {
                    this.minionIDs.add(id.trim().toLowerCase());
                }
            }
        }
    }

    public SimpleSummonWeaponAdapter(String itemStringID, String... minionIDs) {
        this(itemStringID, SummonCategory.GLOBAL_SLOT, minionIDs);
    }

    @Override
    public String getItemStringID() {
        return itemStringID;
    }

    @Override
    public String getDisplayName(InventoryItem item) {
        try {
            if (item != null) {
                return item.getItemDisplayName();
            }
        } catch (Throwable ignored) {}
        return itemStringID;
    }

    @Override
    public boolean isGlobalSlotWeapon(InventoryItem item, PlayerMob player) {
        return getCategory(item, player) == SummonCategory.GLOBAL_SLOT;
    }

    @Override
    public float getSummonSpaceCost(InventoryItem item, PlayerMob player) {
        if (item != null && item.item instanceof SummonToolItem) {
            try {
                float cost = ((SummonToolItem) item.item).getSummonSpaceTaken(item, player);
                if (!Float.isNaN(cost) && !Float.isInfinite(cost) && cost > 0) {
                    return cost;
                }
            } catch (Throwable ignored) {}
        }
        return spaceCost > 0 ? spaceCost : 1.0f;
    }

    @Override
    public int getMaxAllowed(InventoryItem item, PlayerMob player, int playerMaxSummons) {
        if (isGlobalSlotWeapon(item, player)) {
            if (playerMaxSummons <= 0) return 0;
            float cost = getSummonSpaceCost(item, player);
            if (cost <= 0) cost = 1.0f;
            return Math.max(1, (int) Math.floor(playerMaxSummons / cost));
        } else {
            if (defaultMaxAllowed > 0) {
                return defaultMaxAllowed;
            }
            if (item != null && item.item instanceof SummonToolItem) {
                try {
                    return Math.max(1, ((SummonToolItem) item.item).getMaxSummons(item, player));
                } catch (Throwable ignored) {}
            }
            return 1;
        }
    }

    @Override
    public boolean isMatchingMinion(Mob mob, InventoryItem item) {
        if (mob == null || mob.removed() || mob.getHealth() <= 0) return false;
        if (SummonCompatRegistry.isIgnoredFollower(mob)) return false;

        String mobID = mob.getStringID();
        if (mobID == null) return false;
        String lower = mobID.toLowerCase();

        if (minionIDs.contains(lower) || minionIDs.contains(mobID)) {
            return true;
        }

        for (String id : minionIDs) {
            if (!id.isEmpty() && lower.startsWith(id)) {
                return true;
            }
        }

        if (item != null && item.item instanceof SummonToolItem) {
            SummonToolItem staff = (SummonToolItem) item.item;
            if (staff.mobStringID != null && staff.mobStringID.equalsIgnoreCase(mobID)) {
                return true;
            }
            if (staff.summonType != null && staff.summonType.equalsIgnoreCase(mobID)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public String canAttack(PlayerMob player, InventoryItem item) {
        if (player == null || player.getLevel() == null || item == null) {
            return "Invalid state";
        }
        try {
            if (item.item instanceof ToolItem) {
                return ((ToolItem) item.item).canAttack(player.getLevel(), (int) player.getX(), (int) player.getY(), player, item);
            }
        } catch (Throwable t) {
            AutoSummonLog.logThrottledError("simple_adapter_canAttack_" + itemStringID, "Error in canAttack for " + itemStringID, t, 5000);
            return "Attack check failed";
        }
        return null;
    }

    @Override
    public boolean isSecondarySummon() {
        return defaultCategory == SummonCategory.INDEPENDENT_FOLLOWER;
    }

    @Override
    public boolean isSupportWeapon() {
        return defaultCategory == SummonCategory.SENTRY_SUPPORT;
    }

    @Override
    public SummonCategory getCategory(InventoryItem item, PlayerMob player) {
        return defaultCategory;
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
