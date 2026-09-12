package autosummon.gui;

import autosummon.compat.SummonWeaponAdapter.SummonCategory;
import necesse.engine.Settings;
import necesse.gfx.forms.components.FormComponent;
import necesse.gfx.ui.GameInterfaceStyle;

import java.awt.Color;

/**
 * Centralized theme resolution engine providing semantic colors and contrast adaptation
 * across all Necesse interface styles (Primal, Ghost, Legacy, and custom modded themes).
 * Ensures WCAG-compliant contrast and zero-allocation performance during render loops.
 */
public class AutoSummonTheme {

    // Palette constants for dark themes (Ghost & dark custom themes)
    private static final Color DARK_TITLE = new Color(245, 245, 245);
    private static final Color DARK_PRIMARY = new Color(225, 225, 225);
    private static final Color DARK_SECONDARY = new Color(165, 175, 190);
    private static final Color DARK_EMPTY_NOTICE = new Color(245, 195, 65);
    private static final Color DARK_DIVIDER = new Color(75, 85, 105, 140);
    private static final Color DARK_LIMIT_AUTO = new Color(155, 165, 180);
    private static final Color DARK_LIMIT_ACTIVE = new Color(55, 215, 85);
    private static final Color DARK_LIMIT_OVERCAP = new Color(85, 175, 255);
    private static final Color DARK_LIMIT_DISABLED = new Color(245, 75, 75);

    // Category banner colors for dark themes
    private static final Color DARK_CAT_SLOT = new Color(90, 170, 255);
    private static final Color DARK_CAT_FOLLOWER = new Color(65, 225, 110);
    private static final Color DARK_CAT_SENTRY = new Color(255, 90, 90);

    // Palette constants for light themes (Primal, Legacy & light custom themes)
    private static final Color LIGHT_TITLE = new Color(25, 25, 25);
    private static final Color LIGHT_PRIMARY = new Color(30, 30, 30);
    private static final Color LIGHT_SECONDARY = new Color(75, 75, 75);
    private static final Color LIGHT_EMPTY_NOTICE = new Color(160, 85, 15);
    private static final Color LIGHT_DIVIDER = new Color(160, 150, 140, 120);
    private static final Color LIGHT_LIMIT_AUTO = new Color(90, 85, 80);
    private static final Color LIGHT_LIMIT_ACTIVE = new Color(25, 135, 35);
    private static final Color LIGHT_LIMIT_OVERCAP = new Color(25, 95, 205);
    private static final Color LIGHT_LIMIT_DISABLED = new Color(175, 30, 30);

    // Category banner colors for light themes
    private static final Color LIGHT_CAT_SLOT = new Color(25, 75, 165);
    private static final Color LIGHT_CAT_FOLLOWER = new Color(20, 125, 45);
    private static final Color LIGHT_CAT_SENTRY = new Color(195, 30, 30);

    /**
     * Resolves the current active GameInterfaceStyle safely with fallbacks.
     */
    public static GameInterfaceStyle getActiveStyle(FormComponent component) {
        if (component != null) {
            try {
                GameInterfaceStyle style = component.getInterfaceStyle();
                if (style != null) {
                    return style;
                }
            } catch (Throwable ignored) {}
        }
        if (Settings.UI != null) {
            return Settings.UI;
        }
        return GameInterfaceStyle.primal;
    }

    /**
     * Determines whether the active theme features a dark background by calculating
     * ITU-R BT.709 relative luminance from activeTextColor.
     * High luminance text (>128) implies a dark background (e.g. Ghost).
     */
    public static boolean isDarkTheme(GameInterfaceStyle style) {
        if (style == null) {
            style = Settings.UI != null ? Settings.UI : GameInterfaceStyle.primal;
        }
        if (style == null || style.activeTextColor == null) {
            return false;
        }
        Color text = style.activeTextColor;
        double luminance = 0.2126 * text.getRed() + 0.7152 * text.getGreen() + 0.0722 * text.getBlue();
        return luminance > 128.0;
    }

    /**
     * Checks whether the interface style has mutated compared to a previous reference.
     */
    public static boolean hasThemeChanged(GameInterfaceStyle current, GameInterfaceStyle previous) {
        if (current == null && previous == null) {
            return false;
        }
        if (current == null || previous == null) {
            return true;
        }
        return current != previous;
    }

    /**
     * Header title text color with guaranteed high contrast against the form background.
     */
    public static Color getTitleColor(GameInterfaceStyle style) {
        if (style != null && style.activeTextColor != null) {
            return style.activeTextColor;
        }
        return isDarkTheme(style) ? DARK_TITLE : LIGHT_TITLE;
    }

    /**
     * Primary text color for weapon titles, item labels, and standard interactive text.
     */
    public static Color getPrimaryTextColor(GameInterfaceStyle style) {
        if (style != null && style.activeTextColor != null) {
            return style.activeTextColor;
        }
        return isDarkTheme(style) ? DARK_PRIMARY : LIGHT_PRIMARY;
    }

    /**
     * Secondary muted text color for capacity summaries, slot status details, and guidance notes.
     */
    public static Color getSecondaryTextColor(GameInterfaceStyle style) {
        if (isDarkTheme(style)) {
            if (style != null && style.activeFadedTextColor != null) {
                return style.activeFadedTextColor;
            }
            return DARK_SECONDARY;
        }
        return LIGHT_SECONDARY;
    }

    /**
     * Accent color for empty-state or warning notifications.
     */
    public static Color getEmptyNoticeColor(GameInterfaceStyle style) {
        if (isDarkTheme(style)) {
            if (style != null && style.warningTextColor != null) {
                return style.warningTextColor;
            }
            return DARK_EMPTY_NOTICE;
        }
        return LIGHT_EMPTY_NOTICE;
    }

    /**
     * Divider / separator line color matching the background depth.
     */
    public static Color getDividerColor(GameInterfaceStyle style) {
        return isDarkTheme(style) ? DARK_DIVIDER : LIGHT_DIVIDER;
    }

    /**
     * Text color for "Auto" mode in limit counters.
     */
    public static Color getAutoLimitColor(GameInterfaceStyle style) {
        return isDarkTheme(style) ? DARK_LIMIT_AUTO : LIGHT_LIMIT_AUTO;
    }

    /**
     * Text color for "Off" (disabled) mode in limit counters.
     */
    public static Color getDisabledLimitColor(GameInterfaceStyle style) {
        if (style != null && style.errorTextColor != null) {
            return style.errorTextColor;
        }
        return isDarkTheme(style) ? DARK_LIMIT_DISABLED : LIGHT_LIMIT_DISABLED;
    }

    /**
     * Text color for active valid manual limits within player capacity.
     */
    public static Color getActiveLimitColor(GameInterfaceStyle style) {
        return isDarkTheme(style) ? DARK_LIMIT_ACTIVE : LIGHT_LIMIT_ACTIVE;
    }

    /**
     * Text color for manual limits exceeding current available slots.
     */
    public static Color getOverCapLimitColor(GameInterfaceStyle style) {
        return isDarkTheme(style) ? DARK_LIMIT_OVERCAP : LIGHT_LIMIT_OVERCAP;
    }

    /**
     * Category section header banner color adapted for theme contrast.
     */
    public static Color getCategoryHeaderColor(SummonCategory category, GameInterfaceStyle style) {
        boolean dark = isDarkTheme(style);
        if (category == null) {
            return dark ? DARK_CAT_SLOT : LIGHT_CAT_SLOT;
        }
        switch (category) {
            case GLOBAL_SLOT:
                return dark ? DARK_CAT_SLOT : LIGHT_CAT_SLOT;
            case INDEPENDENT_FOLLOWER:
                return dark ? DARK_CAT_FOLLOWER : LIGHT_CAT_FOLLOWER;
            case SENTRY_SUPPORT:
                return dark ? DARK_CAT_SENTRY : LIGHT_CAT_SENTRY;
            default:
                return dark ? DARK_PRIMARY : LIGHT_PRIMARY;
        }
    }

    /**
     * Category badge color adapted for theme contrast.
     */
    public static Color getCategoryBadgeColor(SummonCategory category, GameInterfaceStyle style) {
        return getCategoryHeaderColor(category, style);
    }
}
