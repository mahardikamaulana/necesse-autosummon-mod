package autosummon;

import autosummon.util.AutoSummonLog;
import necesse.engine.modLoader.annotations.ModEntry;
import necesse.engine.modLoader.ModSettings;

@ModEntry
public class AutoSummonMod {

    public void init() {
        try {
            AutoSummonLog.info("Auto Summon Mod loaded!");
            AutoSummonLog.info("Automatically summons using the rightmost staff in your hotbar.");
            
            // Initialize settings first
            AutoSummonConfig.initSettings();
            
            // Initialize the control
            AutoSummonConfig.initControl();

            // Initialize modular compatibility providers
            autosummon.compat.SummonCompatRegistry.init();
        } catch (Throwable t) {
            AutoSummonLog.error("Error during AutoSummonMod.init()", t);
        }
    }

    public void initResources() {
        // No resources needed for this mod
    }

    public void postInit() {
        try {
            autosummon.compat.SummonCompatRegistry.init();
            
            // Optional registration with CustomSettingsLib if installed
            autosummon.compat.CustomSettingsLibBridge.tryRegisterSettings(AutoSummonConfig.getSettings());

            AutoSummonLog.info("Auto Summon Mod initialization complete!");
        } catch (Throwable t) {
            AutoSummonLog.error("Error during AutoSummonMod.postInit()", t);
        }
    }
    
    /**
     * Get mod settings for saving/loading
     * This method is called by the game to persist mod settings
     */
    public ModSettings getModSettings() {
        try {
            ModSettings settings = AutoSummonConfig.getSettings();
            if (settings == null) {
                AutoSummonConfig.initSettings();
                settings = AutoSummonConfig.getSettings();
            }
            return settings;
        } catch (Throwable t) {
            AutoSummonLog.error("Error retrieving mod settings", t);
            return null;
        }
    }
}