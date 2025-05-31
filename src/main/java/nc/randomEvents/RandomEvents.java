package nc.randomEvents;

import org.bukkit.plugin.java.JavaPlugin;

import nc.randomEvents.commands.CommandManager;
import nc.randomEvents.services.*;
import nc.randomEvents.utils.Metrics;

public final class RandomEvents extends JavaPlugin {

    private EventManager eventManager;
    private DataManager dataManager;
    private ConfigManager configManager;
    private SessionRegistry sessionRegistry;
    private RewardGenerator rewardGenerator;
    private EquipmentManager equipmentManager;
    private TestManager testManager;
    @Override
    public void onEnable() {
        // Initialize metrics
        new Metrics(this, 26005);
        
        // First layer: Independent services
        configManager = new ConfigManager(this);
        dataManager = new DataManager(this);
        sessionRegistry = new SessionRegistry(this);
        rewardGenerator = new RewardGenerator(this);
        
        // Second layer: Services that depend on the registry
        equipmentManager = new EquipmentManager(this);
        testManager = new TestManager(this);
        
        // Third layer: Event system that coordinates everything
        eventManager = new EventManager(this);
        
        // Finally: Command system that needs access to everything
        new CommandManager(this);
        
        getLogger().info("RandomEvents has been enabled!");
    }

    @Override
    public void onDisable() {
        if (eventManager != null) {
            eventManager.shutdown();
        }
    }

    public EventManager getEventManager() {
        return eventManager;
    }

    public DataManager getDataManager() {
        return dataManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    public SessionRegistry getSessionRegistry() {
        return sessionRegistry;
    }
    
    public RewardGenerator getRewardGenerator() {
        return rewardGenerator;
    }
    
    public EquipmentManager getEquipmentManager() {
        return equipmentManager;
    }

    public TestManager getTestManager() {
        return testManager;
    }
}
