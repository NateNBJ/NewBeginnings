package new_beginnings;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import lunalib.lunaSettings.LunaSettings;
import new_beginnings.campaign.CampaignPlugin;
import org.json.JSONObject;

import java.awt.*;
import java.util.MissingResourceException;

public class ModPlugin extends BaseModPlugin {
    public static final String ID = "sun_new_beginnings";
    public static final String PREFIX = "sun_nb_";
    public static final String SETTINGS_PATH = "NEW_BEGINNINGS_OPTIONS.ini";

    static final String LUNALIB_ID = "lunalib";
    static JSONObject settingsCfg = null;
    static <T> T get(String id, Class<T> type) throws Exception {
        if(Global.getSettings().getModManager().isModEnabled(LUNALIB_ID)) {
            id = PREFIX + id;

            if(type == Integer.class) return type.cast(LunaSettings.getInt(ModPlugin.ID, id));
            if(type == Float.class) return type.cast(LunaSettings.getFloat(ModPlugin.ID, id));
            if(type == Boolean.class) return type.cast(LunaSettings.getBoolean(ModPlugin.ID, id));
            if(type == Double.class) return type.cast(LunaSettings.getDouble(ModPlugin.ID, id));
            if(type == String.class) return type.cast(LunaSettings.getString(ModPlugin.ID, id));
        } else {
            if(settingsCfg == null) settingsCfg = Global.getSettings().getMergedJSONForMod(SETTINGS_PATH, ID);

            if(type == Integer.class) return type.cast(settingsCfg.getInt(id));
            if(type == Float.class) return type.cast((float) settingsCfg.getDouble(id));
            if(type == Boolean.class) return type.cast(settingsCfg.getBoolean(id));
            if(type == Double.class) return type.cast(settingsCfg.getDouble(id));
            if(type == String.class) return type.cast(settingsCfg.getString(id));
        }

        throw new MissingResourceException("No setting found with id: " + id, type.getName(), id);
    }
    static int getInt(String id) throws Exception { return get(id, Integer.class); }
    static double getDouble(String id) throws Exception { return get(id, Double.class); }
    static float getFloat(String id) throws Exception { return get(id, Float.class); }
    static boolean getBoolean(String id) throws Exception { return get(id, Boolean.class); }
    static String getString(String id) throws Exception { return get(id, String.class); }
    static boolean readSettings() throws Exception {
        RESPAWN_FLEET_VALUE_MULT = getFloat("respawnFleetValueMult");
        MAX_RESPAWN_FLEET_VALUE = getFloat("maxRespawnFleetValue");
        MIN_RESPAWN_FLEET_VALUE = getFloat("minRespawnFleetValue");
        CHANCE_TO_RESPAWN_WITH_NON_PLAYER_FACTION_FLEET_MULTIPLIER = getFloat("chanceToRespawnWithNonPlayerFactionFleetMultiplier");

        return true;
    }

    static boolean settingsAreRead = false;

    static float
            RESPAWN_FLEET_VALUE_MULT = 0.5f,
            MAX_RESPAWN_FLEET_VALUE = 1000000,
            MIN_RESPAWN_FLEET_VALUE = 15000,
            CHANCE_TO_RESPAWN_WITH_NON_PLAYER_FACTION_FLEET_MULTIPLIER = 1;

    CampaignScript script;

    void removeScripts() {
        Global.getSector().removeTransientScript(script);
        Global.getSector().removeListener(script);
    }

    @Override
    public void beforeGameSave() {
        try {
            Saved.updatePersistentData();
            removeScripts();
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
            removeScripts();

            Global.getSector().registerPlugin(new CampaignPlugin());
            Global.getSector().addTransientScript(script = new CampaignScript());

            Saved.loadPersistentData();

            if(Global.getSettings().getModManager().isModEnabled(LUNALIB_ID)) {
                LunaSettingsChangedListener.addToManagerIfNeeded();
            }

            readSettingsIfNecessary();
        } catch (Exception e) { reportCrash(e); }
    }

    static boolean readSettingsIfNecessary() {
        try {
            if(settingsAreRead) return true;

            readSettings();

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
