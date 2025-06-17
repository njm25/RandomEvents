package nc.randomEvents;

import nc.randomEvents.commands.CommandManager;
import nc.randomEvents.services.ConfigManager;
import nc.randomEvents.services.DataManager;
import nc.randomEvents.services.DisableManager;
import nc.randomEvents.services.EventManager;
import nc.randomEvents.services.RewardGenerator;
import nc.randomEvents.services.SessionRegistry;
import nc.randomEvents.services.participants.BlockManager;
import nc.randomEvents.services.participants.EntityManager;
import nc.randomEvents.services.participants.EquipmentManager;
import nc.randomEvents.services.participants.ProjectileManager;
import nc.randomEvents.services.participants.TestManager;
import nc.randomEvents.services.participants.container.ContainerManager;
import nc.randomEvents.utils.Metrics;

public class StartManager {
    private final RandomEvents plugin;

    public StartManager(RandomEvents plugin) {
        this.plugin = plugin;
    }

    public void start() {
        // First layer: Independent services
        plugin.setMetrics(new Metrics(plugin, 26005));
        plugin.setConfigManager(new ConfigManager(plugin));
        plugin.setDataManager(new DataManager(plugin));
        plugin.setSessionRegistry(new SessionRegistry(plugin));
        plugin.setRewardGenerator(new RewardGenerator(plugin));

        // Second layer: Services that depend on the registry
        plugin.setDisableManager(new DisableManager(plugin));
        plugin.setEquipmentManager(new EquipmentManager(plugin));
        plugin.setBlockManager(new BlockManager(plugin));
        plugin.setTestManager(new TestManager(plugin));
        plugin.setEntityManager(new EntityManager(plugin));
        plugin.setProjectileManager(new ProjectileManager(plugin));
        plugin.setContainerManager(new ContainerManager(plugin));

        // Third layer: Event system that coordinates everything
        plugin.setEventManager(new EventManager(plugin));

        // Finally: Command system that needs access to everything
        plugin.setCommandManager(new CommandManager(plugin));
    }
}
