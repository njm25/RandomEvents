package nc.randomEvents;

import org.bukkit.plugin.java.JavaPlugin;

import nc.randomEvents.commands.CommandManager;
import nc.randomEvents.services.*;
import nc.randomEvents.utils.Metrics;

public final class RandomEvents extends JavaPlugin {

    private Metrics metrics;
    private EventManager eventManager;
    private DataManager dataManager;
    private ConfigManager configManager;
    private SessionRegistry sessionRegistry;
    private RewardGenerator rewardGenerator;
    private EquipmentManager equipmentManager;
    private TestManager testManager;
    private DisableManager disableManager;
    private CommandManager commandManager;
    private StartManager startManager;
    private BlockManager blockManager;

    @Override
    public void onEnable() {
        try {
            startManager = new StartManager(this);
            startManager.start();
        } catch (Exception ex) {
            getLogger().severe("Plugin failed to start correctly. Disabling...");
            ex.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
            
        getLogger().info("RandomEvents has been enabled!");
    }

    @Override
    public void onDisable() {
        if (disableManager != null) {
            disableManager.disablePlugin();
        }
        getLogger().info("RandomEvents has been disabled!");
    }
    
    // #region Setters

    public void setCommandManager(CommandManager commandManager) {
        this.commandManager = commandManager;
    }

    public void setMetrics(Metrics metrics) {
        this.metrics = metrics;
    }

    public void setEventManager(EventManager eventManager) {
        this.eventManager = eventManager;
    }

    public void setDataManager(DataManager dataManager) {
        this.dataManager = dataManager;
    }
    
    public void setConfigManager(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public void setSessionRegistry(SessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    public void setRewardGenerator(RewardGenerator rewardGenerator) {    
        this.rewardGenerator = rewardGenerator;
    }

    public void setEquipmentManager(EquipmentManager equipmentManager) {
        this.equipmentManager = equipmentManager;
    }

    public void setTestManager(TestManager testManager) {
        this.testManager = testManager;
    }

    public void setDisableManager(DisableManager disableManager) {
        this.disableManager = disableManager;
    }

    public void setBlockManager(BlockManager blockManager) {
        this.blockManager = blockManager;
    }

    // #endregion Setters

    // #region Getters

    public Metrics getMetrics() {
        return metrics;
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

    public DisableManager getDisableManager() {
        return disableManager;
    }

    public CommandManager getCommandManager() {
        return commandManager;
    }

    public BlockManager getBlockManager() {
        return blockManager;
    }

    // #endregion Getters
}
