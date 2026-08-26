package autosummon;

import autosummon.gui.AutoSummonGuiManager;
import autosummon.util.AutoSummonLog;
import necesse.engine.input.Control;
import necesse.engine.input.InputEvent;
import necesse.engine.localization.message.StaticMessage;

/**
 * Control for opening/toggling the auto summon configuration GUI.
 */
public class AutoSummonSettingsControl extends Control {
    
    public AutoSummonSettingsControl() {
        super(necesse.engine.input.InputID.KEY_F9, "autosummonsettings", new StaticMessage("Auto Summon Settings"));
    }
    
    @Override
    public void activate(InputEvent event) {
        try {
            if (event != null && event.state) {
                AutoSummonGuiManager.toggleGui();
            }
        } catch (Throwable t) {
            AutoSummonLog.error("Error activating AutoSummonSettingsControl", t);
        }
        try {
            super.activate(event);
        } catch (Throwable ignored) {}
    }
}

