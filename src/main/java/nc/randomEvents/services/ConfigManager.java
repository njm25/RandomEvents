package nc.randomEvents.services;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import nc.randomEvents.RandomEvents;
import nc.randomEvents.core.BaseEvent;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.logging.Logger;

public class ConfigManager {
    private final RandomEvents plugin;
    private final Logger logger;
    private File configFile;
    private FileConfiguration defaultConfig;

    public ConfigManager(RandomEvents plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        
        // Ensure plugin directory exists
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        // Get the config.yml file in the plugin's folder
        configFile = new File(plugin.getDataFolder(), "config.yml");
        
        // Load default config from resources
        loadDefaults();
        
        // If config doesn't exist, save the default one
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }

        // Update config with any missing values
        setDefaults();
    }

    private void loadDefaults() {
        InputStream defaultStream = plugin.getResource("config.yml");
        if (defaultStream != null) {
            defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultStream));
        } else {
            logger.severe("Could not find config.yml in resources!");
            defaultConfig = new YamlConfiguration();
        }
    }

    public void reload() {
        loadDefaults();
        plugin.reloadConfig();
        setDefaults();
    }

    public void setDefaults() {
        boolean needsSaving = false;
        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);

        // Get all sections from defaults
        ConfigurationSection defaultEvents = defaultConfig.getConfigurationSection("events");
        if (defaultEvents == null) {
            logger.warning("No events section found in default config!");
            return;
        }

        // Create events section if it doesn't exist
        if (!config.isConfigurationSection("events")) {
            config.createSection("events");
            needsSaving = true;
        }

        // For each event in defaults
        for (String eventName : defaultEvents.getKeys(false)) {
            ConfigurationSection defaultEventSection = defaultEvents.getConfigurationSection(eventName);
            if (defaultEventSection == null) continue;

            // Create event section if it doesn't exist
            String eventPath = "events." + eventName;
            if (!config.isConfigurationSection(eventPath)) {
                config.createSection(eventPath);
                needsSaving = true;
            }

            // For each key in the default event section
            for (String key : defaultEventSection.getKeys(false)) {
                String fullPath = eventPath + "." + key;
                if (!config.contains(fullPath)) {
                    config.set(fullPath, defaultEventSection.get(key));
                    needsSaving = true;
                }
            }
        }

        if (needsSaving) {
            try {
                config.save(configFile);
                plugin.reloadConfig();
            } catch (Exception e) {
                logger.severe("Error saving config.yml: " + e.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T getConfigValue(String eventName, String key) {
        String path = "events." + eventName + "." + key;
        
        // Try to get from current config
        Object value = plugin.getConfig().get(path);
        
        // If not found in current config, try to get from defaults
        if (value == null) {
            value = defaultConfig.get(path);
            if (value == null) {
                logger.warning("No value or default found for " + path);
                return null;
            }
        }
        
        try {
            return (T) value;
        } catch (ClassCastException e) {
            logger.warning("Invalid type for config value at " + path);
            return null;
        }
    }

    public <T> T getConfigValue(BaseEvent event, String key) {
        return getConfigValue(event.getName(), key);
    }

    public ConfigurationSection getEventConfig(BaseEvent event) {
        String eventName = event.getName();
        ConfigurationSection eventSection = plugin.getConfig().getConfigurationSection("events." + eventName);
        
        if (eventSection == null) {
            plugin.getLogger().warning("No configuration found for event: " + eventName);
            return plugin.getConfig().createSection("events." + eventName);
        }
        
        return eventSection;
    }

}
