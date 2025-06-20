package nc.randomEvents.services;

import nc.randomEvents.RandomEvents;
import nc.randomEvents.services.participants.container.ContainerData;
import org.bukkit.Location;
import org.bukkit.block.Container;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class DataManager {
    private final RandomEvents plugin;
    private FileConfiguration dataConfig = null;
    private File configFile = null;
    private static final String WORLDS_PATH = "accepted-worlds";
    private static final String CONTAINERS_PATH = "containers";

    public DataManager(RandomEvents plugin) {
        this.plugin = plugin;
        saveDefaultConfig();
        reloadData(); // Load data on initialization
    }

    public void reloadData() {
        if (configFile == null) {
            configFile = new File(plugin.getDataFolder(), "data.yml");
        }
        dataConfig = YamlConfiguration.loadConfiguration(configFile);

        // Ensure the list path exists
        if (!dataConfig.isList(WORLDS_PATH)) {
            dataConfig.set(WORLDS_PATH, new ArrayList<String>());
            saveData();
        }
    }

    public FileConfiguration getData() {
        if (dataConfig == null) {
            reloadData();
        }
        return dataConfig;
    }

    public void saveData() {
        if (dataConfig == null || configFile == null) {
            return;
        }
        try {
            getData().save(configFile);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save config to " + configFile, ex);
        }
    }

    public void saveDefaultConfig() {
        if (configFile == null) {
            configFile = new File(plugin.getDataFolder(), "data.yml");
        }
        if (!configFile.exists()) {
            plugin.saveResource("data.yml", false);
        }
    }

    public List<String> getAcceptedWorlds() {
        return getData().getStringList(WORLDS_PATH);
    }

    public boolean addAcceptedWorld(String worldName) {
        List<String> worlds = getAcceptedWorlds();
        if (!worlds.contains(worldName.toLowerCase())) {
            worlds.add(worldName.toLowerCase());
            getData().set(WORLDS_PATH, worlds);
            saveData();
            return true;
        }
        return false;
    }

    public boolean removeAcceptedWorld(String worldName) {
        List<String> worlds = getAcceptedWorlds();
        boolean removed = worlds.remove(worldName.toLowerCase());
        if (removed) {
            getData().set(WORLDS_PATH, worlds);
            saveData();
        }
        return removed;
    }

    // Container data management methods
    public void saveContainers(Map<Location, ContainerData> containers) {
        // Clear the existing containers section
        getData().set(CONTAINERS_PATH, null);

        for (ContainerData data : containers.values()) {
            Location loc = data.getLocation();
            String path = CONTAINERS_PATH + "." + loc.getWorld().getName() + "." + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
            getData().set(path + ".type", data.getType().name());
            getData().set(path + ".container_id", data.getContainerId());
            getData().set(path + ".session_id", data.getSessionId().toString());
            getData().set(path + ".clear_at_end", data.isClearAtEnd());
        }
        saveData();
    }

    public void loadAndVerifyContainers(Map<Location, ContainerData> containers) {
        ConfigurationSection containersSection = getData().getConfigurationSection(CONTAINERS_PATH);
        if (containersSection == null) {
            return;
        }

        boolean needsSave = false;
        for (String worldName : containersSection.getKeys(false)) {
            ConfigurationSection worldSection = containersSection.getConfigurationSection(worldName);
            if (worldSection == null) continue;

            for (String coords : worldSection.getKeys(false)) {
                try {
                    String[] parts = coords.split(",");
                    int x = Integer.parseInt(parts[0]);
                    int y = Integer.parseInt(parts[1]);
                    int z = Integer.parseInt(parts[2]);

                    Location location = new Location(
                        plugin.getServer().getWorld(worldName),
                        x, y, z
                    );

                    // Verification Step
                    if (!location.isWorldLoaded() || !(location.getBlock().getState() instanceof Container)) {
                        plugin.getLogger().warning("Phantom container at " + location + " found in data.yml. Removing.");
                        worldSection.set(coords, null); // Mark for removal from the config
                        needsSave = true;
                        continue;
                    }
                    
                    ConfigurationSection containerSection = worldSection.getConfigurationSection(coords);
                    if (containerSection == null) continue;

                    ContainerData.ContainerType type = ContainerData.ContainerType.valueOf(containerSection.getString("type"));
                    String containerId = containerSection.getString("container_id");
                    UUID sessionId = UUID.fromString(containerSection.getString("session_id"));
                    boolean clearAtEnd = containerSection.getBoolean("clear_at_end");

                    ContainerData data = new ContainerData(location, type, containerId, sessionId, clearAtEnd);
                    containers.put(location, data);

                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Failed to load container data for " + worldName + "," + coords + ", it will be removed.", e);
                    worldSection.set(coords, null);
                    needsSave = true;
                }
            }
        }

        if (needsSave) {
            saveData();
        }
    }

    public void removeContainer(Location location) {
        String path = CONTAINERS_PATH + "." + location.getWorld().getName() + "." + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
        
        getData().set(path, null);
        saveData();
    }
}
