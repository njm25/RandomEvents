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

interface IConfigManager {
    void reload();
    void setDefaults();
    <T> T getConfigValue(String eventName, String key);
    Integer getIntValue(String eventName, String key);
    Double getDoubleValue(String eventName, String key);
    ConfigurationSection getEventConfig(BaseEvent event);
}

public class ConfigManager implements IConfigManager {
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
        FileConfiguration oldConfig = plugin.getConfig();
        loadDefaults();
        plugin.reloadConfig();
        
        // Validate critical config sections
        if (!plugin.getConfig().isConfigurationSection("events")) {
            logger.severe("Invalid config file! Missing 'events' section!");
            logger.info("Reverting to previous configuration...");
            // Save the old config back
            try {
                oldConfig.save(configFile);
                plugin.reloadConfig();
            } catch (Exception e) {
                logger.severe("Failed to revert configuration: " + e.getMessage());
            }
            return;
        }
        
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
                logger.warning(String.format("No value or default found for key '%s' in event '%s'", 
                                           key, eventName));
                return null;
            }
            logger.info(String.format("Using default value for key '%s' in event '%s'", 
                                    key, eventName));
        }
        
        try {
            return (T) value;
        } catch (ClassCastException e) {
            String expectedType = e.getMessage().contains("cannot be cast to") ? 
                e.getMessage().split("cannot be cast to")[1].trim() : "unknown";
            String actualType = value != null ? value.getClass().getSimpleName() : "null";
            logger.warning(String.format("Invalid type for config value at '%s' in event '%s'. Expected: %s, Got: %s", 
                                       key, eventName, expectedType, actualType));
            return null;
        }
    }

    public Integer getIntValue(String eventName, String key) {
        String path = "events." + eventName + "." + key;
        Object value = plugin.getConfig().get(path);

        if (value instanceof Number) {
            return ((Number) value).intValue();
        }

        if (value != null) {
            logger.warning("Invalid type for config value at " + path + ": expected int but got " + value);
        }

        // Try default config
        Object defaultVal = defaultConfig.get(path);
        if (defaultVal instanceof Number) {
            return ((Number) defaultVal).intValue();
        }

        if (defaultVal != null) {
            logger.warning("Invalid default type at " + path + ": expected int but got " + defaultVal);
        }

        return null;
    }

    public Double getDoubleValue(String eventName, String key) {
        String path = "events." + eventName + "." + key;
        Object value = plugin.getConfig().get(path);

        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }

        if (value != null) {
            logger.warning("Invalid type for config value at " + path + ": expected double but got " + value);
        }

        // Try default config
        Object defaultVal = defaultConfig.get(path);
        if (defaultVal instanceof Number) {
            return ((Number) defaultVal).doubleValue();
        }

        if (defaultVal != null) {
            logger.warning("Invalid default type at " + path + ": expected double but got " + defaultVal);
        }

        return null;
    }

    public Integer getIntValue(BaseEvent event, String key) {
        return getIntValue(event.getName(), key);
    }

    public Double getDoubleValue(BaseEvent event, String key) {
        return getDoubleValue(event.getName(), key);
    }

    // Keep the generic method for non-numeric types
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
    
    public Boolean getBooleanValue(String eventName, String key) {
        String path = "events." + eventName + "." + key;
        Object value = plugin.getConfig().get(path);

        if (value instanceof Boolean) {
            return (Boolean) value;
        }

        if (value != null) {
            logger.warning(String.format("Invalid type for config value at '%s' in event '%s': expected boolean but got %s", 
                                       key, eventName, value.getClass().getSimpleName()));
        }

        // Try default config
        Object defaultVal = defaultConfig.get(path);
        if (defaultVal instanceof Boolean) {
            return (Boolean) defaultVal;
        }

        if (defaultVal != null) {
            logger.warning(String.format("Invalid default type at '%s' in event '%s': expected boolean but got %s", 
                                       key, eventName, defaultVal.getClass().getSimpleName()));
        }

        return null;
    }

}
