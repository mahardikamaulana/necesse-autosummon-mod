package autosummon.patches;

import autosummon.AutoSummonConfig;
import autosummon.AutoSummonModSettings;
import necesse.engine.modLoader.annotations.ModMethodPatch;
import necesse.engine.registries.BuffRegistry;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.PlayerMob;
import necesse.entity.mobs.WormMobBody;
import necesse.entity.mobs.buffs.ActiveBuff;
import necesse.entity.mobs.buffs.BuffModifiers;
import necesse.entity.mobs.summon.FollowingWormMobHead;
import necesse.entity.mobs.summon.summonFollowingMob.SummonedFollowingMob;
import necesse.inventory.InventoryItem;
import necesse.inventory.PlayerInventorySlot;
import necesse.inventory.item.toolItem.summonToolItem.SummonToolItem;
import net.bytebuddy.asm.Advice;
import java.util.Timer;
import java.util.TimerTask;

/**
 * High-performance auto-summoning patch for Necesse 1.3.2.
 * Executes on PlayerMob.clientTick with zero per-tick allocations, single-pass
 * entity manager scanning, smart tick backoff, and instant fast-paths.
 */
@ModMethodPatch(target = PlayerMob.class, name = "clientTick", arguments = {})
public class AutoSummonPatch {

    // Local client player primitive tracking (public to allow bytecode inlined execution in PlayerMob)
    public static long localNextCheckTime = 0;
    public static int localLastMaxSummons = -1;

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
        public SummonToolItem staff;
        public int activeCount;
        public int maxAllowed;
        public int customLimit;
        public int summonCost;
        public boolean isSlotWeapon;
        public boolean present;

        public void reset() {
            this.slotIndex = -1;
            this.item = null;
            this.staff = null;
            this.activeCount = 0;
            this.maxAllowed = 0;
            this.customLimit = 0;
            this.summonCost = 0;
            this.isSlotWeapon = false;
            this.present = false;
        }
    }

    /**
     * Clear all SummonedMobBuff stacks from the player and despawn followers
     */
    public static void clearSummonBuffs(PlayerMob player) {
        if (player != null && player.buffManager != null) {
            player.buffManager.removeBuff("summonedmob", true);
        }
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
                    if (mob instanceof WormMobBody) {
                        continue;
                    }
                    
                    SummonToolItem matchedStaff = findStaffForMob(player, mob);
                    if (matchedStaff != null && matchedStaff.drawMaxSummons) {
                        slotsUsed += matchedStaff.getSummonSpaceTaken(null, player);
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
                SummonToolItem staff = (SummonToolItem) item.item;
                if (isSummonedByStaff(mob, staff, item)) {
                    return staff;
                }
            }
        }
        return null;
    }

    /**
     * Get the current summon count for custom follower staffs using atomic ID comparison
     */
    public static int getCurrentSummonCountFollowers(PlayerMob player, SummonToolItem staff, InventoryItem hotbarItem) {
        int currentSummons = 0;
        
        try {
            if (player != null && player.getLevel() != null && player.getLevel().entityManager != null && player.getLevel().entityManager.mobs != null) {
                int playerID = player.getUniqueID();
                for (Mob mob : player.getLevel().entityManager.mobs) {
                    if (mob != null && !mob.removed() && mob.getHealth() > 0 && mob.getFollowingUniqueID() == playerID) {
                        if (isSummonedByStaff(mob, staff, hotbarItem)) {
                            currentSummons++;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        
        return currentSummons;
    }

    /**
     * Check if a summoned mob was created by a specific staff
     */
    public static boolean isSummonedByStaff(Mob summonedMob, SummonToolItem staff, InventoryItem hotbarItem) {
        if (summonedMob == null || staff == null) return false;
        try {
            if (summonedMob instanceof WormMobBody) {
                return false;
            }

            String mobStringID = summonedMob.getStringID();
            String staffMobStringID = staff.mobStringID;
            
            if (mobStringID != null && staffMobStringID != null) {
                if (mobStringID.equalsIgnoreCase(staffMobStringID)) {
                    return true;
                }
            }
            
            if (mobStringID != null && staff.summonType != null && staff.summonType.equalsIgnoreCase(mobStringID)) {
                return true;
            }

            if (summonedMob instanceof SummonedFollowingMob) {
                SummonedFollowingMob sfm = (SummonedFollowingMob) summonedMob;
                if (sfm.removeWhenNotInInventoryItem != null && sfm.removeWhenNotInInventoryItem.getStringID().equalsIgnoreCase(staff.getStringID())) {
                    return true;
                }
            }

            if (summonedMob instanceof FollowingWormMobHead) {
                FollowingWormMobHead<?, ?> fwh = (FollowingWormMobHead<?, ?>) summonedMob;
                if (fwh.removeWhenNotInInventoryItem != null && fwh.removeWhenNotInInventoryItem.getStringID().equalsIgnoreCase(staff.getStringID())) {
                    return true;
                }
            }
            
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get the maximum summon count for a specific staff using direct engine API in Necesse 1.3.2
     */
    public static int getStaffMaxSummons(SummonToolItem staff, PlayerMob player, InventoryItem hotbarItem, int playerMaxSummons) {
        int weaponLimit = AutoSummonConfig.getSettings().getWeaponSummonLimit(staff.getStringID());
        if (weaponLimit > 0) {
            return weaponLimit;
        }

        if (AutoSummonConfig.getSettings().hasGlobalCustomLimit()) {
            return AutoSummonConfig.getSettings().getGlobalCustomSummonLimit();
        }
        
        if (!staff.drawMaxSummons) {
            try {
                return staff.getMaxSummons(hotbarItem, player);
            } catch (Throwable t) {
                try {
                    java.lang.reflect.Method m = staff.getClass().getMethod("getMaxSummons", InventoryItem.class, necesse.entity.mobs.itemAttacker.ItemAttackerMob.class);
                    Object res = m.invoke(staff, hotbarItem, player);
                    if (res instanceof Integer) {
                        return (Integer) res;
                    }
                } catch (Exception ignored) {}
            }
        }
        
        return playerMaxSummons;
    }

    /**
     * Check if a specific summon staff can summon another minion
     */
    public static boolean canSummon(PlayerMob player, SummonToolItem staff, InventoryItem hotbarItem, int playerMaxSummons) {
        int activeForStaff = getCurrentSummonCountFollowers(player, staff, hotbarItem);
        int weaponLimit = AutoSummonConfig.getSettings().getWeaponSummonLimit(staff.getStringID());

        if (weaponLimit > 0 && activeForStaff >= weaponLimit) {
            return false;
        }

        if (AutoSummonConfig.getSettings().hasGlobalCustomLimit() && activeForStaff >= AutoSummonConfig.getSettings().getGlobalCustomSummonLimit()) {
            return false;
        }

        int summonCost = Math.max(1, Math.round(staff.getSummonSpaceTaken(hotbarItem, player)));

        if (staff.drawMaxSummons) {
            int currentGlobalSlots = getTotalGlobalSummonSlotsUsed(player);
            return currentGlobalSlots + summonCost <= playerMaxSummons;
        }

        int staffMax = staff.getMaxSummons(hotbarItem, player);
        return activeForStaff + summonCost <= staffMax;
    }

    /**
     * Check if the player is at max summons across all hotbar staffs
     */
    public static boolean isAtMaxSummons(PlayerMob player) {
        int playerMaxSummons = player.buffManager.getModifier(BuffModifiers.MAX_SUMMONS);
        
        for (int i = 9; i >= 0; i--) {
            InventoryItem hotbarItem = player.getInv().main.getItem(i);
            
            if (hotbarItem != null && hotbarItem.item instanceof SummonToolItem) {
                SummonToolItem staff = (SummonToolItem) hotbarItem.item;
                if (canSummon(player, staff, hotbarItem, playerMaxSummons)) {
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

        // Fast-path 5: Quick scan hotbar slots 0-9 for any summon weapons
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
            if (item != null && item.item instanceof SummonToolItem) {
                SummonToolItem staff = (SummonToolItem) item.item;
                HotbarSlotInfo info = hotbarSlots[i];
                info.slotIndex = i;
                info.item = item;
                info.staff = staff;
                info.isSlotWeapon = staff.drawMaxSummons;
                info.summonCost = Math.max(1, Math.round(staff.getSummonSpaceTaken(item, player)));
                info.customLimit = settings.getWeaponSummonLimit(staff.getStringID());
                info.maxAllowed = info.isSlotWeapon 
                    ? Math.max(1, (int) Math.floor((float) playerMaxSummons / info.summonCost))
                    : Math.max(1, staff.getMaxSummons(item, player));
                info.present = true;
                hasAnySummonStaff = true;
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
                if (mob instanceof WormMobBody) {
                    continue;
                }

                boolean matched = false;
                for (int s = 0; s < 10; s++) {
                    HotbarSlotInfo info = hotbarSlots[s];
                    if (!info.present) continue;

                    if (isSummonedByStaff(mob, info.staff, info.item)) {
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
                    SummonToolItem invStaff = findStaffForMob(player, mob);
                    if (invStaff != null && invStaff.drawMaxSummons) {
                        globalSlotsUsed += Math.max(1, Math.round(invStaff.getSummonSpaceTaken(null, player)));
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

            boolean canSummonThis = false;

            // Check custom limit
            if (info.customLimit > 0 && info.activeCount >= info.customLimit) {
                continue;
            }

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
                String canAttackResult = info.staff.canAttack(player.getLevel(), (int) player.getX(),
                        (int) player.getY(), player, info.item);

                if (canAttackResult == null) {
                    PlayerInventorySlot slot = new PlayerInventorySlot(player.getInv().main, i);
                    player.tryAttack(slot, (int) player.getX(), (int) player.getY());
                    localNextCheckTime = currentTime + AutoSummonConfig.getSummonCheckCooldown();
                    summonedAny = true;
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