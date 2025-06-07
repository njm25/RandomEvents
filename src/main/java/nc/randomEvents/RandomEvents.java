package nc.randomEvents;

import org.bukkit.plugin.java.JavaPlugin;
import nc.randomEvents.commands.CommandManager;
import nc.randomEvents.services.*;
import nc.randomEvents.services.participants.BlockManager;
import nc.randomEvents.services.participants.EntityManager;
import nc.randomEvents.services.participants.EquipmentManager;
import nc.randomEvents.services.participants.ProjectileManager;
import nc.randomEvents.services.participants.TestManager;
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
    private EntityManager entityManager;
    private ProjectileManager projectileManager;

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

    void setCommandManager(CommandManager commandManager) {
        this.commandManager = commandManager;
    }

    void setMetrics(Metrics metrics) {
        this.metrics = metrics;
    }

    void setEventManager(EventManager eventManager) {
        this.eventManager = eventManager;
    }

    void setDataManager(DataManager dataManager) {
        this.dataManager = dataManager;
    }
    
    void setConfigManager(ConfigManager configManager) {
        this.configManager = configManager;
    }

    void setSessionRegistry(SessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    void setRewardGenerator(RewardGenerator rewardGenerator) {    
        this.rewardGenerator = rewardGenerator;
    }

    void setEquipmentManager(EquipmentManager equipmentManager) {
        this.equipmentManager = equipmentManager;
    }

    void setTestManager(TestManager testManager) {
        this.testManager = testManager;
    }

    void setDisableManager(DisableManager disableManager) {
        this.disableManager = disableManager;
    }

    void setBlockManager(BlockManager blockManager) {
        this.blockManager = blockManager;
    }

    void setEntityManager(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    void setProjectileManager(ProjectileManager projectileManager) {
        this.projectileManager = projectileManager;
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

    public EntityManager getEntityManager() {
        return entityManager;
    }

    public ProjectileManager getProjectileManager() {
        return projectileManager;
    }

    // #endregion Getters
}
