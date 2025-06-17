package nc.randomEvents.services.participants.container;

import nc.randomEvents.RandomEvents;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

public class ContainerPersistence {
    private final RandomEvents plugin;
    private final File dataFile;
    private final ContainerRegistry registry;

    public ContainerPersistence(RandomEvents plugin, ContainerRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
        this.dataFile = new File(plugin.getDataFolder(), "containers.yml");
    }

    public void saveContainers() {
        YamlConfiguration config = new YamlConfiguration();
        
        for (Map.Entry<Location, ContainerData> entry : registry.getAllContainers().entrySet()) {
            Location loc = entry.getKey();
            ContainerData data = entry.getValue();
            
            String path = String.format("%s.%d,%d,%d", 
                loc.getWorld().getName(),
                loc.getBlockX(),
                loc.getBlockY(),
                loc.getBlockZ());
            
            config.set(path + ".type", data.getType().name());
            config.set(path + ".container_id", data.getContainerId());
            config.set(path + ".session_id", data.getSessionId().toString());
            config.set(path + ".clear_at_end", data.isClearAtEnd());
            config.set(path + ".last_modified", data.getLastModified());
        }
        
        try {
            config.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save container data: " + e.getMessage());
        }
    }

    public void loadContainers() {
        if (!dataFile.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        
        for (String worldName : config.getKeys(false)) {
            ConfigurationSection worldSection = config.getConfigurationSection(worldName);
            if (worldSection == null) continue;
            
            for (String coords : worldSection.getKeys(false)) {
                ConfigurationSection containerSection = worldSection.getConfigurationSection(coords);
                if (containerSection == null) continue;
                
                try {
                    String[] parts = coords.split(",");
                    int x = Integer.parseInt(parts[0]);
                    int y = Integer.parseInt(parts[1]);
                    int z = Integer.parseInt(parts[2]);
                    
                    Location location = new Location(
                        plugin.getServer().getWorld(worldName),
                        x, y, z
                    );
                    
                    ContainerData.ContainerType type = ContainerData.ContainerType.valueOf(containerSection.getString("type"));
                    String containerId = containerSection.getString("container_id");
                    UUID sessionId = UUID.fromString(containerSection.getString("session_id"));
                    boolean clearAtEnd = containerSection.getBoolean("clear_at_end");
                    
                    ContainerData data = new ContainerData(location, type, containerId, sessionId, clearAtEnd);
                    registry.registerContainer(location, data);
                    
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load container data for " + worldName + "," + coords + ": " + e.getMessage());
                }
            }
        }
    }
} 