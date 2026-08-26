package autosummon.debug;

import autosummon.AutoSummonConfig;
import autosummon.compat.MobClassifier;
import autosummon.compat.SummonCompatRegistry;
import autosummon.compat.SummonWeaponAdapter;
import autosummon.util.AutoSummonLog;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.PlayerMob;
import necesse.inventory.InventoryItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Real-time diagnostic and inspection utility for mod creators to debug
 * summon weapons, adapters, minion matching, and limits in-game.
 */
public final class AutoSummonDiagnostic {

    private AutoSummonDiagnostic() {}

    public static class DiagnosticReport {
        public String itemID;
        public String displayName;
        public String providerName;
        public String adapterClass;
        public String category;
        public float spaceCost;
        public int maxAllowed;
        public int customLimit;
        public int activeInWorld;
        public String attackCheckStatus;
        public List<String> matchedMobIDs = new ArrayList<>();
        public boolean isSupported;

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== Auto Summon Diagnostic Report ===\n");
            sb.append("Item ID: ").append(itemID).append(" (").append(displayName).append(")\n");
            sb.append("Supported: ").append(isSupported ? "YES" : "NO").append("\n");
            if (isSupported) {
                sb.append("Provider: ").append(providerName).append("\n");
                sb.append("Adapter Class: ").append(adapterClass).append("\n");
                sb.append("Category: ").append(category).append("\n");
                sb.append("Slot Cost: ").append(spaceCost).append("\n");
                sb.append("Max Allowed: ").append(maxAllowed).append("\n");
                sb.append("Custom Limit Setting: ").append(customLimit == 0 ? "Auto (0)" : (customLimit == -1 ? "Disabled (-1)" : String.valueOf(customLimit))).append("\n");
                sb.append("Active Alive Minions: ").append(activeInWorld).append("\n");
                if (!matchedMobIDs.isEmpty()) {
                    sb.append("Matched Active Mobs: ").append(String.join(", ", matchedMobIDs)).append("\n");
                }
                sb.append("Ready to Attack (canAttack): ").append(attackCheckStatus == null ? "YES (Ready)" : "BLOCKED (" + attackCheckStatus + ")").append("\n");
            }
            sb.append("=====================================");
            return sb.toString();
        }
    }

    /**
     * Inspect an inventory item and generate a structured diagnostic report.
     */
    public static DiagnosticReport inspectItem(PlayerMob player, InventoryItem item) {
        DiagnosticReport report = new DiagnosticReport();
        if (item == null || item.item == null) {
            report.itemID = "None";
            report.displayName = "Empty Item";
            report.isSupported = false;
            return report;
        }

        report.itemID = item.item.getStringID();
        report.displayName = item.getItemDisplayName();

        SummonWeaponAdapter adapter = SummonCompatRegistry.getAdapter(item);
        if (adapter == null) {
            report.isSupported = false;
            return report;
        }

        report.isSupported = true;
        report.providerName = SummonCompatRegistry.getProviderNameForWeapon(item);
        report.adapterClass = adapter.getClass().getSimpleName();
        report.category = adapter.getCategory(item, player).name();
        report.spaceCost = adapter.getSummonSpaceCost(item, player);

        int playerMaxSummons = SummonCompatRegistry.getPlayerMaxSummonSlots(player, java.util.Collections.singletonList(item));
        report.maxAllowed = adapter.getMaxAllowed(item, player, playerMaxSummons);
        report.customLimit = AutoSummonConfig.getSettings().getWeaponSummonLimit(report.itemID);

        // Count active matching mobs in current world
        int activeCount = 0;
        if (player != null && player.getLevel() != null && player.getLevel().entityManager != null && player.getLevel().entityManager.mobs != null) {
            int playerID = player.getUniqueID();
            for (Mob mob : player.getLevel().entityManager.mobs) {
                if (mob == null || mob.removed() || mob.getHealth() <= 0 || mob.getFollowingUniqueID() != playerID) continue;
                if (MobClassifier.isIgnoredFollower(mob)) continue;
                if (adapter.isMatchingMinion(mob, item)) {
                    activeCount++;
                    String mobID = mob.getStringID();
                    if (mobID != null && !report.matchedMobIDs.contains(mobID)) {
                        report.matchedMobIDs.add(mobID);
                    }
                }
            }
        }
        report.activeInWorld = activeCount;

        if (player != null) {
            report.attackCheckStatus = adapter.canAttack(player, item);
        }

        return report;
    }

    /**
     * Inspect the weapon held in hotbar slot 1 (or currently selected item) and print to client chat.
     */
    public static void inspectAndPrintToChat(PlayerMob player) {
        if (player == null || player.getInv() == null || player.getInv().main == null) return;

        InventoryItem heldItem = player.getInv().main.getItem(0); // Hotbar slot 1
        if (heldItem == null || heldItem.item == null) {
            // Fallback to active attack slot if possible
            for (int i = 0; i < 10; i++) {
                InventoryItem check = player.getInv().main.getItem(i);
                if (check != null && SummonCompatRegistry.isSummonWeapon(check)) {
                    heldItem = check;
                    break;
                }
            }
        }

        DiagnosticReport report = inspectItem(player, heldItem);
        String text = report.toString();
        AutoSummonLog.info(text);

        if (player.getLevel() != null && player.getLevel().getClient() != null && player.getLevel().getClient().chat != null) {
            for (String line : text.split("\n")) {
                player.getLevel().getClient().chat.addMessage(line);
            }
        }
    }
}
