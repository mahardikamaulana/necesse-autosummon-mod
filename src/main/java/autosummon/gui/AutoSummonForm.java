package autosummon.gui;

import autosummon.AutoSummonConfig;
import autosummon.AutoSummonModSettings;
import autosummon.patches.AutoSummonPatch;
import necesse.entity.mobs.PlayerMob;
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
 */
public class AutoSummonForm extends Form {

    public static final int FORM_WIDTH = 470;
    public static final int FORM_HEIGHT = 410;

    private final PlayerMob player;
    private FormContentBox contentBox;
    private FormLabel capacitySummaryLabel;
    private FormCheckBox enableToggle;

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

        // 1. Header & Title
        addComponent(new FormLabel("Auto Summon Configuration", new FontOptions(18), FormLabel.ALIGN_LEFT, 12, 8, getWidth() - 50));
        
        FormTextButton closeHeaderBtn = addComponent(new FormTextButton("X", getWidth() - 34, 6, 24, FormInputSize.SIZE_24, ButtonColor.BASE));
        closeHeaderBtn.onClicked(e -> AutoSummonGuiManager.closeGui());

        // 2. Master Toggle & Global Status
        AutoSummonModSettings settings = AutoSummonConfig.getSettings();
        enableToggle = addComponent(new FormCheckBox("Enable Auto Summon", 12, 36, settings.isEnabled()));
        enableToggle.onClicked(e -> settings.setEnabled(enableToggle.checked));

        capacitySummaryLabel = addComponent(new FormLabel(getCapacitySummaryText(), new FontOptions(12).color(new Color(40, 40, 40)), FormLabel.ALIGN_LEFT, 12, 60, getWidth() - 24));

        addComponent(new FormBreakLine(FormBreakLine.ALIGN_BEGINNING, 10, 80, getWidth() - 20, true));

        // 3. Scrollable List of Hotbar Summon Weapons
        contentBox = addComponent(new FormContentBox(6, 86, getWidth() - 12, getHeight() - 86 - 46));
        populateWeaponList();

        // 4. Footer Controls
        addComponent(new FormBreakLine(FormBreakLine.ALIGN_BEGINNING, 10, getHeight() - 44, getWidth() - 20, true));

        FormTextButton resetAllBtn = addComponent(new FormTextButton("Reset All Limits", 10, getHeight() - 36, 135, FormInputSize.SIZE_24, ButtonColor.BASE));
        resetAllBtn.onClicked(e -> {
            settings.clearAllWeaponLimits();
            refresh();
        });

        FormTextButton doneBtn = addComponent(new FormTextButton("Done", getWidth() - 86, getHeight() - 36, 76, FormInputSize.SIZE_24, ButtonColor.GREEN));
        doneBtn.onClicked(e -> AutoSummonGuiManager.closeGui());
    }

    /**
     * Builds summary text for player max summon capacity and live active count
     */
    private String getCapacitySummaryText() {
        if (player == null || player.buffManager == null) {
            return "Player Max Summon Capacity: -";
        }
        int maxCapacity = player.buffManager.getModifier(BuffModifiers.MAX_SUMMONS);
        int currentSlotsUsed = AutoSummonPatch.getTotalGlobalSummonSlotsUsed(player);
        return "Global Summon Slots: " + currentSlotsUsed + " / " + maxCapacity + " used";
    }

    /**
     * Scans hotbar and populates content box with weapon rows
     */
    private void populateWeaponList() {
        contentBox.clearComponents();

        List<HotbarSummonEntry> entries = getHotbarSummonWeapons();

        if (entries.isEmpty()) {
            contentBox.addComponent(new FormLabel("No summon staves found in hotbar.", new FontOptions(14).color(new Color(130, 70, 20)), FormLabel.ALIGN_LEFT, 12, 20, contentBox.getWidth() - 24));
            contentBox.addComponent(new FormLabel("Place summon weapons in hotbar slots 1-10 to customize their individual summon limits.", new FontOptions(12).color(new Color(60, 50, 40)), FormLabel.ALIGN_LEFT, 12, 44, contentBox.getWidth() - 24));
            contentBox.setContentBox(new Rectangle(0, 0, contentBox.getWidth(), 120));
            return;
        }

        AutoSummonModSettings settings = AutoSummonConfig.getSettings();
        int rowHeight = 56;
        int currentY = 4;

        for (HotbarSummonEntry entry : entries) {
            final int slot = entry.slotIndex;
            final InventoryItem item = entry.item;
            final SummonToolItem staff = entry.staff;
            final String itemID = staff.getStringID();

            // Calculate valid maximum for this weapon
            final int maxAllowed = getMaxAllowedSummons(staff, item, player);
            int configuredLimit = settings.getWeaponSummonLimit(itemID);
            if (configuredLimit > maxAllowed) {
                // If capacity decreased or limit exceeds allowable max, reset to Auto
                configuredLimit = 0;
                settings.setWeaponSummonLimit(itemID, 0);
            }

            // Native Item Icon with native hover tooltip
            contentBox.addComponent(new FormItemIcon(8, currentY + 4, item, true));

            // Name & Slot label (high contrast dark text)
            String titleText = "[Slot " + (slot + 1) + "] " + item.getItemDisplayName();
            contentBox.addComponent(new FormLabel(titleText, new FontOptions(14).color(new Color(30, 30, 30)), FormLabel.ALIGN_LEFT, 46, currentY + 2, 220));

            // Space cost & limit details
            float spaceCost = staff.getSummonSpaceTaken(item, player);
            String costDisplay = (spaceCost == (int) spaceCost) ? String.valueOf((int) spaceCost) : String.format("%.1f", spaceCost);
            int activeForStaff = AutoSummonPatch.getCurrentSummonCountFollowers(player, staff, item);

            String typeInfo = staff.drawMaxSummons 
                ? (costDisplay + " slot(s)/summon (Max: " + maxAllowed + ")") 
                : ("Follower (Max: " + maxAllowed + ")");
            String statusText = "Cost: " + typeInfo + " | Active: " + activeForStaff;
            contentBox.addComponent(new FormLabel(statusText, new FontOptions(12).color(new Color(60, 50, 40)), FormLabel.ALIGN_LEFT, 46, currentY + 22, 220));

            // Adjuster Controls: [-] [ Auto / N ] [+] [Auto] (strictly separated intervals)
            int controlY = currentY + 12;

            FormTextButton decBtn = contentBox.addComponent(new FormTextButton("-", 270, controlY, 24, FormInputSize.SIZE_24, ButtonColor.BASE));
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
            // ALIGN_MID centers text at x = 325 (midpoint between 294 and 356)
            FormLabel limitLabel = contentBox.addComponent(new FormLabel(limitText, new FontOptions(13), FormLabel.ALIGN_MID, 325, controlY + 4, 60));
            if (configuredLimit > 0) {
                limitLabel.setColor(new Color(20, 130, 20));
            } else {
                limitLabel.setColor(new Color(40, 40, 40));
            }

            FormTextButton incBtn = contentBox.addComponent(new FormTextButton("+", 356, controlY, 24, FormInputSize.SIZE_24, ButtonColor.BASE));
            incBtn.onClicked(e -> {
                int limit = settings.getWeaponSummonLimit(itemID);
                if (limit < maxAllowed) {
                    settings.setWeaponSummonLimit(itemID, limit + 1);
                }
                refresh();
            });

            FormTextButton resetItemBtn = contentBox.addComponent(new FormTextButton("Auto", 388, controlY, 46, FormInputSize.SIZE_24, ButtonColor.BASE));
            resetItemBtn.onClicked(e -> {
                settings.setWeaponSummonLimit(itemID, 0);
                refresh();
            });

            // Row separator
            currentY += rowHeight;
            contentBox.addComponent(new FormBreakLine(FormBreakLine.ALIGN_BEGINNING, 8, currentY - 4, contentBox.getWidth() - 16, true));
        }

        contentBox.setContentBox(new Rectangle(0, 0, contentBox.getWidth(), Math.max(contentBox.getHeight(), currentY + 10)));
    }

    /**
     * Calculate the maximum allowed summon limit for this weapon based on slots or weapon cap
     */
    public static int getMaxAllowedSummons(SummonToolItem staff, InventoryItem item, PlayerMob player) {
        if (staff == null || player == null) {
            return 1;
        }
        if (staff.drawMaxSummons) {
            int playerMaxSlots = player.buffManager != null 
                ? player.buffManager.getModifier(BuffModifiers.MAX_SUMMONS) : 1;
            float spaceCost = staff.getSummonSpaceTaken(item, player);
            if (spaceCost <= 0) spaceCost = 1.0f;
            return Math.max(1, (int) Math.floor(playerMaxSlots / spaceCost));
        } else {
            return Math.max(1, staff.getMaxSummons(item, player));
        }
    }

    /**
     * Reads all summon weapons currently in the player's hotbar (slots 0-9)
     */
    private List<HotbarSummonEntry> getHotbarSummonWeapons() {
        List<HotbarSummonEntry> list = new ArrayList<>();
        if (player == null || player.getInv() == null || player.getInv().main == null) {
            return list;
        }

        for (int i = 0; i < 10; i++) {
            InventoryItem item = player.getInv().main.getItem(i);
            if (item != null && item.item instanceof SummonToolItem) {
                list.add(new HotbarSummonEntry(i, item, (SummonToolItem) item.item));
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
        if (capacitySummaryLabel != null) {
            capacitySummaryLabel.setText(getCapacitySummaryText());
        }
        if (contentBox != null) {
            populateWeaponList();
        }
    }

    /**
     * Internal container holding hotbar summon weapon details
     */
    private static class HotbarSummonEntry {
        final int slotIndex;
        final InventoryItem item;
        final SummonToolItem staff;

        HotbarSummonEntry(int slotIndex, InventoryItem item, SummonToolItem staff) {
            this.slotIndex = slotIndex;
            this.item = item;
            this.staff = staff;
        }
    }
}
