package autosummon.gui;

import autosummon.compat.SummonWeaponAdapter.SummonCategory;
import necesse.gfx.ui.GameInterfaceStyle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AutoSummonTheme contrast validation, dynamic classification,
 * semantic color mapping, and fail-safe fallbacks across all Necesse themes.
 */
public class AutoSummonThemeTest {

    private double calculateLuminance(Color color) {
        if (color == null) return 0.0;
        return 0.2126 * color.getRed() + 0.7152 * color.getGreen() + 0.0722 * color.getBlue();
    }

    @Test
    @DisplayName("isDarkTheme should correctly identify dark vs light backgrounds based on active text luminance")
    public void testThemeLuminanceClassification() {
        // Ghost has light text (rgb(200,200,200)) indicating a dark slate/ethereal background
        if (GameInterfaceStyle.ghost != null) {
            assertTrue(AutoSummonTheme.isDarkTheme(GameInterfaceStyle.ghost), "Ghost theme should be classified as dark");
        }

        // Primal has dark text (rgb(20,20,20)) indicating a light parchment/stone background
        if (GameInterfaceStyle.primal != null) {
            assertFalse(AutoSummonTheme.isDarkTheme(GameInterfaceStyle.primal), "Primal theme should be classified as light");
        }

        // Legacy has dark text (rgb(35,35,35)) indicating a light parchment background
        if (GameInterfaceStyle.legacy != null) {
            assertFalse(AutoSummonTheme.isDarkTheme(GameInterfaceStyle.legacy), "Legacy theme should be classified as light");
        }

        // Null style should safely fall back to false without crashing
        assertFalse(AutoSummonTheme.isDarkTheme(null), "Null style should safely return false");
    }

    @Test
    @DisplayName("hasThemeChanged should accurately detect style reference mutations")
    public void testThemeChangeDetection() {
        assertFalse(AutoSummonTheme.hasThemeChanged(GameInterfaceStyle.primal, GameInterfaceStyle.primal));
        assertFalse(AutoSummonTheme.hasThemeChanged(GameInterfaceStyle.ghost, GameInterfaceStyle.ghost));
        assertFalse(AutoSummonTheme.hasThemeChanged(null, null));

        assertTrue(AutoSummonTheme.hasThemeChanged(GameInterfaceStyle.primal, GameInterfaceStyle.ghost));
        assertTrue(AutoSummonTheme.hasThemeChanged(GameInterfaceStyle.ghost, GameInterfaceStyle.legacy));
        assertTrue(AutoSummonTheme.hasThemeChanged(GameInterfaceStyle.primal, null));
        assertTrue(AutoSummonTheme.hasThemeChanged(null, GameInterfaceStyle.primal));
    }

    @Test
    @DisplayName("Semantic text colors should be non-null and maintain high contrast across themes")
    public void testSemanticColorsAcrossThemes() {
        GameInterfaceStyle[] styles = new GameInterfaceStyle[] {
            GameInterfaceStyle.primal,
            GameInterfaceStyle.ghost,
            GameInterfaceStyle.legacy,
            null
        };

        for (GameInterfaceStyle style : styles) {
            Color title = AutoSummonTheme.getTitleColor(style);
            Color primary = AutoSummonTheme.getPrimaryTextColor(style);
            Color secondary = AutoSummonTheme.getSecondaryTextColor(style);
            Color emptyNotice = AutoSummonTheme.getEmptyNoticeColor(style);
            Color divider = AutoSummonTheme.getDividerColor(style);
            Color autoLimit = AutoSummonTheme.getAutoLimitColor(style);
            Color disabledLimit = AutoSummonTheme.getDisabledLimitColor(style);
            Color activeLimit = AutoSummonTheme.getActiveLimitColor(style);
            Color overCapLimit = AutoSummonTheme.getOverCapLimitColor(style);

            assertNotNull(title, "Title color must not be null");
            assertNotNull(primary, "Primary text color must not be null");
            assertNotNull(secondary, "Secondary text color must not be null");
            assertNotNull(emptyNotice, "Empty notice color must not be null");
            assertNotNull(divider, "Divider color must not be null");
            assertNotNull(autoLimit, "Auto limit color must not be null");
            assertNotNull(disabledLimit, "Disabled limit color must not be null");
            assertNotNull(activeLimit, "Active limit color must not be null");
            assertNotNull(overCapLimit, "Over-cap limit color must not be null");

            boolean isDark = AutoSummonTheme.isDarkTheme(style);
            if (isDark) {
                // In dark mode, text colors should be bright (high luminance) to ensure readability
                assertTrue(calculateLuminance(title) > 120.0, "Dark theme title must have high luminance");
                assertTrue(calculateLuminance(primary) > 120.0, "Dark theme primary text must have high luminance");
                assertTrue(calculateLuminance(activeLimit) > 100.0, "Dark theme active limit must have high luminance");
            } else {
                // In light mode, text colors should be dark (low luminance) to ensure contrast against parchment
                assertTrue(calculateLuminance(title) < 140.0, "Light theme title must have low luminance");
                assertTrue(calculateLuminance(primary) < 140.0, "Light theme primary text must have low luminance");
                assertTrue(calculateLuminance(activeLimit) < 140.0, "Light theme active limit must have low luminance");
            }
        }
    }

    @Test
    @DisplayName("Category header and badge colors should adapt cleanly to theme contrast")
    public void testCategoryColorsAdaptation() {
        for (SummonCategory cat : SummonCategory.values()) {
            Color darkHeader = AutoSummonTheme.getCategoryHeaderColor(cat, GameInterfaceStyle.ghost);
            Color lightHeader = AutoSummonTheme.getCategoryHeaderColor(cat, GameInterfaceStyle.primal);

            assertNotNull(darkHeader, "Dark header color must not be null for " + cat);
            assertNotNull(lightHeader, "Light header color must not be null for " + cat);
            assertNotEquals(darkHeader, lightHeader, "Dark and light header colors should be distinct for contrast for " + cat);

            // Verify enum delegation methods match AutoSummonTheme
            assertEquals(darkHeader, cat.getHeaderColor(GameInterfaceStyle.ghost));
            assertEquals(lightHeader, cat.getHeaderColor(GameInterfaceStyle.primal));

            // Verify badge colors match header colors
            assertEquals(darkHeader, cat.getBadgeColor(GameInterfaceStyle.ghost));
            assertEquals(lightHeader, cat.getBadgeColor(GameInterfaceStyle.primal));

            // Verify dark mode headers have brighter tone than light mode headers
            assertTrue(calculateLuminance(darkHeader) > calculateLuminance(lightHeader),
                "Dark theme header should have higher luminance than light theme header for " + cat);
        }

        // Null category fallback check
        assertNotNull(AutoSummonTheme.getCategoryHeaderColor(null, GameInterfaceStyle.ghost));
        assertNotNull(AutoSummonTheme.getCategoryHeaderColor(null, GameInterfaceStyle.primal));
    }

    @Test
    @DisplayName("getActiveStyle should handle null component gracefully")
    public void testGetActiveStyleNullSafety() {
        GameInterfaceStyle style = AutoSummonTheme.getActiveStyle(null);
        assertNotNull(style, "getActiveStyle must never return null");
    }
}
