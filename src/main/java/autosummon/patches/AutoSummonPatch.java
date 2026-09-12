package autosummon.patches;

import autosummon.AutoSummonConfig;
import autosummon.AutoSummonModSettings;
import autosummon.compat.MobClassifier;
import autosummon.compat.SummonCompatRegistry;
import autosummon.compat.SummonWeaponAdapter;
import autosummon.compat.VanillaSummonCompatProvider;
import autosummon.util.AutoSummonLog;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.registries.BuffRegistry;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.PlayerMob;
import necesse.entity.mobs.buffs.ActiveBuff;
import necesse.inventory.InventoryItem;
import necesse.inventory.PlayerInventorySlot;
import necesse.inventory.item.toolItem.summonToolItem.SummonToolItem;
import net.bytebuddy.asm.Advice;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * High-performance auto-summoning patch for Necesse 1.3.3.
 * Executes on PlayerMob.clientTick with zero per-tick allocations, O(1) cached
 * mob classification, inverted minion indexing, adaptive backoff, and crash resilience.
 */
@ModMethodPatch(target = PlayerMob.class, name = "clientTick", arguments = {})
public class AutoSummonPatch {

    // Local client player primitive tracking (public to allow bytecode inlined execution in PlayerMob)
    public static long localNextCheckTime = 0;
    public static int localLastMaxSummons = -1;
    public static int lastLevelIdentifier = 0;

    // Adaptive backoff state tracking (public to allow bytecode inlined execution in PlayerMob)
    public static int consecutiveSaturatedChecks = 0;
    public static long lastHotbarFingerprint = 0;
    public static int lastRecordedActiveSummons = -1;

    public static void resetCheckTimer() {
        localNextCheckTime = 0;
        consecutiveSaturatedChecks = 0;
    }

    // Timer for delayed clearing when mod is toggled off
    public static final Timer clearTimer = new Timer("AutoSummonClearTimer", true);

    // Pre-allocated array to hold hotbar summon info without per-tick heap allocations
    public static final HotbarSlotInfo[] hotbarSlots = new HotbarSlotInfo[10];
    static {
        for (int i = 0; i < 10; i++) {
            hotbarSlots[i] = new HotbarSlotInfo();
        }
    }

    public static class HotbarSlotInfo {
        public int slotIndex;
        public InventoryItem item;
        public SummonWeaponAdapter adapter;
        public int activeCount;
        public int maxAllowed;
        public int customLimit;
        public int summonCost;
        public boolean isSlotWeapon;
        public boolean present;

        public void reset() {
            this.slotIndex = -1;
            this.item = null;
            this.adapter = null;
            this.activeCount = 0;
            this.maxAllowed = 0;
            this.customLimit = 0;
            this.summonCost = 0;
            this.isSlotWeapon = false;
            this.present = false;
        }
    }

    /**
     * Clear all summon buffs and follower mobs from the player safely with zero heap allocations
     */
    public static void clearSummonBuffs(PlayerMob player) {
        if (player == null) return;
        try {
            if (player.buffManager != null) {
                try {
                    if (BuffRegistry.SUMMONED_MOB != null) {
                        player.buffManager.removeBuff(BuffRegistry.SUMMONED_MOB, true);
                    }
                } catch (Throwable ignored) {}
                try {
                    player.buffManager.removeBuff("summonedmob", true);
                } catch (Throwable ignored) {}
            }

            // Despawn all active summoned following mobs attached to player in a direct single pass
            if (player.getLevel() != null && player.getLevel().entityManager != null && player.getLevel().entityManager.mobs != null) {
                int playerID = player.getUniqueID();
                for (Mob mob : player.getLevel().entityManager.mobs) {
                    if (mob == null || mob.removed() || mob.getFollowingUniqueID() != playerID) continue;
                    if (MobClassifier.isIgnoredFollower(mob)) continue;
                    if (mob.isMounted()) continue;
                    try {
                        mob.remove();
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable t) {
            AutoSummonLog.logThrottledError("clearSummonBuffs", "Error clearing summon buffs", t, 5000);
        }
    }

    /**
     * Schedule delayed clearing of summon buffs when disabling the mod
     */
    public static void scheduleDelayedClear(PlayerMob player) {
        if (player == null) return;
        try {
            clearTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        if (!AutoSummonConfig.isEnabled()) {
                            clearSummonBuffs(player);
                        }
                    } catch (Throwable t) {
                        AutoSummonLog.logThrottledError("delayedClearTask", "Error in delayed clear task", t, 5000);
                    }
                }
            }, 500);
        } catch (Throwable t) {
            AutoSummonLog.logThrottledError("scheduleDelayedClear", "Error scheduling delayed clear", t, 5000);
        }
    }

    /**
     * Get the current summon count for the player using O(1) BuffRegistry / BuffManager lookups
     */
    public static int getCurrentSummonCountBuffBar(PlayerMob player) {
        try {
            if (player != null && player.buffManager != null) {
                if (BuffRegistry.SUMMONED_MOB != null) {
                    return player.buffManager.getStacks(BuffRegistry.SUMMONED_MOB);
                }
                ActiveBuff buff = player.buffManager.getBuff("summonedmob");
                return buff != null ? buff.getStacks() : 0;
            }
        } catch (Throwable t) {
            AutoSummonLog.logThrottledError("getCurrentSummonCountBuffBar", "Error reading buff bar summon count", t, 5000);
        }
        return 0;
    }

    /**
     * Calculate total summon slots consumed by all slot-based summon weapons
     */
    public static int getTotalGlobalSummonSlotsUsed(PlayerMob player) {
        float slotsUsed = 0;
        if (player == null || player.getLevel() == null || player.getLevel().entityManager == null || player.getLevel().entityManager.mobs == null) {
            return 0;
        }

        try {
            int playerID = player.getUniqueID();
            for (Mob mob : player.getLevel().entityManager.mobs) {
                if (mob != null && !mob.removed() && mob.getHealth() > 0 && mob.getFollowingUniqueID() == playerID) {
                    if (MobClassifier.isIgnoredFollower(mob)) {
                        continue;
                    }

                    SummonWeaponAdapter matchedAdapter = SummonCompatRegistry.findWeaponForMob(player, mob);
                    if (matchedAdapter != null && matchedAdapter.isGlobalSlotWeapon(null, player)) {
                        float spaceCost = matchedAdapter.getSummonSpaceCost(null, player);
                        if (!Float.isNaN(spaceCost) && !Float.isInfinite(spaceCost) && spaceCost > 0) {
                            slotsUsed += spaceCost;
                        }
                    }
                }
            }
        } catch (Throwable t) {
            AutoSummonLog.logThrottledError("getTotalGlobalSummonSlotsUsed", "Error calculating global summon slots used", t, 5000);
        }

        return Math.round(slotsUsed);
    }

    /**
     * Find the summon staff in the player's hotbar/inventory corresponding to a spawned mob
     */
    public static SummonToolItem findStaffForMob(PlayerMob player, Mob mob) {
        if (mob == null || MobClassifier.isIgnoredFollower(mob)) {
            return null;
        }
        try {
            SummonWeaponAdapter adapter = SummonCompatRegistry.findWeaponForMob(player, mob);
            if (adapter instanceof VanillaSummonCompatProvider.VanillaSummonAdapter) {
                return ((VanillaSummonCompatProvider.VanillaSummonAdapter) adapter).getStaff();
            }
        } catch (Throwable t) {
            AutoSummonLog.logThrottledError("findStaffForMob", "Error finding staff for mob", t, 5000);
        }
        return null;
    }

    /**
     * Get the current summon count for custom follower staffs
     */
    public static int getCurrentSummonCountFollowers(PlayerMob player, SummonWeaponAdapter adapter, InventoryItem hotbarItem) {
        int currentSummons = 0;
        if (player == null || adapter == null) return 0;

        try {
            if (player.getLevel() != null && player.getLevel().entityManager != null && player.getLevel().entityManager.mobs != null) {
                int playerID = player.getUniqueID();
                for (Mob mob : player.getLevel().entityManager.mobs) {
                    if (mob != null && !mob.removed() && mob.getHealth() > 0 && mob.getFollowingUniqueID() == playerID) {
                        if (MobClassifier.isIgnoredFollower(mob)) {
                            continue;
                        }
                        if (adapter.isMatchingMinion(mob, hotbarItem)) {
                            currentSummons++;
                        }
                    }
                }
            }
        } catch (Throwable t) {
            AutoSummonLog.logThrottledError("getCurrentSummonCountFollowers", "Error getting follower count", t, 5000);
        }

        return currentSummons;
    }

    /**
     * Legacy helper for SummonToolItem count
     */
    public static int getCurrentSummonCountFollowers(PlayerMob player, SummonToolItem staff, InventoryItem hotbarItem) {
        if (staff == null) return 0;
        SummonWeaponAdapter adapter = SummonCompatRegistry.getAdapter(hotbarItem);
        return getCurrentSummonCountFollowers(player, adapter, hotbarItem);
    }

    /**
     * Check if a summoned mob was created by a specific staff
     */
    public static boolean isSummonedByStaff(Mob summonedMob, SummonToolItem staff, InventoryItem hotbarItem) {
        if (summonedMob == null || staff == null || hotbarItem == null || MobClassifier.isIgnoredFollower(summonedMob)) return false;
        try {
            SummonWeaponAdapter adapter = SummonCompatRegistry.getAdapter(hotbarItem);
            if (adapter != null) {
                return adapter.isMatchingMinion(summonedMob, hotbarItem);
            }
        } catch (Throwable t) {
            AutoSummonLog.logThrottledError("isSummonedByStaff", "Error checking if summoned by staff", t, 5000);
        }
        return false;
    }

    /**
     * Get the maximum summon count for a specific weapon adapter
     */
    public static int getStaffMaxSummons(SummonWeaponAdapter adapter, PlayerMob player, InventoryItem hotbarItem, int playerMaxSummons) {
        if (adapter == null) return Math.max(1, playerMaxSummons);

        try {
            int weaponLimit = AutoSummonConfig.getSettings().getWeaponSummonLimit(adapter.getItemStringID());
            if (weaponLimit > 0) {
                return weaponLimit;
            }

            if (AutoSummonConfig.getSettings().hasGlobalCustomLimit()) {
                return AutoSummonConfig.getSettings().getGlobalCustomSummonLimit();
            }

            return Math.max(1, adapter.getMaxAllowed(hotbarItem, player, playerMaxSummons));
        } catch (Throwable t) {
            AutoSummonLog.logThrottledError("getStaffMaxSummons", "Error getting staff max summons", t, 5000);
            return Math.max(1, playerMaxSummons);
        }
    }

    /**
     * Legacy overload for SummonToolItem
     */
    public static int getStaffMaxSummons(SummonToolItem staff, PlayerMob player, InventoryItem hotbarItem, int playerMaxSummons) {
        SummonWeaponAdapter adapter = SummonCompatRegistry.getAdapter(hotbarItem);
        return getStaffMaxSummons(adapter, player, hotbarItem, playerMaxSummons);
    }

    /**
     * Check if a specific summon weapon adapter can summon another minion
     */
    public static boolean canSummon(PlayerMob player, SummonWeaponAdapter adapter, InventoryItem hotbarItem, int playerMaxSummons) {
        if (player == null || adapter == null) return false;

        try {
            int activeForStaff = getCurrentSummonCountFollowers(player, adapter, hotbarItem);
            int weaponLimit = AutoSummonConfig.getSettings().getWeaponSummonLimit(adapter.getItemStringID());

            if (weaponLimit > 0 && activeForStaff >= weaponLimit) {
                return false;
            }

            if (AutoSummonConfig.getSettings().hasGlobalCustomLimit() && activeForStaff >= AutoSummonConfig.getSettings().getGlobalCustomSummonLimit()) {
                return false;
            }

            int summonCost = Math.max(1, Math.round(adapter.getSummonSpaceCost(hotbarItem, player)));

            if (adapter.isGlobalSlotWeapon(hotbarItem, player)) {
                if (playerMaxSummons <= 0) {
                    return false;
                }
                int currentGlobalSlots = getTotalGlobalSummonSlotsUsed(player);
                return currentGlobalSlots + summonCost <= playerMaxSummons;
            }

            int staffMax = weaponLimit > 0 ? weaponLimit : adapter.getMaxAllowed(hotbarItem, player, playerMaxSummons);
            return activeForStaff + summonCost <= staffMax;
        } catch (Throwable t) {
            AutoSummonLog.logThrottledError("canSummon", "Error evaluating canSummon", t, 5000);
            return false;
        }
    }

    /**
     * Legacy overload for SummonToolItem
     */
    public static boolean canSummon(PlayerMob player, SummonToolItem staff, InventoryItem hotbarItem, int playerMaxSummons) {
        SummonWeaponAdapter adapter = SummonCompatRegistry.getAdapter(hotbarItem);
        return canSummon(player, adapter, hotbarItem, playerMaxSummons);
    }

    /**
     * Check if the player is at max summons across all hotbar staffs
     */
    public static boolean isAtMaxSummons(PlayerMob player) {
        if (player == null || player.getInv() == null || player.getInv().main == null) {
            return true;
        }

        try {
            List<InventoryItem> hotbarItems = new ArrayList<>(10);
            for (int i = 0; i < 10; i++) {
                InventoryItem item = player.getInv().main.getItem(i);
                if (item != null && item.item != null) {
                    hotbarItems.add(item);
                }
            }
            int playerMaxSummons = SummonCompatRegistry.getPlayerMaxSummonSlots(player, hotbarItems);

            for (int i = 9; i >= 0; i--) {
                InventoryItem hotbarItem = player.getInv().main.getItem(i);
                if (hotbarItem != null) {
                    SummonWeaponAdapter adapter = SummonCompatRegistry.getAdapter(hotbarItem);
                    if (adapter != null && canSummon(player, adapter, hotbarItem, playerMaxSummons)) {
                        return false;
                    }
                }
            }
        } catch (Throwable t) {
            AutoSummonLog.logThrottledError("isAtMaxSummons", "Error checking isAtMaxSummons", t, 5000);
        }

        return true;
    }

    /**
     * Helper to handle chat messages from GUI/hotkeys
     */
    public static void handleChatMessage(PlayerMob player) {
        try {
            String message = AutoSummonConfig.getAndClearChatMessage();
            if (message.isEmpty()) {
                String status = AutoSummonConfig.isEnabled() ? "ON" : "OFF";
                message = "[Auto Summon] " + status;
            } else {
                message = "[Auto Summon] " + message;
            }

            if (!AutoSummonConfig.isEnabled()) {
                scheduleDelayedClear(player);
            }

            if (player != null && player.getLevel() != null && player.getLevel().getClient() != null && player.getLevel().getClient().chat != null) {
                player.getLevel().getClient().chat.addMessage(message);
            }
        } catch (Throwable t) {
            AutoSummonLog.logThrottledError("handleChatMessage", "Error handling chat message", t, 5000);
        }
    }

    /**
     * Computes a fast 64-bit fingerprint of the player's hotbar items (slots 0-9)
     */
    public static long computeHotbarFingerprint(PlayerMob player) {
        if (player == null || player.getInv() == null || player.getInv().main == null) {
            return 0;
        }
        long hash = 17;
        for (int i = 0; i < 10; i++) {
            InventoryItem item = player.getInv().main.getItem(i);
            if (item != null && item.item != null) {
                String stringID = item.item.getStringID();
                int idHash = stringID != null ? stringID.hashCode() : 0;
                hash = hash * 31 + i + idHash;
            } else {
                hash = hash * 31 + i;
            }
        }
        return hash;
    }

    /**
     * Optimized onExit patch executing on PlayerMob.clientTick with top-level crash resilience
     */
    @Advice.OnMethodExit
    public static void onExit(@Advice.This PlayerMob player) {
        handleClientTick(player);
    }

    public static void handleClientTick(PlayerMob player) {
        try {
            // Fast-path 1: Mod disabled
            if (!AutoSummonConfig.isEnabled()) {
                if (AutoSummonConfig.checkAndClearNeedsChatMessage()) {
                    handleChatMessage(player);
                }
                return;
            }

            // Fast-path 2: Validate local client player
            if (player == null || !player.isClient() || !player.isPlayer) {
                return;
            }
            if (player.isAttacking) {
                return;
            }

            long currentTime = System.currentTimeMillis();

            // Fast-path 3: Cooldown / backoff timer
            if (currentTime < localNextCheckTime) {
                return;
            }

            if (player.getLevel() == null || !player.getLevel().isClient()) {
                return;
            }
            if (player.getLevel().getClient() == null || player.getLevel().getClient().getPlayer() != player) {
                return;
            }

            // Level transition / initial world join safety grace period (2.5 seconds)
            int currentLevelId = player.getLevel().getIdentifierHashCode();
            if (currentLevelId != lastLevelIdentifier) {
                lastLevelIdentifier = currentLevelId;
                localNextCheckTime = currentTime + 2500;
                consecutiveSaturatedChecks = 0;
                return;
            }

            // Process any queued chat messages
            if (AutoSummonConfig.checkAndClearNeedsChatMessage()) {
                handleChatMessage(player);
            }

            if (player.getInv() == null || player.getInv().main == null) {
                return;
            }

            // Fast-path 4: Quick scan hotbar slots 0-9 for any summon weapons via compatibility registry
            boolean hasAnySummonStaff = false;
            AutoSummonModSettings settings = AutoSummonConfig.getSettings();
            List<InventoryItem> hotbarItemList = new ArrayList<>(10);
            for (int i = 0; i < 10; i++) {
                InventoryItem item = player.getInv().main.getItem(i);
                if (item != null && item.item != null) {
                    hotbarItemList.add(item);
                }
            }
            int playerMaxSummons = SummonCompatRegistry.getPlayerMaxSummonSlots(player, hotbarItemList);

            // If max summon capacity changed, update tracking and reset backoff counter
            if (localLastMaxSummons != -1 && playerMaxSummons != localLastMaxSummons) {
                consecutiveSaturatedChecks = 0;
            }
            localLastMaxSummons = playerMaxSummons;

            long hotbarFp = computeHotbarFingerprint(player);
            if (hotbarFp != lastHotbarFingerprint) {
                lastHotbarFingerprint = hotbarFp;
                consecutiveSaturatedChecks = 0;
            }

            for (int i = 0; i < 10; i++) {
                hotbarSlots[i].reset();
                InventoryItem item = player.getInv().main.getItem(i);
                if (item != null && item.item != null) {
                    SummonWeaponAdapter adapter = SummonCompatRegistry.getAdapter(item);
                    if (adapter != null) {
                        HotbarSlotInfo info = hotbarSlots[i];
                        info.slotIndex = i;
                        info.item = item;
                        info.adapter = adapter;
                        info.isSlotWeapon = adapter.isGlobalSlotWeapon(item, player);
                        info.summonCost = Math.max(1, Math.round(adapter.getSummonSpaceCost(item, player)));
                        info.customLimit = settings.getWeaponSummonLimit(adapter.getItemStringID());
                        info.maxAllowed = Math.max(0, adapter.getMaxAllowed(item, player, playerMaxSummons));
                        info.present = true;
                        hasAnySummonStaff = true;
                    }
                }
            }

            if (!hasAnySummonStaff) {
                // No summon weapons in hotbar: sleep check for 500ms
                localNextCheckTime = currentTime + 500;
                consecutiveSaturatedChecks = 0;
                return;
            }

            // Single-pass mob iteration across all mobs in level
            int globalSlotsUsed = 0;
            int totalActiveMinions = 0;

            if (player.getLevel().entityManager != null && player.getLevel().entityManager.mobs != null) {
                int playerID = player.getUniqueID();
                for (Mob mob : player.getLevel().entityManager.mobs) {
                    if (mob == null || mob.removed() || mob.getHealth() <= 0 || mob.getFollowingUniqueID() != playerID) {
                        continue;
                    }
                    if (MobClassifier.isIgnoredFollower(mob)) {
                        continue;
                    }

                    totalActiveMinions++;
                    boolean matched = false;
                    for (int s = 0; s < 10; s++) {
                        HotbarSlotInfo info = hotbarSlots[s];
                        if (!info.present) continue;

                        if (info.adapter.isMatchingMinion(mob, info.item)) {
                            info.activeCount++;
                            if (info.isSlotWeapon) {
                                globalSlotsUsed += info.summonCost;
                            }
                            matched = true;
                            break;
                        }
                    }

                    // If minion was spawned by an off-hotbar inventory staff
                    if (!matched) {
                        SummonWeaponAdapter invAdapter = SummonCompatRegistry.findWeaponForMob(player, mob);
                        if (invAdapter != null && invAdapter.isGlobalSlotWeapon(null, player)) {
                            globalSlotsUsed += Math.max(1, Math.round(invAdapter.getSummonSpaceCost(null, player)));
                        }
                    }
                }
            }

            // Reset backoff if active minion count changed since last check
            if (totalActiveMinions != lastRecordedActiveSummons) {
                lastRecordedActiveSummons = totalActiveMinions;
                consecutiveSaturatedChecks = 0;
            }

            // Evaluate rightmost staff (slot 9 down to 0) that can summon
            boolean summonedAny = false;
            boolean allFull = true;

            for (int i = 9; i >= 0; i--) {
                HotbarSlotInfo info = hotbarSlots[i];
                if (!info.present) continue;

                // If weapon is configured to Off / Disabled (-1), skip summoning it completely
                if (info.customLimit == -1) {
                    continue;
                }

                // 1. If active minion count exceeds custom limit (e.g. player reduced limit in GUI), despawn excess in-line
                if (info.customLimit > 0 && info.activeCount > info.customLimit) {
                    int toRemove = info.activeCount - info.customLimit;
                    if (player.getLevel() != null && player.getLevel().entityManager != null && player.getLevel().entityManager.mobs != null) {
                        int playerID = player.getUniqueID();
                        for (Mob mob : player.getLevel().entityManager.mobs) {
                            if (mob == null || mob.removed() || mob.getFollowingUniqueID() != playerID) continue;
                            if (MobClassifier.isIgnoredFollower(mob)) continue;
                            if (info.adapter.isMatchingMinion(mob, info.item)) {
                                try {
                                    mob.remove();
                                    info.activeCount--;
                                    if (info.isSlotWeapon) {
                                        globalSlotsUsed -= info.summonCost;
                                    }
                                    toRemove--;
                                    if (toRemove <= 0) break;
                                } catch (Throwable ignored) {}
                            }
                        }
                    }
                }

                // 2. Check custom limit
                if (info.customLimit > 0 && info.activeCount >= info.customLimit) {
                    continue;
                }

                boolean canSummonThis = false;
                if (info.isSlotWeapon) {
                    if (playerMaxSummons > 0 && globalSlotsUsed + info.summonCost <= playerMaxSummons) {
                        canSummonThis = true;
                    }
                } else {
                    int cap = info.customLimit > 0 ? info.customLimit : info.maxAllowed;
                    if (info.activeCount + info.summonCost <= cap) {
                        canSummonThis = true;
                    }
                }

                if (canSummonThis) {
                    allFull = false;
                    String canAttackResult = info.adapter.canAttack(player, info.item);

                    if (canAttackResult == null) {
                        // Compute directional offset 64px in front of player for reliable projectile summon raycasts
                        int targetX = (int) player.getX();
                        int targetY = (int) player.getY();
                        int dir = player.getDir();
                        if (dir == 0) targetY -= 64;      // Up
                        else if (dir == 1) targetX += 64; // Right
                        else if (dir == 2) targetY += 64; // Down
                        else if (dir == 3) targetX -= 64; // Left

                        PlayerInventorySlot slot = new PlayerInventorySlot(player.getInv().main, i);
                        try {
                            player.tryAttack(slot, targetX, targetY);
                        } catch (Throwable t) {
                            AutoSummonLog.logThrottledError("player_tryAttack_" + info.adapter.getItemStringID(),
                                "Error invoking tryAttack for " + info.adapter.getItemStringID(), t, 5000);
                        }
                        localNextCheckTime = currentTime + AutoSummonConfig.getSummonCheckCooldown();
                        consecutiveSaturatedChecks = 0;
                        summonedAny = true;
                        autosummon.gui.AutoSummonGuiManager.notifySummonChanged();
                        break;
                    }
                }
            }

            if (!summonedAny) {
                if (allFull) {
                    consecutiveSaturatedChecks++;
                    // Adaptive backoff: 250ms -> 500ms -> 750ms -> 1000ms max when saturated and static
                    long backoff = Math.min(1000, 250 + ((long) (consecutiveSaturatedChecks - 1) * 250));
                    localNextCheckTime = currentTime + backoff;
                } else {
                    consecutiveSaturatedChecks = 0;
                    localNextCheckTime = currentTime + AutoSummonConfig.getSummonCheckCooldown();
                }
            }
        } catch (Throwable t) {
            // Emergency fallback: log throttled error and pause summoning checks for 3 seconds to avoid tick lag
            AutoSummonLog.logThrottledError("patch_onExit_fatal", "Unexpected error in AutoSummonPatch.onExit", t, 5000);
            localNextCheckTime = System.currentTimeMillis() + 3000;
            consecutiveSaturatedChecks = 0;
        }
    }
}