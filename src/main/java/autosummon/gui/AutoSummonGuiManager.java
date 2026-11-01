package autosummon.gui;

import autosummon.AutoSummonConfig;
import autosummon.util.AutoSummonLog;
import necesse.engine.GlobalData;
import necesse.engine.state.MainGame;
import necesse.engine.window.WindowManager;
import necesse.entity.mobs.PlayerMob;

/**
 * Manager handling the opening, closing, and toggling of the native AutoSummonForm GUI.
 * Hardened against state transitions, window nulls, and component lifecycle errors.
 */
public class AutoSummonGuiManager {

    private static AutoSummonForm activeForm;

    /**
     * Toggles the Auto Summon configuration GUI
     */
    public static void toggleGui() {
        try {
            if (!(GlobalData.getCurrentState() instanceof MainGame)) {
                return;
            }

            MainGame mainGame = (MainGame) GlobalData.getCurrentState();
            if (mainGame == null || mainGame.getClient() == null) {
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
        } catch (Throwable t) {
            AutoSummonLog.error("Error toggling AutoSummon GUI", t);
        }
    }

    /**
     * Toggles auto summon globally, announces the new state in chat and syncs the open GUI
     */
    public static void toggleEnabled() {
        try {
            if (!(GlobalData.getCurrentState() instanceof MainGame)) {
                return;
            }

            MainGame mainGame = (MainGame) GlobalData.getCurrentState();
            if (mainGame == null || mainGame.getClient() == null) {
                return;
            }

            AutoSummonConfig.toggle();

            if (mainGame.getClient().chat != null) {
                mainGame.getClient().chat.addMessage("[Auto Summon] " + (AutoSummonConfig.isEnabled() ? "ON" : "OFF"));
            }
            if (isGuiOpen(mainGame)) {
                activeForm.refresh();
            }
        } catch (Throwable t) {
            AutoSummonLog.error("Error toggling auto summon", t);
        }
    }

    /**
     * Open or display the Auto Summon form
     */
    public static void openGui(MainGame mainGame, PlayerMob player) {
        if (mainGame == null || player == null || mainGame.formManager == null) {
            return;
        }

        try {
            if (activeForm == null) {
                activeForm = new AutoSummonForm(player);
            } else {
                activeForm.refresh();
            }

            if (WindowManager.getWindow() != null) {
                activeForm.setPosMiddle(
                    WindowManager.getWindow().getHudWidth() / 2,
                    WindowManager.getWindow().getHudHeight() / 2
                );
            }

            if (!mainGame.formManager.hasComponent(activeForm)) {
                mainGame.formManager.addComponent(activeForm);
            }
            activeForm.setHidden(false);
        } catch (Throwable t) {
            AutoSummonLog.error("Error opening AutoSummon GUI", t);
            closeGui(mainGame);
        }
    }

    /**
     * Close and remove the Auto Summon form
     */
    public static void closeGui() {
        try {
            if (GlobalData.getCurrentState() instanceof MainGame) {
                closeGui((MainGame) GlobalData.getCurrentState());
            } else if (activeForm != null) {
                activeForm = null;
            }
        } catch (Throwable t) {
            AutoSummonLog.error("Error closing AutoSummon GUI", t);
            activeForm = null;
        }
    }

    /**
     * Close the form on a specific MainGame instance
     */
    public static void closeGui(MainGame mainGame) {
        try {
            if (activeForm != null) {
                if (mainGame != null && mainGame.formManager != null) {
                    mainGame.formManager.removeComponent(activeForm);
                }
                activeForm = null;
            }
        } catch (Throwable t) {
            AutoSummonLog.error("Error closing active AutoSummon form", t);
            activeForm = null;
        }
    }

    /**
     * Check if the GUI is currently open
     */
    public static boolean isGuiOpen(MainGame mainGame) {
        try {
            return activeForm != null && mainGame != null && mainGame.formManager != null 
                && mainGame.formManager.hasComponent(activeForm) && !activeForm.isHidden();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Notify the active GUI that a summon event occurred so it can update counters immediately
     */
    public static void notifySummonChanged() {
        try {
            if (activeForm != null && !activeForm.isHidden()) {
                activeForm.markSnapshotDirty();
            }
        } catch (Throwable ignored) {}
    }
}
