package autosummon;

import autosummon.util.AutoSummonLog;

/**
 * Configuration class for the auto summon mod.
 * Modify these values to adjust the summoning behavior.
 */
public class AutoSummonConfig {
    
    // Main configuration values
    public static final long SUMMON_CHECK_COOLDOWN = 50; // ms debounce (matches 20 ticks/sec game tick rate)
    
    // Runtime toggle state - managed by persistent settings
    private static volatile AutoSummonModSettings settings;
    private static volatile boolean needsChatMessage = false;
    private static volatile String chatMessage = "";
    private static final Object CHAT_LOCK = new Object();
    
    // Control for opening auto-summon settings GUI
    public static necesse.engine.input.Control SETTINGS_CONTROL;
    
    /**
     * Initialize settings
     */
    public static synchronized void initSettings() {
        if (settings == null) {
            settings = new AutoSummonModSettings();
        }
    }
    
    /**
     * Get the settings instance, guaranteeing non-null return
     */
    public static AutoSummonModSettings getSettings() {
        if (settings == null) {
            initSettings();
        }
        return settings;
    }
    
    /**
     * Check if the mod is enabled
     */
    public static boolean isEnabled() {
        AutoSummonModSettings s = settings;
        return s != null ? s.isEnabled() : true;
    }
    
    /**
     * Toggle the mod on/off
     */
    public static void toggle() {
        AutoSummonModSettings s = getSettings();
        if (s != null) {
            s.setEnabled(!s.isEnabled());
        }
    }
    
    /**
     * Set flag that chat message is needed
     */
    public static void setNeedsChatMessage(boolean needs) {
        synchronized (CHAT_LOCK) {
            needsChatMessage = needs;
        }
    }
    
    /**
     * Set flag that chat message is needed with a specific message
     */
    public static void setNeedsChatMessage(boolean needs, String message) {
        synchronized (CHAT_LOCK) {
            needsChatMessage = needs;
            chatMessage = message != null ? message : "";
        }
    }
    
    /**
     * Set chat message
     */
    public static void setChatMessage(String message) {
        synchronized (CHAT_LOCK) {
            chatMessage = message != null ? message : "";
        }
    }
    
    /**
     * Check if chat message is needed and clear the flag
     */
    public static boolean checkAndClearNeedsChatMessage() {
        synchronized (CHAT_LOCK) {
            if (needsChatMessage) {
                needsChatMessage = false;
                return true;
            }
            return false;
        }
    }
    
    /**
     * Get the current chat message and clear it
     */
    public static String getAndClearChatMessage() {
        synchronized (CHAT_LOCK) {
            String message = chatMessage;
            chatMessage = "";
            return message != null ? message : "";
        }
    }
    
    /**
     * Initialize the control
     */
    public static void initControl() {
        try {
            SETTINGS_CONTROL = necesse.engine.input.Control.addModControl(
                new AutoSummonSettingsControl() // F9 key
            );
        } catch (Throwable t) {
            AutoSummonLog.error("Failed to register AutoSummonSettingsControl", t);
        }
    }
    
    /**
     * Get the summon check cooldown
     */
    public static long getSummonCheckCooldown() {
        return SUMMON_CHECK_COOLDOWN;
    }
}
