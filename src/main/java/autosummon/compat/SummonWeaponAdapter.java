package autosummon.compat;

import autosummon.gui.AutoSummonTheme;
import necesse.entity.mobs.Mob;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.ui.GameInterfaceStyle;
import necesse.inventory.InventoryItem;

import java.awt.Color;

/**
 * Normalized adapter interface representing any summon-capable weapon,
 * allowing seamless compatibility between vanilla Necesse, SummonerExpansion,
 * and future summoner mods.
 */
public interface SummonWeaponAdapter {

    /**
     * Category classification for distinct visual separation in the GUI.
     */
    enum SummonCategory {
        GLOBAL_SLOT("SHARED SUMMON SLOT WEAPONS (Uses Global Slots)", new Color(30, 90, 180), "[SLOT]", new Color(20, 80, 170)),
        INDEPENDENT_FOLLOWER("INDEPENDENT FOLLOWER WEAPONS (0 Global Slots)", new Color(20, 130, 40), "[FOLLOWER]", new Color(20, 120, 30)),
        SENTRY_SUPPORT("SENTRY & SUPPORT WEAPONS (0 Global Slots)", new Color(215, 45, 45), "[SENTRY]", new Color(200, 35, 35));

        public final String sectionTitle;
        public final Color sectionHeaderColor;
        public final String badgeText;
        public final Color badgeColor;

        SummonCategory(String sectionTitle, Color sectionHeaderColor, String badgeText, Color badgeColor) {
            this.sectionTitle = sectionTitle;
            this.sectionHeaderColor = sectionHeaderColor;
            this.badgeText = badgeText;
            this.badgeColor = badgeColor;
        }

        /**
         * Resolves the section header color dynamically adapted to the active theme's contrast.
         */
        public Color getHeaderColor(GameInterfaceStyle style) {
            return AutoSummonTheme.getCategoryHeaderColor(this, style);
        }

        /**
         * Resolves the badge color dynamically adapted to the active theme's contrast.
         */
        public Color getBadgeColor(GameInterfaceStyle style) {
            return AutoSummonTheme.getCategoryBadgeColor(this, style);
        }
    }

    /**
     * Unique item string identifier (e.g., "skeletonstaff", "redspiderstaff")
     */
    String getItemStringID();

    /**
     * User-friendly display name of the item
     */
    String getDisplayName(InventoryItem item);

    /**
     * Whether this weapon draws from and consumes the player's global max summon capacity
     * (BuffModifiers.MAX_SUMMONS). Returns false for independent/secondary follower staves.
     */
    boolean isGlobalSlotWeapon(InventoryItem item, PlayerMob player);

    /**
     * Number of summon slots consumed per summon action.
     */
    float getSummonSpaceCost(InventoryItem item, PlayerMob player);

    /**
     * Maximum allowed active summons for this weapon.
     * For global slot weapons, this is playerMaxSummons / summonCost.
     * For secondary/follower weapons, this is the weapon's dedicated active cap.
     */
    int getMaxAllowed(InventoryItem item, PlayerMob player, int playerMaxSummons);

    /**
     * Check if a live mob in the level was spawned by this weapon.
     */
    boolean isMatchingMinion(Mob mob, InventoryItem item);

    /**
     * Check if the weapon can currently perform an attack/summon action.
     * @return null if attack can proceed, or error message string if blocked.
     */
    String canAttack(PlayerMob player, InventoryItem item);

    /**
     * Whether this weapon is a secondary summon with its own independent cap.
     */
    boolean isSecondarySummon();

    /**
     * Whether this weapon is a support/sentry summon weapon.
     */
    boolean isSupportWeapon();

    /**
     * Gets the category classification of this summon weapon.
     */
    SummonCategory getCategory(InventoryItem item, PlayerMob player);

    /**
     * Formatted description of weapon type and cost for GUI presentation.
     */
    String getWeaponTypeDisplay(InventoryItem item, PlayerMob player, int maxAllowed);
}
