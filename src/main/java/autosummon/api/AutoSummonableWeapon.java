package autosummon.api;

import autosummon.compat.SummonWeaponAdapter.SummonCategory;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.InventoryItem;

import java.util.Collection;
import java.util.Collections;

/**
 * Optional interface that third-party mod item classes can implement directly
 * to provide full Auto Summon compatibility without creating separate adapter classes.
 */
public interface AutoSummonableWeapon {

    /**
     * Category classification for this weapon (Global Slot, Follower, or Sentry).
     * Defaults to GLOBAL_SLOT.
     */
    default SummonCategory getSummonCategory(InventoryItem item, PlayerMob player) {
        return SummonCategory.GLOBAL_SLOT;
    }

    /**
     * Returns a collection of mob string IDs that this weapon can spawn.
     * Auto Summon will automatically map and track these minion entities.
     */
    default Collection<String> getSummonMinionIDs(InventoryItem item) {
        return Collections.emptyList();
    }

    /**
     * Checks if a live mob in the world was created by this weapon.
     * Defaults to matching the mob's string ID against getSummonMinionIDs(item).
     */
    default boolean isMatchingSummonMinion(Mob mob, InventoryItem item) {
        if (mob == null) return false;
        String mobID = mob.getStringID();
        if (mobID == null) return false;
        for (String id : getSummonMinionIDs(item)) {
            if (id != null && (mobID.equalsIgnoreCase(id) || mobID.equalsIgnoreCase(id.trim()))) {
                return true;
            }
        }
        return false;
    }

    /**
     * How many summon slots this minion takes (usually 1.0f).
     */
    default float getSummonSpaceCost(InventoryItem item, PlayerMob player) {
        return 1.0f;
    }

    /**
     * Maximum allowed active summons for this weapon.
     * For global slot weapons, return playerMaxSummons.
     * For independent followers or sentries, return the weapon's custom cap.
     */
    default int getSummonMaxAllowed(InventoryItem item, PlayerMob player, int playerMaxSummons) {
        return playerMaxSummons;
    }

    /**
     * Checks if the weapon is ready to summon (cooldown, line of sight, etc.).
     * Return null if ready to attack, or a reason string to skip.
     */
    default String canAutoSummon(PlayerMob player, InventoryItem item) {
        return null;
    }

    /**
     * Returns true if Auto Summon should ignore this item.
     */
    default boolean isAutoSummonDisabled(InventoryItem item) {
        return false;
    }
}
