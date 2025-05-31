package nc.randomEvents.services;

import nc.randomEvents.RandomEvents;
import nc.randomEvents.commands.CommandManager;
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
        plugin.setTestManager(new TestManager(plugin));

        // Third layer: Event system that coordinates everything
        plugin.setEventManager(new EventManager(plugin));

        // Finally: Command system that needs access to everything
        plugin.setCommandManager(new CommandManager(plugin));
    }
}
