package nc.randomEvents;

import org.bukkit.plugin.java.JavaPlugin;

import nc.randomEvents.commands.CommandManager;
import nc.randomEvents.services.EventManager;
import nc.randomEvents.services.DataManager;
import nc.randomEvents.services.RewardGenerator;
import nc.randomEvents.services.ConfigManager;

public final class RandomEvents extends JavaPlugin {

    private EventManager eventManager;
    private DataManager dataManager;
    private RewardGenerator rewardGenerator;
    private ConfigManager configManager;

    @Override
    public void onEnable() {
        // Plugin startup logic
        configManager = new ConfigManager(this);
        dataManager = new DataManager(this);
        rewardGenerator = new RewardGenerator(this);
        eventManager = new EventManager(this, dataManager);
        new CommandManager(this, eventManager);
        
        getLogger().info("RandomEvents has been enabled!");
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    public EventManager getEventManager() {
        return eventManager;
    }

    public DataManager getDataManager() {
        return dataManager;
    }

    public RewardGenerator getRewardGenerator() {
        return rewardGenerator;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }
}
