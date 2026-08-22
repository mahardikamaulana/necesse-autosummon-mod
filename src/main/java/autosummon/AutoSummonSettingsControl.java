package autosummon;

import autosummon.gui.AutoSummonGuiManager;
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
        if (event.state) {
            AutoSummonGuiManager.toggleGui();
        }
        super.activate(event);
    }
}

