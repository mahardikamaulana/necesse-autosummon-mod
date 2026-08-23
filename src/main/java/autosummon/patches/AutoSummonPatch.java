package autosummon.patches;

import autosummon.AutoSummonConfig;
import autosummon.AutoSummonModSettings;
import autosummon.compat.SummonCompatRegistry;
import autosummon.compat.SummonWeaponAdapter;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.registries.BuffRegistry;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.PlayerMob;
import necesse.entity.mobs.buffs.ActiveBuff;
import necesse.entity.mobs.buffs.BuffModifiers;
import necesse.inventory.InventoryItem;
import necesse.inventory.PlayerInventorySlot;
import necesse.inventory.item.toolItem.summonToolItem.SummonToolItem;
import net.bytebuddy.asm.Advice;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * High-performance auto-summoning patch for Necesse 1.3.2.
 * Executes on PlayerMob.clientTick with zero per-tick allocations, single-pass
 * entity manager scanning, smart tick backoff, and modular mod compatibility.
 */
@ModMethodPatch(target = PlayerMob.class, name = "clientTick", arguments = {})
public class AutoSummonPatch {

    // Local client player primitive tracking (public to allow bytecode inlined execution in PlayerMob)
    public static long localNextCheckTime = 0;
    public static int localLastMaxSummons = -1;

    public static void resetCheckTimer() {
        localNextCheckTime = 0;
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
     * Clear all summon buffs and follower mobs from the player
     */
    public static void clearSummonBuffs(PlayerMob player) {
        if (player == null) return;
        try {
            if (player.buffManager != null) {
                if (BuffRegistry.SUMMONED_MOB != null) {
                    player.buffManager.removeBuff(BuffRegistry.SUMMONED_MOB, true);
                }
                player.buffManager.removeBuff("summonedmob", true);
            }

            // Despawn all active summoned following mobs attached to player (excluding mounts/pets)
            if (player.getLevel() != null && player.getLevel().entityManager != null && player.getLevel().entityManager.mobs != null) {
                int playerID = player.getUniqueID();
                List<Mob> toRemoveList = new ArrayList<>();
                for (Mob mob : player.getLevel().entityManager.mobs) {
                    if (mob == null || mob.removed() || mob.getFollowingUniqueID() != playerID) continue;
                    String stringID = mob.getStringID();
                    if (stringID != null) {
                        String lower = stringID.toLowerCase();
                        if (lower.contains("pet") || lower.contains("mount")) continue;
                    }
                    if (mob.isMounted()) continue;
                    toRemoveList.add(mob);
                }
                for (Mob mob : toRemoveList) {
                    mob.remove();
                }
            }
        } catch (Exception ignored) {}
    }

    /**
     * Schedule delayed clearing of summon buffs when disabling the mod
     */
    public static void scheduleDelayedClear(PlayerMob player) {
        clearTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (!AutoSummonConfig.isEnabled()) {
                    clearSummonBuffs(player);
                }
            }
        }, 500);
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
        } catch (Exception ignored) {}
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
                    if (SummonCompatRegistry.isWormBody(mob)) {
                        continue;
                    }

                    SummonWeaponAdapter matchedAdapter = SummonCompatRegistry.findWeaponForMob(player, mob);
                    if (matchedAdapter != null && matchedAdapter.isGlobalSlotWeapon(null, player)) {
                        slotsUsed += matchedAdapter.getSummonSpaceCost(null, player);
                    }
                }
            }
        } catch (Exception ignored) {}

        return Math.round(slotsUsed);
    }

    /**
     * Find the summon staff in the player's hotbar/inventory corresponding to a spawned mob
     */
    public static SummonToolItem findStaffForMob(PlayerMob player, Mob mob) {
        if (player == null || player.getInv() == null || player.getInv().main == null) {
            return null;
        }
        for (int i = 0; i < player.getInv().main.getSize(); i++) {
            InventoryItem item = player.getInv().main.getItem(i);
            if (item != null && item.item instanceof SummonToolItem) {
                SummonWeaponAdapter adapter = SummonCompatRegistry.getAdapter(item);
                if (adapter != null && adapter.isMatchingMinion(mob, item)) {
                    return (SummonToolItem) item.item;
                }
            }
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
                        if (SummonCompatRegistry.isWormBody(mob)) {
                            continue;
                        }
                        if (adapter.isMatchingMinion(mob, hotbarItem)) {
                            currentSummons++;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

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
        if (summonedMob == null || staff == null) return false;
        SummonWeaponAdapter adapter = SummonCompatRegistry.getAdapter(hotbarItem);
        if (adapter != null) {
            return adapter.isMatchingMinion(summonedMob, hotbarItem);
        }
        return false;
    }

    /**
     * Get the maximum summon count for a specific weapon adapter
     */
    public static int getStaffMaxSummons(SummonWeaponAdapter adapter, PlayerMob player, InventoryItem hotbarItem, int playerMaxSummons) {
        if (adapter == null) return playerMaxSummons;

        int weaponLimit = AutoSummonConfig.getSettings().getWeaponSummonLimit(adapter.getItemStringID());
        if (weaponLimit > 0) {
            return weaponLimit;
        }

        if (AutoSummonConfig.getSettings().hasGlobalCustomLimit()) {
            return AutoSummonConfig.getSettings().getGlobalCustomSummonLimit();
        }

        return adapter.getMaxAllowed(hotbarItem, player, playerMaxSummons);
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
            int currentGlobalSlots = getTotalGlobalSummonSlotsUsed(player);
            return currentGlobalSlots + summonCost <= playerMaxSummons;
        }

        int staffMax = adapter.getMaxAllowed(hotbarItem, player, playerMaxSummons);
        return activeForStaff + summonCost <= staffMax;
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
        int playerMaxSummons = player.buffManager != null ? player.buffManager.getModifier(BuffModifiers.MAX_SUMMONS) : 1;

        for (int i = 9; i >= 0; i--) {
            InventoryItem hotbarItem = player.getInv().main.getItem(i);
            if (hotbarItem != null) {
                SummonWeaponAdapter adapter = SummonCompatRegistry.getAdapter(hotbarItem);
                if (adapter != null && canSummon(player, adapter, hotbarItem, playerMaxSummons)) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Helper to handle chat messages from GUI/hotkeys
     */
    public static void handleChatMessage(PlayerMob player) {
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

        if (player.getLevel() != null && player.getLevel().getClient() != null && player.getLevel().getClient().chat != null) {
            player.getLevel().getClient().chat.addMessage(message);
        }
    }

    /**
     * Optimized onExit patch executing on PlayerMob.clientTick
     */
    @Advice.OnMethodExit
    public static void onExit(@Advice.This PlayerMob player) {
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
        if (player.getLevel() == null || !player.getLevel().isClient()) {
            return;
        }
        if (player.getLevel().getClient() == null || player.getLevel().getClient().getPlayer() != player) {
            return;
        }

        // Process any queued chat messages
        if (AutoSummonConfig.checkAndClearNeedsChatMessage()) {
            handleChatMessage(player);
        }

        // Fast-path 3: Player currently in attack animation
        if (player.isAttacking) {
            return;
        }

        // Fast-path 4: Debounce / cooldown timer
        long currentTime = System.currentTimeMillis();
        if (currentTime < localNextCheckTime) {
            return;
        }

        if (player.getInv() == null || player.getInv().main == null) {
            return;
        }

        // Fast-path 5: Quick scan hotbar slots 0-9 for any summon weapons via compatibility registry
        boolean hasAnySummonStaff = false;
        AutoSummonModSettings settings = AutoSummonConfig.getSettings();
        int playerMaxSummons = player.buffManager != null ? player.buffManager.getModifier(BuffModifiers.MAX_SUMMONS) : 1;

        // Fallback: If max summon capacity decreased, reset all slot-consuming weapons to Auto
        if (localLastMaxSummons != -1 && playerMaxSummons < localLastMaxSummons) {
            settings.resetSlotWeaponLimits(player);
            if (player.getLevel().getClient() != null && player.getLevel().getClient().chat != null) {
                player.getLevel().getClient().chat.addMessage("[Auto Summon] Summon capacity decreased: reset slot weapon limits to Auto.");
            }
        }
        localLastMaxSummons = playerMaxSummons;

        for (int i = 0; i < 10; i++) {
            hotbarSlots[i].reset();
            InventoryItem item = player.getInv().main.getItem(i);
            if (item != null) {
                SummonWeaponAdapter adapter = SummonCompatRegistry.getAdapter(item);
                if (adapter != null) {
                    HotbarSlotInfo info = hotbarSlots[i];
                    info.slotIndex = i;
                    info.item = item;
                    info.adapter = adapter;
                    info.isSlotWeapon = adapter.isGlobalSlotWeapon(item, player);
                    info.summonCost = Math.max(1, Math.round(adapter.getSummonSpaceCost(item, player)));
                    info.customLimit = settings.getWeaponSummonLimit(adapter.getItemStringID());
                    info.maxAllowed = adapter.getMaxAllowed(item, player, playerMaxSummons);
                    info.present = true;
                    hasAnySummonStaff = true;
                }
            }
        }

        if (!hasAnySummonStaff) {
            // No summon weapons in hotbar: sleep check for 500ms
            localNextCheckTime = currentTime + 500;
            return;
        }

        // Single-pass mob iteration across all mobs in level
        int globalSlotsUsed = 0;
        if (player.getLevel().entityManager != null && player.getLevel().entityManager.mobs != null) {
            int playerID = player.getUniqueID();
            for (Mob mob : player.getLevel().entityManager.mobs) {
                if (mob == null || mob.removed() || mob.getHealth() <= 0 || mob.getFollowingUniqueID() != playerID) {
                    continue;
                }
                if (SummonCompatRegistry.isWormBody(mob)) {
                    continue;
                }

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

        // Evaluate rightmost staff (slot 9 down to 0) that can summon
        boolean summonedAny = false;
        boolean allFull = true;

        for (int i = 9; i >= 0; i--) {
            HotbarSlotInfo info = hotbarSlots[i];
            if (!info.present) continue;

            // 1. If active minion count exceeds custom limit (e.g. player reduced limit in GUI), despawn excess
            if (info.customLimit > 0 && info.activeCount > info.customLimit) {
                int toRemove = info.activeCount - info.customLimit;
                if (player.getLevel() != null && player.getLevel().entityManager != null && player.getLevel().entityManager.mobs != null) {
                    int playerID = player.getUniqueID();
                    List<Mob> excessList = new ArrayList<>();
                    for (Mob mob : player.getLevel().entityManager.mobs) {
                        if (mob == null || mob.removed() || mob.getFollowingUniqueID() != playerID) continue;
                        if (SummonCompatRegistry.isWormBody(mob)) continue;
                        if (info.adapter.isMatchingMinion(mob, info.item)) {
                            excessList.add(mob);
                            info.activeCount--;
                            if (info.isSlotWeapon) {
                                globalSlotsUsed -= info.summonCost;
                            }
                            toRemove--;
                            if (toRemove <= 0) break;
                        }
                    }
                    for (Mob mob : excessList) {
                        mob.remove();
                    }
                }
            }

            // 2. Check custom limit
            if (info.customLimit > 0 && info.activeCount >= info.customLimit) {
                continue;
            }

            boolean canSummonThis = false;
            if (info.isSlotWeapon) {
                if (globalSlotsUsed + info.summonCost <= playerMaxSummons) {
                    canSummonThis = true;
                }
            } else {
                if (info.activeCount + info.summonCost <= info.maxAllowed) {
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
                    player.tryAttack(slot, targetX, targetY);
                    localNextCheckTime = currentTime + AutoSummonConfig.getSummonCheckCooldown();
                    summonedAny = true;
                    autosummon.gui.AutoSummonGuiManager.notifySummonChanged();
                    break;
                }
            }
        }

        if (!summonedAny) {
            // When all summons are satisfied, back off check by 250ms to save CPU
            localNextCheckTime = currentTime + (allFull ? 250 : AutoSummonConfig.getSummonCheckCooldown());
        }
    }
}