package autosummon;

import autosummon.gui.AutoSummonGuiManager;
import autosummon.util.AutoSummonLog;
import necesse.engine.input.Control;
import necesse.engine.input.InputEvent;
import necesse.engine.localization.message.StaticMessage;

/**
 * Control for enabling/disabling auto summon globally.
 */
public class AutoSummonToggleControl extends Control {

    public AutoSummonToggleControl() {
        super(necesse.engine.input.InputID.KEY_F10, "autosummontoggle", new StaticMessage("Toggle Auto Summon"));
    }

    @Override
    public void activate(InputEvent event) {
        try {
            if (event != null && event.state) {
                AutoSummonGuiManager.toggleEnabled();
            }
        } catch (Throwable t) {
            AutoSummonLog.error("Error activating AutoSummonToggleControl", t);
        }
        try {
            super.activate(event);
        } catch (Throwable ignored) {}
    }
}
