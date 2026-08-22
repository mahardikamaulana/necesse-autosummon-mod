package autosummon.gui;

import necesse.engine.GlobalData;
import necesse.engine.state.MainGame;
import necesse.engine.window.WindowManager;
import necesse.entity.mobs.PlayerMob;

/**
 * Manager handling the opening, closing, and toggling of the native AutoSummonForm GUI.
 */
public class AutoSummonGuiManager {

    private static AutoSummonForm activeForm;

    /**
     * Toggles the Auto Summon configuration GUI
     */
    public static void toggleGui() {
        if (!(GlobalData.getCurrentState() instanceof MainGame)) {
            return;
        }

        MainGame mainGame = (MainGame) GlobalData.getCurrentState();
        if (mainGame.getClient() == null) {
            return;
        }

        PlayerMob player = mainGame.getClient().getPlayer();
        if (player == null) {
            return;
        }

        if (isGuiOpen(mainGame)) {
            closeGui(mainGame);
        } else {
            openGui(mainGame, player);
        }
    }

    /**
     * Open or display the Auto Summon form
     */
    public static void openGui(MainGame mainGame, PlayerMob player) {
        if (activeForm == null) {
            activeForm = new AutoSummonForm(player);
        } else {
            activeForm.refresh();
        }

        activeForm.setPosMiddle(
            WindowManager.getWindow().getHudWidth() / 2,
            WindowManager.getWindow().getHudHeight() / 2
        );

        if (!mainGame.formManager.hasComponent(activeForm)) {
            mainGame.formManager.addComponent(activeForm);
        }
        activeForm.setHidden(false);
    }

    /**
     * Close and remove the Auto Summon form
     */
    public static void closeGui() {
        if (GlobalData.getCurrentState() instanceof MainGame) {
            closeGui((MainGame) GlobalData.getCurrentState());
        }
    }

    /**
     * Close the form on a specific MainGame instance
     */
    public static void closeGui(MainGame mainGame) {
        if (activeForm != null) {
            if (mainGame != null && mainGame.formManager != null) {
                mainGame.formManager.removeComponent(activeForm);
            }
            activeForm = null;
        }
    }

    /**
     * Check if the GUI is currently open
     */
    public static boolean isGuiOpen(MainGame mainGame) {
        return activeForm != null && mainGame != null && mainGame.formManager != null 
            && mainGame.formManager.hasComponent(activeForm) && !activeForm.isHidden();
    }
}
