package autosummon.gui;

import autosummon.AutoSummonConfig;
import autosummon.AutoSummonModSettings;
import autosummon.compat.SummonCompatRegistry;
import autosummon.compat.SummonWeaponAdapter;
import autosummon.compat.SummonWeaponAdapter.SummonCategory;
import autosummon.patches.AutoSummonPatch;
import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.registries.BuffRegistry;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.PlayerMob;
import necesse.entity.mobs.buffs.ActiveBuff;
import necesse.entity.mobs.buffs.BuffModifiers;
import necesse.gfx.forms.Form;
import necesse.gfx.forms.components.FormBreakLine;
import necesse.gfx.forms.components.FormCheckBox;
import necesse.gfx.forms.components.FormContentBox;
import necesse.gfx.forms.components.FormInputSize;
import necesse.gfx.forms.components.FormItemIcon;
import necesse.gfx.forms.components.FormLabel;
import necesse.gfx.forms.components.FormTextButton;
import necesse.gfx.gameFont.FontOptions;
import necesse.gfx.ui.ButtonColor;
import necesse.inventory.InventoryItem;
import necesse.inventory.item.toolItem.summonToolItem.SummonToolItem;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/**
 * Native in-game GUI Form for Auto Summon configuration and per-weapon limit controls.
 * Features centered counters, padded Auto/Reset buttons, cap validation, red sentry header,
 * and summon dismissal upon clicking Done.
 */
public class AutoSummonForm extends Form {

    public static final int FORM_WIDTH = 510;
    public static final int FORM_HEIGHT = 420;

    private final PlayerMob player;
    private FormContentBox contentBox;
    private FormLabel capacitySummaryLabel;
    private FormCheckBox enableToggle;

    private final List<WeaponRowUI> weaponRowUIs = new ArrayList<>();
    private List<HotbarSummonEntry> currentHotbarEntries = new ArrayList<>();
    private long lastHotbarFingerprint = 0;
    private long lastSnapshotTime = 0;
    private boolean snapshotDirty = true;

    public AutoSummonForm(PlayerMob player) {
        super("autosummonsettingsform", FORM_WIDTH, FORM_HEIGHT);
        this.player = player;
        setDraggingBox(new Rectangle(0, 0, FORM_WIDTH, 32));
        initFormComponents();
    }

    /**
     * Build all form components using native Necesse UI elements
     */
    private void initFormComponents() {
        clearComponents();
        weaponRowUIs.clear();

        // 1. Header & Title (16px left margin)
        addComponent(new FormLabel("Auto Summon Configuration", new FontOptions(18), FormLabel.ALIGN_LEFT, 16, 8, getWidth() - 56));
        
        FormTextButton closeHeaderBtn = addComponent(new FormTextButton("X", getWidth() - 36, 6, 24, FormInputSize.SIZE_24, ButtonColor.BASE));
        closeHeaderBtn.onClicked(e -> AutoSummonGuiManager.closeGui());

        // 2. Master Toggle & Global Status (16px left margin)
        AutoSummonModSettings settings = AutoSummonConfig.getSettings();
        enableToggle = addComponent(new FormCheckBox("Enable Auto Summon", 16, 36, settings.isEnabled()));
        enableToggle.onClicked(e -> settings.setEnabled(enableToggle.checked));

        capacitySummaryLabel = addComponent(new FormLabel("Loading capacity...", new FontOptions(12).color(new Color(40, 40, 40)), FormLabel.ALIGN_LEFT, 16, 60, getWidth() - 32));

        addComponent(new FormBreakLine(FormBreakLine.ALIGN_BEGINNING, 12, 80, getWidth() - 24, true));

        // 3. Scrollable List of Hotbar Summon Weapons
        contentBox = addComponent(new FormContentBox(6, 86, getWidth() - 12, getHeight() - 86 - 46));
        populateWeaponList();

        // 4. Footer Controls (16px margins on left & right)
        addComponent(new FormBreakLine(FormBreakLine.ALIGN_BEGINNING, 12, getHeight() - 44, getWidth() - 24, true));

        FormTextButton resetAllBtn = addComponent(new FormTextButton("Reset All Limits", 16, getHeight() - 36, 140, FormInputSize.SIZE_24, ButtonColor.BASE));
        resetAllBtn.onClicked(e -> {
            settings.clearAllWeaponLimits();
            refresh();
        });

        FormTextButton doneBtn = addComponent(new FormTextButton("Done", getWidth() - 16 - 80, getHeight() - 36, 80, FormInputSize.SIZE_24, ButtonColor.GREEN));
        doneBtn.onClicked(e -> {
            cancelExistingSummons(player);
            AutoSummonGuiManager.closeGui();
        });
    }

    /**
     * Cancels/dismisses all current summon buffs and resets the timer so updated limits cast immediately
     */
    public static void cancelExistingSummons(PlayerMob player) {
        if (player == null) return;
        try {
            AutoSummonPatch.clearSummonBuffs(player);
            AutoSummonPatch.resetCheckTimer();
        } catch (Exception ignored) {}
    }

    /**
     * Mark that a summon/despawn event occurred so the snapshot updates on the very next render frame
     */
    public void markSnapshotDirty() {
        this.snapshotDirty = true;
    }

    /**
     * Computes a 64-bit fingerprint of the player's hotbar items (slots 0-9) to detect layout changes in O(1)
     */
    private long computeHotbarFingerprint() {
        if (player == null || player.getInv() == null || player.getInv().main == null) {
            return 0;
        }
        long hash = 17;
        for (int i = 0; i < 10; i++) {
            InventoryItem item = player.getInv().main.getItem(i);
            if (item != null && item.item != null) {
                hash = hash * 31 + i + item.item.getStringID().hashCode();
            } else {
                hash = hash * 31 + i;
            }
        }
        return hash;
    }

    /**
     * Performance-optimized single-pass aggregation of all level mobs to collect all hotbar and global slot counts
     */
    private SummonSnapshot computeSnapshot(List<HotbarSummonEntry> entries) {
        SummonSnapshot snapshot = new SummonSnapshot();
        if (player == null) return snapshot;

        snapshot.playerMaxSlots = player.buffManager != null ? player.buffManager.getModifier(BuffModifiers.MAX_SUMMONS) : 1;

        if (player.getLevel() != null && player.getLevel().entityManager != null && player.getLevel().entityManager.mobs != null) {
            int playerID = player.getUniqueID();
            for (Mob mob : player.getLevel().entityManager.mobs) {
                if (mob == null || mob.removed() || mob.getHealth() <= 0 || mob.getFollowingUniqueID() != playerID) {
                    continue;
                }
                if (SummonCompatRegistry.isWormBody(mob)) {
                    continue;
                }

                boolean matched = false;
                for (HotbarSummonEntry entry : entries) {
                    if (entry.adapter.isMatchingMinion(mob, entry.item)) {
                        snapshot.slotActiveCounts[entry.slotIndex]++;
                        SummonCategory category = entry.adapter.getCategory(entry.item, player);
                        if (category == SummonCategory.GLOBAL_SLOT) {
                            snapshot.totalGlobalSlotsUsed += (int) Math.max(1, Math.round(entry.adapter.getSummonSpaceCost(entry.item, player)));
                        } else if (category == SummonCategory.INDEPENDENT_FOLLOWER) {
                            snapshot.totalFollowers++;
                        } else if (category == SummonCategory.SENTRY_SUPPORT) {
                            snapshot.totalSentries++;
                        }
                        matched = true;
                        break;
                    }
                }

                // If minion was spawned by an off-hotbar inventory staff
                if (!matched) {
                    SummonWeaponAdapter invAdapter = SummonCompatRegistry.findWeaponForMob(player, mob);
                    if (invAdapter != null && invAdapter.isGlobalSlotWeapon(null, player)) {
                        snapshot.totalGlobalSlotsUsed += (int) Math.max(1, Math.round(invAdapter.getSummonSpaceCost(null, player)));
                    }
                }
            }
        }

        snapshot.freeSlots = Math.max(0, snapshot.playerMaxSlots - snapshot.totalGlobalSlotsUsed);
        return snapshot;
    }

    /**
     * Formats the header capacity summary text from the pre-aggregated snapshot
     */
    private String formatCapacitySummary(SummonSnapshot snapshot) {
        return "Global Slots: " + snapshot.totalGlobalSlotsUsed + " / " + snapshot.playerMaxSlots + " used (" + snapshot.freeSlots + " free) | Followers: " + snapshot.totalFollowers + " | Sentries: " + snapshot.totalSentries;
    }

    /**
     * Performs lightweight live in-place label updates without tearing down the UI component tree
     */
    public void updateLiveCounters() {
        long currentTime = System.currentTimeMillis();

        // 1. Hotbar Change Detection: If items in hotbar were swapped, trigger full layout rebuild
        long fingerprint = computeHotbarFingerprint();
        if (fingerprint != lastHotbarFingerprint) {
            lastHotbarFingerprint = fingerprint;
            populateWeaponList();
            return;
        }

        // 2. Throttle check: Only aggregate if event-triggered dirty OR every 250ms heartbeat
        if (!snapshotDirty && (currentTime - lastSnapshotTime < 250)) {
            return;
        }

        snapshotDirty = false;
        lastSnapshotTime = currentTime;

        // 3. Single-pass aggregation
        SummonSnapshot snapshot = computeSnapshot(currentHotbarEntries);

        // 4. Update Header Summary Label
        if (capacitySummaryLabel != null) {
            capacitySummaryLabel.setText(formatCapacitySummary(snapshot));
        }

        // 5. Update each weapon row label in place (with zero-allocation early exit if count is unchanged)
        for (WeaponRowUI row : weaponRowUIs) {
            int activeCount = snapshot.slotActiveCounts[row.entry.slotIndex];
            row.update(player, activeCount, snapshot.playerMaxSlots);
        }
    }

    /**
     * Hook into Necesse's frame render loop for real-time live updates
     */
    @Override
    public void draw(TickManager tickManager, PlayerMob player, Rectangle rectangle) {
        updateLiveCounters();
        super.draw(tickManager, player, rectangle);
    }

    /**
     * Scans hotbar and populates content box with distinct categorized sections
     */
    private void populateWeaponList() {
        contentBox.clearComponents();
        weaponRowUIs.clear();

        currentHotbarEntries = getHotbarSummonWeapons();
        lastHotbarFingerprint = computeHotbarFingerprint();

        if (currentHotbarEntries.isEmpty()) {
            contentBox.addComponent(new FormLabel("No summon weapons found in hotbar.", new FontOptions(14).color(new Color(130, 70, 20)), FormLabel.ALIGN_LEFT, 12, 20, contentBox.getWidth() - 24));
            contentBox.addComponent(new FormLabel("Place summon weapons in hotbar slots 1-10 to customize their individual summon limits.", new FontOptions(12).color(new Color(60, 50, 40)), FormLabel.ALIGN_LEFT, 12, 44, contentBox.getWidth() - 24));
            contentBox.setContentBox(new Rectangle(0, 0, contentBox.getWidth(), 120));
            if (capacitySummaryLabel != null) {
                SummonSnapshot emptySnapshot = computeSnapshot(currentHotbarEntries);
                capacitySummaryLabel.setText(formatCapacitySummary(emptySnapshot));
            }
            return;
        }

        AutoSummonModSettings settings = AutoSummonConfig.getSettings();
        int currentY = 4;
        int playerMaxSlots = player != null && player.buffManager != null ? player.buffManager.getModifier(BuffModifiers.MAX_SUMMONS) : 1;

        // Separate entries by category
        List<HotbarSummonEntry> slotEntries = new ArrayList<>();
        List<HotbarSummonEntry> followerEntries = new ArrayList<>();
        List<HotbarSummonEntry> sentryEntries = new ArrayList<>();

        for (HotbarSummonEntry entry : currentHotbarEntries) {
            SummonCategory category = entry.adapter.getCategory(entry.item, player);
            if (category == SummonCategory.GLOBAL_SLOT) {
                slotEntries.add(entry);
            } else if (category == SummonCategory.INDEPENDENT_FOLLOWER) {
                followerEntries.add(entry);
            } else {
                sentryEntries.add(entry);
            }
        }

        // Render each category in order with dynamic height
        currentY = renderCategorySection(slotEntries, SummonCategory.GLOBAL_SLOT, currentY, playerMaxSlots, settings);
        currentY = renderCategorySection(followerEntries, SummonCategory.INDEPENDENT_FOLLOWER, currentY, playerMaxSlots, settings);
        currentY = renderCategorySection(sentryEntries, SummonCategory.SENTRY_SUPPORT, currentY, playerMaxSlots, settings);

        contentBox.setContentBox(new Rectangle(0, 0, contentBox.getWidth(), Math.max(contentBox.getHeight(), currentY + 10)));

        // Perform initial counter population
        snapshotDirty = true;
        updateLiveCounters();
    }

    /**
     * Renders a category header banner and all weapon rows belonging to it with centered counters, padded buttons, and cap validation
     */
    private int renderCategorySection(List<HotbarSummonEntry> list, SummonCategory category, int startY, int playerMaxSlots, AutoSummonModSettings settings) {
        if (list.isEmpty()) {
            return startY;
        }

        int currentY = startY;

        // Section header banner (Sentry uses Red)
        String headerText = "--- " + category.sectionTitle + " ---";
        contentBox.addComponent(new FormLabel(headerText, new FontOptions(12).color(category.sectionHeaderColor), FormLabel.ALIGN_LEFT, 10, currentY + 2, contentBox.getWidth() - 20));
        currentY += 22;

        int boxWidth = contentBox.getWidth();
        // Exact pixel placement with horizontal padding
        int btnResetWidth = 46;
        int btnResetX = boxWidth - 12 - btnResetWidth; // 12px right padding before scrollbar
        int btnIncWidth = 24;
        int btnIncX = btnResetX - 8 - btnIncWidth;      // 8px left margin before Auto button
        int counterSpan = 44;                          // 44px dedicated space for counter
        int btnDecWidth = 24;
        int btnDecX = btnIncX - counterSpan - btnDecWidth;
        int counterCenterX = (btnDecX + btnDecWidth + btnIncX) / 2; // Exact mathematical center
        int textMaxWidth = btnDecX - 52;

        for (HotbarSummonEntry entry : list) {
            final int slot = entry.slotIndex;
            final InventoryItem item = entry.item;
            final SummonWeaponAdapter adapter = entry.adapter;
            final String itemID = adapter.getItemStringID();

            // Calculate valid maximum for this weapon
            final int maxAllowed = adapter.getMaxAllowed(item, player, playerMaxSlots);
            int configuredLimit = settings.getWeaponSummonLimit(itemID);
            if (configuredLimit > maxAllowed) {
                configuredLimit = 0;
                settings.setWeaponSummonLimit(itemID, 0);
            }

            // Name & Slot label
            String titleText = "[Slot " + (slot + 1) + "] " + adapter.getDisplayName(item);
            FormLabel titleLabel = contentBox.addComponent(new FormLabel(titleText, new FontOptions(13).color(new Color(30, 30, 30)), FormLabel.ALIGN_LEFT, 46, currentY + 4, textMaxWidth));
            int titleHeight = Math.max(14, titleLabel.getHeight());

            // Status label placed directly beneath title with dynamic offset (neutral text)
            int statusY = currentY + 4 + titleHeight + 2;
            FormLabel statusLabel = contentBox.addComponent(new FormLabel("Calculating...", new FontOptions(11).color(new Color(60, 50, 40)), FormLabel.ALIGN_LEFT, 46, statusY, textMaxWidth));
            int statusHeight = Math.max(12, statusLabel.getHeight());

            // Register holder for real-time updates
            weaponRowUIs.add(new WeaponRowUI(entry, statusLabel, category));

            // Calculate dynamic row height based on actual text height
            int textTotalHeight = titleHeight + statusHeight + 6;
            int rowHeight = Math.max(50, textTotalHeight + 10);

            // Native Item Icon vertically centered
            int iconY = currentY + (rowHeight - 32) / 2;
            contentBox.addComponent(new FormItemIcon(8, iconY, item, true));

            // Adjuster Controls vertically centered in the dynamic row
            int controlY = currentY + (rowHeight - 24) / 2;

            FormTextButton decBtn = contentBox.addComponent(new FormTextButton("-", btnDecX, controlY, btnDecWidth, FormInputSize.SIZE_24, ButtonColor.BASE));
            decBtn.setActive(configuredLimit > 0);
            decBtn.onClicked(e -> {
                int limit = settings.getWeaponSummonLimit(itemID);
                if (limit > 1) {
                    settings.setWeaponSummonLimit(itemID, limit - 1);
                } else if (limit == 1) {
                    settings.setWeaponSummonLimit(itemID, 0); // 0 = Auto
                }
                refresh();
            });

            String limitText = (configuredLimit <= 0) ? "Auto" : String.valueOf(configuredLimit);
            // Perfectly centered at counterCenterX with ALIGN_MID
            FormLabel limitLabel = contentBox.addComponent(new FormLabel(limitText, new FontOptions(13), FormLabel.ALIGN_MID, counterCenterX, controlY + 4, counterSpan));
            if (configuredLimit > 0) {
                limitLabel.setColor(new Color(20, 130, 20));
            } else {
                limitLabel.setColor(new Color(40, 40, 40));
            }

            FormTextButton incBtn = contentBox.addComponent(new FormTextButton("+", btnIncX, controlY, btnIncWidth, FormInputSize.SIZE_24, ButtonColor.BASE));
            incBtn.setActive(configuredLimit < maxAllowed);
            incBtn.onClicked(e -> {
                int limit = settings.getWeaponSummonLimit(itemID);
                if (limit < maxAllowed) {
                    settings.setWeaponSummonLimit(itemID, limit + 1);
                    refresh();
                }
            });

            FormTextButton resetItemBtn = contentBox.addComponent(new FormTextButton("Auto", btnResetX, controlY, btnResetWidth, FormInputSize.SIZE_24, ButtonColor.BASE));
            resetItemBtn.setActive(configuredLimit > 0);
            resetItemBtn.onClicked(e -> {
                settings.setWeaponSummonLimit(itemID, 0);
                refresh();
            });

            // Row separator line
            currentY += rowHeight;
            contentBox.addComponent(new FormBreakLine(FormBreakLine.ALIGN_BEGINNING, 8, currentY - 2, contentBox.getWidth() - 16, true));
        }

        currentY += 4;
        return currentY;
    }

    /**
     * Calculate the maximum allowed summon limit for this weapon based on slots or weapon cap
     */
    public static int getMaxAllowedSummons(SummonToolItem staff, InventoryItem item, PlayerMob player) {
        if (item == null || player == null) {
            return 1;
        }
        SummonWeaponAdapter adapter = SummonCompatRegistry.getAdapter(item);
        if (adapter != null) {
            int playerMaxSlots = player.buffManager != null ? player.buffManager.getModifier(BuffModifiers.MAX_SUMMONS) : 1;
            return adapter.getMaxAllowed(item, player, playerMaxSlots);
        }
        return 1;
    }

    /**
     * Reads all summon weapons currently in the player's hotbar (slots 0-9) via SummonCompatRegistry
     */
    private List<HotbarSummonEntry> getHotbarSummonWeapons() {
        List<HotbarSummonEntry> list = new ArrayList<>();
        if (player == null || player.getInv() == null || player.getInv().main == null) {
            return list;
        }

        for (int i = 0; i < 10; i++) {
            InventoryItem item = player.getInv().main.getItem(i);
            if (item != null) {
                SummonWeaponAdapter adapter = SummonCompatRegistry.getAdapter(item);
                if (adapter != null) {
                    list.add(new HotbarSummonEntry(i, item, adapter));
                }
            }
        }
        return list;
    }

    /**
     * Refresh the form UI with updated player/settings data
     */
    public void refresh() {
        if (enableToggle != null) {
            enableToggle.checked = AutoSummonConfig.isEnabled();
        }
        populateWeaponList();
    }

    /**
     * Pre-aggregated snapshot of all summon metrics across the level in a single pass
     */
    public static class SummonSnapshot {
        public final int[] slotActiveCounts = new int[10];
        public int totalGlobalSlotsUsed = 0;
        public int totalFollowers = 0;
        public int totalSentries = 0;
        public int playerMaxSlots = 1;
        public int freeSlots = 0;
    }

    /**
     * Lightweight UI reference holder for each weapon row in the content box
     */
    private static class WeaponRowUI {
        final HotbarSummonEntry entry;
        final FormLabel statusLabel;
        final SummonCategory category;
        int lastActiveCount = -1;
        int lastPlayerMaxSlots = -1;

        WeaponRowUI(HotbarSummonEntry entry, FormLabel statusLabel, SummonCategory category) {
            this.entry = entry;
            this.statusLabel = statusLabel;
            this.category = category;
        }

        void update(PlayerMob player, int activeCount, int playerMaxSlots) {
            // Zero-allocation early exit if values haven't mutated
            if (activeCount == lastActiveCount && playerMaxSlots == lastPlayerMaxSlots) {
                return;
            }
            lastActiveCount = activeCount;
            lastPlayerMaxSlots = playerMaxSlots;

            String statusText;
            if (category == SummonCategory.GLOBAL_SLOT) {
                float spaceCost = entry.adapter.getSummonSpaceCost(entry.item, player);
                String costDisplay = (spaceCost == (int) spaceCost) ? String.valueOf((int) spaceCost) : String.format("%.1f", spaceCost);
                int slotsConsumedByThis = (int) Math.round(activeCount * spaceCost);
                statusText = "Cost: " + costDisplay + " slot(s) | Consuming: " + slotsConsumedByThis + " / " + playerMaxSlots + " Slots (Active: " + activeCount + ")";
            } else if (category == SummonCategory.INDEPENDENT_FOLLOWER) {
                int maxAllowed = entry.adapter.getMaxAllowed(entry.item, player, playerMaxSlots);
                statusText = "Cap: " + maxAllowed + " | Active: " + activeCount + " (0 Global Slots)";
            } else {
                int maxAllowed = entry.adapter.getMaxAllowed(entry.item, player, playerMaxSlots);
                statusText = "Cap: " + maxAllowed + " | Active: " + activeCount + " (0 Global Slots)";
            }

            statusLabel.setText(statusText);
        }
    }

    /**
     * Internal container holding hotbar summon weapon details
     */
    private static class HotbarSummonEntry {
        final int slotIndex;
        final InventoryItem item;
        final SummonWeaponAdapter adapter;

        HotbarSummonEntry(int slotIndex, InventoryItem item, SummonWeaponAdapter adapter) {
            this.slotIndex = slotIndex;
            this.item = item;
            this.adapter = adapter;
        }
    }
}
