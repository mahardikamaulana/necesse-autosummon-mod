package autosummon.gui;

import autosummon.AutoSummonConfig;
import autosummon.AutoSummonModSettings;
import autosummon.compat.MobClassifier;
import autosummon.compat.SummonCompatRegistry;
import autosummon.compat.SummonWeaponAdapter;
import autosummon.compat.SummonWeaponAdapter.SummonCategory;
import autosummon.patches.AutoSummonPatch;
import autosummon.util.AutoSummonLog;
import necesse.engine.gameLoop.tickManager.TickManager;
import necesse.engine.input.Control;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.PlayerMob;
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
import java.util.Collections;
import java.util.List;

/**
 * Native in-game GUI Form for Auto Summon configuration and per-weapon limit controls.
 * Features centered counters, padded Auto/Reset buttons, cap validation, red sentry header,
 * resilient render-loop fault tolerance, zero-allocation MobClassifier, and summon dismissal upon clicking Done.
 */
public class AutoSummonForm extends Form {

    public static final int FORM_WIDTH = 540;
    public static final int FORM_HEIGHT = 420;

    private final PlayerMob player;
    private FormContentBox contentBox;
    private FormLabel capacitySummaryLabel;
    private FormCheckBox enableToggle;

    private final List<WeaponRowUI> weaponRowUIs = new ArrayList<>();
    private List<HotbarSummonEntry> currentHotbarEntries = new ArrayList<>();
    private long lastHotbarFingerprint = 0;
    private long lastDrawCheckTime = 0;
    private long lastSnapshotTime = 0;
    private boolean snapshotDirty = true;

    // Capacity summary change detection
    private int lastSummaryGlobal = -1;
    private int lastSummaryMax = -1;
    private int lastSummaryFollowers = -1;
    private int lastSummarySentries = -1;

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
        try {
            clearComponents();
            weaponRowUIs.clear();

            // 1. Header & Title (16px left margin)
            addComponent(new FormLabel("Auto Summon Configuration", new FontOptions(18), FormLabel.ALIGN_LEFT, 16, 8, getWidth() - 56));
            
            FormTextButton closeHeaderBtn = addComponent(new FormTextButton("X", getWidth() - 36, 6, 24, FormInputSize.SIZE_24, ButtonColor.BASE));
            closeHeaderBtn.onClicked(e -> AutoSummonGuiManager.closeGui());

            // 2. Master Toggle & Global Status (16px left margin)
            AutoSummonModSettings settings = AutoSummonConfig.getSettings();
            enableToggle = addComponent(new FormCheckBox("Enable Auto Summon", 16, 36, settings.isEnabled()));
            enableToggle.onClicked(e -> {
                try {
                    settings.setEnabled(enableToggle.checked);
                } catch (Throwable t) {
                    AutoSummonLog.error("Error toggling enable in GUI", t);
                }
            });

            FormCheckBox autoOnJoinToggle = addComponent(new FormCheckBox("Auto Summon on Join", 210, 36, settings.isAutoSummonOnJoin()));
            autoOnJoinToggle.onClicked(e -> {
                try {
                    settings.setAutoSummonOnJoin(autoOnJoinToggle.checked);
                } catch (Throwable t) {
                    AutoSummonLog.error("Error toggling auto on join in GUI", t);
                }
            });

            capacitySummaryLabel = addComponent(new FormLabel("Loading capacity...", new FontOptions(12).color(new Color(40, 40, 40)), FormLabel.ALIGN_LEFT, 16, 60, getWidth() - 32));

            addComponent(new FormBreakLine(FormBreakLine.ALIGN_BEGINNING, 12, 80, getWidth() - 24, true));

            // 3. Scrollable List of Hotbar Summon Weapons
            contentBox = addComponent(new FormContentBox(6, 86, getWidth() - 12, getHeight() - 86 - 46));
            populateWeaponList();

            // 4. Footer Controls (16px margins on left & right)
            addComponent(new FormBreakLine(FormBreakLine.ALIGN_BEGINNING, 12, getHeight() - 44, getWidth() - 24, true));

            FormTextButton resetAllBtn = addComponent(new FormTextButton("Reset All Limits", 16, getHeight() - 36, 140, FormInputSize.SIZE_24, ButtonColor.BASE));
            resetAllBtn.onClicked(e -> {
                try {
                    settings.clearAllWeaponLimits();
                    refresh();
                } catch (Throwable t) {
                    AutoSummonLog.error("Error resetting limits in GUI", t);
                }
            });

            FormTextButton doneBtn = addComponent(new FormTextButton("Done", getWidth() - 16 - 80, getHeight() - 36, 80, FormInputSize.SIZE_24, ButtonColor.GREEN));
            doneBtn.onClicked(e -> {
                try {
                    cancelExistingSummons(player);
                    AutoSummonGuiManager.closeGui();
                } catch (Throwable t) {
                    AutoSummonLog.error("Error completing GUI Done action", t);
                    AutoSummonGuiManager.closeGui();
                }
            });
        } catch (Throwable t) {
            AutoSummonLog.error("Critical error initializing AutoSummonForm components", t);
        }
    }

    /**
     * Cancels/dismisses all current summon buffs and resets the timer so updated limits cast immediately
     */
    public static void cancelExistingSummons(PlayerMob player) {
        if (player == null) return;
        try {
            AutoSummonPatch.clearSummonBuffs(player);
            AutoSummonPatch.resetCheckTimer();
        } catch (Throwable t) {
            AutoSummonLog.error("Error in cancelExistingSummons", t);
        }
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
        try {
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
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * Performance-optimized single-pass aggregation of all level mobs to collect all hotbar and global slot counts
     */
    private SummonSnapshot computeSnapshot(List<HotbarSummonEntry> entries) {
        return computeSnapshot(entries, this.player);
    }

    private SummonSnapshot computeSnapshot(List<HotbarSummonEntry> entries, PlayerMob activePlayer) {
        SummonSnapshot snapshot = new SummonSnapshot();
        PlayerMob p = activePlayer != null ? activePlayer : this.player;
        if (p == null) return snapshot;

        try {
            List<InventoryItem> itemsToCheck = null;
            if (entries != null) {
                itemsToCheck = new ArrayList<>(entries.size());
                for (HotbarSummonEntry entry : entries) {
                    if (entry != null && entry.item != null) {
                        itemsToCheck.add(entry.item);
                    }
                }
            }
            snapshot.playerMaxSlots = SummonCompatRegistry.getPlayerMaxSummonSlots(p, itemsToCheck);

            if (p.getLevel() != null && p.getLevel().entityManager != null && p.getLevel().entityManager.mobs != null) {
                int playerID = p.getUniqueID();
                for (Mob mob : p.getLevel().entityManager.mobs) {
                    if (mob == null || mob.removed() || mob.getHealth() <= 0 || mob.getFollowingUniqueID() != playerID) {
                        continue;
                    }
                    if (MobClassifier.isIgnoredFollower(mob)) {
                        continue;
                    }

                    boolean matched = false;
                    if (entries != null) {
                        for (HotbarSummonEntry entry : entries) {
                            if (entry == null || entry.adapter == null) continue;
                            if (entry.adapter.isMatchingMinion(mob, entry.item)) {
                                if (entry.slotIndex >= 0 && entry.slotIndex < 10) {
                                    snapshot.slotActiveCounts[entry.slotIndex]++;
                                }
                                SummonCategory category = entry.adapter.getCategory(entry.item, p);
                                if (category == SummonCategory.GLOBAL_SLOT) {
                                    snapshot.totalGlobalSlotsUsed += (int) Math.max(1, Math.round(entry.adapter.getSummonSpaceCost(entry.item, p)));
                                } else if (category == SummonCategory.INDEPENDENT_FOLLOWER) {
                                    snapshot.totalFollowers++;
                                } else if (category == SummonCategory.SENTRY_SUPPORT) {
                                    snapshot.totalSentries++;
                                }
                                matched = true;
                                break;
                            }
                        }
                    }

                    // If minion was spawned by an off-hotbar inventory staff
                    if (!matched) {
                        SummonWeaponAdapter invAdapter = SummonCompatRegistry.findWeaponForMob(p, mob);
                        if (invAdapter != null && invAdapter.isGlobalSlotWeapon(null, p)) {
                            snapshot.totalGlobalSlotsUsed += (int) Math.max(1, Math.round(invAdapter.getSummonSpaceCost(null, p)));
                        }
                    }
                }
            }
        } catch (Throwable t) {
            AutoSummonLog.logThrottledError("gui_computeSnapshot", "Error computing summon snapshot in GUI", t, 5000);
        }

        snapshot.freeSlots = Math.max(0, snapshot.playerMaxSlots - snapshot.totalGlobalSlotsUsed);
        return snapshot;
    }

    /**
     * Formats the header capacity summary text from the pre-aggregated snapshot
     */
    private String formatCapacitySummary(SummonSnapshot snapshot) {
        if (snapshot == null) return "Capacity: Unavailable";
        return "Global Slots: " + snapshot.totalGlobalSlotsUsed + " / " + snapshot.playerMaxSlots + " used (" + snapshot.freeSlots + " free) | Followers: " + snapshot.totalFollowers + " | Sentries: " + snapshot.totalSentries;
    }

    /**
     * Performs lightweight live in-place label updates without tearing down the UI component tree
     */
    public void updateLiveCounters() {
        updateLiveCounters(this.player);
    }

    public void updateLiveCounters(PlayerMob livePlayer) {
        PlayerMob p = livePlayer != null ? livePlayer : this.player;
        try {
            long currentTime = System.currentTimeMillis();

            // 1. Throttle hotbar fingerprint checking to 100ms intervals to prevent frame overhead
            if (currentTime - lastDrawCheckTime >= 100) {
                lastDrawCheckTime = currentTime;
                long fingerprint = computeHotbarFingerprint();
                if (fingerprint != lastHotbarFingerprint) {
                    lastHotbarFingerprint = fingerprint;
                    populateWeaponList();
                    return;
                }
            }

            // 2. Throttle check: Only aggregate if event-triggered dirty OR every 250ms heartbeat
            if (!snapshotDirty && (currentTime - lastSnapshotTime < 250)) {
                return;
            }

            snapshotDirty = false;
            lastSnapshotTime = currentTime;

            // 3. Single-pass aggregation with MobClassifier
            SummonSnapshot snapshot = computeSnapshot(currentHotbarEntries, p);

            // 4. Update Header Summary Label with zero-allocation early exit if unchanged
            if (capacitySummaryLabel != null) {
                if (snapshot.totalGlobalSlotsUsed != lastSummaryGlobal
                        || snapshot.playerMaxSlots != lastSummaryMax
                        || snapshot.totalFollowers != lastSummaryFollowers
                        || snapshot.totalSentries != lastSummarySentries) {
                    lastSummaryGlobal = snapshot.totalGlobalSlotsUsed;
                    lastSummaryMax = snapshot.playerMaxSlots;
                    lastSummaryFollowers = snapshot.totalFollowers;
                    lastSummarySentries = snapshot.totalSentries;
                    capacitySummaryLabel.setText(formatCapacitySummary(snapshot));
                }
            }

            // 5. Update each weapon row label in place (with zero-allocation early exit if count is unchanged)
            for (WeaponRowUI row : weaponRowUIs) {
                if (row != null && row.entry != null && row.entry.slotIndex >= 0 && row.entry.slotIndex < 10) {
                    int activeCount = snapshot.slotActiveCounts[row.entry.slotIndex];
                    row.update(p, activeCount, snapshot.playerMaxSlots);
                }
            }
        } catch (Throwable t) {
            AutoSummonLog.logThrottledError("gui_updateLiveCounters", "Error updating live counters in GUI", t, 5000);
        }
    }

    /**
     * Hook into Necesse's frame render loop for real-time live updates with fail-safe boundaries
     */
    @Override
    public void draw(TickManager tickManager, PlayerMob player, Rectangle rectangle) {
        try {
            updateLiveCounters(player);
        } catch (Throwable t) {
            AutoSummonLog.logThrottledError("gui_draw_update", "Error during AutoSummonForm.draw() counter update", t, 5000);
        }
        try {
            super.draw(tickManager, player, rectangle);
        } catch (Throwable t) {
            AutoSummonLog.logThrottledError("gui_draw_super", "Error rendering AutoSummonForm components", t, 5000);
        }
    }

    /**
     * Scans hotbar and populates content box with distinct categorized sections
     */
    private void populateWeaponList() {
        if (contentBox == null) return;
        try {
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
            List<InventoryItem> hotbarItems = new ArrayList<>(currentHotbarEntries.size());
            for (HotbarSummonEntry entry : currentHotbarEntries) {
                if (entry != null && entry.item != null) {
                    hotbarItems.add(entry.item);
                }
            }
            int playerMaxSlots = SummonCompatRegistry.getPlayerMaxSummonSlots(player, hotbarItems);

            // Separate entries by category
            List<HotbarSummonEntry> slotEntries = new ArrayList<>();
            List<HotbarSummonEntry> followerEntries = new ArrayList<>();
            List<HotbarSummonEntry> sentryEntries = new ArrayList<>();

            for (HotbarSummonEntry entry : currentHotbarEntries) {
                if (entry == null || entry.adapter == null) continue;
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
        } catch (Throwable t) {
            AutoSummonLog.error("Error populating weapon list in GUI", t);
        }
    }

    /**
     * Renders a category header banner and all weapon rows belonging to it with centered counters, padded buttons, and cap validation
     */
    private int renderCategorySection(List<HotbarSummonEntry> list, SummonCategory category, int startY, int playerMaxSlots, AutoSummonModSettings settings) {
        if (list == null || list.isEmpty() || contentBox == null) {
            return startY;
        }

        int currentY = startY;

        try {
            // Section header banner (Sentry uses Red)
            String headerText = "--- " + (category != null ? category.sectionTitle : "SUMMON WEAPONS") + " ---";
            Color headerColor = category != null ? category.sectionHeaderColor : new Color(30, 90, 180);
            contentBox.addComponent(new FormLabel(headerText, new FontOptions(12).color(headerColor), FormLabel.ALIGN_LEFT, 10, currentY + 2, contentBox.getWidth() - 20));
            currentY += 22;

            int boxWidth = contentBox.getWidth();
            // Exact pixel placement with horizontal padding
            int btnResetWidth = 46;
            int btnResetX = boxWidth - 14 - btnResetWidth; // 14px right padding before scrollbar
            int btnIncWidth = 24;
            int btnIncX = btnResetX - 6 - btnIncWidth;      // 6px spacing
            int counterSpan = 38;                          // 38px dedicated space for counter
            int btnDecWidth = 24;
            int btnDecX = btnIncX - counterSpan - btnDecWidth;
            int counterCenterX = btnDecX + btnDecWidth + (counterSpan / 2);
            int textMaxWidth = Math.max(100, btnDecX - 46 - 8);

            for (HotbarSummonEntry entry : list) {
                if (entry == null || entry.adapter == null) continue;
                final int slot = entry.slotIndex;
                final InventoryItem item = entry.item;
                final SummonWeaponAdapter adapter = entry.adapter;
                final String itemID = adapter.getItemStringID();
                if (itemID == null) continue;

                // Calculate valid maximum for this weapon
                final int maxAllowed = Math.max(1, adapter.getMaxAllowed(item, player, playerMaxSlots));
                int configuredLimit = settings != null ? settings.getWeaponSummonLimit(itemID) : 0;

                // Name & Slot label
                String titleText = "[Slot " + (slot + 1) + "] " + adapter.getDisplayName(item);
                FormLabel titleLabel = contentBox.addComponent(new FormLabel(titleText, new FontOptions(13).color(new Color(30, 30, 30)), FormLabel.ALIGN_LEFT, 46, currentY + 4, textMaxWidth));
                int titleHeight = Math.max(14, titleLabel.getHeight());

                // Status label placed directly beneath title with dynamic offset (neutral text)
                int statusY = currentY + 4 + titleHeight + 2;
                FormLabel statusLabel = contentBox.addComponent(new FormLabel("Calculating...", new FontOptions(11).color(new Color(60, 50, 40)), FormLabel.ALIGN_LEFT, 46, statusY, textMaxWidth));
                int statusHeight = Math.max(12, statusLabel.getHeight());

                // Register holder for real-time updates
                weaponRowUIs.add(new WeaponRowUI(entry, statusLabel, category, textMaxWidth));

                // Calculate dynamic row height based on actual text height
                int textTotalHeight = titleHeight + statusHeight + 6;
                int rowHeight = Math.max(50, textTotalHeight + 10);

                // Native Item Icon vertically centered
                int iconY = currentY + (rowHeight - 32) / 2;
                contentBox.addComponent(new FormItemIcon(8, iconY, item, true));

                // Adjuster Controls vertically centered in the dynamic row
                int controlY = currentY + (rowHeight - 24) / 2;

                FormTextButton decBtn = contentBox.addComponent(new FormTextButton("-", btnDecX, controlY, btnDecWidth, FormInputSize.SIZE_24, ButtonColor.BASE));
                decBtn.setActive(configuredLimit != -1);
                decBtn.onClicked(e -> {
                    try {
                        if (settings == null) return;
                        boolean isShiftDown = Control.CRAFT_10 != null && Control.CRAFT_10.isDown();
                        int step = isShiftDown ? 5 : 1;
                        int limit = settings.getWeaponSummonLimit(itemID);
                        if (limit == 0) {
                            if (maxAllowed > 1) {
                                settings.setWeaponSummonLimit(itemID, Math.max(1, maxAllowed - step));
                            } else {
                                settings.setWeaponSummonLimit(itemID, -1); // -1 = Off
                            }
                        } else if (limit > step) {
                            settings.setWeaponSummonLimit(itemID, limit - step);
                        } else if (limit > 1) {
                            settings.setWeaponSummonLimit(itemID, 1);
                        } else if (limit == 1) {
                            settings.setWeaponSummonLimit(itemID, -1); // -1 = Off
                        }
                        refresh();
                    } catch (Throwable t) {
                        AutoSummonLog.error("Error on decrement limit click", t);
                    }
                });

                String limitText;
                Color limitColor;
                if (configuredLimit == -1) {
                    limitText = "Off";
                    limitColor = new Color(180, 40, 40);
                } else if (configuredLimit > 0) {
                    limitText = String.valueOf(configuredLimit);
                    if (configuredLimit <= maxAllowed) {
                        limitColor = new Color(20, 130, 20);
                    } else {
                        limitColor = new Color(20, 100, 200);
                    }
                } else {
                    limitText = "Auto";
                    limitColor = new Color(40, 40, 40);
                }

                // Perfectly centered at counterCenterX with ALIGN_MID
                FormLabel limitLabel = contentBox.addComponent(new FormLabel(limitText, new FontOptions(13), FormLabel.ALIGN_MID, counterCenterX, controlY + 4, counterSpan));
                limitLabel.setColor(limitColor);

                FormTextButton incBtn = contentBox.addComponent(new FormTextButton("+", btnIncX, controlY, btnIncWidth, FormInputSize.SIZE_24, ButtonColor.BASE));
                boolean canInc = (configuredLimit == -1) || (configuredLimit >= 0 && configuredLimit < AutoSummonModSettings.MAX_MANUAL_LIMIT);
                incBtn.setActive(canInc);
                incBtn.onClicked(e -> {
                    try {
                        if (settings == null) return;
                        boolean isShiftDown = Control.CRAFT_10 != null && Control.CRAFT_10.isDown();
                        int step = isShiftDown ? 5 : 1;
                        int limit = settings.getWeaponSummonLimit(itemID);
                        if (limit == -1) {
                            settings.setWeaponSummonLimit(itemID, 1);
                        } else if (limit == 0) {
                            int startVal = isShiftDown ? 5 : (maxAllowed > 1 ? Math.min(2, maxAllowed) : 2);
                            settings.setWeaponSummonLimit(itemID, Math.min(AutoSummonModSettings.MAX_MANUAL_LIMIT, startVal));
                        } else {
                            settings.setWeaponSummonLimit(itemID, Math.min(AutoSummonModSettings.MAX_MANUAL_LIMIT, limit + step));
                        }
                        refresh();
                    } catch (Throwable t) {
                        AutoSummonLog.error("Error on increment limit click", t);
                    }
                });

                FormTextButton resetItemBtn = contentBox.addComponent(new FormTextButton("Auto", btnResetX, controlY, btnResetWidth, FormInputSize.SIZE_24, ButtonColor.BASE));
                resetItemBtn.setActive(configuredLimit != 0);
                resetItemBtn.onClicked(e -> {
                    try {
                        if (settings == null) return;
                        settings.setWeaponSummonLimit(itemID, 0);
                        refresh();
                    } catch (Throwable t) {
                        AutoSummonLog.error("Error on reset item limit click", t);
                    }
                });

                // Row separator line
                currentY += rowHeight;
                contentBox.addComponent(new FormBreakLine(FormBreakLine.ALIGN_BEGINNING, 8, currentY - 2, contentBox.getWidth() - 16, true));
            }

            currentY += 4;
        } catch (Throwable t) {
            AutoSummonLog.error("Error rendering category section: " + category, t);
        }
        return currentY;
    }

    /**
     * Calculate the maximum allowed summon limit for this weapon based on slots or weapon cap
     */
    public static int getMaxAllowedSummons(SummonToolItem staff, InventoryItem item, PlayerMob player) {
        if (item == null || player == null) {
            return 1;
        }
        try {
            SummonWeaponAdapter adapter = SummonCompatRegistry.getAdapter(item);
            if (adapter != null) {
                int playerMaxSlots = SummonCompatRegistry.getPlayerMaxSummonSlots(player, Collections.singletonList(item));
                return Math.max(0, adapter.getMaxAllowed(item, player, playerMaxSlots));
            }
        } catch (Throwable t) {
            AutoSummonLog.logThrottledError("gui_getMaxAllowedSummons", "Error getting max allowed summons", t, 5000);
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

        try {
            for (int i = 0; i < 10; i++) {
                InventoryItem item = player.getInv().main.getItem(i);
                if (item != null && item.item != null) {
                    SummonWeaponAdapter adapter = SummonCompatRegistry.getAdapter(item);
                    if (adapter != null) {
                        list.add(new HotbarSummonEntry(i, item, adapter));
                    }
                }
            }
        } catch (Throwable t) {
            AutoSummonLog.error("Error reading hotbar summon weapons", t);
        }
        return list;
    }

    /**
     * Refresh the form UI with updated player/settings data
     */
    public void refresh() {
        try {
            if (enableToggle != null) {
                enableToggle.checked = AutoSummonConfig.isEnabled();
            }
            populateWeaponList();
        } catch (Throwable t) {
            AutoSummonLog.error("Error refreshing AutoSummonForm", t);
        }
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
        final int maxWidth;
        int lastActiveCount = -1;
        int lastPlayerMaxSlots = -1;

        WeaponRowUI(HotbarSummonEntry entry, FormLabel statusLabel, SummonCategory category, int maxWidth) {
            this.entry = entry;
            this.statusLabel = statusLabel;
            this.category = category;
            this.maxWidth = maxWidth;
        }

        void update(PlayerMob player, int activeCount, int playerMaxSlots) {
            try {
                // Zero-allocation early exit if values haven't mutated
                if (activeCount == lastActiveCount && playerMaxSlots == lastPlayerMaxSlots) {
                    return;
                }
                lastActiveCount = activeCount;
                lastPlayerMaxSlots = playerMaxSlots;

                if (entry == null || entry.adapter == null || statusLabel == null) {
                    return;
                }

                String itemID = entry.adapter.getItemStringID();
                int configuredLimit = AutoSummonConfig.getSettings().getWeaponSummonLimit(itemID);
                if (configuredLimit == -1) {
                    statusLabel.setText("Status: Off (Disabled)", maxWidth);
                    return;
                }

                String statusText;
                int maxAllowed = entry.adapter.getMaxAllowed(entry.item, player, playerMaxSlots);
                if (category == SummonCategory.GLOBAL_SLOT) {
                    float spaceCost = entry.adapter.getSummonSpaceCost(entry.item, player);
                    String costDisplay = (spaceCost == (int) spaceCost) ? String.valueOf((int) spaceCost) : String.format("%.1f", spaceCost);
                    String costStr = (spaceCost == 1.0f) ? "1 slot" : (costDisplay + " slots");
                    if (configuredLimit > 0) {
                        statusText = "Cost: " + costStr + " | Active: " + activeCount + " (Limit: " + configuredLimit + ")";
                    } else {
                        statusText = "Cost: " + costStr + " | Active: " + activeCount + " (Max: " + maxAllowed + ")";
                    }
                } else if (category == SummonCategory.INDEPENDENT_FOLLOWER) {
                    if (configuredLimit > 0) {
                        statusText = "Cap: " + maxAllowed + " | Active: " + activeCount + " (Limit: " + configuredLimit + ")";
                    } else {
                        statusText = "Cap: " + maxAllowed + " | Active: " + activeCount + " (Independent)";
                    }
                } else {
                    if (configuredLimit > 0) {
                        statusText = "Cap: " + maxAllowed + " | Active: " + activeCount + " (Limit: " + configuredLimit + ")";
                    } else {
                        statusText = "Cap: " + maxAllowed + " | Active: " + activeCount + " (Sentry)";
                    }
                }

                statusLabel.setText(statusText, maxWidth);
            } catch (Throwable t) {
                if (statusLabel != null) {
                    statusLabel.setText("Status: Error reading details", maxWidth);
                }
            }
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
