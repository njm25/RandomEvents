package nc.randomEvents;

import org.bukkit.plugin.java.JavaPlugin;

import nc.randomEvents.commands.CommandManager;
import nc.randomEvents.services.DataManager;
import nc.randomEvents.services.EventManager;
import nc.randomEvents.services.RewardGenerator;
import nc.randomEvents.utils.Metrics;
import nc.randomEvents.services.ConfigManager;
import nc.randomEvents.services.EquipmentManager;

public final class RandomEvents extends JavaPlugin {

    private EventManager eventManager;
    private DataManager dataManager;
    private RewardGenerator rewardGenerator;
    private ConfigManager configManager;
    private EquipmentManager equipmentManager;
    
    @Override
    public void onEnable() {
        new Metrics(this, 26005);
        configManager = new ConfigManager(this);
        dataManager = new DataManager(this);
        rewardGenerator = new RewardGenerator(this);
        equipmentManager = new EquipmentManager(this);
        eventManager = new EventManager(this);
        new CommandManager(this);
        
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

    public EquipmentManager getEquipmentManager() {
        return equipmentManager;
    }
}
