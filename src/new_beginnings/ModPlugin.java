package new_beginnings;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import new_beginnings.campaign.CampaignPlugin;
import org.json.JSONObject;

import java.awt.*;

public class ModPlugin extends BaseModPlugin {
    public static final String SETTINGS_PATH = "NEW_BEGINNINGS_OPTIONS.ini";

    static boolean settingsAreRead = false;

    static float
            RESPAWN_FLEET_VALUE_MULT = 0.5f,
            MAX_RESPAWN_FLEET_VALUE = 1000000,
            MIN_RESPAWN_FLEET_VALUE = 15000,
            CHANCE_TO_RESPAWN_WITH_NON_PLAYER_FACTION_FLEET_MULTIPLIER = 1;

    CampaignScript script;

    @Override
    public void beforeGameSave() {
        try {
            Saved.updatePersistentData();
            Global.getSector().removeTransientScript(script);
            Global.getSector().removeListener(script);
        } catch (Exception e) { reportCrash(e); }
    }

    @Override
    public void afterGameSave() {
        try {
            Global.getSector().addTransientScript(script = new CampaignScript());

            Saved.loadPersistentData(); // Because script attributes will be reset
        } catch (Exception e) { reportCrash(e); }
    }

    @Override
    public void onGameLoad(boolean newGame) {
        try {
            Global.getSector().registerPlugin(new CampaignPlugin());
            Global.getSector().addTransientScript(script = new CampaignScript());

            Saved.loadPersistentData();

            readSettingsIfNecessary();
        } catch (Exception e) { reportCrash(e); }
    }

    static boolean readSettingsIfNecessary() {
        try {
            if(settingsAreRead) return true;

            JSONObject cfg = Global.getSettings().loadJSON(SETTINGS_PATH);
            RESPAWN_FLEET_VALUE_MULT = (float) cfg.getDouble("respawnFleetValueMult");
            MAX_RESPAWN_FLEET_VALUE = (float) cfg.getDouble("maxRespawnFleetValue");
            MIN_RESPAWN_FLEET_VALUE = (float) cfg.getDouble("minRespawnFleetValue");
            CHANCE_TO_RESPAWN_WITH_NON_PLAYER_FACTION_FLEET_MULTIPLIER = (float) cfg.getDouble("chanceToRespawnWithNonPlayerFactionFleetMultiplier");

            return settingsAreRead = true;
        } catch (Exception e) {
            return settingsAreRead = reportCrash(e);
        }
    }

    public static boolean reportCrash(Exception exception) {
        try {
            String stackTrace = "", message = "New Beginnings encountered an error!\nPlease let the mod author know.";

            for(int i = 0; i < exception.getStackTrace().length; i++) {
                stackTrace += "    " + exception.getStackTrace()[i].toString() + System.lineSeparator();
            }

            Global.getLogger(ModPlugin.class).error(exception.getMessage() + System.lineSeparator() + stackTrace);

            if (Global.getCombatEngine() != null && Global.getCurrentState() == GameState.COMBAT) {
                Global.getCombatEngine().getCombatUI().addMessage(1, Color.ORANGE, exception.getMessage());
                Global.getCombatEngine().getCombatUI().addMessage(2, Color.RED, message);
            } else if (Global.getSector() != null) {
                Global.getSector().getCampaignUI().addMessage(message, Color.RED);
                Global.getSector().getCampaignUI().addMessage(exception.getMessage(), Color.ORANGE);
                Global.getSector().getCampaignUI().showConfirmDialog(message + "\n\n"
                        + exception.getMessage(), "Ok", null, null, null);
            } else return false;

            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
