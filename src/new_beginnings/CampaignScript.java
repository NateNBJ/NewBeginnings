package new_beginnings;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.fleets.DefaultFleetInflater;
import com.fs.starfarer.api.impl.campaign.fleets.DefaultFleetInflaterParams;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.json.JSONArray;
import org.json.JSONObject;

import java.awt.*;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class CampaignScript extends BaseCampaignEventListener implements EveryFrameScript {
    static void log(String message) { if(true) Global.getLogger(CampaignScript.class).info(message); }

    static final float FLEET_VALUE_NEEDS_REASSESMENT_FLAG = Float.MIN_VALUE;
    static final String ALLOWED_FACTIONS_FILE_PATH = "data/config/newbeginnings/allowed_factions.json";

    static JSONObject allowedFactions = null;
    static boolean playerJustRespawned = false;

    Saved<Float> startFleetValue = new Saved("startFleetValue", FLEET_VALUE_NEEDS_REASSESMENT_FLAG);
    Saved<Float> fleetLosses = new Saved("fleetLosses", 0f);

    CampaignFleetAPI pf;
    Random random = new Random();
    float readAllowedFactionsDelay = 1f;

    public CampaignScript() { super(true); }
    
    @Override
    public void advance(float amount) {
        try {
            if(!ModPlugin.readSettingsIfNecessary()) return;

            pf = Global.getSector().getPlayerFleet();

            if (pf == null) return;

            if (playerJustRespawned) {
                createNewRespawnFleet();
                playerJustRespawned = false;
            }

            if (startFleetValue.val == FLEET_VALUE_NEEDS_REASSESMENT_FLAG) {
                startFleetValue.val = getFleetValue();
            }

            if (allowedFactions == null
                    && !Global.getSector().isInNewGameAdvance()
                    && !Global.getSector().getCampaignUI().isShowingDialog()
                    && (readAllowedFactionsDelay -= amount) < 0) {

                try {
                    allowedFactions = Global.getSettings().getMergedJSONForMod(ALLOWED_FACTIONS_FILE_PATH, "sun_new_beginnings");
                } catch (Exception e) {
                    ModPlugin.reportCrash(e);
                    allowedFactions = Global.getSettings().loadJSON(ALLOWED_FACTIONS_FILE_PATH, "sun_new_beginnings");
                }
            }
        } catch (Exception e) { ModPlugin.reportCrash(e);}
    }
    
    void createNewRespawnFleet() throws Exception {
        pf.getFleetData().clear();
        assessFleetValueChange();

        float newFleetValueTarget = fleetLosses.val * ModPlugin.RESPAWN_FLEET_VALUE_MULT;
        newFleetValueTarget = Math.min(newFleetValueTarget, ModPlugin.MAX_RESPAWN_FLEET_VALUE);
        newFleetValueTarget = Math.max(newFleetValueTarget, ModPlugin.MIN_RESPAWN_FLEET_VALUE);

        float fp = 2 + newFleetValueTarget / 1000;
        int sizeOfAllPlayerMarkets = 0;
        int requiredCrew = 0;
        int attempts = 0;
        float newFleetValue = 0;

        String type = FleetTypes.PATROL_SMALL;
        if (fp > 150) type = FleetTypes.PATROL_MEDIUM;
        if (fp > 300) type = FleetTypes.PATROL_LARGE;

        WeightedRandomPicker<String> factionPicker = new WeightedRandomPicker();

        for(MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if(market.isPlayerOwned()) sizeOfAllPlayerMarkets += market.getSize();
        }

        factionPicker.add(Global.getSector().getPlayerFaction().getId(), 1 + sizeOfAllPlayerMarkets * 0.25f);

        for(FactionAPI faction : Global.getSector().getAllFactions()) {
            if(faction.isShowInIntelTab() && allowedFactions.has(faction.getId())) {
                factionPicker.add(faction.getId(), (1 + faction.getRelToPlayer().getRel())
                        * ModPlugin.CHANCE_TO_RESPAWN_WITH_NON_PLAYER_FACTION_FLEET_MULTIPLIER);
            }
        }

        FleetParamsV3 params = new FleetParamsV3(pf.getLocation(), "player", 1f, type, fp, 0f, 0f, 0f, 0f, 0f, 0f);
        params.withOfficers = false;
        params.random = random;
        params.mode = FactionAPI.ShipPickMode.PRIORITY_THEN_ALL;

        DefaultFleetInflaterParams inflaterParams = new DefaultFleetInflaterParams();
        inflaterParams.allWeapons = true;
        inflaterParams.mode = FactionAPI.ShipPickMode.PRIORITY_THEN_ALL;
        inflaterParams.persistent = false;

        do {
            pf.getFleetData().clear();

            params.factionId = factionPicker.pick();

            log("Creating player respawn fleet of " + params.factionId + " faction worth $" + newFleetValueTarget);

            newFleetValue = 0;
            requiredCrew = 0;
            inflaterParams.quality = Math.min(1, 0.2f + random.nextFloat() * 0.8f + sizeOfAllPlayerMarkets * 0.05f);
            params.qualityOverride = inflaterParams.quality;
            CampaignFleetAPI newFleet = FleetFactoryV3.createFleet(params);
            List<FleetMemberAPI> newShipPool = newFleet.getFleetData().getMembersListCopy();
            Set<String> dissallowedShips = new HashSet();
            JSONArray dasJsonArray = allowedFactions.getJSONArray(params.factionId);

            if(dasJsonArray == null) continue;
            else for(int i = 0; i < dasJsonArray.length(); i++) dissallowedShips.add(dasJsonArray.getString(i));

            while (!newShipPool.isEmpty() && pf.getFleetData().getNumMembers() < Global.getSettings().getInt("maxShipsInFleet")) {
                int newShipIndex = random.nextInt(newShipPool.size());
                FleetMemberAPI newShip = newShipPool.get(newShipIndex);

                if(newShip.getHullSpec().getHints().contains(ShipHullSpecAPI.ShipTypeHints.UNBOARDABLE)
                        || dissallowedShips.contains(newShip.getHullId())) {

                    newShipPool.remove(newShipIndex);
                    continue;
                }

                float shipValue = getShipValue(newShip);

                if (newFleetValue + shipValue < newFleetValueTarget) {
                    pf.getFleetData().addFleetMember(newShip);
                    newShipPool.remove(newShipIndex);
                    requiredCrew += newShip.getMinCrew();
                    newFleetValue += shipValue;
                } else if (newFleetValue < 0.95f * newFleetValueTarget - 1000) {
                    newShipPool.remove(newShipIndex);
                } else break;
            }

            log("left:" + newShipPool.size() + "    dif:" + (newFleetValueTarget - newFleetValue));
        } while(newFleetValue < (0.8f * newFleetValueTarget - 1000) && attempts++ < 15);

        if(pf.getFleetData().getNumMembers() == 0) {
            pf.getFleetData().addFleetMember("hound_d_Standard");
            requiredCrew = 10;
        }

        DefaultFleetInflater inflater = new DefaultFleetInflater(inflaterParams);
        inflater.inflate(pf);

        pf.getFleetData().sort();
        pf.getFleetData().getMembersInPriorityOrder().get(0).setFlagship(true);
        pf.getFleetData().updateCargoCapacities();
        pf.getCargo().clear();
        pf.getCargo().addSupplies(pf.getCargo().getMaxCapacity() * 0.2f);
        pf.getCargo().addFuel(pf.getCargo().getMaxFuel() * 0.2f);
        pf.getCargo().addCrew((int)(requiredCrew * 1.1f));
        pf.getFleetData().syncIfNeeded();

        for(FleetMemberAPI ship : pf.getFleetData().getMembersListCopy()) {
            ship.getRepairTracker().setCR(ship.getRepairTracker().getMaxCR());
        }

        startFleetValue.val = getFleetValue();
        fleetLosses.val = newFleetValueTarget - startFleetValue.val;
    }

    public static void setPlayerJustRespawned() { playerJustRespawned = true; }
    
    public void assessFleetValueChange() {
        float endFleetValue = getFleetValue(); // Upon arriving at any station

        fleetLosses.val = Math.max(0, fleetLosses.val + startFleetValue.val - endFleetValue);

        if(Global.getSettings().isDevMode()) {
            log("Fleet value change: " + (endFleetValue - startFleetValue.val));
            log("Fleet losses since last wipe: " + fleetLosses.val);
            log("----------");

            Global.getSector().getCampaignUI().addMessage("Fleet value change: " + (endFleetValue - startFleetValue.val), Color.WHITE);
        }

        startFleetValue.val = FLEET_VALUE_NEEDS_REASSESMENT_FLAG;
    }

    public float getFleetValue() {
        float value = 0;

        for(FleetMemberAPI ship : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
            float shipValue = getShipValue(ship);
            // getBaseValue confirmed in-game to include fitted weapons, but not dMods
            // getPermaMods().size() returned 0 for a hyperion in spite of permanent high-maintenance mod
            if(Global.getSettings().isDevMode()) log(ship.getHullId() + " : " + shipValue);

            value += shipValue;
        }

        if(Global.getSettings().isDevMode()) log("Total fleet value: " + value);

        return value;
    }

    public float getShipValue(FleetMemberAPI ship) {
        int dMods = 0;

        for(String id : ship.getVariant().getPermaMods()) {
            dMods += Global.getSettings().getHullModSpec(id).hasTag("dmod") ? 1 : 0;
        }

        return ship.getBaseValue() * (float)Math.pow(0.8f, dMods);
    }
    
    @Override
    public boolean isDone() { return false; }

    @Override
    public boolean runWhilePaused() { return allowedFactions == null || !ModPlugin.settingsAreRead; }

    @Override
    public void reportPlayerOpenedMarket(MarketAPI market) {
        try {
            if(startFleetValue.val != FLEET_VALUE_NEEDS_REASSESMENT_FLAG) {
                assessFleetValueChange();
            }
        } catch (Exception e) { ModPlugin.reportCrash(e); }

        super.reportPlayerOpenedMarket(market);
    }
}