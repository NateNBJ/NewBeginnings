package new_beginnings.campaign;

import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.BaseCampaignPlugin;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import new_beginnings.CampaignScript;

public class CampaignPlugin extends BaseCampaignPlugin {

    @Override
    public String getId()
    {
        return "NewBeginningsCampaignPlugin";
    }

    @Override
    public boolean isTransient() { return true; }

    @Override
    public PluginPick<InteractionDialogPlugin> pickRespawnPlugin() {
        CampaignScript.setPlayerJustRespawned();
        return null;
    }
}
